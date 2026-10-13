package com.example.chesspairmobile

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chesspairmobile.data.db.*
import com.example.chesspairmobile.viewmodel.TournamentViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TournamentWorkflowIntegrationTest {
    private val event = TournamentEntity("fixture", "Workflow review", null, "SWISS", false, true,
        false, null, "COMPLETED", 1, 1, 1.0, 0.5, 0.0, false, "[]")

    private fun fixture(
        initial: TournamentEntity = event,
        boards: List<MatchEntity>,
        block: suspend (TournamentViewModel, TournamentDao, TournamentEntity) -> Unit
    ) = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val dao = AppDatabase.getInstance(app).tournamentDao()
        val id = "workflow-${UUID.randomUUID()}"
        val tournament = initial.copy(id = id)
        val store = ViewModelStore()
        val viewModel = withContext(Dispatchers.Main) {
            TournamentViewModel(app).also { store.put(id, it) }
        }
        try {
            dao.insertTournament(tournament)
            dao.insertPlayers(listOf("a", "b", "c").map {
                PlayerEntity("$id-$it", id, it, 1500, null, null, active = true,
                    withdrawn = false, requestedByes = "[]", penaltyPoints = 0.0)
            })
            dao.insertMatches(boards.map {
                it.copy(id = "$id-${it.id}", tournamentId = id,
                    whiteId = it.whiteId?.let { player -> "$id-$player" },
                    blackId = it.blackId?.let { player -> "$id-$player" })
            })
            withTimeout(10_000) { viewModel.tournaments.first { list -> list.any { it.id == id } } }
            withContext(Dispatchers.Main) { viewModel.setActiveTournament(id) }
            withTimeout(10_000) {
                viewModel.players.first { it.size == 3 }
                viewModel.matches.first { it.size == boards.size }
            }
            block(viewModel, dao, tournament)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            dao.deleteMatchesByTournament(id)
            dao.deletePlayersByTournament(id)
            dao.deleteTeamsByTournament(id)
            dao.deleteTournamentById(id)
        }
    }

    private val game = MatchEntity("game", "fixture", 1, "a", "b", "WHITE_WIN", 1, null, false, null)
    private val bye = MatchEntity("bye", "fixture", 1, "c", null, "BYE", 999, null, false, null)

    @Test fun reopeningPreservesEveryResultAndCanFinishAgain() = fixture(boards = listOf(game, bye)) { vm, dao, t ->
        val before = dao.getMatchesByTournament(t.id).sortedBy { it.id }
        withContext(Dispatchers.Main) { vm.rollbackRound() }
        withTimeout(10_000) { vm.tournaments.first { list -> list.any { it.id == t.id && it.status == "ACTIVE" } } }
        assertEquals(before, dao.getMatchesByTournament(t.id).sortedBy { it.id })
        assertTrue(withContext(Dispatchers.Main) { vm.completeRound() })
        withTimeout(10_000) { vm.tournaments.first { list -> list.any { it.id == t.id && it.status == "COMPLETED" } } }
        assertEquals(before, dao.getMatchesByTournament(t.id).sortedBy { it.id })
    }

    @Test fun knockoutDrawCannotAdvanceInViewModel() = fixture(
        initial = event.copy(type = "KNOCKOUT", status = "ACTIVE", totalRounds = 2),
        boards = listOf(game.copy(result = "DRAW"))
    ) { vm, dao, t ->
        assertFalse(withContext(Dispatchers.Main) { vm.completeRound() })
        assertEquals(1, dao.getTournament(t.id)?.currentRound)
    }

    @Test fun setupEditKeepsRosterAndClearsOptionalFields() = fixture(
        initial = event.copy(status = "SETUP", currentRound = 0, timeControl = "90+30", section = "Open"),
        boards = emptyList()
    ) { vm, dao, t ->
        val before = dao.getPlayersByTournament(t.id).sortedBy { it.id }
        assertTrue(withContext(Dispatchers.Main) { vm.saveTournamentSetup(t.copy(name = "Edited event",
            timeControl = null, section = null, autoCalculateRounds = false, totalRounds = 7, pointsForWin = 3.0)) })
        withTimeout(10_000) { vm.tournaments.first { list -> list.any { it.id == t.id && it.name == "Edited event" } } }
        val saved = requireNotNull(dao.getTournament(t.id))
        assertEquals(7, saved.totalRounds)
        assertEquals(3.0, saved.pointsForWin, 0.0)
        assertNull(saved.timeControl)
        assertNull(saved.section)
        assertEquals(before, dao.getPlayersByTournament(t.id).sortedBy { it.id })
    }
}

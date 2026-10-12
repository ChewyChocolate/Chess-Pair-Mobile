package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.db.Converters
import org.junit.Assert.*
import org.junit.Test

class TestTournamentPresetsTest {
    @Test fun repeatedLoadsHaveIndependentIdsAndRepeatableRosters() {
        TestTournamentPreset.entries.forEach { preset ->
            val first = buildTestTournament(preset)
            val second = buildTestTournament(preset)
            assertEquals(preset.playerCount, first.players.size)
            assertEquals("SETUP", first.tournament.status)
            assertEquals(0, first.tournament.currentRound)
            assertTrue(first.tournament.autoCalculateRounds)
            assertEquals(preset.isTeam, first.tournament.isTeamTournament)
            assertTrue(first.players.all { it.tournamentId == first.tournament.id && it.active && !it.withdrawn })
            assertEquals(first.players.size, first.players.map { it.name }.distinct().size)
            assertEquals(first.players.map { listOf(it.name, it.rating, it.title, it.club) },
                second.players.map { listOf(it.name, it.rating, it.title, it.club) })
            val firstIds = listOf(first.tournament.id) + first.players.map { it.id } + first.teams.map { it.id }
            val secondIds = listOf(second.tournament.id) + second.players.map { it.id } + second.teams.map { it.id }
            assertEquals(firstIds.size, firstIds.distinct().size)
            assertTrue(firstIds.toSet().intersect(secondIds.toSet()).isEmpty())
            if (!preset.isTeam) assertTrue(first.teams.isEmpty())
        }
    }

    @Test fun teamsCoverEntireRosterOnceInRatingOrder() {
        val data = buildTestTournament(TestTournamentPreset.TEAMS)
        val converters = Converters()
        assertEquals(4, data.teams.size)
        assertTrue(data.teams.all { it.tournamentId == data.tournament.id })
        val rosters = data.teams.map { converters.toStringList(it.playerIds) }
        assertTrue(rosters.all { it.size == 4 })
        assertEquals(data.players.map { it.id }, rosters.flatten())
        data.teams.zip(data.players.chunked(4)).forEach { (team, players) ->
            assertTrue(players.all { it.club == team.name })
        }
    }
}

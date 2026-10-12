package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.db.*
import com.example.chesspairmobile.data.models.*
import java.util.UUID

enum class TestTournamentPreset(val label: String, val playerCount: Int, val isTeam: Boolean = false) {
    QUICK("8 players · quick test", 8),
    ODD("9 players · bye handling", 9),
    LARGE("32 players · larger field", 32),
    TEAMS("4 teams · 4 players each", 16, true)
}

data class TestTournamentData(
    val tournament: TournamentEntity,
    val players: List<PlayerEntity>,
    val teams: List<TeamEntity>
)

/** Fixed roster and settings, with fresh IDs so repeated loads remain independent. */
fun buildTestTournament(preset: TestTournamentPreset): TestTournamentData {
    val converters = Converters()
    val id = UUID.randomUUID().toString()
    val clubs = listOf("Northside", "Westside", "Riverside", "Central")
    val names = listOf(
        "Alex Morgan", "Blair Chen", "Casey Patel", "Dana Reyes",
        "Elliot Park", "Finley Brooks", "Gray Davis", "Harper Singh",
        "Indigo Cruz", "Jamie Lee", "Kai Bennett", "Logan Shah",
        "Micah Kim", "Noel Rivera", "Oakley Reed", "Parker Ali",
        "Quinn Foster", "Riley Tran", "Sage Torres", "Taylor Nguyen",
        "Uma Collins", "Val Jordan", "Winter Evans", "Xavier Bell",
        "Yara Scott", "Zion Wilson", "Avery Clark", "Bailey Ahmed",
        "Cameron Ross", "Devon Flores", "Emery Brown", "Frankie Silva"
    )
    val players = names.take(preset.playerCount).mapIndexed { index, name ->
        PlayerEntity(
            id = UUID.randomUUID().toString(), tournamentId = id, name = name,
            rating = if (index == preset.playerCount - 1) null else 2200 - index * 40,
            title = if (index == 0) "CM" else null,
            club = clubs[if (preset.isTeam) index / 4 else index % clubs.size],
            active = true, withdrawn = false, requestedByes = "[]", penaltyPoints = 0.0
        )
    }
    val teams = if (preset.isTeam) players.chunked(4).mapIndexed { index, roster ->
        TeamEntity(UUID.randomUUID().toString(), id, clubs[index], converters.fromStringList(roster.map { it.id }))
    } else emptyList()
    val tournament = TournamentEntity(
        id = id, name = "Test · ${preset.label} · ${id.take(8)}", section = "Test data",
        type = TournamentType.SWISS.name, isTeamTournament = preset.isTeam,
        autoCalculateRounds = true, manualRoundOverride = false, timeControl = "10+5",
        status = TournamentStatus.SETUP.name, currentRound = 0, totalRounds = 1,
        pointsForWin = 1.0, pointsForDraw = 0.5, pointsForLoss = 0.0,
        avoidClubPairings = false,
        tiebreakOrder = converters.fromTiebreakList(defaultTiebreakOrderForType(TournamentType.SWISS))
    )
    return TestTournamentData(tournament, players, teams)
}

package com.example.chesspairmobile.data.models

import java.util.UUID

data class Player(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val rating: Int? = null,
    val title: String? = null,
    val club: String? = null,
    val active: Boolean = true,
    val withdrawn: Boolean = false,
    val requestedByes: List<Int> = emptyList(),
    val penaltyPoints: Double = 0.0
)

data class Team(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val playerIds: List<String> = emptyList()
)

data class Match(
    val id: String = UUID.randomUUID().toString(),
    val round: Int,
    val whiteId: String? = null,
    val blackId: String? = null,
    val result: MatchResult? = null,
    val boardNumber: Int? = null,
    val teamMatchId: String? = null,
    val isManual: Boolean = false,
    val pgn: String? = null
)

enum class MatchResult {
    WHITE_WIN, BLACK_WIN, DRAW, BYE, FORFEIT_WHITE, FORFEIT_BLACK
}

data class ForcedPairing(
    val whiteId: String,
    val blackId: String
)

enum class TournamentType {
    SWISS, ROUND_ROBIN, KNOCKOUT
}

enum class TournamentStatus {
    SETUP, ACTIVE, COMPLETED
}

enum class TiebreakType {
    DIRECT_ENCOUNTER, BUCHHOLZ, MEDIAN_BUCHHOLZ, SONNEBORN_BERGER, MOST_WINS, RATING
}

data class Tournament(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val section: String? = null,
    val type: TournamentType,
    val isTeamTournament: Boolean = false,
    val autoCalculateRounds: Boolean = false,
    val manualRoundOverride: Boolean = false,
    val timeControl: String? = null,
    val status: TournamentStatus = TournamentStatus.SETUP,
    val currentRound: Int = 0,
    val totalRounds: Int = 0,
    val pointsForWin: Double = 1.0,
    val pointsForDraw: Double = 0.5,
    val pointsForLoss: Double = 0.0,
    val avoidClubPairings: Boolean = false,
    val forcedPairings: List<ForcedPairing> = emptyList(),
    val tiebreakOrder: List<TiebreakType> = listOf(
        TiebreakType.DIRECT_ENCOUNTER,
        TiebreakType.BUCHHOLZ,
        TiebreakType.MEDIAN_BUCHHOLZ,
        TiebreakType.SONNEBORN_BERGER,
        TiebreakType.MOST_WINS,
        TiebreakType.RATING
    )
) {
}

data class PlayerStanding(
    val player: Player,
    val score: Double,
    val buchholz: Double,
    val sonnebornBerger: Double,
    val medianBuchholz: Double,
    val mostWins: Int,
    val directEncounter: Double
)

data class TeamStanding(
    val team: Team,
    val matchPoints: Double,
    val gamePoints: Double,
    val sonnebornBerger: Double
)

data class PlayerMatchStat(
    val round: Int,
    val opponentId: String?,
    val opponentName: String,
    val color: Char?, // 'W', 'B', or null
    val result: String? // "1", "0", "0.5", or null
)

data class PlayerStatsResult(
    val stats: List<PlayerMatchStat>,
    val consecutiveColors: Int,
    val lastColor: Char?,
    val colorDiff: Int,
    val hasColorWarning: Boolean,
    val byeCount: Int,
    val hasByeWarning: Boolean
)

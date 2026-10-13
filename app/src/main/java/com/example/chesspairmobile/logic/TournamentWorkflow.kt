package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*

fun tournamentRoundCount(type: String, participants: Int, automatic: Boolean, manualOverride: Boolean, configured: Int, extraSwissRound: Boolean): Int {
    if (manualOverride || (type == "SWISS" && !automatic)) return configured
    if (participants < 2) return 0
    return when (type) {
        "ROUND_ROBIN" -> if (participants % 2 == 0) participants - 1 else participants
        "KNOCKOUT" -> kotlin.math.ceil(kotlin.math.log2(participants.toDouble())).toInt()
        else -> kotlin.math.ceil(kotlin.math.log2(participants.toDouble())).toInt() + if (extraSwissRound) 1 else 0
    }
}

fun startRequirement(players: List<Player>, teams: List<Team>, teamEvent: Boolean, totalRounds: Int = 0): String? {
    val activeIds = players.filter { it.active && !it.withdrawn }.map { it.id }.toSet()
    if (teamEvent) {
        if (teams.any { team -> team.playerIds.any { it !in activeIds } }) {
            return "Remove unavailable players from teams before starting."
        }
        if (teams.count { it.playerIds.isNotEmpty() } < 2) {
            return "Create at least two teams and assign active players to each."
        }
    } else if (activeIds.size < 2) {
        return "Add at least ${2 - activeIds.size} more active player(s) to begin."
    }
    if (!teamEvent && totalRounds > 0 && players.any { it.id in activeIds && it.requestedByes.any { round -> round !in 1..totalRounds } }) {
        return "Review requested byes: every bye must be within rounds 1–$totalRounds."
    }
    return null
}

fun canUpdateResult(status: String, currentRound: Int, matchRound: Int, correctionRound: Int?): Boolean =
    (status == "ACTIVE" && matchRound == currentRound) ||
        (status in listOf("ACTIVE", "COMPLETED") && matchRound <= currentRound && correctionRound == matchRound)

fun roundCompletionIssue(type: String, teamEvent: Boolean, matches: List<Match>, teams: List<Team> = emptyList()): String? {
    if (matches.isEmpty()) return "Generate pairings before completing this round."
    val pending = matches.count { it.result == null }
    if (pending > 0) return "$pending results remaining"
    if (type == "KNOCKOUT" && !teamEvent && matches.any { it.result == MatchResult.DRAW }) {
        return "Resolve knockout draws with a playoff, then enter the deciding result."
    }
    if (type == "KNOCKOUT" && teamEvent) {
        val playerTeams = teams.flatMap { team -> team.playerIds.map { it to team.id } }.toMap()
        val gamePoints = calculateTeamScores(teams, matches).second
        val tied = matches.groupBy { it.teamMatchId }.values.any { boards ->
            val teamIds = boards.flatMap { listOfNotNull(playerTeams[it.whiteId], playerTeams[it.blackId]) }.distinct()
            teamIds.size == 2 && gamePoints[teamIds[0]] == gamePoints[teamIds[1]]
        }
        if (tied) return "Resolve tied team matches with a playoff, then enter the deciding board results."
    }
    return null
}

fun supportsForcedPairings(type: String, teamEvent: Boolean): Boolean = type == "SWISS" && !teamEvent

fun forcedPairingIssue(pair: ForcedPairing, existing: List<ForcedPairing>, players: List<Player>): String? {
    val activeIds = players.filter { it.active && !it.withdrawn }.map { it.id }.toSet()
    if (pair.whiteId == pair.blackId || pair.whiteId !in activeIds || pair.blackId !in activeIds) {
        return "Choose two different active players."
    }
    val assigned = existing.flatMap { listOf(it.whiteId, it.blackId) }.toSet()
    if (pair.whiteId in assigned || pair.blackId in assigned) return "A player is already in a forced pairing."
    return null
}

fun standingsSnapshotRound(round: Int, matches: List<Match>): Int =
    matches.filter { it.round <= round }.maxOfOrNull { it.round } ?: 0

fun standingsRoundLabel(round: Int, matches: List<Match>): String {
    val throughRound = matches.filter { it.round <= round }
    if (throughRound.none { it.round == round }) {
        val previous = throughRound.maxOfOrNull { it.round }
        return if (previous == null) "Round $round has not started · no results yet"
        else "Round $round has not started · standings through Round $previous" +
            if (throughRound.any { it.result == null }) " · provisional" else ""
    }
    return "Standings through Round $round" + if (throughRound.any { it.result == null }) " · provisional" else ""
}

fun parseByeRounds(text: String, totalRounds: Int): List<Int>? {
    if (text.isBlank()) return emptyList()
    val rounds = text.split(',').map { it.trim().toIntOrNull() ?: return null }
    if (rounds.any { it < 1 || (totalRounds > 0 && it > totalRounds) }) return null
    return rounds.distinct().sorted()
}

fun standingsCsv(
    players: List<Player>, teams: List<Team>, matches: List<Match>, round: Int,
    win: Double, draw: Double, loss: Double, order: List<TiebreakType>, teamView: Boolean
): String {
    fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
    return buildString {
        if (teamView) {
            append("Round,Rank,Team,MatchPoints,GamePoints,SonnebornBerger\n")
            calculateTeamStandings(teams, matches, round).forEachIndexed { index, standing ->
                append("$round,${index + 1},${quote(standing.team.name)},${standing.matchPoints},${standing.gamePoints},${standing.sonnebornBerger}\n")
            }
        } else {
            append("Round,Rank,Name,Rating,Score,Buchholz,MedianBuchholz,BuchholzCut1,SonnebornBerger,MostWins,Progressive,Performance\n")
            calculateStandings(players, matches, win, draw, loss, order, round).forEachIndexed { index, s ->
                append("$round,${index + 1},${quote(s.player.name)},${s.player.rating ?: ""},${s.score},${s.buchholz},${s.medianBuchholz},${s.buchholzCut1},${s.sonnebornBerger},${s.mostWins},${s.progressive},${s.performanceRating}\n")
            }
        }
    }
}

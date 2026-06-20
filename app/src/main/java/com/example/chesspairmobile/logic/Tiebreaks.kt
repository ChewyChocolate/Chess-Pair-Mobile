package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow

fun calculateStandings(
    players: List<Player>,
    matches: List<Match>,
    pointsForWin: Double,
    pointsForDraw: Double,
    pointsForLoss: Double,
    tiebreakOrder: List<TiebreakType>,
    upToRound: Int? = null
): List<PlayerStanding> {
    val data = calculateScores(players, matches, pointsForWin, pointsForDraw, pointsForLoss, upToRound)
    val matchesToProcess = if (upToRound != null) matches.filter { it.round <= upToRound } else matches

    // Progressive score: sum of scores after each round.
    // Pre-index matches by round so we walk rounds once, not n*m times.
    val maxRound = upToRound ?: matches.maxOfOrNull { it.round } ?: 0
    val matchesByRound: Map<Int, List<Match>> = matchesToProcess
        .filter { it.result != null }
        .groupBy { it.round }

    val progressiveScores = mutableMapOf<String, Double>()
    players.forEach { p ->
        var cumulative = 0.0
        val requested = p.requestedByes.toHashSet()
        for (r in 1..maxRound) {
            val roundMatches = matchesByRound[r] ?: emptyList()
            val playerMatch = roundMatches.firstOrNull { it.whiteId == p.id || it.blackId == p.id }
            if (playerMatch != null) {
                cumulative += getMatchPointsForPlayer(playerMatch, p.id, pointsForWin, pointsForDraw, pointsForLoss)
            }
            if (r in requested) {
                cumulative += pointsForDraw
            }
        }
        progressiveScores[p.id] = cumulative
    }

    val standingsResult = players.map { p ->
        val score = data.scores[p.id] ?: 0.0
        val opponents = data.played[p.id]?.toList() ?: emptyList()
        val opponentScores = opponents.map { data.scores[it] ?: 0.0 }

        val buchholz = opponentScores.sum()

        val medianBuchholz = if (opponentScores.size > 2) {
            val sorted = opponentScores.sorted()
            sorted.drop(1).dropLast(1).sum()
        } else buchholz

        val buchholzCut1 = if (opponentScores.size > 1) {
            opponentScores.sorted().drop(1).sum()
        } else opponentScores.sum()

        var sb = 0.0
        matchesToProcess.forEach { m ->
            if (m.whiteId == p.id) {
                val oppScore = data.scores[m.blackId] ?: 0.0
                when (m.result) {
                    MatchResult.WHITE_WIN -> sb += oppScore
                    MatchResult.DRAW -> sb += oppScore / 2
                    // Forfeit: treat as draw for tiebreaks per FIDE Annex 3
                    MatchResult.FORFEIT_WHITE, MatchResult.FORFEIT_BLACK -> sb += oppScore / 2
                    else -> {}
                }
            } else if (m.blackId == p.id) {
                val oppScore = data.scores[m.whiteId] ?: 0.0
                when (m.result) {
                    MatchResult.BLACK_WIN -> sb += oppScore
                    MatchResult.DRAW -> sb += oppScore / 2
                    MatchResult.FORFEIT_WHITE, MatchResult.FORFEIT_BLACK -> sb += oppScore / 2
                    else -> {}
                }
            }
        }

        var mostWins = 0
        matchesToProcess.forEach { m ->
            if (m.result == MatchResult.WHITE_WIN && m.whiteId == p.id) mostWins++
            if (m.result == MatchResult.BLACK_WIN && m.blackId == p.id) mostWins++
            if (m.result == MatchResult.FORFEIT_WHITE && m.blackId == p.id) mostWins++
            if (m.result == MatchResult.FORFEIT_BLACK && m.whiteId == p.id) mostWins++
        }

        // Performance Rating
        val perfRating = calculatePerformanceRating(players, matchesToProcess, p.id)

        PlayerStanding(
            player = p,
            score = score,
            buchholz = buchholz,
            sonnebornBerger = sb,
            medianBuchholz = medianBuchholz,
            buchholzCut1 = buchholzCut1,
            mostWins = mostWins,
            directEncounter = 0.0,
            progressive = progressiveScores[p.id] ?: 0.0,
            performanceRating = perfRating
        )
    }.toMutableList()

    // Sort
    standingsResult.sortWith(compareByDescending<PlayerStanding> { it.score }
        .thenComparator { a, b ->
            for (tb in tiebreakOrder) {
                when (tb) {
                    TiebreakType.DIRECT_ENCOUNTER -> {
                        val match = matchesToProcess.find { m ->
                            (m.whiteId == a.player.id && m.blackId == b.player.id) ||
                            (m.whiteId == b.player.id && m.blackId == a.player.id)
                        }
                        if (match?.result != null) {
                            if (match.whiteId == a.player.id) {
                                when (match.result) {
                                    MatchResult.WHITE_WIN, MatchResult.FORFEIT_BLACK -> return@thenComparator -1
                                    MatchResult.BLACK_WIN, MatchResult.FORFEIT_WHITE -> return@thenComparator 1
                                    else -> {}
                                }
                            } else {
                                when (match.result) {
                                    MatchResult.BLACK_WIN, MatchResult.FORFEIT_WHITE -> return@thenComparator -1
                                    MatchResult.WHITE_WIN, MatchResult.FORFEIT_BLACK -> return@thenComparator 1
                                    else -> {}
                                }
                            }
                        }
                    }
                    TiebreakType.BUCHHOLZ -> {
                        val diff = b.buchholz.compareTo(a.buchholz)
                        if (diff != 0) return@thenComparator diff
                    }
                    TiebreakType.MEDIAN_BUCHHOLZ -> {
                        val diff = b.medianBuchholz.compareTo(a.medianBuchholz)
                        if (diff != 0) return@thenComparator diff
                    }
                    TiebreakType.BUCHHOLZ_CUT1 -> {
                        val diff = b.buchholzCut1.compareTo(a.buchholzCut1)
                        if (diff != 0) return@thenComparator diff
                    }
                    TiebreakType.SONNEBORN_BERGER -> {
                        val diff = b.sonnebornBerger.compareTo(a.sonnebornBerger)
                        if (diff != 0) return@thenComparator diff
                    }
                    TiebreakType.MOST_WINS -> {
                        val diff = b.mostWins.compareTo(a.mostWins)
                        if (diff != 0) return@thenComparator diff
                    }
                    TiebreakType.RATING -> {
                        val aRating = a.player.rating ?: 0
                        val bRating = b.player.rating ?: 0
                        val diff = bRating.compareTo(aRating)
                        if (diff != 0) return@thenComparator diff
                    }
                    TiebreakType.PROGRESSIVE -> {
                        val diff = b.progressive.compareTo(a.progressive)
                        if (diff != 0) return@thenComparator diff
                    }
                }
            }
            a.player.name.compareTo(b.player.name)
        }
    )

    return standingsResult
}

// ---- Performance Rating ----
fun calculatePerformanceRating(players: List<Player>, matches: List<Match>, playerId: String): Int {
    val ratingMap = players.associate { it.id to (it.rating ?: 0) }
    var totalOpponentRating = 0
    var ratedGames = 0
    var score = 0.0

    for (m in matches) {
        if (m.result == null) continue
        if (m.result == MatchResult.BYE) continue
        val isWhite = m.whiteId == playerId
        val isBlack = m.blackId == playerId
        if (!isWhite && !isBlack) continue

        val opponentId = if (isWhite) m.blackId else m.whiteId
        val oppRating = ratingMap[opponentId] ?: 0
        if (oppRating == 0) continue

        totalOpponentRating += oppRating
        ratedGames++

        score += getMatchPointsForPlayer(m, playerId, 1.0, 0.5, 0.0)
    }

    if (ratedGames == 0) return 0

    val avgOppRating = totalOpponentRating.toDouble() / ratedGames
    val scorePercentage = score / ratedGames

    // Elo performance rating formula
    val dp = when {
        scorePercentage >= 1.0 -> 800.0
        scorePercentage <= 0.0 -> -800.0
        else -> -400.0 * log10(1.0 / scorePercentage - 1.0)
    }

    return (avgOppRating + dp).toInt().coerceAtLeast(0)
}

fun getMatchPointsForPlayer(
    match: Match, playerId: String,
    win: Double, draw: Double, loss: Double
): Double {
    val isWhite = match.whiteId == playerId
    return when (match.result) {
        MatchResult.WHITE_WIN -> if (isWhite) win else loss
        MatchResult.BLACK_WIN -> if (isWhite) loss else win
        MatchResult.DRAW -> draw
        MatchResult.BYE -> if (match.whiteId == playerId) win else loss
        MatchResult.FORFEIT_WHITE -> if (isWhite) loss else win
        MatchResult.FORFEIT_BLACK -> if (isWhite) win else loss
        null -> 0.0
    }
}

// ---- Team standings ----
fun calculateTeamStandings(
    teams: List<Team>,
    matches: List<Match>,
    upToRound: Int? = null
): List<TeamStanding> {
    val (teamMatchPoints, teamGamePoints) = calculateTeamScores(teams, matches, upToRound)
    val matchesToProcess = if (upToRound != null) matches.filter { it.round <= upToRound } else matches

    val teamPlayed = mutableMapOf<String, MutableSet<String>>()
    teams.forEach { teamPlayed[it.id] = mutableSetOf() }
    matchesToProcess.filter { it.teamMatchId != null && it.whiteId != null && it.blackId != null }.forEach { m ->
        val t1 = teams.find { it.playerIds.contains(m.whiteId) }
        val t2 = teams.find { it.playerIds.contains(m.blackId) }
        if (t1 != null && t2 != null) {
            teamPlayed.getOrPut(t1.id) { mutableSetOf() }.add(t2.id)
            teamPlayed.getOrPut(t2.id) { mutableSetOf() }.add(t1.id)
        }
    }

    val standings = teams.map { team ->
        val mp = teamMatchPoints[team.id] ?: 0.0
        val gp = teamGamePoints[team.id] ?: 0.0
        var sb = 0.0

        val opponents = teamPlayed[team.id] ?: emptySet()
        opponents.forEach { oppId ->
            val teamMatches = matchesToProcess.filter { m ->
                m.teamMatchId != null &&
                ((teams.find { it.id == team.id }?.playerIds?.contains(m.whiteId) == true &&
                  teams.find { it.id == oppId }?.playerIds?.contains(m.blackId) == true) ||
                 (teams.find { it.id == team.id }?.playerIds?.contains(m.blackId) == true &&
                  teams.find { it.id == oppId }?.playerIds?.contains(m.whiteId) == true))
            }
            if (teamMatches.isNotEmpty()) {
                var teamGP = 0.0
                var oppGP = 0.0
                teamMatches.forEach { m ->
                    val isTeamWhite = teams.find { it.id == team.id }?.playerIds?.contains(m.whiteId) == true
                    when (m.result) {
                        MatchResult.WHITE_WIN -> if (isTeamWhite) teamGP++ else oppGP++
                        MatchResult.BLACK_WIN -> if (isTeamWhite) oppGP++ else teamGP++
                        MatchResult.DRAW -> { teamGP += 0.5; oppGP += 0.5 }
                        MatchResult.FORFEIT_WHITE -> if (isTeamWhite) oppGP++ else teamGP++
                        MatchResult.FORFEIT_BLACK -> if (isTeamWhite) teamGP++ else oppGP++
                        else -> {}
                    }
                }
                val oppMP = teamMatchPoints[oppId] ?: 0.0
                if (teamGP > oppGP) sb += oppMP
                else if (teamGP == oppGP) sb += oppMP / 2
            }
        }

        TeamStanding(team = team, matchPoints = mp, gamePoints = gp, sonnebornBerger = sb)
    }

    return standings.sortedWith(
        compareByDescending<TeamStanding> { it.matchPoints }
            .thenByDescending { it.gamePoints }
            .thenByDescending { it.sonnebornBerger }
    )
}

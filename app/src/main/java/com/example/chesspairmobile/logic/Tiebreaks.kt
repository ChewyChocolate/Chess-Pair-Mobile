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

    // Pre-compute per-player Sonneborn-Berger and most-wins in a single pass
    // over matches: each match contributes to (at most) two players' SB and
    // exactly two players' most-wins counters. This is O(n+m) instead of O(n*m).
    val sbByPlayer = HashMap<String, Double>().apply {
        players.forEach { put(it.id, 0.0) }
    }
    val mostWinsByPlayer = HashMap<String, Int>().apply {
        players.forEach { put(it.id, 0) }
    }
    matchesToProcess.forEach { m ->
        val result = m.result ?: return@forEach
        val white = m.whiteId
        val black = m.blackId
        if (white != null && black != null) {
            val whiteScore = data.scores[black] ?: 0.0
            val blackScore = data.scores[white] ?: 0.0
            when (result) {
                MatchResult.WHITE_WIN -> {
                    sbByPlayer[white] = (sbByPlayer[white] ?: 0.0) + whiteScore
                    mostWinsByPlayer[white] = (mostWinsByPlayer[white] ?: 0) + 1
                }
                MatchResult.BLACK_WIN -> {
                    sbByPlayer[black] = (sbByPlayer[black] ?: 0.0) + blackScore
                    mostWinsByPlayer[black] = (mostWinsByPlayer[black] ?: 0) + 1
                }
                MatchResult.DRAW -> {
                    sbByPlayer[white] = (sbByPlayer[white] ?: 0.0) + whiteScore / 2
                    sbByPlayer[black] = (sbByPlayer[black] ?: 0.0) + blackScore / 2
                }
                // Forfeit: treat as draw for tiebreaks per FIDE Annex 3
                MatchResult.FORFEIT_WHITE -> {
                    sbByPlayer[black] = (sbByPlayer[black] ?: 0.0) + blackScore / 2
                    mostWinsByPlayer[black] = (mostWinsByPlayer[black] ?: 0) + 1
                }
                MatchResult.FORFEIT_BLACK -> {
                    sbByPlayer[white] = (sbByPlayer[white] ?: 0.0) + whiteScore / 2
                    mostWinsByPlayer[white] = (mostWinsByPlayer[white] ?: 0) + 1
                }
                MatchResult.BYE -> {}
            }
        }
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

        val sb = sbByPlayer[p.id] ?: 0.0
        val mostWins = mostWinsByPlayer[p.id] ?: 0

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

    // O(1) lookups: player → team and teamMatchId → list of matches.
    val playerToTeam: Map<String, String> = teams
        .flatMap { t -> t.playerIds.map { it to t.id } }
        .toMap()
    val matchesByTeamMatch: Map<String, List<Match>> = matchesToProcess
        .filter { it.teamMatchId != null }
        .groupBy { it.teamMatchId!! }

    // For each (teamMatchId), determine the two teams and the per-team game points.
    // Then accumulate Sonneborn-Berger per team pair.
    val sbByTeam = HashMap<String, Double>().apply {
        teams.forEach { put(it.id, 0.0) }
    }
    val teamMatchResults = HashMap<String, Pair<String, String>>() // teamMatchId -> (teamA, teamB)

    matchesByTeamMatch.forEach { (tmid, ms) ->
        val first = ms.firstOrNull() ?: return@forEach
        val teamA = playerToTeam[first.whiteId] ?: return@forEach
        val teamB = first.blackId?.let { playerToTeam[it] } ?: return@forEach
        if (teamA == teamB) return@forEach
        var gpA = 0.0
        var gpB = 0.0
        ms.forEach { m ->
            val isAWhite = playerToTeam[m.whiteId] == teamA
            when (m.result) {
                MatchResult.WHITE_WIN -> if (isAWhite) gpA++ else gpB++
                MatchResult.BLACK_WIN -> if (isAWhite) gpB++ else gpA++
                MatchResult.DRAW -> { gpA += 0.5; gpB += 0.5 }
                MatchResult.FORFEIT_WHITE -> if (isAWhite) gpB++ else gpA++
                MatchResult.FORFEIT_BLACK -> if (isAWhite) gpA++ else gpB++
                else -> {}
            }
        }
        val mpA = teamMatchPoints[teamA] ?: 0.0
        val mpB = teamMatchPoints[teamB] ?: 0.0
        if (gpA > gpB) {
            sbByTeam[teamA] = (sbByTeam[teamA] ?: 0.0) + mpB
        } else if (gpA < gpB) {
            sbByTeam[teamB] = (sbByTeam[teamB] ?: 0.0) + mpA
        } else {
            sbByTeam[teamA] = (sbByTeam[teamA] ?: 0.0) + mpB / 2
            sbByTeam[teamB] = (sbByTeam[teamB] ?: 0.0) + mpA / 2
        }
        teamMatchResults[tmid] = teamA to teamB
    }

    val standings = teams.map { team ->
        TeamStanding(
            team = team,
            matchPoints = teamMatchPoints[team.id] ?: 0.0,
            gamePoints = teamGamePoints[team.id] ?: 0.0,
            sonnebornBerger = sbByTeam[team.id] ?: 0.0
        )
    }

    return standings.sortedWith(
        compareByDescending<TeamStanding> { it.matchPoints }
            .thenByDescending { it.gamePoints }
            .thenByDescending { it.sonnebornBerger }
    )
}

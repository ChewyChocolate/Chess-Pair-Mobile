package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*

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

    val standingsResult = players.map { p ->
        val score = data.scores[p.id] ?: 0.0
        val opponents = data.played[p.id]?.toList() ?: emptyList()
        val opponentScores = opponents.map { data.scores[it] ?: 0.0 }

        val buchholz = opponentScores.sum()

        val medianBuchholz = if (opponentScores.size > 2) {
            val sorted = opponentScores.sorted()
            sorted.drop(1).dropLast(1).sum()
        } else buchholz

        var sb = 0.0
        matchesToProcess.forEach { m ->
            if (m.whiteId == p.id) {
                val oppScore = data.scores[m.blackId] ?: 0.0
                when (m.result) {
                    MatchResult.WHITE_WIN, MatchResult.FORFEIT_BLACK -> sb += oppScore
                    MatchResult.DRAW -> sb += oppScore / 2
                    else -> {}
                }
            } else if (m.blackId == p.id) {
                val oppScore = data.scores[m.whiteId] ?: 0.0
                when (m.result) {
                    MatchResult.BLACK_WIN, MatchResult.FORFEIT_WHITE -> sb += oppScore
                    MatchResult.DRAW -> sb += oppScore / 2
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

        PlayerStanding(
            player = p,
            score = score,
            buchholz = buchholz,
            sonnebornBerger = sb,
            medianBuchholz = medianBuchholz,
            mostWins = mostWins,
            directEncounter = 0.0
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
                }
            }
            a.player.name.compareTo(b.player.name)
        }
    )

    return standingsResult
}

package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*

fun getPlayerStats(
    players: List<Player>,
    matches: List<Match>,
    playerId: String
): PlayerStatsResult {
    val stats = mutableListOf<PlayerMatchStat>()
    var consecutiveColors = 0
    var lastColor: Char? = null
    var colorDiff = 0
    var byeCount = 0

    val player = players.find { it.id == playerId }
    val requestedByeRounds = player?.requestedByes?.toSet() ?: emptySet()

    val sortedMatches = matches
        .filter { it.whiteId == playerId || it.blackId == playerId }
        .sortedBy { it.round }

    for (m in sortedMatches) {
        val isWhite = m.whiteId == playerId
        val color: Char? = if (m.result == MatchResult.BYE) null else if (isWhite) 'W' else 'B'
        val opponentId = if (isWhite) m.blackId else m.whiteId
        val opponentName = opponentId?.let { players.find { p -> p.id == it }?.name } ?: "BYE"

        val result: String? = when (m.result) {
            MatchResult.WHITE_WIN -> if (isWhite) "1" else "0"
            MatchResult.BLACK_WIN -> if (isWhite) "0" else "1"
            MatchResult.DRAW -> "0.5"
            MatchResult.BYE -> "1"
            MatchResult.FORFEIT_WHITE -> if (isWhite) "0" else "1"
            MatchResult.FORFEIT_BLACK -> if (isWhite) "1" else "0"
            else -> null
        }

        stats.add(
            PlayerMatchStat(
                round = m.round,
                opponentId = opponentId,
                opponentName = opponentName,
                color = color,
                result = result
            )
        )

        if (color != null) {
            colorDiff += if (color == 'W') 1 else -1
            if (color == lastColor) {
                consecutiveColors++
            } else {
                lastColor = color
                consecutiveColors = 1
            }
        }

        if (m.result == MatchResult.BYE && !requestedByeRounds.contains(m.round)) {
            byeCount++
        }
    }

    return PlayerStatsResult(
        stats = stats,
        consecutiveColors = consecutiveColors,
        lastColor = lastColor,
        colorDiff = colorDiff,
        hasColorWarning = consecutiveColors >= 3,
        byeCount = byeCount,
        hasByeWarning = byeCount > 1
    )
}

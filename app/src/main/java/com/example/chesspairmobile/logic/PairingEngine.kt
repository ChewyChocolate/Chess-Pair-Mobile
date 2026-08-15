package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow

data class ScoreData(
    val scores: MutableMap<String, Double> = mutableMapOf(),
    val played: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val colorBalance: MutableMap<String, Int> = mutableMapOf(),
    val colorSequence: MutableMap<String, MutableList<Char>> = mutableMapOf(),
    val floatHistory: MutableMap<String, MutableList<Int>> = mutableMapOf(),
    val byes: MutableSet<String> = mutableSetOf()
)

fun calculateScores(
    players: List<Player>,
    matches: List<Match>,
    pointsForWin: Double,
    pointsForDraw: Double,
    pointsForLoss: Double,
    upToRound: Int? = null
): ScoreData {
    val data = ScoreData()

    players.forEach { p ->
        data.scores[p.id] = 0.0
        data.played[p.id] = mutableSetOf()
        data.colorBalance[p.id] = 0
        data.colorSequence[p.id] = mutableListOf()
        data.floatHistory[p.id] = mutableListOf()
        p.requestedByes.forEach { byeRound ->
            if (upToRound == null || byeRound <= upToRound) {
                data.scores[p.id] = (data.scores[p.id] ?: 0.0) + pointsForDraw
            }
        }
    }

    val matchesToProcess = if (upToRound != null) matches.filter { it.round <= upToRound } else matches
    val sortedMatches = matchesToProcess.sortedBy { it.round }

    val roundScores = mutableMapOf<String, Double>()
    players.forEach { p ->
        roundScores[p.id] = 0.0
    }

    sortedMatches.forEach { m ->
        if (m.result == null) return@forEach

        if (m.whiteId != null && m.blackId != null) {
            data.played[m.whiteId]?.add(m.blackId)
            data.played[m.blackId]?.add(m.whiteId)

            data.colorBalance[m.whiteId] = (data.colorBalance[m.whiteId] ?: 0) + 1
            data.colorBalance[m.blackId] = (data.colorBalance[m.blackId] ?: 0) - 1
            data.colorSequence[m.whiteId]?.add('W')
            data.colorSequence[m.blackId]?.add('B')

            val whiteScore = roundScores[m.whiteId] ?: 0.0
            val blackScore = roundScores[m.blackId] ?: 0.0
            if (whiteScore > blackScore) {
                data.floatHistory[m.whiteId]?.add(-1)
                data.floatHistory[m.blackId]?.add(1)
            } else if (whiteScore < blackScore) {
                data.floatHistory[m.whiteId]?.add(1)
                data.floatHistory[m.blackId]?.add(-1)
            } else {
                data.floatHistory[m.whiteId]?.add(0)
                data.floatHistory[m.blackId]?.add(0)
            }

            when (m.result) {
                MatchResult.WHITE_WIN -> {
                    data.scores[m.whiteId] = (data.scores[m.whiteId] ?: 0.0) + pointsForWin
                    data.scores[m.blackId] = (data.scores[m.blackId] ?: 0.0) + pointsForLoss
                    roundScores[m.whiteId] = (roundScores[m.whiteId] ?: 0.0) + pointsForWin
                    roundScores[m.blackId] = (roundScores[m.blackId] ?: 0.0) + pointsForLoss
                }
                MatchResult.BLACK_WIN -> {
                    data.scores[m.blackId] = (data.scores[m.blackId] ?: 0.0) + pointsForWin
                    data.scores[m.whiteId] = (data.scores[m.whiteId] ?: 0.0) + pointsForLoss
                    roundScores[m.blackId] = (roundScores[m.blackId] ?: 0.0) + pointsForWin
                    roundScores[m.whiteId] = (roundScores[m.whiteId] ?: 0.0) + pointsForLoss
                }
                MatchResult.DRAW -> {
                    data.scores[m.whiteId] = (data.scores[m.whiteId] ?: 0.0) + pointsForDraw
                    data.scores[m.blackId] = (data.scores[m.blackId] ?: 0.0) + pointsForDraw
                    roundScores[m.whiteId] = (roundScores[m.whiteId] ?: 0.0) + pointsForDraw
                    roundScores[m.blackId] = (roundScores[m.blackId] ?: 0.0) + pointsForDraw
                }
                MatchResult.FORFEIT_WHITE -> {
                    data.scores[m.blackId] = (data.scores[m.blackId] ?: 0.0) + pointsForWin
                    data.scores[m.whiteId] = (data.scores[m.whiteId] ?: 0.0) + pointsForLoss
                    roundScores[m.blackId] = (roundScores[m.blackId] ?: 0.0) + pointsForWin
                    roundScores[m.whiteId] = (roundScores[m.whiteId] ?: 0.0) + pointsForLoss
                }
                MatchResult.FORFEIT_BLACK -> {
                    data.scores[m.whiteId] = (data.scores[m.whiteId] ?: 0.0) + pointsForWin
                    data.scores[m.blackId] = (data.scores[m.blackId] ?: 0.0) + pointsForLoss
                    roundScores[m.whiteId] = (roundScores[m.whiteId] ?: 0.0) + pointsForWin
                    roundScores[m.blackId] = (roundScores[m.blackId] ?: 0.0) + pointsForLoss
                }
                MatchResult.BYE -> { /* handled separately */ }
            }
        } else if (m.whiteId != null && m.result == MatchResult.BYE) {
            data.scores[m.whiteId] = (data.scores[m.whiteId] ?: 0.0) + pointsForWin
            roundScores[m.whiteId] = (roundScores[m.whiteId] ?: 0.0) + pointsForWin
            data.byes.add(m.whiteId)
        } else if (m.blackId != null && m.result == MatchResult.BYE) {
            data.scores[m.blackId] = (data.scores[m.blackId] ?: 0.0) + pointsForWin
            roundScores[m.blackId] = (roundScores[m.blackId] ?: 0.0) + pointsForWin
            data.byes.add(m.blackId)
        }
    }

    players.forEach { p ->
        if (p.penaltyPoints > 0) {
            data.scores[p.id] = (data.scores[p.id] ?: 0.0) - p.penaltyPoints
        }
    }

    return data
}

fun checkColorSequence(seq: List<Char>, newColor: Char): Boolean {
    if (seq.size < 2) return true
    val last2 = seq.takeLast(2)
    return !(last2[0] == newColor && last2[1] == newColor)
}

private const val DUMMY_ID = "__DUMMY__"

private fun isSameClub(a: Player, b: Player): Boolean =
    !a.club.isNullOrEmpty() && !b.club.isNullOrEmpty() && a.club == b.club

fun generateRoundRobin(
    players: List<Player>,
    round: Int,
    avoidClubPairings: Boolean = false
): List<Match> {
    // Deterministic ordering by pairing number / rating so the Berger table is stable
    val seedComparator = compareByDescending<Player> {
        if (it.pairingNumber > 0) Int.MAX_VALUE - it.pairingNumber else it.rating ?: 0
    }.thenBy { it.name }

    val activePlayers = players.filter { !it.withdrawn && !it.requestedByes.contains(round) }
        .sortedWith(seedComparator)
    val matches = mutableListOf<Match>()
    val n = activePlayers.size
    if (n < 2) return emptyList()
    val isOdd = n % 2 != 0

    val dummy = Player(id = DUMMY_ID, name = "Bye")
    val workingPlayers = if (isOdd) activePlayers + dummy else activePlayers
    val totalPlayers = workingPlayers.size

    val p = workingPlayers.toMutableList()
    for (i in 1 until round) {
        val last = p.removeAt(p.lastIndex)
        p.add(1, last)
    }

    // Build initial Berger pairs (ignoring clubs) and collect dummy BYEs
    val byeMatches = mutableListOf<Match>()
    val initialPairs = mutableListOf<Pair<Player, Player>>()

    for (i in 0 until totalPlayers / 2) {
        val p1 = p[i]
        val p2 = p[totalPlayers - 1 - i]
        if (p1.id == DUMMY_ID || p2.id == DUMMY_ID) {
            val realPlayer = if (p1.id == DUMMY_ID) p2 else p1
            byeMatches.add(
                Match(
                    round = round,
                    whiteId = realPlayer.id,
                    blackId = null,
                    result = MatchResult.BYE,
                    boardNumber = 999
                )
            )
        } else {
            initialPairs.add(p1 to p2)
        }
    }

    if (!avoidClubPairings) {
        // No club restriction – emit Berger pairs directly
        initialPairs.forEachIndexed { idx, (a, b) ->
            val isEvenRound = round % 2 == 0
            val white = if (isEvenRound) b else a
            val black = if (isEvenRound) a else b
            matches.add(
                Match(
                    round = round,
                    whiteId = white.id,
                    blackId = black.id,
                    boardNumber = idx + 1
                )
            )
        }
        matches.addAll(byeMatches)
        return matches
    }

    // Club avoidance – try to resolve same-club pairs via swaps before resorting to BYEs
    val pairs = initialPairs.toMutableList()
    var iter = 0
    while (iter < 10) {
        val conflictIdx = pairs.indexOfFirst { (a, b) -> isSameClub(a, b) }
        if (conflictIdx == -1) break
        val (a, b) = pairs[conflictIdx]
        var swapped = false
        for (j in pairs.indices) {
            if (j == conflictIdx) continue
            val (c, d) = pairs[j]
            // Try swap b with c -> (a,c) & (b,d)
            if (!isSameClub(a, c) && !isSameClub(b, d)) {
                pairs[conflictIdx] = a to c
                pairs[j] = b to d
                swapped = true
                break
            }
            // Try swap b with d -> (a,d) & (b,c)
            if (!isSameClub(a, d) && !isSameClub(b, c)) {
                pairs[conflictIdx] = a to d
                pairs[j] = b to c
                swapped = true
                break
            }
        }
        if (!swapped) {
            // No swap can resolve – give BYEs to both players of the conflicted pair
            // (covers the orphan bug: previously only p1 got a BYE, p2 was dropped)
            byeMatches.add(
                Match(round = round, whiteId = a.id, blackId = null, result = MatchResult.BYE, boardNumber = 999)
            )
            byeMatches.add(
                Match(round = round, whiteId = b.id, blackId = null, result = MatchResult.BYE, boardNumber = 999)
            )
            pairs.removeAt(conflictIdx)
        }
        iter++
    }

    pairs.forEachIndexed { idx, (a, b) ->
        val isEvenRound = round % 2 == 0
        val white = if (isEvenRound) b else a
        val black = if (isEvenRound) a else b
        matches.add(
            Match(
                round = round,
                whiteId = white.id,
                blackId = black.id,
                boardNumber = idx + 1
            )
        )
    }
    matches.addAll(byeMatches)
    return matches
}

fun generateKnockout(players: List<Player>, matches: List<Match>, round: Int): List<Match> {
    if (round == 1) {
        val activePlayers = players.filter { it.active && !it.withdrawn && !it.requestedByes.contains(round) }
        val n = activePlayers.size
        if (n < 2) return emptyList()
        val nextPowerOfTwo = 2.0.pow(ceil(log2(n.toDouble()))).toInt()
        val byesCount = nextPowerOfTwo - n

        val shuffled = activePlayers.shuffled().toMutableList()
        val resultMatches = mutableListOf<Match>()
        var boardNumber = 1

        val byePlayers = shuffled.take(byesCount)
        shuffled.removeAll(byePlayers)
        byePlayers.forEach { p ->
            resultMatches.add(
                Match(round = 1, whiteId = p.id, blackId = null, result = MatchResult.BYE, boardNumber = 999)
            )
        }

        for (i in shuffled.indices step 2) {
            resultMatches.add(
                Match(
                    round = 1,
                    whiteId = shuffled[i].id,
                    blackId = shuffled.getOrNull(i + 1)?.id,
                    boardNumber = boardNumber++
                )
            )
        }
        return resultMatches
    }

    val prevRoundMatches = matches.filter { it.round == round - 1 }
    val winners = mutableListOf<String>()

    prevRoundMatches.forEach { m ->
        when (m.result) {
            MatchResult.WHITE_WIN, MatchResult.FORFEIT_BLACK -> m.whiteId?.let { winners.add(it) }
            MatchResult.BLACK_WIN, MatchResult.FORFEIT_WHITE -> m.blackId?.let { winners.add(it) }
            MatchResult.BYE -> m.whiteId?.let { winners.add(it) }
            // Draw in knockout: must be resolved by user before advancing. Skip silently.
            MatchResult.DRAW, null -> {}
        }
    }

    val resultMatches = mutableListOf<Match>()
    var boardNumber = 1
    for (i in winners.indices step 2) {
        val hasOpponent = i + 1 < winners.size
        resultMatches.add(
            Match(
                round = round,
                whiteId = winners[i],
                blackId = if (hasOpponent) winners[i + 1] else null,
                result = if (hasOpponent) null else MatchResult.BYE,
                boardNumber = if (hasOpponent) boardNumber++ else 999
            )
        )
    }
    return resultMatches
}

fun calculateTeamScores(
    teams: List<Team>,
    matches: List<Match>,
    upToRound: Int? = null
): Pair<MutableMap<String, Double>, MutableMap<String, Double>> {
    // Returns (teamMatchPoints, teamGamePoints)
    val teamMatchPoints = mutableMapOf<String, Double>()
    val teamGamePoints = mutableMapOf<String, Double>()

    teams.forEach { t ->
        teamMatchPoints[t.id] = 0.0
        teamGamePoints[t.id] = 0.0
    }

    val matchesToProcess = if (upToRound != null) matches.filter { it.round <= upToRound } else matches

    val teamMatchesByRound = mutableMapOf<Int, MutableMap<String, MutableList<Match>>>()
    matchesToProcess.forEach { m ->
        if (m.teamMatchId == null || m.result == null) return@forEach
        teamMatchesByRound
            .getOrPut(m.round) { mutableMapOf() }
            .getOrPut(m.teamMatchId) { mutableListOf() }
            .add(m)
    }

    teamMatchesByRound.values.forEach { roundMatches ->
        roundMatches.values.forEach { teamMatches ->
            val firstMatch = teamMatches.firstOrNull() ?: return@forEach
            var teamA: String? = null
            var teamB: String? = null
            var isBye = false

            if (firstMatch.result == MatchResult.BYE) {
                isBye = true
                teamA = teams.find { it.playerIds.contains(firstMatch.whiteId) }?.id
            } else {
                teamA = teams.find { it.playerIds.contains(firstMatch.whiteId!!) }?.id
                teamB = firstMatch.blackId?.let { bid -> teams.find { it.playerIds.contains(bid) }?.id }
            }

            if (isBye && teamA != null) {
                teamMatchPoints[teamA] = (teamMatchPoints[teamA] ?: 0.0) + 2.0
                return@forEach
            }

            if (teamA != null && teamB != null) {
                var teamAGP = 0.0
                var teamBGP = 0.0
                teamMatches.forEach { m ->
                    val isTeamAWhite = teams.find { it.id == teamA }?.playerIds?.contains(m.whiteId) == true
                    when (m.result) {
                        MatchResult.WHITE_WIN -> if (isTeamAWhite) teamAGP++ else teamBGP++
                        MatchResult.BLACK_WIN -> if (isTeamAWhite) teamBGP++ else teamAGP++
                        MatchResult.DRAW -> { teamAGP += 0.5; teamBGP += 0.5 }
                        MatchResult.FORFEIT_WHITE -> if (isTeamAWhite) teamBGP++ else teamAGP++
                        MatchResult.FORFEIT_BLACK -> if (isTeamAWhite) teamAGP++ else teamBGP++
                        MatchResult.BYE -> {}
                        null -> {}
                    }
                }
                teamGamePoints[teamA] = (teamGamePoints[teamA] ?: 0.0) + teamAGP
                teamGamePoints[teamB] = (teamGamePoints[teamB] ?: 0.0) + teamBGP

                if (teamAGP > teamBGP) teamMatchPoints[teamA] = (teamMatchPoints[teamA] ?: 0.0) + 2.0
                else if (teamBGP > teamAGP) teamMatchPoints[teamB] = (teamMatchPoints[teamB] ?: 0.0) + 2.0
                else {
                    teamMatchPoints[teamA] = (teamMatchPoints[teamA] ?: 0.0) + 1.0
                    teamMatchPoints[teamB] = (teamMatchPoints[teamB] ?: 0.0) + 1.0
                }
            }
        }
    }

    return teamMatchPoints to teamGamePoints
}

// ---- Acceleration ----

fun applyAcceleration(players: List<Player>, mode: AccelerationMode): List<Player> {
    if (players.size < 4) return players

    return when (mode) {
        AccelerationMode.BAKU -> {
            // Split into 4 groups by rating/seed
            val half = players.size / 2
            val quarter = players.size / 4
            val top = players
            val g1 = top.subList(0, quarter)            // top quarter
            val g2 = top.subList(quarter, half)         // second quarter
            val g3 = top.subList(half, half + quarter)   // third quarter
            val g4 = top.subList(half + quarter, top.size) // bottom quarter

            // G1 vs G2 interleaved, G3 vs G4 interleaved
            val a = mutableListOf<Player>()
            for (i in 0 until maxOf(g1.size, g2.size)) {
                g1.getOrNull(i)?.let { a.add(it) }
                g2.getOrNull(i)?.let { a.add(it) }
            }
            val b = mutableListOf<Player>()
            for (i in 0 until maxOf(g3.size, g4.size)) {
                g3.getOrNull(i)?.let { b.add(it) }
                g4.getOrNull(i)?.let { b.add(it) }
            }
            a + b
        }
        AccelerationMode.LIM -> {
            // Lim: top quarter plays bottom quarter of top half, middle plays middle
            val half = players.size / 2
            val quarter = players.size / 4
            val g1 = players.subList(0, quarter)
            val g2 = players.subList(quarter, half)
            val g3 = players.subList(half, half + quarter)
            val g4 = players.subList(half + quarter, players.size)

            val upper = mutableListOf<Player>()
            for (i in 0 until maxOf(g1.size, g3.size)) {
                g1.getOrNull(i)?.let { upper.add(it) }
                g3.getOrNull(i)?.let { upper.add(it) }
            }
            val lower = mutableListOf<Player>()
            for (i in 0 until maxOf(g2.size, g4.size)) {
                g2.getOrNull(i)?.let { lower.add(it) }
                g4.getOrNull(i)?.let { lower.add(it) }
            }
            upper + lower
        }
        AccelerationMode.NONE -> players
    }
}

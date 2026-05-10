package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min

/**
 * FIDE Dutch System pairings (C.04).
 * This engine is used when the tournament type is SWISS and
 * the tournament is not a team event.
 *
 * Implements:
 *  - Score brackets with S1/S2 splitting
 *  - Max-pair (matching within brackets)
 *  - Color allocation with absolute color preferences
 *  - Float (downfloat/upfloat) tracking
 *  - Acceleration-aware round 1
 */

fun generateFideDutch(
    players: List<Player>,
    matches: List<Match>,
    round: Int,
    totalRounds: Int,
    pointsForWin: Double,
    pointsForDraw: Double,
    pointsForLoss: Double,
    avoidClubPairings: Boolean,
    forcedPairings: List<ForcedPairing>,
    accelerationMode: AccelerationMode = AccelerationMode.NONE
): List<Match> {
    val data = calculateScores(players, matches, pointsForWin, pointsForDraw, pointsForLoss)
    val resultMatches = mutableListOf<Match>()
    var boardNumber = 1
    val pairedSet = mutableSetOf<String>()

    // Process existing round matches and forced pairings first
    val existing = matches.filter { it.round == round }
    if (existing.isNotEmpty()) {
        boardNumber = (existing.maxOfOrNull { it.boardNumber ?: 0 } ?: 0) + 1
        existing.forEach { m ->
            m.whiteId?.let { pairedSet.add(it) }
            m.blackId?.let { pairedSet.add(it) }
        }
    }

    forcedPairings.forEach { fp ->
        resultMatches.add(Match(round = round, whiteId = fp.whiteId, blackId = fp.blackId, boardNumber = boardNumber++))
        pairedSet.add(fp.whiteId)
        pairedSet.add(fp.blackId)
    }

    // Build available player pool
    val compareBySeed = compareByDescending<Player> { data.scores[it.id] ?: 0.0 }
        .thenByDescending {
            if (it.pairingNumber > 0) Int.MAX_VALUE - it.pairingNumber
            else it.rating ?: 0
        }

    var available = players
        .filter { p -> p.active && !p.withdrawn && !p.requestedByes.contains(round) && p.id !in pairedSet }
        .sortedWith(compareBySeed)

    if (available.isEmpty()) return resultMatches

    // Acceleration for round 1
    if (round == 1 && accelerationMode != AccelerationMode.NONE && available.size >= 4) {
        available = applyAcceleration(available, accelerationMode)
    }

    // Handle odd number — assign bye to lowest-score, non-byed player
    if (available.size % 2 != 0) {
        val byeIdx = available.indices.lastOrNull { available[it].id !in data.byes } ?: available.size - 1
        val byePlayer = available[byeIdx]
        resultMatches.add(Match(round = round, whiteId = byePlayer.id, blackId = null, result = MatchResult.BYE, boardNumber = 999))
        pairedSet.add(byePlayer.id)
        available = available.toMutableList().also { it.removeAt(byeIdx) }
    }

    if (available.isEmpty()) return resultMatches

    // ---- FIDE Dutch Pairing ----
    // Group by score into brackets
    val scoreBrackets = LinkedHashMap<Double, MutableList<Player>>()
    available.forEach { p ->
        val s = data.scores[p.id] ?: 0.0
        scoreBrackets.getOrPut(s) { mutableListOf() }.add(p)
    }

    val sortedScores = scoreBrackets.keys.sortedDescending()
    var leftover = mutableListOf<Player>() // downfloats from previous bracket

    for (i in sortedScores.indices) {
        val score = sortedScores[i]
        val bracket = scoreBrackets[score]?.toMutableList() ?: mutableListOf()
        val isLastBracket = i == sortedScores.size - 1

        // Add leftover downfloats to this bracket (heterogeneous bracket)
        if (leftover.isNotEmpty()) {
            bracket.addAll(0, leftover) // downfloats top the bracket
            leftover.clear()
        }

        // If bracket is empty, skip
        if (bracket.isEmpty()) continue

        // Split bracket into S1 (top half) and S2 (bottom half)
        val m0 = bracket.size
        val s1Size = (m0 + 1) / 2 // ceil(m/2)
        val s1 = bracket.take(s1Size).toMutableList()
        val s2 = bracket.drop(s1Size).toMutableList()

        // Pair S1 players, processing weakest first (reverse order) for better max-pair behavior
        val pairedBracket = mutableListOf<Match>()
        val usedFromS1 = mutableSetOf<String>()
        val usedFromS2 = mutableSetOf<String>()

        for (p1 in s1.asReversed()) {
            var bestJ = -1
            var bestScore = Int.MIN_VALUE

            for (j in s2.indices) {
                val p2 = s2[j]
                if (p2.id in usedFromS2) continue

                val score = pairQuality(p1, p2, data, avoidClubPairings)
                if (score > bestScore) {
                    bestScore = score
                    bestJ = j
                }
            }

            if (bestJ >= 0) {
                val p2 = s2[bestJ]
                val colors = assignColors(p1, p2, data)
                val (whiteId, blackId) = if (colors.first == 'W') p1.id to p2.id else p2.id to p1.id
                pairedBracket.add(Match(round = round, whiteId = whiteId, blackId = blackId, boardNumber = 0))
                usedFromS1.add(p1.id)
                usedFromS2.add(p2.id)
            }
        }

        // Handle unpaired players in current bracket
        s1.filter { it.id !in usedFromS1 }.forEach { leftover.add(it) }
        s2.filter { it.id !in usedFromS2 }.forEach { leftover.add(it) }
        leftover.sortWith(compareBySeed)

        // Add newly paired matches
        pairedBracket.forEach { m ->
            resultMatches.add(m.copy(boardNumber = boardNumber++))
        }

        // If last bracket and leftover has > 1 player, pair greedily
        if (isLastBracket && leftover.size > 1) {
            while (leftover.size > 1) {
                val p1 = leftover[0]
                var p2Idx = -1
                for (j in 1 until leftover.size) {
                    if (data.played[p1.id]?.contains(leftover[j].id) != true) { p2Idx = j; break }
                }
                if (p2Idx < 0) {
                    // No unplayed opponent available — move p1 to end and try next
                    leftover.add(leftover.removeAt(0))
                    continue
                }
                val p2 = leftover[p2Idx]
                val colors = assignColors(p1, p2, data)
                val (wid, bid) = if (colors.first == 'W') p1.id to p2.id else p2.id to p1.id
                resultMatches.add(Match(round = round, whiteId = wid, blackId = bid, boardNumber = boardNumber++))
                leftover.removeAt(p2Idx)
                leftover.removeAt(0)
            }
        }
    }

    // If exactly 1 player left unpaired after all brackets, assign a bye
    if (leftover.size == 1) {
        val byePlayer = leftover[0]
        resultMatches.add(Match(round = round, whiteId = byePlayer.id, blackId = null, result = MatchResult.BYE, boardNumber = 999))
    }

    return resultMatches
}

/** Assign colors to a pairing. Returns (p1Color, p2Color). */
fun assignColors(p1: Player, p2: Player, data: ScoreData): Pair<Char, Char> {
    val bal1 = data.colorBalance[p1.id] ?: 0
    val bal2 = data.colorBalance[p2.id] ?: 0
    val seq1 = data.colorSequence[p1.id] ?: emptyList()
    val seq2 = data.colorSequence[p2.id] ?: emptyList()

    // Absolute color preference: a player due white (bal < 0, last was B) MUST get white
    val p1DueWhite = bal1 < -1 || (bal1 < 0 && seq1.lastOrNull() == 'B')
    val p2DueWhite = bal2 < -1 || (bal2 < 0 && seq2.lastOrNull() == 'B')
    val p1DueBlack = bal1 > 1 || (bal1 > 0 && seq1.lastOrNull() == 'W')
    val p2DueBlack = bal2 > 1 || (bal2 > 0 && seq2.lastOrNull() == 'W')

    // Strong color preference (balance non-zero, last was opposite)
    val p1StrongWhite = bal1 < 0 && seq1.lastOrNull() != 'W'
    val p2StrongWhite = bal2 < 0 && seq2.lastOrNull() != 'W'
    val p1StrongBlack = bal1 > 0 && seq1.lastOrNull() != 'B'
    val p2StrongBlack = bal2 > 0 && seq2.lastOrNull() != 'B'

    // Option 1: p1 white, p2 black
    val option1Valid = !(p1DueBlack || p2DueWhite)

    // Option 2: p1 black, p2 white
    val option2Valid = !(p1DueWhite || p2DueBlack)

    // Pick the better option
    return when {
        // Both valid: choose based on stronger preference
        option1Valid && option2Valid -> {
            val opt1Score = (if (p1StrongWhite) 2 else 0) + (if (p2StrongBlack) 2 else 0) + (if (!p1StrongWhite && !p1StrongBlack) (if (bal1 < 0) 1 else 0) else 0) + (if (!p2StrongWhite && !p2StrongBlack) (if (bal2 > 0) 1 else 0) else 0)
            val opt2Score = (if (p1StrongBlack) 2 else 0) + (if (p2StrongWhite) 2 else 0) + (if (!p1StrongWhite && !p1StrongBlack) (if (bal1 > 0) 1 else 0) else 0) + (if (!p2StrongWhite && !p2StrongBlack) (if (bal2 < 0) 1 else 0) else 0)
            if (opt1Score >= opt2Score) 'W' to 'B' else 'B' to 'W'
        }
        option1Valid -> 'W' to 'B'
        option2Valid -> 'B' to 'W'
        // Neither valid (constraint violation): minimize damage
        !p1DueBlack && !p2DueWhite -> 'W' to 'B'
        !p1DueWhite && !p2DueBlack -> 'B' to 'W'
        bal1 <= bal2 -> 'B' to 'W' // p1 needs black more
        else -> 'W' to 'B'
    }
}

/** Score how good a pairing is. Higher = better. Returns -1 if invalid. */
fun pairQuality(p1: Player, p2: Player, data: ScoreData, avoidClubPairings: Boolean): Int {
    // Cannot play same opponent twice
    if (data.played[p1.id]?.contains(p2.id) == true) return -1

    // Club avoidance
    if (avoidClubPairings && !p1.club.isNullOrEmpty() && !p2.club.isNullOrEmpty() && p1.club == p2.club) return -1

    // Check if at least one color assignment is possible
    val bal1 = data.colorBalance[p1.id] ?: 0
    val bal2 = data.colorBalance[p2.id] ?: 0
    val seq1 = data.colorSequence[p1.id] ?: emptyList()
    val seq2 = data.colorSequence[p2.id] ?: emptyList()

    val p1DueWhite = bal1 < -1 || (bal1 < 0 && seq1.lastOrNull() == 'B')
    val p2DueWhite = bal2 < -1 || (bal2 < 0 && seq2.lastOrNull() == 'B')
    val p1DueBlack = bal1 > 1 || (bal1 > 0 && seq1.lastOrNull() == 'W')
    val p2DueBlack = bal2 > 1 || (bal2 > 0 && seq2.lastOrNull() == 'W')

    val option1 = !p1DueBlack && !p2DueWhite
    val option2 = !p1DueWhite && !p2DueBlack
    if (!option1 && !option2) return -1

    // Quality score: prefer pairing with equal scores (cross-score pairings are necessary evil)
    var score = 100
    val p1Score = data.scores[p1.id] ?: 0.0
    val p2Score = data.scores[p2.id] ?: 0.0
    if (abs(p1Score - p2Score) < 0.001) score += 50

    // Prefer pairing players closer in rating/seed (FIDE principle)
    val seed1 = if (p1.pairingNumber > 0) Int.MAX_VALUE - p1.pairingNumber else p1.rating ?: 0
    val seed2 = if (p2.pairingNumber > 0) Int.MAX_VALUE - p2.pairingNumber else p2.rating ?: 0
    score -= abs(seed1 - seed2) / 10

    return score
}

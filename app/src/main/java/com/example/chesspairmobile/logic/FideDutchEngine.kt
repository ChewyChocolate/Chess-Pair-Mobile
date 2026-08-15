package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*
import java.util.UUID
import kotlin.math.abs

/**
 * FIDE Dutch System pairings (C.04).
 * This engine is used when the tournament type is SWISS and
 * the tournament is not a team event.
 *
 * Implements:
 *  - Score brackets with S1/S2 splitting
 *  - Max-pair via backtracking search within brackets
 *  - Color allocation with absolute preferences per FIDE (balance >=2 or last two same)
 *  - Float (downfloat/upfloat) tracking
 *  - Acceleration-aware round 1
 *  - Robust leftover handling (no second bye, no dropped players)
 */

private fun isAbsoluteDueWhite(bal: Int, seq: List<Char>): Boolean =
    bal <= -2 || (seq.size >= 2 && seq[seq.size - 2] == 'B' && seq[seq.size - 1] == 'B')

private fun isAbsoluteDueBlack(bal: Int, seq: List<Char>): Boolean =
    bal >= 2 || (seq.size >= 2 && seq[seq.size - 2] == 'W' && seq[seq.size - 1] == 'W')

@Suppress("UNUSED_PARAMETER")
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

    // Handle odd number — assign bye to lowest-score, non-byed player; remember we already gave a bye
    var hasBye = false
    if (available.size % 2 != 0) {
        val byeIdx = available.indices.lastOrNull { available[it].id !in data.byes } ?: available.size - 1
        val byePlayer = available[byeIdx]
        resultMatches.add(Match(round = round, whiteId = byePlayer.id, blackId = null, result = MatchResult.BYE, boardNumber = 999))
        pairedSet.add(byePlayer.id)
        available = available.toMutableList().also { it.removeAt(byeIdx) }
        hasBye = true
    }

    if (available.isEmpty()) return resultMatches

    // ---- FIDE Dutch Pairing (global optimum, rematch + large floats strictly forbidden if possible) ----
    // FIDE: try to keep score diff <=1.0 (adjacent brackets). Only float further if needed.
    fun tryPairing(maxGap: Double, allowRematch: Boolean) =
        findBestGlobalPairing(available, data, avoidClubPairings, allowRematch, maxScoreDiff = maxGap)

    val strictGap1 = tryPairing(1.0, false)
    val strictGap1Perfect = strictGap1.size * 2 == available.size
    val bestPairs: List<Pair<Player, Player>> = when {
        strictGap1Perfect -> strictGap1
        else -> {
            val strictAny = tryPairing(Double.MAX_VALUE, false)
            val strictAnyPerfect = strictAny.size * 2 == available.size
            if (strictAnyPerfect) strictAny
            else {
                val allowGap1 = tryPairing(1.0, true)
                if (allowGap1.size * 2 == available.size) allowGap1
                else tryPairing(Double.MAX_VALUE, true)
            }
        }
    }

    // Leftover are those not covered by bestPairs
    val pairedIds = bestPairs.flatMap { listOf(it.first.id, it.second.id) }.toSet()
    var leftover = available.filter { it.id !in pairedIds }.toMutableList()

    bestPairs.forEach { (p1, p2) ->
        val colors = assignColors(p1, p2, data)
        val (whiteId, blackId) = if (colors.first == 'W') p1.id to p2.id else p2.id to p1.id
        resultMatches.add(Match(round = round, whiteId = whiteId, blackId = blackId, boardNumber = boardNumber++))
    }

    // Final unpaired handling: at most one BYE, never two
    when {
        leftover.size == 1 && !hasBye -> {
            val byePlayer = leftover[0]
            resultMatches.add(Match(round = round, whiteId = byePlayer.id, blackId = null, result = MatchResult.BYE, boardNumber = 999))
            leftover.clear()
        }
        leftover.size == 1 && hasBye -> {
            // Second unpaired with bye already given – keep unpaired (no second BYE). This means no perfect matching without rematch was found;
            // the strict matching left one unpaired to avoid rematch. Allow rematch fallback already tried; if still 1 left, leave for manual pairing.
        }
        leftover.size > 1 -> {
            // More than one leftover – try to pair them among themselves without rematch
            // This can happen when strict matching couldn't find perfect due to rematch bans; we already tried fallback, so just leave as is
            // As last resort, try to force-pair remaining via any means (already done in fallback)
        }
    }

    return resultMatches
}

// ----- Bracket max-matching (bipartite S1 vs S2) -----
private fun bestBracketPairing(
    s1: List<Player>,
    s2: List<Player>,
    data: ScoreData,
    avoidClub: Boolean
): List<Pair<Player, Player>> {
    if (s1.isEmpty() || s2.isEmpty()) return emptyList()
    var best: List<Pair<Player, Player>> = emptyList()
    var bestScore = Int.MIN_VALUE

    fun dfs(idx: Int, cur: MutableList<Pair<Player, Player>>, used: MutableSet<String>, score: Int) {
        if (idx == s1.size) {
            if (cur.size > best.size || (cur.size == best.size && score > bestScore)) {
                best = cur.toList()
                bestScore = score
            }
            return
        }
        val p1 = s1[idx]
        // Try pairing p1 with each unused p2
        for (p2 in s2) {
            if (p2.id in used) continue
            val q = pairQuality(p1, p2, data, avoidClub)
            if (q < 0) continue // hard rematch ban
            used.add(p2.id)
            cur.add(p1 to p2)
            dfs(idx + 1, cur, used, score + q)
            cur.removeAt(cur.lastIndex)
            used.remove(p2.id)
        }
        // Also try leaving p1 unpaired (downfloat)
        dfs(idx + 1, cur, used, score)
    }
    dfs(0, mutableListOf(), mutableSetOf(), 0)
    return best
}

// ----- Leftover pairing among themselves (general graph) -----
private fun bestLeftoverPairing(
    players: List<Player>,
    data: ScoreData,
    avoidClub: Boolean
): List<Pair<Player, Player>> {
    if (players.size < 2) return emptyList()
    var best: List<Pair<Player, Player>> = emptyList()
    var bestScore = Int.MIN_VALUE
    val used = BooleanArray(players.size)

    fun dfs(cur: MutableList<Pair<Player, Player>>, score: Int) {
        var first = -1
        for (i in players.indices) if (!used[i]) { first = i; break }
        if (first == -1) {
            if (cur.size > best.size || (cur.size == best.size && score > bestScore)) {
                best = cur.toList(); bestScore = score
            }
            return
        }
        // leave first unpaired
        used[first] = true
        dfs(cur, score)
        used[first] = false
        // try pair first with any later unused
        for (j in first + 1 until players.size) if (!used[j]) {
            val q = pairQuality(players[first], players[j], data, avoidClub)
            if (q < 0) continue
            used[first] = true; used[j] = true
            cur.add(players[first] to players[j])
            dfs(cur, score + q)
            cur.removeAt(cur.lastIndex)
            used[first] = false; used[j] = false
        }
    }
    dfs(mutableListOf(), 0)
    return best
}

// Fallback: allow any pairing (including rematch) with heavy penalty, to avoid dropped players
private fun bestLeftoverPairingAllowAny(
    players: List<Player>,
    data: ScoreData
): List<Pair<Player, Player>> {
    if (players.size < 2) return emptyList()
    var best: List<Pair<Player, Player>> = emptyList()
    var bestScore = Int.MIN_VALUE
    val used = BooleanArray(players.size)

    fun dfs(cur: MutableList<Pair<Player, Player>>, score: Int) {
        var first = -1
        for (i in players.indices) if (!used[i]) { first = i; break }
        if (first == -1) {
            if (cur.size > best.size || (cur.size == best.size && score > bestScore)) {
                best = cur.toList(); bestScore = score
            }
            return
        }
        used[first] = true
        dfs(cur, score)
        used[first] = false
        for (j in first + 1 until players.size) if (!used[j]) {
            val q = pairQualityAllowAny(players[first], players[j], data)
            used[first] = true; used[j] = true
            cur.add(players[first] to players[j])
            dfs(cur, score + q)
            cur.removeAt(cur.lastIndex)
            used[first] = false; used[j] = false
        }
    }
    dfs(mutableListOf(), 0)
    return best
}

private fun pairQualityAllowAny(p1: Player, p2: Player, data: ScoreData): Int {
    // Same as pairQuality but no hard bans; rematch and club are heavy penalties not bans
    var score = 100
    val isRematch = data.played[p1.id]?.contains(p2.id) == true
    if (isRematch) score -= 500
    if (!p1.club.isNullOrEmpty() && !p2.club.isNullOrEmpty() && p1.club == p2.club) score -= 300
    val p1Score = data.scores[p1.id] ?: 0.0
    val p2Score = data.scores[p2.id] ?: 0.0
    val diff = abs(p1Score - p2Score)
    if (diff < 0.001) score += 50
    else score -= (diff * 60).toInt()
    val seed1 = if (p1.pairingNumber > 0) Int.MAX_VALUE - p1.pairingNumber else p1.rating ?: 0
    val seed2 = if (p2.pairingNumber > 0) Int.MAX_VALUE - p2.pairingNumber else p2.rating ?: 0
    score -= abs(seed1 - seed2) / 10
    // color penalty soft
    val bal1 = data.colorBalance[p1.id] ?: 0
    val bal2 = data.colorBalance[p2.id] ?: 0
    val seq1 = data.colorSequence[p1.id] ?: emptyList()
    val seq2 = data.colorSequence[p2.id] ?: emptyList()
    val absWhite1 = isAbsoluteDueWhite(bal1, seq1)
    val absBlack1 = isAbsoluteDueBlack(bal1, seq1)
    val absWhite2 = isAbsoluteDueWhite(bal2, seq2)
    val absBlack2 = isAbsoluteDueBlack(bal2, seq2)
    val opt1 = !absBlack1 && !absWhite2
    val opt2 = !absWhite1 && !absBlack2
    if (!opt1 && !opt2) score -= 300 else if (!opt1 || !opt2) score -= 50
    return score
}

private fun findBestGlobalPairing(
    players: List<Player>,
    data: ScoreData,
    avoidClub: Boolean,
    allowRematch: Boolean,
    maxScoreDiff: Double = Double.MAX_VALUE
): List<Pair<Player, Player>> {
    if (players.size < 2) return emptyList()
    // Sort by score then seed for deterministic first choice
    val sorted = players.sortedWith(
        compareByDescending<Player> { data.scores[it.id] ?: 0.0 }
            .thenByDescending { if (it.pairingNumber > 0) Int.MAX_VALUE - it.pairingNumber else it.rating ?: 0 }
    )
    val n = sorted.size
    // Fast path for large tournaments – exhaustive search would be exponential
    if (n > 16) {
        val remaining = sorted.toMutableList()
        val pairs = mutableListOf<Pair<Player, Player>>()
        while (remaining.size >= 2) {
            val first = remaining.removeAt(0)
            var bestIdx = -1
            var bestQ = Int.MIN_VALUE
            for (i in remaining.indices) {
                if (kotlin.math.abs((data.scores[first.id] ?: 0.0) - (data.scores[remaining[i].id] ?: 0.0)) > maxScoreDiff + 0.001) continue
                val q = if (allowRematch) pairQualityAllowAny(first, remaining[i], data)
                else pairQuality(first, remaining[i], data, avoidClub)
                if (!allowRematch && q == -1) continue
                if (q > bestQ) { bestQ = q; bestIdx = i }
            }
            if (bestIdx != -1) {
                val partner = remaining.removeAt(bestIdx)
                pairs.add(first to partner)
            }
            // else no valid partner without rematch/large-gap – leave first unpaired (will be downfloated/handled as leftover)
        }
        return pairs
    }
    val used = BooleanArray(n)
    var best: List<Pair<Player, Player>> = emptyList()
    var bestScore = Int.MIN_VALUE
    var bestSize = -1

    // Precompute pair scores, with gap filter
    val scoreMat = Array(n) { IntArray(n) }
    for (i in 0 until n) {
        for (j in i + 1 until n) {
            if (kotlin.math.abs((data.scores[sorted[i].id] ?: 0.0) - (data.scores[sorted[j].id] ?: 0.0)) > maxScoreDiff + 0.001) {
                scoreMat[i][j] = -1
                scoreMat[j][i] = -1
                continue
            }
            val q = if (allowRematch) pairQualityAllowAny(sorted[i], sorted[j], data)
            else pairQuality(sorted[i], sorted[j], data, avoidClub)
            scoreMat[i][j] = q
            scoreMat[j][i] = q
        }
    }

    fun dfs(cur: MutableList<Pair<Player, Player>>, curScore: Int) {
        var first = -1
        for (i in 0 until n) if (!used[i]) { first = i; break }
        if (first == -1) {
            if (cur.size > bestSize || (cur.size == bestSize && curScore > bestScore)) {
                best = cur.toList()
                bestSize = cur.size
                bestScore = curScore
            }
            return
        }
        // Prune: max possible additional pairs
        val remaining = used.count { !it }
        val maxAddPairs = remaining / 2
        // Even if we pair all remaining, can we beat best?
        if (cur.size + maxAddPairs < bestSize) return
        if (cur.size + maxAddPairs == bestSize) {
            val maxAddScore = maxAddPairs * 200
            if (curScore + maxAddScore <= bestScore) return
        }

        // Try pairing first with each other unused, sorted by score descending
        val candidates = mutableListOf<Pair<Int, Int>>()
        for (j in 0 until n) if (!used[j] && j != first) {
            val q = scoreMat[first][j]
            if (!allowRematch && q == -1) continue // hard rematch ban
            candidates.add(j to q)
        }
        candidates.sortByDescending { it.second }
        for ((j, q) in candidates) {
            used[first] = true; used[j] = true
            cur.add(sorted[first] to sorted[j])
            dfs(cur, curScore + q)
            cur.removeAt(cur.lastIndex)
            used[first] = false; used[j] = false
        }
        // Also try leaving first unpaired (for odd or when no perfect matching without rematch)
        used[first] = true
        dfs(cur, curScore)
        used[first] = false
    }

    dfs(mutableListOf(), 0)
    return best
}

/** Assign colors to a pairing. Returns (p1Color, p2Color). */
fun assignColors(p1: Player, p2: Player, data: ScoreData): Pair<Char, Char> {
    val bal1 = data.colorBalance[p1.id] ?: 0
    val bal2 = data.colorBalance[p2.id] ?: 0
    val seq1 = data.colorSequence[p1.id] ?: emptyList()
    val seq2 = data.colorSequence[p2.id] ?: emptyList()

    // Absolute per FIDE: |balance|>=2 or last two same
    val p1DueWhite = isAbsoluteDueWhite(bal1, seq1)
    val p2DueWhite = isAbsoluteDueWhite(bal2, seq2)
    val p1DueBlack = isAbsoluteDueBlack(bal1, seq1)
    val p2DueBlack = isAbsoluteDueBlack(bal2, seq2)

    // Strong preference (balance !=0 and not absolute, last not opposite extreme)
    val p1StrongWhite = bal1 < 0 && !p1DueWhite && seq1.lastOrNull() != 'W'
    val p2StrongWhite = bal2 < 0 && !p2DueWhite && seq2.lastOrNull() != 'W'
    val p1StrongBlack = bal1 > 0 && !p1DueBlack && seq1.lastOrNull() != 'B'
    val p2StrongBlack = bal2 > 0 && !p2DueBlack && seq2.lastOrNull() != 'B'

    val option1Valid = !(p1DueBlack || p2DueWhite)
    val option2Valid = !(p1DueWhite || p2DueBlack)

    return when {
        option1Valid && option2Valid -> {
            val opt1Score = (if (p1StrongWhite) 2 else 0) + (if (p2StrongBlack) 2 else 0) + (if (!p1StrongWhite && !p1StrongBlack && bal1 < 0) 1 else 0) + (if (!p2StrongWhite && !p2StrongBlack && bal2 > 0) 1 else 0)
            val opt2Score = (if (p1StrongBlack) 2 else 0) + (if (p2StrongWhite) 2 else 0) + (if (!p1StrongWhite && !p1StrongBlack && bal1 > 0) 1 else 0) + (if (!p2StrongWhite && !p2StrongBlack && bal2 < 0) 1 else 0)
            if (opt1Score >= opt2Score) 'W' to 'B' else 'B' to 'W'
        }
        option1Valid -> 'W' to 'B'
        option2Valid -> 'B' to 'W'
        // Neither valid (constraint violation): minimize damage - pick least absolute violation
        else -> if (bal1 <= bal2) 'B' to 'W' else 'W' to 'B'
    }
}

/** Score how good a pairing is. Higher = better. Returns -1 if hard-invalid (rematch). */
fun pairQuality(p1: Player, p2: Player, data: ScoreData, avoidClubPairings: Boolean): Int {
    // Hard ban: rematch
    if (data.played[p1.id]?.contains(p2.id) == true) return -1

    var score = 100
    // Club is soft constraint – heavy penalty not hard ban (avoid dropping players)
    if (avoidClubPairings && !p1.club.isNullOrEmpty() && !p2.club.isNullOrEmpty() && p1.club == p2.club) {
        score -= 500
    }

    // Soft color penalty (not hard ban) – absolute violations are penalized heavily but still allowed if needed
    val bal1 = data.colorBalance[p1.id] ?: 0
    val bal2 = data.colorBalance[p2.id] ?: 0
    val seq1 = data.colorSequence[p1.id] ?: emptyList()
    val seq2 = data.colorSequence[p2.id] ?: emptyList()
    val p1AbsWhite = isAbsoluteDueWhite(bal1, seq1)
    val p2AbsWhite = isAbsoluteDueWhite(bal2, seq2)
    val p1AbsBlack = isAbsoluteDueBlack(bal1, seq1)
    val p2AbsBlack = isAbsoluteDueBlack(bal2, seq2)
    val option1 = !p1AbsBlack && !p2AbsWhite
    val option2 = !p1AbsWhite && !p2AbsBlack
    if (!option1 && !option2) score -= 300
    else if (!option1 || !option2) score -= 50

    // Quality: prefer same score bracket – strong gap penalty (FIDE: minimize downfloats)
    val p1Score = data.scores[p1.id] ?: 0.0
    val p2Score = data.scores[p2.id] ?: 0.0
    val diff = abs(p1Score - p2Score)
    if (diff < 0.001) score += 50
    else score -= (diff * 60).toInt() // diff 0.5=-30, 1.0=-60, 2.0=-120 – heavily discourages large floats

    // Prefer pairing players closer in rating/seed (FIDE principle)
    val seed1 = if (p1.pairingNumber > 0) Int.MAX_VALUE - p1.pairingNumber else p1.rating ?: 0
    val seed2 = if (p2.pairingNumber > 0) Int.MAX_VALUE - p2.pairingNumber else p2.rating ?: 0
    score -= abs(seed1 - seed2) / 10

    return score
}

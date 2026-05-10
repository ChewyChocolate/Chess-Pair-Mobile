package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sqrt

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

fun generateSwiss(
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
    val pairedPlayers = mutableSetOf<String>()

    val existingRoundMatches = matches.filter { it.round == round }
    if (existingRoundMatches.isNotEmpty()) {
        boardNumber = (existingRoundMatches.maxOfOrNull { it.boardNumber ?: 0 } ?: 0) + 1
        existingRoundMatches.forEach { m ->
            m.whiteId?.let { pairedPlayers.add(it) }
            m.blackId?.let { pairedPlayers.add(it) }
        }
    }

    // Process forced pairings
    forcedPairings.forEach { fp ->
        resultMatches.add(
            Match(
                round = round,
                whiteId = fp.whiteId,
                blackId = fp.blackId,
                boardNumber = boardNumber++
            )
        )
        pairedPlayers.add(fp.whiteId)
        pairedPlayers.add(fp.blackId)
    }

    // Compare function: score then pairingNumber (or rating as fallback)
    val comparePlayers = compareByDescending<Player> { data.scores[it.id] ?: 0.0 }
        .thenByDescending {
            if (it.pairingNumber > 0) 1_000_000 - it.pairingNumber // lower pairing# = higher seed
            else it.rating ?: 0
        }

    // Filter players to pair
    var playersToPair = players
        .filter { p ->
            p.active && !p.withdrawn &&
            !p.requestedByes.contains(round) &&
            !pairedPlayers.contains(p.id)
        }
        .sortedWith(comparePlayers)

    // Apply acceleration for round 1
    if (round == 1 && accelerationMode != AccelerationMode.NONE && playersToPair.size >= 4) {
        playersToPair = applyAcceleration(playersToPair, accelerationMode)
    }

    // Handle bye for odd number
    if (playersToPair.size % 2 != 0) {
        var byeIndex = playersToPair.size - 1
        while (byeIndex >= 0 && data.byes.contains(playersToPair[byeIndex].id)) {
            byeIndex--
        }
        if (byeIndex < 0) byeIndex = playersToPair.size - 1

        val byePlayer = playersToPair[byeIndex]
        resultMatches.add(
            Match(
                round = round,
                whiteId = byePlayer.id,
                blackId = null,
                result = MatchResult.BYE,
                boardNumber = 999
            )
        )
        pairedPlayers.add(byePlayer.id)
        playersToPair = playersToPair.toMutableList().also { it.removeAt(byeIndex) }
    }

    val isLastRound = round == totalRounds

    fun canPair(p1: Player, p2: Player, relaxColorBalance: Boolean = false, relaxFloats: Boolean = false): Pair<Boolean, Pair<Boolean, Boolean>> {
        if (data.played[p1.id]?.contains(p2.id) == true) return false to (false to false)
        if (avoidClubPairings && !p1.club.isNullOrEmpty() && !p2.club.isNullOrEmpty() && p1.club == p2.club) {
            return false to (false to false)
        }

        fun checkBalance(balance: Int, newColor: Char): Boolean {
            val newBalance = if (newColor == 'W') balance + 1 else balance - 1
            return abs(newBalance) <= 2
        }

        val p1SeqW = checkColorSequence(data.colorSequence[p1.id] ?: emptyList(), 'W')
        val p1SeqB = checkColorSequence(data.colorSequence[p1.id] ?: emptyList(), 'B')
        val p2SeqW = checkColorSequence(data.colorSequence[p2.id] ?: emptyList(), 'W')
        val p2SeqB = checkColorSequence(data.colorSequence[p2.id] ?: emptyList(), 'B')

        val p1BalW = relaxColorBalance || checkBalance(data.colorBalance[p1.id] ?: 0, 'W')
        val p1BalB = relaxColorBalance || checkBalance(data.colorBalance[p1.id] ?: 0, 'B')
        val p2BalW = relaxColorBalance || checkBalance(data.colorBalance[p2.id] ?: 0, 'W')
        val p2BalB = relaxColorBalance || checkBalance(data.colorBalance[p2.id] ?: 0, 'B')

        val p1CanW = p1SeqW && p1BalW
        val p1CanB = p1SeqB && p1BalB
        val p2CanW = p2SeqW && p2BalW
        val p2CanB = p2SeqB && p2BalB

        val option1 = p1CanW && p2CanB
        val option2 = p1CanB && p2CanW

        if (!option1 && !option2) return false to (false to false)

        val lastFloatP1 = data.floatHistory[p1.id]?.lastOrNull() ?: 0
        val lastFloatP2 = data.floatHistory[p2.id]?.lastOrNull() ?: 0

        val p1Score = data.scores[p1.id] ?: 0.0
        val p2Score = data.scores[p2.id] ?: 0.0
        val isFloat = p1Score != p2Score
        if (isFloat && !relaxFloats) {
            val p1FloatDir = if (p1Score > p2Score) -1 else 1
            val p2FloatDir = if (p2Score > p1Score) -1 else 1

            if (lastFloatP1 != 0 && p1FloatDir == lastFloatP1) return false to (false to false)
            if (lastFloatP2 != 0 && p2FloatDir == lastFloatP2) return false to (false to false)
        }

        return true to (option1 to option2)
    }

    fun pairPlayers(toPair: List<Player>, relaxColorBalance: Boolean = false, relaxFloats: Boolean = false, depth: Int = 0): List<Match>? {
        if (toPair.isEmpty()) return emptyList()
        if (depth > 200) return null

        val p1 = toPair[0]
        val half = toPair.size / 2
        val searchOrder = mutableListOf<Int>()
        for (i in half until toPair.size) searchOrder.add(i)
        for (i in 1 until half) searchOrder.add(i)

        for (i in searchOrder) {
            val p2 = toPair[i]
            val (valid, options) = canPair(p1, p2, relaxColorBalance, relaxFloats)
            if (valid) {
                val remaining = toPair.subList(1, i) + toPair.subList(i + 1, toPair.size)
                val subMatches = pairPlayers(remaining, relaxColorBalance, relaxFloats, depth + 1)
                if (subMatches != null) {
                    val (p1CanW, p1CanB) = options
                    val p1Color: Char = when {
                        p1CanW && !p1CanB -> 'W'
                        !p1CanW && p1CanB -> 'B'
                        else -> {
                            val p1Pref = when {
                                (data.colorBalance[p1.id] ?: 0) > 0 -> 'B'
                                (data.colorBalance[p1.id] ?: 0) < 0 -> 'W'
                                else -> null
                            }
                            val p2Pref = when {
                                (data.colorBalance[p2.id] ?: 0) > 0 -> 'B'
                                (data.colorBalance[p2.id] ?: 0) < 0 -> 'W'
                                else -> null
                            }
                            when {
                                p1Pref == 'W' || p2Pref == 'B' -> 'W'
                                p1Pref == 'B' || p2Pref == 'W' -> 'B'
                                else -> if (p1.id.hashCode() % 2 == 0) 'W' else 'B' // deterministic fallback
                            }
                        }
                    }
                    val whiteId = if (p1Color == 'W') p1.id else p2.id
                    val blackId = if (p1Color == 'W') p2.id else p1.id

                    return listOf(
                        Match(
                            round = round,
                            whiteId = whiteId,
                            blackId = blackId,
                            boardNumber = 0
                        )
                    ) + subMatches
                }
            }
        }
        return null
    }

    // Group by score
    val scoreGroups = LinkedHashMap<Double, MutableList<Player>>()
    playersToPair.forEach { p ->
        val s = data.scores[p.id] ?: 0.0
        scoreGroups.getOrPut(s) { mutableListOf() }.add(p)
    }

    val sortedScores = scoreGroups.keys.sortedDescending()
    var currentToPair = mutableListOf<Player>()

    for ((index, scoreKey) in sortedScores.withIndex()) {
        val group = scoreGroups[scoreKey] ?: continue
        currentToPair.addAll(group)
        val isLastScoreGroup = index == sortedScores.size - 1

        val targetCount = if (currentToPair.size % 2 == 0 || isLastScoreGroup) currentToPair.size else currentToPair.size - 1

        var groupMatches: List<Match>? = null
        var pairedCount = targetCount

        while (pairedCount > 0 && groupMatches == null) {
            groupMatches = pairPlayers(currentToPair.take(pairedCount), false, false)
            if (groupMatches == null) pairedCount -= 2
        }

        if (groupMatches == null && (isLastRound || isLastScoreGroup)) {
            pairedCount = targetCount
            while (pairedCount > 0 && groupMatches == null) {
                groupMatches = pairPlayers(currentToPair.take(pairedCount), true, false)
                if (groupMatches == null) pairedCount -= 2
            }
        }

        if (groupMatches == null && (isLastRound || isLastScoreGroup)) {
            pairedCount = targetCount
            while (pairedCount > 0 && groupMatches == null) {
                groupMatches = pairPlayers(currentToPair.take(pairedCount), true, true)
                if (groupMatches == null) pairedCount -= 2
            }
        }

        if (groupMatches != null) {
            groupMatches.forEach { m ->
                resultMatches.add(m.copy(boardNumber = boardNumber++))
                m.whiteId?.let { pairedPlayers.add(it) }
                m.blackId?.let { pairedPlayers.add(it) }
            }
            currentToPair = currentToPair.drop(pairedCount).toMutableList()
        }

        if (isLastScoreGroup && currentToPair.size > 1) {
            val maxAttempts = currentToPair.size
            var attempts = 0
            while (currentToPair.size > 1) {
                if (attempts >= maxAttempts) break
                val p1 = currentToPair[0]
                var p2Index = -1
                for (j in 1 until currentToPair.size) {
                    if (data.played[p1.id]?.contains(currentToPair[j].id) != true) {
                        p2Index = j
                        break
                    }
                }
                if (p2Index < 0) {
                    currentToPair.add(currentToPair.removeAt(0))
                    attempts++
                    continue
                }
                attempts = 0
                val p2 = currentToPair[p2Index]

                val p1SeqW = checkColorSequence(data.colorSequence[p1.id] ?: emptyList(), 'W')
                val p1SeqB = checkColorSequence(data.colorSequence[p1.id] ?: emptyList(), 'B')
                val p2SeqW = checkColorSequence(data.colorSequence[p2.id] ?: emptyList(), 'W')
                val p2SeqB = checkColorSequence(data.colorSequence[p2.id] ?: emptyList(), 'B')

                val p1Color: Char = when {
                    p1SeqW && p2SeqB && (!p1SeqB || !p2SeqW) -> 'W'
                    p1SeqB && p2SeqW && (!p1SeqW || !p2SeqB) -> 'B'
                    else -> {
                        val p1Pref = when {
                            (data.colorBalance[p1.id] ?: 0) > 0 -> 'B'
                            (data.colorBalance[p1.id] ?: 0) < 0 -> 'W'
                            else -> {
                                val seq = data.colorSequence[p1.id] ?: emptyList()
                                if (seq.isNotEmpty()) if (seq.last() == 'W') 'B' else 'W' else null
                            }
                        }
                        val p2Pref = when {
                            (data.colorBalance[p2.id] ?: 0) > 0 -> 'B'
                            (data.colorBalance[p2.id] ?: 0) < 0 -> 'W'
                            else -> {
                                val seq = data.colorSequence[p2.id] ?: emptyList()
                                if (seq.isNotEmpty()) if (seq.last() == 'W') 'B' else 'W' else null
                            }
                        }
                        when {
                            p1Pref == 'W' || p2Pref == 'B' -> 'W'
                            p1Pref == 'B' || p2Pref == 'W' -> 'B'
                            else -> if (p1.id.hashCode() % 2 == 0) 'W' else 'B' // deterministic fallback
                        }
                    }
                }

                val whiteId = if (p1Color == 'W') p1.id else p2.id
                val blackId = if (p1Color == 'W') p2.id else p1.id

                resultMatches.add(
                    Match(
                        round = round,
                        whiteId = whiteId,
                        blackId = blackId,
                        boardNumber = boardNumber++
                    )
                )
                currentToPair.removeAt(p2Index)
                currentToPair.removeAt(0)
            }
        }
    }

    return resultMatches
}

fun generateRoundRobin(players: List<Player>, round: Int): List<Match> {
    val activePlayers = players.filter { !it.withdrawn && !it.requestedByes.contains(round) }
    val matches = mutableListOf<Match>()
    val n = activePlayers.size
    val isOdd = n % 2 != 0

    data class DummyPlayer(val id: String = "dummy")
    val workingPlayers = if (isOdd) activePlayers + Player(id = "dummy", name = "Bye") else activePlayers
    val totalPlayers = workingPlayers.size

    val p = workingPlayers.toMutableList()
    for (i in 1 until round) {
        val last = p.removeLast()
        p.add(1, last)
    }

    for (i in 0 until totalPlayers / 2) {
        val p1 = p[i]
        val p2 = p[totalPlayers - 1 - i]

        if (p1.id == "dummy" || p2.id == "dummy") {
            val realPlayer = if (p1.id == "dummy") p2 else p1
            matches.add(
                Match(
                    round = round,
                    whiteId = realPlayer.id,
                    blackId = null,
                    result = MatchResult.BYE,
                    boardNumber = 999
                )
            )
        } else {
            val isEvenRound = round % 2 == 0
            val white = if (isEvenRound) p2 else p1
            val black = if (isEvenRound) p1 else p2
            matches.add(
                Match(
                    round = round,
                    whiteId = white.id,
                    blackId = black.id,
                    boardNumber = i + 1
                )
            )
        }
    }
    return matches
}

fun generateKnockout(players: List<Player>, matches: List<Match>, round: Int): List<Match> {
    if (round == 1) {
        val activePlayers = players.filter { it.active && !it.withdrawn && !it.requestedByes.contains(round) }
        val n = activePlayers.size
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

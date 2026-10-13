package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*

data class ScoreData(
    val scores: MutableMap<String, Double> = mutableMapOf(),
    val played: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    val colorBalance: MutableMap<String, Int> = mutableMapOf(),
    val colorSequence: MutableMap<String, MutableList<Char>> = mutableMapOf(),
    val floatHistory: MutableMap<String, MutableList<Int>> = mutableMapOf(),
    val byes: MutableSet<String> = mutableSetOf(),
    val unplayedRounds: MutableMap<String, Int> = mutableMapOf()
)

fun calculateScores(players: List<Player>, matches: List<Match>, pointsForWin: Double,
    pointsForDraw: Double, pointsForLoss: Double, upToRound: Int? = null): ScoreData {
    val through = upToRound ?: (matches.maxOfOrNull { it.round } ?: 0)
    val data = ScoreData()
    players.forEach { p ->
        data.scores[p.id] = 0.0
        data.played[p.id] = mutableSetOf()
        data.colorBalance[p.id] = 0
        data.colorSequence[p.id] = mutableListOf()
        data.floatHistory[p.id] = MutableList(through.coerceAtLeast(0)) { 0 }
        data.unplayedRounds[p.id] = 0
    }
    val byRound = matches.filter { it.round <= through && it.result != null }.groupBy { it.round }
    for (round in 1..through) {
        val roundMatches = byRound[round].orEmpty()
        val previousScores = data.scores.toMap()
        val involved = roundMatches.flatMap { listOfNotNull(it.whiteId, it.blackId) }.toSet()
        fun award(id: String?, points: Double, played: Boolean) {
            if (id == null || id !in data.scores) return
            data.scores[id] = data.scores.getValue(id) + points
            if (!played) {
                if (points >= pointsForWin) data.byes.add(id)
                if (points > pointsForLoss) data.floatHistory.getValue(id)[round - 1] = -1
            }
        }
        roundMatches.forEach { m ->
            val played = m.whiteId != null && m.blackId != null && isPlayedResult(m.result)
            if (played) {
                val white = m.whiteId!!
                val black = m.blackId!!
                data.played[white]?.add(black)
                data.played[black]?.add(white)
                data.colorBalance[white] = (data.colorBalance[white] ?: 0) + 1
                data.colorBalance[black] = (data.colorBalance[black] ?: 0) - 1
                data.colorSequence[white]?.add('W')
                data.colorSequence[black]?.add('B')
                val comparison = (previousScores[white] ?: 0.0).compareTo(previousScores[black] ?: 0.0)
                data.floatHistory[white]?.set(round - 1, -comparison)
                data.floatHistory[black]?.set(round - 1, comparison)
            }
            award(m.whiteId, getMatchPointsForPlayer(m, m.whiteId, pointsForWin, pointsForDraw, pointsForLoss), played)
            award(m.blackId, getMatchPointsForPlayer(m, m.blackId, pointsForWin, pointsForDraw, pointsForLoss), played)
            if (m.result == MatchResult.BYE) listOfNotNull(m.whiteId, m.blackId).forEach { data.byes.add(it) }
        }
        players.forEach { p ->
            if (p.id !in involved && round in p.requestedByes) award(p.id, pointsForDraw, false)
            if (roundMatches.none { (it.whiteId == p.id || it.blackId == p.id) && isPlayedResult(it.result) }) {
                data.unplayedRounds[p.id] = (data.unplayedRounds[p.id] ?: 0) + 1
            }
        }
    }
    players.forEach { p -> data.scores[p.id] = (data.scores[p.id] ?: 0.0) - p.penaltyPoints }
    return data
}

fun getMatchPointsForPlayer(match: Match, playerId: String?, win: Double, draw: Double, loss: Double): Double {
    if (playerId == null || (playerId != match.whiteId && playerId != match.blackId)) return 0.0
    val white = playerId == match.whiteId
    return when (match.result) {
        MatchResult.WHITE_WIN, MatchResult.FORFEIT_BLACK -> if (white) win else loss
        MatchResult.BLACK_WIN, MatchResult.FORFEIT_WHITE -> if (white) loss else win
        MatchResult.DRAW, MatchResult.TEAM_BYE -> draw
        MatchResult.BYE -> win
        MatchResult.FORFEIT_BOTH -> loss
        MatchResult.REST, null -> 0.0
    }
}

fun checkColorSequence(seq: List<Char>, newColor: Char): Boolean = seq.size < 2 || seq.takeLast(2).any { it != newColor }

/** Official Berger rotation. The highest slot is the dummy for an odd roster. */
fun bergerPairs(size: Int, round: Int): List<Pair<Int, Int>> {
    val n = if (size % 2 == 0) size else size + 1
    if (n < 2 || round !in 1 until n) return emptyList()
    fun advance(number: Int) = ((number - 1 + (round - 1) * (n / 2)) % (n - 1)) + 1
    return (1..n / 2).map { board ->
        if (board == 1) {
            val rotating = advance(1)
            if (round % 2 == 1) rotating to n else n to rotating
        } else advance(board) to advance(n + 1 - board)
    }
}

fun generateRoundRobin(players: List<Player>, round: Int, avoidClubPairings: Boolean = false): List<Match> {
    if (avoidClubPairings) throw PairingException("Round robin requires every opponent to meet. Turn off club avoidance.")
    val seeded = players.any { it.pairingNumber > 0 }
    val roster = if (seeded) players.filter { it.pairingNumber > 0 } else players.filter { it.active && !it.withdrawn }
    val ordered = withPairingNumbers(roster).sortedBy { it.pairingNumber }
    fun available(p: Player) = p.active && !p.withdrawn && round !in p.requestedByes
    return bergerPairs(ordered.size, round).mapIndexed { index, (w, b) ->
        val white = ordered.getOrNull(w - 1)
        val black = ordered.getOrNull(b - 1)
        if (white == null || black == null) Match(round = round, whiteId = (white ?: black)!!.id, result = MatchResult.REST, boardNumber = index + 1)
        else Match(round = round, whiteId = white.id, blackId = black.id, boardNumber = index + 1,
            result = when {
                !available(white) && !available(black) -> MatchResult.FORFEIT_BOTH
                !available(white) -> MatchResult.FORFEIT_WHITE
                !available(black) -> MatchResult.FORFEIT_BLACK
                else -> null
            })
    }
}

/** Every adjacent pair of prior boards feeds a fixed next-round board. */
fun generateKnockout(players: List<Player>, matches: List<Match>, round: Int): List<Match> {
    val existing = matches.filter { it.round == round }
    if (existing.isNotEmpty()) return existing.sortedBy { it.boardNumber }
    if (round == 1) {
        val active = withPairingNumbers(players.filter { it.active && !it.withdrawn && round !in it.requestedByes }).sortedBy { it.pairingNumber }
        if (active.size < 2) return emptyList()
        var slots = listOf(1, 2)
        while (slots.size < active.size) {
            val nextSize = slots.size * 2
            slots = slots.flatMap { listOf(it, nextSize + 1 - it) }
        }
        return slots.chunked(2).mapIndexed { index, pair ->
            val a = active.getOrNull(pair[0] - 1)
            val b = active.getOrNull(pair[1] - 1)
            if (a == null || b == null) Match(round = round, whiteId = (a ?: b)!!.id, result = MatchResult.BYE, boardNumber = index + 1)
            else Match(round = round, whiteId = a.id, blackId = b.id, boardNumber = index + 1)
        }
    }
    val previous = matches.filter { it.round == round - 1 }.sortedWith(compareBy<Match> { it.boardNumber ?: Int.MAX_VALUE }.thenBy { it.id })
    if (previous.isEmpty() || previous.any { it.result == null || it.result == MatchResult.DRAW }) throw PairingException("Resolve every previous knockout match before generating the next round.")
    if (previous.size == 1) return emptyList() // The final has already produced the champion.
    if (previous.size % 2 != 0) throw PairingException("The knockout bracket is incomplete. Review the previous round.")
    val eligible = players.filter { it.active && !it.withdrawn && round !in it.requestedByes }.map { it.id }.toSet()
    fun winner(m: Match): String? = when (m.result) {
        MatchResult.WHITE_WIN, MatchResult.FORFEIT_BLACK, MatchResult.BYE -> m.whiteId
        MatchResult.BLACK_WIN, MatchResult.FORFEIT_WHITE -> m.blackId
        else -> null
    }?.takeIf { it in eligible }
    return previous.chunked(2).mapIndexed { index, pair ->
        val a = winner(pair[0])
        val b = winner(pair[1])
        if (a == null && b == null) throw PairingException("Both winners in a knockout path are unavailable. The arbiter must resolve the bracket.")
        if (a == null || b == null) Match(round = round, whiteId = a ?: b, result = MatchResult.BYE, boardNumber = index + 1)
        else Match(round = round, whiteId = a, blackId = b, boardNumber = index + 1)
    }
}

fun calculateTeamScores(teams: List<Team>, matches: List<Match>, upToRound: Int? = null): Pair<MutableMap<String, Double>, MutableMap<String, Double>> {
    val matchPoints = teams.associate { it.id to 0.0 }.toMutableMap()
    val gamePoints = teams.associate { it.id to 0.0 }.toMutableMap()
    val playerTeam = teams.flatMap { team -> team.playerIds.map { it to team.id } }.toMap()
    matches.filter { it.teamMatchId != null && (upToRound == null || it.round <= upToRound) }.groupBy { it.round to it.teamMatchId }.values.forEach { boards ->
        val teamIds = boards.flatMap { listOfNotNull(playerTeam[it.whiteId], playerTeam[it.blackId]) }.distinct()
        val points = teamIds.associateWith { 0.0 }.toMutableMap()
        boards.forEach { board ->
            listOfNotNull(board.whiteId, board.blackId).forEach playerLoop@ { id ->
                val teamId = playerTeam[id] ?: return@playerLoop
                points[teamId] = (points[teamId] ?: 0.0) + getMatchPointsForPlayer(board, id, 1.0, 0.5, 0.0)
            }
        }
        points.forEach { (id, score) -> gamePoints[id] = (gamePoints[id] ?: 0.0) + score }
        if (boards.any { it.result == null }) return@forEach
        if (teamIds.size == 1 && boards.all { it.result == MatchResult.TEAM_BYE }) {
            val id = teamIds.single()
            matchPoints[id] = (matchPoints[id] ?: 0.0) + 1.0
        } else if (teamIds.size == 1 && boards.all { it.result == MatchResult.BYE }) {
            val id = teamIds.single()
            matchPoints[id] = (matchPoints[id] ?: 0.0) + 2.0
        } else if (teamIds.size == 2) {
            val a = teamIds[0]
            val b = teamIds[1]
            val comparison = (points[a] ?: 0.0).compareTo(points[b] ?: 0.0)
            matchPoints[a] = (matchPoints[a] ?: 0.0) + if (comparison > 0) 2.0 else if (comparison == 0) 1.0 else 0.0
            matchPoints[b] = (matchPoints[b] ?: 0.0) + if (comparison < 0) 2.0 else if (comparison == 0) 1.0 else 0.0
        }
    }
    return matchPoints to gamePoints
}

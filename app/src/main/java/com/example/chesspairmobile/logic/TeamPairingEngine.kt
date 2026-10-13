package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*
import java.util.UUID
import kotlin.math.abs

fun withTeamPairingNumbers(teams: List<Team>): List<Team> {
    val assigned = teams.filter { it.pairingNumber > 0 }
    if (assigned.map { it.pairingNumber }.distinct().size != assigned.size) throw PairingException("Team pairing numbers must be unique.")
    var number = assigned.maxOfOrNull { it.pairingNumber } ?: 0
    val numbers = teams.filter { it.pairingNumber == 0 }.sortedWith(compareBy<Team> { it.name.lowercase() }.thenBy { it.id }).associate { it.id to ++number }
    return teams.map { if (it.pairingNumber > 0) it else it.copy(pairingNumber = numbers.getValue(it.id)) }
}

/** Translate one team encounter to boards without confusing missing-board forfeits with team byes. */
private fun teamBoards(a: Team, b: Team?, round: Int, firstBoard: Int, bye: MatchResult = MatchResult.TEAM_BYE, players: List<Player> = emptyList()): List<Match> {
    val encounter = UUID.randomUUID().toString()
    if (b == null) return a.playerIds.mapIndexed { index, id -> Match(round = round, whiteId = id, result = bye, boardNumber = firstBoard + index, teamMatchId = encounter) }
    return (0 until maxOf(a.playerIds.size, b.playerIds.size)).map { board ->
        val p1 = a.playerIds.getOrNull(board)
        val p2 = b.playerIds.getOrNull(board)
        val white = if (board % 2 == 0) p1 else p2
        val black = if (board % 2 == 0) p2 else p1
        fun available(id: String?) = id != null && (players.isEmpty() || players.any { it.id == id && it.active && !it.withdrawn && round !in it.requestedByes })
        Match(round = round, whiteId = white, blackId = black, boardNumber = firstBoard + board, teamMatchId = encounter,
            result = when { !available(white) && !available(black) -> MatchResult.FORFEIT_BOTH; !available(white) -> MatchResult.FORFEIT_WHITE; !available(black) -> MatchResult.FORFEIT_BLACK; else -> null })
    }
}

fun generateTeamRoundRobin(teams: List<Team>, round: Int, players: List<Player> = emptyList()): List<Match> {
    val ordered = withTeamPairingNumbers(teams.filter { it.playerIds.isNotEmpty() }).sortedBy { it.pairingNumber }
    var board = 1
    return bergerPairs(ordered.size, round).flatMap { (w, b) ->
        val a = ordered.getOrNull(w - 1)
        val other = ordered.getOrNull(b - 1)
        val encounter = teamBoards(a ?: other!!, if (a == null) null else other, round, board, MatchResult.REST, players)
        board += encounter.size
        encounter
    }
}

fun generateTeamKnockout(teams: List<Team>, history: List<Match>, round: Int, players: List<Player> = emptyList()): List<Match> {
    val ordered = withTeamPairingNumbers(teams.filter { it.playerIds.isNotEmpty() })
    val memberTeam = ordered.flatMap { team -> team.playerIds.map { it to team.id } }.toMap()
    val encounters = history.filter { it.teamMatchId != null }.groupBy { it.round to it.teamMatchId }.values.map { boards ->
        val ids = boards.flatMap { listOfNotNull(memberTeam[it.whiteId], memberTeam[it.blackId]) }.distinct()
        val first = boards.minBy { it.boardNumber ?: Int.MAX_VALUE }
        val points = calculateTeamScores(ordered, boards).second
        val settled = boards.all { it.result != null }
        val a = ids.firstOrNull()
        val b = ids.getOrNull(1)
        Match(id = first.teamMatchId!!, round = first.round, whiteId = a, blackId = b, boardNumber = first.boardNumber,
            result = when {
                !settled -> null
                b == null -> if (boards.all { it.result == MatchResult.BYE }) MatchResult.BYE else MatchResult.REST
                points.getValue(a!!) > points.getValue(b) -> MatchResult.WHITE_WIN
                points.getValue(a) < points.getValue(b) -> MatchResult.BLACK_WIN
                else -> MatchResult.DRAW
            })
    }
    val pseudo = ordered.map { team -> Player(id = team.id, name = team.name, pairingNumber = team.pairingNumber,
        active = players.isEmpty() || players.any { it.id in team.playerIds && it.active && !it.withdrawn && round !in it.requestedByes }) }
    var board = 1
    return generateKnockout(pseudo, encounters, round).flatMap { encounter ->
        val a = ordered.first { it.id == encounter.whiteId }
        val b = ordered.find { it.id == encounter.blackId }
        val boards = teamBoards(a, b, round, board, MatchResult.BYE, players)
        board += boards.size
        boards
    }
}

/** C.04.6, default match-point primary score and Type A colour preferences. */
fun generateTeamSwiss(teams: List<Team>, history: List<Match>, round: Int, totalRounds: Int, initialColour: Char = 'W', players: List<Player> = emptyList()): List<Match> {
    val ordered = withTeamPairingNumbers(teams.filter { it.playerIds.isNotEmpty() }).sortedBy { it.pairingNumber }
    val earlier = history.filter { it.round < round }
    if (earlier.any { it.result == null }) throw PairingException("Complete previous team results before pairing.")
    val memberTeam = ordered.flatMap { team -> team.playerIds.map { it to team.id } }.toMap()
    val encounters = earlier.filter { it.teamMatchId != null }.groupBy { it.round to it.teamMatchId }.values.toList()
    val (scores, secondary) = calculateTeamScores(ordered, earlier)
    val played = ordered.associate { it.id to mutableSetOf<String>() }
    val byed = mutableSetOf<String>()
    val colours = ordered.associate { it.id to mutableListOf<Char>() }
    val floats = ordered.associate { it.id to mutableSetOf<Int>() }
    encounters.sortedBy { it.first().round }.forEach { boards ->
        val ids = boards.flatMap { listOfNotNull(memberTeam[it.whiteId], memberTeam[it.blackId]) }.distinct()
        if (ids.size == 1 && boards.all { it.result == MatchResult.TEAM_BYE || it.result == MatchResult.BYE }) byed.add(ids[0])
        if (ids.size == 2) {
            val actual = boards.any { isPlayedResult(it.result) }
            if (actual) {
                played.getValue(ids[0]).add(ids[1]); played.getValue(ids[1]).add(ids[0])
                val first = boards.minBy { it.boardNumber ?: Int.MAX_VALUE }
                memberTeam[first.whiteId]?.let { colours.getValue(it).add('W') }
                memberTeam[first.blackId]?.let { colours.getValue(it).add('B') }
                val before = calculateTeamScores(ordered, earlier, first.round - 1).first
                if (before[ids[0]] != before[ids[1]]) ids.forEach { floats.getValue(it).add(first.round) }
            } else {
                val points = calculateTeamScores(ordered, boards).second
                if ((points[ids[0]] ?: 0.0) > (points[ids[1]] ?: 0.0)) byed.add(ids[0])
                if ((points[ids[1]] ?: 0.0) > (points[ids[0]] ?: 0.0)) byed.add(ids[1])
            }
        }
    }
    fun preference(team: Team): Char? {
        val seq = colours.getValue(team.id)
        val difference = seq.count { it == 'W' } - seq.count { it == 'B' }
        return when {
            difference > 1 || (difference in 0..1 && seq.size >= 2 && seq.takeLast(2).all { it == 'W' }) -> 'B'
            difference < -1 || (difference in -1..0 && seq.size >= 2 && seq.takeLast(2).all { it == 'B' }) -> 'W'
            else -> null
        }
    }
    fun allocate(a: Team, b: Team): Pair<Team, Team> {
        val first = listOf(a, b).sortedWith(compareByDescending<Team> { scores[it.id] ?: 0.0 }
            .thenByDescending { secondary[it.id] ?: 0.0 }.thenBy { it.pairingNumber }).first()
        val other = if (first.id == a.id) b else a
        val pa = preference(first); val pb = preference(other)
        val sa = colours.getValue(first.id); val sb = colours.getValue(other.id)
        fun difference(seq: List<Char>) = seq.count { it == 'W' } - seq.count { it == 'B' }
        var assigned: Char? = when {
            sa.isEmpty() && sb.isEmpty() -> if (first.pairingNumber % 2 == 1) initialColour else if (initialColour == 'W') 'B' else 'W'
            pa != null && pa != pb -> pa
            pb != null && pa == null -> if (pb == 'W') 'B' else 'W'
            difference(sa) != difference(sb) -> if (difference(sa) < difference(sb)) 'W' else 'B'
            else -> null
        }
        if (assigned == null) for (i in 1..minOf(sa.size, sb.size)) {
            if (sa[sa.size - i] != sb[sb.size - i]) { assigned = if (sa[sa.size - i] == 'W') 'B' else 'W'; break }
        }
        assigned = assigned ?: pa ?: sa.lastOrNull()?.let { if (it == 'W') 'B' else 'W' } ?:
            sb.lastOrNull()?.let { if (it == 'W') 'W' else 'B' } ?: initialColour
        return if (assigned == 'W') first to other else other to first
    }
    var visited = 0
    fun step() { if (++visited > 500_000 || Thread.currentThread().isInterrupted) throw PairingException("Team search limit reached. The arbiter must resolve the round.") }
    fun <T> combinations(list: List<T>, count: Int): Sequence<List<T>> = sequence {
        if (count == 0) { yield(emptyList()); return@sequence }
        for (i in 0..list.size - count) {
            step()
            for (tail in combinations(list.drop(i + 1), count - 1)) yield(listOf(list[i]) + tail)
        }
    }
    fun match(top: List<Team>, bottom: List<Team>): Sequence<List<Pair<Team, Team>>> = sequence {
        if (top.isEmpty()) { yield(emptyList()); return@sequence }
        for (b in bottom) {
            step()
            val a = top.first()
            if (a.pairingNumber >= b.pairingNumber || b.id in played.getValue(a.id)) continue
            for (tail in match(top.drop(1), bottom.filter { it.id != b.id })) yield(listOf(a to b) + tail)
        }
    }
    fun compare(a: List<Double>, b: List<Double>): Int {
        for (i in a.indices) if (a[i] != b[i]) return a[i].compareTo(b[i])
        return 0
    }
    data class Result(val pairs: List<Pair<Team, Team>>, val quality: List<Double>)
    fun pair(pool: List<Team>): Result? {
        step()
        if (pool.isEmpty()) return Result(emptyList(), emptyList())
        val topScore = pool.maxOf { scores[it.id] ?: 0.0 }
        val residents = pool.filter { scores[it.id] == topScore }
        val lower = pool.filter { scores[it.id] != topScore }.sortedWith(compareByDescending<Team> { scores[it.id] ?: 0.0 }.thenBy { it.pairingNumber })
        var best: Result? = null
        for (count in residents.size % 2..lower.size step 2) {
            for (up in combinations(lower, count)) {
                val bracket = (residents + up).sortedBy { it.pairingNumber }
                val next = pair(lower.filter { it !in up }) ?: continue
                for (s1 in combinations(bracket, bracket.size / 2)) {
                    for (pairs in match(s1, bracket.filter { it !in s1 })) {
                        val upIds = up.map { it.id }.toSet()
                        val opponents = pairs.flatMap { (a, b) ->
                            listOfNotNull(b.takeIf { a.id in upIds }, a.takeIf { b.id in upIds })
                        }
                        val refused = pairs.sumOf { (a, b) ->
                            val (w, black) = allocate(a, b)
                            (if (preference(w) == 'B') 1 else 0) + (if (preference(black) == 'W') 1 else 0)
                        }
                        val gaps = up.map { topScore - (scores[it.id] ?: 0.0) }.sortedDescending() + List(ordered.size - count) { 0.0 }
                        val penalizeFloats = round <= totalRounds - 2
                        val quality = listOf(count.toDouble()) + gaps + listOf(next.quality.firstOrNull() ?: 0.0,
                            if (penalizeFloats) up.count { round - 1 in floats.getValue(it.id) }.toDouble() else 0.0,
                            refused.toDouble(), if (penalizeFloats) opponents.count { round - 1 in floats.getValue(it.id) }.toDouble() else 0.0)
                        if (best == null || compare(quality, best.quality) < 0) best = Result(pairs + next.pairs, quality)
                        if (count == residents.size % 2 && quality.drop(1 + ordered.size).all { it == 0.0 } &&
                            up.map { scores[it.id] ?: 0.0 }.sortedDescending() == lower.take(count).map { scores[it.id] ?: 0.0 }.sortedDescending()) return best
                    }
                }
            }
            if (best != null) break // C4 outranks all other quality criteria.
        }
        return best
    }
    val available = ordered.filter { team -> players.isEmpty() || players.any { it.id in team.playerIds && it.active && !it.withdrawn && round !in it.requestedByes } }
    val byeCandidates: List<Team?> = if (available.size % 2 == 0) listOf(null) else available.filter { it.id !in byed }.sortedWith(
        compareBy<Team> { scores[it.id] ?: 0.0 }.thenByDescending { played.getValue(it.id).size }.thenByDescending { it.pairingNumber })
    for (bye in byeCandidates) {
        val result = pair(available.filter { it.id != bye?.id }) ?: continue
        var board = 1
        val output = result.pairs.flatMap { (a, b) ->
            val (white, black) = allocate(a, b)
            val boards = teamBoards(white, black, round, board, players = players)
            board += boards.size
            boards
        }
        return output + if (bye == null) emptyList() else teamBoards(bye, null, round, board)
    }
    throw PairingException("No legal team Swiss round exists. The Chief Arbiter must resolve the pairing.")
}

package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*
import kotlin.math.abs

/** Dutch bracket search. Absolute restrictions are never relaxed to obtain a round. */
fun generateFideDutch(
    players: List<Player>, matches: List<Match>, round: Int, totalRounds: Int,
    pointsForWin: Double, pointsForDraw: Double, pointsForLoss: Double,
    avoidClubPairings: Boolean, forcedPairings: List<ForcedPairing>,
    accelerationMode: AccelerationMode = AccelerationMode.NONE, initialColour: Char = 'W'
): List<Match> {
    if (initialColour !in listOf('W', 'B')) throw PairingException("Choose White or Black for the initial colour.")
    if (round !in 1..totalRounds) throw PairingException("The round must be within the announced tournament length.")
    if (avoidClubPairings) throw PairingException("FIDE Dutch pairing does not include club avoidance. Turn off club avoidance.")
    if (accelerationMode == AccelerationMode.LIM) throw PairingException("Lim acceleration is unsupported. Select None or Baku.")
    if (accelerationMode == AccelerationMode.BAKU && (pointsForLoss != 0.0 || pointsForWin != pointsForDraw * 2)) {
        throw PairingException("Baku requires a win to equal two draws and a loss to score zero.")
    }
    if (matches.any { it.round < round && it.result == null }) throw PairingException("Complete previous-round results before pairing.")
    val historicalIds = matches.flatMap { listOfNotNull(it.whiteId, it.blackId) }.toSet()
    val seeded = withPairingNumbers(players.filter { it.pairingNumber > 0 || it.id in historicalIds || (it.active && !it.withdrawn) })
    val real = calculateScores(seeded, matches, pointsForWin, pointsForDraw, pointsForLoss, round - 1)
    val data = real.copy(scores = real.scores.toMutableMap())
    if (accelerationMode == AccelerationMode.BAKU) {
        seeded.forEach { p -> data.scores[p.id] = data.scores.getValue(p.id) + bakuVirtualPoints(p.pairingNumber, seeded.size, round, totalRounds, pointsForWin) }
        // Float directions use the pairing scores of the round in which the players met.
        for (previous in 1 until round) {
            val before = calculateScores(seeded, matches, pointsForWin, pointsForDraw, pointsForLoss, previous - 1)
            matches.filter { it.round == previous && isPlayedResult(it.result) && it.whiteId != null && it.blackId != null }.forEach { m ->
                fun score(id: String): Double {
                    val p = seeded.first { it.id == id }
                    return (before.scores[id] ?: 0.0) + bakuVirtualPoints(p.pairingNumber, seeded.size, previous, totalRounds, pointsForWin)
                }
                val comparison = score(m.whiteId!!).compareTo(score(m.blackId!!))
                data.floatHistory[m.whiteId]?.set(previous - 1, -comparison)
                data.floatHistory[m.blackId]?.set(previous - 1, comparison)
            }
        }
    }
    val top = if (round == totalRounds) seeded.filter { (real.scores[it.id] ?: 0.0) > (round - 1) * pointsForWin / 2 }.map { it.id }.toSet() else emptySet()
    val rank = compareByDescending<Player> { data.scores[it.id] ?: 0.0 }.thenBy { it.pairingNumber }
    val pool = seeded.filter { it.active && !it.withdrawn && round !in it.requestedByes }.sortedWith(rank)
    val reserved = matches.filter { it.round == round }.flatMap { listOfNotNull(it.whiteId, it.blackId) }.toMutableSet()
    val forced = mutableListOf<Match>()
    forcedPairings.forEach { fp ->
        val a = pool.find { it.id == fp.whiteId }
        val b = pool.find { it.id == fp.blackId }
        if (a == null || b == null || a.id in reserved || b.id in reserved || !legalSwissPair(a, b, data, top)) {
            throw PairingException("A forced pairing violates player availability, opponent or colour restrictions.")
        }
        if (a.id !in top && b.id !in top && (colourPreference(a.id, data).let { it.strength == 3 && it.colour != 'W' } || colourPreference(b.id, data).let { it.strength == 3 && it.colour != 'B' })) {
            throw PairingException("The forced colours violate an absolute colour preference.")
        }
        forced.add(Match(round = round, whiteId = a.id, blackId = b.id, isManual = true))
        reserved.add(a.id); reserved.add(b.id)
    }
    val available = pool.filter { it.id !in reserved }
    val search = DutchSearch(available, data, top, initialColour, rank)
    val paired = search.solve() ?: throw PairingException("No legal Swiss round exists. The Chief Arbiter must resolve the pairing.")
    val generated = forced + paired.pairs.map { (a, b) ->
        val (first, second) = if (rank.compare(a, b) <= 0) a to b else b to a
        val colours = assignColors(first, second, data, initialColour)
        Match(round = round, whiteId = if (colours.first == 'W') first.id else second.id,
            blackId = if (colours.first == 'W') second.id else first.id)
    }
    val numbered = generated.sortedWith(compareByDescending<Match> { maxOf(data.scores[it.whiteId] ?: 0.0, data.scores[it.blackId] ?: 0.0) }
        .thenByDescending { (data.scores[it.whiteId] ?: 0.0) + (data.scores[it.blackId] ?: 0.0) }
        .thenBy { minOf(seeded.first { p -> p.id == it.whiteId }.pairingNumber, seeded.first { p -> p.id == it.blackId }.pairingNumber) })
        .mapIndexed { index, m -> m.copy(boardNumber = index + 1) }
    return numbered + listOfNotNull(paired.bye?.let { Match(round = round, whiteId = it.id, result = MatchResult.BYE, boardNumber = numbered.size + 1) })
}

private data class BracketCandidate(val pairs: List<Pair<Player, Player>>, val outgoing: List<Player>)
private data class DutchRound(val pairs: List<Pair<Player, Player>>, val bye: Player?, val quality: List<Double>)

private class DutchSearch(
    private val players: List<Player>, private val data: ScoreData, private val topscorers: Set<String>,
    private val initialColour: Char, private val rank: Comparator<Player>
) {
    private val groups = players.groupBy { data.scores[it.id] ?: 0.0 }.toSortedMap(reverseOrder()).values.toList()
    private val cache = mutableMapOf<String, DutchRound?>()
    private var visited = 0
    private val width = players.size
    private fun step() {
        if (++visited > 1_000_000 || Thread.currentThread().isInterrupted) {
            throw PairingException("The Swiss search limit was reached. The arbiter must resolve this round; no partial pairing was saved.")
        }
    }
    private fun score(p: Player) = data.scores[p.id] ?: 0.0
    private fun floated(p: Player, ago: Int, direction: Int) = data.floatHistory[p.id].orEmpty().let { history -> history.getOrNull(history.size - ago) == direction }
    private fun vector(values: List<Double>) = values.sortedDescending() + List((width - values.size).coerceAtLeast(0)) { 0.0 }
    private fun compare(a: List<Double>, b: List<Double>): Int {
        for (i in 0 until minOf(a.size, b.size)) if (a[i] != b[i]) return a[i].compareTo(b[i])
        return a.size.compareTo(b.size)
    }
    fun solve(): DutchRound? = search(0, emptyList())
    private fun search(index: Int, incoming: List<Player>): DutchRound? {
        step()
        if (index == groups.size) {
            return when {
                incoming.isEmpty() -> DutchRound(emptyList(), null, emptyList())
                incoming.size == 1 && incoming[0].id !in data.byes -> DutchRound(emptyList(), incoming[0], emptyList())
                else -> null
            }
        }
        val key = "$index:" + incoming.joinToString(",") { it.id }
        if (cache.containsKey(key)) return cache[key]
        val residents = groups[index]
        val bracket = (incoming + residents).sortedWith(rank)
        var best: DutchRound? = null
        val maxPairs = minOf(bracket.size / 2, if (incoming.isEmpty()) bracket.size / 2 else residents.size)
        // Article 3.8: compare criteria lexicographically; equal candidates retain generation order.
        for (pairCount in maxPairs downTo 0) {
            for (candidate in candidates(incoming, residents, pairCount)) {
                val tail = search(index + 1, candidate.outgoing) ?: continue
                val quality = quality(candidate, incoming, tail)
                if (best == null || compare(quality, best.quality) < 0) {
                    best = DutchRound(candidate.pairs + tail.pairs, tail.bye, quality)
                    if (ideal(candidate, incoming, residents, tail, index, maxPairs)) {
                        cache[key] = best
                        return best
                    }
                }
            }
        }
        cache[key] = best
        return best
    }
    private fun candidates(incoming: List<Player>, residents: List<Player>, count: Int): Sequence<BracketCandidate> = sequence {
        if (incoming.isEmpty()) { yieldAll(homogeneous(residents, count)); return@sequence }
        for (m1 in minOf(incoming.size, residents.size, count) downTo 0) {
            for (chosen in combinations(incoming, m1)) {
                val limbo = incoming.filter { it !in chosen }
                for (mdp in matchSubgroups(chosen, residents)) {
                    val remainder = residents.filter { p -> mdp.none { it.second.id == p.id } }
                    for (rest in homogeneous(remainder, count - m1)) {
                        yield(BracketCandidate(mdp + rest.pairs, (limbo + rest.outgoing).sortedWith(rank)))
                    }
                }
            }
        }
    }
    private fun homogeneous(group: List<Player>, count: Int): Sequence<BracketCandidate> = sequence {
        if (count > group.size / 2 || count < 0) return@sequence
        val original1 = group.take(count)
        val original2 = group.drop(count)
        for (exchanges in 0..count) {
            // Article 4.3: number, sum difference, largest departing BSN, smallest arriving BSN.
            val changes = mutableListOf<Pair<List<Player>, List<Player>>>()
            for (out in combinations(original1, exchanges)) for (`in` in combinations(original2, exchanges)) {
                step(); changes.add(out to `in`)
            }
            changes.sortWith { a, b ->
                fun sum(change: Pair<List<Player>, List<Player>>) = change.second.sumOf { group.indexOf(it) } - change.first.sumOf { group.indexOf(it) }
                val difference = sum(a).compareTo(sum(b))
                if (difference != 0) difference else {
                    val out = compare(a.first.map { -group.indexOf(it).toDouble() }.sorted(), b.first.map { -group.indexOf(it).toDouble() }.sorted())
                    if (out != 0) out else compare(a.second.map { group.indexOf(it).toDouble() }, b.second.map { group.indexOf(it).toDouble() })
                }
            }
            for ((out, `in`) in changes) {
                val s1 = (original1.filter { it !in out } + `in`).sortedWith(rank)
                val s2 = (original2.filter { it !in `in` } + out).sortedWith(rank)
                for (pairs in matchSubgroups(s1, s2)) {
                    step()
                    val paired = pairs.flatMap { listOf(it.first.id, it.second.id) }.toSet()
                    yield(BracketCandidate(pairs, group.filter { it.id !in paired }))
                }
            }
        }
    }
    private fun matchSubgroups(s1: List<Player>, s2: List<Player>): Sequence<List<Pair<Player, Player>>> = sequence {
        if (s1.isEmpty()) { yield(emptyList()); return@sequence }
        val first = s1.first()
        for (second in s2) {
            step()
            if (!legalSwissPair(first, second, data, topscorers)) continue
            for (rest in matchSubgroups(s1.drop(1), s2.filter { it.id != second.id })) yield(listOf(first to second) + rest)
        }
    }
    private fun <T> combinations(list: List<T>, count: Int): Sequence<List<T>> = sequence {
        if (count == 0) { yield(emptyList()); return@sequence }
        if (count > list.size) return@sequence
        for (i in 0..list.size - count) {
            step()
            for (tail in combinations(list.drop(i + 1), count - 1)) yield(listOf(list[i]) + tail)
        }
    }
    private fun colourCosts(pairs: List<Pair<Player, Player>>): List<Double> {
        var imbalance = 0; var repeated = 0; var denied = 0; var strong = 0
        for ((a0, b0) in pairs) {
            val (a, b) = if (rank.compare(a0, b0) <= 0) a0 to b0 else b0 to a0
            val colours = assignColors(a, b, data, initialColour)
            for ((p, colour) in listOf(a to colours.first, b to colours.second)) {
                if (abs((data.colorBalance[p.id] ?: 0) + if (colour == 'W') 1 else -1) > 2) imbalance++
                if (!checkColorSequence(data.colorSequence[p.id].orEmpty(), colour)) repeated++
                val pref = colourPreference(p.id, data)
                if (pref.colour != null && pref.colour != colour) {
                    denied++
                    if (pref.strength == 2) strong++
                }
            }
        }
        return listOf(imbalance.toDouble(), repeated.toDouble(), denied.toDouble(), strong.toDouble())
    }
    private fun quality(c: BracketCandidate, incoming: List<Player>, tail: DutchRound): List<Double> {
        val incomingIds = incoming.map { it.id }.toSet()
        val residentOut = c.outgoing.filter { it.id !in incomingIds }
        val mdpPairs = c.pairs.filter { it.first.id in incomingIds }
        val opponents = mdpPairs.map { it.second }
        fun differences(ago: Int, incomingFloat: Boolean) = mdpPairs.filter { floated(if (incomingFloat) it.first else it.second, ago, if (incomingFloat) -1 else 1) }.map { abs(score(it.first) - score(it.second)) }
        return listOf(tail.bye?.let(::score) ?: 0.0, c.outgoing.size.toDouble()) + vector(c.outgoing.map(::score)) +
            // C8 checks the following bracket's C5-C7 before lower priorities in this bracket.
            (if (tail.quality.isEmpty()) List(width + 2) { 0.0 } else tail.quality.take(width + 2)) +
            listOf(if (c.outgoing.size == 1 && c.outgoing[0].id == tail.bye?.id) (data.unplayedRounds[tail.bye.id] ?: 0).toDouble() else 0.0) +
            colourCosts(c.pairs) + listOf(
                residentOut.count { floated(it, 1, -1) }.toDouble(), opponents.count { floated(it, 1, 1) }.toDouble(),
                residentOut.count { floated(it, 2, -1) }.toDouble(), opponents.count { floated(it, 2, 1) }.toDouble()) +
            vector(differences(1, true)) + vector(differences(1, false)) + vector(differences(2, true)) + vector(differences(2, false))
    }
    private fun ideal(c: BracketCandidate, incoming: List<Player>, residents: List<Player>, tail: DutchRound, index: Int, maxPairs: Int): Boolean {
        // A proved lower bound allows the common perfect candidate to finish without enumerating permutations.
        if (c.pairs.size != maxPairs) return false
        if (c.outgoing.isNotEmpty() && index != groups.lastIndex) return false
        val lowestBye = players.filter { it.id !in data.byes }.minOfOrNull(::score)
        if (tail.bye != null && score(tail.bye) != lowestBye) return false
        if (tail.bye != null && (data.unplayedRounds[tail.bye.id] ?: 0) != players.filter { it.id !in data.byes && score(it) == lowestBye }.minOfOrNull { data.unplayedRounds[it.id] ?: 0 }) return false
        val prefs = (incoming + residents).map { colourPreference(it.id, data) }
        fun deniedBound(strongOnly: Boolean): Int {
            val relevant = prefs.filter { !strongOnly || it.strength == 2 }
            val difference = abs(relevant.count { it.colour == 'W' } - relevant.count { it.colour == 'B' })
            val noPreference = prefs.size - relevant.count { it.colour != null }
            return maxOf(0, (difference - noPreference - c.outgoing.size + 1) / 2)
        }
        if (colourCosts(c.pairs) != listOf(0.0, 0.0, deniedBound(false).toDouble(), deniedBound(true).toDouble())) return false
        val incomingIds = incoming.map { it.id }.toSet()
        if (c.outgoing.any { it.id !in incomingIds && (floated(it, 1, -1) || floated(it, 2, -1)) }) return false
        if (c.pairs.any { it.first.id in incomingIds && (floated(it.second, 1, 1) || floated(it.second, 2, 1) || floated(it.first, 1, -1) || floated(it.first, 2, -1)) }) return false
        return true
    }
}

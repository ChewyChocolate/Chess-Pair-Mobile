package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*
import kotlin.math.abs

class PairingException(message: String) : IllegalArgumentException(message)

private val titleOrder = listOf("GM", "IM", "WGM", "FM", "WIM", "CM", "WFM", "WCM")
val initialPlayerOrder: Comparator<Player> = compareByDescending<Player> { it.rating ?: 0 }
    .thenBy { titleOrder.indexOf(it.title?.uppercase()).let { index -> if (index < 0) titleOrder.size else index } }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }

/** Preserve assigned TPNs; assign late/unseeded entrants after the existing list. */
fun withPairingNumbers(players: List<Player>): List<Player> {
    val assigned = players.filter { it.pairingNumber > 0 }
    if (assigned.map { it.pairingNumber }.distinct().size != assigned.size) {
        throw PairingException("Pairing numbers must be unique. Review the player list.")
    }
    var number = assigned.maxOfOrNull { it.pairingNumber } ?: 0
    val newNumbers = players.filter { it.pairingNumber == 0 }.sortedWith(initialPlayerOrder)
        .associate { it.id to ++number }
    return players.map { if (it.pairingNumber > 0) it else it.copy(pairingNumber = newNumbers.getValue(it.id)) }
}

fun isPlayedResult(result: MatchResult?): Boolean = result in listOf(MatchResult.WHITE_WIN, MatchResult.BLACK_WIN, MatchResult.DRAW)

internal data class ColourPreference(val colour: Char?, val strength: Int)
internal fun colourPreference(id: String, data: ScoreData): ColourPreference {
    val balance = data.colorBalance[id] ?: 0
    val seq = data.colorSequence[id].orEmpty()
    val repeated = seq.size >= 2 && seq.takeLast(2).distinct().size == 1
    val colour = when {
        balance > 1 -> 'B'
        balance < -1 -> 'W'
        repeated -> if (seq.last() == 'W') 'B' else 'W'
        balance > 0 -> 'B'
        balance < 0 -> 'W'
        seq.isNotEmpty() -> if (seq.last() == 'W') 'B' else 'W'
        else -> null
    }
    return ColourPreference(colour, when { abs(balance) > 1 || repeated -> 3; balance != 0 -> 2; colour != null -> 1; else -> 0 })
}

/** C.04.3 article 5, in descending priority. Inputs must be ranked first. */
fun assignColors(p1: Player, p2: Player, data: ScoreData, initialColour: Char = 'W'): Pair<Char, Char> {
    val a = colourPreference(p1.id, data)
    val b = colourPreference(p2.id, data)
    fun allocation(colour: Char) = colour to if (colour == 'W') 'B' else 'W'
    if (a.colour != null && (b.colour == null || a.colour != b.colour)) return allocation(a.colour)
    if (b.colour != null && a.colour == null) return allocation(if (b.colour == 'W') 'B' else 'W')
    if (a.colour != null && a.strength != b.strength) return allocation(if (a.strength > b.strength) a.colour else if (b.colour == 'W') 'B' else 'W')
    if (a.strength == 3 && b.strength == 3) {
        val difference = abs(data.colorBalance[p1.id] ?: 0) - abs(data.colorBalance[p2.id] ?: 0)
        if (difference != 0) return allocation(if (difference > 0) a.colour!! else if (b.colour == 'W') 'B' else 'W')
    }
    val seq1 = data.colorSequence[p1.id].orEmpty().asReversed()
    val seq2 = data.colorSequence[p2.id].orEmpty().asReversed()
    for (i in 0 until minOf(seq1.size, seq2.size)) {
        if (seq1[i] != seq2[i]) return allocation(if (seq1[i] == 'W') 'B' else 'W')
    }
    if (a.colour != null) return allocation(a.colour)
    return allocation(if (p1.pairingNumber % 2 != 0) initialColour else if (initialColour == 'W') 'B' else 'W')
}

internal fun legalSwissPair(a: Player, b: Player, data: ScoreData, topscorers: Set<String> = emptySet()): Boolean {
    if (a.id == b.id || b.id in data.played[a.id].orEmpty()) return false
    val pa = colourPreference(a.id, data)
    val pb = colourPreference(b.id, data)
    return !(a.id !in topscorers && b.id !in topscorers && pa.strength == 3 && pb.strength == 3 && pa.colour == pb.colour)
}

// Retained for callers/tests: legality is never represented by a weighted penalty.
@Suppress("UNUSED_PARAMETER")
fun pairQuality(p1: Player, p2: Player, data: ScoreData, avoidClubPairings: Boolean): Int =
    if (legalSwissPair(p1, p2, data)) 0 else -1

fun bakuVirtualPoints(tpn: Int, participantCount: Int, round: Int, totalRounds: Int, win: Double): Double {
    val groupACount = 2 * ((participantCount + 3) / 4)
    val acceleratedRounds = (totalRounds + 1) / 2
    return when {
        tpn > groupACount || round > acceleratedRounds -> 0.0
        round <= (acceleratedRounds + 1) / 2 -> win
        else -> win / 2
    }
}

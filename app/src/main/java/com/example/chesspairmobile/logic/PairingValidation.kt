package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*

/** Validate generated and manually edited rounds before persisting them. */
fun swissRoundIssue(players: List<Player>, history: List<Match>, proposed: List<Match>, round: Int,
    totalRounds: Int, win: Double, draw: Double, loss: Double, requireComplete: Boolean = true): String? {
    val available = players.filter { it.active && !it.withdrawn && round !in it.requestedByes }.map { it.id }.toSet() +
        history.filter { it.round == round }.flatMap { listOfNotNull(it.whiteId, it.blackId) }
    val ids = proposed.flatMap { listOfNotNull(it.whiteId, it.blackId) }
    if (ids.distinct().size != ids.size) return "A player is assigned more than once in this round."
    if (ids.any { it !in available } || (requireComplete && ids.toSet() != available)) return "Pair every available player exactly once before saving the round."
    if (proposed.any { it.round != round }) return "Pairings must belong to the current round."
    val data = calculateScores(players, history, win, draw, loss, round - 1)
    val topscorers = if (round == totalRounds) players.filter { (data.scores[it.id] ?: 0.0) > (round - 1) * win / 2 }.map { it.id }.toSet() else emptySet()
    val byes = proposed.filter { it.whiteId == null || it.blackId == null }
    if (byes.size > available.size % 2 || (requireComplete && byes.size != available.size % 2) || byes.any { it.result != MatchResult.BYE }) return "Swiss rounds allow exactly one pairing bye when the field is odd."
    if (byes.any { (it.whiteId ?: it.blackId) in data.byes }) return "A previous bye or full-point forfeit winner cannot receive the pairing bye."
    for (m in proposed.filter { it.whiteId != null && it.blackId != null }) {
        val a = players.first { it.id == m.whiteId }
        val b = players.first { it.id == m.blackId }
        if (!legalSwissPair(a, b, data, topscorers)) return "This pairing repeats played opponents or conflicts with absolute colour preferences."
        if (a.id !in topscorers && b.id !in topscorers &&
            (colourPreference(a.id, data).let { it.strength == 3 && it.colour != 'W' } || colourPreference(b.id, data).let { it.strength == 3 && it.colour != 'B' })) {
            return "This colour assignment violates an absolute colour preference."
        }
    }
    return null
}

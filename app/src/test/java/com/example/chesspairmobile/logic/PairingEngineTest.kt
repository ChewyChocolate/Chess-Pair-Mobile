package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.AccelerationMode
import com.example.chesspairmobile.data.models.Match
import com.example.chesspairmobile.data.models.MatchResult
import com.example.chesspairmobile.data.models.Player
import com.example.chesspairmobile.data.models.TournamentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingEngineTest {

    private fun player(id: String, rating: Int = 1500, name: String = id): Player = Player(
        id = id, name = name, rating = rating, pairingNumber = 0,
        active = true, withdrawn = false
    )

    private fun bye(id: String, round: Int): Match = Match(
        round = round, whiteId = id, blackId = null, result = MatchResult.BYE, boardNumber = 999
    )

    @Test
    fun `calculateScores gives 1 point for a win`() {
        val p1 = player("p1")
        val p2 = player("p2")
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.WHITE_WIN, boardNumber = 1)
        )
        val data = calculateScores(listOf(p1, p2), matches, 1.0, 0.5, 0.0)
        assertEquals(1.0, data.scores["p1"]!!, 0.0)
        assertEquals(0.0, data.scores["p2"]!!, 0.0)
    }

    @Test
    fun `calculateScores gives half point for a draw`() {
        val p1 = player("p1")
        val p2 = player("p2")
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.DRAW, boardNumber = 1)
        )
        val data = calculateScores(listOf(p1, p2), matches, 1.0, 0.5, 0.0)
        assertEquals(0.5, data.scores["p1"]!!, 0.0)
        assertEquals(0.5, data.scores["p2"]!!, 0.0)
    }

    @Test
    fun `calculateScores tracks played opponents`() {
        val p1 = player("p1")
        val p2 = player("p2")
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.WHITE_WIN, boardNumber = 1)
        )
        val data = calculateScores(listOf(p1, p2), matches, 1.0, 0.5, 0.0)
        assertTrue(data.played["p1"]!!.contains("p2"))
        assertTrue(data.played["p2"]!!.contains("p1"))
    }

    @Test
    fun `calculateScores counts forfeits as full point for winner`() {
        val p1 = player("p1")
        val p2 = player("p2")
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.FORFEIT_WHITE, boardNumber = 1)
        )
        val data = calculateScores(listOf(p1, p2), matches, 1.0, 0.5, 0.0)
        assertEquals(0.0, data.scores["p1"]!!, 0.0)
        assertEquals(1.0, data.scores["p2"]!!, 0.0)
    }

    @Test
    fun `calculateScores applies penalty points`() {
        val p1 = player("p1").copy(penaltyPoints = 0.5)
        val p2 = player("p2")
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.WHITE_WIN, boardNumber = 1)
        )
        val data = calculateScores(listOf(p1, p2), matches, 1.0, 0.5, 0.0)
        assertEquals(0.5, data.scores["p1"]!!, 0.0)
    }

    @Test
    fun `generateRoundRobin pairs all players in even round`() {
        val players = (1..4).map { player("p$it") }
        val matches = generateRoundRobin(players, round = 1)
        assertEquals(2, matches.size)
        val allParticipants = matches.flatMap { listOfNotNull(it.whiteId, it.blackId) }
        assertEquals(4, allParticipants.toSet().size)
    }

    @Test
    fun `generateRoundRobin handles odd number with bye`() {
        val players = (1..5).map { player("p$it") }
        val matches = generateRoundRobin(players, round = 1)
        assertEquals(3, matches.size) // 2 real + 1 bye
        assertEquals(1, matches.count { it.result == MatchResult.REST })
    }

    @Test
    fun `generateRoundRobin avoids rematches in first two rounds`() {
        val players = (1..4).map { player("p$it") }
        val r1 = generateRoundRobin(players, round = 1)
        val r2 = generateRoundRobin(players, round = 2)
        val r1Pairs = r1.map { setOfNotNull(it.whiteId, it.blackId) }
        val r2Pairs = r2.map { setOfNotNull(it.whiteId, it.blackId) }
        r1Pairs.forEach { p1 ->
            r2Pairs.forEach { p2 ->
                assertFalse("Round 2 should not repeat round 1 pair $p1", p1 == p2)
            }
        }
    }

    @Test
    fun `generateKnockout round 1 produces power-of-two bracket with byes`() {
        val players = (1..5).map { player("p$it") }
        val matches = generateKnockout(players, emptyList(), round = 1)
        val byeMatches = matches.filter { it.result == MatchResult.BYE }
        val realMatches = matches.filter { it.result == null }
        // 5 players → 8 bracket, 3 byes
        assertEquals(3, byeMatches.size)
        assertEquals(1, realMatches.size)
        assertEquals(2, realMatches.first().whiteId?.let { w -> realMatches.first().blackId?.let { b -> listOf(w, b).toSet().size } })
    }

    @Test
    fun `generateFideDutch produces exactly one bye for odd players`() {
        val players = (1..5).map { player("p$it", rating = 1500 + it) }
        val matches = generateFideDutch(players, emptyList(), 1, totalRounds = 5,
            pointsForWin = 1.0, pointsForDraw = 0.5, pointsForLoss = 0.0,
            avoidClubPairings = false, forcedPairings = emptyList())
        val byeCount = matches.count { it.result == MatchResult.BYE }
        val realCount = matches.count { it.result == null }
        assertEquals(1, byeCount)
        assertEquals(2, realCount)
    }

    @Test
    fun `generateFideDutch does not repeat opponents from completed rounds`() {
        val players = (1..4).map { player("p$it", rating = 1500 + it) }
        val r1 = generateFideDutch(players, emptyList(), 1, 5, 1.0, 0.5, 0.0, false, emptyList())
            .map { it.copy(result = MatchResult.DRAW) }
        val r2 = generateFideDutch(players, r1, 2, 5, 1.0, 0.5, 0.0, false, emptyList())
        val old = r1.map { setOf(it.whiteId, it.blackId) }.toSet()
        assertTrue(r2.none { setOf(it.whiteId, it.blackId) in old })
    }

    @Test
    fun `generateFideDutch honors forced pairings`() {
        val players = (1..4).map { player("p$it", rating = 1500 + it) }
        val forced = com.example.chesspairmobile.data.models.ForcedPairing("p1", "p4")
        val matches = generateFideDutch(players, emptyList(), 1, 5, 1.0, 0.5, 0.0, false, listOf(forced))
        val matching = matches.firstOrNull { it.whiteId == "p1" && it.blackId == "p4" }
        assertNotNull(matching)
    }

    @Test
    fun `checkColorSequence rejects three in a row`() {
        assertTrue(checkColorSequence(listOf('W', 'B'), 'W'))
        assertFalse(checkColorSequence(listOf('W', 'W'), 'W'))
    }

    @Test
    fun `NONE acceleration preserves the opening field`() {
        val players = (1..8).map { player("p$it", rating = 1500 + it) }
        val out = generateFideDutch(players, emptyList(), 1, 5, 1.0, 0.5, 0.0, false, emptyList(), AccelerationMode.NONE)
        assertEquals(players.map { it.id }.toSet(), out.flatMap { listOfNotNull(it.whiteId, it.blackId) }.toSet())
    }

    @Test
    fun `BAKU acceleration preserves the opening field`() {
        val players = (1..8).map { player("p$it", rating = 1500 + it * 100) }
        val out = generateFideDutch(players, emptyList(), 1, 5, 1.0, 0.5, 0.0, false, emptyList(), AccelerationMode.BAKU)
        assertEquals(players.map { it.id }.toSet(), out.flatMap { listOfNotNull(it.whiteId, it.blackId) }.toSet())
    }

    @Test
    fun `generateKnockout round 2 advances winners of round 1`() {
        val players = (1..4).map { player("p$it") }
        val r1 = generateKnockout(players, emptyList(), round = 1)
        // Mark round 1 winners
        val completedR1 = r1.map { m ->
            if (m.blackId != null) m.copy(result = MatchResult.WHITE_WIN) else m
        }
        val r2 = generateKnockout(players, completedR1, round = 2)
        // 4 players → 2 winners → 1 final match
        assertEquals(1, r2.size)
        assertNotNull(r2.first().whiteId)
        assertNotNull(r2.first().blackId)
    }

    @Test
    fun `generateKnockout rejects n=1 in round 1`() {
        val players = listOf(player("p1"))
        val matches = generateKnockout(players, emptyList(), round = 1)
        assertTrue("Single player should produce no matches", matches.isEmpty())
    }

    @Test
    fun `generateKnockout with 2 players in round 1 produces one match`() {
        val players = (1..2).map { player("p$it") }
        val matches = generateKnockout(players, emptyList(), round = 1)
        assertEquals(1, matches.size)
        // Order depends on shuffle; just check both are present
        val pair = setOf(matches[0].whiteId, matches[0].blackId)
        assertEquals(setOf("p1", "p2"), pair)
    }

    @Test(expected = PairingException::class)
    fun `round robin rejects club avoidance instead of replacing games with byes`() {
        generateRoundRobin((1..4).map { player("p$it").copy(club = "A") }, 1, true)
    }

    @Test
    fun `generateRoundRobin without avoidClubPairings has no club restriction`() {
        val players = listOf(
            player("p1").copy(club = "A"),
            player("p2").copy(club = "A")
        )
        val matches = generateRoundRobin(players, round = 1, avoidClubPairings = false)
        assertEquals(1, matches.size)
        assertEquals("p1", matches[0].whiteId)
        assertEquals("p2", matches[0].blackId)
    }

    @Test
    fun `generateRoundRobin with empty player list returns empty`() {
        val matches = generateRoundRobin(emptyList(), round = 1)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `generateFideDutch BAKU acceleration does not lose players`() {
        val players = (1..8).map { player("p$it", rating = 1500 + it * 100) }
        val matches = generateFideDutch(players, emptyList(), 1, 5, 1.0, 0.5, 0.0,
            false, emptyList(), AccelerationMode.BAKU)
        val allPlayers = matches.flatMap { listOfNotNull(it.whiteId, it.blackId) }.toSet()
        assertEquals(8, allPlayers.size)
    }
}

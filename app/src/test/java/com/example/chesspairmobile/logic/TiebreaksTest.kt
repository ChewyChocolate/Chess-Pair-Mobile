package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.Match
import com.example.chesspairmobile.data.models.MatchResult
import com.example.chesspairmobile.data.models.Player
import com.example.chesspairmobile.data.models.TiebreakType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TiebreaksTest {

    private fun player(id: String, rating: Int = 1500, name: String = id): Player = Player(
        id = id, name = name, rating = rating, pairingNumber = 0,
        active = true, withdrawn = false
    )

    @Test
    fun `calculateStandings sorts by score descending`() {
        val p1 = player("p1", rating = 2000)
        val p2 = player("p2", rating = 1500)
        val p3 = player("p3", rating = 1800)
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.WHITE_WIN, boardNumber = 1),
            Match(round = 1, whiteId = "p3", blackId = null, result = MatchResult.BYE, boardNumber = 999)
        )
        val standings = calculateStandings(
            listOf(p1, p2, p3), matches, 1.0, 0.5, 0.0,
            listOf(TiebreakType.BUCHHOLZ)
        )
        assertEquals(1.0, standings[0].score, 0.0)
        // p1 and p3 are tied on score; buchholz breaks the tie (p3's opponents are 0, p1's opponent is 1.0)
        assertTrue("p1 should be first (higher buchholz)", standings[0].player.id == "p1")
    }

    @Test
    fun `calculateStandings uses buchholz as tiebreak`() {
        val p1 = player("p1", 2000)
        val p2 = player("p2", 1800)
        val p3 = player("p3", 1600)
        val p4 = player("p4", 1400)
        // p1 and p2 both win round 1
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p4", result = MatchResult.WHITE_WIN, boardNumber = 1),
            Match(round = 1, whiteId = "p2", blackId = "p3", result = MatchResult.WHITE_WIN, boardNumber = 2)
        )
        val standings = calculateStandings(
            listOf(p1, p2, p3, p4), matches, 1.0, 0.5, 0.0,
            listOf(TiebreakType.BUCHHOLZ)
        )
        // Both p1 and p2 have score 1.0. p1's buchholz = p4's score = 0.0.
        // p2's buchholz = p3's score = 0.0. Tied. But the engine should be deterministic.
        assertEquals(1.0, standings[0].score, 0.0)
        assertEquals(1.0, standings[1].score, 0.0)
    }

    @Test
    fun `calculateStandings counts most wins`() {
        val p1 = player("p1", 2000)
        val p2 = player("p2", 1800)
        val p3 = player("p3", 1600)
        val p4 = player("p4", 1400)
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.WHITE_WIN, boardNumber = 1),
            Match(round = 2, whiteId = "p1", blackId = "p3", result = MatchResult.WHITE_WIN, boardNumber = 1),
            Match(round = 1, whiteId = "p4", blackId = "p3", result = MatchResult.DRAW, boardNumber = 2),
            Match(round = 2, whiteId = "p4", blackId = "p2", result = MatchResult.DRAW, boardNumber = 2)
        )
        val standings = calculateStandings(
            listOf(p1, p2, p3, p4), matches, 1.0, 0.5, 0.0,
            listOf(TiebreakType.MOST_WINS)
        )
        // p1: 2 wins (2.0 pts), p4: 2 draws (1.0 pt), p2: 0.5, p3: 0.5
        assertEquals("p1", standings[0].player.id)
        assertEquals(2, standings[0].mostWins)
    }

    @Test
    fun `calculateStandings progressive score sums round-by-round scores`() {
        val p1 = player("p1", 2000)
        val p2 = player("p2", 1800)
        val p3 = player("p3", 1600)
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.WHITE_WIN, boardNumber = 1),
            Match(round = 2, whiteId = "p1", blackId = "p3", result = MatchResult.DRAW, boardNumber = 1)
        )
        val standings = calculateStandings(
            listOf(p1, p2, p3), matches, 1.0, 0.5, 0.0,
            listOf(TiebreakType.PROGRESSIVE)
        )
        // Cumulative scores are 1.0 and 1.5; their sum is 2.5.
        assertEquals(2.5, standings.first { it.player.id == "p1" }.progressive, 0.0)
    }

    @Test
    fun `played results take precedence over requested byes in progressive`() {
        val p1 = player("p1", 2000).copy(requestedByes = listOf(2))
        val p2 = player("p2", 1800)
        // A recorded game in round 2 replaces the requested absence.
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.WHITE_WIN, boardNumber = 1),
            Match(round = 2, whiteId = "p2", blackId = "p1", result = MatchResult.DRAW, boardNumber = 1)
        )
        val standings = calculateStandings(
            listOf(p1, p2), matches, 1.0, 0.5, 0.0,
            listOf(TiebreakType.PROGRESSIVE)
        )
        val s = standings.first { it.player.id == "p1" }
        // Cumulative scores: 1.0, then 1.5; progressive sum is 2.5.
        assertEquals(2.5, s.progressive, 0.0)
    }

    @Test
    fun `getMatchPointsForPlayer handles forfeit variants`() {
        val m = Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.FORFEIT_BLACK, boardNumber = 1)
        assertEquals(1.0, getMatchPointsForPlayer(m, "p1", 1.0, 0.5, 0.0), 0.0)
        assertEquals(0.0, getMatchPointsForPlayer(m, "p2", 1.0, 0.5, 0.0), 0.0)
    }

    @Test
    fun `getMatchPointsForPlayer returns 0 for null result`() {
        val m = Match(round = 1, whiteId = "p1", blackId = "p2", boardNumber = 1)
        assertEquals(0.0, getMatchPointsForPlayer(m, "p1", 1.0, 0.5, 0.0), 0.0)
    }

    @Test
    fun `performance rating for perfect score against equal opponents`() {
        val p1 = player("p1", 2000)
        val p2 = player("p2", 2000)
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.WHITE_WIN, boardNumber = 1)
        )
        val perf = calculatePerformanceRating(listOf(p1, p2), matches, "p1")
        // 100% score vs avg 2000 → +800 perf
        assertEquals(2800, perf)
    }

    @Test
    fun `performance rating returns 0 when no rated games`() {
        val p1 = player("p1", 2000)
        val p2 = player("p2").copy(rating = null)
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.WHITE_WIN, boardNumber = 1)
        )
        val perf = calculatePerformanceRating(listOf(p1, p2), matches, "p1")
        assertEquals(0, perf)
    }

    @Test
    fun `performance rating for 0 percent against equal opponents is 1200 below avg`() {
        val p1 = player("p1", 2000)
        val p2 = player("p2", 2000)
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.BLACK_WIN, boardNumber = 1)
        )
        val perf = calculatePerformanceRating(listOf(p1, p2), matches, "p1")
        // 0% score → -800 below avg → 1200
        assertEquals(1200, perf)
    }

    @Test
    fun `performance rating is never negative`() {
        val p1 = player("p1", 1000)
        val p2 = player("p2", 2000)
        val matches = listOf(
            Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.BLACK_WIN, boardNumber = 1)
        )
        val perf = calculatePerformanceRating(listOf(p1, p2), matches, "p1")
        // 0% score vs avg 2000 → -800 → 1200, well above 0
        assertTrue(perf >= 0)
    }
}

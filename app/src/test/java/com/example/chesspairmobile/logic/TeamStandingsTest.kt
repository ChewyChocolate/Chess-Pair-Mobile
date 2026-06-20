package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.Match
import com.example.chesspairmobile.data.models.MatchResult
import com.example.chesspairmobile.data.models.Team
import com.example.chesspairmobile.data.models.TeamStanding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamStandingsTest {

    private fun team(id: String, vararg playerIds: String) = Team(
        id = id, name = id, playerIds = playerIds.toList()
    )

    private fun teamMatch(round: Int, teamMatchId: String, whiteId: String?, blackId: String?, result: MatchResult) =
        Match(
            round = round, whiteId = whiteId, blackId = blackId,
            result = result, boardNumber = 1, teamMatchId = teamMatchId
        )

    @Test
    fun `team with more game points wins the team match`() {
        val tA = team("A", "a1", "a2", "a3")
        val tB = team("B", "b1", "b2", "b3")
        val tm = "tm1"
        val matches = listOf(
            teamMatch(1, tm, "a1", "b1", MatchResult.WHITE_WIN),
            teamMatch(1, tm, "a2", "b2", MatchResult.WHITE_WIN),
            teamMatch(1, tm, "a3", "b3", MatchResult.BLACK_WIN)
        )
        val standings = calculateTeamStandings(listOf(tA, tB), matches)
        // A: 1 + 1 + 0 = 2 game points, B: 0 + 0 + 1 = 1 → A wins 2-0
        assertEquals(2.0, standings.first { it.team.id == "A" }.matchPoints, 0.0)
        assertEquals(0.0, standings.first { it.team.id == "B" }.matchPoints, 0.0)
        assertEquals(2.0, standings.first { it.team.id == "A" }.gamePoints, 0.0)
        assertEquals(1.0, standings.first { it.team.id == "B" }.gamePoints, 0.0)
    }

    @Test
    fun `tied team match awards 1 match point to each team`() {
        val tA = team("A", "a1", "a2")
        val tB = team("B", "b1", "b2")
        val tm = "tm1"
        val matches = listOf(
            teamMatch(1, tm, "a1", "b1", MatchResult.WHITE_WIN),
            teamMatch(1, tm, "a2", "b2", MatchResult.BLACK_WIN)
        )
        val standings = calculateTeamStandings(listOf(tA, tB), matches)
        // 1-1 tie → 1 match point each, 1 game point each
        assertEquals(1.0, standings.first { it.team.id == "A" }.matchPoints, 0.0)
        assertEquals(1.0, standings.first { it.team.id == "B" }.matchPoints, 0.0)
    }

    @Test
    fun `empty input returns empty standings`() {
        val standings = calculateTeamStandings(emptyList(), emptyList())
        assertTrue(standings.isEmpty())
    }

    @Test
    fun `standings are sorted by match points then game points`() {
        val tA = team("A", "a1", "a2")
        val tB = team("B", "b1", "b2")
        val tC = team("C", "c1", "c2")
        val matches = listOf(
            // A beats C 2-0
            teamMatch(1, "tm1", "a1", "c1", MatchResult.WHITE_WIN),
            teamMatch(1, "tm1", "a2", "c2", MatchResult.WHITE_WIN),
            // A beats B 1.5-0.5
            teamMatch(1, "tm2", "a1", "b1", MatchResult.WHITE_WIN),
            teamMatch(1, "tm2", "a2", "b2", MatchResult.DRAW),
            // B beats C 2-0
            teamMatch(1, "tm3", "b1", "c1", MatchResult.WHITE_WIN),
            teamMatch(1, "tm3", "b2", "c2", MatchResult.WHITE_WIN)
        )
        val standings = calculateTeamStandings(listOf(tA, tB, tC), matches)
        assertEquals("A", standings[0].team.id) // 4 MP
        assertEquals("B", standings[1].team.id) // 2 MP
        assertEquals("C", standings[2].team.id) // 0 MP
    }
}

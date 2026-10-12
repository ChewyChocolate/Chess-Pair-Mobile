package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*
import org.junit.Assert.*
import org.junit.Test

class TournamentWorkflowTest {
    private val players = listOf(Player(id = "a", name = "A"), Player(id = "b", name = "B"))

    @Test fun `team readiness requires two valid nonempty teams`() {
        assertNotNull(startRequirement(players, emptyList(), true))
        assertNotNull(startRequirement(players, listOf(Team(name = "One", playerIds = listOf("a", "b"))), true))
        assertNull(startRequirement(players, listOf(Team(name = "One", playerIds = listOf("a")), Team(name = "Two", playerIds = listOf("b"))), true))
        assertNotNull(startRequirement(players.map { it.copy(withdrawn = true) }, listOf(Team(name = "One", playerIds = listOf("a")), Team(name = "Two", playerIds = listOf("b"))), true))
    }

    @Test fun `completed and historical results need correction for their own round`() {
        assertTrue(canUpdateResult("ACTIVE", 2, 2, null))
        assertFalse(canUpdateResult("ACTIVE", 2, 1, null))
        assertFalse(canUpdateResult("COMPLETED", 2, 2, null))
        assertTrue(canUpdateResult("COMPLETED", 2, 2, 2))
        assertFalse(canUpdateResult("COMPLETED", 2, 1, 2))
        assertFalse(canUpdateResult("SETUP", 0, 1, 1))
        assertFalse(canUpdateResult("ACTIVE", 2, 3, 3))
    }

    @Test fun `requested byes reject invalid tokens and rounds beyond the tournament`() {
        assertNull(parseByeRounds("1, 99", 5))
        assertNull(parseByeRounds("1, bad", 5))
        assertNull(parseByeRounds("0", 5))
        assertEquals(listOf(1, 3), parseByeRounds("3, 1, 3", 5))
        assertEquals(emptyList<Int>(), parseByeRounds("", 5))
    }

    @Test fun `round export excludes later games and future requested byes and quotes names`() {
        val roster = listOf(players[0].copy(name = "A, \"Ace\"", requestedByes = listOf(3)), players[1])
        val matches = listOf(Match(round = 1, whiteId = "a", blackId = "b", result = MatchResult.WHITE_WIN), Match(round = 2, whiteId = "a", blackId = "b", result = MatchResult.WHITE_WIN))
        val csv = standingsCsv(roster, emptyList(), matches, 1, 1.0, 0.5, 0.0, emptyList(), false)
        assertTrue(csv.lines()[1].startsWith("1,1,\"A, \"\"Ace\"\"\",,1.0,"))
        val current = standingsCsv(roster, emptyList(), matches, 2, 1.0, 0.5, 0.0, emptyList(), false)
        assertTrue(current.lines()[1].startsWith("2,1,\"A, \"\"Ace\"\"\",,2.0,"))
    }

    @Test fun `team export follows the selected round`() {
        val teams = listOf(Team(id = "one", name = "One", playerIds = listOf("a")), Team(id = "two", name = "Two", playerIds = listOf("b")))
        val matches = listOf(Match(round = 1, teamMatchId = "first", whiteId = "a", blackId = "b", result = MatchResult.WHITE_WIN), Match(round = 2, teamMatchId = "second", whiteId = "a", blackId = "b", result = MatchResult.BLACK_WIN))
        val csv = standingsCsv(players, teams, matches, 1, 1.0, 0.5, 0.0, emptyList(), true)
        assertTrue(csv.lines()[1].startsWith("1,1,\"One\",2.0,1.0,"))
    }

    @Test fun `partial team results do not decide the team match`() {
        val teams = listOf(Team(id = "one", name = "One", playerIds = listOf("a", "a2")), Team(id = "two", name = "Two", playerIds = listOf("b", "b2")))
        val matches = listOf(Match(round = 1, teamMatchId = "match", whiteId = "a", blackId = "b", result = MatchResult.WHITE_WIN), Match(round = 1, teamMatchId = "match", whiteId = "a2", blackId = "b2"))
        val standings = calculateTeamStandings(teams, matches, 1)
        assertTrue(standings.all { it.matchPoints == 0.0 && it.sonnebornBerger == 0.0 })
        assertEquals(1.0, standings.first { it.team.id == "one" }.gamePoints, 0.0)
    }

    @Test fun `unequal boards and team byes award game points to the correct side`() {
        val teams = listOf(Team(id = "one", name = "One", playerIds = listOf("a", "a2")), Team(id = "two", name = "Two", playerIds = listOf("b")))
        val matches = listOf(Match(round = 1, teamMatchId = "match", whiteId = "a2", result = MatchResult.BYE), Match(round = 1, teamMatchId = "match", whiteId = "b", blackId = "a", result = MatchResult.BLACK_WIN))
        val standings = calculateTeamStandings(teams, matches, 1)
        assertEquals(2.0, standings.first { it.team.id == "one" }.gamePoints, 0.0)
        assertEquals(2.0, standings.first { it.team.id == "one" }.matchPoints, 0.0)
        val bye = calculateTeamStandings(teams, listOf(Match(round = 1, teamMatchId = "bye", whiteId = "a", result = MatchResult.BYE), Match(round = 1, teamMatchId = "bye", whiteId = "a2", result = MatchResult.BYE)), 1)
        assertEquals(2.0, bye.first { it.team.id == "one" }.gamePoints, 0.0)
        assertEquals(2.0, bye.first { it.team.id == "one" }.matchPoints, 0.0)
    }
}

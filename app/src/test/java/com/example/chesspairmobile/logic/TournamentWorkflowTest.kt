package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*
import org.junit.Assert.*
import org.junit.Test

class TournamentWorkflowTest {
    private val players = listOf(Player(id = "a", name = "A"), Player(id = "b", name = "B"))

    @Test fun `knockout round requires deciding results before either advancing or finishing`() {
        val drawn = listOf(Match(round = 1, whiteId = "a", blackId = "b", result = MatchResult.DRAW))
        assertNotNull(roundCompletionIssue("KNOCKOUT", false, drawn))
        assertNull(roundCompletionIssue("SWISS", false, drawn))
        assertNull(roundCompletionIssue("KNOCKOUT", true, drawn))
        val decided = drawn.map { it.copy(result = MatchResult.BLACK_WIN) }
        assertNull(roundCompletionIssue("KNOCKOUT", false, decided))
        assertTrue(generateKnockout(players, decided, 2).isEmpty())
        assertNotNull(roundCompletionIssue("SWISS", false, emptyList()))
    }

    @Test fun `bye results must count in round completeness`() {
        val board = Match(round = 1, whiteId = "a", blackId = "b", result = MatchResult.WHITE_WIN)
        val bye = Match(round = 1, whiteId = "c", result = MatchResult.BYE)
        assertNull(roundCompletionIssue("SWISS", false, listOf(board, bye)))
        assertNotNull(roundCompletionIssue("SWISS", false, listOf(board, bye.copy(result = null))))
    }

    @Test fun `team knockout allows drawn boards but requires an aggregate winner`() {
        val teams = listOf(Team(id = "one", name = "One", playerIds = listOf("a", "a2")),
            Team(id = "two", name = "Two", playerIds = listOf("b", "b2")))
        val boards = listOf(Match(round = 1, whiteId = "a", blackId = "b", result = MatchResult.DRAW, teamMatchId = "match"),
            Match(round = 1, whiteId = "b2", blackId = "a2", result = MatchResult.DRAW, teamMatchId = "match"))
        assertNotNull(roundCompletionIssue("KNOCKOUT", true, boards, teams))
        assertNull(roundCompletionIssue("SWISS", true, boards, teams))
        assertNull(roundCompletionIssue("KNOCKOUT", true, boards.mapIndexed { index, match ->
            if (index == 1) match.copy(result = MatchResult.WHITE_WIN) else match
        }, teams))
    }

    @Test fun `forced pairings are supported only in individual Swiss and never overlap`() {
        assertTrue(supportsForcedPairings("SWISS", false))
        assertFalse(supportsForcedPairings("SWISS", true))
        assertFalse(supportsForcedPairings("ROUND_ROBIN", false))
        assertFalse(supportsForcedPairings("KNOCKOUT", false))
        val pair = ForcedPairing("a", "b")
        assertNull(forcedPairingIssue(pair, emptyList(), players))
        assertNotNull(forcedPairingIssue(pair, listOf(ForcedPairing("b", "c")), players))
        assertNotNull(forcedPairingIssue(ForcedPairing("a", "a"), emptyList(), players))
        assertNotNull(forcedPairingIssue(pair, emptyList(), players.map { it.copy(withdrawn = true) }))
    }

    @Test fun `standings distinguish an undrawn round from complete and provisional results`() {
        assertEquals("Round 1 has not started · no results yet", standingsRoundLabel(1, emptyList()))
        val completed = listOf(Match(round = 1, whiteId = "a", blackId = "b", result = MatchResult.WHITE_WIN))
        assertEquals("Standings through Round 1", standingsRoundLabel(1, completed))
        assertEquals("Round 2 has not started · standings through Round 1", standingsRoundLabel(2, completed))
        val pending = completed + Match(round = 2, whiteId = "a", blackId = "b")
        assertEquals("Standings through Round 2 · provisional", standingsRoundLabel(2, pending))
        assertEquals(0, standingsSnapshotRound(1, emptyList()))
        assertEquals(1, standingsSnapshotRound(2, completed))
        assertEquals(2, standingsSnapshotRound(2, pending))
        val roster = players.map { it.copy(requestedByes = listOf(2)) }
        val actualRound = standingsSnapshotRound(2, completed)
        val scores = calculateStandings(roster, completed, 1.0, 0.5, 0.0, emptyList(), actualRound)
        assertEquals(1.0, scores.first { it.player.id == "a" }.score, 0.0)
    }

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

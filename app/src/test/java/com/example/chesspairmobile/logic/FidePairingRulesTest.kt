package com.example.chesspairmobile.logic

import com.example.chesspairmobile.data.models.*
import org.junit.Assert.*
import org.junit.Test

class FidePairingRulesTest {
    private fun players(n: Int) = (1..n).map { Player(id = "p$it", name = "Player $it", rating = 2500 - 50 * it, pairingNumber = it) }
    private fun swiss(p: List<Player>, history: List<Match> = emptyList(), round: Int = 1, mode: AccelerationMode = AccelerationMode.NONE, forced: List<ForcedPairing> = emptyList()) =
        generateFideDutch(p, history, round, 7, 1.0, 0.5, 0.0, false, forced, mode)
    private fun opponents(matches: List<Match>) = matches.filter { it.whiteId != null && it.blackId != null }.map { setOf(it.whiteId, it.blackId) }.toSet()

    @Test fun `Dutch opening splits both small and large fields into halves`() {
        for (n in listOf(4, 8, 20, 64)) {
            val round = swiss(players(n))
            val expected = (1..n / 2).map { setOf("p$it", "p${it + n / 2}") }.toSet()
            assertEquals(expected, opponents(round))
        }
    }
    @Test fun `bye choice preserves completion instead of repeating opponents`() {
        val history = listOf(Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.WHITE_WIN),
            Match(round = 2, whiteId = "p1", blackId = "p3", result = MatchResult.WHITE_WIN))
        val round = swiss(players(3), history, 3)
        assertEquals("p1", round.single { it.result == MatchResult.BYE }.whiteId)
        assertEquals(setOf(setOf("p2", "p3")), opponents(round))
    }
    @Test fun `impossible repeat and second bye fail without partial results`() {
        assertThrows(PairingException::class.java) { swiss(players(2), listOf(Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.DRAW)), 2) }
        val allByed = players(3).map { Match(round = 1, whiteId = it.id, result = MatchResult.BYE) }
        assertThrows(PairingException::class.java) { swiss(players(3), allByed, 2) }
    }
    @Test fun `absolute colours are hard restrictions and mild preferences alternate`() {
        val data = ScoreData()
        players(2).forEach { data.colorBalance[it.id] = 2; data.colorSequence[it.id] = mutableListOf('W', 'W') }
        assertEquals(-1, pairQuality(players(2)[0], players(2)[1], data, false))
        data.colorBalance["p1"] = 0; data.colorBalance["p2"] = 0
        data.colorSequence["p1"] = mutableListOf('B', 'W'); data.colorSequence["p2"] = mutableListOf('W', 'B')
        assertEquals('B' to 'W', assignColors(players(2)[0], players(2)[1], data))
    }
    @Test fun `drawn initial colour and TPN parity are respected`() {
        val round = generateFideDutch(players(4), emptyList(), 1, 5, 1.0, 0.5, 0.0, false, emptyList(), initialColour = 'B')
        assertEquals("p3", round[0].whiteId)
        assertEquals("p2", round[1].whiteId)
    }
    @Test fun `unplayed forfeits earn points and exclude their winner from a bye without colour history`() {
        val data = calculateScores(players(2), listOf(Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.FORFEIT_WHITE)), 1.0, 0.5, 0.0)
        assertEquals(1.0, data.scores["p2"]!!, 0.0)
        assertTrue(data.played.getValue("p1").isEmpty())
        assertTrue(data.colorSequence.getValue("p1").isEmpty())
        assertTrue("p2" in data.byes)
        assertEquals(listOf(-1), data.floatHistory["p2"])
    }
    @Test fun `future requested byes do not change current scores or opening opponents`() {
        val roster = players(4).map { if (it.id == "p1") it.copy(requestedByes = listOf(7)) else it }
        assertEquals(0.0, calculateScores(roster, emptyList(), 1.0, 0.5, 0.0).scores["p1"]!!, 0.0)
        assertEquals(opponents(swiss(players(4))), opponents(swiss(roster)))
        assertEquals(0.5, calculateScores(roster, emptyList(), 1.0, 0.5, 0.0, 7).scores["p1"]!!, 0.0)
    }
    @Test fun `requested bye float is recorded in its actual round and not double scored`() {
        val p = players(2).map { if (it.id == "p1") it.copy(requestedByes = listOf(1)) else it }
        val data = calculateScores(p, listOf(Match(round = 2, whiteId = "p1", blackId = "p2", result = MatchResult.DRAW)), 1.0, 0.5, 0.0, 2)
        assertEquals(listOf(-1, -1), data.floatHistory["p1"])
        val explicit = calculateScores(p, listOf(Match(round = 1, whiteId = "p1", result = MatchResult.BYE)), 1.0, 0.5, 0.0, 1)
        assertEquals(1.0, explicit.scores["p1"]!!, 0.0)
    }
    @Test fun `Baku follows the nine round virtual point schedule and changes opponents`() {
        assertEquals(listOf(1.0, 1.0, 1.0, 0.5, 0.5, 0.0, 0.0, 0.0, 0.0), (1..9).map { bakuVirtualPoints(1, 8, it, 9, 1.0) })
        assertEquals(0.0, bakuVirtualPoints(5, 8, 1, 9, 1.0), 0.0)
        assertEquals(1.0, bakuVirtualPoints(82, 161, 1, 9, 1.0), 0.0)
        assertEquals(0.0, bakuVirtualPoints(83, 161, 1, 9, 1.0), 0.0)
        assertEquals(setOf(setOf("p1", "p3"), setOf("p2", "p4"), setOf("p5", "p7"), setOf("p6", "p8")), opponents(swiss(players(8), mode = AccelerationMode.BAKU)))
        assertThrows(PairingException::class.java) { swiss(players(8), mode = AccelerationMode.LIM) }
    }
    @Test fun `forced rematches and duplicate player assignments are rejected`() {
        val previous = listOf(Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.DRAW))
        assertThrows(PairingException::class.java) { swiss(players(4), previous, 2, forced = listOf(ForcedPairing("p1", "p2"))) }
        assertThrows(PairingException::class.java) { swiss(players(4), forced = listOf(ForcedPairing("p1", "p2"), ForcedPairing("p1", "p3"))) }
        val round = swiss(players(4))
        assertNotNull(swissRoundIssue(players(4), emptyList(), round + round[0], 1, 7, 1.0, 0.5, 0.0))
    }
    @Test fun `initial ordering resolves rating ties by FIDE title then name`() {
        val roster = listOf(Player(id = "a", name = "Aaron", rating = 2000), Player(id = "z", name = "Zoe", rating = 2000, title = "GM"), Player(id = "b", name = "Bob", rating = 2000))
        assertEquals(listOf("z", "a", "b"), withPairingNumbers(roster).sortedBy { it.pairingNumber }.map { it.id })
    }
    @Test fun `Berger tables match published four and six player fixtures`() {
        assertEquals(listOf(listOf(1 to 4, 2 to 3), listOf(4 to 3, 1 to 2), listOf(2 to 4, 3 to 1)), (1..3).map { bergerPairs(4, it) })
        assertEquals(listOf(listOf(1 to 6, 2 to 5, 3 to 4), listOf(6 to 4, 5 to 3, 1 to 2), listOf(2 to 6, 3 to 1, 4 to 5), listOf(6 to 5, 1 to 4, 2 to 3), listOf(3 to 6, 4 to 2, 5 to 1)), (1..5).map { bergerPairs(6, it) })
    }
    @Test fun `every Berger opponent occurs once and colours are balanced`() {
        for (n in 3..24) {
            val rounds = if (n % 2 == 0) n - 1 else n
            val matches = (1..rounds).flatMap { generateRoundRobin(players(n), it) }
            assertEquals(n * (n - 1) / 2, opponents(matches).size)
            for (p in players(n)) {
                assertTrue(kotlin.math.abs(matches.count { it.whiteId == p.id && it.blackId != null } - matches.count { it.blackId == p.id }) <= 1)
                assertEquals(if (n % 2 == 0) 0 else 1, matches.count { it.whiteId == p.id && it.result == MatchResult.REST })
            }
        }
    }
    @Test fun `round robin absences and withdrawals keep the frozen Berger schedule`() {
        val roster = players(4).map { if (it.id == "p2") it.copy(requestedByes = listOf(2)) else it }
        assertEquals(opponents(generateRoundRobin(players(4), 2)), opponents(generateRoundRobin(roster, 2)))
        assertTrue(generateRoundRobin(roster, 2).any { it.result == MatchResult.FORFEIT_BLACK })
        assertEquals(opponents(generateRoundRobin(players(4), 2)), opponents(generateRoundRobin(roster.map { if (it.id == "p3") it.copy(withdrawn = true) else it }, 2)))
    }
    @Test fun `round robin rest scores zero`() {
        val p = players(3)
        val round = generateRoundRobin(p, 1).map { if (it.result == null) it.copy(result = MatchResult.DRAW) else it }
        val resting = round.single { it.result == MatchResult.REST }.whiteId!!
        assertEquals(0.0, calculateScores(p, round, 1.0, 0.5, 0.0).scores[resting]!!, 0.0)
    }
    @Test fun `knockout bracket paths survive reordered database records and exclude withdrawals`() {
        val p = players(8)
        val first = generateKnockout(p, emptyList(), 1).map { it.copy(result = MatchResult.WHITE_WIN) }
        val second = generateKnockout(p, first, 2)
        assertEquals(opponents(second), opponents(generateKnockout(p, first.reversed(), 2)))
        val withdrawn = p.map { if (it.id == first[0].whiteId) it.copy(withdrawn = true) else it }
        val next = generateKnockout(withdrawn, first, 2)
        assertTrue(next.none { it.whiteId == first[0].whiteId || it.blackId == first[0].whiteId })
        assertEquals(1, next.count { it.result == MatchResult.BYE })
    }
    @Test fun `twenty player Swiss rounds retain absolute invariants`() {
        val p = players(20)
        var history = emptyList<Match>()
        for (round in 1..4) {
            val paired = swiss(p, history, round)
            assertNull(swissRoundIssue(p, history, paired, round, 7, 1.0, 0.5, 0.0))
            history = history + paired.map { if (it.result == null) it.copy(result = MatchResult.DRAW) else it }
        }
    }
    @Test fun `team Swiss bye uses draw match and game points`() {
        val teams = (1..3).map { Team(id = "t$it", name = "Team $it", playerIds = listOf("a$it", "b$it"), pairingNumber = it) }
        val round = generateTeamSwiss(teams, emptyList(), 1, 5)
        val bye = round.filter { it.result == MatchResult.TEAM_BYE }
        assertEquals(2, bye.size)
        val scores = calculateTeamScores(teams, bye)
        assertEquals(1.0, scores.first["t3"]!!, 0.0)
        assertEquals(1.0, scores.second["t3"]!!, 0.0)
    }
    @Test fun `missing team boards are forfeits not pairing byes`() {
        val teams = listOf(Team(id = "a", name = "A", playerIds = listOf("a1", "a2")), Team(id = "b", name = "B", playerIds = listOf("b1")))
        val round = generateTeamSwiss(teams, emptyList(), 1, 5)
        assertTrue(round.none { it.result == MatchResult.TEAM_BYE || it.result == MatchResult.BYE })
        assertEquals(1, round.count { it.result == MatchResult.FORFEIT_WHITE || it.result == MatchResult.FORFEIT_BLACK })
    }
    @Test fun `round robin withdrawal omits standings results below half while preserving games`() {
        val p = players(4).map { if (it.id == "p4") it.copy(withdrawn = true) else it }
        val history = listOf(Match(round = 1, whiteId = "p1", blackId = "p4", result = MatchResult.WHITE_WIN),
            Match(round = 1, whiteId = "p2", blackId = "p3", result = MatchResult.DRAW),
            Match(round = 2, whiteId = "p3", blackId = "p1", result = MatchResult.WHITE_WIN),
            Match(round = 2, whiteId = "p4", blackId = "p2", result = MatchResult.FORFEIT_WHITE))
        val standings = calculateStandings(p, history, 1.0, 0.5, 0.0, emptyList(), tournamentType = TournamentType.ROUND_ROBIN)
        assertEquals(0.0, standings.first { it.player.id == "p1" }.score, 0.0)
        assertEquals(0.5, standings.first { it.player.id == "p2" }.score, 0.0)
        assertEquals(MatchResult.WHITE_WIN, history[0].result)
        val played = history.map { if (it.result == MatchResult.FORFEIT_WHITE) it.copy(result = MatchResult.BLACK_WIN) else it }
        val retained = calculateStandings(p, played, 1.0, 0.5, 0.0, emptyList(), tournamentType = TournamentType.ROUND_ROBIN)
        assertEquals(1.0, retained.first { it.player.id == "p1" }.score, 0.0)
        assertEquals(1.5, retained.first { it.player.id == "p2" }.score, 0.0)
    }
    @Test fun `team Swiss never repeats an already played opponent`() {
        val teams = (1..4).map { Team(id = "t$it", name = "Team $it", playerIds = listOf("p$it"), pairingNumber = it) }
        val first = generateTeamSwiss(teams, emptyList(), 1, 5).map { it.copy(result = MatchResult.DRAW) }
        val second = generateTeamSwiss(teams, first, 2, 5)
        assertTrue(opponents(second).intersect(opponents(first)).isEmpty())
        assertThrows(PairingException::class.java) { generateTeamSwiss(teams.take(2),
            listOf(Match(round = 1, whiteId = "p1", blackId = "p2", result = MatchResult.DRAW, teamMatchId = "previous")), 2, 5) }
    }
}

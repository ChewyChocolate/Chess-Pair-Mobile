package com.example.chesspairmobile.data.db

import com.example.chesspairmobile.data.models.AccelerationMode
import com.example.chesspairmobile.data.models.MatchResult
import com.example.chesspairmobile.data.models.TiebreakType
import com.example.chesspairmobile.data.models.TournamentStatus
import com.example.chesspairmobile.data.models.TournamentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {

    private val c = Converters()

    @Test
    fun `string list round trip`() {
        val list = listOf("a", "b", "c")
        val json = c.fromStringList(list)
        assertEquals(list, c.toStringList(json))
    }

    @Test
    fun `string list null is encoded as empty`() {
        assertEquals("[]", c.fromStringList(null))
    }

    @Test
    fun `int list round trip`() {
        val list = listOf(1, 2, 3)
        val json = c.fromIntList(list)
        assertEquals(list, c.toIntList(json))
    }

    @Test
    fun `int list null is encoded as empty`() {
        assertEquals("[]", c.fromIntList(null))
    }

    @Test
    fun `tournament type round trip`() {
        TournamentType.entries.forEach { type ->
            assertEquals(type, c.toTournamentType(c.fromTournamentType(type)))
        }
    }

    @Test
    fun `tournament type invalid string returns SWISS`() {
        assertEquals(TournamentType.SWISS, c.toTournamentType("BOGUS"))
    }

    @Test
    fun `tournament status round trip`() {
        TournamentStatus.entries.forEach { s ->
            assertEquals(s, c.toTournamentStatus(c.fromTournamentStatus(s)))
        }
    }

    @Test
    fun `tournament status invalid string returns SETUP`() {
        assertEquals(TournamentStatus.SETUP, c.toTournamentStatus("BOGUS"))
    }

    @Test
    fun `tiebreak list round trip`() {
        val list = listOf(TiebreakType.BUCHHOLZ, TiebreakType.MEDIAN_BUCHHOLZ)
        val json = c.fromTiebreakList(list)
        assertEquals(list, c.toTiebreakList(json))
    }

    @Test
    fun `tiebreak list with unknown values filters them out`() {
        val list = listOf(TiebreakType.BUCHHOLZ, TiebreakType.SONNEBORN_BERGER)
        val json = c.fromTiebreakList(list)
        // Corrupt the JSON to include an unknown value (only the first occurrence)
        val corrupted = json.replaceFirst("BUCHHOLZ", "BOGUS_VALUE")
        val parsed = c.toTiebreakList(corrupted)
        // SONNEBORN_BERGER should survive
        assertTrue(parsed.contains(TiebreakType.SONNEBORN_BERGER))
        assertEquals(1, parsed.size)
    }

    @Test
    fun `tiebreak list with all unknown values falls back to default`() {
        val parsed = c.toTiebreakList("[\"FOO\",\"BAR\"]")
        assertTrue(parsed.isNotEmpty())
    }

    @Test
    fun `tiebreak list blank returns default`() {
        val parsed = c.toTiebreakList("")
        assertTrue(parsed.isNotEmpty())
    }

    @Test
    fun `match result round trip`() {
        MatchResult.entries.forEach { r ->
            assertEquals(r, c.toMatchResult(c.fromMatchResult(r)))
        }
    }

    @Test
    fun `match result null`() {
        assertNull(c.toMatchResult(null))
        assertNull(c.fromMatchResult(null))
    }

    @Test
    fun `acceleration mode round trip`() {
        AccelerationMode.entries.forEach { m ->
            assertEquals(m, c.toAccelerationMode(c.fromAccelerationMode(m)))
        }
    }

    @Test
    fun `acceleration mode invalid returns NONE`() {
        assertEquals(AccelerationMode.NONE, c.toAccelerationMode("BOGUS"))
    }
}

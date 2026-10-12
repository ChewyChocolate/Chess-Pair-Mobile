package com.example.chesspairmobile.logic

import org.junit.Assert.*
import org.junit.Test

class PlayerImportTest {
    @Test fun `CSV header quoted commas and escaped quotes import correctly`() {
        val preview = previewPlayerImport("Name,Rating,Title,Club\n\"Doe, Jane\",1500,IM,\"Club \"\"A\"\"\"", emptyList())
        assertTrue(preview.errors.isEmpty())
        assertEquals(ImportedPlayer("Doe, Jane", 1500, "IM", "Club \"A\""), preview.players.single())
    }
    @Test fun `duplicate names are previewed and skipped`() {
        val preview = previewPlayerImport("Alice,1500\nalice,1800\nBOB,1400", listOf("Bob"))
        assertEquals(1, preview.players.size)
        assertEquals(2, preview.duplicates)
    }
    @Test fun `bad ratings and unclosed quotes are reported with row numbers`() {
        val preview = previewPlayerImport("Alice,nope\n\"Bob,1500\nCarol,-1", emptyList())
        assertTrue(preview.players.isEmpty())
        assertEquals(3, preview.errors.size)
        assertTrue(preview.errors[0].startsWith("Row 1:"))
    }
}

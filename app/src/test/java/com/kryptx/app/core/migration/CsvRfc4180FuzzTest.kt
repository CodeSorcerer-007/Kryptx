package com.kryptx.app.core.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvRfc4180FuzzTest {

    @Test
    fun testParseSimpleCsvLine() {
        val line = "folder,1,login,Google,Primary account,,0,https://google.com,user@gmail.com,secret123,JBSWY3DPEHPK3PXP,,"
        val cols = VaultImporter.parseCsvLine(line)
        assertEquals(13, cols.size)
        assertEquals("Google", cols[3])
        assertEquals("user@gmail.com", cols[8])
        assertEquals("secret123", cols[9])
        assertEquals("JBSWY3DPEHPK3PXP", cols[10])
    }

    @Test
    fun testParseEscapedDoubleQuotesRfc4180() {
        // In RFC 4180, "" inside a quoted string represents a literal "
        val line = "\"\"\"quoted\"\" name\",\"pass\"\"word with quotes\",\"notes, with comma\""
        val cols = VaultImporter.parseCsvLine(line)
        assertEquals(3, cols.size)
        assertEquals("\"quoted\" name", cols[0])
        assertEquals("pass\"word with quotes", cols[1])
        assertEquals("notes, with comma", cols[2])
    }

    @Test
    fun testParseCommasInsideQuotedFields() {
        val line = "\"Site, Inc.\",\"user,name\",\"p@ss,w0rd!#123\",\"https://site.com/path?a=1,2\""
        val cols = VaultImporter.parseCsvLine(line)
        assertEquals(4, cols.size)
        assertEquals("Site, Inc.", cols[0])
        assertEquals("user,name", cols[1])
        assertEquals("p@ss,w0rd!#123", cols[2])
        assertEquals("https://site.com/path?a=1,2", cols[3])
    }

    @Test
    fun testParseComplexFuzzedLines() {
        val fuzzedInputs = listOf(
            "",
            "   ",
            ",,,,",
            "\"\",,\"\",\"\"",
            "\"test\"\"1\",\"test\"\"2\",\"test\"\"3\"",
            "normal,field,\"quoted,with,commas\",another",
            "\"trailing quote\"\"\",\"leading \"\"quote\"",
            "\"complex: !@#\$%^&*()_+-=[]{}|;':,.<>/?\""
        )

        for (input in fuzzedInputs) {
            val cols = VaultImporter.parseCsvLine(input)
            // Ensure parser never throws exceptions on edge-case lines
            assertTrue("Parsed columns should be present", cols.isNotEmpty() || input.isBlank())
        }
    }

    @Test
    fun testImportCsvRoundTripWithEscapedQuotes() {
        val csv = """
            type,title,username,password,url,notes
            login,"Bank ""Super"" Secure",john.doe,"P@ss""w0rd,2026!",https://bank.com,"Note with ""quotes"" and, commas"
        """.trimIndent()

        val items = VaultImporter.importCsv(csv)
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("Bank \"Super\" Secure", item.title)
        assertEquals("john.doe", item.username)
        assertEquals("P@ss\"w0rd,2026!", item.password)
        assertEquals("https://bank.com", item.website)
        assertEquals("Note with \"quotes\" and, commas", item.notes)
    }
}

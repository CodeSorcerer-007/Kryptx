package com.kryptx.app.core.migration

import com.kryptx.app.core.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultMigrationTest {

    @Test
    fun testCsvImport() {
        val sampleCsv = """
            name,url,username,password,note
            GitHub,https://github.com,testuser,SecretPassword123,My personal repo login
            Google,https://accounts.google.com,test@gmail.com,GoogleP@ssw0rd!,""
        """.trimIndent()

        val items = VaultImporter.importCsv(sampleCsv)
        assertEquals(2, items.size)
        assertEquals("GitHub", items[0].title)
        assertEquals("testuser", items[0].username)
        assertEquals("SecretPassword123", items[0].password)
        assertEquals("https://github.com", items[0].website)

        assertEquals("Google", items[1].title)
        assertEquals("test@gmail.com", items[1].username)
    }

    @Test
    fun testBitwardenJsonImport() {
        val sampleBitwardenJson = """
            {
              "items": [
                {
                  "type": 1,
                  "name": "Twitter / X",
                  "notes": "Verified account",
                  "login": {
                    "username": "kryptx_user",
                    "password": "SuperSecretPassword99!",
                    "totp": "JBSWY3DPEHPK3PXP",
                    "uris": [
                      { "uri": "https://x.com" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val items = VaultImporter.importJson(sampleBitwardenJson)
        assertEquals(1, items.size)
        assertEquals("Twitter / X", items[0].title)
        assertEquals("kryptx_user", items[0].username)
        assertEquals("SuperSecretPassword99!", items[0].password)
        assertEquals("JBSWY3DPEHPK3PXP", items[0].totpSecret)
        assertEquals("https://x.com", items[0].website)
    }

    @Test
    fun testExportToCsvAndReimport() {
        val originalItem = VaultItem(
            id = "exp_1",
            title = "Personal Vault Item",
            username = "alice",
            password = "AliceSecretPassword123!",
            website = "https://alice.org",
            totpSecret = "JBSWY3DPEHPK3PXP",
            notes = "Test notes with comma, and quotes here"
        )

        val csvString = VaultExporter.exportToCsv(listOf(originalItem))
        assertTrue(csvString.contains("Personal Vault Item"))
        assertTrue(csvString.contains("alice"))

        val reimported = VaultImporter.importAutoDetect(csvString)
        assertEquals(1, reimported.size)
        assertEquals("Personal Vault Item", reimported[0].title)
        assertEquals("alice", reimported[0].username)
        assertEquals("AliceSecretPassword123!", reimported[0].password)
    }

    @Test
    fun testAutoDetectEmptyOrInvalidContent() {
        val empty = VaultImporter.importAutoDetect("")
        assertTrue(empty.isEmpty())

        val invalid = VaultImporter.importAutoDetect("NotAValidFormat")
        assertTrue(invalid.isEmpty())
    }
}

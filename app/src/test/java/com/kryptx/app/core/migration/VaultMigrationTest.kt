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

    @Test
    fun testExportCsvEscapesCarriageReturn() {
        val itemWithCr = VaultItem(
            id = "crlf_1",
            title = "CRLF\rTitle",
            notes = "Line1\r\nLine2"
        )
        val csv = VaultExporter.exportToCsv(listOf(itemWithCr))
        assertTrue(csv.contains("\"CRLF\rTitle\""))
        assertTrue(csv.contains("\"Line1\r\nLine2\""))
    }

    @Test
    fun testImportCsvWithUtf8Bom() {
        val bomCsv = "\uFEFFname,url,username,password\nGitHub,https://github.com,octocat,Pass123!"
        val items = VaultImporter.importAutoDetect(bomCsv)
        assertEquals(1, items.size)
        assertEquals("GitHub", items[0].title)
        assertEquals("octocat", items[0].username)
        assertEquals("Pass123!", items[0].password)
    }

    @Test
    fun testImportSemicolonDelimitedCsv() {
        val semicolonCsv = "name;url;username;password;notes\nPersonal Bank;https://bank.eu;myuser;SecretPin;European style"
        val items = VaultImporter.importAutoDetect(semicolonCsv)
        assertEquals(1, items.size)
        assertEquals("Personal Bank", items[0].title)
        assertEquals("myuser", items[0].username)
        assertEquals("SecretPin", items[0].password)
        assertEquals("https://bank.eu", items[0].website)
    }

    @Test
    fun testBitwardenJsonWithNullLoginAndCardsDoesNotCrash() {
        val bitwardenJsonWithNulls = """
            {
              "items": [
                {
                  "type": 2,
                  "name": "Secret Server Recovery Codes",
                  "notes": "Code1\nCode2\nCode3",
                  "login": null,
                  "card": null,
                  "fields": null
                },
                {
                  "type": 1,
                  "name": "GitLab",
                  "notes": "",
                  "login": {
                    "username": "gitlab_user",
                    "password": "GitLabPassword456!",
                    "totp": null,
                    "uris": null
                  },
                  "card": null
                }
              ]
            }
        """.trimIndent()

        val items = VaultImporter.importJson(bitwardenJsonWithNulls)
        assertEquals(2, items.size)
        assertEquals("Secret Server Recovery Codes", items[0].title)
        assertEquals(com.kryptx.app.core.model.ItemType.SECURE_NOTE, items[0].type)
        assertEquals("Code1\nCode2\nCode3", items[0].notes)

        assertEquals("GitLab", items[1].title)
        assertEquals("gitlab_user", items[1].username)
        assertEquals("GitLabPassword456!", items[1].password)
    }

    @Test
    fun testImportGenericJsonArrayFallback() {
        val genericArrayJson = """
            [
              {
                "title": "AWS Root Account",
                "username": "aws_admin",
                "password": "SuperSecretAwsPassword!",
                "website": "https://aws.amazon.com",
                "notes": "Root credentials"
              }
            ]
        """.trimIndent()

        val items = VaultImporter.importAutoDetect(genericArrayJson)
        assertEquals(1, items.size)
        assertEquals("AWS Root Account", items[0].title)
        assertEquals("aws_admin", items[0].username)
        assertEquals("SuperSecretAwsPassword!", items[0].password)
        assertEquals("https://aws.amazon.com", items[0].website)
    }
}


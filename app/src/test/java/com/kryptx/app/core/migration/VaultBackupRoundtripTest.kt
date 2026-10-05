package com.kryptx.app.core.migration

import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.model.BackupHeader
import com.kryptx.app.core.model.CustomField
import com.kryptx.app.core.model.EncryptedBackupPayload
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.UUID

/**
 * End-to-end integration tests for vault backup, migration, and export/import roundtrips.
 *
 * Validates:
 * 1. Encrypted backup export → serialize → deserialize → decrypt → item parity
 * 2. Tampered ciphertext rejection and authentication tag failure
 * 3. Invalid password rejection during encrypted import
 * 4. Plaintext CSV RFC 4180 export → import roundtrip with special characters
 * 5. Offline Web Vault HTML companion generation and secret shielding
 * 6. Cryptographic checksum verification across backup payloads
 */
class VaultBackupRoundtripTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun createSampleVaultItems(): List<VaultItem> {
        return listOf(
            VaultItem(
                id = UUID.randomUUID().toString(),
                title = "ProtonMail",
                type = ItemType.LOGIN,
                username = "alice.sec@pm.me",
                password = "CorrectHorse-Battery.Staple-99!",
                website = "https://mail.proton.me",
                totpSecret = "JBSWY3DPEHPK3PXP",
                notes = "Personal secure email with PGP key attached.\nLine 2 with commas, and \"quotes\".",
                isFavorite = true,
                customFields = listOf(
                    CustomField(
                        id = "cf1",
                        label = "Recovery Email",
                        value = "backup@tutamail.com",
                        isSecured = false
                    ),
                    CustomField(
                        id = "cf2",
                        label = "Security Pin",
                        value = "849201",
                        isSecured = true
                    )
                ),
                createdAt = 1700000000000L,
                updatedAt = 1700001000000L
            ),
            VaultItem(
                id = UUID.randomUUID().toString(),
                title = "Primary Debit Card",
                type = ItemType.CREDIT_CARD,
                cardholderName = "Alice Doe",
                cardNumber = "4532123456789010",
                cardExpiry = "12/29",
                cardCvv = "891",
                cardPin = "4412",
                notes = "Bank issued debit card",
                isFavorite = false,
                createdAt = 1700002000000L
            ),
            VaultItem(
                id = UUID.randomUUID().toString(),
                title = "Office HighSpeed Wi-Fi",
                type = ItemType.WIFI,
                wifiSsid = "KryptxSecure-5G",
                wifiPassword = "WPA3-Enterprise-Key!987",
                notes = "Floor 4 Access Point",
                createdAt = 1700003000000L
            )
        )
    }

    @Test
    fun testEncryptedBackupExportAndImportRoundtrip() {
        val originalItems = createSampleVaultItems()
        val backupPassword = "SuperSecretVaultBackupPassphrase2026!#"
        val passwordChars = backupPassword.toCharArray()

        // 1. Serialize items to plaintext JSON
        val plaintextJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(VaultItem.serializer()),
            originalItems
        )
        val plaintextBytes = plaintextJson.toByteArray(Charsets.UTF_8)

        // 2. Encrypt using KeyDerivation + CryptoEngine
        val salt = KeyDerivation.generateSalt()
        val encryptionKey = KeyDerivation.deriveKeyArgon2(passwordChars, salt)
        val ciphertext = CryptoEngine.encrypt(plaintextBytes, encryptionKey)

        val saltBase64 = Base64.getEncoder().encodeToString(salt)
        val ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext)
        val checksum = VaultExporter.computeSha256Checksum(ciphertextBase64)

        val header = BackupHeader(
            app = "Kryptx",
            version = "2.2.0",
            formatVersion = 3,
            exportedAt = System.currentTimeMillis(),
            isEncrypted = true,
            kdfAlgorithm = "Argon2id",
            saltBase64 = saltBase64,
            checksumSha256 = checksum
        )
        val payload = EncryptedBackupPayload(header, ciphertextBase64)

        // 3. Serialize backup envelope to transport string
        val backupFileContent = json.encodeToString(EncryptedBackupPayload.serializer(), payload)
        assertTrue(backupFileContent.contains("ciphertextBase64"))
        assertTrue(backupFileContent.contains("Argon2id"))
        assertFalse(backupFileContent.contains("CorrectHorse-Battery")) // Plaintext secrets shielded

        // 4. Deserialize transport string back to envelope
        val parsedPayload = json.decodeFromString<EncryptedBackupPayload>(backupFileContent)
        assertEquals("2.2.0", parsedPayload.header.version)
        assertEquals("Argon2id", parsedPayload.header.kdfAlgorithm)

        // 5. Verify checksum
        assertTrue(VaultExporter.verifySha256Checksum(parsedPayload.ciphertextBase64, parsedPayload.header.checksumSha256!!))

        // 6. Decrypt using password
        val restoredSalt = Base64.getDecoder().decode(parsedPayload.header.saltBase64)
        val restoredKey = KeyDerivation.deriveKeyArgon2(passwordChars, restoredSalt)
        val rawCiphertext = Base64.getDecoder().decode(parsedPayload.ciphertextBase64)

        val decryptedBytes = CryptoEngine.decrypt(rawCiphertext, restoredKey)
        assertNotNull("Decrypted bytes must not be null", decryptedBytes)

        val decryptedJson = String(decryptedBytes!!, Charsets.UTF_8)

        // 7. Decode restored items and verify complete data parity
        val restoredItems = json.decodeFromString<List<VaultItem>>(decryptedJson)
        assertEquals(originalItems.size, restoredItems.size)

        for (i in originalItems.indices) {
            val orig = originalItems[i]
            val rest = restoredItems[i]
            assertEquals(orig.id, rest.id)
            assertEquals(orig.title, rest.title)
            assertEquals(orig.type, rest.type)
            assertEquals(orig.username, rest.username)
            assertEquals(orig.password, rest.password)
            assertEquals(orig.website, rest.website)
            assertEquals(orig.totpSecret, rest.totpSecret)
            assertEquals(orig.notes, rest.notes)
            assertEquals(orig.cardNumber, rest.cardNumber)
            assertEquals(orig.wifiSsid, rest.wifiSsid)
            assertEquals(orig.wifiPassword, rest.wifiPassword)
            assertEquals(orig.customFields.size, rest.customFields.size)
        }

        SecureMemory.wipe(passwordChars)
        SecureMemory.wipe(encryptionKey)
        SecureMemory.wipe(restoredKey)
    }

    @Test
    fun testEncryptedBackupWrongPasswordFails() {
        val originalItems = createSampleVaultItems()
        val correctPassword = "CorrectPassword123!"
        val wrongPassword = "WrongPassword456?"

        val plaintextJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(VaultItem.serializer()),
            originalItems
        )

        val salt = KeyDerivation.generateSalt()
        val correctKey = KeyDerivation.deriveKeyArgon2(correctPassword.toCharArray(), salt)
        val ciphertext = CryptoEngine.encrypt(plaintextJson.toByteArray(Charsets.UTF_8), correctKey)

        // Attempt decrypt with wrong key
        val wrongKey = KeyDerivation.deriveKeyArgon2(wrongPassword.toCharArray(), salt)
        try {
            val result = CryptoEngine.decrypt(ciphertext, wrongKey)
            assertNull("Decryption with wrong password must return null", result)
        } catch (_: Exception) {
            // Expected: AEADBadTagException or GeneralSecurityException is thrown on bad key
        }
    }

    @Test
    fun testTamperedCiphertextIsRejected() {
        val originalItems = createSampleVaultItems()
        val password = "StrongPassword987!"

        val plaintextJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(VaultItem.serializer()),
            originalItems
        )

        val salt = KeyDerivation.generateSalt()
        val key = KeyDerivation.deriveKeyArgon2(password.toCharArray(), salt)
        val ciphertext = CryptoEngine.encrypt(plaintextJson.toByteArray(Charsets.UTF_8), key)

        // Tamper with the last byte of ciphertext (part of the auth tag)
        val tamperedData = ciphertext.clone()
        tamperedData[tamperedData.size - 1] = (tamperedData[tamperedData.size - 1].toInt() xor 0x01).toByte()

        try {
            val result = CryptoEngine.decrypt(tamperedData, key)
            assertNull("Tampered ciphertext must fail auth tag check and return null", result)
        } catch (_: Exception) {
            // Expected: AEADBadTagException or GeneralSecurityException is thrown on tampered tag
        }
    }

    @Test
    fun testPlaintextCsvExportAndImportParity() {
        val items = createSampleVaultItems()
        val csv = VaultExporter.exportToCsv(items)

        assertNotNull(csv)
        assertTrue(csv.startsWith("folder,favorite,type,name,notes"))
        assertTrue(csv.contains("ProtonMail"))
        assertTrue(csv.contains("alice.sec@pm.me"))
        assertTrue(csv.contains("CorrectHorse-Battery.Staple-99!"))

        // Reimport and verify structure
        val imported = VaultImporter.importCsv(csv)
        assertEquals(items.size, imported.size)
        assertEquals("ProtonMail", imported[0].title)
        assertEquals("alice.sec@pm.me", imported[0].username)
        assertEquals("CorrectHorse-Battery.Staple-99!", imported[0].password)
        assertEquals("https://mail.proton.me", imported[0].website)
        assertEquals("JBSWY3DPEHPK3PXP", imported[0].totpSecret)
    }

    @Test
    fun testOfflineWebVaultGenerationIntegrity() {
        val items = createSampleVaultItems()
        val passwordChars = "WebVaultPassphrase2026!".toCharArray()

        val html = OfflineWebVaultGenerator.generateOfflineHtml(items, passwordChars)

        assertNotNull(html)
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("Kryptx Sovereign Offline Vault"))
        assertTrue(html.contains("crypto.subtle"))
        // Secrets must be encrypted in base64, NEVER embedded in raw plaintext HTML
        assertFalse(html.contains("CorrectHorse-Battery.Staple-99!"))
        assertFalse(html.contains("WPA3-Enterprise-Key!987"))

        SecureMemory.wipe(passwordChars)
    }

    @Test
    fun testSha256ChecksumVerification() {
        val payload = "Critical security configuration payload 2026"
        val checksum = VaultExporter.computeSha256Checksum(payload)

        assertEquals(64, checksum.length)
        assertTrue(VaultExporter.verifySha256Checksum(payload, checksum))
        assertFalse(VaultExporter.verifySha256Checksum(payload + "tamper", checksum))
    }
}

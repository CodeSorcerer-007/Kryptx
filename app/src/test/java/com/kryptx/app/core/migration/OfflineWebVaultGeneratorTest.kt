package com.kryptx.app.core.migration

import java.util.Base64
import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineWebVaultGeneratorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testGenerateOfflineHtmlContainsEmbeddedPayload() {
        val items = listOf(
            VaultItem(
                id = "1",
                title = "GitHub Personal",
                type = ItemType.LOGIN,
                username = "dev_user",
                password = "SuperSecretDevPassword!2026",
                website = "https://github.com"
            )
        )

        val password = "StrongMasterPassword99!".toCharArray()
        val html = OfflineWebVaultGenerator.generateOfflineHtml(items, password)

        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("window.crypto.subtle"))
        assertTrue(html.contains("salt:"))
        assertTrue(html.contains("iv:"))
        assertTrue(html.contains("ciphertext:"))

        // Must NOT leak plaintext credentials in the HTML file
        assertFalse(html.contains("SuperSecretDevPassword!2026"))
        assertFalse(html.contains("dev_user"))
    }

    @Test
    fun testOfflineWebVaultCryptographicRoundTrip() {
        val items = listOf(
            VaultItem(
                id = "item_123",
                title = "API Key Token",
                type = ItemType.API_KEY,
                apiKey = "sk_live_123456"
            )
        )

        val pass = "SovereignPassphrase2026".toCharArray()
        val html = OfflineWebVaultGenerator.generateOfflineHtml(items, pass)

        // Parse embedded base64 parameters from HTML
        val saltRegex = Regex("""salt:\s*"([^"]+)"""")
        val ivRegex = Regex("""iv:\s*"([^"]+)"""")
        val ciphertextRegex = Regex("""ciphertext:\s*"([^"]+)"""")

        val saltBase64 = saltRegex.find(html)!!.groupValues[1]
        val ivBase64 = ivRegex.find(html)!!.groupValues[1]
        val ciphertextBase64 = ciphertextRegex.find(html)!!.groupValues[1]

        val salt = Base64.getDecoder().decode(saltBase64)
        val iv = Base64.getDecoder().decode(ivBase64)
        val ciphertext = Base64.getDecoder().decode(ciphertextBase64)

        // Verify key derivation matches browser WebCrypto specification (100,000 iterations PBKDF2)
        val derivedKey = KeyDerivation.deriveKey(pass, salt, iterations = 100_000)

        // Recombine IV + Ciphertext for CryptoEngine.decrypt
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        val decryptedBytes = CryptoEngine.decrypt(combined, derivedKey)
        val decryptedJson = String(decryptedBytes, Charsets.UTF_8)
        val decryptedItems = json.decodeFromString<List<VaultItem>>(decryptedJson)

        assertEquals(1, decryptedItems.size)
        assertEquals("API Key Token", decryptedItems[0].title)
        assertEquals("sk_live_123456", decryptedItems[0].apiKey)
    }
}

package com.kryptx.app.core.security

import android.util.Base64
import com.kryptx.app.core.crypto.CryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalP2PSyncTest {

    @Test
    fun `test P2P transfer payload encryption and decryption`() {
        val transferKey = CryptoEngine.generateVaultKey()
        val samplePayload = """[{"id":"1","title":"Test Account","type":"LOGIN","username":"user","password":"pass123","createdAt":1000,"updatedAt":1000,"lastUsedAt":1000}]"""

        val encryptedBytes = CryptoEngine.encrypt(samplePayload.toByteArray(Charsets.UTF_8), transferKey)
        assertNotNull(encryptedBytes)
        assertTrue(encryptedBytes.isNotEmpty())

        val decryptedBytes = CryptoEngine.decrypt(encryptedBytes, transferKey)
        val decryptedString = String(decryptedBytes, Charsets.UTF_8)

        assertEquals(samplePayload, decryptedString)
    }

    @Test
    fun `test sync QR URI query parsing structure`() {
        val testIp = "192.168.1.105"
        val testPort = 8765
        val testPin = "482910"
        val transferKey = CryptoEngine.generateVaultKey()
        val keyBase64 = java.util.Base64.getEncoder().encodeToString(transferKey)

        val uri = "kryptx-sync://$testIp:$testPort?key=$keyBase64&pin=$testPin"
        assertTrue(uri.startsWith("kryptx-sync://"))

        val noScheme = uri.removePrefix("kryptx-sync://")
        val parts = noScheme.split("?")
        val hostPort = parts[0].split(":")

        assertEquals(testIp, hostPort[0])
        assertEquals(testPort, hostPort[1].toInt())

        val queryParams = parts[1].split("&").associate {
            it.substringBefore("=") to it.substringAfter("=")
        }

        assertEquals(testPin, queryParams["pin"])
        assertEquals(keyBase64, queryParams["key"])
    }
}

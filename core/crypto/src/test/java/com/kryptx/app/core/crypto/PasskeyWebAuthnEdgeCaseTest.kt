package com.kryptx.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyWebAuthnEdgeCaseTest {

    @Test
    fun testPasskeyRegistrationMultipleDomains() {
        val domains = listOf(
            "accounts.google.com",
            "login.microsoftonline.com",
            "apple.com",
            "kryptx.local",
            "localhost"
        )

        for (domain in domains) {
            val reg = PasskeyEngine.createPasskeyRegistration(
                rpId = domain,
                userHandle = "user-handle-for-$domain",
                userName = "user@$domain"
            )
            assertEquals(domain, reg.rpId)
            assertTrue(reg.credentialId.isNotEmpty())
            assertTrue(reg.publicKeyCoseBase64.isNotEmpty())
            assertTrue(reg.rawPrivateKeyBytes.isNotEmpty())
        }
    }

    @Test
    fun testBuildClientDataJsonStructure() {
        val challenge = "dGVzdC1jaGFsbGVuZ2UtMTIzNDU2"
        val origin = "https://vault.kryptx.app"
        val clientData = PasskeyEngine.buildClientDataJson(
            type = "webauthn.get",
            challengeBase64 = challenge,
            origin = origin
        )
        val jsonString = String(clientData, Charsets.UTF_8)

        assertTrue(jsonString.contains("\"type\":\"webauthn.get\""))
        assertTrue(jsonString.contains("\"challenge\":\"$challenge\""))
        assertTrue(jsonString.contains("\"origin\":\"$origin\""))
    }

    @Test
    fun testPasskeyAssertionWithIncrementalCounters() {
        val reg = PasskeyEngine.createPasskeyRegistration(
            rpId = "auth.example.com",
            userHandle = "user123",
            userName = "tester"
        )

        val challengeBytes = ByteArray(32) { it.toByte() }
        val challengeBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)
        val clientData = PasskeyEngine.buildClientDataJson(
            type = "webauthn.get",
            challengeBase64 = challengeBase64,
            origin = "https://auth.example.com"
        )

        for (counter in listOf(0, 1, 42, 99999, Int.MAX_VALUE - 10)) {
            val assertion = PasskeyEngine.signPasskeyAssertion(
                rpId = "auth.example.com",
                clientDataJsonBytes = clientData,
                privateKeyBytes = reg.rawPrivateKeyBytes,
                credentialId = reg.credentialId,
                userHandle = reg.userHandle,
                signCount = counter
            )
            assertNotNull(assertion)
            assertTrue(assertion.signatureBase64.isNotBlank())
            assertTrue(assertion.authenticatorDataBase64.isNotBlank())
        }
    }

    @Test
    fun testCorruptedPrivateKeyHandling() {
        val reg = PasskeyEngine.createPasskeyRegistration(
            rpId = "test.org",
            userHandle = "uid",
            userName = "u"
        )

        val clientData = PasskeyEngine.buildClientDataJson(
            type = "webauthn.get",
            challengeBase64 = "Y2hhbGxlbmdl",
            origin = "https://test.org"
        )

        val corruptedKey = ByteArray(16) { 0x00 }
        var failed = false
        try {
            PasskeyEngine.signPasskeyAssertion(
                rpId = "test.org",
                clientDataJsonBytes = clientData,
                privateKeyBytes = corruptedKey,
                credentialId = reg.credentialId,
                userHandle = reg.userHandle,
                signCount = 1
            )
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("Corrupted key must throw exception on sign", failed)
    }

    @Test
    fun testP256PublicKeyEncodingValidity() {
        val reg = PasskeyEngine.createPasskeyRegistration(
            rpId = "github.com",
            userHandle = "octo",
            userName = "octo"
        )

        val coseBytes = Base64.getUrlDecoder().decode(reg.publicKeyCoseBase64)
        assertTrue("COSE key structure must have non-zero length", coseBytes.isNotEmpty())
        assertTrue("COSE key length should be at least 32 bytes", coseBytes.size >= 32)
    }
}

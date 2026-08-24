package com.kryptx.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasskeyEngineTest {

    @Test
    fun `passkey registration generates valid P256 keypair and COSE key`() {
        val result = PasskeyEngine.createPasskeyRegistration(
            rpId = "github.com",
            userHandle = "octocat_user_123",
            userName = "octocat"
        )

        assertNotNull(result.credentialId)
        assertTrue(result.credentialId.isNotBlank())
        assertNotNull(result.publicKeyCoseBase64)
        assertTrue(result.publicKeyCoseBase64.isNotBlank())
        assertTrue(result.rawPrivateKeyBytes.isNotEmpty())
        assertTrue(result.rawPrivateKeyBytes.size >= 32)
        assertEquals("github.com", result.rpId)
        assertEquals("octocat_user_123", result.userHandle)
    }

    @Test
    fun `passkey assertion signs challenge with correct authenticator data and client data hash`() {
        val reg = PasskeyEngine.createPasskeyRegistration(
            rpId = "webauthn.io",
            userHandle = "test_user_handle",
            userName = "test_user"
        )

        val challengeBytes = ByteArray(32) { (it + 1).toByte() }
        val challengeBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)
        val clientDataJson = PasskeyEngine.buildClientDataJson(
            type = "webauthn.get",
            challengeBase64 = challengeBase64,
            origin = "https://webauthn.io"
        )

        val assertion = PasskeyEngine.signPasskeyAssertion(
            rpId = "webauthn.io",
            clientDataJsonBytes = clientDataJson,
            privateKeyBytes = reg.rawPrivateKeyBytes,
            credentialId = reg.credentialId,
            userHandle = reg.userHandle,
            signCount = 5
        )

        assertNotNull(assertion)
        assertTrue(assertion.signatureBase64.isNotBlank())
        assertTrue(assertion.authenticatorDataBase64.isNotBlank())
        assertEquals(reg.credentialId, assertion.credentialId)

        // Authenticator Data must contain 32-byte rpIdHash + 1 byte flags + 4 bytes counter = 37 bytes
        val authData = Base64.getUrlDecoder().decode(assertion.authenticatorDataBase64)
        assertEquals(37, authData.size)
        assertEquals(0x05.toByte(), authData[32]) // UP + UV flags
    }
}

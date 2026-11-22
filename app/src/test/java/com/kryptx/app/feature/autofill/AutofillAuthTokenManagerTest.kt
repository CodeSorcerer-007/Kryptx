package com.kryptx.app.feature.autofill

import com.kryptx.app.feature.credentials.CredentialAuthTokenManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillAuthTokenManagerTest {

    @Test
    fun testAutofillTokenSingleUseLifecycle() {
        val token = AutofillAuthTokenManager.generateToken()
        assertNotNull(token)
        assertTrue(token.isNotBlank())

        // First use must succeed
        assertTrue("First token consumption must succeed", AutofillAuthTokenManager.validateAndConsumeToken(token))

        // Replay attempt must fail
        assertFalse("Replaying already consumed token must fail", AutofillAuthTokenManager.validateAndConsumeToken(token))
    }

    @Test
    fun testAutofillTokenRejectsInvalidOrNull() {
        assertFalse(AutofillAuthTokenManager.validateAndConsumeToken(null))
        assertFalse(AutofillAuthTokenManager.validateAndConsumeToken(""))
        assertFalse(AutofillAuthTokenManager.validateAndConsumeToken("   "))
        assertFalse(AutofillAuthTokenManager.validateAndConsumeToken("invalid-crafted-token-12345"))
    }

    @Test
    fun testCredentialTokenSingleUseLifecycle() {
        val token = CredentialAuthTokenManager.generateToken()
        assertNotNull(token)
        assertTrue(token.isNotBlank())

        // First use must succeed
        assertTrue("First token consumption must succeed", CredentialAuthTokenManager.validateAndConsumeToken(token))

        // Replay attempt must fail
        assertFalse("Replaying consumed credential token must fail", CredentialAuthTokenManager.validateAndConsumeToken(token))
    }

    @Test
    fun testCredentialTokenRejectsInvalidOrNull() {
        assertFalse(CredentialAuthTokenManager.validateAndConsumeToken(null))
        assertFalse(CredentialAuthTokenManager.validateAndConsumeToken(""))
        assertFalse(CredentialAuthTokenManager.validateAndConsumeToken("random-token-xyz"))
    }
}

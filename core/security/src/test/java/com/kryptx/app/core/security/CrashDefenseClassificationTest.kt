package com.kryptx.app.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.IllegalBlockSizeException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.SignatureException

class CrashDefenseClassificationTest {

    @Test
    fun testSealedSecurityCriticalExceptionsClassifiedAsCritical() {
        assertTrue(CrashDefense.isSecurityCritical(VaultKeyDerivationException("KDF failed")))
        assertTrue(CrashDefense.isSecurityCritical(CipherIntegrityException("AEAD tag mismatch")))
        assertTrue(CrashDefense.isSecurityCritical(VaultSessionException("Active session invalidated")))
    }

    @Test
    fun testMemoryAndStackErrorsClassifiedAsCritical() {
        assertTrue(CrashDefense.isSecurityCritical(OutOfMemoryError("Heap exhausted")))
        assertTrue(CrashDefense.isSecurityCritical(StackOverflowError("Recursive loop")))
    }

    @Test
    fun testStandardCryptoExceptionsClassifiedAsCritical() {
        assertTrue(CrashDefense.isSecurityCritical(AEADBadTagException("Bad tag")))
        assertTrue(CrashDefense.isSecurityCritical(IllegalBlockSizeException("Block size error")))
        assertTrue(CrashDefense.isSecurityCritical(InvalidKeyException("Key corrupted")))
        assertTrue(CrashDefense.isSecurityCritical(NoSuchAlgorithmException("Cipher missing")))
        assertTrue(CrashDefense.isSecurityCritical(SignatureException("Signature verification failed")))
        assertTrue(CrashDefense.isSecurityCritical(SecurityException("Permission or policy violation")))
    }

    @Test
    fun testRecoveryActionExecution() {
        var actionExecuted = false
        CrashDefense.registerRecoveryAction {
            actionExecuted = true
        }
        val result = CrashDefense.executeRecoveryActionOrFailClosed()
        assertTrue("Recovery action must succeed without throwing", result)
        assertTrue("Recovery action callback must have executed", actionExecuted)
    }

    @Test
    fun testRecoveryActionFailureTriggersFailClosed() {
        CrashDefense.registerRecoveryAction {
            throw RuntimeException("Simulated vault locking failure during crash recovery")
        }
        val result = CrashDefense.executeRecoveryActionOrFailClosed()
        assertFalse("Recovery action failure must return false to trigger fail-closed process termination", result)
    }

    @Test
    fun testBackgroundCriticalExceptionsClassifiedAsSecurityCritical() {
        val badTag = AEADBadTagException("Background thread AEAD auth tag failure")
        assertTrue(CrashDefense.isSecurityCritical(badTag))

        val keyEx = InvalidKeyException("Background thread corrupted hardware key")
        assertTrue(CrashDefense.isSecurityCritical(keyEx))

        val secEx = SecurityException("Background thread unauthorized memory inspection")
        assertTrue(CrashDefense.isSecurityCritical(secEx))
    }

    @Test
    fun testTransientUiExceptionsClassifiedAsNonCritical() {
        assertFalse(CrashDefense.isSecurityCritical(IllegalStateException("Compose recomposition anomaly")))
        assertFalse(CrashDefense.isSecurityCritical(NullPointerException("Missing layout attribute")))
        assertFalse(CrashDefense.isSecurityCritical(IndexOutOfBoundsException("List scroll boundary")))
        assertFalse(CrashDefense.isSecurityCritical(IllegalArgumentException("Invalid view dimensions")))
    }
}

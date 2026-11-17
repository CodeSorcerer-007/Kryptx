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
    }

    @Test
    fun testTransientUiExceptionsClassifiedAsNonCritical() {
        assertFalse(CrashDefense.isSecurityCritical(IllegalStateException("Compose recomposition anomaly")))
        assertFalse(CrashDefense.isSecurityCritical(NullPointerException("Missing layout attribute")))
        assertFalse(CrashDefense.isSecurityCritical(IndexOutOfBoundsException("List scroll boundary")))
        assertFalse(CrashDefense.isSecurityCritical(IllegalArgumentException("Invalid view dimensions")))
    }
}

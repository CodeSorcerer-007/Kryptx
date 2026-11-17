package com.kryptx.app.core.security

import com.kryptx.app.core.crypto.CryptoEngine
import com.kryptx.app.core.crypto.KeyDerivation
import com.kryptx.app.core.crypto.SecureMemory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuressVaultTest {

    @Test
    fun `test duress password derives isolated decoy key`() {
        val masterPassword = "PrimaryMasterPassword123!".toCharArray()
        val duressPassword = "DuressPanicCode999!".toCharArray()

        val masterSalt = KeyDerivation.generateSalt()
        val duressSalt = KeyDerivation.generateSalt()

        val derivedMasterKey = KeyDerivation.deriveKey(masterPassword, masterSalt)
        val derivedDuressKey = KeyDerivation.deriveKey(duressPassword, duressSalt)

        val realVek = CryptoEngine.generateVaultKey()
        val decoyVek = CryptoEngine.generateVaultKey()

        val encryptedRealToken = CryptoEngine.encrypt(realVek, derivedMasterKey)
        val encryptedDecoyToken = CryptoEngine.encrypt(decoyVek, derivedDuressKey)

        // Decrypting with master password yields real VEK
        val decryptedReal = CryptoEngine.decrypt(encryptedRealToken, derivedMasterKey)
        assertArrayEquals(realVek, decryptedReal)

        // Decrypting with duress password yields decoy VEK
        val decryptedDecoy = CryptoEngine.decrypt(encryptedDecoyToken, derivedDuressKey)
        assertArrayEquals(decoyVek, decryptedDecoy)

        // Duress password CANNOT decrypt real VEK
        var failed = false
        try {
            CryptoEngine.decrypt(encryptedRealToken, derivedDuressKey)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue("Duress key must fail to decrypt primary vault", failed)

        // Cleanup
        SecureMemory.wipe(derivedMasterKey)
        SecureMemory.wipe(derivedDuressKey)
        SecureMemory.wipe(realVek)
        SecureMemory.wipe(decoyVek)
    }

    @Test
    fun `test session manager transitions into decoy mode correctly`() {
        val sessionManager = VaultSessionManager()
        val decoyVek = CryptoEngine.generateVaultKey()

        sessionManager.unlock(decoyVek, isDecoy = true)
        assertTrue(sessionManager.isUnlocked.value)
        assertTrue(sessionManager.isDecoy.value)

        sessionManager.lock()
        assertFalse(sessionManager.isUnlocked.value)
        assertFalse(sessionManager.isDecoy.value)
    }
}

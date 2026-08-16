package com.kryptx.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays

class KeyDerivationTest {

    @Test
    fun testKeyDerivationIsDeterministic() {
        val password = "StrongMasterPassword!@#123".toCharArray()
        val salt = KeyDerivation.generateSalt(32)

        val key1 = KeyDerivation.deriveKey(password, salt, iterations = 10_000)
        val key2 = KeyDerivation.deriveKey(password, salt, iterations = 10_000)

        assertEquals(32, key1.size)
        assertEquals(32, key2.size)
        assertTrue(Arrays.equals(key1, key2))
    }

    @Test
    fun testDifferentSaltsProduceDifferentKeys() {
        val password = "MasterPassword123".toCharArray()
        val salt1 = KeyDerivation.generateSalt(32)
        val salt2 = KeyDerivation.generateSalt(32)

        assertFalse(Arrays.equals(salt1, salt2))

        val key1 = KeyDerivation.deriveKey(password, salt1, iterations = 10_000)
        val key2 = KeyDerivation.deriveKey(password, salt2, iterations = 10_000)

        assertFalse(Arrays.equals(key1, key2))
    }
}

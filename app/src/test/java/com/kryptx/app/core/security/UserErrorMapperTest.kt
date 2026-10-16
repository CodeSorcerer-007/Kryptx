package com.kryptx.app.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException
import java.security.InvalidKeyException
import java.security.KeyStoreException

class UserErrorMapperTest {

    @Test
    fun testAeadBadTagExceptionSanitized() {
        val sanitized = UserErrorMapper.sanitize(AEADBadTagException("Tag mismatch 0xdeadbeef"))
        assertTrue(sanitized.contains("master password or vault data corrupted"))
        assertFalse(sanitized.contains("0xdeadbeef"))
        assertFalse(sanitized.contains("AEADBadTagException"))
    }

    @Test
    fun testInvalidKeyExceptionSanitized() {
        val sanitized = UserErrorMapper.sanitize(InvalidKeyException("Hardware key 256-bit parity error"))
        assertTrue(sanitized.contains("Encryption key is invalid"))
        assertFalse(sanitized.contains("parity error"))
    }

    @Test
    fun testKeystoreExceptionSanitized() {
        val sanitized = UserErrorMapper.sanitize(KeyStoreException("AndroidKeyStore: key operation rejected by StrongBox"))
        assertTrue(sanitized.contains("Hardware security key unavailable"))
        assertFalse(sanitized.contains("AndroidKeyStore"))
    }

    @Test
    fun testSecurityCriticalExceptionSanitized() {
        val sanitized = UserErrorMapper.sanitize(VaultKeyDerivationException("Argon2id memory allocation failed"))
        assertTrue(sanitized.contains("security integrity condition"))
        assertFalse(sanitized.contains("Argon2id"))
    }

    @Test
    fun testSqlExceptionSanitizedWithoutDatabaseStructureLeak() {
        val sanitized = UserErrorMapper.sanitize(RuntimeException("sqlite3: error in table 'vault_items' column 'vek_wrapped'"))
        assertTrue(sanitized.contains("Database error encountered"))
        assertFalse(sanitized.contains("vault_items"))
        assertFalse(sanitized.contains("vek_wrapped"))
    }

    @Test
    fun testNullOrUnknownExceptionReturnsFallback() {
        val nullResult = UserErrorMapper.sanitize(null)
        assertTrue(nullResult.contains("unexpected error"))

        val unknownResult = UserErrorMapper.sanitize(IllegalStateException("Internal state failure"))
        assertTrue(unknownResult.contains("unexpected error"))
        assertFalse(unknownResult.contains("Internal state failure"))
    }
}

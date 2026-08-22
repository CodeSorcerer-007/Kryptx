package com.kryptx.app.core.crypto

import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Key derivation utility implementing OWASP-compliant PBKDF2WithHmacSHA256
 * and Argon2id (RFC 9106) for deriving cryptographic vault keys from master passwords.
 */
object KeyDerivation {

    const val DEFAULT_ITERATIONS = 600_000
    const val HIGH_SECURITY_ITERATIONS = 1_000_000
    const val FAST_ITERATIONS_TEST = 10_000 // Used during fast unit test executions
    const val KEY_LENGTH_BITS = 256
    const val SALT_LENGTH_BYTES = 32
    const val MIN_SALT_LENGTH_BYTES = 16

    private val secureRandom = SecureRandom()

    enum class KdfAlgorithm(val displayName: String, val identifier: String) {
        PBKDF2_HMAC_SHA256("PBKDF2-HMAC-SHA256 (600,000 rounds)", "pbkdf2_sha256"),
        ARGON2ID("Argon2id (Memory-Hard RFC 9106)", "argon2id")
    }

    /**
     * Generates a cryptographically secure random salt of [lengthBytes] bytes.
     */
    fun generateSalt(lengthBytes: Int = SALT_LENGTH_BYTES): ByteArray {
        require(lengthBytes >= MIN_SALT_LENGTH_BYTES) { "Salt length must be at least $MIN_SALT_LENGTH_BYTES bytes" }
        val salt = ByteArray(lengthBytes)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Derives a 256-bit symmetric encryption key from a master password and salt using PBKDF2WithHmacSHA256.
     *
     * @param password CharArray containing the master password.
     * @param salt Cryptographically secure salt (minimum 16 bytes).
     * @param iterations Iteration count (minimum 1,000, default 600,000).
     * @return Derived 32-byte key.
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS
    ): ByteArray {
        require(salt.size >= MIN_SALT_LENGTH_BYTES) { "Salt must be at least $MIN_SALT_LENGTH_BYTES bytes for secure key derivation" }
        require(iterations >= 1000) { "Iterations must be at least 1000" }

        val keySpec: KeySpec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        val keyFactory = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("PBKDF2WithHmacSHA256 is required but not supported on this platform", e)
        }

        return try {
            val secretKey = keyFactory.generateSecret(keySpec)
            secretKey.encoded
        } catch (e: InvalidKeySpecException) {
            throw IllegalStateException("Failed to derive cryptographic key from master password", e)
        } finally {
            (keySpec as? PBEKeySpec)?.clearPassword()
        }
    }

    /**
     * Derives a 256-bit symmetric encryption key using Argon2id (RFC 9106).
     */
    fun deriveKeyArgon2(
        password: CharArray,
        salt: ByteArray,
        params: Argon2Engine.Argon2Params = Argon2Engine.Argon2Params.DEFAULT
    ): ByteArray {
        require(salt.size >= MIN_SALT_LENGTH_BYTES) { "Salt must be at least $MIN_SALT_LENGTH_BYTES bytes for secure key derivation" }
        return Argon2Engine.deriveKey(password, salt, params)
    }

    /**
     * Derives a key using the specified [KdfAlgorithm].
     */
    fun deriveKeyWithAlgorithm(
        password: CharArray,
        salt: ByteArray,
        algorithm: KdfAlgorithm = KdfAlgorithm.PBKDF2_HMAC_SHA256,
        iterations: Int = DEFAULT_ITERATIONS,
        argon2Params: Argon2Engine.Argon2Params = Argon2Engine.Argon2Params.DEFAULT
    ): ByteArray {
        return when (algorithm) {
            KdfAlgorithm.PBKDF2_HMAC_SHA256 -> deriveKey(password, salt, iterations)
            KdfAlgorithm.ARGON2ID -> deriveKeyArgon2(password, salt, argon2Params)
        }
    }
}

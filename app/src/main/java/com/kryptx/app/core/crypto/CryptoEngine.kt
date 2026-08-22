package com.kryptx.app.core.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * High-performance AES-256-GCM authenticated encryption/decryption engine.
 *
 * Encrypted payload layout:
 * [IV: 12 bytes] + [Ciphertext + GCM Auth Tag: variable]
 */
object CryptoEngine {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    private val secureRandom = SecureRandom()

    /**
     * Generates a 256-bit random encryption key (Vault Encryption Key).
     */
    fun generateVaultKey(): ByteArray {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        return key
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with the specified 256-bit key.
     *
     * @param plaintext Raw bytes to encrypt.
     * @param key 256-bit (32 bytes) symmetric key.
     * @param associatedData Optional authenticated associated data (AAD).
     * @return Combined byte array containing [12-byte IV + Ciphertext with GCM Auth Tag].
     */
    fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        require(key.size == 32) { "AES-256 requires a 32-byte key" }

        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(key, KEY_ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }

        val ciphertext = cipher.doFinal(plaintext)

        // Combine IV + Ciphertext
        val byteBuffer = ByteBuffer.allocate(iv.size + ciphertext.size)
        byteBuffer.put(iv)
        byteBuffer.put(ciphertext)
        return byteBuffer.array()
    }

    /**
     * Decrypts an encrypted payload using AES-256-GCM with the specified key.
     *
     * @param encryptedData Combined byte array of [12-byte IV + Ciphertext with GCM Auth Tag].
     * @param key 256-bit symmetric key.
     * @param associatedData Optional authenticated associated data (AAD) that must match encryption.
     * @return Decrypted plaintext bytes.
     * @throws javax.crypto.AEADBadTagException if authentication fails (tampered or wrong key).
     */
    fun decrypt(
        encryptedData: ByteArray,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        require(key.size == 32) { "AES-256 requires a 32-byte key" }
        require(encryptedData.size > IV_LENGTH_BYTES) { "Invalid encrypted payload: too short" }

        val iv = ByteArray(IV_LENGTH_BYTES)
        val ciphertextLength = encryptedData.size - IV_LENGTH_BYTES
        val ciphertext = ByteArray(ciphertextLength)

        val byteBuffer = ByteBuffer.wrap(encryptedData)
        byteBuffer.get(iv)
        byteBuffer.get(ciphertext)

        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(key, KEY_ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        if (associatedData != null) {
            cipher.updateAAD(associatedData)
        }

        return cipher.doFinal(ciphertext)
    }

    /**
     * Helper to encrypt a String into a Base64-encoded encrypted payload string.
     *
     * @param plainText Plaintext string to encrypt.
     * @param key 256-bit symmetric key.
     * @param associatedData Optional authenticated associated data (AAD).
     * @return Base64-encoded ciphertext payload.
     */
    fun encryptString(
        plainText: String,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): String {
        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        return try {
            val encrypted = encrypt(plainBytes, key, associatedData)
            Base64.getEncoder().encodeToString(encrypted)
        } finally {
            SecureMemory.wipe(plainBytes)
        }
    }

    /**
     * Helper to decrypt a Base64-encoded encrypted payload string into a plaintext String.
     *
     * @param encryptedBase64 Base64-encoded ciphertext payload.
     * @param key 256-bit symmetric key.
     * @param associatedData Optional authenticated associated data (AAD) that must match encryption.
     * @return Decrypted plaintext String.
     */
    fun decryptString(
        encryptedBase64: String,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): String {
        val encryptedBytes = Base64.getDecoder().decode(encryptedBase64)
        val decryptedBytes = decrypt(encryptedBytes, key, associatedData)
        return try {
            String(decryptedBytes, Charsets.UTF_8)
        } finally {
            SecureMemory.wipe(decryptedBytes)
        }
    }
}

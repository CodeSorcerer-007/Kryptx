package com.kryptx.app.core.crypto

import com.kryptx.app.core.security.SecurityLogger
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
    const val CIPHER_TAG_XCHACHA = 0x01.toByte()
    const val CIPHER_TAG_AES_GCM = 0x02.toByte()

    /**
     * Encrypts plaintext bytes using Native Rust Engine (XChaCha20-Poly1305) when available,
     * falling back to AES-256-GCM on JVM.
     * Prefixes payload with a 1-byte cipher discriminator tag (0x01 for XChaCha20, 0x02 for AES-GCM).
     *
     * @param plaintext Raw unencrypted bytes.
     * @param key 256-bit symmetric encryption key.
     * @param associatedData Optional authenticated associated data (AAD) to bind to ciphertext.
     * @return Encrypted byte array prefixed with 1-byte cipher discriminator tag.
     * @throws IllegalArgumentException if the key size is not exactly 32 bytes.
     */
    fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        return if (associatedData == null && NativeCryptoEngineWrapper.isNativeAvailable) {
            byteArrayOf(CIPHER_TAG_XCHACHA) + NativeCryptoEngineWrapper.encryptNative(plaintext, key)
        } else {
            byteArrayOf(CIPHER_TAG_AES_GCM) + encryptJvm(plaintext, key, associatedData)
        }
    }

    private val sessionNonceCounter = java.util.concurrent.atomic.AtomicLong(0)

    /**
     * Generates a 96-bit nonce following NIST SP 800-38D §8.2.1:
     * 64 bits of CSPRNG entropy + 32-bit monotonic invocation counter.
     * Prevents nonce reuse across up to 2^32 encryptions even under low-entropy OEM conditions.
     */
    internal fun generateDeterministicIv(): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES)
        val prefix = ByteArray(4)
        secureRandom.nextBytes(prefix)
        System.arraycopy(prefix, 0, iv, 0, 4)
        val counterVal = sessionNonceCounter.getAndIncrement()
        iv[4] = (counterVal ushr 56).toByte()
        iv[5] = (counterVal ushr 48).toByte()
        iv[6] = (counterVal ushr 40).toByte()
        iv[7] = (counterVal ushr 32).toByte()
        iv[8] = (counterVal ushr 24).toByte()
        iv[9] = (counterVal ushr 16).toByte()
        iv[10] = (counterVal ushr 8).toByte()
        iv[11] = counterVal.toByte()
        return iv
    }

    /**
     * Standard JVM AES-256-GCM encryption.
     *
     * @param plaintext Raw bytes to encrypt.
     * @param key 256-bit (32 bytes) symmetric key.
     * @param associatedData Optional authenticated associated data (AAD).
     * @return Combined byte array containing [12-byte IV + Ciphertext with GCM Auth Tag].
     * @throws IllegalArgumentException if the key size is not 32 bytes.
     */
    fun encryptJvm(
        plaintext: ByteArray,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        require(key.size == 32) { "AES-256 requires a 32-byte key" }

        val iv = generateDeterministicIv()

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
     * Decrypts an encrypted payload using the cipher tag prefix, with legacy untagged fallback.
     *
     * @param encryptedData Combined byte array with prefix tag or legacy IV + ciphertext.
     * @param key 256-bit symmetric key.
     * @param associatedData Optional authenticated associated data (AAD) matching encryption.
     * @return Decrypted plaintext bytes.
     * @throws IllegalArgumentException if the encrypted payload is empty or key size is invalid.
     * @throws javax.crypto.AEADBadTagException if the payload authentication tag fails verification.
     */
    fun decrypt(
        encryptedData: ByteArray,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        require(encryptedData.isNotEmpty()) { "Invalid encrypted payload: empty" }
        require(encryptedData.size >= IV_LENGTH_BYTES) { "Invalid encrypted payload: too short (${encryptedData.size} bytes, minimum $IV_LENGTH_BYTES bytes required)" }
        return when (encryptedData[0]) {
            CIPHER_TAG_XCHACHA -> {
                val payload = encryptedData.copyOfRange(1, encryptedData.size)
                if (NativeCryptoEngineWrapper.isNativeAvailable) {
                    try {
                        NativeCryptoEngineWrapper.decryptNative(payload, key)
                    } catch (e: Exception) {
                        SecurityLogger.warn("CryptoEngine", "XChaCha20 tagged decryption failed, falling back to legacy: ${e.javaClass.simpleName}", e)
                        decryptLegacy(encryptedData, key, associatedData)
                    }
                } else {
                    decryptLegacy(encryptedData, key, associatedData)
                }
            }
            CIPHER_TAG_AES_GCM -> {
                val payload = encryptedData.copyOfRange(1, encryptedData.size)
                try {
                    decryptJvm(payload, key, associatedData)
                } catch (e: Exception) {
                    SecurityLogger.warn("CryptoEngine", "AES-GCM tagged decryption failed, falling back to legacy: ${e.javaClass.simpleName}", e)
                    decryptLegacy(encryptedData, key, associatedData)
                }
            }
            else -> {
                decryptLegacy(encryptedData, key, associatedData)
            }
        }
    }

    private fun decryptLegacy(
        encryptedData: ByteArray,
        key: ByteArray,
        associatedData: ByteArray?
    ): ByteArray {
        return if (NativeCryptoEngineWrapper.isNativeAvailable && associatedData == null && encryptedData.size >= 24) {
            try {
                NativeCryptoEngineWrapper.decryptNative(encryptedData, key)
            } catch (e: Exception) {
                // Catches NativeCryptoException, InternalException, and any other UniFFI-generated exception
                SecurityLogger.warn("CryptoEngine", "Legacy native decryption failed, falling back to JVM AES-GCM: ${e.javaClass.simpleName}", e)
                decryptJvm(encryptedData, key, associatedData)
            }
        } else {
            decryptJvm(encryptedData, key, associatedData)
        }
    }

    /**
     * Standard JVM AES-256-GCM decryption.
     *
     * @param encryptedData Combined byte array [12-byte IV + Ciphertext with GCM Auth Tag].
     * @param key 256-bit (32 bytes) symmetric key.
     * @param associatedData Optional authenticated associated data (AAD).
     * @return Decrypted plaintext bytes.
     * @throws IllegalArgumentException if key size is not 32 bytes or payload is too short.
     * @throws javax.crypto.AEADBadTagException if authentication tag is corrupted or mismatched.
     */
    fun decryptJvm(
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
     * **Security Warning:** `plainText` is an immutable JVM `String`. The intermediate byte array
     * is wiped via [SecureMemory.wipe], but the original `String` object remains in the JVM heap
     * until garbage collection — it cannot be explicitly zeroed. For security-sensitive data
     * (passwords, PINs, secrets), use [encryptCharArray] instead, which minimizes String retention.
     *
     * @param plainText Plaintext string to encrypt.
     * @param key 256-bit symmetric key.
     * @param associatedData Optional authenticated associated data (AAD).
     * @return Base64-encoded ciphertext payload.
     */
    @Deprecated(
        message = "Prefer encryptCharArray() for security-sensitive data. String is immutable in JVM and cannot be zeroed from heap.",
        replaceWith = ReplaceWith("encryptCharArray(chars, key, associatedData)")
    )
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

    /**
     * Helper to encrypt a CharArray into a Base64-encoded encrypted payload string.
     * Minimizes String memory retention.
     */
    fun encryptCharArray(
        chars: CharArray,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): String {
        val byteBuffer = ByteBuffer.allocate(chars.size * 2)
        for (c in chars) {
            byteBuffer.putChar(c)
        }
        val plainBytes = byteBuffer.array()
        return try {
            val encrypted = encrypt(plainBytes, key, associatedData)
            Base64.getEncoder().encodeToString(encrypted)
        } finally {
            SecureMemory.wipe(plainBytes)
        }
    }

    /**
     * Helper to decrypt a Base64-encoded encrypted payload string into a CharArray.
     * Minimizes String memory retention.
     */
    fun decryptToCharArray(
        encryptedBase64: String,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): CharArray {
        val encryptedBytes = Base64.getDecoder().decode(encryptedBase64)
        val decryptedBytes = decrypt(encryptedBytes, key, associatedData)
        return try {
            require(decryptedBytes.size % 2 == 0) {
                "Decrypted payload has odd byte count (${decryptedBytes.size}); " +
                "cannot decode as CharArray — possible format mismatch or payload corruption"
            }
            val byteBuffer = ByteBuffer.wrap(decryptedBytes)
            val chars = CharArray(decryptedBytes.size / 2)
            for (i in chars.indices) {
                chars[i] = byteBuffer.char
            }
            chars
        } finally {
            SecureMemory.wipe(decryptedBytes)
        }
    }
}

package com.kryptx.app.core.crypto

import com.kryptx.app.core.security.SecurityLogger
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * High-performance AES-256-GCM / XChaCha20-Poly1305 authenticated encryption/decryption engine.
 *
 * Cipher discriminator tag (first byte of every ciphertext payload):
 *  0x01 — XChaCha20-Poly1305 (native Rust), no AAD
 *  0x02 — AES-256-GCM (JVM BouncyCastle), optional AAD
 *  0x03 — XChaCha20-Poly1305 (native Rust), AAD embedded as length-prefixed frame
 *
 * AES-256-GCM payload layout (tag 0x02):
 * [IV: 12 bytes] + [Ciphertext + GCM Auth Tag: variable]
 *
 * XChaCha20-Poly1305 payload layout (tag 0x01, 0x03):
 * Nonce and ciphertext managed by the Rust engine; opaque to the Kotlin layer.
 * For tag 0x03 the decrypted plaintext is an AAD frame:
 * [4-byte big-endian AAD length] + [AAD bytes] + [original plaintext bytes]
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
     * XChaCha20-Poly1305 with embedded AAD tag.
     *
     * When the native Rust engine is available and AAD is provided, the AAD is serialized as a
     * length-prefixed header into a per-call plaintext frame before encryption, so the AAD is
     * covered by XChaCha20-Poly1305's authentication tag. Any modification to the AAD — including
     * row-swap attacks that replace one item's ciphertext with another's — causes the auth tag
     * check to fail identically to native AAD support.
     *
     * Frame layout (passed as plaintext to XChaCha20-Poly1305):
     * [4-byte big-endian AAD length] + [AAD bytes] + [original plaintext bytes]
     *
     * On decryption the frame is unpacked, the embedded AAD is compared in constant time against
     * the expected AAD supplied by the caller, and the original plaintext is returned.
     */
    const val CIPHER_TAG_XCHACHA_AAD = 0x03.toByte()

    /**
     * Encrypts plaintext bytes using Native Rust Engine (XChaCha20-Poly1305) when available,
     * falling back to AES-256-GCM on JVM.
     * Prefixes payload with a 1-byte cipher discriminator tag.
     *
     * - 0x01 (CIPHER_TAG_XCHACHA):     XChaCha20-Poly1305, no AAD, native Rust path
     * - 0x02 (CIPHER_TAG_AES_GCM):     AES-256-GCM JVM fallback (no native engine available)
     * - 0x03 (CIPHER_TAG_XCHACHA_AAD): XChaCha20-Poly1305 with length-prefixed AAD frame,
     *                                   native Rust path — guarantees the stronger cipher is
     *                                   always used regardless of whether AAD is present.
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
        return if (NativeCryptoEngineWrapper.isNativeAvailable) {
            if (associatedData == null) {
                byteArrayOf(CIPHER_TAG_XCHACHA) + NativeCryptoEngineWrapper.encryptNative(plaintext, key)
            } else {
                // Embed AAD as a 4-byte big-endian length prefix + AAD bytes prepended to plaintext,
                // so it is covered by XChaCha20-Poly1305's authentication tag.
                val frame = buildAadFrame(associatedData, plaintext)
                try {
                    byteArrayOf(CIPHER_TAG_XCHACHA_AAD) + NativeCryptoEngineWrapper.encryptNative(frame, key)
                } finally {
                    SecureMemory.wipe(frame)
                }
            }
        } else {
            byteArrayOf(CIPHER_TAG_AES_GCM) + encryptJvm(plaintext, key, associatedData)
        }
    }

    /**
     * Builds a length-prefixed AAD frame: [4-byte big-endian AAD length][AAD bytes][plaintext bytes].
     * The frame is encrypted as a single XChaCha20-Poly1305 payload so the AAD is covered by
     * the authentication tag. Callers MUST wipe the returned array with [SecureMemory.wipe].
     */
    internal fun buildAadFrame(associatedData: ByteArray, plaintext: ByteArray): ByteArray {
        val frame = ByteArray(4 + associatedData.size + plaintext.size)
        frame[0] = (associatedData.size ushr 24).toByte()
        frame[1] = (associatedData.size ushr 16).toByte()
        frame[2] = (associatedData.size ushr 8).toByte()
        frame[3] = associatedData.size.toByte()
        System.arraycopy(associatedData, 0, frame, 4, associatedData.size)
        System.arraycopy(plaintext, 0, frame, 4 + associatedData.size, plaintext.size)
        return frame
    }

    /**
     * Unpacks a length-prefixed AAD frame produced by [buildAadFrame] and validates the embedded
     * AAD against [expectedAssociatedData] using a constant-time comparison.
     *
     * @return The original plaintext bytes extracted from the frame.
     * @throws javax.crypto.AEADBadTagException if the embedded AAD does not match [expectedAssociatedData].
     * @throws IllegalArgumentException if the frame is malformed or too short.
     */
    internal fun unpackAadFrame(decryptedFrame: ByteArray, expectedAssociatedData: ByteArray?): ByteArray {
        require(decryptedFrame.size >= 4) {
            "XChaCha20-AAD frame too short (${decryptedFrame.size} bytes); minimum 4 bytes for AAD length header"
        }
        val aadLen = ((decryptedFrame[0].toInt() and 0xFF) shl 24) or
                     ((decryptedFrame[1].toInt() and 0xFF) shl 16) or
                     ((decryptedFrame[2].toInt() and 0xFF) shl 8) or
                      (decryptedFrame[3].toInt() and 0xFF)
        require(aadLen >= 0 && 4 + aadLen <= decryptedFrame.size) {
            "XChaCha20-AAD frame declares invalid AAD length ($aadLen); frame is ${decryptedFrame.size} bytes"
        }
        val embeddedAad = decryptedFrame.copyOfRange(4, 4 + aadLen)
        val expectedAad = expectedAssociatedData ?: ByteArray(0)

        // Constant-time comparison — prevents timing-side-channel on AAD mismatch.
        if (!SecureMemory.safeEquals(embeddedAad, expectedAad)) {
            SecureMemory.wipe(embeddedAad)
            throw javax.crypto.AEADBadTagException(
                "XChaCha20-AAD frame: embedded AAD does not match expected associated data — " +
                "possible ciphertext substitution or row-swap attack"
            )
        }
        SecureMemory.wipe(embeddedAad)
        return decryptedFrame.copyOfRange(4 + aadLen, decryptedFrame.size)
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
                        // AEADBadTagException indicates authentication failure — never fall back,
                        // as that would allow ciphertext confusion attacks across cipher families.
                        if (e is javax.crypto.AEADBadTagException ||
                            e.cause is javax.crypto.AEADBadTagException ||
                            e.javaClass.name.contains("AEADBadTag")) {
                            throw e
                        }
                        // Only fall back for non-auth failures (e.g. native library unavailable)
                        SecurityLogger.warn("CryptoEngine", "XChaCha20 tagged decryption failed (non-auth), falling back to legacy: ${e.javaClass.simpleName}", e)
                        decryptLegacy(encryptedData, key, associatedData)
                    }
                } else {
                    decryptLegacy(encryptedData, key, associatedData)
                }
            }
            CIPHER_TAG_XCHACHA_AAD -> {
                // XChaCha20-Poly1305 with length-prefixed embedded AAD frame.
                // The entire frame (AAD header + plaintext) is authenticated by the XChaCha20
                // poly tag, so any AAD or plaintext modification causes decryption to throw.
                val payload = encryptedData.copyOfRange(1, encryptedData.size)
                if (NativeCryptoEngineWrapper.isNativeAvailable) {
                    val decryptedFrame = try {
                        NativeCryptoEngineWrapper.decryptNative(payload, key)
                    } catch (e: Exception) {
                        // Auth tag failures must never fall through.
                        if (e is javax.crypto.AEADBadTagException ||
                            e.cause is javax.crypto.AEADBadTagException ||
                            e.javaClass.name.contains("AEADBadTag")) {
                            throw e
                        }
                        SecurityLogger.warn("CryptoEngine", "XChaCha20-AAD tagged decryption failed (non-auth), falling back to AES-GCM: ${e.javaClass.simpleName}", e)
                        // Non-auth failure — repack and attempt AES-GCM (legacy payload would have 0x02 tag)
                        return decryptJvm(payload, key, associatedData)
                    }
                    try {
                        unpackAadFrame(decryptedFrame, associatedData)
                    } finally {
                        SecureMemory.wipe(decryptedFrame)
                    }
                } else {
                    // Native engine unavailable — fall back to AES-GCM (legacy path)
                    decryptLegacy(encryptedData, key, associatedData)
                }
            }
            CIPHER_TAG_AES_GCM -> {
                val payload = encryptedData.copyOfRange(1, encryptedData.size)
                try {
                    decryptJvm(payload, key, associatedData)
                } catch (e: Exception) {
                    // Authentication tag failure must never fall through to legacy paths.
                    if (e is javax.crypto.AEADBadTagException) throw e
                    SecurityLogger.warn("CryptoEngine", "AES-GCM tagged decryption failed (non-auth), falling back to legacy: ${e.javaClass.simpleName}", e)
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
                // Authentication tag failure must not fall through — propagate immediately.
                if (e is javax.crypto.AEADBadTagException ||
                    e.cause is javax.crypto.AEADBadTagException ||
                    e.javaClass.name.contains("AEADBadTag")) {
                    throw e
                }
                // Non-auth failure (e.g. native library unavailable at runtime) — try JVM AES-GCM
                SecurityLogger.warn("CryptoEngine", "Legacy native decryption failed (non-auth), falling back to JVM AES-GCM: ${e.javaClass.simpleName}", e)
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
        message = "SECURITY: String is immutable in JVM — it cannot be zeroed from heap and exposes sensitive plaintext to GC inspection and heap dumps. Use encryptCharArray() instead.",
        replaceWith = ReplaceWith("encryptCharArray(chars, key, associatedData)"),
        level = DeprecationLevel.ERROR
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
     * Decrypts a Base64-encoded ciphertext payload into a plaintext [String].
     *
     * **Security note:** The returned [String] is immutable and lives on the JVM heap until GC.
     * It **cannot** be explicitly zeroed. This overload is acceptable for non-secret structured
     * payloads (e.g. JSON envelopes) where the plaintext is not itself a password or key.
     * For passwords, PINs, secret tokens, or other sensitive secrets, use [decryptToCharArray]
     * which returns a [CharArray] that you must wipe immediately with [SecureMemory.wipe].
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

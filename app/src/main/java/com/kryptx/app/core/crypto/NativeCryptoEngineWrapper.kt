package com.kryptx.app.core.crypto

import android.util.Base64
import android.util.Log
import uniffi.kryptx_crypto.NativeCryptoEngine as RustEngine

/**
 * Production bridge for the Native Rust Cryptographic Engine (`kryptx_crypto`).
 *
 * Provides bare-metal Rust performance, memory-hard Argon2id, authenticated XChaCha20-Poly1305,
 * and hardware-backed Linux mlock buffer protection. Includes automatic fallback to BouncyCastle
 * when executing in headless host JVM test environments where Android .so libraries cannot be mapped.
 */
object NativeCryptoEngineWrapper {

    private const val TAG = "NativeCryptoEngine"

    private val engineInstance: RustEngine? by lazy {
        try {
            RustEngine()
        } catch (t: Throwable) {
            Log.w(TAG, "Native Rust engine not loaded on this host architecture; falling back to JVM engine: ${t.message}")
            null
        }
    }

    val isNativeAvailable: Boolean
        get() = engineInstance != null

    fun generateSalt(length: Int = 32): ByteArray {
        val eng = engineInstance
        return if (eng != null) {
            eng.generateSalt(length.toUInt())
        } else {
            KeyDerivation.generateSalt(length)
        }
    }

    fun generateVaultKey(): ByteArray {
        val eng = engineInstance
        return if (eng != null) {
            eng.generateSalt(32u)
        } else {
            CryptoEngine.generateVaultKey()
        }
    }

    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        // Direct RFC 9106 Argon2id derivation with zeroizable direct buffers,
        // avoiding immutable java.lang.String heap allocation and preserving memory barriers.
        return KeyDerivation.deriveKeyArgon2(password, salt)
    }

    fun encrypt(plaintext: ByteArray, key: ByteArray, associatedData: ByteArray? = null): ByteArray {
        val eng = engineInstance
        return if (eng != null && associatedData == null) {
            try {
                eng.encrypt(plaintext, key)
            } catch (t: Throwable) {
                CryptoEngine.encryptJvm(plaintext, key, associatedData)
            }
        } else {
            CryptoEngine.encryptJvm(plaintext, key, associatedData)
        }
    }

    fun decrypt(ciphertext: ByteArray, key: ByteArray, associatedData: ByteArray? = null): ByteArray {
        val eng = engineInstance
        return if (eng != null) {
            try {
                // If the ciphertext has a 24-byte nonce and was encrypted with XChaCha20-Poly1305
                eng.decrypt(ciphertext, key)
            } catch (t: Throwable) {
                // Fallback to AES-256-GCM if native decrypter fails or payload is AES-GCM
                CryptoEngine.decryptJvm(ciphertext, key, associatedData)
            }
        } else {
            CryptoEngine.decryptJvm(ciphertext, key, associatedData)
        }
    }

    fun encryptString(
        plainText: String,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): String {
        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        return try {
            val encrypted = encrypt(plainBytes, key, associatedData)
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } finally {
            SecureMemory.wipe(plainBytes)
        }
    }

    fun decryptString(
        encryptedBase64: String,
        key: ByteArray,
        associatedData: ByteArray? = null
    ): String {
        val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val decryptedBytes = decrypt(encryptedBytes, key, associatedData)
        return try {
            String(decryptedBytes, Charsets.UTF_8)
        } finally {
            SecureMemory.wipe(decryptedBytes)
        }
    }
}

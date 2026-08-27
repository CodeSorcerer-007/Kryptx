package com.kryptx.app.core.crypto

import uniffi.kryptx_crypto.NativeCryptoEngine as RustEngine

/**
 * A wrapper around the UniFFI-generated Rust NativeCryptoEngine.
 * Provides memory-safe XChaCha20 and Argon2id implementations running outside the JVM.
 */
object NativeCryptoEngineWrapper {
    
    // Lazy init to ensure the native library is loaded before instantiation
    private val engine by lazy { RustEngine() }
    
    fun generateSalt(length: Int = 32): ByteArray {
        return engine.generateSalt(length.toUInt())
    }

    fun generateVaultKey(): ByteArray {
        return engine.generateSalt(32u)
    }

    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        // Warning: Passing CharArray via String to uniffi temporarily exposes it to the String pool.
        // A future C API should be used for raw pointer passing to avoid this entirely.
        val passwordString = String(password)
        val result = try {
            engine.deriveKey(passwordString, salt)
        } finally {
            SecureMemory.wipe(password)
        }
        return result
    }
    
    fun encrypt(plaintext: ByteArray, key: ByteArray): ByteArray {
        return engine.encrypt(plaintext, key)
    }

    fun decrypt(ciphertext: ByteArray, key: ByteArray): ByteArray {
        return engine.decrypt(ciphertext, key)
    }
}

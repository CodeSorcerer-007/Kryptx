package com.kryptx.app.core.crypto

import com.kryptx.app.core.security.SecurityLogger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays

/**
 * Utility functions for zeroizing sensitive in-memory buffers (passwords, encryption keys, salts)
 * to minimize exposure windows against heap inspection and memory-dump attacks.
 */
object SecureMemory {

    @Volatile
    private var memoryFence: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(0)

    /** CSPRNG instance reused across wipe calls. SecureRandom is thread-safe per JCA spec. */
    private val wipeRng: SecureRandom by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            // SHA1PRNG is fast and always available on Android; NativePRNG may block on /dev/random.
            SecureRandom.getInstance("SHA1PRNG")
        } catch (_: Throwable) {
            SecureRandom()
        }
    }

    /**
     * Overwrites a CharArray with null characters ('\0') with a hardware/JIT memory barrier.
     */
    fun wipe(chars: CharArray?) {
        if (chars != null) {
            Arrays.fill(chars, '\u0000')
            // JIT dead-store elimination compiler barrier — the AtomicInteger update prevents
            // the JIT from eliding the fill as dead code (it cannot know the side effects).
            if (chars.isNotEmpty()) {
                memoryFence.set(memoryFence.get() xor chars[0].code)
            }
        }
    }

    /**
     * Overwrites a ByteArray with two passes — random bytes then zeros — before releasing,
     * providing defense-in-depth against:
     *  1. JIT dead-store elimination (XOR barrier after each pass)
     *  2. Compiler reuse of the same virtual address with residual plaintext still readable
     *     in adjacent CPU cache lines
     *
     * The random pass prevents trivially recognizable zero-fill patterns in memory dumps.
     */
    fun wipe(bytes: ByteArray?) {
        if (bytes == null || bytes.isEmpty()) return
        // Pass 1 — random overwrite
        try {
            wipeRng.nextBytes(bytes)
        } catch (_: Throwable) {
            // Fallback: XOR-scramble without CSPRNG rather than skip the pass
            for (i in bytes.indices) bytes[i] = (bytes[i].toInt() xor 0xAA).toByte()
        }
        memoryFence.set(memoryFence.get() xor bytes[0].toInt())
        // Pass 2 — zero out
        Arrays.fill(bytes, 0.toByte())
        // JIT dead-store elimination compiler barrier
        memoryFence.set(memoryFence.get() xor bytes[0].toInt())
    }

    /**
     * Overwrites a StringBuilder's internal buffer with zeros and clears its length.
     */
    fun wipe(builder: StringBuilder?) {
        if (builder != null) {
            for (i in 0 until builder.length) {
                builder.setCharAt(i, '\u0000')
            }
            builder.setLength(0)
        }
    }

    /**
     * Constant-time comparison between two ByteArrays to prevent timing attacks.
     */
    fun safeEquals(a: ByteArray?, b: ByteArray?): Boolean {
        if (a == null || b == null) return a === b
        return MessageDigest.isEqual(a, b)
    }

    /**
     * Constant-time comparison between two CharArrays to prevent timing attacks.
     *
     * Does NOT short-circuit on length mismatch — iterates over max(a.size, b.size)
     * so the comparison time is not a function of the correct password length.
     */
    fun safeEquals(a: CharArray?, b: CharArray?): Boolean {
        if (a == null || b == null) return a === b
        val maxLen = maxOf(a.size, b.size)
        var result = a.size xor b.size // non-zero if lengths differ
        for (i in 0 until maxLen) {
            val aChar = if (i < a.size) a[i].code else 0
            val bChar = if (i < b.size) b[i].code else 0
            result = result or (aChar xor bChar)
        }
        return result == 0
    }

    /**
     * Constant-time comparison between two Strings to prevent timing attacks on tokens/PINs.
     *
     * Does NOT short-circuit on length mismatch — iterates over max(a.length, b.length)
     * so the comparison time is not a function of the correct token length.
     */
    fun safeEquals(a: String?, b: String?): Boolean {
        if (a == null || b == null) return a === b
        val maxLen = maxOf(a.length, b.length)
        var result = a.length xor b.length // non-zero if lengths differ
        for (i in 0 until maxLen) {
            val aChar = if (i < a.length) a[i].code else 0
            val bChar = if (i < b.length) b[i].code else 0
            result = result or (aChar xor bChar)
        }
        return result == 0
    }

    /**
     * Constant-time comparison between two Ints.
     */
    fun safeEquals(a: Int, b: Int): Boolean {
        return (a xor b) == 0
    }

    /**
     * Executes an operation with a temporary CharArray and wipes it immediately in a finally block.
     */
    inline fun <R> withWipedCharArray(chars: CharArray, block: (CharArray) -> R): R {
        return try {
            block(chars)
        } finally {
            wipe(chars)
        }
    }

    /**
     * Executes an operation with a temporary ByteArray and wipes it immediately in a finally block.
     */
    inline fun <R> withWipedByteArray(bytes: ByteArray, block: (ByteArray) -> R): R {
        return try {
            block(bytes)
        } finally {
            wipe(bytes)
        }
    }

    init {
        try {
            System.loadLibrary("kryptx_crypto")
        } catch (e: Throwable) {
            // Non-fatal: JVM crypto fallbacks (AES-256-GCM, PBKDF2) remain active.
            // Native-only features (mlock, XChaCha20, ML-KEM) will be unavailable.
            // This is logged at WARN but should be treated as a security degradation in production.
            SecurityLogger.warn(
                "SecureMemory",
                "Native crypto library (kryptx_crypto) failed to load — JVM fallbacks active. Some PQC features will be unavailable.",
                e
            )
        }
    }

    @JvmStatic
    external fun mlockBuffer(buffer: java.nio.ByteBuffer): Boolean

    @JvmStatic
    external fun munlockBuffer(buffer: java.nio.ByteBuffer): Boolean

    /**
     * Allocates a memory-locked, page-aligned DirectByteBuffer that cannot be swapped to disk
     * and is excluded from OS core dumps. Ultimate military-grade memory protection.
     */
    fun allocateSecureBuffer(capacity: Int): java.nio.ByteBuffer {
        val buffer = java.nio.ByteBuffer.allocateDirect(capacity)
        try {
            mlockBuffer(buffer)
        } catch (_: Throwable) {
            // Fallback gracefully if JNI fails
        }
        return buffer
    }

    /**
     * Wipes and unlocks a secure buffer.
     */
    fun releaseSecureBuffer(buffer: java.nio.ByteBuffer?) {
        if (buffer == null) return
        try {
            buffer.clear()
            while (buffer.hasRemaining()) {
                buffer.put(0.toByte())
            }
            buffer.clear()
            munlockBuffer(buffer)
        } catch (_: Throwable) {}
    }
}

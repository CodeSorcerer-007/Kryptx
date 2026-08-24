package com.kryptx.app.core.crypto

import java.security.MessageDigest
import java.util.Arrays

/**
 * Utility functions for zeroizing sensitive in-memory buffers (passwords, encryption keys, salts)
 * to minimize exposure windows against heap inspection and memory-dump attacks.
 */
object SecureMemory {

    /**
     * Overwrites a CharArray with null characters ('\0').
     */
    fun wipe(chars: CharArray?) {
        if (chars != null) {
            Arrays.fill(chars, '\u0000')
        }
    }

    /**
     * Overwrites a ByteArray with zeros.
     */
    fun wipe(bytes: ByteArray?) {
        if (bytes != null) {
            Arrays.fill(bytes, 0.toByte())
        }
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
     */
    fun safeEquals(a: CharArray?, b: CharArray?): Boolean {
        if (a == null || b == null) return a === b
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
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
}


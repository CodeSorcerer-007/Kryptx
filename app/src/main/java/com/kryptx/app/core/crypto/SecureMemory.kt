package com.kryptx.app.core.crypto

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

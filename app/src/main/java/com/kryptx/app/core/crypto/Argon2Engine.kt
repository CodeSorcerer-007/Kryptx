package com.kryptx.app.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * Standard, RFC 9106 compliant Argon2id Key Derivation Function engine.
 * Powered by certified Bouncy Castle cryptographic primitives (Blake2b, version 1.3).
 *
 * Argon2 is the winner of the Password Hashing Competition (PHC) and is recommended by OWASP
 * for maximum resistance against GPU, ASIC, and FPGA brute-force cracking attacks through
 * memory-hard memory layout and data-dependent/independent permutation rounds.
 */
object Argon2Engine {

    const val ARGON2ID_TYPE = Argon2Parameters.ARGON2_id
    const val ARGON2_VERSION = Argon2Parameters.ARGON2_VERSION_13

    /**
     * Standard RFC 9106 Argon2id cryptographic profiles.
     */
    data class Argon2Params(
        val memoryCostKb: Int = 16_384, // 16 MB standard mobile memory footprint
        val iterations: Int = 3,         // 3 passes
        val parallelism: Int = 1,        // 1 lane
        val outputLength: Int = 32       // 256-bit output key
    ) {
        companion object {
            val DEFAULT = Argon2Params(memoryCostKb = 16_384, iterations = 3, parallelism = 1, outputLength = 32)
            val HIGH_SECURITY = Argon2Params(memoryCostKb = 65_536, iterations = 4, parallelism = 1, outputLength = 32)
            val FAST_TEST = Argon2Params(memoryCostKb = 64, iterations = 1, parallelism = 1, outputLength = 32)
        }
    }

    /**
     * Derives a cryptographic key using standard RFC 9106 Argon2id from a master password CharArray.
     *
     * @param password CharArray containing the master password.
     * @param salt Cryptographically secure salt byte array.
     * @param params Argon2 parameter specifications (memory in KB, iterations, parallelism, output length).
     * @return Derived key byte array.
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        params: Argon2Params = Argon2Params.DEFAULT
    ): ByteArray {
        val passwordBytes = charArrayToUtf8(password)
        return try {
            deriveKey(passwordBytes, salt, params)
        } finally {
            SecureMemory.wipe(passwordBytes)
        }
    }

    /**
     * Raw byte-array variant of RFC 9106 Argon2id key derivation.
     */
    fun deriveKey(
        passwordBytes: ByteArray,
        salt: ByteArray,
        params: Argon2Params = Argon2Params.DEFAULT
    ): ByteArray {
        require(salt.isNotEmpty()) { "Salt cannot be empty" }
        require(params.outputLength in 4..1024) { "Output length must be between 4 and 1024 bytes" }
        require(params.memoryCostKb >= 8 * params.parallelism) { "Memory cost must be at least 8 * parallelism KB" }
        require(params.iterations >= 1) { "Iterations must be at least 1" }

        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(params.memoryCostKb)
            .withIterations(params.iterations)
            .withParallelism(params.parallelism)
            .withSalt(salt)

        val generator = Argon2BytesGenerator()
        generator.init(builder.build())

        val result = ByteArray(params.outputLength)
        generator.generateBytes(passwordBytes, result, 0, result.size)
        return result
    }

    /**
     * Converts a CharArray password into a UTF-8 byte array without intermediate immutable Strings.
     */
    fun charArrayToUtf8(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer: ByteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        // Zero out intermediate byte buffer backing array if accessible
        if (byteBuffer.hasArray()) {
            Arrays.fill(byteBuffer.array(), 0.toByte())
        }
        return bytes
    }
}

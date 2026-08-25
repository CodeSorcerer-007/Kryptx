package com.kryptx.app.core.crypto

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class CryptoMicroBenchmarkTest {

    private val secureRandom = SecureRandom()

    @Test
    fun benchmarkAes256GcmThroughput() {
        val key = CryptoEngine.generateVaultKey()
        val payload1Mb = ByteArray(1024 * 1024)
        secureRandom.nextBytes(payload1Mb)

        val startTime = System.currentTimeMillis()
        val iterations = 10
        for (i in 0 until iterations) {
            val encrypted = CryptoEngine.encrypt(payload1Mb, key)
            val decrypted = CryptoEngine.decrypt(encrypted, key)
            assertTrue(decrypted.size == payload1Mb.size)
        }
        val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
        val totalMb = iterations * 2 // 1MB encrypt + 1MB decrypt per iteration
        val mbPerSec = (totalMb.toDouble() / (elapsedMs.toDouble() / 1000.0))

        println("[CryptoBenchmark] AES-256-GCM Throughput: %.2f MB/s ($elapsedMs ms for $totalMb MB)".format(mbPerSec))
        assertTrue("AES-GCM throughput should exceed 1 MB/s on JVM", mbPerSec > 1.0)
    }

    @Test
    fun benchmarkPostQuantumMlkem768Latency() {
        val keyPair = PostQuantumEngine.generateKeyPair()
        assertNotNull(keyPair.publicKey)
        assertNotNull(keyPair.privateKey)

        val startTime = System.currentTimeMillis()
        val iterations = 50
        for (i in 0 until iterations) {
            val enc = PostQuantumEngine.encapsulate(keyPair.publicKey)
            val secret = PostQuantumEngine.decapsulate(enc.encapsulation, keyPair.privateKey)
            assertTrue(secret.isNotEmpty())
        }
        val elapsedMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
        val avgLatencyMs = elapsedMs.toDouble() / iterations

        println("[CryptoBenchmark] ML-KEM-768 Kyber KEM Latency: %.2f ms/op ($iterations ops in $elapsedMs ms)".format(avgLatencyMs))
        assertTrue("ML-KEM-768 avg latency should be under 50ms", avgLatencyMs < 50.0)
    }

    @Test
    fun benchmarkArgon2idDerivation() {
        val password = "StrongMasterPassword123!".toCharArray()
        val salt = ByteArray(32).apply { secureRandom.nextBytes(this) }
        val params = Argon2Engine.Argon2Params.FAST_TEST

        val startTime = System.currentTimeMillis()
        val derived = Argon2Engine.deriveKey(password, salt, params)
        val elapsedMs = System.currentTimeMillis() - startTime

        assertNotNull(derived)
        assertTrue(derived.size == 32)
        println("[CryptoBenchmark] Argon2id Fast Test derivation time: $elapsedMs ms")
    }

    @Test
    fun benchmarkHardwareEntropyHarvester() {
        val startTime = System.currentTimeMillis()
        val entropy = HardwareEntropyHarvester.harvestEntropy(32)
        val elapsedMs = System.currentTimeMillis() - startTime

        assertNotNull(entropy)
        assertTrue(entropy.size == 32)
        println("[CryptoBenchmark] HardwareEntropyHarvester 32-byte harvest time: $elapsedMs ms")
    }
}

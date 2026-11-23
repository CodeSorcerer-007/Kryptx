package com.kryptx.app.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class ShamirSecretSharingTest {

    private val secureRandom = SecureRandom()

    @Test
    fun testGaloisFieldAxioms() {
        // Multiplicative identity: a * 1 == a
        for (a in 1..255) {
            assertEquals(a, ShamirSecretSharing.gfMul(a, 1))
        }

        // Multiplicative inverse: a * (1 / a) == 1
        for (a in 1..255) {
            val inv = ShamirSecretSharing.gfDiv(1, a)
            assertEquals(1, ShamirSecretSharing.gfMul(a, inv))
        }

        // Zero multiplication: a * 0 == 0
        for (a in 0..255) {
            assertEquals(0, ShamirSecretSharing.gfMul(a, 0))
            assertEquals(0, ShamirSecretSharing.gfMul(0, a))
        }

        // Self-inverse addition: a xor a == 0
        for (a in 0..255) {
            assertEquals(0, ShamirSecretSharing.gfAdd(a, a))
        }
    }

    @Test
    fun testTwoOfThreeSecretSharingRoundTrip() {
        val secret = "Kryptx-Master-Seed-Secret-Key-12345678".toByteArray(Charsets.UTF_8)
        val shares = ShamirSecretSharing.split(secret, threshold = 2, totalShares = 3)

        assertEquals(3, shares.size)
        assertEquals(2, shares[0].threshold)
        assertEquals(3, shares[0].totalShares)

        // Any 2 shares must reconstruct the exact secret
        val comb12 = ShamirSecretSharing.combine(listOf(shares[0], shares[1]))
        assertArrayEquals(secret, comb12)

        val comb23 = ShamirSecretSharing.combine(listOf(shares[1], shares[2]))
        assertArrayEquals(secret, comb23)

        val comb13 = ShamirSecretSharing.combine(listOf(shares[0], shares[2]))
        assertArrayEquals(secret, comb13)
    }

    @Test
    fun testThreeOfFiveAllCombinationsReconstructCorrectly() {
        val secret = ByteArray(32).also { secureRandom.nextBytes(it) }
        val shares = ShamirSecretSharing.split(secret, threshold = 3, totalShares = 5)

        assertEquals(5, shares.size)

        // All 10 combinations of 3 shares from 5
        val indices = listOf(0, 1, 2, 3, 4)
        var testedCombinations = 0
        for (i in 0 until 5) {
            for (j in (i + 1) until 5) {
                for (k in (j + 1) until 5) {
                    val subset = listOf(shares[i], shares[j], shares[k])
                    val reconstructed = ShamirSecretSharing.combine(subset)
                    assertArrayEquals("Failed on combo ($i, $j, $k)", secret, reconstructed)
                    testedCombinations++
                }
            }
        }
        assertEquals(10, testedCombinations)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInsufficientSharesThrowsException() {
        val secret = "Ultra-Secure-Recovery-Key".toByteArray()
        val shares = ShamirSecretSharing.split(secret, threshold = 3, totalShares = 5)

        // Only 2 shares provided when 3 are required
        ShamirSecretSharing.combine(listOf(shares[0], shares[1]))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDuplicateShareIndicesRejected() {
        val secret = "Ultra-Secure-Recovery-Key".toByteArray()
        val shares = ShamirSecretSharing.split(secret, threshold = 2, totalShares = 3)

        // Provide share 0 twice
        ShamirSecretSharing.combine(listOf(shares[0], shares[0]))
    }

    @Test
    fun testShareStringSerializationRoundTrip() {
        val secret = "Cryptographic-Seed-Payload-Test".toByteArray()
        val shares = ShamirSecretSharing.split(secret, threshold = 2, totalShares = 4)

        for (share in shares) {
            val encoded = share.toEncodedString()
            assertTrue(encoded.startsWith("KSS1-"))

            val parsed = ShamirShare.fromEncodedString(encoded)
            assertEquals(share.threshold, parsed.threshold)
            assertEquals(share.totalShares, parsed.totalShares)
            assertEquals(share.index, parsed.index)
            assertArrayEquals(share.identifier, parsed.identifier)
            assertArrayEquals(share.data, parsed.data)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCorruptedSharePayloadFailsChecksum() {
        val secret = "Secret-Token-123".toByteArray()
        val shares = ShamirSecretSharing.split(secret, threshold = 2, totalShares = 3)
        val originalEncoded = shares[0].toEncodedString()

        // Tamper with one character in payload
        val parts = originalEncoded.split("-").toMutableList()
        val payload = parts[4]
        val tamperedPayload = if (payload.startsWith("0")) "1" + payload.substring(1) else "0" + payload.substring(1)
        parts[4] = tamperedPayload
        val tamperedEncoded = parts.joinToString("-")

        ShamirShare.fromEncodedString(tamperedEncoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMismatchedIdentifiersRejected() {
        val secretA = "Secret-Vault-A".toByteArray()
        val secretB = "Secret-Vault-B".toByteArray()

        val sharesA = ShamirSecretSharing.split(secretA, threshold = 2, totalShares = 3)
        val sharesB = ShamirSecretSharing.split(secretB, threshold = 2, totalShares = 3)

        // Mix share from vault A with share from vault B
        ShamirSecretSharing.combine(listOf(sharesA[0], sharesB[1]))
    }

    @Test
    fun testFormattedCardTextReadability() {
        val secret = ByteArray(16).also { secureRandom.nextBytes(it) }
        val shares = ShamirSecretSharing.split(secret, threshold = 2, totalShares = 3)
        val cardText = shares[0].toFormattedCardText()

        assertTrue(cardText.contains(" "))
        // Should parse cleanly back when spaces are present
        val parsed = ShamirShare.fromEncodedString(cardText)
        assertEquals(shares[0], parsed)
    }
}

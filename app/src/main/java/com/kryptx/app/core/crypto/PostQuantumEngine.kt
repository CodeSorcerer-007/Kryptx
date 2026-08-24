package com.kryptx.app.core.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMExtractor
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyGenerationParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyPairGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters
import java.security.SecureRandom
import java.util.Base64

/**
 * Quantum-Resistant Hybrid Key Encapsulation Engine.
 *
 * Implements NIST FIPS 203 ML-KEM-768 (Kyber) Post-Quantum Cryptography coupled with
 * HKDF-SHA256 hybrid secret derivation to defend against "Harvest Now, Decrypt Later"
 * quantum computing attacks during P2P sync and encrypted backup archive generation.
 */
object PostQuantumEngine {

    private val secureRandom = SecureRandom()
    private val mlkemParams = MLKEMParameters.ml_kem_768

    data class PqcKeyPair(
        val publicKey: ByteArray,
        val privateKey: ByteArray
    ) {
        val publicKeyBase64: String get() = Base64.getEncoder().encodeToString(publicKey)
        val privateKeyBase64: String get() = Base64.getEncoder().encodeToString(privateKey)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as PqcKeyPair
            return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
        }

        override fun hashCode(): Int {
            var result = publicKey.contentHashCode()
            result = 31 * result + privateKey.contentHashCode()
            return result
        }
    }

    data class EncapsulatedPayload(
        val encapsulation: ByteArray,
        val sharedSecret: ByteArray
    ) {
        val encapsulationBase64: String get() = Base64.getEncoder().encodeToString(encapsulation)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EncapsulatedPayload
            return encapsulation.contentEquals(other.encapsulation) && sharedSecret.contentEquals(other.sharedSecret)
        }

        override fun hashCode(): Int {
            var result = encapsulation.contentHashCode()
            result = 31 * result + sharedSecret.contentHashCode()
            return result
        }
    }

    /**
     * Generates a new ML-KEM-768 key pair.
     */
    fun generateKeyPair(): PqcKeyPair {
        val keyGen = MLKEMKeyPairGenerator()
        keyGen.init(MLKEMKeyGenerationParameters(secureRandom, mlkemParams))
        val pair = keyGen.generateKeyPair()

        val pub = (pair.public as MLKEMPublicKeyParameters).encoded
        val priv = (pair.private as MLKEMPrivateKeyParameters).encoded
        return PqcKeyPair(publicKey = pub, privateKey = priv)
    }

    /**
     * Encapsulates a shared secret against a recipient's ML-KEM-768 public key.
     * Combines with an optional classical secret via HKDF to form a true hybrid secret.
     */
    fun encapsulate(
        recipientPublicKeyBytes: ByteArray,
        classicalSaltOrSecret: ByteArray? = null
    ): EncapsulatedPayload {
        val pubParams = MLKEMPublicKeyParameters(mlkemParams, recipientPublicKeyBytes)
        val generator = MLKEMGenerator(secureRandom)
        val secretWithEncapsulation = generator.generateEncapsulated(pubParams)

        val rawPqcSecret = secretWithEncapsulation.secret
        val encapsulation = secretWithEncapsulation.encapsulation

        val derivedHybridSecret = deriveHybridSecret(rawPqcSecret, classicalSaltOrSecret)
        SecureMemory.wipe(rawPqcSecret)

        return EncapsulatedPayload(
            encapsulation = encapsulation,
            sharedSecret = derivedHybridSecret
        )
    }

    /**
     * Decapsulates the shared secret using the recipient's ML-KEM-768 private key.
     */
    fun decapsulate(
        encapsulationBytes: ByteArray,
        privateKeyBytes: ByteArray,
        classicalSaltOrSecret: ByteArray? = null
    ): ByteArray {
        val privParams = MLKEMPrivateKeyParameters(mlkemParams, privateKeyBytes)
        val extractor = MLKEMExtractor(privParams)
        val rawPqcSecret = extractor.extractSecret(encapsulationBytes)

        val derivedHybridSecret = deriveHybridSecret(rawPqcSecret, classicalSaltOrSecret)
        SecureMemory.wipe(rawPqcSecret)

        return derivedHybridSecret
    }

    /**
     * Derives a 256-bit symmetric encryption key using HKDF-SHA256 over the PQC secret and classical material.
     */
    fun deriveHybridSecret(
        pqcSecret: ByteArray,
        classicalMaterial: ByteArray?
    ): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        val info = "Kryptx-MLKEM768-Hybrid-v1".toByteArray(Charsets.UTF_8)
        val salt = classicalMaterial ?: ByteArray(32)

        hkdf.init(HKDFParameters(pqcSecret, salt, info))
        val outputKey = ByteArray(32)
        hkdf.generateBytes(outputKey, 0, 32)
        return outputKey
    }

    /**
     * Encrypts data with Post-Quantum Hybrid protection.
     * Returns: [PQC Encapsulation Header] + [AES-256-GCM Ciphertext].
     */
    fun encryptHybrid(
        plaintext: ByteArray,
        recipientPublicKeyBytes: ByteArray,
        associatedData: ByteArray? = null
    ): Pair<ByteArray, ByteArray> {
        val encapsulated = encapsulate(recipientPublicKeyBytes)
        val ciphertext = CryptoEngine.encrypt(plaintext, encapsulated.sharedSecret, associatedData)
        return Pair(encapsulated.encapsulation, ciphertext)
    }

    /**
     * Decrypts Post-Quantum Hybrid ciphertext.
     */
    fun decryptHybrid(
        encapsulationBytes: ByteArray,
        ciphertextBytes: ByteArray,
        privateKeyBytes: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray {
        val sharedSecret = decapsulate(encapsulationBytes, privateKeyBytes)
        return try {
            CryptoEngine.decrypt(ciphertextBytes, sharedSecret, associatedData)
        } finally {
            SecureMemory.wipe(sharedSecret)
        }
    }
}

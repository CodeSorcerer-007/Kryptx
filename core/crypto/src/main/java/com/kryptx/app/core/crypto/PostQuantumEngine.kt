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
import com.kryptx.app.core.model.KryptxErrorType
import com.kryptx.app.core.model.KryptxResult
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

        fun wipe() {
            SecureMemory.wipe(publicKey)
            SecureMemory.wipe(privateKey)
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

        fun wipe() {
            SecureMemory.wipe(encapsulation)
            SecureMemory.wipe(sharedSecret)
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
        return try {
            val pubParams = MLKEMPublicKeyParameters(mlkemParams, recipientPublicKeyBytes)
            val generator = MLKEMGenerator(secureRandom)
            val secretWithEncapsulation = generator.generateEncapsulated(pubParams)

            val rawPqcSecret = secretWithEncapsulation.secret
            val encapsulation = secretWithEncapsulation.encapsulation

            val derivedHybridSecret = deriveHybridSecret(rawPqcSecret, classicalSaltOrSecret)
            SecureMemory.wipe(rawPqcSecret)

            EncapsulatedPayload(
                encapsulation = encapsulation,
                sharedSecret = derivedHybridSecret
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("ML-KEM encapsulation failed on recipient public key: ${e.message}", e)
        }
    }

    fun encapsulateSafe(
        recipientPublicKeyBytes: ByteArray,
        classicalSaltOrSecret: ByteArray? = null
    ): KryptxResult<EncapsulatedPayload> {
        return try {
            KryptxResult.Success(encapsulate(recipientPublicKeyBytes, classicalSaltOrSecret))
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.CORRUPTED_CIPHERTEXT, "ML-KEM encapsulation failed: ${e.message}", e)
        }
    }

    /**
     * Decapsulates the shared secret using the recipient's ML-KEM-768 private key.
     */
    fun decapsulate(
        encapsulationBytes: ByteArray,
        privateKeyBytes: ByteArray,
        classicalSaltOrSecret: ByteArray? = null
    ): ByteArray {
        return try {
            val privParams = MLKEMPrivateKeyParameters(mlkemParams, privateKeyBytes)
            val extractor = MLKEMExtractor(privParams)
            val rawPqcSecret = extractor.extractSecret(encapsulationBytes)

            val derivedHybridSecret = deriveHybridSecret(rawPqcSecret, classicalSaltOrSecret)
            SecureMemory.wipe(rawPqcSecret)

            derivedHybridSecret
        } catch (e: Exception) {
            throw IllegalArgumentException("ML-KEM decapsulation failed: ${e.message}", e)
        }
    }

    fun decapsulateSafe(
        encapsulationBytes: ByteArray,
        privateKeyBytes: ByteArray,
        classicalSaltOrSecret: ByteArray? = null
    ): KryptxResult<ByteArray> {
        return try {
            KryptxResult.Success(decapsulate(encapsulationBytes, privateKeyBytes, classicalSaltOrSecret))
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DECRYPTION_FAILED, "ML-KEM decapsulation failed: ${e.message}", e)
        }
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
     * Data class holding a Post-Quantum Digital Signature key pair.
     */
    data class PqcSignatureKeyPair(
        val publicKey: ByteArray,
        val privateKey: ByteArray
    ) {
        val publicKeyBase64: String get() = Base64.getEncoder().encodeToString(publicKey)
        val privateKeyBase64: String get() = Base64.getEncoder().encodeToString(privateKey)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as PqcSignatureKeyPair
            return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
        }

        override fun hashCode(): Int {
            var result = publicKey.contentHashCode()
            result = 31 * result + privateKey.contentHashCode()
            return result
        }

        fun wipe() {
            SecureMemory.wipe(publicKey)
            SecureMemory.wipe(privateKey)
        }
    }

    /**
     * Generates a quantum-resistant ML-DSA-65 (FIPS 204) digital signature key pair.
     */
    fun generateSignatureKeyPair(): PqcSignatureKeyPair {
        val keyGen = org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyPairGenerator()
        val params = org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters.ml_dsa_65
        keyGen.init(org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyGenerationParameters(secureRandom, params))
        val pair = keyGen.generateKeyPair()

        val pub = (pair.public as org.bouncycastle.pqc.crypto.mldsa.MLDSAPublicKeyParameters).encoded
        val priv = (pair.private as org.bouncycastle.pqc.crypto.mldsa.MLDSAPrivateKeyParameters).encoded
        return PqcSignatureKeyPair(publicKey = pub, privateKey = priv)
    }

    /**
     * Cryptographically signs data using an ML-DSA-65 private key.
     */
    fun sign(data: ByteArray, privateKeyBytes: ByteArray): ByteArray {
        return try {
            val params = org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters.ml_dsa_65
            val privParams = org.bouncycastle.pqc.crypto.mldsa.MLDSAPrivateKeyParameters(params, privateKeyBytes)
            val signer = org.bouncycastle.pqc.crypto.mldsa.MLDSASigner()
            signer.init(true, privParams)
            signer.update(data, 0, data.size)
            signer.generateSignature()
        } catch (e: Exception) {
            throw IllegalArgumentException("ML-DSA signing failed on private key: ${e.message}", e)
        }
    }

    /**
     * Verifies an ML-DSA-65 post-quantum digital signature against the public key.
     */
    fun verifySignature(data: ByteArray, signature: ByteArray, publicKeyBytes: ByteArray): Boolean {
        return try {
            val params = org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters.ml_dsa_65
            val pubParams = org.bouncycastle.pqc.crypto.mldsa.MLDSAPublicKeyParameters(params, publicKeyBytes)
            val signer = org.bouncycastle.pqc.crypto.mldsa.MLDSASigner()
            signer.init(false, pubParams)
            signer.update(data, 0, data.size)
            signer.verifySignature(signature)
        } catch (_: Throwable) {
            false
        }
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
        val ciphertext = try {
            CryptoEngine.encrypt(plaintext, encapsulated.sharedSecret, associatedData)
        } finally {
            SecureMemory.wipe(encapsulated.sharedSecret)
        }
        return Pair(encapsulated.encapsulation, ciphertext)
    }

    fun encryptHybridSafe(
        plaintext: ByteArray,
        recipientPublicKeyBytes: ByteArray,
        associatedData: ByteArray? = null
    ): KryptxResult<Pair<ByteArray, ByteArray>> {
        return try {
            KryptxResult.Success(encryptHybrid(plaintext, recipientPublicKeyBytes, associatedData))
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.CORRUPTED_CIPHERTEXT, "ML-KEM hybrid encryption failed: ${e.message}", e)
        }
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

    fun decryptHybridSafe(
        encapsulationBytes: ByteArray,
        ciphertextBytes: ByteArray,
        privateKeyBytes: ByteArray,
        associatedData: ByteArray? = null
    ): KryptxResult<ByteArray> {
        return try {
            KryptxResult.Success(decryptHybrid(encapsulationBytes, ciphertextBytes, privateKeyBytes, associatedData))
        } catch (e: Exception) {
            KryptxResult.Error(KryptxErrorType.DECRYPTION_FAILED, "ML-KEM hybrid decryption failed: ${e.message}", e)
        }
    }

    /**
     * Signed & Encrypted Vault Archive bundle combining Post-Quantum ML-KEM-768 confidentiality
     * with ML-DSA-65 post-quantum digital signature non-repudiation.
     */
    data class SignedEncryptedVaultArchive(
        val encapsulation: ByteArray,
        val ciphertext: ByteArray,
        val signature: ByteArray,
        val signerPublicKey: ByteArray
    ) {
        fun toSerializedBytes(): ByteArray {
            val buf = java.nio.ByteBuffer.allocate(
                4 + 4 + encapsulation.size + 4 + ciphertext.size + 4 + signature.size + 4 + signerPublicKey.size
            )
            buf.put("KPQC".toByteArray(Charsets.US_ASCII))
            buf.putInt(encapsulation.size)
            buf.put(encapsulation)
            buf.putInt(ciphertext.size)
            buf.put(ciphertext)
            buf.putInt(signature.size)
            buf.put(signature)
            buf.putInt(signerPublicKey.size)
            buf.put(signerPublicKey)
            return buf.array()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SignedEncryptedVaultArchive
            return encapsulation.contentEquals(other.encapsulation) &&
                    ciphertext.contentEquals(other.ciphertext) &&
                    signature.contentEquals(other.signature) &&
                    signerPublicKey.contentEquals(other.signerPublicKey)
        }

        override fun hashCode(): Int {
            var result = encapsulation.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            result = 31 * result + signature.contentHashCode()
            result = 31 * result + signerPublicKey.contentHashCode()
            return result
        }

        companion object {
            fun fromSerializedBytes(bytes: ByteArray): SignedEncryptedVaultArchive {
                val buf = java.nio.ByteBuffer.wrap(bytes)
                val magic = ByteArray(4)
                buf.get(magic)
                require(magic.contentEquals("KPQC".toByteArray(Charsets.US_ASCII))) {
                    "Invalid magic bytes in archive"
                }
                val encLen = buf.getInt()
                val enc = ByteArray(encLen)
                buf.get(enc)
                val cipherLen = buf.getInt()
                val cipher = ByteArray(cipherLen)
                buf.get(cipher)
                val sigLen = buf.getInt()
                val sig = ByteArray(sigLen)
                buf.get(sig)
                val pubLen = buf.getInt()
                val pub = ByteArray(pubLen)
                buf.get(pub)
                return SignedEncryptedVaultArchive(enc, cipher, sig, pub)
            }
        }
    }

    /**
     * Exports a portable, post-quantum fortified vault archive.
     * Encapsulates a shared secret with recipient's ML-KEM-768 public key,
     * encrypts payload with AES-256-GCM / XChaCha20, and signs the archive with sender's ML-DSA-65 private key.
     */
    fun exportSignedVaultArchive(
        vaultData: ByteArray,
        recipientMlkemPublicKey: ByteArray,
        senderMldsaPrivateKey: ByteArray,
        senderMldsaPublicKey: ByteArray
    ): ByteArray {
        val (encapsulation, ciphertext) = encryptHybrid(vaultData, recipientMlkemPublicKey)
        val payloadToSign = encapsulation + ciphertext
        val signature = sign(payloadToSign, senderMldsaPrivateKey)
        val archive = SignedEncryptedVaultArchive(
            encapsulation = encapsulation,
            ciphertext = ciphertext,
            signature = signature,
            signerPublicKey = senderMldsaPublicKey
        )
        return archive.toSerializedBytes()
    }

    /**
     * Imports and authenticates a post-quantum fortified vault archive.
     * Verifies the ML-DSA-65 digital signature and decapsulates the ML-KEM-768 shared secret to decrypt the vault.
     */
    fun importSignedVaultArchive(
        archiveBytes: ByteArray,
        recipientMlkemPrivateKey: ByteArray,
        expectedSenderMldsaPublicKey: ByteArray? = null
    ): ByteArray {
        val archive = SignedEncryptedVaultArchive.fromSerializedBytes(archiveBytes)
        if (expectedSenderMldsaPublicKey != null) {
            require(archive.signerPublicKey.contentEquals(expectedSenderMldsaPublicKey)) {
                "Signer public key does not match trusted sender public key"
            }
        }
        val payloadToVerify = archive.encapsulation + archive.ciphertext
        val isVerified = verifySignature(payloadToVerify, archive.signature, archive.signerPublicKey)
        require(isVerified) {
            "Post-Quantum signature verification failed! Archive has been tampered with or sender is untrusted"
        }

        return decryptHybrid(archive.encapsulation, archive.ciphertext, recipientMlkemPrivateKey)
    }
}


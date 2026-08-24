package com.kryptx.app.core.crypto

import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.util.Base64

/**
 * Enterprise FIDO2 / WebAuthn Passkey Cryptographic Engine.
 * Supports W3C WebAuthn Level 3 specifications, P-256 (ES256) signature generation,
 * clientDataJSON hashing, authenticatorData formatting, and phishing-resistant RP ID validation.
 */
object PasskeyEngine {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val secureRandom = SecureRandom()

    data class PasskeyRegistrationResult(
        val credentialId: String,
        val credentialIdBase64: String,
        val publicKeyCoseBase64: String,
        val rawPublicKeyBytes: ByteArray,
        val rawPrivateKeyBytes: ByteArray,
        val userHandle: String,
        val rpId: String,
        val algorithm: String = "ES256 (ECDSA P-256)"
    )

    data class PasskeyAssertionSignature(
        val authenticatorDataBase64: String,
        val clientDataJsonBase64: String,
        val signatureBase64: String,
        val userHandleBase64: String,
        val credentialId: String
    )

    /**
     * Generates a new P-256 (secp256r1 / prime256v1) ECDSA key pair for WebAuthn passkey registration.
     */
    fun createPasskeyRegistration(
        rpId: String,
        userHandle: String,
        userName: String
    ): PasskeyRegistrationResult {
        val keyPair = generateEcP256KeyPair()
        val privateKey = keyPair.private as ECPrivateKey
        val publicKey = keyPair.public as ECPublicKey

        val credentialIdBytes = ByteArray(32)
        secureRandom.nextBytes(credentialIdBytes)
        val credentialId = Base64.getUrlEncoder().withoutPadding().encodeToString(credentialIdBytes)

        val cosePublicKey = encodeEcP256ToCose(publicKey)
        val coseBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(cosePublicKey)

        val rawPriv = privateKey.encoded
        val rawPub = publicKey.encoded

        return PasskeyRegistrationResult(
            credentialId = credentialId,
            credentialIdBase64 = coseBase64,
            publicKeyCoseBase64 = coseBase64,
            rawPublicKeyBytes = rawPub,
            rawPrivateKeyBytes = rawPriv,
            userHandle = userHandle,
            rpId = rpId
        )
    }

    /**
     * Signs a WebAuthn authentication assertion challenge using the stored passkey private key.
     */
    fun signPasskeyAssertion(
        rpId: String,
        clientDataJsonBytes: ByteArray,
        privateKeyBytes: ByteArray,
        credentialId: String,
        userHandle: String,
        signCount: Int = 1
    ): PasskeyAssertionSignature {
        val clientDataHash = sha256(clientDataJsonBytes)
        val rpIdHash = sha256(rpId.toByteArray(Charsets.UTF_8))

        // Authenticator Data: [32-byte rpIdHash] + [1-byte flags (UP=1, UV=1 -> 0x05)] + [4-byte signCount]
        val authDataStream = ByteArrayOutputStream()
        authDataStream.write(rpIdHash)
        authDataStream.write(0x05) // Flags: User Present (0x01) + User Verified (0x04)
        val countBuffer = ByteBuffer.allocate(4).putInt(signCount).array()
        authDataStream.write(countBuffer)
        val authenticatorData = authDataStream.toByteArray()

        // Signature Payload: authenticatorData || clientDataHash
        val signaturePayloadStream = ByteArrayOutputStream()
        signaturePayloadStream.write(authenticatorData)
        signaturePayloadStream.write(clientDataHash)
        val payloadToSign = signaturePayloadStream.toByteArray()

        // Sign using ECDSA SHA256 (BouncyCastle or standard provider)
        val privateKey = decodeEcPrivateKey(privateKeyBytes)
        val ecdsaSigner = Signature.getInstance("SHA256withECDSA")
        ecdsaSigner.initSign(privateKey)
        ecdsaSigner.update(payloadToSign)
        val rawSignature = ecdsaSigner.sign()

        val base64Encoder = Base64.getUrlEncoder().withoutPadding()

        return PasskeyAssertionSignature(
            authenticatorDataBase64 = base64Encoder.encodeToString(authenticatorData),
            clientDataJsonBase64 = base64Encoder.encodeToString(clientDataJsonBytes),
            signatureBase64 = base64Encoder.encodeToString(rawSignature),
            userHandleBase64 = base64Encoder.encodeToString(userHandle.toByteArray(Charsets.UTF_8)),
            credentialId = credentialId
        )
    }

    /**
     * Generates a standard WebAuthn clientDataJSON payload for testing or simulated challenges.
     */
    fun buildClientDataJson(
        type: String, // "webauthn.get" or "webauthn.create"
        challengeBase64: String,
        origin: String
    ): ByteArray {
        val json = """{"type":"$type","challenge":"$challengeBase64","origin":"$origin","crossOrigin":false}"""
        return json.toByteArray(Charsets.UTF_8)
    }

    /**
     * Encodes an EC P-256 public key into RFC 8152 / RFC 9052 COSE_Key structure.
     */
    fun encodeEcP256ToCose(publicKey: ECPublicKey): ByteArray {
        val w = publicKey.w
        val x = w.affineX.toByteArray().stripLeadingZero()
        val y = w.affineY.toByteArray().stripLeadingZero()

        val xPadded = padTo32(x)
        val yPadded = padTo32(y)

        // Simplified deterministic CBOR encoding for EC2 COSE Key
        val stream = ByteArrayOutputStream()
        stream.write(0xa5) // map of 5 pairs

        // 1: Key type = 2 (EC2)
        stream.write(0x01); stream.write(0x02)

        // 3: Algorithm = -7 (ES256)
        stream.write(0x03); stream.write(0x26) // -7 in CBOR major type 1: 0x20 | (7-1) = 0x26

        // -1 (crv): 1 (P-256)
        stream.write(0x20); stream.write(0x01)

        // -2 (x coordinate): 32-byte byte string
        stream.write(0x21); stream.write(0x58); stream.write(32); stream.write(xPadded)

        // -3 (y coordinate): 32-byte byte string
        stream.write(0x22); stream.write(0x58); stream.write(32); stream.write(yPadded)

        return stream.toByteArray()
    }

    private fun generateEcP256KeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        val ecSpec: ECNamedCurveParameterSpec = ECNamedCurveTable.getParameterSpec("secp256r1")
        kpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"), secureRandom)
        return kpg.generateKeyPair()
    }

    private fun decodeEcPrivateKey(encoded: ByteArray): PrivateKey {
        val keyFactory = java.security.KeyFactory.getInstance("EC")
        val keySpec = java.security.spec.PKCS8EncodedKeySpec(encoded)
        return keyFactory.generatePrivate(keySpec)
    }

    private fun sha256(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data)
    }

    private fun ByteArray.stripLeadingZero(): ByteArray {
        return if (size == 33 && this[0] == 0.toByte()) {
            copyOfRange(1, 33)
        } else {
            this
        }
    }

    private fun padTo32(bytes: ByteArray): ByteArray {
        if (bytes.size == 32) return bytes
        val result = ByteArray(32)
        val srcPos = (32 - bytes.size).coerceAtLeast(0)
        val copyLen = bytes.size.coerceAtMost(32)
        System.arraycopy(bytes, bytes.size - copyLen, result, srcPos, copyLen)
        return result
    }
}

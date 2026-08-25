package com.kryptx.app.core.crypto

import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERTaggedObject
import org.bouncycastle.asn1.DERUTCTime
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.TBSCertificate
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * High-performance on-demand Ephemeral TLS Engine.
 *
 * Generates volatile in-memory self-signed X.509 certificates and SSLContexts for the
 * Local Web Companion and P2P sync sockets using certified Bouncy Castle ASN.1 structures.
 */
object EphemeralTlsEngine {

    private const val KEY_ALGORITHM = "RSA"
    private const val KEY_SIZE = 2048
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    private const val TLS_PROTOCOL = "TLSv1.3"

    data class TlsCredentials(
        val sslContext: SSLContext,
        val certificate: X509Certificate,
        val fingerprintSha256: String,
        val keyPair: KeyPair
    )

    /**
     * Generates a fresh ephemeral RSA keypair and self-signed certificate, returning an active SSLContext.
     */
    fun createEphemeralTls(
        commonName: String = "Kryptx Secure Local Node",
        validityDays: Int = 1
    ): TlsCredentials {
        val keyPairGen = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        keyPairGen.initialize(KEY_SIZE, SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()

        // Generate valid X.509 Certificate
        val cert = generateBouncyCastleX509(keyPair, commonName, validityDays)
        val fingerprint = computeCertificateFingerprint(cert)

        // Store into in-memory keystore
        val password = "kryptx_ephemeral_mem_pass".toCharArray()
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry(
            "kryptx-ephemeral",
            keyPair.private,
            password,
            arrayOf(cert)
        )

        // Setup KeyManager & SSLContext
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, password)

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(keyStore)

        val sslContext = try {
            SSLContext.getInstance(TLS_PROTOCOL)
        } catch (_: Exception) {
            SSLContext.getInstance("TLS")
        }
        sslContext.init(kmf.keyManagers, null, SecureRandom())

        SecureMemory.wipe(password)

        return TlsCredentials(
            sslContext = sslContext,
            certificate = cert,
            fingerprintSha256 = fingerprint,
            keyPair = keyPair
        )
    }

    /**
     * Generates a fully standard RFC 5280 compliant X.509 certificate using pure Bouncy Castle ASN.1.
     */
    private fun generateBouncyCastleX509(
        keyPair: KeyPair,
        cn: String,
        validityDays: Int
    ): X509Certificate {
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 60_000L)
        val notAfter = Date(now + (validityDays * 86_400_000L))
        val serial = BigInteger.valueOf(now)

        val name = X500Name("CN=$cn, O=Kryptx Sovereign Vault, C=ZZ")
        val sigAlgId = AlgorithmIdentifier(PKCSObjectIdentifiers.sha256WithRSAEncryption, DERNull.INSTANCE)

        val tbsGen = V3TBSCertificateGenerator()
        tbsGen.setSerialNumber(ASN1Integer(serial))
        tbsGen.setIssuer(name)
        tbsGen.setSubject(name)
        tbsGen.setStartDate(Time(notBefore))
        tbsGen.setEndDate(Time(notAfter))
        tbsGen.setSignature(sigAlgId)
        tbsGen.setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(keyPair.public.encoded))

        val tbsCert = tbsGen.generateTBSCertificate()
        val tbsDer = tbsCert.encoded

        // Sign TBS Certificate
        val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
        signer.initSign(keyPair.private)
        signer.update(tbsDer)
        val signatureBytes = signer.sign()

        // Assemble outer Certificate sequence: [ TBSCertificate, AlgorithmIdentifier, BitString Signature ]
        val certSeq = ASN1EncodableVector()
        certSeq.add(tbsCert)
        certSeq.add(sigAlgId)
        certSeq.add(DERBitString(signatureBytes))

        val fullCertDer = DERSequence(certSeq).encoded

        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(ByteArrayInputStream(fullCertDer)) as X509Certificate
    }

    /**
     * Computes the formatted SHA-256 fingerprint of the X.509 certificate (e.g. AA:BB:CC:...).
     */
    fun computeCertificateFingerprint(cert: X509Certificate): String {
        val md = MessageDigest.getInstance("SHA-256")
        val der = cert.encoded
        val digest = md.digest(der)
        return digest.joinToString(":") { String.format("%02X", it) }
    }
}

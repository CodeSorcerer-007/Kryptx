package com.kryptx.app.core.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Android Keystore manager for protecting vault master keys with hardware-backed security (StrongBox / TEE).
 * Cryptographically binds the Vault Encryption Key (VEK) to hardware biometric authentication
 * using asymmetric RSA-2048 OAEP.
 *
 * - Public Key (PURPOSE_ENCRYPT): Wraps the 32-byte VEK in background with zero user prompts.
 * - Private Key (PURPOSE_DECRYPT): Hardware-bound to Class 3 Strong Biometrics with per-use authentication.
 */
class KeystoreManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val BIOMETRIC_KEY_ALIAS = "kryptx_biometric_master_key"
        private const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        private val OAEP_SPEC = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT
        )
    }

    // Widened to `internal` so top-level extension functions in this file can access it
    // without reflection. Still not visible to external modules.
    internal fun getKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    /**
     * Checks if biometric key exists in Android Keystore.
     */
    fun hasBiometricKey(): Boolean {
        return try {
            val ks = getKeyStore()
            ks.containsAlias(BIOMETRIC_KEY_ALIAS)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes existing biometric key from Keystore.
     */
    fun removeBiometricKey() {
        try {
            val ks = getKeyStore()
            if (ks.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                ks.deleteEntry(BIOMETRIC_KEY_ALIAS)
            }
        } catch (_: Exception) {
            // Ignore deletion errors
        }
    }

    /**
     * Generates or retrieves the hardware-backed RSA-2048 key pair from Android Keystore.
     * Decryption is cryptographically bound to Strong Biometrics (Class 3) with per-use authentication.
     * Enrolls StrongBox Keymaster when supported, falling back to standard TEE.
     */
    @Synchronized
    fun getOrCreateBiometricKeyPair(): KeyPair {
        val ks = getKeyStore()
        if (ks.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            val privateKey = ks.getKey(BIOMETRIC_KEY_ALIAS, null) as? PrivateKey
            val cert = ks.getCertificate(BIOMETRIC_KEY_ALIAS)
            if (privateKey != null && cert?.publicKey != null) {
                return KeyPair(cert.publicKey, privateKey)
            }
            // Clear corrupted or legacy symmetric key entry
            removeBiometricKey()
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )

        // Configure strict hardware biometric authentication constraints for decryption only
        fun configureBuilder(isStrongBox: Boolean): KeyGenParameterSpec.Builder {
            val builder = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEY_ALIAS,
                KeyProperties.PURPOSE_DECRYPT or KeyProperties.PURPOSE_ENCRYPT
            )
                .setDigests(
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA1,
                    KeyProperties.DIGEST_SHA512
                )
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setKeySize(2048)
                .setUserAuthenticationRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }

            builder.setInvalidatedByBiometricEnrollment(true)

            if (isStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }

            return builder
        }

        // Attempt StrongBox Keymaster first on Android 9+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                keyPairGenerator.initialize(configureBuilder(isStrongBox = true).build())
                return keyPairGenerator.generateKeyPair()
            } catch (_: Exception) {
                // Fallback to standard TEE hardware security
            }
        }

        keyPairGenerator.initialize(configureBuilder(isStrongBox = false).build())
        return keyPairGenerator.generateKeyPair()
    }

    @Deprecated("Use getOrCreateBiometricKeyPair() for asymmetric RSA hardware keys")
    fun getOrCreateBiometricKey(): java.security.Key {
        return getOrCreateBiometricKeyPair().public
    }

    /**
     * Checks whether the biometric hardware key was permanently invalidated by a newly enrolled biometric.
     */
    fun isBiometricKeyPermanentlyInvalidated(iv: ByteArray = ByteArray(0)): Boolean {
        if (!hasBiometricKey()) return false
        return try {
            val keyPair = getOrCreateBiometricKeyPair()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyPair.private, OAEP_SPEC)
            false
        } catch (e: Exception) {
            e is android.security.keystore.KeyPermanentlyInvalidatedException
        }
    }

    /**
     * Encrypts the raw Vault Encryption Key (VEK) directly using the RSA public key.
     * Can execute in the background with zero user prompts required.
     */
    fun wrapWithPublicKey(vek: ByteArray): ByteArray {
        return try {
            val keyPair = getOrCreateBiometricKeyPair()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keyPair.public, OAEP_SPEC)
            cipher.doFinal(vek)
        } catch (_: Exception) {
            removeBiometricKey()
            val newKeyPair = getOrCreateBiometricKeyPair()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, newKeyPair.public, OAEP_SPEC)
            cipher.doFinal(vek)
        }
    }

    /**
     * Creates an initialized decryption Cipher for BiometricPrompt.CryptoObject.
     * Cryptographically requires hardware biometric verification to authorize decryption.
     */
    fun getDecryptCipher(iv: ByteArray = ByteArray(0)): Cipher? {
        return try {
            val keyPair = getOrCreateBiometricKeyPair()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyPair.private, OAEP_SPEC)
            cipher
        } catch (e: Exception) {
            if (e is android.security.keystore.KeyPermanentlyInvalidatedException ||
                e is java.security.InvalidKeyException) {
                removeBiometricKey()
            }
            null
        }
    }

    /**
     * Creates an initialized encryption Cipher using the RSA public key.
     */
    fun getEncryptCipher(): Cipher? {
        return try {
            val keyPair = getOrCreateBiometricKeyPair()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keyPair.public, OAEP_SPEC)
            cipher
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encrypts the raw Vault Encryption Key (VEK) using an encryption Cipher.
     * @return Pair of (Ciphertext, IV (empty byte array for RSA-OAEP))
     */
    fun wrapWithCipher(cipher: Cipher, vek: ByteArray): Pair<ByteArray, ByteArray> {
        val ciphertext = cipher.doFinal(vek)
        return Pair(ciphertext, ByteArray(0))
    }

    /**
     * Decrypts the wrapped Vault Encryption Key using an authenticated Cipher from BiometricPrompt.CryptoObject.
     */
    fun unwrapWithCipher(cipher: Cipher, ciphertext: ByteArray): ByteArray {
        return cipher.doFinal(ciphertext)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ECDH X25519 / EC P-256 forward-migration support
//
// RSA-2048 OAEP provides ~112-bit classical security, which exceeds NIST minimum
// but is broken by Shor's algorithm on a large enough quantum computer (same as
// any factoring-based scheme).  This block provides the migration path:
//
//  1. generateEcdhKeyPair()   — generates an ECDH P-256 key pair in the Keystore,
//                               bound to Class 3 biometrics just like the RSA key.
//  2. wrapVekWithEcdh()       — performs ECDH + HKDF-SHA256 key agreement and
//                               wraps the VEK under the derived 256-bit session key
//                               using AES-256-GCM.
//  3. unwrapVekWithEcdh()     — performs ECDH + HKDF-SHA256 and decrypts the VEK.
//
// Migration strategy:
//  • On first unlock with the new binary, detect no ECDH key pair → create one.
//  • On successful biometric auth, re-wrap the VEK under ECDH alongside the RSA
//    copy.  Both exist in parallel until the RSA path is retired (v3.x).
//  • Once both paths are in place, the RSA entry can be removed via a settings
//    migration flag after the next successful unlock.
//
// Curve choice: ECDH P-256 (secp256r1) is supported by the Android Keystore on
// all devices running API 26+; X25519 is only available from API 31 in the TEE
// and API 33 in StrongBox.  P-256 offers the broadest hardware security coverage
// and is NIST-approved for key agreement (SP 800-56A).  Ciphers are still quantum-
// vulnerable (Shor), but combined with ML-KEM-768 wrapping at the backup/export
// layer, the full chain is hybrid-quantum-safe.
// ─────────────────────────────────────────────────────────────────────────────

private const val ECDH_KEY_ALIAS = "kryptx_ecdh_v2_key"
private const val ECDH_KEY_ALGORITHM = "EC"
private const val ECDH_KEY_AGREEMENT = "ECDH"
private const val ECDH_HKDF_INFO = "Kryptx-ECDH-VEK-Wrap-v2"

/**
 * Returns true when the ECDH P-256 migration key pair exists in the Keystore.
 */
fun KeystoreManager.hasEcdhKey(): Boolean = try {
    getKeyStore().containsAlias(ECDH_KEY_ALIAS)
} catch (_: Exception) { false }

/**
 * Removes the ECDH key pair from the Keystore (called by emergency wipe).
 */
fun KeystoreManager.removeEcdhKey() {
    try {
        val ks = getKeyStore()
        if (ks.containsAlias(ECDH_KEY_ALIAS)) ks.deleteEntry(ECDH_KEY_ALIAS)
    } catch (_: Exception) {}
}

/**
 * Generates (or retrieves) a hardware-backed ECDH P-256 key pair bound to Class 3 biometrics.
 * StrongBox is preferred; falls back to TEE.
 *
 * The private key is PURPOSE_AGREE_KEY only — it cannot be used for anything else,
 * limiting its blast radius to ECDH operations exclusively.
 */
@Synchronized
fun KeystoreManager.getOrCreateEcdhKeyPair(): java.security.KeyPair {
    val ks = getKeyStore()
    if (ks.containsAlias(ECDH_KEY_ALIAS)) {
        val priv = ks.getKey(ECDH_KEY_ALIAS, null) as? java.security.PrivateKey
        val cert = ks.getCertificate(ECDH_KEY_ALIAS)
        if (priv != null && cert?.publicKey != null) {
            return java.security.KeyPair(cert.publicKey, priv)
        }
        ks.deleteEntry(ECDH_KEY_ALIAS)
    }

    val kpg = java.security.KeyPairGenerator.getInstance(ECDH_KEY_ALGORITHM, "AndroidKeyStore")

    fun buildSpec(strongBox: Boolean): android.security.keystore.KeyGenParameterSpec {
        val b = android.security.keystore.KeyGenParameterSpec.Builder(
            ECDH_KEY_ALIAS,
            KeyProperties.PURPOSE_AGREE_KEY
        )
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .setUserAuthenticationRequired(true)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            b.setUserAuthenticationValidityDurationSeconds(-1)
        }
        b.setInvalidatedByBiometricEnrollment(true)

        if (strongBox && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            b.setIsStrongBoxBacked(true)
        }
        return b.build()
    }

    // StrongBox first on API 28+
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        try {
            kpg.initialize(buildSpec(strongBox = true))
            return kpg.generateKeyPair()
        } catch (_: Exception) { /* fall through to TEE */ }
    }
    kpg.initialize(buildSpec(strongBox = false))
    return kpg.generateKeyPair()
}

/**
 * Wraps the Vault Encryption Key using ECDH P-256 key agreement + HKDF-SHA256 + AES-256-GCM.
 *
 * An ephemeral P-256 key pair is generated; ECDH is performed between the ephemeral private key
 * and the Keystore public key.  The ECDH shared secret is fed into HKDF-SHA256 to derive a 32-byte
 * wrapping key; the VEK is then encrypted with AES-256-GCM.
 *
 * The caller must already have an authenticated Keystore [Cipher] or perform ECDH in the
 * BiometricPrompt CryptoObject flow to authorise use of the private key.
 *
 * @return Triple of (ephemeral public key bytes, AES-GCM IV, AES-GCM ciphertext+tag).
 */
fun KeystoreManager.wrapVekWithEcdh(
    vek: ByteArray,
    keystorePublicKey: java.security.PublicKey
): Triple<ByteArray, ByteArray, ByteArray> {
    // 1. Ephemeral key pair (software, not Keystore — we only need it for this wrap operation)
    val ephemeralKpg = java.security.KeyPairGenerator.getInstance(ECDH_KEY_ALGORITHM)
    ephemeralKpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"), java.security.SecureRandom())
    val ephemeralPair = ephemeralKpg.generateKeyPair()

    // 2. ECDH shared secret
    val ka = javax.crypto.KeyAgreement.getInstance(ECDH_KEY_AGREEMENT)
    ka.init(ephemeralPair.private)
    ka.doPhase(keystorePublicKey, true)
    val rawSharedSecret = ka.generateSecret()

    // 3. HKDF-SHA256 to derive 32-byte wrapping key
    val wrappingKey = try {
        val hkdf = org.bouncycastle.crypto.generators.HKDFBytesGenerator(
            org.bouncycastle.crypto.digests.SHA256Digest()
        )
        hkdf.init(org.bouncycastle.crypto.params.HKDFParameters(
            rawSharedSecret,
            null, // no salt — raw shared secret is high entropy
            ECDH_HKDF_INFO.toByteArray(Charsets.UTF_8)
        ))
        ByteArray(32).also { hkdf.generateBytes(it, 0, 32) }
    } finally {
        SecureMemory.wipe(rawSharedSecret)
    }

    // 4. AES-256-GCM encrypt the VEK under the HKDF-derived wrapping key
    val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
        javax.crypto.Cipher.ENCRYPT_MODE,
        javax.crypto.spec.SecretKeySpec(wrappingKey, "AES"),
        javax.crypto.spec.GCMParameterSpec(128, iv)
    )
    val ciphertext = cipher.doFinal(vek)
    SecureMemory.wipe(wrappingKey)

    return Triple(ephemeralPair.public.encoded, iv, ciphertext)
}

/**
 * Unwraps a VEK previously wrapped with [wrapVekWithEcdh].
 *
 * Requires the Keystore private key to be authorised (i.e. called within a
 * BiometricPrompt CryptoObject ECDH flow or immediately after biometric success).
 *
 * @param ephemeralPublicKeyBytes   The ephemeral public key encoded bytes from wrap.
 * @param iv                        The AES-GCM IV from wrap.
 * @param wrappedVek                The AES-GCM ciphertext+tag from wrap.
 * @param keystorePrivateKey        The authenticated private key from the BiometricPrompt result.
 * @return Plaintext VEK (caller must wipe after use).
 */
fun KeystoreManager.unwrapVekWithEcdh(
    ephemeralPublicKeyBytes: ByteArray,
    iv: ByteArray,
    wrappedVek: ByteArray,
    keystorePrivateKey: java.security.PrivateKey
): ByteArray {
    // 1. Decode ephemeral public key
    val ephemeralPub = java.security.KeyFactory.getInstance(ECDH_KEY_ALGORITHM)
        .generatePublic(java.security.spec.X509EncodedKeySpec(ephemeralPublicKeyBytes))

    // 2. ECDH shared secret using the Keystore private key
    val ka = javax.crypto.KeyAgreement.getInstance(ECDH_KEY_AGREEMENT)
    ka.init(keystorePrivateKey)
    ka.doPhase(ephemeralPub, true)
    val rawSharedSecret = ka.generateSecret()

    // 3. HKDF-SHA256
    val wrappingKey = try {
        val hkdf = org.bouncycastle.crypto.generators.HKDFBytesGenerator(
            org.bouncycastle.crypto.digests.SHA256Digest()
        )
        hkdf.init(org.bouncycastle.crypto.params.HKDFParameters(
            rawSharedSecret,
            null,
            ECDH_HKDF_INFO.toByteArray(Charsets.UTF_8)
        ))
        ByteArray(32).also { hkdf.generateBytes(it, 0, 32) }
    } finally {
        SecureMemory.wipe(rawSharedSecret)
    }

    // 4. AES-256-GCM decrypt
    return try {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(wrappingKey, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, iv)
        )
        cipher.doFinal(wrappedVek)
    } finally {
        SecureMemory.wipe(wrappingKey)
    }
}

/**
 * Returns an initialized ECDH [KeyAgreement] ready for the BiometricPrompt CryptoObject flow.
 * Returns null if the ECDH key pair doesn't exist or cannot be initialized.
 */
fun KeystoreManager.getEcdhKeyAgreementForBiometric(): javax.crypto.KeyAgreement? {
    if (!hasEcdhKey()) return null
    return try {
        val pair = getOrCreateEcdhKeyPair()
        val ka = javax.crypto.KeyAgreement.getInstance(ECDH_KEY_AGREEMENT, "AndroidKeyStore")
        ka.init(pair.private)
        ka
    } catch (_: Exception) { null }
}

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
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        )
    }

    private fun getKeyStore(): KeyStore {
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
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
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
        val keyPair = getOrCreateBiometricKeyPair()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.public, OAEP_SPEC)
        return cipher.doFinal(vek)
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
        } catch (_: Exception) {
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

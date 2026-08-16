package com.kryptx.app.core.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore manager for protecting vault master keys with hardware-backed security (StrongBox / TEE).
 * Cryptographically binds the Vault Encryption Key (VEK) to hardware biometric authentication.
 */
class KeystoreManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val BIOMETRIC_KEY_ALIAS = "kryptx_biometric_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
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
            getKeyStore().containsAlias(BIOMETRIC_KEY_ALIAS)
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
     * Generates or retrieves the hardware-backed AES-256 key from Android Keystore.
     * Cryptographically bound to Strong Biometrics (Class 3) with per-use authentication.
     * Enrolls StrongBox Keymaster when supported, falling back to standard TEE.
     */
    @Synchronized
    fun getOrCreateBiometricKey(): SecretKey {
        val ks = getKeyStore()
        if (ks.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            val key = ks.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
            if (key != null) return key
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        // Configure strict hardware biometric authentication constraints
        fun configureBuilder(isStrongBox: Boolean): KeyGenParameterSpec.Builder {
            val builder = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(-1)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(true)
            }

            if (isStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }

            return builder
        }

        // Attempt StrongBox Keymaster first on Android 9+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                keyGenerator.init(configureBuilder(isStrongBox = true).build())
                return keyGenerator.generateKey()
            } catch (_: Exception) {
                // Fallback to TEE hardware security
            }
        }

        keyGenerator.init(configureBuilder(isStrongBox = false).build())
        return keyGenerator.generateKey()
    }

    /**
     * Creates an initialized decryption Cipher for BiometricPrompt.CryptoObject.
     */
    fun getDecryptCipher(iv: ByteArray): Cipher? {
        return try {
            val secretKey = getOrCreateBiometricKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            cipher
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Creates an initialized encryption Cipher for BiometricPrompt.CryptoObject.
     */
    fun getEncryptCipher(): Cipher? {
        return try {
            val secretKey = getOrCreateBiometricKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encrypts the raw Vault Encryption Key (VEK) using an authenticated Cipher from BiometricPrompt.CryptoObject.
     * @return Pair of (Ciphertext, IV)
     */
    fun wrapWithCipher(cipher: Cipher, vek: ByteArray): Pair<ByteArray, ByteArray> {
        val ciphertext = cipher.doFinal(vek)
        return Pair(ciphertext, cipher.iv)
    }

    /**
     * Decrypts the wrapped Vault Encryption Key using an authenticated Cipher from BiometricPrompt.CryptoObject.
     */
    fun unwrapWithCipher(cipher: Cipher, ciphertext: ByteArray): ByteArray {
        return cipher.doFinal(ciphertext)
    }
}

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
 * Manages encryption/decryption of the Vault Encryption Key (VEK) via hardware-bound keys.
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            // Ignore deletion errors
        }
    }

    /**
     * Generates or retrieves the hardware-backed AES-256 key from Android Keystore.
     * Attempts StrongBox Keymaster first, then falls back to standard TEE.
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

        // Attempt StrongBox Keymaster on Android 9+ devices first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val strongBoxBuilder = KeyGenParameterSpec.Builder(
                    BIOMETRIC_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .setIsStrongBoxBacked(true)

                keyGenerator.init(strongBoxBuilder.build())
                return keyGenerator.generateKey()
            } catch (e: Exception) {
                // Fallback to TEE hardware security
            }
        }

        val teeBuilder = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        keyGenerator.init(teeBuilder.build())
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
        } catch (e: Exception) {
            android.util.Log.e("KeystoreManager", "Failed to initialize decrypt cipher", e)
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
        } catch (e: Exception) {
            android.util.Log.e("KeystoreManager", "Failed to initialize encrypt cipher", e)
            null
        }
    }

    /**
     * Encrypts the raw Vault Encryption Key (VEK) using the Android Keystore key.
     * @return Pair of (Ciphertext, IV)
     */
    fun wrapVek(vek: ByteArray): Pair<ByteArray, ByteArray> {
        val secretKey = getOrCreateBiometricKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(vek)
        return Pair(ciphertext, iv)
    }

    /**
     * Decrypts the wrapped Vault Encryption Key (VEK) using the Android Keystore key and IV.
     */
    fun unwrapVek(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val secretKey = getOrCreateBiometricKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Decrypts the wrapped Vault Encryption Key using an authenticated Cipher from BiometricPrompt.CryptoObject.
     */
    fun unwrapWithCipher(cipher: Cipher, ciphertext: ByteArray): ByteArray {
        return cipher.doFinal(ciphertext)
    }
}

package com.kryptx.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kryptx.app.core.crypto.KeystoreManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class KeystoreManagerInstrumentedTest {

    private lateinit var keystoreManager: KeystoreManager

    @Before
    fun setup() {
        keystoreManager = KeystoreManager()
    }

    @Test
    fun testKeystoreKeyGenerationAndWrapUnwrap() {
        val masterKey = ByteArray(32)
        SecureRandom().nextBytes(masterKey)

        // Generate or ensure biometric wrapping key in AndroidKeyStore TEE
        val biometricKey = keystoreManager.getOrCreateBiometricKey()
        assertNotNull("Biometric master key should be created in AndroidKeyStore", biometricKey)

        // Validate cipher initialization for encryption
        val encryptCipher = keystoreManager.getEncryptCipher()
        assertNotNull("Encryption cipher should be initialized", encryptCipher)

        if (encryptCipher != null) {
            val (ciphertext, iv) = keystoreManager.wrapWithCipher(encryptCipher, masterKey)
            assertNotNull("Wrapped vault key should not be null", ciphertext)
            assertTrue("Encrypted payload should contain ciphertext", ciphertext.isNotEmpty())

            val decryptCipher = keystoreManager.getDecryptCipher(iv)
            assertNotNull("Decryption cipher should be initialized with IV", decryptCipher)

            if (decryptCipher != null) {
                val unwrappedKey = keystoreManager.unwrapWithCipher(decryptCipher, ciphertext)
                assertArrayEquals("Unwrapped key should match original master key", masterKey, unwrappedKey)
            }
        }
    }
}

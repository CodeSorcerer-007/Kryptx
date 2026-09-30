package com.kryptx.app

import androidx.biometric.BiometricPrompt
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kryptx.app.core.crypto.KeystoreManager
import com.kryptx.app.core.security.BiometricAuthManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiometricFlowInstrumentedTest {

    private lateinit var authManager: BiometricAuthManager
    private lateinit var keystoreManager: KeystoreManager

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        authManager = BiometricAuthManager(context)
        keystoreManager = KeystoreManager()
    }

    @Test
    fun testBiometricStatusCheck() {
        val status = authManager.checkBiometricAvailability()
        assertNotNull("Biometric status must return a valid enum", status)
    }

    @Test
    fun testCryptoObjectInitialization() {
        val cipher = keystoreManager.getEncryptCipher()
        assertNotNull("Keystore cipher should initialize", cipher)

        if (cipher != null) {
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)
            assertNotNull("BiometricPrompt.CryptoObject should wrap cipher successfully", cryptoObject.cipher)
        }
    }
}

package com.kryptx.app.bugfix

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import androidx.biometric.BiometricPrompt
import com.kryptx.app.MainActivity
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.model.KryptxErrorType
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.security.ContextualLockManager
import com.kryptx.app.core.security.VaultSessionManager
import com.kryptx.app.fake.FakePreferencesRepository
import com.kryptx.app.feature.auth.UnlockViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Task 1: Bug Condition Exploration Tests (Before Fixes)
 *
 * Each test scopes a specific bug condition among the seven bugs (eight conditions):
 * - Bug 3: Shake false-positive on walking/desk vibration
 * - Bug 6: Shake sensor multi-fire without stopListening
 * - Bug 5: Unsafe cipher-less biometric unlock dead code
 * - Bug 2: Setup biometric switch toggle ignored (hardcoded false)
 * - Bug 1a: setConfirmationRequired(true) combined with CryptoObject
 * - Bug 4: Photo picker requestCode crash using GetContent
 * - Bug 1b: Silent enrollment failure without error feedback
 * - Bug 7: Wrong cipher type (decrypt instead of encrypt) in enrollment
 *
 * These tests MUST FAIL on unfixed code, proving the defect exists and surfacing counterexamples.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiBugExplorationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSensorEvent(values: FloatArray, type: Int = Sensor.TYPE_ACCELEROMETER): SensorEvent {
        val constructor = SensorEvent::class.java.declaredConstructors.first()
        constructor.isAccessible = true
        val params = constructor.parameterTypes
        val event = if (params.isEmpty()) {
            constructor.newInstance() as SensorEvent
        } else {
            constructor.newInstance(values.size) as SensorEvent
        }
        val sensor = mock<Sensor>()
        whenever(sensor.type).thenReturn(type)

        val sensorField = SensorEvent::class.java.getField("sensor")
        sensorField.set(event, sensor)

        val valuesField = SensorEvent::class.java.getField("values")
        try {
            valuesField.set(event, values)
        } catch (_: Exception) {
            val modifiersField = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
            modifiersField.isAccessible = true
            modifiersField.setInt(valuesField, valuesField.modifiers and java.lang.reflect.Modifier.FINAL.inv())
            valuesField.set(event, values)
        }
        return event
    }

    private fun resolveSourceFile(path: String): java.io.File {
        val candidates = listOf(
            java.io.File(path),
            java.io.File(path.removePrefix("app/")),
            java.io.File("..", path),
            java.io.File("../app", path.removePrefix("app/"))
        )
        return candidates.firstOrNull { it.exists() } ?: java.io.File(path)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 3: Shake false-positive on gentle walking / table vibration
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testBug3_shakeFalsePositive_walkingVibrationMustNotTriggerLock() {
        val context = mock<Context>()
        var shakeCount = 0
        val manager = ContextualLockManager(context, onShakeTriggered = { shakeCount++ })

        // Simulate walking or desk vibration: sequence of gentle oscillations where |delta| < 8.0 m/s^2
        // Gravity = 9.80665 m/s^2; walking oscillates around +/- 5.2 m/s^2
        val walkingMagnitudes = floatArrayOf(
            9.8f,   // initial baseline
            15.0f,  // delta = 5.2
            9.8f,   // delta = 5.2
            15.0f,  // delta = 5.2
            9.8f    // delta = 5.2
        )

        for (mag in walkingMagnitudes) {
            val event = createSensorEvent(floatArrayOf(0f, 0f, mag))
            manager.onSensorChanged(event)
        }

        // EXPECTED TO FAIL ON UNFIXED CODE:
        // On unfixed code (threshold = 14.5f, alpha = 0.85f), walking vibrations accumulate:
        // Event 1: 0.85*0 + 5.2 = 5.2
        // Event 2: 0.85*5.2 + 5.2 = 9.62
        // Event 3: 0.85*9.62 + 5.2 = 13.38
        // Event 4: 0.85*13.38 + 5.2 = 16.57 > 14.5f -> onShakeTriggered() is triggered!
        assertEquals(
            "onShakeTriggered must NOT fire for ordinary walking vibrations where |delta| < 8 m/s^2",
            0,
            shakeCount
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 6: Multi-fire shake: stopListening() must be called after shake lock
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testBug6_multiFireShake_stopListeningMustBeCalledAfterShakeLock() {
        // Inspect MainActivity.onCreate onShakeTriggered lambda:
        // On unfixed code, onShakeTriggered locks the vault, clears clipboard, triggers haptic,
        // but NEVER calls contextualLockManager?.stopListening().
        // Consequently, the accelerometer keeps firing within the same gesture.
        val mainActivityClassBytes = MainActivity::class.java.classLoader
            ?.getResourceAsStream("com/kryptx/app/MainActivity.class")
            ?.readBytes()
        assertNotNull("MainActivity.class must be loadable", mainActivityClassBytes)

        val mainActivityClassText = String(mainActivityClassBytes!!, Charsets.ISO_8859_1)

        // Let's check if onShakeTriggered callback lambda specifically calls stopListening:
        // In unfixed MainActivity.kt lines 155-162:
        // onShakeTriggered = {
        //     if (app.sessionManager.isUnlocked.value) {
        //         app.sessionManager.lock()
        //         app.clipboardManager.copySensitiveText("", "", 0)
        //         app.clipboardManager.clearNow()
        //         KryptxHaptics.warning(window.decorView)
        //     }
        // }
        // NOTICE: stopListening is NOT called anywhere inside this lambda in unfixed code!
        val sourceFile = resolveSourceFile("app/src/main/java/com/kryptx/app/MainActivity.kt")
        assertTrue("MainActivity.kt source file must exist", sourceFile.exists())
        val sourceText = sourceFile.readText()

        val shakeBlock = sourceText.substringAfter("onShakeTriggered = {").substringBefore("onFaceDownTriggered")
        // EXPECTED TO FAIL ON UNFIXED CODE:
        // Unfixed code does not contain contextualLockManager?.stopListening() in onShakeTriggered
        assertTrue(
            "onShakeTriggered callback must call contextualLockManager?.stopListening() immediately after lock() to prevent rapid duplicate events",
            shakeBlock.contains("contextualLockManager?.stopListening()") || shakeBlock.contains("contextualLockManager.stopListening()")
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 5: Unsafe cipher-less biometric unlock path in UnlockViewModel
    // ─────────────────────────────────────────────────────────────────────────
    @Suppress("DEPRECATION")
    @Test
    fun testBug5_cipherlessBiometricUnlock_isDeadCodeAndMustBeInert() = runTest(testDispatcher) {
        val mockRepo = mock<VaultRepository>()
        val sessionManager = VaultSessionManager(testScope)
        val prefs = FakePreferencesRepository()
        val viewModel = UnlockViewModel(mockRepo, sessionManager, prefs, ioDispatcher = testDispatcher)

        var onSuccessCalled = false
        viewModel.unlockWithBiometrics { onSuccessCalled = true }
        testScheduler.runCurrent()

        // EXPECTED TO FAIL ON UNFIXED CODE:
        // 1. Unfixed code dispatches vaultRepository.unlockWithBiometrics()
        // 2. Unfixed code does NOT set the deprecation guard error message
        verify(mockRepo, never()).unlockWithBiometrics()
        assertFalse("onSuccess must not be invoked on cipher-less unlock", onSuccessCalled)
        assertEquals(
            "Biometric unlock requires a cipher — use unlockWithBiometricCipher()",
            viewModel.uiState.value.errorMessage
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 2: Biometric toggle hardcoded to false at setup
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testBug2_biometricToggleIgnoredAtSetup() {
        // In SetupMasterPasswordScreen.kt:
        // Unfixed line ~256:
        // viewModel.setupNewVault(
        //     passwordChars = pCopy,
        //     confirmChars = cCopy,
        //     enableBiometrics = false,  // <-- BUG: literal false
        //     onSuccess = { ... }
        // )
        val sourceFile = resolveSourceFile("app/src/main/java/com/kryptx/app/feature/auth/SetupMasterPasswordScreen.kt")
        assertTrue("SetupMasterPasswordScreen.kt must exist", sourceFile.exists())
        val source = sourceFile.readText()

        val setupNewVaultCall = source.substringAfter("viewModel.setupNewVault(").substringBefore("onSuccess =")

        // EXPECTED TO FAIL ON UNFIXED CODE:
        // Unfixed code passes "enableBiometrics = false," instead of "enableBiometrics = enableBiometrics,"
        assertFalse(
            "setupNewVault must not pass hardcoded false for enableBiometrics",
            setupNewVaultCall.contains("enableBiometrics = false")
        )
        assertTrue(
            "setupNewVault must pass the local enableBiometrics state variable",
            setupNewVaultCall.contains("enableBiometrics = enableBiometrics")
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 1a: setConfirmationRequired(true) combined with CryptoObject
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testBug1a_promptBiometric_confirmationRequiredWithCryptoObjectMustBeFalse() {
        // In BiometricAuthManager.kt promptBiometric():
        // Unfixed code: .setConfirmationRequired(true) unconditionally.
        // Requirement 2.2: When cryptoObject != null, PromptInfo.isConfirmationRequired must be false.
        val sourceFile = resolveSourceFile("app/src/main/java/com/kryptx/app/core/security/BiometricAuthManager.kt")
        assertTrue("BiometricAuthManager.kt must exist", sourceFile.exists())
        val source = sourceFile.readText()

        // EXPECTED TO FAIL ON UNFIXED CODE:
        // Unfixed code contains .setConfirmationRequired(true) instead of .setConfirmationRequired(cryptoObject == null)
        assertTrue(
            "BiometricAuthManager must set setConfirmationRequired(cryptoObject == null) so that CryptoObject prompts use false",
            source.contains(".setConfirmationRequired(cryptoObject == null)")
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 4: Photo picker requestCode crash
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testBug4_photoPicker_mustUsePickVisualMediaInsteadOfGetContent() {
        // In AddEditItemScreen.kt:
        // Unfixed code:
        // val photoPickerLauncher = rememberLauncherForActivityResult(
        //     contract = ActivityResultContracts.GetContent()
        // )
        // and photoPickerLauncher.launch("image/*")
        // Requirement 2.7: Must use ActivityResultContracts.PickVisualMedia() and PickVisualMediaRequest
        val sourceFile = resolveSourceFile("app/src/main/java/com/kryptx/app/feature/vault/AddEditItemScreen.kt")
        assertTrue("AddEditItemScreen.kt must exist", sourceFile.exists())
        val source = sourceFile.readText()

        val photoLauncherDecl = source.substringAfter("val photoPickerLauncher").substringBefore("val filePickerLauncher")

        // EXPECTED TO FAIL ON UNFIXED CODE:
        // Unfixed code uses ActivityResultContracts.GetContent()
        assertTrue(
            "photoPickerLauncher must use ActivityResultContracts.PickVisualMedia() to avoid legacy requestCode overflow",
            photoLauncherDecl.contains("ActivityResultContracts.PickVisualMedia()")
        )
        assertFalse(
            "photoPickerLauncher must NOT use ActivityResultContracts.GetContent()",
            photoLauncherDecl.contains("ActivityResultContracts.GetContent()")
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 1b: Silent enrollment failure without error message
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testBug1b_triggerBiometricEnrollment_setupFailureMustShowErrorMessage() {
        // In MainActivity.kt triggerBiometricEnrollment():
        // Unfixed lines ~401-405:
        // if (setupResult.isError) {
        //     isPromptingBiometrics.set(false)
        //     onResult(false)
        //     return@launch
        // }
        // Requirement 2.3: Must call unlockViewModel.setErrorMessage("Biometric setup failed. Please try again.")
        val sourceFile = resolveSourceFile("app/src/main/java/com/kryptx/app/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", sourceFile.exists())
        val source = sourceFile.readText()

        val enrollmentFn = source.substringAfter("fun triggerBiometricEnrollment").substringBefore("override fun dispatchTouchEvent")
        val errorBlock = enrollmentFn.substringAfter("if (setupResult.isError) {").let {
            if (it.contains("val encryptCipher")) it.substringBefore("val encryptCipher")
            else it.substringBefore("val decryptCipher")
        }

        // EXPECTED TO FAIL ON UNFIXED CODE:
        // Unfixed code does not call unlockViewModel.setErrorMessage in the setupResult.isError branch
        assertTrue(
            "triggerBiometricEnrollment must display error message when setupResult.isError",
            errorBlock.contains("unlockViewModel.setErrorMessage(\"Biometric setup failed. Please try again.\")")
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bug 7: Wrong cipher type in triggerBiometricEnrollment
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testBug7_triggerBiometricEnrollment_mustUseEncryptCipherAndPersistVEK() {
        // In MainActivity.kt triggerBiometricEnrollment():
        // Unfixed:
        // val decryptCipher = try {
        //     app.vaultRepository.getBiometricDecryptCipher()
        // }
        // and onSuccess does NOT call setupBiometricsWithCipher(authenticatedCipher)
        // Requirement 2.4: Must call getBiometricEncryptCipher() and setupBiometricsWithCipher(authenticatedCipher)
        val sourceFile = resolveSourceFile("app/src/main/java/com/kryptx/app/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", sourceFile.exists())
        val source = sourceFile.readText()

        val enrollmentFn = source.substringAfter("fun triggerBiometricEnrollment").substringBefore("override fun dispatchTouchEvent")

        // EXPECTED TO FAIL ON UNFIXED CODE:
        // 1. Unfixed code calls getBiometricDecryptCipher()
        // 2. Unfixed code does NOT call setupBiometricsWithCipher(authenticatedCipher)
        assertFalse(
            "triggerBiometricEnrollment must NOT call getBiometricDecryptCipher()",
            enrollmentFn.contains("app.vaultRepository.getBiometricDecryptCipher()")
        )
        assertTrue(
            "triggerBiometricEnrollment must call getBiometricEncryptCipher()",
            enrollmentFn.contains("app.vaultRepository.getBiometricEncryptCipher()")
        )
        assertTrue(
            "triggerBiometricEnrollment must call setupBiometricsWithCipher(authenticatedCipher) on success",
            enrollmentFn.contains("setupBiometricsWithCipher(authenticatedCipher)")
        )
    }
}

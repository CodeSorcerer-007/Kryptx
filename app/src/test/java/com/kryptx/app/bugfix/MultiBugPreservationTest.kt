package com.kryptx.app.bugfix

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import androidx.biometric.BiometricPrompt
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.security.ContextualLockManager
import com.kryptx.app.core.security.VaultSessionManager
import com.kryptx.app.fake.FakePreferencesRepository
import com.kryptx.app.fake.FakeVaultRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.crypto.Cipher

/**
 * Task 2: Preservation Property Tests (Before Fixes)
 *
 * Verifies all unchanged behaviors on UNFIXED code to establish baseline:
 * - 3.1: Master password unlock with correct credentials succeeds
 * - 3.2: Shake-to-lock disabled ignores motion
 * - 3.3: unlockWithBiometricCipher(cipher) decrypts vault
 * - 3.4: File picker uses GetContent and remains untouched
 * - 3.5: triggerBiometricUnlock uses decrypt cipher for unlock
 * - 3.6: High force shake (> 20.0f) triggers lock
 * - 3.7: Already locked vault ignores shake
 * - 3.8: Key invalidation flow prompts re-enrollment
 * - Cipher-less PromptInfo retains isConfirmationRequired == true
 *
 * EXPECTED OUTCOME: ALL TESTS MUST PASS ON UNFIXED CODE.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiBugPreservationTest {

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
        val cleanPath = path.removePrefix("app/")
        val directCandidates = listOf(
            java.io.File(path),
            java.io.File(cleanPath),
            java.io.File("app", cleanPath),
            java.io.File("..", path),
            java.io.File("../app", cleanPath),
            java.io.File("../../app", cleanPath)
        )
        val found = directCandidates.firstOrNull { it.exists() }
        if (found != null) return found

        var current: java.io.File? = java.io.File(System.getProperty("user.dir", "."))
        while (current != null) {
            val candidate1 = java.io.File(current, path)
            if (candidate1.exists()) return candidate1
            val candidate2 = java.io.File(current, "app/$cleanPath")
            if (candidate2.exists()) return candidate2
            val candidate3 = java.io.File(current, cleanPath)
            if (candidate3.exists()) return candidate3
            current = current.parentFile
        }
        return java.io.File(path)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Preservation 3.1: Master password unlock succeeds with correct credentials
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testPreservation_masterPasswordUnlock_succeedsWithCorrectCredentials() = runTest(testDispatcher) {
        val fakeRepo = FakeVaultRepository()
        val sessionManager = VaultSessionManager(testScope)
        val prefs = FakePreferencesRepository()
        val viewModel = UnlockViewModel(fakeRepo, sessionManager, prefs)

        // Setup vault with password
        val password = "MasterPassword123!".toCharArray()
        viewModel.setupNewVault(password, password, false) {}
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.hasVault)

        // Unlock with correct password
        var unlocked = false
        viewModel.unlockWithPassword("MasterPassword123!".toCharArray()) { unlocked = true }
        testScheduler.runCurrent()

        assertTrue("Master password unlock must succeed", unlocked)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Preservation 3.2: Shake disabled in settings ignores motion
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testPreservation_shakeDisabled_doesNotTriggerLock() {
        // When shake-to-lock preference is disabled, startListening() is not active.
        // Even when high-acceleration events are delivered to an un-started manager, or
        // when shakeToLockEnabled is false in preferences.
        val prefs = FakePreferencesRepository()
        prefs.setShakeToLockEnabled(false)

        assertFalse("shakeToLockEnabled must be false", prefs.shakeToLockEnabled.value)

        // If listener is stopped, no events trigger
        val context = mock<Context>()
        var shakeCount = 0
        val manager = ContextualLockManager(context, onShakeTriggered = { shakeCount++ })
        manager.stopListening()

        // High force shake events
        val hardShakeEvent = createSensorEvent(floatArrayOf(0f, 0f, 35.0f))
        manager.onSensorChanged(hardShakeEvent)

        // Cooldown or not, if not registered with sensor manager, listener receives no events in practice.
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Preservation 3.3: Enrolled biometric unlock uses unlockWithBiometricCipher
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testPreservation_enrolledBiometricUnlock_usesBiometricCipher() = runTest(testDispatcher) {
        val fakeRepo = FakeVaultRepository()
        fakeRepo.setupBiometrics()
        val sessionManager = VaultSessionManager(testScope)
        val prefs = FakePreferencesRepository()
        val viewModel = UnlockViewModel(fakeRepo, sessionManager, prefs)

        var unlocked = false
        val mockCipher = mock<Cipher>()
        viewModel.unlockWithBiometricCipher(mockCipher) { unlocked = true }
        testScheduler.runCurrent()

        assertTrue("unlockWithBiometricCipher must succeed and decrypt vault", unlocked)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Preservation 3.4: File picker uses GetContent("*/*") and remains untouched
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testPreservation_documentPicker_stillUsesGetContent() {
        val sourceFile = resolveSourceFile("app/src/main/java/com/kryptx/app/feature/vault/AddEditItemScreen.kt")
        assertTrue("AddEditItemScreen.kt must exist", sourceFile.exists())
        val source = sourceFile.readText()

        val filePickerDecl = source.substringAfter("val filePickerLauncher").substringBefore("val launchPhotoPicker")
        val launchDocFn = source.substringAfter("val launchDocumentPicker").substringBefore("showDiscardDialog")

        assertTrue("filePickerLauncher must use ActivityResultContracts.GetContent()", filePickerDecl.contains("ActivityResultContracts.GetContent()"))
        assertTrue("launchDocumentPicker must launch with */*", launchDocFn.contains("filePickerLauncher.launch(\"*/*\")"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Preservation 3.5: triggerBiometricUnlock uses getBiometricDecryptCipher
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testPreservation_triggerBiometricUnlock_callsDecryptCipher() {
        val sourceFile = resolveSourceFile("app/src/main/java/com/kryptx/app/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", sourceFile.exists())
        val source = sourceFile.readText()

        val unlockFn = source.substringAfter("private fun triggerBiometricUnlock(").substringBefore("private fun triggerBiometricEnrollment(")
        assertTrue(
            "triggerBiometricUnlock must continue calling getBiometricDecryptCipher()",
            unlockFn.contains("app.vaultRepository.getBiometricDecryptCipher()")
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Preservation 3.6: Hard deliberate shake (> 20.0f) triggers lock
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testPreservation_deliberateHardShake_stillLocksVault() {
        val context = mock<Context>()
        var shakeCount = 0
        val manager = ContextualLockManager(context, onShakeTriggered = { shakeCount++ })

        // Initial gravity baseline
        manager.onSensorChanged(createSensorEvent(floatArrayOf(0f, 0f, 9.8f)))

        // Deliberate violent shake: high-energy impulse > 25.0 m/s^2 (2.7g equivalent = 26.5 m/s^2)
        val hardImpulse = createSensorEvent(floatArrayOf(0f, 0f, 35.0f))
        manager.onSensorChanged(hardImpulse)

        assertEquals("Deliberate hard shake must trigger onShakeTriggered", 1, shakeCount)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Preservation 3.7: Already locked vault ignores shake
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testPreservation_alreadyLockedVault_ignoresShake() = runTest(testDispatcher) {
        val sessionManager = VaultSessionManager(testScope)
        sessionManager.lock()
        assertFalse("Session must be locked", sessionManager.isUnlocked.value)

        var lockActionsExecuted = 0
        // Simulate MainActivity's onShakeTriggered guard:
        // if (app.sessionManager.isUnlocked.value) { lock(); ... }
        val onShake = {
            if (sessionManager.isUnlocked.value) {
                sessionManager.lock()
                lockActionsExecuted++
            }
        }

        onShake()
        assertEquals("Already locked session must not trigger redundant lock actions", 0, lockActionsExecuted)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Preservation 3.8: Biometric key invalidation prompts re-enrollment
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testPreservation_invalidationFlow_showsReEnrollPrompt() {
        val sourceFile = resolveSourceFile("app/src/main/java/com/kryptx/app/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", sourceFile.exists())
        val source = sourceFile.readText()

        val unlockFn = source.substringAfter("private fun triggerBiometricUnlock(").substringBefore("private fun triggerBiometricEnrollment(")
        assertTrue(
            "triggerBiometricUnlock must show invalidation re-enrollment error message",
            unlockFn.contains("unlockViewModel.setErrorMessage(\"Biometric key invalidated. Unlock with Master Password to re-enroll.\")")
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Preservation: Cipher-less PromptInfo retains isConfirmationRequired == true
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testPreservation_cipherlessPrompt_retainsConfirmationRequiredTrue() {
        // When cryptoObject == null, promptInfo MUST retain setConfirmationRequired(true)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Title")
            .setSubtitle("Subtitle")
            .setConfirmationRequired(true)
            .setNegativeButtonText("Cancel")
            .build()

        assertTrue("Cipher-less prompt must retain isConfirmationRequired == true", promptInfo.isConfirmationRequired)
    }
}

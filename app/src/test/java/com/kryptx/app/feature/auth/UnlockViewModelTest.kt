package com.kryptx.app.feature.auth

import com.kryptx.app.core.security.VaultSessionManager
import com.kryptx.app.fake.FakePreferencesRepository
import com.kryptx.app.fake.FakeVaultRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var fakePreferencesRepository: FakePreferencesRepository
    private lateinit var viewModel: UnlockViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeVaultRepository = FakeVaultRepository()
        sessionManager = VaultSessionManager(testScope)
        fakePreferencesRepository = FakePreferencesRepository()
        viewModel = UnlockViewModel(fakeVaultRepository, sessionManager, fakePreferencesRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStatusNoVault() {
        viewModel.checkVaultStatus()
        assertFalse(viewModel.uiState.value.hasVault)
        assertFalse(viewModel.uiState.value.isBiometricsAvailable)
        assertEquals(0, viewModel.uiState.value.passwordLength)
    }

    @Test
    fun testOnPasswordLengthChangedUpdatesState() {
        viewModel.onPasswordLengthChanged(10)
        assertEquals(10, viewModel.uiState.value.passwordLength)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testSetupNewVaultValidationAndSuccess() = runTest(testDispatcher) {
        var setupSuccess = false

        // Password too short
        viewModel.setupNewVault("short".toCharArray(), "short".toCharArray(), false) { setupSuccess = true }
        testScheduler.runCurrent()
        assertFalse(setupSuccess)
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Passwords do not match
        viewModel.setupNewVault("ValidPassword123".toCharArray(), "MismatchPassword456".toCharArray(), false) { setupSuccess = true }
        testScheduler.runCurrent()
        assertFalse(setupSuccess)
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Valid setup with CharArray
        viewModel.setupNewVault("ValidPassword123".toCharArray(), "ValidPassword123".toCharArray(), true) { setupSuccess = true }
        testScheduler.runCurrent()
        assertTrue(setupSuccess)
        assertTrue(viewModel.uiState.value.hasVault)
        assertTrue(fakePreferencesRepository.biometricEnabled.value)
    }

    @Test
    fun testUnlockWithPasswordSuccessAndFailure() = runTest(testDispatcher) {
        var unlockSuccess = false

        // Empty password
        viewModel.unlockWithPassword(charArrayOf()) { unlockSuccess = true }
        assertFalse(unlockSuccess)
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Setup first
        viewModel.setupNewVault("SecretMasterKey123".toCharArray(), "SecretMasterKey123".toCharArray(), false) {}
        testScheduler.runCurrent()

        // Wrong password
        viewModel.unlockWithPassword("WrongPassword".toCharArray()) { unlockSuccess = true }
        testScheduler.runCurrent()
        assertFalse(unlockSuccess)
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Correct password
        viewModel.unlockWithPassword("SecretMasterKey123".toCharArray()) { unlockSuccess = true }
        testScheduler.runCurrent()
        assertTrue(unlockSuccess)
        assertEquals(0, viewModel.uiState.value.passwordLength)
    }

    @Test
    fun testUnlockWithBiometrics() = runTest(testDispatcher) {
        var unlockSuccess = false
        fakeVaultRepository.setupBiometrics()

        viewModel.unlockWithBiometrics { unlockSuccess = true }
        testScheduler.runCurrent()
        assertTrue(unlockSuccess)
    }

    @Test
    fun testKeepUnlockedUncheckedSetsLockOnBackground() = runTest(testDispatcher) {
        viewModel.setupNewVault("SecretMasterKey123".toCharArray(), "SecretMasterKey123".toCharArray(), false) {}
        testScheduler.runCurrent()

        sessionManager.setLockOnBackground(false)
        viewModel.setKeepUnlocked(false)

        var unlockSuccess = false
        viewModel.unlockWithPassword("SecretMasterKey123".toCharArray()) { unlockSuccess = true }
        testScheduler.runCurrent()

        assertTrue(unlockSuccess)
        assertTrue(sessionManager.isLockOnBackground())
    }

    @Test
    fun testKeepUnlockedCheckedPreservesLockOnBackground() = runTest(testDispatcher) {
        viewModel.setupNewVault("SecretMasterKey123".toCharArray(), "SecretMasterKey123".toCharArray(), false) {}
        testScheduler.runCurrent()

        sessionManager.setLockOnBackground(false)
        viewModel.setKeepUnlocked(true)

        var unlockSuccess = false
        viewModel.unlockWithPassword("SecretMasterKey123".toCharArray()) { unlockSuccess = true }
        testScheduler.runCurrent()

        assertTrue(unlockSuccess)
        assertFalse(sessionManager.isLockOnBackground())
    }
}

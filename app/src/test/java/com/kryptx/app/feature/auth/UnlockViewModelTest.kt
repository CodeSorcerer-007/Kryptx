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
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun testOnPasswordChangedUpdatesState() {
        viewModel.onPasswordChanged("NewPass123")
        assertEquals("NewPass123", viewModel.uiState.value.password)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testSetupNewVaultValidationAndSuccess() = runTest(testDispatcher) {
        var setupSuccess = false

        // Password too short
        viewModel.setupNewVault("short", "short", false) { setupSuccess = true }
        testScheduler.runCurrent()
        assertFalse(setupSuccess)
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Passwords do not match
        viewModel.setupNewVault("ValidPassword123", "MismatchPassword456", false) { setupSuccess = true }
        testScheduler.runCurrent()
        assertFalse(setupSuccess)
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Valid setup
        viewModel.setupNewVault("ValidPassword123", "ValidPassword123", true) { setupSuccess = true }
        testScheduler.runCurrent()
        assertTrue(setupSuccess)
        assertTrue(viewModel.uiState.value.hasVault)
        assertTrue(fakePreferencesRepository.biometricEnabled.value)
    }

    @Test
    fun testUnlockWithPasswordSuccessAndFailure() = runTest(testDispatcher) {
        var unlockSuccess = false

        // Empty password
        viewModel.onPasswordChanged("")
        viewModel.unlockWithPassword { unlockSuccess = true }
        assertFalse(unlockSuccess)
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Setup first
        viewModel.setupNewVault("SecretMasterKey123", "SecretMasterKey123", false) {}
        testScheduler.runCurrent()

        // Wrong password
        viewModel.onPasswordChanged("WrongPassword")
        viewModel.unlockWithPassword { unlockSuccess = true }
        testScheduler.runCurrent()
        assertFalse(unlockSuccess)
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Correct password
        viewModel.onPasswordChanged("SecretMasterKey123")
        viewModel.unlockWithPassword { unlockSuccess = true }
        testScheduler.runCurrent()
        assertTrue(unlockSuccess)
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun testUnlockWithBiometrics() = runTest(testDispatcher) {
        var unlockSuccess = false
        fakeVaultRepository.setupBiometrics()

        viewModel.unlockWithBiometrics { unlockSuccess = true }
        testScheduler.runCurrent()
        assertTrue(unlockSuccess)
    }
}

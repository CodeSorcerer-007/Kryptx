package com.kryptx.app.feature.settings

import com.kryptx.app.core.database.AppThemeMode
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakePreferencesRepository: FakePreferencesRepository
    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePreferencesRepository = FakePreferencesRepository()
        fakeVaultRepository = FakeVaultRepository()
        sessionManager = VaultSessionManager(testScope)
        viewModel = SettingsViewModel(fakePreferencesRepository, fakeVaultRepository, sessionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSetThemeMode() {
        viewModel.setThemeMode(AppThemeMode.AMOLED)
        assertEquals(AppThemeMode.AMOLED, fakePreferencesRepository.themeMode.value)
    }

    @Test
    fun testSetDynamicColor() {
        viewModel.setDynamicColor(true)
        assertTrue(fakePreferencesRepository.dynamicColor.value)
    }

    @Test
    fun testSetAutoLockSeconds() {
        viewModel.setAutoLockSeconds(60L)
        assertEquals(60L, fakePreferencesRepository.autoLockSeconds.value)
    }

    @Test
    fun testChangeMasterPassword() = runTest(testDispatcher) {
        fakeVaultRepository.setupNewVault("CurrentPass123!".toCharArray())
        testScheduler.runCurrent()

        var successCalled = false
        var errorMsg: String? = null

        // Test new password too short
        viewModel.changeMasterPassword("CurrentPass123!", "short", { successCalled = true }, { errorMsg = it })
        assertFalse(successCalled)
        assertNotNull(errorMsg)

        // Test wrong current password
        errorMsg = null
        viewModel.changeMasterPassword("WrongCurrentPass", "ValidNewPass456!", { successCalled = true }, { errorMsg = it })
        testScheduler.runCurrent()
        assertFalse(successCalled)
        assertNotNull(errorMsg)

        // Test valid password change
        viewModel.changeMasterPassword("CurrentPass123!", "ValidNewPass456!", { successCalled = true }, { errorMsg = it })
        testScheduler.runCurrent()
        assertTrue(successCalled)
    }

    @Test
    fun testResetVaultClearsAll() = runTest(testDispatcher) {
        var resetDone = false
        var errorReported: String? = null
        viewModel.resetVault(
            onResetComplete = { resetDone = true },
            onResetError = { errorReported = it }
        )
        testScheduler.runCurrent()

        assertTrue(resetDone)
        org.junit.Assert.assertNull(errorReported)
        assertFalse(fakeVaultRepository.hasVault())
        assertFalse(fakePreferencesRepository.onboardingCompleted.value)
    }

    @Test
    fun testResetVaultFailureDoesNotCompleteOrClearOnboarding() = runTest(testDispatcher) {
        var resetDone = false
        var errorReported: String? = null
        fakePreferencesRepository.setOnboardingCompleted(true)
        fakeVaultRepository.resetVaultResult = com.kryptx.app.core.model.KryptxResult.Error(
            com.kryptx.app.core.model.KryptxErrorType.UNKNOWN,
            "Primary database/storage cleanup incomplete"
        )

        viewModel.resetVault(
            onResetComplete = { resetDone = true },
            onResetError = { errorReported = it }
        )
        testScheduler.runCurrent()

        assertFalse("onResetComplete must not be invoked on reset failure", resetDone)
        assertNotNull("onResetError must be called when reset fails", errorReported)
        assertTrue(errorReported!!.contains("Primary database/storage cleanup incomplete"))
        assertTrue("Onboarding status must remain completed if reset failed", fakePreferencesRepository.onboardingCompleted.value)
    }

    @Test
    fun testSetBiometricEnabledSuccessAndDisableSuccess() = runTest(testDispatcher) {
        var enableReported = false
        var disableReported = false

        viewModel.setBiometricEnabled(true) { enableReported = it }
        testScheduler.runCurrent()
        assertTrue("Enabling biometrics should succeed", enableReported)
        assertTrue("Preference should reflect enabled", fakePreferencesRepository.biometricEnabled.value)

        viewModel.setBiometricEnabled(false) { disableReported = it }
        testScheduler.runCurrent()
        assertTrue("Disabling biometrics should succeed", disableReported)
        assertFalse("Preference should reflect disabled", fakePreferencesRepository.biometricEnabled.value)
    }

    @Test
    fun testDisableBiometricsFailurePreservesPreferenceAndReportsFalse() = runTest(testDispatcher) {
        var disableReported = true
        fakePreferencesRepository.setBiometricEnabled(true)
        fakeVaultRepository.disableBiometricsResult = com.kryptx.app.core.model.KryptxResult.Error(
            com.kryptx.app.core.model.KryptxErrorType.BIOMETRICS_FAILED,
            "Failed to delete biometric key from Android Keystore"
        )

        viewModel.setBiometricEnabled(false) { disableReported = it }
        testScheduler.runCurrent()

        assertFalse("onResult must receive false when disableBiometrics fails", disableReported)
        assertTrue("Preference must remain true when disabling fails", fakePreferencesRepository.biometricEnabled.value)
    }

    @Test
    fun testCategoryVisibilityToggling() {
        val initialVisible = viewModel.visibleCategories.value
        assertTrue(initialVisible.contains("LOGIN"))

        // Toggle LOGIN off
        viewModel.toggleCategoryVisibility(com.kryptx.app.core.model.ItemType.LOGIN)
        assertFalse(viewModel.visibleCategories.value.contains("LOGIN"))

        // Toggle LOGIN back on
        viewModel.toggleCategoryVisibility(com.kryptx.app.core.model.ItemType.LOGIN)
        assertTrue(viewModel.visibleCategories.value.contains("LOGIN"))
    }

    @Test
    fun testMinimalistPreference() {
        assertFalse(viewModel.minimalistDashboardMode.value)
        viewModel.setMinimalistDashboardMode(true)
        assertTrue(viewModel.minimalistDashboardMode.value)
    }

    @Test
    fun testShakeToLockToggle() {
        assertTrue(viewModel.shakeToLockEnabled.value)
        viewModel.setShakeToLockEnabled(false)
        assertFalse(viewModel.shakeToLockEnabled.value)
        viewModel.setShakeToLockEnabled(true)
        assertTrue(viewModel.shakeToLockEnabled.value)
    }

    @Test
    fun testActivityEventsDefaultAndClear() {
        assertNotNull(viewModel.activityEvents.value)
        assertTrue(viewModel.activityEvents.value.isEmpty())
        viewModel.refreshActivityLog()
        viewModel.clearActivityLog()
        assertTrue(viewModel.activityEvents.value.isEmpty())
    }
}

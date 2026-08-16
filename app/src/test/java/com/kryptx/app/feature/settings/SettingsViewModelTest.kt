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
        viewModel.resetVault { resetDone = true }
        testScheduler.runCurrent()

        assertTrue(resetDone)
        assertFalse(fakeVaultRepository.hasVault())
        assertFalse(fakePreferencesRepository.onboardingCompleted.value)
    }
}

package com.kryptx.app.feature.settings

import com.kryptx.app.core.security.VaultSessionManager
import com.kryptx.app.fake.FakePreferencesRepository
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DuressSettingsTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakePrefs: FakePreferencesRepository
    private lateinit var fakeVaultRepo: FakeVaultRepository
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePrefs = FakePreferencesRepository()
        fakeVaultRepo = FakeVaultRepository()
        sessionManager = VaultSessionManager()
        viewModel = SettingsViewModel(fakePrefs, fakeVaultRepo, sessionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test setting and removing duress pin updates state flow`() = runTest {
        assertFalse("Initially duress must not be configured", viewModel.hasDuress.value)

        var successCalled = false
        var errorCalled = false

        // 1. Setup valid duress pin
        viewModel.setupDuressPassword(
            duressPin = "9876",
            onSuccess = { successCalled = true },
            onError = { errorCalled = true }
        )
        advanceUntilIdle()

        assertTrue("Success callback must be called", successCalled)
        assertFalse("Error callback must not be called", errorCalled)
        assertTrue("hasDuress state flow must be true", viewModel.hasDuress.value)
        assertTrue("Repository must confirm duress configured", fakeVaultRepo.hasDuressPassword())

        // 2. Remove duress pin
        var removeCalled = false
        viewModel.removeDuressPassword {
            removeCalled = true
        }
        advanceUntilIdle()

        assertTrue("Remove callback must be called", removeCalled)
        assertFalse("hasDuress state flow must be false", viewModel.hasDuress.value)
        assertFalse("Repository must confirm duress removed", fakeVaultRepo.hasDuressPassword())
    }

    @Test
    fun `test short duress pin triggers validation error`() = runTest {
        var errorMsg: String? = null

        viewModel.setupDuressPassword(
            duressPin = "12", // Under 4 characters
            onSuccess = {},
            onError = { errorMsg = it }
        )
        advanceUntilIdle()

        assertTrue("Error message must be set for short pin", errorMsg != null)
        assertFalse("hasDuress must remain false", viewModel.hasDuress.value)
    }
}

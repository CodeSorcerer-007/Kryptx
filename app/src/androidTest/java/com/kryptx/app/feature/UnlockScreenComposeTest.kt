package com.kryptx.app.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryptx.app.core.security.VaultSessionManager
import com.kryptx.app.fake.FakePreferencesRepository
import com.kryptx.app.fake.FakeVaultRepository
import com.kryptx.app.feature.auth.UnlockScreen
import com.kryptx.app.feature.auth.UnlockViewModel
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnlockScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var fakePreferencesRepository: FakePreferencesRepository
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var viewModel: UnlockViewModel

    @Before
    fun setup() {
        fakeVaultRepository = FakeVaultRepository()
        fakePreferencesRepository = FakePreferencesRepository()
        sessionManager = VaultSessionManager()
        viewModel = UnlockViewModel(fakeVaultRepository, sessionManager, fakePreferencesRepository)
    }

    @Test
    fun testUnlockScreenDisplaysKeyComponents() {
        composeTestRule.setContent {
            UnlockScreen(
                viewModel = viewModel,
                onUnlockSuccess = {},
                onTriggerBiometrics = {}
            )
        }

        composeTestRule.onNodeWithTag("unlock_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("master_password_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unlock_button").assertIsDisplayed()
    }

    @Test
    fun testBiometricSensorTriggerWhenAvailable() {
        fakeVaultRepository.setupBiometrics()
        fakePreferencesRepository.setBiometricEnabled(true)
        viewModel.checkVaultStatus()

        composeTestRule.setContent {
            UnlockScreen(
                viewModel = viewModel,
                onUnlockSuccess = {},
                onTriggerBiometrics = {}
            )
        }

        composeTestRule.onNodeWithTag("secondary_biometric_button").assertIsDisplayed()
    }

    @Test
    fun testMasterPasswordInputEnablesUnlockButton() {
        var unlocked = false
        composeTestRule.setContent {
            UnlockScreen(
                viewModel = viewModel,
                onUnlockSuccess = { unlocked = true },
                onTriggerBiometrics = {}
            )
        }

        composeTestRule.onNodeWithTag("master_password_field").performTextInput("MasterPassword123!")
        composeTestRule.onNodeWithTag("unlock_button").performClick()
        assertTrue("Successful unlock callback should fire", unlocked)
    }
}

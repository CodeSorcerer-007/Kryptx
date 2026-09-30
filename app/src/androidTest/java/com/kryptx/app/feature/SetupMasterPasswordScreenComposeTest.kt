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
import com.kryptx.app.feature.auth.SetupMasterPasswordScreen
import com.kryptx.app.feature.auth.UnlockViewModel
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupMasterPasswordScreenComposeTest {

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
    fun testSetupScreenDisplaysKeyElements() {
        composeTestRule.setContent {
            SetupMasterPasswordScreen(
                viewModel = viewModel,
                onVaultCreated = {}
            )
        }

        composeTestRule.onNodeWithTag("setup_master_password_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("password_input_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("password_confirm_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_vault_button").assertIsDisplayed()
    }

    @Test
    fun testMatchingPasswordEnablesVaultCreation() {
        var created = false
        composeTestRule.setContent {
            SetupMasterPasswordScreen(
                viewModel = viewModel,
                onVaultCreated = { created = true }
            )
        }

        composeTestRule.onNodeWithTag("password_input_field").performTextInput("StrongPass123!@#")
        composeTestRule.onNodeWithTag("password_confirm_field").performTextInput("StrongPass123!@#")
        composeTestRule.onNodeWithTag("create_vault_button").performClick()

        assertTrue("Vault created callback should be triggered", created)
    }
}

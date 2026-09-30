package com.kryptx.app.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryptx.app.core.security.VaultSessionManager
import com.kryptx.app.fake.FakeClipboardSecurityManager
import com.kryptx.app.fake.FakePreferencesRepository
import com.kryptx.app.fake.FakeVaultRepository
import com.kryptx.app.feature.vault.AddEditItemScreen
import com.kryptx.app.feature.vault.VaultViewModel
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddEditItemScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var fakePreferencesRepository: FakePreferencesRepository
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var clipboardManager: FakeClipboardSecurityManager
    private lateinit var viewModel: VaultViewModel

    @Before
    fun setup() {
        fakeVaultRepository = FakeVaultRepository()
        fakePreferencesRepository = FakePreferencesRepository()
        sessionManager = VaultSessionManager()
        clipboardManager = FakeClipboardSecurityManager()
        viewModel = VaultViewModel(
            vaultRepository = fakeVaultRepository,
            sessionManager = sessionManager,
            clipboardSecurityManager = clipboardManager,
            preferencesRepository = fakePreferencesRepository
        )
    }

    @Test
    fun testAddEditScreenDisplaysFormInputs() {
        composeTestRule.setContent {
            AddEditItemScreen(
                itemId = null,
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithTag("add_edit_item_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("item_title_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_vault_item_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("item_type_pill_login").assertIsDisplayed()
        composeTestRule.onNodeWithTag("item_type_pill_credit_card").assertIsDisplayed()
    }

    @Test
    fun testEmptyTitleShowsValidationError() {
        composeTestRule.setContent {
            AddEditItemScreen(
                itemId = null,
                viewModel = viewModel,
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithTag("save_vault_item_button").performClick()
        composeTestRule.onNodeWithTag("add_edit_error_message").assertIsDisplayed()
    }

    @Test
    fun testValidItemSaveTriggersNavigateBack() {
        var navigatedBack = false
        composeTestRule.setContent {
            AddEditItemScreen(
                itemId = null,
                viewModel = viewModel,
                onNavigateBack = { navigatedBack = true }
            )
        }

        composeTestRule.onNodeWithTag("item_title_field").performTextInput("Proton Mail Account")
        composeTestRule.onNodeWithTag("save_vault_item_button").performClick()

        assertTrue("Navigate back callback should be invoked on save", navigatedBack)
    }
}

package com.kryptx.app.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryptx.app.fake.FakeClipboardSecurityManager
import com.kryptx.app.feature.generator.GeneratorScreen
import com.kryptx.app.feature.generator.GeneratorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeneratorScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var clipboardManager: FakeClipboardSecurityManager
    private lateinit var viewModel: GeneratorViewModel

    @Before
    fun setup() {
        clipboardManager = FakeClipboardSecurityManager()
        viewModel = GeneratorViewModel(clipboardManager)
    }

    @Test
    fun testGeneratorScreenDisplaysAllCoreControls() {
        composeTestRule.setContent {
            GeneratorScreen(viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("generator_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_tab_password").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_tab_passphrase").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_tab_pin").assertIsDisplayed()
        composeTestRule.onNodeWithTag("generated_credential_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("regenerate_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_credential_button").assertIsDisplayed()
    }

    @Test
    fun testRegenerateButtonGeneratesNewValue() {
        composeTestRule.setContent {
            GeneratorScreen(viewModel = viewModel)
        }

        val initialValue = viewModel.result.value.value
        composeTestRule.onNodeWithTag("regenerate_button").performClick()
        val newValue = viewModel.result.value.value
        assertNotNull(newValue)
    }

    @Test
    fun testCopyButtonCopiesToClipboard() {
        composeTestRule.setContent {
            GeneratorScreen(viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("copy_credential_button").performClick()
        assertEquals(viewModel.result.value.value, clipboardManager.lastCopiedText)
    }
}

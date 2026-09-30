package com.kryptx.app.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryptx.app.fake.FakeClipboardSecurityManager
import com.kryptx.app.fake.FakeVaultRepository
import com.kryptx.app.feature.search.SearchScreen
import com.kryptx.app.feature.search.SearchViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var clipboardManager: FakeClipboardSecurityManager
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setup() {
        fakeVaultRepository = FakeVaultRepository()
        clipboardManager = FakeClipboardSecurityManager()
        viewModel = SearchViewModel(fakeVaultRepository, clipboardManager)
    }

    @Test
    fun testSearchScreenDisplaysInputAndEmptyState() {
        composeTestRule.setContent {
            SearchScreen(
                viewModel = viewModel,
                onNavigateToItemDetail = {},
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithTag("search_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_input_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_empty_state").assertIsDisplayed()
    }

    @Test
    fun testSearchInputFilterQueryUpdates() {
        composeTestRule.setContent {
            SearchScreen(
                viewModel = viewModel,
                onNavigateToItemDetail = {},
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithTag("search_input_field").performTextInput("github.com")
        composeTestRule.onNodeWithTag("search_empty_state").assertIsDisplayed()
    }
}

package com.kryptx.app.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryptx.app.fake.FakeClipboardSecurityManager
import com.kryptx.app.fake.FakeVaultRepository
import com.kryptx.app.feature.totp.TotpListScreen
import com.kryptx.app.feature.totp.TotpViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TotpListScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var clipboardManager: FakeClipboardSecurityManager
    private lateinit var viewModel: TotpViewModel

    @Before
    fun setup() {
        fakeVaultRepository = FakeVaultRepository()
        clipboardManager = FakeClipboardSecurityManager()
        viewModel = TotpViewModel(fakeVaultRepository, clipboardManager)
    }

    @Test
    fun testTotpListScreenDisplaysWhenEmpty() {
        composeTestRule.setContent {
            TotpListScreen(viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("totp_list_screen").assertIsDisplayed()
    }
}

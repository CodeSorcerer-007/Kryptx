package com.kryptx.app.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryptx.app.fake.FakeClipboardSecurityManager
import com.kryptx.app.fake.FakeVaultRepository
import com.kryptx.app.feature.securitycenter.SecurityCenterScreen
import com.kryptx.app.feature.securitycenter.SecurityCenterViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityCenterScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var clipboardManager: FakeClipboardSecurityManager
    private lateinit var viewModel: SecurityCenterViewModel

    @Before
    fun setup() {
        fakeVaultRepository = FakeVaultRepository()
        clipboardManager = FakeClipboardSecurityManager()
        viewModel = SecurityCenterViewModel(fakeVaultRepository, clipboardManager)
    }

    @Test
    fun testSecurityCenterDisplaysScoreAndStatBoxes() {
        composeTestRule.setContent {
            SecurityCenterScreen(
                viewModel = viewModel,
                onNavigateToFixItem = {},
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithTag("security_center_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("refresh_audit_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("security_score_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audit_stat_breached").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audit_stat_weak").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audit_stat_reused").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audit_stat_no_2fa").assertIsDisplayed()
    }

    @Test
    fun testRefreshAuditTriggersReload() {
        composeTestRule.setContent {
            SecurityCenterScreen(
                viewModel = viewModel,
                onNavigateToFixItem = {},
                onNavigateBack = {}
            )
        }

        composeTestRule.onNodeWithTag("refresh_audit_button").performClick()
        composeTestRule.onNodeWithTag("security_score_card").assertIsDisplayed()
    }
}

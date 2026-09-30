package com.kryptx.app.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kryptx.app.fake.FakeVaultRepository
import com.kryptx.app.feature.settings.SettingsScreen
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeVaultRepository: FakeVaultRepository

    @Before
    fun setup() {
        fakeVaultRepository = FakeVaultRepository()
    }

    @Test
    fun testSettingsScreenDisplaysNavRows() {
        composeTestRule.setContent {
            SettingsScreen(
                onNavigateToSecurity = {},
                onNavigateToAppearance = {},
                onNavigateToBackup = {},
                onReplayGuides = {},
                vaultRepository = fakeVaultRepository
            )
        }

        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_nav_security_center_and_biometrics").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_nav_security_architecture_explained").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_nav_backup_and_export").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_nav_appearance_and_theme").assertIsDisplayed()
    }

    @Test
    fun testSettingsNavigationCallbacksFire() {
        var securityNavigated = false
        var appearanceNavigated = false

        composeTestRule.setContent {
            SettingsScreen(
                onNavigateToSecurity = { securityNavigated = true },
                onNavigateToAppearance = { appearanceNavigated = true },
                onNavigateToBackup = {},
                onReplayGuides = {},
                vaultRepository = fakeVaultRepository
            )
        }

        composeTestRule.onNodeWithTag("settings_nav_security_center_and_biometrics").performClick()
        assertTrue("Security nav callback should fire", securityNavigated)

        composeTestRule.onNodeWithTag("settings_nav_appearance_and_theme").performClick()
        assertTrue("Appearance nav callback should fire", appearanceNavigated)
    }
}

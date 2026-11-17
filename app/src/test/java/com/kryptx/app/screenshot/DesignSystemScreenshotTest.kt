package com.kryptx.app.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.kryptx.app.core.database.AppThemeMode
import com.kryptx.app.core.designsystem.components.ItemTypeBadge
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxEmptyState
import com.kryptx.app.core.designsystem.components.KryptxLogo
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxScoreRing
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.theme.KryptxTheme
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.feature.onboarding.components.SetupPermissionItemCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Password
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Headless Roborazzi Screenshot Regression Suite.
 *
 * Catches visual layout shifts, typography breaks, and component scaling anomalies across:
 * - Phone compact (Pixel 5, 360x640 hdpi, 411x891 xxhdpi, 412x915 xxxhdpi)
 * - Foldable unfolded (w673dp-h841dp-420dpi)
 * - Tablet landscape (sw600dp-land, w1280dp-h800dp-xhdpi)
 * - Multiple display densities: hdpi, xhdpi, xxhdpi, xxxhdpi
 * - Theme variants: Sovereign Dark & High-Contrast Light
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class DesignSystemScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun captureScoreRing_darkTheme() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(
                    modifier = Modifier.padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    KryptxScoreRing(score = 92, grade = "A+")
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun captureScoreRing_lightTheme() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.LIGHT) {
                Box(
                    modifier = Modifier.padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    KryptxScoreRing(score = 58, grade = "C")
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun captureScoreRing_warningState() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(
                    modifier = Modifier.padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    KryptxScoreRing(score = 34, grade = "D")
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun captureButton_primary() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(modifier = Modifier.padding(16.dp)) {
                    KryptxPrimaryButton(
                        text = "Unlock Vault",
                        onClick = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun captureTextField_withContent() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(modifier = Modifier.padding(16.dp)) {
                    KryptxTextField(
                        value = "master.vault.kryptx",
                        onValueChange = {},
                        label = "Vault Identity",
                        placeholder = "Enter alias or handle"
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun captureBadges_itemTypes() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ItemTypeBadge(type = ItemType.LOGIN)
                    ItemTypeBadge(type = ItemType.PASSKEY)
                    ItemTypeBadge(type = ItemType.SECURE_NOTE)
                    ItemTypeBadge(type = ItemType.CREDIT_CARD)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun captureCard_sample() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(modifier = Modifier.padding(16.dp)) {
                    KryptxCard(
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Kryptx Encrypted Vault Secure Item",
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Zero-Knowledge Hardware Protected",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-hdpi")
    fun captureCard_compactPhone_hdpi() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(modifier = Modifier.padding(8.dp)) {
                    KryptxCard(
                        modifier = Modifier.fillMaxWidth().padding(4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Compact Screen (360dp hdpi)",
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Validating layout stability on lower density devices",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xxxhdpi")
    fun captureCard_phone_ultraHighDensity_xxxhdpi() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(modifier = Modifier.padding(16.dp)) {
                    KryptxCard(
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Ultra High Density (xxxhdpi 560dpi)",
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Subpixel boundary anti-aliasing and font rendering",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "w673dp-h841dp-420dpi")
    fun captureCard_foldableUnfolded() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(modifier = Modifier.padding(24.dp)) {
                    KryptxCard(
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Foldable Unfolded Screen (673dp)",
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Aspect ratio adaptation and responsive flex spacing",
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "sw600dp-land")
    fun captureCard_tabletLayout() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(modifier = Modifier.padding(32.dp)) {
                    KryptxCard(
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = "Tablet High Density Display Layout",
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Verification of responsive density and scale shifts",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun captureCard_largeTablet_landscape() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(modifier = Modifier.padding(32.dp)) {
                    KryptxCard(
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(32.dp)) {
                            Text(
                                text = "Large Desktop / Tablet Display (1280dp xhdpi)",
                                fontSize = 22.sp
                            )
                            Text(
                                text = "Testing maximum layout expansion bounds without text distortion",
                                fontSize = 15.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun captureEmptyState_sovereignVaultDial() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    KryptxEmptyState(
                        title = "Zero Credentials Stored",
                        subtitle = "Initialize your air-gapped vault with post-quantum security",
                        actionButtonText = "Create First Credential",
                        onActionClick = {}
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun captureLogo_subtleGlow_darkTheme() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(
                    modifier = Modifier.padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    KryptxLogo(size = 96.dp, showGlow = true)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun captureLogo_noGlow_lightTheme() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.LIGHT) {
                Box(
                    modifier = Modifier.padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    KryptxLogo(size = 96.dp, showGlow = false)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xxxhdpi")
    fun captureAutofillPermissionCard_xxxhdpi() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(modifier = Modifier.padding(16.dp)) {
                    SetupPermissionItemCard(
                        title = "1-Tap Android Autofill",
                        badgeText = "Optional Setup",
                        isPositive = false,
                        icon = Icons.Default.Password,
                        description = "Allows Kryptx to securely auto-fill your credentials inside Chrome, Firefox, and third-party native apps with 1 tap.",
                        action = null
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun captureAutofillPermissionCard_active() {
        composeTestRule.setContent {
            KryptxTheme(themeMode = AppThemeMode.DARK) {
                Box(modifier = Modifier.padding(16.dp)) {
                    SetupPermissionItemCard(
                        title = "1-Tap Android Autofill",
                        badgeText = "Active Provider",
                        isPositive = true,
                        icon = Icons.Default.Password,
                        description = "Allows Kryptx to securely auto-fill your credentials inside Chrome, Firefox, and third-party native apps with 1 tap.",
                        action = null
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}

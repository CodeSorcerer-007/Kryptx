package com.kryptx.app.core.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.kryptx.app.core.designsystem.theme.KryptxElectricBlueGradient
import com.kryptx.app.core.designsystem.theme.KryptxShapes
import com.kryptx.app.core.designsystem.theme.LocalKryptxAudio
import com.kryptx.app.core.designsystem.theme.LocalKryptxHaptics

enum class BottomNavTab(
    val label: String,
    val icon: ImageVector,
    val featureGuide: FeatureGuide
) {
    VAULT("Vault", Icons.Default.Lock, FeatureGuide.VAULT),
    TOTP("2FA", Icons.Default.Key, FeatureGuide.TOTP),
    GENERATOR("Generator", Icons.Default.AutoAwesome, FeatureGuide.GENERATOR),
    SETTINGS("Settings", Icons.Default.Settings, FeatureGuide.SETTINGS)
}

/**
 * Sovereign Tactile Magnetic Gliding Bottom Navigation Bar.
 * Features an organic physics-based spring indicator, specular edge highlights,
 * TalkBack accessibility announcements, and haptic/acoustic feedback.
 */
@Composable
fun KryptxBottomNavBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalKryptxHaptics.current
    val audio = LocalKryptxAudio.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val navBg = if (isDark) Color(0xFF070B14).copy(alpha = 0.92f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    val navBorder = if (isDark) GlassmorphismSpecularBrush else SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

    val pillBgBrush = if (isDark) {
        Brush.linearGradient(
            listOf(
                Color.White,
                Color(0xFFE2E8F0)
            )
        )
    } else {
        KryptxElectricBlueGradient
    }

    val selectedIconTint = if (isDark) Color(0xFF04060A) else Color.White
    val unselectedIconTint = if (isDark) Color(0xFF7E8B9E) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

    val tabs = remember { BottomNavTab.entries }
    val selectedIndex = remember(selectedTab) { tabs.indexOf(selectedTab).coerceAtLeast(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag("bottom_nav_bar"),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(KryptxShapes.Pill)
                .background(navBg)
                .border(1.dp, navBorder, KryptxShapes.Pill)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidth = maxWidth
            val tabCount = tabs.size
            val slotWidth = totalWidth / tabCount
            val pillWidth = (slotWidth - 8.dp).coerceAtLeast(44.dp)
            val pillHeight = 52.dp

            val animatedIndex by animateFloatAsState(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.74f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "magnetic_pill_offset"
            )

            // 1. Fluid Magnetic Gliding Pill Indicator
            Box(
                modifier = Modifier
                    .offset {
                        val slotPx = slotWidth.toPx()
                        val pillPx = pillWidth.toPx()
                        val x = (slotPx * animatedIndex) + ((slotPx - pillPx) / 2f)
                        IntOffset(x.toInt(), 0)
                    }
                    .size(width = pillWidth, height = pillHeight)
                    .clip(KryptxShapes.Floating)
                    .background(pillBgBrush)
                    .border(
                        1.dp,
                        if (isDark) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.25f),
                        KryptxShapes.Floating
                    )
            )

            // 2. Interactive Tab Targets
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedIndex == index

                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.14f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = 0.65f,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tab_icon_scale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(pillHeight)
                            .clip(KryptxShapes.Floating)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!isSelected) {
                                    haptics.tap()
                                    audio.tick()
                                    onTabSelected(tab)
                                }
                            }
                            .testTag("bottom_nav_tab_${tab.name.lowercase()}")
                            .semantics {
                                role = Role.Tab
                                contentDescription = "${tab.label} tab"
                                stateDescription = if (isSelected) "Selected" else "Not selected"
                                selected = isSelected
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) selectedIconTint else unselectedIconTint,
                                modifier = Modifier
                                    .size(22.dp)
                                    .scale(iconScale)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun KryptxBottomNavBarPreview() {
    KryptxBottomNavBar(
        selectedTab = BottomNavTab.VAULT,
        onTabSelected = {}
    )
}

package com.kryptx.app.feature.vault

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxSkyBlue
import com.kryptx.app.core.designsystem.theme.KryptxViolet
import com.kryptx.app.core.model.ItemType

/**
 * Floating 3D Category Badges Hero Area with dynamic category support and cyber mechanical aesthetics.
 */
@Composable
fun VaultCategoryBadges(
    allItemsCount: Int,
    loginsCount: Int,
    cardsCount: Int,
    notesCount: Int,
    totpCount: Int,
    selectedCategory: ItemType?,
    onSelectCategory: (ItemType?) -> Unit,
    onNavigateTo2Fa: () -> Unit,
    visibleCategories: Set<ItemType> = ItemType.entries.toSet(),
    categoryCounts: Map<ItemType, Int> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FloatingCategoryBadge(
            label = "All",
            badgeText = "ALL",
            count = allItemsCount,
            color = KryptxBlue,
            isSelected = selectedCategory == null,
            onClick = {
                KryptxHaptics.tap(view)
                onSelectCategory(null)
            }
        )

        // Render core categories and any category that actually has items
        val coreCategories = setOf(ItemType.LOGIN, ItemType.CREDIT_CARD, ItemType.SECURE_NOTE, ItemType.PASSKEY)
        visibleCategories.filter { type ->
            coreCategories.contains(type) || (categoryCounts[type] ?: 0) > 0 || selectedCategory == type
        }.forEach { type ->
            val count = categoryCounts[type] ?: when (type) {
                ItemType.LOGIN -> loginsCount
                ItemType.CREDIT_CARD -> cardsCount
                ItemType.SECURE_NOTE -> notesCount
                else -> 0
            }

            val (badgeText, badgeColor) = when (type) {
                ItemType.LOGIN -> "KEY" to KryptxBlue
                ItemType.CREDIT_CARD -> "CARD" to KryptxBrightBlue
                ItemType.PASSKEY -> "PASSKEY" to KryptxCyan
                ItemType.SECURE_NOTE -> "NOTE" to KryptxSkyBlue
                ItemType.WIFI -> "WI-FI" to KryptxEmerald
                ItemType.IDENTITY -> "ID" to KryptxViolet
                ItemType.API_KEY -> "API" to KryptxAmber
                ItemType.CUSTOM -> "CUSTOM" to KryptxBlue
            }

            FloatingCategoryBadge(
                label = type.categoryName,
                badgeText = badgeText,
                count = count,
                color = badgeColor,
                isSelected = selectedCategory == type,
                onClick = {
                    KryptxHaptics.tap(view)
                    onSelectCategory(type)
                }
            )
        }
    }
}

/**
 * 3D-styled floating mechanical category badge with active neon glow.
 */
@Composable
fun FloatingCategoryBadge(
    label: String,
    badgeText: String,
    count: Int,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        animationSpec = tween(250),
        label = "categoryBg"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) color.copy(alpha = 0.9f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
        animationSpec = tween(250),
        label = "categoryBorder"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .bounceClick(scaleDown = 0.93f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics {
                role = Role.Tab
                contentDescription = "Category $label, $count items"
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) color else color.copy(alpha = 0.75f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isSelected) color.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$count",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


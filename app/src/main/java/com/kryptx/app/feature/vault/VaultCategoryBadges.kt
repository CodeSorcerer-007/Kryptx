package com.kryptx.app.feature.vault

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxSkyBlue
import com.kryptx.app.core.model.ItemType

/**
 * Floating 3D Category Badges Hero Area with standard theme color tokens.
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FloatingCategoryBadge(
            label = "All",
            badgeText = "ALL",
            count = allItemsCount,
            color = KryptxBlue,
            isSelected = selectedCategory == null,
            onClick = { onSelectCategory(null) }
        )
        FloatingCategoryBadge(
            label = "Logins",
            badgeText = "KEY",
            count = loginsCount,
            color = KryptxBlue,
            isSelected = selectedCategory == ItemType.LOGIN,
            onClick = { onSelectCategory(ItemType.LOGIN) }
        )
        FloatingCategoryBadge(
            label = "Cards",
            badgeText = "CARD",
            count = cardsCount,
            color = KryptxBrightBlue,
            isSelected = selectedCategory == ItemType.CREDIT_CARD,
            onClick = { onSelectCategory(ItemType.CREDIT_CARD) }
        )
        FloatingCategoryBadge(
            label = "Notes",
            badgeText = "NOTE",
            count = notesCount,
            color = KryptxSkyBlue,
            isSelected = selectedCategory == ItemType.SECURE_NOTE,
            onClick = { onSelectCategory(ItemType.SECURE_NOTE) }
        )
        FloatingCategoryBadge(
            label = "2FA",
            badgeText = "TOTP",
            count = totpCount,
            color = KryptxCyan,
            isSelected = false,
            onClick = onNavigateTo2Fa
        )
    }
}

/**
 * 3D-styled floating category badge (WorkONE hero badge).
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) color.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .border(
                1.dp,
                if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp)
            )
            .bounceClick(scaleDown = 0.94f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics {
                role = Role.Tab
                contentDescription = "Category $label"
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$label ($count)",
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

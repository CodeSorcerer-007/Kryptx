package com.kryptx.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.database.AppThemeMode
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.designsystem.theme.KryptxTheme
import com.kryptx.app.core.model.IssueSeverity
import com.kryptx.app.core.model.ItemType

/**
 * Visual Quality Assurance and Component Showcase Catalog for Kryptx design system primitives.
 * Displays all buttons, badges, security rings, empty states, cards, and input fields
 * across Light, Dark, and AMOLED themes.
 */
@Composable
fun KryptxComponentCatalog(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    var sampleText by remember { mutableStateOf("sample_password_123!") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Kryptx Component Catalog",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // ── 1. Buttons ──────────────────────────────────────────────────────────
        CatalogSection(title = "Buttons") {
            KryptxPrimaryButton(
                text = "Primary Button (Default)",
                onClick = {}
            )
            KryptxPrimaryButton(
                text = "Primary Button (Brand Gradient)",
                useBrandGradient = true,
                onClick = {}
            )
            KryptxOutlinedButton(
                text = "Secondary Outlined Button",
                onClick = {}
            )
            KryptxPrimaryButton(
                text = "Danger / Auto-Destruct Button",
                containerColor = KryptxRed,
                onClick = {}
            )
        }

        // ── 2. Badges ───────────────────────────────────────────────────────────
        CatalogSection(title = "Type & Strength Badges") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ItemTypeBadge(type = ItemType.LOGIN)
                ItemTypeBadge(type = ItemType.PASSKEY)
                ItemTypeBadge(type = ItemType.CREDIT_CARD)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StrengthBadge(strength = EntropyCalculator.StrengthScore.VERY_STRONG)
                StrengthBadge(strength = EntropyCalculator.StrengthScore.STRONG)
                StrengthBadge(strength = EntropyCalculator.StrengthScore.FAIR)
                StrengthBadge(strength = EntropyCalculator.StrengthScore.WEAK)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SeverityBadge(severity = IssueSeverity.CRITICAL)
                SeverityBadge(severity = IssueSeverity.WARNING)
                SeverityBadge(severity = IssueSeverity.INFO)
            }
        }

        // ── 3. Cards & Containers ───────────────────────────────────────────────
        CatalogSection(title = "Cards & Glassmorphic Containers") {
            KryptxCard(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Standard Kryptx Card",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hardware-accelerated border with subtle inner sheen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── 4. Security Indicators ──────────────────────────────────────────────
        CatalogSection(title = "Security Indicators & Score Rings") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                KryptxScoreRing(score = 98)
                KryptxScoreRing(score = 72)
                KryptxScoreRing(score = 45)
            }
        }

        // ── 5. Input Fields ─────────────────────────────────────────────────────
        CatalogSection(title = "Inputs & Text Fields") {
            var secureBuffer by remember { mutableStateOf("sample_password_123!".toCharArray()) }
            KryptxTextField(
                value = sampleText,
                onValueChange = { sampleText = it },
                label = "Username or Email",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            SecureTextField(
                value = secureBuffer,
                onValueChange = { secureBuffer = it },
                label = "Master Password",
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── 6. Empty States ─────────────────────────────────────────────────────
        CatalogSection(title = "Empty States") {
            KryptxEmptyState(
                icon = Icons.Default.Security,
                title = "Zero Compromises Found",
                subtitle = "Your encrypted database passed all device security heuristics."
            )
        }
    }
}

@Composable
private fun CatalogSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

// ── Compose Previews (Task 3.1 & 3.2) ──────────────────────────────────────────

@Preview(name = "Catalog - Dark Theme", showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
private fun KryptxComponentCatalogDarkPreview() {
    KryptxTheme(themeMode = AppThemeMode.DARK) {
        Surface {
            KryptxComponentCatalog()
        }
    }
}

@Preview(name = "Catalog - Light Theme", showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun KryptxComponentCatalogLightPreview() {
    KryptxTheme(themeMode = AppThemeMode.LIGHT) {
        Surface {
            KryptxComponentCatalog()
        }
    }
}

@Preview(name = "Catalog - AMOLED Pure Black", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun KryptxComponentCatalogAmoledPreview() {
    KryptxTheme(themeMode = AppThemeMode.AMOLED) {
        Surface {
            KryptxComponentCatalog()
        }
    }
}

package com.kryptx.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxBrightBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxPurple
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.model.ItemType
import java.util.Locale
import kotlin.math.abs

/**
 * Deterministic offline domain & brand identicon generator.
 * 100% zero network requests, zero telemetry, and zero CDN exposure.
 * Computes aesthetic cryptographic geometric avatars and brand-tuned visual palettes.
 */
object OfflineIdenticonGenerator {

    data class BrandIdentity(
        val monogram: String,
        val primaryColor: Color,
        val secondaryColor: Color,
        val icon: ImageVector? = null
    )

    private val KNOWN_BRANDS = mapOf(
        "google" to BrandIdentity("G", Color(0xFF4285F4), Color(0xFFEA4335)),
        "gmail" to BrandIdentity("M", Color(0xFFEA4335), Color(0xFFFBBC05)),
        "youtube" to BrandIdentity("YT", Color(0xFFFF0000), Color(0xFF282828)),
        "github" to BrandIdentity("GH", Color(0xFF6E5494), Color(0xFF24292E), Icons.Default.Terminal),
        "gitlab" to BrandIdentity("GL", Color(0xFFFC6D26), Color(0xFFE24329)),
        "microsoft" to BrandIdentity("MS", Color(0xFF00A4EF), Color(0xFF7FBA00)),
        "apple" to BrandIdentity("AP", Color(0xFFA2AAAD), Color(0xFF1D1D1F)),
        "icloud" to BrandIdentity("iC", Color(0xFF369BFF), Color(0xFFFFFFFF)),
        "amazon" to BrandIdentity("AZ", Color(0xFFFF9900), Color(0xFF146EB4)),
        "aws" to BrandIdentity("AWS", Color(0xFFFF9900), Color(0xFF232F3E)),
        "netflix" to BrandIdentity("N", Color(0xFFE50914), Color(0xFF221F1F)),
        "spotify" to BrandIdentity("SP", Color(0xFF1DB954), Color(0xFF191414)),
        "steam" to BrandIdentity("ST", Color(0xFF171A21), Color(0xFF66C0F4)),
        "discord" to BrandIdentity("DC", Color(0xFF5865F2), Color(0xFF23272A)),
        "slack" to BrandIdentity("SL", Color(0xFF4A154B), Color(0xFF36C5F0)),
        "twitter" to BrandIdentity("X", Color(0xFF1DA1F2), Color(0xFF14171A)),
        "x.com" to BrandIdentity("X", Color(0xFF000000), Color(0xFFFFFFFF)),
        "reddit" to BrandIdentity("RD", Color(0xFFFF4500), Color(0xFFFFFFFF)),
        "facebook" to BrandIdentity("FB", Color(0xFF1877F2), Color(0xFFFFFFFF)),
        "instagram" to BrandIdentity("IG", Color(0xFFE4405F), Color(0xFF833AB4)),
        "linkedin" to BrandIdentity("IN", Color(0xFF0A66C2), Color(0xFFFFFFFF)),
        "proton" to BrandIdentity("PM", Color(0xFF6D4AFF), Color(0xFF241641)),
        "bitwarden" to BrandIdentity("BW", Color(0xFF175DDC), Color(0xFF175DDC)),
        "1password" to BrandIdentity("1P", Color(0xFF0094F5), Color(0xFF0B5394)),
        "paypal" to BrandIdentity("PP", Color(0xFF003087), Color(0xFF0079C1)),
        "stripe" to BrandIdentity("ST", Color(0xFF635BFF), Color(0xFF0A2540)),
        "binance" to BrandIdentity("BN", Color(0xFFF3BA2F), Color(0xFF181A20)),
        "coinbase" to BrandIdentity("CB", Color(0xFF0052FF), Color(0xFFFFFFFF)),
        "notion" to BrandIdentity("NT", Color(0xFF000000), Color(0xFFFFFFFF)),
        "figma" to BrandIdentity("FG", Color(0xFFF24E1E), Color(0xFFA259FF)),
        "chatgpt" to BrandIdentity("AI", Color(0xFF10A37F), Color(0xFF202123)),
        "openai" to BrandIdentity("OA", Color(0xFF10A37F), Color(0xFF000000)),
        "anthropic" to BrandIdentity("CL", Color(0xFFD97706), Color(0xFF78350F)),
        "claude" to BrandIdentity("CL", Color(0xFFD97706), Color(0xFF1F2937)),
        "dropbox" to BrandIdentity("DB", Color(0xFF0061FF), Color(0xFFFFFFFF)),
        "telegram" to BrandIdentity("TG", Color(0xFF24A1DE), Color(0xFFFFFFFF)),
        "whatsapp" to BrandIdentity("WA", Color(0xFF25D366), Color(0xFF075E54)),
        "uber" to BrandIdentity("UB", Color(0xFF000000), Color(0xFFFFFFFF)),
        "airbnb" to BrandIdentity("AB", Color(0xFFFF5A5F), Color(0xFF484848))
    )

    private val PALETTE = listOf(
        Pair(KryptxBlue, KryptxBrightBlue),
        Pair(KryptxPurple, Color(0xFFD946EF)),
        Pair(KryptxEmerald, KryptxCyan),
        Pair(KryptxAmber, Color(0xFFFF7043)),
        Pair(Color(0xFF06B6D4), Color(0xFF3B82F6)),
        Pair(Color(0xFFEC4899), Color(0xFFF43F5E)),
        Pair(Color(0xFF8B5CF6), Color(0xFF6366F1)),
        Pair(Color(0xFF10B981), Color(0xFF059669))
    )

    /**
     * Resolves brand or extracts initials and deterministic gradient for any given title / domain / item.
     */
    fun resolve(title: String, website: String, type: ItemType): BrandIdentity {
        val cleanDomain = extractDomain(website).lowercase(Locale.ROOT)
        val cleanTitle = title.trim().lowercase(Locale.ROOT)

        for ((key, identity) in KNOWN_BRANDS) {
            if (cleanDomain.contains(key) || cleanTitle.contains(key)) {
                return identity
            }
        }

        // Category-specific fallback icons
        val defaultIcon = when (type) {
            ItemType.LOGIN -> null
            ItemType.PASSKEY -> Icons.Default.Fingerprint
            ItemType.CREDIT_CARD -> Icons.Default.CreditCard
            ItemType.IDENTITY -> Icons.Default.Person
            ItemType.SECURE_NOTE -> Icons.AutoMirrored.Filled.Note
            ItemType.WIFI -> Icons.Default.Wifi
            ItemType.API_KEY -> Icons.Default.DataObject
            ItemType.BANK_ACCOUNT -> Icons.Default.AccountBalance
            ItemType.CRYPTO_WALLET -> Icons.Default.Key
            ItemType.SSH_KEY -> Icons.Default.Terminal
            ItemType.MEDICAL -> Icons.Default.MedicalServices
            ItemType.CUSTOM -> Icons.Default.Lock
        }

        val seedText = cleanDomain.ifBlank { cleanTitle.ifBlank { type.name } }
        val hash = abs(seedText.hashCode())
        val palettePair = PALETTE[hash % PALETTE.size]

        val monogram = if (defaultIcon == null) {
            extractMonogram(if (cleanDomain.isNotBlank()) cleanDomain else title)
        } else ""

        return BrandIdentity(
            monogram = monogram,
            primaryColor = palettePair.first,
            secondaryColor = palettePair.second,
            icon = defaultIcon
        )
    }

    private fun extractDomain(uriOrDomain: String): String {
        var clean = uriOrDomain.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
        val slashIndex = clean.indexOf('/')
        if (slashIndex != -1) {
            clean = clean.substring(0, slashIndex)
        }
        val colonIndex = clean.indexOf(':')
        if (colonIndex != -1) {
            clean = clean.substring(0, colonIndex)
        }
        return clean
    }

    private fun extractMonogram(text: String): String {
        val words = text.trim().split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "K"
            words.size == 1 -> {
                val single = words[0]
                if (single.length >= 2) single.take(2).uppercase(Locale.ROOT)
                else single.take(1).uppercase(Locale.ROOT)
            }
            else -> {
                "${words[0].take(1)}${words[1].take(1)}".uppercase(Locale.ROOT)
            }
        }
    }
}

/**
 * High-performance, offline vector identicon badge for vault items.
 */
@Composable
fun OfflineIdenticonBadge(
    title: String,
    website: String,
    type: ItemType,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    shapeRadius: Dp = 10.dp
) {
    val identity = remember(title, website, type) {
        OfflineIdenticonGenerator.resolve(title, website, type)
    }

    val gradient = remember(identity) {
        Brush.linearGradient(listOf(identity.primaryColor.copy(alpha = 0.25f), identity.secondaryColor.copy(alpha = 0.12f)))
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(shapeRadius))
            .background(gradient)
            .border(
                1.dp,
                identity.primaryColor.copy(alpha = 0.35f),
                RoundedCornerShape(shapeRadius)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (identity.icon != null) {
            Icon(
                imageVector = identity.icon,
                contentDescription = title,
                tint = identity.primaryColor,
                modifier = Modifier.size(size * 0.52f)
            )
        } else if (identity.monogram.isNotBlank()) {
            Text(
                text = identity.monogram,
                color = identity.primaryColor,
                fontSize = if (identity.monogram.length > 2) 11.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = title,
                tint = identity.primaryColor,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}

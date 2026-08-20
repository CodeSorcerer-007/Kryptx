package com.kryptx.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.model.IssueSeverity
import com.kryptx.app.core.model.ItemType

@Composable
fun ItemTypeBadge(type: ItemType, modifier: Modifier = Modifier) {
    val (icon, color) = when (type) {
        ItemType.LOGIN -> Pair(Icons.Default.Lock, KryptxBlue)
        ItemType.CREDIT_CARD -> Pair(Icons.Default.CreditCard, KryptxAmber)
        ItemType.IDENTITY -> Pair(Icons.Default.Person, Color(0xFFAB47BC))
        ItemType.SECURE_NOTE -> Pair(Icons.AutoMirrored.Filled.Note, Color(0xFF26A69A))
        ItemType.WIFI -> Pair(Icons.Default.Wifi, Color(0xFF42A5F5))
        ItemType.API_KEY -> Pair(Icons.Default.Key, Color(0xFFFF7043))
        ItemType.BANK_ACCOUNT -> Pair(Icons.Default.CreditCard, Color(0xFF4CAF50))
        ItemType.CRYPTO_WALLET -> Pair(Icons.Default.Key, Color(0xFFFF9800))
        ItemType.SSH_KEY -> Pair(Icons.Default.Key, Color(0xFF7E57C2))
        ItemType.MEDICAL -> Pair(Icons.Default.Person, Color(0xFFEF5350))
        ItemType.CUSTOM -> Pair(Icons.Default.Lock, Color(0xFF78909C))
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = type.displayName,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun SeverityBadge(severity: IssueSeverity, modifier: Modifier = Modifier) {
    val (bgColor, textColor, label) = when (severity) {
        IssueSeverity.CRITICAL -> Triple(KryptxRed.copy(alpha = 0.15f), KryptxRed, "CRITICAL")
        IssueSeverity.WARNING -> Triple(KryptxAmber.copy(alpha = 0.15f), KryptxAmber, "WARNING")
        IssueSeverity.INFO -> Triple(KryptxBlue.copy(alpha = 0.15f), KryptxBlue, "INFO")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun StrengthBadge(strength: EntropyCalculator.StrengthScore, modifier: Modifier = Modifier) {
    val color = when (strength) {
        EntropyCalculator.StrengthScore.VERY_WEAK, EntropyCalculator.StrengthScore.WEAK -> KryptxRed
        EntropyCalculator.StrengthScore.FAIR -> KryptxAmber
        EntropyCalculator.StrengthScore.STRONG, EntropyCalculator.StrengthScore.VERY_STRONG -> KryptxEmerald
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = strength.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

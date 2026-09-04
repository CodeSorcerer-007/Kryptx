package com.kryptx.app.feature.vault.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.model.SecurityIssue
import com.kryptx.app.core.model.IssueType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

@Composable
fun PasswordDnaCard(
    password: String,
    issues: List<SecurityIssue>,
    modifier: Modifier = Modifier
) {
    if (password.isBlank()) return

    val analysis = EntropyCalculator.analyze(password)
    
    val hasLower = password.any { it.isLowerCase() }
    val hasUpper = password.any { it.isUpperCase() }
    val hasDigit = password.any { it.isDigit() }
    val hasSymbol = password.any { !it.isLetterOrDigit() }

    val strengthColor = when (analysis.strength) {
        EntropyCalculator.StrengthScore.VERY_WEAK, EntropyCalculator.StrengthScore.WEAK -> KryptxRed
        EntropyCalculator.StrengthScore.FAIR -> KryptxAmber
        EntropyCalculator.StrengthScore.STRONG, EntropyCalculator.StrengthScore.VERY_STRONG -> KryptxEmerald
    }

    val isReused = issues.any { it.type == IssueType.REUSED_PASSWORD }
    val isSimilar = issues.any { it.type == IssueType.SIMILAR_PASSWORD }
    val isCompromised = issues.any { it.type == IssueType.COMPROMISED }
    
    val issueColor = if (isCompromised) KryptxRed else if (isReused || isSimilar) KryptxAmber else KryptxEmerald

    val issueText = when {
        isCompromised -> "Compromised Password"
        isReused -> "Reused Password"
        isSimilar -> "Similar to another password"
        else -> "Unique Password"
    }

    KryptxCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = 
                    "Password DNA Analysis. ${password.length} characters, ${analysis.entropyBits} bits of entropy. " +
                    "Strength: ${analysis.strength.label}. Crack time: ${analysis.crackTimeDisplay}. " +
                    "Status: $issueText."
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(strengthColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DNA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = strengthColor
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Password DNA",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${password.length} chars • ${analysis.entropyBits} bits entropy",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Strength and Crack Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STRENGTH",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = analysis.strength.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = strengthColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "CRACK TIME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = analysis.crackTimeDisplay,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = strengthColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Animated Entropy Meter Bar
            val entropyRatio = (analysis.entropyBits.toFloat() / 128f).coerceIn(0.05f, 1f)
            val animatedEntropy by androidx.compose.animation.core.animateFloatAsState(
                targetValue = entropyRatio,
                animationSpec = androidx.compose.animation.core.tween(500),
                label = "entropyBar"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedEntropy)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(strengthColor)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Character Composition
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CharTypeChip("a-z", hasLower)
                CharTypeChip("A-Z", hasUpper)
                CharTypeChip("0-9", hasDigit)
                CharTypeChip("!@#", hasSymbol)
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Uniqueness Status
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(issueColor.copy(alpha = 0.1f))
                    .border(1.dp, issueColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCompromised || isReused || isSimilar) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = null,
                        tint = issueColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = issueText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = issueColor
                    )
                }
            }
        }
    }
}

@Composable
private fun CharTypeChip(label: String, present: Boolean) {
    val color = if (present) KryptxBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics {
                contentDescription = "$label characters ${if (present) "present" else "missing"}"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (present) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

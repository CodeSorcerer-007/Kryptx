package com.kryptx.app.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxViolet

data class SecurityConcept(
    val title: String,
    val simpleHeadline: String,
    val technicalTerm: String,
    val plainEnglishExplanation: String,
    val practicalBenefit: String,
    val icon: ImageVector,
    val accentColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityExplainerSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val concepts = remember {
        listOf(
            SecurityConcept(
                title = "Zero-Network Air-Gapped Sandbox",
                simpleHeadline = "Physically impossible to leak or hack remotely",
                technicalTerm = "0 Manifest Network Permissions (No INTERNET)",
                plainEnglishExplanation = "Kryptx completely strips the INTERNET permission from its app code. The Android Linux kernel strictly forbids this app from opening any Wi-Fi or cellular data sockets.",
                practicalBenefit = "Even if a bug or rogue library existed, zero bytes can ever physically leave your phone. There are no cloud servers that can be breached.",
                icon = Icons.Default.WifiOff,
                accentColor = KryptxEmerald
            ),
            SecurityConcept(
                title = "Post-Quantum Cryptography",
                simpleHeadline = "Immune to future quantum supercomputers",
                technicalTerm = "NIST FIPS 203 ML-KEM-768 (Kyber)",
                plainEnglishExplanation = "Standard encryption (like RSA) will be broken when large-scale quantum computers emerge in the next decade. Kryptx wraps its backup and encryption layers with lattice-based post-quantum algorithms.",
                practicalBenefit = "Your encrypted data is protected against 'Harvest Now, Decrypt Later' attacks by foreign state actors or advanced quantum computing systems.",
                icon = Icons.Default.Shield,
                accentColor = KryptxCyan
            ),
            SecurityConcept(
                title = "Memory-Hard Master Key Derivation",
                simpleHeadline = "Makes supercomputers melt trying to guess your password",
                technicalTerm = "Argon2id (RFC 9106) & PBKDF2 (600,000 Rounds)",
                plainEnglishExplanation = "When you enter your password, Kryptx uses a mathematical hashing formula that forces an attacker's computer to allocate massive amounts of physical RAM (16MB+) for every single guess.",
                practicalBenefit = "Specialized cracking rigs with high-end GPUs cannot brute-force your password in parallel because they immediately run out of memory.",
                icon = Icons.Default.VpnKey,
                accentColor = KryptxBlue
            ),
            SecurityConcept(
                title = "Hardware StrongBox & TEE Isolation",
                simpleHeadline = "A physical vault chip inside your smartphone",
                technicalTerm = "AndroidKeyStore / StrongBox Hardware Attestation",
                plainEnglishExplanation = "Your biometric credentials and encryption keys are stored inside a physically separate, tamper-proof hardware chip on your phone (like Google Titan M2 or Samsung Knox Vault).",
                practicalBenefit = "Even if your phone is rooted or malware infects the main Android OS, it cannot extract your cryptographic master keys from the secure hardware chip.",
                icon = Icons.Default.Fingerprint,
                accentColor = KryptxViolet
            ),
            SecurityConcept(
                title = "Volatile RAM Scrubbing & Memory Locking",
                simpleHeadline = "Instant digital amnesia the moment you lock",
                technicalTerm = "Native Memory Locking (`mlock`) & Byte Zeroization",
                plainEnglishExplanation = "When Kryptx decrypts a record in memory, it locks that memory so Android cannot write it to temporary swap disk storage. When you close the app, every byte is actively overwritten with zeros.",
                practicalBenefit = "Forensic memory dumps and rogue spyware inspection cannot recover your credentials from phone RAM after you close the app.",
                icon = Icons.Default.Memory,
                accentColor = KryptxAmber
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(KryptxBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = KryptxBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Security Architecture",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Plain-English explanation of your protection",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Kryptx was built on one sovereign principle: you should never have to surrender trust to a cloud company or third-party server. Here is how your vault mathematically enforces that promise:",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Accordion Cards
            concepts.forEach { concept ->
                SecurityConceptCard(concept = concept)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            KryptxPrimaryButton(
                text = "I Understand",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SecurityConceptCard(concept: SecurityConcept) {
    var isExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = if (isExpanded) concept.accentColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { isExpanded = !isExpanded }
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(concept.accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = concept.icon,
                        contentDescription = null,
                        tint = concept.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = concept.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = concept.simpleHeadline,
                        fontSize = 11.sp,
                        color = concept.accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = concept.technicalTerm,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = concept.plainEnglishExplanation,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(concept.accentColor.copy(alpha = 0.08f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Benefit: ${concept.practicalBenefit}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = concept.accentColor,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

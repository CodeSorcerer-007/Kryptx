package com.kryptx.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import com.kryptx.app.core.designsystem.theme.KryptxEmerald

/**
 * Feature registry containing short, sweet, beginner-friendly introductions
 * for first-time user interactions.
 */
enum class FeatureGuide(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val whatIsIt: String,
    val whyUseIt: String,
    val proTip: String
) {
    VAULT(
        key = "vault_tab",
        title = "Zero-Knowledge Vault",
        subtitle = "Encrypted Offline Fortress",
        icon = Icons.Default.Lock,
        whatIsIt = "Your private, encrypted store for credentials, cards, and sensitive data.",
        whyUseIt = "Protected with military-grade AES-256-GCM. Plaintext is only decrypted in local RAM when unlocked.",
        proTip = "Tap any credential to copy passwords or tokens with automatic 30s clipboard clearing."
    ),
    TOTP(
        key = "totp_tab",
        title = "2FA Authenticator",
        subtitle = "Replace Insecure SMS Codes",
        icon = Icons.Default.Key,
        whatIsIt = "Generates live 6-digit verification codes (TOTP) that cycle every 30 seconds.",
        whyUseIt = "Protects your accounts from SIM-swapping and credential theft without relying on mobile signal.",
        proTip = "Tap the Camera icon in the top right to scan 2FA setup QR codes in 1 second!"
    ),
    GENERATOR(
        key = "generator_tab",
        title = "Credential Generator",
        subtitle = "Unbreakable Random Entropy",
        icon = Icons.Default.AutoAwesome,
        whatIsIt = "Creates cryptographically secure random passwords, passphrases, PINs, and usernames.",
        whyUseIt = "Stops credential stuffing by ensuring every single account has a unique, high-entropy secret.",
        proTip = "Try 'Passphrase' mode for 4-word passphrases that are easy to type yet mathematically uncrackable."
    ),
    SECURITY(
        key = "security_tab",
        title = "Security Health Radar",
        subtitle = "Continuous Automated Audit",
        icon = Icons.Default.Security,
        whatIsIt = "Automated scanner that computes a real-time 0–100 health score for your vault.",
        whyUseIt = "Instantly highlights weak entropy, reused passwords, and public data breach leaks.",
        proTip = "Tap 'Fix Now' on any warning item to rotate and secure vulnerable accounts."
    ),
    SETTINGS(
        key = "settings_tab",
        title = "Settings & Privacy Center",
        subtitle = "Hardware Security & Backups",
        icon = Icons.Default.Settings,
        whatIsIt = "Configure biometric unlock, auto-lock timers, AMOLED dark themes, and encrypted backups.",
        whyUseIt = "Fine-tune app protections to match your personal workflow and hardware capabilities.",
        proTip = "Enable Biometrics for instant fingerprint / face unlock backed by hardware Keystore."
    ),
    CATEGORIES(
        key = "categories_feature",
        title = "11 Vault Categories",
        subtitle = "Tailored For Every Secret",
        icon = Icons.Default.Category,
        whatIsIt = "Dedicated fields for Bank Accounts, Crypto Seed Phrases, SSH Keys, Medical data, and Wi-Fi.",
        whyUseIt = "Structured layouts keep your sensitive recovery seeds and PINs cleanly organized and masked.",
        proTip = "Use the horizontal category bar at the top to filter items instantly."
    ),
    QR_SCANNER(
        key = "qr_scanner_feature",
        title = "Live Camera QR Scanner",
        subtitle = "Offline & Zero-Knowledge",
        icon = Icons.Default.QrCodeScanner,
        whatIsIt = "Real-time camera lens analyzer for scanning 2FA authenticator QR codes.",
        whyUseIt = "Camera frames are decoded strictly in volatile RAM and no images are ever saved or transmitted.",
        proTip = "Hold the camera steady over the QR code for instant auto-capture."
    ),
    DURESS_VAULT(
        key = "duress_vault_feature",
        title = "Duress Password & Decoy Vault",
        subtitle = "Coercion & Panic Protection",
        icon = Icons.Default.Lock,
        whatIsIt = "A secondary password that unlocks a realistic decoy vault containing dummy accounts.",
        whyUseIt = "Protects you under forced unlock situations. Real passwords and keys remain completely isolated and invisible.",
        proTip = "Set up in Settings → Security & Vault Lock."
    ),
    EMERGENCY_KIT(
        key = "emergency_kit_feature",
        title = "Printable Recovery Kit (PDF)",
        subtitle = "Physical Safe Custody",
        icon = Icons.Default.Lock,
        whatIsIt = "Generates a clean vector PDF sheet with vault crypto specs and an offline recovery QR key.",
        whyUseIt = "Allows safe offline physical storage in a home safe or bank deposit box for emergency recovery.",
        proTip = "Print it out and store it offline away from your mobile device."
    ),
    ATTACHMENTS(
        key = "attachments_feature",
        title = "Encrypted Attachments",
        subtitle = "Sandboxed AES-256-GCM Files",
        icon = Icons.Default.Category,
        whatIsIt = "Attach private photos, driver's licenses, passports, or SSH key files directly to vault entries.",
        whyUseIt = "Files are encrypted in chunks and stored inside the app sandbox with on-demand zero-knowledge decryption.",
        proTip = "Tap any attachment to decrypt and open in your preferred viewer."
    ),
    LOCAL_SYNC(
        key = "local_sync_feature",
        title = "Zero-Cloud Local P2P Sync",
        subtitle = "Direct Wi-Fi / Hotspot Beam",
        icon = Icons.Default.Security,
        whatIsIt = "Transfer and synchronize encrypted credentials directly between nearby devices over local Wi-Fi.",
        whyUseIt = "No cloud servers, no intermediate storage, and zero internet connection required.",
        proTip = "Generate a QR code on the sender phone and scan it with the receiver camera."
    )
}

/**
 * Clean, modern, informative bottom sheet explaining a feature on first interaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureIntroSheet(
    feature: FeatureGuide,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            // Header Row with glowing icon badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(KryptxCyan.copy(alpha = 0.15f))
                        .border(1.dp, KryptxCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = feature.title,
                        tint = KryptxCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = feature.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = feature.subtitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KryptxCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // What is it?
            FeatureInfoRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                iconTint = KryptxEmerald,
                title = "WHAT IS IT?",
                description = feature.whatIsIt
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Why use it?
            FeatureInfoRow(
                icon = Icons.Default.CheckCircle,
                iconTint = KryptxCyan,
                title = "WHY USE IT?",
                description = feature.whyUseIt
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pro-Tip Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(KryptxAmber.copy(alpha = 0.10f))
                    .border(1.dp, KryptxAmber.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Pro Tip",
                        tint = KryptxAmber,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "PRO TIP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = KryptxAmber
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = feature.proTip,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            KryptxPrimaryButton(
                text = "Got It, Explore!",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FeatureInfoRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
        }
    }
}

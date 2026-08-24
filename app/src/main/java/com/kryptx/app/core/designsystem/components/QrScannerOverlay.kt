package com.kryptx.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald

@Composable
fun ScannerOverlay(laserPosition: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, KryptxBlue.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offset(y = laserPosition.dp)
                    .background(KryptxEmerald)
            )
        }
    }
}

@Composable
fun CameraPermissionRationale(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFromGallery: () -> Unit,
    showSettingsPrompt: Boolean,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(KryptxBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera Icon",
                    tint = KryptxBlue,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Camera Permission Required",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (showSettingsPrompt) {
                    "Camera access was previously denied or blocked by Android. You can grant access in Settings, or import a QR code screenshot directly from your photo gallery."
                } else {
                    "Kryptx needs camera access to scan 2FA TOTP QR codes. The camera stream is analyzed locally in real-time RAM and no image data is stored or transmitted."
                },
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            KryptxPrimaryButton(
                text = "Pick QR from Photo Gallery",
                containerColor = KryptxEmerald,
                contentColor = Color(0xFF0A0F1A),
                onClick = onPickFromGallery,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = Color(0xFF0A0F1A),
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (showSettingsPrompt) {
                KryptxPrimaryButton(
                    text = "Open App Settings",
                    containerColor = KryptxBlue,
                    contentColor = Color.White,
                    onClick = onOpenSettings,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                KryptxOutlinedButton(
                    text = "Try Camera Prompt Again",
                    borderColor = KryptxBlue.copy(alpha = 0.5f),
                    textColor = Color.White,
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                KryptxPrimaryButton(
                    text = "Grant Camera Permission",
                    containerColor = KryptxBlue,
                    contentColor = Color.White,
                    onClick = onRequestPermission,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                KryptxOutlinedButton(
                    text = "Open App Settings",
                    borderColor = KryptxBlue.copy(alpha = 0.5f),
                    textColor = Color.White,
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            KryptxOutlinedButton(
                text = "Cancel",
                borderColor = Color.White.copy(alpha = 0.2f),
                textColor = Color.White.copy(alpha = 0.7f),
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

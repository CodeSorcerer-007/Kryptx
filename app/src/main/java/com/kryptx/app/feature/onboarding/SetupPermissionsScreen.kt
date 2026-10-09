package com.kryptx.app.feature.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kryptx.app.KryptxApplication
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.KryptxLogo
import com.kryptx.app.core.designsystem.components.KryptxPermissionRationaleDialog
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.components.findActivity
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import com.kryptx.app.core.designsystem.theme.KryptxShapes
import com.kryptx.app.core.security.NfcHardwareKeyManager

/**
 * In-app permission onboarding screen — shows a rationale dialog (with privacy guarantees)
 * before launching any OS system permission dialog or settings deeplink.
 *
 * Flow per permission:
 *  1. User taps "Grant Permission" button
 *  2. [KryptxPermissionRationaleDialog] is shown explaining WHY and confirming offline privacy
 *  3. User taps "Continue" → OS system dialog fires
 *  4. If previously denied permanently → Settings deeplink shown directly
 *
 * On Android 13+ (API 33) POST_NOTIFICATIONS is also surfaced here since Kryptx uses
 * a local AlarmManager notification to remind the user when the clipboard auto-clears.
 */
@Composable
fun SetupPermissionsScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val app = context.applicationContext as? KryptxApplication
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }

    // ── Permission states ────────────────────────────────────────────────────
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true // Permission not required below Android 13
        )
    }

    val requiresNotificationPermission = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    // Track whether we've fired the OS dialog at least once (for permanent-denial detection)
    var notificationPermissionRequested by remember { mutableStateOf(false) }

    // True once the user has permanently denied notifications (system won't show dialog again)
    val isNotificationPermanentlyDenied = requiresNotificationPermission &&
            notificationPermissionRequested && activity?.let {
        !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
    } ?: false

    var isAutofillEnabled by remember {
        mutableStateOf(
            context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
        )
    }

    val hasBiometrics = remember {
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        canAuth == BiometricManager.BIOMETRIC_SUCCESS || canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    }

    val hasNfcHardware = remember { NfcHardwareKeyManager.hasNfc(context) }

    var isNfcActive by remember { mutableStateOf(NfcHardwareKeyManager.isNfcEnabled(context)) }

    // ── Rationale dialog state ───────────────────────────────────────────────
    var showCameraRationale by remember { mutableStateOf(false) }
    var showNotificationRationale by remember { mutableStateOf(false) }
    var showAutofillRationale by remember { mutableStateOf(false) }

    // ── Permission launchers ─────────────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        app?.sessionManager?.setPickerActive(false)
        hasCameraPermission = granted
        if (granted) KryptxHaptics.confirm(view)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        app?.sessionManager?.setPickerActive(false)
        hasNotificationPermission = granted
        notificationPermissionRequested = true
        if (granted) KryptxHaptics.confirm(view)
    }

    // Refresh all states whenever the app returns from a system settings deeplink
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }

                isAutofillEnabled = context.getSystemService(AutofillManager::class.java)
                    ?.hasEnabledAutofillServices() == true
                isNfcActive = NfcHardwareKeyManager.isNfcEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(onBack = onBack)

    // Settings deeplinks
    val openAppSettings: () -> Unit = {
        app?.sessionManager?.setPickerActive(true)
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) { app?.sessionManager?.setPickerActive(false) }
    }

    val openAutofillSettings: () -> Unit = {
        app?.sessionManager?.setPickerActive(true)
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE))
            } catch (_: Exception) { app?.sessionManager?.setPickerActive(false) }
        }
    }

    val openNfcSettings: () -> Unit = {
        try {
            context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}
    }

    // ── Rationale dialogs ────────────────────────────────────────────────────
    if (showCameraRationale) {
        KryptxPermissionRationaleDialog(
            icon = Icons.Default.CameraAlt,
            title = "Camera Access",
            description = "Kryptx uses the camera exclusively to scan 2FA QR codes and capture encrypted attachment photos. Frames are processed in volatile RAM and never written to external storage.",
            confirmButtonText = "Grant Camera Access",
            dismissButtonText = "Not Now",
            onConfirm = {
                showCameraRationale = false
                app?.sessionManager?.setPickerActive(true)
                cameraLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { showCameraRationale = false }
        )
    }

    if (showNotificationRationale) {
        KryptxPermissionRationaleDialog(
            icon = Icons.Default.Notifications,
            title = "Allow Notifications",
            description = "Kryptx shows a local-only notification when it auto-clears your clipboard after a copy. No push servers, no remote alerts — strictly local OS notifications on this device.",
            privacyGuarantee = "100% Offline: All notifications are generated locally. Zero network calls are ever made.",
            confirmButtonText = "Allow Notifications",
            dismissButtonText = "Not Now",
            onConfirm = {
                showNotificationRationale = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (isNotificationPermanentlyDenied) {
                        // OS dialog won't appear again — send user to system settings
                        openAppSettings()
                    } else {
                        app?.sessionManager?.setPickerActive(true)
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            },
            onDismiss = { showNotificationRationale = false }
        )
    }

    if (showAutofillRationale) {
        KryptxPermissionRationaleDialog(
            icon = Icons.Default.AutoAwesome,
            title = "1-Tap Android Autofill",
            description = "This will open Android Settings where you can set Kryptx as your autofill provider. Kryptx fills credentials inside Chrome, Firefox, and native apps. All matching happens 100% on-device.",
            privacyGuarantee = "No data leaves your device. Autofill matching is done offline in the app sandbox.",
            confirmButtonText = "Open Autofill Settings",
            dismissButtonText = "Not Now",
            onConfirm = {
                showAutofillRationale = false
                openAutofillSettings()
            },
            onDismiss = { showAutofillRationale = false }
        )
    }

    // All required (non-optional) permissions are satisfied
    val allCriticalGranted = hasCameraPermission &&
            (!requiresNotificationPermission || hasNotificationPermission)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("setup_permissions_screen")
            .atmosphericTopGlow(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            KryptxLogo(size = 72.dp, showGlow = true)

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "System Setup & Permissions",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Configure device hardware and Android integrations. Everything runs 100% offline with zero cloud telemetry.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )

            // ── 1. Camera ──────────────────────────────────────────────────────
            SetupPermissionItemCard(
                title = "Camera Access",
                badgeText = if (hasCameraPermission) "Granted & Active" else "Permission Required",
                isPositive = hasCameraPermission,
                icon = Icons.Default.CameraAlt,
                description = "Used exclusively in RAM to scan 2FA TOTP QR codes and capture encrypted document attachments. Zero unencrypted photos ever touch external storage.",
                action = {
                    if (!hasCameraPermission) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KryptxPrimaryButton(
                                text = "Grant Permission",
                                onClick = { showCameraRationale = true },
                                height = 40.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("grant_camera_permission_button")
                            )
                            OutlinedButton(
                                onClick = openAppSettings,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open system settings",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Settings", fontSize = 12.sp)
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 2. Notifications (Android 13+) ─────────────────────────────────
            if (requiresNotificationPermission) {
                SetupPermissionItemCard(
                    title = "Local Notifications",
                    badgeText = if (hasNotificationPermission) "Granted & Active" else "Recommended",
                    isPositive = hasNotificationPermission,
                    icon = Icons.Default.Notifications,
                    description = "Allows Kryptx to show a local reminder when clipboard auto-clear fires. Zero remote notifications, push servers, or analytics — strictly on-device.",
                    action = {
                        if (!hasNotificationPermission) {
                            KryptxPrimaryButton(
                                text = if (isNotificationPermanentlyDenied) "Open App Settings" else "Allow Notifications",
                                onClick = {
                                    if (isNotificationPermanentlyDenied) {
                                        openAppSettings()
                                    } else {
                                        showNotificationRationale = true
                                    }
                                },
                                height = 40.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("grant_notification_permission_button")
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── 3. Biometric ───────────────────────────────────────────────────
            SetupPermissionItemCard(
                title = "Biometric Authentication",
                badgeText = if (hasBiometrics) "Hardware Enclave Ready" else "Device Unsupported",
                isPositive = hasBiometrics,
                icon = Icons.Default.Fingerprint,
                description = "Protected by StrongBox / TEE hardware isolation. You will enroll fingerprint or facial unlock right after setting your master password.",
                action = null
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 4. Autofill ────────────────────────────────────────────────────
            SetupPermissionItemCard(
                title = "1-Tap Android Autofill",
                badgeText = if (isAutofillEnabled) "Active Provider" else "Optional Setup",
                isPositive = isAutofillEnabled,
                icon = Icons.Default.AutoAwesome,
                description = "Allows Kryptx to securely auto-fill your credentials inside Chrome, Firefox, and third-party native apps with 1 tap.",
                action = {
                    if (!isAutofillEnabled) {
                        KryptxPrimaryButton(
                            text = "Enable Kryptx Autofill",
                            onClick = { showAutofillRationale = true },
                            height = 40.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enable_autofill_button")
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 5. NFC Hardware Security Keys ─────────────────────────────────
            if (hasNfcHardware) {
                SetupPermissionItemCard(
                    title = "NFC Security Keys",
                    badgeText = if (isNfcActive) "NFC Active" else "NFC Disabled",
                    isPositive = isNfcActive,
                    icon = Icons.Default.VpnKey,
                    description = "Authenticate or unlock your vault by tapping physical YubiKey or FIDO2 NFC hardware tokens on your phone.",
                    action = {
                        if (!isNfcActive) {
                            OutlinedButton(
                                onClick = openNfcSettings,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Turn On NFC in Settings", fontSize = 12.sp)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── 6. Zero-Network Guarantee ─────────────────────────────────────
            SetupPermissionItemCard(
                title = "Zero-Network Security",
                badgeText = "100% Air-Gapped",
                isPositive = true,
                icon = Icons.Default.Shield,
                description = "Kryptx declares 0 internet permissions in its Android manifest. The OS guarantees complete network isolation with zero outbound sockets.",
                action = null
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Bottom CTA ─────────────────────────────────────────────────────
            if (!allCriticalGranted) {
                KryptxPrimaryButton(
                    text = "Grant OS Permissions",
                    containerColor = KryptxBlue,
                    contentColor = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_grant_all_permissions_button"),
                    onClick = {
                        // Show rationale for first ungranted required permission
                        when {
                            !hasCameraPermission -> showCameraRationale = true
                            requiresNotificationPermission && !hasNotificationPermission ->
                                if (isNotificationPermanentlyDenied) openAppSettings()
                                else showNotificationRationale = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = onContinue,
                    modifier = Modifier.testTag("skip_permissions_button")
                ) {
                    Text(
                        text = "Skip for Now & Continue to Master Key →",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                KryptxPrimaryButton(
                    text = "Continue to Master Key →",
                    containerColor = KryptxBlue,
                    contentColor = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_continue_button"),
                    onClick = onContinue
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SetupPermissionItemCard(
    title: String,
    badgeText: String,
    isPositive: Boolean,
    icon: ImageVector,
    description: String,
    action: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(KryptxShapes.CardMedium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), KryptxShapes.CardMedium)
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPositive) KryptxEmerald.copy(alpha = 0.15f) else KryptxBlue.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isPositive) KryptxEmerald else KryptxBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isPositive) KryptxEmerald.copy(alpha = 0.15f) else KryptxAmber.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPositive) KryptxEmerald else KryptxAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )

            if (action != null) {
                Spacer(modifier = Modifier.height(10.dp))
                action()
            }
        }
    }
}

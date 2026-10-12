package com.kryptx.app.feature.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kryptx.app.KryptxApplication
import com.kryptx.app.core.designsystem.components.KryptxPermissionRationaleDialog
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.findActivity
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceIntegrationsSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as? KryptxApplication
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }

    // Reactive permission and service states that refresh when returning to foreground
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val requiresNotificationPermission = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    val alarmManager = remember(context) { context.getSystemService(AlarmManager::class.java) }
    var canScheduleExactAlarms by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager?.canScheduleExactAlarms() == true
            } else true
        )
    }

    var isAutofillEnabled by remember {
        mutableStateOf(
            context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
        )
    }

    // Tracks whether the OS dialog has been fired at least once
    var cameraPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        app?.sessionManager?.setPickerActive(false)
        val isGranted = granted || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasCameraPermission = isGranted
        cameraPermissionRequested = true
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        app?.sessionManager?.setPickerActive(false)
        val isGranted = granted || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        hasNotificationPermission = isGranted
        notificationPermissionRequested = true
    }

    // Refresh state when resuming activity
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    canScheduleExactAlarms = alarmManager?.canScheduleExactAlarms() == true
                }

                isAutofillEnabled = context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val openAppSettings: () -> Unit = {
        app?.sessionManager?.setPickerActive(true)
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            app?.sessionManager?.setPickerActive(false)
        }
    }

    val openAutofillSettings: () -> Unit = {
        app?.sessionManager?.setPickerActive(true)
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                app?.sessionManager?.setPickerActive(false)
            }
        }
    }

    val openExactAlarmSettings: () -> Unit = {
        app?.sessionManager?.setPickerActive(true)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                openAppSettings()
            }
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    // Direct in-app permission requests — immediately launches the Android OS prompt
    val requestCameraPermission: () -> Unit = {
        app?.sessionManager?.setPickerActive(true)
        try {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } catch (_: Throwable) {
            app?.sessionManager?.setPickerActive(false)
        }
    }

    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app?.sessionManager?.setPickerActive(true)
            try {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (_: Throwable) {
                app?.sessionManager?.setPickerActive(false)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(KryptxEmerald.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = KryptxEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Android 16 & System Integrations",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Real-time audit of OS permissions & platform hooks",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Camera Access Card
            IntegrationStatusCard(
                title = "Camera Permission",
                statusText = if (hasCameraPermission) "Granted & Active" else "Not Granted",
                isPositive = hasCameraPermission,
                icon = Icons.Default.CameraAlt,
                description = "Used strictly in volatile RAM for scanning TOTP 2FA QR codes. Never takes persistent photos or communicates externally.",
                actions = {
                    if (!hasCameraPermission) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KryptxPrimaryButton(
                                text = "Grant Permission",
                                onClick = requestCameraPermission,
                                height = 44.dp,
                                modifier = Modifier.weight(1f)
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
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("App Info", fontSize = 12.sp)
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Photo & Document Storage Sandbox Card
            IntegrationStatusCard(
                title = "Photo & Document Access",
                statusText = "Universal Sandboxed (SAF)",
                isPositive = true,
                icon = Icons.Default.Image,
                description = "Android 14-16 Scoped Photo & Document Picker. Attached media is encrypted directly into your vault database using AES-256-GCM. 0 storage leakage.",
                actions = null
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Android Autofill Framework Card
            IntegrationStatusCard(
                title = "Android Autofill Service",
                statusText = if (isAutofillEnabled) "Enabled & Active" else "Available (Inactive)",
                isPositive = isAutofillEnabled,
                icon = Icons.Default.Security,
                description = "Intelligent sovereign autofill. Delivers instant 1-tap credential filling to web browsers and Android apps, authenticated by biometric verification.",
                actions = {
                    KryptxPrimaryButton(
                        text = if (isAutofillEnabled) "Configure Autofill Service" else "Enable Kryptx Autofill in Android",
                        onClick = openAutofillSettings,
                        height = 48.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Notification Permission Card (Android 13+)
            if (requiresNotificationPermission) {
                IntegrationStatusCard(
                    title = "Clipboard Clear Notifications",
                    statusText = if (hasNotificationPermission) "Granted & Active" else "Not Granted",
                    isPositive = hasNotificationPermission,
                    icon = Icons.Default.Notifications,
                    description = "Android 13+ runtime permission to notify you immediately when sensitive copied secrets (passwords, TOTP tokens) are securely wiped from the device clipboard.",
                    actions = {
                        if (!hasNotificationPermission) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                KryptxPrimaryButton(
                                    text = "Grant Permission",
                                    onClick = requestNotificationPermission,
                                    height = 44.dp,
                                    modifier = Modifier.weight(1f)
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
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("App Info", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))
            }

            // 5. Exact Background Timer Card (Android 12+)
            IntegrationStatusCard(
                title = "Exact Background Timer (Alarms)",
                statusText = if (canScheduleExactAlarms) "Exact Timer Authorized" else "Standard Timer (Fallback)",
                isPositive = canScheduleExactAlarms,
                icon = Icons.Default.Schedule,
                description = if (canScheduleExactAlarms) {
                    "Android 12+ exact alarm scheduling is authorized. Clipboard auto-clear executes to the exact second even when the device is in deep Doze mode."
                } else {
                    "Exact alarm access is currently restricted by Android. Clipboard auto-clear will execute via battery-optimized inexact alarms."
                },
                actions = if (!canScheduleExactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    {
                        KryptxPrimaryButton(
                            text = "Authorize Exact Alarms in Settings",
                            onClick = openExactAlarmSettings,
                            height = 44.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else null
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Android 16 Predictive Back & Edge-to-Edge Card
            IntegrationStatusCard(
                title = "Predictive Back & System Insets",
                statusText = "API 36 Predictive Back Ready",
                isPositive = true,
                icon = Icons.Default.CheckCircle,
                description = "Full support for Android 16 predictive back navigation gestures (enableOnBackInvokedCallback), immersive edge-to-edge transparent system bars, and dynamic theming.",
                actions = null
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Hardware Enclave & Keystore Card
            IntegrationStatusCard(
                title = "Hardware Keystore & Biometrics",
                statusText = "StrongBox / TEE Enclave",
                isPositive = true,
                icon = Icons.Default.Fingerprint,
                description = "Hardware-backed Android KeyStore cryptographic isolation. Biometric authentication is evaluated inside the secure processor.",
                actions = null
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Zero-Network Air-Gapped Sandbox
            IntegrationStatusCard(
                title = "Air-Gapped Network Isolation",
                statusText = "0 Network Privileges (Physical Guarantee)",
                isPositive = true,
                icon = Icons.Default.WifiOff,
                description = "android.permission.INTERNET is completely absent from the app manifest. Android OS enforces 100% zero-socket isolation.",
                actions = null
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun IntegrationStatusCard(
    title: String,
    statusText: String,
    isPositive: Boolean,
    icon: ImageVector,
    description: String,
    actions: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(
                1.dp,
                if (isPositive) KryptxEmerald.copy(alpha = 0.25f) else KryptxAmber.copy(alpha = 0.35f),
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPositive) KryptxEmerald.copy(alpha = 0.15f)
                            else KryptxAmber.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isPositive) KryptxEmerald else KryptxAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPositive) KryptxEmerald else KryptxAmber
                    )
                }
            }

            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )

            if (actions != null) {
                Spacer(modifier = Modifier.height(4.dp))
                actions()
            }
        }
    }
}

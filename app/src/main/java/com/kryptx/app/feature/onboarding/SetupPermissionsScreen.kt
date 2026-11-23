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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kryptx.app.feature.onboarding.components.OnboardingStepIndicator
import com.kryptx.app.feature.onboarding.components.SetupPermissionItemCard

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

    // Track whether we've fired the OS dialog at least once
    var cameraPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }

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

    // ── Rationale dialog state (only for system settings that require external dispatch) ──
    var showAutofillRationale by remember { mutableStateOf(false) }
    var showCameraBlockedRationale by remember { mutableStateOf(false) }
    var showNotificationBlockedRationale by remember { mutableStateOf(false) }

    // ── Permission launchers ─────────────────────────────────────────────────
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        app?.sessionManager?.setPickerActive(false)
        val isGranted = granted || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasCameraPermission = isGranted
        cameraPermissionRequested = true
        if (isGranted) {
            KryptxHaptics.confirm(view)
        } else {
            val permanentlyDenied = activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
            if (permanentlyDenied) {
                showCameraBlockedRationale = true
            }
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        app?.sessionManager?.setPickerActive(false)
        val isGranted = granted || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        hasNotificationPermission = isGranted
        notificationPermissionRequested = true
        if (isGranted) {
            KryptxHaptics.confirm(view)
        } else {
            val permanentlyDenied = activity?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
                } else false
            } ?: false
            if (permanentlyDenied) {
                showNotificationBlockedRationale = true
            }
        }
    }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        app?.sessionManager?.setPickerActive(false)
        val cameraGranted = results[Manifest.permission.CAMERA] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasCameraPermission = cameraGranted
        cameraPermissionRequested = true

        if (requiresNotificationPermission) {
            val notifGranted = results[Manifest.permission.POST_NOTIFICATIONS] == true ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            hasNotificationPermission = notifGranted
            notificationPermissionRequested = true
        }

        if (hasCameraPermission || hasNotificationPermission) {
            KryptxHaptics.confirm(view)
        } else {
            val cameraPermanentlyDenied = activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
            if (cameraPermanentlyDenied) {
                showCameraBlockedRationale = true
            }
        }
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

    val isCameraPermanentlyDenied = cameraPermissionRequested && activity?.let {
        !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
    } ?: false

    val isNotificationPermanentlyDenied = notificationPermissionRequested && activity?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } else false
    } ?: false

    // Direct in-app permission requests — immediately fires native Android OS prompt
    val requestCameraPermission: () -> Unit = {
        if (isCameraPermanentlyDenied) {
            showCameraBlockedRationale = true
        } else {
            app?.sessionManager?.setPickerActive(true)
            try {
                cameraLauncher.launch(Manifest.permission.CAMERA)
            } catch (_: Throwable) {
                app?.sessionManager?.setPickerActive(false)
                showCameraBlockedRationale = true
            }
        }
    }

    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isNotificationPermanentlyDenied) {
                showNotificationBlockedRationale = true
            } else {
                app?.sessionManager?.setPickerActive(true)
                try {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (_: Throwable) {
                    app?.sessionManager?.setPickerActive(false)
                    showNotificationBlockedRationale = true
                }
            }
        }
    }

    val requestAllPermissions: () -> Unit = {
        if (isCameraPermanentlyDenied && (!requiresNotificationPermission || isNotificationPermanentlyDenied)) {
            openAppSettings()
        } else {
            val permissionsToRequest = mutableListOf<String>()
            if (!hasCameraPermission) {
                permissionsToRequest.add(Manifest.permission.CAMERA)
            }
            if (requiresNotificationPermission && !hasNotificationPermission) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (permissionsToRequest.isNotEmpty()) {
                app?.sessionManager?.setPickerActive(true)
                try {
                    multiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
                } catch (_: Throwable) {
                    app?.sessionManager?.setPickerActive(false)
                    openAppSettings()
                }
            }
        }
    }

    if (showCameraBlockedRationale) {
        KryptxPermissionRationaleDialog(
            icon = Icons.Default.CameraAlt,
            title = "Camera Permission Required",
            description = "Camera permission has been blocked by Android because it was previously declined. To scan QR codes and attach documents, please enable Camera access in App Settings.",
            privacyGuarantee = "Kryptx works 100% offline. Zero photos or videos ever leave your device.",
            confirmButtonText = "Open App Settings",
            dismissButtonText = "Dismiss",
            onConfirm = {
                showCameraBlockedRationale = false
                openAppSettings()
            },
            onDismiss = { showCameraBlockedRationale = false }
        )
    }

    if (showNotificationBlockedRationale) {
        KryptxPermissionRationaleDialog(
            icon = Icons.Default.Notifications,
            title = "Notification Permission Required",
            description = "Notification permission has been blocked by Android. To receive local clipboard auto-clear reminders, please enable Notifications in App Settings.",
            privacyGuarantee = "Zero remote notifications or push servers are used — all reminders are strictly local.",
            confirmButtonText = "Open App Settings",
            dismissButtonText = "Dismiss",
            onConfirm = {
                showNotificationBlockedRationale = false
                openAppSettings()
            },
            onDismiss = { showNotificationBlockedRationale = false }
        )
    }

    if (showAutofillRationale) {
        KryptxPermissionRationaleDialog(
            icon = Icons.Default.Password,
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!allCriticalGranted) {
                        KryptxPrimaryButton(
                            text = "Grant In-App Permissions",
                            containerColor = KryptxBlue,
                            contentColor = Color.White,
                            height = 46.dp,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_grant_all_permissions_button"),
                            onClick = requestAllPermissions
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        TextButton(
                            onClick = onContinue,
                            modifier = Modifier.testTag("skip_permissions_button")
                        ) {
                            Text(
                                text = "Skip for Now & Continue to Master Key →",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        KryptxPrimaryButton(
                            text = "Continue to Master Key →",
                            containerColor = KryptxBlue,
                            contentColor = Color.White,
                            height = 46.dp,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_continue_button"),
                            onClick = onContinue
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            KryptxLogo(size = 48.dp, showGlow = true)

            Spacer(modifier = Modifier.height(6.dp))

            OnboardingStepIndicator(
                currentStep = 2,
                totalSteps = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "System Setup & Permissions",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Configure device hardware and Android integrations. Everything runs 100% offline with zero cloud telemetry.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                lineHeight = 17.sp,
                textAlign = TextAlign.Center
            )

            // ── 1. Camera ──────────────────────────────────────────────────────
            SetupPermissionItemCard(
                title = "Camera Access",
                badgeText = if (hasCameraPermission) "Granted" else "Required",
                isPositive = hasCameraPermission,
                icon = Icons.Default.CameraAlt,
                description = "Used exclusively in RAM to scan 2FA TOTP QR codes and capture encrypted document attachments. Zero unencrypted photos ever touch external storage.",
                action = {
                    if (!hasCameraPermission) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            KryptxPrimaryButton(
                                text = "Grant Permission",
                                onClick = requestCameraPermission,
                                height = 38.dp,
                                fontSize = 13.sp,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("grant_camera_permission_button")
                            )
                            OutlinedButton(
                                onClick = openAppSettings,
                                shape = RoundedCornerShape(19.dp),
                                modifier = Modifier.height(38.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open system settings",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 2. Notifications (Android 13+) ─────────────────────────────────
            if (requiresNotificationPermission) {
                SetupPermissionItemCard(
                    title = "Local Notifications",
                    badgeText = if (hasNotificationPermission) "Active" else "Recommended",
                    isPositive = hasNotificationPermission,
                    icon = Icons.Default.Notifications,
                    description = "Allows Kryptx to show a local reminder when clipboard auto-clear fires. Zero remote notifications, push servers, or analytics — strictly on-device.",
                    action = {
                        if (!hasNotificationPermission) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                KryptxPrimaryButton(
                                    text = "Allow Notifications",
                                    onClick = requestNotificationPermission,
                                    height = 38.dp,
                                    fontSize = 13.sp,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("grant_notification_permission_button")
                                )
                                OutlinedButton(
                                    onClick = openAppSettings,
                                    shape = RoundedCornerShape(19.dp),
                                    modifier = Modifier.height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open system settings",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── 3. Biometric ───────────────────────────────────────────────────
            SetupPermissionItemCard(
                title = "Biometric Authentication",
                badgeText = if (hasBiometrics) "Enclave Ready" else "Unsupported",
                isPositive = hasBiometrics,
                icon = Icons.Default.Fingerprint,
                description = "Protected by StrongBox / TEE hardware isolation. You will enroll fingerprint or facial unlock right after setting your master password.",
                action = null
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 4. Autofill ────────────────────────────────────────────────────
            SetupPermissionItemCard(
                title = "1-Tap Android Autofill",
                badgeText = if (isAutofillEnabled) "Active" else "Optional",
                isPositive = isAutofillEnabled,
                icon = Icons.Default.Password,
                description = "Allows Kryptx to securely auto-fill your credentials inside Chrome, Firefox, and third-party native apps with 1 tap.",
                action = {
                    if (!isAutofillEnabled) {
                        KryptxPrimaryButton(
                            text = "Enable Kryptx Autofill",
                            onClick = { showAutofillRationale = true },
                            height = 38.dp,
                            fontSize = 13.sp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enable_autofill_button")
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 5. Zero-Network Guarantee ─────────────────────────────────────
            SetupPermissionItemCard(
                title = "Zero-Network Security",
                badgeText = "Air-Gapped",
                isPositive = true,
                icon = Icons.Default.Shield,
                description = "Kryptx declares 0 internet permissions in its Android manifest. The OS guarantees complete network isolation with zero outbound sockets.",
                action = null
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

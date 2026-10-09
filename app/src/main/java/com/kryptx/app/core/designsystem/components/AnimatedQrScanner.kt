package com.kryptx.app.core.designsystem.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A continuous QR scanner composable meant to be embedded directly in a screen
 * for Animated QR Code scanning. It does not close itself upon a successful scan.
 *
 * Permission flow:
 *  1. If CAMERA is already granted → show the live viewfinder immediately.
 *  2. If not granted → show [KryptxPermissionRationaleDialog] explaining why.
 *  3. User confirms → OS system dialog fires.
 *  4. Permanently denied (shouldShowRationale = false after a prior request) →
 *     show an informational UI with a "Open App Settings" button instead.
 */
@Composable
fun AnimatedQrScanner(
    onFrameScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }
    val app = remember(context) { context.applicationContext as? com.kryptx.app.KryptxApplication }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Tracks whether we have already fired the OS dialog at least once this composition
    var permissionRequested by remember { mutableStateOf(false) }
    // Controls showing the in-app rationale dialog before the OS system dialog
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        app?.sessionManager?.setPickerActive(false)
        hasCameraPermission = isGranted
        permissionRequested = true
    }

    // Helper: whether the system will show the OS dialog again or has permanently silenced it
    val isPermanentlyDenied = permissionRequested && activity?.let {
        !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
    } ?: false

    // In-app rationale dialog — shown before the OS system dialog
    if (showRationale) {
        KryptxPermissionRationaleDialog(
            icon = Icons.Default.CameraAlt,
            title = "Camera Access",
            description = "Kryptx uses the camera to scan TOTP 2FA QR codes. Frames are processed in volatile RAM and never written to storage.",
            confirmButtonText = "Grant Camera Access",
            dismissButtonText = "Not Now",
            onConfirm = {
                showRationale = false
                try {
                    app?.sessionManager?.setPickerActive(true)
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                } catch (e: Throwable) {
                    app?.sessionManager?.setPickerActive(false)
                    android.util.Log.e("AnimatedQrScanner", "Failed to launch camera permission", e)
                }
            },
            onDismiss = { showRationale = false }
        )
    }

    if (hasCameraPermission) {
        val scope = rememberCoroutineScope()
        val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
        val zxingReader = remember {
            MultiFormatReader().apply {
                setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
            }
        }

        // To prevent spamming the decoder with the same frame, keep track of last scanned
        var lastScannedFrame by remember { mutableStateOf<String?>(null) }
        var lastScannedTime by remember { mutableLongStateOf(0L) }

        DisposableEffect(Unit) {
            onDispose {
                cameraExecutor.shutdown()
            }
        }

        Box(modifier = modifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        val result = decodeQrCode(imageProxy, zxingReader)
                                        if (!result.isNullOrBlank()) {
                                            val now = System.currentTimeMillis()
                                            // Only emit if it's a different frame or 500ms have passed (to re-trigger if needed)
                                            if (result != lastScannedFrame || (now - lastScannedTime > 500)) {
                                                lastScannedFrame = result
                                                lastScannedTime = now
                                                scope.launch(Dispatchers.Main) {
                                                    onFrameScanned(result)
                                                }
                                            }
                                        }
                                        imageProxy.close()
                                    }
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("AnimatedQrScanner", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )
        }
    } else if (isPermanentlyDenied) {
        // Permanently denied — guide the user to system settings
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Camera permission required",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Camera access was denied. Open App Settings to grant it.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("Open App Settings")
                }
            }
        }
    } else {
        // Not yet granted, not permanently denied — show the in-app rationale prompt
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Camera access needed",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap below to grant camera access for scanning QR codes.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                KryptxPrimaryButton(
                    text = "Grant Camera Access",
                    onClick = { showRationale = true },
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
            }
        }
    }
}

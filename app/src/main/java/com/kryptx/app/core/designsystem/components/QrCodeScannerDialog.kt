package com.kryptx.app.core.designsystem.components

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxEmerald
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * High-performance, offline-first zero-knowledge CameraX QR Code scanner with
 * runtime camera permission handling, bulletproof lifecycle binding, and real-time ZXing stream decoding.
 */
@Composable
fun QrCodeScannerDialog(
    onDismiss: () -> Unit,
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                CameraPreviewWithScanner(
                    onQrCodeScanned = onQrCodeScanned,
                    onClose = onDismiss
                )
            } else {
                CameraPermissionRationale(
                    onRequestPermission = {
                        try {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        } catch (e: Throwable) {
                            android.util.Log.e("QrScanner", "Permission launch failed", e)
                        }
                    },
                    onClose = onDismiss
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewWithScanner(
    onQrCodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val fallbackLifecycleOwner = LocalLifecycleOwner.current
    val hostLifecycleOwner: LifecycleOwner = remember(context, fallbackLifecycleOwner) {
        context.findActivity() ?: fallbackLifecycleOwner
    }

    var isTorchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var hasScanned by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraExecutor.shutdown()
            } catch (_: Throwable) {}
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 240f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_position"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraError == null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }

                    try {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                if (hostLifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                                    return@addListener
                                }

                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                val multiFormatReader = MultiFormatReader().apply {
                                    setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                                }

                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    try {
                                        if (!hasScanned) {
                                            val scannedText = decodeQrCode(imageProxy, multiFormatReader)
                                            if (!scannedText.isNullOrBlank()) {
                                                hasScanned = true
                                                ContextCompat.getMainExecutor(ctx).execute {
                                                    onQrCodeScanned(scannedText)
                                                }
                                            }
                                        }
                                    } catch (_: Throwable) {
                                        // Ignore transient frame analysis errors
                                    } finally {
                                        try {
                                            imageProxy.close()
                                        } catch (_: Throwable) {}
                                    }
                                }

                                cameraProvider.unbindAll()

                                val hasBack = try { cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) } catch (_: Throwable) { false }
                                val hasFront = try { cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) } catch (_: Throwable) { false }

                                val cameraSelector = when {
                                    hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                                    hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                                    else -> null
                                }

                                if (cameraSelector == null) {
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        cameraError = "No camera hardware detected on this device."
                                    }
                                    return@addListener
                                }

                                val boundCamera = try {
                                    cameraProvider.bindToLifecycle(
                                        hostLifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (bindErr: Throwable) {
                                    android.util.Log.e("QrScanner", "bindToLifecycle failed", bindErr)
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        cameraError = "Camera binding error: ${bindErr.localizedMessage ?: "Unknown error"}"
                                    }
                                    null
                                }
                                camera = boundCamera
                            } catch (e: Throwable) {
                                android.util.Log.e("QrScanner", "Camera initialization failed", e)
                                ContextCompat.getMainExecutor(ctx).execute {
                                    cameraError = "Unable to access camera hardware: ${e.localizedMessage ?: "Unknown error"}"
                                }
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    } catch (e: Throwable) {
                        android.util.Log.e("QrScanner", "ProcessCameraProvider error", e)
                        cameraError = "Camera provider unavailable."
                    }

                    previewView
                }
            )
        } else {
            // Error State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = KryptxAmber,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Unavailable",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = cameraError ?: "Unknown camera error occurred",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Overlay Viewfinder Layer
        ScannerOverlay(laserPosition = laserPosition)

        // Top bar actions: Close and Torch toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Camera",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                IconButton(
                    onClick = {
                        val next = !isTorchOn
                        camera?.cameraControl?.enableTorch(next)
                        isTorchOn = next
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isTorchOn) KryptxAmber.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Torch",
                        tint = if (isTorchOn) Color.Black else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bottom instruction badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = KryptxEmerald,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Align QR Code inside target window",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ScannerOverlay(laserPosition: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Center Scan Box
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, KryptxBlue.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
        ) {
            // Animated Scanning Laser Line
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
private fun CameraPermissionRationale(
    onRequestPermission: () -> Unit,
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
                text = "Kryptx needs camera access to scan 2FA TOTP QR codes. The camera stream is analyzed locally in real-time RAM and no image data is stored or transmitted.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

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
                text = "Cancel",
                borderColor = KryptxBlue,
                textColor = KryptxBlue,
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Extracts raw Y-plane luminance from ImageProxy and decodes QR codes via ZXing safely.
 */
private fun decodeQrCode(
    imageProxy: ImageProxy,
    reader: MultiFormatReader
): String? {
    return try {
        val planes = imageProxy.planes
        if (planes.isEmpty()) return null

        val plane = planes[0]
        val buffer = plane.buffer
        val remaining = buffer.remaining()
        if (remaining <= 0) return null

        val bytes = ByteArray(remaining)
        buffer.get(bytes)

        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = plane.rowStride

        // Prevent BufferOverflow / IndexOutOfBounds in PlanarYUVLuminanceSource
        val source = if (rowStride >= width && bytes.size >= rowStride * height) {
            PlanarYUVLuminanceSource(
                bytes,
                rowStride,
                height,
                0,
                0,
                width,
                height,
                false
            )
        } else {
            PlanarYUVLuminanceSource(
                bytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
        }

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val text = try {
            reader.decodeWithState(binaryBitmap)?.text
        } catch (_: Exception) {
            // Inverted QR code fallback (for dark-mode QR codes / screens)
            try {
                reader.reset()
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert())))?.text
            } catch (_: Exception) {
                null
            }
        }
        text
    } catch (_: Throwable) {
        null
    } finally {
        try {
            reader.reset()
        } catch (_: Throwable) {}
    }
}

/**
 * Helper to traverse ContextWrapper chain up to ComponentActivity.
 */
private fun Context.findActivity(): ComponentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

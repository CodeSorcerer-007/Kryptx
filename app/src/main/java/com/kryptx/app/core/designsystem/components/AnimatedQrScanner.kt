package com.kryptx.app.core.designsystem.components

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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
 */
@Composable
fun AnimatedQrScanner(
    onFrameScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            try {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } catch (e: Throwable) {
                android.util.Log.e("AnimatedQrScanner", "Failed to launch camera permission", e)
            }
        }
    }

    if (hasCameraPermission) {
        val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
        val zxingReader = remember {
            MultiFormatReader().apply {
                setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
            }
        }

        // To prevent spamming the decoder with the same frame, keep track of last scanned
        var lastScannedFrame by remember { mutableStateOf<String?>(null) }
        var lastScannedTime by remember { mutableStateOf(0L) }

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
                                                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
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
    } else {
        // Fallback UI while waiting for permission
        Box(modifier = modifier)
    }
}

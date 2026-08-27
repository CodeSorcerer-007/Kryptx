package com.kryptx.app.core.designsystem.components

import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts raw Y-plane luminance from ImageProxy and decodes QR codes via ZXing safely.
 */
fun decodeQrCode(
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
 * Decodes a QR code from an image URI (e.g. screenshot selected via photo picker).
 */
suspend fun decodeQrFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        decoder.isMutableRequired = false
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val maxDim = maxOf(info.size.width, info.size.height)
                        if (maxDim > 1600) {
                            decoder.setTargetSampleSize(maxDim / 1600 + 1)
                        }
                    }
                } catch (_: Throwable) {
                    decodeBitmapFromStream(context, uri)
                }
            } else {
                decodeBitmapFromStream(context, uri)
            }
        } catch (e: Throwable) {
            android.util.Log.e("QrScanner", "Bitmap decoding error", e)
            decodeBitmapFromStream(context, uri)
        } ?: return@withContext null

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader().apply {
            setHints(mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            ))
        }

        try {
            reader.decodeWithState(binaryBitmap)?.text
        } catch (_: Exception) {
            try {
                reader.reset()
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert())))?.text
            } catch (_: Exception) {
                null
            }
        }
    } catch (e: Throwable) {
        android.util.Log.e("QrScanner", "Failed to decode QR from URI", e)
        null
    }
}

private fun decodeBitmapFromStream(context: Context, uri: Uri): android.graphics.Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        val maxDim = maxOf(options.outWidth, options.outHeight)
        val sampleSize = if (maxDim > 1600) maxDim / 1600 + 1 else 1
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    } catch (e: Throwable) {
        android.util.Log.e("QrScanner", "decodeBitmapFromStream failed", e)
        null
    }
}

/**
 * Helper to traverse ContextWrapper chain up to ComponentActivity.
 */
fun Context.findActivity(): ComponentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

package com.kryptx.app.core.security

import android.os.Build
import java.io.File

/**
 * Advanced system integrity scanner for detecting root, custom ROMs, Magisk,
 * hooking frameworks (Frida/Xposed), and emulator environments.
 */
object RootDetector {

    data class SecurityStatus(
        val isRooted: Boolean,
        val isEmulator: Boolean,
        val hasTestKeys: Boolean,
        val detectedIndicators: List<String>
    )

    private val ROOT_PATHS = listOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su",
        "/system/xbin/busybox",
        "/system/bin/busybox",
        "/system/bin/magisk",
        "/sbin/magisk",
        "/data/adb/magisk",
        "/data/local/tmp/frida-server"
    )

    private val ROOT_PACKAGES_DIR = listOf(
        "/data/data/eu.chainfire.supersu",
        "/data/data/com.topjohnwu.magisk",
        "/data/data/com.koushikdutta.superuser",
        "/data/data/com.noshufou.android.su",
        "/data/data/com.thirdparty.superuser"
    )

    fun checkDeviceSecurity(): SecurityStatus {
        val indicators = mutableListOf<String>()

        // 1. Check OS test-keys
        val buildTags = Build.TAGS
        val hasTestKeys = buildTags != null && buildTags.contains("test-keys")
        if (hasTestKeys) {
            indicators.add("OS build signed with test-keys (custom ROM)")
        }

        // 2. Check SU / Magisk / Busybox binaries
        var hasRootBinary = false
        for (path in ROOT_PATHS) {
            try {
                if (File(path).exists()) {
                    hasRootBinary = true
                    indicators.add("Root/tampering binary found: $path")
                }
            } catch (_: Exception) {
                // Ignore permission denial on strictly sandboxed paths
            }
        }

        // 3. Check known root manager directories
        var hasRootManager = false
        for (pkgPath in ROOT_PACKAGES_DIR) {
            try {
                if (File(pkgPath).exists()) {
                    hasRootManager = true
                    indicators.add("Root management package detected: $pkgPath")
                }
            } catch (_: Exception) {
                // Ignore permission denial
            }
        }

        // 4. Check for attached debugger or hooking frameworks
        try {
            if (android.os.Debug.isDebuggerConnected() || android.os.Debug.waitingForDebugger()) {
                indicators.add("Debugger currently connected to application process")
            }
        } catch (_: Throwable) {}

        // 5. Check memory maps for Frida or Xposed
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                val mapsContent = mapsFile.readText()
                if (mapsContent.contains("frida") || mapsContent.contains("xposed") || mapsContent.contains("substrate")) {
                    indicators.add("Runtime hooking framework detected in memory maps")
                }
            }
        } catch (_: Throwable) {}

        // 6. Check emulator properties safely with null checks for JVM testing
        val fingerprint = Build.FINGERPRINT.orEmpty()
        val model = Build.MODEL.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val device = Build.DEVICE.orEmpty()
        val product = Build.PRODUCT.orEmpty()

        val isEmulator = (fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("Emulator")
                || model.contains("Android SDK built for x86")
                || manufacturer.contains("Genymotion")
                || (brand.startsWith("generic") && device.startsWith("generic"))
                || "google_sdk" == product)

        if (isEmulator) {
            indicators.add("Running in Android Virtual Device / Emulator")
        }

        val isRooted = hasRootBinary || hasRootManager || (hasTestKeys && !isEmulator)

        return SecurityStatus(
            isRooted = isRooted,
            isEmulator = isEmulator,
            hasTestKeys = hasTestKeys,
            detectedIndicators = indicators
        )
    }
}

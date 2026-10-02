package com.kryptx.app.core.security

import android.annotation.SuppressLint
import android.os.Build
import org.bouncycastle.asn1.ASN1Boolean
import org.bouncycastle.asn1.ASN1Enumerated
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1TaggedObject
import java.io.File

/**
 * Advanced system integrity scanner for detecting root, custom ROMs, Magisk,
 * hooking frameworks (Frida/Xposed), and emulator environments.
 */
@SuppressLint("SdCardPath")
object RootDetector {

    data class SecurityStatus(
        val isRooted: Boolean,
        val isEmulator: Boolean,
        val hasTestKeys: Boolean,
        val detectedIndicators: List<String>,
        val attestationSecurityLevel: String? = null,
        val verifiedBootState: String? = null,
        val isDeviceLocked: Boolean? = null
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
            } catch (e: Exception) {
                SecurityLogger.trace("RootDetector", "Permission denial scanning $path", e)
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
            } catch (e: Exception) {
                SecurityLogger.trace("RootDetector", "Permission denial scanning $pkgPath", e)
            }
        }

        // 4. Check for attached debugger or hooking frameworks
        try {
            if (android.os.Debug.isDebuggerConnected() || android.os.Debug.waitingForDebugger()) {
                indicators.add("Debugger currently connected to application process")
            }
        } catch (e: Throwable) {
            SecurityLogger.trace("RootDetector", "Debugger check exception", e)
        }

        // 5. Check memory maps for Frida or Xposed
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                val isHooked = mapsFile.useLines { lines ->
                    lines.any { line ->
                        line.contains("frida", ignoreCase = true) ||
                        line.contains("xposed", ignoreCase = true) ||
                        line.contains("substrate", ignoreCase = true) ||
                        line.contains("gum-js", ignoreCase = true)
                    }
                }
                if (isHooked) {
                    indicators.add("Runtime hooking framework detected in memory maps")
                }
            }
        } catch (e: Throwable) {
            SecurityLogger.trace("RootDetector", "Memory maps inspect exception", e)
        }

        // 5b. Check for LD_PRELOAD injection
        try {
            val ldPreload = System.getenv("LD_PRELOAD")
            if (!ldPreload.isNullOrBlank()) {
                indicators.add("LD_PRELOAD injection detected: $ldPreload")
            }
        } catch (e: Throwable) {
            SecurityLogger.trace("RootDetector", "LD_PRELOAD check exception", e)
        }

        // 5c. ptrace self-check — a debugger or Frida attaches via ptrace(PTRACE_ATTACH).
        // Check /proc/self/status for TracerPid != 0
        try {
            val statusFile = File("/proc/self/status")
            if (statusFile.exists()) {
                val tracerLine = statusFile.readLines().find { it.startsWith("TracerPid:") }
                val tracerPid = tracerLine?.split(":")?.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                if (tracerPid != 0) {
                    indicators.add("Active ptrace debugger detected (TracerPid: $tracerPid)")
                }
            }
        } catch (e: Throwable) {
            SecurityLogger.trace("RootDetector", "Proc status check exception", e)
        }

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

        // 7. Hardware-Backed Key Attestation (Verified Boot Check)
        var hardwareAttestationFailed = false
        var attestationSecLevel: String? = null
        var verifiedBootState: String? = null
        var isDeviceLocked: Boolean? = null

        val alias = "kryptx_attestation_key"
        var keyStore: java.security.KeyStore? = null
        try {
            keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }

            val keyPairGenerator = java.security.KeyPairGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
            )
            val builder = android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_SIGN
            )
            builder.setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
            builder.setAttestationChallenge("kryptx_secure_challenge".toByteArray())

            // Attempt to require StrongBox if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }

            try {
                keyPairGenerator.initialize(builder.build())
                keyPairGenerator.generateKeyPair()
            } catch (e: Exception) {
                // Fallback to TEE if StrongBox is unavailable
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    builder.setIsStrongBoxBacked(false)
                    keyPairGenerator.initialize(builder.build())
                    keyPairGenerator.generateKeyPair()
                }
            }

            val certs = keyStore.getCertificateChain(alias)
            if (certs != null && certs.isNotEmpty()) {
                val leafCert = certs[0] as java.security.cert.X509Certificate
                val attestationExtensionBytes = leafCert.getExtensionValue("1.3.6.1.4.1.11129.2.1.17")

                if (attestationExtensionBytes == null) {
                    indicators.add("Hardware Attestation Extension missing from TEE certificate")
                    hardwareAttestationFailed = true
                } else {
                    // Decode ASN.1 KeyDescription sequence
                    try {
                        val octetString = ASN1InputStream(attestationExtensionBytes).use { it.readObject() as? ASN1OctetString }
                        if (octetString != null) {
                            val recordSeq = ASN1InputStream(octetString.octets).use { it.readObject() as? ASN1Sequence }
                            if (recordSeq != null && recordSeq.size() >= 8) {
                                val secLevelObj = recordSeq.getObjectAt(1) as? ASN1Enumerated
                                attestationSecLevel = when (secLevelObj?.value?.toInt()) {
                                    0 -> "Software"
                                    1 -> "TrustedEnvironment"
                                    2 -> "StrongBox"
                                    else -> "Unknown"
                                }

                                // teeEnforced is at index 7
                                val teeEnforced = recordSeq.getObjectAt(7) as? ASN1Sequence
                                if (teeEnforced != null) {
                                    for (i in 0 until teeEnforced.size()) {
                                        val taggedObj = teeEnforced.getObjectAt(i) as? ASN1TaggedObject ?: continue
                                        if (taggedObj.tagNo == 704) { // rootOfTrust
                                            val rootOfTrustSeq = (taggedObj.baseObject) as? ASN1Sequence
                                            if (rootOfTrustSeq != null && rootOfTrustSeq.size() >= 3) {
                                                val deviceLockedObj = rootOfTrustSeq.getObjectAt(1) as? ASN1Boolean
                                                val verifiedBootStateObj = rootOfTrustSeq.getObjectAt(2) as? ASN1Enumerated

                                                isDeviceLocked = deviceLockedObj?.isTrue
                                                verifiedBootState = when (verifiedBootStateObj?.value?.toInt()) {
                                                    0 -> "Verified"
                                                    1 -> "SelfSigned"
                                                    2 -> "Unverified"
                                                    3 -> "Failed"
                                                    else -> "Unknown"
                                                }

                                                if (isDeviceLocked == false) {
                                                    indicators.add("Hardware Attestation: Bootloader unlocked (deviceLocked=false)")
                                                    hardwareAttestationFailed = true
                                                }
                                                if (verifiedBootState != "Verified") {
                                                    indicators.add("Hardware Attestation: Verified Boot State is $verifiedBootState")
                                                    if (verifiedBootState != "SelfSigned" || !isEmulator) {
                                                        hardwareAttestationFailed = true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (asnE: Throwable) {
                        SecurityLogger.trace("RootDetector", "Error decoding Hardware Attestation ASN.1", asnE)
                    }
                }
            } else {
                indicators.add("Could not retrieve Hardware Attestation certificate chain")
                hardwareAttestationFailed = true
            }
        } catch (e: Exception) {
            indicators.add("Hardware Attestation failed: ${e.message}")
            SecurityLogger.trace("RootDetector", "Hardware Attestation key generation error", e)
            hardwareAttestationFailed = true
        } finally {
            try {
                keyStore?.deleteEntry(alias)
            } catch (_: Throwable) {}
        }

        val isRooted = hasRootBinary || hasRootManager || (hasTestKeys && !isEmulator) || hardwareAttestationFailed

        return SecurityStatus(
            isRooted = isRooted,
            isEmulator = isEmulator,
            hasTestKeys = hasTestKeys,
            detectedIndicators = indicators,
            attestationSecurityLevel = attestationSecLevel,
            verifiedBootState = verifiedBootState,
            isDeviceLocked = isDeviceLocked
        )
    }
}

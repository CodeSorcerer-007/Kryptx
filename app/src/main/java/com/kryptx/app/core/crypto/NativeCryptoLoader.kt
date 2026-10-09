package com.kryptx.app.core.crypto

import com.kryptx.app.core.security.SecurityLogger

/**
 * Thread-safe centralized loader for `libkryptx_crypto` preventing concurrent JNI loading races.
 */
internal object NativeCryptoLoader {
    @Volatile
    private var isLoaded: Boolean = false

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (!isLoaded) {
            try {
                System.loadLibrary("kryptx_crypto")
                isLoaded = true
            } catch (e: Throwable) {
                SecurityLogger.warn(
                    "NativeCryptoLoader",
                    "Native crypto library (kryptx_crypto) failed to load — JVM fallbacks active. Some PQC features will be unavailable.",
                    e
                )
                isLoaded = false
            }
        }
        return isLoaded
    }
}

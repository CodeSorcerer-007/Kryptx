package com.kryptx.app.core.util

import android.os.Build
import android.view.Display
import android.view.Window
import android.view.WindowManager

/**
 * Configures the activity window to prioritize high refresh rates (90Hz / 120Hz / 144Hz),
 * preventing OEM frame throttling (especially under FLAG_SECURE) and enabling fluid 120 FPS animations.
 */
object DisplayConfigurator {

    fun configureHighRefreshRate(window: Window, windowManager: WindowManager) {
        try {
            val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    window.context.display
                } catch (_: Throwable) {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay
                }
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            applyHighRefreshRate(window, display)
        } catch (_: Throwable) {
            // Non-fatal fallback to default system refresh rate
        }
    }

    private fun applyHighRefreshRate(window: Window, targetDisplay: Display?) {
        val modes = targetDisplay?.supportedModes ?: return
        val maxMode = modes.maxByOrNull { it.refreshRate } ?: return
        if (maxMode.refreshRate >= 90f) {
            val lp = window.attributes
            lp.preferredDisplayModeId = maxMode.modeId
            lp.preferredRefreshRate = maxMode.refreshRate
            window.attributes = lp
        }
    }
}

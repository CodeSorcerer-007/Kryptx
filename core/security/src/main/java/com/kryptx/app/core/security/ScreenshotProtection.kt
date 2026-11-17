package com.kryptx.app.core.security

import android.app.Activity
import android.view.WindowManager

/**
 * Manages FLAG_SECURE window flags to prevent screen recording, screenshots,
 * and recent apps previews on sensitive vault views.
 */
object ScreenshotProtection {

    fun apply(activity: Activity, enable: Boolean) {
        if (enable) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

package com.kryptx.app.core.designsystem.theme

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.kryptx.app.core.designsystem.components.KryptxAudio
import com.kryptx.app.core.designsystem.components.KryptxHaptics

/**
 * Interface abstraction for Kryptx sensory tactile feedback.
 */
interface KryptxHapticsController {
    fun tap()
    fun heavyClick()
    fun sliderSnap()
    fun tick()
    fun confirm()
    fun warning()
    fun error()
    fun success()
    fun secretCopied()
    fun entropyLevelUp()
    fun panicAlert()
    fun successVibration()
}

/**
 * Interface abstraction for Kryptx acoustic feedback.
 */
interface KryptxAudioController {
    fun tick()
    fun click()
    fun snap()
    fun unlockChime()
    var isEnabled: Boolean
}

class DefaultKryptxHapticsController(
    private val viewProvider: () -> View?,
    private val contextProvider: () -> Context?
) : KryptxHapticsController {
    override fun tap() { viewProvider()?.let { KryptxHaptics.tap(it) } }
    override fun heavyClick() { viewProvider()?.let { KryptxHaptics.heavyClick(it) } }
    override fun sliderSnap() { viewProvider()?.let { KryptxHaptics.sliderSnap(it) } }
    override fun tick() { viewProvider()?.let { KryptxHaptics.tick(it) } }
    override fun confirm() { viewProvider()?.let { KryptxHaptics.confirm(it) } }
    override fun warning() { viewProvider()?.let { KryptxHaptics.warning(it) } }
    override fun error() { viewProvider()?.let { KryptxHaptics.error(it) } }
    override fun success() { viewProvider()?.let { KryptxHaptics.success(it) } }
    override fun secretCopied() { contextProvider()?.let { KryptxHaptics.secretCopied(it) } }
    override fun entropyLevelUp() { contextProvider()?.let { KryptxHaptics.entropyLevelUp(it) } }
    override fun panicAlert() { contextProvider()?.let { KryptxHaptics.panicAlert(it) } }
    override fun successVibration() { contextProvider()?.let { KryptxHaptics.successVibration(it) } }
}

class DefaultKryptxAudioController(
    private val contextProvider: () -> Context?
) : KryptxAudioController {
    override fun tick() { KryptxAudio.tick(contextProvider()) }
    override fun click() { KryptxAudio.click(contextProvider()) }
    override fun snap() { KryptxAudio.snap(contextProvider()) }
    override fun unlockChime() { KryptxAudio.unlockChime(contextProvider()) }
    override var isEnabled: Boolean
        get() = KryptxAudio.isEnabled
        set(value) { KryptxAudio.isEnabled = value }
}

val LocalKryptxHaptics: ProvidableCompositionLocal<KryptxHapticsController> =
    compositionLocalOf {
        object : KryptxHapticsController {
            override fun tap() {}
            override fun heavyClick() {}
            override fun sliderSnap() {}
            override fun tick() {}
            override fun confirm() {}
            override fun warning() {}
            override fun error() {}
            override fun success() {}
            override fun secretCopied() {}
            override fun entropyLevelUp() {}
            override fun panicAlert() {}
            override fun successVibration() {}
        }
    }

val LocalKryptxAudio: ProvidableCompositionLocal<KryptxAudioController> =
    staticCompositionLocalOf {
        object : KryptxAudioController {
            override fun tick() {}
            override fun click() {}
            override fun snap() {}
            override fun unlockChime() {}
            override var isEnabled: Boolean = true
        }
    }

@Composable
fun ProvideKryptxSensory(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val haptics = DefaultKryptxHapticsController(
        viewProvider = { view },
        contextProvider = { context }
    )
    val audio = DefaultKryptxAudioController(
        contextProvider = { context }
    )

    CompositionLocalProvider(
        LocalKryptxHaptics provides haptics,
        LocalKryptxAudio provides audio,
        content = content
    )
}

package com.kryptx.app.core.designsystem.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sovereign Acoustic Audio Engine for Kryptx.
 * Synthesizes ultra-low-latency, tactile physical micro-sounds in volatile RAM without
 * external audio files, preserving zero-network isolation and minimal APK footprint.
 * Respects system ringer mode (silent/vibrate) and user settings.
 */
object KryptxAudio {

    private val executor = Executors.newSingleThreadExecutor()
    private const val SAMPLE_RATE = 44100

    @Volatile
    var isEnabled: Boolean = true

    // Pre-synthesized PCM buffers
    private val tickBuffer: ShortArray by lazy { generateTickBuffer() }
    private val snapBuffer: ShortArray by lazy { generateSnapBuffer() }
    private val unlockChimeBuffer: ShortArray by lazy { generateUnlockChimeBuffer() }

    /**
     * Subtle mechanical gear tick (e.g. dial rotation, tab switch, slider milestone).
     */
    fun tick(context: Context? = null) {
        playSound(context, tickBuffer, 0.45f)
    }

    /**
     * Subtle tactile click (e.g. item selection, button tap).
     */
    fun click(context: Context? = null) {
        tick(context)
    }

    /**
     * Crisp mechanical latch snap (e.g. copy secret, toggle item favorite).
     */
    fun snap(context: Context? = null) {
        playSound(context, snapBuffer, 0.65f)
    }

    /**
     * Harmonic sovereign unlock chime (e.g. successful vault decryption).
     */
    fun unlockChime(context: Context? = null) {
        playSound(context, unlockChimeBuffer, 0.75f)
    }

    private fun isSystemAudioSilent(context: Context?): Boolean {
        if (context == null) return false
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
    }

    private fun playSound(context: Context?, pcmData: ShortArray, volume: Float) {
        if (!isEnabled) return
        if (context != null && isSystemAudioSilent(context)) return

        executor.execute {
            try {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val track = AudioTrack(
                    attributes,
                    format,
                    pcmData.size * 2,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                track.write(pcmData, 0, pcmData.size)
                track.setVolume(volume.coerceIn(0f, 1f))
                track.play()

                // Allow track to play out then release
                val durationMs = (pcmData.size * 1000L / SAMPLE_RATE) + 20L
                Thread.sleep(durationMs)
                track.stop()
                track.release()
            } catch (_: Throwable) {
                // Silently absorb audio buffer anomalies on specialized OEM ROMs
            }
        }
    }

    /**
     * Synthesize 8ms high-frequency mechanical tick.
     */
    private fun generateTickBuffer(): ShortArray {
        val durationSec = 0.009
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ShortArray(totalSamples)
        val frequency = 2400.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-i.toDouble() / (totalSamples * 0.18))
            val wave = sin(2.0 * PI * frequency * t)
            buffer[i] = (wave * envelope * Short.MAX_VALUE * 0.7).toInt().toShort()
        }
        return buffer
    }

    /**
     * Synthesize 18ms dual-impulse tactile latch snap.
     */
    private fun generateSnapBuffer(): ShortArray {
        val durationSec = 0.020
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val env1 = exp(-i.toDouble() / (totalSamples * 0.15))
            val env2 = if (i > totalSamples * 0.3) exp(-(i - totalSamples * 0.3) / (totalSamples * 0.2)) else 0.0
            val wave1 = sin(2.0 * PI * 1800.0 * t)
            val wave2 = sin(2.0 * PI * 950.0 * t)
            val combined = (wave1 * env1 * 0.6) + (wave2 * env2 * 0.4)
            buffer[i] = (combined * Short.MAX_VALUE * 0.85).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    /**
     * Synthesize 150ms dual-harmonic major-third unlock chime (880Hz + 1108Hz).
     */
    private fun generateUnlockChimeBuffer(): ShortArray {
        val durationSec = 0.160
        val totalSamples = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-t * 22.0)
            val waveA = sin(2.0 * PI * 880.0 * t) * 0.55
            val waveB = sin(2.0 * PI * 1108.73 * t) * 0.45
            val sample = (waveA + waveB) * decay
            buffer[i] = (sample * Short.MAX_VALUE * 0.75).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }
}

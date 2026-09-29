package com.kryptx.app.core.security

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Monitors device sensors (Accelerometer) to trigger contextual locking of the vault.
 * Calibrated for high-velocity "Shake-to-Lock" (2.7g) with cooldown protection
 * and debounced "Face Down" privacy locking.
 */
class ContextualLockManager(
    context: Context,
    private val onShakeTriggered: () -> Unit,
    private val onFaceDownTriggered: (() -> Unit)? = null
) : SensorEventListener {

    constructor(
        context: Context,
        onLockTriggered: () -> Unit
    ) : this(
        context = context,
        onShakeTriggered = onLockTriggered,
        onFaceDownTriggered = onLockTriggered
    )

    private val sensorManager = try {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    } catch (_: Exception) {
        null
    }
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var isListening = false

    // Shake detection state
    private var acceleration = 0f
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var lastAcceleration = SensorManager.GRAVITY_EARTH
    private var lastShakeTimestamp = 0L

    // Face-down state
    private var faceDownStartTime = 0L

    companion object {
        // 2.7g threshold matching physical device security standard in TESTING.md
        const val SHAKE_THRESHOLD_ACCELERATION = 14.5f
        const val SHAKE_COOLDOWN_MS = 1000L

        const val FACE_DOWN_GRAVITY_THRESHOLD = -8.5f
        const val FACE_DOWN_SUSTAINED_MS = 600L
    }

    fun startListening() {
        if (!isListening && accelerometer != null && sensorManager != null) {
            try {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
                isListening = true
                faceDownStartTime = 0L
                acceleration = 0f
            } catch (_: Exception) {}
        }
    }

    fun stopListening() {
        if (isListening && sensorManager != null) {
            try {
                sensorManager.unregisterListener(this)
            } catch (_: Exception) {}
            isListening = false
            faceDownStartTime = 0L
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val now = System.currentTimeMillis()

        // 1. Debounced Face Down Detection (Z-axis gravity heavily negative for sustained duration)
        if (z < FACE_DOWN_GRAVITY_THRESHOLD) {
            if (faceDownStartTime == 0L) {
                faceDownStartTime = now
            } else if (now - faceDownStartTime >= FACE_DOWN_SUSTAINED_MS) {
                faceDownStartTime = 0L
                onFaceDownTriggered?.invoke()
                return
            }
        } else {
            faceDownStartTime = 0L
        }

        // 2. High-Energy Shake Detection with cooldown
        lastAcceleration = currentAcceleration
        currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta = kotlin.math.abs(currentAcceleration - lastAcceleration)
        acceleration = acceleration * 0.85f + delta

        if (acceleration > SHAKE_THRESHOLD_ACCELERATION && (now - lastShakeTimestamp) >= SHAKE_COOLDOWN_MS) {
            lastShakeTimestamp = now
            acceleration = 0f
            onShakeTriggered()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}

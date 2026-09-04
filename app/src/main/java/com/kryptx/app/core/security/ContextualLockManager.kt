package com.kryptx.app.core.security

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Monitors device sensors (Accelerometer) to trigger contextual locking of the vault.
 * Detects "Face Down" and "Shake" events.
 */
class ContextualLockManager(
    context: Context,
    private val onLockTriggered: () -> Unit
) : SensorEventListener {

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
    
    // Thresholds
    private val SHAKE_THRESHOLD = 12f
    private val FACE_DOWN_GRAVITY_THRESHOLD = -8.5f

    fun startListening() {
        if (!isListening && accelerometer != null && sensorManager != null) {
            try {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
                isListening = true
            } catch (_: Exception) {}
        }
    }

    fun stopListening() {
        if (isListening && sensorManager != null) {
            try {
                sensorManager.unregisterListener(this)
            } catch (_: Exception) {}
            isListening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // 1. Detect Face Down (Z-axis gravity heavily negative)
        if (z < FACE_DOWN_GRAVITY_THRESHOLD) {
            triggerLock()
            return
        }

        // 2. Detect Shake
        lastAcceleration = currentAcceleration
        currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta = currentAcceleration - lastAcceleration
        acceleration = acceleration * 0.9f + delta // low-cut filter

        if (acceleration > SHAKE_THRESHOLD) {
            triggerLock()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun triggerLock() {
        // Debounce or immediately trigger
        onLockTriggered()
    }
}

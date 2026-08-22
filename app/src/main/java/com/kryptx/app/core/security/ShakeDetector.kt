package com.kryptx.app.core.security

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * SensorEventListener detecting physical device shake gestures for instant emergency vault lockdown.
 */
class ShakeDetector(
    private val onShake: () -> Unit
) : SensorEventListener {

    companion object {
        private const val SHAKE_THRESHOLD_GRAVITY = 2.7f
        private const val SHAKE_SLOP_TIME_MS = 600
        private const val SHAKE_COUNT_RESET_TIME_MS = 3000
    }

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var shakeTimestamp: Long = 0
    private var shakeCount: Int = 0
    private var isListening: Boolean = false

    fun start(context: Context): Boolean {
        if (isListening) return true

        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        accelerometer?.let {
            isListening = sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) ?: false
        }
        return isListening
    }

    fun stop() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        shakeCount = 0
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // gForce will be close to 1 when still
        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()
            // Ignore shake events too close to each other
            if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return
            }

            // Reset the shake count if 3 seconds have passed
            if (shakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                shakeCount = 0
            }

            shakeTimestamp = now
            shakeCount++

            if (shakeCount >= 1) {
                shakeCount = 0
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

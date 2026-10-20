package fi.dreamr.rinkulat.game

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Latest in-plane gravity ([x], [y]) and device acceleration from shakes and nudges ([shakeX], [shakeY]),
 * in g units and screen axes (x right, y down). Read from the frame loop.
 */
class TiltReading {
    @Volatile var x = 0f
    @Volatile var y = 1f
    @Volatile var shakeX = 0f
    @Volatile var shakeY = 0f
}

/** Ignore hand tremor below this (g). */
private const val SHAKE_DEAD_ZONE = 0.08f

/** Cap single jolts (g) so a hard knock doesn't fling every ring through the walls. */
private const val SHAKE_MAX = 3f

private fun shakeValue(raw: Float): Float {
    val magnitude = (kotlin.math.abs(raw) - SHAKE_DEAD_ZONE).coerceIn(0f, SHAKE_MAX)
    return kotlin.math.sign(raw) * magnitude
}

@Composable
fun rememberTiltReading(): TiltReading {
    val context = LocalContext.current
    val reading = remember { TiltReading() }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gravitySensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val sensor = gravitySensor ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // Raw accelerometer data includes hand shake, so smooth it more.
        val smoothing = if (gravitySensor != null) 0.5f else 0.15f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // The sensor reports the reaction to gravity in device axes (y up); flip into screen axes.
                val gx = -event.values[0] / SensorManager.GRAVITY_EARTH
                val gy = event.values[1] / SensorManager.GRAVITY_EARTH
                reading.x += (gx - reading.x) * smoothing
                reading.y += (gy - reading.y) * smoothing
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        // Shakes: the fused linear-acceleration sensor, or raw accelerometer minus the smoothed gravity.
        val linearSensor = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val shakeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Device axes have y up; screen axes have y down.
                reading.shakeX = shakeValue(event.values[0] / SensorManager.GRAVITY_EARTH)
                reading.shakeY = shakeValue(-event.values[1] / SensorManager.GRAVITY_EARTH)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val rawShakeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Raw reading is the reaction (-acceleration + gravity reaction); remove gravity, flip to motion.
                val gx = -event.values[0] / SensorManager.GRAVITY_EARTH
                val gy = event.values[1] / SensorManager.GRAVITY_EARTH
                reading.shakeX = shakeValue(-(gx - reading.x))
                reading.shakeY = shakeValue(-(gy - reading.y))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        when {
            linearSensor != null -> manager.registerListener(shakeListener, linearSensor, SensorManager.SENSOR_DELAY_GAME)
            sensor != null && gravitySensor == null ->
                manager.registerListener(rawShakeListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            manager.unregisterListener(listener)
            manager.unregisterListener(shakeListener)
            manager.unregisterListener(rawShakeListener)
        }
    }
    return reading
}

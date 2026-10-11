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

/** Latest in-plane gravity in g units, in screen axes (x right, y down). Read from the frame loop. */
class TiltReading {
    @Volatile var x = 0f
    @Volatile var y = 1f
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
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    return reading
}

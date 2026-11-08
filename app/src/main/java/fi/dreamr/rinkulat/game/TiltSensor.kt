package fi.dreamr.rinkulat.game

import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.math.hypot

/**
 * Latest in-plane gravity ([x], [y]) and device acceleration from shakes and nudges ([shakeX], [shakeY]),
 * in g units and screen axes (x right, y down). Read from the frame loop.
 */
class TiltReading {
    /** Smoothed gravity pull in device axes (natural orientation, y up); kept there so a screen flip never blends two frames. */
    @Volatile internal var deviceX = 0f
    @Volatile internal var deviceY = -1f

    /** Current display rotation, a `Surface.ROTATION_*` value. */
    @Volatile internal var rotation = Surface.ROTATION_0

    val x get() = toScreenX(deviceX, deviceY, rotation)

    /** May point up the screen: within the flip margin, or while the screen is still turning. */
    internal val unclampedY get() = toScreenY(deviceX, deviceY, rotation)

    /** Never points up the screen, so turning the device over can't lift the rings. */
    val y get() = unclampedY.coerceAtLeast(0f)

    @Volatile var shakeX = 0f
    @Volatile var shakeY = 0f
}

/** Ignore hand tremor below this (g). */
private const val SHAKE_DEAD_ZONE = 0.08f

/** Cap single jolts (g) so a hard knock doesn't fling every ring through the walls. */
private const val SHAKE_MAX = 3f

/** A device lying nearly flat has too little in-plane gravity to decide which way is up. */
internal const val FLIP_MIN_IN_PLANE = 0.3f

/** sin(10°): how far past sideways the device must turn before the app flips. Prevents jitter at the threshold. */
internal const val FLIP_MARGIN_SIN = 0.17365f

private fun shakeValue(raw: Float): Float {
    val magnitude = (kotlin.math.abs(raw) - SHAKE_DEAD_ZONE).coerceIn(0f, SHAKE_MAX)
    return kotlin.math.sign(raw) * magnitude
}

/** Screen x (right) of a device-axes vector ([vx], [vy] with y up) on a display at [rotation]. */
internal fun toScreenX(vx: Float, vy: Float, rotation: Int): Float = when (rotation) {
    Surface.ROTATION_90 -> vy
    Surface.ROTATION_180 -> -vx
    Surface.ROTATION_270 -> -vy
    else -> vx
}

/** Screen y (down) of a device-axes vector ([vx], [vy] with y up) on a display at [rotation]. */
internal fun toScreenY(vx: Float, vy: Float, rotation: Int): Float = when (rotation) {
    Surface.ROTATION_90 -> vx
    Surface.ROTATION_180 -> vy
    Surface.ROTATION_270 -> -vx
    else -> -vy
}

/** Screen-axes gravity points more than the margin past sideways, toward the top of the screen. */
internal fun pastHalfway(x: Float, y: Float): Boolean {
    val inPlane = hypot(x, y)
    return inPlane >= FLIP_MIN_IN_PLANE && y < -inPlane * FLIP_MARGIN_SIN
}

/**
 * Gravity sensor (accelerometer fallback) readings in screen axes. Turning the device more than halfway
 * over flips the activity between portrait and reverse portrait, so down stays down; landscape is never used.
 */
@Composable
fun rememberTiltReading(): TiltReading {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val reading = remember { TiltReading() }
    DisposableEffect(context, activity) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val display = ContextCompat.getDisplayOrDefault(context)
        // The orientation last requested, so a device that ignores requests isn't asked again on every event.
        var requestedOrientation: Int? = null
        val gravitySensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val sensor = gravitySensor ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // Raw accelerometer data includes hand shake, so smooth it more.
        val smoothing = if (gravitySensor != null) 0.5f else 0.15f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // The sensor reports the reaction to gravity; the pull is its opposite.
                reading.deviceX += (-event.values[0] / SensorManager.GRAVITY_EARTH - reading.deviceX) * smoothing
                reading.deviceY += (-event.values[1] / SensorManager.GRAVITY_EARTH - reading.deviceY) * smoothing
                val rotation = display.rotation
                reading.rotation = rotation
                if (activity != null && pastHalfway(reading.x, reading.unclampedY)) {
                    val opposite = when (rotation) {
                        Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                        Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        else -> null
                    }
                    if (opposite != null && opposite != requestedOrientation) {
                        requestedOrientation = opposite
                        activity.requestedOrientation = opposite
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun setShake(motionX: Float, motionY: Float) {
            reading.shakeX = shakeValue(toScreenX(motionX, motionY, reading.rotation))
            reading.shakeY = shakeValue(toScreenY(motionX, motionY, reading.rotation))
        }

        // Shakes: the fused linear-acceleration sensor, or raw accelerometer minus the smoothed gravity.
        val linearSensor = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val shakeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) =
                setShake(event.values[0] / SensorManager.GRAVITY_EARTH, event.values[1] / SensorManager.GRAVITY_EARTH)

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val rawShakeListener = object : SensorEventListener {
            // The raw reading is motion minus the gravity pull, so adding the smoothed pull back leaves the motion.
            override fun onSensorChanged(event: SensorEvent) = setShake(
                event.values[0] / SensorManager.GRAVITY_EARTH + reading.deviceX,
                event.values[1] / SensorManager.GRAVITY_EARTH + reading.deviceY,
            )

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

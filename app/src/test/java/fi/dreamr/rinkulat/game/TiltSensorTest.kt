package fi.dreamr.rinkulat.game

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TiltSensorTest {
    @Test
    fun uprightDeviceSinksTowardScreenBottomInEveryRotation() {
        // Gravity pull in device axes (y up) for a device held so that its screen is upright.
        val pulls = mapOf(
            Surface.ROTATION_0 to (0f to -1f),
            Surface.ROTATION_90 to (1f to 0f),
            Surface.ROTATION_180 to (0f to 1f),
            Surface.ROTATION_270 to (-1f to 0f),
        )
        for ((rotation, pull) in pulls) {
            assertEquals("x at $rotation", 0f, toScreenX(pull.first, pull.second, rotation), 1e-6f)
            assertEquals("y at $rotation", 1f, toScreenY(pull.first, pull.second, rotation), 1e-6f)
        }
    }

    @Test
    fun portraitConversionMatchesThePreviousSignFlips() {
        // Previously: gravity x = pull x, y = -pull y; shakes x = motion x, y = -motion y.
        assertEquals(-0.6f, toScreenX(-0.6f, -0.8f, Surface.ROTATION_0), 1e-6f)
        assertEquals(0.8f, toScreenY(-0.6f, -0.8f, Surface.ROTATION_0), 1e-6f)
        assertEquals(-0.5f, toScreenY(0f, 0.5f, Surface.ROTATION_0), 1e-6f)
        // Reverse portrait mirrors both axes.
        assertEquals(0.6f, toScreenX(-0.6f, -0.8f, Surface.ROTATION_180), 1e-6f)
        assertEquals(-0.8f, toScreenY(-0.6f, -0.8f, Surface.ROTATION_180), 1e-6f)
    }

    @Test
    fun flipsOnlyPastTheHalfwayMargin() {
        fun gravityAt(degreesFromDown: Double, strength: Float = 1f): Pair<Float, Float> {
            val a = Math.toRadians(degreesFromDown)
            return (sin(a) * strength).toFloat() to (cos(a) * strength).toFloat()
        }
        for (degrees in listOf(0.0, 45.0, 90.0, 95.0, -95.0)) {
            val (x, y) = gravityAt(degrees)
            assertFalse("$degrees° must not flip", pastHalfway(x, y))
        }
        for (degrees in listOf(105.0, -105.0, 135.0, 180.0)) {
            val (x, y) = gravityAt(degrees)
            assertTrue("$degrees° must flip", pastHalfway(x, y))
        }
        val (flatX, flatY) = gravityAt(180.0, strength = 0.2f)
        assertFalse("nearly flat must not flip", pastHalfway(flatX, flatY))
    }

    @Test
    fun readingNeverPullsTowardScreenTopButKeepsSideways() {
        val reading = TiltReading()
        // Upside down in portrait, before the screen has flipped.
        reading.deviceX = 0f
        reading.deviceY = 1f
        assertEquals(-1f, reading.unclampedY, 1e-6f)
        assertEquals(0f, reading.y, 0f)
        // Turned well past sideways: the sideways part still steers.
        reading.deviceX = 0.8f
        reading.deviceY = 0.6f
        assertEquals(0.8f, reading.x, 1e-6f)
        assertEquals(0f, reading.y, 0f)
        // Once the screen has flipped the same pose pulls toward the new bottom.
        reading.rotation = Surface.ROTATION_180
        assertEquals(0.6f, reading.y, 1e-6f)
    }
}

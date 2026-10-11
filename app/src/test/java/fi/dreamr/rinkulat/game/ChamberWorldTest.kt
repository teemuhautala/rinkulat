package fi.dreamr.rinkulat.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.random.Random

class ChamberWorldTest {
    private val step = ChamberWorld.STEP_DT

    private fun ChamberWorld.run(seconds: Float, gx: Float = 0f, gy: Float = 1f, pumping: Boolean = false) {
        repeat((seconds / step).toInt()) { step(step, gx, gy, pumping) }
    }

    @Test
    fun ringsSettleOnFloorAndStayInside() {
        val world = ChamberWorld(random = Random(1))
        world.rings.forEach { it.y = 0.3f }
        world.run(15f)
        world.rings.forEach { ring ->
            assertTrue("ring x ${ring.x} inside", ring.x in 0f..ChamberWorld.WIDTH)
            if (!ring.isCaught) assertTrue("ring ${ring.y} near floor", ring.y > ChamberWorld.HEIGHT - 0.25f)
        }
    }

    @Test
    fun pumpingLiftsRingsNearTheJet() {
        val world = ChamberWorld(ringCount = 1, random = Random(2))
        val ring = world.rings.single()
        ring.x = ChamberWorld.WIDTH / 2f
        ring.y = ChamberWorld.HEIGHT - ChamberWorld.RING_RADIUS
        val startY = ring.y
        world.run(0.8f, pumping = true)
        assertTrue("ring rose from $startY to ${ring.y}", ring.y < startY - 0.5f)
    }

    @Test
    fun flatRingFallingOntoPegIsCaught() {
        val world = ChamberWorld(ringCount = 1, random = Random(3))
        val peg = world.pegs[0]
        val ring = world.rings.single()
        ring.x = peg.x
        ring.y = peg.tipY - 0.15f
        ring.phi = (PI / 2).toFloat()
        world.run(3f)
        assertTrue(ring.isCaught)
        assertEquals(peg.baseY, ring.y, 0.001f)
    }

    @Test
    fun faceOnRingIsNotCaught() {
        val world = ChamberWorld(ringCount = 1, random = Random(4))
        val peg = world.pegs[0]
        val ring = world.rings.single()
        ring.x = peg.x
        ring.y = peg.tipY - ChamberWorld.RING_RADIUS - 0.02f
        ring.phi = 0f
        ring.vy = 0.1f
        world.step(step, 0f, 1f, pumping = false)
        assertFalse(ring.isCaught)
    }

    @Test
    fun caughtRingsStackAndWinIsDetected() {
        val world = ChamberWorld(ringCount = 2, random = Random(5))
        world.rings.forEachIndexed { i, ring ->
            ring.peg = 0
            ring.y = world.pegs[0].tipY + i * 0.01f
        }
        world.run(3f)
        val ys = world.rings.map { it.y }.sorted()
        assertEquals(ChamberWorld.RING_STACK_HEIGHT, ys[1] - ys[0], 0.001f)
        assertTrue(world.won)
    }

    @Test
    fun timerStartsWithFirstCaughtRingNotFirstPump() {
        val world = ChamberWorld(ringCount = 2, random = Random(10))
        world.run(1f, pumping = true)
        assertFalse(world.started)
        assertEquals(0f, world.elapsed, 0f)
        world.rings[0].peg = 0
        world.run(1f)
        assertTrue(world.started)
        assertTrue(world.elapsed > 0.9f)
    }

    @Test
    fun rolledRingIsNotCaught() {
        val world = ChamberWorld(ringCount = 1, random = Random(8))
        val peg = world.pegs[0]
        val ring = world.rings.single()
        ring.x = peg.x
        ring.y = peg.tipY - ChamberWorld.RING_RADIUS - 0.02f
        ring.phi = (PI / 2).toFloat()
        ring.roll = (PI / 2).toFloat() // Lying flat but rolled on its side: axis horizontal.
        ring.vy = 0.1f
        world.step(step, 0f, 1f, pumping = false)
        assertFalse(ring.isCaught)
    }

    @Test
    fun jetSpinWeakensWithHeightAndDistanceFromCenter() {
        val world = ChamberWorld(ringCount = 1, random = Random(9))
        world.run(1f, pumping = true)
        val center = ChamberWorld.WIDTH / 2f
        val low = ChamberWorld.HEIGHT - 0.05f
        val nearNozzle = world.jetSpinInfluence(center, low)
        assertTrue(world.jetSpinInfluence(center, 0.3f) < nearNozzle * 0.3f)
        assertTrue(world.jetSpinInfluence(0.1f, low) < nearNozzle * 0.3f)
    }

    @Test
    fun winWaitsUntilRingsSettleOnStack() {
        val world = ChamberWorld(ringCount = 1, random = Random(11))
        val peg = world.pegs[0]
        val ring = world.rings.single()
        ring.peg = 0
        ring.y = peg.tipY
        world.run(0.05f)
        assertTrue(world.started)
        assertFalse("still sliding down", world.won)
        world.run(3f)
        assertEquals(peg.baseY, ring.y, 0.001f)
        assertTrue(world.won)
    }

    @Test
    fun caughtRingSurvivesShortPump() {
        val world = ChamberWorld(ringCount = 1, random = Random(7))
        val ring = world.rings.single()
        ring.peg = 0
        ring.y = world.pegs[0].baseY
        world.run(0.5f, pumping = true)
        assertTrue("ring at ${ring.y} still on peg", ring.isCaught)
    }

    @Test
    fun sidewaysTiltMovesRingsToThatWall() {
        val world = ChamberWorld(ringCount = 1, random = Random(6))
        val ring = world.rings.single()
        ring.x = ChamberWorld.WIDTH / 2f
        ring.y = 0.2f
        world.run(6f, gx = 1f, gy = 0f)
        assertTrue("ring x ${ring.x}", ring.x > ChamberWorld.WIDTH - ChamberWorld.RING_RADIUS - 0.01f)
    }
}

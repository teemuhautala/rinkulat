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

    private fun ChamberWorld.runMask(seconds: Float, mask: Int, gx: Float = 0f, gy: Float = 1f) {
        repeat((seconds / step).toInt()) { step(step, gx, gy, mask) }
    }

    @Test
    fun mechanicalMotionStaysInsideChamberAndResets() {
        for (level in 16..20) {
            val world = ChamberWorld(0, Random(1))
            world.reset(0, Tiers.layoutFor(level))
            var rotated = false
            var slid = false
            repeat(2400) {
                world.step(step, 0f, 1f, false)
                world.pegs.forEach { peg ->
                    rotated = rotated || kotlin.math.abs(peg.angle) > 0.1f
                    slid = slid || kotlin.math.abs(peg.x - peg.homeX) > 0.02f
                    val tipX = peg.x - kotlin.math.sin(peg.angle) * (peg.tipY - peg.baseY)
                    assertTrue(tipX > 0.05f && tipX < 0.95f)
                }
            }
            assertEquals(level != 16, rotated)
            assertEquals(level == 16 || level == 18, slid)
            world.reset()
            assertEquals(0f, world.clock, 0f)
            world.pegs.forEachIndexed { index, peg ->
                assertEquals(peg.homeX, peg.x, 0f)
                assertEquals(peg.homeBaseY, peg.baseY, 0f)
                assertEquals(peg.homeTipY, peg.tipY, 0f)
                assertEquals(0f, peg.velocityY, 0f)
                assertEquals(if (level == 20) index * ChamberWorld.SPIN_PHASE else 0f, peg.angle, 0f)
            }
        }
    }

    @Test
    fun verticalPegMotionHasOppositePhasesAndKeepsItsLength() {
        for (level in 19..20) {
            val world = ChamberWorld(0, Random(1))
            world.reset(0, Tiers.layoutFor(level))
            var rose = false
            var fell = false
            repeat(2400) {
                world.step(step, 0f, 1f, false)
                val left = world.pegs[0]
                val right = world.pegs[1]
                val offset = left.baseY - left.homeBaseY
                rose = rose || offset < -0.09f
                fell = fell || offset > 0.09f
                assertEquals(-offset, right.baseY - right.homeBaseY, 1e-6f)
                assertEquals(-left.velocityY, right.velocityY, 0.0001f)
                for (peg in world.pegs) {
                    assertEquals(peg.homeBaseY - peg.homeTipY, peg.baseY - peg.tipY, 1e-6f)
                    assertTrue(kotlin.math.abs(peg.baseY - peg.homeBaseY) <= ChamberWorld.PEG_VERTICAL_TRAVEL + 1e-6f)
                }
            }
            assertTrue(rose && fell)
        }
    }

    @Test
    fun fullRotationHasStaggeredPhasesAndClearanceThroughoutSeveralTurns() {
        val world = ChamberWorld(0, Random(1))
        world.reset(0, Tiers.layoutFor(20))
        val initialAngles = world.pegs.map { it.angle }
        repeat(6000) {
            val previousAngles = world.pegs.map { it.angle }
            world.step(step, 0f, 1f, false)
            val radii = world.pegs.mapIndexed { index, peg ->
                assertTrue(peg.angle > previousAngles[index])
                assertEquals(ChamberWorld.SPIN_SPEED, peg.angularVelocity, 0.001f)
                assertEquals(peg.homeX, peg.x, 0f)
                assertEquals(ChamberWorld.SPIN_PEG_LENGTH, peg.baseY - peg.tipY, 1e-6f)
                // A bounding circle covers both the shaft/tip and every corner of the mount.
                val mountRadius = kotlin.math.sqrt(
                    ChamberWorld.MOUNT_HEIGHT * ChamberWorld.MOUNT_HEIGHT +
                        ChamberWorld.MOUNT_HALF_WIDTH * ChamberWorld.MOUNT_HALF_WIDTH,
                )
                val radius = maxOf(peg.baseY - peg.tipY + ChamberWorld.PEG_RADIUS, mountRadius)
                assertTrue(peg.x - radius > 0f && peg.x + radius < ChamberWorld.WIDTH)
                assertTrue(peg.baseY - radius > 0f && peg.baseY + radius < ChamberWorld.HEIGHT)
                radius
            }
            assertTrue(radii.sum() < world.pegs[1].x - world.pegs[0].x)
            assertEquals(ChamberWorld.SPIN_PHASE, world.pegs[1].angle - world.pegs[0].angle, 1e-5f)
            // Horizontal is cos(angle) == 0; the two cannot approach it simultaneously.
            assertTrue(world.pegs.any { kotlin.math.abs(kotlin.math.cos(it.angle)) > 0.3f })
        }
        world.pegs.forEachIndexed { index, peg ->
            assertTrue(peg.angle - initialAngles[index] > 6f * PI.toFloat())
        }
        val capacity = world.pegs.sumOf { ((it.baseY - it.tipY) / ChamberWorld.RING_STACK_HEIGHT + 1e-3f).toInt() }
        assertTrue(capacity >= Tiers.ringsFor(20))
        assertTrue(capacity >= ChamberWorld.DEFAULT_RING_COUNT)
    }

    @Test
    fun ringsCatchOnTiltedMovingTips() {
        for (level in 16..20) {
            val world = ChamberWorld(1, Random(12))
            world.reset(1, Tiers.layoutFor(level))
            world.run(1.5f)
            val peg = world.pegs.first()
            val ring = world.rings.single()
            val distance = peg.tipY - peg.baseY - 0.025f
            ring.x = peg.x - kotlin.math.sin(peg.angle) * distance
            ring.y = peg.baseY + kotlin.math.cos(peg.angle) * distance
            ring.phi = PI.toFloat() / 2f
            ring.roll = peg.angle
            ring.vx = peg.velocityX - peg.angularVelocity * (ring.y - peg.baseY)
            ring.vy = peg.velocityY + peg.angularVelocity * (ring.x - peg.x)
            world.step(step, 0f, 1f, false)
            assertTrue("level $level catches at its moving tip", ring.isCaught)
        }
    }

    @Test
    fun rotatingMountsAreSolidAndRingsCanLeaveMovingTips() {
        val world = ChamberWorld(1, Random(7))
        world.reset(1, Tiers.layoutFor(18))
        world.run(1.5f)
        val peg = world.pegs.first()
        val ring = world.rings.single()
        val distance = ChamberWorld.MOUNT_HEIGHT / 2f
        ring.x = peg.x - kotlin.math.sin(peg.angle) * distance
        ring.y = peg.baseY + kotlin.math.cos(peg.angle) * distance
        ring.phi = PI.toFloat() / 2f
        ring.roll = peg.angle
        ring.vx = 0f
        ring.vy = 0f
        world.step(step, 0f, 1f, false)
        val across = (ring.x - peg.x) * kotlin.math.cos(peg.angle) +
            (ring.y - peg.baseY) * kotlin.math.sin(peg.angle)
        assertTrue(kotlin.math.abs(across) >= ChamberWorld.MOUNT_HALF_WIDTH)
        assertFalse(ring.isCaught)

        ring.peg = 0
        val offTip = peg.tipY - peg.baseY - ChamberWorld.RELEASE_MARGIN - 0.01f
        ring.x = peg.x - kotlin.math.sin(peg.angle) * offTip
        ring.y = peg.baseY + kotlin.math.cos(peg.angle) * offTip
        world.step(step, 0f, 1f, false)
        assertFalse("rings can be pushed off a moving peg", ring.isCaught)
    }

    @Test
    fun movingStacksTravelWithPegsAndCanWin() {
        for (level in 16..20) {
            val world = ChamberWorld(3, Random(17))
            world.reset(3, Tiers.layoutFor(level))
            world.rings.forEachIndexed { i, ring ->
                ring.peg = 0
                ring.x = world.pegs.first().x
                ring.y = ChamberWorld.PEG_BASE_Y - 0.04f - i * ChamberWorld.RING_STACK_HEIGHT
                ring.phi = PI.toFloat() / 2f
            }
            world.run(8f)
            assertTrue("level $level can settle and win", world.won)
            val peg = world.pegs.first()
            world.rings.forEach { ring ->
                assertTrue(ring.isCaught)
                val across = (ring.x - peg.x) * kotlin.math.cos(peg.angle) +
                    (ring.y - peg.baseY) * kotlin.math.sin(peg.angle)
                assertEquals(0f, across, 1e-5f)
            }
        }
    }

    @Test
    fun onlyThePressedJetLiftsItsRing() {
        val world = ChamberWorld(ringCount = 2, random = Random(20))
        world.reset(2, Tiers.layoutFor(2))
        // Jets 1 and 2 are the quarter-width bottom jets.
        val (left, right) = world.rings
        left.x = world.layout.jets[1].x
        right.x = world.layout.jets[2].x
        world.rings.forEach { it.y = ChamberWorld.HEIGHT - ChamberWorld.RING_RADIUS }
        world.runMask(0.8f, mask = 1 shl 1)
        assertTrue("left rose to ${left.y}", left.y < ChamberWorld.HEIGHT - 0.4f)
        assertTrue("right stayed low at ${right.y}", right.y > ChamberWorld.HEIGHT - 0.3f)
    }

    @Test
    fun upsideDownRingsRiseToTheCeiling() {
        val world = ChamberWorld(ringCount = 1, random = Random(21))
        world.reset(1, Tiers.layoutFor(13).copy(weightless = false))
        val ring = world.rings.single()
        ring.x = 0.08f // Clear of pegs and obstacle.
        ring.y = ChamberWorld.HEIGHT / 2f
        world.run(10f)
        assertTrue("ring at ${ring.y}", ring.y < 0.1f)
    }

    @Test
    fun upsideDownPegCatchesRisingRingAndStacksTowardItsBase() {
        val world = ChamberWorld(ringCount = 1, random = Random(22))
        world.reset(1, Tiers.layoutFor(13).copy(weightless = false))
        val peg = world.pegs[0]
        assertTrue("tip hangs below base", peg.tipY > peg.baseY)
        val ring = world.rings.single()
        ring.x = peg.x
        ring.y = peg.tipY + 0.15f
        ring.phi = (PI / 2).toFloat()
        ring.roll = 0f
        world.run(4f)
        assertTrue(ring.isCaught)
        assertEquals(peg.baseY, ring.y, 0.001f)
        assertTrue(world.won)
    }

    @Test
    fun weightlessRingsDriftSlowlyButStillSettleOnPegs() {
        val normal = ChamberWorld(ringCount = 1, random = Random(26))
        val floating = ChamberWorld(ringCount = 1, random = Random(26))
        floating.reset(1, Tiers.layoutFor(11))
        assertTrue(floating.layout.weightless)
        for (world in listOf(normal, floating)) {
            world.rings.single().apply { x = 0.08f; y = 0.3f; vx = 0f; vy = 0f }
            world.run(0.5f)
        }
        val fell = normal.rings.single().y - 0.3f
        val drifted = floating.rings.single().y - 0.3f
        assertTrue("normal fell $fell, weightless drifted $drifted", drifted < fell * 0.4f)

        // A flat ring dropped onto a peg still slides down and settles, so the level can be won.
        val peg = floating.pegs[0]
        floating.rings.single().apply { x = peg.x; y = peg.tipY - 0.05f; vx = 0f; vy = 0.3f; phi = (PI / 2).toFloat(); roll = 0f }
        floating.run(15f)
        assertTrue(floating.rings.single().isCaught)
        assertTrue(floating.won)
    }

    @Test
    fun shakesPushRingsAgainstTheMotionAndHarderInStrongChapters() {
        fun push(multiplier: Float): Float {
            val world = ChamberWorld(ringCount = 1, random = Random(27))
            world.shakeMultiplier = multiplier
            val ring = world.rings.single().apply { x = 0.5f; y = 0.4f; vx = 0f; vy = 0f }
            world.shakeX = 1f // Phone jerked to the right.
            world.run(0.15f, gy = 0f)
            return ring.x - 0.5f
        }
        val normal = push(1f)
        val strong = push(Tiers.shakeFor(11))
        assertTrue("normal moved $normal", normal < -0.003f)
        assertTrue("strong moved $strong vs $normal", strong < normal * 1.8f)
        assertEquals(1f, Tiers.shakeFor(10), 0f)
    }

    @Test
    fun attractingObstaclesPullOnlyNearbyRings() {
        val world = ChamberWorld(ringCount = 1, random = Random(28))
        world.reset(1, Tiers.layoutFor(12))
        val obstacle = world.layout.obstacles.single()
        val ring = world.rings.single()
        val out = FloatArray(2)
        // Just right of the obstacle: pulled left, weaker than gravity so jets and tilt can free it.
        ring.x = obstacle.x + obstacle.radius + 0.02f; ring.y = obstacle.y
        world.obstaclePull(ring, obstacle, out)
        assertTrue("pull ${out[0]}", out[0] < -0.5f)
        assertTrue(-out[0] < ChamberWorld.EFFECTIVE_GRAVITY * 1.5f)
        // Out of reach: nothing.
        ring.x = obstacle.x + obstacle.radius + ChamberWorld.ATTRACT_REACH + 0.01f
        world.obstaclePull(ring, obstacle, out)
        assertEquals(0f, out[0], 0f)

        // A ring drifting past with no gravity bends toward the obstacle.
        fun passBy(layout: LevelLayout): Float {
            val w = ChamberWorld(ringCount = 1, random = Random(29))
            w.reset(1, layout)
            val r = w.rings.single().apply { x = obstacle.x + 0.15f; y = obstacle.y + 0.15f; vx = 0f; vy = -0.4f }
            w.run(0.6f, gy = 0f)
            return r.x
        }
        val pulled = passBy(world.layout)
        val plain = passBy(world.layout.copy(attractingObstacles = false))
        assertTrue("pulled x $pulled vs plain $plain", pulled < plain - 0.02f)
    }

    @Test
    fun wormholesCarryRingsAcrossKeepingVelocity() {
        val world = ChamberWorld(ringCount = 1, random = Random(30))
        val layout = Tiers.layoutFor(15)
        val (a, b) = layout.wormholes!!
        // No jets, gravity, obstacles or turbulence-free flow needed: just a ring flying straight into mouth A.
        world.reset(1, layout.copy(obstacles = emptyList()))
        val ring = world.rings.single().apply { x = a.x - a.radius - 0.01f; y = a.y; vx = 1.5f; vy = 0f }
        world.step(1f / 120f, 0f, 0f, pumping = false)
        world.step(1f / 120f, 0f, 0f, pumping = false)
        // Came out of B, on the far side, still heading right at (almost) the same speed.
        assertTrue("x ${ring.x} y ${ring.y}", kotlin.math.abs(ring.y - b.y) < b.radius && ring.x >= b.x)
        assertTrue("vx ${ring.vx}", ring.vx > 1.3f)
        // It keeps going and doesn't bounce back through.
        repeat(6) { world.step(1f / 120f, 0f, 0f, pumping = false) }
        assertTrue("ring went back to ${ring.y}", kotlin.math.abs(ring.y - b.y) < 0.1f)
        assertTrue(ring.x > b.x + b.radius)
    }

    @Test
    fun obstacleKeepsRingsOut() {
        val world = ChamberWorld(ringCount = 1, random = Random(23))
        world.reset(1, Tiers.layoutFor(6))
        val obstacle = world.layout.obstacles.single()
        val ring = world.rings.single()
        ring.x = obstacle.x + 0.01f
        ring.y = obstacle.y - 0.3f
        world.run(3f)
        val dx = ring.x - obstacle.x
        val dy = ring.y - obstacle.y
        assertTrue("ring passed below the obstacle", ring.y > obstacle.y)
        assertTrue(kotlin.math.sqrt(dx * dx + dy * dy) > obstacle.radius)
    }

    @Test
    fun rightCurrentCarriesRingsRight() {
        val world = ChamberWorld(ringCount = 1, random = Random(24))
        world.reset(1, LevelLayout(0, "test", LevelLayout.CLASSIC.jets, current = Current.Right))
        val ring = world.rings.single()
        ring.x = 0.2f
        ring.y = 0.2f
        world.run(2f, gy = 0f)
        assertTrue("ring x ${ring.x}", ring.x > 0.35f)
    }

    @Test
    fun shearCurrentFlowsOppositeWaysTopAndBottom() {
        val world = ChamberWorld(ringCount = 1, random = Random(25))
        world.reset(1, Tiers.layoutFor(10))
        val out = FloatArray(2)
        var top = 0f
        var bottom = 0f
        // Average over x to cancel the turbulence term.
        for (i in 1..19) {
            world.flowAt(i / 20f, 0.3f, out); top += out[0]
            world.flowAt(i / 20f, ChamberWorld.HEIGHT - 0.3f, out); bottom += out[0]
        }
        assertTrue("top $top", top > 0f)
        assertTrue("bottom $bottom", bottom < 0f)
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

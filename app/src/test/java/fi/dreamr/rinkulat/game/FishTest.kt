package fi.dreamr.rinkulat.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class FishTest {
    @Test fun nearestStackSelectionStillTakesItsExposedRing() {
        val w = ChamberWorld(4, Random(1))
        w.reset(4, Tiers.layoutFor(6))
        w.rings[0].apply { peg = 0; x = w.pegs[0].x; y = w.pegs[0].baseY }
        w.rings[1].apply { peg = 0; x = w.pegs[0].x; y = w.pegs[0].baseY - 0.08f }
        w.rings[2].apply { peg = 1; x = w.pegs[1].x; y = w.pegs[1].tipY }
        w.summonFish()
        w.fishActive = true
        w.fish!!.x = 0.08f
        w.fish!!.y = w.pegs[0].baseY
        w.stepFish(0.01f, 0)
        assertSame(w.rings[1], w.fish!!.target)
        assertEquals(-1f, w.fish!!.approachSide, 0f)
    }

    @Test fun fishActuallyReachesNearestPegFromOutsideWithBothPegsOccupied() {
        for (level in listOf(6, 13)) {
            for (nearPeg in 0..1) {
                val w = ChamberWorld(3, Random(1))
                w.reset(3, Tiers.layoutFor(level))
                for (index in 0..1) {
                    val peg = w.pegs[index]
                    w.rings[index].apply {
                        this.peg = index
                        x = peg.x
                        // The far ring is higher: that must not force a cross-chamber attack.
                        y = if (index == nearPeg) peg.baseY else peg.tipY + peg.sink * 0.01f
                        phi = kotlin.math.PI.toFloat() / 2f
                    }
                }
                w.summonFish()
                w.fishActive = true
                val fish = w.fish!!
                fish.x = if (nearPeg == 0) 0.08f else 0.92f
                fish.heading = if (nearPeg == 0) 0f else kotlin.math.PI.toFloat()
                var steps = 600
                while (!fish.pulling && steps-- > 0) {
                    w.rings[2].apply { peg = -1; x = 0.5f; y = 0.1f; vx = 0f; vy = 0f }
                    w.advance(ChamberWorld.STEP_DT, 0f, 1f, 0)
                    assertSame("must target the nearer occupied peg", w.rings[nearPeg], fish.target)
                }
                assertTrue("must physically reach the ring", fish.pulling)
                assertTrue("must stay on the outside at contact", if (nearPeg == 0) fish.x < w.rings[nearPeg].x else fish.x > w.rings[nearPeg].x)
            }
        }
    }

    @Test fun fishOnlyAppearsFromLevelSixAndResetClearsIt() {
        val w = ChamberWorld(2, Random(1))
        for (level in 1..Tiers.MAX_LEVEL) {
            w.reset(2, Tiers.layoutFor(level))
            assertNull(w.fish)
            w.summonFish()
            assertEquals(level >= 6, w.fish != null)
        }
        w.reset(2, Tiers.layoutFor(5))
        w.summonFish()
        assertNull(w.fish)
    }

    @Test fun onlyFocusedNearbyBurstsAffectFishForEveryJetDirection() {
        val w = world()
        for ((i, jet) in w.layout.jets.withIndex()) {
            w.jetStrengths.fill(1f)
            fun influence(along: Float, across: Float = 0f): Float = w.fishJetInfluence(
                i,
                jet.x + jet.dirX * along - jet.dirY * across,
                jet.y + jet.dirY * along + jet.dirX * across,
            )
            assertTrue(influence(0.2f) > influence(0.8f))
            assertTrue(influence(0.8f) > 0f)
            val width = jet.coneHalfWidthAt(0.4f)
            for (side in listOf(-1f, 1f)) {
                assertTrue(influence(0.4f, side * width * 0.15f) > 0f)
                assertTrue(influence(0.4f, side * width * 0.6f) > 0f)
                assertEquals(0f, influence(0.4f, side * width * 0.9f), 0f)
                assertEquals(0f, influence(0.4f, side * width), 0f)
                assertEquals(0f, influence(0.4f, side * width * 1.5f), 0f)
            }
            assertEquals(0f, influence(-0.01f), 0f)
            assertEquals(0f, influence(ChamberWorld.FISH_JET_REACH + 0.001f), 0f)
            assertEquals(0f, influence(jet.coneLength + 0.001f), 0f)
            w.jetStrengths[i] = 0.2f
            assertEquals(0f, influence(0.2f), 0f)
        }
        // The limit is nozzle-to-fish distance, not just distance along the cone axis.
        w.jetStrengths[0] = 1f
        val jet = w.layout.jets[0]
        val reach = ChamberWorld.FISH_JET_REACH
        assertTrue(w.fishJetInfluence(0, jet.x, jet.y - reach + 0.001f) > 0f)
        assertEquals(0f, w.fishJetInfluence(0, jet.x + 0.03f, jet.y - reach), 0f)
    }

    @Test fun excludedStreamsDoNotCountHitsOrMoveRoamingFish() {
        // Bottom center jet: outside cone, at its edge, and beyond the range limit.
        for ((x, y) in listOf(0.85f to 1.1f, 0.645f to 1.1f, 0.5f to 0.2f)) {
            for (roaming in listOf(false, true)) {
                val calm = world()
                val pumping = world()
                for (w in listOf(calm, pumping)) {
                    w.fish!!.x = x
                    w.fish!!.y = y
                    if (roaming) w.fish!!.cooldown = 30f
                }
                pumping.jetStrengths[0] = 1f
                calm.stepFish(0.01f, 0)
                pumping.stepFish(0.01f, 1)
                assertEquals(0, pumping.fish!!.contacts)
                assertTrue(pumping.fish!!.hits.isEmpty())
                assertEquals(calm.fish!!.x, pumping.fish!!.x, 0f)
                assertEquals(calm.fish!!.y, pumping.fish!!.y, 0f)
                assertEquals(calm.fish!!.heading, pumping.fish!!.heading, 0f)
            }
        }
    }

    @Test fun focusedStreamsReachFishBesideBothPegTips() {
        assertEquals(ChamberWorld.HEIGHT * 0.7f, ChamberWorld.FISH_JET_REACH, 0f)
        for (level in listOf(6, 13, 16, 19, 20)) {
            val w = world(level)
            for ((index, peg) in w.pegs.withIndex()) {
                val ring = w.rings[0]
                val along = peg.tipY - peg.baseY
                ring.peg = index
                ring.x = peg.x - sin(peg.angle) * along
                ring.y = peg.baseY + cos(peg.angle) * along
                val fish = w.fish!!
                fish.x = w.fishRingX(ring)
                fish.y = ring.y
                fish.hits.clear()
                fish.contacts = 0
                w.jetStrengths.fill(1f)
                val jet = w.layout.jets.indices.maxBy { w.fishJetInfluence(it, fish.x, fish.y) }
                assertTrue("level $level peg $index must be reachable", w.fishJetInfluence(jet, fish.x, fish.y) > 0f)
                w.stepFish(0.01f, 1 shl jet)
                assertEquals(1, fish.hits.size)
                assertTrue(fish.reactingToHit)
                assertFalse(fish.escaping)
            }
        }
    }

    @Test fun bothMiddleButtonsCanHitDuringTheWholeLiftOnEitherPeg() {
        for (level in listOf(6, 13)) {
            val w = world(level)
            val middleJets = w.layout.jets.indices.filter { w.layout.jets[it].slot == 2 }
            assertEquals(2, middleJets.size)
            for ((pegIndex, peg) in w.pegs.withIndex()) {
                for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                    val ring = w.rings[0]
                    ring.peg = pegIndex
                    ring.x = peg.x
                    ring.y = peg.baseY + (peg.tipY - peg.baseY) * fraction
                    for (jetIndex in middleJets) {
                        val fish = w.fish!!
                        fish.approachSide = if (pegIndex == 0) 1f else -1f
                        fish.x = w.fishRingX(ring)
                        fish.y = ring.y
                        fish.target = ring
                        fish.hits.clear()
                        fish.contacts = 0
                        fish.cooldown = 0f
                        w.jetStrengths.fill(0f)
                        // A normal short burst must work, not only a fully ramped stream.
                        w.jetStrengths[jetIndex] = 0.75f
                        val context = "level $level peg $pegIndex lift $fraction jet $jetIndex"
                        assertTrue(context, w.fishJetInfluence(jetIndex, fish.x, fish.y) > 0f)
                        w.stepFish(0.01f, 1 shl jetIndex)
                        assertEquals(context, 1, fish.hits.size)
                        assertTrue(context, fish.reactingToHit)
                        assertFalse(context, fish.escaping)
                    }
                }
            }
        }
    }

    @Test fun fishChoosesNearestSideOfEitherPegAndKeepsItDuringAttack() {
        for (level in listOf(6, 13, 19, 20)) {
            for (pegIndex in 0..1) {
                for (side in listOf(-1f, 1f)) {
                    val w = world(level)
                    val ring = w.rings[0]
                    val peg = w.pegs[pegIndex]
                    ring.peg = pegIndex
                    ring.x = peg.x
                    ring.y = peg.baseY
                    val fish = w.fish!!
                    fish.x = ring.x + side * 0.2f
                    fish.y = ring.y
                    w.stepFish(0.01f, 0)
                    assertSame(ring, fish.target)
                    assertEquals(side, fish.approachSide, 0f)
                    assertEquals(ring.x + side * ChamberWorld.FISH_RING_OFFSET, w.fishRingX(ring), 0.00001f)
                    // A dodge crossing the peg must not keep flipping the approach side.
                    fish.x = ring.x - side * 0.2f
                    w.stepFish(0.01f, 0)
                    assertEquals(side, fish.approachSide, 0f)
                    // A later attack chooses afresh from the fish's new position.
                    fish.target = null
                    w.stepFish(0.01f, 0)
                    assertEquals(-side, fish.approachSide, 0f)
                }
            }
        }
    }

    @Test fun successfulScareWaitsThirtySecondsFromThirdHitBeforeAnyNewTarget() {
        val w = world()
        w.swim(2f)
        w.hit()
        w.stepFish(0.125f, 0)
        w.hit()
        w.stepFish(0.125f, 0)
        w.hit()
        assertNull(w.fish!!.target)
        assertEquals(30f, w.fish!!.cooldown, 0f)
        w.jetStrengths.fill(0f)
        // Keep both pegs available and put the fish right next to a ring throughout
        // the cooldown. Neither proximity nor a different target may bypass it.
        w.rings[1].apply { peg = 1; x = w.pegs[1].x; y = w.pegs[1].baseY }
        repeat(239) {
            w.fish!!.x = w.rings[it % 2].x
            w.fish!!.y = w.rings[it % 2].y
            w.stepFish(0.125f, 0)
            assertNull(w.fish!!.target)
            assertFalse(w.fish!!.pulling)
        }
        assertEquals(0.125f, w.fish!!.cooldown, 0f)
        w.stepFish(0.125f, 0)
        assertEquals(0f, w.fish!!.cooldown, 0f)
        assertNotNull(w.fish!!.target)
    }

    @Test fun outsideApproachesCanBeHitByQuarterButtonsThroughoutLift() {
        for (level in listOf(6, 13)) {
            val w = world(level)
            w.jetStrengths.fill(0.75f)
            for ((pegIndex, peg) in w.pegs.withIndex()) {
                w.fish!!.approachSide = if (pegIndex == 0) -1f else 1f
                val jets = w.layout.jets.indices.filter { w.layout.jets[it].slot == if (pegIndex == 0) 1 else 3 }
                assertEquals(2, jets.size)
                for (fraction in listOf(0f, 0.5f, 1f)) {
                    val ring = w.rings[0]
                    ring.peg = pegIndex
                    ring.x = peg.x
                    ring.y = peg.baseY + (peg.tipY - peg.baseY) * fraction
                    for (jet in jets) {
                        assertTrue(w.fishJetInfluence(jet, w.fishRingX(ring), ring.y) > 0f)
                    }
                }
            }
        }
    }

    @Test fun newlyCaughtRingKeepsSinkingUntilFishArrives() {
        val w = world()
        val control = world().apply { fishActive = false }
        for (world in listOf(w, control)) {
            world.rings[0].apply {
                y = world.pegs[0].tipY + 0.01f
                vy = 0.2f
                phi = kotlin.math.PI.toFloat() / 2f
            }
            world.fish!!.x = 0.08f
            world.fish!!.y = 0.15f
        }
        val initialY = w.rings[0].y
        repeat(30) {
            for (world in listOf(w, control)) {
                world.rings[1].apply { peg = -1; x = 0.08f; y = 0.1f; vx = 0f; vy = 0f }
                world.step(ChamberWorld.STEP_DT, 0f, 1f, false)
            }
            assertEquals(control.rings[0].y, w.rings[0].y, 0.000001f)
            assertFalse(w.fish!!.pulling)
            assertEquals(0f, w.fish!!.attackTime, 0f)
        }
        assertTrue(w.rings[0].y > initialY)
        var steps = 600
        while (!w.fish!!.pulling && steps-- > 0) {
            w.rings[1].apply { peg = -1; x = 0.08f; y = 0.1f; vx = 0f; vy = 0f }
            w.step(ChamberWorld.STEP_DT, 0f, 1f, false)
        }
        assertTrue("fish must eventually reach the sinking ring", w.fish!!.pulling)
        val grabbedY = w.rings[0].y
        assertTrue("must grab the settled position, not snap back to the tip", grabbedY > initialY + 0.05f)
        assertTrue(w.fish!!.attackTime < 0.02f)
        repeat(30) {
            w.rings[1].apply { peg = -1; x = 0.08f; y = 0.1f; vx = 0f; vy = 0f }
            w.step(ChamberWorld.STEP_DT, 0f, 1f, false)
        }
        assertTrue(w.rings[0].y < grabbedY)
    }

    @Test fun scaredFishStopsPullingAndRingSettles() {
        val w = world()
        w.swim(3f)
        assertTrue(w.fish!!.pulling)
        repeat(3) {
            w.stepFish(0.01f, 0)
            w.hit()
        }
        assertFalse(w.fish!!.pulling)
        val liftedY = w.rings[0].y
        repeat(20) { w.step(ChamberWorld.STEP_DT, 0f, 1f, false) }
        assertEquals(0f, w.fish!!.attackTime, 0f)
        assertNull(w.fish!!.target)
        assertTrue(w.rings[0].y > liftedY)
        assertTrue(w.rings[0].isCaught)
    }

    private fun world(level: Int = 6): ChamberWorld = ChamberWorld(2, Random(1)).apply {
        reset(2, Tiers.layoutFor(level))
        rings[0].apply { peg = 0; x = pegs[0].x; y = pegs[0].baseY }
        summonFish()
        fishActive = true
        // Pull-duration tests start with the fish already beside the ring.
        fish!!.x = fishRingX(rings[0])
        fish!!.y = rings[0].y
    }

    private fun ChamberWorld.swim(seconds: Float, mask: Int = 0) {
        repeat((seconds / 0.01f).toInt()) { stepFish(0.01f, mask) }
    }

    private fun ChamberWorld.hit(mask: Int = 1) {
        fish!!.x = 0.5f
        fish!!.y = 1.2f
        jetStrengths.fill(1f)
        stepFish(0.01f, mask)
    }

    @Test fun threeBurstsScareButHeldStreamDoesNotRepeat() {
        val w = world()
        w.hit()
        repeat(30) { w.hit() }
        assertNotNull(w.fish!!.target)
        assertEquals(1, w.fish!!.hits.size)
        w.stepFish(0.01f, 0)
        w.hit()
        assertNotNull(w.fish!!.target)
        w.stepFish(0.01f, 0)
        w.hit()
        assertNull(w.fish!!.target)
        assertEquals(30f, w.fish!!.cooldown, 0.001f)
        assertTrue(w.rings[0].isCaught)
        w.jetStrengths.fill(0f)
        w.swim(29.9f)
        assertNull(w.fish!!.target)
        w.swim(0.2f)
        assertNotNull(w.fish!!.target)
    }

    @Test fun expiredHitsCannotScareAndAmbientFlowIsNotAHit() {
        val w = world()
        w.hit()
        w.swim(1.1f)
        w.hit()
        w.stepFish(0.01f, 0)
        w.hit()
        assertNotNull(w.fish!!.target)
        assertEquals(2, w.fish!!.hits.size)
    }

    @Test fun theftReleasesOneRingAndStartsCooldownAcrossPegLayouts() {
        for (level in listOf(6, 13, 16, 19, 20)) {
            val w = world(level)
            w.swim(9.9f)
            assertTrue(w.rings[0].isCaught)
            w.swim(0.2f)
            assertFalse(w.rings[0].isCaught)
            assertFalse(w.rings[0].settled)
            assertNull(w.fish!!.target)
            assertTrue(w.fish!!.cooldown > 29f)
            assertTrue(w.rings[0].x.isFinite() && w.rings[0].y.isFinite())
        }
    }

    @Test fun pullMovesAlongPegThroughoutAttackAndReleasesAtItsTip() {
        for (level in listOf(6, 13, 16, 19, 20)) {
            val w = world(level)
            val ring = w.rings[0]
            val peg = w.pegs[0]
            var previousProgress = 0f
            repeat(600) {
                // Keep an unfinished ring clear of the pegs so the level cannot end early.
                w.rings[1].apply { this.peg = -1; x = 0.08f; y = 0.1f; vx = 0f; vy = 0f }
                w.step(ChamberWorld.STEP_DT, 0f, 1f, false)
                val along = -(ring.x - peg.x) * sin(peg.angle) + (ring.y - peg.baseY) * cos(peg.angle)
                val across = (ring.x - peg.x) * cos(peg.angle) + (ring.y - peg.baseY) * sin(peg.angle)
                val progress = along / (peg.tipY - peg.baseY)
                assertTrue("pull must advance on level $level", progress > previousProgress)
                assertEquals(0f, across, 0.0001f)
                assertTrue(ring.isCaught)
                assertFalse(ring.settled)
                previousProgress = progress
            }
            assertEquals(0.5f, previousProgress, 0.002f)
            var remainingSteps = 610
            while (ring.isCaught && remainingSteps-- > 0) {
                w.rings[1].apply { this.peg = -1; x = 0.08f; y = 0.1f; vx = 0f; vy = 0f }
                w.step(ChamberWorld.STEP_DT, 0f, 1f, false)
            }
            assertFalse(ring.isCaught)
            assertTrue("must not release early", w.clock >= 9.99f)
            assertEquals(peg.x - sin(peg.angle) * (peg.tipY - peg.baseY), ring.x, 0.0001f)
            assertEquals(peg.baseY + cos(peg.angle) * (peg.tipY - peg.baseY), ring.y, 0.0001f)
            assertEquals(30f, w.fish!!.cooldown, 0.001f)
        }
    }

    @Test fun scaringMidPullLeavesRingOnPegToSettleAgain() {
        val w = world()
        w.swim(5f)
        val ring = w.rings[0]
        val liftedY = ring.y
        assertTrue(liftedY < w.pegs[0].baseY - 0.1f)
        repeat(3) {
            w.stepFish(0.01f, 0)
            w.hit()
        }
        assertNull(w.fish!!.target)
        assertTrue(ring.isCaught)
        repeat(120) { w.step(ChamberWorld.STEP_DT, 0f, 1f, false) }
        assertTrue(ring.y > liftedY)
        assertTrue(ring.isCaught)
    }

    @Test fun waitsForRingAndPausesAndResets() {
        val w = world()
        w.rings[0].peg = -1
        w.swim(11f)
        assertNull(w.fish!!.target)
        w.rings[0].peg = 0
        w.swim(0.1f)
        assertNotNull(w.fish!!.target)
        w.fishActive = false
        val age = w.fish!!.age
        w.swim(20f)
        assertEquals(age, w.fish!!.age, 0f)
        w.reset()
        assertNull(w.fish)
        assertFalse(w.fishActive)
    }

    @Test fun roamingStaysInsideChamberAndStreamsChangeItsCourse() {
        val calm = world()
        val pushed = world()
        for (w in listOf(calm, pushed)) {
            w.fish!!.cooldown = 30f
            w.fish!!.x = 0.5f
            w.fish!!.y = 1.2f
        }
        pushed.jetStrengths[0] = 1f
        calm.swim(0.1f)
        pushed.swim(0.1f, 1)
        val dx = calm.fish!!.x - pushed.fish!!.x
        val dy = calm.fish!!.y - pushed.fish!!.y
        assertTrue("a focused burst must displace the fish", dx * dx + dy * dy > 0.0001f)
        assertTrue(kotlin.math.abs(calm.fish!!.heading - pushed.fish!!.heading) > 0.001f)
        pushed.rings.forEach { it.peg = -1 }
        pushed.swim(120f, 1)
        assertTrue(pushed.fish!!.x in 0.06f..0.94f)
        assertTrue(pushed.fish!!.y in 0.04f..1.46f)
    }

    @Test fun roamingFishKeepsEscapingAfterTheBurstEnds() {
        for (index in world().layout.jets.indices) {
            val w = world()
            val jet = w.layout.jets[index]
            val fish = w.fish!!
            fish.x = jet.x + jet.dirX * 0.3f
            fish.y = jet.y + jet.dirY * 0.3f
            fish.heading = kotlin.math.atan2(jet.dirY, jet.dirX)
            fish.cooldown = 30f
            w.jetStrengths[index] = 1f
            w.stepFish(0.01f, 1 shl index)
            assertTrue(fish.escaping)
            w.jetStrengths[index] = 0f
            w.swim(0.25f)
            val across = -(fish.x - jet.x) * jet.dirY + (fish.y - jet.y) * jet.dirX
            assertTrue("jet $index must drive a clear sideways escape", kotlin.math.abs(across) > 0.08f)
            assertTrue(fish.escaping)
            assertNull(fish.target)
            w.swim(0.5f)
            assertFalse(fish.escaping)
        }
    }

    @Test fun actualBurstsHoldFishAtRingUntilThirdHitThenPreventReturnForThirtySeconds() {
        val w = world()
        val fish = w.fish!!
        val mask = 1 shl w.layout.jets.indexOfFirst { it.slot == 1 && !it.fromTop }
        fun frame(pressed: Int) {
            w.rings[1].apply { peg = -1; x = 0.08f; y = 0.1f; vx = 0f; vy = 0f }
            w.advance(ChamberWorld.STEP_DT, 0f, 1f, pressed)
        }
        frame(0)
        assertTrue(fish.pulling)
        repeat(2) { burst ->
            repeat(20) { frame(mask) }
            assertEquals(burst + 1, fish.hits.size)
            assertTrue(fish.reactingToHit)
            assertFalse(fish.escaping)
            assertTrue(fish.pulling)
            assertSame(w.rings[0], fish.target)
            repeat(9) { frame(0) }
        }
        var frames = 30
        while (fish.target != null && frames-- > 0) frame(mask)
        assertNull("third actual burst must cause a retreat", fish.target)
        assertTrue(fish.escaping)
        assertEquals(30f, fish.cooldown, 0.001f)
        assertFalse(fish.pulling)
        val startX = fish.x
        repeat(60) { frame(0) }
        assertTrue("retreat must visibly move away", kotlin.math.abs(fish.x - startX) > 0.08f)
        repeat(3528) {
            frame(0)
            assertNull("must not return before 30 seconds", fish.target)
            assertFalse(fish.pulling)
        }
        repeat(24) { frame(0) }
        assertNotNull("may attack again after 30 seconds", fish.target)
    }
}

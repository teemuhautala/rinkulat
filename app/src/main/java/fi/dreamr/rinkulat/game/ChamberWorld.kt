package fi.dreamr.rinkulat.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure-Kotlin simulation of a water ring toss toy.
 *
 * World units: the chamber spans x in [0, WIDTH] and y in [0, HEIGHT], y grows downward
 * (same as screen coordinates). The jet nozzle sits at the bottom center.
 */
class ChamberWorld(
    ringCount: Int = DEFAULT_RING_COUNT,
    private val random: Random = Random.Default,
) {
    class Ring(
        val colorIndex: Int,
        var x: Float,
        var y: Float,
    ) {
        var vx = 0f
        var vy = 0f

        /** Tumble angle about the horizontal screen axis: 0 = face-on, PI/2 = edge-on (lying flat). */
        var phi = 0f
        var omega = 0f

        /** Roll angle about the viewing (z) axis: 0 = level, otherwise the ring is drawn tilted in-plane. */
        var roll = 0f
        var rollOmega = 0f

        /** How vertical the ring's axis is (1 = lying perfectly flat, 0 = axis horizontal). */
        val flatness get() = abs(sin(phi) * cos(roll))

        /** Index into [pegs], or -1 while floating freely. */
        var peg = -1

        val isCaught get() = peg >= 0

        /** True while caught and resting on the peg base or on the ring below. */
        var settled = false
    }

    class Peg(val x: Float, val tipY: Float, val baseY: Float)

    class Particle(var x: Float, var y: Float, val size: Float, val isBubble: Boolean)

    val pegs = listOf(
        Peg(x = WIDTH * 0.3f, tipY = PEG_TIP_Y, baseY = PEG_BASE_Y),
        Peg(x = WIDTH * 0.7f, tipY = PEG_TIP_Y, baseY = PEG_BASE_Y),
    )

    var rings: List<Ring> = createRings(ringCount)
        private set

    val particles: List<Particle> = List(PARTICLE_COUNT) { i ->
        Particle(
            x = random.nextFloat() * WIDTH,
            y = random.nextFloat() * HEIGHT,
            size = 0.003f + random.nextFloat() * 0.006f,
            isBubble = i % 6 == 0,
        )
    }

    /** Jet strength in [0, 1]; ramps up while pumping and lingers after release. */
    var jet = 0f
        private set

    /** Seconds of play since the first ring was caught; frozen once won. */
    var elapsed = 0f
        private set

    var started = false
        private set

    var won = false
        private set

    val caughtCount get() = rings.count { it.isCaught }

    private var time = 0f
    private var accumulator = 0f

    /** Advance by a frame of [frameDt] seconds using fixed sub-steps. Gravity is in g units, screen axes. */
    fun advance(frameDt: Float, gravityX: Float, gravityY: Float, pumping: Boolean) {
        accumulator += min(frameDt, MAX_FRAME_DT)
        while (accumulator >= STEP_DT) {
            step(STEP_DT, gravityX, gravityY, pumping)
            accumulator -= STEP_DT
        }
    }

    fun step(dt: Float, gravityX: Float, gravityY: Float, pumping: Boolean) {
        time += dt
        // The clock starts with the first caught ring, so shaking the phone before pumping gains nothing.
        if (!started && rings.any { it.isCaught }) started = true
        if (started && !won) elapsed += dt

        jet = if (pumping) {
            jet + (1f - jet) * (1f - exp(-dt / JET_RISE_TIME))
        } else {
            jet * exp(-dt / JET_DECAY_TIME)
        }

        val gx = gravityX * EFFECTIVE_GRAVITY
        val gy = gravityY * EFFECTIVE_GRAVITY

        for (ring in rings) {
            if (ring.isCaught) stepCaught(ring, dt, gy) else stepFree(ring, dt, gx, gy)
        }
        collideRings()
        stackCaughtRings()
        for (ring in rings) if (!ring.isCaught) collideWalls(ring)
        stepParticles(dt)

        // The clock stops only once every ring has slid all the way down onto its stack.
        if (!won && started && rings.all { it.isCaught && it.settled }) won = true
    }

    /** Liquid velocity at a point, as (vx, vy) packed into [out]. */
    fun flowAt(x: Float, y: Float, out: FloatArray) {
        val dx = x - WIDTH / 2f
        val depth = (HEIGHT - y) / HEIGHT // 0 at the nozzle, 1 at the top
        val spread = PLUME_BASE_WIDTH + PLUME_WIDENING * depth
        val plume = exp(-(dx * dx) / (2f * spread * spread))
        val strength = jet * JET_SPEED * (1f - 0.55f * depth)

        var vx = 0f
        var vy = -strength * plume
        // Near the nozzle liquid is drawn in from the sides.
        vx -= jet * INFLOW_SPEED * sign(dx) * exp(-(HEIGHT - y) / 0.12f) * (1f - plume)
        // At the top the plume fans out toward the walls.
        vx += jet * SPREAD_SPEED * sign(dx) * exp(-y / 0.25f) * min(1f, abs(dx) / 0.1f)
        // Return flow down the side walls.
        val wallDist = min(x, WIDTH - x)
        vy += jet * RETURN_SPEED * exp(-(wallDist * wallDist) / (2f * 0.08f * 0.08f))

        // Gentle swirling turbulence, stronger while the jet runs.
        val turb = TURBULENCE * (0.15f + jet)
        vx += turb * sin(7.3f * y + 1.7f * time) * cos(4.1f * x - 1.1f * time)
        vy += turb * 0.6f * sin(6.1f * x + 2.3f * time) * cos(5.7f * y + 0.7f * time)

        out[0] = vx
        out[1] = vy
    }

    /** Scatter a fresh set of [ringCount] rings on the floor and clear timer and win state. */
    fun reset(ringCount: Int = rings.size) {
        rings = createRings(ringCount)
        jet = 0f
        elapsed = 0f
        started = false
        won = false
        accumulator = 0f
    }

    private val flow = FloatArray(2)
    private val flowSide = FloatArray(2)

    private fun stepFree(ring: Ring, dt: Float, gx: Float, gy: Float) {
        flowAt(ring.x, ring.y, flow)
        val flat = ring.flatness
        // A ring lying flat presents its broad side to vertical motion.
        val dragY = DRAG * (0.5f + 0.9f * flat)
        val dragX = DRAG * 0.7f
        ring.vx += (dragX * (flow[0] - ring.vx) + gx) * dt
        ring.vy += (dragY * (flow[1] - ring.vy) + gy) * dt
        ring.x += ring.vx * dt
        ring.y += ring.vy * dt

        // Shear in the flow spins the ring, falling rings drift toward lying flat, plus random wobble.
        flowAt(ring.x + 0.02f, ring.y, flowSide)
        val shear = (flowSide[1] - flow[1]) / 0.02f
        val relSpeed = sqrt((flow[0] - ring.vx).let { it * it } + (flow[1] - ring.vy).let { it * it })
        val speed = sqrt(ring.vx * ring.vx + ring.vy * ring.vy)
        val wobble = relSpeed + jet * 0.5f + IDLE_TUMBLE
        // The jet spins rings hardest low down and near the nozzle; high and off-center rings barely feel it.
        val jetSpin = jetSpinInfluence(ring.x, ring.y)
        val sideOffset = ((ring.x - WIDTH / 2f) / 0.1f).coerceIn(-1f, 1f)

        val torque = TUMBLE_SHEAR * shear +
            FLATTEN_TORQUE * speed * sin(2f * ring.phi) +
            TUMBLE_NOISE * wobble * randomSigned() +
            JET_TUMBLE_SPIN * jetSpin * randomSigned()
        ring.omega = (ring.omega + torque * dt) * exp(-ANGULAR_DAMPING * dt)
        ring.phi = wrapAngle(ring.phi + ring.omega * dt)

        // Roll: the plume pushes one side of an off-center ring harder, spinning it away from the center.
        val rollTorque = JET_ROLL_SPIN * jetSpin * (sideOffset + 0.5f * randomSigned()) -
            ROLL_FLATTEN_TORQUE * speed * sin(2f * ring.roll) +
            ROLL_NOISE * wobble * randomSigned()
        ring.rollOmega = (ring.rollOmega + rollTorque * dt) * exp(-ANGULAR_DAMPING * dt)
        ring.roll = wrapAngle(ring.roll + ring.rollOmega * dt)

        tryCatch(ring)
        if (!ring.isCaught) collidePegs(ring)
    }

    private fun tryCatch(ring: Ring) {
        if (ring.vy <= -0.05f) return
        if (ring.flatness < CATCH_MIN_FLATNESS) return
        for ((index, peg) in pegs.withIndex()) {
            val dx = abs(ring.x - peg.x)
            val above = peg.tipY - ring.y
            if (dx < CATCH_TOLERANCE && above > -0.01f && above < RING_RADIUS + PEG_RADIUS) {
                ring.peg = index
                ring.x = peg.x
                ring.vx = 0f
                return
            }
        }
    }

    private fun stepCaught(ring: Ring, dt: Float, gy: Float) {
        val peg = pegs[ring.peg]
        flowAt(ring.x, ring.y, flow)
        // Constrained to slide along the vertical peg; the liquid grips it less than a free ring.
        ring.vy += (DRAG * CAUGHT_FLOW_COUPLING * (flow[1] - ring.vy) + gy) * dt
        // Friction resists only upward pushes, so rings still slide down freely but the jet
        // has to overcome it before lifting them off.
        if (ring.vy < 0f) ring.vy = min(0f, ring.vy + PEG_FRICTION * dt)
        ring.y += ring.vy * dt
        ring.x = peg.x
        ring.vx = 0f
        // Settle toward lying flat on the peg.
        val target = if (ring.phi < PI.toFloat()) PI.toFloat() / 2f else PI.toFloat() * 1.5f
        ring.phi += (target - ring.phi) * min(1f, 6f * dt)
        ring.omega = 0f
        val rollTarget = when {
            ring.roll < PI.toFloat() / 2f -> 0f
            ring.roll < PI.toFloat() * 1.5f -> PI.toFloat()
            else -> 2f * PI.toFloat()
        }
        ring.roll = wrapAngle(ring.roll + (rollTarget - ring.roll) * min(1f, 6f * dt))
        ring.rollOmega = 0f

        if (ring.y < peg.tipY - RELEASE_MARGIN) {
            // Pushed off the top of the peg.
            ring.peg = -1
        }
    }

    /** Caught rings rest on the peg base and on top of each other. */
    private fun stackCaughtRings() {
        for ((index, peg) in pegs.withIndex()) {
            var floor = peg.baseY
            rings.filter { it.peg == index }
                .sortedByDescending { it.y }
                .forEach { ring ->
                    if (ring.y > floor) {
                        ring.y = floor
                        if (ring.vy > 0f) ring.vy = 0f
                    }
                    ring.settled = ring.y >= floor - SETTLE_TOLERANCE && ring.vy >= 0f
                    floor = ring.y - RING_STACK_HEIGHT
                }
        }
    }

    private fun collidePegs(ring: Ring) {
        for (peg in pegs) {
            // Closest point on the peg segment.
            val cy = ring.y.coerceIn(peg.tipY, peg.baseY)
            val dx = ring.x - peg.x
            val dy = ring.y - cy
            val dist = sqrt(dx * dx + dy * dy)
            val minDist = PEG_RADIUS + RING_RADIUS * ringExtent(ring, dx, dy, dist)
            if (dist < minDist) {
                val (nx, ny) = if (dist > 1e-5f) dx / dist to dy / dist else (if (random.nextBoolean()) 1f else -1f) to 0f
                val push = minDist - dist
                ring.x += nx * push
                ring.y += ny * push
                val vn = ring.vx * nx + ring.vy * ny
                if (vn < 0f) {
                    ring.vx -= (1f + RESTITUTION) * vn * nx
                    ring.vy -= (1f + RESTITUTION) * vn * ny
                }
                // Balanced on the tip: tip it off to one side.
                if (cy == peg.tipY && abs(dx) < 0.004f) ring.vx += (if (random.nextBoolean()) 0.05f else -0.05f)
            }
        }
    }

    /** Projected ring half-extent along a direction, as a fraction of its radius. */
    private fun ringExtent(ring: Ring, dx: Float, dy: Float, dist: Float): Float {
        if (dist < 1e-5f) return 1f
        return ringSupport(ring, dx / dist, dy / dist)
    }

    /**
     * Half-extent of the ring's projected (rolled) ellipse along unit direction (ux, uy),
     * as a fraction of the ring radius.
     */
    private fun ringSupport(ring: Ring, ux: Float, uy: Float): Float {
        val minor = max(abs(cos(ring.phi)), MIN_RING_PROFILE)
        val c = cos(ring.roll)
        val s = sin(ring.roll)
        val along = ux * c + uy * s
        val across = -ux * s + uy * c
        return sqrt(along * along + across * across * minor * minor)
    }

    /** How strongly the jet spins a ring at this position, in [0, 1]. */
    internal fun jetSpinInfluence(x: Float, y: Float): Float {
        val dx = x - WIDTH / 2f
        val lowness = y / HEIGHT
        return jet * exp(-(dx * dx) / (2f * JET_SPIN_WIDTH * JET_SPIN_WIDTH)) * (0.15f + 0.85f * lowness * lowness)
    }

    private fun randomSigned() = random.nextFloat() * 2f - 1f

    private fun createRings(count: Int): List<Ring> {
        // More rings need a taller pile to start from.
        val pileHeight = 0.25f + count * 0.008f
        return List(count) { i ->
            Ring(
                colorIndex = i % RING_COLOR_COUNT,
                x = RING_RADIUS + random.nextFloat() * (WIDTH - 2 * RING_RADIUS),
                y = HEIGHT - RING_RADIUS - random.nextFloat() * pileHeight,
            ).also {
                it.phi = random.nextFloat() * PI.toFloat()
                it.roll = randomRoll()
            }
        }
    }

    private fun randomRoll() = (random.nextFloat() - 0.5f) * 0.6f

    private fun collideRings() {
        for (i in rings.indices) {
            val a = rings[i]
            for (j in i + 1 until rings.size) {
                val b = rings[j]
                if (a.isCaught && b.isCaught) continue
                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist2 = dx * dx + dy * dy
                val minDist = RING_RADIUS * 2f * RING_OVERLAP
                if (dist2 >= minDist * minDist || dist2 < 1e-8f) continue
                val dist = sqrt(dist2)
                val nx = dx / dist
                val ny = dy / dist
                val push = (minDist - dist) * RING_SOFTNESS
                // Caught rings are held by the peg, so the free ring takes the whole push.
                val shareA = if (a.isCaught) 0f else if (b.isCaught) 1f else 0.5f
                val shareB = 1f - shareA
                a.x -= nx * push * shareA
                a.y -= ny * push * shareA
                b.x += nx * push * shareB
                b.y += ny * push * shareB
                val rel = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
                if (rel < 0f) {
                    val impulse = rel * (1f + RESTITUTION) * 0.5f
                    if (!a.isCaught) { a.vx += impulse * nx; a.vy += impulse * ny }
                    if (!b.isCaught) { b.vx -= impulse * nx; b.vy -= impulse * ny }
                }
            }
        }
    }

    private fun collideWalls(ring: Ring) {
        val halfW = RING_RADIUS * ringSupport(ring, 1f, 0f)
        val halfH = RING_RADIUS * ringSupport(ring, 0f, 1f)
        if (ring.x < halfW) {
            ring.x = halfW
            if (ring.vx < 0f) ring.vx = -ring.vx * RESTITUTION
        } else if (ring.x > WIDTH - halfW) {
            ring.x = WIDTH - halfW
            if (ring.vx > 0f) ring.vx = -ring.vx * RESTITUTION
        }
        if (ring.y < halfH) {
            ring.y = halfH
            if (ring.vy < 0f) ring.vy = -ring.vy * RESTITUTION
        } else if (ring.y > HEIGHT - halfH) {
            ring.y = HEIGHT - halfH
            if (ring.vy > 0f) ring.vy = -ring.vy * RESTITUTION
            ring.vx *= FLOOR_FRICTION
        }
    }

    private fun stepParticles(dt: Float) {
        for (p in particles) {
            flowAt(p.x, p.y, flow)
            val rise = if (p.isBubble) -0.08f else 0.01f
            p.x += flow[0] * dt
            p.y += (flow[1] + rise) * dt
            if (p.x < 0f) p.x += WIDTH else if (p.x > WIDTH) p.x -= WIDTH
            if (p.y < 0f) {
                p.y = HEIGHT
                p.x = if (p.isBubble) WIDTH / 2f + (random.nextFloat() - 0.5f) * 0.1f else random.nextFloat() * WIDTH
            } else if (p.y > HEIGHT) {
                p.y = 0f
            }
        }
    }

    private fun wrapAngle(a: Float): Float {
        val twoPi = 2f * PI.toFloat()
        var r = a % twoPi
        if (r < 0f) r += twoPi
        return r
    }

    companion object {
        const val WIDTH = 1f
        const val HEIGHT = 1.5f
        const val DEFAULT_RING_COUNT = 10
        const val RING_COLOR_COUNT = 5
        const val RING_RADIUS = 0.05f
        const val RING_TUBE = 0.014f
        const val PEG_RADIUS = 0.011f
        const val PEG_BASE_Y = HEIGHT * 0.62f

        /** Peg length fits 12 stacked rings per peg. */
        const val PEG_TIP_Y = PEG_BASE_Y - 0.245f
        const val PEG_COUNT = 2
        const val PARTICLE_COUNT = 60

        // Simulation timing.
        const val STEP_DT = 1f / 120f
        const val MAX_FRAME_DT = 0.05f

        // Tuning: liquid and jet.
        const val JET_SPEED = 3.2f
        const val JET_RISE_TIME = 0.12f
        const val JET_DECAY_TIME = 0.45f
        const val PLUME_BASE_WIDTH = 0.07f
        const val PLUME_WIDENING = 0.22f
        const val INFLOW_SPEED = 0.6f
        const val SPREAD_SPEED = 1.2f
        const val RETURN_SPEED = 0.7f
        const val TURBULENCE = 0.12f

        // Tuning: rings.
        const val EFFECTIVE_GRAVITY = 1.1f
        const val DRAG = 4f
        const val TUMBLE_SHEAR = 0.6f
        const val FLATTEN_TORQUE = 2.5f
        const val TUMBLE_NOISE = 40f

        /** Baseline wobble so rings keep tumbling a little even in still liquid. */
        const val IDLE_TUMBLE = 0.08f

        // Jet-driven spin, strongest for low rings near the nozzle (see jetSpinInfluence).
        const val JET_SPIN_WIDTH = 0.18f
        const val JET_TUMBLE_SPIN = 60f
        const val JET_ROLL_SPIN = 30f
        const val ROLL_FLATTEN_TORQUE = 1.5f
        const val ROLL_NOISE = 25f
        const val ANGULAR_DAMPING = 2.5f
        const val RESTITUTION = 0.2f
        const val FLOOR_FRICTION = 0.97f
        const val RING_OVERLAP = 0.8f
        const val RING_SOFTNESS = 0.3f
        const val MIN_RING_PROFILE = 0.2f

        // Tuning: pegs.
        const val CATCH_TOLERANCE = 0.03f
        const val CATCH_MIN_FLATNESS = 0.55f
        const val RING_STACK_HEIGHT = 0.02f
        const val RELEASE_MARGIN = 0.05f
        const val CAUGHT_FLOW_COUPLING = 0.6f
        const val PEG_FRICTION = 1.2f
        const val SETTLE_TOLERANCE = 0.001f

        /** How many rings fit on all pegs with the top ring still below the tip. */
        val RING_CAPACITY = PEG_COUNT * ((PEG_BASE_Y - PEG_TIP_Y) / RING_STACK_HEIGHT + 1e-3f).toInt()
    }
}

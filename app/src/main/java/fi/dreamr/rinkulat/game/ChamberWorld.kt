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
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Pure-Kotlin simulation of a water ring toss toy.
 *
 * World units: the chamber spans x in [0, WIDTH] and y in [0, HEIGHT], y grows downward
 * (same as screen coordinates). Jets, obstacles, currents and gravity direction come from [layout].
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

        /** Wormhole mouth the ring just came out of; it can't re-enter until it has left that mouth. */
        var wormholeExit: Wormhole? = null

        /** True while caught and resting on the peg base or on the ring below. */
        var settled = false
    }

    /** A peg from [baseY] to [tipY]. [sink] is the direction (+1 down, -1 up) caught rings slide toward the base. */
    class Peg(var x: Float, var tipY: Float, var baseY: Float) {
        val homeX = x
        val homeBaseY = baseY
        val homeTipY = tipY
        var angle = 0f
        var velocityX = 0f
        var velocityY = 0f
        var angularVelocity = 0f
        val sink = if (baseY > tipY) 1f else -1f

        /** Vertical extent of the mount block beyond the base end, which free rings bounce off. */
        val mountTop get() = if (sink > 0f) baseY else baseY - MOUNT_HEIGHT
        val mountBottom get() = mountTop + MOUNT_HEIGHT
    }

    class Fish {
        var x = 0.08f
        var y = HEIGHT / 2f
        var heading = 0f
        var age = 0f
        var target: Ring? = null
        var attackTime = 0f
        var cooldown = 0f
        internal var pullStartAlong = 0f
        internal var pullStartTime = 0f
        internal var pulling = false
        internal var approachSide = -1f
        internal var contacts = 0
        internal val hits = ArrayDeque<Float>()
        internal var escapeRemaining = 0f
        internal var escapeX = 0f
        internal var escapeY = 0f
        internal var escapeStrength = 0f
        internal var hitReactionRemaining = 0f
        val escaping get() = escapeRemaining > 0f
        val reactingToHit get() = hitReactionRemaining > 0f
    }

    var fish: Fish? = null
        private set
    var fishActive = false

    fun summonFish() {
        if (layout.number >= FISH_FIRST_LEVEL && fish == null && !won) fish = Fish()
    }

    /** Keep the side chosen on approach while following the ring and its moving peg. */
    internal fun fishRingX(ring: Ring): Float =
        ring.x + (fish?.approachSide ?: -1f) * FISH_RING_OFFSET

    /** Only the central part of a visible cone can affect the fish; distant jets cannot. */
    internal fun fishJetInfluence(index: Int, x: Float, y: Float): Float {
        val jet = layout.jets[index]
        val px = x - jet.x
        val py = y - jet.y
        val distance = sqrt(px * px + py * py)
        val along = px * jet.dirX + py * jet.dirY
        if (distance > FISH_JET_REACH || along < 0f || along > jet.coneLength) return 0f
        val across = -px * jet.dirY + py * jet.dirX
        val coreWidth = jet.coneHalfWidthAt(along) * FISH_JET_CORE_FRACTION
        val offset = abs(across) / coreWidth
        if (offset >= 1f) return 0f
        val influence = jetStrengths[index] * (1f - 0.5f * distance / FISH_JET_REACH) *
            (1f - 0.5f * offset * offset)
        return if (influence >= FISH_JET_MIN_INFLUENCE) influence else 0f
    }

    internal fun stepFish(dt: Float, pressedJets: Int) {
        val f = fish ?: return
        if (!fishActive || won) return
        f.age += dt
        f.hitReactionRemaining = (f.hitReactionRemaining - dt).coerceAtLeast(0f)
        f.escapeRemaining = (f.escapeRemaining - dt).coerceAtLeast(0f)
        f.cooldown = (f.cooldown - dt).coerceAtLeast(0f)
        if (f.target?.isCaught != true) {
            f.target = null
            f.pulling = false
            f.attackTime = 0f
        }
        if (f.target == null && f.cooldown == 0f && !f.escaping) {
            // Only the exposed ring of each stack is reachable. Choose the nearest
            // stack, rather than crossing the chamber because its ring sits higher.
            f.target = pegs.mapIndexedNotNull { index, peg ->
                rings.filter { it.peg == index }.maxByOrNull { ring ->
                    val along = -(ring.x - peg.x) * sin(peg.angle) + (ring.y - peg.baseY) * cos(peg.angle)
                    -along * peg.sink
                }
            }.minByOrNull { ring ->
                val side = if (f.x <= ring.x) -1f else 1f
                val dx = ring.x + side * FISH_RING_OFFSET - f.x
                val dy = ring.y - f.y
                dx * dx + dy * dy
            }
            f.target?.let { ring ->
                f.approachSide = if (f.x <= ring.x) -1f else 1f
            }
        }
        var contacts = 0
        var pushX = 0f
        var pushY = 0f
        var strongestHit = 0f
        for ((i, j) in layout.jets.withIndex()) {
            val influence = fishJetInfluence(i, f.x, f.y)
            if (influence > 0f) {
                val across = -(f.x - j.x) * j.dirY + (f.y - j.y) * j.dirX
                if (influence > strongestHit) {
                    var side = if (across >= 0f) 1f else -1f
                    // Leave across the cone, slightly downstream. Choose the other side
                    // if the nearest exit would drive the fish into a chamber wall.
                    val exitX = f.x - j.dirY * side * FISH_ESCAPE_DISTANCE
                    val exitY = f.y + j.dirX * side * FISH_ESCAPE_DISTANCE
                    if (exitX !in 0.06f..(WIDTH - 0.06f) || exitY !in 0.04f..(HEIGHT - 0.04f)) side = -side
                    f.escapeX = (f.x + (-j.dirY * side + j.dirX * 0.35f) * FISH_ESCAPE_DISTANCE)
                        .coerceIn(0.06f, WIDTH - 0.06f)
                    f.escapeY = (f.y + (j.dirX * side + j.dirY * 0.35f) * FISH_ESCAPE_DISTANCE)
                        .coerceIn(0.04f, HEIGHT - 0.04f)
                    // Roaming fish evade freely. An attacker holds its ground until
                    // the third hit, so every attack retreat means a full cooldown.
                    if (f.target == null) f.escapeRemaining = FISH_ESCAPE_SECONDS
                    f.escapeStrength = influence
                }
                val speed = if (j.big) JET_SPEED else SMALL_JET_SPEED
                pushX += j.dirX * speed * influence
                pushY += j.dirY * speed * influence
                strongestHit = max(strongestHit, influence)
                if (pressedJets and (1 shl i) != 0) contacts = contacts or (1 shl i)
            }
        }
        if (f.target != null) {
            val newHits = Integer.bitCount(contacts and f.contacts.inv())
            repeat(newHits) { f.hits.addLast(f.age) }
            if (newHits > 0) f.hitReactionRemaining = 0.2f
            while (f.hits.isNotEmpty() && f.age - f.hits.first() > FISH_HIT_WINDOW) f.hits.removeFirst()
            if (f.hits.size >= FISH_HITS_REQUIRED) f.escapeRemaining = FISH_ESCAPE_SECONDS
            f.target?.let { ring ->
                val dx = fishRingX(ring) - f.x
                val dy = ring.y - f.y
                val withinReach = dx * dx + dy * dy <= FISH_GRAB_DISTANCE * FISH_GRAB_DISTANCE
                if (f.escaping || !withinReach) {
                    f.pulling = false
                } else if (!f.pulling) {
                    // Until actual contact the ring continues to sink under normal physics.
                    // Re-grabbing after losing contact starts wherever it has since settled.
                    val peg = pegs[ring.peg]
                    f.pullStartAlong = -(ring.x - peg.x) * sin(peg.angle) +
                        (ring.y - peg.baseY) * cos(peg.angle)
                    f.pullStartTime = f.attackTime
                    f.pulling = true
                }
            }
            if (f.pulling && f.hits.size < FISH_HITS_REQUIRED) f.target?.let { ring ->
                f.attackTime += dt
                val peg = pegs[ring.peg]
                // Interpolate in the peg's frame so mechanisms carry the pull with them.
                // Only time spent actually pulling counts toward the ten-second theft.
                val progress = ((f.attackTime - f.pullStartTime) /
                    (FISH_ATTACK_SECONDS - f.pullStartTime).coerceAtLeast(dt)).coerceIn(0f, 1f)
                val tipAlong = peg.tipY - peg.baseY
                inPegFrame(ring, peg) {
                    ring.x = peg.x
                    ring.y = peg.baseY + f.pullStartAlong + (tipAlong - f.pullStartAlong) * progress
                    ring.vx = 0f
                    ring.vy = 0f
                    ring.settled = false
                }
            }
            if (f.hits.size >= FISH_HITS_REQUIRED || f.attackTime >= FISH_ATTACK_SECONDS) {
                if (f.hits.size < FISH_HITS_REQUIRED) f.target?.let { ring ->
                    // Let go at the tip with a small outward/sideways nudge, preserving
                    // the peg's surface velocity. Normal ring physics takes over next step.
                    val peg = pegs[ring.peg]
                    val side = if (peg.x < WIDTH / 2f) 1f else -1f
                    inPegFrame(ring, peg) {
                        ring.peg = -1
                        ring.settled = false
                        ring.vx = side * 0.2f
                        ring.vy = -peg.sink * 0.2f
                    }
                    ring.omega = 3f
                }
                f.target = null
                f.pulling = false
                // Start the full delay at this scare/theft, not at approach or first hit.
                f.cooldown = FISH_COOLDOWN_SECONDS
                f.attackTime = 0f
                f.hits.clear()
            }
        }
        f.contacts = contacts
        val target = f.target
        // Keep fleeing after leaving the stream core. Attacks only retreat on a
        // successful scare; the target is then cleared for the full cooldown.
        val dx = when {
            f.escaping -> f.escapeX - f.x
            target != null -> fishRingX(target) - f.x
            else -> 0.5f + 0.34f * sin(f.age * 0.47f) - f.x
        }
        val dy = when {
            f.escaping -> f.escapeY - f.y
            target != null -> target.y - f.y
            else -> 0.75f + 0.52f * cos(f.age * 0.31f) - f.y
        }
        val desired = kotlin.math.atan2(dy, dx)
        val turn = kotlin.math.atan2(sin(desired - f.heading), cos(desired - f.heading))
        f.heading += turn * min(1f, dt * if (f.escaping) FISH_ESCAPE_TURN_RATE else 6f)
        val distance = sqrt(dx * dx + dy * dy)
        val speed = when {
            f.escaping -> min(0.55f + 0.45f * f.escapeStrength, distance * 6f)
            target != null -> min(0.5f, distance * 4f)
            else -> 0.2f
        }
        // Use only qualifying jets here: the general flow includes distant plumes and return currents.
        val pushResponse = if (target == null) 0.18f else 0f
        f.x = (f.x + (cos(f.heading) * speed + pushX * pushResponse) * dt).coerceIn(0.06f, WIDTH - 0.06f)
        f.y = (f.y + (sin(f.heading) * speed + pushY * pushResponse) * dt).coerceIn(0.04f, HEIGHT - 0.04f)
    }

    class Particle(var x: Float, var y: Float, val size: Float, val isBubble: Boolean)

    var layout: LevelLayout = LevelLayout.CLASSIC
        private set

    var pegs: List<Peg> = createPegs()
        private set

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

    /** Per-jet strength in [0, 1]; ramps up while its button is held and lingers after release. */
    var jetStrengths = FloatArray(layout.jets.size)
        private set

    /** Strongest jet right now, for turbulence and visuals. */
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

    /** Device acceleration (shakes, nudges) in g units, screen axes, set each frame before [advance]. */
    var shakeX = 0f
    var shakeY = 0f

    /** How strongly rings react to [shakeX]/[shakeY]; set per chapter (1 = normal). */
    var shakeMultiplier = 1f

    /** Advance by a frame of [frameDt] seconds using fixed sub-steps. Gravity is in g units, screen axes. */
    fun advance(frameDt: Float, gravityX: Float, gravityY: Float, pressedJets: Int) {
        accumulator += min(frameDt, MAX_FRAME_DT)
        while (accumulator >= STEP_DT) {
            step(STEP_DT, gravityX, gravityY, pressedJets)
            accumulator -= STEP_DT
        }
    }

    fun advance(frameDt: Float, gravityX: Float, gravityY: Float, pumping: Boolean) =
        advance(frameDt, gravityX, gravityY, if (pumping) ALL_JETS else 0)

    fun step(dt: Float, gravityX: Float, gravityY: Float, pumping: Boolean) =
        step(dt, gravityX, gravityY, if (pumping) ALL_JETS else 0)

    /** One fixed step. Bit i of [pressedJets] holds jet i of the layout. */
    fun step(dt: Float, gravityX: Float, gravityY: Float, pressedJets: Int) {
        time += dt
        movePegs(dt)
        // The clock starts with the first caught ring, so shaking the phone before pumping gains nothing.
        if (!started && rings.any { it.isCaught }) started = true
        if (started && !won) elapsed += dt

        jet = 0f
        for (i in jetStrengths.indices) {
            val s = jetStrengths[i]
            jetStrengths[i] = if (pressedJets and (1 shl i) != 0) {
                s + (1f - s) * (1f - exp(-dt / JET_RISE_TIME))
            } else {
                s * exp(-dt / JET_DECAY_TIME)
            }
            jet = max(jet, jetStrengths[i])
        }

        // Weightless levels keep a faint pull so tilting still steers rings and caught rings still settle.
        val g = EFFECTIVE_GRAVITY * if (layout.weightless) WEIGHTLESS_GRAVITY else 1f
        // Shaking the phone moves the case; rings lag behind, i.e. are pushed opposite to the motion.
        // That's real-world motion, so it is neither flipped upside down nor weakened when weightless.
        val shake = EFFECTIVE_GRAVITY * SHAKE_RESPONSE * shakeMultiplier
        val gx = gravityX * g - shakeX * shake
        val gy = gravityY * g * (if (layout.upsideDown) -1f else 1f) - shakeY * shake

        for (ring in rings) {
            if (ring.isCaught) stepCaught(ring, dt, gx, gy) else stepFree(ring, dt, gx, gy)
        }
        collideRings()
        stackCaughtRings()
        for (ring in rings) if (!ring.isCaught) collideWalls(ring)
        stepParticles(dt, gravityX, gravityY * if (layout.upsideDown) -1f else 1f)
        stepFish(dt, pressedJets)

        // The clock stops only once every ring has slid all the way down onto its stack.
        if (!won && started && rings.all { it.isCaught && it.settled }) won = true
    }

    /** Liquid velocity at a point, as (vx, vy) packed into [out]. */
    fun flowAt(x: Float, y: Float, out: FloatArray) {
        var vx = 0f
        var vy = 0f
        val wallDist = min(x, WIDTH - x)
        val wallReturn = exp(-(wallDist * wallDist) / (2f * 0.08f * 0.08f))

        for ((i, j) in layout.jets.withIndex()) {
            val strength = jetStrengths[i]
            if (strength < 1e-4f) continue
            // Jet-local coordinates: along the jet, and across it (perpendicular = (-dirY, dirX)).
            val px = x - j.x
            val py = y - j.y
            val along = px * j.dirX + py * j.dirY
            val across = -px * j.dirY + py * j.dirX
            val reach = reachOf(j)
            val depth = (along / reach).coerceIn(0f, 1f) // 0 at the nozzle, 1 at the far wall
            val power = if (j.big) 1f else SMALL_JET_SPEED / JET_SPEED
            val spread = (if (j.big) PLUME_BASE_WIDTH else SMALL_PLUME_WIDTH) + PLUME_WIDENING * depth
            val plume = if (along < -0.02f) 0f else exp(-(across * across) / (2f * spread * spread))
            val speed = strength * JET_SPEED * power * (1f - 0.55f * depth)
            val side = sign(across)

            vx += j.dirX * speed * plume
            vy += j.dirY * speed * plume
            // Near the nozzle liquid is drawn in from the sides.
            val inflow = strength * power * INFLOW_SPEED * side * exp(-max(along, 0f) / 0.12f) * (1f - plume)
            // At the far wall the plume fans out sideways.
            val fanOut = strength * power * SPREAD_SPEED * side * exp(-(reach - along).coerceAtLeast(0f) / 0.25f) *
                min(1f, abs(across) / 0.1f)
            vx += (fanOut - inflow) * -j.dirY
            vy += (fanOut - inflow) * j.dirX
            // Return flow along the side walls, against the jet's vertical push.
            vy += -j.dirY * strength * power * RETURN_SPEED * wallReturn
        }

        vx += when (layout.current) {
            Current.None -> 0f
            Current.Right -> CURRENT_SPEED
            Current.Left -> -CURRENT_SPEED
            // Upper half flows right, lower half left, blended across the middle.
            Current.Shear -> CURRENT_SPEED * tanh((HEIGHT / 2f - y) / 0.05f)
        }

        // Gentle swirling turbulence, stronger while a jet runs.
        val turb = TURBULENCE * (0.15f + jet)
        vx += turb * sin(7.3f * y + 1.7f * time) * cos(4.1f * x - 1.1f * time)
        vy += turb * 0.6f * sin(6.1f * x + 2.3f * time) * cos(5.7f * y + 0.7f * time)

        out[0] = vx
        out[1] = vy
    }

    /** Scatter a fresh set of [ringCount] rings for [layout] and clear timer and win state. */
    fun reset(ringCount: Int = rings.size, layout: LevelLayout = this.layout) {
        fish = null
        fishActive = false
        this.layout = layout
        pegs = createPegs()
        rings = createRings(ringCount)
        jetStrengths = FloatArray(layout.jets.size)
        jet = 0f
        elapsed = 0f
        started = false
        won = false
        accumulator = 0f
        time = 0f
    }

    private val flow = FloatArray(2)
    private val flowSide = FloatArray(2)
    private val pull = FloatArray(2)
    private val spin = FloatArray(2)

    private fun stepFree(ring: Ring, dt: Float, gx: Float, gy: Float) {
        flowAt(ring.x, ring.y, flow)
        val flat = ring.flatness
        // A ring lying flat presents its broad side to vertical motion.
        val dragY = DRAG * (0.5f + 0.9f * flat)
        val dragX = DRAG * 0.7f
        var ax = gx
        var ay = gy
        if (layout.attractingObstacles) {
            for (obstacle in layout.obstacles) {
                obstaclePull(ring, obstacle, pull)
                ax += pull[0]
                ay += pull[1]
            }
        }
        ring.vx += (dragX * (flow[0] - ring.vx) + ax) * dt
        ring.vy += (dragY * (flow[1] - ring.vy) + ay) * dt
        ring.x += ring.vx * dt
        ring.y += ring.vy * dt
        layout.wormholes?.let { travelThrough(ring, it) }

        // Shear in the flow spins the ring, falling rings drift toward lying flat, plus random wobble.
        flowAt(ring.x + 0.02f, ring.y, flowSide)
        val shear = (flowSide[1] - flow[1]) / 0.02f
        val relSpeed = sqrt((flow[0] - ring.vx).let { it * it } + (flow[1] - ring.vy).let { it * it })
        val speed = sqrt(ring.vx * ring.vx + ring.vy * ring.vy)
        val wobble = relSpeed + jet * 0.5f + IDLE_TUMBLE
        // Jets spin rings hardest close to the nozzle and on the jet axis; far and off-axis rings barely feel it.
        jetSpin(ring.x, ring.y, spin)
        val jetSpin = spin[0]
        val sideDrive = spin[1]

        val torque = TUMBLE_SHEAR * shear +
            FLATTEN_TORQUE * speed * sin(2f * ring.phi) +
            TUMBLE_NOISE * wobble * randomSigned() +
            JET_TUMBLE_SPIN * jetSpin * randomSigned()
        ring.omega = (ring.omega + torque * dt) * exp(-ANGULAR_DAMPING * dt)
        ring.phi = wrapAngle(ring.phi + ring.omega * dt)

        // Roll: a plume pushes one side of an off-axis ring harder, spinning it away from the axis.
        val rollTorque = JET_ROLL_SPIN * (sideDrive + 0.5f * jetSpin * randomSigned()) -
            ROLL_FLATTEN_TORQUE * speed * sin(2f * ring.roll) +
            ROLL_NOISE * wobble * randomSigned()
        ring.rollOmega = (ring.rollOmega + rollTorque * dt) * exp(-ANGULAR_DAMPING * dt)
        ring.roll = wrapAngle(ring.roll + ring.rollOmega * dt)

        tryCatch(ring)
        if (!ring.isCaught) {
            for (peg in pegs) {
                inPegFrame(ring, peg) {
                    val cy = ring.y.coerceIn(min(peg.tipY, peg.baseY), max(peg.tipY, peg.baseY))
                    pushOut(ring, peg.x, cy, PEG_RADIUS, onTip = cy == peg.tipY)
                    pushOutOfMount(ring, peg)
                }
            }
            layout.obstacles.forEach { pushOut(ring, it.x, it.y, it.radius, onTip = true) }
        }
    }

    /**
     * Wormholes: a ring whose center gets inside one mouth reappears at the other with the same velocity.
     * It is placed on the opposite side of the exit mouth from where it entered, so it carries on out
     * in its direction of travel, and can't re-enter until it has cleared the exit.
     */
    private fun travelThrough(ring: Ring, pair: Pair<Wormhole, Wormhole>) {
        val exit = ring.wormholeExit
        if (exit != null) {
            if (inside(ring, exit)) return
            ring.wormholeExit = null
        }
        val (entry, target) = when {
            inside(ring, pair.first) -> pair.first to pair.second
            inside(ring, pair.second) -> pair.second to pair.first
            else -> return
        }
        ring.x = target.x - (ring.x - entry.x)
        ring.y = target.y - (ring.y - entry.y)
        ring.wormholeExit = target
    }

    private fun inside(ring: Ring, hole: Wormhole): Boolean {
        val dx = ring.x - hole.x
        val dy = ring.y - hole.y
        return dx * dx + dy * dy < hole.radius * hole.radius
    }

    /** Seconds simulated since creation; drives animated decorations like the wormhole swirl. */
    val clock get() = time

    /**
     * Pull of an attracting obstacle on a free ring, into [out]. Zero beyond [ATTRACT_REACH] from the
     * obstacle's surface, growing linearly to [ATTRACT_ACCEL] at contact: enough to bend a passing ring's
     * path, weak enough that a jet or a tilt frees a ring resting against it.
     */
    internal fun obstaclePull(ring: Ring, obstacle: Obstacle, out: FloatArray) {
        out[0] = 0f
        out[1] = 0f
        val dx = obstacle.x - ring.x
        val dy = obstacle.y - ring.y
        val dist = sqrt(dx * dx + dy * dy)
        val gap = dist - obstacle.radius
        if (gap >= ATTRACT_REACH || dist < 1e-4f) return
        val strength = ATTRACT_ACCEL * (1f - gap.coerceAtLeast(0f) / ATTRACT_REACH)
        out[0] = dx / dist * strength
        out[1] = dy / dist * strength
    }

    /** Transform contacts into the peg's moving frame, including its surface velocity. */
    private inline fun inPegFrame(ring: Ring, peg: Peg, action: () -> Unit) {
        val c = cos(peg.angle)
        val s = sin(peg.angle)
        val dx = ring.x - peg.x
        val dy = ring.y - peg.baseY
        val vx = ring.vx - peg.velocityX + peg.angularVelocity * dy
        val vy = ring.vy - peg.velocityY - peg.angularVelocity * dx
        ring.x = peg.x + c * dx + s * dy
        ring.y = peg.baseY - s * dx + c * dy
        ring.vx = c * vx + s * vy
        ring.vy = -s * vx + c * vy
        ring.roll = wrapAngle(ring.roll - peg.angle)
        action()
        val localX = ring.x - peg.x
        val localY = ring.y - peg.baseY
        val worldX = c * localX - s * localY
        val worldY = s * localX + c * localY
        val localVx = ring.vx
        ring.vx = c * localVx - s * ring.vy + peg.velocityX - peg.angularVelocity * worldY
        ring.vy = s * localVx + c * ring.vy + peg.velocityY + peg.angularVelocity * worldX
        ring.x = peg.x + worldX
        ring.y = peg.baseY + worldY
        ring.roll = wrapAngle(ring.roll + peg.angle)
    }

    private fun movePegs(dt: Float) {
        if (layout.pegMotion == PegMotion.Still) return
        for ((index, peg) in pegs.withIndex()) {
            val direction = if (index == 0) 1f else -1f
            val rotating = layout.pegMotion != PegMotion.Slide
            val sliding = layout.pegMotion == PegMotion.Slide || layout.pegMotion == PegMotion.Combined
            // Keep the angle unwrapped so crossing a full turn cannot spike angular velocity.
            val angle = when {
                layout.pegMotion == PegMotion.Spin -> time * SPIN_SPEED + index * SPIN_PHASE
                rotating -> direction * 0.48f * sin(time * 0.65f)
                else -> 0f
            }
            val x = peg.homeX + if (sliding) 0.10f * sin(time * 0.75f) else 0f
            val verticalOffset = if (layout.verticalPegMotion) {
                -direction * PEG_VERTICAL_TRAVEL * sin(time * PEG_VERTICAL_SPEED)
            } else 0f
            val baseY = peg.homeBaseY + verticalOffset
            // Carry attached rings with the mechanism, preserving their axial slide speed.
            val attached = rings.filter { it.peg == index }
            val distances = attached.map { (it.x - peg.x) * -sin(peg.angle) + (it.y - peg.baseY) * cos(peg.angle) }
            val speeds = attached.map {
                (it.vx - peg.velocityX) * -sin(peg.angle) + (it.vy - peg.velocityY) * cos(peg.angle)
            }
            val deltaAngle = angle - peg.angle
            peg.velocityX = (x - peg.x) / dt
            peg.velocityY = (baseY - peg.baseY) / dt
            peg.angularVelocity = deltaAngle / dt
            peg.x = x
            peg.baseY = baseY
            peg.tipY = peg.homeTipY + verticalOffset
            peg.angle = angle
            attached.forEachIndexed { i, ring ->
                val dx = -sin(angle) * distances[i]
                val dy = cos(angle) * distances[i]
                ring.x = x + dx
                ring.y = peg.baseY + dy
                ring.vx = -sin(angle) * speeds[i] + peg.velocityX - peg.angularVelocity * dy
                ring.vy = cos(angle) * speeds[i] + peg.velocityY + peg.angularVelocity * dx
                ring.roll = wrapAngle(ring.roll + deltaAngle)
            }
        }
    }

    private fun tryCatch(ring: Ring) {
        for ((index, peg) in pegs.withIndex()) {
            inPegFrame(ring, peg) {
                val beforeTip = (peg.tipY - ring.y) * peg.sink
                if (ring.flatness >= CATCH_MIN_FLATNESS && ring.vy * peg.sink > -0.05f &&
                    abs(ring.x - peg.x) < CATCH_TOLERANCE &&
                    beforeTip > -0.01f && beforeTip < RING_RADIUS + PEG_RADIUS
                ) {
                    ring.peg = index
                    ring.x = peg.x
                    ring.vx = 0f
                }
            }
            if (ring.isCaught) return
        }
    }

    private fun stepCaught(ring: Ring, dt: Float, gx: Float, gy: Float) {
        val peg = pegs[ring.peg]
        flowAt(ring.x, ring.y, flow)
        val alongFlow = -sin(peg.angle) * (flow[0] - peg.velocityX) + cos(peg.angle) * flow[1]
        val alongGravity = -sin(peg.angle) * gx + cos(peg.angle) * gy
        inPegFrame(ring, peg) {
            ring.vy += (DRAG * CAUGHT_FLOW_COUPLING * (alongFlow - ring.vy) + alongGravity) * dt
            val towardBase = ring.vy * peg.sink
            if (towardBase < 0f) ring.vy = min(0f, towardBase + PEG_FRICTION * dt) * peg.sink
            ring.y += ring.vy * dt
            ring.x = peg.x
            ring.vx = 0f
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
            if ((peg.tipY - ring.y) * peg.sink > RELEASE_MARGIN) ring.peg = -1
        }
    }

    /** Stack by distance along the peg, so tilted and moving stacks can still settle and win. */
    private fun stackCaughtRings() {
        for ((index, peg) in pegs.withIndex()) {
            var floor = peg.baseY
            rings.filter { it.peg == index }
                .sortedByDescending { ((it.x - peg.x) * -sin(peg.angle) + (it.y - peg.baseY) * cos(peg.angle)) * peg.sink }
                .forEach { ring ->
                    inPegFrame(ring, peg) {
                        if ((ring.y - floor) * peg.sink > 0f) {
                            ring.y = floor
                            if (ring.vy * peg.sink > 0f) ring.vy = 0f
                        }
                        ring.settled = (ring.y - floor) * peg.sink >= -SETTLE_TOLERANCE && ring.vy * peg.sink >= -1e-5f
                        floor = ring.y - RING_STACK_HEIGHT * peg.sink
                    }
                }
        }
    }

    /**
     * Push a free ring out of a round contact at ([cx], [cy]) with [radius] and bounce it.
     * [onTip] marks a contact a ring could balance on, which gets nudged off to one side.
     */
    private fun pushOut(ring: Ring, cx: Float, cy: Float, radius: Float, onTip: Boolean) {
        val dx = ring.x - cx
        val dy = ring.y - cy
        val dist = sqrt(dx * dx + dy * dy)
        val minDist = radius + RING_RADIUS * ringExtent(ring, dx, dy, dist)
        if (dist >= minDist) return
        val (nx, ny) = if (dist > 1e-5f) dx / dist to dy / dist else (if (random.nextBoolean()) 1f else -1f) to 0f
        val push = minDist - dist
        ring.x += nx * push
        ring.y += ny * push
        val vn = ring.vx * nx + ring.vy * ny
        if (vn < 0f) {
            ring.vx -= (1f + RESTITUTION) * vn * nx
            ring.vy -= (1f + RESTITUTION) * vn * ny
        }
        if (onTip && abs(dx) < 0.004f) ring.vx += (if (random.nextBoolean()) 0.05f else -0.05f)
    }

    /**
     * The mount is a rounded rectangle: the closest point on its inner (corner-radius-shrunk) rectangle
     * plus a round contact of the corner radius gives the same outline.
     */
    private fun pushOutOfMount(ring: Ring, peg: Peg) {
        val inset = MOUNT_HALF_WIDTH - MOUNT_CORNER
        val cx = ring.x.coerceIn(peg.x - inset, peg.x + inset)
        val cy = ring.y.coerceIn(peg.mountTop + MOUNT_CORNER, peg.mountBottom - MOUNT_CORNER)
        // The end away from the peg is a ledge a ring could balance on.
        val farEnd = if (peg.sink > 0f) peg.mountBottom - MOUNT_CORNER else peg.mountTop + MOUNT_CORNER
        pushOut(ring, cx, cy, MOUNT_CORNER, onTip = cy == farEnd)
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

    /** How strongly the jets spin a ring at this position, in [0, 1]. */
    internal fun jetSpinInfluence(x: Float, y: Float): Float {
        jetSpin(x, y, spin)
        return spin[0]
    }

    /** Writes total jet spin influence (0..1) and the signed roll drive from being off a jet's axis. */
    private fun jetSpin(x: Float, y: Float, out: FloatArray) {
        var influence = 0f
        var sideDrive = 0f
        for ((i, j) in layout.jets.withIndex()) {
            val strength = jetStrengths[i]
            if (strength < 1e-4f) continue
            val px = x - j.x
            val py = y - j.y
            val along = px * j.dirX + py * j.dirY
            if (along < -0.05f) continue
            val across = -px * j.dirY + py * j.dirX
            val closeness = 1f - (along / reachOf(j)).coerceIn(0f, 1f)
            val k = strength * exp(-(across * across) / (2f * JET_SPIN_WIDTH * JET_SPIN_WIDTH)) *
                (0.15f + 0.85f * closeness * closeness)
            influence += k
            sideDrive += k * (across / 0.1f).coerceIn(-1f, 1f)
        }
        out[0] = min(1f, influence)
        out[1] = sideDrive
    }

    /** Distance from a nozzle to the chamber boundary along its direction. */
    private fun reachOf(j: Jet): Float {
        val tx = when {
            j.dirX > 1e-4f -> (WIDTH - j.x) / j.dirX
            j.dirX < -1e-4f -> -j.x / j.dirX
            else -> Float.MAX_VALUE
        }
        val ty = when {
            j.dirY > 1e-4f -> (HEIGHT - j.y) / j.dirY
            j.dirY < -1e-4f -> -j.y / j.dirY
            else -> Float.MAX_VALUE
        }
        return max(min(tx, ty), 0.1f)
    }

    private fun randomSigned() = random.nextFloat() * 2f - 1f

    private fun createPegs(): List<Peg> {
        // Upside down mirrors the pegs vertically: they hang from above, tips pointing down.
        fun mirror(y: Float) = if (layout.upsideDown) HEIGHT - y else y
        val spinning = layout.pegMotion == PegMotion.Spin
        val tipY = if (spinning) PEG_BASE_Y - SPIN_PEG_LENGTH else PEG_TIP_Y
        return listOf(WIDTH * 0.3f, WIDTH * 0.7f).mapIndexed { index, x ->
            Peg(x, mirror(tipY), mirror(PEG_BASE_Y)).also {
                if (spinning) it.angle = index * SPIN_PHASE
            }
        }
    }

    private fun createRings(count: Int): List<Ring> {
        // More rings need a taller pile to start from.
        val pileHeight = 0.25f + count * 0.008f
        return List(count) { i ->
            val offset = RING_RADIUS + random.nextFloat() * pileHeight
            Ring(
                colorIndex = i % RING_COLOR_COUNT,
                x = RING_RADIUS + random.nextFloat() * (WIDTH - 2 * RING_RADIUS),
                y = if (layout.upsideDown) offset else HEIGHT - offset,
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
        // Floor and ceiling both get friction, since either can be "down".
        if (ring.y < halfH) {
            ring.y = halfH
            if (ring.vy < 0f) ring.vy = -ring.vy * RESTITUTION
            ring.vx *= FLOOR_FRICTION
        } else if (ring.y > HEIGHT - halfH) {
            ring.y = HEIGHT - halfH
            if (ring.vy > 0f) ring.vy = -ring.vy * RESTITUTION
            ring.vx *= FLOOR_FRICTION
        }
    }

    private fun stepParticles(dt: Float, gravityX: Float, gravityY: Float) {
        for (p in particles) {
            flowAt(p.x, p.y, flow)
            if (p.isBubble) {
                // Buoyancy opposes the rings' gravity, not the screen's vertical axis.
                // Keep in-plane gravity magnitude so a flat device has no artificial rise.
                // Space bubbles drift slowly even when carried by the liquid flow.
                val speedScale = if (layout.weightless) WEIGHTLESS_GRAVITY else 1f
                p.x += (flow[0] - gravityX * BUBBLE_RISE_SPEED) * speedScale * dt
                p.y += (flow[1] - gravityY * BUBBLE_RISE_SPEED) * speedScale * dt
            } else {
                p.x += flow[0] * dt
                p.y += (flow[1] + 0.01f) * dt
            }
            if (p.x < 0f) p.x += WIDTH else if (p.x > WIDTH) p.x -= WIDTH
            if (p.y < 0f) {
                p.y = HEIGHT
                // Bubbles come out of a nozzle.
                p.x = if (p.isBubble) {
                    (layout.jets.random(random).x + (random.nextFloat() - 0.5f) * 0.1f).coerceIn(0f, WIDTH)
                } else {
                    random.nextFloat() * WIDTH
                }
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
        const val FISH_FIRST_LEVEL = 6
        const val FISH_ESCAPE_SECONDS = 0.65f
        const val FISH_ESCAPE_DISTANCE = 0.35f
        const val FISH_ESCAPE_TURN_RATE = 18f
        const val FISH_JET_CORE_FRACTION = 0.75f
        const val FISH_JET_MIN_INFLUENCE = 0.3f
        const val FISH_RING_OFFSET = 0.07f
        const val FISH_GRAB_DISTANCE = 0.045f
        const val FISH_ATTACK_SECONDS = 10f
        const val FISH_COOLDOWN_SECONDS = 30f
        const val FISH_HIT_WINDOW = 1f
        const val FISH_HITS_REQUIRED = 3

        const val WIDTH = 1f
        const val HEIGHT = 1.5f
        const val FISH_JET_REACH = HEIGHT * 0.7f
        const val DEFAULT_RING_COUNT = 10
        const val RING_COLOR_COUNT = 5
        const val RING_RADIUS = 0.05f
        const val RING_TUBE = 0.014f
        const val PEG_RADIUS = 0.011f
        const val PEG_BASE_Y = HEIGHT * 0.62f

        /** Peg length fits 12 stacked rings per peg. */
        const val PEG_TIP_Y = PEG_BASE_Y - 0.245f
        const val PEG_COUNT = 2

        // Swept peg envelopes (including tip radius) fit between bases 0.4 units apart.
        const val PEG_VERTICAL_TRAVEL = 0.10f
        const val PEG_VERTICAL_SPEED = 0.75f // radians/s: one rise/fall cycle in about 8.4 seconds
        const val SPIN_PEG_LENGTH = 0.18f
        const val SPIN_SPEED = 0.4f // radians/s: one revolution in about 15.7 seconds
        const val SPIN_PHASE = PI.toFloat() / 4f // 45° apart, never horizontal together

        /** Peg mount block beyond the base end (also used for drawing). */
        const val MOUNT_HALF_WIDTH = PEG_RADIUS * 3f
        const val MOUNT_HEIGHT = PEG_RADIUS * 9f
        const val MOUNT_CORNER = PEG_RADIUS * 2f
        const val PARTICLE_COUNT = 60
        const val BUBBLE_RISE_SPEED = 0.08f

        /** Pressed-jets mask with every jet held. */
        const val ALL_JETS = -1

        // Simulation timing.
        const val STEP_DT = 1f / 120f
        const val MAX_FRAME_DT = 0.05f

        // Tuning: liquid and jets.
        const val JET_SPEED = 3.2f
        const val SMALL_JET_SPEED = 2.6f
        const val JET_RISE_TIME = 0.12f
        const val JET_DECAY_TIME = 0.45f
        const val PLUME_BASE_WIDTH = 0.07f
        const val SMALL_PLUME_WIDTH = 0.05f
        const val PLUME_WIDENING = 0.22f
        const val INFLOW_SPEED = 0.6f
        const val SPREAD_SPEED = 1.2f
        const val RETURN_SPEED = 0.7f
        const val TURBULENCE = 0.12f
        const val CURRENT_SPEED = 0.15f

        // Tuning: rings.
        const val EFFECTIVE_GRAVITY = 1.1f

        /** Fraction of the device's acceleration passed on to the rings (before the chapter multiplier). */
        const val SHAKE_RESPONSE = 0.6f

        /** Attracting obstacles: pull at contact (world units/s², gravity is [EFFECTIVE_GRAVITY]) and reach from the surface. */
        const val ATTRACT_ACCEL = 1.4f
        const val ATTRACT_REACH = 0.18f

        /** Fraction of gravity left on weightless levels. */
        const val WEIGHTLESS_GRAVITY = 0.12f
        const val DRAG = 4f
        const val TUMBLE_SHEAR = 0.6f
        const val FLATTEN_TORQUE = 2.5f
        const val TUMBLE_NOISE = 40f

        /** Baseline wobble so rings keep tumbling a little even in still liquid. */
        const val IDLE_TUMBLE = 0.08f

        // Jet-driven spin, strongest near the nozzle and on the jet axis (see jetSpin).
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

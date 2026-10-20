package fi.dreamr.rinkulat.game

import fi.dreamr.rinkulat.game.ChamberWorld.Companion.HEIGHT
import fi.dreamr.rinkulat.game.ChamberWorld.Companion.WIDTH
import kotlin.math.sqrt

/** A water jet: nozzle position in world units and a unit direction it shoots toward. */
data class Jet(val x: Float, val y: Float, val dirX: Float, val dirY: Float, val big: Boolean = false) {
    /** Nozzles in the upper half get their button above the chamber. */
    val fromTop get() = y < HEIGHT / 2f

    /** Button position in its strip: 0 = left corner, 1 = quarter, 2 = center, 3 = three quarters, 4 = right corner. */
    val slot get() = when {
        x < WIDTH * 0.18f -> 0
        x < WIDTH * 0.4f -> 1
        x < WIDTH * 0.6f -> 2
        x < WIDTH * 0.82f -> 3
        else -> 4
    }
}

/** A round bumper free rings bounce off. */
data class Obstacle(val x: Float, val y: Float, val radius: Float)

/** One mouth of a wormhole pair: a free ring whose center enters it comes out of the other mouth. */
data class Wormhole(val x: Float, val y: Float, val radius: Float)

enum class Current { None, Right, Left, Shear }

/**
 * The mechanics of one level: jets, obstacle, current and gravity. [number] places the level in the map;
 * theme, ring count and time come from the chapter (5-level block) that number falls in, see [Tiers].
 */
data class LevelLayout(
    val number: Int,
    val title: String,
    val jets: List<Jet>,
    val obstacles: List<Obstacle> = emptyList(),
    val current: Current = Current.None,
    /** Gravity points up and the pegs hang from above. */
    val upsideDown: Boolean = false,
    /** Rings barely sink: they drift with the jets, currents and a faint pull toward the tilted "down". */
    val weightless: Boolean = false,
    /** Obstacles pull nearby free rings toward them, like small planets. */
    val attractingObstacles: Boolean = false,
    /** Connected wormhole mouths; a ring entering either one leaves through the other. */
    val wormholes: Pair<Wormhole, Wormhole>? = null,
) {
    companion object {
        const val SLOTS_PER_STRIP = 5
        private const val CORNER_INSET = 0.04f
        private const val OBSTACLE_Y = 0.5f
        private const val SIDE_OBSTACLE_Y = 0.33f
        private const val WORMHOLE_RADIUS = 0.06f
        private const val OBSTACLE_RADIUS = 0.05f

        // Jet sets, named by what they contain. Each builds on the previous one without moving any jet.
        private val bottomJet = Jet(WIDTH / 2f, HEIGHT, 0f, -1f, big = true)
        private val threeBottomJets = listOf(
            bottomJet,
            Jet(WIDTH * 0.25f, HEIGHT, 0f, -1f),
            Jet(WIDTH * 0.75f, HEIGHT, 0f, -1f),
        )
        private val withTopCenterJet = threeBottomJets + Jet(WIDTH / 2f, 0f, 0f, 1f)
        private val withCornerJets = withTopCenterJet + listOf(
            Jet(WIDTH * 0.25f, 0f, 0f, 1f),
            Jet(WIDTH * 0.75f, 0f, 0f, 1f),
            aimedAtCenter(CORNER_INSET, HEIGHT - CORNER_INSET),
            aimedAtCenter(WIDTH - CORNER_INSET, HEIGHT - CORNER_INSET),
        )
        private val allJets = withCornerJets + listOf(
            aimedAtCenter(CORNER_INSET, CORNER_INSET),
            aimedAtCenter(WIDTH - CORNER_INSET, CORNER_INSET),
        )

        /** A corner nozzle pointing at the middle of the chamber. */
        private fun aimedAtCenter(x: Float, y: Float): Jet {
            val dx = WIDTH / 2f - x
            val dy = HEIGHT / 2f - y
            val length = sqrt(dx * dx + dy * dy)
            return Jet(x, y, dx / length, dy / length)
        }

        /** Round bumpers at (x, y as seen with normal gravity); mirrored vertically when upside down. */
        private fun obstacles(upsideDown: Boolean, vararg centers: Pair<Float, Float>) = centers.map { (x, y) ->
            Obstacle(x, if (upsideDown) HEIGHT - y else y, OBSTACLE_RADIUS)
        }

        private fun oneObstacle(upsideDown: Boolean) = obstacles(upsideDown, WIDTH / 2f to OBSTACLE_Y)

        /** The two side bumpers of [threeObstacles], without the center one. */
        private fun twoObstacles(upsideDown: Boolean) = obstacles(
            upsideDown,
            WIDTH * 0.22f to SIDE_OBSTACLE_Y,
            WIDTH * 0.78f to SIDE_OBSTACLE_Y,
        )

        /** A downward-pointing triangle: the center bumper plus two higher up on the sides. */
        private fun threeObstacles(upsideDown: Boolean) = obstacles(
            upsideDown,
            WIDTH / 2f to OBSTACLE_Y,
            WIDTH * 0.22f to SIDE_OBSTACLE_Y,
            WIDTH * 0.78f to SIDE_OBSTACLE_Y,
        )

        /**
         * A wormhole pair in the middle of the chamber, mirrored across its center: one mouth where the
         * center bumper would be, the other on the far side of the pegs.
         */
        private val centerWormholes = Wormhole(WIDTH / 2f, OBSTACLE_Y, WORMHOLE_RADIUS) to
            Wormhole(WIDTH / 2f, HEIGHT - OBSTACLE_Y, WORMHOLE_RADIUS)

        /**
         * Every level in the map. To move a level, change its [number]; list order doesn't matter.
         * Numbers must run 1..size without gaps (checked by a unit test).
         */
        val LEVELS: List<LevelLayout> = listOf(
            // Chapter 1: building up the jets.
            LevelLayout(number = 1, title = "Classic", jets = listOf(bottomJet)),
            LevelLayout(number = 2, title = "Three jets", jets = threeBottomJets),
            LevelLayout(number = 3, title = "A jet from above", jets = withTopCenterJet),
            LevelLayout(number = 4, title = "Corner jets", jets = withCornerJets),
            LevelLayout(number = 5, title = "Full set", jets = allJets),
            // Chapter 2: obstacles and currents.
            LevelLayout(number = 6, title = "Watch the obstacle", jets = allJets, obstacles = oneObstacle(false)),
            LevelLayout(
                number = 7,
                title = "Current to the right",
                jets = allJets,
                obstacles = oneObstacle(false),
                current = Current.Right,
            ),
            LevelLayout(number = 8, title = "Three obstacles", jets = allJets, obstacles = threeObstacles(false)),
            LevelLayout(
                number = 9,
                title = "Obstacles in the current",
                jets = allJets,
                obstacles = threeObstacles(false),
                current = Current.Right,
            ),
            LevelLayout(
                number = 10,
                title = "Split currents",
                jets = allJets,
                obstacles = threeObstacles(false),
                current = Current.Shear,
            ),
            // Chapter 3: weightless throughout; attracting obstacles from 12, upside down from 13.
            LevelLayout(number = 11, title = "Weightless", jets = allJets, weightless = true),
            LevelLayout(
                number = 12,
                title = "Pulling planet",
                jets = allJets,
                obstacles = oneObstacle(false),
                attractingObstacles = true,
                weightless = true,
            ),
            LevelLayout(
                number = 13,
                title = "Upside-down planet",
                jets = allJets,
                obstacles = oneObstacle(true),
                upsideDown = true,
                attractingObstacles = true,
                weightless = true,
            ),
            LevelLayout(
                number = 14,
                title = "Two planets",
                jets = allJets,
                obstacles = twoObstacles(true),
                upsideDown = true,
                attractingObstacles = true,
                weightless = true,
            ),
            LevelLayout(
                number = 15,
                title = "Wormholes",
                jets = allJets,
                obstacles = twoObstacles(true),
                wormholes = centerWormholes,
                upsideDown = true,
                attractingObstacles = true,
                weightless = true,
            ),
        ).sortedBy { it.number }

        /** The single-jet toy; the world's default layout. */
        val CLASSIC = LEVELS.first()
    }
}

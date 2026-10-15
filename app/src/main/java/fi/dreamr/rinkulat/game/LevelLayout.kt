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

enum class Current { None, Right, Left, Shear }

/** Everything that changes the toy between tier levels. */
data class LevelLayout(
    val title: String,
    val jets: List<Jet>,
    val obstacle: Obstacle? = null,
    val current: Current = Current.None,
    /** Gravity points up and the pegs hang from above. */
    val upsideDown: Boolean = false,
) {
    companion object {
        const val SLOTS_PER_STRIP = 5
        private const val CORNER_INSET = 0.04f
        private const val OBSTACLE_Y = 0.5f
        private const val OBSTACLE_RADIUS = 0.05f

        private val bottomCenter = Jet(WIDTH / 2f, HEIGHT, 0f, -1f, big = true)
        val CLASSIC = LevelLayout("Classic", listOf(bottomCenter))

        // Each level keeps the previous level's jets, unchanged, and adds more.
        private val level2Jets = listOf(
            bottomCenter,
            Jet(WIDTH * 0.25f, HEIGHT, 0f, -1f),
            Jet(WIDTH * 0.75f, HEIGHT, 0f, -1f),
        )
        private val level3Jets = level2Jets + Jet(WIDTH / 2f, 0f, 0f, 1f)
        private val level4Jets = level3Jets + listOf(
            Jet(WIDTH * 0.25f, 0f, 0f, 1f),
            Jet(WIDTH * 0.75f, 0f, 0f, 1f),
            aimedAtCenter(CORNER_INSET, HEIGHT - CORNER_INSET),
            aimedAtCenter(WIDTH - CORNER_INSET, HEIGHT - CORNER_INSET),
        )
        private val allJets = level4Jets + listOf(
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

        private fun obstacle(upsideDown: Boolean) =
            Obstacle(WIDTH / 2f, if (upsideDown) HEIGHT - OBSTACLE_Y else OBSTACLE_Y, OBSTACLE_RADIUS)

        fun forTier(level: Int): LevelLayout = when (level) {
            1 -> CLASSIC
            2 -> LevelLayout("Three jets", level2Jets)
            3 -> LevelLayout("A jet from above", level3Jets)
            4 -> LevelLayout("Corner jets", level4Jets)
            5 -> LevelLayout("Full set", allJets)
            6 -> LevelLayout("Watch the obstacle", allJets, obstacle(false))
            7 -> LevelLayout("Current to the right", allJets, obstacle(false), Current.Right)
            8 -> LevelLayout("Upside down", allJets, obstacle(true), upsideDown = true)
            9 -> LevelLayout("Current to the left", allJets, obstacle(true), Current.Left, upsideDown = true)
            else -> LevelLayout("Split currents", allJets, obstacle(true), Current.Shear, upsideDown = true)
        }
    }
}

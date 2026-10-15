package fi.dreamr.rinkulat.game

import kotlin.math.ceil

/** Rules for tier mode: each level adds rings and some time, up to what the pegs can hold. */
object Tiers {
    const val START_RINGS = 5
    const val START_SECONDS = 60
    const val RINGS_PER_LEVEL = 2
    const val SECONDS_PER_LEVEL = 10
    const val POINTS_PER_RING = 100
    const val POINTS_PER_SECOND_LEFT = 10

    val MAX_LEVEL = (ChamberWorld.RING_CAPACITY - START_RINGS) / RINGS_PER_LEVEL + 1

    fun ringsFor(level: Int) = START_RINGS + (level - 1) * RINGS_PER_LEVEL

    fun secondsFor(level: Int) = START_SECONDS + (level - 1) * SECONDS_PER_LEVEL

    /** Jets, obstacle, current and gravity for a level. */
    fun layoutFor(level: Int) = LevelLayout.forTier(level)

    /** Finishing faster leaves more time on the clock, which scores higher. */
    fun levelScore(rings: Int, secondsLeft: Float) =
        rings * POINTS_PER_RING + ceil(secondsLeft.coerceAtLeast(0f)).toInt() * POINTS_PER_SECOND_LEFT
}

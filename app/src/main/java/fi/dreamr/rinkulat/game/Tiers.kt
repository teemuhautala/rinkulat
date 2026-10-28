package fi.dreamr.rinkulat.game

import kotlin.math.ceil

/** Visual theme of a chapter; the colors and scenery live in the UI layer. */
enum class ThemeId { Orange, Sea, Space, Mechanicals }

/**
 * One block of [Tiers.CHAPTER_LENGTH] levels: its theme and the ring count and time of each level in it
 * (by position inside the chapter). Missing entries fall back to the defaults.
 */
data class Chapter(
    val theme: ThemeId,
    val rings: List<Int>,
    val seconds: List<Int>,
    /** Multiplier for how hard shakes and nudges push the rings. */
    val shake: Float = 1f,
)

/**
 * Map rules for challenge mode. Levels are grouped into chapters of five; the first level of each chapter
 * is a checkpoint a run can start from. A level's mechanics come from [LevelLayout.LEVELS] by number,
 * everything else from its chapter, so levels can be moved by renumbering them.
 */
object Tiers {
    const val CHAPTER_LENGTH = 5
    const val DEFAULT_RINGS = 10
    const val DEFAULT_SECONDS = 150
    const val POINTS_PER_RING = 100
    const val POINTS_PER_SECOND_LEFT = 10

    val CHAPTERS = listOf(
        Chapter(ThemeId.Orange, rings = listOf(5, 6, 7, 8, 9), seconds = listOf(60, 70, 80, 90, 100)),
        Chapter(ThemeId.Sea, rings = listOf(10, 10, 10, 10, 10), seconds = listOf(110, 120, 130, 140, 150)),
        Chapter(ThemeId.Space, rings = List(5) { 10 }, seconds = List(5) { 150 }, shake = 2.5f),
        Chapter(ThemeId.Mechanicals, rings = List(5) { 10 }, seconds = List(5) { 150 }),
    )

    val MAX_LEVEL = LevelLayout.LEVELS.size

    fun chapterIndex(level: Int) = (level - 1) / CHAPTER_LENGTH

    /** Levels past the defined chapters keep the last chapter's theme and use the default rings/time. */
    fun chapterFor(level: Int): Chapter? = CHAPTERS.getOrNull(chapterIndex(level))

    private fun indexInChapter(level: Int) = (level - 1) % CHAPTER_LENGTH

    fun themeFor(level: Int) = (chapterFor(level) ?: CHAPTERS.last()).theme

    fun ringsFor(level: Int) = chapterFor(level)?.rings?.getOrNull(indexInChapter(level)) ?: DEFAULT_RINGS

    fun secondsFor(level: Int) = chapterFor(level)?.seconds?.getOrNull(indexInChapter(level)) ?: DEFAULT_SECONDS

    fun shakeFor(level: Int) = (chapterFor(level) ?: CHAPTERS.last()).shake

    fun isCheckpoint(level: Int) = level >= 1 && indexInChapter(level) == 0

    /** Checkpoint levels (1, 6, 11, …) up to [upTo], never past the last level. */
    fun checkpoints(upTo: Int): List<Int> = (1..minOf(upTo, MAX_LEVEL) step CHAPTER_LENGTH).toList()

    /** Jets, obstacle, current and gravity for a level. */
    fun layoutFor(level: Int): LevelLayout = LevelLayout.LEVELS.first { it.number == level }

    /** Finishing faster leaves more time on the clock, which scores higher. */
    fun levelScore(rings: Int, secondsLeft: Float) =
        rings * POINTS_PER_RING + ceil(secondsLeft.coerceAtLeast(0f)).toInt() * POINTS_PER_SECOND_LEFT
}

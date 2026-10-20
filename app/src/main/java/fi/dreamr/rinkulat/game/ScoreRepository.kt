package fi.dreamr.rinkulat.game

import android.content.Context

/**
 * One high-score list entry. [value] is points in tier mode and milliseconds in free play;
 * [level] is the last level cleared in challenge mode and the level played in free play.
 * [name] is what the player typed when the score placed (may be empty).
 */
data class ScoreEntry(val value: Long, val level: Int, val timestamp: Long, val name: String = "")

object ScoreList {
    const val MAX_ENTRIES = 10
    const val MAX_NAME_LENGTH = 12

    /** Names are stored inside the encoded list, so drop the separators and keep them short. */
    fun cleanName(name: String) = name.replace(Regex("[;\\r\\n]"), " ").trim().take(MAX_NAME_LENGTH)

    val HIGHEST_FIRST: Comparator<ScoreEntry> = compareByDescending<ScoreEntry> { it.value }.thenBy { it.timestamp }
    val LOWEST_FIRST: Comparator<ScoreEntry> = compareBy<ScoreEntry> { it.value }.thenBy { it.timestamp }

    /** Insert [entry] and keep the best [MAX_ENTRIES]. Returns the new list and the entry's index, or null if it didn't place. */
    fun insert(list: List<ScoreEntry>, entry: ScoreEntry, order: Comparator<ScoreEntry>): Pair<List<ScoreEntry>, Int?> {
        val sorted = (list + entry).sortedWith(order).take(MAX_ENTRIES)
        val rank = sorted.indexOf(entry).takeIf { it >= 0 }
        return sorted to rank
    }

    /**
     * Like [insert], but keeps the best [MAX_ENTRIES] separately for each level (free play).
     * The returned rank is within [entry]'s level.
     */
    fun insertPerLevel(list: List<ScoreEntry>, entry: ScoreEntry, order: Comparator<ScoreEntry>): Pair<List<ScoreEntry>, Int?> {
        val (levelList, rank) = insert(forLevel(list, entry.level), entry, order)
        return (list.filter { it.level != entry.level } + levelList) to rank
    }

    fun forLevel(list: List<ScoreEntry>, level: Int) = list.filter { it.level == level }

    /** Replace [old] with a copy carrying [name]; order and ranks don't change. */
    fun rename(list: List<ScoreEntry>, old: ScoreEntry, name: String) = list.map { if (it == old) it.copy(name = name) else it }

    fun encode(list: List<ScoreEntry>) =
        list.joinToString("\n") { "${it.value};${it.level};${it.timestamp};${cleanName(it.name)}" }

    /** Reads both the current format and the older one without a name field. */
    fun decode(text: String?): List<ScoreEntry> = text.orEmpty().lines().mapNotNull { line ->
        val parts = line.split(';', limit = 4)
        if (parts.size < 3) return@mapNotNull null
        val value = parts[0].toLongOrNull() ?: return@mapNotNull null
        val level = parts[1].toIntOrNull() ?: return@mapNotNull null
        val timestamp = parts[2].toLongOrNull() ?: return@mapNotNull null
        ScoreEntry(value, level, timestamp, parts.getOrElse(3) { "" })
    }
}

/** Best free-play time per level number, in milliseconds. */
object BestTimes {
    fun encode(bests: Map<Int, Long>) = bests.entries.joinToString("\n") { "${it.key};${it.value}" }

    fun decode(text: String?): Map<Int, Long> = text.orEmpty().lines().mapNotNull { line ->
        val parts = line.split(';')
        if (parts.size != 2) return@mapNotNull null
        val level = parts[0].toIntOrNull() ?: return@mapNotNull null
        val millis = parts[1].toLongOrNull() ?: return@mapNotNull null
        level to millis
    }.toMap()
}

class ScoreRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun tierScores() = ScoreList.decode(prefs.getString(KEY_TIER_SCORES, null)).sortedWith(ScoreList.HIGHEST_FIRST)

    fun freePlayTimes() = ScoreList.decode(prefs.getString(KEY_FREE_PLAY_TIMES, null)).sortedWith(ScoreList.LOWEST_FIRST)

    fun saveTierScores(list: List<ScoreEntry>) = prefs.edit().putString(KEY_TIER_SCORES, ScoreList.encode(list)).apply()

    fun saveFreePlayTimes(list: List<ScoreEntry>) = prefs.edit().putString(KEY_FREE_PLAY_TIMES, ScoreList.encode(list)).apply()

    /** Last name typed for a high score; prefilled next time. */
    fun playerName() = prefs.getString(KEY_PLAYER_NAME, null).orEmpty()

    fun savePlayerName(name: String) = prefs.edit().putString(KEY_PLAYER_NAME, name).apply()

    /** Highest challenge level reached; that level and all before it are unlocked. */
    fun highestLevelReached() = prefs.getInt(KEY_HIGHEST_LEVEL_REACHED, 1)

    fun saveHighestLevelReached(level: Int) = prefs.edit().putInt(KEY_HIGHEST_LEVEL_REACHED, level).apply()

    fun freePlayBests() = BestTimes.decode(prefs.getString(KEY_FREE_PLAY_BESTS, null))

    fun saveFreePlayBests(bests: Map<Int, Long>) = prefs.edit().putString(KEY_FREE_PLAY_BESTS, BestTimes.encode(bests)).apply()

    private companion object {
        const val PREFS_NAME = "rinkulat_scores"
        const val KEY_TIER_SCORES = "tier_scores"
        const val KEY_FREE_PLAY_TIMES = "free_play_times"
        const val KEY_HIGHEST_LEVEL_REACHED = "highest_level_reached"
        const val KEY_FREE_PLAY_BESTS = "free_play_best"
        const val KEY_PLAYER_NAME = "player_name"
    }
}

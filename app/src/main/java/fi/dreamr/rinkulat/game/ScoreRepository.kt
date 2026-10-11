package fi.dreamr.rinkulat.game

import android.content.Context

/**
 * One high-score list entry. [value] is points in tier mode and milliseconds in free play;
 * [level] is the number of tiers cleared (free play stores 0).
 */
data class ScoreEntry(val value: Long, val level: Int, val timestamp: Long)

object ScoreList {
    const val MAX_ENTRIES = 10

    val HIGHEST_FIRST: Comparator<ScoreEntry> = compareByDescending<ScoreEntry> { it.value }.thenBy { it.timestamp }
    val LOWEST_FIRST: Comparator<ScoreEntry> = compareBy<ScoreEntry> { it.value }.thenBy { it.timestamp }

    /** Insert [entry] and keep the best [MAX_ENTRIES]. Returns the new list and the entry's index, or null if it didn't place. */
    fun insert(list: List<ScoreEntry>, entry: ScoreEntry, order: Comparator<ScoreEntry>): Pair<List<ScoreEntry>, Int?> {
        val sorted = (list + entry).sortedWith(order).take(MAX_ENTRIES)
        val rank = sorted.indexOf(entry).takeIf { it >= 0 }
        return sorted to rank
    }

    fun encode(list: List<ScoreEntry>) = list.joinToString("\n") { "${it.value};${it.level};${it.timestamp}" }

    fun decode(text: String?): List<ScoreEntry> = text.orEmpty().lines().mapNotNull { line ->
        val parts = line.split(';')
        if (parts.size != 3) return@mapNotNull null
        val value = parts[0].toLongOrNull() ?: return@mapNotNull null
        val level = parts[1].toIntOrNull() ?: return@mapNotNull null
        val timestamp = parts[2].toLongOrNull() ?: return@mapNotNull null
        ScoreEntry(value, level, timestamp)
    }
}

class ScoreRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun tierScores() = ScoreList.decode(prefs.getString(KEY_TIER_SCORES, null)).sortedWith(ScoreList.HIGHEST_FIRST)

    fun freePlayTimes() = ScoreList.decode(prefs.getString(KEY_FREE_PLAY_TIMES, null)).sortedWith(ScoreList.LOWEST_FIRST)

    fun saveTierScores(list: List<ScoreEntry>) = prefs.edit().putString(KEY_TIER_SCORES, ScoreList.encode(list)).apply()

    fun saveFreePlayTimes(list: List<ScoreEntry>) = prefs.edit().putString(KEY_FREE_PLAY_TIMES, ScoreList.encode(list)).apply()

    private companion object {
        const val PREFS_NAME = "rinkulat_scores"
        const val KEY_TIER_SCORES = "tier_scores"
        const val KEY_FREE_PLAY_TIMES = "free_play_times"
    }
}

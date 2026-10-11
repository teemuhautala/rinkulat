package fi.dreamr.rinkulat.game

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlin.math.ceil
import kotlin.math.min

enum class GameMode { FreePlay, Tiers }

sealed interface GamePhase {
    data object Menu : GamePhase
    data object Playing : GamePhase
    data class LevelIntro(val level: Int, val rings: Int, val seconds: Int) : GamePhase
    data class LevelComplete(val level: Int, val levelScore: Int, val totalScore: Int) : GamePhase

    /** A tier run ended, by running out of time or by clearing the last level. */
    data class RunOver(val totalScore: Int, val levelsCleared: Int, val allCleared: Boolean, val rank: Int?) : GamePhase
    data class FreePlayWon(val seconds: Float, val rank: Int?) : GamePhase
}

data class HudState(
    val level: Int?,
    val caught: Int,
    val total: Int,
    /** Elapsed seconds in free play, remaining seconds in tier mode. */
    val seconds: Int,
    val lowTime: Boolean,
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val scores = ScoreRepository(application)

    val world = ChamberWorld()

    private var level = 1
    private var timeLeft = 0f
    private var totalScore = 0

    /** Bumped every simulated frame so the canvas redraws. */
    var frame by mutableLongStateOf(0L)
        private set

    var mode by mutableStateOf(GameMode.FreePlay)
        private set

    var phase by mutableStateOf<GamePhase>(GamePhase.Menu)
        private set

    var hud by mutableStateOf(currentHud())
        private set

    var scoresVisible by mutableStateOf(false)
        private set

    var tierScores by mutableStateOf(scores.tierScores())
        private set

    var freePlayTimes by mutableStateOf(scores.freePlayTimes())
        private set

    /** Which list the scores view shows. */
    var scoresTab by mutableStateOf(GameMode.Tiers)
        private set

    /** The list and index of the most recently added entry, for highlighting. */
    var latestEntry by mutableStateOf<Pair<GameMode, Int>?>(null)
        private set

    fun advance(frameDt: Float, gravityX: Float, gravityY: Float, pumping: Boolean) {
        val playing = phase == GamePhase.Playing
        world.advance(frameDt, gravityX, gravityY, pumping && playing)
        frame++
        if (playing) {
            when (mode) {
                GameMode.FreePlay -> if (world.won) finishFreePlay()
                GameMode.Tiers -> {
                    timeLeft -= min(frameDt, ChamberWorld.MAX_FRAME_DT)
                    if (world.won) finishLevel() else if (timeLeft <= 0f) finishRun(allCleared = false)
                }
            }
        }
        val next = currentHud()
        if (next != hud) hud = next
    }

    fun startFreePlay() {
        mode = GameMode.FreePlay
        world.reset(FREE_PLAY_RINGS)
        phase = GamePhase.Playing
        scoresVisible = false
    }

    fun startTiers() {
        mode = GameMode.Tiers
        totalScore = 0
        scoresVisible = false
        showLevelIntro(1)
    }

    fun beginLevel() {
        timeLeft = Tiers.secondsFor(level).toFloat()
        phase = GamePhase.Playing
    }

    fun nextLevel() = showLevelIntro(level + 1)

    /** Restart whatever mode was being played. */
    fun playAgain() = if (mode == GameMode.Tiers) startTiers() else startFreePlay()

    fun openMenu() {
        scoresVisible = false
        phase = GamePhase.Menu
    }

    fun showScores(tab: GameMode = mode) {
        scoresTab = tab
        scoresVisible = true
    }

    fun hideScores() {
        scoresVisible = false
    }

    private fun showLevelIntro(newLevel: Int) {
        level = newLevel
        world.reset(Tiers.ringsFor(level))
        phase = GamePhase.LevelIntro(level, Tiers.ringsFor(level), Tiers.secondsFor(level))
    }

    private fun finishLevel() {
        val levelScore = Tiers.levelScore(world.rings.size, timeLeft)
        totalScore += levelScore
        if (level >= Tiers.MAX_LEVEL) {
            finishRun(allCleared = true)
        } else {
            phase = GamePhase.LevelComplete(level, levelScore, totalScore)
        }
    }

    private fun finishRun(allCleared: Boolean) {
        val levelsCleared = if (allCleared) level else level - 1
        val (list, rank) = ScoreList.insert(
            tierScores,
            ScoreEntry(totalScore.toLong(), levelsCleared, System.currentTimeMillis()),
            ScoreList.HIGHEST_FIRST,
        )
        tierScores = list
        scores.saveTierScores(list)
        latestEntry = rank?.let { GameMode.Tiers to it }
        phase = GamePhase.RunOver(totalScore, levelsCleared, allCleared, rank)
    }

    private fun finishFreePlay() {
        val seconds = world.elapsed
        val (list, rank) = ScoreList.insert(
            freePlayTimes,
            ScoreEntry((seconds * 1000).toLong(), 0, System.currentTimeMillis()),
            ScoreList.LOWEST_FIRST,
        )
        freePlayTimes = list
        scores.saveFreePlayTimes(list)
        latestEntry = rank?.let { GameMode.FreePlay to it }
        phase = GamePhase.FreePlayWon(seconds, rank)
    }

    private fun currentHud(): HudState {
        val tiers = mode == GameMode.Tiers && phase != GamePhase.Menu
        val seconds = when {
            !tiers -> world.elapsed.toInt()
            phase == GamePhase.Playing -> ceil(timeLeft.coerceAtLeast(0f)).toInt()
            phase is GamePhase.LevelIntro -> Tiers.secondsFor(level)
            else -> ceil(timeLeft.coerceAtLeast(0f)).toInt()
        }
        return HudState(
            level = if (tiers) level else null,
            caught = world.caughtCount,
            total = world.rings.size,
            seconds = seconds,
            lowTime = tiers && phase == GamePhase.Playing && timeLeft < LOW_TIME_SECONDS,
        )
    }

    private companion object {
        const val FREE_PLAY_RINGS = 10
        const val LOW_TIME_SECONDS = 10f
    }
}

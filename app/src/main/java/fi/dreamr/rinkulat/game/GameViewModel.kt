package fi.dreamr.rinkulat.game

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlin.math.ceil
import kotlin.math.min

enum class GameMode { FreePlay, Challenge }

sealed interface GamePhase {
    data object Menu : GamePhase
    data object Playing : GamePhase

    /** Free-play level grid, opened from the start screen. */
    data object LevelSelect : GamePhase
    data class LevelIntro(val level: Int, val rings: Int, val seconds: Int, val title: String) : GamePhase
    data class LevelComplete(val level: Int, val levelScore: Int, val totalScore: Int) : GamePhase

    /** A challenge run ended, by running out of time or by clearing the last level. */
    data class RunOver(
        val totalScore: Int,
        /** Checkpoint the run started from. */
        val startLevel: Int,
        val levelsCleared: Int,
        val allCleared: Boolean,
        val rank: Int?,
        /** Levels were skipped by swiping, so the score wasn't saved. */
        val practice: Boolean,
    ) : GamePhase
    data class FreePlayWon(val seconds: Float, val rank: Int?, val level: Int, val newBest: Boolean) : GamePhase
}

/** One tile of the free-play level grid. */
data class LevelTile(val number: Int, val unlocked: Boolean, val bestMillis: Long?)

data class HudState(
    val level: Int?,
    val levelTitle: String,
    /** Swipe the chamber to jump to other levels (play-testing aid). */
    val canSwipeLevels: Boolean,
    val caught: Int,
    val total: Int,
    /** Elapsed seconds in free play, remaining seconds in tier mode. */
    val seconds: Int,
    val lowTime: Boolean,
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val scores = ScoreRepository(application)

    val world = ChamberWorld()

    /** Debug builds may start locked levels and swipe between levels for play testing. */
    val isDebug = application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** Current challenge level. */
    private var level by mutableIntStateOf(1)
    private var timeLeft = 0f
    private var totalScore = 0

    /** Set once a challenge run jumps levels by swiping; such runs aren't saved to the scores. */
    private var practiceRun = false

    private var runStartLevel = 1

    /** Bumped every simulated frame so the canvas redraws. */
    var frame by mutableLongStateOf(0L)
        private set

    var mode by mutableStateOf(GameMode.FreePlay)
        private set

    /** Level layout used in free play, chosen from the level grid; kept across restarts. */
    var freePlayLevel by mutableIntStateOf(1)
        private set

    /** Highest challenge level reached; it and every level before it are unlocked. */
    var highestReached by mutableIntStateOf(scores.highestLevelReached().coerceIn(1, Tiers.MAX_LEVEL))
        private set

    var freePlayBests by mutableStateOf(scores.freePlayBests())
        private set

    /** Checkpoint the next challenge run starts from, chosen on the start screen. */
    var challengeStart by mutableIntStateOf(1)
        private set

    val selectableCheckpoints get() = Tiers.checkpoints(if (isDebug) Tiers.MAX_LEVEL else highestReached)

    val levelTiles get() = (1..Tiers.MAX_LEVEL).map { LevelTile(it, it <= highestReached, freePlayBests[it]) }

    /** Theme of whatever is on screen: the chosen checkpoint at the start, otherwise the level being played. */
    val themeId get() = Tiers.themeFor(
        when {
            phase == GamePhase.Menu || phase == GamePhase.LevelSelect -> challengeStart
            mode == GameMode.FreePlay -> freePlayLevel
            else -> level
        },
    )

    /** Current jets/twists, drives the button layout. */
    var layout by mutableStateOf(world.layout)
        private set

    var phase by mutableStateOf<GamePhase>(GamePhase.Menu)
        private set

    var hud by mutableStateOf(currentHud())
        private set

    var scoresVisible by mutableStateOf(false)
        private set

    var challengeScores by mutableStateOf(scores.tierScores())
        private set

    var freePlayTimes by mutableStateOf(scores.freePlayTimes())
        private set

    /** Which list the scores view shows. */
    var scoresTab by mutableStateOf(GameMode.Challenge)
        private set

    /** Level whose list the free-play scores tab shows. */
    var scoresLevel by mutableIntStateOf(1)
        private set

    /** The list and entry most recently added, for highlighting and naming. */
    var latestEntry by mutableStateOf<Pair<GameMode, ScoreEntry>?>(null)
        private set

    /** Name typed on the result card; prefilled with the last name used. */
    var playerName by mutableStateOf(scores.playerName())
        private set

    /** True while the result card offers to name [latestEntry]. */
    var namingEntry by mutableStateOf(false)
        private set

    /** Bit i of [pressedJets] is the button for jet i of [layout]. */
    fun advance(frameDt: Float, gravityX: Float, gravityY: Float, pressedJets: Int, shakeX: Float = 0f, shakeY: Float = 0f) {
        val playing = phase == GamePhase.Playing
        world.shakeX = shakeX
        world.shakeY = shakeY
        world.advance(frameDt, gravityX, gravityY, if (playing) pressedJets else 0)
        frame++
        if (playing) {
            when (mode) {
                GameMode.FreePlay -> if (world.won) finishFreePlay()
                GameMode.Challenge -> {
                    timeLeft -= min(frameDt, ChamberWorld.MAX_FRAME_DT)
                    if (world.won) finishLevel() else if (timeLeft <= 0f) finishRun(allCleared = false)
                }
            }
        }
        val next = currentHud()
        if (next != hud) hud = next
    }

    fun openLevelSelect() {
        commitName()
        scoresVisible = false
        phase = GamePhase.LevelSelect
    }

    /** Start free play on [number] if it's unlocked (any level in debug builds). */
    fun pickFreePlayLevel(number: Int) {
        if (!isDebug && number > highestReached) return
        freePlayLevel = number
        startFreePlay()
    }

    /** Step the start-screen checkpoint selector to the next/previous available checkpoint (wrapping). */
    fun changeCheckpoint(delta: Int) {
        val options = selectableCheckpoints
        val index = options.indexOf(challengeStart).coerceAtLeast(0)
        challengeStart = options[(index + delta).mod(options.size)]
    }

    fun startFreePlay() {
        commitName()
        mode = GameMode.FreePlay
        loadLevel(FREE_PLAY_RINGS, freePlayLevel)
        layout = world.layout
        phase = GamePhase.Playing
        scoresVisible = false
    }

    /**
     * Jump to the next/previous level (wrapping) and restart it immediately, for play testing.
     * Free play keeps 10 rings; challenge uses the level's real rings and time and becomes a practice run.
     */
    fun changeLevel(delta: Int) {
        if (!isDebug || phase != GamePhase.Playing) return
        when (mode) {
            GameMode.FreePlay -> {
                freePlayLevel = wrapLevel(freePlayLevel + delta)
                startFreePlay()
            }
            GameMode.Challenge -> {
                practiceRun = true
                level = wrapLevel(level + delta)
                loadLevel(Tiers.ringsFor(level), level)
                layout = world.layout
                timeLeft = Tiers.secondsFor(level).toFloat()
            }
        }
    }

    /** Reset the world to [number]'s layout with [rings] rings and that chapter's shake strength. */
    private fun loadLevel(rings: Int, number: Int) {
        world.reset(rings, Tiers.layoutFor(number))
        world.shakeMultiplier = Tiers.shakeFor(number)
    }

    private fun wrapLevel(level: Int) = (level - 1).mod(Tiers.MAX_LEVEL) + 1

    fun startChallenge() {
        commitName()
        mode = GameMode.Challenge
        totalScore = 0
        practiceRun = false
        scoresVisible = false
        if (challengeStart !in selectableCheckpoints) challengeStart = 1
        runStartLevel = challengeStart
        showLevelIntro(challengeStart)
    }

    fun beginLevel() {
        timeLeft = Tiers.secondsFor(level).toFloat()
        phase = GamePhase.Playing
    }

    fun nextLevel() = showLevelIntro(level + 1)

    /** Restart whatever mode was being played. */
    fun playAgain() = if (mode == GameMode.Challenge) startChallenge() else startFreePlay()

    fun openMenu() {
        commitName()
        scoresVisible = false
        phase = GamePhase.Menu
    }

    fun showScores(tab: GameMode = mode) {
        commitName()
        if (tab == GameMode.FreePlay) scoresLevel = latestEntry?.takeIf { it.first == tab }?.second?.level ?: freePlayLevel
        scoresTab = tab
        scoresVisible = true
    }

    fun hideScores() {
        scoresVisible = false
    }

    /** Step the free-play scores tab to the next/previous level (wrapping). */
    fun changeScoresLevel(delta: Int) {
        scoresLevel = wrapLevel(scoresLevel + delta)
    }

    fun updatePlayerName(name: String) {
        playerName = name.replace("\n", "").take(ScoreList.MAX_NAME_LENGTH)
    }

    /** Store the typed name on the new high score (once the player leaves the result card) and remember it. */
    private fun commitName() {
        if (!namingEntry) return
        namingEntry = false
        val (listMode, entry) = latestEntry ?: return
        val name = ScoreList.cleanName(playerName)
        playerName = name
        scores.savePlayerName(name)
        if (name == entry.name) return
        val renamed = entry.copy(name = name)
        when (listMode) {
            GameMode.Challenge -> {
                challengeScores = ScoreList.rename(challengeScores, entry, name)
                scores.saveTierScores(challengeScores)
            }
            GameMode.FreePlay -> {
                freePlayTimes = ScoreList.rename(freePlayTimes, entry, name)
                scores.saveFreePlayTimes(freePlayTimes)
            }
        }
        latestEntry = listMode to renamed
    }

    private fun showLevelIntro(newLevel: Int) {
        level = newLevel
        loadLevel(Tiers.ringsFor(level), level)
        layout = world.layout
        phase = GamePhase.LevelIntro(level, Tiers.ringsFor(level), Tiers.secondsFor(level), layout.title)
    }

    private fun finishLevel() {
        val levelScore = Tiers.levelScore(world.rings.size, timeLeft)
        totalScore += levelScore
        if (!practiceRun && level + 1 > highestReached && level < Tiers.MAX_LEVEL) {
            highestReached = level + 1
            scores.saveHighestLevelReached(highestReached)
        }
        if (level >= Tiers.MAX_LEVEL) {
            finishRun(allCleared = true)
        } else {
            phase = GamePhase.LevelComplete(level, levelScore, totalScore)
        }
    }

    private fun finishRun(allCleared: Boolean) {
        val lastCleared = if (allCleared) level else level - 1
        val levelsCleared = (lastCleared - runStartLevel + 1).coerceAtLeast(0)
        var rank: Int? = null
        if (!practiceRun) {
            val entry = ScoreEntry(
                totalScore.toLong(),
                if (levelsCleared > 0) lastCleared else 0,
                System.currentTimeMillis(),
                ScoreList.cleanName(playerName),
            )
            val (list, newRank) = ScoreList.insert(
                challengeScores,
                entry,
                ScoreList.HIGHEST_FIRST,
            )
            challengeScores = list
            scores.saveTierScores(list)
            latestEntry = newRank?.let { GameMode.Challenge to entry }
            namingEntry = newRank != null
            rank = newRank
        }
        phase = GamePhase.RunOver(totalScore, runStartLevel, levelsCleared, allCleared, rank, practiceRun)
    }

    private fun finishFreePlay() {
        val seconds = world.elapsed
        val entry = ScoreEntry((seconds * 1000).toLong(), freePlayLevel, System.currentTimeMillis(), ScoreList.cleanName(playerName))
        val (list, rank) = ScoreList.insertPerLevel(
            freePlayTimes,
            entry,
            ScoreList.LOWEST_FIRST,
        )
        freePlayTimes = list
        scores.saveFreePlayTimes(list)
        latestEntry = rank?.let { GameMode.FreePlay to entry }
        namingEntry = rank != null
        val millis = (seconds * 1000).toLong()
        val newBest = freePlayBests[freePlayLevel]?.let { millis < it } ?: true
        if (newBest) {
            freePlayBests = freePlayBests + (freePlayLevel to millis)
            scores.saveFreePlayBests(freePlayBests)
        }
        phase = GamePhase.FreePlayWon(seconds, rank, freePlayLevel, newBest)
    }

    private fun currentHud(): HudState {
        val atStart = phase == GamePhase.Menu || phase == GamePhase.LevelSelect
        val tiers = mode == GameMode.Challenge && !atStart
        val freePlay = mode == GameMode.FreePlay && !atStart
        val seconds = when {
            !tiers -> world.elapsed.toInt()
            phase == GamePhase.Playing -> ceil(timeLeft.coerceAtLeast(0f)).toInt()
            phase is GamePhase.LevelIntro -> Tiers.secondsFor(level)
            else -> ceil(timeLeft.coerceAtLeast(0f)).toInt()
        }
        return HudState(
            level = when {
                tiers -> level
                freePlay -> freePlayLevel
                else -> null
            },
            levelTitle = layout.title,
            canSwipeLevels = isDebug && (freePlay || tiers) && phase == GamePhase.Playing,
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

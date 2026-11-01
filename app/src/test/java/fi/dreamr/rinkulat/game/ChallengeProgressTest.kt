package fi.dreamr.rinkulat.game

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ChallengeProgressTest {
    private lateinit var application: Application
    private lateinit var scores: ScoreRepository

    @Before
    fun setup() {
        application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("rinkulat_scores", Context.MODE_PRIVATE).edit().clear().commit()
        application.applicationInfo.flags = application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        scores = ScoreRepository(application)
    }

    private fun model() = GameViewModel(application)

    private fun start(vm: GameViewModel) {
        vm.startChallenge()
        vm.beginLevel()
    }

    private fun win(vm: GameViewModel) {
        // Inject the simulation's win signal: these tests exercise progression, not ring physics.
        ChamberWorld::class.java.getDeclaredField("won").apply {
            isAccessible = true
            setBoolean(vm.world, true)
        }
        vm.advance(0f, 0f, 1f, 0)
    }

    @Test
    fun gridsRouteToTheirModeAndBrowsingDoesNotSaveProgress() {
        assertEquals(1, model().challengeStart)
        scores.saveHighestLevelReached(7)
        val vm = model()
        vm.openLevelSelect(GameMode.Challenge)
        assertEquals(GamePhase.LevelSelect, vm.phase)
        assertEquals(GameMode.Challenge, vm.levelSelectMode)
        assertEquals(7, vm.challengeStart)
        assertEquals((1..7).toList(), vm.selectableChallengeLevels)
        vm.pickLevel(8)
        assertEquals(GamePhase.LevelSelect, vm.phase)
        vm.pickLevel(0)
        assertEquals(GamePhase.LevelSelect, vm.phase)
        vm.pickLevel(4)
        assertEquals(GameMode.Challenge, vm.mode)
        assertEquals(4, (vm.phase as GamePhase.LevelIntro).level)
        assertEquals(7, model().challengeStart)
        vm.openMenu()
        assertEquals(7, vm.challengeStart)
        vm.openLevelSelect(GameMode.FreePlay)
        vm.pickLevel(4)
        assertEquals(GameMode.FreePlay, vm.mode)
        assertEquals(GamePhase.Playing, vm.phase)
        assertEquals(4, vm.freePlayLevel)
        assertEquals(7, model().challengeStart)
    }

    @Test
    fun quittingAndLosingRememberCurrentLevelAndRetryResetsRun() {
        scores.saveHighestLevelReached(7)
        val vm = model()
        vm.pickChallengeLevel(4)
        start(vm)
        vm.openMenu()
        assertEquals(4, model().challengeStart)
        start(vm)
        repeat(12_000) {
            if (vm.phase == GamePhase.Playing) vm.advance(0.05f, 0f, 1f, 0)
        }
        assertTrue(vm.phase is GamePhase.RunOver)
        assertEquals(4, model().challengeStart)
        vm.playAgain()
        assertEquals(4, (vm.phase as GamePhase.LevelIntro).level)
        vm.beginLevel()
        assertFalse(vm.world.won)
    }

    @Test
    fun winningSavesNextLevelBeforeNextIsPressedAndReplayCanMoveBack() {
        scores.saveHighestLevelReached(7)
        val vm = model()
        start(vm)
        win(vm)
        val completed = vm.phase as GamePhase.LevelComplete
        assertEquals(8, scores.highestLevelReached())
        assertEquals(8, model().challengeStart)
        vm.nextLevel()
        assertEquals(8, (vm.phase as GamePhase.LevelIntro).level)
        vm.beginLevel()
        win(vm)
        assertTrue((vm.phase as GamePhase.LevelComplete).totalScore > completed.totalScore)
        vm.openMenu()
        vm.pickChallengeLevel(3)
        start(vm)
        assertEquals(3, model().challengeStart)
        assertEquals(9, scores.highestLevelReached())
    }

    @Test
    fun finalWinRemembersFinalLevel() {
        scores.saveHighestLevelReached(Tiers.MAX_LEVEL)
        val vm = model()
        start(vm)
        win(vm)
        assertTrue((vm.phase as GamePhase.RunOver).allCleared)
        assertEquals(Tiers.MAX_LEVEL, model().challengeStart)
    }

    @Test
    fun freePlayRespectsLocksAndDoesNotChangeChallengeResume() {
        scores.saveHighestLevelReached(7)
        scores.saveLastChallengeLevel(4)
        val vm = model()
        vm.openLevelSelect()
        vm.pickFreePlayLevel(8)
        assertEquals(GamePhase.LevelSelect, vm.phase)
        vm.pickFreePlayLevel(0)
        assertEquals(GamePhase.LevelSelect, vm.phase)
        vm.pickFreePlayLevel(7)
        assertEquals(GamePhase.Playing, vm.phase)
        vm.openMenu()
        assertEquals(4, vm.challengeStart)
        assertEquals(4, model().challengeStart)
    }

    @Test
    fun restoredLevelIsClampedAndDebugKeepsAccessWithoutPracticeProgress() {
        scores.saveHighestLevelReached(7)
        scores.saveLastChallengeLevel(99)
        assertEquals(7, model().challengeStart)
        scores.saveLastChallengeLevel(-4)
        assertEquals(1, model().challengeStart)
        scores.saveLastChallengeLevel(4)
        application.applicationInfo.flags = application.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
        val vm = model()
        assertEquals(Tiers.MAX_LEVEL, vm.selectableChallengeLevels.size)
        start(vm)
        vm.changeLevel(4)
        vm.playAgain()
        vm.beginLevel()
        win(vm)
        assertEquals(7, scores.highestLevelReached())
        assertEquals(4, model().challengeStart)
        vm.openMenu()
        vm.pickFreePlayLevel(20)
        assertEquals(20, vm.freePlayLevel)
        assertEquals(GamePhase.Playing, vm.phase)
    }

    @Test
    fun openingAndLeavingSettingsPreservesRecords() {
        scores.saveHighestLevelReached(7)
        scores.saveLastChallengeLevel(4)
        scores.saveTierScores(listOf(ScoreEntry(1000, 3, 1)))
        val vm = model()
        vm.openSettings()
        assertEquals(GamePhase.Settings, vm.phase)
        vm.openMenu()
        assertEquals(7, model().highestReached)
        assertEquals(4, model().challengeStart)
        assertEquals(1, model().challengeScores.size)
    }

    @Test
    fun confirmedResetClearsRecordsInMemoryAndOnReloadButKeepsName() {
        scores.saveHighestLevelReached(7)
        scores.saveLastChallengeLevel(4)
        scores.saveTierScores(listOf(ScoreEntry(1000, 3, 1)))
        scores.saveFreePlayTimes(listOf(ScoreEntry(1234, 7, 2)))
        scores.saveFreePlayBests(mapOf(7 to 1234L))
        scores.savePlayerName("Aino")
        val vm = model()
        vm.pickFreePlayLevel(7)
        win(vm)
        assertTrue(vm.namingEntry)
        vm.updatePlayerName("Eino")
        vm.openMenu()
        vm.openSettings()
        vm.resetProgressAndScores()
        assertEquals(GamePhase.Settings, vm.phase)
        assertFalse(vm.namingEntry)
        assertEquals(null, vm.latestEntry)
        vm.openMenu()
        vm.showScores()
        vm.hideScores()
        listOf(vm, model()).forEach { reset ->
            assertEquals(1, reset.highestReached)
            assertEquals(1, reset.challengeStart)
            assertEquals(1, reset.freePlayLevel)
            assertTrue(reset.challengeScores.isEmpty())
            assertTrue(reset.freePlayTimes.isEmpty())
            assertTrue(reset.freePlayBests.isEmpty())
            assertEquals("Eino", reset.playerName)
            assertEquals(listOf(1), reset.levelTiles.filter { it.unlocked }.map { it.number })
            reset.openLevelSelect(GameMode.Challenge)
            reset.pickLevel(7)
            assertEquals(GamePhase.LevelSelect, reset.phase)
            reset.openLevelSelect(GameMode.FreePlay)
            reset.pickLevel(7)
            assertEquals(GamePhase.LevelSelect, reset.phase)
        }
    }

}

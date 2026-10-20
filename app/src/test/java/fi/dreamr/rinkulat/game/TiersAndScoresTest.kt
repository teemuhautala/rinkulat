package fi.dreamr.rinkulat.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TiersAndScoresTest {
    @Test
    fun ringsGrowToTenAndTimeToTwoThirty() {
        val levels = 1..Tiers.MAX_LEVEL
        assertEquals(listOf(5, 6, 7, 8, 9) + List(10) { 10 }, levels.map { Tiers.ringsFor(it) })
        assertEquals((0 until 10).map { 60 + it * 10 } + List(5) { 150 }, levels.map { Tiers.secondsFor(it) })
        // Past the defined chapters: defaults.
        assertEquals(10, Tiers.ringsFor(16))
        assertEquals(150, Tiers.secondsFor(23))
    }

    @Test
    fun levelNumbersAreContiguous() {
        assertEquals(15, Tiers.MAX_LEVEL)
        assertEquals((1..Tiers.MAX_LEVEL).toList(), LevelLayout.LEVELS.map { it.number })
        assertEquals(1, LevelLayout.CLASSIC.number)
    }

    @Test
    fun everyLevelFitsOnThePegs() {
        assertEquals(24, ChamberWorld.RING_CAPACITY)
        (1..Tiers.MAX_LEVEL).forEach { assertTrue(Tiers.ringsFor(it) <= ChamberWorld.RING_CAPACITY) }
        // Every ring stacked on the fullest possible peg still sits below the tip.
        val perPeg = ChamberWorld.RING_CAPACITY / ChamberWorld.PEG_COUNT
        val topRingY = ChamberWorld.PEG_BASE_Y - (perPeg - 1) * ChamberWorld.RING_STACK_HEIGHT
        assertTrue(topRingY >= ChamberWorld.PEG_TIP_Y)
    }

    @Test
    fun chaptersGiveThemesAndCheckpoints() {
        assertEquals(
            List(5) { ThemeId.Orange } + List(5) { ThemeId.Sea } + List(5) { ThemeId.Space },
            (1..15).map { Tiers.themeFor(it) },
        )
        assertEquals(ThemeId.Space, Tiers.themeFor(17))
        assertEquals(listOf(1, 6, 11, 16), (1..16).filter { Tiers.isCheckpoint(it) })
        assertEquals(listOf(1), Tiers.checkpoints(upTo = 5))
        assertEquals(listOf(1, 6), Tiers.checkpoints(upTo = 7))
        assertEquals(listOf(1, 6, 11), Tiers.checkpoints(upTo = 99))
    }

    @Test
    fun bestTimesRoundTripAndSkipBadLines() {
        val bests = mapOf(1 to 12_345L, 7 to 99_000L)
        assertEquals(bests, BestTimes.decode(BestTimes.encode(bests)))
        assertEquals(mapOf(2 to 5L), BestTimes.decode("junk\n2;5\nx;3"))
        assertTrue(BestTimes.decode(null).isEmpty())
    }

    @Test
    fun levelTwistsFollowTheTable() {
        val layouts = (1..Tiers.MAX_LEVEL).map { Tiers.layoutFor(it) }
        assertEquals(listOf(1, 3, 4, 8, 10) + List(10) { 10 }, layouts.map { it.jets.size })
        // Jets accumulate and never change position or direction once they appear.
        for (i in 1 until layouts.size) {
            assertEquals("level ${i + 1}", layouts[i - 1].jets, layouts[i].jets.take(layouts[i - 1].jets.size))
        }
        // Full set: five slots in each strip.
        assertEquals((0..4).toSet(), layouts.last().jets.filter { it.fromTop }.map { it.slot }.toSet())
        assertEquals((0..4).toSet(), layouts.last().jets.filterNot { it.fromTop }.map { it.slot }.toSet())
        assertTrue(layouts[0].jets.single().big)
        assertEquals(listOf(false, false) + List(13) { true }, layouts.map { l -> l.jets.any { it.fromTop } })
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1, 3, 3, 3, 0, 1, 1, 2, 2), layouts.map { it.obstacles.size })
        // Chapter 3 is weightless; 12 keeps normal orientation, 13–15 are upside down.
        assertEquals((11..15).toList(), (1..Tiers.MAX_LEVEL).filter { layouts[it - 1].weightless })
        assertEquals((13..15).toList(), (1..Tiers.MAX_LEVEL).filter { layouts[it - 1].upsideDown })
        val n = Current.None
        assertEquals(
            listOf(n, n, n, n, n, n, Current.Right, n, Current.Right, Current.Shear, n, n, n, n, n),
            layouts.map { it.current },
        )
        assertEquals((12..15).toList(), (1..Tiers.MAX_LEVEL).filter { layouts[it - 1].attractingObstacles })
        assertEquals(listOf(15), (1..Tiers.MAX_LEVEL).filter { layouts[it - 1].wormholes != null })
        // Upside-down obstacles are mirrored into the lower half.
        layouts.filter { it.upsideDown }.flatMap { it.obstacles }.forEach { assertTrue(it.y > ChamberWorld.HEIGHT / 2f) }
        // Corner jets (bottom from level 4, top from level 5) aim straight at the chamber center.
        assertEquals(2, layouts[3].jets.count { it.slot == 0 || it.slot == 4 })
        val corners = layouts[4].jets.filter { it.slot == 0 || it.slot == 4 }
        assertEquals(4, corners.size)
        corners.forEach { jet ->
            val toCenterX = ChamberWorld.WIDTH / 2f - jet.x
            val toCenterY = ChamberWorld.HEIGHT / 2f - jet.y
            assertEquals(0f, toCenterX * jet.dirY - toCenterY * jet.dirX, 1e-4f) // parallel
            assertTrue(toCenterX * jet.dirX + toCenterY * jet.dirY > 0f) // same way
        }
    }

    @Test
    fun fasterClearScoresHigher() {
        assertTrue(Tiers.levelScore(5, 40f) > Tiers.levelScore(5, 10f))
        assertEquals(5 * 100 + 30 * 10, Tiers.levelScore(5, 29.2f))
        assertEquals(500, Tiers.levelScore(5, -1f))
    }

    @Test
    fun worldResetChangesRingCount() {
        val world = ChamberWorld(ringCount = 10)
        world.reset(7)
        assertEquals(7, world.rings.size)
        assertTrue(world.rings.none { it.isCaught })
    }

    @Test
    fun scoreListKeepsBestTenInOrder() {
        var list = emptyList<ScoreEntry>()
        repeat(12) { i -> list = ScoreList.insert(list, ScoreEntry(i * 10L, i, i.toLong()), ScoreList.HIGHEST_FIRST).first }
        assertEquals(ScoreList.MAX_ENTRIES, list.size)
        assertEquals(110L, list.first().value)

        val (_, rank) = ScoreList.insert(list, ScoreEntry(65L, 3, 99L), ScoreList.HIGHEST_FIRST)
        assertEquals(5, rank)
        val (_, missed) = ScoreList.insert(list, ScoreEntry(1L, 0, 99L), ScoreList.HIGHEST_FIRST)
        assertNull(missed)
    }

    @Test
    fun freePlayTimesSortFastestFirst() {
        val (list, rank) = ScoreList.insert(
            listOf(ScoreEntry(30_000L, 0, 1L)),
            ScoreEntry(20_000L, 0, 2L),
            ScoreList.LOWEST_FIRST,
        )
        assertEquals(0, rank)
        assertEquals(20_000L, list.first().value)
    }

    @Test
    fun freePlayListsKeepTenPerLevel() {
        var list = emptyList<ScoreEntry>()
        repeat(12) { i -> list = ScoreList.insertPerLevel(list, ScoreEntry(1000L + i, 1, i.toLong()), ScoreList.LOWEST_FIRST).first }
        val (withLevel2, rank) = ScoreList.insertPerLevel(list, ScoreEntry(5000L, 2, 99L), ScoreList.LOWEST_FIRST)
        assertEquals(0, rank)
        assertEquals(ScoreList.MAX_ENTRIES, ScoreList.forLevel(withLevel2, 1).size)
        assertEquals(1, ScoreList.forLevel(withLevel2, 2).size)
        // A slow level-1 time doesn't place even though level 2 has room.
        assertNull(ScoreList.insertPerLevel(withLevel2, ScoreEntry(9000L, 1, 100L), ScoreList.LOWEST_FIRST).second)
    }

    @Test
    fun namesAreStoredCleanedAndRenamable() {
        val entry = ScoreEntry(10L, 1, 2L, "Aino")
        assertEquals(listOf(entry), ScoreList.decode(ScoreList.encode(listOf(entry))))
        // Older lines without a name still load.
        assertEquals(listOf(ScoreEntry(7L, 2, 3L, "")), ScoreList.decode("7;2;3"))
        assertEquals("a b", ScoreList.cleanName(" a;b\n"))
        assertEquals(ScoreList.MAX_NAME_LENGTH, ScoreList.cleanName("x".repeat(40)).length)
        val renamed = ScoreList.rename(listOf(entry, ScoreEntry(5L, 1, 3L)), entry, "Eino")
        assertEquals(listOf("Eino", ""), renamed.map { it.name })
    }

    @Test
    fun scoreListRoundTripsAndSkipsBadLines() {
        val entries = listOf(ScoreEntry(1234L, 4, 1_700_000_000_000L), ScoreEntry(5L, 1, 2L))
        assertEquals(entries, ScoreList.decode(ScoreList.encode(entries)))
        assertEquals(1, ScoreList.decode("garbage\n7;2;3\n1;x;2").size)
        assertTrue(ScoreList.decode(null).isEmpty())
    }
}

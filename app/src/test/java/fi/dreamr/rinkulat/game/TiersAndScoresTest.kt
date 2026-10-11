package fi.dreamr.rinkulat.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TiersAndScoresTest {
    @Test
    fun levelsGrowByTwoRingsAndTenSeconds() {
        assertEquals(5, Tiers.ringsFor(1))
        assertEquals(60, Tiers.secondsFor(1))
        assertEquals(7, Tiers.ringsFor(2))
        assertEquals(70, Tiers.secondsFor(2))
    }

    @Test
    fun tenLevelsFitOnThePegs() {
        assertEquals(24, ChamberWorld.RING_CAPACITY)
        assertEquals(10, Tiers.MAX_LEVEL)
        assertTrue(Tiers.ringsFor(Tiers.MAX_LEVEL) <= ChamberWorld.RING_CAPACITY)
        assertTrue(Tiers.ringsFor(Tiers.MAX_LEVEL + 1) > ChamberWorld.RING_CAPACITY)
        // Every ring stacked on the fullest possible peg still sits below the tip.
        val perPeg = ChamberWorld.RING_CAPACITY / ChamberWorld.PEG_COUNT
        val topRingY = ChamberWorld.PEG_BASE_Y - (perPeg - 1) * ChamberWorld.RING_STACK_HEIGHT
        assertTrue(topRingY >= ChamberWorld.PEG_TIP_Y)
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
    fun scoreListRoundTripsAndSkipsBadLines() {
        val entries = listOf(ScoreEntry(1234L, 4, 1_700_000_000_000L), ScoreEntry(5L, 1, 2L))
        assertEquals(entries, ScoreList.decode(ScoreList.encode(entries)))
        assertEquals(1, ScoreList.decode("garbage\n7;2;3\n1;x;2").size)
        assertTrue(ScoreList.decode(null).isEmpty())
    }
}

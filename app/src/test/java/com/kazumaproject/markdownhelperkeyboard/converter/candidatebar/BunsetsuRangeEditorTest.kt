package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BunsetsuRangeEditorTest {

    private val reading = "きょうははれ" // 今日は|晴れ
    private val boundaries = listOf(0, 4, 6)

    @Test
    fun boundariesFromSegmentReadings() {
        assertEquals(listOf(0, 4, 6), BunsetsuRangeEditor.boundariesOf(listOf("きょうは", "はれ")))
        assertEquals(listOf(0), BunsetsuRangeEditor.boundariesOf(emptyList()))
    }

    @Test
    fun shrinkFirstBunsetsuMovesTheRestIntoTheRemainder() {
        val r = BunsetsuRangeEditor.resizeFocused(reading, boundaries, focusedIndex = 0, delta = -1)!!
        assertEquals(0, r.focusedStart)
        assertEquals(3, r.focusedEnd)
        assertEquals(3, r.remainderStart)
        assertTrue(r.hasRemainder)
        // remainder "はれ" was "は|れ"?  Sumire says no split -> one bunsetsu
        assertEquals(listOf(0, 3, 6), r.boundariesWithRemainderSplits(emptyList()))
        assertEquals(listOf(0, 3, 4, 6), r.boundariesWithRemainderSplits(listOf(1)))
    }

    @Test
    fun extendFirstBunsetsuConsumesFollowingReading() {
        val r = BunsetsuRangeEditor.resizeFocused(reading, boundaries, 0, +1)!!
        assertEquals(5, r.focusedEnd)
        assertEquals(listOf(0, 5, 6), r.boundariesWithRemainderSplits(emptyList()))
        val all = BunsetsuRangeEditor.resizeFocused(reading, boundaries, 0, +10)!!
        assertEquals(6, all.focusedEnd)
        assertFalse(all.hasRemainder)
        assertEquals(listOf(0, 6), all.boundariesWithRemainderSplits(listOf(1, 2)))
    }

    @Test
    fun shrinkingLastBunsetsuCreatesANewOne() {
        val r = BunsetsuRangeEditor.resizeFocused(reading, boundaries, 1, -1)!!
        assertEquals(4, r.focusedStart)
        assertEquals(5, r.focusedEnd)
        assertEquals(listOf(0, 4), r.keptBoundaries)
        assertEquals(listOf(0, 4, 5, 6), r.boundariesWithRemainderSplits(emptyList()))
    }

    @Test
    fun cannotShrinkBelowOneCharOrExtendPastTheEnd() {
        assertNull(BunsetsuRangeEditor.resizeFocused("あい", listOf(0, 1, 2), 0, -1))
        assertNull(BunsetsuRangeEditor.resizeFocused(reading, boundaries, 1, +1))
        assertNull(BunsetsuRangeEditor.resizeFocused(reading, boundaries, 0, 0))
        assertNull(BunsetsuRangeEditor.resizeFocused(reading, boundaries, 2, -1))
        assertNull(BunsetsuRangeEditor.resizeFocused(reading, boundaries, -1, -1))
    }

    @Test
    fun invalidBoundariesAreRejected() {
        assertNull(BunsetsuRangeEditor.resizeFocused(reading, listOf(0, 4), 0, -1)) // does not reach end
        assertNull(BunsetsuRangeEditor.resizeFocused(reading, listOf(0, 4, 4, 6), 0, -1))
        assertNull(BunsetsuRangeEditor.resizeFocused(reading, listOf(1, 4, 6), 0, -1))
    }

    @Test
    fun surrogatePairsAreNeverSplit() {
        // "𠮷" = U+20BB7 = two UTF-16 units. Reading: "𠮷のや" → units: [hi, lo, の, や]
        val r = "\uD842\uDFB7のや"
        assertEquals(4, r.length)
        // extend first bunsetsu "𠮷" (0..2) by one: 0..3
        val ext = BunsetsuRangeEditor.resizeFocused(r, listOf(0, 2, 4), 0, +1)!!
        assertEquals(3, ext.focusedEnd)
        // shrink "𠮷の" (0..3) by one: back to 0..2, not 0..1
        val shr = BunsetsuRangeEditor.resizeFocused(r, listOf(0, 3, 4), 0, -1)!!
        assertEquals(2, shr.focusedEnd)
        // "𠮷" alone cannot shrink further
        assertNull(BunsetsuRangeEditor.resizeFocused(r, listOf(0, 2, 4), 0, -1))
        // extending "の" (2..3) in "の𠮷": units [の, hi, lo] -> jumps over the pair
        val r2 = "の\uD842\uDFB7"
        val e2 = BunsetsuRangeEditor.resizeFocused(r2, listOf(0, 1, 3), 0, +1)!!
        assertEquals(3, e2.focusedEnd)
        // boundary inside a pair is invalid
        assertFalse(BunsetsuRangeEditor.isValid(r2, listOf(0, 2, 3)))
    }

    @Test
    fun interiorSplits() {
        assertEquals(listOf(4), BunsetsuRangeEditor.interiorSplits(listOf(0, 4, 6)))
        assertEquals(emptyList<Int>(), BunsetsuRangeEditor.interiorSplits(listOf(0, 6)))
    }

    @Test
    fun remainderSplitsOutOfRangeAreIgnored() {
        val r = BunsetsuRangeEditor.resizeFocused(reading, boundaries, 0, -2)!! // 0..2, rem "うははれ"
        assertEquals(listOf(0, 2, 4, 6), r.boundariesWithRemainderSplits(listOf(0, 2, 2, 4, 9)))
    }
}

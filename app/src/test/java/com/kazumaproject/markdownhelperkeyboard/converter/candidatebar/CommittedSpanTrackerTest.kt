package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.CommittedSpanTracker.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommittedSpanTrackerTest {

    private val slots = listOf(
        Slot(0, "和菓子を", "わかしを", listOf("和菓子を", "沸かしを")),
        Slot(4, "買って", "かって", listOf("買って", "勝手")),
    )

    @Test
    fun caretStrictlyInsideABunsetsuHitsIt() {
        val t = CommittedSpanTracker()
        t.record(10, "和菓子を買って", slots)
        assertEquals(0, t.hitAt(12, 12)!!.slotIndex)
        assertEquals(1, t.hitAt(15, 15)!!.slotIndex)
        // Right after the commit (end) and on boundaries: nothing.
        assertNull(t.hitAt(17, 17))
        assertNull(t.hitAt(14, 14))
        assertNull(t.hitAt(10, 10))
        // A selection covering exactly one bunsetsu.
        assertEquals(1, t.hitAt(14, 17)!!.slotIndex)
        assertNull(t.hitAt(13, 17))
    }

    @Test
    fun verifyComparesTheEditorTextAroundTheCursor() {
        val t = CommittedSpanTracker()
        t.record(10, "和菓子を買って", slots)
        val hit = t.hitAt(15, 15)!!
        assertTrue(t.verify(hit, 15, "前文和菓子を買", "って。"))
        assertFalse(t.verify(hit, 15, "前文和菓子が買", "って。"))
        assertFalse(t.verify(hit, 15, "和菓子を買", "た"))
        assertFalse(t.verify(hit, 15, null, "って"))
        t.invalidate(hit.entry)
        assertEquals(0, t.size)
    }

    @Test
    fun replacementShiftsLaterSlotsAndLaterEntries() {
        val t = CommittedSpanTracker()
        t.record(0, "和菓子を買って", slots)
        t.record(7, "勝手", listOf(Slot(0, "勝手", "かって", listOf("勝手", "買って"))))
        val hit = t.hitAt(2, 2)!!
        val updated = t.applyReplacement(hit, "わかしを")!!
        assertEquals("わかしを買って", updated.text)
        assertEquals(listOf("わかしを", "和菓子を", "沸かしを"), updated.slots[0].alternatives)
        assertEquals(4, updated.slots[1].start)
        // Same length: the later entry stays at 7.
        assertEquals(7, t.entries()[1].start)
        val again = t.hitAt(5, 5)!!
        // 買って (3) -> 勝 (1): the later entry moves 2 to the left.
        t.applyReplacement(again, "勝")
        assertEquals(5, t.entries()[1].start)
        assertNotNull(t.hitAt(5, 7))
    }

    @Test
    fun boundsOverlapAndInvalidInputs() {
        val t = CommittedSpanTracker(maxEntries = 2, maxChars = 8)
        val one = listOf(Slot(0, "勝手", "かって", listOf("勝手", "買って")))
        t.record(0, "勝手", one)
        t.record(10, "勝手", one)
        t.record(20, "勝手", one)
        assertEquals(listOf(10, 20), t.entries().map { it.start })
        // Overlapping commit replaces the old one.
        t.record(21, "勝手", one)
        assertEquals(listOf(10, 21), t.entries().map { it.start })
        // Character bound drops the oldest.
        t.record(40, "和菓子を買って", slots)
        assertEquals(listOf(40), t.entries().map { it.start })
        // Slots that do not spell the text, or nothing to offer: ignored.
        t.record(60, "和菓子", slots)
        t.record(70, "勝手", listOf(Slot(0, "勝手", "かって", listOf("勝手"))))
        assertEquals(listOf(40), t.entries().map { it.start })
        t.clear()
        assertEquals(0, t.size)
    }

    @Test
    fun prefixSlotsMatchAPartialCommit() {
        assertEquals(1, CommittedSpanTracker.prefixSlots(slots, "和菓子を")!!.size)
        assertEquals(2, CommittedSpanTracker.prefixSlots(slots, "和菓子を買って")!!.size)
        assertNull(CommittedSpanTracker.prefixSlots(slots, "和菓子"))
        assertNull(CommittedSpanTracker.prefixSlots(slots, "沸かしを"))
    }
}

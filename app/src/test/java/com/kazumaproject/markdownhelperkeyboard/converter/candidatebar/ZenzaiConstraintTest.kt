package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenzaiConstraintTest {
    private fun seg(start: Int, end: Int, out: String) = CandidateConversionSegment(start, end, out)

    // きょうは|いい|てんき -> 今日は|いい|天気
    private val segments = listOf(seg(0, 4, "今日は"), seg(4, 6, "いい"), seg(6, 9, "天気"))
    private val input = "きょうはいいてんき"

    @Test
    fun buildsConstraintWithReadingSpan() {
        val c = ZenzaiConstraint.from("L", input, "今日はいい", segments)!!
        assertEquals("きょうはいい", c.reading)
        assertEquals(listOf(4 to 3, 6 to 5), c.boundaries)
        assertNull(ZenzaiConstraint.from("L", input, "教派", segments))
    }

    @Test
    fun keepsWhileReadingExtends() {
        val c = ZenzaiConstraint.from("L", input, "今日はいい", segments)!!
        assertSame(c, c.carriedFor(input + "だ", "L"))
        assertSame(c, c.carriedFor("きょうはいい", "L"))
    }

    @Test
    fun trimsOnBackspaceAndEditAndDropsOnContextChange() {
        val c = ZenzaiConstraint.from("L", input, "今日はいい", segments)!!
        val trimmed = c.carriedFor("きょうはい", "L")!!
        assertEquals("今日は", trimmed.surface)
        assertEquals("きょうは", trimmed.reading)
        assertEquals("今日は", c.carriedFor("きょうはえ", "L")!!.surface)
        assertNull(c.carriedFor("きょう", "L"))
        assertNull(c.carriedFor("きのうはいい", "L"))
        assertNull(c.carriedFor(input, "other"))
    }

    @Test
    fun dropsWhenNextCharWouldSplitMora() {
        val c = ZenzaiConstraint.from("L", "かんじ", "漢字", listOf(seg(0, 3, "漢字")))!!
        assertNull(c.carriedFor("かんじょ", "L"))
        assertSame(c, c.carriedFor("かんじを", "L"))
    }

    @Test
    fun realizedOnlyWithSameReadingSpan() {
        val c = ZenzaiConstraint.from("L", input, "今日は", segments)!!
        assertTrue(c.isRealizedBy(segments))
        assertFalse(c.isRealizedBy(listOf(seg(0, 3, "今日"), seg(3, 5, "は"))))
        assertFalse(c.isRealizedBy(emptyList()))
    }

    @Test
    fun fixMustKeepUserDictionarySegments() {
        val user = seg(0, 3, "杏里")
        assertTrue(ZenzaiConstraint.preserves(listOf(user, seg(3, 4, "と")), listOf(user)))
        assertFalse(ZenzaiConstraint.preserves(listOf(seg(0, 3, "アンリ"), seg(3, 4, "と")), listOf(user)))
        assertTrue(ZenzaiConstraint.preserves(listOf(seg(0, 3, "アンリ")), emptyList()))
    }

    @Test
    fun onlyUserDictionaryIsProtectedNotLearnedWords() {
        assertTrue(ZenzaiConstraint.isProtectedCandidateType(CANDIDATE_TYPE_USER_DICTIONARY))
        assertFalse(ZenzaiConstraint.isProtectedCandidateType(CANDIDATE_TYPE_LEARNED_DICTIONARY))
    }
}

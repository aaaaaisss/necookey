package com.kazumaproject.markdownhelperkeyboard.zeroquery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroQueryBackoffTest {
    @Test
    fun triesWholeKeyThenWordLikeTailsLongestFirst() {
        val keys = ZeroQueryBackoff.keys("どうもありがとう")
        assertEquals("どうもありがとう", keys.first())
        assertTrue(keys.indexOf("ありがとう") > 0)
        assertFalse("hiragana tails shorter than 3 are mid-word", "とう" in keys)
        assertTrue(keys.indexOf("もありがとう") < keys.indexOf("ありがとう"))
    }

    @Test
    fun kanjiTailsMayBeTwoCharsAndSmallKanaNeverStartsATail() {
        val keys = ZeroQueryBackoff.keys("明日の天気")
        assertTrue("天気" in keys)
        assertFalse(ZeroQueryBackoff.keys("ちょっと").any { it.startsWith("ょ") || it.startsWith("っ") })
    }

    @Test
    fun firstHitStopsAtTheFirstKeyFound() {
        val dict = mapOf("ありがとう" to listOf("ございます"), "がとう" to listOf("x"))
        assertEquals(listOf("ございます"), ZeroQueryBackoff.firstHit("どうもありがとう") { dict[it].orEmpty() })
        assertEquals(emptyList<String>(), ZeroQueryBackoff.firstHit("") { dict[it].orEmpty() })
    }
}

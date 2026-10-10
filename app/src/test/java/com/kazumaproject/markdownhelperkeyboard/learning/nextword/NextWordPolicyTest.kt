package com.kazumaproject.markdownhelperkeyboard.learning.nextword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextWordPolicyTest {
    @Test
    fun pairsChainBunsetsuWithTheirPrecedingOutput() {
        val pairs = NextWordPolicy.pairs(
            precedingBunsetsu = "今日は",
            bunsetsu = listOf("いい" to "良い", "てんき" to "天気"),
            timestamp = 1L,
        )
        assertEquals(
            listOf(Triple("今日は", "いい", "良い"), Triple("良い", "てんき", "天気")),
            pairs.map { Triple(it.context, it.reading, it.output) },
        )
    }

    @Test
    fun firstBunsetsuWithoutKnownPrecedingTextIsNotLearned() {
        val pairs = NextWordPolicy.pairs(null, listOf("きょう" to "今日", "は" to "は"), 1L)
        assertEquals(listOf(Triple("今日", "は", "は")), pairs.map { Triple(it.context, it.reading, it.output) })
    }

    @Test
    fun readingsOverThePredictionCapAreNotLearned() {
        val long = "あ".repeat(NextWordPolicy.MAX_READING_LENGTH + 1)
        assertTrue(NextWordPolicy.pairs("前", listOf(long to "長"), 1L).isEmpty())
    }

    @Test
    fun contextKeyKeepsOnlyTheTail() {
        assertEquals(NextWordPolicy.MAX_CONTEXT_LENGTH, NextWordPolicy.contextKey("一二三四五六七八九十")!!.length)
        assertNull(NextWordPolicy.contextKey("  "))
    }

    @Test
    fun lookupContextsAreSuffixesLongestFirst() {
        assertEquals(listOf("は今日は", "今日は", "日は", "は"), NextWordPolicy.lookupContexts("は今日は"))
        assertTrue(NextWordPolicy.lookupContexts("文章の最後の部分です今日は").first().length == NextWordPolicy.MAX_CONTEXT_LENGTH)
    }

    @Test
    fun learnedPhraseContinuationIsTheRemainder() {
        assertEquals("良い", NextWordPolicy.remainderAfter("今日は", "今日は良い"))
        assertNull(NextWordPolicy.remainderAfter("今日は", "今日は"))
        assertNull(NextWordPolicy.remainderAfter("明日", "今日は良い"))
    }
}

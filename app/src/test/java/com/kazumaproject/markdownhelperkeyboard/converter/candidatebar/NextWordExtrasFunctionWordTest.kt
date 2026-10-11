package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextWordExtrasFunctionWordTest {
    private fun c(s: String) = Candidate(string = s, type = 1, length = 0u, score = 0)

    @Test
    fun functionWordsAreNotOfferedRightAfterPunctuation() {
        assertEquals(listOf("は", "が", "を", "に", "、"), NextWordExtras.functionWordsAfter("今日"))
        assertEquals(listOf("。", "、", "ね", "よ", "！"), NextWordExtras.functionWordsAfter("行きます"))
        assertEquals(emptyList<String>(), NextWordExtras.functionWordsAfter("今日。"))
        assertEquals(emptyList<String>(), NextWordExtras.functionWordsAfter(""))
    }

    @Test
    fun zenzPicksTopThreeFunctionWordsAndKeepsLearnedNextWords() {
        val base = listOf(c("a"), c("b"), c("c"), c("d"))
        val fw = listOf("、", "。", "は", "が", "を").map(::c)
        val emoji = listOf(c("😀"))
        // head a..d, fw 、 。 は が を, emoji
        val scores = floatArrayOf(-3f, -3f, -3f, -3f, -9f, -1f, -2f, -0.5f, -8f, -4f)
        val out = NextWordExtras.rerank(base, scores, fw, 1, listOf(emoji[0] to 0)).map { it.string }
        assertEquals(listOf("a", "😀", "b"), out.take(3))
        assertEquals(listOf("が", "。", "は"), out.filter { it in setOf("、", "。", "は", "が", "を") })
        assertTrue(out.containsAll(listOf("a", "b", "c", "d", "😀")))
        assertEquals(8, out.size)
    }

    @Test
    fun initialShowsAtMostThreeFunctionWordsAfterTopBase() {
        val out = NextWordExtras.initial(listOf(c("a"), c("b"), c("c")), listOf("、", "。", "は", "が").map(::c))
        assertEquals(listOf("a", "b", "、", "。", "は", "c"), out.map { it.string })
    }

    @Test
    fun connectionCostOrdersFunctionWordsWithoutZenz() {
        val costs = mapOf("は" to 900, "が" to 400, "を" to 400, "。" to 3000, "ね" to 5000)
        assertEquals(listOf("が", "を", "は"), NextWordExtras.functionWordsByConnection("天気", costs))
        assertEquals(emptyList<String>(), NextWordExtras.functionWordsByConnection("天気。", costs))
    }

    @Test
    fun rankedFunctionWordsAreNotScoredAndStayAfterTopTwo() {
        val base = listOf(c("a"), c("b"), c("c"))
        val ranked = listOf(c("が"), c("を"))
        val targets = NextWordExtras.scoringTargets(base, emptyList(), emptyList())
        assertEquals(listOf("a", "b", "c"), targets)
        val out = NextWordExtras.rerank(base, floatArrayOf(-1f, -2f, -3f), emptyList(), 0, emptyList(), ranked).map { it.string }
        assertEquals(listOf("a", "b", "が", "を", "c"), out)
    }
}

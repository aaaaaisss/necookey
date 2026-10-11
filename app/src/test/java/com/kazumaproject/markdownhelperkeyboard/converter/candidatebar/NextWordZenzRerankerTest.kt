package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Test

class NextWordZenzRerankerTest {
    private fun c(s: String) = Candidate(string = s, type = 1, length = 0u, score = 0)

    @Test
    fun clearlyBetterZenzScoreMovesUp() {
        val list = listOf(c("a"), c("b"), c("c"))
        val out = NextWordZenzReranker.rerank(list, floatArrayOf(-3f, -1f, -2.9f))
        assertEquals(listOf("b", "a", "c"), out.map { it.string })
    }

    @Test
    fun smallDifferencesKeepOriginalOrderAndNothingIsDropped() {
        val list = listOf(c("a"), c("b"), c("c"))
        val out = NextWordZenzReranker.rerank(list, floatArrayOf(-2f, -1.9f, -1.95f))
        assertEquals(listOf("a", "b", "c"), out.map { it.string })
    }

    @Test
    fun invalidScoresKeepList() {
        val list = listOf(c("a"), c("b"))
        assertEquals(list, NextWordZenzReranker.rerank(list, floatArrayOf(Float.NaN, Float.NaN)))
        assertEquals(list, NextWordZenzReranker.rerank(list, floatArrayOf(-1f)))
    }
}

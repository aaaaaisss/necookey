package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextWordExtrasTest {
    private fun c(s: String) = Candidate(string = s, type = 1, length = 0u, score = 0)

    @Test
    fun initialKeepsBaseOrderWithoutFunctionWords() {
        val out = NextWordExtras.initial(listOf(c("a"), c("b"), c("c")))
        assertEquals(listOf("a", "b", "c"), out.map { it.string })
    }

    @Test
    fun rerankKeepsEveryBaseCandidate() {
        val base = listOf(c("a"), c("b"), c("c"))
        val scores = floatArrayOf(-5f, -1f, -2f)
        val out = NextWordExtras.rerank(base, scores).map { it.string }
        assertEquals(setOf("a", "b", "c"), out.toSet())
        assertEquals(3, out.size)
    }

    @Test
    fun scoringTargetsCappedAtMaxScoredKeepingLearnedHead() {
        val base = (1..12).map { c("b$it") }
        val fw = NextWordExtras.FUNCTION_WORDS.map(::c)
        val targets = NextWordExtras.scoringTargets(base, fw)
        assertTrue(targets.size <= NextWordExtras.MAX_SCORED)
        assertEquals(base.take(NextWordZenzReranker.TOP_K).map { it.string }, targets.take(NextWordZenzReranker.TOP_K))
        assertEquals(NextWordExtras.FUNCTION_WORD_POOL, targets.count { t -> fw.any { it.string == t } })
        val scores = FloatArray(targets.size) { -1f }
        assertTrue(NextWordExtras.rerank(base, scores, fw).map { it.string }.containsAll(base.map { it.string }))
    }

    @Test
    fun rerankWithBadScoresFallsBackToInitial() {
        val base = listOf(c("a"), c("b"))
        assertEquals(NextWordExtras.initial(base), NextWordExtras.rerank(base, floatArrayOf(-1f)))
    }
}

package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextWordExtrasTest {
    private fun c(s: String) = Candidate(string = s, type = 1, length = 0u, score = 0)
    private fun e(s: String) = Candidate(string = s, type = 11, length = 0u, score = 0)

    @Test
    fun initialShowsNoEmojiBeforeZenz() {
        val out = NextWordExtras.initial(listOf(c("a"), c("b"), c("c")))
        assertEquals(listOf("a", "b", "c"), out.map { it.string })
    }

    @Test
    fun rerankKeepsTopBaseAndMergesPickedEmoji() {
        val base = listOf(c("a"), c("b"), c("c"))
        // head a b c, then 4 emoji keywords; picked emoji are placed by their keyword score
        val scores = floatArrayOf(-2f, -2f, -5f, -9f, -3f, -1f, -8f)
        val out = NextWordExtras.rerank(base, scores, emptyList(), 4, listOf(e("E2") to 2, e("E1") to 1)).map { it.string }
        // emoji sit right after the first and second learned words
        assertEquals(listOf("a", "E2", "b", "E1"), out.take(4))
        assertEquals(listOf("E2", "E1"), out.filter { it.startsWith("E") })
        assertTrue(out.containsAll(listOf("a", "b", "c")))
        assertEquals(5, out.size)
    }

    @Test
    fun scoringTargetsCappedAtMaxScoredKeepingLearnedHead() {
        val base = (1..12).map { c("b$it") }
        val fw = NextWordExtras.FUNCTION_WORDS.map(::c)
        val keywords = (1..ContextEmojiSelector.POOL).map { "k$it" }
        val targets = NextWordExtras.scoringTargets(base, keywords, fw)
        assertTrue(targets.size <= NextWordExtras.MAX_SCORED)
        assertEquals(base.take(NextWordZenzReranker.TOP_K).map { it.string }, targets.take(NextWordZenzReranker.TOP_K))
        assertEquals(NextWordExtras.FUNCTION_WORD_POOL, targets.count { t -> fw.any { it.string == t } })
        assertEquals(keywords, targets.takeLast(keywords.size))
        val scores = FloatArray(targets.size) { -1f }
        assertTrue(
            NextWordExtras.rerank(base, scores, fw, keywords.size).map { it.string }.containsAll(base.map { it.string }),
        )
    }

    @Test
    fun rerankWithBadScoresFallsBackToInitial() {
        val base = listOf(c("a"), c("b"))
        assertEquals(NextWordExtras.initial(base), NextWordExtras.rerank(base, floatArrayOf(-1f), emptyList(), 1, listOf(e("E") to 0)))
    }
}

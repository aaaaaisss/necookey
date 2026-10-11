package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextWordExtrasTest {
    private fun c(s: String) = Candidate(string = s, type = 1, length = 0u, score = 0)
    private fun e(s: String) = Candidate(string = s, type = 11, length = 0u, score = 0)

    @Test
    fun emojiKeysPreferRecentWordsThenLeadingWords() {
        val keys = NextWordExtras.emojiKeys("かわいいねこ")
        assertEquals("かわいいねこ", keys.first())
        assertTrue(keys.indexOf("ねこ") < keys.indexOf("かわいい"))
        assertTrue("かわいい" in keys)
        assertEquals(emptyList<String>(), NextWordExtras.emojiKeys("ね"))
    }

    @Test
    fun emojiPoolDropsDuplicatesOfBaseAndCaps() {
        val pool = NextWordExtras.emojiPool(listOf(c("🐱")), (1..9).map { e("$it") } + e("🐱"))
        assertEquals(NextWordExtras.EMOJI_POOL, pool.size)
        assertTrue(pool.none { it.string == "🐱" })
    }

    @Test
    fun initialKeepsBaseFirstAndShowsAtMostThreeEmoji() {
        val out = NextWordExtras.initial(listOf(c("a"), c("b"), c("c")), (1..5).map { e("e$it") })
        assertEquals(listOf("a", "b", "e1", "e2", "e3", "c"), out.map { it.string })
    }

    @Test
    fun rerankKeepsTopBaseAndPicksBestEmojiByScore() {
        val base = listOf(c("a"), c("b"), c("c"))
        val emoji = listOf(e("e1"), e("e2"), e("e3"), e("e4"))
        // only the first MAX_EMOJI emoji are scored; e3 best, e1 worst; base never dropped
        val scores = floatArrayOf(-2f, -2f, -5f, -9f, -3f, -1f)
        val out = NextWordExtras.rerank(base, emoji, scores).map { it.string }
        assertEquals(listOf("a", "b"), out.take(2))
        assertEquals(listOf("e3", "e2", "e1"), out.filter { it.startsWith("e") })
        assertTrue(out.containsAll(listOf("a", "b", "c")))
        assertEquals(6, out.size)
    }

    @Test
    fun scoringTargetsCappedAtMaxScoredKeepingLearnedHead() {
        val base = (1..12).map { c("b$it") }
        val fw = NextWordExtras.FUNCTION_WORDS.map(::c)
        val emoji = (1..5).map { e("e$it") }
        val targets = NextWordExtras.scoringTargets(base, emoji, fw)
        assertEquals(NextWordExtras.MAX_SCORED, targets.size)
        assertEquals(base.take(8), targets.take(8))
        assertEquals(NextWordExtras.FUNCTION_WORD_POOL, targets.count { it in fw })
        assertEquals(NextWordExtras.MAX_EMOJI, targets.count { it in emoji })
        val scores = FloatArray(targets.size) { -1f }
        assertTrue(NextWordExtras.rerank(base, emoji, scores, fw).map { it.string }.containsAll(base.map { it.string }))
    }

    @Test
    fun rerankWithBadScoresFallsBackToInitial() {
        val base = listOf(c("a"), c("b"))
        val emoji = listOf(e("e1"))
        assertEquals(NextWordExtras.initial(base, emoji), NextWordExtras.rerank(base, emoji, floatArrayOf(-1f)))
    }
}

package com.kazumaproject.markdownhelperkeyboard.learning

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.learning.nextword.NextWordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningFeedbackPlanTest {
    private fun c(text: String, type: Byte = 1, yomi: String? = null) =
        Candidate(string = text, type = type, length = 3u, score = 0, yomi = yomi)

    @Test
    fun shownButNotCommittedIsIgnored() {
        val next = NextWordEntity(context = "今日は", reading = "いい", output = "良い", lastUsedAt = 1L)
        val plan = LearningFeedbackPlan.of(
            primary = "居い",
            topPredictions = listOf(c("良い"), c("いいね", CANDIDATE_TYPE_LEARNED_DICTIONARY, "いいね")),
            nextWords = mapOf("良い" to next),
            committedText = "いい",
        )
        assertEquals("居い", plan.ignoredPrimary)
        assertEquals(listOf("いいね" to "いいね"), plan.ignoredLearnedPredictions)
        assertEquals(listOf(next), plan.ignoredNextWords)
    }

    @Test
    fun committedCandidateIsNeverWeakened() {
        val next = NextWordEntity(context = "今日は", reading = "いい", output = "良い", lastUsedAt = 1L)
        val plan = LearningFeedbackPlan.of(
            primary = "良い",
            topPredictions = listOf(c("良い", CANDIDATE_TYPE_LEARNED_DICTIONARY, "いい")),
            nextWords = mapOf("良い" to next),
            committedText = "良い",
        )
        assertNull(plan.ignoredPrimary)
        assertTrue(plan.ignoredLearnedPredictions.isEmpty())
        assertTrue(plan.ignoredNextWords.isEmpty())
    }
}

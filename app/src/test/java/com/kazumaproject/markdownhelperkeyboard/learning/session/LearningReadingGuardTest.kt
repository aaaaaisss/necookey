package com.kazumaproject.markdownhelperkeyboard.learning.session

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningReadingGuardTest {
    private fun candidate(string: String, length: Int, yomi: String? = null) =
        Candidate(string = string, type = 1, length = length.toUByte(), score = 0, yomi = yomi)

    @Test
    fun staleTopRowAfterSameLengthEditIsRejected() {
        // Bar built for "はは" (母), user toggled dakuten to "ばば" and tapped before refresh.
        assertFalse(
            LearningReadingGuard.candidateMatchesReading(candidate("母", 2), "ばば", candidateInput = "はは")
        )
    }

    @Test
    fun currentTopRowIsAccepted() {
        assertTrue(
            LearningReadingGuard.candidateMatchesReading(candidate("母", 2), "はは", candidateInput = "はは")
        )
    }

    @Test
    fun firstBunsetsuOfCurrentInputIsAccepted() {
        assertTrue(
            LearningReadingGuard.candidateMatchesReading(
                candidate("今日", 3, yomi = "きょう"), "きょう", candidateInput = "きょうは",
            )
        )
    }

    @Test
    fun yomiOfAnotherReadingIsRejected() {
        assertFalse(LearningReadingGuard.candidateMatchesReading(candidate("母", 2, yomi = "はは"), "ばば"))
    }

    @Test
    fun conversionPathOfAnotherLengthIsRejected() {
        val c = candidate("今日", 3).copy(
            conversionSegments = listOf(CandidateConversionSegment(0, 4, "今日")),
        )
        assertFalse(LearningReadingGuard.candidateMatchesReading(c, "きょう"))
    }

    @Test
    fun unknownListInputFallsBackToCandidateMetadata() {
        assertTrue(LearningReadingGuard.candidateMatchesReading(candidate("今日", 3), "きょう"))
    }
}

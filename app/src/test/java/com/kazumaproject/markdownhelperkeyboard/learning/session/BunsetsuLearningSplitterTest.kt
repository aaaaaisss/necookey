package com.kazumaproject.markdownhelperkeyboard.learning.session

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BunsetsuLearningSplitterTest {
    private val reading = "きょうはいいてんき"
    private val words = listOf(
        CandidateConversionSegment(0, 3, "今日"),
        CandidateConversionSegment(3, 4, "は"),
        CandidateConversionSegment(4, 6, "良い"),
        CandidateConversionSegment(6, 9, "天気"),
    )

    @Test
    fun groupsWordsIntoBunsetsuAtBoundaries() {
        val parts = BunsetsuLearningSplitter.split(reading, "今日は良い天気", words, listOf(4, 6, 9))
        assertEquals(
            listOf(
                LearnedBunsetsu("きょうは", "今日は"),
                LearnedBunsetsu("いい", "良い"),
                LearnedBunsetsu("てんき", "天気"),
            ),
            parts,
        )
    }

    @Test
    fun zenzOverrideWithBunsetsuNodesKeepsReadingAligned() {
        // zenz re-chose the 2nd bunsetsu: its nodes are the bunsetsu themselves.
        val nodes = listOf(
            CandidateConversionSegment(0, 4, "今日は"),
            CandidateConversionSegment(4, 6, "いい"),
            CandidateConversionSegment(6, 9, "天気"),
        )
        val parts = BunsetsuLearningSplitter.split(reading, "今日はいい天気", nodes, listOf(4, 6, 9))
        assertEquals(LearnedBunsetsu("いい", "いい"), parts!![1])
    }

    @Test
    fun pathThatDoesNotSpellOutputIsRejected() {
        // Stale path (output changed after the segments were attached).
        assertNull(BunsetsuLearningSplitter.split(reading, "今日は良い転機", words, listOf(4, 6)))
    }

    @Test
    fun pathForAnotherReadingLengthIsRejected() {
        assertNull(BunsetsuLearningSplitter.split("きょうはいいてんきだ", "今日は良い天気", words, listOf(4)))
    }

    @Test
    fun prefixReadingUsesOnlyBoundariesInside() {
        val parts = BunsetsuLearningSplitter.split("きょうは", "今日は", words.take(2), listOf(4, 6, 9))
        assertEquals(listOf(LearnedBunsetsu("きょうは", "今日は")), parts)
    }
}

package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenzaiGateTest {
    private fun c(s: String, len: Int, score: Int, type: Byte = 1) =
        Candidate(string = s, type = type, length = len.toUByte(), score = score)

    @Test
    fun gapIgnoresKanaAndPartialCandidates() {
        val input = "きょうはいいてんき"
        val top = c("今日はいい天気", input.length, 16000)
        val list = listOf(
            top,
            c("きょうはいいてんき", input.length, 8000, type = 3), // fixed-score kana candidate
            c("キョウハイイテンキ", input.length, 8000, type = 4),
            c("今日は", 3, 3000), // partial
            c("今日は良い天気", input.length, 16900),
            c("今日はいい転機", input.length, 17500),
        )
        assertEquals(900, ZenzaiGate.pathCostGap(input, list, top))
    }

    @Test
    fun noGapWhenTopIsNotASystemPathOrHasNoRival() {
        val input = "あんり"
        val learned = c("杏里", 3, 100, type = CANDIDATE_TYPE_LEARNED_DICTIONARY)
        assertNull(ZenzaiGate.pathCostGap(input, listOf(learned, c("アンリ", 3, 5000)), learned))
        val only = c("杏里", 3, 5000)
        assertNull(ZenzaiGate.pathCostGap(input, listOf(only), only))
        // Unknown gap always evaluates.
        assertTrue(ZenzaiGate.shouldEvaluate(3, 0, null))
    }

    @Test
    fun thresholdsFollowLengthAndContextBuckets() {
        for ((maxLen, row) in ZenzaiGate.THRESHOLDS) {
            val len = if (maxLen == Int.MAX_VALUE) 40 else maxLen
            assertEquals(row[0], ZenzaiGate.threshold(len, 0))
            assertEquals(row[1], ZenzaiGate.threshold(len, 12))
            val t = row[1]
            if (t != Int.MAX_VALUE) {
                assertTrue(ZenzaiGate.shouldEvaluate(len, 12, t - 1))
                assertFalse(ZenzaiGate.shouldEvaluate(len, 12, t))
            }
        }
        // Buckets are ordered and cover every length.
        val bounds = ZenzaiGate.THRESHOLDS.map { it.first }
        assertEquals(bounds.sorted(), bounds)
        assertEquals(Int.MAX_VALUE, bounds.last())
    }

    @Test
    fun calibratedTableSkipsOnlyConfidentReadings() {
        // Every bucket must still evaluate small gaps (zenz keeps fixing close calls).
        for ((maxLen, row) in ZenzaiGate.THRESHOLDS) {
            val len = if (maxLen == Int.MAX_VALUE) 40 else maxLen
            assertTrue(ZenzaiGate.shouldEvaluate(len, 0, 100))
            assertTrue(ZenzaiGate.shouldEvaluate(len, 5, 100))
            assertTrue(row.all { it >= 100 })
        }
        // Huge gaps are skipped somewhere (the gate is active).
        assertTrue(ZenzaiGate.THRESHOLDS.any { (_, row) -> row.any { it != Int.MAX_VALUE } })
    }
}

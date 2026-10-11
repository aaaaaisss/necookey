package com.kazumaproject.markdownhelperkeyboard.learning.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningScorePolicyTest {
    private val day = 86_400_000L
    private val now = 1_000L * day

    @Test
    fun firstUseKeepsStoredScore() {
        assertEquals(3500, LearningScorePolicy.conversionCost(3500, 1, now, 4, now))
        assertEquals(300, LearningScorePolicy.conversionCost(300, 1, now, 4, now))
    }

    @Test
    fun repeatedUseStopsAtFloorInsteadOfZero() {
        // 何度使っても 0 にはならない。
        val many = LearningScorePolicy.conversionCost(0, 20, now, 4, now)
        assertEquals(LearningScorePolicy.floor(20, 4), many)
        assertTrue(many > 0)
        // 短い読みは下限が高い。
        assertTrue(LearningScorePolicy.conversionCost(0, 20, now, 1, now) > many)
    }

    @Test
    fun neverWeakerThanTheFirstUse() {
        // 初回 1800 → 2 回目 300。推定初回 1800 は下限 2000 より強いので 1800。
        assertEquals(1800, LearningScorePolicy.conversionCost(300, 2, now, 4, now))
        // 保存値が下限より弱いならそのまま。
        assertEquals(4000, LearningScorePolicy.conversionCost(4000, 3, now, 4, now))
    }

    @Test
    fun oldWordsWeakenAfterGraceAndUnknownDatesDoNot() {
        assertEquals(3000, LearningScorePolicy.conversionCost(3000, 1, now - 10 * day, 4, now))
        assertEquals(4000, LearningScorePolicy.conversionCost(3000, 1, now - 74 * day, 4, now))
        assertEquals(5000, LearningScorePolicy.conversionCost(3000, 1, now - 900 * day, 4, now))
        assertEquals(3000, LearningScorePolicy.conversionCost(3000, 1, 0L, 4, now))
    }
}

package com.kazumaproject.markdownhelperkeyboard.learning.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningScorePolicyTest {
    private val day = 86_400_000L
    private val now = 1_000L * day
    private val halfLife = (LearningScorePolicy.HALF_LIFE_DAYS * day).toLong()

    private fun cost(count: Int, lastUsedAt: Long, len: Int = 4, stored: Int = 9000) =
        LearningScorePolicy.conversionCost(stored, count, lastUsedAt, len, now)

    @Test
    fun oneUseIsStrongRightAway() {
        // 1 回確定しただけで最強のコスト（保存値は見ない）。辞書の普通の語（数千）より強い。
        assertEquals(LearningScorePolicy.boostCost(4), cost(1, now))
        assertTrue(cost(1, now)!! <= 500)
        // 短い読みは弱め。
        assertTrue(cost(1, now, len = 1)!! > cost(1, now, len = 2)!!)
        assertTrue(cost(1, now, len = 2)!! > cost(1, now, len = 3)!!)
    }

    @Test
    fun weakensWithinDaysAndDisappears() {
        val fresh = cost(1, now)!!
        val oneHalfLife = cost(1, now - halfLife)!!
        val twoHalfLives = cost(1, now - 2 * halfLife)!!
        assertTrue(fresh < oneHalfLife && oneHalfLife < twoHalfLives)
        assertEquals(fresh + 3000, oneHalfLife) // 強さ 0.5 で FORGET_RANGE の半分
        // 強さ 0.05 未満（1 回だけの語は約 13 日）で消える。
        assertNull(cost(1, now - 14 * day))
        assertTrue(LearningScorePolicy.isExpired(1, now - 14 * day, now))
    }

    @Test
    fun repeatedUseLastsLongerButIsCapped() {
        // 4 回使った語は 2 半減期たっても最強のまま。
        assertEquals(cost(1, now), cost(4, now - 2 * halfLife))
        // 回数は書くたびに減衰後 + 1 の切り上げ、上限あり。
        assertEquals(2, LearningScorePolicy.reinforcedCount(1, now, now))
        assertEquals(2, LearningScorePolicy.reinforcedCount(2, now - 2 * halfLife, now)) // 0.5 + 1 → 2
        // 7 日おきに使う語でも 1 に戻らない（round だと 0.2 + 1 → 1 に戻っていた。ceil では 2 に積み上がって保たれる）。
        assertEquals(2, LearningScorePolicy.reinforcedCount(1, now - 7 * day, now))
        var count = 1
        repeat(4) { count = LearningScorePolicy.reinforcedCount(count, now - 7 * day, now) }
        assertEquals(2, count)
        // 1 日おきなら回数は増え続ける（1→2→3→4→5）。
        count = 1
        repeat(4) { count = LearningScorePolicy.reinforcedCount(count, now - day, now) }
        assertEquals(5, count)
        assertEquals(LearningScorePolicy.MAX_COUNT, LearningScorePolicy.reinforcedCount(100, now, now))
        // 上限まで使った語でも expiryCutoff より前なら消えている。
        val cutoff = LearningScorePolicy.expiryCutoff(now)
        assertTrue(LearningScorePolicy.isExpired(LearningScorePolicy.MAX_COUNT, cutoff - day, now))
        assertTrue(!LearningScorePolicy.isExpired(LearningScorePolicy.MAX_COUNT, cutoff + day, now))
    }

    @Test
    fun ignoredAtTheTopFallsImmediately() {
        val strong = cost(1, now)!!
        val after = LearningScorePolicy.ignored(1, now, now, LearningScorePolicy.IGNORED_FACTOR_CONVERSION)
        assertNotNull(after)
        val (count, lastUsedAt) = after!!
        val weakened = LearningScorePolicy.conversionCost(9000, count, lastUsedAt, 4, now)!!
        assertTrue("強さ 0.1 → +5400", weakened >= strong + 5000)
        // 予測から外れる。
        assertTrue(LearningScorePolicy.strength(count, lastUsedAt, now) < LearningScorePolicy.PREDICTION_MIN_STRENGTH)
        // もう一度無視されたら消える。
        assertNull(LearningScorePolicy.ignored(count, lastUsedAt, now, LearningScorePolicy.IGNORED_FACTOR_CONVERSION))
        // よく使う語は 1 回の無視では消えないが大きく落ちる。
        val often = LearningScorePolicy.ignored(8, now, now, LearningScorePolicy.IGNORED_FACTOR_CONVERSION)!!
        assertEquals(0.8, LearningScorePolicy.strength(often.first, often.second, now), 1e-6)
    }

    @Test
    fun untimedRowsKeepTheirStoredScore() {
        // lastUsedAt 0（旧版・手で登録・取り込み）は時間で弱めず保存値のまま。
        assertEquals(3000, LearningScorePolicy.conversionCost(3000, 1, 0L, 4, now))
        assertTrue(!LearningScorePolicy.isExpired(1, 0L, now))
        // 上位に出して無視されたら時間つきの弱い行になる。
        val after = LearningScorePolicy.ignored(1, 0L, now, LearningScorePolicy.IGNORED_FACTOR_PREDICTION)!!
        assertEquals(0.25, LearningScorePolicy.strength(after.first, after.second, now), 1e-6)
    }

    @Test
    fun predictionPutsFreshLearnedWordsFirstAndDropsWeakOnes() {
        val fresh = LearnEntity(input = "ありがとう", out = "有難う", usageCount = 1, lastUsedAt = now)
        val older = LearnEntity(input = "ありがた", out = "有難", usageCount = 1, lastUsedAt = now - halfLife)
        val ignored = LearnEntity(input = "ありか", out = "在処", usageCount = 1, lastUsedAt = now - 3 * halfLife)
        val legacy = LearnEntity(input = "あり", out = "蟻", usageCount = 5, lastUsedAt = 0L)
        val ranked = LearningScorePolicy.rankForPrediction(listOf(legacy, ignored, older, fresh), "あり", now, 8)
        assertEquals(listOf("有難う", "有難", "蟻"), ranked.map { it.out })
        assertEquals(listOf("有難う"), LearningScorePolicy.rankForPrediction(listOf(older, fresh), "あり", now, 1).map { it.out })
    }
}

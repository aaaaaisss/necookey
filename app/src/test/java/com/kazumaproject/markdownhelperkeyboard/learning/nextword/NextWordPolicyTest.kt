package com.kazumaproject.markdownhelperkeyboard.learning.nextword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextWordPolicyTest {
    @Test
    fun pairsChainBunsetsuWithTheirPrecedingOutput() {
        val pairs = NextWordPolicy.pairs(
            precedingBunsetsu = "今日は",
            bunsetsu = listOf("いい" to "良い", "てんき" to "天気"),
            timestamp = 1L,
        )
        assertEquals(
            listOf(Triple("今日は", "いい", "良い"), Triple("良い", "てんき", "天気")),
            pairs.map { Triple(it.context, it.reading, it.output) },
        )
    }

    @Test
    fun firstBunsetsuWithoutKnownPrecedingTextIsNotLearned() {
        val pairs = NextWordPolicy.pairs(null, listOf("きょう" to "今日", "は" to "は"), 1L)
        assertEquals(listOf(Triple("今日", "は", "は")), pairs.map { Triple(it.context, it.reading, it.output) })
    }

    @Test
    fun readingsOverThePredictionCapAreNotLearned() {
        val long = "あ".repeat(NextWordPolicy.MAX_READING_LENGTH + 1)
        assertTrue(NextWordPolicy.pairs("前", listOf(long to "長"), 1L).isEmpty())
    }

    @Test
    fun contextKeyKeepsOnlyTheTail() {
        assertEquals(NextWordPolicy.MAX_CONTEXT_LENGTH, NextWordPolicy.contextKey("一二三四五六七八九十")!!.length)
        assertNull(NextWordPolicy.contextKey("  "))
    }

    @Test
    fun lookupContextsAreSuffixesLongestFirst() {
        assertEquals(listOf("は今日は", "今日は", "日は", "は"), NextWordPolicy.lookupContexts("は今日は"))
        assertTrue(NextWordPolicy.lookupContexts("文章の最後の部分です今日は").first().length == NextWordPolicy.MAX_CONTEXT_LENGTH)
    }

    @Test
    fun frequentShortContextBeatsOnceLongContext() {
        val now = 100L * 86_400_000L
        val once = NextWordEntity(context = "今日はとても", reading = "あつい", output = "暑い", usageCount = 1, lastUsedAt = now)
        val often = NextWordEntity(context = "も", reading = "いい", output = "良い", usageCount = 40, lastUsedAt = now)
        assertEquals(listOf("良い", "暑い"), NextWordPolicy.rank(listOf(once, often), now, 5).map { it.output })
    }

    private val day = 86_400_000L

    @Test
    fun oldUsesDecayFastAndSameOutputIsShownOnce() {
        val now = 400L * day
        val older = NextWordEntity(context = "は", reading = "あ", output = "A", usageCount = 8, lastUsedAt = now - 9 * day)
        val fresh = NextWordEntity(context = "は", reading = "い", output = "B", usageCount = 2, lastUsedAt = now)
        val dup = NextWordEntity(context = "日は", reading = "い", output = "B", usageCount = 1, lastUsedAt = now)
        val gone = NextWordEntity(context = "は", reading = "う", output = "C", usageCount = 8, lastUsedAt = now - 60 * day)
        // 8 回でも 9 日（3 半減期）で 1 回分。30 日以上前のものは消えている。
        assertEquals(listOf("B", "A"), NextWordPolicy.rank(listOf(older, fresh, dup, gone), now, 5).map { it.output })
        assertTrue(NextWordPolicy.isExpired(gone, now))
    }

    private fun mergeOf(predictions: List<String>, nextWords: List<NextWordEntity>, now: Long) =
        NextWordPolicy.mergeIntoPredictions(
            predictions = predictions,
            textOf = { it },
            nextWords = nextWords,
            toCandidate = { "+" + it.output },
            now = now,
        )

    @Test
    fun nextWordAlreadyInPredictionsMovesUpInsteadOfBeingDuplicated() {
        val now = 10L * day
        val next = NextWordEntity(context = "今日は", reading = "いい", output = "良い", lastUsedAt = now)
        val merged = mergeOf(listOf("いい", "飯", "良い", "言い"), listOf(next), now)
        assertEquals(listOf("良い", "いい", "飯", "言い"), merged.candidates)
        assertEquals(setOf("良い"), merged.backedBy.keys)
    }

    @Test
    fun missingNextWordCompetesInsteadOfAlwaysLeading() {
        val now = 10L * day
        // 短い文脈・1 回だけ: 先頭は元の予測のまま、その次あたりに入る。
        val weak = NextWordEntity(context = "は", reading = "いい", output = "良い", lastUsedAt = now)
        assertEquals(listOf("いい", "飯", "+良い", "言い"), mergeOf(listOf("いい", "飯", "言い"), listOf(weak), now).candidates)
        // 長い文脈で何度も使った: 先頭に来る。
        val strong = weak.copy(context = "明日の朝は", usageCount = 4)
        assertEquals("+良い", mergeOf(listOf("いい", "飯", "言い"), listOf(strong), now).candidates.first())
        // 消えた後続語は混ぜない。
        val gone = weak.copy(lastUsedAt = now - 30 * day)
        assertEquals(listOf("いい", "飯"), mergeOf(listOf("いい", "飯"), listOf(gone), now).candidates)
    }

    @Test
    fun ignoredNextWordFallsAndRecordingDecaysTheCount() {
        val now = 10L * day
        val once = NextWordEntity(context = "は", reading = "いい", output = "良い", usageCount = 1, lastUsedAt = now)
        val weakened = NextWordPolicy.ignored(once, now)!!
        assertEquals(0.25, NextWordPolicy.strength(weakened, now), 1e-6)
        assertTrue(NextWordPolicy.rankScore(weakened, now) < NextWordPolicy.rankScore(once, now))
        // 3 回無視されたら消える（0.25 → 0.0625 → null）。
        assertNull(NextWordPolicy.ignored(NextWordPolicy.ignored(weakened, now)!!, now))
        assertEquals(2, NextWordPolicy.reinforcedCount(once, now))
        assertEquals(NextWordPolicy.MAX_COUNT, NextWordPolicy.reinforcedCount(once.copy(usageCount = 50), now))
    }
}

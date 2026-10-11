package com.kazumaproject.markdownhelperkeyboard.learning.database

object LearningScorePolicy {
    private const val REINFORCEMENT = 1500

    fun initial(candidateScore: Int, candidateIndex: Int): Int =
        (candidateScore - 500L * candidateIndex.coerceAtLeast(0))
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()

    fun phrase(fragmentScores: List<Int>): Int =
        fragmentScores.takeIf { it.isNotEmpty() }
            ?.map(Int::toLong)
            ?.average()
            ?.toLong()
            ?.coerceIn(0L, Int.MAX_VALUE.toLong())
            ?.toInt()
            ?: 3000

    fun reinforce(existingScore: Int, incomingScore: Int): Int =
        (minOf(existingScore, incomingScore).toLong() - REINFORCEMENT)
            .coerceAtLeast(0L)
            .toInt()

    /** 学習語の単語コストの下限（読み 3 文字以上・2 回目）。使うほど [FLOOR_STEP] ずつ下がり、[FLOOR_MIN] まで。 */
    private const val FLOOR_BASE = 2000
    private const val FLOOR_STEP = 250
    private const val FLOOR_MIN_STEPS = 4

    /** 短い読みは他の語に当たりやすいので下限を高くする（azooKey と同じく短い語ほど弱い）。 */
    private const val SHORT_READING_EXTRA_1 = 1000
    private const val SHORT_READING_EXTRA_2 = 500

    /** この日数までは古さで弱めない。 */
    private const val AGE_GRACE_DAYS = 14.0

    /** 猶予を過ぎてから、この日数ごとに [AGE_PENALTY_STEP] ずつ弱める（上限 [AGE_PENALTY_MAX]）。 */
    private const val AGE_PENALTY_DAYS = 60.0
    private const val AGE_PENALTY_STEP = 1000.0
    private const val AGE_PENALTY_MAX = 2000

    private const val DAY_MS = 86_400_000.0

    /**
     * 変換で使う学習語の単語コスト（保存値はそのまま、読み出し時に計算。DB の移行なし）。
     *
     * - 1 回だけ使った語は保存値のまま（選んだ直後は次も同じ読みで先頭に来る、今までどおり）。
     * - 2 回目以降は 1500 ずつ下がった保存値を下限 [floor] で止める。ただし初回の値（保存値＋1500×(回数−1) で推定）
     *   より弱くはしない。→ 何度使っても 0 に張り付かず、ずっと良い辞書の経路には負けられる。
     * - 最後に使ってから [AGE_GRACE_DAYS] 日を過ぎたら少しずつ弱める（最大 +[AGE_PENALTY_MAX]）。
     *   同じ語が辞書にもあれば、グラフでは安いほうが残るので辞書の強さより弱くはならない。
     *   lastUsedAt が無い（0）古い行は弱めない。
     */
    fun conversionCost(
        storedScore: Int,
        usageCount: Int,
        lastUsedAt: Long,
        readingLength: Int,
        now: Long,
    ): Int {
        val stored = storedScore.coerceAtLeast(0)
        val bounded = if (usageCount <= 1) {
            stored
        } else {
            val estimatedFirst = stored.toLong() + REINFORCEMENT.toLong() * (usageCount - 1)
            maxOf(stored.toLong(), minOf(floor(usageCount, readingLength).toLong(), estimatedFirst)).toInt()
        }
        return (bounded.toLong() + agePenalty(lastUsedAt, now))
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    internal fun floor(usageCount: Int, readingLength: Int): Int {
        val steps = (usageCount - 2).coerceIn(0, FLOOR_MIN_STEPS)
        val extra = when {
            readingLength <= 1 -> SHORT_READING_EXTRA_1
            readingLength == 2 -> SHORT_READING_EXTRA_2
            else -> 0
        }
        return FLOOR_BASE + extra - FLOOR_STEP * steps
    }

    internal fun agePenalty(lastUsedAt: Long, now: Long): Int {
        if (lastUsedAt <= 0L || now <= lastUsedAt) return 0
        val days = (now - lastUsedAt) / DAY_MS - AGE_GRACE_DAYS
        if (days <= 0.0) return 0
        return minOf(AGE_PENALTY_MAX.toDouble(), days / AGE_PENALTY_DAYS * AGE_PENALTY_STEP).toInt()
    }
}

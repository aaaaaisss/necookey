package com.kazumaproject.markdownhelperkeyboard.learning.database

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 学習辞書の点数規則（純粋関数。docs/LEARNING.md）。
 *
 * 「極端に覚えて極端に忘れる」:
 * - 確定した語は 1 回で強い単語コスト（[boostCost]）になり、変換でも予測でも上位に出る。
 * - 強さ = 回数 × 0.5^(経過日数 / [HALF_LIFE_DAYS])。回数は書くたびに減衰させてから +1（上限 [MAX_COUNT]）。
 * - 強さが [EXPIRE_STRENGTH] 未満は消えたものとして扱う（変換・予測に出さず、[expiryCutoff] より古い行は消す）。
 * - 上位に出したのに別の候補が確定されたら強さを [IGNORED_FACTOR_CONVERSION] / [IGNORED_FACTOR_PREDICTION] 倍にする
 *   （[ignored]。lastUsedAt を過去へずらして表す。DB の列は増やさない）。
 * - lastUsedAt が 0 の行（旧版の学習・手で登録した語・取り込み）は時間で弱めず、保存値をそのまま単語コストにする。
 */
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

    /** 保存値（score 列）の更新。時間つきの行の変換では使わない（旧版の行・設定画面の表示用）。 */
    fun reinforce(existingScore: Int, incomingScore: Int): Int =
        (minOf(existingScore, incomingScore).toLong() - REINFORCEMENT)
            .coerceAtLeast(0L)
            .toInt()

    /** 強さが半分になる日数。 */
    const val HALF_LIFE_DAYS = 3.0

    /** 回数の上限（よく使う語でも約 log2(8/0.05) ≒ 7 半減期で消える）。 */
    const val MAX_COUNT = 8

    /** これ未満の強さは消えたものとして扱う。 */
    const val EXPIRE_STRENGTH = 0.05

    /** 予測候補の先頭に積むのはこの強さ以上（無視されて弱った語は積まない）。 */
    const val PREDICTION_MIN_STRENGTH = 0.3

    /** 変換の先頭に出したのに別の候補が確定されたときの倍率。 */
    const val IGNORED_FACTOR_CONVERSION = 0.1

    /** 予測候補の上位に出したのに使われなかったときの倍率。 */
    const val IGNORED_FACTOR_PREDICTION = 0.25

    /** 強さ 1 以上の語の単語コスト（読みの長さ別。短い読みは他の語に当たりやすいので弱め）。 */
    private const val BOOST_COST = 500
    private const val BOOST_COST_READING_2 = 1500
    private const val BOOST_COST_READING_1 = 2500

    /** 強さ 0 に近づいたときに足すコスト（強さ 0.5 で半分）。 */
    private const val FORGET_RANGE = 6000

    private const val DAY_MS = 86_400_000.0

    fun isTimed(lastUsedAt: Long): Boolean = lastUsedAt > 0L

    /** 回数 × 0.5^(経過日数 / 半減期)。lastUsedAt が 0 の行は時間で弱めない（回数そのまま）。 */
    fun strength(usageCount: Int, lastUsedAt: Long, now: Long): Double {
        val count = usageCount.coerceAtLeast(1).toDouble()
        if (!isTimed(lastUsedAt)) return count
        val ageDays = (now - lastUsedAt).coerceAtLeast(0L) / DAY_MS
        return count * 0.5.pow(ageDays / HALF_LIFE_DAYS)
    }

    fun isExpired(usageCount: Int, lastUsedAt: Long, now: Long): Boolean =
        isTimed(lastUsedAt) && strength(usageCount, lastUsedAt, now) < EXPIRE_STRENGTH

    internal fun boostCost(readingLength: Int): Int = when {
        readingLength <= 1 -> BOOST_COST_READING_1
        readingLength == 2 -> BOOST_COST_READING_2
        else -> BOOST_COST
    }

    /**
     * 変換で使う学習語の単語コスト。null は「消えた語」（グラフに入れない）。
     * 時間つきの行: [boostCost] + [FORGET_RANGE] × (1 − min(1, 強さ))。保存値は見ない。
     * lastUsedAt が 0 の行: 保存値のまま。
     */
    fun conversionCost(
        storedScore: Int,
        usageCount: Int,
        lastUsedAt: Long,
        readingLength: Int,
        now: Long,
    ): Int? {
        if (!isTimed(lastUsedAt)) return storedScore.coerceAtLeast(0)
        val s = strength(usageCount, lastUsedAt, now)
        if (s < EXPIRE_STRENGTH) return null
        return boostCost(readingLength) + (FORGET_RANGE * (1.0 - minOf(1.0, s))).roundToInt()
    }

    /** もう一度使われたときの回数: 今の強さ（減衰後）+ 1、1..[MAX_COUNT]。 */
    fun reinforcedCount(existingCount: Int, existingLastUsedAt: Long, now: Long): Int {
        val decayed = if (isTimed(existingLastUsedAt)) {
            strength(existingCount, existingLastUsedAt, now)
        } else {
            existingCount.coerceAtLeast(1).toDouble()
        }
        return (decayed + 1.0).roundToInt().coerceIn(1, MAX_COUNT)
    }

    /**
     * 上位に出したのに使われなかった語の新しい (usageCount, lastUsedAt)。null は削除。
     * 強さを [factor] 倍にし、それを lastUsedAt を過去へずらして表す。
     */
    fun ignored(usageCount: Int, lastUsedAt: Long, now: Long, factor: Double): Pair<Int, Long>? {
        val current = if (isTimed(lastUsedAt)) strength(usageCount, lastUsedAt, now) else 1.0
        val next = current * factor
        if (next < EXPIRE_STRENGTH) return null
        val count = usageCount.coerceIn(1, MAX_COUNT)
        val ageDays = HALF_LIFE_DAYS * ln(count / next) / ln(2.0)
        val shifted = now - (ageDays.coerceAtLeast(0.0) * DAY_MS).toLong()
        return count to shifted.coerceAtLeast(1L)
    }

    /** これより前に使われた時間つきの行は、回数が上限でも消えている。 */
    fun expiryCutoff(now: Long): Long {
        val maxLifeDays = HALF_LIFE_DAYS * ln(MAX_COUNT / EXPIRE_STRENGTH) / ln(2.0)
        return now - (maxLifeDays * DAY_MS).toLong()
    }

    /**
     * 読み前方一致で引いた学習語のうち、予測候補の先頭に積むもの（強い順、同点は読み完全一致・新しい順）。
     * 時間つきで [PREDICTION_MIN_STRENGTH] 未満の語は積まない。lastUsedAt が 0 の行は最弱として残す。
     */
    fun rankForPrediction(
        entries: List<LearnEntity>,
        input: String,
        now: Long,
        limit: Int,
    ): List<LearnEntity> =
        entries
            .map { entry ->
                val s = if (isTimed(entry.lastUsedAt)) {
                    strength(entry.usageCount, entry.lastUsedAt, now)
                } else {
                    PREDICTION_MIN_STRENGTH
                }
                entry to s
            }
            .filter { it.second >= PREDICTION_MIN_STRENGTH }
            .sortedWith(
                compareByDescending<Pair<LearnEntity, Double>> { it.second }
                    .thenBy { if (it.first.input == input) 0 else 1 }
                    .thenByDescending { it.first.lastUsedAt }
                    .thenBy { it.first.input.length },
            )
            .map { it.first }
            .distinctBy { it.out }
            .take(limit.coerceAtLeast(0))
}

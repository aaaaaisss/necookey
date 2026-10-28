package com.kazumaproject.markdownhelperkeyboard.learning.nextword

import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Pure rules for learning and looking up following words (後続語) with their left context. */
object NextWordPolicy {
    /** Longest left context kept / matched (characters of the preceding committed text). */
    const val MAX_CONTEXT_LENGTH = 8

    /** Following words longer than this reading are not learned or shown (same cap as prediction). */
    val MAX_READING_LENGTH: Int get() = PredictionConfig.MAX_PREDICTION_INPUT_LENGTH

    /**
     * Context key stored for the bunsetsu committed just before: its output, at most
     * [MAX_CONTEXT_LENGTH] characters. Lookups match it against suffixes of the editor's left text.
     */
    fun contextKey(precedingBunsetsuOutput: String?): String? {
        val trimmed = precedingBunsetsuOutput?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        return trimmed.takeLast(MAX_CONTEXT_LENGTH)
    }

    /** Every suffix of the left context that a stored context key may equal, longest first. */
    fun lookupContexts(leftContext: String): List<String> {
        val tail = leftContext.takeLast(MAX_CONTEXT_LENGTH)
        if (tail.isBlank()) return emptyList()
        return (tail.length downTo 1).map { tail.takeLast(it) }.filter { it.isNotBlank() }.distinct()
    }

    /** 回数の重みが半分になる日数（学習辞書と同じく速く忘れる）。 */
    const val HALF_LIFE_DAYS = 3.0

    /** 減衰後の回数がこれ未満の後続語は消えたものとして扱う。 */
    const val EXPIRE_STRENGTH = 0.05

    /** 回数の上限（書くたびに減衰後の回数 + 1）。 */
    const val MAX_COUNT = 8

    /** 予測の上位に出したのに使われなかったときの倍率。 */
    const val IGNORED_FACTOR = 0.25

    /** 文脈 1 文字あたりの加点（ln(1+回数) と同じ尺度）。 */
    const val CONTEXT_CHAR_WEIGHT = 0.25

    /** 文脈ごとに読み出す件数の倍率（並べ替えの候補を多めに取る）。 */
    const val POOL_FACTOR = 3

    /** 保存する後続語の上限件数（超えたら古い順に消す）。 */
    const val MAX_ENTRIES = 20_000

    /** 予測に既にある表記への加点（並び位置で何個ぶん上げるか）の固定分。 */
    const val PRESENT_BONUS_SLOTS = 2.0

    /** 予測に無い後続語を混ぜるときの基準位置（ここから点数ぶん上げる）。 */
    const val ABSENT_BASE_SLOT = 3.0

    /** [rankScore] 1 あたり何個ぶん上げるか。 */
    const val SLOTS_PER_SCORE = 2.0

    private const val DAY_MS = 86_400_000.0

    /** 回数 × 0.5^(経過日数 / [HALF_LIFE_DAYS])。 */
    fun strength(entry: NextWordEntity, now: Long): Double {
        val ageDays = ((now - entry.lastUsedAt).coerceAtLeast(0L)) / DAY_MS
        return entry.usageCount.coerceAtLeast(1) * 0.5.pow(ageDays / HALF_LIFE_DAYS)
    }

    fun isExpired(entry: NextWordEntity, now: Long): Boolean = strength(entry, now) < EXPIRE_STRENGTH

    /**
     * 並べ替えの点数（大きいほど上）。ln(1+減衰後の回数) に文脈の長さ × [CONTEXT_CHAR_WEIGHT] を足す。
     * 長い文脈で 1 回だけの語が、短い文脈でよく使う語に負けられる。
     */
    fun rankScore(entry: NextWordEntity, now: Long): Double =
        ln(1.0 + strength(entry, now)) + CONTEXT_CHAR_WEIGHT * entry.context.length

    /** 消えていないものを [rankScore] の高い順（同点は新しい順）、同じ表記は最上位の 1 件だけ。 */
    fun rank(entries: List<NextWordEntity>, now: Long, limit: Int): List<NextWordEntity> =
        entries
            .filterNot { isExpired(it, now) }
            .map { it to rankScore(it, now) }
            .sortedWith(
                compareByDescending<Pair<NextWordEntity, Double>> { it.second }
                    .thenByDescending { it.first.lastUsedAt },
            )
            .map { it.first }
            .distinctBy { it.output }
            .take(limit)

    /** もう一度使われたときの回数: 減衰後の回数 + 1、1..[MAX_COUNT]。 */
    fun reinforcedCount(existing: NextWordEntity, now: Long): Int =
        (strength(existing, now) + 1.0).roundToInt().coerceIn(1, MAX_COUNT)

    /**
     * 予測の上位に出したのに使われなかった後続語。減衰後の回数を [IGNORED_FACTOR] 倍にして lastUsedAt を過去へずらす。
     * null は削除。
     */
    fun ignored(entry: NextWordEntity, now: Long): NextWordEntity? {
        val next = strength(entry, now) * IGNORED_FACTOR
        if (next < EXPIRE_STRENGTH) return null
        val count = entry.usageCount.coerceIn(1, MAX_COUNT)
        val ageDays = HALF_LIFE_DAYS * ln(count / next) / ln(2.0)
        return entry.copy(
            usageCount = count,
            lastUsedAt = (now - (ageDays.coerceAtLeast(0.0) * DAY_MS).toLong()).coerceAtLeast(1L),
        )
    }

    /** これより前に使われた後続語は、回数が上限でも消えている。 */
    fun expiryCutoff(now: Long): Long {
        val maxLifeDays = HALF_LIFE_DAYS * ln(MAX_COUNT / EXPIRE_STRENGTH) / ln(2.0)
        return now - (maxLifeDays * DAY_MS).toLong()
    }

    /** [mergeIntoPredictions] の結果: 並べ替えた予測と、そのうち後続語で加点・追加した表記 → 後続語。 */
    data class Merged<T>(val candidates: List<T>, val backedBy: Map<String, NextWordEntity>)

    /**
     * 後続語を予測候補の並べ替えに混ぜる（無条件に先頭へ積まない）。
     * - 予測に同じ表記があれば、その位置 i を i − ([PRESENT_BONUS_SLOTS] + [SLOTS_PER_SCORE] × 点数) にする。
     * - 無ければ [ABSENT_BASE_SLOT] − [SLOTS_PER_SCORE] × 点数 の位置に [toCandidate] で作って入れる。
     * - 同じ位置なら元の予測が先（安定ソート）。消えた後続語は使わない。
     */
    fun <T> mergeIntoPredictions(
        predictions: List<T>,
        textOf: (T) -> String,
        nextWords: List<NextWordEntity>,
        toCandidate: (NextWordEntity) -> T,
        now: Long,
    ): Merged<T> {
        val ranked = rank(nextWords, now, Int.MAX_VALUE)
        if (ranked.isEmpty()) return Merged(predictions, emptyMap())
        val byText = LinkedHashMap<String, NextWordEntity>()
        ranked.forEach { byText.putIfAbsent(it.output, it) }
        val keyed = ArrayList<Pair<T, Double>>(predictions.size + byText.size)
        val present = HashSet<String>()
        predictions.forEachIndexed { index, candidate ->
            val text = textOf(candidate)
            val entry = byText[text]
            val key = if (entry != null && present.add(text)) {
                index - (PRESENT_BONUS_SLOTS + SLOTS_PER_SCORE * rankScore(entry, now))
            } else {
                index.toDouble()
            }
            keyed += candidate to key
        }
        byText.values.filter { it.output !in present }.forEach { entry ->
            keyed += toCandidate(entry) to (ABSENT_BASE_SLOT - SLOTS_PER_SCORE * rankScore(entry, now))
        }
        return Merged(keyed.sortedBy { it.second }.map { it.first }, byText)
    }

    /**
     * Following-word pairs of one completed commit.
     *
     * @param precedingBunsetsu output of the bunsetsu committed right before the first one, when
     *   it is known to still precede the cursor (null otherwise).
     * @param bunsetsu committed bunsetsu in order (reading to output).
     */
    fun pairs(
        precedingBunsetsu: String?,
        bunsetsu: List<Pair<String, String>>,
        timestamp: Long,
    ): List<NextWordEntity> {
        val result = ArrayList<NextWordEntity>()
        var preceding = precedingBunsetsu
        for ((reading, output) in bunsetsu) {
            val context = contextKey(preceding)
            if (context != null && reading.isNotEmpty() && output.isNotBlank() &&
                reading.length <= MAX_READING_LENGTH
            ) {
                result += NextWordEntity(
                    context = context,
                    reading = reading,
                    output = output,
                    lastUsedAt = timestamp,
                )
            }
            preceding = output
        }
        return result.distinctBy { Triple(it.context, it.reading, it.output) }
    }
}

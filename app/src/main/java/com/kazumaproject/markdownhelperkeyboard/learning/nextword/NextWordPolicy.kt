package com.kazumaproject.markdownhelperkeyboard.learning.nextword

import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import kotlin.math.ln
import kotlin.math.pow

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

    /** 回数の重みが半分になる日数（使っていない後続語ほど弱くなる）。 */
    const val HALF_LIFE_DAYS = 30.0

    /** 文脈 1 文字あたりの加点（ln(1+回数) と同じ尺度。文脈 8 文字で回数約 7 回分）。 */
    const val CONTEXT_CHAR_WEIGHT = 0.25

    /** 文脈ごとに読み出す件数の倍率（並べ替えの候補を多めに取る）。 */
    const val POOL_FACTOR = 3

    /** 保存する後続語の上限件数（超えたら古い順に消す）。 */
    const val MAX_ENTRIES = 20_000

    /**
     * 並べ替えの点数（大きいほど上）。回数は [HALF_LIFE_DAYS] で半減させて ln(1+回数)、
     * そこに文脈の長さ × [CONTEXT_CHAR_WEIGHT] を足す。長い文脈で 1 回だけの語が、短い文脈でよく使う語に負けられる。
     */
    fun rankScore(entry: NextWordEntity, now: Long): Double {
        val ageDays = ((now - entry.lastUsedAt).coerceAtLeast(0L)) / 86_400_000.0
        val decayed = entry.usageCount.coerceAtLeast(1) * 0.5.pow(ageDays / HALF_LIFE_DAYS)
        return ln(1.0 + decayed) + CONTEXT_CHAR_WEIGHT * entry.context.length
    }

    /** [rankScore] の高い順（同点は新しい順）、同じ表記は最上位の 1 件だけ。 */
    fun rank(entries: List<NextWordEntity>, now: Long, limit: Int): List<NextWordEntity> =
        entries
            .map { it to rankScore(it, now) }
            .sortedWith(
                compareByDescending<Pair<NextWordEntity, Double>> { it.second }
                    .thenByDescending { it.first.lastUsedAt },
            )
            .map { it.first }
            .distinctBy { it.output }
            .take(limit)

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

    /**
     * Learned phrase entries that start with the committed tail become following-word candidates
     * for the rest of their output (e.g. learned「今日は良い」after committing「今日は」->「良い」).
     */
    fun remainderAfter(committedTail: String, learnedOutput: String): String? {
        if (committedTail.isEmpty() || learnedOutput.length <= committedTail.length) return null
        if (!learnedOutput.startsWith(committedTail)) return null
        return learnedOutput.substring(committedTail.length).takeIf { it.isNotBlank() }
    }
}

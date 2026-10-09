package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Scores candidate outputs for one reading with zenz. Implementations return one average
 * log-probability per candidate (higher is better, non-finite = unusable), or null when zenz is
 * unavailable. The reading is passed in katakana, as zenz expects.
 */
fun interface ZenzSpanScorer {
    suspend fun score(
        leftContext: String,
        rightContext: String,
        readingKatakana: String,
        candidates: List<String>,
    ): FloatArray?
}

data class ZenzReselection(
    /** One output per bunsetsu slot, after zenz re-chose the ambiguous ones. */
    val outputs: List<String>,
    /** Indices of slots whose output differs from Sumire's primary. */
    val changedSlots: List<Int>,
    /** Indices of slots that were sent to zenz (ambiguous by the gate). */
    val scoredSlots: List<Int>,
)

/**
 * Confidence-gated bunsetsu re-selection.
 *
 * Walks the primary's bunsetsu left to right. A bunsetsu whose Sumire cost gap is below the
 * threshold is scored by zenz over its top-K Sumire alternatives. Each score call receives the
 * editor's left context plus preceding chosen outputs, and following primary outputs plus the
 * editor's right context. Confident bunsetsu keep Sumire's output untouched. Cancellation of the
 * calling coroutine propagates.
 */
class ZenzBunsetsuReselector(
    private val config: NecookeyCandidateBarConfig = NecookeyCandidateBarConfig.DEFAULT,
) {

    fun ambiguousSlotIndices(analysis: BunsetsuAnalysis): List<Int> =
        analysis.slots.indices
            .filter { analysis.slots[it].isAmbiguous(config.gateNormalizedGapThreshold) }
            .take(config.maxZenzBunsetsuPerRequest.coerceAtLeast(0))

    suspend fun reselect(
        analysis: BunsetsuAnalysis,
        editorLeftContext: String,
        editorRightContext: String,
        scorer: ZenzSpanScorer,
    ): ZenzReselection {
        val outputs = analysis.slots.map { it.primaryOutput }.toMutableList()
        val targets = if (analysis.input.length > config.maxZenzInputLength) {
            emptyList()
        } else {
            ambiguousSlotIndices(analysis)
        }
        val changed = mutableListOf<Int>()
        val scored = mutableListOf<Int>()

        for (index in targets) {
            currentCoroutineContext().ensureActive()
            val slot = analysis.slots[index]
            val options = slot.alternatives
                .asSequence()
                .map { it.output }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(config.zenzTopK.coerceAtLeast(2))
                .toList()
            if (options.size < 2 || options[0] != slot.primaryOutput) continue

            val left = buildString {
                append(editorLeftContext)
                for (i in 0 until index) append(outputs[i])
            }.takeLast(config.maxLeftContextChars.coerceAtLeast(0))
            val right = buildString {
                for (i in index + 1 until outputs.size) append(outputs[i])
                append(editorRightContext)
            }.take(config.maxRightContextChars.coerceAtLeast(0))
            val reading = analysis.input.substring(slot.span.start, slot.span.end)
                .hiraganaToKatakanaForZenz()

            val scores = scorer.score(left, right, reading, options)
            currentCoroutineContext().ensureActive()
            scored += index
            val pick = pickBest(options, scores, config.minZenzLogProbMargin) ?: continue
            if (pick != outputs[index]) {
                outputs[index] = pick
                changed += index
            }
        }
        return ZenzReselection(outputs = outputs, changedSlots = changed, scoredSlots = scored)
    }

    companion object {
        /**
         * Picks the zenz-preferred option. options[0] is Sumire's choice and is kept on ties,
         * when the winner does not beat it by more than [margin], or when nothing is usable
         * (null result, size mismatch, all scores non-finite -> null, i.e. no decision).
         */
        fun pickBest(options: List<String>, scores: FloatArray?, margin: Float): String? {
            if (scores == null || scores.size != options.size || options.isEmpty()) return null
            var bestIndex = -1
            var bestScore = Float.NEGATIVE_INFINITY
            for (i in options.indices) {
                val s = scores[i]
                if (!s.isFinite()) continue
                if (bestIndex < 0 || s > bestScore) {
                    bestIndex = i
                    bestScore = s
                }
            }
            if (bestIndex < 0) return null
            if (bestIndex == 0) return options[0]
            val baseline = scores[0]
            if (baseline.isFinite() && bestScore - baseline <= margin) return options[0]
            return options[bestIndex]
        }
    }
}

/** Hiragana (U+3041..U+3096, ゝゞ) to katakana; everything else unchanged. */
internal fun String.hiraganaToKatakanaForZenz(): String {
    val chars = CharArray(length)
    for (i in indices) {
        val c = this[i]
        chars[i] = if (c in '\u3041'..'\u3096' || c == '\u309D' || c == '\u309E') c + 0x60 else c
    }
    return String(chars)
}

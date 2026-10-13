package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * How sure the conversion is about one bunsetsu. Larger = more confident; null = no rival surface
 * for that reading span (nothing to choose, never marked).
 *
 * The default is Sumire's own N-best cost gap. A reranker (e.g. SumireLmGate on sumire-lm-app)
 * plugs in by implementing this and passing a threshold in the same units.
 */
fun interface BunsetsuMarginSource {
    fun margin(analysis: BunsetsuAnalysis, slotIndex: Int): Int?
}

/**
 * Cost gap between the best path and the best path with a different surface for the same reading
 * span (Sumire path costs: lower is better, so the gap is >= 0 unless learning reordered the list).
 */
object SumireCostGapMargin : BunsetsuMarginSource {
    override fun margin(analysis: BunsetsuAnalysis, slotIndex: Int): Int? {
        val alternatives = analysis.slots.getOrNull(slotIndex)?.alternatives ?: return null
        if (alternatives.size < 2) return null
        val best = alternatives[0].pathCost
        return alternatives.asSequence().drop(1).minOf { it.pathCost } - best
    }
}

/** One diff chip: [label] is only the differing part, [output] the whole bunsetsu it stands for. */
data class DiffChip(
    val slotIndex: Int,
    val label: String,
    val output: String,
    val isCurrent: Boolean,
)

/** What the homophone UI shows for one conversion. */
data class HomophonePlan(
    /** Output per bunsetsu slot of the shown top-row sentence. */
    val slotOutputs: List<String>,
    /** Marked (low-confidence) slots, most uncertain first. */
    val uncertainSlots: List<Int>,
    /**
     * Slot whose chips are shown: a marked slot, or, when nothing is marked, the slot with the
     * smallest margin (chips without a mark, so a confident result can still be corrected).
     * Null when no slot has a rival surface.
     */
    val focusSlot: Int?,
    /** Chips for [focusSlot]; the current output first. */
    val chips: List<DiffChip>,
    /** UTF-16 ranges (end exclusive) of marked bunsetsu inside the top-row sentence. */
    val markRanges: List<Pair<Int, Int>>,
) {
    val sentence: String get() = slotOutputs.joinToString(separator = "")

    /** Slot index containing UTF-16 offset [offset] of the sentence, or null. */
    fun slotAt(offset: Int): Int? {
        var start = 0
        slotOutputs.forEachIndexed { index, output ->
            val end = start + output.length
            if (offset in start until end) return index
            start = end
        }
        return null
    }

    /** The marked slot after [focusSlot] (wraps), or null when fewer than two are marked. */
    fun nextFocus(): Int? {
        if (uncertainSlots.size < 2 || focusSlot == null) return null
        val sortedByPosition = uncertainSlots.sorted()
        val i = sortedByPosition.indexOf(focusSlot)
        return sortedByPosition[(i + 1) % sortedByPosition.size]
    }
}

object HomophoneChips {

    /**
     * A bunsetsu is marked when its margin is below this. Sumire cost units; calibrated on the
     * tune half of daily.tsv (1500 colloquial sentences, outside the repo, N-best 20): marks 64% of
     * the wrong bunsetsu that one chip fixes, 13% of bunsetsu in correct sentences (1000: 74% / 17%).
     */
    const val SUMIRE_GAP_THRESHOLD = 800

    /** At most this many chips (including the current output) for one bunsetsu. */
    const val MAX_CHIPS = 10

    /**
     * Per-slot outputs of [shown] against [analysis]'s spans, or null when [shown] cannot be laid
     * onto those spans (another reading, or a path whose words straddle a bunsetsu boundary).
     */
    fun slotOutputsOf(analysis: BunsetsuAnalysis, shown: Candidate): List<String>? {
        if (shown.length.toInt() != analysis.input.length) return null
        if (shown.string == analysis.primary.string) return analysis.slots.map { it.primaryOutput }
        val segments = shown.conversionSegments
        if (segments.isEmpty()) return null
        val outputs = analysis.slots.map { slot ->
            BunsetsuAnalyzer.outputForRange(segments, slot.span.start, slot.span.end) ?: return null
        }
        return outputs.takeIf { it.joinToString(separator = "") == shown.string }
    }

    /** Slots whose margin is below [threshold], most uncertain (smallest margin) first. */
    fun uncertainSlots(
        analysis: BunsetsuAnalysis,
        threshold: Int = SUMIRE_GAP_THRESHOLD,
        marginSource: BunsetsuMarginSource = SumireCostGapMargin,
    ): List<Int> = analysis.slots.indices
        .mapNotNull { i -> marginSource.margin(analysis, i)?.let { i to it } }
        .filter { (_, margin) -> margin < threshold }
        .sortedWith(compareBy({ it.second }, { it.first }))
        .map { it.first }

    /**
     * Strips the prefix and suffix shared by every output so only the differing part remains
     * (紙を/神を/髪を -> 紙/神/髪). Falls back to the whole outputs when stripping would leave an
     * empty label or make two labels equal.
     */
    fun diffLabels(outputs: List<String>): List<String> {
        if (outputs.size < 2) return outputs
        val minLength = outputs.minOf { it.length }
        var prefix = 0
        while (prefix < minLength && outputs.all { it[prefix] == outputs[0][prefix] }) prefix++
        prefix = backOffSurrogate(outputs[0], prefix)
        var suffix = 0
        while (suffix < minLength - prefix &&
            outputs.all { it[it.length - 1 - suffix] == outputs[0][outputs[0].length - 1 - suffix] }
        ) suffix++
        if (suffix > 0 && Character.isLowSurrogate(outputs[0][outputs[0].length - suffix])) suffix--
        val labels = outputs.map { it.substring(prefix, it.length - suffix) }
        if (labels.any { it.isEmpty() } || labels.toSet().size != labels.size) return outputs
        return labels
    }

    private fun backOffSurrogate(s: String, index: Int): Int =
        if (index in 1 until s.length && Character.isLowSurrogate(s[index])) index - 1 else index

    /**
     * @param shown the sentence currently in the top row (Sumire's, zenz's or a chip-edited one).
     * @param requestedFocus slot the user moved focus to; ignored unless it is marked.
     * @return null when [shown] cannot be aligned to the analysis.
     */
    fun plan(
        analysis: BunsetsuAnalysis,
        shown: Candidate,
        requestedFocus: Int? = null,
        threshold: Int = SUMIRE_GAP_THRESHOLD,
        marginSource: BunsetsuMarginSource = SumireCostGapMargin,
    ): HomophonePlan? {
        val outputs = slotOutputsOf(analysis, shown) ?: return null
        val uncertain = uncertainSlots(analysis, threshold, marginSource)
        val focus = requestedFocus?.takeIf { it in uncertain }
            ?: uncertain.firstOrNull()
            ?: analysis.slots.indices
                .mapNotNull { i -> marginSource.margin(analysis, i)?.let { i to it } }
                .minWithOrNull(compareBy({ it.second }, { it.first }))
                ?.first
        val chips = focus?.let { chipsFor(analysis, it, outputs[it]) }.orEmpty()
        val ranges = ArrayList<Pair<Int, Int>>()
        var start = 0
        outputs.forEachIndexed { i, output ->
            if (i in uncertain) ranges += start to start + output.length
            start += output.length
        }
        return HomophonePlan(outputs, uncertain, focus, chips, ranges)
    }

    /** Chips for one slot: the current output first, then Sumire's alternatives in N-best order. */
    fun chipsFor(analysis: BunsetsuAnalysis, slotIndex: Int, currentOutput: String): List<DiffChip> {
        val slot = analysis.slots[slotIndex]
        val outputs = LinkedHashSet<String>()
        outputs += currentOutput
        slot.alternatives.forEach { if (it.output.isNotEmpty()) outputs += it.output }
        val list = outputs.take(MAX_CHIPS)
        if (list.size < 2) return emptyList()
        val labels = diffLabels(list)
        return list.mapIndexed { i, output ->
            DiffChip(slotIndex = slotIndex, label = labels[i], output = output, isCurrent = i == 0)
        }
    }

    /** [outputs] with slot [slotIndex] replaced; the rest of the sentence stays. */
    fun replaced(outputs: List<String>, slotIndex: Int, output: String): List<String> =
        outputs.mapIndexed { i, o -> if (i == slotIndex) output else o }
}

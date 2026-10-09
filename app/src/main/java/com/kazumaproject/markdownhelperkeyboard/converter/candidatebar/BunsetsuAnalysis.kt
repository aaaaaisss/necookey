package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment

/** One bunsetsu of a conversion path. Offsets are UTF-16 indices into the reading. */
data class BunsetsuSpan(
    val start: Int,
    val end: Int,
    val output: String,
) {
    val readingLength: Int get() = end - start
}

/** A Sumire alternative for one bunsetsu span, with the best full-path cost that produced it. */
data class BunsetsuAlternative(
    val output: String,
    val pathCost: Int,
)

/**
 * A bunsetsu of the primary conversion together with every distinct output Sumire's N-best paths
 * produced for exactly the same reading range. `alternatives[0]` is always the primary's own
 * output; the rest keep Sumire's N-best order.
 */
data class BunsetsuSlot(
    val span: BunsetsuSpan,
    val alternatives: List<BunsetsuAlternative>,
) {
    val primaryOutput: String get() = span.output

    /**
     * Cost gap between the primary output and its best rival, normalized by reading length.
     * `null` when Sumire offered no rival for this range (nothing for zenz to choose between).
     * A negative gap is possible when Sumire's final ranking is not strictly by path cost
     * (n-gram re-ranking); it counts as maximally ambiguous.
     */
    fun normalizedCostGap(): Double? {
        if (alternatives.size < 2) return null
        val primaryCost = alternatives[0].pathCost
        val rivalCost = alternatives.asSequence().drop(1).minOf { it.pathCost }
        return (rivalCost.toLong() - primaryCost.toLong()).toDouble() /
            span.readingLength.coerceAtLeast(1)
    }

    fun isAmbiguous(threshold: Double): Boolean {
        val gap = normalizedCostGap() ?: return false
        return gap < threshold
    }
}

object BunsetsuAnalyzer {

    /**
     * Groups word-level conversion segments into bunsetsu using Sumire's interior split
     * positions. Returns null when the segments do not form a contiguous cover of the reading.
     * Split positions that do not fall on a segment boundary are ignored (they cannot be
     * represented without cutting a word in half).
     */
    fun spansOf(
        inputLength: Int,
        segments: List<CandidateConversionSegment>,
        splitPositions: List<Int>,
    ): List<BunsetsuSpan>? {
        if (inputLength <= 0 || segments.isEmpty()) return null
        var expectedStart = 0
        for (segment in segments) {
            if (segment.inputStart != expectedStart || segment.inputEnd <= segment.inputStart) {
                return null
            }
            expectedStart = segment.inputEnd
        }
        if (expectedStart != inputLength) return null

        val wordBoundaries = segments.mapTo(HashSet()) { it.inputStart }
        val cuts = splitPositions
            .asSequence()
            .filter { it in 1 until inputLength && it in wordBoundaries }
            .toSortedSet()

        val spans = ArrayList<BunsetsuSpan>(cuts.size + 1)
        var spanStart = 0
        val builder = StringBuilder()
        for (segment in segments) {
            if (segment.inputStart != spanStart && segment.inputStart in cuts) {
                spans += BunsetsuSpan(spanStart, segment.inputStart, builder.toString())
                builder.setLength(0)
                spanStart = segment.inputStart
            }
            builder.append(segment.output)
        }
        spans += BunsetsuSpan(spanStart, inputLength, builder.toString())
        return spans
    }

    /**
     * Output a path produces for reading range [start, end), or null when the path has no word
     * boundary exactly at both ends (its words straddle the range).
     */
    fun outputForRange(
        segments: List<CandidateConversionSegment>,
        start: Int,
        end: Int,
    ): String? {
        var sawStart = start == 0
        val builder = StringBuilder()
        for (segment in segments) {
            if (segment.inputEnd <= start) {
                if (segment.inputEnd == start) sawStart = true
                continue
            }
            if (segment.inputStart >= end) break
            if (segment.inputStart < start || segment.inputEnd > end) return null
            if (segment.inputStart == start) sawStart = true
            builder.append(segment.output)
            if (segment.inputEnd == end) {
                return if (sawStart) builder.toString() else null
            }
        }
        return null
    }

    /**
     * Builds per-bunsetsu slots for the primary path.
     *
     * @param nBest Sumire's full-reading N-best candidates in Sumire order; the first full-length
     *   one becomes the primary (null is returned when its path is unknown).
     * @param segmentsByString word segments per candidate string (Sumire's segment collector).
     * @param splitPositions interior bunsetsu boundaries of the primary path.
     */
    fun analyze(
        input: String,
        nBest: List<Candidate>,
        segmentsByString: Map<String, List<CandidateConversionSegment>>,
        splitPositions: List<Int>,
    ): BunsetsuAnalysis? {
        val fullLength = nBest.filter { it.length.toInt() == input.length }
        val primary = fullLength.firstOrNull() ?: return null
        val primarySegments = segmentsByString[primary.string] ?: return null
        val spans = spansOf(input.length, primarySegments, splitPositions) ?: return null
        if (spans.joinToString(separator = "") { it.output } != primary.string) return null

        val rivals = fullLength.mapNotNull { candidate ->
            segmentsByString[candidate.string]?.let { candidate to it }
        }
        val slots = spans.map { span ->
            val alternatives = LinkedHashMap<String, Int>()
            alternatives[span.output] = primary.score
            for ((candidate, segments) in rivals) {
                val output = outputForRange(segments, span.start, span.end) ?: continue
                if (output == span.output) continue
                val previous = alternatives[output]
                // LinkedHashMap keeps the first-seen (Sumire) order; only the cost is lowered.
                if (previous == null || candidate.score < previous) {
                    alternatives[output] = candidate.score
                }
            }
            BunsetsuSlot(
                span = span,
                alternatives = alternatives.map { (output, cost) ->
                    BunsetsuAlternative(output, cost)
                },
            )
        }
        return BunsetsuAnalysis(input = input, primary = primary, slots = slots)
    }
}

data class BunsetsuAnalysis(
    val input: String,
    val primary: Candidate,
    val slots: List<BunsetsuSlot>,
) {
    val firstBunsetsuEnd: Int get() = slots.first().span.end
    val hasMultipleBunsetsu: Boolean get() = slots.size > 1

    /** Candidate built from one output per slot (used after zenz re-chose some bunsetsu). */
    fun primaryWithOutputs(outputs: List<String>): Candidate {
        require(outputs.size == slots.size) { "outputs/slots size mismatch" }
        if (outputs.withIndex().all { (i, o) -> o == slots[i].primaryOutput }) return primary
        val segments = slots.mapIndexed { i, slot ->
            CandidateConversionSegment(slot.span.start, slot.span.end, outputs[i])
        }
        val string = outputs.joinToString(separator = "")
        return primary.copy(
            string = string,
            commitText = string,
            conversionSegments = segments,
        )
    }
}

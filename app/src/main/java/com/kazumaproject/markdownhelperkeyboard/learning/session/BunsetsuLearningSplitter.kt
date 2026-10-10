package com.kazumaproject.markdownhelperkeyboard.learning.session

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BunsetsuAnalyzer

/** One bunsetsu of a committed conversion: its exact reading and the text that was committed. */
data class LearnedBunsetsu(
    val reading: String,
    val output: String,
)

/**
 * Splits a committed (reading, output) pair into bunsetsu so learning never stores a long whole
 * sentence as one word.
 */
object BunsetsuLearningSplitter {

    /**
     * @param reading the exact reading the output was converted from.
     * @param output the committed text.
     * @param segments the conversion path of [output] (word or bunsetsu nodes, UTF-16 offsets
     *   into [reading]). It must cover [reading] contiguously and spell [output] exactly.
     * @param bunsetsuBoundaries interior bunsetsu boundaries (offsets into [reading]). Boundaries
     *   that do not fall on a node boundary of [segments] are ignored.
     * @return the bunsetsu, or null when the path does not belong to this reading/output (the
     *   caller must then treat the commit as an unsplit whole).
     */
    fun split(
        reading: String,
        output: String,
        segments: List<CandidateConversionSegment>,
        bunsetsuBoundaries: List<Int>,
    ): List<LearnedBunsetsu>? {
        if (reading.isEmpty() || output.isEmpty()) return null
        val spans = BunsetsuAnalyzer.spansOf(reading.length, segments, bunsetsuBoundaries)
            ?: return null
        if (spans.joinToString(separator = "") { it.output } != output) return null
        return spans.map { span ->
            LearnedBunsetsu(reading = reading.substring(span.start, span.end), output = span.output)
        }
    }
}

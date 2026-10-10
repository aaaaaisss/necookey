package com.kazumaproject.markdownhelperkeyboard.learning.nextword

import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig

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

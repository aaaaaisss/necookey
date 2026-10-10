package com.kazumaproject.markdownhelperkeyboard.learning.session

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearningScorePolicy

data class LearningFragment(
    val reading: String,
    val output: String,
    val candidateScore: Int,
    val candidateIndex: Int,
    val leftId: Short? = null,
    val rightId: Short? = null,
    val explicitlySelected: Boolean = false,
    /**
     * True when this fragment is a whole multi-bunsetsu (or unknown-structure) commit that could
     * not be split into bunsetsu. Such fragments are only learned when they are short.
     */
    val unsplitWhole: Boolean = false,
)

/**
 * Collects every committed fragment until the original reading has been fully committed.
 *
 * Learning happens at bunsetsu level: every learnable fragment becomes its own entry. Phrase
 * entries (several fragments joined, including the whole commit) are only written when their
 * reading is at most [MAX_PHRASE_READING_LENGTH] characters, so long sentences never become a
 * single learned word. Phrase entries are also only written while the fragments are a
 * consistent, gap-free prefix of the original reading; otherwise a phrase could pair the full
 * reading with only part of the output.
 */
class ConversionLearningSession {
    private var originalReading: String? = null
    private val fragments = mutableListOf<LearningFragment>()
    private val consumedReading = StringBuilder()
    private var consistent = true

    val isActive: Boolean
        get() = originalReading != null

    fun beginIfNeeded(reading: String) {
        if (originalReading == null && reading.isNotEmpty()) {
            originalReading = reading
        }
    }

    fun record(fragment: LearningFragment) {
        val reading = originalReading ?: return
        if (fragment.reading.isEmpty() || fragment.output.isEmpty()) return
        consumedReading.append(fragment.reading)
        if (!reading.startsWith(consumedReading)) consistent = false
        fragments += fragment
    }

    /** Fragments recorded so far, in commit order (read before [finish], which clears them). */
    fun recordedFragments(): List<LearningFragment> = fragments.toList()

    fun finish(
        learnFirstCandidate: Boolean,
        timestamp: Long = System.currentTimeMillis(),
    ): List<LearnEntity> {
        val reading = originalReading
        val recorded = fragments.toList()
        cancel()
        if (reading.isNullOrEmpty() || recorded.isEmpty()) return emptyList()

        val isConsistent = consistent && recorded.joinToString(separator = "") { it.reading } == reading

        val learnable = recorded.filter {
            (learnFirstCandidate || it.explicitlySelected || it.candidateIndex != 0) &&
                (!it.unsplitWhole || it.reading.length <= MAX_PHRASE_READING_LENGTH)
        }
        if (learnable.isEmpty()) return emptyList()

        val segmentEntries = learnable.map { fragment ->
            fragment.toEntity(timestamp = timestamp, isPhrase = false)
        }
        if (!isConsistent) return segmentEntries.distinctBy { it.input to it.out }

        val cumulativePhraseEntries = recorded.indices.drop(1).mapNotNull { lastIndex ->
            val phraseFragments = recorded.take(lastIndex + 1)
            val phraseReading = phraseFragments.joinToString(separator = "") { it.reading }
            if (phraseReading.length > MAX_PHRASE_READING_LENGTH) return@mapNotNull null
            val containsLearnableSelection = phraseFragments.any {
                learnFirstCandidate || it.explicitlySelected || it.candidateIndex != 0
            }
            if (!containsLearnableSelection) return@mapNotNull null
            LearnEntity(
                input = phraseReading,
                out = phraseFragments.joinToString(separator = "") { it.output },
                score = phraseScore(phraseFragments),
                leftId = phraseFragments.first().leftId,
                rightId = phraseFragments.last().rightId,
                usageCount = 1,
                lastUsedAt = timestamp,
                isPhrase = true,
            )
        }
        val completeEntry = if (reading.length <= MAX_PHRASE_READING_LENGTH) {
            LearnEntity(
                input = reading,
                out = recorded.joinToString(separator = "") { it.output },
                score = phraseScore(recorded),
                leftId = recorded.firstOrNull()?.leftId,
                rightId = recorded.lastOrNull()?.rightId,
                usageCount = 1,
                lastUsedAt = timestamp,
                isPhrase = recorded.size > 1 || reading != recorded.first().reading,
            )
        } else {
            null
        }

        return (segmentEntries + cumulativePhraseEntries + listOfNotNull(completeEntry))
            .distinctBy { it.input to it.out }
    }

    fun cancel() {
        originalReading = null
        fragments.clear()
        consumedReading.setLength(0)
        consistent = true
    }

    private fun LearningFragment.toEntity(timestamp: Long, isPhrase: Boolean) = LearnEntity(
        input = reading,
        out = output,
        score = LearningScorePolicy.initial(candidateScore, candidateIndex),
        leftId = leftId,
        rightId = rightId,
        usageCount = 1,
        lastUsedAt = timestamp,
        isPhrase = isPhrase,
    )

    private fun phraseScore(fragments: List<LearningFragment>): Int =
        LearningScorePolicy.phrase(
            fragments.map {
                LearningScorePolicy.initial(it.candidateScore, it.candidateIndex)
            }
        )

    companion object {
        /** Longest reading learned as one multi-bunsetsu phrase / whole commit. */
        const val MAX_PHRASE_READING_LENGTH = 12
    }
}

package com.kazumaproject.markdownhelperkeyboard.learning.session

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/** Rejects learning pairs whose candidate provably came from a different reading. */
object LearningReadingGuard {
    /**
     * @param reading the reading the commit is about to be learned under.
     * @param candidateInput the input the displayed candidate list was built for, when known.
     */
    fun candidateMatchesReading(
        candidate: Candidate,
        reading: String,
        candidateInput: String? = null,
    ): Boolean {
        if (reading.isEmpty()) return false
        if (candidate.length.toInt() != reading.length) {
            // Prefix / prediction candidates are checked by their own length elsewhere.
            return candidateInput == null || candidateInput.startsWith(reading)
        }
        candidate.yomi?.let { yomi -> if (yomi.length == reading.length && yomi != reading) return false }
        if (candidateInput != null && candidateInput.length >= reading.length &&
            !candidateInput.startsWith(reading)
        ) return false
        val segments = candidate.conversionSegments
        if (segments.isNotEmpty() &&
            (segments.first().inputStart != 0 || segments.last().inputEnd != reading.length)
        ) return false
        return true
    }
}

package com.aaaaaisss.necokey

/**
 * Conservative gate for zenz reranking.
 *
 * Sumire's Candidate.score is a whole-path cost. Its absolute scale is not
 * treated as a probability and is not used with a magic numeric gap.
 *
 * zenz is invoked only when the normal Sumire result already shows a real
 * ambiguity for the same input span: at least two different outputs for that
 * span occur within the inspected top ranks.
 */
object ZenzConfidenceGate {
    fun shouldRerank(
        outputRanks: Collection<Int>,
        maxRank: Int = 3,
    ): Boolean {
        if (outputRanks.size < 2) return false
        val limit = maxRank.coerceAtLeast(2)
        return outputRanks.distinct().count { it < limit } >= 2
    }
}

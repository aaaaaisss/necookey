package com.aaaaaisss.necokey

/**
 * Heuristic gate for the first zenz integration.
 *
 * Sumire's Candidate.score is a path cost, not a probability. Therefore this
 * gate deliberately uses only rank/score-gap information and does not call it
 * a probability or Mozc structure_cost.
 */
object ZenzConfidenceGate {
    fun shouldRerank(
        candidateScores: List<Long>,
        maxRank: Int = 3,
        maxGap: Long = 120L
    ): Boolean {
        if (candidateScores.size < 2) return false
        val limit = maxRank.coerceAtLeast(1).coerceAtMost(candidateScores.size)
        val top = candidateScores.take(limit)
        val best = top.minOrNull() ?: return false
        val second = top.drop(1).minOrNull() ?: return false
        return second - best <= maxGap
    }
}

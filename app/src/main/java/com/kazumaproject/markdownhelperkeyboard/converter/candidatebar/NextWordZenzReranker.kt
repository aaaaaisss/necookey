package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * 確定後の後続語候補（先頭 [TOP_K] 件）を zenz の左文脈つき平均対数尤度で並べ替える。
 *
 * 読みのない（空入力の）プロンプトで採点するため zenz の学習分布から外れており、点数だけで
 * 候補を消すのは安全と言えない。よって削除はせず、元の順位を [POSITION_PENALTY] で残した
 * 並べ替えのみ行う。
 */
object NextWordZenzReranker {
    const val TOP_K = 10

    /** 元の順位 1 つ分に相当する平均対数尤度（nats/token）の差。 */
    const val POSITION_PENALTY = 0.15f

    fun rerank(candidates: List<Candidate>, scores: FloatArray): List<Candidate> {
        if (candidates.size != scores.size || candidates.size < 2) return candidates
        if (scores.none { it.isFinite() }) return candidates
        return candidates.indices
            .sortedWith(
                compareByDescending<Int> { fused(scores[it], it) }.thenBy { it },
            )
            .map { candidates[it] }
    }

    private fun fused(score: Float, index: Int): Float =
        (if (score.isFinite()) score else -1e6f) - POSITION_PENALTY * index
}

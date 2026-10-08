package com.aaaaaisss.necokey

import com.kazumaproject.zenz.ZenzEngine

/**
 * necookey-owned bridge to the standalone zenz scorer.
 *
 * zenz never creates candidates. Sumire creates them first, then this class
 * scores only the strings explicitly supplied by necookey.
 */
class ZenzCandidateScorer {
    @Volatile
    private var ready = false

    @Synchronized
    fun loadModel(path: String): Boolean {
        if (ready) return true
        return runCatching {
            ZenzEngine.initModel(path).also { ready = it }
        }.getOrDefault(false)
    }

    fun isReady(): Boolean = ready

    fun score(
        input: String,
        candidates: List<String>,
        leftContext: String = "",
        rightContext: String = ""
    ): FloatArray? {
        if (!ready || candidates.size < 2) return null
        return runCatching {
            ZenzEngine.scoreCandidatesV32(
                profile = null,
                topic = null,
                style = null,
                preference = null,
                leftContext = leftContext,
                rightContext = rightContext,
                input = input,
                candidates = candidates.toTypedArray()
            )
        }.getOrNull()?.takeIf { it.size == candidates.size }
    }

    fun rerank(
        input: String,
        candidates: List<String>,
        leftContext: String = "",
        rightContext: String = ""
    ): List<String> {
        val scores = score(input, candidates, leftContext, rightContext) ?: return candidates
        return candidates.indices
            .sortedByDescending { scores[it] }
            .map { candidates[it] }
    }
}

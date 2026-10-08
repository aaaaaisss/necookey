package com.aaaaaisss.necokey

import com.kazumaproject.zenz.ZenzEngine

/**
 * Optional zenz reranker.
 *
 * The Lite conversion engine remains the source of candidates. zenz only
 * scores an already generated candidate set. If no model is loaded, this
 * class is a no-op and normal conversion continues unchanged.
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

    fun rerank(
        input: String,
        candidates: List<String>,
        leftContext: String = "",
        rightContext: String = ""
    ): List<String> {
        if (!ready || candidates.size < 2) return candidates
        val scores = runCatching {
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
        }.getOrNull() ?: return candidates

        if (scores.size != candidates.size) return candidates
        return candidates.indices
            .sortedByDescending { scores[it] }
            .map { candidates[it] }
    }
}

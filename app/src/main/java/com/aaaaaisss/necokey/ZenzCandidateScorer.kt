package com.aaaaaisss.necokey

import com.aaaaaisss.necokey.zenz.ZenzEngine

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

    @Synchronized
    fun closeModel() {
        if (!ready) return
        runCatching { ZenzEngine.closeModel() }
        ready = false
    }

    /**
     * Scores explicitly supplied candidates with zenz Teacher Forcing.
     *
     * zenz does not generate a replacement string here. The native layer
     * pre-fills the prompt, then feeds each candidate token sequence as the
     * expected continuation and sums/normalizes the token log probabilities.
     */
    fun scoreTeacherForced(
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
        val scores = scoreTeacherForced(input, candidates, leftContext, rightContext) ?: return candidates
        return candidates.indices
            .sortedByDescending { scores[it] }
            .map { candidates[it] }
    }
}

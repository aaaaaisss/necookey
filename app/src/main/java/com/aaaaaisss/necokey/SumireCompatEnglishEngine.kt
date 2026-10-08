package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * Minimal compatibility implementation for necokey's Japanese-only build.
 * The upstream English/QWERTY engine is excluded to avoid its extra dependencies.
 */
class EnglishEngine {
    fun getCandidates(
        input: String,
        enableTypoCorrection: Boolean = false,
        enablePrediction: Boolean = true,
    ): List<Candidate> = emptyList()
}

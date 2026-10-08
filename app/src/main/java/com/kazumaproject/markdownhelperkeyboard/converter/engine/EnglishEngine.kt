package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * Japanese-only build of Sumire.
 *
 * The upstream engine also contains English/QWERTY conversion, but necokey
 * does not expose that input path. Keeping this small implementation avoids
 * pulling the full QWERTY/Glide stack into the Android build.
 */
class EnglishEngine {
    fun getCandidates(
        input: String,
        enableTypoCorrection: Boolean = false,
        enablePrediction: Boolean = true,
    ): List<Candidate> = emptyList()
}

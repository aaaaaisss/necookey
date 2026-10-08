package com.aaaaaisss.necokey

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import kotlinx.coroutines.runBlocking

class CandidateEngine {
    private var converter: SumireKanaKanjiConverter? = null

    @Synchronized
    fun initialize(context: Context) {
        if (converter == null) {
            converter = SumireKanaKanjiConverter(context.applicationContext)
        }
    }

    /**
     * necookey-owned adapter around Sumire's Candidate objects.
     *
     * The upstream Candidate, including conversionSegments, stays in vendor/sumire.
     * necookey keeps that metadata until reranking has finished.
     */
    fun detailedCandidates(input: String, n: Int = 12): List<Candidate> {
        val current = converter ?: return emptyList()
        return runBlocking { current.candidates(input, n) }
    }

    fun candidates(input: String): List<String> {
        val current = converter ?: return listOf(input)
        return runBlocking {
            current.candidates(input).map(Candidate::string).distinct()
        }
    }
}

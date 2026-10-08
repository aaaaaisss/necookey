package com.aaaaaisss.necokey

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CandidateEngine {
    @Volatile
    private var converter: SumireKanaKanjiConverter? = null

    suspend fun initialize(context: Context) {
        if (converter != null) return
        withContext(Dispatchers.Default) {
            synchronized(this@CandidateEngine) {
                if (converter == null) {
                    converter = SumireKanaKanjiConverter(context.applicationContext)
                }
            }
        }
    }

    fun isReady(): Boolean = converter != null

    /**
     * necookey-owned adapter around Sumire's Candidate objects.
     *
     * The upstream Candidate, including conversionSegments, stays in vendor/sumire.
     * necookey keeps that metadata until reranking has finished.
     */
    suspend fun detailedCandidates(input: String, n: Int = 12): List<Candidate> {
        val current = converter ?: return emptyList()
        return withContext(Dispatchers.Default) {
            current.candidates(input, n)
        }
    }

    suspend fun candidates(input: String): List<String> {
        val current = converter ?: return listOf(input)
        return withContext(Dispatchers.Default) {
            current.candidates(input).map(Candidate::string).distinct()
        }
    }
}

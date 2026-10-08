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

    fun candidates(input: String): List<String> {
        val current = converter ?: return listOf(input)
        return runBlocking {
            current.candidates(input).map(Candidate::string).distinct()
        }
    }
}

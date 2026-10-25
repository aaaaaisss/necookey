package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent

object CandidateStripLayoutPolicy {

    fun shouldUseLinearHorizontalLayout(content: CandidateStripContent): Boolean {
        @Suppress("UNUSED_PARAMETER") val unused = content
        return false
    }
}

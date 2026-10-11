package com.kazumaproject.markdownhelperkeyboard.zeroquery

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

class ZeroQueryLookupUseCase(
    private val bundledProviderHolder: LazyZeroQueryProvider,
) {
    suspend fun lookup(key: String): List<Candidate> {
        if (key.isBlank()) return emptyList()

        val bundledCandidates = bundledProviderHolder.getIfEnabled(enabled = true)
            ?.lookup(key)
            ?.map { it.toCandidate() }
            .orEmpty()

        val seen = LinkedHashSet<String>()
        return bundledCandidates.mapNotNull { candidate ->
            val value = candidate.string
            if (value.isBlank() || !seen.add(value)) {
                null
            } else {
                candidate
            }
        }
    }
}

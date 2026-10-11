package com.kazumaproject.markdownhelperkeyboard.zeroquery

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

class ZeroQueryLookupUseCase(
    private val bundledProviderHolder: LazyZeroQueryProvider,
) {
    suspend fun lookup(key: String): List<Candidate> {
        if (key.isBlank()) return emptyList()

        val provider = bundledProviderHolder.getIfEnabled(enabled = true)
        val bundledCandidates = provider
            ?.let { p -> ZeroQueryBackoff.firstHit(key) { p.lookup(it) } }
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

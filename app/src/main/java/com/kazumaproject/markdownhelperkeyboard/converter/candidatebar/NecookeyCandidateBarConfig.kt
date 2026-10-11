package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

/** Tunables for necookey's two-row candidate bar and Zenzai (zenz-verified conversion). */
data class NecookeyCandidateBarConfig(
    /** Left context sent to zenz is truncated to this many UTF-16 units (keeps the prompt short). */
    val maxLeftContextChars: Int = DEFAULT_MAX_LEFT_CONTEXT_CHARS,
    /** Right context sent to zenz is truncated to this many UTF-16 units. */
    val maxRightContextChars: Int = DEFAULT_MAX_RIGHT_CONTEXT_CHARS,
    /** N-best paths requested from Sumire for the conversion row (per-bunsetsu alternatives). */
    val conversionNBest: Int = DEFAULT_CONVERSION_N_BEST,
    /** Readings longer than this skip zenz (latency guard; Sumire's result stays). */
    val maxZenzInputLength: Int = DEFAULT_MAX_ZENZ_INPUT_LENGTH,
    val maxTopRowCandidates: Int = DEFAULT_MAX_TOP_ROW_CANDIDATES,
    val maxBottomRowCandidates: Int = DEFAULT_MAX_BOTTOM_ROW_CANDIDATES,
) {
    companion object {
        const val DEFAULT_MAX_LEFT_CONTEXT_CHARS = 40
        const val DEFAULT_MAX_RIGHT_CONTEXT_CHARS = 20
        const val DEFAULT_CONVERSION_N_BEST = 8
        const val DEFAULT_MAX_ZENZ_INPUT_LENGTH = 32
        const val DEFAULT_MAX_TOP_ROW_CANDIDATES = 32
        const val DEFAULT_MAX_BOTTOM_ROW_CANDIDATES = 48

        val DEFAULT = NecookeyCandidateBarConfig()
    }
}

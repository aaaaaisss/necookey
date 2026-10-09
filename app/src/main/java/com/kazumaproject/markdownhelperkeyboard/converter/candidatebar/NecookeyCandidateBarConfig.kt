package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

/**
 * Tunables for necookey's two-row candidate bar and the zenz confidence gate.
 *
 * Costs are Sumire/Mozc path costs (lower is better, roughly 500 * -ln p), so a normalized gap
 * of 200 per reading character means "the runner-up for this bunsetsu is within about
 * e^(-0.4) per character of the winner".
 */
data class NecookeyCandidateBarConfig(
    /** Ambiguity gate: a bunsetsu is re-checked by zenz when (cost2 - cost1) / readingLength < this. */
    val gateNormalizedGapThreshold: Double = DEFAULT_GATE_NORMALIZED_GAP_THRESHOLD,
    /** How many Sumire alternatives of one bunsetsu are sent to zenz. */
    val zenzTopK: Int = DEFAULT_ZENZ_TOP_K,
    /** Upper bound of zenz calls per keystroke (each ambiguous bunsetsu is one call). */
    val maxZenzBunsetsuPerRequest: Int = DEFAULT_MAX_ZENZ_BUNSETSU_PER_REQUEST,
    /**
     * Minimum improvement in average log-probability (nats per token) a zenz pick needs over
     * Sumire's own pick before the leftmost slot is swapped. 0 means "any strictly better score".
     */
    val minZenzLogProbMargin: Float = DEFAULT_MIN_ZENZ_LOG_PROB_MARGIN,
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
        const val DEFAULT_GATE_NORMALIZED_GAP_THRESHOLD = 200.0
        const val DEFAULT_ZENZ_TOP_K = 4
        const val DEFAULT_MAX_ZENZ_BUNSETSU_PER_REQUEST = 3
        const val DEFAULT_MIN_ZENZ_LOG_PROB_MARGIN = 0f
        const val DEFAULT_MAX_LEFT_CONTEXT_CHARS = 40
        const val DEFAULT_MAX_RIGHT_CONTEXT_CHARS = 20
        const val DEFAULT_CONVERSION_N_BEST = 8
        const val DEFAULT_MAX_ZENZ_INPUT_LENGTH = 32
        const val DEFAULT_MAX_TOP_ROW_CANDIDATES = 32
        const val DEFAULT_MAX_BOTTOM_ROW_CANDIDATES = 48

        val DEFAULT = NecookeyCandidateBarConfig()
    }
}

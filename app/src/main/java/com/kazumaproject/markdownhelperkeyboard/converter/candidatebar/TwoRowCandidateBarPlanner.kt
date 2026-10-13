package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * What the two rows of the candidate bar show (homophone UI).
 *
 * Top row: the conversion result only, with low-confidence bunsetsu marked ([Candidate.uncertainRanges]).
 * Bottom row: diff chips for the focused uncertain bunsetsu, then one focus chip per other marked
 * bunsetsu, then (only when the prediction row setting is on) prediction candidates.
 */
data class TwoRowCandidateBar(
    /** Sumire's conversion (possibly zenz-corrected or chip-edited), without marks. */
    val primary: Candidate?,
    val homophone: HomophonePlan?,
    /** Diff chips + focus chips ([Candidate.homophoneSlot] >= 0). */
    val chips: List<Candidate>,
    /** Other conversion candidates: only for the expanded candidate list, never in the rows. */
    val conversionAlternatives: List<Candidate>,
    /** Bottom-row predictions (empty when the prediction row is off; action candidates stay). */
    val predictions: List<Candidate>,
) {
    val topRow: List<Candidate>
        get() = listOfNotNull(
            primary?.let { p ->
                val marks = homophone?.markRanges.orEmpty()
                if (marks.isEmpty()) p else p.copy(uncertainRanges = marks)
            },
        )

    val bottomRow: List<Candidate> get() = chips + predictions

    companion object {
        val EMPTY = TwoRowCandidateBar(null, null, emptyList(), emptyList(), emptyList())
    }
}

object TwoRowCandidateBarPlanner {

    /** Sumire's type 5 ("combine part of letters"): a candidate covering a reading prefix. */
    const val CANDIDATE_TYPE_NBEST_PARTIAL: Byte = 5

    /** Diff chip: string = differing part, commitText = whole bunsetsu output. Never committed directly. */
    const val CANDIDATE_TYPE_HOMOPHONE_CHIP: Byte = 70

    /** Focus chip: moves the chips to another marked bunsetsu. string = that bunsetsu's output. */
    const val CANDIDATE_TYPE_HOMOPHONE_FOCUS: Byte = 71

    fun isHomophoneChip(candidate: Candidate): Boolean = candidate.homophoneSlot >= 0 &&
        (candidate.type == CANDIDATE_TYPE_HOMOPHONE_CHIP || candidate.type == CANDIDATE_TYPE_HOMOPHONE_FOCUS)

    /**
     * @param input the current reading.
     * @param conversionCandidates Sumire's merged conversion-mode list (no prediction), in order.
     * @param analysis bunsetsu analysis of the engine primary, or null when unavailable.
     * @param primaryOverride the zenz-corrected or chip-edited primary for this input, if any.
     * @param predictionCandidates Sumire's merged prediction-mode list, in order.
     * @param showPredictions the prediction row setting (off by default in the app).
     * @param focusSlot bunsetsu the user moved the chips to (ignored unless it is marked).
     * @param isActionCandidate candidates that are not text (macros, selection actions) are
     *   never deduplicated away and always stay in the bottom row.
     */
    fun plan(
        input: String,
        conversionCandidates: List<Candidate>,
        analysis: BunsetsuAnalysis?,
        primaryOverride: Candidate?,
        predictionCandidates: List<Candidate>,
        config: NecookeyCandidateBarConfig = NecookeyCandidateBarConfig.DEFAULT,
        isActionCandidate: (Candidate) -> Boolean = { false },
        showPredictions: Boolean = false,
        focusSlot: Int? = null,
        threshold: Int = HomophoneChips.SUMIRE_GAP_THRESHOLD,
        marginSource: BunsetsuMarginSource = SumireCostGapMargin,
    ): TwoRowCandidateBar {
        if (input.isEmpty()) return TwoRowCandidateBar.EMPTY

        val sumirePrimary = conversionCandidates.firstOrNull {
            !isActionCandidate(it) && it.length.toInt() == input.length
        } ?: conversionCandidates.firstOrNull { !isActionCandidate(it) }
        // An override only ever replaces the engine's own best path. When learning / the user
        // dictionary put something else first, the user's history wins and the override is ignored.
        val primary = if (
            primaryOverride != null && analysis != null && sumirePrimary != null &&
            sumirePrimary.string == analysis.primary.string &&
            primaryOverride.length.toInt() == input.length
        ) {
            primaryOverride
        } else {
            sumirePrimary
        }

        val homophone = if (analysis != null && primary != null) {
            HomophoneChips.plan(analysis, primary, focusSlot, threshold, marginSource)
        } else {
            null
        }
        val chips = if (analysis != null && homophone != null) chipCandidates(input, analysis, homophone) else emptyList()

        val shown = HashSet<String>()
        primary?.let { shown += it.string }
        val alternatives = conversionCandidates.filter { candidate ->
            !isActionCandidate(candidate) && candidate.string.isNotEmpty() && shown.add(candidate.string)
        }

        val shownBottom = HashSet<String>()
        val predictions = predictionCandidates.filter { candidate ->
            if (isActionCandidate(candidate)) return@filter true
            showPredictions &&
                candidate.string.isNotEmpty() &&
                candidate.string != primary?.string &&
                shownBottom.add(candidate.string)
        }.take(config.maxBottomRowCandidates.coerceAtLeast(0))

        return TwoRowCandidateBar(
            primary = primary,
            homophone = homophone,
            chips = chips,
            conversionAlternatives = alternatives,
            predictions = predictions,
        )
    }

    private fun chipCandidates(
        input: String,
        analysis: BunsetsuAnalysis,
        plan: HomophonePlan,
    ): List<Candidate> {
        val focus = plan.focusSlot ?: return emptyList()
        val result = ArrayList<Candidate>()
        for (chip in plan.chips) {
            val span = analysis.slots[chip.slotIndex].span
            result += Candidate(
                string = chip.label,
                type = CANDIDATE_TYPE_HOMOPHONE_CHIP,
                length = input.length.toUByte(),
                score = 0,
                yomi = input.substring(span.start, span.end),
                commitText = chip.output,
                // The current choice is drawn underlined like its bunsetsu in the top row.
                uncertainRanges = if (chip.isCurrent) listOf(0 to chip.label.length) else emptyList(),
                homophoneSlot = chip.slotIndex,
            )
        }
        // Other marked bunsetsu, in sentence order: tap to move the chips there.
        for (slot in plan.uncertainSlots.sorted()) {
            if (slot == focus) continue
            val span = analysis.slots[slot].span
            result += Candidate(
                string = plan.slotOutputs[slot],
                type = CANDIDATE_TYPE_HOMOPHONE_FOCUS,
                length = input.length.toUByte(),
                score = 0,
                yomi = input.substring(span.start, span.end),
                homophoneSlot = slot,
            )
        }
        return result
    }
}

package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/** What the two rows of the candidate bar show. */
data class TwoRowCandidateBar(
    /** Leftmost slot of the top row: Sumire's conversion (possibly zenz-corrected). */
    val primary: Candidate?,
    /** Rest of the top row: alternatives for the first bunsetsu, in Sumire order. */
    val firstBunsetsuAlternatives: List<Candidate>,
    /** Bottom row: prediction candidates not already shown in the top row. */
    val predictions: List<Candidate>,
) {
    val topRow: List<Candidate> get() = listOfNotNull(primary) + firstBunsetsuAlternatives

    companion object {
        val EMPTY = TwoRowCandidateBar(null, emptyList(), emptyList())
    }
}

object TwoRowCandidateBarPlanner {

    /** Sumire's type 5 ("combine part of letters"): a candidate covering a reading prefix. */
    const val CANDIDATE_TYPE_NBEST_PARTIAL: Byte = 5

    /**
     * @param input the current reading.
     * @param conversionCandidates Sumire's merged conversion-mode list (no prediction), in order.
     * @param analysis bunsetsu analysis of the engine primary, or null when unavailable.
     * @param primaryOverride the zenz-corrected primary, when one is available for this input.
     * @param predictionCandidates Sumire's merged prediction-mode list, in order.
     * @param isActionCandidate candidates that are not text (macros, selection actions) are
     *   never deduplicated away and never treated as bunsetsu alternatives.
     */
    fun plan(
        input: String,
        conversionCandidates: List<Candidate>,
        analysis: BunsetsuAnalysis?,
        primaryOverride: Candidate?,
        predictionCandidates: List<Candidate>,
        config: NecookeyCandidateBarConfig = NecookeyCandidateBarConfig.DEFAULT,
        isActionCandidate: (Candidate) -> Boolean = { false },
    ): TwoRowCandidateBar {
        if (input.isEmpty()) return TwoRowCandidateBar.EMPTY

        val sumirePrimary = conversionCandidates.firstOrNull {
            !isActionCandidate(it) && it.length.toInt() == input.length
        } ?: conversionCandidates.firstOrNull { !isActionCandidate(it) }
        // zenz only ever replaces the engine's own best path. When learning / the user dictionary
        // put something else first, the user's history wins and the override is ignored.
        val primary = if (
            primaryOverride != null && analysis != null && sumirePrimary != null &&
            sumirePrimary.string == analysis.primary.string &&
            primaryOverride.length.toInt() == input.length
        ) {
            primaryOverride
        } else {
            sumirePrimary
        }

        val shownTop = LinkedHashSet<String>()
        primary?.let { shownTop += it.string }
        val alternatives = ArrayList<Candidate>()
        // The engine primary stays reachable (right next to the leftmost slot) when zenz replaced it.
        if (primary != null && sumirePrimary != null && primary !== sumirePrimary &&
            shownTop.add(sumirePrimary.string)
        ) {
            alternatives += sumirePrimary
        }

        // The first bunsetsu follows the accepted path: a zenz-corrected primary carries its own
        // segmentation, which may put the first boundary elsewhere than Sumire's analysis.
        val sumireFirstEnd = analysis?.takeIf { it.hasMultipleBunsetsu }?.firstBunsetsuEnd
        val acceptedSegments = primary?.takeIf { it === primaryOverride }?.conversionSegments.orEmpty()
        val firstEnd = if (acceptedSegments.isNotEmpty()) {
            acceptedSegments.first().inputEnd.takeIf { acceptedSegments.size > 1 }
        } else {
            sumireFirstEnd
        }
        if (analysis != null && firstEnd != null && firstEnd in 1 until input.length) {
            // 1) Bunsetsu-level alternatives from Sumire's N-best paths (same [0, firstEnd) range).
            val slotAlternatives =
                if (firstEnd == sumireFirstEnd) analysis.slots.first().alternatives else emptyList()
            for (alt in slotAlternatives) {
                if (alt.output.isEmpty() || !shownTop.add(alt.output)) continue
                alternatives += Candidate(
                    string = alt.output,
                    type = CANDIDATE_TYPE_NBEST_PARTIAL,
                    length = firstEnd.toUByte(),
                    score = alt.pathCost,
                    yomi = input.substring(0, firstEnd),
                )
            }
            // 2) Sumire's own partial candidates covering exactly the first bunsetsu.
            for (candidate in conversionCandidates) {
                if (isActionCandidate(candidate)) continue
                if (candidate.length.toInt() != firstEnd) continue
                if (shownTop.add(candidate.string)) alternatives += candidate
            }
        } else {
            // Single bunsetsu: the alternatives for "the first bunsetsu" are the remaining
            // whole-reading conversion candidates.
            for (candidate in conversionCandidates) {
                if (isActionCandidate(candidate)) continue
                if (candidate.length.toInt() != input.length) continue
                if (shownTop.add(candidate.string)) alternatives += candidate
            }
        }

        val maxTop = (config.maxTopRowCandidates - (if (primary != null) 1 else 0)).coerceAtLeast(0)
        val topAlternatives = alternatives.take(maxTop)
        // Dedupe against what is actually visible in the top row (after truncation).
        val visibleTop = HashSet<String>()
        primary?.let { visibleTop += it.string }
        topAlternatives.forEach { visibleTop += it.string }
        val shownBottom = HashSet<String>()
        val predictions = predictionCandidates.filter { candidate ->
            if (isActionCandidate(candidate)) return@filter true
            candidate.string.isNotEmpty() &&
                candidate.string !in visibleTop &&
                shownBottom.add(candidate.string)
        }.take(config.maxBottomRowCandidates.coerceAtLeast(0))

        return TwoRowCandidateBar(
            primary = primary,
            firstBunsetsuAlternatives = topAlternatives,
            predictions = predictions,
        )
    }
}

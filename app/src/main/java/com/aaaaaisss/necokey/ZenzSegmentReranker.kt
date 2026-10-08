package com.aaaaaisss.necokey

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * necookey-owned per-segment zenz integration.
 *
 * Sumire remains responsible for lattice construction and candidate generation.
 * This layer only observes Candidate.conversionSegments, detects spans whose
 * top candidates disagree, and asks zenz to score those segment alternatives.
 *
 * Pipeline:
 *   1. Sumire generates the full candidate lattice.
 *   2. The confidence gate checks each input span for ambiguity.
 *   3. Only ambiguous spans are sent to zenz. The scorer builds the prompt
 *      from left/right context and the reading, then Teacher-Forces each
 *      explicitly supplied candidate token sequence.
 *   4. zenz scores reorder the existing Sumire candidates. No text is generated.
 *
 * This layer never calls zenz for a span with one output and never invents a
 * candidate that Sumire did not generate.
 */
class ZenzSegmentReranker(
    private val scorer: ZenzCandidateScorer,
) {
    private data class Span(
        val start: Int,
        val end: Int,
    )

    private data class SegmentEvidence(
        val output: String,
        val pathScore: Int,
    )

    data class RerankResult(
        val candidates: List<Candidate>,
        val firstSegmentAlternatives: List<String>,
    )

    fun rerank(
        input: String,
        candidates: List<Candidate>,
        maxCandidatesToInspect: Int = 16,
    ): List<Candidate> = rerankDetailed(input, candidates, maxCandidatesToInspect).candidates

    fun rerankDetailed(
        input: String,
        candidates: List<Candidate>,
        maxCandidatesToInspect: Int = 16,
    ): RerankResult {
        if (!scorer.isReady() || candidates.size < 2 || input.isEmpty()) {
            val distinct = candidates.distinctBy(Candidate::string)
            return RerankResult(distinct, fallbackFirstAlternatives(distinct))
        }

        val inspected = candidates.asSequence().distinctBy(Candidate::string).take(maxCandidatesToInspect.coerceAtLeast(2)).toList()
        val bySpan = linkedMapOf<Span, MutableList<SegmentEvidence>>()

        inspected.forEach { candidate ->
            candidate.conversionSegments.forEach { segment ->
                if (segment.inputStart < 0 ||
                    segment.inputEnd <= segment.inputStart ||
                    segment.inputEnd > input.length ||
                    segment.output.isEmpty()
                ) return@forEach

                val span = Span(segment.inputStart, segment.inputEnd)
                bySpan.getOrPut(span) { mutableListOf() }
                    .add(SegmentEvidence(segment.output, candidate.score))
            }
        }

        val zenzBySpan = mutableMapOf<Span, Map<String, Float>>()

        for ((span, evidence) in bySpan) {
            val grouped = evidence
                .groupBy { it.output }
                .mapValues { (_, values) -> values.minOf { it.pathScore } }

            // One output means there is no ambiguity for this input span.
            if (grouped.size < 2) continue

            // Candidate.score is a whole-path cost. It is used only as a cheap
            // ambiguity gate here, never as a zenz probability.
            val scoreList = grouped.values.sorted()
            if (!ZenzConfidenceGate.shouldRerank(scoreList.map(Int::toLong))) continue

            val outputs = grouped.keys.asSequence().filter { it.isNotEmpty() }.distinct().take(8).toList()
            if (outputs.size < 2) continue
            val reference = inspected
                .asSequence()
                .filter { candidate ->
                    candidate.conversionSegments.any {
                        it.inputStart == span.start && it.inputEnd == span.end
                    }
                }
                .minByOrNull { it.score }
                ?: continue
            val referenceSegments = reference.conversionSegments

            val leftContext = referenceSegments
                .filter { it.inputEnd <= span.start }
                .joinToString(separator = "") { it.output }
            val rightContext = referenceSegments
                .filter { it.inputStart >= span.end }
                .joinToString(separator = "") { it.output }

            val reading = input.substring(span.start, span.end)
            // Gate first. Only an ambiguous span reaches the Teacher Forcing scorer.
            val scores = scorer.scoreTeacherForced(
                input = reading,
                candidates = outputs,
                leftContext = leftContext,
                rightContext = rightContext,
            ) ?: continue

            if (scores.any { !it.isFinite() }) continue

            zenzBySpan[span] = outputs.indices.associate { outputs[it] to scores[it] }
        }

        if (zenzBySpan.isEmpty()) {
            val distinct = candidates.distinctBy(Candidate::string)
            return RerankResult(distinct, fallbackFirstAlternatives(distinct))
        }

        val ordered = candidates.mapIndexed { index, candidate ->
            var zenzTotal = 0.0f
            var matched = 0

            candidate.conversionSegments.forEach { segment ->
                val span = Span(segment.inputStart, segment.inputEnd)
                val score = zenzBySpan[span]?.get(segment.output) ?: return@forEach
                zenzTotal += score
                matched++
            }

            RerankedCandidate(
                candidate = candidate,
                originalIndex = index,
                zenzAverageScore = if (matched == 0) 0.0f else zenzTotal / matched,
                matchedSegments = matched,
            )
        }

        // Keep the Sumire path intact. zenz only supplies an ordering signal.
        val reranked = ordered
            .sortedWith(
                compareByDescending<RerankedCandidate> { it.matchedSegments > 0 }
                    .thenByDescending { it.zenzAverageScore }
                    .thenBy { it.candidate.score }
                    .thenBy { it.originalIndex }
            )
            .map { it.candidate }
            .distinctBy(Candidate::string)

        val first = reranked.firstOrNull()?.conversionSegments?.firstOrNull()
        val firstSpan = first?.let { Span(it.inputStart, it.inputEnd) }
        val firstAlternatives = firstSpan
            ?.let { zenzBySpan[it] }
            ?.entries
            ?.sortedByDescending { it.value }
            ?.map { it.key }
            ?.filter { it != first.output }
            ?.take(3)
            ?: fallbackFirstAlternatives(reranked)

        return RerankResult(reranked, firstAlternatives)
    }

    private fun fallbackFirstAlternatives(candidates: List<Candidate>): List<String> {
        val first = candidates.firstOrNull()?.conversionSegments?.firstOrNull() ?: return emptyList()
        return candidates.asSequence()
            .mapNotNull { it.conversionSegments.firstOrNull() }
            .filter { it.inputStart == first.inputStart && it.inputEnd == first.inputEnd && it.output != first.output }
            .map { it.output }
            .distinct()
            .take(3)
            .toList()
    }

    private data class RerankedCandidate(
        val candidate: Candidate,
        val originalIndex: Int,
        val zenzAverageScore: Float,
        val matchedSegments: Int,
    )
}

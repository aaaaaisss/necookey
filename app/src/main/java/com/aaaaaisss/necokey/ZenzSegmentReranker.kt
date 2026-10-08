package com.aaaaaisss.necokey

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment

/**
 * Reranks only existing Sumire paths.
 *
 * Important distinction:
 * Candidate.conversionSegments are lattice/node segments, not bunsetsu.
 * The bunsetsu boundaries come from Sumire's splitPatternByCandidateString.
 * zenz therefore scores actual bunsetsu alternatives, while the final result
 * remains one of the valid paths already produced by Sumire.
 */
class ZenzSegmentReranker(
    private val scorer: ZenzCandidateScorer,
) {
    private data class Span(val start: Int, val end: Int)

    private data class BunsetsuSegment(
        val span: Span,
        val output: String,
    )

    private data class SegmentEvidence(
        val output: String,
    )

    data class RerankResult(
        val candidates: List<Candidate>,
        val firstSegmentAlternatives: List<String>,
    )

    fun rerank(
        input: String,
        candidates: List<Candidate>,
        splitPatternByCandidateString: Map<String, List<Int>> = emptyMap(),
        maxCandidatesToInspect: Int = 16,
    ): List<Candidate> =
        rerankDetailed(input, candidates, splitPatternByCandidateString, maxCandidatesToInspect).candidates

    fun rerankDetailed(
        input: String,
        candidates: List<Candidate>,
        splitPatternByCandidateString: Map<String, List<Int>> = emptyMap(),
        maxCandidatesToInspect: Int = 16,
    ): RerankResult {
        val distinct = candidates.distinctBy(Candidate::string)
        if (!scorer.isReady() || distinct.size < 2 || input.isEmpty()) {
            return RerankResult(
                distinct,
                fallbackFirstAlternatives(input, distinct, splitPatternByCandidateString),
            )
        }

        val inspected = distinct.take(maxCandidatesToInspect.coerceAtLeast(2))
        val bunsetsuByCandidate = inspected.associateWith { candidate ->
            buildBunsetsuSegments(
                input = input,
                candidate = candidate,
                splitPositions = splitPatternByCandidateString[candidate.string].orEmpty(),
            )
        }

        if (bunsetsuByCandidate.values.any { it == null }) {
            // A malformed/missing split map must never cause us to invent segmentation.
            // Candidates with usable Sumire metadata can still be reranked.
        }

        val bySpan = linkedMapOf<Span, MutableList<SegmentEvidence>>()
        bunsetsuByCandidate.forEach { (candidate, segments) ->
            segments.orEmpty().forEach { segment ->
                bySpan.getOrPut(segment.span) { mutableListOf() }
                    .add(SegmentEvidence(segment.output))
            }
        }

        val zenzBySpan = mutableMapOf<Span, Map<String, Float>>()

        for ((span, evidence) in bySpan) {
            val grouped = evidence.groupBy { it.output }

            if (grouped.size < 2) continue

            // Gate on actual top-ranked Sumire ambiguity for this exact bunsetsu.
            val outputRanks = grouped.keys.mapNotNull { output ->
                inspected.indexOfFirst { candidate ->
                    bunsetsuByCandidate[candidate].orEmpty().any {
                        it.span == span && it.output == output
                    }
                }.takeIf { it >= 0 }
            }
            if (!ZenzConfidenceGate.shouldRerank(outputRanks)) continue

            val outputs = grouped.keys
                .asSequence()
                .filter(String::isNotEmpty)
                .take(8)
                .toList()
            if (outputs.size < 2) continue

            // One context is shared for the span so one zenz call can score all
            // competing outputs. Use the best Sumire path that contains this span.
            val reference = inspected
                .asSequence()
                .filter { candidate -> bunsetsuByCandidate[candidate].orEmpty().any { it.span == span } }
                .minByOrNull(Candidate::score)
                ?: continue
            val referenceSegments = bunsetsuByCandidate[reference].orEmpty()

            val leftContext = referenceSegments
                .filter { it.span.end <= span.start }
                .joinToString(separator = "") { it.output }
            val rightContext = referenceSegments
                .filter { it.span.start >= span.end }
                .joinToString(separator = "") { it.output }

            val reading = input.substring(span.start, span.end)
            val scores = scorer.scoreTeacherForced(
                input = reading,
                candidates = outputs,
                leftContext = leftContext,
                rightContext = rightContext,
            ) ?: continue

            if (scores.size != outputs.size || scores.any { !it.isFinite() }) continue
            zenzBySpan[span] = outputs.indices.associate { outputs[it] to scores[it] }
        }

        if (zenzBySpan.isEmpty()) {
            return RerankResult(
                distinct,
                fallbackFirstAlternatives(input, distinct, splitPatternByCandidateString),
            )
        }

        val ordered = distinct.mapIndexed { index, candidate ->
            val segments = bunsetsuByCandidate[candidate].orEmpty()
            var total = 0.0f
            var matched = 0
            segments.forEach { segment ->
                zenzBySpan[segment.span]?.get(segment.output)?.let {
                    total += it
                    matched++
                }
            }

            RerankedCandidate(
                candidate = candidate,
                originalIndex = index,
                zenzAverageScore = if (matched == 0) Float.NEGATIVE_INFINITY else total / matched,
                matchedSegments = matched,
            )
        }.sortedWith(
            compareByDescending<RerankedCandidate> { it.matchedSegments > 0 }
                .thenByDescending { it.zenzAverageScore }
                .thenBy { it.candidate.score }
                .thenBy { it.originalIndex }
        )

        val reranked = ordered.map { it.candidate }
        val main = reranked.firstOrNull()
        val firstSegments = main?.let { bunsetsuByCandidate[it].orEmpty() }
        val firstSpan = firstSegments?.firstOrNull()?.span
        val firstAlternatives = firstSpan
            ?.let { zenzBySpan[it] }
            ?.entries
            ?.sortedByDescending { it.value }
            ?.map { it.key }
            ?.filter { output -> output != firstSegments.first().output }
            ?.take(3)
            ?: fallbackFirstAlternatives(input, reranked, splitPatternByCandidateString)

        return RerankResult(reranked, firstAlternatives)
    }

    private fun buildBunsetsuSegments(
        input: String,
        candidate: Candidate,
        splitPositions: List<Int>,
    ): List<BunsetsuSegment>? {
        val validSplits = splitPositions
            .distinct()
            .filter { it > 0 && it < input.length }
            .sorted()
        val boundaries = listOf(0) + validSplits + listOf(input.length)
        val nodes = candidate.conversionSegments.sortedBy { it.inputStart }

        if (nodes.isEmpty()) return null
        if (nodes.first().inputStart != 0 || nodes.last().inputEnd != input.length) return null

        return boundaries.zipWithNext().mapNotNull { (start, end) ->
            val contained = nodes.filter {
                it.inputStart >= start && it.inputEnd <= end
            }
            if (contained.isEmpty() || contained.first().inputStart != start || contained.last().inputEnd != end) {
                return null
            }
            BunsetsuSegment(
                span = Span(start, end),
                output = contained.joinToString(separator = "") { it.output },
            )
        }
    }

    private fun fallbackFirstAlternatives(
        input: String,
        candidates: List<Candidate>,
        splitPatternByCandidateString: Map<String, List<Int>>,
    ): List<String> {
        val first = candidates.firstOrNull() ?: return emptyList()
        val firstSegment = buildBunsetsuSegments(
            input,
            first,
            splitPatternByCandidateString[first.string].orEmpty(),
        )?.firstOrNull() ?: return emptyList()

        return candidates.asSequence()
            .mapNotNull { candidate ->
                buildBunsetsuSegments(
                    input,
                    candidate,
                    splitPatternByCandidateString[candidate.string].orEmpty(),
                )?.firstOrNull()
            }
            .filter { it.span == firstSegment.span && it.output != firstSegment.output }
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

package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BunsetsuAnalysis

internal data class ResolvedZenzContext(
    val leftContext: String,
    val rightContext: String
)

internal fun resolveZenzContext(
    leftContext: String,
    rawRightContext: String,
    enableRightContext: Boolean
): ResolvedZenzContext {
    return ResolvedZenzContext(
        leftContext = leftContext,
        rightContext = if (enableRightContext) rawRightContext else ""
    )
}

internal fun buildZenzRerankCacheKey(
    profile: String,
    leftContext: String,
    rightContext: String,
    input: String,
    rerankTargets: List<IndexedValue<Candidate>>
): String {
    return buildString {
        append(profile)
        append('\u0001')
        append(leftContext)
        append('\u0001')
        append(rightContext)
        append('\u0001')
        append(input)
        rerankTargets.forEach {
            append('\u0002')
            append(it.index)
            append('\u0003')
            append(it.value.string)
            append('\u0003')
            append(it.value.score)
        }
    }
}

/**
 * Cache identity for the Necookey bunsetsu gate. Context is bounded to the same editor-side
 * limits used by the scorer; all variable fields are length-prefixed to avoid delimiter
 * collisions. Slot ranges and the N-best alternatives are included so a different analysis
 * cannot reuse an override merely because its concatenated primary text is the same.
 */
internal fun buildNecookeyZenzOverrideCacheKey(
    input: String,
    analysis: BunsetsuAnalysis,
    profile: String,
    editorLeftContext: String,
    editorRightContext: String,
    maxLeftContextChars: Int,
    maxRightContextChars: Int,
): String {
    val parts = buildList {
        add("necookey-zenz-v2")
        add(profile)
        add(input)
        add(editorLeftContext.takeLast(maxLeftContextChars.coerceAtLeast(0)))
        add(editorRightContext.take(maxRightContextChars.coerceAtLeast(0)))
        add(analysis.primary.string)
        add(analysis.primary.score.toString())
        add(analysis.slots.size.toString())
        analysis.slots.forEach { slot ->
            add(slot.span.start.toString())
            add(slot.span.end.toString())
            add(slot.primaryOutput)
            add(slot.alternatives.size.toString())
            slot.alternatives.forEach { alternative ->
                add(alternative.output)
                add(alternative.pathCost.toString())
            }
        }
    }
    return buildString {
        parts.forEach { part ->
            append(part.length)
            append(':')
            append(part)
        }
    }
}

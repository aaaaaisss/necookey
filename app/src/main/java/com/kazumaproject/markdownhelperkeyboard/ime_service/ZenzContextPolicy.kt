package com.kazumaproject.markdownhelperkeyboard.ime_service


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

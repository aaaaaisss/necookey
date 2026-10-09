package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/** A selected-text action (local text macros only). */
internal sealed interface SelectionAction {
    data class TextMacro(val id: Long) : SelectionAction
}

internal data class SelectionActionEntry(
    val candidate: Candidate,
    val action: SelectionAction,
)

/** The candidate and action share one ordered entry, so click handling needs no index arithmetic. */
internal data class SelectionActionSession(
    val selectedText: String,
    val entries: List<SelectionActionEntry>,
) {
    fun entryFor(candidate: Candidate, position: Int): SelectionActionEntry? {
        entries.getOrNull(position)?.takeIf { it.candidate == candidate }?.let { return it }
        return entries.firstOrNull { entry ->
                entry.candidate.type == candidate.type &&
                entry.candidate.sourceId == candidate.sourceId &&
                entry.candidate.yomi == candidate.yomi &&
                entry.candidate.string == candidate.string
        }
    }
}

internal object SelectionActionSessionComposer {
    fun compose(
        selectedText: String,
        localMacros: List<SelectionActionEntry>,
    ): SelectionActionSession? = localMacros
        .takeIf { it.isNotEmpty() }
        ?.let { SelectionActionSession(selectedText = selectedText, entries = it) }
}

package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TEXT_MACRO
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionActionSessionTest {
    @Test
    fun clicksResolveTypedMacroActionsDirectly() {
        val firstMacro = macroEntry(id = 10, name = "Quote")
        val secondMacro = macroEntry(id = 11, name = "Bold")

        val session = SelectionActionSessionComposer.compose(
            selectedText = "text",
            localMacros = listOf(firstMacro, secondMacro),
        )!!

        assertEquals(listOf("Quote", "Bold"), session.entries.map { it.candidate.string })
        assertEquals(SelectionAction.TextMacro(11), session.entryFor(secondMacro.candidate, 1)?.action)
        assertTrue(session.entryFor(firstMacro.candidate.copy(), 99)?.action is SelectionAction.TextMacro)
    }

    @Test
    fun emptyActionSetDoesNotCreateASession() {
        assertNull(SelectionActionSessionComposer.compose("text", emptyList()))
    }

    private fun macroEntry(id: Long, name: String) = SelectionActionEntry(
        candidate = Candidate(
            string = name,
            type = CANDIDATE_TYPE_TEXT_MACRO,
            length = 4u,
            score = 1,
            sourceId = id,
        ),
        action = SelectionAction.TextMacro(id),
    )
}

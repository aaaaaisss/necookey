package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceKeyResolverTest {

    @Test
    fun flickReversesTheExistingSpaceWidth() {
        assertEquals(" ", oppositeSpace(normalSpaceIsFullWidth = true))
        assertEquals("　", oppositeSpace(normalSpaceIsFullWidth = false))
    }


    @Test
    fun nonDirectJapaneseFlickKeepsHalfWidthSpace() {
        val space = resolveEmptySpaceForCurrentMode(
            isFlick = true,
            currentInputMode = InputMode.ModeJapanese
        )

        assertEquals(" ", space)
    }

    @Test
    fun nonDirectJapaneseTapKeepsFullWidthSpace() {
        val space = resolveEmptySpaceForCurrentMode(
            isFlick = false,
            currentInputMode = InputMode.ModeJapanese
        )

        assertEquals("　", space)
    }

    @Test
    fun nonDirectEnglishAndNumberKeepHalfWidthSpace() {
        listOf(InputMode.ModeEnglish, InputMode.ModeNumber).forEach { inputMode ->
            val space = resolveEmptySpaceForCurrentMode(
                isFlick = false,
                currentInputMode = inputMode
            )

            assertEquals(" ", space)
        }
    }
}

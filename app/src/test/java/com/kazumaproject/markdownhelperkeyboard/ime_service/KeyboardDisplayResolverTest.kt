package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardDisplayResolverTest {

    @Test
    fun customOnlyOrderNullRequestResolvesCustom() {
        val resolution = resolveKeyboardDisplay(
            requested = null,
            keyboardOrder = listOf(KeyboardType.CUSTOM)
        )

        assertEquals(KeyboardType.CUSTOM, resolution.resolvedKeyboard)
        assertEquals(0, resolution.resolvedIndex)
    }

    @Test
    fun customOnlyOrderSavedPositionZeroResolvesCustom() {
        val resolution = resolveKeyboardDisplay(
            requested = null,
            keyboardOrder = listOf(KeyboardType.CUSTOM),
            savedPosition = 0
        )

        assertEquals(KeyboardType.CUSTOM, resolution.resolvedKeyboard)
        assertEquals(0, resolution.resolvedIndex)
    }

    @Test
    fun customOnlyOrderSavedPositionOutOfRangeResolvesCustom() {
        val resolution = resolveKeyboardDisplay(
            requested = null,
            keyboardOrder = listOf(KeyboardType.CUSTOM),
            savedPosition = 4
        )

        assertEquals(KeyboardType.CUSTOM, resolution.resolvedKeyboard)
        assertEquals(0, resolution.resolvedIndex)
        assertTrue(resolution.savedPositionOutOfRange)
    }

    @Test
    fun emptyOrderFallsBackToCustom() {
        val resolution = resolveKeyboardDisplay(
            requested = KeyboardType.CUSTOM,
            keyboardOrder = emptyList()
        )

        assertEquals(KeyboardType.CUSTOM, resolution.resolvedKeyboard)
        assertNull(resolution.resolvedIndex)
        assertTrue(resolution.usedEmptyOrderFallback)
    }
}

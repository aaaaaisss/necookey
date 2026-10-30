package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceConvertCursorMovePolicyTest {
    @Test
    fun rejectsEverySpaceConvertLongPressSourceWhenInputStringExists() {
        SpaceConvertCursorMoveSource.entries.forEach { source ->
            assertFalse(
                source.name,
                SpaceConvertCursorMovePolicy.shouldEnterCursorMoveMode(
                    hasInputString = true,
                    source = source
                )
            )
        }
    }

    @Test
    fun emptyInputStringAllowsEverySpaceConvertLongPressSource() {
        SpaceConvertCursorMoveSource.entries.forEach { source ->
            assertTrue(
                source.name,
                SpaceConvertCursorMovePolicy.shouldEnterCursorMoveMode(
                    hasInputString = false,
                    source = source
                )
            )
        }
    }
}

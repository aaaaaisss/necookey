package com.kazumaproject.markdownhelperkeyboard.ime_service.candidatebar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FloatingCandidateBandTest {

    @Test
    fun floatsOnlyInChipModeWithTheKeyboardShown() {
        assertTrue(shouldFloat())
        assertFalse(shouldFloat(chip = false))
        assertFalse(shouldFloat(twoRow = false))
        assertFalse(shouldFloat(symbol = true))
        assertFalse(shouldFloat(candidateOnly = true))
        assertFalse(shouldFloat(fullscreen = true))
    }

    @Test
    fun rootLosesTheCandidateStripAndTheBandSitsAboveItWithAGap() {
        val g = FloatingCandidateBand.geometry(
            inputViewHeightPx = 1000, // keyboard 700 + candidates 250 + nav inset 50
            candidateStripHeightPx = 250,
            keyboardBottomMarginPx = 30,
            sideMarginPx = 12,
            gapPx = 8,
            topMarginPx = 8,
        )
        assertEquals(750, g.rootHeightPx)
        assertEquals(250, g.bandHeightPx)
        assertEquals(30 + 750 + 8, g.bandBottomMarginPx)
        assertEquals(12, g.bandSideMarginPx)
        assertEquals(16, g.extraHeightPx)
        // Old layout: root (1000) + bottom margin (30). Band layout adds only the gap and the top margin.
        assertEquals(1000 + 30 + g.extraHeightPx, g.totalHeightPx)
    }

    @Test
    fun geometryNeverGoesNegative() {
        val g = FloatingCandidateBand.geometry(
            inputViewHeightPx = 100,
            candidateStripHeightPx = 300,
            keyboardBottomMarginPx = -5,
            sideMarginPx = -1,
            gapPx = -1,
            topMarginPx = -1,
        )
        assertEquals(0, g.rootHeightPx)
        assertEquals(300, g.bandHeightPx)
        assertEquals(0, g.bandBottomMarginPx)
        assertEquals(0, g.bandSideMarginPx)
        assertEquals(0, g.extraHeightPx)
    }

    /** IMEService の組み込み（ソースの契約）: 帯は画面幅のコンテナに置き、触れる領域・内容の上端に数える。 */
    @Test
    fun imeServiceWiresTheBandIntoLayoutAndInsets() {
        val source = imeServiceSource()
        val insets = source.between("override fun onComputeInsets", "private fun registerCrossWindowBlurListener")
        assertTrue(insets.contains("floatingBandShown"))
        assertTrue(insets.contains("applyDockedCandidateInsets("))

        val layout = source.between("private fun updateKeyboardLayout(", "private fun updateNormalKeyboardBackgroundBounds(")
        assertTrue(layout.contains("FloatingCandidateBand.shouldFloat("))
        assertTrue(layout.contains("syncFloatingCandidateBand(mainView, floatCandidateBand)"))
        assertTrue(layout.contains("floatingBandGeometry?.rootHeightPx ?: inputViewHeight"))

        val params = source.between("private fun applyKeyboardLayoutParameters(", "private fun setKeyboardHeightWithAdditionalOriginal(")
        assertTrue(params.contains("mainView.suggestionViewParent.takeIf { floatingBand == null }"))
        assertTrue(params.contains("applyFloatingCandidateBandParams(mainView, it, forceLayout)"))

        val sync = source.between("private fun syncFloatingCandidateBand(", "private fun applyFloatingCandidateBandChrome(")
        // Band goes to the full-width container, and back to the keyboard root when it should not float.
        assertTrue(sync.contains("container.addView("))
        assertTrue(sync.contains("root.addView(band"))
        assertTrue(sync.contains("ViewGroup.LayoutParams.MATCH_PARENT"))

        val tabOffset = source.between("private fun applyCandidateTabSuggestionOffset(", "private fun shouldUseIndependentShortcutToolbar(")
        assertTrue(tabOffset.contains("if (isFloatingCandidateBandActive(mainView)) return"))
    }

    private fun shouldFloat(
        chip: Boolean = true,
        twoRow: Boolean = true,
        symbol: Boolean = false,
        candidateOnly: Boolean = false,
        fullscreen: Boolean = false,
    ) = FloatingCandidateBand.shouldFloat(chip, twoRow, symbol, candidateOnly, fullscreen)

    private fun imeServiceSource(): String {
        val relative = "src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"
        val file = listOf(File(relative), File("app/$relative")).first { it.exists() }
        return file.readText()
    }

    private fun String.between(start: String, end: String): String {
        val from = indexOf(start)
        require(from >= 0) { "missing $start" }
        val to = indexOf(end, from + start.length)
        require(to >= 0) { "missing $end" }
        return substring(from, to)
    }
}

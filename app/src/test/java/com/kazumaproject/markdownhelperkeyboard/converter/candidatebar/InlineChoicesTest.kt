package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineChoicesTest {

    // "わかしをかって" -> 和菓子を | 買って ; rivals 沸かしを (other segmentation of わかし), 勝手
    private val input = "わかしをかって"

    private fun seg(start: Int, end: Int, out: String) = CandidateConversionSegment(start, end, out)
    private fun cand(string: String, score: Int) =
        Candidate(string = string, type = 1, length = input.length.toUByte(), score = score)

    private val segments = mapOf(
        "和菓子を買って" to listOf(seg(0, 3, "和菓子"), seg(3, 4, "を"), seg(4, 7, "買って")),
        "沸かしを買って" to listOf(seg(0, 2, "沸か"), seg(2, 3, "し"), seg(3, 4, "を"), seg(4, 7, "買って")),
        "和菓子を勝手" to listOf(seg(0, 3, "和菓子"), seg(3, 4, "を"), seg(4, 7, "勝手")),
    )

    private val analysis = BunsetsuAnalyzer.analyze(
        input,
        listOf(cand("和菓子を買って", 4000), cand("沸かしを買って", 4300), cand("和菓子を勝手", 4500)),
        segments,
        listOf(4),
    )!!

    @Test
    fun oneBubblePerUncertainBunsetsuInSentenceOrder() {
        val plan = HomophoneChips.plan(analysis, analysis.primary)!!
        val choices = InlineChoices.of(analysis, plan)
        assertEquals(listOf(0, 1), choices.map { it.slotIndex })
        assertEquals(0 to 4, choices[0].start to choices[0].end)
        assertEquals(4 to 7, choices[1].start to choices[1].end)
        // Different internal segmentation of the same reading span is offered too.
        assertEquals(listOf("和菓子を", "沸かしを"), choices[0].chips.map { it.output })
        assertEquals(listOf("買って", "勝手"), choices[1].chips.map { it.output })
        assertTrue(choices.all { it.chips.first().isCurrent })
    }

    @Test
    fun confidentBunsetsuGetNoBubble() {
        val plan = HomophoneChips.plan(analysis, analysis.primary, threshold = 400)!!
        assertEquals(listOf(0), InlineChoices.of(analysis, plan).map { it.slotIndex })
    }

    @Test
    fun anchorIsTheFirstLineOfTheWord() {
        val boxes = mapOf(
            0 to CharBox(10f, 100f, 20f, 120f),
            1 to CharBox(20f, 98f, 30f, 120f),
            2 to CharBox(0f, 130f, 10f, 150f), // wrapped
        )
        assertEquals(10f to 98f, InlineChoices.anchorOf(0, 3) { boxes[it] })
        assertNull(InlineChoices.anchorOf(3, 5) { boxes[it] })
        // Missing first box: the first character that has bounds anchors.
        assertEquals(20f to 98f, InlineChoices.anchorOf(1, 3) { boxes[it] })
    }

    @Test
    fun bubblesOnOneLineDoNotOverlapAndStayOnScreen() {
        val positions = InlineChoices.layout(
            listOf(
                BubbleRequest(anchorX = 50f, anchorY = 200f, width = 100, height = 40),
                BubbleRequest(anchorX = 80f, anchorY = 200f, width = 100, height = 40),
            ),
            screenWidth = 1000, gap = 4, spacing = 6,
        )
        assertEquals(BubblePosition(50, 156), positions[0])
        assertEquals(BubblePosition(156, 156), positions[1])

        val clamped = InlineChoices.layout(
            listOf(
                BubbleRequest(900f, 200f, 100, 40),
                BubbleRequest(950f, 200f, 100, 40),
            ),
            screenWidth = 1000, gap = 0, spacing = 0,
        )
        assertEquals(listOf(800, 900), clamped.map { it.x })
    }

    @Test
    fun bubblesOnDifferentLinesAreIndependent() {
        val positions = InlineChoices.layout(
            listOf(
                BubbleRequest(50f, 300f, 100, 40),
                BubbleRequest(60f, 200f, 100, 40),
            ),
            screenWidth = 1000, gap = 0, spacing = 0,
        )
        assertEquals(BubblePosition(50, 260), positions[0])
        assertEquals(BubblePosition(60, 160), positions[1])
    }

    @Test
    fun segmentLengthsOnlyWhenOutputsSpellTheComposingText() {
        assertEquals(listOf(4, 3), InlineChoices.segmentLengths("和菓子を買って", listOf("和菓子を", "買って")))
        assertNull(InlineChoices.segmentLengths("和菓子を勝手", listOf("和菓子を", "買って")))
        assertNull(InlineChoices.segmentLengths("和菓子", listOf("和菓子")))
    }

    @Test
    fun boundaryEditFocusesTheSessionSegmentEndingAtTheLiveBoundary() {
        val readings = listOf("わかしを", "かって")
        assertEquals(0, InlineBoundaryEdit.focusIndexFor(readings, 4))
        assertEquals(1, InlineBoundaryEdit.focusIndexFor(readings, 7))
        // Live boundary inside a session segment: that segment.
        assertEquals(1, InlineBoundaryEdit.focusIndexFor(readings, 5))
        assertNull(InlineBoundaryEdit.focusIndexFor(readings, 0))
        assertNull(InlineBoundaryEdit.focusIndexFor(readings, 9))
    }

    @Test
    fun keptOutputsOnlyBeforeTheEditedBunsetsuAndOnlyOnMatchingRanges() {
        val readings = listOf("わかしを", "かって", "きた")
        val kept = InlineBoundaryEdit.keptOutputs(
            readings,
            slotSpans = listOf(0 to 4, 4 to 7, 7 to 9),
            slotOutputs = listOf("沸かしを", "勝手", "来た"),
            focusIndex = 2,
        )
        assertEquals(mapOf(0 to "沸かしを", 1 to "勝手"), kept)
        val mismatched = InlineBoundaryEdit.keptOutputs(
            listOf("わか", "しをかって"),
            slotSpans = listOf(0 to 4, 4 to 7),
            slotOutputs = listOf("沸かしを", "勝手"),
            focusIndex = 1,
        )
        assertTrue(mismatched.isEmpty())
    }

    @Test
    fun remainderAfterTheEditedBunsetsuIsTheOnlyPartReSplit() {
        // わかしを|かって|きた : shrink bunsetsu 1 (かって -> かっ); きた and the moved て are re-split.
        val reading = "わかしをかってきた"
        val resize = BunsetsuRangeEditor.resizeFocused(reading, listOf(0, 4, 7, 9), focusedIndex = 1, delta = -1)!!
        assertEquals(listOf(0, 4), resize.keptBoundaries)
        assertEquals(4 to 6, resize.focusedStart to resize.focusedEnd)
        assertEquals("てきた", reading.substring(resize.remainderStart))
        assertEquals(listOf(0, 4, 6, 7, 9), resize.boundariesWithRemainderSplits(listOf(1)))
        // Extending the last bunsetsu is impossible; extending the first eats one char of the next.
        assertNull(BunsetsuRangeEditor.resizeFocused(reading, listOf(0, 4, 7, 9), focusedIndex = 2, delta = 1))
        val grow = BunsetsuRangeEditor.resizeFocused(reading, listOf(0, 4, 7, 9), focusedIndex = 0, delta = 1)!!
        assertEquals(listOf(0, 5, 9), grow.boundariesWithRemainderSplits(emptyList()))
    }

    @Test
    fun tapPicksTheBunsetsuWhoseEndIsNearest() {
        val outputs = listOf("和菓子を", "買って")
        assertEquals(0, InlineBoundaryEdit.slotForTap(outputs, 2))
        assertEquals(0, InlineBoundaryEdit.slotForTap(outputs, 4))
        assertEquals(1, InlineBoundaryEdit.slotForTap(outputs, 5))
        assertNull(InlineBoundaryEdit.slotForTap(outputs, 9))
    }
}

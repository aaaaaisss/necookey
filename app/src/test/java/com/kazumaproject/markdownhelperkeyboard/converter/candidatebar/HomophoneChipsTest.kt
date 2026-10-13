package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomophoneChipsTest {

    // "かみをきる" -> 紙を | 切る
    private val input = "かみをきる"

    private fun seg(start: Int, end: Int, out: String) = CandidateConversionSegment(start, end, out)
    private fun cand(string: String, score: Int) =
        Candidate(string = string, type = 1, length = input.length.toUByte(), score = score)

    private val segments = mapOf(
        "紙を切る" to listOf(seg(0, 2, "紙"), seg(2, 3, "を"), seg(3, 5, "切る")),
        "髪を切る" to listOf(seg(0, 2, "髪"), seg(2, 3, "を"), seg(3, 5, "切る")),
        "神を切る" to listOf(seg(0, 2, "神"), seg(2, 3, "を"), seg(3, 5, "切る")),
        "紙を着る" to listOf(seg(0, 2, "紙"), seg(2, 3, "を"), seg(3, 5, "着る")),
        // Straddles the bunsetsu boundary at 3: contributes nothing to either span.
        "かみ大きる" to listOf(seg(0, 2, "かみ"), seg(2, 4, "大"), seg(4, 5, "る")),
    )

    private fun analysis(vararg scores: Pair<String, Int>) = BunsetsuAnalyzer.analyze(
        input,
        scores.map { (s, c) -> cand(s, c) },
        segments,
        listOf(3),
    )!!

    private val base = analysis(
        "紙を切る" to 4000, "髪を切る" to 4200, "神を切る" to 4600, "紙を着る" to 6000, "かみ大きる" to 4100,
    )

    @Test
    fun marginIsTheGapToTheBestDifferentSurfaceOnTheSameSpan() {
        assertEquals(200, SumireCostGapMargin.margin(base, 0)) // 髪を 4200 - 4000
        assertEquals(2000, SumireCostGapMargin.margin(base, 1)) // 着る 6000 - 4000
    }

    @Test
    fun spanWithoutRivalHasNoMarginAndIsNeverMarked() {
        val a = analysis("紙を切る" to 4000)
        assertNull(SumireCostGapMargin.margin(a, 0))
        assertTrue(HomophoneChips.uncertainSlots(a, threshold = Int.MAX_VALUE).isEmpty())
    }

    @Test
    fun uncertainSlotsAreFilteredByThresholdAndSortedByMargin() {
        assertEquals(listOf(0), HomophoneChips.uncertainSlots(base, threshold = 800))
        assertEquals(listOf(0, 1), HomophoneChips.uncertainSlots(base, threshold = 2001))
        val flipped = analysis("紙を切る" to 4000, "髪を切る" to 4900, "紙を着る" to 4100)
        assertEquals(listOf(1, 0), HomophoneChips.uncertainSlots(flipped, threshold = 1000))
    }

    @Test
    fun aRerankerMarginPlugsIn() {
        val reranker = BunsetsuMarginSource { _, slot -> if (slot == 1) 1 else 99 }
        assertEquals(listOf(1), HomophoneChips.uncertainSlots(base, threshold = 10, marginSource = reranker))
    }

    @Test
    fun diffLabelsKeepOnlyTheDifferingPart() {
        assertEquals(listOf("紙", "神", "髪"), HomophoneChips.diffLabels(listOf("紙を", "神を", "髪を")))
        assertEquals(listOf("書", "描", "か"), HomophoneChips.diffLabels(listOf("書いた", "描いた", "かいた")))
        assertEquals(listOf("制", "製"), HomophoneChips.diffLabels(listOf("制作", "製作")))
        assertEquals(listOf("only"), HomophoneChips.diffLabels(listOf("only")))
    }

    @Test
    fun diffLabelsFallBackToWholeOutputsWhenStrippingEmptiesOne() {
        // "あ" is a prefix of "あい": stripping would leave an empty label.
        assertEquals(listOf("あ", "あい"), HomophoneChips.diffLabels(listOf("あ", "あい")))
    }

    @Test
    fun diffLabelsNeverSplitASurrogatePair() {
        val a = "\uD842\uDFB7を" // 𠮷を
        val b = "\uD842\uDF9Fを" // 𠮟を (same high surrogate)
        assertEquals(listOf("\uD842\uDFB7", "\uD842\uDF9F"), HomophoneChips.diffLabels(listOf(a, b)))
    }

    @Test
    fun chipsAlignOnTheReadingSpanAndIgnoreStraddlingPaths() {
        val plan = HomophoneChips.plan(base, base.primary, threshold = 800)!!
        assertEquals(0, plan.focusSlot)
        assertEquals(listOf("紙", "髪", "神"), plan.chips.map { it.label })
        assertEquals(listOf("紙を", "髪を", "神を"), plan.chips.map { it.output })
        assertTrue(plan.chips.first().isCurrent)
        assertEquals(listOf(0 to 2), plan.markRanges)
    }

    @Test
    fun replacingOneSpanKeepsTheRest() {
        val outputs = HomophoneChips.replaced(listOf("紙を", "切る"), 0, "髪を")
        assertEquals(listOf("髪を", "切る"), outputs)
        val edited = base.primaryWithOutputs(outputs)
        assertEquals("髪を切る", edited.string)
        assertEquals(listOf("髪を", "切る"), HomophoneChips.slotOutputsOf(base, edited))
        val plan = HomophoneChips.plan(base, edited, threshold = 800)!!
        assertEquals(listOf("髪", "紙", "神"), plan.chips.map { it.label })
    }

    @Test
    fun requestedFocusIsUsedOnlyWhenMarked() {
        assertEquals(0, HomophoneChips.plan(base, base.primary, requestedFocus = 1, threshold = 800)!!.focusSlot)
        val both = HomophoneChips.plan(base, base.primary, requestedFocus = 1, threshold = 5000)!!
        assertEquals(1, both.focusSlot)
        assertEquals(listOf("切", "着"), both.chips.map { it.label })
        assertEquals(0, both.nextFocus())
        assertEquals(listOf(0 to 2, 2 to 4), both.markRanges)
    }

    @Test
    fun withNothingMarkedTheLeastSureSlotStillGetsChipsButNoMark() {
        val plan = HomophoneChips.plan(base, base.primary, threshold = 100)!!
        assertTrue(plan.uncertainSlots.isEmpty())
        assertTrue(plan.markRanges.isEmpty())
        assertEquals(0, plan.focusSlot)
        assertEquals(listOf("紙", "髪", "神"), plan.chips.map { it.label })
        assertNull(HomophoneChips.plan(analysis("紙を切る" to 4000), cand("紙を切る", 4000))!!.focusSlot)
    }

    @Test
    fun slotAtMapsSentenceOffsets() {
        val plan = HomophoneChips.plan(base, base.primary, threshold = 800)!!
        assertEquals(0, plan.slotAt(1))
        assertEquals(1, plan.slotAt(2))
        assertNull(plan.slotAt(4))
        assertNull(plan.nextFocus())
    }

    @Test
    fun unalignableShownCandidateGivesNoPlan() {
        val straddling = cand("かみ大きる", 0).copy(conversionSegments = segments.getValue("かみ大きる"))
        assertNull(HomophoneChips.plan(base, straddling))
        assertNull(HomophoneChips.plan(base, cand("紙を", 0).copy(length = 3u)))
    }
}

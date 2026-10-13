package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NecookeyCandidateBarTest {

    // "きょうははれ" -> 今日は | 晴れ (interior bunsetsu split at 4)
    private val input = "きょうははれ"

    private fun seg(start: Int, end: Int, out: String) = CandidateConversionSegment(start, end, out)

    private fun cand(string: String, score: Int, length: Int = input.length, type: Byte = 1) =
        Candidate(string = string, type = type, length = length.toUByte(), score = score)

    private val segmentsByString = mapOf(
        "今日は晴れ" to listOf(seg(0, 3, "今日"), seg(3, 4, "は"), seg(4, 6, "晴れ")),
        "京は晴れ" to listOf(seg(0, 3, "京"), seg(3, 4, "は"), seg(4, 6, "晴れ")),
        "今日は腫れ" to listOf(seg(0, 3, "今日"), seg(3, 4, "は"), seg(4, 6, "腫れ")),
        "きょうは晴れ" to listOf(seg(0, 4, "きょうは"), seg(4, 6, "晴れ")),
        "今日歯晴れ" to listOf(seg(0, 3, "今日"), seg(3, 4, "歯"), seg(4, 6, "晴れ")),
    )
    private val nBest = listOf(
        cand("今日は晴れ", 5000),
        cand("今日は腫れ", 5100),
        cand("きょうは晴れ", 5300),
        cand("京は晴れ", 9000),
        cand("今日歯晴れ", 9500),
    )
    private val splits = listOf(4)

    private fun analysis() = BunsetsuAnalyzer.analyze(input, nBest, segmentsByString, splits)!!

    // ---------------------------------------------------------------- spans / ranges

    @Test
    fun spansGroupWordsByBunsetsuSplit() {
        val spans = BunsetsuAnalyzer.spansOf(6, segmentsByString.getValue("今日は晴れ"), listOf(4))!!
        assertEquals(listOf(BunsetsuSpan(0, 4, "今日は"), BunsetsuSpan(4, 6, "晴れ")), spans)
    }

    @Test
    fun splitInsideAWordIsIgnoredAndOutOfRangeSplitsDropped() {
        val spans = BunsetsuAnalyzer.spansOf(
            6, segmentsByString.getValue("今日は晴れ"), listOf(0, 1, 4, 6, 99, 4),
        )!!
        assertEquals(listOf(0 to 4, 4 to 6), spans.map { it.start to it.end })
    }

    @Test
    fun nonContiguousSegmentsAreRejected() {
        assertNull(BunsetsuAnalyzer.spansOf(6, listOf(seg(0, 3, "a"), seg(4, 6, "b")), emptyList()))
        assertNull(BunsetsuAnalyzer.spansOf(6, listOf(seg(0, 3, "a")), emptyList()))
        assertNull(BunsetsuAnalyzer.spansOf(0, emptyList(), emptyList()))
    }

    @Test
    fun noSplitGivesOneSpan() {
        val spans = BunsetsuAnalyzer.spansOf(6, segmentsByString.getValue("今日は晴れ"), emptyList())!!
        assertEquals(listOf(BunsetsuSpan(0, 6, "今日は晴れ")), spans)
    }

    @Test
    fun outputForRangeRequiresBoundariesAtBothEnds() {
        val path = segmentsByString.getValue("今日は晴れ")
        assertEquals("今日は", BunsetsuAnalyzer.outputForRange(path, 0, 4))
        assertEquals("晴れ", BunsetsuAnalyzer.outputForRange(path, 4, 6))
        assertNull(BunsetsuAnalyzer.outputForRange(path, 0, 2)) // cuts 今日
        assertNull(BunsetsuAnalyzer.outputForRange(path, 1, 4)) // starts inside 今日
        assertEquals("京", BunsetsuAnalyzer.outputForRange(segmentsByString.getValue("京は晴れ"), 0, 3))
    }

    @Test
    fun utf16OffsetsWithSurrogatePairs() {
        // "𠮷" (U+20BB7) is two UTF-16 units; offsets must be UTF-16 based like Sumire's.
        val segments = listOf(seg(0, 2, "𠮷"), seg(2, 3, "野"))
        val spans = BunsetsuAnalyzer.spansOf(3, segments, listOf(1, 2))!!
        assertEquals(listOf(BunsetsuSpan(0, 2, "𠮷"), BunsetsuSpan(2, 3, "野")), spans)
    }

    // ---------------------------------------------------------------- analysis / gate

    @Test
    fun analysisCollectsAlternativesPerSpanInSumireOrder() {
        val a = analysis()
        assertEquals(2, a.slots.size)
        assertEquals(listOf("今日は", "きょうは", "京は", "今日歯"), a.slots[0].alternatives.map { it.output })
        assertEquals(listOf(5000, 5300, 9000, 9500), a.slots[0].alternatives.map { it.pathCost })
        assertEquals(listOf("晴れ", "腫れ"), a.slots[1].alternatives.map { it.output })
        assertEquals(4, a.firstBunsetsuEnd)
    }

    @Test
    fun analysisIsNullWithoutPrimaryPath() {
        assertNull(BunsetsuAnalyzer.analyze(input, nBest, emptyMap(), splits))
        assertNull(BunsetsuAnalyzer.analyze(input, listOf(cand("今日", 1, length = 3)), segmentsByString, splits))
    }

    @Test
    fun primaryWithOutputsRebuildsStringAndSegments() {
        val a = analysis()
        assertSame(a.primary, a.primaryWithOutputs(listOf("今日は", "晴れ")))
        val c = a.primaryWithOutputs(listOf("今日は", "腫れ"))
        assertEquals("今日は腫れ", c.string)
        assertEquals("今日は腫れ", c.commitText)
        assertEquals(6, c.length.toInt())
        assertEquals(listOf(seg(0, 4, "今日は"), seg(4, 6, "腫れ")), c.conversionSegments)
    }

    // ---------------------------------------------------------------- planner

    private val conversionList = listOf(
        cand("今日は晴れ", 5000),
        cand("今日は腫れ", 5100),
        cand("きょうは晴れ", 5300),
        cand("今日は", 3000, length = 4, type = 5),
        cand("京は", 3500, length = 4, type = 5),
        cand("今日", 2000, length = 3, type = 5),
        cand("きょうははれ", 6000, type = 3),
    )

    @Test
    fun topRowIsOnlyThePrimaryWithMarks() {
        // 晴れ/腫れ gap 100 < threshold -> marked; 今日は vs きょうは gap 300 -> marked too.
        val bar = TwoRowCandidateBarPlanner.plan(input, conversionList, analysis(), null, emptyList())
        assertEquals(listOf("今日は晴れ"), bar.topRow.map { it.string })
        assertEquals(listOf(0 to 3, 3 to 5), bar.topRow.single().uncertainRanges)
        assertTrue(bar.primary!!.uncertainRanges.isEmpty())
    }

    @Test
    fun chipsShowTheMostUncertainBunsetsuThenFocusChips() {
        val bar = TwoRowCandidateBarPlanner.plan(input, conversionList, analysis(), null, emptyList())
        // slot 1 (晴れ, gap 100) is more uncertain than slot 0 (gap 300).
        assertEquals(1, bar.homophone!!.focusSlot)
        val chips = bar.chips.filter { it.type == TwoRowCandidateBarPlanner.CANDIDATE_TYPE_HOMOPHONE_CHIP }
        assertEquals(listOf("晴", "腫"), chips.map { it.string })
        assertEquals(listOf("晴れ", "腫れ"), chips.map { it.commitText })
        assertEquals("はれ", chips[0].yomi)
        val focus = bar.chips.single { it.type == TwoRowCandidateBarPlanner.CANDIDATE_TYPE_HOMOPHONE_FOCUS }
        assertEquals(0, focus.homophoneSlot)
        assertEquals("今日は", focus.string)
        assertTrue(bar.chips.all(TwoRowCandidateBarPlanner::isHomophoneChip))
    }

    @Test
    fun focusSlotMovesTheChips() {
        val bar = TwoRowCandidateBarPlanner.plan(input, conversionList, analysis(), null, emptyList(), focusSlot = 0)
        assertEquals(0, bar.homophone!!.focusSlot)
        assertEquals(
            listOf("今日は", "きょうは", "京は", "今日歯"),
            bar.chips.filter { it.homophoneSlot == 0 && it.type == TwoRowCandidateBarPlanner.CANDIDATE_TYPE_HOMOPHONE_CHIP }
                .map { it.commitText },
        )
    }

    @Test
    fun chipEditedOverrideKeepsTheRestOfTheSentence() {
        val a = analysis()
        val override = a.primaryWithOutputs(HomophoneChips.replaced(listOf("今日は", "晴れ"), 1, "腫れ"))
        val bar = TwoRowCandidateBarPlanner.plan(input, conversionList, a, override, emptyList())
        assertEquals("今日は腫れ", bar.primary!!.string)
        val current = bar.chips.first()
        assertEquals("腫", current.string)
        assertEquals(listOf(0 to 1), current.uncertainRanges)
        assertEquals(listOf("今日は", "腫れ"), bar.homophone!!.slotOutputs)
    }

    @Test
    fun learnedCandidateFirstWinsOverOverride() {
        val learned = cand("今日葉晴れ", 100, type = 34)
        val a = analysis()
        val bar = TwoRowCandidateBarPlanner.plan(
            input, listOf(learned) + conversionList, a, a.primaryWithOutputs(listOf("今日は", "腫れ")), emptyList(),
        )
        assertEquals("今日葉晴れ", bar.primary!!.string)
        // Not alignable with the analysis primary's segments -> no homophone UI.
        assertNull(bar.homophone)
        assertTrue(bar.chips.isEmpty())
    }

    @Test
    fun predictionsAreHiddenUnlessEnabled() {
        val predictions = listOf(
            cand("今日は晴れ", 1), cand("今日は晴れです", 2, length = 6),
            cand("今日は晴れです", 4), cand("", 5), cand("京は", 6, length = 4),
        )
        val off = TwoRowCandidateBarPlanner.plan(input, conversionList, analysis(), null, predictions)
        assertTrue(off.predictions.isEmpty())
        val on = TwoRowCandidateBarPlanner.plan(input, conversionList, analysis(), null, predictions, showPredictions = true)
        assertEquals(listOf("今日は晴れです", "京は"), on.predictions.map { it.string })
        assertEquals(on.chips + on.predictions, on.bottomRow)
    }

    @Test
    fun conversionAlternativesExcludeThePrimary() {
        val bar = TwoRowCandidateBarPlanner.plan(input, conversionList, null, null, emptyList())
        assertEquals("今日は晴れ", bar.primary!!.string)
        assertFalse(bar.conversionAlternatives.any { it.string == "今日は晴れ" })
        assertNull(bar.homophone)
    }

    @Test
    fun actionCandidatesStayInTheBottomRow() {
        val macro = cand("今日は", 0, length = 4, type = 52)
        val bar = TwoRowCandidateBarPlanner.plan(
            input, listOf(macro) + conversionList, analysis(), null, listOf(macro),
            isActionCandidate = { it.type == 52.toByte() },
        )
        assertEquals("今日は晴れ", bar.primary!!.string)
        assertFalse(bar.conversionAlternatives.any { it === macro })
        assertEquals(listOf(macro), bar.predictions)
    }

    @Test
    fun emptyInputGivesEmptyBar() {
        assertEquals(TwoRowCandidateBar.EMPTY, TwoRowCandidateBarPlanner.plan("", conversionList, null, null, conversionList))
    }
}

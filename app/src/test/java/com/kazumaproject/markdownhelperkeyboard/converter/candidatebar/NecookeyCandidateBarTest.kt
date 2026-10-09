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
    fun exactSpanCandidatesFillRivalsMissingFromFullInputNBest() = runBlocking {
        val base = analysis()
        val enriched = BunsetsuAnalyzer.addSpanCandidates(
            base,
            mapOf(
                (4 to 6) to listOf(
                    cand("晴天", 4800, length = 2),
                    cand("読み長不一致", 100, length = 1),
                    cand("", 0, length = 2),
                ),
            ),
        )

        assertEquals("晴れ", enriched.slots[1].alternatives.first().output)
        assertEquals(listOf("晴れ", "腫れ", "晴天"), enriched.slots[1].alternatives.map { it.output })
        assertTrue(enriched.slots[1].isAmbiguous(1000.0))

        val scorer = RecordingScorer { options -> FloatArray(options.size) { i -> if (options[i] == "晴天") 0f else -1f } }
        val result = ZenzBunsetsuReselector(
            NecookeyCandidateBarConfig(gateNormalizedGapThreshold = 1000.0),
        ).reselect(enriched, "", "", scorer)
        assertEquals("晴天", result.outputs[1])
        assertEquals(listOf(0, 1), result.scoredSlots)
    }

    @Test
    fun spanCandidateMergeKeepsPrimaryFirstAndUsesLowestDuplicateCost() {
        val enriched = BunsetsuAnalyzer.addSpanCandidates(
            analysis(),
            mapOf((4 to 6) to listOf(cand("腫れ", 4700, length = 2), cand("新候補", 4000, length = 2))),
        )

        assertEquals(listOf("晴れ", "腫れ", "新候補"), enriched.slots[1].alternatives.map { it.output })
        assertEquals(listOf(5000, 4700, 4000), enriched.slots[1].alternatives.map { it.pathCost })
    }

    @Test
    fun gateNormalizesGapByReadingLength() {
        val a = analysis()
        assertEquals(300.0 / 4, a.slots[0].normalizedCostGap()!!, 1e-9)
        assertEquals(100.0 / 2, a.slots[1].normalizedCostGap()!!, 1e-9)
        assertTrue(a.slots[0].isAmbiguous(100.0))
        assertFalse(a.slots[0].isAmbiguous(75.0)) // strictly below threshold only
        assertTrue(a.slots[1].isAmbiguous(75.0))
    }

    @Test
    fun slotWithoutRivalIsNeverAmbiguous() {
        val slot = BunsetsuSlot(BunsetsuSpan(0, 2, "晴れ"), listOf(BunsetsuAlternative("晴れ", 10)))
        assertNull(slot.normalizedCostGap())
        assertFalse(slot.isAmbiguous(Double.MAX_VALUE))
    }

    @Test
    fun negativeGapCountsAsAmbiguous() {
        val slot = BunsetsuSlot(
            BunsetsuSpan(0, 2, "晴れ"),
            listOf(BunsetsuAlternative("晴れ", 100), BunsetsuAlternative("腫れ", 50)),
        )
        assertTrue(slot.isAmbiguous(0.0))
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

    // ---------------------------------------------------------------- zenz re-selection

    private class RecordingScorer(val answer: (List<String>) -> FloatArray?) : ZenzSpanScorer {
        val calls = mutableListOf<List<String>>()
        override suspend fun score(
            leftContext: String,
            rightContext: String,
            readingKatakana: String,
            candidates: List<String>,
        ): FloatArray? {
            calls += listOf(leftContext, rightContext, readingKatakana) + candidates
            return answer(candidates)
        }
    }

    @Test
    fun onlyAmbiguousBunsetsuAreScoredAndContextCarriesChosenOutputs() = runBlocking {
        val config = NecookeyCandidateBarConfig(gateNormalizedGapThreshold = 80.0, zenzTopK = 4)
        val scorer = RecordingScorer { options ->
            FloatArray(options.size) { i -> if (options[i] == "きょうは" || options[i] == "腫れ") -0.1f else -2f }
        }
        val result = ZenzBunsetsuReselector(config).reselect(analysis(), "昨日は雨。", "", scorer)
        assertEquals(listOf("きょうは", "腫れ"), result.outputs)
        assertEquals(listOf(0, 1), result.changedSlots)
        assertEquals(2, scorer.calls.size)
        assertEquals(listOf("昨日は雨。", "晴れ", "キョウハ", "今日は", "きょうは", "京は", "今日歯"), scorer.calls[0])
        // the second bunsetsu sees the zenz-chosen first bunsetsu as left context
        assertEquals(listOf("昨日は雨。きょうは", "", "ハレ", "晴れ", "腫れ"), scorer.calls[1])
    }

    @Test
    fun confidentBunsetsuIsNotScored() = runBlocking {
        val config = NecookeyCandidateBarConfig(gateNormalizedGapThreshold = 60.0)
        val scorer = RecordingScorer { FloatArray(it.size) { 0f } }
        val result = ZenzBunsetsuReselector(config).reselect(analysis(), "", "", scorer)
        assertEquals(listOf(1), result.scoredSlots) // only the 50/char bunsetsu
        assertEquals(listOf("今日は", "晴れ"), result.outputs) // tie keeps Sumire
        assertTrue(result.changedSlots.isEmpty())
    }

    @Test
    fun topKLimitsOptionsAndMaxBunsetsuLimitsCalls() = runBlocking {
        val config = NecookeyCandidateBarConfig(
            gateNormalizedGapThreshold = 1000.0, zenzTopK = 2, maxZenzBunsetsuPerRequest = 1,
        )
        val scorer = RecordingScorer { FloatArray(it.size) { 0f } }
        ZenzBunsetsuReselector(config).reselect(analysis(), "", "", scorer)
        assertEquals(1, scorer.calls.size)
        assertEquals(listOf("今日は", "きょうは"), scorer.calls[0].drop(3))
    }

    @Test
    fun longInputSkipsZenz() = runBlocking {
        val config = NecookeyCandidateBarConfig(gateNormalizedGapThreshold = 1000.0, maxZenzInputLength = 3)
        val scorer = RecordingScorer { FloatArray(it.size) { 0f } }
        val r = ZenzBunsetsuReselector(config).reselect(analysis(), "", "", scorer)
        assertTrue(scorer.calls.isEmpty())
        assertEquals(listOf("今日は", "晴れ"), r.outputs)
    }

    @Test
    fun contextsAreTruncated() = runBlocking {
        val config = NecookeyCandidateBarConfig(
            gateNormalizedGapThreshold = 1000.0, maxLeftContextChars = 3, maxRightContextChars = 4,
            maxZenzBunsetsuPerRequest = 1,
        )
        val scorer = RecordingScorer { FloatArray(it.size) { 0f } }
        ZenzBunsetsuReselector(config).reselect(analysis(), "あいうえお", "かきくけこ", scorer)
        assertEquals("うえお", scorer.calls[0][0])
        assertEquals("晴れかき", scorer.calls[0][1]) // following bunsetsu is nearest; then editor text
    }

    @Test
    fun zeroRightContextLimitSuppressesFollowingAndEditorText() = runBlocking {
        val config = NecookeyCandidateBarConfig(
            gateNormalizedGapThreshold = 1000.0,
            maxRightContextChars = 0,
            maxZenzBunsetsuPerRequest = 1,
        )
        val scorer = RecordingScorer { FloatArray(it.size) { 0f } }
        ZenzBunsetsuReselector(config).reselect(analysis(), "", "外側", scorer)
        assertEquals("", scorer.calls.first()[1])
    }

    @Test
    fun nullScorerResultKeepsSumire() = runBlocking {
        val config = NecookeyCandidateBarConfig(gateNormalizedGapThreshold = 1000.0)
        val r = ZenzBunsetsuReselector(config).reselect(analysis(), "", "", ZenzSpanScorer { _, _, _, _ -> null })
        assertEquals(listOf("今日は", "晴れ"), r.outputs)
        assertTrue(r.changedSlots.isEmpty())
    }

    @Test
    fun badScoresKeepSumire() {
        val options = listOf("a", "b", "c")
        assertNull(ZenzBunsetsuReselector.pickBest(options, null, 0f))
        assertNull(ZenzBunsetsuReselector.pickBest(options, floatArrayOf(1f, 2f), 0f))
        assertNull(ZenzBunsetsuReselector.pickBest(options, floatArrayOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.NaN), 0f))
        assertEquals("a", ZenzBunsetsuReselector.pickBest(options, floatArrayOf(-1f, -1f, -1f), 0f))
        assertEquals("b", ZenzBunsetsuReselector.pickBest(options, floatArrayOf(-1f, -0.5f, -0.7f), 0f))
        assertEquals("a", ZenzBunsetsuReselector.pickBest(options, floatArrayOf(-1f, -0.5f, -0.7f), 0.6f))
        // Sumire's own option unscorable (e.g. a protocol marker): the best finite one wins
        assertEquals("c", ZenzBunsetsuReselector.pickBest(options, floatArrayOf(Float.NEGATIVE_INFINITY, -3f, -2f), 10f))
    }

    @Test
    fun cancellationPropagates() {
        runBlocking {
            val config = NecookeyCandidateBarConfig(gateNormalizedGapThreshold = 1000.0)
            var calls = 0
            val job = launch {
                val scorer = ZenzSpanScorer { _, _, _, c ->
                    calls++
                    currentCoroutineContext()[Job]!!.cancel()
                    FloatArray(c.size) { 0f }
                }
                ZenzBunsetsuReselector(config).reselect(analysis(), "", "", scorer)
                fail("expected cancellation")
            }
            job.join()
            assertTrue(job.isCancelled)
            assertEquals(1, calls) // the second bunsetsu was never scored
        }
    }

    @Test
    fun katakanaConversion() {
        assertEquals("キョウハ", "きょうは".hiraganaToKatakanaForZenz())
        assertEquals("ヴヽヾ", "ゔゝゞ".hiraganaToKatakanaForZenz())
        assertEquals("ABC漢字ー、", "ABC漢字ー、".hiraganaToKatakanaForZenz())
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
    fun topRowIsPrimaryThenFirstBunsetsuAlternatives() {
        val bar = TwoRowCandidateBarPlanner.plan(input, conversionList, analysis(), null, emptyList())
        assertEquals("今日は晴れ", bar.primary!!.string)
        assertEquals(listOf("今日は", "きょうは", "京は", "今日歯"), bar.firstBunsetsuAlternatives.map { it.string })
        assertTrue(bar.firstBunsetsuAlternatives.all { it.length.toInt() == 4 })
        assertEquals("きょうは", bar.firstBunsetsuAlternatives[1].yomi)
    }

    @Test
    fun zenzOverrideReplacesLeftmostAndKeepsEnginePrimaryReachable() {
        val a = analysis()
        val override = a.primaryWithOutputs(listOf("今日は", "腫れ")).copy(zenzAdjusted = true)
        val bar = TwoRowCandidateBarPlanner.plan(input, conversionList, a, override, emptyList())
        assertEquals("今日は腫れ", bar.primary!!.string)
        assertTrue(bar.primary!!.zenzAdjusted)
        assertEquals("今日は晴れ", bar.firstBunsetsuAlternatives[0].string)
        assertFalse(bar.firstBunsetsuAlternatives[0].zenzAdjusted)
        assertEquals(listOf("今日は", "きょうは", "京は", "今日歯"), bar.firstBunsetsuAlternatives.drop(1).map { it.string })
    }

    @Test
    fun learnedCandidateFirstWinsOverZenz() {
        val learned = cand("今日葉晴れ", 100, type = 34)
        val a = analysis()
        val bar = TwoRowCandidateBarPlanner.plan(
            input, listOf(learned) + conversionList, a, a.primaryWithOutputs(listOf("今日は", "腫れ")), emptyList(),
        )
        assertEquals("今日葉晴れ", bar.primary!!.string)
    }

    @Test
    fun bottomRowDedupesAgainstVisibleTopRowAndItself() {
        val predictions = listOf(
            cand("今日は晴れ", 1), cand("今日は晴れです", 2, length = 6), cand("京は", 3, length = 4),
            cand("今日は晴れです", 4), cand("", 5), cand("今日は腫れ", 6),
        )
        val bar = TwoRowCandidateBarPlanner.plan(input, conversionList, analysis(), null, predictions)
        assertEquals(listOf("今日は晴れです", "今日は腫れ"), bar.predictions.map { it.string })
    }

    @Test
    fun truncatedTopRowItemsMayAppearInBottomRow() {
        val config = NecookeyCandidateBarConfig(maxTopRowCandidates = 2)
        val predictions = listOf(cand("京は", 3, length = 4))
        val bar = TwoRowCandidateBarPlanner.plan(input, conversionList, analysis(), null, predictions, config)
        assertEquals(listOf("今日は"), bar.firstBunsetsuAlternatives.map { it.string })
        assertEquals(listOf("京は"), bar.predictions.map { it.string })
    }

    @Test
    fun singleBunsetsuUsesWholeReadingAlternatives() {
        val single = BunsetsuAnalyzer.analyze(input, nBest, segmentsByString, emptyList())!!
        val bar = TwoRowCandidateBarPlanner.plan(input, conversionList, single, null, emptyList())
        assertEquals(listOf("今日は腫れ", "きょうは晴れ", "きょうははれ"), bar.firstBunsetsuAlternatives.map { it.string })
    }

    @Test
    fun withoutAnalysisTheBarStillWorks() {
        val bar = TwoRowCandidateBarPlanner.plan(input, conversionList, null, null, listOf(cand("今日は晴れです", 1)))
        assertEquals("今日は晴れ", bar.primary!!.string)
        assertEquals(listOf("今日は腫れ", "きょうは晴れ", "きょうははれ"), bar.firstBunsetsuAlternatives.map { it.string })
        assertEquals(listOf("今日は晴れです"), bar.predictions.map { it.string })
    }

    @Test
    fun actionCandidatesAreKeptButNeverAlternatives() {
        val macro = cand("今日は", 0, length = 4, type = 52)
        val bar = TwoRowCandidateBarPlanner.plan(
            input, listOf(macro) + conversionList, analysis(), null, listOf(macro),
            isActionCandidate = { it.type == 52.toByte() },
        )
        assertEquals("今日は晴れ", bar.primary!!.string)
        assertFalse(bar.firstBunsetsuAlternatives.any { it === macro })
        assertEquals(listOf(macro), bar.predictions)
    }

    @Test
    fun emptyInputGivesEmptyBar() {
        assertEquals(TwoRowCandidateBar.EMPTY, TwoRowCandidateBarPlanner.plan("", conversionList, null, null, conversionList))
    }
}

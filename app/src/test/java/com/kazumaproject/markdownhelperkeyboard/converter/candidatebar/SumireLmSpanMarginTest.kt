package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SumireLmSpanMarginTest {
    private fun c(s: String) = Candidate(string = s, type = 1, length = 6.toUByte(), score = 0)
    private fun seg(a: Int, b: Int, o: String) = CandidateConversionSegment(a, b, o)

    // 読み「かみをきる」(0..5): かみ|を|きる
    private val segs = mapOf(
        "紙を切る" to listOf(seg(0, 2, "紙"), seg(2, 3, "を"), seg(3, 5, "切る")),
        "髪を切る" to listOf(seg(0, 2, "髪"), seg(2, 3, "を"), seg(3, 5, "切る")),
        "紙を着る" to listOf(seg(0, 2, "紙"), seg(2, 3, "を"), seg(3, 5, "着る")),
        "紙尾切る" to listOf(seg(0, 3, "紙尾"), seg(3, 5, "切る")),
    )
    private val decision = SumireLmGate.Decision(
        top = c("紙を切る"), margin = 0.3,
        ranked = listOf(c("紙を切る"), c("紙尾切る"), c("紙を着る"), c("髪を切る")),
        scores = listOf(2.0, 1.9, 1.5, 0.7),
    )
    private val segmentsOf: (Candidate) -> List<CandidateConversionSegment> = { segs.getValue(it.string) }

    @Test
    fun outputForRangeRequiresAlignedEdges() {
        val s = segs.getValue("紙を切る")
        assertEquals("紙を", SumireLmSpanMargin.outputForRange(s, 0, 3))
        assertEquals("切る", SumireLmSpanMargin.outputForRange(s, 3, 5))
        assertNull(SumireLmSpanMargin.outputForRange(s, 1, 3))
        assertNull(SumireLmSpanMargin.outputForRange(segs.getValue("紙尾切る"), 0, 2))
    }

    @Test
    fun marginIsGapToBestRivalSurface() {
        // かみ: 紙尾切る は区間に乗らないので数えない、紙を着る は同じ「紙」、最初の対抗は 髪を切る
        assertEquals(1.3, SumireLmSpanMargin.margin(decision, segmentsOf, 0, 2)!!, 1e-9)
        // きる: 最初の対抗は 紙を着る
        assertEquals(0.5, SumireLmSpanMargin.margin(decision, segmentsOf, 3, 5)!!, 1e-9)
        // を: 対抗なし
        assertNull(SumireLmSpanMargin.margin(decision, segmentsOf, 2, 3))
        assertEquals(130, SumireLmSpanMargin.toCents(1.3))
        assertNull(SumireLmSpanMargin.toCents(null))
    }

    @Test
    fun noScoresMeansNoMargin() {
        val d = SumireLmGate.Decision(top = decision.top, margin = null, ranked = decision.ranked)
        assertNull(SumireLmSpanMargin.margin(d, segmentsOf, 0, 2))
    }
}

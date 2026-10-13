package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import kotlin.math.roundToInt

/**
 * 文節ごとの迷い度をリランカーのスコアで測る（同音異義 UI の「迷い印」用、既定オフ:
 * AppPreference.sumire_lm_homophone_margin_preference）。
 *
 * margin(span) = score(リランカー 1 位) − max{ score(c) : c の span の出力が 1 位と違う }。
 * Sumire の経路コスト差（homophone-ui の SumireCostGapMargin）と違い、左文脈つきの LM・PMI を含むので、
 * 「Sumire では僅差だが LM がはっきり選べる」文節に印が付かない（誤発火を減らす狙い）。
 * 単位は線形モデルのスコア。[toCents] で百分の一単位の Int にすると、zenz に回すしきい値（SeekBar 0–300）と同じ尺度になる。
 *
 * homophone-ui ブランチの `fun interface BunsetsuMarginSource { fun margin(analysis: BunsetsuAnalysis, slotIndex: Int): Int? }`
 * への差し込み（両ブランチを合わせたときに使う。ここではそのブランチの型に依存しない）:
 * ```
 * class SumireLmMarginSource(private val decision: SumireLmGate.Decision, private val segmentsOf: (Candidate) -> List<CandidateConversionSegment>)
 *     : BunsetsuMarginSource {
 *     override fun margin(analysis: BunsetsuAnalysis, slotIndex: Int): Int? {
 *         val span = analysis.slots.getOrNull(slotIndex)?.span ?: return null
 *         return SumireLmSpanMargin.toCents(SumireLmSpanMargin.margin(decision, segmentsOf, span.start, span.end))
 *     }
 * }
 * // HomophoneChips.plan(..., threshold = SumireLmSpanMargin.DEFAULT_THRESHOLD_CENTS, marginSource = SumireLmMarginSource(d, segs))
 * ```
 */
object SumireLmSpanMargin {
    /**
     * 暫定しきい値（百分の一単位 = マージン 0.50）。未較正: daily.tsv（リポジトリ外）で SumireCostGapMargin の 800 と
     * 同じ基準（1 文節直しで正解になる誤り文節の何割に印が付くか / 正解文の文節の何割に付くか）で決め直すこと。
     */
    const val DEFAULT_THRESHOLD_CENTS = 50

    /** [segments] の [start, end) の出力（区間の端が語の境界に合わなければ null）。 */
    fun outputForRange(segments: List<CandidateConversionSegment>, start: Int, end: Int): String? {
        if (segments.isEmpty() || start >= end) return null
        val sb = StringBuilder()
        var startOk = false
        var endOk = false
        for (s in segments) {
            if (s.inputStart == start) startOk = true
            if (s.inputEnd <= start || s.inputStart >= end) continue
            if (s.inputStart < start || s.inputEnd > end) return null  // 語が区間の端をまたぐ
            sb.append(s.output)
            if (s.inputEnd == end) endOk = true
        }
        return if (startOk && endOk) sb.toString() else null
    }

    /**
     * リランカー 1 位の [start, end) の出力と違う出力を持つ候補のうち最良のものとのスコア差（>= 0）。
     * 対抗がない・1 位がその区間に乗らない・スコアがないときは null（印を付けない）。
     * 区間に乗らない（語が端をまたぐ）候補は対抗に数えない。
     */
    fun margin(
        decision: SumireLmGate.Decision,
        segmentsOf: (Candidate) -> List<CandidateConversionSegment>,
        start: Int,
        end: Int,
    ): Double? {
        val ranked = decision.ranked
        val scores = decision.scores
        if (ranked.size < 2 || scores.size != ranked.size) return null
        val topOut = outputForRange(segmentsOf(ranked[0]), start, end) ?: return null
        for (i in 1 until ranked.size) {
            val out = outputForRange(segmentsOf(ranked[i]), start, end) ?: continue
            if (out != topOut) return scores[0] - scores[i]  // ranked はスコア降順なので最初の対抗が最良
        }
        return null
    }

    fun toCents(margin: Double?): Int? = margin?.let { (it * 100).roundToInt() }
}

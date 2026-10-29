package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearningScorePolicy

/** 学習辞書の 1 行（読み・表記・文節をまとめた句か）。 */
data class LearnedPiece(val reading: String, val output: String, val isPhrase: Boolean)

/** チップの元になる語分割と文節区切り（候補の表記 → …）。 */
data class ChipSegmentation(
    val segmentsByString: Map<String, List<CandidateConversionSegment>>,
    val splitPatternByCandidateString: Map<String, List<Int>>,
)

/**
 * 学習した句（チップで直して確定した文全体など、複数文節を 1 語として覚えた行）がラティスで 1 語として選ばれると、
 * 語分割が 1 語になりチップも 1 つになる（区切りが再現されない）。句の読み・表記を、同じく覚えている文節（句でない行）の
 * 並びに割り直し、その境目を文節区切りに足す。docs/LEARNING.md「チップ候補欄の学習」。
 */
object LearnedChipSplit {

    /** 読みの各位置から始まる学習語（忘れたものを除く）。 */
    suspend fun piecesFrom(
        reading: String,
        now: Long,
        lookup: suspend (String) -> List<LearnEntity>,
    ): Map<Int, List<LearnedPiece>> {
        val out = HashMap<Int, List<LearnedPiece>>()
        for (i in reading.indices) {
            val found = lookup(reading.substring(i)).filter {
                LearningScorePolicy.conversionCost(it.score, it.usageCount, it.lastUsedAt, it.input.length, now) != null
            }.map { LearnedPiece(it.input, it.out, it.isPhrase) }
            if (found.isNotEmpty()) out[i] = found
        }
        return out
    }

    /**
     * 読み全体を覆う候補ごとに、語分割の中の「学習した句」を文節に割り直す。割れた候補は語分割と文節区切りを差し替える
     * （区切り = 元の文節区切り（無ければ自立語の頭）＋割った境目）。学習語が無ければ [segmentation] をそのまま返す。
     */
    fun patch(
        reading: String,
        candidates: List<Candidate>,
        segmentation: ChipSegmentation,
        piecesAt: Map<Int, List<LearnedPiece>>,
    ): ChipSegmentation {
        if (piecesAt.isEmpty()) return segmentation
        var segs: MutableMap<String, List<CandidateConversionSegment>>? = null
        var splits: MutableMap<String, List<Int>>? = null
        val seen = HashSet<String>()
        for (c in candidates) {
            if (c.length.toInt() != reading.length || c.string.isEmpty() || !seen.add(c.string)) continue
            val original = segmentation.segmentsByString[c.string].orEmpty().ifEmpty { c.conversionSegments }
            val (refined, added) = refine(reading, original, piecesAt) ?: continue
            val base = segmentation.splitPatternByCandidateString[c.string] ?: ChipBarPresenter.contentSplits(original)
            if (segs == null) {
                segs = LinkedHashMap(segmentation.segmentsByString)
                splits = LinkedHashMap(segmentation.splitPatternByCandidateString)
            }
            segs[c.string] = refined
            splits!![c.string] = (base + added).filter { it in 1 until reading.length }.distinct().sorted()
        }
        return if (segs == null) segmentation else ChipSegmentation(segs, splits!!)
    }

    /** 語分割の中の学習した句を割る。割ったら（新しい語分割, 割った境目）、無ければ null。 */
    fun refine(
        reading: String,
        segments: List<CandidateConversionSegment>,
        piecesAt: Map<Int, List<LearnedPiece>>,
    ): Pair<List<CandidateConversionSegment>, List<Int>>? {
        if (segments.isEmpty()) return null
        val out = ArrayList<CandidateConversionSegment>(segments.size)
        val added = ArrayList<Int>()
        for (seg in segments) {
            if (seg.inputStart < 0 || seg.inputEnd > reading.length || seg.inputStart >= seg.inputEnd) return null
            val r = reading.substring(seg.inputStart, seg.inputEnd)
            val learnedPhrase = piecesAt[seg.inputStart].orEmpty().any { it.isPhrase && it.reading == r && it.output == seg.output }
            val split = if (learnedPhrase) decompose(reading, seg, piecesAt) else null
            if (split == null) {
                out += seg
            } else {
                out += split
                split.drop(1).forEach { added += it.inputStart }
            }
        }
        return if (added.isEmpty()) null else out to added
    }

    /** 句 [seg] を句でない学習語の並び（2 つ以上、いちばん少ない数）に割る。割れなければ null。 */
    private fun decompose(
        reading: String,
        seg: CandidateConversionSegment,
        piecesAt: Map<Int, List<LearnedPiece>>,
    ): List<CandidateConversionSegment>? {
        val start = seg.inputStart
        val end = seg.inputEnd
        val output = seg.output
        // 幅優先: 状態 = (読みの位置, 表記の位置)。最初に終点に着いたものが最少の数。
        data class State(val pos: Int, val out: Int)
        val prev = HashMap<State, Pair<State, CandidateConversionSegment>>()
        val startState = State(start, 0)
        var frontier = listOf(startState)
        val visited = hashSetOf(startState)
        while (frontier.isNotEmpty()) {
            val next = ArrayList<State>()
            for (s in frontier) {
                for (p in piecesAt[s.pos].orEmpty()) {
                    if (p.isPhrase || p.reading.isEmpty() || p.output.isEmpty()) continue
                    val pe = s.pos + p.reading.length
                    if (pe > end || !reading.startsWith(p.reading, s.pos) || !output.startsWith(p.output, s.out)) continue
                    if (s.pos == start && pe == end) continue // 句そのもの（1 つ）は割ったことにならない
                    val t = State(pe, s.out + p.output.length)
                    if (!visited.add(t)) continue
                    prev[t] = s to CandidateConversionSegment(s.pos, pe, p.output)
                    if (t.pos == end && t.out == output.length) {
                        val path = ArrayList<CandidateConversionSegment>()
                        var cur = t
                        while (cur != startState) {
                            val (from, piece) = prev.getValue(cur)
                            path += piece
                            cur = from
                        }
                        return path.asReversed()
                    }
                    next += t
                }
            }
            frontier = next
        }
        return null
    }
}

package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.sumirelm.SumireLmReranker

/**
 * 変換エンジン「sumire_lm」: Sumire + SUMIRE-LM リランカーが基本、リランカーが迷った読みだけ zenz xsmall で文脈判断。
 *
 * 1. Sumire の全読み候補（文節分離の N-best、最大 [POOL_SIZE]）をリランカーで採点して 1 位を決める。
 * 2. 1 位と 2 位のスコア差（マージン、線形モデルの単位）が [needsZenz] のしきい値未満なら zenz（Zenzai の
 *    評価・FIX:prefix 制約ループ）にリランカー 1 位をドラフトとして渡し、迷った範囲の語選びを任せる。
 *    しきい値以上なら zenz を呼ばない（ZENZ-GATE の Sumire 経路コスト差 1448 の代わりにこのマージンで判定する）。
 * 3. 表（リランカー）が無ければ Sumire の順のまま、zenz モデルが無ければリランカーだけで動く。
 *
 * しきい値の暫定値 0.5: od5 表（5MB 設定）で JWTD held を採点したとき、マージン < 0.5 の文は 38%、その中の
 * リランカー正解率 .21（それ以外 .45）、リランカー誤りの 47% を含む（tools/sumire_lm/HANDOFF.md）。AJIMEE は不使用。
 */
object SumireLmGate {
    const val POOL_SIZE = 50
    /** 設定値（百分の一単位）の既定 = マージン 0.50。 */
    const val DEFAULT_MARGIN_THRESHOLD_CENTS = 50

    class Decision(
        /** リランカーの 1 位（Sumire の候補そのもの）。 */
        val top: Candidate,
        /** 1 位と 2 位のスコア差。候補が 1 つなら null。 */
        val margin: Double?,
        /** 並べ替えた全読み候補（スコア降順）。 */
        val ranked: List<Candidate>,
        /** [ranked] と同じ順のリランカースコア（線形モデルの単位、大きいほど良い）。 */
        val scores: List<Double> = emptyList(),
    )

    /**
     * [candidates] のうち読み全体を覆う候補（重複なし、先頭から [POOL_SIZE] 件、Sumire の順 = rank）を採点する。
     * 全読み候補が無い・ユーザー辞書の候補が先頭（守る）なら null。
     */
    fun rerank(
        reranker: SumireLmReranker,
        input: String,
        leftContext: String,
        candidates: List<Candidate>,
        segmentsOf: (Candidate) -> List<CandidateConversionSegment>,
        isExcluded: (Candidate) -> Boolean = { false },
    ): Decision? {
        val seen = HashSet<String>()
        val pool = candidates.asSequence()
            .filter { it.length.toInt() == input.length && !isExcluded(it) && seen.add(it.string) }
            .take(POOL_SIZE)
            .toList()
        if (pool.isEmpty() || ZenzaiConstraint.isProtectedCandidateType(pool[0].type)) return null
        val inputs = pool.mapIndexed { rank, c ->
            val segs = segmentsOf(c)
            SumireLmReranker.Input(
                surface = c.string,
                cost = c.score,
                rank = rank,
                nseg = segs.size,
                type = c.type.toInt(),
                tokens = segs.map { it.output },
            )
        }
        val scores = reranker.score(leftContext, inputs)
        // 同点は Sumire の順を保つ。
        val order = pool.indices.sortedWith(compareByDescending<Int> { scores[it] }.thenBy { it })
        val margin = if (order.size > 1) scores[order[0]] - scores[order[1]] else null
        return Decision(top = pool[order[0]], margin = margin, ranked = order.map { pool[it] }, scores = order.map { scores[it] })
    }

    /** zenz に回すか。マージンが取れない（候補 1 つ・リランカーなし）ときは回さない。 */
    fun needsZenz(margin: Double?, thresholdCents: Int): Boolean =
        margin != null && margin < thresholdCents / 100.0
}

package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.sumirelm.CandidateReranker
import com.kazumaproject.markdownhelperkeyboard.converter.sumirelm.SumireLmReranker

/**
 * 変換エンジン「sumire_lm」: Sumire の全読み候補（文節分離の N-best、最大 [POOL_SIZE]）をリランカー
 * （[CandidateReranker]）で採点して 1 位を決める。表（リランカー）が無ければ Sumire の順のまま。
 * zenz は削除済み。マージン（1 位と 2 位のスコア差）は表示・ログ用に返すだけで、追加の判定はしない。
 * [DEFAULT_MARGIN_THRESHOLD_CENTS] は旧 zenz ゲートの設定値の既定（設定キー互換のため残す）。
 * 差し替え手順は docs/RERANKER_HOOKUP.md。
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
     * 全読み候補が無い・ユーザー辞書か学習辞書の候補が先頭（守る）なら null。
     */
    fun rerank(
        reranker: CandidateReranker,
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
        // ユーザー辞書・学習辞書の語を含む経路が先頭なら守る（学習はラティスの中で効くので、学習した文は Sumire 自身の
        // 1 位になる。ここで差し替えると、直した変換が次も LM の好みに戻されて学習が効かない）。
        if (pool.isEmpty() || pool[0].type == CANDIDATE_TYPE_USER_DICTIONARY || pool[0].type == CANDIDATE_TYPE_LEARNED_DICTIONARY) return null
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

}

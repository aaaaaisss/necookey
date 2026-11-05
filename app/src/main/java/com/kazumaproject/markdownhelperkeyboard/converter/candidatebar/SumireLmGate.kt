package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.graph.ElongationNodes
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
        contextBonus: ScreenContextBonus? = null,
    ): Decision? {
        val seen = HashSet<String>()
        val pool = candidates.asSequence()
            .filter { it.length.toInt() == input.length && isRerankable(it) && !isExcluded(it) && seen.add(it.string) }
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
                readings = segs.map { if (it.inputStart in 0..it.inputEnd && it.inputEnd <= input.length) input.substring(it.inputStart, it.inputEnd) else "" },
            )
        }
        val scores = reranker.score(leftContext, inputs)
        // 画面文脈（入力欄の前後・最近の確定・任意のユーザー補助）にある語への小さな加点。上位・僅差の候補だけ（ScreenContextBonus）。
        var bonusApplied = false
        var topWithoutBonus = -1
        contextBonus?.let { bonus ->
            val add = bonus.bonuses(inputs.map { it.tokens }, scores)
            topWithoutBonus = pool.indices.maxWithOrNull(compareBy<Int> { scores[it] }.thenByDescending { it }) ?: -1
            for (k in scores.indices) scores[k] += add[k]
            bonusApplied = add.any { it > 0.0 }
        }
        // 同点は Sumire の順を保つ。
        var order = pool.indices.sortedWith(compareByDescending<Int> { scores[it] }.thenBy { it })
        if (blocksRareSingleKanji(input, pool, order[0])) {
            // 短い読み（2〜3 字）を、Sumire が 1 位にしていない 1 字漢字（おけ→岡、くさ→日 のような稀な読み）に替えない。
            order = listOf(0) + order.filter { it != 0 }
        }
        keepHiraganaOverKatakanaSwap(pool, order, scores)?.let { order = it }
        keepTrailingElongation(input, pool, order)?.let { order = it }
        // 確認用の数（メモリだけ）: 加点が付いたか・加点で 1 位が変わったか（設定「画面の文脈」の状態表示）
        val withBonusTop = if (bonusApplied) pool.indices.sortedWith(compareByDescending<Int> { scores[it] }.thenBy { it }).first() else -1
        ScreenContextStore.noteConversion(input, bonusApplied, bonusApplied && topWithoutBonus >= 0 && withBonusTop != topWithoutBonus)
        val margin = if (order.size > 1) scores[order[0]] - scores[order[1]] else null
        return Decision(top = pool[order[0]], margin = margin, ranked = order.map { pool[it] }, scores = order.map { scores[it] })
    }

    /**
     * リランカーで採点してよい候補か。表は Sumire のラティス経路（N-best）だけで学習していて、コストは経路コスト。
     * かなそのまま・カタカナ（種別 3/4、コスト 6000 固定）・半角カタカナ（31）・絵文字/顔文字/記号（11/12/13/21）・
     * 単漢字辞書（7）・読み一致の単語（2）・読み誤り補正（15）・UT 辞書などは経路コストと尺度が違う（ずっと小さい）ので、
     * dcost の重みで大きく持ち上がって 1 位になっていた（端末報告: 半角カナ・🈚・岡 が先頭）。これらは採点せず Sumire の並びのまま。
     * 経路由来の種別: 1（通常）、28/34（ユーザー辞書・学習を含む経路）、30/31（全角/半角の数字記号だけの経路）。
     */
    fun isRerankable(c: Candidate): Boolean {
        val t = c.type.toInt()
        if (t != 1 && t != 28 && t != 34 && t != 30 && t != 31) return false
        return c.string.none { it in '\uFF61'..'\uFF9F' }
    }

    /**
     * 語末の母音の伸ばし（よくないのおお・そうなのおお）をかなのまま残す。表は伸ばしの母音を語として学習しておらず
     * （語彙外）、の＋大・の＋王 のような漢字 1 字の名詞を選んでいた（端末報告）。リランカー 1 位が伸ばしを崩していて、
     * プールに伸ばしをかなで残す候補があれば、その中でスコアが一番高いものを 1 位にする（残りの順はそのまま）。
     * 変えないなら null。
     */
    internal fun keepTrailingElongation(input: String, pool: List<Candidate>, order: List<Int>): List<Int>? {
        if (order.isEmpty()) return null
        val run = ElongationNodes.trailingRun(input) ?: return null
        if (pool[order[0]].string.endsWith(run)) return null
        val keep = order.firstOrNull { pool[it].string.endsWith(run) } ?: return null
        return listOf(keep) + order.filter { it != keep }
    }

    /** カタカナをひらがなに畳んだ文字列（ー はそのまま）。 */
    internal fun foldKatakana(s: String): String = buildString(s.length) {
        for (ch in s) append(if (ch in '\u30A1'..'\u30F6') ch - 0x60 else ch)
    }

    /** ひらがな→カタカナの書き換えだけの差し替えを止めるリランカーの差（これ未満なら Sumire の 1 位のまま）。 */
    const val KATAKANA_SWAP_MARGIN = 1.5

    /**
     * リランカー 1 位が、Sumire の 1 位のひらがなの部分をカタカナにしただけ（カタカナを畳むと Sumire の 1 位と同じ）で、
     * スコアの差が [KATAKANA_SWAP_MARGIN] 未満なら Sumire の 1 位に戻す（端末報告: かわいそすぎやろ → カワイソスぎやろ。
     * 表はネット語のカタカナ語を語彙に持ち、Sumire の口語のかなの語は語彙外で低く出る。ひらがなの出力は良い、強制は悪い）。
     * 変えないなら null。
     */
    internal fun keepHiraganaOverKatakanaSwap(pool: List<Candidate>, order: List<Int>, scores: DoubleArray): List<Int>? {
        if (order.isEmpty() || order[0] == 0) return null
        val top = pool[order[0]].string
        val sumire = pool[0].string
        if (top == sumire || top.none { it in '\u30A1'..'\u30F6' } || foldKatakana(top) != sumire) return null
        if (scores[order[0]] - scores[0] >= KATAKANA_SWAP_MARGIN) return null
        return listOf(0) + order.filter { it != 0 }
    }

    private fun isSingleKanji(s: String): Boolean =
        s.length == 1 && (s[0] in '\u4E00'..'\u9FFF' || s[0] == '々')

    /** 稀な 1 字漢字への差し替えを止めるか（[pool] は Sumire の順、[top] はリランカー 1 位の位置）。 */
    internal fun blocksRareSingleKanji(input: String, pool: List<Candidate>, top: Int): Boolean {
        if (top == 0 || input.length !in 2..3 || !isSingleKanji(pool[top].string)) return false
        // Sumire の 1 位も 1 字漢字（はし→橋/箸）で、その近く（上位 4 件）の 1 字漢字どうしなら文脈で選び直してよい。
        return !(isSingleKanji(pool[0].string) && top <= 3)
    }

}

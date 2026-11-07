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

    /** 2026-11-07 の並べ替えの歯止め（1 字漢字への差し替え・文頭の助詞・長い読みの 1 字漢字）。評価用に切り替え可能。 */
    @Volatile
    @JvmStatic
    var guards1107: Boolean = true
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
        if (guards1107) {
            val toks = inputs.map { it.tokens }
            keepHiraganaOverSingleKanjiSwap(toks, order, scores)?.let { order = it }
            avoidSentenceInitialParticle(leftContext, toks, inputs.map { it.readings }, order, scores)?.let { order = it }
            avoidLoneLongReadingKanji(input, pool, order, scores)?.let { order = it }
        }
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

    /** 1 字漢字への差し替え・文頭の助詞・長い読みの 1 字漢字を止めるリランカーの差（これ未満なら止める）。 */
    const val SINGLE_KANJI_SWAP_MARGIN = 1.5
    const val INITIAL_PARTICLE_MARGIN = 3.0
    const val LONE_KANJI_MARGIN = 1.0

    private fun isAllHiragana(s: String): Boolean = s.isNotEmpty() && s.all { it in '\u3041'..'\u3096' || it == 'ー' }

    /**
     * リランカー 1 位が、Sumire の 1 位と同じ語分割で、Sumire のひらがなの語を 1 字漢字に替えただけ（しとく→し得）で、
     * 差が [SINGLE_KANJI_SWAP_MARGIN] 未満なら Sumire の 1 位に戻す（2026-11-07 端末: めちゃたのしみしとく → し得）。
     * 補助動詞・縮約（とく・とる・ちゃう）はひらがなで書くのが普通で、表は稀な 1 字漢字の語に引っ張られる。変えないなら null。
     */
    internal fun keepHiraganaOverSingleKanjiSwap(tokens: List<List<String>>, order: List<Int>, scores: DoubleArray): List<Int>? {
        if (order.isEmpty() || order[0] == 0) return null
        val top = tokens[order[0]]
        val sumire = tokens[0]
        if (top.size != sumire.size || top.isEmpty()) return null
        var swapped = false
        for (k in top.indices) {
            if (top[k] == sumire[k]) continue
            if (!(isSingleKanji(top[k]) && isAllHiragana(sumire[k]) && sumire[k].length >= 2)) return null
            swapped = true
        }
        if (!swapped || scores[order[0]] - scores[0] >= SINGLE_KANJI_SWAP_MARGIN) return null
        return listOf(0) + order.filter { it != 0 }
    }

    private val INITIAL_PARTICLES = setOf("は", "が", "を", "に", "へ")

    /**
     * 文頭（左文脈なし・文末記号や改行の直後）の 1 字の助詞（は|磨いて）は日本語として来ない。1 位がそうなら、先頭の語が
     * 違う候補のうち一番スコアの高いもの（歯磨いて）を、差が [INITIAL_PARTICLE_MARGIN] 未満なら 1 位にする（2026-11-07 端末）。
     * 変えないなら null。
     */
    internal fun avoidSentenceInitialParticle(
        leftContext: String,
        tokens: List<List<String>>,
        readings: List<List<String>>,
        order: List<Int>,
        scores: DoubleArray,
    ): List<Int>? {
        if (order.isEmpty()) return null
        val left = leftContext.trimEnd(' ', '\u3000')
        if (left.isNotEmpty() && left.last() !in "。！？!?\n") return null
        val t0 = tokens[order[0]].firstOrNull() ?: return null
        if (t0 !in INITIAL_PARTICLES || readings[order[0]].firstOrNull() != t0) return null
        // 助詞の字だけを替えた候補（は|磨いて→歯|磨いて）に限る。残りまで変わる候補（はよしてや→羽吉手や）は採らない。
        val rest = tokens[order[0]].joinToString("").substring(t0.length)
        val alt = order.drop(1).firstOrNull {
            val f = tokens[it].firstOrNull()
            f != null && f !in INITIAL_PARTICLES && tokens[it].joinToString("").endsWith(rest) &&
                tokens[it].joinToString("").length - rest.length == 1
        } ?: return null
        if (scores[order[0]] - scores[alt] >= INITIAL_PARTICLE_MARGIN) return null
        return listOf(alt) + order.filter { it != alt }
    }

    /**
     * 入力全体（4 字以下）が読み 3 字以上の 1 字漢字（うれし→嬉）になったら、その漢字を含む送り仮名つきの表記（嬉し）のうち
     * 一番スコアの高いものを、差が [LONE_KANJI_MARGIN] 未満なら 1 位にする（2026-11-07 端末）。
     * 変えないなら null。
     */
    internal fun avoidLoneLongReadingKanji(input: String, pool: List<Candidate>, order: List<Int>, scores: DoubleArray): List<Int>? {
        if (order.isEmpty() || input.length !in 3..4) return null
        val top = pool[order[0]].string
        if (!isSingleKanji(top)) return null
        // 送り仮名つきの表記（嬉し）があるときだけ替える。名詞として 1 字で立つ漢字（さかな→魚）にはそれが無いので変わらない。
        val alt = order.drop(1).firstOrNull { val s = pool[it].string; s.length >= 2 && s.startsWith(top) && isAllHiragana(s.substring(1)) }
            ?: return null
        if (scores[order[0]] - scores[alt] >= LONE_KANJI_MARGIN) return null
        return listOf(alt) + order.filter { it != alt }
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

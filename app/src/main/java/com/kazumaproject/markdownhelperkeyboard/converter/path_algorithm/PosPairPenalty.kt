package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node

/**
 * 連接コストだけでは抑えきれない品詞の並びへの追加コスト（前ノード＋現ノードで決まる）。
 * FindPath の全探索（前向き DP・後ろ向き A*・文節）と Zenzai の制約付き探索で同じ値を足す。
 * ID は app/src/main/assets/id.def（Mozc）に対応。
 */
internal object PosPairPenalty {
    /** 読み 1 文字の数詞（よ→四, し→四, に→二…）を数の文脈以外で選ばせない。候補としては残る。 */
    const val ONE_KANA_NUMERAL_PENALTY = 3000

    /**
     * 動詞（自立）連用形の直後のサ変「する」（話し＋した → 話＋した / 話した を優先）。
     * お＋連用形＋する（お待ちします）も同じ並びなので、文頭以外で前の前が「お/ご」でも区別できない点に注意（値は控えめ）。
     */
    const val RENYOU_SURU_PENALTY = 1000

    // 動詞,自立,*,*,*,連用形,* の ID（id.def から生成）。
    private val VERB_RENYOU_IDS: IntArray = intArrayOf(
        596, 597, 615, 616, 626, 638, 644, 701, 702, 703, 704, 705, 706, 707, 717, 727, 735, 742,
        783, 784, 785, 786, 796, 803, 829, 830, 831, 832, 842, 847, 856,
    )
    // 動詞,自立,*,*,サ変・スル,*,* (627..638)。
    private const val SURU_FIRST = 627
    private const val SURU_LAST = 638

    // 名詞,数,* (2043..2055) と 接頭詞,数接続 (2639..2642: 何・第…)。
    private const val NUMBER_FIRST = 2043
    private const val NUMBER_LAST = 2055
    private const val NUMBER_PREFIX_FIRST = 2639
    private const val NUMBER_PREFIX_LAST = 2642

    /**
     * はいる→入る 対策（SUMIRE-LM、既定 [HAIRU_PENALTY]、0 で無効）。読み「はい…」の「入…」に対して:
     *  - 接続助詞「て」「で」の直後の「入る」（わかって|入る → わかって|は|いる、心がけて|は|いる）。
     *    「入って」（熱くて入って・決して入ってみ）は対象外。
     *  - 「と」「そう」「こう」「ああ」「どう」の直後の「入っ」（そう|入っても → そうは言っても、とは言ったが）。
     *    「ぐっと」「ちょっと」等の副詞は 1 ノードなので対象外。
     * 語ごとの LM 補正（LatticeLmCost の MU）は 1 語の「入る」を有利にするので、ここで逆向きに足す。
     */
    const val HAIRU_PENALTY = 8000

    @Volatile
    @JvmStatic
    var hairuPenalty: Int = HAIRU_PENALTY

    private const val TE_CONJ_ID = 348 // 助詞,接続助詞,て
    private const val DE_CONJ_ID = 349 // 助詞,接続助詞,で
    private val HAIRU_QUOTE_PREV = setOf("と", "そう", "こう", "ああ", "どう")

    fun hairuCost(prev: Node, node: Node): Int {
        val p = hairuPenalty
        if (p == 0) return 0
        val t = node.tango
        if (t.isEmpty() || t[0] != '入' || !node.yomiUsed.startsWith("はい")) return 0
        if (t.startsWith("入る") && node.yomiUsed.startsWith("はいる")) {
            val r = prev.r.toInt()
            if ((r == TE_CONJ_ID && prev.tango == "て") || (r == DE_CONJ_ID && prev.tango == "で")) return p
        } else if (t.startsWith("入っ") && prev.tango in HAIRU_QUOTE_PREV) {
            return p
        }
        return 0
    }

    fun cost(prev: Node, node: Node): Int {
        var penalty = hairuCost(prev, node)
        if (isOneKanaNumeral(node) && !isNumberContext(prev)) penalty += ONE_KANA_NUMERAL_PENALTY
        if (node.l.toInt() in SURU_FIRST..SURU_LAST && VERB_RENYOU_IDS.binarySearch(prev.r.toInt()) >= 0) {
            penalty += RENYOU_SURU_PENALTY
        }
        return penalty
    }

    private fun isNumber(id: Int): Boolean = id in NUMBER_FIRST..NUMBER_LAST

    private fun isOneKanaNumeral(node: Node): Boolean =
        node.len.toInt() == 1 && isNumber(node.l.toInt())

    /** 文頭（BOS r=0）・数詞・数接続の接頭詞の直後は数の文脈として扱う（にせんにじゅうご 等）。 */
    private fun isNumberContext(prev: Node): Boolean {
        val r = prev.r.toInt()
        return r == 0 || isNumber(r) || r in NUMBER_PREFIX_FIRST..NUMBER_PREFIX_LAST
    }
}

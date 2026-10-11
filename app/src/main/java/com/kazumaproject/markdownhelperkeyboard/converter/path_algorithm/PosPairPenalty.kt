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

    // 名詞,数,* (2043..2055) と 接頭詞,数接続 (2639..2642: 何・第…)。
    private const val NUMBER_FIRST = 2043
    private const val NUMBER_LAST = 2055
    private const val NUMBER_PREFIX_FIRST = 2639
    private const val NUMBER_PREFIX_LAST = 2642

    fun cost(prev: Node, node: Node): Int {
        var penalty = 0
        if (isOneKanaNumeral(node) && !isNumberContext(prev)) penalty += ONE_KANA_NUMERAL_PENALTY
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

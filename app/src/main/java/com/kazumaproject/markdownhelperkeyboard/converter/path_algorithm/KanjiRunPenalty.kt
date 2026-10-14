package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node

/**
 * 連続漢字（継ぎはぎ）ペナルティ（実験、既定オフ、NK_KANJIRUN）。
 * 辞書にない口語・方言のかな句が「短い低頻度の漢字語の連続」（阿寒+…、野犬+…）に崩れるのを抑える。
 *
 * 辺 prev → node に [penalty] を足す条件（すべて満たすとき）:
 *  - node・prev とも「短い漢字語」: 表記 1〜2 文字が全部漢字（言う・買っ のような送りがな付きは対象外）、読み（yomiUsed）が全部ひらがな、システム辞書 / 未知語、
 *    名詞,数（2043..2055）・名詞,接尾 / 接尾可能（1936..2041）ではない（広島+県、三+月 は対象外）
 *  - node の語コスト（Node.score）≥ [threshold]（低頻度）、node の表記 ≤ [nodeMaxLen] 字。[bothLow] なら prev も ≥ [threshold]
 *  - prev が接頭詞（2596..2642）ではない
 * かな機能語を挟む並び（買っ+て、県庁 1 語、困難 1 語）は隣接しないので対象外。
 * FindPath.getEdgeCost からだけ呼ぶ（zenz / jinen の制約付き探索には入れない）。
 */
internal object KanjiRunPenalty {
    @Volatile @JvmStatic var enabled: Boolean = false
    @Volatile @JvmStatic var penalty: Int = 3000
    @Volatile @JvmStatic var threshold: Int = 6000
    @Volatile @JvmStatic var bothLow: Boolean = true
    /** node（後ろの低頻度語）の表記の最大文字数（1 = 1 字の漢字だけ: 夕方+察、全然+輪）。prev は常に 1〜2 字。 */
    @Volatile @JvmStatic var nodeMaxLen: Int = 2

    private const val NUMBER_FIRST = 2043
    private const val NUMBER_LAST = 2055
    private const val SUFFIX_FIRST = 1936
    private const val SUFFIX_LAST = 2041
    private const val PREFIX_FIRST = 2596
    private const val PREFIX_LAST = 2642

    /** 評価用: 非 null なら条件を満たした辺の (prev 表記:コスト → node 表記:コスト) を数える。 */
    @Volatile @JvmStatic var trace: MutableMap<String, Int>? = null

    private fun isKanji(c: Char): Boolean = c in '\u4E00'..'\u9FFF' || c == '々' || c in '\u3400'..'\u4DBF'

    fun isShortKanji(n: Node): Boolean {
        if (n.mozcNodeType != MozcNodeType.NOR) return false
        if (n.candidateSource != CandidateSource.SYSTEM && n.candidateSource != CandidateSource.UNKNOWN) return false
        val t = n.tango
        if (t.isEmpty() || t.length > 2 || !t.all { isKanji(it) }) return false
        val y = n.yomiUsed
        if (y.isEmpty() || y.any { it !in 'ぁ'..'ゖ' }) return false
        val l = n.l.toInt()
        if (l in NUMBER_FIRST..NUMBER_LAST || l in SUFFIX_FIRST..SUFFIX_LAST) return false
        return true
    }

    @JvmStatic
    fun cost(prev: Node, node: Node): Int {
        if (!enabled) return 0
        if (node.score < threshold) return 0
        if (bothLow && prev.score < threshold) return 0
        if (prev.r.toInt() in PREFIX_FIRST..PREFIX_LAST) return 0
        if (node.tango.length > nodeMaxLen) return 0
        if (!isShortKanji(node) || !isShortKanji(prev)) return 0
        trace?.let { m ->
            synchronized(m) { val k = "${prev.tango}:${prev.score}>${node.tango}:${node.score}"; m[k] = (m[k] ?: 0) + 1 }
        }
        return penalty
    }

    /** NK_KANJIRUN=penalty,threshold[,a|b[,nodeMaxLen]]（負の penalty = 無効。a = prev の頻度は問わない、b = 両方低頻度）。 */
    fun configure(spec: String) {
        val f = spec.split(',').map { it.trim() }
        val p = f[0].toInt()
        penalty = 3000; threshold = 6000; bothLow = true; nodeMaxLen = 2
        enabled = p >= 0
        if (!enabled) return
        penalty = p
        f.getOrNull(1)?.let { threshold = it.toInt() }
        bothLow = f.getOrNull(2) != "a"
        nodeMaxLen = f.getOrNull(3)?.toInt() ?: 2
    }
}

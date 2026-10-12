package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * ZENZ-GATE: Sumire の 1 位と 2 位の経路コスト差が十分大きい（Sumire が自信を持っている）読みでは
 * zenz の評価を省く。しきい値は読みの長さ × 左文脈の有無ごとに持てる（今は全区分で同じ値）。
 *
 * しきい値はオフライン評価で決めた（JWTD 漢字誤変換の調整用半分のみで当てはめ、zenz が正解に直した
 * 文の 98% 以上を残す最小の呼び出し。評価用半分・AJIMEE は検証のみ。データはリポジトリ外）。
 * 経路コスト差はシステム辞書の経路（type 1）の全読み候補どうしで取る。ひらがな・カタカナ等の
 * 付加候補は固定スコアなので比べない。差が取れない（候補が 1 つ、1 位が学習/ユーザー辞書）ときは常に評価する。
 * 前回の FIX 制約を引き継いでいる間と判定キャッシュがあるときはゲートしない（呼び出し側）。
 */
object ZenzaiGate {
    private const val SYSTEM_PATH_TYPE: Byte = 1

    /**
     * 読みの長さの上限（含む）ごとのしきい値: [左文脈なし, 左文脈あり]。差がこれ以上なら評価しない。
     * 長さ × 文脈の区分も試したが、調整用半分の中の 2 分割交差検証では区分ごとのしきい値は過適合して
     * 残せた直しが 92〜97% に落ちたため、全区分で同じ 1 つの値（1448）にしている（2026-10-12）。
     */
    internal const val DEFAULT_GAP_THRESHOLD = 1448
    internal val THRESHOLDS: List<Pair<Int, IntArray>> = listOf(
        Int.MAX_VALUE to intArrayOf(DEFAULT_GAP_THRESHOLD, DEFAULT_GAP_THRESHOLD),
    )

    fun threshold(readingLength: Int, leftContextLength: Int): Int {
        val row = THRESHOLDS.first { readingLength <= it.first }.second
        return row[if (leftContextLength > 0) 1 else 0]
    }

    /** zenz で評価するか。[sumireGap] が null（差が取れない）なら評価する。 */
    fun shouldEvaluate(readingLength: Int, leftContextLength: Int, sumireGap: Int?): Boolean =
        sumireGap == null || sumireGap < threshold(readingLength, leftContextLength)

    /** Sumire の 1 位経路と、別表記の次点経路（全読み・システム辞書経路）のコスト差。 */
    fun pathCostGap(input: String, candidates: List<Candidate>, top: Candidate): Int? {
        if (top.type != SYSTEM_PATH_TYPE || top.length.toInt() != input.length) return null
        val second = candidates.asSequence()
            .filter {
                it.type == SYSTEM_PATH_TYPE && it.string != top.string &&
                    it.length.toInt() == input.length
            }
            .minOfOrNull { it.score } ?: return null
        return second - top.score
    }
}

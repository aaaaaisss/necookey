package com.kazumaproject.markdownhelperkeyboard.zeroquery

/**
 * 後続語辞書の引き方。辞書のキーは単語（「ありがとう」「よろしく」）なので、確定した文字列全体
 * （「どうもありがとう」「本当によろしく」）では当たらないことが多い。見つからなければ末尾の語を
 * 長い順に試す（AI なしでも確定の単位に左右されずに後続語を出す）。
 */
object ZeroQueryBackoff {
    /** 末尾から試す最大文字数。 */
    const val MAX_TAIL = 8

    private const val NOT_WORD_START = "ぁぃぅぇぉゃゅょゎっんーァィゥェォャュョヮッンー、。，．！？!?・"

    private fun isHiragana(c: Char) = c in '\u3041'..'\u309F'

    /** 試すキー: [key] 全体 → 末尾の部分文字列（長い順）。 */
    fun keys(key: String): List<String> {
        if (key.isBlank()) return emptyList()
        val out = mutableListOf(key)
        val tail = key.takeLast(MAX_TAIL)
        val startMin = key.length - tail.length
        for (start in maxOf(1, startMin) until key.length) {
            val suffix = key.substring(start)
            val first = suffix.first()
            if (first in NOT_WORD_START || first.isWhitespace()) continue
            // ひらがなで始まる短い末尾（「とう」「ね」）は語の途中のことが多いので 3 文字以上だけ。
            val minLength = if (isHiragana(first)) 3 else 2
            if (suffix.length >= minLength) out += suffix
        }
        return out
    }

    /** [keys] を順に引き、最初に見つかった結果。 */
    inline fun <T> firstHit(key: String, lookup: (String) -> List<T>): List<T> {
        for (k in keys(key)) {
            val hit = lookup(k)
            if (hit.isNotEmpty()) return hit
        }
        return emptyList()
    }
}

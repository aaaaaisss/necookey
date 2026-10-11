package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * 確定後に表示した後続語（zenz で並べ替えた後の並び）を、そのまま続けて打ち始めたときの予測に引き継ぐ。
 * 例: 「ありがとう」確定後に「ございます」が出ていた → 無視して「ご」と打っても予測欄の先頭に「ございます」。
 *
 * 読みで絞るので、読みがかな（ひらがな・カタカナ・長音）の候補だけが対象。絵文字・読みのない候補は出さない。
 */
object NextWordCarry {
    /** 予測欄に引き継ぐ上限。 */
    const val MAX = 6

    /** 左文脈の照合に使う、確定文字列の末尾文字数。 */
    const val CONTEXT_TAIL = 8

    /** 候補の読み（ひらがな）。かなでない読みは null。 */
    fun reading(candidate: Candidate): String? {
        val yomi = candidate.yomi ?: return null
        if (yomi.isEmpty()) return null
        val sb = StringBuilder(yomi.length)
        for (ch in yomi) {
            when (ch) {
                in '\u3041'..'\u3096', 'ー' -> sb.append(ch)
                in '\u30A1'..'\u30F6' -> sb.append(ch - 0x60)
                else -> return null
            }
        }
        return sb.toString()
    }

    /** [carried] のうち読みが [input] で始まり、まだ続きがある候補（表示順のまま、[MAX] 件まで）。 */
    fun filter(carried: List<Candidate>, input: String): List<Candidate> {
        if (input.isEmpty() || carried.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        return carried.filter { c ->
            val r = reading(c)
            r != null && r.length > input.length && r.startsWith(input) && seen.add(c.string)
        }.take(MAX)
    }

    /**
     * 引き継ぎを使ってよいか: 入力の直前の文字列 [before] が、後続語を出したときの確定文字列 [committed]
     * の末尾で終わっている（カーソル移動・削除で文脈が変わっていない）。
     */
    fun contextMatches(committed: String, before: String): Boolean {
        val tail = committed.substringAfterLast('\n').takeLast(CONTEXT_TAIL)
        return tail.isNotEmpty() && before.endsWith(tail)
    }
}

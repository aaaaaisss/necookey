package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber

/**
 * 文中の複合数詞（にじゅうよん, にせんにじゅうご）を 1 ノードにする。従来は文全体が数詞のときだけ
 * 数として扱われ、文中では 二重＋4 のように崩れていた。位取りの語（じゅう・ひゃく・せん・まん）を含む
 * 読みだけを対象にし（いち・に 等の単独の数は辞書に任せる）、算用数字を先頭、漢数字を次点に置く。
 */
internal object NumberReadingNodes {
    // 算用数字も 名詞,数,漢数字 の品詞で置く（Mozc の数詞リライトと同じく、連接は漢数字のものを使い表記だけ変える。
    // アラビア数字 2044 のままだと 年・円 などとの連接で漢数字に負ける）。
    const val ARABIC_ID: Short = 2046
    const val KANJI_ID: Short = 2046
    const val ARABIC_COST = 3000
    const val KANJI_EXTRA_COST = 300
    private const val MAX_LEN = 16
    private val PLACES = listOf("じゅ", "じっ", "ひゃ", "びゃ", "ぴゃ", "せん", "ぜん", "まん", "おく", "おっ", "ちょう")
    private val BAD_ENDS = listOf("ちょう", "おく", "おっ", "し", "く", "よ", "っ")
    private const val STARTS = "ぜれいにさしよごろなはきくじひびぴせまおち"

    /** str[start..) から始まる複合数詞の (終端 index, 値) の一覧。 */
    fun spans(str: String, start: Int): List<Pair<Int, Long>> {
        if (str[start] !in STARTS) return emptyList()
        var out: MutableList<Pair<Int, Long>>? = null
        val last = minOf(str.length, start + MAX_LEN)
        for (end in start + 2..last) {
            val sub = str.substring(start, end)
            if (PLACES.none { sub.contains(it) }) continue
            // 誤検出の多い形を除く: 兆・億で終わる（にちょう→2兆）、し・く・よ で終わる（さんせんし→三千四）、
            // 「ぜん」は「さん」の直後だけ（ぜんく→千九）。
            if (BAD_ENDS.any { sub.endsWith(it) }) continue
            if (sub.contains("ぜん") && !sub.contains("さんぜん")) continue
            val value = sub.toNumber()?.second?.toLongOrNull() ?: continue
            if (out == null) out = mutableListOf()
            out.add(end to value)
        }
        return out ?: emptyList()
    }

    fun toKanji(value: Long): String {
        if (value == 0L) return "〇"
        val digits = "〇一二三四五六七八九"
        val bigUnits = listOf(1_000_000_000_000L to "兆", 100_000_000L to "億", 10_000L to "万", 1L to "")
        val sb = StringBuilder()
        var rest = value
        for ((unit, name) in bigUnits) {
            val section = (rest / unit).toInt()
            rest %= unit
            if (section == 0) continue
            var s = section
            for ((p, pn) in listOf(1000 to "千", 100 to "百", 10 to "十")) {
                val d = s / p
                s %= p
                if (d == 0) continue
                if (d > 1) sb.append(digits[d])
                sb.append(pn)
            }
            if (s > 0) sb.append(digits[s])
            sb.append(name)
        }
        return sb.toString()
    }
}

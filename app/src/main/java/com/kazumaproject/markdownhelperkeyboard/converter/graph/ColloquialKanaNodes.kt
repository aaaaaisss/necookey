package com.kazumaproject.markdownhelperkeyboard.converter.graph

/**
 * 長音「ー」・語末の促音「っ」で崩した口語のかな語（これー・かー・いっぱーい・うるさーい・しょーがない・ちょーだい・はいっ）を
 * 1 語のひらがなノードにする。従来は「ー」を含む読みが カタカナ語（コレー・カー・ショー）や 漢字片＋ー（一派ーい・売るサーイ）に崩れていた。
 *
 * 読み区間 [i, j) が「ー」を含む（先頭以外、直前がひらがな）か、末尾が「っ」で直後が文末・読点・ー のとき、
 *  - 「ー」を全部除いた形（これー→これ、いっぱーい→いっぱい）
 *  - 「ー」を直前の母音に置き換えた形（お段→う、え段→い: しょー→しょう、ねー→ねえ/ねい、ちょーだい→ちょうだい）
 *  - 末尾の「っ」を除いた形（はいっ→はい）
 * がシステム辞書に「ひらがな表記そのもの」の語として載っていれば、その語の品詞 ID・コスト＋[penalty] で、表記 = 読みそのまま（ー・っ付き）の
 * ノードを足す。区間の読みがそのまま辞書にある（ちーず→チーズ、こーひー）なら足さない。辞書・表は使わない（追加サイズ 0）。
 * 既定は [enabled]（tools/sumire_lm/HANDOFF.md の 2026-10-13 口語境界の節で daily / 口語セットの tune で決めた）。
 */
internal object ColloquialKanaNodes {
    const val DEFAULT_PENALTY = 1000
    const val MAX_LEN = 12

    @Volatile
    @JvmStatic
    var enabled: Boolean = true

    @Volatile
    @JvmStatic
    var penalty: Int = DEFAULT_PENALTY

    private const val A_ROW = "あかさたなはまやらわがざだばぱぁゃゎ"
    private const val I_ROW = "いきしちにひみりぎじぢびぴぃ"
    private const val U_ROW = "うくすつぬふむゆるぐずづぶぷぅゅゔ"
    private const val E_ROW = "えけせてねへめれげぜでべぺぇ"
    private const val O_ROW = "おこそとのほもよろをごぞどぼぽぉょ"
    private const val TAIL_BOUNDARY = "、。，．！？!?ー…"

    private fun isHira(c: Char): Boolean = c in 'ぁ'..'ゖ'

    /** 入力全体に対象の文字が無ければ何もしない（大半の入力はここで終わる）。 */
    fun mayApply(str: String): Boolean = enabled && (str.indexOf('ー') > 0 || str.indexOf('っ') > 0)

    /** 辞書を引く形 [key]。[tail] = 除いた末尾の ー/っ（空 = 語中の長音を除いた/戻した形）。どちらもひらがな表記の語だけを使う。 */
    data class Variant(val key: String, val tail: String)

    /** str[start, end) を口語形とみなせるなら辞書を引く形の一覧（重複なし）、そうでなければ空。 */
    fun variants(str: String, start: Int, end: Int): List<Variant> {
        if (end - start < 2 || end - start > MAX_LEN) return emptyList()
        val first = str[start]
        if (!isHira(first) || first == 'っ' || first == 'ん') return emptyList()
        for (k in start until end) {
            val c = str[k]
            if (c == 'ー') {
                // 直前がひらがなかー（ーー の連続）であること
                if (k == start) return emptyList()
                val p = str[k - 1]
                if (!(isHira(p) || p == 'ー')) return emptyList()
            } else if (!isHira(c)) {
                return emptyList()
            }
        }
        val span = str.substring(start, end)
        // 末尾の ー/っ の並び（っ は文末・読点・ー の直前か、並びに ー を含むときだけ）
        var t = span.length
        while (t > 1 && (span[t - 1] == 'ー' || span[t - 1] == 'っ')) t--
        var tail = span.substring(t)
        if (tail.isNotEmpty() && 'ー' !in tail) {
            val atBoundary = end == str.length || str[end] in TAIL_BOUNDARY
            if (!atBoundary || tail.length > 1) tail = ""
        }
        val body = span.substring(0, span.length - tail.length)
        val out = LinkedHashSet<Variant>(4)
        if (tail.isNotEmpty() && body.isNotEmpty() && 'ー' !in body) out.add(Variant(body, tail))
        if ('ー' in span) {
            val removed = span.replace("ー", "").let { if (it.endsWith('っ') && tail.isNotEmpty()) it.dropLast(1) else it }
            if (removed.isNotEmpty() && removed != body) out.add(Variant(removed, ""))
            out.add(Variant(extendVowel(span.trimEnd('っ'), eRowAsI = true), ""))
            out.add(Variant(extendVowel(span.trimEnd('っ'), eRowAsI = false), ""))
        }
        out.removeAll { it.key.isEmpty() || it.key.contains('ー') || (it.tail.isEmpty() && it.key == span) }
        if (out.none { it.tail.isNotEmpty() } && 'ー' !in span) return emptyList()
        return out.toList()
    }

    private fun extendVowel(s: String, eRowAsI: Boolean): String {
        val sb = StringBuilder(s.length)
        var prev = ' '
        for (c in s) {
            if (c == 'ー') {
                val v = when {
                    prev in A_ROW -> 'あ'
                    prev in I_ROW -> 'い'
                    prev in U_ROW -> 'う'
                    prev in E_ROW -> if (eRowAsI) 'い' else 'え'
                    prev in O_ROW -> 'う'
                    prev == 'ん' -> 'ん'
                    else -> null
                }
                if (v == null) continue
                sb.append(v)
                // ーー は 1 つの長音（そーーっと→そうっと）
                prev = '\u0000'
            } else {
                sb.append(c); prev = c
            }
        }
        return sb.toString()
    }
}

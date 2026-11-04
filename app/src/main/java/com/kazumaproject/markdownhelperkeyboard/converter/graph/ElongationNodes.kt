package com.kazumaproject.markdownhelperkeyboard.converter.graph

/**
 * 語末の母音の伸ばし（よくないのおお・そうなのおお・すごいねええ・だめえええ）を、かなのまま 1 ノードにする。
 *
 * 以前は のおお → の＋大、ねええ → ね＋絵 のように、伸ばしの母音が漢字 1 字の名詞に化けていた。
 * 条件: 直前の字の母音と同じ母音（あいうえお・小書き）が 2 字以上続き（ー も混ぜてよい）、入力の末尾か、かな以外（記号など）の直前で終わる。
 * ノード: 表記 = 読みのまま、品詞 = 終助詞（ぁ ぃ ぅ ぇ ぉ、id.def 405..409 の その母音）、コスト = [cost]。
 */
internal object ElongationNodes {
    const val DEFAULT_COST = 500

    @Volatile
    @JvmStatic
    var enabled: Boolean = true

    @Volatile
    @JvmStatic
    var cost: Int = DEFAULT_COST

    private const val VOWELS = "あいうえお"
    private const val SMALL = "ぁぃぅぇぉ"
    private val POS: ShortArray = shortArrayOf(405, 406, 407, 408, 409) // 終助詞 ぁ ぃ ぅ ぇ ぉ

    /** かな 1 字の母音（0..4）。母音が決まらない字（ん・っ・記号）は -1。 */
    fun vowelOf(c: Char): Int {
        VOWELS.indexOf(c).let { if (it >= 0) return it }
        SMALL.indexOf(c).let { if (it >= 0) return it }
        if (c == 'ゃ') return 0
        if (c == 'ゅ') return 2
        if (c == 'ょ') return 4
        if (c !in 'か'..'ゔ') return -1
        val rows = arrayOf(
            "かがさざただなはばぱまやらわ", "きぎしじちぢにひびぴみり", "くぐすずつづぬふぶぷむゆる",
            "けげせぜてでねへべぺめれ", "こごそぞとどのほぼぽもよろを",
        )
        for (v in rows.indices) if (rows[v].indexOf(c) >= 0) return v
        return -1
    }

    data class Span(val end: Int, val posId: Short)

    /** [i] から始まる伸ばしの母音の並び（無ければ null）。 */
    fun spanAt(str: String, i: Int): Span? {
        if (!enabled || i <= 0 || i >= str.length) return null
        val v = vowelOf(str[i - 1])
        if (v < 0) return null
        var j = i
        var vowels = 0
        while (j < str.length) {
            val c = str[j]
            if (c == 'ー') { j++; continue }
            if (c != VOWELS[v] && c != SMALL[v]) break
            vowels++
            j++
        }
        if (vowels < 2 || str[j - 1] == 'ー' && vowels < 2) return null
        if (j < str.length && isKana(str[j])) return null
        return Span(j, POS[v])
    }

    /** 入力の末尾の伸ばしの母音の並び（よくないのおお → おお）。無ければ null。 */
    fun trailingRun(str: String): String? {
        for (i in maxOf(1, str.length - 16) until str.length - 1) {
            val sp = spanAt(str, i) ?: continue
            if (sp.end == str.length) return str.substring(i)
        }
        return null
    }

    private fun isKana(c: Char): Boolean = c in 'ぁ'..'ゖ' || c in 'ァ'..'ヺ' || c == 'ー'
}

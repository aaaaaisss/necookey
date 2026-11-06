package com.kazumaproject.markdownhelperkeyboard.converter.graph

/**
 * 長音符 ー を前の字の母音の伸ばしとして読む、ひらがなの語のノード（そーだけど・よー頑張った・すげー・ねー）。
 *
 * 以前は ー を含む読みがカタカナ語（ソーダ・ヨー）にしかならず、思ってソーダけど・ヨー頑張ったね になっていた。
 * 読み str[i, e) の ー を前の字の母音の字（あ段→あ、い段→い、う段→う、え段→い/え、お段→う/お）に置き換えた読みで
 * システム辞書を引き、表記がひらがなそのもの（= 置き換えた読み）の語だけを、表記 = 元の読み（ー のまま）・同じ品詞 ID・
 * 語コスト＋[penalty] のノードにする。漢字・カタカナの語は作らない（ソーダ・コーヒー などのカタカナ語はそのまま）。
 * ラティス LM は置き換えた読み（そう・よう）で引く（[lmWord]）。辞書・表は追加しない。
 *
 * もう 1 つ: 母音だけの感動詞（うおお・うおー・あああ）。文頭（または かな以外の後）から母音の字（と ー）だけが 3 字以上続き、
 * 最後が同じ母音の繰り返しか ー で終わり、入力の末尾・かな以外の直前・ー で終わる並びを、かなのまま感動詞のノードにする
 * （以前は うおお → ウオオ）。母音だけの普通の語（青い・多い・家・上）は最後が同じ母音の繰り返しにならないので当たらない。
 */
internal object LongVowelNodes {
    const val DEFAULT_PENALTY = 800
    const val INTERJECTION_COST = 1500
    const val INTJ: Short = 2591 // 感動詞（id.def）

    @Volatile
    @JvmStatic
    var enabled: Boolean = true

    @Volatile
    @JvmStatic
    var penalty: Int = DEFAULT_PENALTY

    /** 置き換えを試す読みの最大長（ー を含む語）。 */
    const val MAX_LEN = 6

    private const val VOWELS = "あいうえお"
    private const val SMALL = "ぁぃぅぇぉ"

    fun mayApply(str: String): Boolean = enabled && str.indexOf('ー') > 0

    /** ー を前の字の母音で置き換えた読みの候補（無ければ空）。お段は う→お、え段は い→え の順。 */
    fun variants(yomi: String): List<String> {
        if (yomi.indexOf('ー') <= 0) return emptyList()
        var outs = listOf(StringBuilder())
        var prevVowel = -1
        for (c in yomi) {
            if (c == 'ー') {
                if (prevVowel < 0) return emptyList()
                val alts = when (prevVowel) {
                    0 -> "あ"; 1 -> "い"; 2 -> "う"; 3 -> "いえ"; else -> "うお"
                }
                outs = outs.flatMap { sb -> alts.map { a -> StringBuilder(sb).append(a) } }
                // 伸ばしの後の母音は前の母音のまま（そーー）
            } else {
                if (c !in 'ぁ'..'ゖ') return emptyList()
                outs.forEach { it.append(c) }
                prevVowel = ElongationNodes.vowelOf(c)
            }
        }
        return outs.map { it.toString() }
    }

    /** ラティス LM で引く語: ー を最初の候補の母音で置き換えた読み（そー → そう）。 */
    fun lmWord(tango: String): String = variants(tango).firstOrNull() ?: tango

    private fun isVowelChar(c: Char) = c in VOWELS || c in SMALL
    private fun isKana(c: Char): Boolean = c in 'ぁ'..'ゖ' || c in 'ァ'..'ヺ' || c == 'ー'

    /** [i] から始まる母音だけの感動詞の終わり（無ければ -1）。 */
    fun interjectionEnd(str: String, i: Int): Int {
        if (!enabled || i >= str.length) return -1
        if (i > 0 && isKana(str[i - 1])) return -1
        if (!isVowelChar(str[i])) return -1
        var j = i
        while (j < str.length && (isVowelChar(str[j]) || str[j] == 'ー')) j++
        if (j - i < 3) return -1
        val last = str[j - 1]
        val ok = last == 'ー' || (ElongationNodes.vowelOf(last) == ElongationNodes.vowelOf(str[j - 2]) && str[j - 2] != 'ー')
        if (!ok) return -1
        if (last != 'ー' && j < str.length && isKana(str[j])) return -1
        return j
    }
}

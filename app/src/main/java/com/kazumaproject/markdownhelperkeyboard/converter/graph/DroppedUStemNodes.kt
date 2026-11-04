package com.kazumaproject.markdownhelperkeyboard.converter.graph

/**
 * 口語で語末の う を落とした形容動詞語幹（かわいそ(う)すぎ・めんど(う)や・ほんと(う)に）を、ひらがなの 1 ノードにする。
 *
 * 以前は かわいそすぎやろ → カワイソ／カワイソス＋ぎ のように、辞書に無い読みがカタカナ語の片に崩れていた。
 * 条件: 読み [i, e)（3 字以上、末字が o 段）＋「う」が辞書の 形容動詞語幹（id.def 1931）の読みで、その後に字が続く（語末は作らない）。
 * ノード: 表記 = 読みのまま（ひらがな。強制的に漢字にしない）、品詞 = 1931、コスト = その語の最小語コスト＋[cost]。
 */
internal object DroppedUStemNodes {
    const val DEFAULT_COST = 300

    @Volatile
    @JvmStatic
    var enabled: Boolean = true

    @Volatile
    @JvmStatic
    var cost: Int = DEFAULT_COST

    const val KEIDOU: Short = 1931 // 名詞 形容動詞語幹
    const val MIN_LEN = 3
    const val MAX_LEN = 6

    private const val O_ROW = "おこごそぞとどのほぼぽもよろょ"

    /** 読み [s]（う を落とした形）が候補になりうるか（末字が o 段）。 */
    fun mayBe(s: String): Boolean = enabled && s.length in MIN_LEN..MAX_LEN && O_ROW.indexOf(s.last()) >= 0
}

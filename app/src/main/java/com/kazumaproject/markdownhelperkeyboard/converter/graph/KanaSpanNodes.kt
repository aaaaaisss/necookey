package com.kazumaproject.markdownhelperkeyboard.converter.graph

/**
 * 汎用ひらがなスパンノード（実験、既定オフ）。入力のうち全部ひらがなの区間（長さ [MIN_LEN]..[maxLen]）に、
 * 表記 = 読みそのままのノードを 1 つ足す（品詞 ID = [posId]、コスト = [base] + [perChar] × 長さ）。
 * 目的: 辞書にない口語・方言・崩した言い回しが短い漢字語の継ぎはぎ（県・念・阿寒…）に崩れず、かなのまま残ること。
 * 先頭が小書き・ん・っ・ゎ の区間は語頭になれないので足さない。辞書・表は使わない（追加サイズ 0）。
 * 連続漢字のペナルティはここでは入れない。評価は tools/sumire_lm/HANDOFF.md（2026-10-14 汎用かなスパン）。
 */
internal object KanaSpanNodes {
    const val MIN_LEN = 2
    const val DEFAULT_MAX_LEN = 6
    const val NOUN: Short = 1851   // 名詞 一般
    const val INTJ: Short = 2591   // 感動詞

    @Volatile @JvmStatic var enabled: Boolean = false
    @Volatile @JvmStatic var base: Int = 8000
    @Volatile @JvmStatic var perChar: Int = 500
    @Volatile @JvmStatic var maxLen: Int = DEFAULT_MAX_LEN
    @Volatile @JvmStatic var posId: Short = NOUN

    private const val NO_START = "ぁぃぅぇぉっゃゅょゎゕゖんー"

    fun isHira(c: Char): Boolean = c in 'ぁ'..'ゖ'

    fun mayApply(str: String): Boolean = enabled && str.length >= MIN_LEN

    fun cost(len: Int): Int = base + perChar * len

    /** 位置 [start] から始まるスパンの終端（排他）を短い順に。 */
    fun ends(str: String, start: Int): List<Int> {
        if (!isHira(str[start]) || str[start] in NO_START) return emptyList()
        val out = ArrayList<Int>(maxLen)
        var j = start
        while (j < str.length && j - start < maxLen && isHira(str[j])) {
            j++
            if (j - start >= MIN_LEN) out.add(j)
        }
        return out
    }

    /** NK_KANASPAN=base,perChar[,pos(n|i)[,maxLen]]（負の base = 無効）。 */
    fun configure(spec: String) {
        val f = spec.split(',').map { it.trim() }
        val b = f[0].toInt()
        enabled = b >= 0
        if (!enabled) return
        base = b
        f.getOrNull(1)?.let { perChar = it.toInt() }
        f.getOrNull(2)?.let { posId = if (it == "i") INTJ else NOUN }
        f.getOrNull(3)?.let { maxLen = it.toInt() }
    }
}

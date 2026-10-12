package com.kazumaproject.markdownhelperkeyboard.converter.graph

/**
 * 動詞の活用形の表記揺れ補正（同じ読み・同じ品詞の活用形で、Mozc 辞書の活用形コストの順位が
 * 終止形の順位と食い違い、終止形側の差が大きい組だけ、終止形で勝つ表記を活用形でも 1 位にする）。
 * 例: つとめる → 務める が 1 位なのに つとめた → 努めた になる問題。
 * 表は tools/verb_inflection_cost/gen.py が Mozc OSS 辞書から生成（resources/converter/verb_inflection_cost.tsv）。
 */
internal object VerbInflectionCost {
    // 動詞,自立 の左文脈 ID の範囲（Mozc id.def）。
    private const val VERB_FIRST = 577
    private const val VERB_LAST = 856

    private val table: HashMap<String, Int> by lazy {
        val map = HashMap<String, Int>(4096)
        VerbInflectionCost::class.java.getResourceAsStream("/converter/verb_inflection_cost.tsv")
            ?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val c = line.split('\t')
                    if (c.size == 4) c[3].toIntOrNull()?.let { map[key(c[0], c[1].toInt(), c[2])] = it }
                }
            }
        map
    }

    private fun key(yomi: String, leftId: Int, surface: String) = "$yomi\t$leftId\t$surface"

    fun delta(leftId: Short, yomi: String, surface: String): Int {
        val l = leftId.toInt()
        if (l < VERB_FIRST || l > VERB_LAST) return 0
        return table[key(yomi, l, surface)] ?: 0
    }
}

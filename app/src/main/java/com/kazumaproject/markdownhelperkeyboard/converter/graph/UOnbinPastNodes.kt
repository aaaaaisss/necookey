package com.kazumaproject.markdownhelperkeyboard.converter.graph

/**
 * 五段ワ行（思う・言う・買う・もらう …）のウ音便の過去・て形（関西・中国）を 1 ノードにする。
 *
 * - 思うた・言うて（基本形の読み＋た/て/たら）
 * - こうた（買うた）・おうた（会うた）・もろうた（もらった）・ゆうた（言うた）: 語幹末の o 段＋う を a 段＋う（い→ゆ）に戻して辞書を引く
 * - 縮約 おもた（思た）・もろた: う を落とした形（た/たら だけ。て は 表 等と衝突するので作らない）
 *
 * 以前は 辞書に語が無いので おもた → 重た・思わた、こうた → 公太 のような名詞片に崩れていた。
 * ノード: 左 ID = その動詞の 連用タ接続（思っ と同じ並び）、右 ID = た / たら / て の品詞、コスト = 基本形の語コスト＋[cost]
 * （縮約は +[CONTRACTED_EXTRA]、o 段の読み替えは +[SHIFT_EXTRA]）。表記 = 辞書の表記＋語尾（思うた・買うた・思た）、
 * 辞書の表記がかなだけの語の読み替え・縮約はかなのまま（もろうた・もろた）。辞書・表は追加しない。
 */
internal object UOnbinPastNodes {
    const val DEFAULT_COST = 1500
    const val CONTRACTED_EXTRA = 1000
    const val SHIFT_EXTRA = 300

    @Volatile
    @JvmStatic
    var enabled: Boolean = true

    @Volatile
    @JvmStatic
    var cost: Int = DEFAULT_COST

    /** 語尾: 読み、右 ID、縮約形（う を落とした形）に付けてよいか。 */
    data class Ending(val reading: String, val r: Short, val contracted: Boolean)

    // assets/id.def
    private const val TA: Short = 142    // た 助動詞 特殊・タ 基本形
    private const val TARA: Short = 140  // たら 助動詞 特殊・タ 仮定形
    private const val TE: Short = 348    // て 接続助詞

    /** 長い語尾を先に（たら が た より先に一致するように）。 */
    val ENDINGS: List<Ending> = listOf(Ending("たら", TARA, true), Ending("た", TA, true), Ending("て", TE, false))

    /** 五段ワ行 基本形の左 ID → 連用タ接続の左 ID（id.def の 動詞,自立/非自立,五段・ワ行ウ音便/促音便）。 */
    private val BASE_TO_TA: Map<Short, Short> = mapOf(
        799 to 802, 813 to 825, 814 to 826, 815 to 827, 816 to 828,
        1779 to 1813, 1780 to 1814, 1781 to 1815, 1782 to 1816, 1783 to 1817, 1784 to 1818,
        1785 to 1819, 1786 to 1820, 1788 to 1822, 1789 to 1823, 1790 to 1824,
    ).entries.associate { (k, v) -> k.toShort() to v.toShort() }

    fun taId(baseLeftId: Short): Short? = BASE_TO_TA[baseLeftId]

    /** o 段＋う を a 段＋う に戻す字（こう→かう、おう→あう、ろう→らう、ゆう→いう …）。 */
    private val O_TO_A: Map<Char, Char> = mapOf(
        'お' to 'あ', 'こ' to 'か', 'ご' to 'が', 'そ' to 'さ', 'ぞ' to 'ざ', 'と' to 'た', 'ど' to 'だ', 'の' to 'な',
        'ほ' to 'は', 'ぼ' to 'ば', 'ぽ' to 'ぱ', 'も' to 'ま', 'ろ' to 'ら', 'よ' to 'や', 'ゆ' to 'い',
    )

    /**
     * 語幹の読み [s]（語尾の手前まで）から引く辞書の読みの候補。
     * [Lookup.contracted] = う を落とした形（縮約）、[Lookup.shifted] = o 段を a 段に戻した。
     */
    data class Lookup(val dictYomi: String, val contracted: Boolean, val shifted: Boolean)

    fun lookups(s: String): List<Lookup> {
        if (s.length < 2) return emptyList()
        val out = ArrayList<Lookup>(2)
        if (s.last() == 'う') {
            out += Lookup(s, contracted = false, shifted = false)
            val a = O_TO_A[s[s.length - 2]]
            if (a != null) out += Lookup(s.substring(0, s.length - 2) + a + "う", contracted = false, shifted = true)
        } else {
            out += Lookup(s + "う", contracted = true, shifted = false)
            val a = O_TO_A[s.last()]
            if (a != null) out += Lookup(s.substring(0, s.length - 1) + a + "う", contracted = true, shifted = true)
        }
        return out
    }

    /** 辞書の表記 [tango]（基本形、う で終わる）と語幹の読み [stemYomi] からノードの表記。作れなければ null。 */
    fun surface(tango: String, stemYomi: String, lk: Lookup, ending: Ending): String? {
        val kanaOnly = tango.all { it in 'ぁ'..'ゖ' || it == 'ー' }
        return when {
            !tango.endsWith("う") -> null
            lk.shifted && (kanaOnly || lk.contracted) -> stemYomi + ending.reading
            lk.contracted -> tango.dropLast(1) + ending.reading
            else -> tango + ending.reading
        }
    }

    fun extraCost(lk: Lookup): Int = (if (lk.contracted) CONTRACTED_EXTRA else 0) + (if (lk.shifted) SHIFT_EXTRA else 0)

    /** 語幹の読みの最大長。 */
    const val MAX_STEM_LEN = 7

    fun mayApply(str: String): Boolean = enabled && str.length >= 3 && (str.indexOf('た') > 0 || str.indexOf('て') > 0)

    /** 語尾が始まりうる位置（た/て、昇順）。 */
    fun endingStarts(str: String): IntArray {
        var n = 0
        val a = IntArray(str.length)
        for (e in 2 until str.length) if (str[e] == 'た' || str[e] == 'て') a[n++] = e
        return a.copyOf(n)
    }

    /** [e] から始まる語尾（[contractedStem] なら縮約に付けてよいものだけ）。 */
    fun endingsAt(str: String, e: Int, contractedStem: Boolean): List<Ending> =
        ENDINGS.filter { str.startsWith(it.reading, e) && (!contractedStem || it.contracted) }

    /** ラティス LM で引く語: 語尾を外し、う を っ にした標準の連用タ接続（思た・思うた → 思っ、買うた → 買っ）。 */
    fun lmWord(tango: String): String {
        for (en in ENDINGS) {
            if (tango.length > en.reading.length && tango.endsWith(en.reading)) {
                val stem = tango.substring(0, tango.length - en.reading.length)
                return (if (stem.endsWith("う")) stem.dropLast(1) else stem) + "っ"
            }
        }
        return tango
    }
}

package com.kazumaproject.markdownhelperkeyboard.converter.graph

/**
 * 方言の活用語尾を、システム辞書の動詞語幹ノードに付けた 1 ノードにする（言え＋んかった＝言えんかった、待ち＋んさい、
 * し＋てもうた、待っ＋とる、き＋たげる …）。語尾だけのノードは作らない（取る/撮る や 歌 などの標準語が語尾に化けないように、
 * 直前が辞書の動詞活用形で、その活用形が語尾に合うときだけ）。
 *
 * 以前は 言えん＋買った（胃炎買った）・し＋て＋も＋歌（しても歌）・地位トマチン祭 のように、語尾の読みが漢字片に崩れていた。
 *
 * ノード: 表記 = 語幹の表記＋語尾（ひらがな）、左 ID = 語幹の左 ID、右 ID = 語尾の標準形の末尾の品詞（た・ん・いる・て・なさい）、
 * コスト = 語幹の語コスト＋[suffixCost]。辞書・表は追加しない（APK 増分はこのクラスだけ）。
 * 語幹の活用形は Mozc id.def の 動詞,自立（577..856）の活用形で判定する（表は id.def から生成）。
 */
internal object DialectInflectionNodes {
    const val DEFAULT_SUFFIX_COST = 1500

    @Volatile
    @JvmStatic
    var enabled: Boolean = true

    /** ラティス LM を語幹の表記で引く（評価用に切り替え可能）。 */
    @Volatile
    @JvmStatic
    var lmStemEnabled: Boolean = true

    /** 可能の否定 れん/れんかった（言えれん）を作るか（評価用に切り替え可能）。 */
    @Volatile
    @JvmStatic
    var potentialNegativeEnabled: Boolean = true

    /** 否定 ん（話さん・言えん）を作るか（評価用に切り替え可能）。SUFFIXES は初期化時に決まるので、切り替えは suffixesAt で見る。 */
    @Volatile
    @JvmStatic
    var plainNegativeEnabled: Boolean = true

    /** 語尾 1 つあたりの追加コスト（語幹の語コストに足す）。 */
    @Volatile
    @JvmStatic
    var suffixCost: Int = DEFAULT_SUFFIX_COST

    // 右 ID（assets/id.def）
    private const val TA: Short = 142        // た 助動詞 特殊・タ 基本形
    private const val N: Short = 45          // ん 助動詞 不変化型
    private const val IRU: Short = 1084      // いる 動詞 非自立 一段 基本形
    private const val TE: Short = 348        // て 接続助詞
    private const val NASAI: Short = 1718    // なさい 動詞 非自立 五段・ラ行特殊 命令ｉ なさる
    private const val TAGERU: Short = 1093   // たげる 動詞 非自立 一段 基本形
    private const val KA: Short = 325        // か 助詞 副助詞／並立助詞／終助詞

    /** 語幹の活用形（ビット）。 */
    const val MIZEN = 1   // 未然形: 行か・言え・食べ・せ・こ
    const val RENYOU = 2  // 連用形: 待ち・食べ・し・き
    const val TE_STEM = 4 // て/で が付く形: 連用タ接続（待っ・運ん・書い）＋一段/サ変/カ変の連用形
    const val MIZEN_U = 8 // 未然ウ接続: 行こ・入ろ・食べよ・しよ・こよ（う が付く形）

    // 動詞,自立 の 未然形 / 連用形 / 連用タ接続（＋一段・サ変・カ変の連用形）の左 ID（id.def から生成）
    private val MIZEN_IDS = intArrayOf(
        594, 595, 613, 614, 625, 637, 643, 694, 695, 696, 697, 698, 699, 700, 715, 725, 733, 741, 771, 772, 773, 774,
        793, 801, 821, 822, 823, 824, 839, 846, 854,
    )
    private val RENYOU_IDS = intArrayOf(
        596, 597, 615, 616, 626, 638, 644, 701, 702, 703, 704, 705, 706, 707, 717, 727, 735, 742, 783, 784, 785, 786,
        796, 803, 829, 830, 831, 832, 842, 847, 856,
    )
    private val TE_IDS = intArrayOf(
        596, 597, 615, 616, 626, 638, 701, 702, 703, 704, 705, 706, 707, 717, 726, 734, 779, 780, 781, 782, 795, 802,
        825, 826, 827, 828, 841,
    )
    // 動詞,自立 の 未然ウ接続（文語を除く、id.def から）
    private val MIZEN_U_IDS = intArrayOf(
        592, 593, 611, 612, 623, 634, 687, 688, 689, 690, 691, 692, 693, 714, 724, 732, 740, 767, 768, 769, 770, 792,
        800, 817, 818, 819, 820, 838,
    )
    private const val VERB_FIRST = 577
    private const val VERB_LAST = 856

    private val stemClassById: ByteArray = ByteArray(VERB_LAST - VERB_FIRST + 1).also { a ->
        for (id in MIZEN_IDS) a[id - VERB_FIRST] = (a[id - VERB_FIRST].toInt() or MIZEN).toByte()
        for (id in RENYOU_IDS) a[id - VERB_FIRST] = (a[id - VERB_FIRST].toInt() or RENYOU).toByte()
        for (id in TE_IDS) a[id - VERB_FIRST] = (a[id - VERB_FIRST].toInt() or TE_STEM).toByte()
        for (id in MIZEN_U_IDS) a[id - VERB_FIRST] = (a[id - VERB_FIRST].toInt() or MIZEN_U).toByte()
    }

    /**
     * 動詞,非自立 の て/で が付く形（買ってき・見てみ・置いとい・行っ: 〜てきたげる・〜てきとる）。id.def の
     * くる/来る 連用形、みる・いる 連用形、おく 連用タ接続、いく/行く 連用タ接続。
     */
    private val AUX_TE_IDS = intArrayOf(866, 876, 1206, 1225, 1331, 1385, 1389)

    fun stemClass(leftId: Short): Int {
        val l = leftId.toInt()
        if (l in AUX_TE_IDS) return TE_STEM
        return if (l < VERB_FIRST || l > VERB_LAST) 0 else stemClassById[l - VERB_FIRST].toInt()
    }

    /** 方言語尾: [reading] が語幹（活用形 [stem]）の直後に続くとき、右 ID [r] の 1 ノード。 */
    data class Suffix(val reading: String, val stem: Int, val r: Short)

    val SUFFIXES: List<Suffix> = buildList {
        // 否定過去（広島・西日本）: 言えんかった・行かんかった。関西の へん/ひん（行かへん・見ひん）
        add(Suffix("んかった", MIZEN, TA))
        for (s in listOf("へん", "ひん")) {
            add(Suffix(s, MIZEN, N))
            add(Suffix(s + "かった", MIZEN, TA))
        }
        // 否定 ん（広島・西日本）: 話さん・言えん・行かん・せん。辞書の 未然＋ん は 花さん・肺炎 のような名詞に負けていた
        // （2026-11-07 端末: あんまはなさんとこ・そうはいえん）。1 ノードにして経路に残す。
        add(Suffix("ん", MIZEN, N))
        // 可能の否定（広島・西日本、ら抜き＋ん）: 言えれん・食べれん・来れんかった（語幹 = 可能動詞/一段の未然形 言え・食べ）
        add(Suffix("れん", MIZEN, N))
        add(Suffix("れんかった", MIZEN, TA))
        // 意向の う が詰まった誘い（話し言葉）: 行こっか・入ろっか・食べよっか・しよっか。辞書の 行こう か は う が要り、
        // う の無い読みは 作業は色っか・いついこっか のように崩れていた（2026-11-07 端末）。
        add(Suffix("っか", MIZEN_U, KA))
        // 命令（広島）: 待ちんさい・食べんさい・しんさい
        add(Suffix("んさい", RENYOU, NASAI))
        // 〜てしまった（関西・中国）: 寝落ちしてもうた・食べてしもうた・忘れてもた
        for (t in listOf("て", "で")) {
            for (s in listOf("もうた", "しもうた", "もた", "しもた")) add(Suffix(t + s, TE_STEM, TA))
            for (s in listOf("もうて", "しもうて")) add(Suffix(t + s, TE_STEM, TE))
        }
        // 〜ている（西日本 とる/どる、山口・九州 ちょる）: 待っとる・読んどる・しとった・言うとらん
        for (s in listOf("とる", "どる", "ちょる")) add(Suffix(s, TE_STEM, IRU))
        for (s in listOf("とった", "どった", "ちょった")) add(Suffix(s, TE_STEM, TA))
        for (s in listOf("とらん", "どらん", "ちょらん")) {
            add(Suffix(s, TE_STEM, N))
            add(Suffix(s + "かった", TE_STEM, TA))
        }
        for (s in listOf("とって", "どって")) add(Suffix(s, TE_STEM, TE))
        // 〜てあげる の縮約（関西・中国）: 買うてきたげる・見たげる・読んだげる・したげた
        for (t in listOf("た", "だ")) {
            add(Suffix(t + "げる", TE_STEM, TAGERU))
            add(Suffix(t + "げた", TE_STEM, TA))
            add(Suffix(t + "げて", TE_STEM, TE))
        }
    }

    private val byFirst: Map<Char, List<Suffix>> = SUFFIXES.groupBy { it.reading[0] }
    private val longestFirst: List<String> = SUFFIXES.map { it.reading }.distinct().sortedByDescending { it.length }

    /** 合成ノードの表記から語尾を外した語幹の表記（ラティス LM 用）。語尾で終わらなければそのまま。 */
    fun lmStem(tango: String): String {
        if (!lmStemEnabled) return tango
        for (s in longestFirst) if (tango.length > s.length && tango.endsWith(s)) return tango.substring(0, tango.length - s.length)
        return tango
    }

    /** 入力 [str] の [end] から始まる、語幹の活用形 [stemClass] に付く語尾。 */
    fun suffixesAt(str: String, end: Int, stemClass: Int): List<Suffix> {
        if (stemClass == 0 || end >= str.length) return emptyList()
        val cands = byFirst[str[end]] ?: return emptyList()
        var out: MutableList<Suffix>? = null
        for (s in cands) {
            if (!potentialNegativeEnabled && s.reading[0] == 'れ') continue
            if (!plainNegativeEnabled && s.reading == "ん") continue
            if (s.stem and stemClass != 0 && str.startsWith(s.reading, end)) {
                (out ?: ArrayList<Suffix>(2).also { out = it }).add(s)
            }
        }
        return out ?: emptyList()
    }

    /** 語幹の読みの最大長（これより長い語幹には付けない）。 */
    const val MAX_STEM_LEN = 8

    fun mayApply(str: String): Boolean = enabled && str.any { it in byFirst }

    /** 何かの語尾が読みとして始まる位置（昇順）。語幹の活用形は問わない。 */
    fun suffixStarts(str: String): IntArray {
        var out: IntArray? = null
        var n = 0
        for (e in 1 until str.length) {
            val cands = byFirst[str[e]] ?: continue
            if (cands.none { str.startsWith(it.reading, e) }) continue
            val a = out ?: IntArray(4).also { out = it }
            if (n == a.size) out = a.copyOf(n * 2)
            out!![n++] = e
        }
        return out?.copyOf(n) ?: IntArray(0)
    }
}

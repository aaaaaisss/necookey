package com.kazumaproject.markdownhelperkeyboard.converter.graph

/**
 * 方言・口語のかな語（じゃけん・そやねん・ほんま・あかん・そゆこと・ってゆうか …）を 1 語のひらがなノードにする。
 * 従来は 県・念・本間・阿寒・祖ゆ… のような漢字片に崩れていた。
 *
 * 各語は「標準形の構成語」（読み, 品詞 ID）の並びを持つ（じゃけん = じゃ[助動詞 特殊・ジャ] + から[接続助詞]）。
 * ノードの左 ID = 先頭の構成語、右 ID = 末尾の構成語、コスト = 各構成語のシステム辞書での最小語コスト（その品詞 ID の語、表記は問わない）の和
 * ＋ [penalty]（全語共通）。表記 = 読みそのまま（ひらがな）。品詞 ID は assets/id.def。構成語が辞書に無い語は足さない。辞書・表は使わない（追加サイズ 0）。
 * 既定オン（P=1000）: held 5 セットで exact/lenient の低下なし（方言 held exact .557→.561、daily held -1 文 = 文頭 ねん→年 の 1 件）。
 * 除外（誤変換が多い/標準語と衝突）: いけん こんなん わし よる ゆう へん や やろう とる とった（取る/撮る がかなに崩れる） ってゆう（言うたり→ゆうたり）。
 */
internal object KanaDialectNodes {
    const val DEFAULT_PENALTY = 1000

    @Volatile
    @JvmStatic
    var enabled: Boolean = true

    @Volatile
    @JvmStatic
    var penalty: Int = DEFAULT_PENALTY

    /** 評価用: 読みがここにある語は出さない（NK_DIALECT_SKIP）。 */
    @Volatile
    @JvmStatic
    var skip: Set<String> = emptySet()

    /** 標準形の構成語: 読み [yomi] の、左 ID が [posId] の語。 */
    data class Part(val yomi: String, val posId: Short)

    /**
     * [reading] = 方言形の読み（= 表記）。[afterTe] = 直前の読みが て/で のときだけ（はる）。
     * [after] != null なら文頭では出さず、直前の読みの 1 字が [after] のどれかのときだけ（ねん: 名詞の直後
     * = 年とともに・記念・去年 を避け、用言・助動詞の語尾 ん/い/う/や/る/え/た/す の後だけ）。
     */
    data class Entry(val reading: String, val parts: List<Part>, val afterTe: Boolean = false, val after: String? = null)

    // 品詞 ID（assets/id.def）
    private const val KARA: Short = 330      // から 接続助詞
    private const val JA: Short = 137        // じゃ 助動詞 特殊・ジャ 基本形
    private const val DA: Short = 165        // だ 助動詞 特殊・ダ 基本形
    private const val DARO: Short = 166      // だろ 助動詞 特殊・ダ 未然形
    private const val U: Short = 35          // う 助動詞 不変化型
    private const val NO: Short = 2080       // の 名詞 非自立
    private const val TE: Short = 348        // て 接続助詞
    private const val IRU: Short = 1084      // いる 動詞 非自立 一段 基本形
    private const val I_REN: Short = 1206    // い 動詞 非自立 一段 連用形
    private const val TA: Short = 142        // た 助動詞 特殊・タ 基本形
    private const val SURU_REN: Short = 638  // し サ変・スル 連用形
    private const val SOU: Short = 21        // そう 副詞 助詞類接続
    private const val KOU: Short = 17        // こう
    private const val DOU: Short = 22        // どう
    private const val AA: Short = 16         // ああ 副詞 助詞類接続
    private const val NA: Short = 421        // な 終助詞
    private const val KEDO: Short = 334      // けど 接続助詞
    private const val NOUN: Short = 1851     // 名詞 一般（ほんとう）
    private const val KEIDOU: Short = 1931   // 名詞 形容動詞語幹（だめ）
    private const val INTJ: Short = 2591     // 感動詞（すみません）
    private const val MASE: Short = 252      // ませ 特殊・マス 未然形
    private const val N: Short = 45          // ん 不変化型
    private const val ADV: Short = 16        // 副詞 助詞類接続（とても）
    private const val KA: Short = 325        // か 副助詞／並立助詞／終助詞
    private const val MO: Short = 284        // も 係助詞
    private const val SHIRE: Short = 694     // しれ 一段 未然形
    private const val IU: Short = 814        // いう 五段・ワ行促音便 基本形
    private const val ITT: Short = 826      // いっ 五段・ワ行促音便 連用タ接続
    private const val KOTO: Short = 2066     // こと 名詞 非自立
    private const val TTE: Short = 391       // って 格助詞 連語

    private fun p(y: String, id: Short) = Part(y, id)
    private fun e(r: String, vararg parts: Part, afterTe: Boolean = false, after: String? = null) = Entry(r, parts.toList(), afterTe, after)

    /** ねん の直前に許す字（用言・助動詞の語尾）。文頭・名詞の直後（年とともに・きねん・きょねん）では出さない。 */
    const val NEN_AFTER = "んいうやるえたす"

    val ENTRIES: List<Entry> = buildList {
        for (r in listOf("けん", "けぇ")) add(e(r, p("から", KARA)))
        for (r in listOf("じゃけん", "じゃけぇ")) add(e(r, p("じゃ", JA), p("から", KARA)))
        add(e("やけん", p("だ", DA), p("から", KARA)))
        add(e("ねん", p("の", NO), p("だ", DA), after = NEN_AFTER))
        add(e("てんねん", p("て", TE), p("いる", IRU), p("の", NO), p("だ", DA)))
        for (r in listOf("せや", "そや")) {
            add(e(r, p("そう", SOU), p("だ", DA)))
            add(e(r + "な", p("そう", SOU), p("だ", DA), p("な", NA)))
            add(e(r + "けど", p("そう", SOU), p("だ", DA), p("けど", KEDO)))
            add(e(r + "から", p("そう", SOU), p("だ", DA), p("から", KARA)))
        }
        add(e("そやねん", p("そう", SOU), p("だ", DA), p("の", NO), p("だ", DA)))
        add(e("ほんま", p("ほんとう", NOUN)))
        add(e("あかん", p("だめ", KEIDOU)))
        add(e("まへん", p("ませ", MASE), p("ん", N)))
        add(e("すんまへん", p("すみません", INTJ)))
        add(e("はる", p("いる", IRU), afterTe = true))
        add(e("しとる", p("し", SURU_REN), p("て", TE), p("いる", IRU)))
        add(e("しとった", p("し", SURU_REN), p("て", TE), p("い", I_REN), p("た", TA)))
        add(e("ちょる", p("いる", IRU)))
        add(e("ちょった", p("い", I_REN), p("た", TA)))
        for (r in listOf("じゃろう", "だべ", "だっぺ")) add(e(r, p("だろ", DARO), p("う", U)))
        add(e("なまら", p("とても", ADV)))
        add(e("かもしれん", p("か", KA), p("も", MO), p("しれ", SHIRE), p("ん", N)))
        add(e("そゆこと", p("そう", SOU), p("いう", IU), p("こと", KOTO)))
        add(e("どゆこと", p("どう", DOU), p("いう", IU), p("こと", KOTO)))
        add(e("こゆこと", p("こう", KOU), p("いう", IU), p("こと", KOTO)))
        add(e("そうゆう", p("そう", SOU), p("いう", IU)))
        add(e("こうゆう", p("こう", KOU), p("いう", IU)))
        add(e("どうゆう", p("どう", DOU), p("いう", IU)))
        add(e("ああゆう", p("ああ", AA), p("いう", IU)))
        for (r in listOf("ってゆうか", "っちゅうか", "てゆうか", "ちゅうか")) add(e(r, p("って", TTE), p("いう", IU), p("か", KA)))
        add(e("っちゅう", p("って", TTE), p("いう", IU)))
        for (r in listOf("ゆって", "ゆうて")) add(e(r, p("いっ", ITT), p("て", TE)))
        add(e("ゆった", p("いっ", ITT), p("た", TA)))
    }

    private val byFirst: Map<Char, List<Entry>> = ENTRIES.groupBy { it.reading[0] }

    fun mayApply(str: String): Boolean = enabled && str.any { it in byFirst }

    /** str の位置 [start] から始まる語（読みが一致するもの）。 */
    fun entriesAt(str: String, start: Int): List<Entry> {
        val cands = byFirst[str[start]] ?: return emptyList()
        return cands.filter { en ->
            en.reading !in skip &&
                str.startsWith(en.reading, start) &&
                (!en.afterTe || (start > 0 && (str[start - 1] == 'て' || str[start - 1] == 'で'))) &&
                (en.after == null || (start > 0 && str[start - 1] in en.after))
        }
    }
}

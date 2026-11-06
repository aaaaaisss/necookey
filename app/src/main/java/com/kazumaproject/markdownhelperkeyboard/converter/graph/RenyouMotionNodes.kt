package com.kazumaproject.markdownhelperkeyboard.converter.graph

/**
 * 口語の 動詞連用形＋行く/来る（に を落とした形: 買い行く・食べ行った・飲み来る）を 1 ノードにする。
 *
 * 以前は なんかかいいく → なんか会行く のように、に の無い 連用形＋行く が辞書の名詞（会・回）＋行く に取られていた。
 * 語幹 = 入力 str[i, e) のシステム辞書の動詞連用形（読み 2 字以上、DialectInflectionNodes.RENYOU）、
 * 続き = str[e, e+k) の 行く/来る の活用形（読み 2 字以上、表記が 行/来 で始まる動詞,自立）。
 * ノード: 表記 = 語幹の表記＋続きの表記、左 ID = 語幹の左 ID、右 ID = 続きの右 ID、コスト = 両方の語コスト＋[penalty]。
 * ラティス LM は語幹の表記で引く（[lmStem]）。辞書・表は追加しない。
 */
internal object RenyouMotionNodes {
    const val DEFAULT_PENALTY = 2000

    @Volatile
    @JvmStatic
    var enabled: Boolean = true

    @Volatile
    @JvmStatic
    var penalty: Int = DEFAULT_PENALTY

    const val MIN_STEM_LEN = 2
    const val MAX_STEM_LEN = 6
    const val MIN_MOTION_LEN = 2
    const val MAX_MOTION_LEN = 3

    private const val VERB_FIRST = 577
    private const val VERB_LAST = 856

    /** 続きの読みの先頭（行く い…・来る く…）。 */
    fun mayStartAt(str: String, e: Int): Boolean = e < str.length && (str[e] == 'い' || str[e] == 'く')

    fun isMotionVerb(leftId: Short, tango: String): Boolean =
        leftId in VERB_FIRST..VERB_LAST && tango.isNotEmpty() && (tango[0] == '行' || tango[0] == '来')

    fun isRenyouStem(leftId: Short): Boolean =
        DialectInflectionNodes.stemClass(leftId) and DialectInflectionNodes.RENYOU != 0 &&
            leftId.toInt() in VERB_FIRST..VERB_LAST

    /** 合成ノードの表記から 行/来 以降を外した語幹の表記（ラティス LM 用）。 */
    fun lmStem(tango: String): String {
        val k = maxOf(tango.lastIndexOf('行'), tango.lastIndexOf('来'))
        return if (k > 0) tango.substring(0, k) else tango
    }
}

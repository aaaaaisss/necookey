package com.kazumaproject.graph

enum class MozcNodeType {
    NOR,
    BOS,
    EOS,
    CON,
    HIS,
}

enum class CandidateSource {
    SYSTEM,
    UNKNOWN,
    USER_DICTIONARY,
    LEARNED_DICTIONARY,
}

object MozcNodeAttributes {
    const val NONE = 0
    const val STARTS_WITH_PARTICLE = 1

    /** 方言の活用語尾を動詞語幹に付けた合成ノード（DialectInflectionNodes）。ラティス LM は語幹の表記で引く。 */
    const val DIALECT_INFLECTION = 1 shl 8

    /** 五段ワ行のウ音便過去の合成ノード（思うた・思た、UOnbinPastNodes）。ラティス LM は標準の連用タ接続（思っ）で引く。 */
    const val U_ONBIN_PAST = 1 shl 9

    /** ー を母音の伸ばしとして読んだひらがなの語（そー・よー、LongVowelNodes）。ラティス LM は置き換えた読み（そう）で引く。 */
    const val LONG_VOWEL = 1 shl 10

    /** 動詞の連用形＋行く/来る の合成ノード（買い行く・食べ来る、RenyouMotionNodes）。ラティス LM は前の動詞の表記で引く。 */
    const val RENYOU_MOTION = 1 shl 11
}

data class Node(
    val l: Short,
    val r: Short,
    var score: Int,
    var f: Int,
    var g: Int = 0,
    val tango: String,
    val len: Short,
    val yomiUsed: String,
    var sPos: Int,
    var prev: Node? = null,
    var next: Node? = null,
    var adjustedScore: Int = score,
    val mozcNodeType: MozcNodeType = MozcNodeType.NOR,
    val mozcAttributes: Int = MozcNodeAttributes.NONE,
    val candidateSource: CandidateSource = CandidateSource.SYSTEM,
) {
    override fun toString(): String {
        return this.tango
    }
}

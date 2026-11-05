package com.kazumaproject.markdownhelperkeyboard.converter.graph

/**
 * システム辞書（app/dictionary-src/system の LOUDS 表記トライ）の表記誤りを読み込み時に直す。
 * 表記トライは LOUDS（ノード番号 = 幅優先順）で、1 語の表記だけを書き換えると先頭字を共有する他の語のノード番号がずれ、
 * トークン配列の参照をすべて作り直すことになるため、辞書は再生成せずここで置き換える（APK 増分はこのクラスだけ）。
 * 辞書を作り直すときは元データ側を直してここから消す。
 */
internal object SystemDictionaryErrata {
    @Volatile
    @JvmStatic
    var enabled: Boolean = true

    /** 誤った表記 → 正しい表記。 */
    private val FIXES: Map<String, String> = mapOf(
        // すもももももももものうち: 李（すもも）を 季 と誤記
        "季も桃も桃のうち" to "李も桃も桃のうち",
    )
    private val FIRST_CHARS: Set<Char> = FIXES.keys.mapTo(HashSet()) { it[0] }
    private val LENGTHS: Set<Int> = FIXES.keys.mapTo(HashSet()) { it.length }

    /**
     * 読みと表記が合っていない誤った語（読み → 表記）。ラティスに入れない（[isDropped]）。
     * - ついた → 一: 一（ついたち）の読みが欠けた語。文末の「着いた・付いた」を「一」にしていた（2026-11-05 端末「もうついた？ → もう一？」）。
     */
    private val DROPS: Map<String, Set<String>> = mapOf(
        "ついた" to setOf("一"),
    )

    /** 読み [yomi] の表記 [tango] が誤った語として外すものか。 */
    fun isDropped(yomi: String, tango: String): Boolean = enabled && DROPS[yomi]?.contains(tango) == true

    fun fix(tango: String): String {
        if (!enabled || tango.isEmpty() || tango[0] !in FIRST_CHARS || tango.length !in LENGTHS) return tango
        return FIXES[tango] ?: tango
    }
}

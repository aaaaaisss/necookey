package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.graph.ContextLexicon
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.LatticeLmCost
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/** 画面文脈の語がラティスで効くか（A1 文脈ノード加点・A2 カタカナ語の一時辞書、実機経路）。 */
class ContextLexiconConversionTest {
    @After
    fun tearDown() {
        ContextLexicon.provider = null
        LatticeLmCost.table = null
    }

    private fun rank(conv: DeviceConfigConversion, engine: com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine, reading: String, want: String): Int =
        conv.deviceTopRow(engine, reading).map { it.string }.indexOf(want)

    @Test
    fun contextWordsComeUpInTheLattice() {
        LatticeLmCost.table = DeviceConfigConversion.latticeTableFile()?.inputStream()?.use { LatticeLmCost.load(it) }
        val conv = DeviceConfigConversion()
        val engine = conv.newEngine()
        val cases = listOf(
            Triple("まるめ", "丸目", "丸目ヘッドライトが特徴の新型"),
            Triple("きせい。", "規制。", "排ガス規制の強化について"),
            Triple("ぽりめたるぐれー", "ポリメタルグレー", "ボディカラーはポリメタルグレーとジルコンサンドメタリック"),
            Triple("ぽりめたるぐれーいいね", "ポリメタルグレーいいね", "ボディカラーはポリメタルグレーとジルコンサンドメタリック"),
        )
        for ((reading, want, screen) in cases) {
            ContextLexicon.provider = null
            val before = rank(conv, engine, reading, want)
            val lex = ContextLexicon.build(1, Long.MAX_VALUE, mapOf(ContextLexicon.LAYER_SCREEN to screen))
            ContextLexicon.provider = { lex }
            val after = rank(conv, engine, reading, want)
            println("CTX $reading -> $want: before=${before + 1} after=${after + 1}")
            assertTrue("$reading → $want should be in top 3 with context (was ${before + 1}, now ${after + 1})", after in 0..2)
            assertTrue("$reading → $want should not get worse with context", before < 0 || after <= before)
        }
    }
}

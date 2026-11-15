package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ScreenContextStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextLexiconTest {
    @After
    fun tearDown() {
        ScreenContextStore.onKeyboardHidden()
        ScreenContextStore.clear()
    }

    private fun lex(layer: Int, text: String) = ContextLexicon.build(1, Long.MAX_VALUE, mapOf(layer to text))

    @Test
    fun substringsOfKanjiRunsGetLayerBonus() {
        val l = lex(ContextLexicon.LAYER_SCREEN, "丸目ヘッドライトの新型")
        assertEquals(ContextLexicon.B_SCREEN, l.nodeBonus("丸目"))
        assertEquals(ContextLexicon.B_SCREEN, l.nodeBonus("新型"))
        assertEquals(ContextLexicon.B_SCREEN, l.nodeBonus("ヘッドライト"))
        assertEquals(0, l.nodeBonus("丸")) // 1 字の漢字は対象外
        assertEquals(0, l.nodeBonus("の"))
        assertEquals(0, l.nodeBonus("丸め"))
    }

    @Test
    fun katakanaPartsOnlyAsWholeRuns() {
        val l = lex(ContextLexicon.LAYER_EDITOR, "カワサキのバイク")
        assertEquals(0, l.nodeBonus("カワ")) // かわ → カワ にしない
        assertEquals(ContextLexicon.B_EDITOR, l.nodeBonus("カワサキ"))
    }

    @Test
    fun stemsGiveHalfBonusToInflectedNodes() {
        val l = lex(ContextLexicon.LAYER_COMMIT, "前に言ってたやつ")
        assertEquals(ContextLexicon.B_COMMIT / 2, l.nodeBonus("言っ"))
        assertEquals(0, l.nodeBonus("行っ"))
        assertEquals(0, l.nodeBonus("言")) // 1 字の漢字のノードは対象外
    }

    @Test
    fun layerMaxAndKatakanaDictionary() {
        val l = ContextLexicon.build(
            1, Long.MAX_VALUE,
            mapOf(ContextLexicon.LAYER_SCREEN to "ポリメタルグレーとアーキテクチャ", ContextLexicon.LAYER_COMMIT to "アーキテクチャ"),
        )
        assertEquals(ContextLexicon.B_COMMIT, l.nodeBonus("アーキテクチャ"))
        val at = l.katakanaAt("ぽりめたるぐれーです", 0)
        assertEquals(listOf("ポリメタルグレー"), at.map { it.surface })
        assertEquals("ぽりめたるぐれー", at.single().reading)
        assertEquals(listOf("アーキテクチャ"), l.katakanaCompletions("あーきてく").map { it.surface })
        assertTrue(l.katakanaCompletions("あーきてくちゃ").isEmpty()) // 同じ長さは補完しない
        assertEquals(ContextLexicon.LAYER_SCREEN or ContextLexicon.LAYER_COMMIT, l.layersOf("アーキテクチャ"))
    }

    @Test
    fun storeBuildsLexiconOnceAndHonorsTtl() {
        ScreenContextStore.recordEditor("a", "丸目のライト", "", 0L)
        val l1 = ScreenContextStore.lexicon("a", 1000L)
        assertNotNull(l1)
        // 同じ中身の入力欄の記録（変換ごと）では作り直さない
        ScreenContextStore.recordEditor("a", "丸目のライト", "", 2000L)
        assertSame(l1, ScreenContextStore.lexicon("a", 3000L))
        assertEquals(ContextLexicon.B_EDITOR, l1!!.nodeBonus("丸目"))
        // 期限が来たら消える（入力欄 10 分）
        assertNull(ScreenContextStore.lexicon("a", 2000L + ScreenContextStore.EDITOR_TTL_MS + 1))
    }

    @Test
    fun screenLayerAndCorrectionLayerBits() {
        ScreenContextStore.onKeyboardShown("a")
        ScreenContextStore.recordScreen("a", listOf("丸目ヘッドライトが特徴", "溜める"), 0L)
        val l = ScreenContextStore.lexicon("a", 0L)!!
        assertEquals(ContextLexicon.B_SCREEN, l.nodeBonus("丸目"))
        assertEquals(ContextLexicon.B_SCREEN / 2, l.nodeBonus("溜め"))
        assertEquals("001", ScreenContextStore.contextLayersFor("a", "丸め", "丸目", 0L))
        assertEquals("000", ScreenContextStore.contextLayersFor("a", "川に", "革に", 0L))
        assertNull(ScreenContextStore.lexicon("a", ScreenContextStore.SCREEN_TTL_MS + 1))
    }
}

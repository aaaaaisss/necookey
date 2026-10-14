package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.KanjiRunPenalty
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KanjiRunPenaltyTest {
    private fun node(id: Int, tango: String, yomi: String, score: Int) =
        Node(l = id.toShort(), r = id.toShort(), score = score, f = score, tango = tango, len = yomi.length.toShort(), yomiUsed = yomi, sPos = 1)

    @After
    fun reset() {
        KanjiRunPenalty.configure("-1")
    }

    @Test
    fun defaultOff() {
        assertFalse(KanjiRunPenalty.enabled)
        assertEquals(0, KanjiRunPenalty.cost(node(1924, "阿寒", "あかん", 9000), node(1851, "野犬", "やけん", 9000)))
    }

    @Test
    fun patchworkPenalized() {
        KanjiRunPenalty.configure("3000,6000")
        val akan = node(1924, "阿寒", "あかん", 9000)
        val yaken = node(1851, "野犬", "やけん", 8000)
        assertEquals(3000, KanjiRunPenalty.cost(akan, yaken))
        // 頻度の高い語（コスト < 閾値）は対象外
        assertEquals(0, KanjiRunPenalty.cost(akan, node(1851, "料理", "りょうり", 3000)))
        assertEquals(0, KanjiRunPenalty.cost(node(1851, "中華", "ちゅうか", 4000), yaken))
        // a = 前の語の頻度は問わない
        KanjiRunPenalty.configure("3000,6000,a")
        assertEquals(3000, KanjiRunPenalty.cost(node(1851, "中華", "ちゅうか", 4000), yaken))
        // nodeMaxLen = 1: 後ろの語が 1 字のときだけ（夕方+察 は対象、海軍+将校 は対象外）
        KanjiRunPenalty.configure("3000,4500,a,1")
        assertEquals(3000, KanjiRunPenalty.cost(node(1851, "夕方", "ゆうがた", 3000), node(1851, "察", "さっ", 11000)))
        assertEquals(0, KanjiRunPenalty.cost(node(1851, "海軍", "かいぐん", 3000), node(1851, "将校", "しょうこう", 7000)))
    }

    @Test
    fun exemptions() {
        KanjiRunPenalty.configure("3000,0")
        val hiro = node(1924, "広島", "ひろしま", 7000)
        // 名詞,接尾（県）・数（三+月）・かな語（買っ+て）・3 文字以上・文頭・接頭詞の後は対象外
        assertEquals(0, KanjiRunPenalty.cost(hiro, node(1990, "県", "けん", 7000)))
        assertEquals(0, KanjiRunPenalty.cost(node(2046, "三", "さん", 7000), node(1851, "月", "がつ", 7000)))
        assertEquals(0, KanjiRunPenalty.cost(node(700, "買っ", "かっ", 7000), node(348, "て", "て", 7000)))
        assertEquals(0, KanjiRunPenalty.cost(hiro, node(1851, "広島県", "ひろしまけん", 7000)))
        assertEquals(0, KanjiRunPenalty.cost(BOS, node(1851, "野犬", "やけん", 9000)))
        assertEquals(0, KanjiRunPenalty.cost(node(2600, "超", "ちょう", 7000), node(1851, "野犬", "やけん", 9000)))
    }
}

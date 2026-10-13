package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.PosPairPenalty
import org.junit.Assert.assertEquals
import org.junit.Test

class PosPairPenaltyTest {
    private fun node(id: Int, tango: String, yomi: String) =
        Node(l = id.toShort(), r = id.toShort(), score = 0, f = 0, tango = tango, len = yomi.length.toShort(), yomiUsed = yomi, sPos = 1)

    private val ta = Node(l = 142, r = 142, score = 0, f = 0, tango = "た", len = 1, yomiUsed = "た", sPos = 0)
    private val yon = node(2046, "四", "よ")

    @Test
    fun oneKanaNumeralAfterAuxiliaryIsPenalized() {
        assertEquals(PosPairPenalty.ONE_KANA_NUMERAL_PENALTY, PosPairPenalty.cost(ta, yon))
    }

    @Test
    fun numeralContextsAreNotPenalized() {
        assertEquals(0, PosPairPenalty.cost(BOS, yon))
        assertEquals(0, PosPairPenalty.cost(node(2051, "十", "じゅう"), node(2046, "五", "ご")))
        assertEquals(0, PosPairPenalty.cost(node(2640, "何", "なん"), yon))
        assertEquals(0, PosPairPenalty.cost(ta, node(2046, "四", "よん")))
    }

    @Test
    fun verbRenyouFollowedBySuruIsPenalized() {
        assertEquals(PosPairPenalty.RENYOU_SURU_PENALTY, PosPairPenalty.cost(node(842, "話し", "はなし"), node(638, "し", "し")))
        assertEquals(0, PosPairPenalty.cost(node(1848, "話", "はなし"), node(638, "し", "し")))
    }

    @Test
    fun hairuAfterTeOrQuoteIsPenalized() {
        val p = PosPairPenalty.HAIRU_PENALTY
        val hairu = node(744, "入る", "はいる")
        val haitt = node(744, "入っ", "はいっ")
        assertEquals(p, PosPairPenalty.cost(node(348, "て", "て"), hairu))
        assertEquals(p, PosPairPenalty.cost(node(349, "で", "で"), hairu))
        assertEquals(p, PosPairPenalty.cost(node(387, "と", "と"), haitt))
        assertEquals(p, PosPairPenalty.cost(node(2600, "そう", "そう"), haitt))
        // 入って（熱くて入って）・格助詞「で」（車で入る）・副詞「ぐっと」の後は対象外
        assertEquals(0, PosPairPenalty.cost(node(348, "て", "て"), haitt))
        assertEquals(0, PosPairPenalty.cost(node(370, "で", "で"), hairu))
        assertEquals(0, PosPairPenalty.cost(node(2600, "ぐっと", "ぐっと"), haitt))
        assertEquals(0, PosPairPenalty.cost(node(2000, "部屋", "へや"), hairu))
        val old = PosPairPenalty.hairuPenalty
        try {
            PosPairPenalty.hairuPenalty = 0
            assertEquals(0, PosPairPenalty.cost(node(348, "て", "て"), hairu))
        } finally {
            PosPairPenalty.hairuPenalty = old
        }
    }
}

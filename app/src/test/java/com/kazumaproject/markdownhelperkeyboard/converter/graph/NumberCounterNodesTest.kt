package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.graph.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 数＋助数詞を算用数字にするノード（辞書なしの単体。辞書ありの 1 位は quality/ArabicNumeralConversionTest）。 */
class NumberCounterNodesTest {

    private fun node(yomi: String, tango: String, cost: Int, source: CandidateSource = CandidateSource.SYSTEM, l: Short = 1851, r: Short = 1851) =
        Node(l = l, r = r, score = cost, f = cost, g = cost, tango = tango, len = yomi.length.toShort(), yomiUsed = yomi, sPos = 0, candidateSource = source)

    /** 読み全体を覆うノードの表記をコスト順に。 */
    private fun whole(str: String, existing: List<Node> = emptyList()): List<String> =
        NumberCounterNodes.build(str, 0, existing).filter { it.end == str.length }.sortedBy { it.cost }.map { it.tango }

    @Test
    fun kanaNumeralPlusCounterIsArabicFirst() {
        assertEquals(listOf("2代目", "二代目", "2台目", "二台目"), whole("にだいめ"))
        assertEquals("3代目", whole("さんだいめ").first())
        assertEquals("3つ", whole("みっつ").first())
        assertTrue("三つ" in whole("みっつ"))
        assertEquals("9度", whole("くど").first())
        assertEquals("6分", whole("ろくぶ").first())
        assertEquals("24時間", whole("にじゅうよじかん").first())
        assertEquals("34箇所", whole("さんじゅうよっかしょ").first())
        assertEquals("10分", whole("じゅっぷん").first())
        assertEquals("1回", whole("いっかい").first())
    }

    @Test
    fun typedDigitsPlusCounter() {
        assertEquals("200台", whole("200だい").first())
        assertEquals("1255台", whole("1255だい").first())
        assertEquals("9度台", whole("9どだい").first())
        assertEquals("8度", whole("8ど").first())
        assertEquals("2代目", whole("2だいめ").first())
        // 全角で打っても半角が先、全角は次点
        assertEquals(listOf("200台", "２００台"), whole("２００だい").filter { it.endsWith("台") }.take(2))
        // 数字だけのノード（1 から）
        val one = NumberCounterNodes.build("1から", 0, emptyList())
        assertTrue(one.any { it.end == 1 && it.tango == "1" })
        // 数字の途中からは作らない
        assertTrue(NumberCounterNodes.build("12だい", 1, emptyList()).isEmpty())
        assertEquals(3, NumberCounterNodes.digitRunEnd("1.5ど", 0))
    }

    @Test
    fun nativeDaysNeedSuffix() {
        assertEquals("5日前", whole("いつかまえ").first())
        assertTrue("五日前" in whole("いつかまえ"))
        assertEquals("8日後", whole("ようかご").first())
        assertEquals("10日間", whole("とおかかん").first())
        assertEquals("5日目", whole("いつかめ").first())
        // いつか だけ・いつか＋ご飯 は作らない
        assertTrue(whole("いつか").isEmpty())
        assertTrue(NumberCounterNodes.build("いつかごはん", 0, emptyList()).none { it.tango.startsWith("5") })
        assertTrue(NumberCounterNodes.build("いつかかんがえる", 0, emptyList()).none { it.tango.startsWith("5") })
    }

    @Test
    fun idiomsStayKanji() {
        for (idiom in listOf("いちど", "いちばん", "いちぶ", "いちじ", "いちにち")) {
            assertTrue(idiom, whole(idiom).isEmpty())
        }
        // 二度と
        assertTrue(NumberCounterNodes.build("にどと", 0, emptyList()).none { it.end == 2 })
        // ひとつ・ひとり は扱わない
        assertTrue(whole("ひとつ").isEmpty())
        assertTrue(whole("ひとり").isEmpty())
        // し は数にしない
        assertTrue(whole("しかい").isEmpty())
    }

    @Test
    fun dictionaryGuards() {
        // 辞書に漢数字の語があれば同じ品詞・コスト − 1 の双子
        val twin = NumberCounterNodes.build("にだいめ", 0, listOf(node("にだいめ", "二代目", 4000, l = 1900, r = 1901)))
            .filter { it.end == 4 }.sortedBy { it.cost }
        assertEquals("2代目", twin.first().tango)
        assertEquals(3999, twin.first().cost)
        assertEquals(1900.toShort(), twin.first().l)
        assertFalse(twin.any { it.tango == "二代目" }) // 漢数字は辞書の語のまま
        // 同じ読みに数でない語（誤解）があれば負ける
        val gokai = NumberCounterNodes.build("ごかい", 0, listOf(node("ごかい", "誤解", 3000)))
        assertTrue(gokai.first { it.tango == "5回" }.cost > 3000 + NumberCounterNodes.NON_NUMERIC_MARGIN - 1)
        // 数の文脈（くど＋ろくぶ）なら数でない同じ読みの語（苦土）にも負けない
        val kudo = NumberCounterNodes.build("くどろくぶ", 0, listOf(node("くど", "苦土", 3000)))
        assertTrue(kudo.first { it.tango == "9度" }.cost < 3000)
        // この先まで続く数でない語（くどい）があれば作らない
        assertTrue(NumberCounterNodes.build("くどい", 0, listOf(node("くどい", "くどい", 3000))).none { it.end == 2 })
        // 長い辞書語（日本語・六本木）があれば作らない。終わりが数＋助数詞（二時間）なら作る
        assertTrue(NumberCounterNodes.build("にほんご", 0, listOf(node("にほんご", "日本語", 3000))).none { it.end == 3 })
        assertTrue(NumberCounterNodes.build("ろっぽんぎ", 0, listOf(node("ろっぽんぎ", "六本木", 3000))).none { it.end == 4 })
        assertTrue(NumberCounterNodes.build("にじかん", 0, listOf(node("にじかん", "二時間", 3000))).any { it.end == 2 && it.tango == "2時" })
        // 学習した漢数字には勝たない
        val learned = NumberCounterNodes.build("にだいめ", 0, listOf(node("にだいめ", "二代目", 2000, CandidateSource.LEARNED_DICTIONARY)))
        assertTrue(learned.first { it.tango == "2代目" }.cost > 2000)
    }

    @Test
    fun parseAndLmWord() {
        assertEquals(24L, NumberCounterNodes.parseNumeral("にじゅうよん"))
        assertEquals(10L, NumberCounterNodes.parseNumeral("じゅっ"))
        assertEquals(34L, NumberCounterNodes.parseNumeral("さんじゅうよっ"))
        assertNull(NumberCounterNodes.parseNumeral("か"))
        assertEquals("二代目", NumberCounterNodes.lmWord("2代目"))
        assertEquals("二百台", NumberCounterNodes.lmWord("200台"))
        assertEquals("九度六分", NumberCounterNodes.kanjiForm("9度6分"))
        assertEquals("二代目の社長", NumberCounterNodes.kanjiForm("2代目の社長"))
        assertEquals("いつか", NumberCounterNodes.kanjiForm("いつか"))
    }
}

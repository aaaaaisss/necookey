package com.kazumaproject.markdownhelperkeyboard.converter.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-11-04: ウ音便過去（UOnbinPastNodes）・語末の伸ばし（ElongationNodes）・う落ち語幹（DroppedUStemNodes）・〜たげる の純粋な部分。 */
class ColloquialFormNodesTest {

    @Test
    fun uOnbinLookups() {
        // おも（た）→ 思う を縮約で、思う（た）はそのまま
        assertEquals(UOnbinPastNodes.Lookup("おもう", contracted = true, shifted = false), UOnbinPastNodes.lookups("おも").first())
        val kou = UOnbinPastNodes.lookups("こう")
        assertTrue(UOnbinPastNodes.Lookup("かう", contracted = false, shifted = true) in kou) // こうた = 買うた
        assertTrue(UOnbinPastNodes.Lookup("いう", contracted = false, shifted = true) in UOnbinPastNodes.lookups("ゆう"))
        assertTrue(UOnbinPastNodes.Lookup("もらう", contracted = true, shifted = true) in UOnbinPastNodes.lookups("もろ"))
        // 1 字の語幹（かた・いた）は作らない
        assertTrue(UOnbinPastNodes.lookups("か").isEmpty())
    }

    @Test
    fun uOnbinSurfaceAndEndings() {
        val ta = UOnbinPastNodes.ENDINGS.first { it.reading == "た" }
        val te = UOnbinPastNodes.ENDINGS.first { it.reading == "て" }
        assertEquals("思た", UOnbinPastNodes.surface("思う", "おも", UOnbinPastNodes.Lookup("おもう", true, false), ta))
        assertEquals("言うた", UOnbinPastNodes.surface("言う", "ゆう", UOnbinPastNodes.Lookup("いう", false, true), ta))
        assertEquals("もろた", UOnbinPastNodes.surface("貰う", "もろ", UOnbinPastNodes.Lookup("もらう", true, true), ta))
        assertEquals("もろうて", UOnbinPastNodes.surface("もらう", "もろう", UOnbinPastNodes.Lookup("もらう", false, true), te))
        // 縮約形に て は付けない（おもて = 表）
        assertTrue(UOnbinPastNodes.endingsAt("おもて", 2, contractedStem = true).isEmpty())
        assertEquals(listOf("たら", "た"), UOnbinPastNodes.endingsAt("おもたら", 2, contractedStem = true).map { it.reading })
        assertEquals("思っ", UOnbinPastNodes.lmWord("思た"))
        assertEquals("言っ", UOnbinPastNodes.lmWord("言うた"))
        assertEquals(826.toShort(), UOnbinPastNodes.taId(814))
        assertNull(UOnbinPastNodes.taId(1851))
    }

    @Test
    fun elongationOnlyAtEndWithSameVowel() {
        val s = "よくないのおお"
        val sp = ElongationNodes.spanAt(s, 5)!!
        assertEquals(7, sp.end)
        assertEquals(409.toShort(), sp.posId) // ぉ
        assertEquals(408.toShort(), ElongationNodes.spanAt("すごいねええ", 4)!!.posId) // ぇ
        // 1 字だけ・母音が違う・後ろにかなが続く ときは作らない
        assertNull(ElongationNodes.spanAt("よくないのお", 5))
        assertNull(ElongationNodes.spanAt("よくないのああ", 5))
        assertNull(ElongationNodes.spanAt("のおおきい", 1))
        assertEquals(4, ElongationNodes.spanAt("なのおお！", 2)!!.end)
    }

    @Test
    fun droppedUStemNeedsORowEnding() {
        assertTrue(DroppedUStemNodes.mayBe("かわいそ"))
        assertFalse(DroppedUStemNodes.mayBe("かわ"))
        assertFalse(DroppedUStemNodes.mayBe("かわいい"))
    }

    @Test
    fun tageruSuffixOnAuxiliaryKuru() {
        // 買ってき＋たげる: き = くる（非自立）連用形 866
        assertEquals(DialectInflectionNodes.TE_STEM, DialectInflectionNodes.stemClass(866))
        val sfx = DialectInflectionNodes.suffixesAt("かってきたげる", 4, DialectInflectionNodes.TE_STEM).map { it.reading }
        assertEquals(listOf("たげる"), sfx)
    }
}

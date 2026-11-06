package com.kazumaproject.markdownhelperkeyboard.converter.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-11-06: ー の伸ばし・母音だけの感動詞（LongVowelNodes）、連用形＋行く（RenyouMotionNodes）、可能の否定 れん の純粋な部分。 */
class ColloquialForms1106Test {

    @Test
    fun longVowelVariantsUsePreviousVowel() {
        assertEquals(listOf("そう", "そお"), LongVowelNodes.variants("そー"))
        assertEquals(listOf("よう", "よお"), LongVowelNodes.variants("よー"))
        assertEquals(listOf("すげい", "すげえ"), LongVowelNodes.variants("すげー"))
        assertEquals(listOf("まあ"), LongVowelNodes.variants("まー"))
        // ー が無い・先頭の ー・カタカナ混じりは作らない
        assertTrue(LongVowelNodes.variants("そう").isEmpty())
        assertTrue(LongVowelNodes.variants("ーそ").isEmpty())
        assertTrue(LongVowelNodes.variants("ソー").isEmpty())
        assertEquals("そう", LongVowelNodes.lmWord("そー"))
        assertTrue(LongVowelNodes.mayApply("おもってそーだけど"))
        assertFalse(LongVowelNodes.mayApply("ーあ"))
    }

    @Test
    fun vowelInterjectionNeedsRepeatedEnding() {
        assertEquals(3, LongVowelNodes.interjectionEnd("うおお", 0))
        assertEquals(3, LongVowelNodes.interjectionEnd("うおーすげー", 0))
        assertEquals(3, LongVowelNodes.interjectionEnd("あああ！", 0))
        // 普通の語（青い・多い・家・いいえ）、2 字、かなの後ろ、かなが続く は作らない
        assertEquals(-1, LongVowelNodes.interjectionEnd("あおい", 0))
        assertEquals(-1, LongVowelNodes.interjectionEnd("おおい", 0))
        assertEquals(-1, LongVowelNodes.interjectionEnd("いいえ", 0))
        assertEquals(-1, LongVowelNodes.interjectionEnd("ええ", 0))
        assertEquals(-1, LongVowelNodes.interjectionEnd("かうおお", 1))
        assertEquals(-1, LongVowelNodes.interjectionEnd("うおおきい", 0))
    }

    @Test
    fun renyouMotionStem() {
        assertEquals("買い", RenyouMotionNodes.lmStem("買い行く"))
        assertEquals("食べ", RenyouMotionNodes.lmStem("食べ来る"))
        assertTrue(RenyouMotionNodes.isMotionVerb(800, "行く"))
        assertFalse(RenyouMotionNodes.isMotionVerb(1851, "行"))
        assertFalse(RenyouMotionNodes.isMotionVerb(800, "言う"))
        assertTrue(RenyouMotionNodes.mayStartAt("かいいく", 2))
        assertFalse(RenyouMotionNodes.mayStartAt("かいもの", 2))
    }

    @Test
    fun potentialNegativeRen() {
        // 言え＋れん / 言え＋れんかった（語幹 = 未然形）
        val sfx = DialectInflectionNodes.suffixesAt("もんくいえれん", 5, DialectInflectionNodes.MIZEN).map { it.reading }
        assertEquals(listOf("れん"), sfx)
        val past = DialectInflectionNodes.suffixesAt("いえれんかった", 2, DialectInflectionNodes.MIZEN).map { it.reading }
        assertTrue("れんかった" in past)
        assertEquals("言え", DialectInflectionNodes.lmStem("言えれん"))
    }
}

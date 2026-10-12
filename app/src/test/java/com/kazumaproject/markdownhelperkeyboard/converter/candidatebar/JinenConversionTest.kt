package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JinenConversionTest {
    private fun c(s: String, len: Int, segs: List<CandidateConversionSegment> = emptyList()) =
        Candidate(string = s, type = 1, length = len.toUByte(), score = 1000, conversionSegments = segs)

    @Test
    fun promptUsesKatakanaReadingTagsAndNfkc() {
        assertEquals("\uEE00キョウハイイテンキ\uEE01", JinenConversion.buildPrompt("", "きょうはいいてんき"))
        // Left context first, then the whole prompt is NFKC (full-width digits/latin/！ become half-width).
        assertEquals(
            "\uEE02ABC12!\uEE00ヨンジュウ\uEE01",
            JinenConversion.buildPrompt("ＡＢＣ１２！", "よんじゅう"),
        )
        // No space -> U+3000 mapping (NFKC even maps U+3000 to a plain space).
        assertEquals("\uEE02a b\uEE00ア\uEE01", JinenConversion.buildPrompt("a\u3000b", "あ"))
        // Protocol markers and control characters in the context cannot alter the prompt grammar.
        assertEquals("\uEE02xy\uEE00ア\uEE01", JinenConversion.buildPrompt("x\uEE01\ny", "あ"))
        // Long vowel and symbols in the reading survive.
        assertEquals("\uEE00シーラナイ?\uEE01", JinenConversion.buildPrompt("", "しーらない？"))
    }

    @Test
    fun maxTokensScalesWithReading() {
        assertEquals(13, JinenConversion.maxTokensFor("あいうえお"))
        assertEquals(JinenConversion.MAX_TOKENS, JinenConversion.maxTokensFor("あ".repeat(200)))
    }

    @Test
    fun symbolTableFollowsSumireConvention() {
        // Digits, latin, % . , - / stay half-width; other ASCII symbols and space become full-width.
        assertEquals("何？（括弧）びっくり！ 5%T-1/2", JinenConversion.symbolRestore("何?(括弧)びっくり! 5%T-1/2").replace('\u3000', ' '))
        assertEquals("ａ\u3000b", "ａ" + JinenConversion.symbolRestore(" b"))
    }

    @Test
    fun widthVariantsInOrderWithoutDuplicates() {
        assertEquals(
            listOf("MI6！", "MI6!", "ＭＩ６！", "MI６！", "ＭＩ6！"),
            JinenConversion.widthVariants("MI6!"),
        )
        assertEquals(listOf("今日"), JinenConversion.widthVariants("今日"))
    }

    @Test
    fun r1UsesSumireCandidateWithSegments() = runBlocking {
        val input = "えむあいしっくす"
        val segs = listOf(CandidateConversionSegment(0, input.length, "ＭＩ６"))
        val sumire = listOf(c("エムアイシックス", input.length), c("ＭＩ６", input.length, segs), c("ＭＩ", 4))
        val r = JinenConversion.restore("MI6", input, sumire, 1, 0) { error("R2 must not run") }
        assertNotNull(r)
        assertEquals(JinenConversion.Via.SUMIRE_NBEST, r!!.via)
        assertEquals("ＭＩ６", r.candidate.string)
        assertEquals(segs, r.candidate.conversionSegments)
    }

    @Test
    fun r2TriesWidthVariantsOnConstrainedExactPath() = runBlocking {
        val input = "なに？"
        val tried = mutableListOf<String>()
        val path = c("何？", input.length, listOf(CandidateConversionSegment(0, 2, "何"), CandidateConversionSegment(2, 3, "？")))
        val r = JinenConversion.restore("何?", input, emptyList(), 1, 0) { v ->
            tried += v
            if (v == "何？") path else null
        }
        assertEquals(JinenConversion.Via.SUMIRE_PATH, r!!.via)
        assertEquals(path, r.candidate)
        assertEquals(listOf("何？"), tried)
    }

    @Test
    fun r3FallsBackToSingleSegmentWhenKanaAnchorsAlign() = runBlocking {
        val input = "じゅうろくばんがいせいよはねきょうかい"
        val r = JinenConversion.restore("16番街聖ヨハネ教会", input, emptyList(), 1, 4242) { null }
        assertEquals(JinenConversion.Via.SYMBOL_TABLE, r!!.via)
        assertEquals("16番街聖ヨハネ教会", r.candidate.string)
        assertEquals(input.length, r.candidate.length.toInt())
        assertEquals(4242, r.candidate.score)
        assertEquals(listOf(CandidateConversionSegment(0, input.length, "16番街聖ヨハネ教会")), r.candidate.conversionSegments)
    }

    @Test
    fun r3RejectsOutputWhoseKanaDoNotMatchTheReading() = runBlocking {
        assertNull(JinenConversion.restore("こんばんは", "こんにちは", emptyList(), 1, 0) { null })
        assertNull(JinenConversion.restore("", "あ", emptyList(), 1, 0) { null })
    }

    @Test
    fun alignmentAnchorsKanaAndGivesRunsAtLeastOneChar() {
        val a = JinenConversion.align("忘るべからざる", "わすするべからざる".replace("すす", "す"))!!
        assertTrue(a.unique)
        assertEquals(
            listOf(CandidateConversionSegment(0, 2, "忘"), CandidateConversionSegment(2, 8, "るべからざる")),
            a.segments,
        )
        // Katakana in the output anchors to hiragana in the reading; ー matches ー.
        val b = JinenConversion.align("じゃあ知ーらない", "じゃあしーらない")!!
        assertTrue(b.unique)
        assertEquals(CandidateConversionSegment(3, 4, "知"), b.segments[1])
        // Two adjacent runs separated by nothing are one run; a kanji run between kana is unique.
        val c = JinenConversion.align("16番街聖ヨハネ教会", "じゅうろくばんがいせいよはねきょうかい")!!
        assertTrue(c.unique)
        assertEquals("ヨハネ", c.segments[1].output)
        assertEquals(11, c.segments[1].inputStart)
        // Ambiguous: two kanji runs with no anchor between them would be one run, so use kanji-kana-kanji.
        val d = JinenConversion.align("日の本", "ひのもと")!!
        assertTrue(d.unique)
        val e = JinenConversion.align("KC", "けーしー")!!
        assertTrue(e.unique)
        assertEquals(listOf(CandidateConversionSegment(0, 4, "KC")), e.segments)
        assertNull(JinenConversion.align("AB", ""))
    }

    @Test
    fun alignmentReportsAmbiguity() {
        // 漢字・かな・漢字 where the kana occurs twice in the reading -> two ways.
        val a = JinenConversion.align("日の日", "ひのひのひ")!!
        assertFalse(a.unique)
    }
}

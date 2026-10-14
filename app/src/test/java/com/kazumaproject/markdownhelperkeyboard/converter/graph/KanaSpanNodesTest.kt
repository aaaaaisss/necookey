package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** 汎用ひらがなスパンノード（KanaSpanNodes、既定オフ）。 */
class KanaSpanNodesTest {
    @After
    fun tearDown() {
        KanaSpanNodes.enabled = false
        KanaSpanNodes.base = 8000; KanaSpanNodes.perChar = 500
        KanaSpanNodes.posId = KanaSpanNodes.NOUN; KanaSpanNodes.maxLen = KanaSpanNodes.DEFAULT_MAX_LEN
    }

    private val userDict = mock<UserDictionaryRepository>().also {
        runBlocking { whenever(it.commonPrefixSearchInUserDict(any())).thenReturn(emptyList()) }
    }
    private val engine = TestEngineFactory.create()
    private fun convert(input: String) = runBlocking {
        engine.getCandidatesWithBunsetsuSeparation(
            input = input, n = 30,
            mozcUtPersonName = false, mozcUTPlaces = false, mozcUTWiki = false,
            mozcUTNeologd = false, mozcUTWeb = false,
            userDictionaryRepository = userDict, learnRepository = null,
            isOmissionSearchEnable = false,
            typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 1900,
        ).candidates.map { it.string to it.score }
    }
    private fun top(input: String) = convert(input).first().first

    @Test
    fun spansAreHiraganaOnly() {
        assertEquals(listOf(2, 3), KanaSpanNodes.ends("あいう", 0))
        assertEquals(listOf(3, 4), KanaSpanNodes.ends("xあいう", 1))
        assertTrue(KanaSpanNodes.ends("んだ", 0).isEmpty())
        assertTrue(KanaSpanNodes.ends("あカ", 0).isEmpty())
        assertEquals(KanaSpanNodes.DEFAULT_MAX_LEN, KanaSpanNodes.ends("あいうえおかきくけこ", 0).last())
    }

    @Test
    fun standardWordsUnchangedWhenOn() {
        // こんなんだ は無効時もかな（こんなん だ）: 有効時に変わらないことだけ見る
        val inputs = listOf("かって", "けんちょう", "こんなんだ", "ひろしまけん", "こんなんになった")
        KanaSpanNodes.enabled = false
        val off = inputs.map { top(it) }
        KanaSpanNodes.configure("8000,500")
        assertEquals(off, inputs.map { top(it) })
        for ((input, want) in listOf("かって" to "買って", "けんちょう" to "県庁", "ひろしまけん" to "広島県")) {
            val t = top(input)
            assertTrue("$input -> $t", t.contains(want))
        }
    }

    @Test
    fun noChangeWhenDisabled() {
        val inputs = listOf("そやねん", "かって", "ひろしまけん", "めっちゃええやん")
        KanaSpanNodes.enabled = false
        val off = inputs.map { convert(it) }
        KanaSpanNodes.configure("8000,500")
        KanaSpanNodes.configure("-1")
        assertEquals(off, inputs.map { convert(it) })
    }

    /** 設定を選ぶ目安の出力（アサートなし）。 */
    @Test
    fun probe() {
        val inputs = listOf(
            "かって", "けんちょう", "こんなんだ", "めっちゃええやん", "なにしとんねん", "しらんけど", "ほなまたな",
            "ねんとともにかおがたるんできた", "きのうともだちとごはんたべた", "ちゃうちゃうそんなんちゃう", "おおきにありがとう",
        )
        KanaSpanNodes.enabled = false
        println("PROBE off: " + inputs.joinToString(" / ") { top(it) })
        for (spec in (System.getenv("NK_KANASPAN_PROBE") ?: "6000,500;8000,500;10000,500;8000,1000;8000,500,i").split(';')) {
            KanaSpanNodes.configure(spec)
            println("PROBE $spec: " + inputs.joinToString(" / ") { top(it) })
        }
    }
}

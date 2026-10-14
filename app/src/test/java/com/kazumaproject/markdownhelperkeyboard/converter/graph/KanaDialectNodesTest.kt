package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** 方言・口語のかな語ノード（KanaDialectNodes）。 */
class KanaDialectNodesTest {
    @Before
    fun setUp() {
        KanaDialectNodes.enabled = true
        KanaDialectNodes.penalty = KanaDialectNodes.DEFAULT_PENALTY
    }

    @After
    fun tearDown() {
        KanaDialectNodes.enabled = false
        KanaDialectNodes.penalty = KanaDialectNodes.DEFAULT_PENALTY
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
    fun entriesAtMatchesReadingAndContext() {
        assertTrue(KanaDialectNodes.entriesAt("じゃけん", 0).any { it.reading == "じゃけん" })
        assertTrue(KanaDialectNodes.entriesAt("きてはる", 2).any { it.reading == "はる" })
        assertTrue(KanaDialectNodes.entriesAt("はるがきた", 0).none { it.reading == "はる" })
    }

    @Test
    fun dialectPhrasesStayKana() {
        for ((input, kana) in listOf(
            "だめじゃけん" to "じゃけん",
            "そやねん" to "そやねん",
            "そゆこと" to "そゆこと",
            "ほんまにすごい" to "ほんま",
        )) {
            val t = top(input)
            // ほんまに は辞書の ホンマに（副詞）でもよい: かなのまま
            assertTrue("$input -> $t", t.contains(kana) || t.contains("ホンマ"))
        }
    }

    @Test
    fun standardWordsUnchanged() {
        for ((input, want) in listOf(
            "かって" to "買って",
            "けんちょう" to "県庁",
            "いけん" to "意見",
            "ひろしまけん" to "広島県",
            "さんねん" to "年",
        )) {
            val t = top(input)
            assertTrue("$input -> $t", t.contains(want))
        }
    }

    @Test
    fun noChangeWhenDisabled() {
        val inputs = listOf("そやねん", "だめじゃけん", "かって", "ひろしまけん")
        KanaDialectNodes.enabled = false
        val off = inputs.map { convert(it) }
        KanaDialectNodes.enabled = true
        val on = inputs.map { convert(it) }
        KanaDialectNodes.enabled = false
        assertEquals(off, inputs.map { convert(it) })
        assertTrue(on != off)
    }
}

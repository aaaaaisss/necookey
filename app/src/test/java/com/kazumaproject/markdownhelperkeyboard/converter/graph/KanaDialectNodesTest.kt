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
        KanaDialectNodes.enabled = true
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
        ).candidates.map { it.string to it.score }
    }
    private fun top(input: String) = convert(input).first().first

    @Test
    fun entriesAtMatchesReadingAndContext() {
        assertTrue(KanaDialectNodes.entriesAt("じゃけん", 0).any { it.reading == "じゃけん" })
        assertTrue(KanaDialectNodes.entriesAt("きてはる", 2).any { it.reading == "はる" })
        assertTrue(KanaDialectNodes.entriesAt("はるがきた", 0).none { it.reading == "はる" })
        // ねん: 文頭・名詞の直後では出さない
        assertTrue(KanaDialectNodes.entriesAt("ねんとともに", 0).none { it.reading == "ねん" })
        assertTrue(KanaDialectNodes.entriesAt("きねんび", 1).none { it.reading == "ねん" })
        assertTrue(KanaDialectNodes.entriesAt("なにやねん", 3).any { it.reading == "ねん" })
        assertTrue(KanaDialectNodes.entriesAt("あるねん", 2).any { it.reading == "ねん" })
        // 2026-11-08: よに は ん の後だけ、もろて は て形の後だけ、ぜい・くね・やろうね は文末だけ
        assertTrue(KanaDialectNodes.entriesAt("ひかんよに", 3).any { it.reading == "よに" })
        assertTrue(KanaDialectNodes.entriesAt("よにでる", 0).none { it.reading == "よに" })
        assertTrue(KanaDialectNodes.entriesAt("きてもろて", 2).any { it.reading == "もろて" })
        assertTrue(KanaDialectNodes.entriesAt("もろてをあげて", 0).none { it.reading == "もろて" })
        assertTrue(KanaDialectNodes.entriesAt("まっとくぜい", 4).any { it.reading == "ぜい" })
        assertTrue(KanaDialectNodes.entriesAt("まっとくぜい！", 4).any { it.reading == "ぜい" })
        assertTrue(KanaDialectNodes.entriesAt("とくぜいきん", 2).none { it.reading == "ぜい" })
        assertTrue(KanaDialectNodes.entriesAt("あったくね", 3).any { it.reading == "くね" })
        assertTrue(KanaDialectNodes.entriesAt("あったくねる", 3).none { it.reading == "くね" })
        assertTrue(KanaDialectNodes.entriesAt("よりけりやろうね", 4).any { it.reading == "やろうね" })
        assertTrue(KanaDialectNodes.entriesAt("やろうねこ", 0).none { it.reading == "やろうね" })
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
            "ねんとともにかおがたるんできた" to "年とともに",
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

package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * 1 字ずつ入力する差分変換（IncrementalSessionState）でも、方言の活用語尾ノード（語幹が再利用範囲にあり語尾が新しい範囲）と
 * 方言かな語・ツギハギ修復候補が、毎回作り直す変換と同じ候補になる（Robolectric を使わない JVM 版。セッションの
 * KanaKanjiConversionSessionParityTest は aarch64 で Robolectric が動かないため）。
 */
class DialectIncrementalParityTest {
    private val userDict = mock<UserDictionaryRepository>().also {
        runBlocking { whenever(it.commonPrefixSearchInUserDict(any())).thenReturn(emptyList()) }
    }

    private suspend fun conv(engine: KanaKanjiEngine, input: String, state: KanaKanjiEngine.IncrementalSessionState?): List<String> {
        state?.beginQueryTransaction()
        val r = engine.getCandidatesWithoutPredictionWithBunsetsu(
            input = input, n = 8,
            mozcUtPersonName = false, mozcUTPlaces = false, mozcUTWiki = false,
            mozcUTNeologd = false, mozcUTWeb = false,
            userDictionaryRepository = userDict, learnRepository = null,
            typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 1900,
            incrementalSessionState = state,
            predictionConfig = PredictionConfig().copy(japanesePredictionEnabled = false, englishPredictionEnabled = false),
        )
        state?.commitQueryTransaction()
        return r.candidates.map { "${it.string}:${it.score}" }
    }

    @Test
    fun incrementalTypingMatchesFullRebuild() = runBlocking {
        val engine = TestEngineFactory.create()
        for (sentence in listOf("なにもいえんかった", "ちいとまちんさい", "ねおちしてもうた", "まだねとるけえ", "しゅくだいしとらんかった")) {
            val state = engine.createIncrementalSessionState()
            for (end in 1..sentence.length) {
                val prefix = sentence.substring(0, end)
                val inc = conv(engine, prefix, state)
                val full = conv(engine, prefix, null)
                assertEquals(prefix, full, inc)
            }
        }
    }
}

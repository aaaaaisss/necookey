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
 * 1 字ずつ入力したときのエンジン内の追記キャッシュ（GraphBuilder / FindPath の 1 文字追加の再利用）でも、方言の活用語尾ノード
 * （語幹が再利用範囲にあり語尾が新しい範囲）と方言かな語・ツギハギ修復候補が、毎回作り直す変換と同じ候補になる。
 * （2026-10-30: セッション型変換エンジン INCREMENTAL_SESSION の削除に合わせて、従来の追記キャッシュの確認に置き換え。）
 */
class DialectIncrementalParityTest {
    private val userDict = mock<UserDictionaryRepository>().also {
        runBlocking { whenever(it.commonPrefixSearchInUserDict(any())).thenReturn(emptyList()) }
    }

    private suspend fun conv(engine: KanaKanjiEngine, input: String): List<String> {
        val r = engine.getCandidatesWithoutPredictionWithBunsetsu(
            input = input, n = 8,
            mozcUtPersonName = false, mozcUTPlaces = false, mozcUTWiki = false,
            mozcUTNeologd = false, mozcUTWeb = false,
            userDictionaryRepository = userDict, learnRepository = null,
            predictionConfig = PredictionConfig().copy(japanesePredictionEnabled = false, englishPredictionEnabled = false),
        )
        return r.candidates.map { "${it.string}:${it.score}" }
    }

    @Test
    fun incrementalTypingMatchesFullRebuild() = runBlocking {
        val engine = TestEngineFactory.create()
        for (sentence in listOf("なにもいえんかった", "ちいとまちんさい", "ねおちしてもうた", "まだねとるけえ", "しゅくだいしとらんかった")) {
            for (end in 1..sentence.length) {
                val prefix = sentence.substring(0, end)
                // 直前の呼び出しが 1 字短い読みなので追記キャッシュを使う → 同じ読みの 2 回目は作り直し
                val inc = conv(engine, prefix)
                val full = conv(engine, prefix)
                assertEquals(prefix, full, inc)
            }
        }
    }
}

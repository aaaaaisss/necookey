package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** 区切り固定の再変換（既定では無効）: 有効にしても通常の候補と順序は変わらず、追加候補は区切りどおりで重複しない。 */
class SplitReconversionTest {
    private val userDict = mock<UserDictionaryRepository>().also {
        runBlocking { whenever(it.commonPrefixSearchInUserDict(any())).thenReturn(emptyList()) }
    }

    private fun KanaKanjiEngine.convert(
        input: String,
        config: SplitReconversionConfig? = null,
        out: SplitReconversionResult? = null,
    ): BunsetsuCandidateResult = runBlocking {
        getCandidatesWithBunsetsuSeparation(
            input = input, n = 50,
            mozcUtPersonName = false, mozcUTPlaces = false, mozcUTWiki = false,
            mozcUTNeologd = false, mozcUTWeb = false,
            userDictionaryRepository = userDict, learnRepository = null,
            isOmissionSearchEnable = false,
            typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 1900,
            splitReconversionConfig = config,
            splitReconversionCollector = out,
        )
    }

    @Test
    fun extraCandidatesKeepDefaultOrderAndRespectSplits() {
        val engine = TestEngineFactory.create()
        for (input in listOf("きょうはいいてんきです", "けががなおった", "かれはがっこうへいった")) {
            val plain = engine.convert(input)
            val out = SplitReconversionResult()
            val withExtra = engine.convert(input, SplitReconversionConfig(maxPatterns = 8, wordsPerSpan = 3, perPattern = 5), out)
            assertEquals(plain.candidates.map { it.string }, withExtra.candidates.map { it.string })
            assertTrue(out.patterns.isNotEmpty())
            assertTrue(out.patterns.size <= 8)
            assertEquals(out.patterns.size, out.patterns.distinct().size)
            val nBest = withExtra.candidates.map { it.string }.toSet()
            val strings = out.candidates.map { it.candidate.string }
            assertEquals(strings.size, strings.toSet().size)
            for (x in out.candidates) {
                assertFalse(x.candidate.string in nBest)
                assertTrue(x.maxWordRank < 3)
                assertEquals(x.splitPositions, out.patterns[x.patternIndex])
                assertEquals(x.candidate.string, x.segments.joinToString("") { it.output })
                assertEquals(0, x.segments.first().inputStart)
                assertEquals(input.length, x.segments.last().inputEnd)
                // 区切りの位置をまたぐ語はない
                for (p in x.splitPositions) {
                    assertTrue(x.segments.none { it.inputStart < p && p < it.inputEnd })
                }
            }
        }
    }
}

package com.kazumaproject.markdownhelperkeyboard.converter.session

import com.kazumaproject.markdownhelperkeyboard.ime_service.BunsetsuConversionSnapshot
import com.kazumaproject.markdownhelperkeyboard.ime_service.buildConvertedBunsetsuSegments
import com.kazumaproject.markdownhelperkeyboard.ime_service.mergeBunsetsuCandidates
import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KanaKanjiConversionSessionParityTest {

    @Test
    fun bunsetsuDisplayPreservesFullLatticeResultAcrossBackendsAndCandidateLoading() = runBlocking {
        val input = "あしたはとうきょうにいきます"
        run {
            val backend = "legacy"
            val session = KanaKanjiConversionSession(engine)
            for (mode in listOf(CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION, CandidateQueryMode.CONVERSION)) {
                val query = request(input, mode, bunsetsu = true)
                val result = session.query(query)
                val first = result.candidates.first()
                val splits = result.bunsetsuResult!!.primarySplitPositions
                assertTrue("$backend/$mode must have multiple bunsetsu", splits.size >= 2)
                val segments = buildConvertedBunsetsuSegments(
                    input, splits,
                    BunsetsuConversionSnapshot(input, result.candidates, result.candidateSegmentsByString),
                )
                assertTrue("$backend/$mode must provide exact paths", segments.all { it.hasConvertedDisplay })
                assertEquals(first.string, segments.joinToString("") { it.displayText })
                for (segment in segments) {
                    val alternatives = session.query(request(segment.reading, CandidateQueryMode.CONVERSION, true))
                    val loaded = mergeBunsetsuCandidates(segment, alternatives.candidates.filter {
                        it.length.toInt() == segment.reading.length
                    })
                    assertEquals(segment.displayText, loaded.displayText)
                    assertEquals(segment.displayText, loaded.candidates[loaded.selectedIndex].string)
                }
                // Collection changes metadata only, not the converter's scores or ordering.
                val withoutPaths = session.query(query.copy(collectCandidateSegments = false))
                assertEquals(result.candidates.fingerprint(), withoutPaths.candidates.fingerprint())
            }
        }
    }

    @Test
    fun segmentCollectionUsesExactPathNodesWhenBunsetsuDisplayIsDisabled() = runBlocking {
        val result = KanaKanjiConversionSession(engine).query(
            request("ひを", CandidateQueryMode.CONVERSION, bunsetsu = false),
        )

        assertEquals(
            listOf(Triple(0, 1, "火"), Triple(1, 2, "を")),
            result.candidateSegmentsByString.getValue("火を").map {
                Triple(it.inputStart, it.inputEnd, it.output)
            },
        )
        assertEquals(null, result.bunsetsuResult)
    }

    @Test
    fun conversionKeepsExactSymbolEmojiEmoticonAndValueBasedNumberCandidates() = runBlocking {
        val session = KanaKanjiConversionSession(engine)

        val neko = session.query(request("ねこ", CandidateQueryMode.CONVERSION, bunsetsu = false))
        assertTrue(neko.candidates.map { it.string }.any { it.contains("🐈") })

        val niko = session.query(request("にこ", CandidateQueryMode.CONVERSION, bunsetsu = false))
        assertTrue(niko.candidates.map { it.string }.contains("(^o^)"))

        val ichi = session.query(request("いち", CandidateQueryMode.CONVERSION, bunsetsu = false))
        assertTrue(ichi.candidates.map { it.string }.contains("①"))
    }

    @Test
    fun predictionIncludesExactSymbolEmojiEmoticonCandidates() = runBlocking {
        val session = KanaKanjiConversionSession(engine)

        val exactEmoji = session.query(
            request("ねこ", CandidateQueryMode.PREDICTION, bunsetsu = false),
        )
        assertTrue(exactEmoji.candidates.map { it.string }.any { it.contains("🐈") })

        val exactEmojiWithBunsetsu = session.query(
            request("ねこ", CandidateQueryMode.PREDICTION, bunsetsu = true),
        )
        assertTrue(exactEmojiWithBunsetsu.candidates.map { it.string }.any { it.contains("🐈") })

        val exactEmoticon = session.query(
            request("にこ", CandidateQueryMode.PREDICTION, bunsetsu = false),
        )
        assertTrue(exactEmoticon.candidates.map { it.string }.contains("(^o^)"))

        val exactEmoticonWithBunsetsu = session.query(
            request("にこ", CandidateQueryMode.PREDICTION, bunsetsu = true),
        )
        assertTrue(exactEmoticonWithBunsetsu.candidates.map { it.string }.contains("(^o^)"))

        val exactSymbol = session.query(
            request("さんかく", CandidateQueryMode.PREDICTION, bunsetsu = false),
        )
        assertTrue(exactSymbol.candidates.any { it.type.toInt() == 13 })

        val prefixEmoji = session.query(
            request("うれし", CandidateQueryMode.PREDICTION, bunsetsu = false),
        )
        assertTrue(prefixEmoji.candidates.any { it.type.toInt() == 11 })

        val prefixEmoticon = session.query(
            request("にこに", CandidateQueryMode.PREDICTION, bunsetsu = false),
        )
        assertTrue(prefixEmoticon.candidates.any { it.type.toInt() == 12 })

        val prefixSymbol = session.query(
            request("さんか", CandidateQueryMode.PREDICTION, bunsetsu = false),
        )
        assertTrue(prefixSymbol.candidates.any { it.type.toInt() == 13 })

        val disabledConfig = PredictionConfig(symbolEmojiEnabled = false)
        val disabledPrefixEmoji = session.query(
            request("うれし", CandidateQueryMode.PREDICTION, bunsetsu = false).copy(
                predictionConfig = disabledConfig,
            ),
        )
        assertTrue(disabledPrefixEmoji.candidates.none { it.type.toInt() == 11 })

        val disabledPrefixEmoticon = session.query(
            request("にこに", CandidateQueryMode.PREDICTION, bunsetsu = false).copy(
                predictionConfig = disabledConfig,
            ),
        )
        assertTrue(disabledPrefixEmoticon.candidates.none { it.type.toInt() == 12 })

        val disabledPrefixSymbol = session.query(
            request("さんか", CandidateQueryMode.PREDICTION, bunsetsu = false).copy(
                predictionConfig = disabledConfig,
            ),
        )
        assertTrue(disabledPrefixSymbol.candidates.none { it.type.toInt() == 13 })
    }

    @Test
    fun symbolEmojiCandidateDisplaySettingsHideCandidatesInEveryMode() = runBlocking {
        val session = KanaKanjiConversionSession(engine)
        val hiddenConfig = PredictionConfig(
            showSymbolCandidates = false,
            showEmojiCandidates = false,
            showEmoticonCandidates = false,
        )

        for (mode in listOf(
            CandidateQueryMode.PREDICTION,
            CandidateQueryMode.CONVERSION,
            CandidateQueryMode.NO_TAB_DEFAULT,
        )) {
            for (bunsetsu in listOf(false, true)) {
                val neko = session.query(
                    request("ねこ", mode, bunsetsu).copy(predictionConfig = hiddenConfig),
                )
                assertFalse(
                    "$mode/bunsetsu=$bunsetsu emoji",
                    neko.candidates.any { it.string.contains("🐈") },
                )

                val niko = session.query(
                    request("にこ", mode, bunsetsu).copy(predictionConfig = hiddenConfig),
                )
                assertFalse(
                    "$mode/bunsetsu=$bunsetsu emoticon",
                    niko.candidates.any { it.string == "(^o^)" },
                )

                val ichi = session.query(
                    request("いち", mode, bunsetsu).copy(predictionConfig = hiddenConfig),
                )
                assertFalse(
                    "$mode/bunsetsu=$bunsetsu value-based symbol",
                    ichi.candidates.any { it.string == "①" },
                )
            }
        }
    }

    @Test
    fun symbolEmojiCandidateDisplaySettingsAreIndependent() = runBlocking {
        val session = KanaKanjiConversionSession(engine)

        val emojiHidden = session.query(
            request("ねこ", CandidateQueryMode.PREDICTION, bunsetsu = false).copy(
                predictionConfig = PredictionConfig(showEmojiCandidates = false),
            ),
        )
        assertFalse(emojiHidden.candidates.any { it.string.contains("🐈") })
        assertTrue(
            session.query(request("にこ", CandidateQueryMode.PREDICTION, false))
                .candidates.any { it.string == "(^o^)" },
        )
        assertTrue(
            session.query(request("さんかく", CandidateQueryMode.PREDICTION, false))
                .candidates.any { it.type.toInt() == 13 },
        )

        val emoticonHidden = session.query(
            request("にこ", CandidateQueryMode.PREDICTION, bunsetsu = false).copy(
                predictionConfig = PredictionConfig(showEmoticonCandidates = false),
            ),
        )
        assertFalse(emoticonHidden.candidates.any { it.string == "(^o^)" })
        assertTrue(
            session.query(request("ねこ", CandidateQueryMode.PREDICTION, false))
                .candidates.any { it.string.contains("🐈") },
        )

        val symbolHidden = session.query(
            request("さんかく", CandidateQueryMode.PREDICTION, bunsetsu = false).copy(
                predictionConfig = PredictionConfig(showSymbolCandidates = false),
            ),
        )
        assertTrue(symbolHidden.candidates.none { it.type.toInt() == 13 })
        assertFalse(
            session.query(
                request("いち", CandidateQueryMode.CONVERSION, false).copy(
                    predictionConfig = PredictionConfig(showSymbolCandidates = false),
                ),
            )
                .candidates.any { it.string == "①" },
        )
        assertTrue(
            session.query(request("ねこ", CandidateQueryMode.PREDICTION, false))
                .candidates.any { it.string.contains("🐈") },
        )
    }

    private fun request(
        input: String,
        mode: CandidateQueryMode,
        bunsetsu: Boolean,
    ) = KanaKanjiQueryRequest(
        input = input,
        mode = mode,
        bunsetsuSeparation = bunsetsu,
        n = 4,
        mozcUtPersonName = false,
        mozcUtPlaces = false,
        mozcUtWiki = false,
        mozcUtNeologd = false,
        mozcUtWeb = false,
        userDictionaryRepository = userDictionaryRepository,
        learnRepository = null,
        beamWidth = 20,
        collectCandidateSegments = true,
    )

    private fun List<Candidate>.fingerprint(): List<List<Any?>> = map { candidate ->
        listOf(
            candidate.string,
            candidate.type,
            candidate.length,
            candidate.score,
            candidate.yomi,
            candidate.leftId,
            candidate.rightId,
        )
    }

    companion object {
        private lateinit var engine: com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
        private lateinit var userDictionaryRepository: UserDictionaryRepository

        @JvmStatic
        @BeforeClass
        fun setUp() {
            engine = TestEngineFactory.create()
            userDictionaryRepository = mock()
            runBlocking {
                whenever(userDictionaryRepository.commonPrefixSearchInUserDict(any()))
                    .thenReturn(emptyList())
                whenever(userDictionaryRepository.exactMatchesForConversion(any()))
                    .thenReturn(emptyList())
            }
        }
    }
}

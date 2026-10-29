package com.kazumaproject.markdownhelperkeyboard.converter.engine

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KanaKanjiEngineEnglishKanaNumberTest {

    private lateinit var engine: KanaKanjiEngine

    @Before
    fun setUp() {
        engine = KanaKanjiEngine()

        val englishEngine = mock<EnglishEngine>()
        whenever(englishEngine.getCandidates(org.mockito.kotlin.any(), org.mockito.kotlin.any())).doAnswer { invocation ->
            val input = invocation.arguments[0] as String
            listOf(
                Candidate(
                    string = input,
                    type = 29,
                    length = input.length.toUByte(),
                    score = 100
                )
            )
        }

        KanaKanjiEngine::class.java.getDeclaredField("englishEngine").apply {
            isAccessible = true
            set(engine, englishEngine)
        }
    }

    @Test
    fun getCandidatesEnglishKana_returns_numeric_variants_for_hiragana_numbers() {
        val candidateStrings =
            engine.getCandidatesEnglishKana("いちまんにせんさんびゃくよんじゅうご").map { it.string }

        assertTrue(candidateStrings.contains("12345"))
        assertTrue(candidateStrings.contains("12,345"))
        assertTrue(candidateStrings.contains("一万二千三百四十五"))
        assertTrue(candidateStrings.contains("1万2345"))
    }

    @Test
    fun getCandidatesEnglishKana_returns_number_unit_candidates_for_hiragana_inputs() {
        assertTrue(engine.getCandidatesEnglishKana("さんにん").any { it.string == "3人" })
        assertTrue(engine.getCandidatesEnglishKana("ごえん").any { it.string == "5円" })
        assertTrue(engine.getCandidatesEnglishKana("にじゅっぷん").any { it.string == "20分" })
        assertTrue(engine.getCandidatesEnglishKana("にじゅっふん").any { it.string == "20分" })
        assertTrue(engine.getCandidatesEnglishKana("ろくじ").any { it.string == "6時" })
        assertTrue(engine.getCandidatesEnglishKana("にじゅうよじ").any { it.string == "24時" })
        assertTrue(engine.getCandidatesEnglishKana("くじ").any { it.string == "9時" })
        assertTrue(engine.getCandidatesEnglishKana("じゅうくじ").any { it.string == "19時" })
        assertTrue(engine.getCandidatesEnglishKana("よにん").any { it.string == "4人" })
        assertTrue(engine.getCandidatesEnglishKana("よえん").any { it.string == "4円" })
        assertTrue(engine.getCandidatesEnglishKana("くえん").any { it.string == "9円" })
        assertTrue(engine.getCandidatesEnglishKana("くにん").any { it.string == "9人" })
        assertTrue(engine.getCandidatesEnglishKana("いっぷん").any { it.string == "1分" })
        assertTrue(engine.getCandidatesEnglishKana("ろっぷん").any { it.string == "6分" })
        assertTrue(engine.getCandidatesEnglishKana("はっぷん").any { it.string == "8分" })
    }

    @Test
    fun getCandidatesEnglishKana_does_not_generate_numeric_candidates_for_ordinary_words() {
        val cases = mapOf(
            "よしよし" to setOf("4444", "４４４４", "8383", "８３８３", "四千四百四十四"),
            "しせん" to setOf("4000", "４０００", "4,000", "四千"),
            "くちょう" to setOf("9000000000000", "９００００００００００００", "九兆", "9兆"),
            "ちょうせん" to setOf("1000000001000", "一兆一千"),
            "ごご" to setOf("55", "５５", "五十五"),
            "さんご" to setOf("35", "３５", "三十五"),
            "しえん" to setOf("4円", "４円"),
            "しじ" to setOf("4時", "４時"),
            "いちいち" to setOf("11", "１１", "十一"),
            "さんさん" to setOf("33", "３３", "三十三"),
            "ろくろく" to setOf("66", "６６", "六十六"),
            "よせん" to setOf("4000", "４０００", "4,000", "四千"),
            "くせん" to setOf("9000", "９０００", "9,000", "九千"),
        )

        cases.forEach { (input, forbidden) ->
            val candidates = engine.getCandidatesEnglishKana(input).map { it.string }
            assertEquals(input, candidates.first())
            assertTrue("$input: $candidates", candidates.none { it in forbidden })
        }
    }

    @Test
    fun getCandidatesEnglishKana_keeps_valid_cardinal_and_counter_candidates() {
        val fourThousand = engine.getCandidatesEnglishKana("よんせん")
        assertTrue(fourThousand.any { it.string == "4000" })
        assertTrue(fourThousand.any { it.string == "四千" })
        assertTrue(fourThousand.any {
            it.string == "四千" && it.leftId == 2046.toShort() && it.rightId == 2046.toShort()
        })

        val nineTrillion = engine.getCandidatesEnglishKana("きゅうちょう")
        assertTrue(nineTrillion.any { it.string == "9000000000000" })
        assertTrue(nineTrillion.any { it.string == "九兆" })

        val oneTrillion = engine.getCandidatesEnglishKana("いっちょう")
        assertTrue(oneTrillion.any { it.string == "1000000000000" })
        assertTrue(oneTrillion.any { it.string == "一兆" })

        val historicalForty = engine.getCandidatesEnglishKana("しじゅう")
        assertTrue(historicalForty.any { it.string == "40" })
        assertTrue(historicalForty.any { it.string == "四十" })

        val threePeople = engine.getCandidatesEnglishKana("さんにん")
        assertTrue(threePeople.any {
            it.string == "3人" && it.leftId == 2044.toShort() && it.rightId == 2011.toShort()
        })
    }

    @Test
    fun halfWidthTimeUnitUsesTimeTypeAndFullWidthVariantKeepsFullWidthType() {
        val candidates = engine.getCandidatesEnglishKana("にじゅっぷん")

        assertTrue(candidates.any { it.string == "20分" && it.type == CANDIDATE_TYPE_TIME })
        assertFalse(candidates.any { it.string == "20分" && it.type == 30.toByte() })
        assertTrue(candidates.any { it.string == "２０分" && it.type == 30.toByte() })
    }

    @Test
    fun getCandidatesEnglishKana_preserves_existing_non_numeric_behaviour() {
        assertTrue(engine.getCandidatesEnglishKana("1234").any { it.string == "1234" })
        assertTrue(engine.getCandidatesEnglishKana("1234").any { it.string == "1,234" })
        assertTrue(engine.getCandidatesEnglishKana("１２３４").any { it.string == "１２３４" })
        assertTrue(engine.getCandidatesEnglishKana("１２３４").any { it.string == "1234" })
        assertTrue(engine.getCandidatesEnglishKana("abc").any { it.string == "abc" })
        assertTrue(engine.getCandidatesEnglishKana("ａｂｃ").any { it.string == "abc" })
        assertTrue(engine.getCandidatesEnglishKana("きょう").any { it.string == "きょう" })
        assertTrue(engine.getCandidatesEnglishKana("ことし").any { it.string == "ことし" })
        assertTrue(engine.getCandidatesEnglishKana("きょねん").any { it.string == "きょねん" })
        assertTrue(engine.getCandidatesEnglishKana("らいねん").any { it.string == "らいねん" })
        assertTrue(engine.getCandidatesEnglishKana("2025/04/01").any { it.string == "2025/04/01" })
    }
}

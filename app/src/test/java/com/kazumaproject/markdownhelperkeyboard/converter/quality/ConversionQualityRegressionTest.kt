package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * 変換品質の回帰コーパス（Sumire 単体＝本番と同じ文節分離経路、学習・ユーザー辞書・UT 辞書なし）。
 * zenz は JVM では動かないので、Zenzai 込みの期待は端末でのみ確認する（下の kimono を参照）。
 */
class ConversionQualityRegressionTest {

    @Test
    fun sentenceFinalYoAfterInflectionIsParticleNotNumeral() {
        val top = top("なんかいもそういうはなししたよ")
        assertTrue(top, top.endsWith("したよ"))
        assertFalse(top, top.contains("四"))
        assertEquals("何回もそういう話したよ", top)
    }

    @Test
    fun hanashitaIsHanashita() {
        assertEquals("話した", top("はなした"))
    }

    @Test
    fun renyouPlusSuruPenaltyKeepsHumbleForms() {
        // 変更前から お話します が先頭（名詞 話＋します）。お話しします は候補に残る。
        assertEquals("お話します", top("おはなしします"))
        assertTrue(strings("おはなしします").take(5).contains("お話しします"))
        assertEquals("お持ちします", top("おもちします"))
        assertEquals("お待ちしています", top("おまちしています"))
        assertEquals("お待ちしております", top("おまちしております"))
    }

    @Test
    fun numeralReadingsStillConvert() {
        assertEquals("四日", top("よっか"))
        assertEquals("三人", top("さんにん"))
        assertEquals("何人", top("なんにん"))
        assertEquals("第二", top("だいに"))
        assertTrue(strings("よん").take(10).contains("四"))
        assertTrue(strings("し").contains("四"))
        assertTrue(strings("よ").contains("四"))
    }

    /** Sumire 単体の期待。Zenzai（zenz）を通した結果は JVM では検証できない（端末で [z]/[Z] と合わせて確認）。 */
    @Test
    fun kimonoSumireOnly() {
        assertEquals("ここでは着物を履きましょう", top("ここではきものをはきましょう"))
    }

    private fun top(input: String): String = strings(input).first()

    private fun strings(input: String): List<String> = runBlocking {
        engine.getCandidatesWithBunsetsuSeparation(
            input = input,
            n = 20,
            mozcUtPersonName = false,
            mozcUTPlaces = false,
            mozcUTWiki = false,
            mozcUTNeologd = false,
            mozcUTWeb = false,
            userDictionaryRepository = userDictionaryRepository,
            learnRepository = null,
            isOmissionSearchEnable = false,
            typoCorrectionOffsetScore = 3000,
            omissionSearchOffsetScore = 1900,
        ).candidates.map { it.string }
    }

    companion object {
        private lateinit var engine: KanaKanjiEngine
        private lateinit var userDictionaryRepository: UserDictionaryRepository

        @JvmStatic
        @BeforeClass
        fun setUp() {
            engine = TestEngineFactory.create()
            userDictionaryRepository = mock()
            runBlocking {
                whenever(userDictionaryRepository.commonPrefixSearchInUserDict(any())).thenReturn(emptyList())
            }
        }
    }
}

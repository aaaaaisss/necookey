package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** システム辞書の表記誤りの修正（SystemDictionaryErrata）。 */
class SystemDictionaryErrataTest {
    private val userDict = mock<UserDictionaryRepository>().also {
        runBlocking { whenever(it.commonPrefixSearchInUserDict(any())).thenReturn(emptyList()) }
    }
    private val engine = TestEngineFactory.create()
    private fun convert(input: String) = runBlocking {
        engine.getCandidatesWithBunsetsuSeparation(
            input = input, n = 20,
            mozcUtPersonName = false, mozcUTPlaces = false, mozcUTWiki = false,
            mozcUTNeologd = false, mozcUTWeb = false,
            userDictionaryRepository = userDict, learnRepository = null,
            isOmissionSearchEnable = false,
            typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 1900,
        ).candidates.map { it.string }
    }

    @Test
    fun sumomoIdiomUsesCorrectKanji() {
        assertEquals("李も桃も桃のうち", SystemDictionaryErrata.fix("季も桃も桃のうち"))
        assertEquals("季節", SystemDictionaryErrata.fix("季節"))
        val c = convert("すもももももももものうち")
        assertTrue(c.toString(), c.none { it.contains("季も桃") } && c.first() == "李も桃も桃のうち")
    }
}

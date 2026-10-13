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

/** 長音・語末促音で崩した口語のかな語ノード（ColloquialKanaNodes）。 */
class ColloquialKanaNodesTest {
    @After
    fun tearDown() {
        ColloquialKanaNodes.enabled = true
        ColloquialKanaNodes.penalty = ColloquialKanaNodes.DEFAULT_PENALTY
    }

    private fun v(s: String) = ColloquialKanaNodes.variants(s, 0, s.length)
    private fun V(key: String, tail: String = "") = ColloquialKanaNodes.Variant(key, tail)

    @Test
    fun variantsOfLongVowelAndTailTsu() {
        assertTrue(V("これ", "ー") in v("これー"))
        assertTrue(V("きけ", "ー") in v("きけー"))
        assertTrue(V("ばか", "ーっ") in v("ばかーっ"))
        assertTrue(V("いっぱい") in v("いっぱーい"))
        assertTrue(V("しょうがない") in v("しょーがない"))
        assertTrue(V("ちょうだい") in v("ちょーだい"))
        assertTrue(V("ねえ") in v("ねー"))
        assertTrue(V("そうっと") in v("そーーっと"))
        assertEquals(listOf(V("はい", "っ")), v("はいっ"))
        // 語中の促音・先頭の長音・カタカナは対象外
        assertEquals(emptyList<ColloquialKanaNodes.Variant>(), v("いって"))
        assertEquals(emptyList<ColloquialKanaNodes.Variant>(), ColloquialKanaNodes.variants("いって", 0, 2))
        assertEquals(emptyList<ColloquialKanaNodes.Variant>(), v("ーい"))
        assertEquals(emptyList<ColloquialKanaNodes.Variant>(), v("コー"))
        // 末尾の促音は文末・読点・ー の直前だけ
        assertEquals(listOf(V("はい", "っ")), ColloquialKanaNodes.variants("はいっ、", 0, 3))
        assertEquals(emptyList<ColloquialKanaNodes.Variant>(), ColloquialKanaNodes.variants("はいって", 0, 3))
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

    @Test
    fun elongatedKanaWordStaysKana() {
        val top = convert("いびきがうるさーい").first().first
        assertTrue(top, top.endsWith("うるさーい"))
        val top2 = convert("いっぱーいあった").first().first
        assertTrue(top2, top2.startsWith("いっぱーい"))
    }

    @Test
    fun noEffectWithoutLongVowelOrTsuAndWhenDisabled() {
        val plain = listOf("きょうはいいてんきです", "かれはがっこうへいった")
        val on = plain.map { convert(it) }
        ColloquialKanaNodes.enabled = false
        assertEquals(on, plain.map { convert(it) })
        // ちーず は辞書の語（チーズ）なので口語ノードを足さない: 有効・無効で同じ
        val cheese = convert("ちーずをかう")
        ColloquialKanaNodes.enabled = true
        assertEquals(cheese, convert("ちーずをかう"))
    }
}

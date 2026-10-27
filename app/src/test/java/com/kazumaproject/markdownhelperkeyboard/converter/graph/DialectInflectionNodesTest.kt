package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** 方言の活用語尾を動詞語幹に付けた合成ノード（DialectInflectionNodes）。 */
class DialectInflectionNodesTest {
    @Before
    fun setUp() {
        DialectInflectionNodes.enabled = true
        DialectInflectionNodes.suffixCost = DialectInflectionNodes.DEFAULT_SUFFIX_COST
        DialectInflectionNodes.lmStemEnabled = true
    }

    @After
    fun tearDown() = setUp()

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
        ).candidates.filter { it.length.toInt() == input.length }.map { it.string }
    }
    private fun top(input: String) = convert(input).first()

    @Test
    fun suffixesNeedMatchingStemForm() {
        val mizen = DialectInflectionNodes.stemClass(694) // 一段 未然形（言え・食べ）
        val renyou = DialectInflectionNodes.stemClass(842) // 五段 連用形（待ち）
        val ta = DialectInflectionNodes.stemClass(841) // 五段 連用タ接続（待っ・運ん）
        assertTrue(DialectInflectionNodes.suffixesAt("いえんかった", 2, mizen).any { it.reading == "んかった" })
        assertTrue(DialectInflectionNodes.suffixesAt("まちんさい", 2, renyou).any { it.reading == "んさい" })
        assertTrue(DialectInflectionNodes.suffixesAt("まっとる", 2, ta).any { it.reading == "とる" })
        // 活用形が合わなければ付けない（待ち＋とる、待っ＋んさい）、名詞（1851）には付けない
        assertTrue(DialectInflectionNodes.suffixesAt("まちとる", 2, renyou).isEmpty())
        assertTrue(DialectInflectionNodes.suffixesAt("まっんさい", 2, ta).isEmpty())
        assertEquals(0, DialectInflectionNodes.stemClass(1851))
        assertTrue(DialectInflectionNodes.suffixesAt("しゃしんとる", 4, DialectInflectionNodes.stemClass(1851)).isEmpty())
    }

    @Test
    fun suffixStartsAndLmStem() {
        assertTrue(DialectInflectionNodes.suffixStarts("いえんかった").contains(2))
        assertEquals(0, DialectInflectionNodes.suffixStarts("きょうはいいてんき").size)
        assertEquals("言え", DialectInflectionNodes.lmStem("言えんかった"))
        assertEquals("撮っ", DialectInflectionNodes.lmStem("撮っとる"))
        assertEquals("寝落ちし", DialectInflectionNodes.lmStem("寝落ちしてもうた"))
        assertEquals("言え", DialectInflectionNodes.lmStem("言え"))
    }

    @Test
    fun dialectEndingsStayOnVerb() {
        for ((input, want) in listOf(
            "なにもいえんかった" to "んかった",
            "ちょっとまちんさい" to "待ちんさい",
            "わすれてもうた" to "てもうた",
            "まだねとる" to "寝とる",
            "しゅくだいしとらんかった" to "しとらんかった",
        )) {
            val t = top(input)
            assertTrue("$input -> $t", t.contains(want))
        }
    }

    /** 標準語は方言の合成ノードにならない（しゃしんをとる の と は 撮る/取る のまま。撮る/取る の選び分けは LM 表の担当）。 */
    @Test
    fun standardWordsUnchanged() {
        assertFalse(convert("しゃしんをとる").first().endsWith("をとる"))
        for ((input, want) in listOf(
            "しゃしんをとる" to "写真を",
            "かって" to "買って",
            "いえんになった" to "胃炎",
            "にもつをはこんでる" to "荷物を運んでる",
            "ひろしまけん" to "広島県",
            "まちあわせにおくれた" to "待ち合わせ",
        )) {
            val t = top(input)
            assertTrue("$input -> $t", t.contains(want))
        }
    }

    @Test
    fun noDialectNodesWhenDisabled() {
        DialectInflectionNodes.enabled = false
        assertFalse(DialectInflectionNodes.mayApply("なにもいえんかった"))
        assertFalse(convert("ちょっとまちんさい").any { it.contains("待ちんさい") })
    }
}

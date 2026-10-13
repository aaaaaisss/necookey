package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * 案2 の辺コスト補正: 表なし（既定）なら 0・候補不変、SLC2 の読み込み、式（期待値は tools/sumire_lm/lcverify.py と同じ計算で求めた値）。
 */
class LatticeLmCostTest {
    @After
    fun tearDown() {
        LatticeLmCost.table = null
    }

    /** 語: 東京(lp1 -58, la -30), に(-34, -40), 行く(-66, -30)。対: (東京, に) = -10。floor -122。 */
    private fun smallTable(): LatticeLmCost.Table {
        val words = listOf(Triple("東京", -58, -30), Triple("に", -34, -40), Triple("行く", -66, -30))
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { o ->
            o.write("SLC2".toByteArray(Charsets.US_ASCII))
            o.writeInt(words.size); o.writeInt(1)
            o.writeFloat(272.9f); o.writeFloat(760.5f); o.writeFloat(-684.1f); o.writeFloat(7f)
            o.writeByte(-122)
            words.forEach { o.writeInt(LatticeLmCost.wordHash(it.first)) }
            words.forEach { o.writeByte(it.second) }
            words.forEach { o.writeByte(it.third) }
            o.writeInt(LatticeLmCost.pairKey(LatticeLmCost.wordHash("東京"), LatticeLmCost.wordHash("に")))
            o.writeByte(-10)
        }
        return LatticeLmCost.load(ByteArrayInputStream(bos.toByteArray()))
    }

    private fun node(tango: String, type: MozcNodeType = MozcNodeType.NOR) =
        Node(l = 1, r = 1, score = 0, f = 0, tango = tango, len = 1, yomiUsed = tango, sPos = 0, mozcNodeType = type)

    @Test
    fun hashesMatchPythonExporter() {
        assertEquals(745305631, LatticeLmCost.wordHash("東京"))
        assertEquals(250362900, LatticeLmCost.wordHash("に"))
        assertEquals(-1542471090, LatticeLmCost.wordHash("行く"))
        assertEquals(-598991040, LatticeLmCost.pairKey(745305631, 250362900))
    }

    @Test
    fun loadsTable() {
        val t = smallTable()
        assertEquals(3, t.wordCount)
        assertEquals(1, t.pairCount)
        assertTrue(t.isKnown("東京"))
        assertFalse(t.isKnown("未知語x"))
        assertEquals(-58, t.logProbQ(null, "東京"))
        assertEquals(-10, t.logProbQ("東京", "に"))
        assertEquals(-98, t.logProbQ("に", "東京"))
        assertEquals(-152, t.logProbQ("東京", "未知語x"))
        assertEquals(-34, t.logProbQ("未知語x", "に"))
    }

    @Test
    fun formulaMatchesSimulation() {
        val t = smallTable()
        assertEquals(3022, t.correction(null, "東京"))       // BOS: p1 only
        assertEquals(1150, t.correction("東京", "に"))       // stored pair
        assertEquals(4581, t.correction("に", "東京"))       // backoff alpha(v) p1(w)
        assertEquals(6002, t.correction("東京", "未知語x"))  // OOV word: floor + OMEGA
        assertEquals(2086, t.correction("未知語x", "に"))    // OOV history: alpha = 1
        assertEquals(4833, t.correction(null, "未知語x"))
    }

    @Test
    fun nodeLevelBosEosAndDisabled() {
        assertEquals(0, LatticeLmCost.correction(BOS, node("東京")))  // table null = disabled
        LatticeLmCost.table = smallTable()
        assertEquals(3022, LatticeLmCost.correction(BOS, node("東京")))
        assertEquals(1150, LatticeLmCost.correction(node("東京"), node("に")))
        assertEquals(0, LatticeLmCost.correction(node("東京"), node("EOS", MozcNodeType.EOS)))
        LatticeLmCost.table = null
        assertEquals(0, LatticeLmCost.correction(node("東京"), node("に")))
    }

    @Test
    fun nullTableKeepsCandidatesIdentical() {
        val userDict = mock<UserDictionaryRepository>().also {
            runBlocking { whenever(it.commonPrefixSearchInUserDict(any())).thenReturn(emptyList()) }
        }
        val engine = TestEngineFactory.create()
        fun convert(input: String) = runBlocking {
            engine.getCandidatesWithBunsetsuSeparation(
                input = input, n = 30,
                mozcUtPersonName = false, mozcUTPlaces = false, mozcUTWiki = false,
                mozcUTNeologd = false, mozcUTWeb = false,
                userDictionaryRepository = userDict, learnRepository = null,
                isOmissionSearchEnable = false,
                typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 1900,
            ).candidates.map { it.string to it.score }
        }
        val inputs = listOf("きょうはいいてんきです", "わかっている", "かれはがっこうへいった")
        val before = inputs.map { convert(it) }
        LatticeLmCost.table = smallTable()
        val with = inputs.map { convert(it) }
        LatticeLmCost.table = null
        val after = inputs.map { convert(it) }
        assertEquals(before, after)
        assertTrue(before != with)  // the correction is actually applied when a table is set
    }
}

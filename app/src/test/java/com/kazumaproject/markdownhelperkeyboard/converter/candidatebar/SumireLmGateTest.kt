package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.sumirelm.SumireLmReranker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class SumireLmGateTest {
    private fun c(s: String, len: Int, score: Int, type: Byte = 1) =
        Candidate(string = s, type = type, length = len.toUByte(), score = score)

    /** 空の n-gram 表と「cost だけ」の線形モデル（コストが小さいほど良い）の表一式。 */
    private fun syntheticTables(costWeight: Double = -1.0, extra: (File) -> Unit = {}): File {
        val dir = Files.createTempDirectory("slm").toFile()
        File(dir, "meta.tsv").writeText("q\t4\nfp\t12\nKc\t3\nNw\t1000.0\nNc\t1000.0\nunk_w\t-20.0\nunk_c\t-20.0\n")
        val cbKeys = listOf("w1.lp", "w2.lp", "w3.lp", "w4.lp", "w1.bw", "w2.bw", "w3.bw", "c1.lp", "c2.lp", "c3.lp", "c1.bw", "c2.bw", "wcnt", "p.cnt")
        File(dir, "cb.tsv").writeText(cbKeys.joinToString("") { "$it\t0.0,5.0\n" })
        File(dir, "model.tsv").writeText("cost\t0.0\t1.0\t$costWeight\n")
        File(dir, "vocab.tsv").writeText("<s>\t0\t-1.0\n東京\t1\t5.0\nに\t2\t3.0\n")
        for (name in listOf("w1", "w2", "w3", "w4", "c1", "c2", "c3", "p")) {
            for (ext in listOf("dir", "rel", "fp")) File(dir, "$name.$ext").writeBytes(ByteArray(0))
        }
        extra(dir)
        return dir
    }

    /** 他のテスト用: コストだけ（-1/1000 nat）の合成リランカー。 */
    fun syntheticReranker() = SumireLmReranker.load(syntheticTables())!!

    @Test
    fun missingTablesLoadAsNull() {
        assertNull(SumireLmReranker.load(File("/nonexistent/sumire_lm")))
        val dir = syntheticTables()
        File(dir, "vocab.tsv").delete()
        assertNull(SumireLmReranker.load(dir))
    }

    @Test
    fun rerankOrdersByModelAndReportsMargin() {
        val reranker = SumireLmReranker.load(syntheticTables())
        assertNotNull(reranker)
        reranker!!
        val input = "とうきょうに"
        val list = listOf(
            c("東京に", input.length, 3000),
            c("とうきょうに", input.length, 1000),
            c("東京", 4, 500), // 読み全体を覆わない候補は対象外
            c("トウキョウに", input.length, 2000),
            c("東京に", input.length, 3500), // 重複
        )
        val d = SumireLmGate.rerank(reranker, input, "", list, { emptyList() })!!
        assertEquals("とうきょうに", d.top.string)
        assertEquals(listOf("とうきょうに", "トウキョウに", "東京に"), d.ranked.map { it.string })
        assertEquals(1.0, d.margin!!, 1e-9)
    }

    @Test
    fun segmentsAreUsedAsTokens() {
        val reranker = SumireLmReranker.load(syntheticTables())!!
        val input = "とうきょうに"
        val segs = listOf(CandidateConversionSegment(0, 5, "東京"), CandidateConversionSegment(5, 6, "に"))
        val d = SumireLmGate.rerank(reranker, input, "きのうは", listOf(c("東京に", input.length, 10)), { segs })!!
        assertEquals("東京に", d.top.string)
        assertNull(d.margin)
    }

    @Test
    fun userDictionaryTopIsKept() {
        val reranker = SumireLmReranker.load(syntheticTables())!!
        val input = "とうきょうに"
        val list = listOf(
            c("東京ニ", input.length, 9000, CANDIDATE_TYPE_USER_DICTIONARY),
            c("東京に", input.length, 100),
        )
        assertNull(SumireLmGate.rerank(reranker, input, "", list, { emptyList() }))
    }

    @Test
    fun hashTableFindsStoredKeyOnly() {
        // 1 エントリ（語 id 1 のユニグラム）: バケット数 1、dir = [0]、rel = [0]、fp = splitmix(1) の下位 12bit。
        val dir = syntheticTables { d ->
            val fp = (SumireLmReranker.splitmix(1L) and 0xFFF).toInt()
            fun le(n: Int, put: ByteBuffer.() -> Unit) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN).apply(put).array()
            File(d, "w1.dir").writeBytes(le(4) { putInt(0) })
            File(d, "w1.rel").writeBytes(le(2) { putChar(0.toChar()) })
            File(d, "w1.fp").writeBytes(le(2) { putChar(fp.toChar()) })
        }
        val table = SumireLmReranker.HTable(dir, "w1", 12)
        assertEquals(0, table.find(1L))
        val other = (2L..50L).count { table.find(it) >= 0 }
        assertTrue("false hits $other", other <= 1)
    }

    /** 端末報告 2026-11-03: 経路でない候補（半角カナ・絵文字・単漢字辞書・かなそのまま）はコストの尺度が違うので採点しない。 */
    @Test
    fun nonLatticeCandidatesAreNotScored() {
        val reranker = SumireLmReranker.load(syntheticTables())!!
        for ((input, list) in listOf(
            "おけ" to listOf(c("桶", 2, 7000), c("おけ", 2, 7800), c("おけ", 2, 6000, 3), c("オケ", 2, 6000, 4), c("ｵｹ", 2, 6000, 31), c("岡", 2, 4500, 7)),
            "くさ" to listOf(c("草", 2, 6500), c("くさ", 2, 7600), c("ｸｻ", 2, 6000, 31), c("日", 2, 100, 7), c("日", 2, 100, 2)),
            "むりょう" to listOf(c("無料", 4, 6000), c("むりょう", 4, 9500), c("🈚", 4, 10, 11), c("☆", 4, 10, 13), c("(^^)", 4, 10, 12), c("むりょう", 4, 6000, 3)),
        )) {
            val d = SumireLmGate.rerank(reranker, input, "", list, { emptyList() })!!
            assertEquals(input, list[0].string, d.top.string)
            assertTrue(d.ranked.all { it.type.toInt() == 1 })
        }
        // 半角の数字記号だけの経路（種別 31）は採点する、半角カナは種別 31 でも採点しない
        assertTrue(SumireLmGate.isRerankable(c("12:00", 5, 1, 31)))
        assertFalse(SumireLmGate.isRerankable(c("ｵｹ", 2, 1, 31)))
        assertFalse(SumireLmGate.isRerankable(c("ｵｹ", 2, 1, 1)))
        assertTrue(SumireLmGate.isRerankable(c("県庁", 4, 1, 34)))
    }

    @Test
    fun rareSingleKanjiDoesNotReplaceSumireTopForShortReadings() {
        // コストが小さいほど良いモデルで、Sumire 1 位（おけ）より安い経路の 1 字漢字（岡）を出させる
        val reranker = SumireLmReranker.load(syntheticTables())!!
        val d = SumireLmGate.rerank(reranker, "おけ", "", listOf(c("おけ", 2, 9000), c("桶", 2, 9500), c("岡", 2, 100)), { emptyList() })!!
        assertEquals("おけ", d.top.string)
        // Sumire 1 位が 1 字漢字（橋）なら、近くの 1 字漢字（箸）への差し替えは文脈で許す
        val h = SumireLmGate.rerank(reranker, "はし", "", listOf(c("橋", 2, 9000), c("端", 2, 9500), c("箸", 2, 100)), { emptyList() })!!
        assertEquals("箸", h.top.string)
        // 4 字以上の読みや 2 字以上の表記は対象外
        val k = SumireLmGate.rerank(reranker, "けんちょう", "", listOf(c("県長", 5, 9000), c("県庁", 5, 100)), { emptyList() })!!
        assertEquals("県庁", k.top.string)
    }

    @Test
    fun trailingElongationStaysKana() {
        val input = "よくないのおお"
        val pool = listOf(c("良くないのおお", 7, 100), c("よくないの大", 7, 110), c("よくないのおお", 7, 120))
        // リランカーが の大 を 1 位にしても、伸ばしを残す候補のうち一番上（よくないのおお）を 1 位に
        assertEquals(listOf(2, 1, 0), SumireLmGate.keepTrailingElongation(input, pool, listOf(1, 2, 0)))
        // 1 位が既に残している・伸ばしが無い入力・残す候補が無い なら何もしない
        assertEquals(null, SumireLmGate.keepTrailingElongation(input, pool, listOf(0, 1, 2)))
        assertEquals(null, SumireLmGate.keepTrailingElongation("よくないのお", pool, listOf(1, 0, 2)))
        assertEquals(null, SumireLmGate.keepTrailingElongation(input, listOf(c("よくないの大", 7, 1)), listOf(0)))
    }

    @Test
    fun katakanaOnlySwapOfSumireTopNeedsClearMargin() {
        val pool = listOf(c("かわいそすぎやろ", 8, 100), c("カワイソスぎやろ", 8, 101), c("かわいそ過ぎやろ", 8, 102))
        val scores = doubleArrayOf(16.4, 17.0, 13.1)
        assertEquals("かわいそすぎやろ", SumireLmGate.foldKatakana("カワイソスぎやろ"))
        assertEquals(listOf(0, 1, 2), SumireLmGate.keepHiraganaOverKatakanaSwap(pool, listOf(1, 0, 2), scores))
        // 差が大きい・カタカナにするだけでない（漢字が変わる）・1 位が Sumire の 1 位 なら何もしない
        assertNull(SumireLmGate.keepHiraganaOverKatakanaSwap(pool, listOf(1, 0, 2), doubleArrayOf(14.0, 17.0, 13.1)))
        assertNull(SumireLmGate.keepHiraganaOverKatakanaSwap(pool, listOf(2, 0, 1), doubleArrayOf(16.4, 13.0, 17.0)))
        assertNull(SumireLmGate.keepHiraganaOverKatakanaSwap(pool, listOf(0, 1, 2), scores))
    }

    @Test
    fun shortKatakanaWordGivesWayToKanaSpelling() {
        // 2026-11-07 端末: 繋がっトンカ・帰りクラインちゃう
        val pool = listOf(c("繋がっトンカ", 7, 100), c("繋がっとんか", 7, 110), c("帰り本気", 7, 120))
        val toks = listOf(listOf("繋がっ", "トンカ"), listOf("繋がっ", "とんか"), listOf("帰り", "本気"))
        assertEquals(listOf(1, 0, 2), SumireLmGate.avoidShortKatakanaToken(pool, toks, listOf(0, 1, 2), doubleArrayOf(2.0, 1.0, 0.0)))
        // 差が大きければそのまま
        assertNull(SumireLmGate.avoidShortKatakanaToken(pool, toks, listOf(0, 1, 2), doubleArrayOf(3.0, 1.0, 0.0)))
        // 漢字だけの当て字（マジ → 本気）には替えない
        val p2 = listOf(c("マジで", 3, 100), c("本気で", 3, 110))
        assertNull(SumireLmGate.avoidShortKatakanaToken(p2, listOf(listOf("マジ", "で"), listOf("本気", "で")), listOf(0, 1), doubleArrayOf(2.0, 1.9)))
        // ー を含む語（デート）は対象外
        val p3 = listOf(c("デートする", 5, 100), c("でーとする", 5, 110))
        assertNull(SumireLmGate.avoidShortKatakanaToken(p3, listOf(listOf("デート", "する"), listOf("でーと", "する")), listOf(0, 1), doubleArrayOf(2.0, 1.9)))
    }
}

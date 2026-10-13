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
        assertFalse(SumireLmGate.needsZenz(d.margin, 50))
        assertTrue(SumireLmGate.needsZenz(d.margin, 150))
    }

    @Test
    fun segmentsAreUsedAsTokens() {
        val reranker = SumireLmReranker.load(syntheticTables())!!
        val input = "とうきょうに"
        val segs = listOf(CandidateConversionSegment(0, 5, "東京"), CandidateConversionSegment(5, 6, "に"))
        val d = SumireLmGate.rerank(reranker, input, "きのうは", listOf(c("東京に", input.length, 10)), { segs })!!
        assertEquals("東京に", d.top.string)
        assertNull(d.margin)
        assertFalse(SumireLmGate.needsZenz(d.margin, 300))
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

    @Test
    fun marginThresholdBoundary() {
        assertTrue(SumireLmGate.needsZenz(0.49, 50))
        assertFalse(SumireLmGate.needsZenz(0.5, 50))
        assertFalse(SumireLmGate.needsZenz(0.1, 0))
        assertFalse(SumireLmGate.needsZenz(null, 300))
    }
}

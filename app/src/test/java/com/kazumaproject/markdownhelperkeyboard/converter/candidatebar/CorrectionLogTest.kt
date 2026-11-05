package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class CorrectionLogTest {
    @Test
    fun kindOfClassifiesCorrections() {
        assertNull(CorrectionLog.kindOf(listOf(0, 2, 5), "もう一", listOf(0, 2, 5), "もう一"))
        assertEquals(CorrectionLog.KIND_SEGMENT, CorrectionLog.kindOf(listOf(0, 2, 5), "もう一", listOf(0, 2, 4, 5), "もう着いた"))
        assertEquals(CorrectionLog.KIND_KANA, CorrectionLog.kindOf(listOf(0, 3), "大丈夫", listOf(0, 3), "だいじょうぶ"))
        assertEquals(CorrectionLog.KIND_CANDIDATE, CorrectionLog.kindOf(listOf(0, 2, 5), "もう一", listOf(0, 2, 5), "もう着いた"))
    }

    @Test
    fun fileKeepsLatestEntriesAndCopiesNewestFirst() {
        val dir = Files.createTempDirectory("cl").toFile()
        val f = java.io.File(dir, CorrectionLog.FILE_NAME)
        repeat(CorrectionLog.MAX_ENTRIES + 5) { i ->
            CorrectionLog.append(f, CorrectionLog.Entry(i.toLong(), "よみ$i", "見せ\t$i", "選び$i", CorrectionLog.KIND_CANDIDATE, i % 2 == 1))
        }
        val all = CorrectionLog.read(f)
        assertEquals(CorrectionLog.MAX_ENTRIES, all.size)
        assertEquals(204L, all.first().time)
        assertEquals("よみ204\t見せ 204\t選び204\t候補\tctx=0", all.first().toCopyLine())
        assertEquals("よみ203\t見せ 203\t選び203\t候補\tctx=1", CorrectionLog.copyText(all).lines()[1])
        CorrectionLog.clear(f)
        assertEquals(0, CorrectionLog.read(f).size)
    }
}

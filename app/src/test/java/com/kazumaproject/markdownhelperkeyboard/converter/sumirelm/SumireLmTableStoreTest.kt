package com.kazumaproject.markdownhelperkeyboard.converter.sumirelm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SumireLmTableStoreTest {
    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((n, c) in entries) {
                z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private val required = CandidateRerankers.formats.first().requiredFiles

    @Test
    fun importsCompleteTableAndBumpsGeneration() {
        val files = Files.createTempDirectory("lm").toFile()
        val bytes = zip(*required.map { it to "x" }.toTypedArray(), "v2/sub.pe" to "y")
        val gen = SumireLmTableStore.generation
        var last = -1
        val r = SumireLmTableStore.importZip(files, ByteArrayInputStream(bytes), bytes.size.toLong()) { last = it }
        assertEquals(100, last)
        assertFalse(r.hashMatches)
        assertEquals(gen + 1, SumireLmTableStore.generation)
        assertNotNull(SumireLmTableStore.installedBytes(files))
        assertTrue(java.io.File(files, "sumire_lm/v2/sub.pe").isFile)
        assertTrue(SumireLmTableStore.delete(files))
        assertNull(SumireLmTableStore.installedBytes(files))
    }

    @Test
    fun rejectsZipSlipAndIncompleteTables() {
        val files = Files.createTempDirectory("lm").toFile()
        for (bad in listOf(zip("../evil" to "x"), zip("a/../../evil" to "x"), zip("meta-only.txt" to "x"))) {
            try {
                SumireLmTableStore.importZip(files, ByteArrayInputStream(bad), -1)
                fail("should reject")
            } catch (e: SumireLmTableStore.InvalidTableException) {
                // ok
            }
        }
        assertFalse(java.io.File(files.parentFile, "evil").exists())
        assertNull(SumireLmTableStore.installedBytes(files))
    }
}

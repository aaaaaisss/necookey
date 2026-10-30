package com.kazumaproject.markdownhelperkeyboard.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FreezeLogFileTest {
    private fun entry(i: Int, bodyLines: Int = 3) = buildString {
        append(FreezeWatchdog.ENTRY_MARKER).append("2026-10-30 22:00:").append(i).append(" フリーズ #").append(i).append('\n')
        repeat(bodyLines) { append("  at frame").append(i).append('_').append(it).append('\n') }
    }

    @Test
    fun trimKeepsOnlyTheNewestEntriesStartingAtAMarker() {
        val content = (1..100).joinToString("") { entry(it) }
        val trimmed = FreezeLogFile.trim(content)
        assertTrue(trimmed.startsWith(FreezeWatchdog.ENTRY_MARKER))
        assertEquals(FreezeWatchdog.KEEP_ENTRIES_ON_TRIM, trimmed.split(FreezeWatchdog.ENTRY_MARKER).size - 1)
        assertTrue(trimmed.endsWith(entry(100)))
    }

    @Test
    fun trimDropsMoreEntriesWhenTheyAreHuge() {
        val content = (1..40).joinToString("") { entry(it, bodyLines = 2_000) }
        val trimmed = FreezeLogFile.trim(content)
        assertTrue(trimmed.toByteArray().size <= FreezeWatchdog.MAX_LOG_BYTES / 2)
        assertTrue(trimmed.startsWith(FreezeWatchdog.ENTRY_MARKER))
        assertTrue(trimmed.endsWith(entry(40, bodyLines = 2_000)))
    }

    @Test
    fun appendCapsTheFileSize() {
        val dir = Files.createTempDirectory("freeze").toFile()
        val file = File(dir, FreezeWatchdog.LOG_FILE_NAME)
        repeat(400) { FreezeLogFile.append(file, entry(it, bodyLines = 30)) }
        assertTrue(file.length() <= FreezeWatchdog.MAX_LOG_BYTES + entry(0, bodyLines = 30).length * 3)
        assertTrue(FreezeLogFile.read(file).contains("フリーズ #399"))
        FreezeLogFile.clear(file)
        assertEquals("", FreezeLogFile.read(file))
        dir.deleteRecursively()
    }
}

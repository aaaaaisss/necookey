package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenzaiSchedulerTest {
    @Test
    fun waitsSlightlyLongerThanTypingRhythm() {
        val s = ZenzaiScheduler()
        assertEquals(ZenzaiScheduler.MIN_DELAY_MS, s.startDelayMs())
        var t = 0L
        "あいう".forEachIndexed { i, _ -> s.onInput("あいう".take(i + 1), t); t += 150 }
        s.onInferenceStarted()
        assertEquals(195L, s.startDelayMs())
    }

    @Test
    fun delayIsClampedAndLongPausesDoNotCountAsRhythm() {
        val s = ZenzaiScheduler()
        s.onInput("あ", 0)
        s.onInput("あい", 5000) // thinking pause, ignored
        assertEquals(ZenzaiScheduler.MIN_DELAY_MS, s.startDelayMs())
        s.onInput("あいう", 5600)
        s.onInferenceStarted()
        assertEquals(ZenzaiScheduler.MAX_DELAY_MS, s.startDelayMs())
    }

    @Test
    fun runsInBackgroundOnlyAfterThreeCharacters() {
        val s = ZenzaiScheduler()
        assertFalse(s.runsInBackground("あいう"))
        assertTrue(s.runsInBackground("あいうえ"))
    }

    @Test
    fun sameReadingRefreshIsNotAKeystroke() {
        val s = ZenzaiScheduler()
        s.onInput("あ", 0)
        s.onInput("あ", 50)
        s.onInput("あい", 200)
        s.onInferenceStarted()
        assertEquals(260L, s.startDelayMs())
    }
}

package com.kazumaproject.markdownhelperkeyboard.ime_service.zenz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZenzConfidenceTest {
    @Test
    fun splitsVerdictAndConfidence() {
        val (verdict, conf) = ZenzConfidence.split("FIX:今日は\u001Fconf:-0.1200,2.5000,3.0000,-0.0500,4")
        assertEquals("FIX:今日は", verdict)
        assertEquals(-0.12f, conf!!.minLogprob, 1e-4f)
        assertEquals(2.5f, conf.minMargin, 1e-4f)
        assertEquals(3.0f, conf.fixGap, 1e-4f)
        assertEquals(4, conf.acceptedTokens)
    }

    @Test
    fun passHasNanFixValuesAndOldVerdictsHaveNoConfidence() {
        val (verdict, conf) = ZenzConfidence.split("PASS:-1.5\u001Fconf:-0.3000,1.0000,nan,nan,3")
        assertEquals("PASS:-1.5", verdict)
        assertTrue(conf!!.fixGap.isNaN())
        val (old, none) = ZenzConfidence.split("PASS:-1.5")
        assertEquals("PASS:-1.5", old)
        assertNull(none)
    }
}

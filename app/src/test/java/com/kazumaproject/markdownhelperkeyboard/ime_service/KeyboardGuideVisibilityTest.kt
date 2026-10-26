package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardGuideVisibilityTest {

    @Test
    fun customSurface_keepsLegacyCustomSettingAndLabelPolicy() {
        val config = resolveFlickGuideRuntimeConfig(
            FlickGuideSurface.Custom,
            customGuideEnabled = true
        )

        assertTrue(config.enabled)
        assertFalse(config.allowMultiCharacterLabels)
    }

    @Test
    fun otherSurface_neverShowsGuide() {
        assertEquals(
            FlickGuideRuntimeConfig(enabled = false, allowMultiCharacterLabels = false),
            resolveFlickGuideRuntimeConfig(FlickGuideSurface.Other, customGuideEnabled = true)
        )
    }
}

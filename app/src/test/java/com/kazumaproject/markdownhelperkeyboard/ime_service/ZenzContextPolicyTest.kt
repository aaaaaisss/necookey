package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BunsetsuAlternative
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BunsetsuAnalysis
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BunsetsuSlot
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BunsetsuSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ZenzContextPolicyTest {

    private fun sampleAnalysis(alternativeOutput: String = "缶字"): BunsetsuAnalysis {
        val primary = Candidate("漢字", 1, 3.toUByte(), 100)
        return BunsetsuAnalysis(
            input = "かんじ",
            primary = primary,
            slots = listOf(
                BunsetsuSlot(
                    span = BunsetsuSpan(0, 3, "漢字"),
                    alternatives = listOf(
                        BunsetsuAlternative("漢字", 100),
                        BunsetsuAlternative(alternativeOutput, 120),
                    ),
                ),
            ),
        )
    }

    private fun cacheKey(
        left: String = "左文脈",
        right: String = "右文脈",
        analysis: BunsetsuAnalysis = sampleAnalysis(),
        profile: String = "profile",
    ) = buildNecookeyZenzOverrideCacheKey(
        input = "かんじ",
        analysis = analysis,
        profile = profile,
        editorLeftContext = left,
        editorRightContext = right,
        maxLeftContextChars = 3,
        maxRightContextChars = 3,
    )

    @Test
    fun resolveZenzContext_returnsEmptyRightContextWhenPreferenceDisabled() {
        val context = resolveZenzContext(
            leftContext = "左かな".dropLast(2),
            rawRightContext = "右側",
            enableRightContext = false
        )

        assertEquals("左", context.leftContext)
        assertEquals("", context.rightContext)
    }

    @Test
    fun resolveZenzContext_keepsRightContextSeparateWhenPreferenceEnabled() {
        val context = resolveZenzContext(
            leftContext = "左かな".dropLast(2),
            rawRightContext = "右側",
            enableRightContext = true
        )

        assertEquals("左", context.leftContext)
        assertEquals("右側", context.rightContext)
    }

    @Test
    fun resolveZenzContext_doesNotUseRightContextAsLeftContextFallback() {
        val context = resolveZenzContext(
            leftContext = "かな".dropLast(2),
            rawRightContext = "右側",
            enableRightContext = true
        )

        assertEquals("", context.leftContext)
        assertEquals("右側", context.rightContext)
    }

    @Test
    fun buildZenzRerankCacheKey_includesRightContext() {
        val targets = listOf(
            IndexedValue(
                index = 0,
                value = Candidate(
                    string = "候補",
                    type = 1.toByte(),
                    length = 2.toUByte(),
                    score = -100
                )
            )
        )

        val key = buildZenzRerankCacheKey(
            profile = "profile",
            leftContext = "left",
            rightContext = "right-a",
            input = "input",
            rerankTargets = targets
        )
        val otherRightContextKey = buildZenzRerankCacheKey(
            profile = "profile",
            leftContext = "left",
            rightContext = "right-b",
            input = "input",
            rerankTargets = targets
        )

        assertEquals("profile\u0001left\u0001right-a\u0001input\u00020\u0003候補\u0003-100", key)
        assertNotEquals(key, otherRightContextKey)
    }

    @Test
    fun necookeyOverrideKeyIncludesEffectiveEditorContextsAndProfile() {
        val baseline = cacheKey()
        assertNotEquals(baseline, cacheKey(left = "異なる文脈"))
        assertNotEquals(baseline, cacheKey(right = "異なる文脈"))
        assertNotEquals(baseline, cacheKey(profile = "other-profile"))
    }

    @Test
    fun necookeyOverrideKeyUsesTruncatedContextsAndNoDelimiterAmbiguity() {
        assertEquals(cacheKey(left = "012左文脈", right = "右文脈XYZ"), cacheKey())

        val delimiterLeft = cacheKey(left = "a:3:b", right = "c")
        val delimiterRight = cacheKey(left = "a", right = "3:b:c")
        assertNotEquals(delimiterLeft, delimiterRight)
    }

    @Test
    fun necookeyOverrideKeyDistinguishesSlotOptionsButDisabledRightContextIsEmpty() {
        assertNotEquals(cacheKey(), cacheKey(analysis = sampleAnalysis("別候補")))

        val disabledRightA = resolveZenzContext("左文脈", "右A", enableRightContext = false)
        val disabledRightB = resolveZenzContext("左文脈", "右B", enableRightContext = false)
        assertEquals(
            cacheKey(left = disabledRightA.leftContext, right = disabledRightA.rightContext),
            cacheKey(left = disabledRightB.leftContext, right = disabledRightB.rightContext),
        )
    }
}

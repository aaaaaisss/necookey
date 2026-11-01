package com.kazumaproject.markdownhelperkeyboard.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetypeCorrectionTest {
    private val memory = RetypeCorrection.Memory(
        reading = "きょうははいしゃにいく",
        surface = "今日は敗者に行く",
        segments = listOf("きょうは" to "今日は", "はいしゃに" to "敗者に", "いく" to "行く"),
        revertedAt = 1_000L,
    )

    @Test
    fun sameReadingDifferentSurfaceWeakensOldAndReinforcesNew() {
        val plan = RetypeCorrection.plan(
            memory = memory,
            committedReading = "きょうははいしゃにいく",
            committedText = "今日は歯医者に行く",
            committedSegments = listOf("きょうは" to "今日は", "はいしゃに" to "歯医者に", "いく" to "行く"),
            now = 5_000L,
        )!!
        assertEquals(
            listOf("きょうははいしゃにいく" to "今日は敗者に行く", "はいしゃに" to "敗者に"),
            plan.weaken,
        )
        assertEquals(
            listOf("きょうははいしゃにいく" to "今日は歯医者に行く", "はいしゃに" to "歯医者に"),
            plan.reinforce,
        )
    }

    @Test
    fun noPlanWhenSameSurfaceOtherReadingOrTooLate() {
        assertNull(RetypeCorrection.plan(memory, memory.reading, memory.surface, memory.segments, 2_000L))
        assertNull(RetypeCorrection.plan(memory, "きょうは", "今日は", listOf("きょうは" to "今日は"), 2_000L))
        assertNull(
            RetypeCorrection.plan(
                memory, memory.reading, "今日は歯医者に行く", emptyList(),
                1_000L + RetypeCorrection.WINDOW_MS + 1,
            )
        )
        assertNull(RetypeCorrection.plan(null, memory.reading, "今日は歯医者に行く", emptyList(), 2_000L))
    }

    @Test
    fun longReadingSkipsWholeSentenceRowAndChangedSplitIsNotJudged() {
        val plan = RetypeCorrection.plan(
            memory = memory,
            committedReading = memory.reading,
            committedText = "今日は歯医者にいく",
            committedSegments = listOf("きょうは" to "今日は", "はいしゃにいく" to "歯医者にいく"),
            now = 2_000L,
            maxWholeReading = 5,
        )!!
        // 区切りが変わった文節（はいしゃに / いく）は比べない。文全体だけ弱める。
        assertEquals(listOf(memory.reading to memory.surface), plan.weaken)
        assertEquals(emptyList<Pair<String, String>>(), plan.reinforce)
    }
}

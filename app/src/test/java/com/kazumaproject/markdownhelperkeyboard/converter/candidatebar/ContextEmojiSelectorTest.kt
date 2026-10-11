package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextEmojiSelectorTest {
    private val entries = listOf(
        MoodEmoji("😂", "大爆笑", "smile"), MoodEmoji("😊", "笑顔", "smile"),
        MoodEmoji("😭", "号泣", "sad"), MoodEmoji("😢", "泣く", "sad"),
        MoodEmoji("🍺", "ビール", "drink"), MoodEmoji("☕", "コーヒー", "drink"),
        MoodEmoji("🐶", "犬", "animal"), MoodEmoji("🐱", "猫", "animal"),
        MoodEmoji("🙏", "お願い", "hands"), MoodEmoji("👏", "拍手", "hands"),
        MoodEmoji("🎉", "パーティ", "event"), MoodEmoji("🎂", "誕生日", "food"),
        MoodEmoji("☔", "雨", "weather"), MoodEmoji("🔥", "燃える", "weather"),
    )

    private fun withPriors(sel: ContextEmojiSelector, value: Float = -10f) {
        val keys = sel.missingPriors("m", emptyList())
        sel.putPriors("m", keys, FloatArray(keys.size) { value })
    }

    @Test
    fun bundledAssetHasAboutOneHundredFiftyUniqueKeywords() {
        val file = listOf("src/main/assets/emoji/emoji_mood.tsv", "app/src/main/assets/emoji/emoji_mood.tsv")
            .map(::File).first { it.exists() }
        val parsed = ContextEmojiSelector.parse(file.readText())
        assertTrue(parsed.size in 120..180)
        assertEquals(parsed.size, parsed.map { it.keyword }.distinct().size)
        assertEquals(parsed.size, ContextEmojiSelector(parsed).size)
    }

    @Test
    fun prefilterTakesPoolFromDistinctGroupsAndRotates() {
        val sel = ContextEmojiSelector(entries)
        val first = sel.prefilter()
        assertEquals(minOf(ContextEmojiSelector.POOL, entries.map { it.group }.distinct().size), first.size)
        assertEquals(first.size, first.map { it.group }.distinct().size)
        val second = sel.prefilter()
        val seen = (first + second).map { it.keyword }.toSet()
        // two commits cover every group and start on second members
        assertTrue(entries.map { it.group }.toSet().all { g -> (first + second).any { it.group == g } })
        assertTrue(seen.size > first.size)
    }

    @Test
    fun rankUsesPmiNotRawScore() {
        val sel = ContextEmojiSelector(entries)
        val targets = listOf(entries[0], entries[2], entries[4])
        // priors: 大爆笑 common (-2), 号泣 rare (-12), ビール (-8)
        sel.missingPriors("m", targets)
        sel.putPriors("m", listOf("大爆笑", "号泣", "ビール"), floatArrayOf(-2f, -12f, -8f))
        // raw: 大爆笑 -3 best, but PMI 号泣 = +6 > ビール +1 > 大爆笑 -1 (dropped)
        val picked = sel.rank(targets, floatArrayOf(-3f, -6f, -7f))
        assertEquals(listOf(1, 2), picked)
    }

    @Test
    fun rankSkipsKeywordsWithoutPriorAndCapsAtThree() {
        val sel = ContextEmojiSelector(entries)
        val targets = entries.take(6)
        sel.missingPriors("m", targets)
        sel.putPriors("m", targets.take(5).map { it.keyword }, FloatArray(5) { -10f })
        val picked = sel.rank(targets, floatArrayOf(-1f, -2f, -3f, -4f, -5f, 0f))
        assertEquals(listOf(0, 1, 2), picked)
        assertFalse(5 in picked)
    }

    @Test
    fun winnersStayInNextPrefilterAndDropWhenTheyLose() {
        val sel = ContextEmojiSelector(entries)
        withPriors(sel)
        val targets = sel.prefilter()
        val scores = FloatArray(targets.size) { -20f }.also { it[3] = -1f }
        assertEquals(listOf(3), sel.rank(targets, scores))
        val next = sel.prefilter()
        assertEquals(targets[3], next.first())
        assertEquals(1, next.count { it.group == targets[3].group })
        sel.rank(next, FloatArray(next.size) { -20f })
        assertFalse(targets[3] in sel.prefilter().take(1))
    }

    @Test
    fun priorsResetWhenModelChangesAndAreBatched() {
        val sel = ContextEmojiSelector(entries)
        withPriors(sel)
        assertTrue(sel.missingPriors("m").isEmpty())
        val again = sel.missingPriors("other", listOf(entries[5]))
        assertEquals("コーヒー", again.first())
        assertTrue(again.size <= ContextEmojiSelector.PRIOR_BATCH)
        assertEquals(null, sel.prior("大爆笑"))
    }

    @Test
    fun prefilterPutsKeywordsFoundInLeftContextFirst() {
        val sel = ContextEmojiSelector(entries)
        val pool = sel.prefilter("昨日は友達とビールを飲んで大爆笑した")
        assertEquals(listOf("大爆笑", "ビール"), pool.take(2).map { it.keyword })
    }

    @Test
    fun matchedKeywordPassesWithSlightlyNegativePmi() {
        val sel = ContextEmojiSelector(entries)
        withPriors(sel, -10f)
        val pool = sel.prefilter("ビール")
        val scores = FloatArray(pool.size) { -20f }
        scores[0] = -10.5f // ビール: PMI -0.5（文脈に出た語なので出す）
        assertEquals(listOf(0), sel.rank(pool, scores))
    }

    @Test
    fun priorsSurviveExportImportForSameModelOnly() {
        val sel = ContextEmojiSelector(entries)
        withPriors(sel, -7f)
        val text = sel.exportPriors("m", "m|1|2")!!
        val fresh = ContextEmojiSelector(entries)
        assertEquals(0, fresh.importPriors("m", "m|9|9", text))
        assertEquals(entries.size, fresh.importPriors("m", "m|1|2", text))
        assertTrue(fresh.missingPriors("m").isEmpty())
        assertEquals(-7f, fresh.prior("大爆笑")!!, 0f)
    }

    @Test
    fun plainMatchesOnlyLookAtTheCurrentSentence() {
        val sel = ContextEmojiSelector(entries)
        assertEquals(listOf("☕"), sel.plainMatches("ビール飲んだ。今からコーヒー").map { it.emoji })
        assertEquals(listOf("☕"), sel.plainMatches("今からコーヒー。").map { it.emoji })
        assertTrue(sel.plainMatches("ビール飲んだ。\n次の話").isEmpty())
    }
}

package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenContextTest {
    @After
    fun tearDown() = ScreenContextStore.clear()

    @Test
    fun commitCacheIsPerAppAndExpires() {
        ScreenContextStore.recordCommit("chat", "山口県庁に行った", 0L)
        val b = ScreenContextStore.snapshot("chat", 1000L)!!
        assertEquals(ScreenContextBonus.W_COMMIT, b.tokenBonus("県庁"), 1e-9)
        assertEquals(0.0, b.tokenBonus("県長"), 1e-9)
        assertNull(ScreenContextStore.snapshot("mail", 1000L))
        assertNull(ScreenContextStore.snapshot("chat", ScreenContextStore.COMMIT_TTL_MS + 1))
    }

    @Test
    fun singleCharAndKanaTokensGetNoBonus() {
        ScreenContextStore.recordEditor("a", "今日は草が生えた", "", 0L)
        val b = ScreenContextStore.snapshot("a", 0L)!!
        assertEquals(0.0, b.tokenBonus("日"), 1e-9) // くさ→日 を画面の 今日 で押さない
        assertEquals(0.0, b.tokenBonus("草"), 1e-9)
        assertEquals(0.0, b.tokenBonus("は"), 1e-9)
        assertEquals(ScreenContextBonus.W_EDITOR, b.tokenBonus("今日"), 1e-9)
    }

    @Test
    fun screenLayerExpiresQuickly() {
        ScreenContextStore.onKeyboardShown("a")
        ScreenContextStore.recordScreen("a", listOf("県庁前のバス停", "ok"), 0L)
        assertEquals(ScreenContextBonus.W_SCREEN, ScreenContextStore.snapshot("a", 0L)!!.tokenBonus("県庁"), 1e-9)
        assertNull(ScreenContextStore.snapshot("a", ScreenContextStore.SCREEN_TTL_MS + 1))
    }

    @Test
    fun bonusOnlyForCloseTopCandidatesAndCapped() {
        val b = ScreenContextBonus(editor = "", commits = "県庁 県庁所在地", screen = "")
        val tokens = listOf(listOf("県長", "に"), listOf("県庁", "に"), listOf("剣頂", "に"), listOf("県庁", "所在地", "に"))
        val add = b.bonuses(tokens, doubleArrayOf(2.0, 1.8, 1.7, -5.0))
        assertEquals(0.0, add[0], 1e-9)
        assertEquals(ScreenContextBonus.W_COMMIT, add[1], 1e-9)
        assertEquals(0.0, add[2], 1e-9)
        assertEquals(0.0, add[3], 1e-9) // 1 位から遠い候補は押さない
        val capped = b.bonuses(listOf(listOf("県庁", "県庁所在地"), listOf("けんちょう")), doubleArrayOf(1.0, 1.0))
        assertEquals(ScreenContextBonus.CAP, capped[0], 1e-9)
    }

    @Test
    fun gateUsesBonusToKeepTopicConsistency() {
        // コストだけのモデル（SumireLmGateTest と同じ合成表）: 県長 が僅差で 1 位 → 直前に確定した 県庁 が勝つ
        val reranker = SumireLmGateTest().syntheticReranker()
        val input = "けんちょうに"
        fun c(s: String, cost: Int) = com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate(s, 1, input.length.toUByte(), cost)
        val segs = mapOf(
            "県長に" to listOf(com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment(0, 5, "県長"), com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment(5, 6, "に")),
            "県庁に" to listOf(com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment(0, 5, "県庁"), com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment(5, 6, "に")),
        )
        val list = listOf(c("県長に", 1000), c("県庁に", 1300))
        assertEquals("県長に", SumireLmGate.rerank(reranker, input, "", list, { segs.getValue(it.string) })!!.top.string)
        ScreenContextStore.recordCommit("chat", "山口県庁", 0L)
        val bonus = ScreenContextStore.snapshot("chat", 0L)
        assertEquals("県庁に", SumireLmGate.rerank(reranker, input, "", list, { segs.getValue(it.string) }, contextBonus = bonus)!!.top.string)
        // 大差なら押さない（未知の漢字を無理に出さない）
        val far = listOf(c("県長に", 1000), c("県庁に", 4000))
        assertEquals("県長に", SumireLmGate.rerank(reranker, input, "", far, { segs.getValue(it.string) }, contextBonus = bonus)!!.top.string)
        assertTrue(true)
    }

    @Test
    fun screenKeepsOnlyWordsAndOnlyWhileKeyboardVisible() {
        ScreenContextStore.clear()
        ScreenContextStore.onKeyboardHidden()
        ScreenContextStore.recordScreen("a", listOf("県庁前のバス停"), 0L)
        assertNull(ScreenContextStore.snapshot("a", 0L)) // キーボードが出ていない
        ScreenContextStore.onKeyboardShown("a")
        ScreenContextStore.recordScreen("a", listOf("今日は県庁前のバス停で、パスワード1234を見た"), 0L)
        assertEquals(listOf("県庁前", "バス停", "パスワード"), ScreenContextStore.extractWords("今日は県庁前のバス停で、パスワード1234を見た").drop(1))
        assertEquals(4, ScreenContextStore.stats(0L).screenWords) // 今日・県庁前・バス停・パスワード
        val snap = ScreenContextStore.snapshot("a", 0L)!!
        assertEquals(0.0, snap.tokenBonus("見た"), 1e-9)
        assertEquals(ScreenContextBonus.W_SCREEN, snap.tokenBonus("県庁"), 1e-9)
        ScreenContextStore.onKeyboardHidden()
        assertNull(ScreenContextStore.snapshot("a", 0L)) // 隠れたら捨てる
    }
}

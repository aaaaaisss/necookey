package com.kazumaproject.markdownhelperkeyboard.ime_service.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripData
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripItem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/** チップ列の並べ方と当たり判定（1 文字 = 10px で測る）、長押しの区切りポップアップの純粋な部分。 */
class ChipStripLayoutTest {

    private val spec = ChipStripLayout(chipPadding = 10f, gap = 6f)
    private val measure: (String) -> Float = { it.length * 10f }

    @Test
    fun layoutPlacesChipsWithPaddingAndGap() {
        val laid = spec.layout(ChipStripData(listOf(ChipStripItem("では", false), ChipStripItem("着物", true))), measure)
        // [6, 46) [52, 92)、幅 98
        assertEquals(listOf(ChipStripLayout.Box(6f, 46f), ChipStripLayout.Box(52f, 92f)), laid.chips)
        assertEquals(98f, laid.width)
    }

    @Test
    fun chipAtOnlyInsideChips() {
        val laid = spec.layout(ChipStripData(listOf(ChipStripItem("では", false), ChipStripItem("着物", false))), measure)
        assertEquals(0, spec.chipAt(laid, 20f))
        assertEquals(0, spec.chipAt(laid, 45f))
        assertEquals(1, spec.chipAt(laid, 80f))
        // 隙間 [46, 52) と左右の余白はチップではない（隙間は joinAt で くっつける）
        assertEquals(-1, spec.chipAt(laid, 49f))
        assertEquals(-1, spec.chipAt(laid, 2f))
        assertEquals(-1, spec.chipAt(laid, 200f))
    }

    @Test
    fun joinAtCoversGapAndThinChipEdgesOnly() {
        // [6, 46) [52, 92) [98, 128)
        val laid = spec.layout(
            ChipStripData(listOf(ChipStripItem("では", false), ChipStripItem("着物", false), ChipStripItem("だ", false))),
            measure,
        )
        // 帯 0|1 = [46 - min(6, 8), 52 + min(6, 8)) = [40, 58)
        assertEquals(0, spec.joinAt(laid, 49f, edgeSlop = 6f))
        assertEquals(0, spec.joinAt(laid, 41f, edgeSlop = 6f))
        assertEquals(0, spec.joinAt(laid, 57f, edgeSlop = 6f))
        assertNull(spec.joinAt(laid, 39f, edgeSlop = 6f)) // チップ 0 の中 = 選択
        assertNull(spec.joinAt(laid, 70f, edgeSlop = 6f))
        // 端の幅が大きくても各チップの幅の 1/5 まで: 帯 1|2 = [92 - 8, 98 + 6) = [84, 104)
        assertEquals(1, spec.joinAt(laid, 103f, edgeSlop = 12f))
        assertNull(spec.joinAt(laid, 104f, edgeSlop = 12f))
        assertNull(spec.joinAt(laid, 83f, edgeSlop = 12f))
        assertEquals(2, spec.chipAt(laid, 104f))
        // 左右の余白・1 チップだけ は くっつけない
        assertNull(spec.joinAt(laid, 2f, edgeSlop = 6f))
        val one = spec.layout(ChipStripData(listOf(ChipStripItem("では", false))), measure)
        assertNull(spec.joinAt(one, 47f, edgeSlop = 6f))
    }

    @Test
    fun scrollKeepsFocusVisibleOrShowsRightEnd() {
        val items = (0 until 6).map { ChipStripItem("ああああ", it == 1) }
        val data = ChipStripData(items)
        val laid = spec.layout(data, measure) // 1 チップ 60、幅 6 + 6×66 = 402
        assertEquals(0f, spec.scrollFor(laid, data, 500f, 0f)) // 収まる
        assertEquals(0f, spec.scrollFor(laid, data, 200f, 0f)) // 選択チップ [72,132) は見えている
        val tail = ChipStripData(items.map { it.copy(selected = false) })
        assertEquals(202f, spec.scrollFor(laid, tail, 200f, 0f)) // 選択なし → 右端
        val last = ChipStripData(items.mapIndexed { i, it -> it.copy(selected = i == 5) })
        assertEquals(202f, spec.scrollFor(laid, last, 200f, 0f))
    }

    @Test
    fun scrollKeepsManualPositionUnlessFocusOrReadingChanges() {
        val items = (0 until 6).map { ChipStripItem("ああああ", false) }
        val data = ChipStripData(items)
        val laid = spec.layout(data, measure) // 幅 402、表示 200 → 最大 202
        assertEquals(202f, spec.maxScroll(laid, 200f))
        // 選択なし: 打っている間は右端、手でスクロールした後（followEnd = false）はその位置を保つ（範囲内に丸める）
        assertEquals(202f, spec.scrollFor(laid, data, 200f, 50f))
        assertEquals(50f, spec.scrollFor(laid, data, 200f, 50f, followEnd = false))
        assertEquals(202f, spec.scrollFor(laid, data, 200f, 900f, followEnd = false))
        // 選択あり: reveal で最小限動かして見せる。reveal しない更新では手の位置のまま
        val sel0 = ChipStripData(items.mapIndexed { i, it -> it.copy(selected = i == 0) })
        assertEquals(0f, spec.scrollFor(laid, sel0, 200f, 150f))
        assertEquals(150f, spec.scrollFor(laid, sel0, 200f, 150f, reveal = false))
    }

    @Test
    fun revealScrollBringsChipIntoViewWithMinimalMove() {
        val data = ChipStripData((0 until 6).map { ChipStripItem("ああああ", false) })
        val laid = spec.layout(data, measure) // チップ [6+66k, 66+66k)
        assertEquals(10f, spec.revealScroll(laid, 1, 200f, 10f)) // [72,132) は見えている
        assertEquals(136f, spec.revealScroll(laid, 4, 200f, 0f)) // [270,330) → 右端 336 - 200
        assertEquals(198f, spec.revealScroll(laid, 3, 200f, 202f)) // [204,264) → 左端 198
        // 表示より広いチップは左端を優先
        val wide = spec.layout(ChipStripData(listOf(ChipStripItem("あ".repeat(30), false), ChipStripItem("い", false))), measure)
        assertEquals(0f, spec.revealScroll(wide, 0, 100f, 150f))
    }

    // ---- 長押しの区切りポップアップ ----

    private fun item(label: String, reading: String) = ChipStripItem(label, false, reading = reading)

    @Test
    fun segSourceJoinsReadingsAndBoundaries() {
        val data = ChipStripData(listOf(item("では", "では"), item("着物", "きもの"), item("を", "を")))
        assertEquals("ではきものを" to setOf(2, 5), ChipStripLayout.segSource(data))
        assertEquals("きもの" to emptySet<Int>(), ChipStripLayout.segSource(ChipStripData(listOf(item("着物", "きもの")))))
        assertEquals("" to emptySet<Int>(), ChipStripLayout.segSource(ChipStripData.EMPTY))
    }

    @Test
    fun segOffsetsFollowCodePoints() {
        assertArrayEquals(intArrayOf(0, 1, 2, 3), ChipStripLayout.segOffsets(ChipStripLayout.codePoints("きもの")))
        // サロゲートペア（𠮷 = 2 単位）は 1 マス
        val sp = ChipStripLayout.codePoints("𠮷やの")
        assertEquals(listOf("𠮷", "や", "の"), sp)
        assertArrayEquals(intArrayOf(0, 2, 3, 4), ChipStripLayout.segOffsets(sp))
    }

    @Test
    fun toggleBoundaryAddsAndRemoves() {
        val b = setOf(2, 5)
        assertEquals(setOf(2, 5, 3), ChipStripLayout.toggleBoundary(b, 3))
        assertEquals(setOf(5), ChipStripLayout.toggleBoundary(b, 2))
        assertEquals(b, ChipStripLayout.toggleBoundary(ChipStripLayout.toggleBoundary(b, 4), 4))
    }

    @Test
    fun holdStartGapMapsPressFractionToReadingLetters() {
        // では[6,46) 着物[52,92)、読み では|きもの（5 字、境目 1..4）
        val data = ChipStripData(listOf(item("では", "では"), item("着物", "きもの")))
        val laid = spec.layout(data, measure)
        assertEquals(1, ChipStripLayout.holdStartGap(laid, data, 26f)) // では の真ん中 → で|は
        assertEquals(1, ChipStripLayout.holdStartGap(laid, data, 7f)) // 左端は境目 0 → 1 に丸める
        assertEquals(2, ChipStripLayout.holdStartGap(laid, data, 45f)) // では の右端 = 区切り
        assertEquals(2, ChipStripLayout.holdStartGap(laid, data, 49f)) // 隙間 = 区切り
        assertEquals(3, ChipStripLayout.holdStartGap(laid, data, 65f)) // 着物 の 1/3 → き|もの
        assertEquals(4, ChipStripLayout.holdStartGap(laid, data, 91f)) // 末尾 → 4 に丸める
        assertNull(ChipStripLayout.holdStartGap(spec.layout(ChipStripData(listOf(item("木", "き"))), measure), ChipStripData(listOf(item("木", "き"))), 10f))
    }

    @Test
    fun holdCursorMovesRelativelyAndReanchorsAtEnds() {
        val c = ChipStripLayout.HoldCursor(count = 6, startGap = 2, startX = 100f, step = 20f)
        assertEquals(2, c.gap)
        assertFalse(c.move(109f)) // 半歩未満は動かない
        assertTrue(c.move(111f)); assertEquals(3, c.gap)
        assertTrue(c.move(160f)); assertEquals(5, c.gap) // 端で止まる（3 歩分 = 5）
        c.move(300f); assertEquals(5, c.gap)
        // 端で基準を取り直したので、戻すとすぐ動く
        assertTrue(c.move(280f)); assertEquals(4, c.gap)
        c.move(-500f); assertEquals(1, c.gap)
        assertTrue(c.move(-480f)); assertEquals(2, c.gap)
    }

    @Test
    fun holdWindowShiftsOnlyAtEdges() {
        assertEquals(0, ChipStripLayout.holdWindowFirst(count = 5, visible = 8, gap = 3, first = 2)) // 全部入る
        // 20 字を 8 マスで: 窓 [4,12) の境目 5..11 の間は動かない
        assertEquals(4, ChipStripLayout.holdWindowFirst(20, 8, 7, 4))
        assertEquals(4, ChipStripLayout.holdWindowFirst(20, 8, 11, 4))
        assertEquals(5, ChipStripLayout.holdWindowFirst(20, 8, 12, 4))
        assertEquals(3, ChipStripLayout.holdWindowFirst(20, 8, 4, 4))
        assertEquals(12, ChipStripLayout.holdWindowFirst(20, 8, 19, 15)) // 最後まで
        assertEquals(6, ChipStripLayout.holdInitialFirst(20, 8, gap = 9, preferredSlot = 3))
        assertEquals(0, ChipStripLayout.holdInitialFirst(20, 8, gap = 2, preferredSlot = 5))
    }
}

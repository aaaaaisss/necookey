package com.kazumaproject.markdownhelperkeyboard.ime_service.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripData
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripItem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** チップ列の並べ方と当たり判定（1 文字 = 10px で測る）、区切り編集モードの純粋な部分。 */
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

    // ---- 区切り編集モード ----

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
    fun segGapAtPicksNearestGapWithoutSnap() {
        // マス幅 30、先頭 8: 境目 k の x = 8 + 30k（k = 1..4 が切れる所、5 文字）
        fun gap(x: Float) = ChipStripLayout.segGapAt(x, start = 8f, cellWidth = 30f, count = 5)
        assertEquals(1, gap(38f))
        assertEquals(1, gap(30f)) // 1 マス目の右寄り → 境目 1
        assertEquals(1, gap(52f)) // 境目 1 と 2 の中間（53）より左
        assertEquals(2, gap(54f))
        assertEquals(4, gap(150f)) // 末尾のマス → 一番近い切れる所（境目 4）
        assertEquals(1, gap(9f)) // 先頭のマス → 境目 1
        assertNull(gap(7f)) // マスの外
        assertNull(gap(159f))
        assertNull(ChipStripLayout.segGapAt(20f, 8f, 30f, 1)) // 1 文字は区切れない
        // どの境目にもちょうどその上をタップすれば届く
        for (k in 1..4) assertEquals(k, gap(8f + 30f * k))
    }

    @Test
    fun cellsDoNotMoveWhenBoundariesChange() {
        // マスの位置は区切りに依存しない（引数に区切りが無い）。幅も文字数だけで決まる
        assertEquals(8f + 30f * 3, ChipStripLayout.segCellLeft(8f, 30f, 3))
        assertEquals(16f + 150f, ChipStripLayout.segWidth(8f, 30f, 5))
    }

    @Test
    fun toggleBoundaryAddsAndRemoves() {
        val b = setOf(2, 5)
        assertEquals(setOf(2, 5, 3), ChipStripLayout.toggleBoundary(b, 3))
        assertEquals(setOf(5), ChipStripLayout.toggleBoundary(b, 2))
        assertEquals(b, ChipStripLayout.toggleBoundary(ChipStripLayout.toggleBoundary(b, 4), 4))
    }

    @Test
    fun segEntryScrollKeepsPressedChipInPlace() {
        // 押したチップの先頭の文字のマス（左端 98）を、チップの左端があった画面位置 40 に置く → 58
        assertEquals(58f, ChipStripLayout.segEntryScroll(cellLeft = 98f, chipScreenLeft = 40f, maxScroll = 300f))
        assertEquals(0f, ChipStripLayout.segEntryScroll(10f, 40f, 300f)) // 左端より前には行かない
        assertEquals(30f, ChipStripLayout.segEntryScroll(200f, 0f, 30f)) // 最大で止める
        assertEquals(0f, ChipStripLayout.segEntryScroll(200f, 0f, -5f)) // はみ出していない
    }
}

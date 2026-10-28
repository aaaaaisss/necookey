package com.kazumaproject.markdownhelperkeyboard.ime_service.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripData
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripItem
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidatebar.ChipStripLayout.Hit
import org.junit.Assert.assertEquals
import org.junit.Test

/** チップ列の並べ方と当たり判定（1 文字 = 10px で測る）。 */
class ChipStripLayoutTest {

    private val spec = ChipStripLayout(chipPadding = 10f, gap = 6f, letterPadding = 5f, joinSlop = 12f)
    private val measure: (String) -> Float = { it.length * 10f }

    @Test
    fun layoutPlacesChipsWithPaddingAndGap() {
        val laid = spec.layout(ChipStripData(listOf(ChipStripItem("では", false), ChipStripItem("着物", true))), measure)
        // [6, 46) [52, 92)、幅 98
        assertEquals(listOf(ChipStripLayout.Box(6f, 46f), ChipStripLayout.Box(52f, 92f)), laid.chips)
        assertEquals(98f, laid.width)
    }

    @Test
    fun hitChipJoinWithWidenedGap() {
        val laid = spec.layout(ChipStripData(listOf(ChipStripItem("では", false), ChipStripItem("着物", false))), measure)
        assertEquals(Hit.Chip(0), spec.hit(laid, 20f))
        assertEquals(Hit.Chip(1), spec.hit(laid, 80f))
        // 隙間 [46, 52) の中心 49 から ±(3 + min(12, 40/4)) = [36, 62] はくっつける
        assertEquals(Hit.Join(0), spec.hit(laid, 49f))
        assertEquals(Hit.Join(0), spec.hit(laid, 37f))
        assertEquals(Hit.Join(0), spec.hit(laid, 61f))
        assertEquals(Hit.Chip(0), spec.hit(laid, 35f))
        assertEquals(Hit.None, spec.hit(laid, 2f))
        assertEquals(Hit.None, spec.hit(laid, 200f))
    }

    @Test
    fun smallChipKeepsTapAreaAgainstJoinSlop() {
        val laid = spec.layout(ChipStripData(listOf(ChipStripItem("は", false), ChipStripItem("あ", false))), measure)
        // チップ幅 30 → くっつける領域は隙間 ±(3 + 7.5)。チップの中央はチップ
        val a = laid.chips[0]
        assertEquals(Hit.Chip(0), spec.hit(laid, a.center))
        assertEquals(Hit.Join(0), spec.hit(laid, a.right + 1f))
    }

    @Test
    fun expandedChipHitsNearestLetterGap() {
        val data = ChipStripData(listOf(ChipStripItem("では", false), ChipStripItem("着物", true, listOf("き", "も", "の"))))
        val laid = spec.layout(data, measure)
        // 展開中: 左右 5 + 文字箱 20×3 + 5 = 70 → [52, 122)、文字の箱 [57,77) [77,97) [97,117)
        assertEquals(ChipStripLayout.Box(52f, 122f), laid.chips[1])
        assertEquals(listOf(77f, 97f, 117f), laid.letters[1].map { it.right })
        assertEquals(Hit.Cut(1, 0), spec.hit(laid, 80f))
        assertEquals(Hit.Cut(1, 1), spec.hit(laid, 95f))
        assertEquals(Hit.Cut(1, 1), spec.hit(laid, 110f))
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
}

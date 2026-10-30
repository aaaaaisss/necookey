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

    @Test
    fun chipSwipeBecomesScrollOnlyWhenLongOrSlowAndScrollable() {
        fun scroll(dx: Float, ms: Long, can: Boolean = true) =
            ChipStripLayout.chipSwipeIsScroll(dx, ms, can, scrollDistance = 56f, flickMaxMs = 220L)
        assertEquals(false, scroll(30f, 80L)) // 短く速い = フリック
        assertEquals(true, scroll(60f, 80L)) // 長い
        assertEquals(true, scroll(25f, 300L)) // ゆっくり
        assertEquals(false, scroll(200f, 900L, can = false)) // はみ出していなければ常にフリック
    }

    @Test
    fun edgeAutoScrollSpeedGrowsTowardEdges() {
        fun speed(x: Float) = ChipStripLayout.edgeAutoScrollSpeed(x, left = 34f, right = 334f, edge = 30f, maxSpeed = 600f)
        assertEquals(0f, speed(184f))
        assertEquals(0f, speed(64f))
        assertEquals(-300f, speed(49f))
        assertEquals(-600f, speed(10f))
        assertEquals(300f, speed(319f))
        assertEquals(600f, speed(400f))
        assertEquals(0f, ChipStripLayout.edgeAutoScrollSpeed(50f, 0f, 50f, 30f, 600f)) // 狭すぎる
    }

    // ---- 長押しの切るカーソル（磁石つき） ----

    /** 1 字 20px、境目 k の x = 20k（とうきょうと = 6 字）。 */
    private val xs6 = (0..6).map { it * 20f }

    @Test
    fun snapCutGapPicksNearestCharBoundaryWithoutWordGaps() {
        fun gap(x: Float) = ChipStripLayout.snapCutGap(xs6, emptySet(), x, snapBonus = 12f)
        assertEquals(1, gap(22f))
        assertEquals(3, gap(55f))
        assertEquals(5, gap(95f))
        // 両端（とその外）は切らない = null（離すと取り消し）
        assertEquals(null, gap(3f))
        assertEquals(null, gap(-40f))
        assertEquals(null, gap(118f))
        assertEquals(null, gap(300f))
    }

    @Test
    fun snapCutGapPullsTowardWordBoundaryWithinAboutOneChar() {
        // 語の境目 = 5（とうきょう｜と）。吸い付き 12px（字幅 20 の 0.6）
        val words = setOf(5)
        fun gap(x: Float) = ChipStripLayout.snapCutGap(xs6, words, x, snapBonus = 12f)
        assertEquals(5, gap(100f))
        assertEquals(5, gap(85f)) // 4 との中間 (90) より 4 寄りでも、吸い付き圏内（境目まで 15 − 12 = 3 < 5）なら 5
        assertEquals(5, gap(115f)) // 右端寄りでも 5（端より境目が強い）
        // それでも隣の文字の境目 4 には止まれる（真上なら 0 < 20 − 12）
        assertEquals(4, gap(80f))
        assertEquals(4, gap(77f))
        assertEquals(3, gap(60f))
    }

    @Test
    fun snapCutGapKeepsEveryBoundaryReachableEvenWithHugeBonus() {
        // 語の境目が並んでいても、ボーナスは隣との間隔の 3/4 までなので全部の境目に止まれる
        val words = setOf(1, 2, 4, 5)
        for (k in 1..5) {
            assertEquals(k, ChipStripLayout.snapCutGap(xs6, words, k * 20f, snapBonus = 1000f))
        }
        // 1 字のチップ（境目なし）は常に null
        assertEquals(null, ChipStripLayout.snapCutGap(listOf(0f, 20f), emptySet(), 10f, 12f))
    }

    @Test
    fun snapCutGapHandlesUnevenLetterWidths() {
        // 文字幅がばらばら: 境目 x = 0, 10, 40, 50
        val xs = listOf(0f, 10f, 40f, 50f)
        assertEquals(2, ChipStripLayout.snapCutGap(xs, setOf(2), 28f, snapBonus = 12f))
        assertEquals(1, ChipStripLayout.snapCutGap(xs, setOf(2), 12f, snapBonus = 12f))
        // 語の境目 2 の隣の間隔は 10 → ボーナスは 7.5 まで。x = 46 なら端 (50) と 2 (40) で 2 が勝つ（6 − 7.5 < 4）
        assertEquals(2, ChipStripLayout.snapCutGap(xs, setOf(2), 46f, snapBonus = 12f))
        assertEquals(null, ChipStripLayout.snapCutGap(xs, setOf(2), 49f, snapBonus = 12f))
    }

    @Test
    fun wordGapIndicesAndGapOffsetMapBetweenLettersAndReading() {
        val letters = listOf("と", "う", "き", "ょ", "う", "と")
        assertEquals(setOf(5), ChipStripLayout.wordGapIndices(letters, listOf(5)))
        assertEquals(emptySet<Int>(), ChipStripLayout.wordGapIndices(letters, emptyList()))
        assertEquals(5, ChipStripLayout.gapOffset(letters, 5))
        // サロゲートペア（𠮷 = 2 UTF-16）: 読みの位置 2 は文字の境目 1
        val sp = ChipStripLayout.codePoints("𠮷やの")
        assertEquals(listOf("𠮷", "や", "の"), sp)
        assertEquals(setOf(1, 2), ChipStripLayout.wordGapIndices(sp, listOf(2, 3)))
        assertEquals(3, ChipStripLayout.gapOffset(sp, 2))
    }

    @Test
    fun cutPreviewShowsReadingWithMark() {
        val letters = ChipStripLayout.codePoints("とうきょうと")
        assertEquals("とうきょう｜と", ChipStripLayout.cutPreview(letters, 5))
        assertEquals("とうきょうと", ChipStripLayout.cutPreview(letters, null))
        assertEquals("とうきょうと", ChipStripLayout.cutPreview(letters, 0)) // 端 = 切らない
        val long = ChipStripLayout.codePoints("あいうえおかきくけこさしすせそ")
        assertEquals("…うえおかきく｜けこさしすせ…", ChipStripLayout.cutPreview(long, 8))
        assertEquals("あいうえおかきくけこさし…", ChipStripLayout.cutPreview(long, null))
    }
}

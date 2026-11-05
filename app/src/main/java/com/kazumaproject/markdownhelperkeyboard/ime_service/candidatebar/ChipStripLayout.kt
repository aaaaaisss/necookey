package com.kazumaproject.markdownhelperkeyboard.ime_service.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripData
import kotlin.math.abs

/**
 * 文節チップ列の並べ方と当たり判定（Android なし。文字幅は [measure] で注入）。
 *
 * - 普段: チップ = 左右に [chipPadding] を付けた表記。チップの間は [gap]。チップの上のタップ = 選択、
 *   2 つのチップの間（[joinAt]: 隙間＋両側のチップ端の細い帯）のタップ = くっつける（2026-11-04 復活）。
 * - 長押し（docs/CHIP_BAR.md「2026-11-05 長押しの区切りポップアップ」）: チップ列の上に読みのかなを大きく出し、押した所に一番近い
 *   文字の境目（[holdStartGap]）にカーソル。指を置いたまま横に動かすとカーソルが相対的に動き（[HoldCursor]）、離すとそこの区切りを
 *   入れる／消す（[toggleBoundary]）。長い読みは窓（[holdWindowFirst]）で見せる。
 */
class ChipStripLayout(
    val chipPadding: Float,
    val gap: Float,
) {
    data class Box(val left: Float, val right: Float) {
        val width: Float get() = right - left
        val center: Float get() = (left + right) / 2f
        operator fun contains(x: Float): Boolean = x >= left && x < right
    }

    data class Laid(
        val chips: List<Box>,
        val width: Float,
    ) {
        companion object {
            val EMPTY = Laid(emptyList(), 0f)
        }
    }

    fun layout(data: ChipStripData, measure: (String) -> Float): Laid {
        if (data.items.isEmpty()) return Laid.EMPTY
        val chips = ArrayList<Box>(data.items.size)
        var x = gap
        for (item in data.items) {
            val left = x
            x += measure(item.label) + chipPadding * 2f
            chips += Box(left, x)
            x += gap
        }
        return Laid(chips, x)
    }

    /** 指の [x]（チップ列の座標 = スクロール込み）の下のチップ。チップの外（隙間・余白）は -1。 */
    fun chipAt(laid: Laid, x: Float): Int = laid.chips.indexOfFirst { x in it }

    /**
     * くっつける当たり判定: [x] がチップ i と i+1 の間なら i、それ以外は null。帯 = 隙間全体＋両側のチップの端
     * （それぞれ min([edgeSlop], チップ幅 × [EDGE_FRACTION])。チップの中央側の大部分は選択のまま残る）。
     * 普段のタップはこれを先に見て、外れたら [chipAt]。
     */
    fun joinAt(laid: Laid, x: Float, edgeSlop: Float): Int? {
        val c = laid.chips
        for (i in 0 until c.size - 1) {
            val a = c[i]
            val b = c[i + 1]
            val left = a.right - minOf(edgeSlop, a.width * EDGE_FRACTION)
            val right = b.left + minOf(edgeSlop, b.width * EDGE_FRACTION)
            if (x >= left && x < right) return i
        }
        return null
    }

    /**
     * はみ出したときの横スクロール量（0..[maxScroll]）。
     * - [reveal] = true で選択中のチップがあれば、[current] から最小限動かしてそのチップを見せる。
     * - 見せるチップが無いとき: [followEnd] = true なら右端（打っている所）、false なら [current] のまま（手でスクロールした位置を保つ）。
     */
    fun scrollFor(
        laid: Laid,
        data: ChipStripData,
        viewWidth: Float,
        current: Float,
        reveal: Boolean = true,
        followEnd: Boolean = true,
    ): Float {
        val overflow = maxScroll(laid, viewWidth)
        if (overflow <= 0f) return 0f
        val focus = data.selectedIndex
        if (focus >= 0 && focus < laid.chips.size) {
            return if (reveal) revealScroll(laid, focus, viewWidth, current) else current.coerceIn(0f, overflow)
        }
        return if (followEnd) overflow else current.coerceIn(0f, overflow)
    }

    /** スクロールできる最大量（はみ出していなければ 0）。 */
    fun maxScroll(laid: Laid, viewWidth: Float): Float = (laid.width - viewWidth).coerceAtLeast(0f)

    /** チップ [index] が（両側の隙間ごと）見えるように [current] から最小限動かしたスクロール量。 */
    fun revealScroll(laid: Laid, index: Int, viewWidth: Float, current: Float): Float {
        val overflow = maxScroll(laid, viewWidth)
        if (overflow <= 0f) return 0f
        val box = laid.chips.getOrNull(index) ?: return current.coerceIn(0f, overflow)
        var scroll = current.coerceIn(0f, overflow)
        // チップが表示幅より広いときは左端を優先（読み始めが見える）
        if (box.right + gap > scroll + viewWidth) scroll = box.right + gap - viewWidth
        if (box.left - gap < scroll) scroll = box.left - gap
        return scroll.coerceIn(0f, overflow)
    }

    companion object {
        /** くっつける帯がチップに食い込む最大の割合（チップ幅の 1/5。小さいチップでも 3/5 は選択に残る）。 */
        const val EDGE_FRACTION = 0.2f

        /** 読みを 1 文字（コードポイント）ずつ（presenter の letters と同じ分け方）。 */
        fun codePoints(reading: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            while (i < reading.length) {
                val next = reading.offsetByCodePoints(i, 1)
                out += reading.substring(i, next)
                i = next
            }
            return out
        }

        // ---- 区切り編集モード ----

        /** 区切り編集モードの元: 読み全体（チップの読みを繋げたもの）と今の区切り（読みの UTF-16 位置、0 と末尾を除く）。 */
        fun segSource(data: ChipStripData): Pair<String, Set<Int>> {
            val reading = StringBuilder()
            val bounds = LinkedHashSet<Int>()
            data.items.forEachIndexed { i, item ->
                reading.append(item.reading)
                if (i < data.items.lastIndex) bounds += reading.length
            }
            return reading.toString() to bounds.filterTo(LinkedHashSet()) { it in 1 until reading.length }
        }

        /** 文字の境目 k（0..文字数）の読みの UTF-16 位置。[letters] は [codePoints] の結果。 */
        fun segOffsets(letters: List<String>): IntArray {
            val out = IntArray(letters.size + 1)
            for (k in letters.indices) out[k + 1] = out[k] + letters[k].length
            return out
        }

        /** 区切り（読みの位置）[at] を入れる／消す。 */
        fun toggleBoundary(bounds: Set<Int>, at: Int): Set<Int> =
            if (at in bounds) bounds - at else bounds + at

        // ---- 長押しの区切りポップアップ（2026-11-05） ----

        /**
         * 長押しした所に一番近い文字の境目（1..文字数−1、文字単位）。チップの上なら、そのチップの表記の幅のうち押した割合を
         * チップの読みの文字数に当てて丸める（チップの両端 = その区切り）。チップの外（隙間・余白）なら一番近いチップの端。
         * 読みが 1 文字以下なら null。
         */
        fun holdStartGap(laid: Laid, data: ChipStripData, x: Float): Int? {
            val counts = data.items.map { codePoints(it.reading).size }
            val total = counts.sum()
            if (total < 2 || laid.chips.isEmpty()) return null
            var i = laid.chips.indexOfFirst { x in it }
            val frac: Float
            if (i < 0) {
                // 一番近いチップの近い方の端
                var best = 0
                var bestD = Float.MAX_VALUE
                laid.chips.forEachIndexed { k, box ->
                    val d = minOf(abs(x - box.left), abs(x - box.right))
                    if (d < bestD) { bestD = d; best = k }
                }
                i = best
                frac = if (abs(x - laid.chips[i].left) <= abs(x - laid.chips[i].right)) 0f else 1f
            } else {
                val box = laid.chips[i]
                frac = if (box.width > 0f) ((x - box.left) / box.width).coerceIn(0f, 1f) else 0.5f
            }
            val before = counts.take(i.coerceAtMost(counts.size)).sum()
            val inChip = Math.round(frac * counts.getOrElse(i) { 0 })
            return (before + inChip).coerceIn(1, total - 1)
        }

        /**
         * ポップアップに出す文字の範囲の先頭: 全部入れば 0。入らなければ今の [first] を保ち、カーソル [gap] が
         * 窓の端（両端の境目の 1 つ内側）を越えたときだけ 1 字ずつずらす（文字はふだん動かない）。
         */
        fun holdWindowFirst(count: Int, visible: Int, gap: Int, first: Int): Int {
            if (visible <= 0 || count <= visible) return 0
            var f = first.coerceIn(0, count - visible)
            if (gap < f + 1) f = gap - 1
            if (gap > f + visible - 1) f = gap - visible + 1
            return f.coerceIn(0, count - visible)
        }

        /** 窓の先頭の初期値: カーソル [gap] が窓の中のなるべく [preferredSlot] 番目の境目に来るように。 */
        fun holdInitialFirst(count: Int, visible: Int, gap: Int, preferredSlot: Int): Int {
            if (visible <= 0 || count <= visible) return 0
            return holdWindowFirst(count, visible, gap, gap - preferredSlot.coerceIn(1, visible - 1))
        }
    }

    /**
     * 長押し中のカーソル（相対移動）: 指の横の移動量 [step] ごとに境目を 1 つ動かす（スペースキーのカーソル移動と同じ。
     * 指の絶対位置には合わせないので、ポップアップの文字が動かず、ずれも溜まらない。磁石なし）。端（1 / 文字数−1）で止まったら
     * そこで基準を取り直すので、戻すとすぐ動く。
     */
    class HoldCursor(private val count: Int, startGap: Int, startX: Float, private val step: Float) {
        var gap: Int = startGap.coerceIn(1, (count - 1).coerceAtLeast(1))
            private set
        private var anchorGap = gap
        private var anchorX = startX

        /** 指が [x] に来た。境目が変わったら true。 */
        fun move(x: Float): Boolean {
            if (count < 2 || step <= 0f) return false
            val raw = anchorGap + ((x - anchorX) / step).let { if (it >= 0f) (it + 0.5f).toInt() else -((-it + 0.5f).toInt()) }
            val g = raw.coerceIn(1, count - 1)
            if (g != raw) {
                anchorGap = g
                anchorX = x
            }
            if (g == gap) return false
            gap = g
            return true
        }
    }
}

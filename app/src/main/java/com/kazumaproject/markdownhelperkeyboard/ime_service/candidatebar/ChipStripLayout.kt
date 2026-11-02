package com.kazumaproject.markdownhelperkeyboard.ime_service.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripData

/**
 * 文節チップ列の並べ方と当たり判定（Android なし。文字幅は [measure] で注入）。
 *
 * - 普段: チップ = 左右に [chipPadding] を付けた表記。チップの間は [gap]。タップはチップの上だけ（隙間・余白は何もしない）。
 * - 区切り編集モード（長押しで入る。docs/CHIP_BAR.md「2026-11-02 区切り編集モード」）: 読み全体をかな 1 文字ずつ、
 *   同じ幅のマス（[segCellLeft]）に並べる。区切りの縦線は幅を取らない（マスの境目に描く）ので、区切りを足しても消しても
 *   文字は動かない。タップは一番近い文字の境目（[segGapAt]、磁石なし）の区切りを入れる／消す（[toggleBoundary]）。
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

        /** マス k の左端（全マス同じ幅 [cellWidth]、先頭は [start]）。k = 文字数 で右端。 */
        fun segCellLeft(start: Float, cellWidth: Float, k: Int): Float = start + cellWidth * k

        /** マス全体の幅（左右の余白 [start] 込み）。 */
        fun segWidth(start: Float, cellWidth: Float, count: Int): Float = start * 2f + cellWidth * count

        /**
         * 指の [x]（チップ列の座標）に一番近い文字の境目（1..[count]−1）。マスの外（左右の余白より外）や 1 文字以下は null。
         * 吸い付き・磁石は無い（押した所の一番近い境目）。
         */
        fun segGapAt(x: Float, start: Float, cellWidth: Float, count: Int): Int? {
            if (count < 2 || cellWidth <= 0f) return null
            if (x < start || x > start + cellWidth * count) return null
            val k = Math.round((x - start) / cellWidth)
            return k.coerceIn(1, count - 1)
        }

        /** 区切り（読みの位置）[at] を入れる／消す。 */
        fun toggleBoundary(bounds: Set<Int>, at: Int): Set<Int> =
            if (at in bounds) bounds - at else bounds + at

        /**
         * 区切り編集モードに入ったとき、指の下の文字が大きく跳ばないスクロール量: 押したチップの先頭の文字のマスを、
         * 押したチップの左端があった画面位置 [chipScreenLeft] に置く（0..[maxScroll] に収める）。
         */
        fun segEntryScroll(cellLeft: Float, chipScreenLeft: Float, maxScroll: Float): Float =
            (cellLeft - chipScreenLeft).coerceIn(0f, maxScroll.coerceAtLeast(0f))
    }
}

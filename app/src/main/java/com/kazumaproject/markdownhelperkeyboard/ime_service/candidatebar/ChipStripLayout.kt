package com.kazumaproject.markdownhelperkeyboard.ime_service.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripData

/**
 * 文節チップ列の並べ方と当たり判定（Android なし。文字幅は [measure] で注入）。
 *
 * - チップ = 左右に [chipPadding] を付けた表記。チップの間は [gap]。
 * - 展開中のチップ = かな 1 文字ずつの箱（左右 [letterPadding]）を並べたもの。箱の間（文字の間）をタップで切る。
 * - チップ間のタップでくっつける。隙間は狭いので、当たり領域を隙間の中心から左右 [joinSlop] まで広げる
 *   （ただし隣のチップ幅の 1/4 まで。小さいチップでもタップで選べるように）。
 */
class ChipStripLayout(
    val chipPadding: Float,
    val gap: Float,
    val letterPadding: Float,
    val joinSlop: Float,
) {
    data class Box(val left: Float, val right: Float) {
        val width: Float get() = right - left
        val center: Float get() = (left + right) / 2f
        operator fun contains(x: Float): Boolean = x >= left && x < right
    }

    data class Laid(
        val chips: List<Box>,
        /** 展開中のチップの文字の箱（展開していないチップは空）。 */
        val letters: List<List<Box>>,
        val width: Float,
    ) {
        companion object {
            val EMPTY = Laid(emptyList(), emptyList(), 0f)
        }
    }

    sealed interface Hit {
        data class Chip(val index: Int) : Hit
        /** チップ [index] と [index]+1 の間（くっつける）。 */
        data class Join(val index: Int) : Hit
        /** 展開中のチップ [index] の文字 [gapIndex] と [gapIndex]+1 の間（切る）。 */
        data class Cut(val index: Int, val gapIndex: Int) : Hit
        data object None : Hit
    }

    fun layout(data: ChipStripData, measure: (String) -> Float): Laid {
        if (data.items.isEmpty()) return Laid.EMPTY
        val chips = ArrayList<Box>(data.items.size)
        val letters = ArrayList<List<Box>>(data.items.size)
        var x = gap
        for (item in data.items) {
            val left = x
            if (item.expanded) {
                val boxes = ArrayList<Box>(item.letters.size)
                x += chipPadding / 2f
                for (letter in item.letters) {
                    val w = measure(letter) + letterPadding * 2f
                    boxes += Box(x, x + w)
                    x += w
                }
                x += chipPadding / 2f
                letters += boxes
            } else {
                x += measure(item.label) + chipPadding * 2f
                letters += emptyList<Box>()
            }
            chips += Box(left, x)
            x += gap
        }
        return Laid(chips, letters, x)
    }

    fun hit(laid: Laid, x: Float): Hit {
        val chips = laid.chips
        if (chips.isEmpty()) return Hit.None
        // くっつける（隙間の当たり判定を広げる）を先に見る。
        for (i in 0 until chips.lastIndex) {
            val a = chips[i]
            val b = chips[i + 1]
            val center = (a.right + b.left) / 2f
            val half = (b.left - a.right) / 2f
            val slop = minOf(joinSlop, a.width / 4f, b.width / 4f)
            if (x >= center - half - slop && x <= center + half + slop) return Hit.Join(i)
        }
        val index = chips.indexOfFirst { x in it }
        if (index < 0) return Hit.None
        val boxes = laid.letters.getOrNull(index).orEmpty()
        if (boxes.size >= 2) {
            // 文字の間のうち一番近いところ。
            var best = 0
            var bestDistance = Float.MAX_VALUE
            for (k in 0 until boxes.lastIndex) {
                val d = kotlin.math.abs(x - boxes[k].right)
                if (d < bestDistance) {
                    bestDistance = d
                    best = k
                }
            }
            return Hit.Cut(index, best)
        }
        return Hit.Chip(index)
    }

    /**
     * はみ出したときの横スクロール量（0..[maxScroll]）。
     * - [reveal] = true で展開・選択中のチップがあれば、[current] から最小限動かしてそのチップを見せる。
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
        val focus = data.expandedIndex.takeIf { it >= 0 } ?: data.selectedIndex
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
        /**
         * 長押しした表記の文字の境目 [labelIndex]（0..表記の長さ）を、チップの読みの切る位置（1..読みの長さ−1）にする。
         * 表記が読みと同じ（かなのまま）なら同じ位置、漢字なら表記の中の割合で読みに割り当てる（位置は区切りドラッグで直せる）。
         * 1 字のチップは切れないので null。
         */
        fun cutOffsetForLabel(label: String, reading: String, labelIndex: Int): Int? {
            if (reading.length < 2) return null
            val raw = if (label == reading || label.length == reading.length) labelIndex
            else if (label.isEmpty()) reading.length / 2
            else Math.round(labelIndex.toDouble() * reading.length / label.length).toInt()
            var at = raw.coerceIn(1, reading.length - 1)
            // サロゲートペアの間は切らない
            if (Character.isLowSurrogate(reading[at]) && at > 1) at--
            return at
        }

        /**
         * チップ上で横に動いた指を、スクロールにするか（true）、離すまで待ってフリック（1 字ずらす）にするか（false）。
         * フリックは短く速い（[flickMaxMs] 以内・[scrollDistance] 未満）。それより長く動かす・ゆっくり動かすとスクロール。
         * スクロールできない（はみ出していない）ときは常に false（従来どおりフリック）。
         */
        fun chipSwipeIsScroll(
            absDx: Float,
            elapsedMs: Long,
            canScroll: Boolean,
            scrollDistance: Float,
            flickMaxMs: Long,
        ): Boolean = canScroll && (absDx >= scrollDistance || elapsedMs > flickMaxMs)

        /**
         * 区切りドラッグ中、指が表示域の端（[left]..[right] の内側 [edge] 以内）に来たときの自動スクロールの速さ（px/秒、
         * 負 = 左へ）。端に近いほど速く、最大 [maxSpeed]。端から離れていれば 0。
         */
        fun edgeAutoScrollSpeed(x: Float, left: Float, right: Float, edge: Float, maxSpeed: Float): Float {
            if (edge <= 0f || right - left <= edge * 2f) return 0f
            return when {
                x < left + edge -> -maxSpeed * ((left + edge - x) / edge).coerceIn(0f, 1f)
                x > right - edge -> maxSpeed * ((x - (right - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
        }

        /** 区切りドラッグで動かせる字数（左のチップ・右のチップとも 1 字は残す）。 */
        fun clampDelta(leftLength: Int, rightLength: Int, delta: Int): Int =
            delta.coerceIn(-(leftLength - 1).coerceAtLeast(0), (rightLength - 1).coerceAtLeast(0))

        /** バブルに出す境界の前後のかな（それぞれ最大 [span] 字）。 */
        fun boundaryPreview(left: String, right: String, delta: Int, span: Int = 3): Pair<String, String> {
            val all = left + right
            val b = (left.length + clampDelta(left.length, right.length, delta)).coerceIn(0, all.length)
            return all.substring((b - span).coerceAtLeast(0), b) to all.substring(b, (b + span).coerceAtMost(all.length))
        }
    }
}

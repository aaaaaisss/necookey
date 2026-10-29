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

    /** 展開・選択中のチップが見えるように、はみ出したときの横スクロール量を決める（無ければ右端 = 打っている所）。 */
    fun scrollFor(laid: Laid, data: ChipStripData, viewWidth: Float, current: Float): Float {
        val overflow = laid.width - viewWidth
        if (overflow <= 0f) return 0f
        val focus = data.expandedIndex.takeIf { it >= 0 } ?: data.selectedIndex
        val box = laid.chips.getOrNull(focus) ?: return overflow
        var scroll = current.coerceIn(0f, overflow)
        if (box.left - gap < scroll) scroll = box.left - gap
        if (box.right + gap > scroll + viewWidth) scroll = box.right + gap - viewWidth
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

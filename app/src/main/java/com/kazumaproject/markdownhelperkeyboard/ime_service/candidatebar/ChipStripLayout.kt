package com.kazumaproject.markdownhelperkeyboard.ime_service.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripData
import kotlin.math.abs

/**
 * 文節チップ列の並べ方と当たり判定（Android なし。文字幅は [measure] で注入）。
 *
 * - チップ = 左右に [chipPadding] を付けた表記。チップの間は [gap]。
 * - 展開中のチップ = かな 1 文字ずつの箱（左右 [letterPadding]）を並べたもの。長押しの切るカーソル（[snapCutGap]）と、
 *   箱の間（文字の間）のタップで切る。
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
         * 長押しの切るカーソル（磁石つき）。展開中のチップの文字の境目 k（0..n、n = 文字数）の x 座標 [boundaryX]（n+1 個、
         * 左から順）と指の [x] から、切る位置（文字の間 1..n−1）を選ぶ。
         * - 語の境目 [wordGaps]（自立語｜付属語など）は当たり幅が広い: 距離から [snapBonus] を引いて比べる（≒ 1 字以内なら吸い付く）。
         *   ただし隣の境目との間隔の 3/4 までに抑えるので、語の境目の隣の文字の境目にも必ず止まれる（どの境目にも届く）。
         * - いちばん近いのがチップの両端（k = 0, n。チップの外に出た指を含む）なら null = 切らない（離すと取り消し）。
         */
        fun snapCutGap(boundaryX: List<Float>, wordGaps: Set<Int>, x: Float, snapBonus: Float): Int? {
            val n = boundaryX.size - 1
            if (n < 2) return null
            var best = -1
            var bestScore = Float.MAX_VALUE
            for (k in 0..n) {
                val d = abs(x - boundaryX[k])
                val score = if (k in 1 until n && k in wordGaps) {
                    val spacing = minOf(boundaryX[k] - boundaryX[k - 1], boundaryX[k + 1] - boundaryX[k])
                    d - minOf(snapBonus, spacing * 0.75f).coerceAtLeast(0f)
                } else d
                if (score < bestScore) {
                    bestScore = score
                    best = k
                }
            }
            return best.takeIf { it in 1 until n }
        }

        /** 文字（コードポイント）の並び [letters] で、読みの位置（UTF-16）[wordBoundaries] に当たる文字の境目の番号。 */
        fun wordGapIndices(letters: List<String>, wordBoundaries: List<Int>): Set<Int> {
            if (wordBoundaries.isEmpty()) return emptySet()
            val targets = wordBoundaries.toHashSet()
            val out = HashSet<Int>()
            var offset = 0
            for (k in 1 until letters.size) {
                offset += letters[k - 1].length
                if (offset in targets) out += k
            }
            return out
        }

        /** 文字の境目 [gap] の、チップの読みの中の位置（UTF-16）。 */
        fun gapOffset(letters: List<String>, gap: Int): Int = letters.take(gap.coerceIn(0, letters.size)).sumOf { it.length }

        /**
         * 切るカーソルのバブルの文字列（例: 「とうきょう｜と」）。[gap] が null（切らない位置）なら読みだけ（区切り記号なし）。
         * 長い読みはカーソルの左右 [span] 字ずつに縮めて「…」を付ける。
         */
        fun cutPreview(letters: List<String>, gap: Int?, span: Int = 6, mark: String = "｜"): String {
            if (gap == null || gap !in 1 until letters.size) {
                val all = letters.joinToString("")
                return if (letters.size <= span * 2) all else letters.take(span * 2).joinToString("") + "…"
            }
            val from = (gap - span).coerceAtLeast(0)
            val to = (gap + span).coerceAtMost(letters.size)
            return buildString {
                if (from > 0) append('…')
                append(letters.subList(from, gap).joinToString(""))
                append(mark)
                append(letters.subList(gap, to).joinToString(""))
                if (to < letters.size) append('…')
            }
        }

        /** チップの読みを 1 文字（コードポイント）ずつ（View がモデルを待たずに展開して見せる用。presenter の letters と同じ分け方）。 */
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

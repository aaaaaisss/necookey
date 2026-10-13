package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

/**
 * Inline floating choices (prototype): one small bubble right above each uncertain ("迷い")
 * bunsetsu of the composing text, instead of the two-tap candidate-bar chips.
 *
 * Pure logic only (no Android types) so it can be unit tested: which bubbles to show, where the
 * composing-text ranges are, and how bubbles are laid out once their sizes are known.
 */

/** One bubble: the choices for bunsetsu [slotIndex], covering [start, end) of the composing text. */
data class InlineChoice(
    val slotIndex: Int,
    val start: Int,
    val end: Int,
    /** Current output first (same chips and order as the candidate bar). */
    val chips: List<DiffChip>,
)

/** Screen-space box of one character (CursorAnchorInfo character bounds after its matrix). */
data class CharBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A bubble to place: [anchorX] = left edge of the word, [anchorY] = top of the word (screen px). */
data class BubbleRequest(val anchorX: Float, val anchorY: Float, val width: Int, val height: Int)

/** Top-left of a placed bubble (screen px). */
data class BubblePosition(val x: Int, val y: Int)

object InlineChoices {

    /** Bubbles shown at once (most uncertain bunsetsu first when there are more). */
    const val MAX_BUBBLES = 4

    /** Choices per bubble (current output included). */
    const val MAX_CHOICES = 5

    /**
     * One [InlineChoice] per marked bunsetsu of [plan], in sentence order. Each bubble carries the
     * same diff chips the candidate bar would show for that bunsetsu: homophones and same-reading
     * outputs with a different internal segmentation from Sumire's N-best (和菓子／沸かし).
     */
    fun of(analysis: BunsetsuAnalysis, plan: HomophonePlan): List<InlineChoice> {
        val starts = slotStarts(plan.slotOutputs)
        return plan.uncertainSlots
            .take(MAX_BUBBLES)
            .sorted()
            .mapNotNull { slot ->
                if (slot !in plan.slotOutputs.indices || slot !in analysis.slots.indices) return@mapNotNull null
                val chips = HomophoneChips.chipsFor(analysis, slot, plan.slotOutputs[slot]).take(MAX_CHOICES)
                if (chips.size < 2) return@mapNotNull null
                InlineChoice(slot, starts[slot], starts[slot] + plan.slotOutputs[slot].length, chips)
            }
    }

    /** UTF-16 start offset of each slot output inside the joined sentence. */
    fun slotStarts(outputs: List<String>): List<Int> {
        val out = ArrayList<Int>(outputs.size)
        var position = 0
        for (output in outputs) {
            out += position
            position += output.length
        }
        return out
    }

    /**
     * Anchor of the word covering composing offsets [start, end): the left/top of its first line.
     * [boxAt] gets a composing-relative offset and returns null when the editor gave no bounds.
     * Null when no character of the range has bounds (caller falls back to the candidate bar).
     */
    fun anchorOf(start: Int, end: Int, boxAt: (Int) -> CharBox?): Pair<Float, Float>? {
        var first: CharBox? = null
        var top = Float.MAX_VALUE
        for (offset in start until end) {
            val box = boxAt(offset) ?: continue
            if (first == null) first = box
            // Only characters on the first line of the word (a wrapped word keeps its bubble on top).
            if (box.top < first.bottom) top = minOf(top, box.top)
        }
        val anchor = first ?: return null
        return anchor.left to top
    }

    /**
     * Places bubbles above their words without overlapping: each bubble's bottom sits [gap] px above
     * its word; bubbles on the same line are pushed right in order, then the row is pulled back
     * left if it runs past [screenWidth]. Returns positions in request order.
     */
    fun layout(requests: List<BubbleRequest>, screenWidth: Int, gap: Int, spacing: Int): List<BubblePosition> {
        if (requests.isEmpty()) return emptyList()
        val order = requests.indices.sortedWith(compareBy({ requests[it].anchorY }, { requests[it].anchorX }))
        val positions = arrayOfNulls<BubblePosition>(requests.size)
        var row = ArrayList<Int>()
        fun flushRow() {
            if (row.isEmpty()) return
            var x = Int.MIN_VALUE
            val xs = IntArray(row.size)
            row.forEachIndexed { i, index ->
                val r = requests[index]
                val wanted = r.anchorX.toInt()
                xs[i] = if (x == Int.MIN_VALUE) wanted else maxOf(wanted, x)
                x = xs[i] + r.width + spacing
            }
            // Pull the row back from the right edge (never past the left edge).
            val lastIndex = row.last()
            val overflow = xs.last() + requests[lastIndex].width - screenWidth
            if (overflow > 0) {
                var limit = screenWidth
                for (i in row.indices.reversed()) {
                    val r = requests[row[i]]
                    xs[i] = minOf(xs[i], limit - r.width).coerceAtLeast(0)
                    limit = xs[i] - spacing
                }
            }
            row.forEachIndexed { i, index ->
                val r = requests[index]
                positions[index] = BubblePosition(xs[i].coerceAtLeast(0), (r.anchorY - gap - r.height).toInt())
            }
            row = ArrayList()
        }
        var rowY = Float.NaN
        for (index in order) {
            val r = requests[index]
            if (row.isNotEmpty() && kotlin.math.abs(r.anchorY - rowY) > r.height / 2f) flushRow()
            if (row.isEmpty()) rowY = r.anchorY
            row += index
        }
        flushRow()
        return positions.map { it!! }
    }

    /**
     * Lengths of the bunsetsu of the composing text for per-bunsetsu styling, or null when
     * [outputs] do not spell [composing] (then the whole text keeps one underline).
     */
    fun segmentLengths(composing: String, outputs: List<String>?): List<Int>? {
        if (outputs.isNullOrEmpty() || outputs.size < 2) return null
        if (outputs.joinToString(separator = "") != composing) return null
        if (outputs.any { it.isEmpty() }) return null
        return outputs.map { it.length }
    }
}

/**
 * Boundary edit from the inline ◀▶ bubble, on top of the Mozc-style bunsetsu session.
 *
 * The session is built from Sumire's path; the bunsetsu shown in live conversion may differ
 * (chip edits, zenz). These helpers map the live bunsetsu onto the session so that everything up
 * to the edited bunsetsu keeps what the user saw; [BunsetsuRangeEditor.resizeFocused] then keeps
 * the bunsetsu before the edited one and re-converts / re-splits only the reading after it.
 */
object InlineBoundaryEdit {

    /**
     * Session segment whose reading range ends at [slotEnd] (reading offset of the live bunsetsu's
     * end); otherwise the segment containing the character just before it. Null when out of range.
     */
    fun focusIndexFor(sessionReadings: List<String>, slotEnd: Int): Int? {
        if (sessionReadings.isEmpty() || slotEnd <= 0) return null
        var end = 0
        sessionReadings.forEachIndexed { index, reading ->
            val start = end
            end += reading.length
            if (end == slotEnd) return index
            if (slotEnd in (start + 1) until end) return index
        }
        return null
    }

    /**
     * Display texts to keep for session segments before [focusIndex]: a live bunsetsu output is
     * kept when its reading range is exactly that segment's range. [slotSpans] are reading
     * [start, end) pairs of the live bunsetsu, [slotOutputs] what was shown for them.
     */
    fun keptOutputs(
        sessionReadings: List<String>,
        slotSpans: List<Pair<Int, Int>>,
        slotOutputs: List<String>,
        focusIndex: Int,
    ): Map<Int, String> {
        if (slotSpans.size != slotOutputs.size) return emptyMap()
        val bySpan = slotSpans.indices.associate { slotSpans[it] to slotOutputs[it] }
        val out = LinkedHashMap<Int, String>()
        var start = 0
        sessionReadings.forEachIndexed { index, reading ->
            val end = start + reading.length
            if (index < focusIndex) bySpan[start to end]?.let { out[index] = it }
            start = end
        }
        return out
    }

    /** Live bunsetsu whose end lies nearest to composing offset [offset] (a tap in the text). */
    fun slotForTap(outputs: List<String>, offset: Int): Int? {
        if (outputs.isEmpty()) return null
        var end = 0
        var best: Int? = null
        var bestDistance = Int.MAX_VALUE
        outputs.forEachIndexed { index, output ->
            val start = end
            end += output.length
            if (offset in start..end) {
                val distance = end - offset
                if (distance < bestDistance) {
                    best = index
                    bestDistance = distance
                }
            }
        }
        return best
    }
}

package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

/**
 * Mozc/ATOK-style bunsetsu range editing on a reading.
 *
 * Boundaries are UTF-16 offsets into the reading: `[0, b1, ..., reading.length]`, strictly
 * increasing. Resizing moves the END of the focused bunsetsu by one code point (never inside a
 * surrogate pair). Everything after the new end becomes a single "remainder" range that the
 * caller re-converts and re-splits; the bunsetsu before the focused one are kept untouched.
 */
object BunsetsuRangeEditor {

    data class Resize(
        /** Boundaries of the kept bunsetsu before the focused one, ending at [focusedStart]. */
        val keptBoundaries: List<Int>,
        val focusedStart: Int,
        val focusedEnd: Int,
        /** Reading after the focused bunsetsu, to be re-converted; empty when none. */
        val remainderStart: Int,
        val readingLength: Int,
    ) {
        val hasRemainder: Boolean get() = remainderStart < readingLength

        /**
         * Final boundaries once the remainder was re-split. [remainderSplits] are interior split
         * positions relative to the remainder (as Sumire returns them for that reading).
         */
        fun boundariesWithRemainderSplits(remainderSplits: List<Int>): List<Int> {
            val out = ArrayList<Int>(keptBoundaries.size + remainderSplits.size + 2)
            out += keptBoundaries
            if (out.lastOrNull() != focusedStart) out += focusedStart
            out += focusedEnd
            if (hasRemainder) {
                val remainderLength = readingLength - remainderStart
                remainderSplits
                    .filter { it in 1 until remainderLength }
                    .distinct()
                    .sorted()
                    .forEach { out += remainderStart + it }
                out += readingLength
            }
            return out
        }
    }

    /** Boundaries from consecutive segment readings. */
    fun boundariesOf(segmentReadings: List<String>): List<Int> {
        val out = ArrayList<Int>(segmentReadings.size + 1)
        var position = 0
        out += 0
        for (reading in segmentReadings) {
            position += reading.length
            out += position
        }
        return out
    }

    fun isValid(reading: String, boundaries: List<Int>): Boolean {
        if (boundaries.size < 2 || boundaries.first() != 0 || boundaries.last() != reading.length) {
            return false
        }
        for (i in 1 until boundaries.size) {
            if (boundaries[i] <= boundaries[i - 1]) return false
            if (splitsSurrogatePair(reading, boundaries[i])) return false
        }
        return true
    }

    /**
     * @param delta negative shrinks the focused bunsetsu, positive extends it; the magnitude is
     *   the number of code points.
     * @return null when nothing changes (already one character, or already reaching the end).
     */
    fun resizeFocused(
        reading: String,
        boundaries: List<Int>,
        focusedIndex: Int,
        delta: Int,
    ): Resize? {
        if (delta == 0 || !isValid(reading, boundaries)) return null
        if (focusedIndex !in 0 until boundaries.size - 1) return null
        val start = boundaries[focusedIndex]
        val end = boundaries[focusedIndex + 1]
        var newEnd = end
        repeat(kotlin.math.abs(delta)) {
            newEnd = if (delta < 0) previousCodePointBoundary(reading, newEnd)
            else nextCodePointBoundary(reading, newEnd)
        }
        // A bunsetsu keeps at least one code point and cannot extend past the reading.
        val minEnd = nextCodePointBoundary(reading, start)
        newEnd = newEnd.coerceIn(minEnd, reading.length)
        if (newEnd == end) return null
        return Resize(
            keptBoundaries = boundaries.subList(0, focusedIndex + 1).toList(),
            focusedStart = start,
            focusedEnd = newEnd,
            remainderStart = newEnd,
            readingLength = reading.length,
        )
    }

    /** Interior split positions (what Sumire calls split positions) from boundaries. */
    fun interiorSplits(boundaries: List<Int>): List<Int> =
        if (boundaries.size <= 2) emptyList() else boundaries.subList(1, boundaries.size - 1).toList()

    internal fun nextCodePointBoundary(text: String, index: Int): Int {
        if (index >= text.length) return text.length
        val next = index + 1
        return if (next < text.length && Character.isHighSurrogate(text[index]) &&
            Character.isLowSurrogate(text[next])
        ) next + 1 else next
    }

    internal fun previousCodePointBoundary(text: String, index: Int): Int {
        if (index <= 0) return 0
        val previous = index - 1
        return if (previous > 0 && Character.isLowSurrogate(text[previous]) &&
            Character.isHighSurrogate(text[previous - 1])
        ) previous - 1 else previous
    }

    private fun splitsSurrogatePair(text: String, index: Int): Boolean =
        index in 1 until text.length &&
            Character.isHighSurrogate(text[index - 1]) && Character.isLowSurrogate(text[index])
}

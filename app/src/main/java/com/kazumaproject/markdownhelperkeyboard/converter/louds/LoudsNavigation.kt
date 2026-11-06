package com.kazumaproject.markdownhelperkeyboard.converter.louds

import java.util.BitSet

/**
 * A LOUDS child must be a stored one-bit strictly after its parent.
 * Port of upstream Sumire PR #1216 ("Fix dictionary boundary crashes").
 */
internal fun loudsChildPosition(bits: BitSet, parent: Int, delimiter: Int): Int {
    // Compact dictionaries can omit trailing zero words, including leaf delimiters, so
    // select0 can fail (-1). Check that before +1, which would otherwise revisit the root
    // sentinel at position 0 and loop forever (OutOfMemoryError while typing ちゅう).
    if (parent < 0 || delimiter < 0) return -1
    val child = delimiter + 1
    return if (child > parent && bits[child]) child else -1
}

package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

/**
 * Post-commit correction (prototype): remembers the last few committed texts with the choices
 * each bunsetsu had, so moving the cursor into one of them can offer its alternatives again.
 *
 * Offsets are absolute UTF-16 positions in the editor as reported by onUpdateSelection. Nothing
 * here trusts an offset blindly: [verify] compares the stored text with the editor's text around
 * the cursor, and the caller drops the entry when they differ (the surrounding text changed).
 */
class CommittedSpanTracker(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxChars: Int = DEFAULT_MAX_CHARS,
) {

    /** One bunsetsu of a committed text. [start] is relative to the entry. */
    data class Slot(
        val start: Int,
        val output: String,
        val reading: String,
        /** Distinct outputs to offer, the committed one first. */
        val alternatives: List<String>,
    ) {
        val end: Int get() = start + output.length
    }

    data class Entry(
        val start: Int,
        val text: String,
        val slots: List<Slot>,
    ) {
        val end: Int get() = start + text.length
    }

    /** The cursor is inside [Entry.slots] of [entry] at [slotIndex]. */
    data class Hit(val entry: Entry, val slotIndex: Int) {
        val slot: Slot get() = entry.slots[slotIndex]
        val absoluteStart: Int get() = entry.start + slot.start
        val absoluteEnd: Int get() = entry.start + slot.end
    }

    private val entries = ArrayList<Entry>()

    val size: Int get() = entries.size
    fun entries(): List<Entry> = entries.toList()

    fun clear() = entries.clear()

    /**
     * Remembers a commit of [text] at absolute [start]. [slots] must spell [text]; slots without a
     * real alternative are kept for alignment but never offered. Older entries overlapping the new
     * one are dropped, then the oldest entries until the count and character bounds hold.
     */
    fun record(start: Int, text: String, slots: List<Slot>) {
        if (start < 0 || text.isEmpty() || text.length > maxChars) return
        if (slots.joinToString(separator = "") { it.output } != text) return
        if (slots.none { it.alternatives.size >= 2 }) return
        var position = 0
        for (slot in slots) {
            if (slot.start != position) return
            position = slot.end
        }
        val entry = Entry(start, text, slots)
        entries.removeAll { it.start < entry.end && entry.start < it.end }
        entries += entry
        while (entries.size > maxEntries || entries.sumOf { it.text.length } > maxChars) entries.removeAt(0)
    }

    /**
     * The remembered bunsetsu the cursor is in. A caret counts only strictly inside the bunsetsu
     * (the caret right after a commit sits at its end and must not pop a bubble); a selection
     * counts when it covers exactly one bunsetsu. Newest entries win.
     */
    fun hitAt(selectionStart: Int, selectionEnd: Int): Hit? {
        for (entry in entries.asReversed()) {
            entry.slots.forEachIndexed { index, slot ->
                if (slot.alternatives.size < 2) return@forEachIndexed
                val absStart = entry.start + slot.start
                val absEnd = entry.start + slot.end
                val inside = if (selectionStart == selectionEnd) {
                    selectionStart in (absStart + 1) until absEnd
                } else {
                    selectionStart == absStart && selectionEnd == absEnd
                }
                if (inside) return Hit(entry, index)
            }
        }
        return null
    }

    /**
     * True when the editor still has the entry's text around the cursor: [textBeforeCursor] must
     * end with the entry text up to [cursor] and [textAfterCursor] start with the rest.
     */
    fun verify(hit: Hit, cursor: Int, textBeforeCursor: CharSequence?, textAfterCursor: CharSequence?): Boolean {
        val entry = hit.entry
        if (cursor < entry.start || cursor > entry.end) return false
        val before = entry.text.substring(0, cursor - entry.start)
        val after = entry.text.substring(cursor - entry.start)
        val b = textBeforeCursor?.toString() ?: return false
        val a = textAfterCursor?.toString() ?: return false
        return b.endsWith(before) && a.startsWith(after)
    }

    /** Forgets [entry] (its text was changed by something else). */
    fun invalidate(entry: Entry) {
        entries.removeAll { it == entry }
    }

    /**
     * Updates the remembered text after bunsetsu [Hit.slotIndex] was replaced with [output] in the
     * editor: the slot's output changes (its alternatives stay, the new one first), later slots and
     * later entries shift by the length difference. Returns the updated entry, or null if the hit
     * is stale.
     */
    fun applyReplacement(hit: Hit, output: String): Entry? {
        val index = entries.indexOf(hit.entry)
        if (index < 0) return null
        val old = hit.slot
        val delta = output.length - old.output.length
        val alternatives = (listOf(output) + old.alternatives).distinct()
        val slots = hit.entry.slots.mapIndexed { i, slot ->
            when {
                i == hit.slotIndex -> slot.copy(output = output, alternatives = alternatives)
                i > hit.slotIndex -> slot.copy(start = slot.start + delta)
                else -> slot
            }
        }
        val updated = Entry(hit.entry.start, slots.joinToString(separator = "") { it.output }, slots)
        val oldEnd = hit.entry.end
        entries[index] = updated
        if (delta != 0) {
            for (i in entries.indices) {
                if (i != index && entries[i].start >= oldEnd) {
                    entries[i] = entries[i].copy(start = entries[i].start + delta)
                }
            }
        }
        return updated
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 5
        const val DEFAULT_MAX_CHARS = 200

        /** Slots of a live-conversion commit from the homophone plan shown for it. */
        fun slotsOf(analysis: BunsetsuAnalysis, outputs: List<String>, maxAlternatives: Int = InlineChoices.MAX_CHOICES): List<Slot>? {
            if (outputs.size != analysis.slots.size) return null
            val starts = InlineChoices.slotStarts(outputs)
            return outputs.mapIndexed { i, output ->
                val span = analysis.slots[i].span
                val alternatives = (listOf(output) + analysis.slots[i].alternatives.map { it.output })
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(maxAlternatives)
                Slot(starts[i], output, analysis.input.substring(span.start, span.end), alternatives)
            }
        }

        /**
         * The leading slots of [slots] that spell [committed] exactly (a commit of the first
         * bunsetsu only, for example). Null when [committed] does not end on a slot boundary.
         */
        fun prefixSlots(slots: List<Slot>, committed: String): List<Slot>? {
            var length = 0
            val out = ArrayList<Slot>()
            for (slot in slots) {
                if (length == committed.length) break
                out += slot
                length += slot.output.length
            }
            if (length != committed.length) return null
            if (out.joinToString(separator = "") { it.output } != committed) return null
            return out
        }
    }
}

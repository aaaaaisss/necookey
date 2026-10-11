package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment

/**
 * Zenzai の FIX 制約（azooKey の推論上限 1 と同じく、得た制約を次のキー入力へ引き継ぐ）。
 *
 * 表層 [surface] と、それが占める読み [reading]（入力の先頭）を組で持つ。[boundaries] は制約内の
 * 文節境界ごとの (読みの終端, 表層の終端)。最後の要素は (reading.length, surface.length)。
 * 読みが変わったら（後退・途中編集・濁点切替など）読みが先頭一致している境界まで縮め、
 * 一致しなければ捨てる。
 */
data class ZenzaiConstraint(
    val leftContext: String,
    val reading: String,
    val surface: String,
    val boundaries: List<Pair<Int, Int>>,
) {
    /** [input]・[leftContext] でまだ使える制約（必要なら縮めたもの）。使えなければ null。 */
    fun carriedFor(input: String, leftContext: String): ZenzaiConstraint? {
        if (leftContext != this.leftContext) return null
        val index = boundaries.indexOfLast { (readingEnd, _) ->
            readingEnd in 1..input.length &&
                input.regionMatches(0, reading, 0, readingEnd) &&
                !splitsMora(input, readingEnd)
        }
        if (index < 0) return null
        if (index == boundaries.lastIndex) return this
        val (readingEnd, surfaceEnd) = boundaries[index]
        return copy(
            reading = reading.substring(0, readingEnd),
            surface = surface.substring(0, surfaceEnd),
            boundaries = boundaries.subList(0, index + 1).toList(),
        )
    }

    /** 経路 [segments] がこの制約の表層を同じ読み範囲で出しているか。 */
    fun isRealizedBy(segments: List<CandidateConversionSegment>): Boolean =
        readingEndOf(surface, segments) == reading.length

    companion object {
        private const val NON_INITIAL = "ぁぃぅぇぉゃゅょゎっゕゖァィゥェォャュョヮッヵヶー゛゜"

        /** 経路 [segments] が守るべき文節 [protected]（ユーザー辞書・学習の語）をそのまま含むか。 */
        fun preserves(
            segments: List<CandidateConversionSegment>,
            protected: List<CandidateConversionSegment>,
        ): Boolean = protected.all { it in segments }

        /** [readingEnd] で切ると拗音・促音・長音が前の音から離れる。 */
        fun splitsMora(input: String, readingEnd: Int): Boolean =
            readingEnd < input.length && input[readingEnd] in NON_INITIAL

        /**
         * 経路 [segments]（読み [input] 上の連続した区間）のうち [prefix] を出し終える文節の読み終端。
         * 経路の表層が [prefix] で始まらなければ null。
         */
        fun readingEndOf(prefix: String, segments: List<CandidateConversionSegment>): Int? =
            boundariesOf(prefix, segments)?.lastOrNull()?.first

        private fun boundariesOf(
            prefix: String,
            segments: List<CandidateConversionSegment>,
        ): List<Pair<Int, Int>>? {
            if (prefix.isEmpty()) return null
            val result = ArrayList<Pair<Int, Int>>()
            var readingPos = 0
            var surfacePos = 0
            for (segment in segments) {
                if (segment.inputStart != readingPos || segment.inputEnd <= segment.inputStart) return null
                val next = surfacePos + segment.output.length
                if (next >= prefix.length) {
                    if (!prefix.regionMatches(surfacePos, segment.output, 0, prefix.length - surfacePos)) {
                        return null
                    }
                    result.add(segment.inputEnd to prefix.length)
                    return result
                }
                if (!prefix.regionMatches(surfacePos, segment.output, 0, segment.output.length)) return null
                result.add(segment.inputEnd to next)
                readingPos = segment.inputEnd
                surfacePos = next
            }
            return null
        }

        /** 経路 [segments] が [prefix] を満たすときの制約。満たさなければ null。 */
        fun from(
            leftContext: String,
            input: String,
            prefix: String,
            segments: List<CandidateConversionSegment>,
        ): ZenzaiConstraint? {
            val boundaries = boundariesOf(prefix, segments) ?: return null
            val readingEnd = boundaries.last().first
            if (readingEnd > input.length) return null
            return ZenzaiConstraint(leftContext, input.substring(0, readingEnd), prefix, boundaries)
        }
    }
}

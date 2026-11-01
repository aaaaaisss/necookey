package com.kazumaproject.markdownhelperkeyboard.learning

/**
 * 「確定直後に消して、同じ読みを別の表記で打ち直した」を負の教師にする（純粋関数。docs/LEARNING.md、IDEAS_NEXT_ACCURACY 4）。
 *
 * 確定直後のバックスペースで学習を取り消したとき、その確定の読み・表記・文節を [Memory] として少しの間覚えておく。
 * 次に確定した文が同じ読みで別の表記なら、元の表記（文全体と、表記が変わった文節）を弱め、新しい表記を 1 回書く。
 */
object RetypeCorrection {
    /** 取り消してからこの時間内に確定したものだけを打ち直しとみなす。 */
    const val WINDOW_MS: Long = 60_000L

    /** 元の表記の強さに掛ける係数（変換の先頭を無視したときと同じ強さ）。 */
    const val WEAKEN_FACTOR: Double = 0.1

    data class Memory(
        val reading: String,
        val surface: String,
        /** 文節（読み to 表記）。 */
        val segments: List<Pair<String, String>>,
        val revertedAt: Long,
    )

    /**
     * @property weaken 弱める学習行（読み to 表記）。
     * @property reinforce 1 回書く学習行（読み to 表記）。
     */
    data class Plan(
        val weaken: List<Pair<String, String>>,
        val reinforce: List<Pair<String, String>>,
    )

    fun plan(
        memory: Memory?,
        committedReading: String,
        committedText: String,
        committedSegments: List<Pair<String, String>>,
        now: Long,
        maxWholeReading: Int = 12,
    ): Plan? {
        if (memory == null) return null
        if (now < memory.revertedAt || now - memory.revertedAt > WINDOW_MS) return null
        if (committedReading.isEmpty() || committedText.isEmpty()) return null
        if (memory.reading != committedReading || memory.surface == committedText) return null
        if (memory.surface.isEmpty()) return null

        val newSegments = committedSegments.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }.toSet()
        val newByReading = newSegments.groupBy({ it.first }, { it.second })
        // 同じ読みの文節が新しい確定にもあって表記が違うものだけ（区切りが変わった文節は判断しない）。
        val changedOld = memory.segments
            .filter { (r, o) -> r.isNotEmpty() && o.isNotEmpty() && (r to o) !in newSegments }
            .filter { (r, _) -> newByReading.containsKey(r) }
        val changedNew = newSegments.filter { (r, o) ->
            memory.segments.any { (mr, mo) -> mr == r && mo != o }
        }
        val weaken = (listOf(committedReading to memory.surface) + changedOld)
            .filter { it != committedReading to committedText && it !in newSegments }
            .distinct()
        // 文全体の行は短い文だけ（ConversionLearningSession と同じく長い文は句として覚えない）。
        val whole = (committedReading to committedText).takeIf { committedReading.length <= maxWholeReading }
        val reinforce = (listOfNotNull(whole) + changedNew).distinct()
        return Plan(weaken = weaken, reinforce = reinforce)
    }
}

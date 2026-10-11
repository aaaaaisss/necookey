package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * 確定後の後続語スロットに足す「おまけ」候補（文脈絵文字）。
 *
 * 学習済み・ゼロクエリの後続語（base）を押し出さないよう、先頭 [KEEP_BASE] 件は常に base。
 * 並びは [NextWordZenzReranker] と同じ 1 回の zenz 採点（head + おまけを同じ配列で採点）で決める。
 */
object NextWordExtras {
    /** 先頭に必ず残す base 候補の数。 */
    const val KEEP_BASE = 2

    /** 表示する文脈絵文字の上限。 */
    const val MAX_EMOJI = 3

    /** zenz に採点させる絵文字候補の上限（ここから [MAX_EMOJI] 件を選ぶ）。 */
    const val EMOJI_POOL = 5

    /** おまけ候補を base の何位相当として位置ペナルティを掛けるか。 */
    const val EXTRA_RANK = 3

    private const val MIN_KEY_LENGTH = 2
    private const val MAX_KEY_LENGTH = 8

    /**
     * 確定した読みから絵文字辞書を引くキー: 末尾側（直近の語）から長い順、次に先頭側から長い順。
     */
    fun emojiKeys(reading: String): List<String> {
        if (reading.length < MIN_KEY_LENGTH) return emptyList()
        val keys = LinkedHashSet<String>()
        val maxLen = minOf(MAX_KEY_LENGTH, reading.length)
        for (len in maxLen downTo MIN_KEY_LENGTH) keys.add(reading.takeLast(len))
        for (len in maxLen downTo MIN_KEY_LENGTH) keys.add(reading.take(len))
        return keys.toList()
    }

    /** base と重複しない絵文字候補を [EMOJI_POOL] 件まで。 */
    fun emojiPool(base: List<Candidate>, emoji: List<Candidate>): List<Candidate> {
        val seen = base.mapTo(HashSet()) { it.string }
        return emoji.filter { seen.add(it.string) }.take(EMOJI_POOL)
    }

    /** zenz 採点前の並び: base 先頭 [KEEP_BASE] 件 → 絵文字 [MAX_EMOJI] 件 → 残りの base。 */
    fun initial(base: List<Candidate>, emoji: List<Candidate>): List<Candidate> {
        if (emoji.isEmpty()) return base
        return base.take(KEEP_BASE) + emoji.take(MAX_EMOJI) + base.drop(KEEP_BASE)
    }

    /** zenz に渡す候補配列: head（base 上位 [NextWordZenzReranker.TOP_K]）+ 絵文字。 */
    fun scoringTargets(base: List<Candidate>, emoji: List<Candidate>): List<Candidate> =
        base.take(NextWordZenzReranker.TOP_K) + emoji

    /**
     * [scores] は [scoringTargets] と同じ並び。base の head は従来どおり位置ペナルティつきで並べ替え、
     * 先頭 [KEEP_BASE] 件の後ろで head の残りと上位 [MAX_EMOJI] 件の絵文字を同じ尺度で混ぜる。
     */
    fun rerank(base: List<Candidate>, emoji: List<Candidate>, scores: FloatArray): List<Candidate> {
        val head = base.take(NextWordZenzReranker.TOP_K)
        if (scores.size != head.size + emoji.size || scores.none { it.isFinite() }) {
            return initial(base, emoji)
        }
        val penalty = NextWordZenzReranker.POSITION_PENALTY
        fun s(i: Int) = if (scores[i].isFinite()) scores[i] else -1e6f
        val headOrder = head.indices.sortedWith(
            compareByDescending<Int> { s(it) - penalty * it }.thenBy { it },
        )
        val top = headOrder.take(KEEP_BASE)
        val emojiTop = emoji.indices
            .sortedWith(compareByDescending<Int> { s(head.size + it) }.thenBy { it })
            .take(MAX_EMOJI)
        data class Slot(val candidate: Candidate, val fused: Float, val order: Int)
        val window = headOrder.drop(KEEP_BASE).map { Slot(head[it], s(it) - penalty * it, it) } +
            emojiTop.map { Slot(emoji[it], s(head.size + it) - penalty * EXTRA_RANK, head.size + it) }
        val merged = window.sortedWith(
            compareByDescending<Slot> { it.fused }.thenBy { it.order },
        ).map { it.candidate }
        return top.map { head[it] } + merged + base.drop(head.size)
    }
}

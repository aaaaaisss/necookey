package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * 確定後の後続語スロットに足す「おまけ」候補（助詞・助動詞・句読点と文脈絵文字）。
 *
 * 学習済み・ゼロクエリの後続語（base）を押し出さないよう、先頭 [KEEP_BASE] 件は常に base。
 * 並びは [NextWordZenzReranker] と同じ 1 回の zenz 採点（head + おまけを同じ配列で採点）で決める。
 */
object NextWordExtras {
    /** 先頭に必ず残す base 候補の数。 */
    const val KEEP_BASE = 2

    /** 確定後に候補として採点する助詞・助動詞・句読点（固定）。 */
    val FUNCTION_WORDS: List<String> = listOf(
        "、", "。", "！", "？", "は", "が", "を", "に", "で", "と", "も", "の", "へ",
        "から", "まで", "より", "た", "です", "ます", "ね", "よ",
    )

    /** 1 回の zenz 採点にかける候補数の上限（遅延を抑える）。 */
    const val MAX_SCORED = 16

    /** 採点にかける助詞・句読点の数（直前の文字で安く絞り込んだ上位）。 */
    const val FUNCTION_WORD_POOL = 5

    /** 名詞らしい語（漢字・カタカナ・英数字）の後に採点する助詞・句読点。 */
    private val AFTER_NOUN = listOf("は", "が", "を", "に", "、")

    /** ひらがな（述語・助詞など）の後に採点する句読点・終助詞。 */
    private val AFTER_KANA = listOf("。", "、", "ね", "よ", "！")

    /** 表示する助詞・句読点の上限。 */
    const val MAX_FUNCTION_WORDS = 3

    private const val SENTENCE_END = "、。！？!?,.，．"

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

    /**
     * 確定文字列の後に続けて採点する助詞・句読点（[FUNCTION_WORD_POOL] 件、直前の文字で安く絞る）。
     * 句読点の直後には出さない。
     */
    fun functionWordsAfter(committedText: String): List<String> {
        val last = committedText.lastOrNull() ?: return emptyList()
        if (last in SENTENCE_END) return emptyList()
        val pool = if (last in '\u3041'..'\u309F') AFTER_KANA else AFTER_NOUN
        return pool.filter { it in FUNCTION_WORDS }.take(FUNCTION_WORD_POOL)
    }

    /** 採点する base 先頭件数: [MAX_SCORED] からおまけ分を引いた残り（[NextWordZenzReranker.TOP_K] まで）。 */
    private fun headSize(base: List<Candidate>, emoji: List<Candidate>, functionWords: List<Candidate>): Int =
        minOf(
            base.size,
            NextWordZenzReranker.TOP_K,
            (MAX_SCORED - scoredFunctionWords(functionWords).size - scoredEmoji(emoji).size)
                .coerceAtLeast(KEEP_BASE),
        )

    private fun scoredFunctionWords(functionWords: List<Candidate>) = functionWords.take(FUNCTION_WORD_POOL)

    private fun scoredEmoji(emoji: List<Candidate>) = emoji.take(MAX_EMOJI)

    /** base と重複しないもの。 */
    fun withoutBase(base: List<Candidate>, extras: List<Candidate>): List<Candidate> {
        val seen = base.mapTo(HashSet()) { it.string }
        return extras.filter { seen.add(it.string) }
    }

    /** base と重複しない絵文字候補を [EMOJI_POOL] 件まで。 */
    fun emojiPool(base: List<Candidate>, emoji: List<Candidate>): List<Candidate> {
        val seen = base.mapTo(HashSet()) { it.string }
        return emoji.filter { seen.add(it.string) }.take(EMOJI_POOL)
    }

    /**
     * zenz 採点前の並び: base 先頭 [KEEP_BASE] 件 → 助詞・句読点 [MAX_FUNCTION_WORDS] 件 →
     * 絵文字 [MAX_EMOJI] 件 → 残りの base。
     */
    fun initial(
        base: List<Candidate>,
        emoji: List<Candidate>,
        functionWords: List<Candidate> = emptyList(),
    ): List<Candidate> {
        if (emoji.isEmpty() && functionWords.isEmpty()) return base
        return base.take(KEEP_BASE) + functionWords.take(MAX_FUNCTION_WORDS) +
            emoji.take(MAX_EMOJI) + base.drop(KEEP_BASE)
    }

    /**
     * zenz に渡す候補配列（合計 [MAX_SCORED] 件まで）: head（学習済み base の上位）+
     * 助詞・句読点 [FUNCTION_WORD_POOL] 件 + 絵文字 [MAX_EMOJI] 件。
     */
    fun scoringTargets(
        base: List<Candidate>,
        emoji: List<Candidate>,
        functionWords: List<Candidate> = emptyList(),
    ): List<Candidate> = base.take(headSize(base, emoji, functionWords)) +
        scoredFunctionWords(functionWords) + scoredEmoji(emoji)

    /**
     * [scores] は [scoringTargets] と同じ並び。base の head は従来どおり位置ペナルティつきで並べ替え、
     * 先頭 [KEEP_BASE] 件の後ろで head の残り・上位 [MAX_FUNCTION_WORDS] 件の助詞・句読点・
     * 上位 [MAX_EMOJI] 件の絵文字を同じ尺度で混ぜる。
     */
    fun rerank(
        base: List<Candidate>,
        allEmoji: List<Candidate>,
        scores: FloatArray,
        allFunctionWords: List<Candidate> = emptyList(),
    ): List<Candidate> {
        val head = base.take(headSize(base, allEmoji, allFunctionWords))
        val functionWords = scoredFunctionWords(allFunctionWords)
        val emoji = scoredEmoji(allEmoji)
        if (scores.size != head.size + functionWords.size + emoji.size || scores.none { it.isFinite() }) {
            return initial(base, emoji, functionWords)
        }
        val penalty = NextWordZenzReranker.POSITION_PENALTY
        fun s(i: Int) = if (scores[i].isFinite()) scores[i] else -1e6f
        val headOrder = head.indices.sortedWith(
            compareByDescending<Int> { s(it) - penalty * it }.thenBy { it },
        )
        val top = headOrder.take(KEEP_BASE)
        data class Slot(val candidate: Candidate, val fused: Float, val order: Int)
        fun best(group: List<Candidate>, offset: Int, limit: Int): List<Slot> = group.indices
            .sortedWith(compareByDescending<Int> { s(offset + it) }.thenBy { it })
            .take(limit)
            .map { Slot(group[it], s(offset + it) - penalty * EXTRA_RANK, offset + it) }
        val window = headOrder.drop(KEEP_BASE).map { Slot(head[it], s(it) - penalty * it, it) } +
            best(functionWords, head.size, MAX_FUNCTION_WORDS) +
            best(emoji, head.size + functionWords.size, MAX_EMOJI)
        val merged = window.sortedWith(
            compareByDescending<Slot> { it.fused }.thenBy { it.order },
        ).map { it.candidate }
        return top.map { head[it] } + merged + base.drop(head.size)
    }
}

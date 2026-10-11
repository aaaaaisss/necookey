package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate

/**
 * 確定後の後続語スロットに足す「おまけ」候補（助詞・助動詞・句読点と文脈絵文字 [ContextEmojiSelector]）。
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
    const val MAX_SCORED = 25

    /** 採点にかける助詞・句読点の数（直前の文字で安く絞り込んだ上位）。 */
    const val FUNCTION_WORD_POOL = 5

    /** 名詞らしい語（漢字・カタカナ・英数字）の後に採点する助詞・句読点。 */
    private val AFTER_NOUN = listOf("は", "が", "を", "に", "、")

    /** ひらがな（述語・助詞など）の後に採点する句読点・終助詞。 */
    private val AFTER_KANA = listOf("。", "、", "ね", "よ", "！")

    /** 表示する助詞・句読点の上限。 */
    const val MAX_FUNCTION_WORDS = 3

    private const val SENTENCE_END = "、。！？!?,.，．"

    /** おまけ候補を base の何位相当として位置ペナルティを掛けるか。 */
    const val EXTRA_RANK = 3

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
    private fun headSize(base: List<Candidate>, emojiTargets: Int, functionWords: List<Candidate>): Int =
        minOf(
            base.size,
            NextWordZenzReranker.TOP_K,
            (MAX_SCORED - scoredFunctionWords(functionWords).size - emojiTargets).coerceAtLeast(KEEP_BASE),
        )

    private fun scoredFunctionWords(functionWords: List<Candidate>) = functionWords.take(FUNCTION_WORD_POOL)

    /** base と重複しないもの。 */
    fun withoutBase(base: List<Candidate>, extras: List<Candidate>): List<Candidate> {
        val seen = base.mapTo(HashSet()) { it.string }
        return extras.filter { seen.add(it.string) }
    }

    /**
     * zenz 採点前の並び: base 先頭 [KEEP_BASE] 件 → 助詞・句読点 [MAX_FUNCTION_WORDS] 件 → 残りの base。
     * 文脈絵文字は zenz の採点後にだけ出す。
     */
    /**
     * zenz を使わないときの文脈絵文字の置き方: 1 個目は先頭の後続語の直後、残りは 2 番目の直後
     * （zenz あり時の [rerank] と同じ位置）。
     */
    fun placeEmoji(shown: List<Candidate>, emoji: List<Candidate>): List<Candidate> {
        if (emoji.isEmpty()) return shown
        val strings = emoji.mapTo(HashSet()) { it.string }
        val rest = shown.filter { it.string !in strings }
        return rest.take(1) + emoji.take(1) + rest.drop(1).take(1) + emoji.drop(1) + rest.drop(2)
    }

    fun initial(base: List<Candidate>, functionWords: List<Candidate> = emptyList()): List<Candidate> {
        if (functionWords.isEmpty()) return base
        return base.take(KEEP_BASE) + functionWords.take(MAX_FUNCTION_WORDS) + base.drop(KEEP_BASE)
    }

    /**
     * zenz に渡す文字列（合計 [MAX_SCORED] 件まで）: head（学習済み base の上位）+
     * 助詞・句読点 [FUNCTION_WORD_POOL] 件 + 文脈絵文字のキーワード（[ContextEmojiSelector.POOL] 件）。
     */
    fun scoringTargets(
        base: List<Candidate>,
        emojiKeywords: List<String>,
        functionWords: List<Candidate> = emptyList(),
    ): List<String> = base.take(headSize(base, emojiKeywords.size, functionWords)).map { it.string } +
        scoredFunctionWords(functionWords).map { it.string } + emojiKeywords

    /**
     * [scores] は [scoringTargets] と同じ並び（末尾 [emojiTargets] 件が絵文字キーワード）。base の head は
     * 従来どおり位置ペナルティつきで並べ替え、先頭 [KEEP_BASE] 件の後ろで head の残り・上位
     * [MAX_FUNCTION_WORDS] 件の助詞・句読点・[emoji]（PMI で選んだ絵文字と、キーワードの位置
     * = 絵文字キーワード内のインデックス）を同じ尺度（キーワードの生スコア）で混ぜる。
     */
    fun rerank(
        base: List<Candidate>,
        scores: FloatArray,
        allFunctionWords: List<Candidate> = emptyList(),
        emojiTargets: Int = 0,
        emoji: List<Pair<Candidate, Int>> = emptyList(),
    ): List<Candidate> {
        val head = base.take(headSize(base, emojiTargets, allFunctionWords))
        val functionWords = scoredFunctionWords(allFunctionWords)
        if (scores.size != head.size + functionWords.size + emojiTargets || scores.none { it.isFinite() }) {
            return initial(base, functionWords)
        }
        val penalty = NextWordZenzReranker.POSITION_PENALTY
        fun s(i: Int) = if (scores[i].isFinite()) scores[i] else -1e6f
        val headOrder = head.indices.sortedWith(
            compareByDescending<Int> { s(it) - penalty * it }.thenBy { it },
        )
        val top = headOrder.take(KEEP_BASE)
        data class Slot(val candidate: Candidate, val fused: Float, val order: Int)
        val fwOffset = head.size
        val window = headOrder.drop(KEEP_BASE).map { Slot(head[it], s(it) - penalty * it, it) } +
            functionWords.indices
                .sortedWith(compareByDescending<Int> { s(fwOffset + it) }.thenBy { it })
                .take(MAX_FUNCTION_WORDS)
                .map { Slot(functionWords[it], s(fwOffset + it) - penalty * EXTRA_RANK, fwOffset + it) }
        val merged = window.sortedWith(
            compareByDescending<Slot> { it.fused }.thenBy { it.order },
        ).map { it.candidate }
        // 絵文字は PMI で既に選別済み。キーワードの生スコアは語の長さ・種類で助詞より低く出やすく、
        // 生スコアで混ぜると画面外に押し出されていた。1 個目は base の 1 位の直後、残りは base の 2 位の直後に固定する
        // （一番押しやすい先頭は学習済みの後続語に残す）。
        val picked = emoji.filter { it.second in 0 until emojiTargets }
            .take(ContextEmojiSelector.MAX_SHOWN).map { it.first }
        val tops = top.map { head[it] }
        return tops.take(1) + picked.take(1) + tops.drop(1) + picked.drop(1) + merged + base.drop(head.size)
    }
}

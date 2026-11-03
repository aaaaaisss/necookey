package com.kazumaproject.markdownhelperkeyboard.converter.candidate

/**
 * かなそのままの候補の位置（端末報告 2026-11-03: おけ→岡、くさ→日、むりょう→🈚、半角カナが上位。
 * ユーザー「ひらがなが上位に出るほうがまだまし」）。
 *
 * 短い読み（[MIN_LEN]〜[MAX_LEN] 字のひらがな）では、変換候補の先頭 [MAX_INDEX] + 1 件の中に
 * 入力そのまま（ひらがな）を必ず入れる。既にあればそのまま、後ろにあれば [MAX_INDEX] へ移し、無ければ差し込む。
 * 1 字の読み（め・て・き）は対象外（漢字 1 字が普通に欲しい）。
 */
object KanaCandidateOrder {
    const val MIN_LEN = 2
    const val MAX_LEN = 4
    const val MAX_INDEX = 2

    /** 入力そのまま候補の種別（KanaKanjiEngine の hirakanaAndKana と同じ）。 */
    private const val TYPE_HIRAGANA: Byte = 3
    private const val HIRAGANA_SCORE = 6000

    fun appliesTo(input: String): Boolean =
        input.length in MIN_LEN..MAX_LEN && input.all { it in 'ぁ'..'ゖ' || it == 'ー' }

    fun ensureKanaNearTop(candidates: List<Candidate>, input: String): List<Candidate> {
        if (!appliesTo(input) || candidates.size <= MAX_INDEX) return candidates
        val index = candidates.indexOfFirst { it.string == input }
        if (index in 0..MAX_INDEX) return candidates
        val result = candidates.toMutableList()
        val kana = if (index > 0) result.removeAt(index)
        else Candidate(input, TYPE_HIRAGANA, input.length.toUByte(), HIRAGANA_SCORE)
        result.add(MAX_INDEX, kana)
        return result
    }
}

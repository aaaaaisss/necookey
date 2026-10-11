package com.kazumaproject.markdownhelperkeyboard.converter.candidate


/**
 * @see 1:NBest 2:Part of letters 3:Hirakana 4:Katakana 5:Combine part of letter 6. Single Kanji
 **/
data class Candidate(
    val string: String,
    val type: Byte,
    val length: UByte,
    val score: Int,
    val yomi: String? = null,
    val leftId: Short? = null,
    val rightId: Short? = null,
    /** Stable source identity for action candidates whose display string must never be committed. */
    val sourceId: Long? = null,
    /** Text sent to InputConnection. Defaults to the legacy candidate string. */
    val commitText: String = string,
    /** Exact conversion path used only to align live candidate readings. */
    val conversionSegments: List<CandidateConversionSegment> = emptyList(),
    /** True when the two-row bar's primary was selected by the zenz bunsetsu gate. */
    val zenzAdjusted: Boolean = false,
    /** zenz がこの変換を評価して同意した（判定キャッシュの再利用を含む）。表示は [z]。 */
    val zenzChecked: Boolean = false,
)

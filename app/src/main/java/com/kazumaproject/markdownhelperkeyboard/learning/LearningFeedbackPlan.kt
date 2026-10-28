package com.kazumaproject.markdownhelperkeyboard.learning

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.learning.nextword.NextWordEntity

/**
 * 確定したときの負のフィードバック（純粋関数。docs/LEARNING.md）。
 * 候補欄の上位に出したのに確定されなかったものを挙げる。実際に弱めるのは学習辞書・後続語にある行だけ。
 *
 * @property ignoredPrimary 変換の先頭に出ていた表記（確定と違うとき）。読みは確定した読み全体。
 * @property ignoredLearnedPredictions 予測の上位に出ていた学習辞書の語（読み to 表記）。
 * @property ignoredNextWords 予測の上位に出ていた（加点・追加した）後続語。
 */
data class LearningFeedbackPlan(
    val ignoredPrimary: String?,
    val ignoredLearnedPredictions: List<Pair<String, String>>,
    val ignoredNextWords: List<NextWordEntity>,
) {
    companion object {
        fun of(
            primary: String?,
            topPredictions: List<Candidate>,
            nextWords: Map<String, NextWordEntity>,
            committedText: String,
        ): LearningFeedbackPlan {
            val notUsed = topPredictions.filter { it.string.isNotEmpty() && it.string != committedText }
            return LearningFeedbackPlan(
                ignoredPrimary = primary?.takeIf { it.isNotEmpty() && it != committedText },
                ignoredLearnedPredictions = notUsed.mapNotNull { candidate ->
                    val reading = candidate.yomi
                    if (candidate.type == CANDIDATE_TYPE_LEARNED_DICTIONARY && !reading.isNullOrEmpty()) {
                        reading to candidate.string
                    } else {
                        null
                    }
                }.distinct(),
                ignoredNextWords = notUsed.mapNotNull { nextWords[it.string] }.distinct(),
            )
        }
    }
}

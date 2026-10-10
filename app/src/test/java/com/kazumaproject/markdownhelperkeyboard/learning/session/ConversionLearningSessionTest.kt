package com.kazumaproject.markdownhelperkeyboard.learning.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionLearningSessionTest {
    @Test
    fun separatedConversionLearnsSegmentsAndCompletePhrase() {
        val session = ConversionLearningSession()
        session.beginIfNeeded("しうんすみか")
        session.record(fragment("しうん", "紫雲", index = 2))
        session.record(fragment("すみ", "清", index = 1))
        session.record(fragment("か", "夏", index = 3))

        val entries = session.finish(learnFirstCandidate = false, timestamp = 123L)

        assertTrue(entries.any { it.input == "しうん" && it.out == "紫雲" })
        assertTrue(entries.any { it.input == "すみ" && it.out == "清" })
        assertTrue(entries.any { it.input == "か" && it.out == "夏" })
        assertTrue(
            entries.any {
                it.input == "しうんすみか" && it.out == "紫雲清夏" && it.isPhrase
            }
        )
    }

    @Test
    fun partialPredictionKeepsOriginalReadingUntilLastFragment() {
        val session = ConversionLearningSession()
        session.beginIfNeeded("しうんすみか")
        session.record(fragment("しうん", "紫雲", index = 1))
        session.record(fragment("すみ", "清", index = 2))
        session.record(fragment("か", "夏", index = 1))

        val entries = session.finish(learnFirstCandidate = false)

        assertTrue(entries.any { it.input == "しうんすみ" && it.out == "紫雲清" })
        assertTrue(entries.any { it.input == "しうんすみか" && it.out == "紫雲清夏" })
    }

    @Test
    fun firstCandidatesFollowDedicatedPreference() {
        fun finished(learnFirst: Boolean): List<com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity> {
            val session = ConversionLearningSession()
            session.beginIfNeeded("しうんすみか")
            session.record(fragment("しうん", "紫雲", index = 0))
            session.record(fragment("すみか", "清夏", index = 0))
            return session.finish(learnFirstCandidate = learnFirst)
        }

        assertTrue(finished(learnFirst = false).isEmpty())
        assertEquals("紫雲清夏", finished(learnFirst = true).single { it.isPhrase }.out)
    }

    @Test
    fun explicitlySelectedFirstCandidateIsLearnedWhenPreferenceIsDisabled() {
        val session = ConversionLearningSession()
        session.beginIfNeeded("しうん")
        session.record(
            LearningFragment(
                reading = "しうん",
                output = "紫雲",
                candidateScore = 40_000,
                candidateIndex = 0,
                explicitlySelected = true,
            )
        )

        val entries = session.finish(learnFirstCandidate = false, timestamp = 123L)

        assertTrue(entries.any { it.input == "しうん" && it.out == "紫雲" })
    }

    @Test
    fun longSentenceLearnsOnlyBunsetsuNotWholeSentence() {
        val session = ConversionLearningSession()
        val reading = "きょうはとてもいいてんきですね"
        session.beginIfNeeded(reading)
        session.record(fragment("きょうは", "今日は", index = 1))
        session.record(fragment("とても", "とても", index = 0))
        session.record(fragment("いい", "良い", index = 1))
        session.record(fragment("てんきですね", "天気ですね", index = 0))

        val entries = session.finish(learnFirstCandidate = false)

        assertTrue(entries.any { it.input == "きょうは" && it.out == "今日は" && !it.isPhrase })
        assertTrue(entries.any { it.input == "いい" && it.out == "良い" })
        assertTrue(entries.none { it.input == reading })
        assertTrue(entries.all { it.input.length <= ConversionLearningSession.MAX_PHRASE_READING_LENGTH })
        // Short cumulative phrases are still learned.
        assertTrue(entries.any { it.input == "きょうはとてもいい" && it.out == "今日はとても良い" })
    }

    @Test
    fun longUnsplitWholeCommitIsNotLearned() {
        val session = ConversionLearningSession()
        val reading = "きょうはとてもいいてんきですね"
        session.beginIfNeeded(reading)
        session.record(
            LearningFragment(
                reading = reading,
                output = "今日はとても良い天気ですね",
                candidateScore = 40_000,
                candidateIndex = 1,
                unsplitWhole = true,
            )
        )
        assertTrue(session.finish(learnFirstCandidate = true).isEmpty())
    }

    @Test
    fun shortUnsplitWholeCommitIsLearned() {
        val session = ConversionLearningSession()
        session.beginIfNeeded("きょうは")
        session.record(
            LearningFragment(
                reading = "きょうは",
                output = "今日は",
                candidateScore = 40_000,
                candidateIndex = 1,
                unsplitWhole = true,
            )
        )
        val entries = session.finish(learnFirstCandidate = false)
        assertEquals(listOf("きょうは" to "今日は"), entries.map { it.input to it.out })
    }

    @Test
    fun fragmentsThatDoNotSpellOriginalReadingNeverProduceWholeEntry() {
        // A tail edited after a partial commit: the original reading is stale.
        val session = ConversionLearningSession()
        session.beginIfNeeded("きょうはいい")
        session.record(fragment("きょう", "今日", index = 1))
        session.record(fragment("はいいてんき", "は良い天気", index = 1))

        val entries = session.finish(learnFirstCandidate = false)

        assertTrue(entries.any { it.input == "きょう" && it.out == "今日" })
        assertTrue(entries.none { it.input == "きょうはいい" })
        assertTrue(entries.none { it.isPhrase })
    }

    @Test
    fun missingFragmentNeverPairsFullReadingWithPartialOutput() {
        val session = ConversionLearningSession()
        session.beginIfNeeded("きょうはいい")
        session.record(fragment("きょう", "今日", index = 1))
        // "はいい" was committed through a path that did not record a fragment.

        val entries = session.finish(learnFirstCandidate = false)

        assertEquals(listOf("きょう" to "今日"), entries.map { it.input to it.out })
    }

    private fun fragment(reading: String, output: String, index: Int) = LearningFragment(
        reading = reading,
        output = output,
        candidateScore = 40_000,
        candidateIndex = index,
    )
}

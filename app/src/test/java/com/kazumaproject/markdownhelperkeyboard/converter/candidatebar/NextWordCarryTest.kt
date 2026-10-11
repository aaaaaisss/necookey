package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextWordCarryTest {
    private fun c(s: String, yomi: String? = s) = Candidate(string = s, type = 1, length = 0u, score = 0, yomi = yomi)

    @Test
    fun filtersShownNextWordsByReadingPrefixInShownOrder() {
        val shown = listOf(c("ございます"), c("😀", ""), c("御座候", "ござそうろう"), c("ね"), c("ゴール", "ゴール"))
        assertEquals(listOf("ございます", "御座候", "ゴール"), NextWordCarry.filter(shown, "ご").map { it.string })
        assertEquals(listOf("ございます"), NextWordCarry.filter(shown, "ござい").map { it.string })
    }

    @Test
    fun dropsWordsWithoutKanaReadingOrAlreadyFullyTyped() {
        val shown = listOf(c("下さい", "下さい"), c("ね"), c("です"))
        assertTrue(NextWordCarry.filter(shown, "ね").isEmpty())
        assertTrue(NextWordCarry.filter(shown, "です").isEmpty())
        assertTrue(NextWordCarry.filter(shown, "く").isEmpty())
    }

    @Test
    fun contextMustStillEndWithCommittedText() {
        assertTrue(NextWordCarry.contextMatches("ありがとう", "どうもありがとう"))
        assertFalse(NextWordCarry.contextMatches("ありがとう", "どうもありが"))
        assertFalse(NextWordCarry.contextMatches("", "なにか"))
        assertTrue(NextWordCarry.contextMatches("一行目\n二行目", "二行目"))
    }
}

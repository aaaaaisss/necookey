package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class KanaCandidateOrderTest {
    private fun c(s: String, type: Byte = 1) = Candidate(string = s, type = type, length = 2u, score = 1000)
    private fun strings(l: List<Candidate>) = l.map { it.string }

    @Test
    fun shortReadingKeepsKanaWithinTopThree() {
        val list = listOf(c("岡"), c("桶"), c("置け"), c("オケ"), c("おけ"))
        assertEquals(listOf("岡", "桶", "おけ", "置け", "オケ"), strings(KanaCandidateOrder.ensureKanaNearTop(list, "おけ")))
    }

    @Test
    fun kanaAlreadyNearTopIsUnchanged() {
        val list = listOf(c("無料"), c("むりょう"), c("無量"), c("🈚", 11))
        assertSame(list, KanaCandidateOrder.ensureKanaNearTop(list, "むりょう"))
    }

    @Test
    fun missingKanaIsInsertedAsHiraganaCandidate() {
        val r = KanaCandidateOrder.ensureKanaNearTop(listOf(c("日"), c("草"), c("臭"), c("瘡")), "くさ")
        assertEquals(listOf("日", "草", "くさ", "臭", "瘡"), strings(r))
        assertEquals(3.toByte(), r[2].type)
    }

    @Test
    fun oneCharAndLongReadingsAreLeftAlone() {
        val one = listOf(c("目"), c("芽"), c("女"), c("め"))
        assertSame(one, KanaCandidateOrder.ensureKanaNearTop(one, "め"))
        val long = listOf(c("県庁に"), c("県長に"), c("剣頂に"), c("けんちょうに"))
        assertSame(long, KanaCandidateOrder.ensureKanaNearTop(long, "けんちょうに"))
    }
}

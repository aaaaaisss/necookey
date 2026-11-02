package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 状態 → 表示データ（上段のチップ列・下段の候補・入力欄・学習）の変換。 */
class ChipBarPresenterTest {

    private fun path(reading: String, wordReadings: String, outputs: String, splits: List<Int> = emptyList()): ChipPath {
        val rs = wordReadings.split('|')
        val os = outputs.split('|')
        require(rs.joinToString("") == reading && rs.size == os.size)
        var p = 0
        val words = rs.zip(os).map { (r, o) -> ChipWord(p, p + r.length, o).also { p += r.length } }
        return ChipPath(os.joinToString(""), words, splits)
    }

    private class Table(vararg entries: Pair<String, List<ChipPath>>) : ChipConverter {
        val map = entries.toMap()
        val calls = ArrayList<String>()
        override fun convert(reading: String, context: ChipContext): ChipConversion {
            calls += reading
            return map[reading]?.let { ChipConversion(reading, it) } ?: ChipConversion.unconverted(reading)
        }
    }

    private val table = Table(
        "ではきもの" to listOf(
            path("ではきもの", "で|は|きもの", "で|は|着物", listOf(2)),
            path("ではきもの", "で|はきもの", "で|履物", listOf(1)),
        ),
        "では" to listOf(path("では", "では", "では")),
        "きもの" to listOf(path("きもの", "きもの", "着物"), path("きもの", "きもの", "きもの")),
        "で" to listOf(path("で", "で", "で")),
        "はきもの" to listOf(path("はきもの", "はきもの", "履物")),
        "きも" to listOf(path("きも", "きも", "肝")),
        "もの" to listOf(path("もの", "もの", "物")),
    )

    private fun pred(s: String) = Candidate(string = s, type = 1, length = 5u, score = 0)

    @Test
    fun stripShowsChipsSelectionAndExpandedLetters() {
        val m = ChipBarModel(table)
        val s = m.initial("ではきもの")
        assertEquals(
            ChipStripData(
                listOf(
                    ChipStripItem("では", false, reading = "では"),
                    ChipStripItem("着物", false, reading = "きもの"),
                ),
            ),
            ChipBarPresenter.strip(s),
        )
        val ex = m.longPress(s, 1)!!
        val strip = ChipBarPresenter.strip(ex)
        assertEquals(listOf("き", "も", "の"), strip.items[1].letters)
        assertTrue(strip.items[1].selected)
        assertEquals(1, strip.expandedIndex)
        assertEquals(ChipStripData.EMPTY, ChipBarPresenter.strip(null))
    }

    @Test
    fun expandForCutShowsLettersWithoutChangingSelection() {
        val m = ChipBarModel(table)
        val s = m.initial("ではきもの")
        val ex = m.expandForCut(s, 1)!!
        val strip = ChipBarPresenter.strip(ex)
        assertEquals(listOf("き", "も", "の"), strip.items[1].letters)
        assertEquals(-1, strip.selectedIndex)
        // 展開したチップの letters は View の自前展開（codePoints）と同じ
        assertEquals(strip.items[1].letters, com.kazumaproject.markdownhelperkeyboard.ime_service.candidatebar.ChipStripLayout.codePoints("きもの"))
    }

    @Test
    fun cutOffsetMapsLetterGapToReadingOffset() {
        val m = ChipBarModel(table)
        val ex = m.longPress(m.initial("ではきもの"), 1)!!
        assertEquals(m.cutPositions(ex), listOf(0, 1).map { ChipBarPresenter.cutOffset(ex, it) })
        assertNull(ChipBarPresenter.cutOffset(ex, 2)) // 末尾はチップの端
        assertNull(ChipBarPresenter.cutOffset(m.initial("ではきもの"), 0)) // 展開なし
        val cut = m.cutAt(ex, ChipBarPresenter.cutOffset(ex, 1)!!)!!
        assertEquals(listOf("では", "肝", "の"), cut.chips.map { it.output })
    }

    @Test
    fun lowerRowPredictionsWithFullAlternativesAfterSlot() {
        val m = ChipBarModel(table)
        val s = m.initial("ではきもの")
        val preds = listOf("a", "b", "c", "d").map(::pred)
        val row = ChipBarPresenter.lowerRow(s, m.lowerRow(s), preds)
        assertEquals(listOf("a", "b", "c", "で履物", "d"), row.map { it.string })
        assertEquals(CANDIDATE_TYPE_CHIP_FULL_ALTERNATIVE, row[3].type)
        assertTrue(ChipBarPresenter.isChipCandidate(row[3]))
        assertFalse(ChipBarPresenter.isChipCandidate(row[0]))
        // 予測に同じ表記があれば全体候補は重ねない
        val dup = ChipBarPresenter.lowerRow(s, m.lowerRow(s), listOf(pred("で履物")))
        assertEquals(listOf("で履物"), dup.map { it.string })
        assertEquals(1.toByte(), dup[0].type)
    }

    @Test
    fun lowerRowChipCandidatesReplacePredictions() {
        val m = ChipBarModel(table)
        val s = m.tap(m.initial("ではきもの"), 1)
        val row = ChipBarPresenter.lowerRow(s, m.lowerRow(s), listOf(pred("a")))
        assertEquals(listOf("着物", "きもの", "キモノ"), row.map { it.string })
        assertTrue(row.all { it.type == CANDIDATE_TYPE_CHIP_CANDIDATE && it.yomi == "きもの" && it.length.toInt() == 3 })
        // もう一度タップで予測に戻る
        val back = m.tap(s, 1)
        assertEquals("a", ChipBarPresenter.lowerRow(back, m.lowerRow(back), listOf(pred("a"))).first().string)
    }

    @Test
    fun composingAndTouchedFollowEdits() {
        val m = ChipBarModel(table)
        val s = m.initial("ではきもの")
        assertFalse(ChipBarPresenter.isTouched(s))
        assertFalse(ChipBarPresenter.isEdited(s))
        val sel = m.tap(s, 1)
        assertTrue(ChipBarPresenter.isTouched(sel))
        assertFalse(ChipBarPresenter.isEdited(sel))
        assertEquals(ComposingView("では着物", 2, 4), m.composing(sel))
        val picked = m.pickCandidate(sel, "きもの")!!
        assertTrue(ChipBarPresenter.isEdited(picked))
        assertEquals("ではきもの", m.commit(picked))
        val full = m.pickFullAlternative(s, "で履物")!!
        assertEquals(-1, full.selected)
        assertTrue(ChipBarPresenter.isTouched(full))
    }

    @Test
    fun learningFragmentsAreChipsWithExplicitEdits() {
        val m = ChipBarModel(table)
        val s = m.initial("ではきもの")
        assertEquals(
            listOf(ChipLearningFragment("では", "では", false), ChipLearningFragment("きもの", "着物", false)),
            ChipBarPresenter.learningFragments(s),
        )
        val joined = m.join(s, 0)!! // ではきもの → 1 チップに再変換（RESIZED）
        assertEquals(listOf(true), ChipBarPresenter.learningFragments(joined).map { it.explicit })
        val picked = m.pickCandidate(m.tap(s, 1), "きもの")!!
        assertEquals(listOf(false, true), ChipBarPresenter.learningFragments(picked).map { it.explicit })
        assertEquals(listOf(true, true), ChipBarPresenter.learningFragments(picked).map { it.learn })
        val full = m.pickFullAlternative(s, "で履物")!!
        assertEquals(
            // で は 1 位経路の同じ範囲と同じ表記なので明示選択にしない（文は変えたので学習には書く）
            listOf(ChipLearningFragment("で", "で", false, learn = true), ChipLearningFragment("はきもの", "履物", true)),
            ChipBarPresenter.learningFragments(full),
        )
    }

    @Test
    fun seededConverterUsesSeedOnlyForSameReadingWithoutContext() {
        val seed = ChipConversion("ではきもの", listOf(path("ではきもの", "で|は|きもの", "で|は|着物", listOf(2))))
        val t = Table()
        val c = SeededChipConverter(seed, t)
        assertEquals(seed, c.convert("ではきもの"))
        assertTrue(t.calls.isEmpty())
        c.convert("ではきもの", ChipContext("左", "ひだり"))
        c.convert("きもの")
        assertEquals(listOf("ではきもの", "きもの"), t.calls)
    }

    @Test
    fun wholeConversionUsesShownPrimaryAndItsSplits() {
        fun seg(s: Int, e: Int, o: String) = CandidateConversionSegment(inputStart = s, inputEnd = e, output = o)
        val reading = "ではきもの"
        val engine = Candidate("では着物", 1, 5u, 100)
        val other = Candidate("で履物", 1, 5u, 200)
        val segs = mapOf(
            "では着物" to listOf(seg(0, 1, "で"), seg(1, 2, "は"), seg(2, 5, "着物")),
            "で履物" to listOf(seg(0, 1, "で"), seg(1, 5, "履物")),
        )
        val splits = mapOf("では着物" to listOf(2), "で履物" to listOf(1))
        val whole = ChipBarPresenter.wholeConversion(reading, engine, listOf(engine, other), segs, splits)
        assertEquals(listOf("では着物", "で履物"), whole.paths.map { it.output })
        assertEquals(listOf("では", "着物"), ChipBarModel(table).chipsOf(whole, whole.primary).map { it.output })
        // リランカーが差し替えた 1 位（split pattern なし）は語分割の自立語の頭で区切る
        val override = Candidate("で履物", 1, 5u, 0, conversionSegments = segs.getValue("で履物"))
        val w2 = ChipBarPresenter.wholeConversion(reading, override, listOf(engine, other), segs, emptyMap())
        assertEquals("で履物", w2.primary?.output)
        assertEquals(listOf("で", "履物"), ChipBarModel(table).chipsOf(w2, w2.primary).map { it.output })
    }
}

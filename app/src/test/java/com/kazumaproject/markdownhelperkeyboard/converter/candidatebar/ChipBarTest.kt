package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChipBarTest {

    /** "で|は|着物" のような語列を作る（"|" で語、読みは別に与える）。 */
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
        override fun convert(reading: String): ChipConversion {
            calls += reading
            return map[reading]?.let { ChipConversion(reading, it) } ?: ChipConversion.unconverted(reading)
        }
    }

    // ではきもの: 1 位 では|着物、2 位 で|履物
    private val table = Table(
        "ではきもの" to listOf(
            path("ではきもの", "で|は|きもの", "で|は|着物", listOf(2)),
            path("ではきもの", "で|はきもの", "で|履物", listOf(1)),
        ),
        "では" to listOf(path("では", "では", "では")),
        "きもの" to listOf(path("きもの", "きもの", "着物"), path("きもの", "きもの", "きもの")),
        "で" to listOf(path("で", "で", "で")),
        "はきもの" to listOf(path("はきもの", "はきもの", "履物"), path("はきもの", "は|きもの", "は|着物")),
        "ではき" to listOf(path("ではき", "で|はき", "で|吐き")),
        "もの" to listOf(path("もの", "もの", "物")),
        "きも" to listOf(path("きも", "きも", "肝")),
    )

    @Test
    fun initialChipsFollowPrimaryBunsetsu() {
        val m = ChipBarModel(table)
        val s = m.initial("ではきもの")
        assertEquals(listOf("では", "着物"), s.chips.map { it.output })
        assertEquals(listOf(0, 2, 5), s.boundaries)
        assertEquals(-1, s.selected)
        assertEquals(LowerRow.Predictions(listOf("で履物")), m.lowerRow(s))
    }

    @Test
    fun tapShowsChipCandidatesAndTapAgainReturnsToPredictions() {
        val m = ChipBarModel(table)
        val s = m.tap(m.initial("ではきもの"), 1)
        val row = m.lowerRow(s) as LowerRow.ChipCandidates
        assertEquals(1, row.chipIndex)
        assertEquals(listOf("着物", "きもの"), row.candidates)
        assertEquals(listOf("キモノ"), row.kana)
        assertTrue(m.lowerRow(m.tap(s, 1)) is LowerRow.Predictions)
    }

    @Test
    fun splitAlternativeComesFromAnotherNBestPath() {
        val m = ChipBarModel(table)
        val s = m.tap(m.initial("ではきもの"), 0)
        val alts = (m.lowerRow(s) as LowerRow.ChipCandidates).splitAlternatives
        assertEquals(listOf("で|履物"), alts.map { it.label })
        val picked = m.pickSplitAlternative(s, alts.single())!!
        assertEquals("で履物", picked.text)
        assertEquals(listOf(0, 1, 5), picked.boundaries)
    }

    @Test
    fun flickLeftShrinksAndNeighbourAbsorbs() {
        val m = ChipBarModel(table)
        val s = m.flickLeft(m.initial("ではきもの"), 0)!!
        assertEquals(listOf("で", "履物"), s.chips.map { it.output })
        assertEquals(0, s.selected)
        // 右フリックで戻る
        assertEquals(listOf("では", "着物"), m.flickRight(s, 0)!!.chips.map { it.output })
    }

    @Test
    fun flickRightUntilNeighbourDisappears() {
        val m = ChipBarModel(table)
        var s = m.initial("ではきもの")
        s = m.flickRight(s, 0)!! // ではき|もの
        assertEquals(listOf("で吐き", "物"), s.chips.map { it.output })
        s = m.flickRight(s, 0)!!
        s = m.flickRight(s, 0)!! // 全部 1 チップ
        assertEquals(1, s.chips.size)
        assertNull(m.flickRight(s, 0))
    }

    @Test
    fun flickLeftOnLastChipCreatesNewChip() {
        val m = ChipBarModel(table)
        val s = m.flickLeft(m.initial("ではきもの"), 1)!!
        assertEquals(listOf(0, 2, 4, 5), s.boundaries)
        assertNull(m.flickLeft(m.flickLeft(s, 2) ?: s, 2)) // 1 字のチップは縮まない
    }

    @Test
    fun pickCandidateKeepsSelectionUnlessAdvancing() {
        val s0 = ChipBarModel(table).let { it.tap(it.initial("ではきもの"), 1) }
        val keep = ChipBarModel(table).pickCandidate(s0, "きもの")!!
        assertEquals("ではきもの", keep.text)
        assertEquals(1, keep.selected)
        val adv = ChipBarModel(table, ChipBarConfig(advanceAfterPick = true))
        assertEquals(1, adv.pickCandidate(adv.tap(adv.initial("ではきもの"), 0), "で")!!.selected)
        assertEquals(-1, adv.pickCandidate(adv.tap(adv.initial("ではきもの"), 1), "きもの")!!.selected)
    }

    @Test
    fun longChipIsSplitAtContentWords() {
        // 値落ち|し|て|も|歌 が 1 文節（読み 8 字）なら 値落ちしても|歌 に割る
        val r = "ねおちしてもうた"
        val t = Table(r to listOf(path(r, "ねおち|し|て|も|うた", "値落ち|し|て|も|歌")))
        val s = ChipBarModel(t).initial(r)
        assertEquals(listOf("値落ちしても", "歌"), s.chips.map { it.output })
        assertTrue(s.chips.all { it.origin == Chip.Origin.WORD_SPLIT })
        val unsplit = ChipBarModel(t, ChipBarConfig(maxChipReading = 20)).initial(r)
        assertEquals(listOf("値落ちしても歌"), unsplit.chips.map { it.output })
    }

    @Test
    fun fullAlternativeReplacesChips() {
        val m = ChipBarModel(table)
        val s = m.pickFullAlternative(m.initial("ではきもの"), "で履物")!!
        assertEquals(listOf("で", "履物"), s.chips.map { it.output })
        assertEquals("で履物", m.commit(s))
    }

    @Test
    fun solversCountMoves() {
        val m = ChipBarModel(table)
        assertEquals(0, NewUiSolver(m).solve("ではきもの", "では着物").moves)
        // 左フリック 1 回で で|履物
        assertEquals(1, NewUiSolver(m).solve("ではきもの", "で履物").moves)
        // では|きもの: タップ + かな候補
        assertEquals(2, NewUiSolver(m).solve("ではきもの", "ではきもの").moves)
        assertNull(NewUiSolver(m).solve("ではきもの", "出歯着物").moves)

        assertEquals(1, CurrentUiSolver(table).solve("ではきもの", "で履物").moves) // 全体の 2 位
        // 変換 → フォーカス→ → 候補(きもの)
        assertEquals(3, CurrentUiSolver(table).solve("ではきもの", "ではきもの").moves)
    }

    @Test
    fun memoConverterCallsEngineOnce() {
        val t = Table()
        val memo = MemoChipConverter(t)
        memo.convert("あ"); memo.convert("あ")
        assertEquals(listOf("あ"), t.calls)
        assertEquals(1, memo.calls)
    }

    @Test
    fun firstWrongFindsDivergingChip() {
        assertEquals(1 to 2, firstWrong(listOf("では", "着物"), "では履物"))
        assertEquals(2 to 4, firstWrong(listOf("では", "着物"), "では着物"))
        assertEquals(1 to 1, firstWrong(listOf("で", "は"), "でわい"))
    }
}

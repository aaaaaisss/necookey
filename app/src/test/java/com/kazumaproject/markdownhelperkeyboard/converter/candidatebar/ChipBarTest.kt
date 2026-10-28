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
        val contexts = ArrayList<Pair<String, ChipContext>>()
        override fun convert(reading: String, context: ChipContext): ChipConversion {
            calls += reading
            contexts += reading to context
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
    fun longPressExpandsAndTapBetweenLettersCuts() {
        val t = Table(*table.map.toList().toTypedArray())
        val m = ChipBarModel(t)
        val ex = m.longPress(m.initial("ではきもの"), 1)!!
        assertEquals(1, ex.expanded)
        assertEquals(1, ex.selected)
        assertEquals(listOf(3, 4), m.cutPositions(ex))
        assertNull(m.cutAt(ex, 2)) // チップの端は切れない
        t.contexts.clear()
        val cut = m.cutAt(ex, 4)!!
        assertEquals(listOf("では", "肝", "の"), cut.chips.map { it.output })
        assertEquals(listOf(0, 2, 4, 5), cut.boundaries)
        assertEquals(1, cut.selected)
        assertEquals(-1, cut.expanded)
        // 右の片は切った左の片まで含めた左文脈で再変換
        assertEquals(listOf("きも" to ChipContext("では", "では"), "の" to ChipContext("では肝", "ではきも")), t.contexts)
        // 1 字のチップは展開しない、展開していなければ切れない
        assertNull(m.longPress(cut, 2))
        assertNull(m.cutAt(cut, 3))
        assertTrue(m.cutPositions(cut).isEmpty())
    }

    @Test
    fun tapClosesExpansion() {
        val m = ChipBarModel(table)
        val ex = m.longPress(m.initial("ではきもの"), 0)!!
        val t = m.tap(ex, 0)
        assertEquals(-1, t.expanded)
        assertEquals(0, t.selected)
        assertEquals(-1, m.tap(t, 0).selected)
    }

    @Test
    fun joinMergesNeighboursAndCutRestoresOriginal() {
        val t = Table(*table.map.toList().toTypedArray())
        val m = ChipBarModel(t)
        val picked = m.pickCandidate(m.tap(m.initial("ではきもの"), 1), "きもの")!!
        val joined = m.join(picked, 0)!!
        assertEquals(listOf("では着物"), joined.chips.map { it.output })
        assertEquals(Chip.Origin.RESIZED, joined.chips.single().origin)
        assertEquals(0, joined.selected)
        assertNull(m.join(joined, 0)) // 末尾チップ
        // 同じ位置で切り直すと再変換せず元の表記（選んだ「きもの」）に戻る
        t.calls.clear()
        val back = m.cutAt(m.longPress(joined, 0)!!, 2)!!
        assertEquals(listOf("では", "きもの"), back.chips.map { it.output })
        assertTrue(t.calls.isEmpty())
    }

    @Test
    fun flickReconvertsWithLeftContextAndRestoresOriginalOutput() {
        val t = Table(*table.map.toList().toTypedArray())
        val m = ChipBarModel(t)
        val picked = m.pickCandidate(m.tap(m.initial("ではきもの"), 1), "きもの")!!
        t.contexts.clear()
        val s = m.flickLeft(picked, 0)!!
        assertEquals(listOf("で", "履物"), s.chips.map { it.output })
        assertTrue("はきもの" to ChipContext("で", "で") in t.contexts)
        // 区切りを戻すと再変換せず元の表記（選んだ「きもの」）に戻る
        val back = m.flickRight(s, 0)!!
        assertEquals(listOf("では", "きもの"), back.chips.map { it.output })
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
    fun longChipIsSplitAtContentWordsOfPrimaryPathOnly() {
        // 値落ち|し|て|も|歌 が 1 文節（読み 8 字）なら 値落ちしても|歌 に割る（2 位の区切りは見ない）
        val r = "ねおちしてもうた"
        val t = Table(
            r to listOf(
                path(r, "ねおち|し|て|も|うた", "値落ち|し|て|も|歌"),
                path(r, "ねおち|してもうた", "寝落ち|してもうた", listOf(3)),
            ),
        )
        val s = ChipBarModel(t).initial(r)
        assertEquals(listOf("値落ちしても", "歌"), s.chips.map { it.output })
        assertTrue(s.chips.all { it.origin == Chip.Origin.WORD_SPLIT })
        val unsplit = ChipBarModel(t, ChipBarConfig(maxChipReading = 20)).initial(r)
        assertEquals(listOf("値落ちしても歌"), unsplit.chips.map { it.output })
    }

    @Test
    fun shortChipIsNotSplitEvenIfRivalsDisagree() {
        // 2 位が で|履物 でも では は割らない（揺れによる分割はしない）
        assertEquals(listOf("では", "着物"), ChipBarModel(table).initial("ではきもの").chips.map { it.output })
    }

    @Test
    fun fullAlternativeReplacesChips() {
        val m = ChipBarModel(table)
        val s = m.pickFullAlternative(m.initial("ではきもの"), "で履物")!!
        assertEquals(listOf("で", "履物"), s.chips.map { it.output })
        assertEquals("で履物", m.commit(s))
    }

    @Test
    fun composingTextSpanAndCommit() {
        val m = ChipBarModel(table)
        val s = m.initial("ではきもの")
        assertEquals(ComposingView("では着物"), m.composing(s))
        assertTrue(!m.composing(s).hasSelection)
        val sel = m.pickCandidate(m.tap(s, 1), "きもの")!!
        assertEquals(ComposingView("ではきもの", 2, 5), m.composing(sel))
        assertEquals(ComposingView("ではきもの", 0, 2), m.composing(m.tap(sel, 0)))
        assertEquals("ではきもの", m.commit(sel))
    }

    @Test
    fun solversCountMoves() {
        val m = ChipBarModel(table)
        assertEquals(0, NewUiSolver(m).solve("ではきもの", "では着物").moves)
        // 左フリック 1 回で で|履物
        assertEquals(1, NewUiSolver(m).solve("ではきもの", "で履物").moves)
        // では|きもの: タップ → 候補
        assertEquals(2, NewUiSolver(m).solve("ではきもの", "ではきもの").moves)
        assertNull(NewUiSolver(m).solve("ではきもの", "出歯着物").moves)

        // 1 チップを離れた位置で切る = 長押し＋タップ 2 手（フリックなら 3 手）
        val t2 = Table(
            "かきくけこ" to listOf(path("かきくけこ", "かきくけこ", "かきくけこ")),
            "かき" to listOf(path("かき", "かき", "柿")),
            "くけこ" to listOf(path("くけこ", "くけこ", "区毛子")),
        )
        val cut = NewUiSolver(ChipBarModel(t2)).solve("かきくけこ", "柿区毛子")
        assertEquals(2, cut.moves)
        assertEquals(listOf("切る#0「かき|くけこ」"), cut.plan)

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

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
    fun expandForCutKeepsSelectionAndCollapseOrCutCloses() {
        val m = ChipBarModel(table)
        val s = m.tap(m.initial("ではきもの"), 0)
        val ex = m.expandForCut(s, 1)!!
        assertEquals(1, ex.expanded)
        assertEquals(0, ex.selected) // 選択は変えない（取り消しで元どおり）
        assertNull(m.expandForCut(ex, 1)) // もう展開している
        val back = m.collapse(ex)!!
        assertEquals(s, back)
        assertNull(m.collapse(back))
        // 離して切る: 左の片が選択、展開は閉じる
        val cut = m.cutChip(ex, 1, 4)!!
        assertEquals(listOf("では", "肝", "の"), cut.chips.map { it.output })
        assertEquals(1, cut.selected)
        assertEquals(-1, cut.expanded)
        // 1 字のチップは展開しない
        assertNull(m.expandForCut(cut, 2))
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

    @Test
    fun typingMoreKeepsEditsLeftOfTheChange() {
        val t = Table(
            *table.map.toList().toTypedArray(),
            "ではきものを" to listOf(path("ではきものを", "で|は|きもの|を", "で|は|着物|を", listOf(2))),
            "ではきもと" to listOf(path("ではきもと", "で|は|きもと", "で|は|木許", listOf(2))),
        )
        val m = ChipBarModel(t)
        val picked = m.pickCandidate(m.tap(m.initial("ではきもの"), 1), "きもの")!!
        // 打ち足し: 選んだ「きもの」を残し、足した「を」は新しい 1 位経路の語で
        val more = m.carryOver(picked, m.initial("ではきものを"))!!
        assertEquals(listOf("では", "きもの", "を"), more.chips.map { it.output })
        assertEquals(listOf(0, 2, 5, 6), more.boundaries)
        assertEquals(-1, more.selected)
        // 最後の字が変わった（フリックの濁点など）なら、その字を含むチップの編集は残さない
        assertNull(m.carryOver(picked, m.initial("ではきもと")))
        // 残す範囲の終わりが新しい 1 位経路の語の頭でないときは、残りを左文脈付きで再変換する
        val shifted = m.shiftBoundary(m.initial("ではきもの"), 0, 1)!! // で吐き|物
        assertNull(m.carryOver(shifted, m.initial("ではきもと"), reconvert = false))
        val fresh = m.initial("ではきもと")
        t.contexts.clear()
        val re = m.carryOver(shifted, fresh)!!
        assertEquals(listOf("で吐き", "もと"), re.chips.map { it.output })
        assertEquals(listOf("もと" to ChipContext("で吐き", "ではき")), t.contexts)
    }

    // ---- 区切り編集モード（applySegmentation） ----

    @Test
    fun applySegmentationAddsBoundaryAndKeepsUnchangedChips() {
        val m = ChipBarModel(table)
        val s = m.initial("ではきもの") // では|着物
        val first = s.chips[0]
        val out = m.applySegmentation(s, setOf(2, 4))!! // では|きも|の
        assertEquals(listOf("では", "肝", "の"), out.chips.map { it.output })
        assertEquals(listOf(0, 2, 4, 5), out.boundaries)
        assertTrue(out.chips[0] === first) // 同じ範囲のチップはそのまま（再変換しない）
        assertEquals(Chip.Origin.RESIZED, out.chips[1].origin)
        assertEquals(1, out.selected) // 変わった一番左のチップ
        assertEquals(-1, out.expanded)
    }

    @Test
    fun applySegmentationRemovesAndMovesBoundaries() {
        val m = ChipBarModel(table)
        val s = m.initial("ではきもの")
        val whole = m.applySegmentation(s, emptySet())!!
        assertEquals(listOf("では着物"), whole.chips.map { it.output })
        assertEquals(listOf(0, 5), whole.boundaries)
        // 作った塊は自動で割らない（読み 7 字以上の文節の割り直しは初期チップだけ）
        assertEquals(1, whole.chips.size)
        val moved = m.applySegmentation(s, setOf(3))!! // ではき|もの
        assertEquals(listOf("で吐き", "物"), moved.chips.map { it.output })
        assertEquals(0, moved.selected)
    }

    @Test
    fun applySegmentationConvertsLeftToRightWithNewLeftContext() {
        val m = ChipBarModel(table)
        m.applySegmentation(m.initial("ではきもの"), setOf(3))!!
        // 右の片（もの）は、作ったばかりの左の片（で吐き）を左文脈にして変換
        assertEquals(ChipContext("で吐き", "ではき"), table.contexts.last { it.first == "もの" }.second)
        assertEquals(ChipContext.EMPTY, table.contexts.last { it.first == "ではき" }.second)
    }

    @Test
    fun applySegmentationRestoresRememberedChipsWithoutReconverting() {
        val m = ChipBarModel(table)
        val s = m.initial("ではきもの")
        val picked = m.pickCandidate(m.tap(s, 1), "きもの")!! // では|きもの（かなを選んだ）
        val split = m.applySegmentation(picked, setOf(2, 4))!!
        val callsBefore = table.calls.size
        val back = m.applySegmentation(split, setOf(2))!!
        assertEquals(listOf("では", "きもの"), back.chips.map { it.output })
        assertEquals(Chip.Origin.PICKED, back.chips[1].origin) // 選んだ表記・origin のまま戻る
        assertEquals(callsBefore, table.calls.size)
    }

    @Test
    fun applySegmentationIgnoresInvalidAndUnchangedBoundaries() {
        val m = ChipBarModel(table)
        val s = m.initial("ではきもの")
        assertNull(m.applySegmentation(s, setOf(2)))
        assertNull(m.applySegmentation(s, setOf(0, 2, 5, 99, -1))) // 両端・範囲外は無視 → 今と同じ
        assertNull(m.applySegmentation(m.initial(""), setOf(1)))
    }

    @Test
    fun applySegmentationSelectOverride() {
        val m = ChipBarModel(table)
        val s = m.initial("ではきもの")
        assertEquals(-1, m.applySegmentation(s, setOf(2, 4), select = -1)!!.selected)
        assertEquals(2, m.applySegmentation(s, setOf(2, 4), select = 2)!!.selected)
        assertEquals(-1, m.applySegmentation(s, setOf(2, 4), select = 9)!!.selected) // 範囲外は選択なし
    }

    @Test
    fun applySegmentationDoesNotSplitSurrogatePairs() {
        val m = ChipBarModel(Table("𠮷や" to listOf(path("𠮷や", "𠮷や", "吉野家"))))
        val s = m.initial("𠮷や")
        assertNull(m.applySegmentation(s, setOf(1))) // 𠮷 の途中
        assertEquals(listOf(0, 2, 3), m.applySegmentation(s, setOf(2))!!.boundaries)
    }

    // ---- 区切りを変える候補（2026-11-07） ----

    @Test
    fun segmentAlternativesComeFromWholeNBestPaths() {
        val t = Table(*table.map.toList().toTypedArray())
        val m = ChipBarModel(t)
        val s = m.tap(m.initial("ではきもの"), 1) // では|[着物]
        val row = m.lowerRow(s) as LowerRow.ChipCandidates
        // 2 位の経路 で|履物 → 左隣を取り込んで区切りが動く
        assertEquals(1, row.segments.size)
        val alt = row.segments[0]
        assertEquals(listOf("で", "履物"), alt.pieces.map { it.output })
        assertEquals(0 to 5, alt.start to alt.end)
        assertEquals(SegmentAlternative.Direction.LEFT, alt.direction)
        // 適用: 変換し直さず（表記は候補のまま）、元の選択チップの頭を含むチップを選択
        val calls = t.calls.size
        val out = m.applyAlternative(s, alt)!!
        assertEquals(calls, t.calls.size)
        assertEquals(listOf("で", "履物"), out.chips.map { it.output })
        assertEquals(listOf(0, 1, 5), out.boundaries)
        assertEquals(1, out.selected)
        assertTrue(out.chips.all { it.origin == Chip.Origin.RESIZED })
        // 戻すと remembered で元の表記
        val back = m.applySegmentation(out, setOf(2))!!
        assertEquals(listOf("では", "着物"), back.chips.map { it.output })
        // 状態が変わって範囲が合わなければ null
        assertNull(m.applyAlternative(m.applySegmentation(s, setOf(2, 4))!!, alt.copy(start = 1)))
    }

    @Test
    fun refineAddsMergedConversionsAndContextSurfaces() {
        val t = Table(
            "いいほうほうないか" to listOf(path("いいほうほうないか", "いい|ほうほう|ないか", "いい|方法|内科", listOf(2, 6))),
            "ないか" to listOf(path("ないか", "ないか", "内科")),
            "ほうほうないか" to listOf(path("ほうほうないか", "ほうほう|ない|か", "方法|ない|か")),
        )
        val m = ChipBarModel(t)
        val s = m.tap(m.initial("いいほうほうないか"), 2) // いい|方法|[内科]
        val row = m.lowerRow(s) as LowerRow.ChipCandidates
        assertTrue(row.segments.isEmpty())
        val refined = m.refineLowerRow(s, row)!!
        assertEquals(listOf("方法ないか"), refined.segments.map { it.surface })
        assertEquals(SegmentAlternative.Direction.LEFT, refined.segments[0].direction)
        assertEquals(2 to 9, refined.segments[0].start to refined.segments[0].end)
        assertEquals(listOf("ないか"), refined.context) // くっつけた読みの経路で、このチップの範囲 = ない|か
        // 左隣を左文脈にして変換
        assertEquals(ChipContext("いい", "いい"), t.contexts.last { it.first == "ほうほうないか" }.second)
        val out = m.applyAlternative(s, refined.segments[0])!!
        assertEquals(listOf("いい", "方法ないか"), out.chips.map { it.output })
        assertEquals(1, out.selected)
        // もう一杯・選択が違うときは何もしない
        assertNull(m.refineLowerRow(s.copy(selected = 1), refined))
    }

    @Test
    fun refineOffersMergedChipWhenPathKeepsSameBoundary() {
        // は|磨いて の「磨いて」を選ぶ → くっつけた読みの 2 位「歯磨いて」を 1 チップで
        val t = Table(
            "はみがいて" to listOf(
                path("はみがいて", "は|みがい|て", "は|磨い|て", listOf(1)),
                path("はみがいて", "は|みがい|て", "歯|磨い|て"),
            ),
            "みがいて" to listOf(path("みがいて", "みがい|て", "磨い|て")),
        )
        val m = ChipBarModel(t)
        val s = m.tap(m.initial("はみがいて"), 1)
        assertEquals(listOf("は", "磨いて"), s.chips.map { it.output })
        val row = m.lowerRow(s) as LowerRow.ChipCandidates
        assertTrue(row.segments.isEmpty()) // 2 位の文節は区切りが同じ（歯|磨いて）なので区切りの候補ではない
        val refined = m.refineLowerRow(s, row)!!
        assertEquals(listOf("歯磨いて"), refined.segments.map { it.surface })
        assertEquals(1, refined.segments[0].pieces.size) // 1 チップ
    }

    @Test
    fun alternativeFromRejectsFarReachingAndUnchanged() {
        val m = ChipBarModel(table)
        val s = m.applySegmentation(m.initial("ではきもの"), setOf(1, 2, 4))!! // で|は|きも|の
        // 4 チップをまたぐ塊（左右の隣より先）は出さない
        assertNull(m.alternativeFrom(s, 0, listOf(Chip(0, 5, "では着物"))))
        // 同じ区切り・同じ表記は出さない
        assertNull(m.alternativeFrom(s, 1, s.chips))
        // 右隣とくっつける
        val alt = m.alternativeFrom(s, 1, listOf(Chip(0, 1, "で"), Chip(1, 4, "はきも")))!!
        assertEquals(SegmentAlternative.Direction.RIGHT, alt.direction)
        assertEquals(1 to 4, alt.start to alt.end)
    }

    @Test
    fun applySegmentationWithSurfacesSkipsReconversion() {
        val t = Table(*table.map.toList().toTypedArray())
        val m = ChipBarModel(t)
        val s = m.initial("ではきもの")
        val calls = t.calls.size
        val out = m.applySegmentation(s, setOf(1), select = 1, surfaces = mapOf((0 to 1) to "出", (1 to 5) to "履物"))!!
        assertEquals(listOf("出", "履物"), out.chips.map { it.output })
        assertEquals(1, out.selected)
        assertEquals(calls, t.calls.size)
        // 区切りが同じでも表記が違えば反映、同じなら null
        assertEquals("肝の", m.applySegmentation(s, setOf(2), surfaces = mapOf((2 to 5) to "肝の"))!!.chips[1].output)
        assertNull(m.applySegmentation(s, setOf(2), surfaces = mapOf((2 to 5) to "着物")))
    }
}

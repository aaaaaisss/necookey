package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment

/*
 * 文節チップ UI の中身（View なしのモデル）。docs/CHIP_BAR.md 参照。
 *
 * 上段 = 文節チップ列、下段 = 普段は予測、チップをタップしたときだけそのチップの候補（漢字候補・かな）。
 * 境界操作: 長押しでチップをかな 1 文字ずつに展開 → 文字の間をタップで切る / 隣り合うチップの間をタップ
 * （またはチップからチップへスワイプ）でくっつける / チップの左右フリックで境界を 1 字ずらす。
 * 変わった範囲だけ左文脈付きで再変換する。エンジンは [ChipConverter] で注入する（テストでは表、評価ハーネスでは実エンジン）。
 */

/** 1 語（ラティスのノード）。オフセットは変換した読みの UTF-16 位置。 */
data class ChipWord(val start: Int, val end: Int, val output: String)

/** 読み全体を覆う 1 本の経路。[bunsetsuSplits] は文節の内部区切り（Sumire の split pattern）。 */
data class ChipPath(
    val output: String,
    val words: List<ChipWord>,
    val bunsetsuSplits: List<Int>,
)

/**
 * ある読みの変換結果。[paths] はエンジン順（[0] が 1 位）。[partials] は読みの先頭だけを覆う候補
 * （Sumire の type 5。現行 UI の「最初の文節の候補」用）。
 */
data class ChipConversion(
    val reading: String,
    val paths: List<ChipPath>,
    val partials: List<ChipWord> = emptyList(),
) {
    val primary: ChipPath? get() = paths.firstOrNull()

    companion object {
        fun unconverted(reading: String) = ChipConversion(
            reading,
            listOf(ChipPath(reading, listOf(ChipWord(0, reading.length, reading)), emptyList())),
        )

        /**
         * エンジンの結果から作る。[candidates] は表示順（昇格後）。語分割が出力と合わない候補は 1 語扱い。
         */
        fun fromEngine(
            reading: String,
            candidates: List<Candidate>,
            segmentsByString: Map<String, List<CandidateConversionSegment>>,
            splitPatternByCandidateString: Map<String, List<Int>>,
            primarySplits: List<Int> = emptyList(),
            maxPaths: Int = 20,
        ): ChipConversion {
            val seen = HashSet<String>()
            val full = candidates.filter { it.length.toInt() == reading.length && it.string.isNotEmpty() && seen.add(it.string) }
                .take(maxPaths)
            val paths = full.mapIndexed { i, c ->
                val segs = segmentsByString[c.string].orEmpty().ifEmpty { c.conversionSegments }
                val ok = segs.isNotEmpty() && segs.first().inputStart == 0 && segs.last().inputEnd == reading.length &&
                    segs.zipWithNext().all { (a, b) -> a.inputEnd == b.inputStart && a.inputStart < a.inputEnd } &&
                    segs.joinToString("") { it.output } == c.string
                val words = if (ok) segs.map { ChipWord(it.inputStart, it.inputEnd, it.output) }
                else listOf(ChipWord(0, reading.length, c.string))
                val splits = (splitPatternByCandidateString[c.string] ?: if (i == 0) primarySplits else emptyList())
                    .filter { it in 1 until reading.length }.distinct().sorted()
                ChipPath(c.string, words, splits)
            }
            val partials = candidates.filter { it.length.toInt() in 1 until reading.length }
                .map { ChipWord(0, it.length.toInt(), it.string) }
                .distinct()
            return if (paths.isEmpty()) unconverted(reading) else ChipConversion(reading, paths, partials)
        }
    }
}

/** 再変換の左文脈（そのチップより左のチップの表記と読み）。 */
data class ChipContext(val leftText: String = "", val leftReading: String = "") {
    companion object {
        val EMPTY = ChipContext()
    }
}

fun interface ChipConverter {
    /** [context] = 左文脈。実機では左文脈付き変換（無視してもよい）。 */
    fun convert(reading: String, context: ChipContext): ChipConversion

    fun convert(reading: String): ChipConversion = convert(reading, ChipContext.EMPTY)
}

/** 同じ読み（と文脈）を何度も変換しないための薄いキャッシュ（評価・UI 共通）。 */
class MemoChipConverter(private val base: ChipConverter) : ChipConverter {
    private val cache = HashMap<Pair<String, ChipContext>, ChipConversion>()
    val calls: Int get() = cache.size
    override fun convert(reading: String, context: ChipContext): ChipConversion =
        cache.getOrPut(reading to context) {
            if (reading.isEmpty()) ChipConversion(reading, emptyList()) else base.convert(reading, context)
        }
}

/** 上段の 1 チップ。オフセットは入力全体の読みの位置。 */
data class Chip(val start: Int, val end: Int, val output: String, val origin: Origin = Origin.BUNSETSU) {
    /** BUNSETSU = 1 位経路の文節、WORD_SPLIT = 長い文節を割った片、RESIZED = 境界操作（切る・くっつける・ずらす）で再変換、PICKED = 候補を選んだ。 */
    enum class Origin { BUNSETSU, WORD_SPLIT, RESIZED, PICKED }

    val length: Int get() = end - start
}

sealed interface LowerRow {
    /** 普段の下段（予測）。モデルは予測を持たず、読み全体の別候補だけ渡す（UI が予測と並べる）。 */
    data class Predictions(val fullAlternatives: List<String>) : LowerRow

    /**
     * [segments] = 区切りを変える候補（選択チップの周りを割り直した変換済みの塊。最大 [ChipBarConfig.maxSegmentAlternatives]）。
     * [context] = 文全体の経路で選択チップと同じ範囲になった表記（例: 「ないか」。前の方に寄せる普通の候補）。
     * docs/CHIP_BAR.md「2026-11-07 区切りを変える候補・端のハンドル」。
     */
    data class ChipCandidates(
        val chipIndex: Int,
        val candidates: List<String>,
        val kana: List<String>,
        val segments: List<SegmentAlternative> = emptyList(),
        val context: List<String> = emptyList(),
    ) : LowerRow {
        val all: List<String> get() = (candidates + kana).distinct()
    }
}

/**
 * 区切りを変える候補: 選択チップ（読みの [chipStart]..[chipEnd]）の周りの読み [start]..[end]（今のチップの区切りに揃う。
 * 選択チップと左右の隣まで）を [pieces]（変換済み）に置き換える。
 */
data class SegmentAlternative(
    val chipStart: Int,
    val chipEnd: Int,
    val start: Int,
    val end: Int,
    val pieces: List<Chip>,
) {
    enum class Direction {
        /** 左隣を取り込む（左の区切りが動く・消える）。 */
        LEFT,
        /** 右隣を取り込む。 */
        RIGHT,
        BOTH,
        /** 選択チップの中だけ（割る）。 */
        SPLIT,
    }

    val surface: String get() = pieces.joinToString("") { it.output }

    val direction: Direction
        get() {
            val left = start < chipStart
            val right = end > chipEnd
            return when {
                left && right -> Direction.BOTH
                left -> Direction.LEFT
                right -> Direction.RIGHT
                else -> Direction.SPLIT
            }
        }

    /** 同じ置き換えかどうか（重複を除くため）。 */
    val key: List<Triple<Int, Int, String>> get() = pieces.map { Triple(it.start, it.end, it.output) }
}

data class ChipBarConfig(
    /** これより長い（読みの UTF-16 長）チップは 1 位経路の自立語の頭で割る。 */
    val maxChipReading: Int = 6,
    val maxCandidates: Int = 8,
    val maxFullAlternatives: Int = 8,
    /** 下段に出す「区切りを変える候補」の最大数。 */
    val maxSegmentAlternatives: Int = 3,
    /** 文全体の経路から取った同じ範囲の表記（[LowerRow.ChipCandidates.context]）の最大数。 */
    val maxContextSurfaces: Int = 2,
    /** 候補を選んだら次のチップへ選択を進める（既定は仕様どおり、選んだチップのまま）。 */
    val advanceAfterPick: Boolean = false,
) {
    companion object {
        val DEFAULT = ChipBarConfig()
    }
}

data class ChipBarState(
    val reading: String,
    val chips: List<Chip>,
    /** 選択中のチップ。-1 = なし（下段は予測）。 */
    val selected: Int,
    /** 入力全体の変換（予測行の別候補の元）。 */
    val whole: ChipConversion,
    /** 境界操作で消えたチップ（範囲 → チップ）。同じ範囲に戻ったら再変換せず元の表記に戻す。 */
    val remembered: Map<Pair<Int, Int>, Chip> = emptyMap(),
    /** 長押しでかな 1 文字ずつに展開しているチップ。-1 = なし。 */
    val expanded: Int = -1,
) {
    val text: String get() = chips.joinToString("") { it.output }
    val boundaries: List<Int> get() = listOf(0) + chips.map { it.end }
}

/** 入力欄の composing 表示。[selectedStart]..[selectedEnd] は選択チップの範囲（UTF-16、色付け span 用。無ければ -1）。 */
data class ComposingView(val text: String, val selectedStart: Int = -1, val selectedEnd: Int = -1) {
    val hasSelection: Boolean get() = selectedStart in 0..selectedEnd
}

class ChipBarModel(
    private val converter: ChipConverter,
    private val config: ChipBarConfig = ChipBarConfig.DEFAULT,
) {

    fun initial(reading: String): ChipBarState {
        val whole = converter.convert(reading)
        return ChipBarState(reading, chipsOf(whole, whole.primary), -1, whole)
    }

    /** 読み全体の経路を文節チップにする（読みが [ChipBarConfig.maxChipReading] より長い文節は自立語の頭で割る）。 */
    internal fun chipsOf(whole: ChipConversion, path: ChipPath?): List<Chip> {
        val reading = whole.reading
        if (path == null || reading.isEmpty()) return listOf(Chip(0, reading.length, reading))
        val wordStarts = path.words.mapTo(HashSet()) { it.start }
        val cuts = path.bunsetsuSplits.filter { it in 1 until reading.length && it in wordStarts }
        val bounds = listOf(0) + cuts + reading.length
        val out = ArrayList<Chip>()
        for ((s, e) in bounds.zipWithNext()) {
            val words = path.words.filter { it.start >= s && it.end <= e }
            if (e - s > config.maxChipReading) {
                val groups = contentGroups(words)
                if (groups.size > 1) {
                    groups.forEach { g -> out += Chip(g.first().start, g.last().end, g.joinToString("") { it.output }, Chip.Origin.WORD_SPLIT) }
                    continue
                }
            }
            out += Chip(s, e, words.joinToString("") { it.output })
        }
        return out
    }

    fun lowerRow(state: ChipBarState): LowerRow {
        val i = state.selected
        if (i !in state.chips.indices) {
            return LowerRow.Predictions(
                state.whole.paths.drop(1).map { it.output }.filter { it != state.text }.distinct().take(config.maxFullAlternatives),
            )
        }
        val chip = state.chips[i]
        val chipReading = state.reading.substring(chip.start, chip.end)
        val conv = converter.convert(chipReading, leftContext(state.reading, state.chips, i))
        val candidates = (listOf(chip.output) + conv.paths.map { it.output }).distinct().take(config.maxCandidates + 1)
        val kana = listOf(chipReading, toKatakana(chipReading)).distinct().filter { it !in candidates }
        // 区切りを変える候補（追加の変換なし）: 文全体の N-best 経路 → このチップだけの変換の経路（割る）
        val segments = ArrayList<SegmentAlternative>()
        val context = ArrayList<String>()
        for (path in state.whole.paths) {
            addSegment(segments, alternativeFrom(state, i, pathPieces(state.whole, path, 0)))
            addContext(context, chip, contextSurface(path, 0, chip))
        }
        for (path in conv.paths) addSegment(segments, alternativeFrom(state, i, pathPieces(conv, path, chip.start)))
        return LowerRow.ChipCandidates(i, candidates, kana, segments, context)
    }

    /**
     * 下段の 2 段目の計算（選択チップを左右の隣とくっつけた読みを変換する。1 回 1〜2 変換）。[row] に区切りを変える候補・
     * 同じ範囲の表記を足したもの。足すものが無い・もう一杯なら null。IMEService が下段を出した後に裏で呼ぶ
     * （選択の反応を待たせないため）。
     */
    fun refineLowerRow(state: ChipBarState, row: LowerRow.ChipCandidates): LowerRow.ChipCandidates? {
        val i = row.chipIndex
        val chip = state.chips.getOrNull(i) ?: return null
        if (i != state.selected || row.segments.size >= config.maxSegmentAlternatives) return null
        val segments = row.segments.toMutableList()
        val context = row.context.toMutableList()
        for ((a, b) in listOf(i - 1 to i, i to i + 1)) {
            if (segments.size >= config.maxSegmentAlternatives) break
            if (a < 0 || b > state.chips.lastIndex) continue
            val lo = state.chips[a].start
            val hi = state.chips[b].end
            val conv = converter.convert(state.reading.substring(lo, hi), leftContext(state.reading, state.chips, a))
            for (path in conv.paths.take(MERGE_PATHS)) {
                // 経路の文節どおり（区切りが動くことがある）→ 1 つにくっつけた塊
                val before = segments.size
                addSegment(segments, alternativeFrom(state, i, pathPieces(conv, path, lo)))
                if (segments.size == before) addSegment(segments, alternativeFrom(state, i, listOf(Chip(lo, hi, path.output, Chip.Origin.RESIZED))))
                if (segments.size > before) break
            }
            conv.primary?.let { addContext(context, chip, contextSurface(it, lo, chip)) }
        }
        if (segments.size == row.segments.size && context.size == row.context.size) return null
        return row.copy(segments = segments, context = context)
    }

    private fun addSegment(out: MutableList<SegmentAlternative>, alt: SegmentAlternative?) {
        if (alt == null || out.size >= config.maxSegmentAlternatives) return
        if (out.any { it.key == alt.key }) return
        out += alt
    }

    private fun addContext(out: MutableList<String>, chip: Chip, surface: String?) {
        if (surface == null || surface.isEmpty() || surface == chip.output || surface in out) return
        if (out.size < config.maxContextSurfaces) out += surface
    }

    /**
     * 経路 [path]（[conv] の読み = 入力全体の読みの [offset] から）を文節の塊にする（文節区切りが無い経路は自立語の頭で）。
     * オフセットは入力全体の読みの位置。
     */
    internal fun pathPieces(conv: ChipConversion, path: ChipPath, offset: Int): List<Chip> {
        val base = if (path.bunsetsuSplits.isNotEmpty()) {
            chipsOf(conv, path)
        } else {
            contentGroups(path.words).map { g -> Chip(g.first().start, g.last().end, g.joinToString("") { it.output }) }
        }
        return base.map { it.copy(start = it.start + offset, end = it.end + offset, origin = Chip.Origin.RESIZED) }
    }

    /**
     * 塊 [pieces]（入力全体の読みの位置、連続）から選択チップ [index] の区切りを変える候補を作る: チップの範囲から始めて、
     * 塊の境目と今のチップの境目が両方そろう所まで広げた範囲を [pieces] で置き換える。範囲が左右の隣より先へ広がる・
     * 塊が範囲を覆わない・区切りが今と同じ・表記が今と同じなら null。
     */
    internal fun alternativeFrom(state: ChipBarState, index: Int, pieces: List<Chip>): SegmentAlternative? {
        val chips = state.chips
        val chip = chips.getOrNull(index) ?: return null
        if (pieces.isEmpty() || pieces.zipWithNext().any { (a, b) -> a.end != b.start }) return null
        var lo = chip.start
        var hi = chip.end
        while (true) {
            val pl = pieces.firstOrNull { lo >= it.start && lo < it.end } ?: return null
            val ph = pieces.firstOrNull { hi > it.start && hi <= it.end } ?: return null
            val cl = chips.firstOrNull { lo >= it.start && lo < it.end } ?: return null
            val ch = chips.firstOrNull { hi > it.start && hi <= it.end } ?: return null
            val nlo = minOf(pl.start, cl.start)
            val nhi = maxOf(ph.end, ch.end)
            if (nlo == lo && nhi == hi) break
            lo = nlo
            hi = nhi
        }
        val first = chips.indexOfFirst { it.start == lo }
        val last = chips.indexOfFirst { it.end == hi }
        if (first < 0 || last < 0 || first < index - 1 || last > index + 1) return null
        val inside = pieces.filter { it.start >= lo && it.end <= hi }
        if (inside.isEmpty() || inside.any { it.output.isEmpty() }) return null
        val current = chips.subList(first, last + 1)
        if (inside.map { it.start to it.end } == current.map { it.start to it.end }) return null
        if (inside.joinToString("") { it.output } == current.joinToString("") { it.output }) return null
        if (!isCodePointBoundary(state.reading, lo) || inside.any { !isCodePointBoundary(state.reading, it.end) }) return null
        return SegmentAlternative(chip.start, chip.end, lo, hi, inside.map { it.copy(origin = Chip.Origin.RESIZED) })
    }

    /** 経路 [path]（読みの [offset] から）の語の境目がチップ [chip] の両端にそろえば、その範囲の表記。 */
    internal fun contextSurface(path: ChipPath, offset: Int, chip: Chip): String? {
        val inside = path.words.filter { it.start + offset >= chip.start && it.end + offset <= chip.end }
        if (inside.isEmpty() || inside.first().start + offset != chip.start || inside.last().end + offset != chip.end) return null
        if (inside.zipWithNext().any { (a, b) -> a.end != b.start }) return null
        return inside.joinToString("") { it.output }
    }

    /**
     * 区切りを変える候補を適用する（[applySegmentation] に区切りと表記を渡す。作ったチップは RESIZED = 明示選択として学習）。
     * 選択は元の選択チップの頭を含むチップ。今のチップの区切りと合わない（状態が変わった）なら null。
     */
    fun applyAlternative(state: ChipBarState, alt: SegmentAlternative): ChipBarState? {
        val bounds = state.boundaries
        if (alt.start !in bounds || alt.end !in bounds || alt.pieces.isEmpty()) return null
        if (alt.pieces.first().start != alt.start || alt.pieces.last().end != alt.end) return null
        if (alt.pieces.zipWithNext().any { (a, b) -> a.end != b.start }) return null
        val next = (bounds.filter { it <= alt.start || it >= alt.end } + alt.pieces.map { it.end }).toSortedSet()
        val select = next.filter { it < state.reading.length }.indexOfLast { it <= alt.chipStart }
        val surfaces = alt.pieces.associate { (it.start to it.end) to it.output }
        return applySegmentation(state, next, select, surfaces)
    }

    /** チップのタップ = 選択 / 選択解除（展開は閉じる）。 */
    fun tap(state: ChipBarState, index: Int): ChipBarState {
        if (index !in state.chips.indices) return state
        return state.copy(selected = if (state.selected == index && state.expanded < 0) -1 else index, expanded = -1)
    }

    /**
     * スペースキー: 選択なし → 先頭のチップを選ぶ、選択中 → 次のチップへ（末尾の次は先頭に戻る）。展開は閉じる。
     * 旧文節変換には入らない（チップ方式の間、スペースはチップの選択を進めるだけ）。チップが無ければ null。
     */
    fun spaceNext(state: ChipBarState): ChipBarState? {
        if (state.chips.isEmpty()) return null
        val next = if (state.selected in state.chips.indices) (state.selected + 1) % state.chips.size else 0
        return state.copy(selected = next, expanded = -1)
    }

    // ---- 打ち足し・後退でチップの編集を引き継ぐ ----

    /**
     * 読みが [old] から [fresh]（新しい読みの [initial]）に変わったとき、[old] で変えたチップ（境界操作・候補選択・
     * 全体候補で 1 位と違う表記）のうち、変わらなかった読みの先頭部分に収まる一番右のものまでを、その左のチップごと残す。
     * 残りは [fresh] のチップ（境界が合わなければ 1 位経路の語の境界で割る）、それも合わなければ [reconvert] のときだけ
     * 左文脈付きで再変換する。引き継ぐ編集が無い・[reconvert] = false で作れないときは null。
     */
    fun carryOver(old: ChipBarState, fresh: ChipBarState, reconvert: Boolean = true): ChipBarState? {
        val reading = fresh.reading
        if (old.reading == reading) return null
        val prefix = old.reading.commonPrefixWith(reading).length
        val last = old.chips.indexOfLast { it.end <= prefix && isChanged(old, it) }
        if (last < 0) return null
        val kept = old.chips.take(last + 1)
        val k = kept.last().end
        val tail: List<Chip> = when {
            k >= reading.length -> emptyList()
            fresh.chips.any { it.start == k } -> fresh.chips.filter { it.start >= k }
            else -> splitFreshAt(fresh, k) ?: if (reconvert) convertTail(reading, kept, k) else return null
        }
        return fresh.copy(
            chips = kept + tail,
            selected = -1,
            expanded = -1,
            remembered = old.remembered.filterKeys { (_, end) -> end <= k },
        )
    }

    /** [fresh] のチップ列を読みの位置 [k] から後ろだけにする（[k] が 1 位経路の語の頭でなければ null）。 */
    private fun splitFreshAt(fresh: ChipBarState, k: Int): List<Chip>? {
        val words = fresh.whole.primary?.words ?: return null
        if (words.none { it.start == k }) return null
        val i = fresh.chips.indexOfFirst { it.start < k && k < it.end }
        if (i < 0) return null
        val chip = fresh.chips[i]
        val inside = words.filter { it.start >= k && it.end <= chip.end }
        if (inside.isEmpty() || inside.last().end != chip.end) return null
        return listOf(Chip(k, chip.end, inside.joinToString("") { it.output }, chip.origin)) + fresh.chips.drop(i + 1)
    }

    /** 読みの [k] から後ろを [kept] を左文脈にして変換し、文節チップにする。 */
    private fun convertTail(reading: String, kept: List<Chip>, k: Int): List<Chip> {
        val rest = reading.substring(k)
        val conv = converter.convert(rest, leftContext(reading, kept, kept.size))
        return chipsOf(conv, conv.primary).map { it.copy(start = it.start + k, end = it.end + k) }
    }

    /** チップを変えたか（境界操作・候補選択、または 1 位経路の同じ範囲の表記と違う）。 */
    private fun isChanged(state: ChipBarState, chip: Chip): Boolean {
        if (chip.origin == Chip.Origin.PICKED || chip.origin == Chip.Origin.RESIZED) return true
        val words = state.whole.primary?.words ?: return false
        val inside = words.filter { it.start >= chip.start && it.end <= chip.end }
        if (inside.isEmpty() || inside.first().start != chip.start || inside.last().end != chip.end) return true
        return inside.joinToString("") { it.output } != chip.output
    }

    // ---- 切る（長押しで展開 → 文字の間をタップ） ----

    /** 長押し = そのチップをかな 1 文字ずつに展開（選択も兼ねる）。1 字のチップは切れないので null。 */
    fun longPress(state: ChipBarState, index: Int): ChipBarState? {
        val chip = state.chips.getOrNull(index) ?: return null
        if (BunsetsuRangeEditor.nextCodePointBoundary(state.reading, chip.start) >= chip.end) return null
        return state.copy(selected = index, expanded = index)
    }

    /** 展開中のチップで切れる位置（読み全体のオフセット、文字の間）。展開していなければ空。 */
    fun cutPositions(state: ChipBarState): List<Int> {
        val chip = state.chips.getOrNull(state.expanded) ?: return emptyList()
        val out = ArrayList<Int>()
        var p = BunsetsuRangeEditor.nextCodePointBoundary(state.reading, chip.start)
        while (p < chip.end) {
            out += p
            p = BunsetsuRangeEditor.nextCodePointBoundary(state.reading, p)
        }
        return out
    }

    /** 展開中のチップを読みの位置 [at] で 2 つに切る。両方を左文脈付きで再変換し、左の片を選択する。展開は閉じる。 */
    fun cutAt(state: ChipBarState, at: Int): ChipBarState? {
        val i = state.expanded
        if (at !in cutPositions(state)) return null
        val chip = state.chips[i]
        val memo = state.remembered + ((chip.start to chip.end) to chip)
        val chips = state.chips.toMutableList()
        chips[i] = reconverted(state.reading, memo, chips, i, chip.start, at)
        chips.add(i + 1, reconverted(state.reading, memo, chips, i + 1, at, chip.end))
        return state.copy(chips = chips, selected = i, expanded = -1, remembered = memo)
    }

    /**
     * 長押しで切る（View の切るカーソル）の始まり: チップ [index] をかな 1 文字ずつに展開する。選択は変えない
     * （取り消したとき元に戻るように）。1 字のチップは null。
     */
    fun expandForCut(state: ChipBarState, index: Int): ChipBarState? {
        val chip = state.chips.getOrNull(index) ?: return null
        if (BunsetsuRangeEditor.nextCodePointBoundary(state.reading, chip.start) >= chip.end) return null
        if (state.expanded == index) return null
        return state.copy(expanded = index)
    }

    /** 展開を閉じる（切るカーソルの取り消し）。展開していなければ null。 */
    fun collapse(state: ChipBarState): ChipBarState? =
        if (state.expanded < 0) null else state.copy(expanded = -1)

    /**
     * 長押しで即座に切る: チップ [index] を読みの位置 [at]（読み全体のオフセット、チップの中）で 2 つに切る
     * （展開を経ない 1 手。結果は [cutAt] と同じ）。
     */
    fun cutChip(state: ChipBarState, index: Int, at: Int): ChipBarState? {
        if (index !in state.chips.indices) return null
        return cutAt(state.copy(expanded = index), at)
    }

    // ---- 区切り編集（長押しで入る区切り編集モード。docs/CHIP_BAR.md「2026-11-02 区切り編集モード」） ----

    /**
     * 区切りをまとめて [boundaries]（読みの UTF-16 位置。0 と読みの長さ・範囲外・サロゲートペアの途中は無視）にする。
     * 前と同じ範囲のチップはそのまま残し、新しい範囲は前に同じ範囲のチップがあればその表記に戻し（remembered）、
     * 無ければ左文脈（左側のチップ。左から順に作るので、作ったばかりの片も含む）付きで 1 チップとして再変換する
     * （ユーザーが作った区切りなので自動では割らない）。消えたチップは remembered に残す。
     * 選択は [select]（null なら変わった一番左のチップ）。展開は閉じる。区切りが今と同じなら null。
     */
    fun applySegmentation(
        state: ChipBarState,
        boundaries: Set<Int>,
        select: Int? = null,
        surfaces: Map<Pair<Int, Int>, String> = emptyMap(),
    ): ChipBarState? {
        val reading = state.reading
        if (reading.isEmpty()) return null
        val cuts = boundaries.filter { it in 1 until reading.length && isCodePointBoundary(reading, it) }.sorted()
        val bounds = listOf(0) + cuts + reading.length
        if (bounds == state.boundaries && surfaces.none { (k, v) -> state.chips.none { it.start to it.end == k && it.output == v } }) {
            return null
        }
        val memo = state.remembered + state.chips.map { (it.start to it.end) to it }
        val existing = state.chips.associateBy { it.start to it.end }
        val chips = ArrayList<Chip>(bounds.size - 1)
        var firstChanged = -1
        for ((s, e) in bounds.zipWithNext()) {
            val keep = existing[s to e]
            val forced = surfaces[s to e]?.takeIf { it.isNotEmpty() }
            if (forced != null && keep?.output != forced) {
                // 区切りを変える候補で選んだ表記（変換済み）
                if (firstChanged < 0) firstChanged = chips.size
                chips += Chip(s, e, forced, Chip.Origin.RESIZED)
            } else if (keep != null) {
                chips += keep
            } else {
                if (firstChanged < 0) firstChanged = chips.size
                chips += reconverted(reading, memo, chips, chips.size, s, e)
            }
        }
        val selected = (select ?: firstChanged).takeIf { it in chips.indices } ?: -1
        return state.copy(chips = chips, selected = selected, expanded = -1, remembered = memo)
    }

    // ---- くっつける（チップ間タップ / スワイプ） ----

    /** チップ [index] と右隣をくっつけて 1 チップにし、左文脈付きで再変換して選択する。末尾チップは null。 */
    fun join(state: ChipBarState, index: Int): ChipBarState? {
        val a = state.chips.getOrNull(index) ?: return null
        val b = state.chips.getOrNull(index + 1) ?: return null
        val memo = state.remembered + listOf(a, b).map { (it.start to it.end) to it }
        val chips = state.chips.toMutableList()
        chips.removeAt(index + 1)
        chips[index] = reconverted(state.reading, memo, chips, index, a.start, b.end)
        return state.copy(chips = chips, selected = index, expanded = -1, remembered = memo)
    }

    // ---- ずらす（チップ端の左右フリック） ----

    /** 右フリック = チップを 1 字伸ばす（右隣が 1 字縮む。空になれば消える）。末尾チップは何もしない。 */
    fun flickRight(state: ChipBarState, index: Int): ChipBarState? {
        val chips = state.chips
        if (index !in chips.indices || index == chips.lastIndex) return null
        val newEnd = BunsetsuRangeEditor.nextCodePointBoundary(state.reading, chips[index].end)
        return moveBoundary(state, index, newEnd)
    }

    /** 左フリック = チップを 1 字縮める（右隣が 1 字伸びる。末尾チップなら新しいチップができる）。 */
    fun flickLeft(state: ChipBarState, index: Int): ChipBarState? {
        val chips = state.chips
        if (index !in chips.indices) return null
        val chip = chips[index]
        val newEnd = BunsetsuRangeEditor.previousCodePointBoundary(state.reading, chip.end)
        if (newEnd <= chip.start) return null
        return moveBoundary(state, index, newEnd)
    }

    /**
     * 区切りドラッグ: チップ [index] と右隣の境界を [delta] 字（コードポイント）動かす（正 = 右へ）。
     * 両側に 1 字以上残らない・動かない・末尾チップは null。
     */
    fun shiftBoundary(state: ChipBarState, index: Int, delta: Int): ChipBarState? {
        val chips = state.chips
        if (index !in chips.indices || index == chips.lastIndex || delta == 0) return null
        val chip = chips[index]
        val next = chips[index + 1]
        var end = chip.end
        repeat(kotlin.math.abs(delta)) {
            end = if (delta > 0) BunsetsuRangeEditor.nextCodePointBoundary(state.reading, end)
            else BunsetsuRangeEditor.previousCodePointBoundary(state.reading, end)
        }
        if (end <= chip.start || end >= next.end || end == chip.end) return null
        return moveBoundary(state, index, end)
    }

    private fun moveBoundary(state: ChipBarState, index: Int, newEnd: Int): ChipBarState {
        val chips = state.chips.toMutableList()
        val chip = chips[index]
        val next = chips.getOrNull(index + 1)
        val nextEnd = next?.end ?: state.reading.length
        val memo = state.remembered + listOfNotNull(chip, next).map { (it.start to it.end) to it }
        chips[index] = reconverted(state.reading, memo, chips, index, chip.start, newEnd)
        when {
            next == null -> chips += reconverted(state.reading, memo, chips, index + 1, newEnd, nextEnd)
            newEnd >= nextEnd -> chips.removeAt(index + 1)
            else -> chips[index + 1] = reconverted(state.reading, memo, chips, index + 1, newEnd, nextEnd)
        }
        return state.copy(chips = chips, selected = index, expanded = -1, remembered = memo)
    }

    /**
     * 範囲 [start, end) のチップ（[index] 番目に置く）。前に同じ範囲のチップがあればその表記に戻し、
     * 無ければ左文脈（[chips] の [index] より左）付きで再変換。
     */
    private fun reconverted(
        reading: String,
        memo: Map<Pair<Int, Int>, Chip>,
        chips: List<Chip>,
        index: Int,
        start: Int,
        end: Int,
    ): Chip {
        memo[start to end]?.let { return it }
        val r = reading.substring(start, end)
        val ctx = leftContext(reading, chips, index)
        return Chip(start, end, converter.convert(r, ctx).primary?.output ?: r, Chip.Origin.RESIZED)
    }

    /** チップ [index] より左のチップの表記と読み。 */
    private fun leftContext(reading: String, chips: List<Chip>, index: Int): ChipContext {
        val left = chips.take(index.coerceIn(0, chips.size))
        if (left.isEmpty()) return ChipContext.EMPTY
        return ChipContext(left.joinToString("") { it.output }, reading.substring(0, left.last().end))
    }

    // ---- 候補 ----

    /** 選択チップの表記を [text]（漢字候補・かな）にする。 */
    fun pickCandidate(state: ChipBarState, text: String): ChipBarState? {
        val i = state.selected
        if (i !in state.chips.indices || text.isEmpty()) return null
        val chips = state.chips.toMutableList()
        chips[i] = chips[i].copy(output = text, origin = Chip.Origin.PICKED)
        return state.copy(chips = chips, selected = afterPick(i, chips.size), expanded = -1)
    }

    /** 予測行の読み全体の別候補を選ぶ（その経路の文節チップに置き換える）。 */
    fun pickFullAlternative(state: ChipBarState, text: String): ChipBarState? {
        val path = state.whole.paths.firstOrNull { it.output == text } ?: return null
        return state.copy(chips = chipsOf(state.whole, path), selected = -1, expanded = -1, remembered = emptyMap())
    }

    // ---- 入力欄への反映（純粋関数） ----

    /** 入力欄の composing 文字列（チップ表記の連結）と選択チップの span。 */
    fun composing(state: ChipBarState): ComposingView {
        val text = state.text
        val i = state.selected
        if (i !in state.chips.indices) return ComposingView(text)
        val start = state.chips.take(i).sumOf { it.output.length }
        return ComposingView(text, start, start + state.chips[i].output.length)
    }

    /** 確定時に入力欄へ commit する文字列。下段は予測に戻る（呼び出し側で状態を捨てる）。 */
    fun commit(state: ChipBarState): String = state.text

    private fun afterPick(index: Int, size: Int): Int =
        if (config.advanceAfterPick) (if (index + 1 < size) index + 1 else -1) else index

    companion object {
        /** くっつけた読みの変換で見る経路の数（[refineLowerRow]）。 */
        private const val MERGE_PATHS = 2

        /** [at] が文字（コードポイント）の境目か（サロゲートペアの途中でない）。 */
        internal fun isCodePointBoundary(s: String, at: Int): Boolean =
            at <= 0 || at >= s.length || !(s[at - 1].isHighSurrogate() && s[at].isLowSurrogate())

        /** 自立語の頭（漢字・カタカナ・英数で始まる語）で区切ったグループ。付属語は前に付く。 */
        internal fun contentGroups(words: List<ChipWord>): List<List<ChipWord>> {
            val groups = ArrayList<MutableList<ChipWord>>()
            for (w in words) {
                if (groups.isEmpty() || isContentStart(w.output)) groups += mutableListOf(w) else groups.last() += w
            }
            return groups
        }

        private fun isContentStart(s: String): Boolean {
            val c = s.firstOrNull() ?: return false
            return Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN || c in 'ァ'..'ヺ' ||
                c.isLetterOrDigit() && c.code < 0x3000 || c == '々'
        }

        fun toKatakana(s: String): String = buildString(s.length) {
            for (c in s) append(if (c in 'ぁ'..'ゖ') c + 0x60 else c)
        }
    }
}

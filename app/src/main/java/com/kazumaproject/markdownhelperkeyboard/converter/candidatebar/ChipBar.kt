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

    data class ChipCandidates(
        val chipIndex: Int,
        val candidates: List<String>,
        val kana: List<String>,
    ) : LowerRow {
        val all: List<String> get() = (candidates + kana).distinct()
    }
}

data class ChipBarConfig(
    /** これより長い（読みの UTF-16 長）チップは 1 位経路の自立語の頭で割る。 */
    val maxChipReading: Int = 6,
    val maxCandidates: Int = 8,
    val maxFullAlternatives: Int = 8,
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
        return LowerRow.ChipCandidates(i, candidates, kana)
    }

    /** チップのタップ = 選択 / 選択解除（展開は閉じる）。 */
    fun tap(state: ChipBarState, index: Int): ChipBarState {
        if (index !in state.chips.indices) return state
        return state.copy(selected = if (state.selected == index && state.expanded < 0) -1 else index, expanded = -1)
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
     * 長押しで即座に切る: チップ [index] を読みの位置 [at]（読み全体のオフセット、チップの中）で 2 つに切る
     * （展開を経ない 1 手。結果は [cutAt] と同じ）。
     */
    fun cutChip(state: ChipBarState, index: Int, at: Int): ChipBarState? {
        if (index !in state.chips.indices) return null
        return cutAt(state.copy(expanded = index), at)
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

package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment

/*
 * 文節チップ UI の中身（View なしのモデル）。docs/CHIP_BAR.md 参照。
 *
 * 上段 = 文節チップ列、下段 = 普段は予測、チップをタップしたときだけそのチップの候補
 * （漢字候補・かな・区切り候補）。チップの左右フリックで区切りを 1 字ずらし、隣のチップだけ再変換する。
 * エンジンは [ChipConverter] で注入する（テストでは表、評価ハーネスでは実エンジン）。
 */

/** 1 語（ラティスのノード）。オフセットは変換した読みの UTF-16 位置。 */
data class ChipWord(val start: Int, val end: Int, val output: String)

/** 読み全体を覆う 1 本の経路。[bunsetsuSplits] は文節の内部区切り（Sumire の split pattern）。 */
data class ChipPath(
    val output: String,
    val words: List<ChipWord>,
    val bunsetsuSplits: List<Int>,
) {
    /** 読み全体を 1 語で覆い、漢字を含まない（ひらがな・カタカナ・半角カナ・英数など文字種変換）経路。 */
    fun isWholeScriptConversion(readingLength: Int): Boolean =
        words.size == 1 && words[0].start == 0 && words[0].end == readingLength &&
            output.none { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN || it == '々' }
}

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

    /** 区切り・揺れの判定に使う経路（文全体を 1 語にした文字種変換を除く）。 */
    val segmentedPaths: List<ChipPath> get() = paths.filterNot { it.isWholeScriptConversion(reading.length) }

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
    enum class Origin { BUNSETSU, WORD_SPLIT, RESIZED, PICKED }

    val length: Int get() = end - start
}

/** 区切り候補: 選択チップを含むチップ firstChip から lastChip までを pieces で置き換える。 */
data class SplitAlternative(val firstChip: Int, val lastChip: Int, val pieces: List<Chip>) {
    val label: String get() = pieces.joinToString("|") { it.output }
}

/** 予測行に混ぜる「いちばん怪しいチップ」の候補。選ぶとそのチップだけ置き換わる。 */
data class ChipPick(val chipIndex: Int, val text: String)

sealed interface LowerRow {
    /**
     * 普段の下段（予測）。モデルは予測を持たない。読み全体の別候補と、怪しいチップの候補
     * [chipPicks]（UI は予測行の先頭付近に並べる）だけ渡す。
     */
    data class Predictions(
        val fullAlternatives: List<String>,
        val chipPicks: List<ChipPick> = emptyList(),
    ) : LowerRow

    data class ChipCandidates(
        val chipIndex: Int,
        val candidates: List<String>,
        val kana: List<String>,
        val splitAlternatives: List<SplitAlternative>,
    ) : LowerRow {
        val all: List<String> get() = (candidates + kana).distinct()
    }
}

data class ChipBarConfig(
    /** これより長い（読みの UTF-16 長）チップは自立語の頭で割る。 */
    val maxChipReading: Int = 6,
    /** 2・3 位の経路がチップの両端で区切らない（区切り自体が揺れている）チップも割る。 */
    val splitLowConfidence: Boolean = true,
    val maxCandidates: Int = 8,
    val maxFullAlternatives: Int = 8,
    val maxSplitAlternatives: Int = 5,
    /** 区切り候補が置き換えるチップ数の上限。 */
    val maxSplitSpanChips: Int = 3,
    /** 予測行に混ぜる怪しいチップの候補数（0 = 混ぜない）。 */
    val maxChipPicks: Int = 3,
    /** N-best の区切りでチップを割るとき、割った片の読みの最小長（では → で|は のような割り方を避ける）。 */
    val minSplitPiece: Int = 2,
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
    /** 入力全体の変換（区切り候補と予測行の別候補の元）。 */
    val whole: ChipConversion,
    /** フリックで消えたチップ（範囲 → チップ）。区切りを戻したとき元の表記に戻す用。 */
    val remembered: Map<Pair<Int, Int>, Chip> = emptyMap(),
) {
    val text: String get() = chips.joinToString("") { it.output }
    val boundaries: List<Int> get() = listOf(0) + chips.map { it.end }
}

class ChipBarModel(
    private val converter: ChipConverter,
    private val config: ChipBarConfig = ChipBarConfig.DEFAULT,
) {

    fun initial(reading: String): ChipBarState {
        val whole = converter.convert(reading)
        return ChipBarState(reading, chipsOf(whole, whole.primary, config.splitLowConfidence), -1, whole)
    }

    /** 読み全体の経路を文節チップにする（長い・揺れているチップは自立語の頭で割る）。 */
    internal fun chipsOf(whole: ChipConversion, path: ChipPath?, lowConfidence: Boolean): List<Chip> {
        val reading = whole.reading
        if (path == null || reading.isEmpty()) return listOf(Chip(0, reading.length, reading))
        val wordStarts = path.words.mapTo(HashSet()) { it.start }
        val cuts = path.bunsetsuSplits.filter { it in 1 until reading.length && it in wordStarts }
        val bounds = listOf(0) + cuts + reading.length
        val rivals = rivals(whole, path)
        val out = ArrayList<Chip>()
        for ((s, e) in bounds.zipWithNext()) {
            val words = path.words.filter { it.start >= s && it.end <= e }
            val output = words.joinToString("") { it.output }
            val fragile = lowConfidence && rivals.any { r -> BunsetsuAnalyzer.outputForRange(r.words.toSegments(), s, e) == null }
            if (e - s > config.maxChipReading || fragile) {
                val groups = rivalGroups(words, rivals, s, e).takeIf { it.size > 1 } ?: contentGroups(words)
                if (groups.size > 1) {
                    groups.forEach { g -> out += Chip(g.first().start, g.last().end, g.joinToString("") { it.output }, Chip.Origin.WORD_SPLIT) }
                    continue
                }
            }
            out += Chip(s, e, output)
        }
        return out
    }

    /** 2・3 位の経路（文字種変換と [path] と同じ出力を除く）。 */
    private fun rivals(whole: ChipConversion, path: ChipPath): List<ChipPath> =
        whole.paths.drop(1).filter { it.output != path.output && !it.isWholeScriptConversion(whole.reading.length) }.take(2)

    /**
     * N-best の 2・3 位経路の文節区切り（splitPattern 由来の bunsetsuSplits）で [s, e) の語を割る。
     * 1 位の語頭に当たる区切りだけ使い、片が [ChipBarConfig.minSplitPiece] より短くなる割り方はしない。
     */
    private fun rivalGroups(words: List<ChipWord>, rivals: List<ChipPath>, s: Int, e: Int): List<List<ChipWord>> {
        if (words.isEmpty()) return emptyList()
        val starts = words.mapTo(HashSet()) { it.start }
        val cuts = rivals.flatMap { it.bunsetsuSplits }.filter { it in (s + 1) until e && it in starts }.distinct().sorted()
        if (cuts.isEmpty()) return emptyList()
        val bounds = listOf(s) + cuts + e
        if (bounds.zipWithNext().any { (a, b) -> b - a < config.minSplitPiece }) return emptyList()
        return bounds.zipWithNext().map { (a, b) -> words.filter { it.start >= a && it.end <= b } }
    }

    /**
     * いちばん怪しいチップ（2・3 位経路で区切りが揺れる > 表記が揺れる > 割ったり伸縮したりした低信頼）と
     * その候補（2・3 位経路のその範囲の表記 → そのチップ単独の変換）。候補が無いチップは飛ばす。
     */
    internal fun chipPicks(state: ChipBarState): List<ChipPick> {
        if (config.maxChipPicks <= 0 || state.chips.size < 2) return emptyList()
        val primary = state.whole.primary ?: return emptyList()
        val rivals = rivals(state.whole, primary)
        val scored = state.chips.mapIndexed { i, chip ->
            val outs = rivals.map { r -> BunsetsuAnalyzer.outputForRange(r.words.toSegments(), chip.start, chip.end) }
            var score = outs.count { it == null } * 3 + outs.count { it != null && it != chip.output } * 2
            if (chip.origin == Chip.Origin.WORD_SPLIT || chip.origin == Chip.Origin.RESIZED) score += 1
            Triple(i, score, outs.filterNotNull())
        }.filter { it.second > 0 }.sortedWith(compareByDescending<Triple<Int, Int, List<String>>> { it.second }.thenBy { it.first })
        for ((i, _, rivalOutputs) in scored) {
            val chip = state.chips[i]
            val r = state.reading.substring(chip.start, chip.end)
            val own = converter.convert(r, leftContext(state.reading, state.chips, i)).paths.map { it.output }
            val picks = (rivalOutputs + own).distinct().filter { it != chip.output && it.isNotEmpty() }.take(config.maxChipPicks)
            if (picks.isNotEmpty()) return picks.map { ChipPick(i, it) }
        }
        return emptyList()
    }

    /** 予測行の怪しいチップ候補を選ぶ（そのチップだけ置き換え、選択はしない）。 */
    fun pickChipPick(state: ChipBarState, pick: ChipPick): ChipBarState? {
        if (pick.chipIndex !in state.chips.indices || pick.text.isEmpty()) return null
        val chips = state.chips.toMutableList()
        chips[pick.chipIndex] = chips[pick.chipIndex].copy(output = pick.text, origin = Chip.Origin.PICKED)
        return state.copy(chips = chips, selected = -1)
    }

    fun lowerRow(state: ChipBarState): LowerRow {
        val i = state.selected
        if (i !in state.chips.indices) {
            return LowerRow.Predictions(
                state.whole.paths.drop(1).map { it.output }.filter { it != state.text }.distinct().take(config.maxFullAlternatives),
                chipPicks(state),
            )
        }
        val chip = state.chips[i]
        val chipReading = state.reading.substring(chip.start, chip.end)
        val conv = converter.convert(chipReading, leftContext(state.reading, state.chips, i))
        val candidates = (listOf(chip.output) + conv.paths.map { it.output }).distinct().take(config.maxCandidates + 1)
        val kana = listOf(chipReading, toKatakana(chipReading)).distinct().filter { it !in candidates }
        return LowerRow.ChipCandidates(i, candidates, kana, splitAlternatives(state, i))
    }

    /** N-best の別の区切り（選択チップを含む範囲で、経路の語境界が両端に来るまでチップを広げる）。 */
    internal fun splitAlternatives(state: ChipBarState, index: Int): List<SplitAlternative> {
        val chips = state.chips
        val out = LinkedHashMap<String, SplitAlternative>()
        for (path in state.whole.segmentedPaths) {
            val starts = path.words.mapTo(HashSet()) { it.start }
            val ends = path.words.mapTo(HashSet()) { it.end }
            var a = index
            var b = index
            var guard = 0
            while (guard++ < chips.size) {
                val sOk = chips[a].start == 0 || chips[a].start in starts
                val eOk = chips[b].end == state.reading.length || chips[b].end in ends
                if (sOk && eOk) break
                if (!sOk) a--
                if (!eOk) b++
                if (a < 0 || b > chips.lastIndex) break
            }
            if (a < 0 || b > chips.lastIndex || b - a + 1 > config.maxSplitSpanChips) continue
            val s = chips[a].start
            val e = chips[b].end
            val words = path.words.filter { it.start >= s && it.end <= e }
            if (words.isEmpty()) continue
            val cuts = path.bunsetsuSplits.filter { it in (s + 1) until e && it in starts }
            val pieces = (listOf(s) + cuts + e).zipWithNext().flatMap { (ps, pe) ->
                val w = words.filter { it.start >= ps && it.end <= pe }
                val groups = if (pe - ps > config.maxChipReading) contentGroups(w) else listOf(w)
                groups.map { g -> Chip(g.first().start, g.last().end, g.joinToString("") { it.output }, Chip.Origin.PICKED) }
            }
            val current = chips.subList(a, b + 1)
            val sameBounds = pieces.map { it.start to it.end } == current.map { it.start to it.end }
            // 区切りが同じで表記だけ違うものは漢字候補の役目。
            if (sameBounds) continue
            val alt = SplitAlternative(a, b, pieces)
            out.putIfAbsent("$a/$b/${alt.label}", alt)
            if (out.size >= config.maxSplitAlternatives) break
        }
        return out.values.toList()
    }

    fun tap(state: ChipBarState, index: Int): ChipBarState {
        if (index !in state.chips.indices) return state
        return state.copy(selected = if (state.selected == index) -1 else index)
    }

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

    private fun moveBoundary(state: ChipBarState, index: Int, newEnd: Int): ChipBarState {
        val chips = state.chips.toMutableList()
        val chip = chips[index]
        val next = chips.getOrNull(index + 1)
        val nextEnd = next?.end ?: state.reading.length
        val memo = state.remembered + listOfNotNull(chip, next).map { (it.start to it.end) to it }
        chips[index] = reconverted(state, memo, chips, index, chip.start, newEnd)
        when {
            next == null -> chips += reconverted(state, memo, chips, index + 1, newEnd, nextEnd)
            newEnd >= nextEnd -> chips.removeAt(index + 1)
            else -> chips[index + 1] = reconverted(state, memo, chips, index + 1, newEnd, nextEnd)
        }
        return state.copy(chips = chips, selected = index, remembered = memo)
    }

    /** 範囲 [start, end) のチップ。前に同じ範囲のチップがあればその表記に戻し、無ければ左文脈付きで再変換。 */
    private fun reconverted(
        state: ChipBarState,
        memo: Map<Pair<Int, Int>, Chip>,
        chips: List<Chip>,
        index: Int,
        start: Int,
        end: Int,
    ): Chip {
        memo[start to end]?.let { return it }
        val r = state.reading.substring(start, end)
        val ctx = leftContext(state.reading, chips, index)
        return Chip(start, end, converter.convert(r, ctx).primary?.output ?: r, Chip.Origin.RESIZED)
    }

    /** チップ [index] より左のチップの表記と読み。 */
    private fun leftContext(reading: String, chips: List<Chip>, index: Int): ChipContext {
        val left = chips.take(index.coerceIn(0, chips.size))
        if (left.isEmpty()) return ChipContext.EMPTY
        return ChipContext(left.joinToString("") { it.output }, reading.substring(0, left.last().end))
    }

    /** 選択チップの表記を [text]（漢字候補・かな）にする。 */
    fun pickCandidate(state: ChipBarState, text: String): ChipBarState? {
        val i = state.selected
        if (i !in state.chips.indices || text.isEmpty()) return null
        val chips = state.chips.toMutableList()
        chips[i] = chips[i].copy(output = text, origin = Chip.Origin.PICKED)
        return state.copy(chips = chips, selected = afterPick(i, chips.size))
    }

    fun pickSplitAlternative(state: ChipBarState, alt: SplitAlternative): ChipBarState? {
        if (alt.firstChip !in state.chips.indices || alt.lastChip !in state.chips.indices) return null
        val chips = state.chips.subList(0, alt.firstChip) + alt.pieces + state.chips.subList(alt.lastChip + 1, state.chips.size)
        val sel = alt.firstChip + alt.pieces.lastIndex
        return state.copy(chips = chips, selected = afterPick(sel, chips.size))
    }

    /** 予測行の読み全体の別候補を選ぶ（その経路の文節チップに置き換える）。 */
    fun pickFullAlternative(state: ChipBarState, text: String): ChipBarState? {
        val path = state.whole.paths.firstOrNull { it.output == text } ?: return null
        return state.copy(chips = chipsOf(state.whole, path, lowConfidence = false), selected = -1)
    }

    /** 確定。下段は予測に戻る（呼び出し側で状態を捨てる）。 */
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

        private fun List<ChipWord>.toSegments() = map { CandidateConversionSegment(it.start, it.end, it.output) }
    }
}

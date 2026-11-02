package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.learning.session.LearningFragment

/*
 * 文節チップ候補欄の「状態 → 表示データ」（View・IMEService なしの純粋関数）。docs/CHIP_BAR.md の「組み込み」参照。
 *
 * 上段 = [ChipStripData]（チップの表記・選択・展開中ならかな 1 文字ずつ）。
 * 下段 = 予測（IMEService が作った [Candidate]）か、選択チップの候補（漢字 → かな）。チップの候補と読み全体の別候補は
 * 専用の type を付けた [Candidate] にして既存の候補アダプタにそのまま載せ、タップで区別する。
 */

/** 下段のチップ候補（タップ = 選択チップの表記を差し替える。確定はしない）。 */
const val CANDIDATE_TYPE_CHIP_CANDIDATE: Byte = 60

/** 下段の読み全体の別候補（タップ = その経路の文節チップに置き換える。確定はしない）。 */
const val CANDIDATE_TYPE_CHIP_FULL_ALTERNATIVE: Byte = 61

/**
 * 上段の 1 チップの表示。[reading] = チップの読み（区切り編集モードで読み全体をかな 1 文字ずつ並べる元。
 * 全チップの [reading] を繋げると読み全体、各チップの終わりが今の区切り）。
 * [letters] が空でなければ展開中（モデルの [ChipBarModel.longPress] / [ChipBarModel.expandForCut]。View からは使わなくなった）。
 */
data class ChipStripItem(
    val label: String,
    val selected: Boolean,
    val letters: List<String> = emptyList(),
    val reading: String = "",
) {
    val expanded: Boolean get() = letters.isNotEmpty()
}

data class ChipStripData(val items: List<ChipStripItem>) {
    val expandedIndex: Int get() = items.indexOfFirst { it.expanded }
    val selectedIndex: Int get() = items.indexOfFirst { it.selected }

    companion object {
        val EMPTY = ChipStripData(emptyList())
    }
}

/**
 * 確定時に学習へ渡す 1 文節（チップ 1 つ）。[explicit] = 境界操作か候補選択で変えたチップ。
 * [learn] = 学習辞書に書く（どこかのチップを変えた文は、変えていないチップも書く。次に同じ読みを打ったとき文全体と
 * 各チップの区切りを再現するため。何も変えていなければ書かない）。
 */
data class ChipLearningFragment(
    val reading: String,
    val output: String,
    val explicit: Boolean,
    val learn: Boolean = explicit,
)

/** 読み全体の変換を既に持っているとき（候補欄の更新で取ったもの）、同じ読み・文脈なしはそれを返す。 */
class SeededChipConverter(private val seed: ChipConversion, private val base: ChipConverter) : ChipConverter {
    override fun convert(reading: String, context: ChipContext): ChipConversion =
        if (reading == seed.reading && context == ChipContext.EMPTY) seed else base.convert(reading, context)
}

object ChipBarPresenter {

    /** 予測行で読み全体の別候補を差し込む位置（予測の先頭 [slot] 個の後ろ）。 */
    const val FULL_ALTERNATIVE_SLOT = 3

    fun strip(state: ChipBarState?): ChipStripData {
        if (state == null || state.chips.isEmpty()) return ChipStripData.EMPTY
        return ChipStripData(
            state.chips.mapIndexed { i, chip ->
                ChipStripItem(
                    label = chip.output,
                    selected = i == state.selected,
                    letters = if (i == state.expanded) letters(state.reading, chip) else emptyList(),
                    reading = state.reading.substring(chip.start, chip.end),
                )
            },
        )
    }

    /** チップの読みを 1 文字（コードポイント）ずつ。 */
    fun letters(reading: String, chip: Chip): List<String> {
        val out = ArrayList<String>()
        var p = chip.start
        while (p < chip.end) {
            val next = BunsetsuRangeEditor.nextCodePointBoundary(reading, p).coerceAtMost(chip.end)
            if (next <= p) break
            out += reading.substring(p, next)
            p = next
        }
        return out
    }

    /**
     * 展開中のチップで、文字 [gapIndex] と [gapIndex]+1 の間の読み全体のオフセット（[ChipBarModel.cutAt] に渡す）。
     * 範囲外・展開なしは null。
     */
    fun cutOffset(state: ChipBarState, gapIndex: Int): Int? {
        val chip = state.chips.getOrNull(state.expanded) ?: return null
        if (gapIndex < 0) return null
        var p = chip.start
        repeat(gapIndex + 1) { p = BunsetsuRangeEditor.nextCodePointBoundary(state.reading, p) }
        return p.takeIf { it > chip.start && it < chip.end }
    }

    /**
     * 下段に出す候補。予測のときは [predictions] の先頭 [FULL_ALTERNATIVE_SLOT] 個の後ろに読み全体の別候補
     * （予測に同じ表記が無いものだけ）を差し込む。チップ選択中はそのチップの候補（漢字 → かな）だけ。
     */
    fun lowerRow(state: ChipBarState, row: LowerRow, predictions: List<Candidate>): List<Candidate> = when (row) {
        is LowerRow.ChipCandidates -> {
            val chip = state.chips.getOrNull(row.chipIndex)
            if (chip == null) predictions else {
                val reading = state.reading.substring(chip.start, chip.end)
                row.all.filter { it.isNotEmpty() }.map { text ->
                    Candidate(
                        string = text,
                        type = CANDIDATE_TYPE_CHIP_CANDIDATE,
                        length = reading.length.coerceAtMost(255).toUByte(),
                        score = 0,
                        yomi = reading,
                    )
                }
            }
        }

        is LowerRow.Predictions -> {
            val shown = predictions.mapTo(HashSet()) { it.string }
            val alternatives = row.fullAlternatives.filter { it.isNotEmpty() && it !in shown }.map { text ->
                Candidate(
                    string = text,
                    type = CANDIDATE_TYPE_CHIP_FULL_ALTERNATIVE,
                    length = state.reading.length.coerceAtMost(255).toUByte(),
                    score = 0,
                    yomi = state.reading,
                )
            }
            predictions.take(FULL_ALTERNATIVE_SLOT) + alternatives + predictions.drop(FULL_ALTERNATIVE_SLOT)
        }
    }

    fun isChipCandidate(candidate: Candidate): Boolean =
        candidate.type == CANDIDATE_TYPE_CHIP_CANDIDATE || candidate.type == CANDIDATE_TYPE_CHIP_FULL_ALTERNATIVE

    /** チップの表記を変えたか（境界操作・候補選択・全体候補の選択）。選択だけなら false。 */
    fun isEdited(state: ChipBarState): Boolean =
        state.chips.any { it.origin == Chip.Origin.PICKED || it.origin == Chip.Origin.RESIZED } ||
            state.whole.primary?.output?.let { it != state.text } == true

    /** ユーザーがチップに触ったか（選択・展開・編集）。触った後は入力欄をチップの表記で上書きし、ライブ変換で戻さない。 */
    fun isTouched(state: ChipBarState): Boolean = state.selected >= 0 || state.expanded >= 0 || isEdited(state)

    /**
     * 確定時の文節ごとの学習（チップ = 文節）。境界操作・候補選択で変えたチップと、読み全体の別候補から来て
     * 1 位経路の同じ範囲の表記と違うチップを明示選択にする。
     */
    fun learningFragments(state: ChipBarState): List<ChipLearningFragment> {
        val edited = isEdited(state)
        return state.chips.map { chip ->
            val explicit = chip.origin == Chip.Origin.PICKED || chip.origin == Chip.Origin.RESIZED ||
                primaryOutput(state, chip.start, chip.end) != chip.output
            ChipLearningFragment(
                reading = state.reading.substring(chip.start, chip.end),
                output = chip.output,
                explicit = explicit,
                learn = explicit || edited,
            )
        }
    }

    /** 確定時に学習セッションへ渡す文節（IMEService と再現テストが同じものを使う）。 */
    fun learningSessionFragments(state: ChipBarState, candidateScore: Int): List<LearningFragment> =
        learningFragments(state).map { fragment ->
            LearningFragment(
                reading = fragment.reading,
                output = fragment.output,
                candidateScore = candidateScore,
                candidateIndex = if (fragment.learn) 1 else 0,
                explicitlySelected = fragment.explicit,
            )
        }

    /** 1 位経路で読みの範囲 [start, end) を覆う語の表記（語の境界と合わなければ null）。 */
    private fun primaryOutput(state: ChipBarState, start: Int, end: Int): String? {
        val words = state.whole.primary?.words ?: return null
        val inside = words.filter { it.start >= start && it.end <= end }
        if (inside.isEmpty() || inside.first().start != start || inside.last().end != end) return null
        if (inside.zipWithNext().any { (a, b) -> a.end != b.start }) return null
        return inside.joinToString("") { it.output }
    }

    /**
     * 候補欄の更新で取った変換から読み全体の [ChipConversion] を作る。[primary] = 上段に出す 1 位（学習・リランカーで
     * 差し替わったものを含む）。その文節区切りはエンジンの split pattern、無ければ語分割の自立語の頭で決める。
     */
    fun wholeConversion(
        reading: String,
        primary: Candidate?,
        conversion: List<Candidate>,
        segmentsByString: Map<String, List<CandidateConversionSegment>>,
        splitPatternByCandidateString: Map<String, List<Int>>,
    ): ChipConversion {
        val ordered = listOfNotNull(primary?.takeIf { it.length.toInt() == reading.length }) + conversion
        val head = ordered.firstOrNull { it.length.toInt() == reading.length && it.string.isNotEmpty() }
        val primarySplits = head?.let { c ->
            splitPatternByCandidateString[c.string] ?: contentSplits(
                c.conversionSegments.ifEmpty { segmentsByString[c.string].orEmpty() },
            )
        }.orEmpty()
        return ChipConversion.fromEngine(
            reading = reading,
            candidates = ordered,
            segmentsByString = segmentsByString,
            splitPatternByCandidateString = splitPatternByCandidateString,
            primarySplits = primarySplits,
        )
    }

    /** 語分割を自立語の頭でまとめたときの区切り（文節区切りの代わり）。 */
    fun contentSplits(segments: List<CandidateConversionSegment>): List<Int> {
        if (segments.isEmpty()) return emptyList()
        val words = segments.map { ChipWord(it.inputStart, it.inputEnd, it.output) }
        return ChipBarModel.contentGroups(words).drop(1).map { it.first().start }
    }
}

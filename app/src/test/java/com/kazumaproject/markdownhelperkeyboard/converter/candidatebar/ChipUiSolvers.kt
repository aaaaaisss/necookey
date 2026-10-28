package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import java.util.PriorityQueue

/**
 * 正解文に届く最少操作数（評価用、JVM のみ）。どちらも一様コスト探索で上限 [maxMoves] 手。
 * 確定（最後のコミット）は両 UI 共通なので数えない。
 *
 * 枝刈り: 正解の先頭と一致しない最初のチップ（文節）を f とし、f と f-1 に関わる操作だけを試す
 * （直す順番は手数に影響しないので左から直すと決めてよい）。候補選択は正解の該当位置と一致するものだけ。
 */
data class SolveResult(val moves: Int?, val plan: List<String>) {
    val reached: Boolean get() = moves != null
}

private class Node<S>(val state: S, val cost: Int, val plan: List<String>)

private fun <S> uniformCost(
    start: S,
    maxMoves: Int,
    key: (S) -> Any,
    isGoal: (S) -> Boolean,
    expand: (S, Int) -> List<Triple<S, Int, String>>,
): SolveResult {
    val pq = PriorityQueue<Node<S>>(compareBy { it.cost })
    pq += Node(start, 0, emptyList())
    val best = HashMap<Any, Int>()
    var popped = 0
    while (pq.isNotEmpty()) {
        val n = pq.poll()
        if (isGoal(n.state)) return SolveResult(n.cost, n.plan)
        val k = key(n.state)
        if ((best[k] ?: Int.MAX_VALUE) < n.cost) continue
        if (++popped > 20_000) break
        for ((s, c, label) in expand(n.state, n.cost)) {
            val cost = n.cost + c
            if (cost > maxMoves) continue
            val sk = key(s)
            if ((best[sk] ?: Int.MAX_VALUE) <= cost) continue
            best[sk] = cost
            pq += Node(s, cost, n.plan + label)
        }
    }
    return SolveResult(null, emptyList())
}

/** チップ列のうち正解 [gold] の先頭と食い違う最初のチップと、その出力位置。全部一致なら index = size。 */
internal fun firstWrong(outputs: List<String>, gold: String): Pair<Int, Int> {
    var pos = 0
    for ((i, o) in outputs.withIndex()) {
        if (!gold.startsWith(o, pos) || (i == outputs.lastIndex && pos + o.length != gold.length)) return i to pos
        pos += o.length
    }
    return outputs.size to pos
}

/** 新 UI（文節チップ）。 */
/** [useFullAlternatives] = 予測行の読み全体の別候補を 1 手で選べるとみなす。 */
class NewUiSolver(
    private val model: ChipBarModel,
    private val maxMoves: Int = 6,
    private val useFullAlternatives: Boolean = true,
) {
    fun solve(reading: String, gold: String): SolveResult {
        val start = model.initial(reading)
        return uniformCost(
            start, maxMoves,
            key = { s -> s.chips.map { Triple(it.start, it.end, it.output) } to s.selected },
            isGoal = { it.text == gold },
        ) { s, cost ->
            val out = ArrayList<Triple<ChipBarState, Int, String>>()
            if (cost == 0 && useFullAlternatives) {
                (model.lowerRow(s) as? LowerRow.Predictions)?.fullAlternatives?.forEach { alt ->
                    model.pickFullAlternative(s, alt)?.let { out += Triple(it, 1, "予測行で「$alt」") }
                }
            }
            val (f, pos) = firstWrong(s.chips.map { it.output }, gold)
            if (f >= s.chips.size) return@uniformCost out
            if (s.selected != f) out += Triple(model.tap(s, f), 1, "タップ#$f「${s.chips[f].output}」")
            else {
                val row = model.lowerRow(s) as LowerRow.ChipCandidates
                val last = f == s.chips.lastIndex
                for (c in row.all) {
                    if (c == s.chips[f].output || !gold.startsWith(c, pos) || (last && pos + c.length != gold.length)) continue
                    model.pickCandidate(s, c)?.let { out += Triple(it, 1, "候補「$c」") }
                }
                for (alt in row.splitAlternatives) {
                    model.pickSplitAlternative(s, alt)?.let { out += Triple(it, 1, "区切り候補「${alt.label}」") }
                }
            }
            for (i in listOf(f - 1, f)) {
                if (i < 0) continue
                model.flickRight(s, i)?.let { out += Triple(it, 1, "右フリック#$i") }
                model.flickLeft(s, i)?.let { out += Triple(it, 1, "左フリック#$i") }
            }
            out
        }
    }
}

/**
 * 現行 UI の推定。
 *  - 変換前: 上段 = 1 位 + 最初の文節の別候補（選ぶとその文節だけ確定、残りが新しい入力）、読み全体の別候補（予測行にある前提、1 手）。
 *  - 変換ボタン（1 手）で文節セッション: フォーカス移動（長押し矢印、1 手）、フォーカス文節の伸縮（矢印、1 手。
 *    残り全体を再変換）、区切りパターン切替（1 手）、候補タップ（1 手。フォーカスまで確定し残りは新しい入力に戻る）、
 *    スペースで候補送り（k 位まで k 手、確定しない）。
 */
class CurrentUiSolver(
    private val converter: ChipConverter,
    private val maxMoves: Int = 6,
    private val maxCandidates: Int = 8,
    private val maxFullAlternatives: Int = 8,
    private val useFullAlternatives: Boolean = true,
) {
    private data class Seg(val start: Int, val end: Int, val output: String)

    private sealed interface St {
        data class Raw(val offset: Int, val committed: Int) : St
        data class Session(val offset: Int, val committed: Int, val segs: List<Seg>, val focus: Int, val pattern: Int) : St
        object Done : St
    }

    private fun bunsetsuSegs(conv: ChipConversion, path: ChipPath?, splits: List<Int>): List<Seg> {
        val r = conv.reading
        val bounds = listOf(0) + splits.filter { it in 1 until r.length }.distinct().sorted() + r.length
        val chosen = conv.paths.firstOrNull { p -> bounds.all { b -> b == 0 || p.words.any { it.end == b } } } ?: path
        return bounds.zipWithNext().map { (s, e) ->
            val ws = chosen?.words?.filter { it.start >= s && it.end <= e }.orEmpty()
            val ok = ws.isNotEmpty() && ws.first().start == s && ws.last().end == e
            Seg(s, e, if (ok) ws.joinToString("") { it.output } else r.substring(s, e))
        }
    }

    private fun primarySegs(conv: ChipConversion): List<Seg> {
        val p = conv.primary ?: return listOf(Seg(0, conv.reading.length, conv.reading))
        val starts = p.words.mapTo(HashSet()) { it.start }
        return bunsetsuSegs(conv, p, p.bunsetsuSplits.filter { it in starts })
    }

    private fun patterns(conv: ChipConversion): List<List<Int>> =
        conv.paths.map { p -> p.bunsetsuSplits }.distinct()

    fun solve(reading: String, gold: String): SolveResult = uniformCost<St>(
        St.Raw(0, 0), maxMoves,
        key = { it },
        isGoal = { it == St.Done },
    ) { st, _ ->
        val out = ArrayList<Triple<St, Int, String>>()
        when (st) {
            St.Done -> Unit
            is St.Raw -> {
                val r = reading.substring(st.offset)
                val g = gold.substring(st.committed)
                if (r.isEmpty()) {
                    if (g.isEmpty()) out += Triple(St.Done, 0, "確定")
                    return@uniformCost out
                }
                val conv = converter.convert(r)
                if (conv.primary?.output == g) out += Triple(St.Done, 0, "確定")
                conv.paths.drop(1).take(if (useFullAlternatives) maxFullAlternatives else 0).firstOrNull { it.output == g }?.let {
                    out += Triple(St.Done, 1, "全体候補「${it.output}」")
                }
                val p = conv.primary
                val firstEnd = p?.bunsetsuSplits?.firstOrNull { s -> s in 1 until r.length && p.words.any { it.start == s } }
                if (p != null && firstEnd != null) {
                    val alts = LinkedHashSet<String>()
                    conv.paths.drop(1).forEach { q ->
                        BunsetsuAnalyzer.outputForRange(q.words.map { CandidateConversionSegment(it.start, it.end, it.output) }, 0, firstEnd)?.let { alts += it }
                    }
                    conv.partials.filter { it.end == firstEnd }.forEach { alts += it.output }
                    for (o in alts) if (g.startsWith(o)) out += Triple(St.Raw(st.offset + firstEnd, st.committed + o.length), 1, "上段の文節候補「$o」")
                }
                out += Triple(St.Session(st.offset, st.committed, primarySegs(conv), 0, 0), 1, "変換")
            }
            is St.Session -> {
                val r = reading.substring(st.offset)
                val g = gold.substring(st.committed)
                val (f, pos) = firstWrong(st.segs.map { it.output }, g)
                if (f >= st.segs.size) {
                    out += Triple(St.Done, 0, "確定")
                    return@uniformCost out
                }
                if (st.focus < f) out += Triple(st.copy(focus = st.focus + 1), 1, "フォーカス→")
                if (st.focus > f - 1 && st.focus > 0) out += Triple(st.copy(focus = st.focus - 1), 1, "フォーカス←")
                if (st.focus == f || st.focus == f - 1) {
                    for (d in listOf(-1, 1)) {
                        val bounds = listOf(0) + st.segs.map { it.end }
                        val rz = BunsetsuRangeEditor.resizeFocused(r, bounds, st.focus, d) ?: continue
                        val fr = r.substring(rz.focusedStart, rz.focusedEnd)
                        val focused = Seg(rz.focusedStart, rz.focusedEnd, converter.convert(fr).primary?.output ?: fr)
                        val rest = if (rz.hasRemainder) {
                            primarySegs(converter.convert(r.substring(rz.remainderStart))).map {
                                Seg(it.start + rz.remainderStart, it.end + rz.remainderStart, it.output)
                            }
                        } else emptyList()
                        out += Triple(st.copy(segs = st.segs.take(st.focus) + focused + rest), 1, if (d < 0) "文節縮め" else "文節伸ばし")
                    }
                }
                val pats = patterns(converter.convert(r))
                if (pats.size > 1) {
                    val next = (st.pattern + 1) % pats.size
                    val segs = bunsetsuSegs(converter.convert(r), converter.convert(r).primary, pats[next])
                    out += Triple(st.copy(segs = segs, pattern = next, focus = st.focus.coerceAtMost(segs.lastIndex)), 1, "区切りパターン切替")
                }
                if (st.focus == f) {
                    val seg = st.segs[f]
                    val sr = r.substring(seg.start, seg.end)
                    val list = (listOf(seg.output) + converter.convert(sr).paths.map { it.output }).distinct().take(maxCandidates + 1)
                    val last = f == st.segs.lastIndex
                    list.forEachIndexed { k, c ->
                        if (k == 0 || !g.startsWith(c, pos) || (last && pos + c.length != g.length)) return@forEachIndexed
                        out += Triple(St.Raw(st.offset + seg.end, st.committed + pos + c.length), 1, "候補「$c」(確定)")
                        val segs = st.segs.toMutableList().also { it[f] = seg.copy(output = c) }
                        out += Triple(st.copy(segs = segs), k, "スペース${k}回「$c」")
                    }
                }
            }
        }
        out
    }
}

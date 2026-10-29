package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment

/**
 * ツギハギ抑制（かな修復候補）: 1 位の経路に「短くて低頻度の漢字・カタカナ語」が 2 つ以上続く区間（地位|トマチン|祭、胃炎|買っ、
 * 痛く|注が）があれば、その区間だけ読みのひらがなに戻した候補を [position] 位に入れる（1 = 1 位に、2 = 2 位に、0 = 無効）。
 * 区切りを誤った漢字片より、区間がかなのままの候補のほうが直しやすい（漢字片は文節ごと選び直しになる）。
 * 既定は 1 位・語コスト 3000 以上・読み 4 字以下（2026-10-29、exp-overnight の実験 3。179 lenient 158→160・致命 4→3、
 * 新規 206 文 lenient 144→152、lenient 壊れ 0。exact では 明日→あした のような漢字→かなが出るが、目標「日常で困らない変換」では
 * 劣化扱いしない）。以前は 2 位・4000・3 字（1 位は変えない）。
 *
 * 片（piece）の条件: 読み [maxPieceYomi] 字以下・語コスト（辞書コスト、Node.score）[minWordCost] 以上・表記に漢字かカタカナを含む・
 * 名詞,数（2043..2055）でない・ユーザー/学習辞書の語でない。
 * 以前の連続漢字ペナルティ（KanjiRunPenalty、辺コストに足す）は正しい漢語複合（海軍+将校）を壊し、正解経路に辞書外の語が要る
 * 文には効かなかった。ここでは経路探索は変えず、1 位の区間をかなに戻す候補を足すだけにする（正解の漢字が辞書に無くても直る）。
 */
internal object TsugihagiKanaRepair {
    const val DEFAULT_POSITION = 1
    const val DEFAULT_MIN_WORD_COST = 3000
    const val DEFAULT_MAX_PIECE_YOMI = 4

    @Volatile
    @JvmStatic
    var position: Int = DEFAULT_POSITION

    @Volatile
    @JvmStatic
    var minWordCost: Int = DEFAULT_MIN_WORD_COST

    @Volatile
    @JvmStatic
    var maxPieceYomi: Int = DEFAULT_MAX_PIECE_YOMI

    /** 区間の片の最小数。 */
    @Volatile
    @JvmStatic
    var minRun: Int = 2

    private const val NUMBER_FIRST = 2043
    private const val NUMBER_LAST = 2055

    private fun isKanjiOrKatakana(c: Char) = c in '\u4E00'..'\u9FFF' || c == '々' || c in '\u30A1'..'\u30FA'
    private fun isHiragana(c: Char) = c in '\u3041'..'\u3096' || c == 'ー'

    fun isPiece(node: Node): Boolean {
        val y = node.yomiUsed
        if (y.isEmpty() || y.length > maxPieceYomi || node.len.toInt() != y.length) return false
        if (node.score < minWordCost) return false
        if (node.l.toInt() in NUMBER_FIRST..NUMBER_LAST) return false
        if (node.candidateSource != CandidateSource.SYSTEM && node.candidateSource != CandidateSource.UNKNOWN) return false
        if (!y.all { isHiragana(it) }) return false
        return node.tango.any { isKanjiOrKatakana(it) }
    }

    /** 経路（BOS/EOS を除く語の並び）の修復結果: かなに戻した文字列と語分割。片の区間が無ければ null。 */
    fun repair(path: List<Node>): Pair<String, List<CandidateConversionSegment>>? {
        if (position <= 0 || path.size < minRun) return null
        val piece = BooleanArray(path.size) { isPiece(path[it]) }
        val kana = BooleanArray(path.size)
        var i = 0
        var any = false
        while (i < path.size) {
            if (!piece[i]) { i++; continue }
            var j = i
            while (j < path.size && piece[j]) j++
            if (j - i >= minRun) {
                for (k in i until j) kana[k] = true
                any = true
            }
            i = j
        }
        if (!any) return null
        val sb = StringBuilder()
        val segs = ArrayList<CandidateConversionSegment>(path.size)
        var pos = 0
        var k = 0
        while (k < path.size) {
            if (kana[k]) {
                val start = pos
                val y = StringBuilder()
                while (k < path.size && kana[k]) { y.append(path[k].yomiUsed); pos += path[k].len.toInt(); k++ }
                sb.append(y)
                segs.add(CandidateConversionSegment(inputStart = start, inputEnd = pos, output = y.toString()))
            } else {
                val n = path[k]
                sb.append(n.tango)
                segs.add(CandidateConversionSegment(inputStart = pos, inputEnd = pos + n.len.toInt(), output = n.tango))
                pos += n.len.toInt(); k++
            }
        }
        return sb.toString() to segs
    }

    /**
     * [ranked] の 1 位が [bestString]（経路 [bestPath]）のとき、修復候補を [position] 位に入れる（同じ文字列が既にあればそれを動かす）。
     * 修復候補のスコアは入れた位置の直前の候補＋1（1 位に入れるときは 1 位−1）。
     */
    fun apply(
        ranked: List<Candidate>,
        bestString: String?,
        bestPath: List<Node>?,
        segmentCollector: MutableMap<String, List<CandidateConversionSegment>>?,
    ): List<Candidate> {
        if (position <= 0 || bestPath == null || ranked.isEmpty() || ranked[0].string != bestString) return ranked
        // 学習辞書の語を含む 1 位（ユーザーが直した変換）はかなに戻さない。
        if (ranked[0].type == CANDIDATE_TYPE_LEARNED_DICTIONARY) return ranked
        val (s, segs) = repair(bestPath) ?: return ranked
        val top = ranked[0]
        val rest = ranked.filter { it.string != s }
        val at = (position - 1).coerceIn(0, rest.size)
        // 呼び出し側（KanaKanjiEngine）が語のスコアで並べ直すので、位置はスコアで決める（1 位なら 1 位より 1 小さく、それ以外は直前より 1 大きく）
        val score = if (at == 0) top.score - 1 else rest[at - 1].score + 1
        val cand = top.copy(string = s, score = score, commitText = s, conversionSegments = segs)
        segmentCollector?.set(s, segs)
        return ArrayList<Candidate>(rest.size + 1).apply { addAll(rest); add(at, cand) }
    }
}

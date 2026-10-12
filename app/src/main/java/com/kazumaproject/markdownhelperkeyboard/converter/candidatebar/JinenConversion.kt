package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import java.text.Normalizer

/**
 * jinen-v2（togatogah/jinen-v2-small）の自由生成まわりの純 Kotlin 部分。
 *
 * - プロンプト: `\uEE02` + 左文脈（あれば） + `\uEE00` + 読み（カタカナ） + `\uEE01` を丸ごと NFKC。
 *   BOS なし・空白の全角化なし・貪欲・EOS で停止（ブリッジの generateRaw）。読みはカタカナでないと壊れる
 *   （オフライン評価: ひらがなは 0%）。
 * - jinen は NFKC で学習しているので出力は半角英数・半角記号。アプリの表記（Sumire の慣習）に戻す
 *   （オフライン評価 zg/finalists.md の R1/R2/R3、zg/fin/rest.py と同じ規則）:
 *   1. Sumire の N-best に NFKC が一致する候補があればそれ（表層と文節経路をそのまま使う）。
 *   2. 幅違いの変種のどれかが Sumire の読み全体の完全一致経路になればそれ（getConstrainedBestCandidate exact）。
 *   3. それ以外は記号表で全角に戻し、読み全体で 1 文節として出す（かなを読みに当てて整合を確かめる。
 *      かなが読みと合わない出力は捨てる）。
 */
object JinenConversion {
    private const val INPUT_TAG = '\uEE00'
    private const val OUTPUT_TAG = '\uEE01'
    private const val LEFT_CONTEXT_TAG = '\uEE02'
    private const val PROTOCOL_FIRST = '\uEE00'
    private const val PROTOCOL_LAST = '\uEE0F'

    /** 生成トークン数の上限 = 読みの長さ + これ（オフライン評価は 48 固定）。 */
    const val EXTRA_TOKENS = 8
    const val MAX_TOKENS = 96

    /** jinen に渡す読みの長さの上限（zenz の 32 文字制限は使わない。自由生成は長文に強い）。 */
    const val MAX_INPUT_LENGTH = 64

    enum class Via { SUMIRE_NBEST, SUMIRE_PATH, SYMBOL_TABLE }

    data class Restored(val candidate: Candidate, val via: Via)

    fun nfkc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)

    /** ひらがな（ぁ〜ゖ、ゝゞ）をカタカナに。他の文字はそのまま。 */
    fun toKatakana(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in '\u3041'..'\u3096' || c == '\u309D' || c == '\u309E') c + 0x60 else c)
    }

    private fun toHiragana(c: Char): Char =
        if (c in '\u30A1'..'\u30F6' || c == '\u30FD' || c == '\u30FE') c - 0x60 else c

    /** プロトコル記号（U+EE00〜EE0F）と制御文字を除く（文脈がプロンプトの文法を変えないように）。 */
    fun sanitize(text: String): String = buildString(text.length) {
        for (c in text) {
            if (c in PROTOCOL_FIRST..PROTOCOL_LAST) continue
            if (c < ' ' || c == '\u007F') continue
            append(c)
        }
    }

    fun buildPrompt(leftContext: String, readingHiragana: String): String {
        val left = sanitize(leftContext)
        val reading = sanitize(toKatakana(readingHiragana))
        val raw = buildString {
            if (left.isNotEmpty()) append(LEFT_CONTEXT_TAG).append(left)
            append(INPUT_TAG).append(reading).append(OUTPUT_TAG)
        }
        return nfkc(raw)
    }

    fun maxTokensFor(reading: String): Int = (reading.length + EXTRA_TOKENS).coerceAtMost(MAX_TOKENS)

    // ---- 幅の復元（R3 の記号表と R2 の変種） ----

    /** Sumire の慣習: 数字・英字・% . , - / は半角、それ以外の ASCII 記号と空白は全角。 */
    private val SYMBOLS: Map<Char, Char> = mapOf(
        '!' to '！', '?' to '？', '(' to '（', ')' to '）', '~' to '～', ':' to '：', ';' to '；',
        ' ' to '\u3000', '"' to '”', '[' to '［', ']' to '］', '&' to '＆', '*' to '＊', '<' to '＜',
        '>' to '＞', '=' to '＝', '+' to '＋', '#' to '＃', '@' to '＠', '^' to '＾', '|' to '｜',
        '\\' to '＼', '{' to '｛', '}' to '｝', '\'' to '’', '`' to '‘', '$' to '＄',
    )

    fun symbolRestore(text: String): String = buildString(text.length) {
        for (c in text) append(SYMBOLS[c] ?: c)
    }

    private fun fullWidth(c: Char): Char = when (c) {
        in '!'..'~' -> c + 0xFEE0
        ' ' -> '\u3000'
        else -> c
    }

    private fun isAsciiDigit(c: Char) = c in '0'..'9'
    private fun isAsciiLetter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    /** R2 で Sumire の完全一致経路を探す変種（重複なし、この順）: [慣習, そのまま, 全角, 数字だけ全角, 英字だけ全角]。 */
    fun widthVariants(output: String): List<String> {
        val conv = symbolRestore(output)
        val all = output.map(::fullWidth).joinToString("")
        val digits = conv.map { if (isAsciiDigit(it)) fullWidth(it) else it }.joinToString("")
        val latin = conv.map { if (isAsciiLetter(it)) fullWidth(it) else it }.joinToString("")
        return listOf(conv, output, all, digits, latin).distinct()
    }

    // ---- 読みとの整合（R3 の 1 文節フォールバック用） ----

    data class Alignment(
        /** 漢字・数字・英字の連なりとかな・記号の連なりごとの読み範囲。 */
        val segments: List<CandidateConversionSegment>,
        /** 当て方が 1 通りに決まったか（2 通り以上あれば false。先頭から短く取った 1 つを返す）。 */
        val unique: Boolean,
    )

    private fun isKana(c: Char): Boolean =
        c in '\u3041'..'\u309F' || c in '\u30A0'..'\u30FF'

    private fun isIdeograph(c: Char): Boolean =
        c in '\u4E00'..'\u9FFF' || c in '\u3400'..'\u4DBF' || c == '々' || c == '〆' || c == 'ヶ'

    private fun isAnchor(c: Char): Boolean {
        val n = nfkc(c.toString()).firstOrNull() ?: c
        if (isIdeograph(n)) return false
        return isKana(n) || (!n.isLetterOrDigit() && !isIdeograph(n))
    }

    private fun sameReadingChar(a: Char, b: Char): Boolean {
        val x = nfkc(a.toString())
        val y = nfkc(b.toString())
        if (x.length != 1 || y.length != 1) return x == y
        return toHiragana(x[0]) == toHiragana(y[0])
    }

    /**
     * かな・記号を読みの同じ文字に当て、漢字・数字・英字の連なりにはそれぞれ 1 文字以上の読みを当てる
     * （zg/fin/fin.py の charalign）。当てられなければ null。
     */
    fun align(output: String, reading: String): Alignment? {
        if (output.isEmpty() || reading.isEmpty()) return null
        // トークン: (開始, 終了, かな/記号か)
        data class Tok(val start: Int, val end: Int, val anchor: Boolean)
        val toks = ArrayList<Tok>()
        for ((i, c) in output.withIndex()) {
            val anchor = isAnchor(c)
            val last = toks.lastOrNull()
            if (!anchor && last != null && !last.anchor) {
                toks[toks.lastIndex] = last.copy(end = i + 1)
            } else {
                toks.add(Tok(i, i + 1, anchor))
            }
        }
        val n = toks.size
        val m = reading.length
        // ways[i][j]: toks[i..] を reading[j..] に当てる方法の数（2 で頭打ち）。
        val ways = Array(n + 1) { IntArray(m + 1) }
        ways[n][m] = 1
        for (i in n - 1 downTo 0) {
            val t = toks[i]
            for (j in m downTo 0) {
                ways[i][j] = if (t.anchor) {
                    if (j < m && sameReadingChar(output[t.start], reading[j])) ways[i + 1][j + 1] else 0
                } else {
                    var w = 0
                    for (e in j + 1..m) {
                        w += ways[i + 1][e]
                        if (w >= 2) break
                    }
                    minOf(w, 2)
                }
            }
        }
        if (ways[0][0] == 0) return null
        // 1 つ復元する（漢字の連なりは短い読みから）。かなの連なりは 1 つの範囲にまとめる。
        val spans = ArrayList<Triple<Int, Int, Tok>>()
        var j = 0
        for (i in 0 until n) {
            val t = toks[i]
            val e = if (t.anchor) j + 1 else (j + 1..m).first { ways[i + 1][it] > 0 }
            spans.add(Triple(j, e, t))
            j = e
        }
        val segments = ArrayList<CandidateConversionSegment>()
        var k = 0
        while (k < spans.size) {
            val (s, e0, t0) = spans[k]
            var e = e0
            var outEnd = t0.end
            if (t0.anchor) {
                while (k + 1 < spans.size && spans[k + 1].third.anchor) {
                    k++
                    e = spans[k].second
                    outEnd = spans[k].third.end
                }
            }
            segments.add(CandidateConversionSegment(s, e, output.substring(t0.start, outEnd)))
            k++
        }
        return Alignment(segments, unique = ways[0][0] == 1)
    }

    // ---- 復元の本体 ----

    /** R1: 読み全体を覆う Sumire 候補のうち、NFKC が jinen の出力と一致する最初のもの。 */
    fun findNfkcMatch(output: String, input: String, candidates: List<Candidate>): Candidate? {
        val target = nfkc(output)
        return candidates.firstOrNull { it.length.toInt() == input.length && nfkc(it.string) == target }
    }

    /**
     * jinen の出力 [output] をアプリの表記と文節経路に戻す。使えない出力（空・読みと合わない）なら null。
     * [constrainedExact] は読み全体がその表層になる Sumire の経路（なければ null）。
     */
    suspend fun restore(
        output: String,
        input: String,
        sumireCandidates: List<Candidate>,
        fallbackType: Byte,
        fallbackScore: Int,
        constrainedExact: suspend (String) -> Candidate?,
    ): Restored? {
        if (output.isBlank() || input.isEmpty() || input.length > 255) return null
        findNfkcMatch(output, input, sumireCandidates)?.let { return Restored(it, Via.SUMIRE_NBEST) }
        for (variant in widthVariants(output)) {
            val path = constrainedExact(variant)
            if (path != null && path.string == variant) return Restored(path, Via.SUMIRE_PATH)
        }
        val restored = symbolRestore(output)
        align(restored, input) ?: return null
        return Restored(
            Candidate(
                string = restored,
                type = fallbackType,
                length = input.length.toUByte(),
                score = fallbackScore,
                yomi = input,
                conversionSegments = listOf(CandidateConversionSegment(0, input.length, restored)),
            ),
            Via.SYMBOL_TABLE,
        )
    }
}

package com.kazumaproject.markdownhelperkeyboard.converter.graph

/**
 * 画面文脈の語の集合（2026-11-15、ラティスの文脈ノード加点 A1 と画面のカタカナ語の一時辞書 A2）。
 *
 * 文脈の層（ScreenContextStore の EDITOR / COMMIT / SCREEN と他アプリの確定）の文字列から、**文脈が変わったときに 1 回だけ**作る
 * （変換ごとには作らない）。メモリだけで保存しない。期限は元の層と同じ（[expiresAt] を過ぎたら作り直す）。
 *
 *  - [words]: 漢字・カタカナの連続（2 字以上）の長さ 2..[MAX_SUBSTRING] の全部分文字列 → 層のビット。
 *    ラティスは入力の読みから作れる (読み, 表記) を全部並べるので、ノードの表記をここで引けば「画面の語を読みに逆引きして
 *    語ごと加点」と同じ（逆引き辞書は不要、候補の語分割にも左右されない）。1 字の漢字・ひらがなだけの語は入らない。
 *  - [stems]: 「漢字 + ひらがな」の漢字の部分（溜める → 溜）→ 層のビット。表記が 漢字 + ひらがな のノード（活用語）だけに、半分の加点。
 *  - [katakana]: カタカナだけの連続（[MIN_KATAKANA] 字以上）。読み = ひらがなに直したもの。ラティスのノードと前方一致の予測に使う。
 *
 * 加点 B（コスト単位、大きいほど強い）は層の最大: 最近の確定 [B_COMMIT]・入力欄の前後 [B_EDITOR]・画面 [B_SCREEN]・他アプリの確定 [B_OTHER]。
 * 参考: ラティス LM の同音の差 川に/革に ≈780、丸め/丸目 ≈510、時期/次期 ≈700（ctx-learning-review-1115）。辞書の語コストは 3000〜8000。
 */
class ContextLexicon private constructor(
    val revision: Long,
    val expiresAt: Long,
    private val words: HashMap<String, Int>,
    private val stems: HashMap<String, Int>,
    /** カタカナ語（読みの先頭の字ごと）。 */
    private val katakanaByFirst: HashMap<Char, MutableList<KanaWord>>,
) {
    /** 画面のカタカナ語 [surface]（読み [reading]、層のビット [layers]）。 */
    class KanaWord(val reading: String, val surface: String, val layers: Int)

    val isEmpty: Boolean get() = words.isEmpty() && stems.isEmpty()

    val size: Int get() = words.size

    /** 表記 [tango] のノードに足す加点（コスト単位、0 = なし）。 */
    fun nodeBonus(tango: String): Int {
        if (tango.length >= 2) {
            words[tango]?.let { return bonusOf(it) }
        }
        val stem = stemOf(tango) ?: return 0
        val m = stems[stem] ?: return 0
        return bonusOf(m) / 2
    }

    /** 表記 [tango] そのものが文脈の語（漢字・カタカナの連続の部分文字列）にあるときの加点（活用語の漢字部の半分の加点は含まない）。 */
    fun wordBonus(tango: String): Int = if (tango.length >= 2) words[tango]?.let { bonusOf(it) } ?: 0 else 0

    /** 層ごとの語の数（部分文字列の集合のうち、その層のビットを持つもの。設定の状態表示用）。 */
    fun layerCounts(): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        for (m in words.values) for (b in intArrayOf(LAYER_EDITOR, LAYER_COMMIT, LAYER_SCREEN, LAYER_OTHER_COMMIT)) {
            if (m and b != 0) out[b] = (out[b] ?: 0) + 1
        }
        return out
    }

    /** 表記 [text] の語（漢字・カタカナの連続、または活用語の漢字部）がどの層にあるか（ビット）。記録の ctx 列用。 */
    fun layersOf(text: String): Int = layersOf(runsOf(text), stemRunsOf(text))

    /** 語 [runs]（漢字・カタカナの連続）と漢字部 [stemList] がどの層にあるか（ビット）。 */
    fun layersOf(runs: Collection<String>, stemList: Collection<String>): Int {
        var m = 0
        for (run in runs) {
            val w = words[run]
            if (w != null) { m = m or w; continue }
            // 9 字以上のカタカナ語は部分文字列の集合に入らないので、カタカナ語の一覧で引く
            val r = toHiragana(run) ?: continue
            katakanaByFirst[r[0]]?.firstOrNull { it.surface == run }?.let { m = m or it.layers }
        }
        for (s in stemList) m = m or (stems[s] ?: 0)
        return m
    }

    /** [str] の位置 [start] から読みが一致するカタカナ語。 */
    fun katakanaAt(str: String, start: Int): List<KanaWord> {
        val list = katakanaByFirst[str[start]] ?: return emptyList()
        return list.filter { str.regionMatches(start, it.reading, 0, it.reading.length) }
    }

    /** 読みが [prefix] で始まり、[prefix] より長いカタカナ語（前方一致の予測）。 */
    fun katakanaCompletions(prefix: String): List<KanaWord> {
        if (prefix.isEmpty()) return emptyList()
        val list = katakanaByFirst[prefix[0]] ?: return emptyList()
        return list.filter { it.reading.length > prefix.length && it.reading.startsWith(prefix) }
    }

    companion object {
        const val LAYER_EDITOR = 1
        const val LAYER_COMMIT = 2
        const val LAYER_SCREEN = 4
        const val LAYER_OTHER_COMMIT = 8

        const val B_COMMIT = 1500
        const val B_EDITOR = 1200
        const val B_SCREEN = 1000
        const val B_OTHER = 800

        const val MAX_SUBSTRING = 8
        const val MIN_KATAKANA = 3
        /** 1 回の作り直しで入れる部分文字列の上限（画面 6000 字でも数万件まで）。 */
        const val MAX_WORDS = 40_000
        /** カタカナ語の一時辞書のノードコスト（加点の前。辞書のカタカナ語並み）。 */
        const val KATAKANA_NODE_COST = 5000
        /** 名詞,一般（id.def 1851）。 */
        const val KATAKANA_NODE_POS: Short = 1851

        /** 変換で使う文脈（IMEService が入れる。入力欄が使えない・文脈なしは null、テストは null）。 */
        @Volatile
        @JvmStatic
        var provider: (() -> ContextLexicon?)? = null

        /** いま使う文脈（[provider] が無い・失敗したら null）。 */
        fun current(): ContextLexicon? = try {
            provider?.invoke()?.takeIf { !it.isEmpty }
        } catch (_: Exception) {
            null
        }

        fun bonusOf(layers: Int): Int {
            var b = 0
            if (layers and LAYER_COMMIT != 0) b = maxOf(b, B_COMMIT)
            if (layers and LAYER_EDITOR != 0) b = maxOf(b, B_EDITOR)
            if (layers and LAYER_SCREEN != 0) b = maxOf(b, B_SCREEN)
            if (layers and LAYER_OTHER_COMMIT != 0) b = maxOf(b, B_OTHER)
            return b
        }

        /**
         * 層ごとの文字列から作る。[texts] = 層のビット → 文字列（改行区切りでよい）。[screenStems] は画面の層の漢字部
         * （画面の層は語だけを残しているので、元の文字列から取った漢字部を別に渡す）。
         */
        fun build(revision: Long, expiresAt: Long, texts: Map<Int, String>, screenStems: Collection<String> = emptyList()): ContextLexicon {
            val words = HashMap<String, Int>()
            val stems = HashMap<String, Int>()
            val kata = LinkedHashMap<String, Int>()
            for ((layer, text) in texts) {
                if (text.isEmpty()) continue
                for (run in runsOf(text)) {
                    val n = run.length
                    for (i in 0 until n) {
                        if (run[i] == 'ー') continue
                        for (j in i + 2..minOf(n, i + MAX_SUBSTRING)) {
                            val s = run.substring(i, j)
                            // カタカナだけの部分はカタカナの並び全体のときだけ（カワサキ の カワ で かわ → カワ にしない）
                            if (s.all(::isKatakana) &&
                                ((i > 0 && isKatakana(run[i - 1])) || (j < n && isKatakana(run[j])))
                            ) continue
                            val prev = words[s]
                            if (prev == null && words.size >= MAX_WORDS) continue
                            words[s] = (prev ?: 0) or layer
                        }
                    }
                    for (k in katakanaRunsOf(run)) kata[k] = (kata[k] ?: 0) or layer
                }
                for (s in stemRunsOf(text)) stems[s] = (stems[s] ?: 0) or layer
            }
            for (s in screenStems) if (s.isNotEmpty()) stems[s] = (stems[s] ?: 0) or LAYER_SCREEN
            val byFirst = HashMap<Char, MutableList<KanaWord>>()
            for ((surface, layers) in kata) {
                val reading = toHiragana(surface) ?: continue
                byFirst.getOrPut(reading[0]) { ArrayList() }.add(KanaWord(reading, surface, layers))
            }
            return ContextLexicon(revision, expiresAt, words, stems, byFirst)
        }

        internal fun isKanji(c: Char): Boolean = c in '\u4E00'..'\u9FFF' || c == '々'
        internal fun isKatakana(c: Char): Boolean = c in 'ァ'..'ヺ' || c == 'ー'
        private fun isHiragana(c: Char): Boolean = c in 'ぁ'..'ゖ'

        /** 漢字・カタカナ（ー 含む）の 2 字以上の連続。 */
        fun runsOf(text: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (!(isKanji(c) || (isKatakana(c) && c != 'ー'))) { i++; continue }
                var j = i + 1
                while (j < text.length && (isKanji(text[j]) || isKatakana(text[j]))) j++
                if (j - i >= 2) out.add(text.substring(i, j))
                i = j
            }
            return out
        }

        /** [run] の中のカタカナだけの連続（[MIN_KATAKANA] 字以上、ー だけは除く）。 */
        fun katakanaRunsOf(run: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            while (i < run.length) {
                if (!isKatakana(run[i]) || run[i] == 'ー') { i++; continue }
                var j = i + 1
                while (j < run.length && isKatakana(run[j])) j++
                if (j - i >= MIN_KATAKANA) out.add(run.substring(i, j))
                i = j
            }
            return out
        }

        /** 漢字の並びのすぐあとにひらがなが続くものの漢字の部分（溜める → 溜）。 */
        fun stemRunsOf(text: String): Set<String> {
            val out = LinkedHashSet<String>()
            var i = 0
            while (i < text.length) {
                if (!isKanji(text[i])) { i++; continue }
                var j = i + 1
                while (j < text.length && isKanji(text[j])) j++
                if (j < text.length && isHiragana(text[j])) out.add(text.substring(i, j))
                i = j
            }
            return out
        }

        /** 表記が「漢字 + ひらがな」（溜め・言っ）ならその漢字の部分。 */
        fun stemOf(tango: String): String? {
            if (tango.length < 2 || !isKanji(tango[0])) return null
            var j = 1
            while (j < tango.length && isKanji(tango[j])) j++
            if (j >= tango.length) return null
            for (k in j until tango.length) if (!isHiragana(tango[k])) return null
            return tango.substring(0, j)
        }

        /** カタカナ → ひらがな（ー はそのまま）。ひらがなの無い字（ヷ〜ヺ）を含めば null。 */
        fun toHiragana(kata: String): String? {
            val sb = StringBuilder(kata.length)
            for (c in kata) {
                when {
                    c == 'ー' -> sb.append(c)
                    c in 'ァ'..'ヶ' -> sb.append(c - 0x60)
                    else -> return null
                }
            }
            return sb.toString()
        }
    }
}

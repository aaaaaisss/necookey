package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber

/**
 * 数＋助数詞を算用数字で出す（2026-11-14 ユーザー方針「数字は基本算用数字に」）。
 *
 * かなで打った数（に・さん・くど・みっつ・にじゅうよっ…）や打った数字（200・1255）の直後に助数詞の読み（だい・どめ・ふん…）が
 * 続くとき、読み「数＋助数詞」を 1 語のノード（表記 = 算用数字＋助数詞、次点に漢数字）にする。辞書に同じ読みの
 * 「漢数字＋助数詞」の語（二代目・三階）があれば、同じ品詞・コストで算用数字の双子（2代目）を 1 だけ安く置く（Mozc の数詞リライトと同じ考え）。
 *
 * 誤検出よけ（「数として扱うのは数の後に助数詞が続く・数だけのときだけ。辞書の語の中は触らない」）:
 *  - 慣用の語は読みで除外（いちど 一度・いちばん 一番・いちぶ 一部・いちじ 一時・いちにち 一日・にどと 二度と・まんにん 万人 など、[IDIOMS]）。
 *  - 同じ読みに数でない辞書語がある（ごかい 誤解・にじ 虹・せんにん 仙人）なら、その語より [NON_NUMERIC_MARGIN] だけ高くして負ける（候補には残る）。
 *  - 入力でこの先まで続く、数でない長い辞書語がある（くどい・いちだいじ・ごじつ）なら作らない。
 *  - 読み 1 字の数（よ・く）は決まった助数詞の前だけ。し（四）は使わない。ひとつ・ひとり（和語の一）は扱わない（一つ・一人のまま）。
 *  - 学習語・ユーザー辞書の語が同じ読みにあればそれより高くする（ユーザーが漢数字を選んだら勝たない）。
 *  - 日数の和語（いつか・ようか・とおか…）は 前・後・間・目・ぶり が続くときだけ（いつかまえ → 5日前、いつか だけは いつか のまま）。
 */
internal object NumberCounterNodes {
    @Volatile
    @JvmStatic
    var enabled: Boolean = true

    /** 左 = 数詞（名詞,数,漢数字）、右 = 助数詞（名詞,接尾,助数詞）。 */
    const val NUMBER_ID: Short = 2046
    const val COUNTER_ID: Short = 2011
    /** 読み「数＋助数詞」の合成ノードの語コスト（算用数字）。漢数字はこれに [KANJI_EXTRA_COST]。 */
    const val BASE_COST = 2000
    const val KANJI_EXTRA_COST = 600
    const val SHORT_NUMERAL_EXTRA_COST = 1000
    /** 数字だけ（1 から・200）のノードの語コスト。 */
    const val DIGITS_COST = 1000
    const val NON_NUMERIC_MARGIN = 1000
    /** 学習語・ユーザー辞書の語に対して負ける幅。 */
    const val USER_MARGIN = 500

    private const val MAX_NUMERAL_LEN = 14

    /** 単独のかなの数（いっ・ろっ 等の促音形を含む）。 */
    private val SINGLE: Map<String, Long> = mapOf(
        "れい" to 0, "ぜろ" to 0,
        "いち" to 1, "いっ" to 1,
        "に" to 2,
        "さん" to 3,
        "よん" to 4, "よ" to 4,
        "ご" to 5,
        "ろく" to 6, "ろっ" to 6,
        "なな" to 7, "しち" to 7,
        "はち" to 8, "はっ" to 8,
        "きゅう" to 9, "く" to 9,
        "じゅう" to 10, "じゅっ" to 10, "じっ" to 10,
        "ひゃく" to 100, "ひゃっ" to 100,
        "せん" to 1000,
        "まん" to 10000,
    )

    /** 読み 1 字の数（よ・く）を許す助数詞。 */
    private val SHORT_NUMERAL_COUNTERS: Map<String, Set<String>> = mapOf(
        "よ" to setOf("にん", "にんめ", "じ", "じかん", "じはん", "ねん", "ねんかん", "ねんめ", "ねんせい", "ねんだい"),
        "く" to setOf("じ", "じかん", "じはん", "ど", "どめ", "どだい", "がつ"),
    )

    /** 助数詞の読み → 表記（先頭が既定）。 */
    private val COUNTERS: Map<String, List<String>> = linkedMapOf(
        "だい" to listOf("台", "代"),
        "だいめ" to listOf("代目", "台目"),
        "ど" to listOf("度"),
        "どめ" to listOf("度目"),
        "どだい" to listOf("度台"),
        "ぶ" to listOf("分"),
        "ふん" to listOf("分"), "ぷん" to listOf("分"),
        "ふんかん" to listOf("分間"), "ぷんかん" to listOf("分間"),
        "びょう" to listOf("秒"), "びょうかん" to listOf("秒間"),
        "じ" to listOf("時"), "じかん" to listOf("時間"), "じはん" to listOf("時半"),
        "にち" to listOf("日"), "にちかん" to listOf("日間"), "にちめ" to listOf("日目"),
        "がつ" to listOf("月"),
        "かげつ" to listOf("か月", "ヶ月"), "かげつかん" to listOf("か月間", "ヶ月間"),
        "しゅう" to listOf("週"), "しゅうかん" to listOf("週間"), "しゅうめ" to listOf("週目"),
        "ねん" to listOf("年"), "ねんかん" to listOf("年間"), "ねんめ" to listOf("年目"),
        "ねんだい" to listOf("年代"), "ねんせい" to listOf("年生"),
        "にん" to listOf("人"), "にんめ" to listOf("人目"),
        "こ" to listOf("個"), "こめ" to listOf("個目"),
        "かい" to listOf("回", "階"), "かいめ" to listOf("回目"),
        "ばん" to listOf("番"), "ばんめ" to listOf("番目"), "ばんせん" to listOf("番線"),
        "ほん" to listOf("本"), "ぽん" to listOf("本"), "ぼん" to listOf("本"),
        "ほんめ" to listOf("本目"), "ぽんめ" to listOf("本目"), "ぼんめ" to listOf("本目"),
        "まい" to listOf("枚"), "まいめ" to listOf("枚目"),
        "さい" to listOf("歳", "才"),
        "えん" to listOf("円"),
        "ばい" to listOf("倍"),
        "さつ" to listOf("冊"),
        "ひき" to listOf("匹"), "びき" to listOf("匹"), "ぴき" to listOf("匹"),
        "とう" to listOf("頭", "等"),
        "ちゃく" to listOf("着"),
        "ごう" to listOf("号"),
        "けん" to listOf("件", "軒"), "げん" to listOf("軒"),
        "かしょ" to listOf("箇所", "か所"),
        "てん" to listOf("点"),
        "ぱーせんと" to listOf("%"),
        "ぺーじ" to listOf("ページ"),
        "きろ" to listOf("キロ"),
        "めーとる" to listOf("メートル"),
        "せんち" to listOf("センチ"),
        "ぐらむ" to listOf("グラム"),
    )

    /** 打った数字の後だけの助数詞（かなの数では紛らわしいもの）。 */
    private val DIGIT_ONLY_COUNTERS: Map<String, List<String>> = mapOf(
        "い" to listOf("位"),
        "つ" to listOf("つ"),
    )

    /** 数として扱わない読み（数＋助数詞の慣用語）。読み全体が一致したら作らない。 */
    private val IDIOMS: Set<String> = setOf(
        "いちど", "いちばん", "いちぶ", "いちじ", "いちにち", "いちにん", "いちだい", "いっしゅう",
        "いちねん", "まんにん", "まんいち", "じゅうぶ", "にのまい", "ごぶ",
    )

    /** 数として扱わない、ノードの後ろに続く読み（にど＋と = 二度と）。キー = ノードの読み、値 = 直後の読みの先頭。 */
    private val IDIOM_FOLLOWERS: Map<String, List<String>> = mapOf(
        "にど" to listOf("と"),
        "さんど" to listOf("の"), // 三度の飯
    )

    /** 和語の数（ひとつ は 一つ のまま）。 */
    private val NATIVE_TSU: Map<String, Long> = mapOf(
        "ふたつ" to 2, "みっつ" to 3, "よっつ" to 4, "いつつ" to 5, "むっつ" to 6,
        "ななつ" to 7, "やっつ" to 8, "ここのつ" to 9,
    )

    /** 日数の和語。 */
    private val NATIVE_DAYS: Map<String, Long> = mapOf(
        "ふつか" to 2, "みっか" to 3, "よっか" to 4, "いつか" to 5, "むいか" to 6,
        "なのか" to 7, "なぬか" to 7, "ようか" to 8, "ここのか" to 9, "とおか" to 10, "はつか" to 20,
    )

    /** 日数の後の読み → 表記。[strictFollow] は直後が語の終わりらしいときだけ（いつか＋ご飯 等を避ける）。 */
    private data class DaySuffix(val reading: String, val surface: String, val strictFollow: Boolean)

    private val DAY_SUFFIXES = listOf(
        DaySuffix("まえ", "前", false),
        DaySuffix("ぶり", "ぶり", false),
        DaySuffix("かん", "間", true),
        DaySuffix("ご", "後", true),
        DaySuffix("め", "目", true),
    )

    /** strictFollow の後ろに来てよい文字（入力の終わりも可）。 */
    private const val FOLLOW_OK = "にのもでくぐへ、。！？!?"

    private const val KANJI_DIGITS = "〇一二三四五六七八九"

    data class Made(val end: Int, val tango: String, val cost: Int, val l: Short, val r: Short)

    /**
     * str[i..) から始まる数＋助数詞のノードを作る。[existing] = str[i..) から始まる既存ノード（辞書・学習・ユーザー辞書）。
     */
    fun build(str: String, i: Int, existing: List<Node>): List<Made> {
        if (!enabled || i >= str.length) return emptyList()
        val out = ArrayList<Made>()
        val digitEnd = digitRunEnd(str, i)
        if (digitEnd > i) {
            // 半角を先に（全角で打ったときは全角を次点に）
            val typed = str.substring(i, digitEnd)
            val half = toHalfWidth(typed)
            val forms = if (half == typed) listOf(half) else listOf(half, typed)
            forms.forEachIndexed { f, digits ->
                val formExtra = f * KANJI_EXTRA_COST
                out += Made(digitEnd, digits, DIGITS_COST + formExtra, NUMBER_ID, NUMBER_ID)
                for ((reading, surfaces) in COUNTERS.entries + DIGIT_ONLY_COUNTERS.entries) {
                    if (!str.startsWith(reading, digitEnd)) continue
                    val end = digitEnd + reading.length
                    surfaces.forEachIndexed { k, s ->
                        out += Made(end, digits + s, DIGITS_COST + k * KANJI_EXTRA_COST + formExtra, NUMBER_ID, COUNTER_ID)
                    }
                }
            }
            return out
        }
        val ends = kanaEnds(str, i)
        if (ends.isEmpty()) return out
        // 和語の つ（みっつ → 3つ）
        for ((reading, value) in NATIVE_TSU) {
            if (!str.startsWith(reading, i)) continue
            addCounted(out, str, i, i + reading.length, value.toString() + "つ", NumberReadingNodes.toKanji(value) + "つ", existing, ends)
        }
        // 日数の和語＋前・後・間・目・ぶり（いつかまえ → 5日前）
        for ((reading, value) in NATIVE_DAYS) {
            if (!str.startsWith(reading, i)) continue
            val dayEnd = i + reading.length
            for (sfx in DAY_SUFFIXES) {
                if (!str.startsWith(sfx.reading, dayEnd)) continue
                val end = dayEnd + sfx.reading.length
                if (sfx.strictFollow && end < str.length && str[end] !in FOLLOW_OK) continue
                addCounted(out, str, i, end, "${value}日${sfx.surface}", NumberReadingNodes.toKanji(value) + "日" + sfx.surface, existing, ends)
            }
        }
        // かなの数＋助数詞
        for (numEnd in i + 1..minOf(str.length - 1, i + MAX_NUMERAL_LEN)) {
            val numeral = str.substring(i, numEnd)
            val value = parseNumeral(numeral) ?: continue
            for ((reading, surfaces) in COUNTERS) {
                if (!str.startsWith(reading, numEnd)) continue
                val allowed = SHORT_NUMERAL_COUNTERS[numeral]
                if (allowed != null && reading !in allowed) continue
                val end = numEnd + reading.length
                val yomi = numeral + reading
                if (yomi in IDIOMS) continue
                val followers = IDIOM_FOLLOWERS[yomi]
                if (followers != null && followers.any { str.startsWith(it, end) }) continue
                val kanjiNum = NumberReadingNodes.toKanji(value)
                // 読み 1 字の数（に・ご・く・よ）は助詞・接頭語と紛らわしいので少し高く
                val shortExtra = if (numeral.length == 1) SHORT_NUMERAL_EXTRA_COST else 0
                surfaces.forEachIndexed { k, s ->
                    addCounted(out, str, i, end, value.toString() + s, kanjiNum + s, existing, ends, extra = k * KANJI_EXTRA_COST, synthExtra = shortExtra)
                }
            }
        }
        return out
    }

    /** str[i..) から始まる、かなの数＋助数詞・和語のつ・日数＋前後 の終わりの位置（作れるかどうかの下見）。 */
    private fun kanaEnds(str: String, i: Int): Set<Int> {
        var ends: MutableSet<Int>? = null
        fun add(e: Int) { (ends ?: HashSet<Int>().also { ends = it }).add(e) }
        for ((reading, _) in NATIVE_TSU) if (str.startsWith(reading, i)) add(i + reading.length)
        for ((reading, _) in NATIVE_DAYS) {
            if (!str.startsWith(reading, i)) continue
            val dayEnd = i + reading.length
            for (sfx in DAY_SUFFIXES) if (str.startsWith(sfx.reading, dayEnd)) add(dayEnd + sfx.reading.length)
        }
        for (numEnd in i + 1..minOf(str.length - 1, i + MAX_NUMERAL_LEN)) {
            val numeral = str.substring(i, numEnd)
            if (parseNumeral(numeral) == null) continue
            for (reading in COUNTERS.keys) if (str.startsWith(reading, numEnd)) add(numEnd + reading.length)
        }
        return ends ?: emptySet()
    }

    /**
     * 読み str[i, end) の「算用数字 [arabic] / 漢数字 [kanji]」を足す。同じ読みの辞書語に合わせてコスト・品詞を決める。
     */
    private fun addCounted(
        out: MutableList<Made>,
        str: String,
        i: Int,
        end: Int,
        arabic: String,
        kanji: String,
        existing: List<Node>,
        ends: Set<Int>,
        extra: Int = 0,
        synthExtra: Int = 0,
    ) {
        val len = end - i
        // 前後も数（くど＋ろくぶ・8ど＋7ぶ）なら数の文脈: 数でない同じ読みの語・読み 1 字の数への上乗せをしない
        val numericContext = (end < str.length && (isDigit(str[end]) || kanaEnds(str, end).isNotEmpty())) ||
            (i > 0 && isDigit(str[i - 1]))
        // この先まで続く長い辞書語（くどい・いちだいじ・ごじつ・日本語・六本木・三人組）があれば作らない。
        // ただしその語の終わりがここで作れる数＋助数詞の終わり（にじ → にじかん 二時間）なら数の語なので作る。
        if (existing.any { it.len > len && it.candidateSource == CandidateSource.SYSTEM && (i + it.len) !in ends }) return
        val same = existing.filter { it.len.toInt() == len }
        val twin = same.filter { it.candidateSource == CandidateSource.SYSTEM && it.tango == kanji }.minByOrNull { it.score }
        val userCost = same.filter {
            it.candidateSource == CandidateSource.LEARNED_DICTIONARY || it.candidateSource == CandidateSource.USER_DICTIONARY
        }.filter { it.tango != arabic }.minOfOrNull { it.score }
        val nonNumeric = same.filter { it.candidateSource == CandidateSource.SYSTEM && !startsNumeric(it.tango) }.minOfOrNull { it.score }
        var cost: Int
        val l: Short
        val r: Short
        if (twin != null) {
            // 辞書の漢数字の語の双子（同じ品詞・コスト − 1）
            cost = twin.score - 1 + extra
            l = twin.l
            r = twin.r
        } else {
            cost = BASE_COST + extra + (if (numericContext) 0 else synthExtra)
            l = NUMBER_ID
            r = COUNTER_ID
            // 同じ読みに辞書の漢数字の語（別の表記）があれば、その双子より先に出ない
            val numeric = same.filter { it.candidateSource == CandidateSource.SYSTEM && startsNumeric(it.tango) }.minOfOrNull { it.score }
            if (numeric != null) cost = maxOf(cost, numeric + extra)
        }
        if (nonNumeric != null && !numericContext) cost = maxOf(cost, nonNumeric + NON_NUMERIC_MARGIN + extra)
        if (userCost != null) cost = maxOf(cost, userCost + USER_MARGIN + extra)
        out += Made(end, arabic, cost, l, r)
        if (twin == null) out += Made(end, kanji, cost + KANJI_EXTRA_COST, l, r)
    }

    /** 漢数字で始まる表記（二代目・三階・十時）。数の語かどうかの目安。 */
    private fun startsNumeric(tango: String): Boolean =
        tango.isNotEmpty() && (tango[0] in KANJI_DIGITS || tango[0] in "十百千万" || tango[0].isDigit())

    /** かなの数を値に（単独の数・位取りのある数・末尾の促音形 にじゅっ・さんびゃっ）。数でなければ null。 */
    fun parseNumeral(s: String): Long? {
        if (s.isEmpty()) return null
        SINGLE[s]?.let { return it }
        if (s.length < 3) return null
        if (s.endsWith("っ")) {
            val stem = s.dropLast(1)
            for (tail in listOf("う", "く", "ち", "ん")) {
                val v = parseCompound(stem + tail)
                if (v != null) return v
            }
            return null
        }
        return parseCompound(s)
    }

    private fun parseCompound(s: String): Long? {
        SINGLE[s]?.let { return it }
        if (listOf("じゅう", "ひゃく", "びゃく", "ぴゃく", "せん", "ぜん", "まん").none { s.contains(it) }) return null
        if (s.contains("ぜん") && !s.contains("さんぜん")) return null
        return s.toNumber()?.second?.toLongOrNull()
    }

    /** 打った数字の並び（半角・全角、間の . , を 1 つずつ許す）の終わり。数字で始まらなければ [i]。 */
    fun digitRunEnd(str: String, i: Int): Int {
        if (i >= str.length || !isDigit(str[i])) return i
        // 前が数字なら途中から始めない
        if (i > 0 && (isDigit(str[i - 1]) || ((str[i - 1] == '.' || str[i - 1] == ',') && i > 1 && isDigit(str[i - 2])))) return i
        var e = i + 1
        while (e < str.length) {
            if (isDigit(str[e])) {
                e++
            } else if ((str[e] == '.' || str[e] == ',' || str[e] == '．' || str[e] == '，') && e + 1 < str.length && isDigit(str[e + 1])) {
                e += 2
            } else {
                break
            }
        }
        return e
    }

    private fun toHalfWidth(s: String): String = buildString(s.length) {
        for (c in s) {
            append(
                when (c) {
                    in '０'..'９' -> '0' + (c - '０')
                    '．' -> '.'
                    '，' -> ','
                    else -> c
                },
            )
        }
    }

    private fun isDigit(c: Char): Boolean = c in '0'..'9' || c in '０'..'９'

    /** 文字列の中の半角数字の並びを漢数字に（2代目 → 二代目、9度6分 → 九度六分）。候補の並べ替えで漢数字の同じ候補を探す。 */
    fun kanjiForm(s: String): String {
        if (s.none { it in '0'..'9' }) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] !in '0'..'9') {
                sb.append(s[i]); i++; continue
            }
            var e = i
            while (e < s.length && s[e] in '0'..'9') e++
            val v = if (e - i <= 15) s.substring(i, e).toLongOrNull() else null
            sb.append(if (v != null) NumberReadingNodes.toKanji(v) else s.substring(i, e))
            i = e
        }
        return sb.toString()
    }

    /** ラティス LM で引く語（算用数字を漢数字に戻す。表は漢数字の表記で数えてある）。 */
    fun lmWord(tango: String): String {
        var e = 0
        while (e < tango.length && tango[e] in '0'..'9') e++
        if (e == 0 || e > 15) return tango
        val v = tango.substring(0, e).toLongOrNull() ?: return tango
        return NumberReadingNodes.toKanji(v) + tango.substring(e)
    }
}

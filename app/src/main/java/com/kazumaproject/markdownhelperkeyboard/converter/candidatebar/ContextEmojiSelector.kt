package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

/** 文脈絵文字の候補: 絵文字・代表キーワード（zenz が採点する語）・ムードグループ。 */
data class MoodEmoji(val emoji: String, val keyword: String, val group: String)

/**
 * 確定後の文脈絵文字（assets/emoji/emoji_mood.tsv、よく使われる約 150 絵文字）。
 *
 * 1 回の後続語 zenz 採点に [POOL] 件のキーワードだけ混ぜる（[prefilter]）: 直近に選ばれた絵文字
 * （最大 [MAX_HOT] 件、同じ会話ではムードが続きやすい）＋残りはムードグループを順に巡回して各グループの
 * 次の絵文字。順位は PMI = logP(キーワード|左文脈) − logP(キーワード|空文脈)（[rank]）。空文脈の
 * 事前スコアはモデルごとに一度だけ計算してキャッシュする（[missingPriors] / [putPriors]）。
 * 事前スコアが未計算のキーワードは順位付けしない。
 */
class ContextEmojiSelector(entries: List<MoodEmoji>) {
    companion object {
        /** 1 回の確定で zenz に採点させるキーワード数。 */
        const val POOL = 10

        /** [POOL] のうち直近に選ばれた絵文字の枠。 */
        const val MAX_HOT = 3

        /** [POOL] のうち左文脈にキーワード（またはその語幹）が出てきた絵文字の枠。 */
        const val MAX_MATCHED = 4

        /** 左文脈に出てきたキーワードの絵文字は、PMI がこれより高ければ出す（通常の [MIN_PMI] より緩い）。 */
        const val MIN_PMI_MATCHED = -1.0f

        /** キーワード照合に使う左文脈の末尾文字数。 */
        const val MATCH_WINDOW = 40

        private val SINGLE_CHAR_KEYS = setOf("犬", "猫", "雨", "雪", "桜", "月", "草", "星")

        /** 表示する絵文字の上限。 */
        const val MAX_SHOWN = 3

        /** これより PMI が低い（文脈で起こりやすくならない）絵文字は出さない。 */
        const val MIN_PMI = 0f

        /** 事前スコアを 1 回で計算する上限（後続語採点と mutex を取り合わないよう小分け）。 */
        const val PRIOR_BATCH = 24

        /** 事前スコア計算を続ける最大回数（150 語 / 24 件 ≒ 7 回）。 */
        const val MAX_PRIOR_ROUNDS = 10

        /** 事前スコア計算の 1 回ごとの間隔（変換の推論に割り込みすぎない）。 */
        const val PRIOR_ROUND_DELAY_MS = 400L

        /** emoji_mood.tsv（emoji TAB keyword TAB group、# はコメント）を読む。 */
        fun parse(text: String): List<MoodEmoji> = text.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 3 || f[0].isEmpty() || f[1].isEmpty()) null else MoodEmoji(f[0], f[1], f[2])
            }
            .toList()
    }

    private val groups: List<List<MoodEmoji>> =
        entries.distinctBy { it.keyword }.groupBy { it.group }.values.toList()
    private val all: List<MoodEmoji> = groups.flatten()
    private var groupCursor = 0
    private val memberCursor = IntArray(groups.size)
    private val hot = ArrayDeque<MoodEmoji>()
    private val priors = HashMap<String, Float>()
    private var priorModel: String? = null
    private var matched: Set<MoodEmoji> = emptySet()

    /** 送り仮名を落とした語幹（美味しい→美味、頑張る→頑張）。2 文字未満になるなら元の語。 */
    private fun stem(keyword: String): String {
        val trimmed = keyword.trimEnd { it in '\u3041'..'\u309F' }
        return if (trimmed.length >= 1 && trimmed != keyword && trimmed.any { it !in '\u3041'..'\u309F' }) trimmed else keyword
    }

    private val stems: Map<MoodEmoji, String> by lazy { all.associateWith { stem(it.keyword) } }

    /** 左文脈 [leftContext] の末尾にキーワード（語幹）が現れる絵文字（新しく出たものほど先）。 */
    fun matchesIn(leftContext: String): List<MoodEmoji> {
        if (leftContext.isBlank()) return emptyList()
        val window = leftContext.takeLast(MATCH_WINDOW)
        return all.mapNotNull { e ->
            val key = stems[e] ?: e.keyword
            // 1 文字の語は誤反応が多い（下さい→👇 など）ので、意味のはっきりした名詞だけ照合する。
            if (key.length < 2 && key !in SINGLE_CHAR_KEYS) return@mapNotNull null
            val at = window.lastIndexOf(key)
            if (at < 0) null else e to at
        }.sortedByDescending { it.second }.map { it.first }
    }

    /**
     * zenz なしで出す絵文字: 今の文（最後の句点・改行より後ろ）にキーワードが出てきたもの、新しい順に [limit] 件。
     * 前の文の語で反応しないよう文で区切る。
     */
    fun plainMatches(leftContext: String, limit: Int = 2): List<MoodEmoji> {
        val sentence = leftContext.takeLast(MATCH_WINDOW)
            .substringAfterLast('\n')
            .let { s ->
                // 末尾の句読点は今の文の終わりなので、その手前の区切りから見る。
                val body = s.trimEnd('。', '！', '？', '!', '?', '、', ' ', '　')
                val cut = body.indexOfLast { it in "。！？!?" }
                if (cut >= 0) body.substring(cut + 1) else body
            }
        return matchesIn(sentence).take(limit)
    }

    val size: Int get() = all.size

    /** 今回の確定で採点するキーワード（[POOL] 件まで、重複なし）。呼ぶたびに巡回位置が進む。 */
    @Synchronized
    fun prefilter(leftContext: String = ""): List<MoodEmoji> {
        if (groups.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, MoodEmoji>()
        // 1. 左文脈に出てきた語の絵文字（文脈に直結するので最優先）
        val hit = matchesIn(leftContext).take(MAX_MATCHED)
        matched = hit.toSet()
        hit.forEach { out[it.keyword] = it }
        // 2. 直近に選ばれた絵文字
        hot.take(MAX_HOT).forEach { out.putIfAbsent(it.keyword, it) }
        val hotGroups = out.values.mapTo(HashSet()) { it.group }
        var visited = 0
        while (out.size < POOL && visited < groups.size) {
            val g = groupCursor
            groupCursor = (groupCursor + 1) % groups.size
            visited++
            val members = groups[g]
            if (members.first().group in hotGroups) continue
            val m = members[memberCursor[g] % members.size]
            memberCursor[g] = (memberCursor[g] + 1) % members.size
            out.putIfAbsent(m.keyword, m)
        }
        return out.values.take(POOL)
    }

    /** [model] の事前スコアが未計算のキーワード: [first] を先に、全体から [PRIOR_BATCH] 件まで。 */
    @Synchronized
    fun missingPriors(model: String, first: List<MoodEmoji> = emptyList()): List<String> {
        if (priorModel != model) {
            priors.clear()
            priorModel = model
        }
        return (first + all).asSequence().map { it.keyword }.distinct()
            .filter { it !in priors }.take(PRIOR_BATCH).toList()
    }

    /** 空文脈で採点した [scores]（[keywords] と同じ並び）を [model] の事前スコアとして保存。 */
    @Synchronized
    fun putPriors(model: String, keywords: List<String>, scores: FloatArray) {
        if (priorModel != model || scores.size != keywords.size) return
        keywords.forEachIndexed { i, k -> if (scores[i].isFinite()) priors[k] = scores[i] }
    }

    @Synchronized
    fun prior(keyword: String): Float? = priors[keyword]

    /** [model] の事前スコアを保存用テキスト（1 行目が [header]＝モデル識別子、以降 keyword TAB score）にする。 */
    @Synchronized
    fun exportPriors(model: String, header: String): String? {
        if (priorModel != model || priors.isEmpty()) return null
        return buildString {
            append(header).append('\n')
            priors.forEach { (k, v) -> append(k).append('\t').append(v).append('\n') }
        }
    }

    /**
     * [exportPriors] の出力を読み込む（モデルが一致するときだけ）。事前スコアは文脈によらずモデルで決まるので、
     * 一度計算すれば再起動後も使い回せる（毎回 150 語を採点し直さない）。読めた件数を返す。
     */
    @Synchronized
    fun importPriors(model: String, header: String, text: String): Int {
        val lines = text.lines()
        if (lines.firstOrNull() != header) return 0
        if (priorModel != model) {
            priors.clear()
            priorModel = model
        }
        var n = 0
        for (line in lines.drop(1)) {
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val v = line.substring(tab + 1).toFloatOrNull() ?: continue
            if (v.isFinite()) {
                priors[line.substring(0, tab)] = v
                n++
            }
        }
        return n
    }

    /**
     * [targets]（[prefilter] の結果）と同じ並びの左文脈つきスコアから PMI 上位 [MAX_SHOWN] 件の
     * インデックスを返す（PMI 降順、[MIN_PMI] 超のみ）。選ばれた絵文字は次回の [prefilter] の hot 枠に入り、採点されて選ばれなかった hot は外れる。
     */
    @Synchronized
    fun rank(targets: List<MoodEmoji>, scores: FloatArray): List<Int> {
        if (scores.size != targets.size) return emptyList()
        val picked = targets.indices.mapNotNull { i ->
            val prior = priors[targets[i].keyword] ?: return@mapNotNull null
            if (!scores[i].isFinite()) return@mapNotNull null
            val pmi = scores[i] - prior
            val threshold = if (targets[i] in matched) MIN_PMI_MATCHED else MIN_PMI
            if (pmi > threshold) i to pmi else null
        }.sortedWith(compareByDescending<Pair<Int, Float>> { it.second }.thenBy { it.first })
            .take(MAX_SHOWN)
            .map { it.first }
        val pickedSet = picked.mapTo(HashSet()) { targets[it] }
        hot.removeAll { it in targets && it !in pickedSet }
        for (i in picked.asReversed()) {
            hot.remove(targets[i])
            hot.addFirst(targets[i])
        }
        while (hot.size > MAX_HOT) hot.removeLast()
        return picked
    }
}

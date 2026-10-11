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
        const val POOL = 8

        /** [POOL] のうち直近に選ばれた絵文字の枠。 */
        const val MAX_HOT = 3

        /** 表示する絵文字の上限。 */
        const val MAX_SHOWN = 3

        /** これより PMI が低い（文脈で起こりやすくならない）絵文字は出さない。 */
        const val MIN_PMI = 0f

        /** 事前スコアを 1 回で計算する上限（後続語採点と mutex を取り合わないよう小分け）。 */
        const val PRIOR_BATCH = 24

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

    val size: Int get() = all.size

    /** 今回の確定で採点するキーワード（[POOL] 件まで、重複なし）。呼ぶたびに巡回位置が進む。 */
    @Synchronized
    fun prefilter(): List<MoodEmoji> {
        if (groups.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, MoodEmoji>()
        hot.take(MAX_HOT).forEach { out[it.keyword] = it }
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
            if (pmi > MIN_PMI) i to pmi else null
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

package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

/** 文脈絵文字の候補: 絵文字・代表キーワード（zenz が採点する語）・ムードグループ。 */
data class MoodEmoji(val emoji: String, val keyword: String, val group: String)

/**
 * 確定後の文脈絵文字（assets/emoji/emoji_mood.tsv、ユーザー指定の 8 絵文字 × 数語のキーワード）。
 *
 * 確定ごとに全キーワードを後続語の zenz 採点 1 回に混ぜ（[prefilter]）、絵文字ごとに一番合ったキーワードの
 * PMI = logP(キーワード|左文脈) − logP(キーワード|空文脈) で比べて、雰囲気に合う絵文字を [MAX_SHOWN] 個だけ出す（[rank]）。
 * 空文脈の事前スコアはモデルごとに一度だけ計算してキャッシュする（[missingPriors] / [putPriors]）。
 * 事前スコアが未計算のキーワードは順位付けしない。
 */
class ContextEmojiSelector(entries: List<MoodEmoji>) {
    companion object {
        /** 左文脈にキーワード（またはその語幹）が出てきた絵文字は、PMI がこれより高ければ出す（通常の [MIN_PMI] より緩い）。 */
        const val MIN_PMI_MATCHED = -1.0f

        /** キーワード照合に使う左文脈の末尾文字数。 */
        const val MATCH_WINDOW = 40

        private val SINGLE_CHAR_KEYS = setOf("犬", "猫", "雨", "雪", "桜", "月", "草", "星")

        /** 表示する絵文字の数（雰囲気に合う 1 個）。 */
        const val MAX_SHOWN = 1

        /** これより PMI が低い（文脈で起こりやすくならない）絵文字は出さない。 */
        const val MIN_PMI = 0f

        /** 事前スコアを 1 回で計算する上限（後続語採点と mutex を取り合わないよう小分け）。 */
        const val PRIOR_BATCH = 24

        /** 事前スコア計算を続ける最大回数。 */
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

    private val all: List<MoodEmoji> = entries.distinctBy { it.keyword }
    private val priors = HashMap<String, Float>()
    private var priorModel: String? = null
    private var matched: Set<MoodEmoji> = emptySet()

    /** 送り仮名を落とした語幹（美味しい→美味、頑張る→頑張、悲しい→悲し）。 */
    private fun stem(keyword: String): String {
        val trimmed = keyword.trimEnd { it in '\u3041'..'\u309F' }
        if (trimmed.isEmpty() || trimmed == keyword) return keyword
        // 1 字の語幹（悲しい→悲）は誤反応するので、送り仮名を 1 字残す（悲し・泣く）。
        return if (trimmed.length >= 2) trimmed else keyword.take(2)
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
     * zenz なしで出す絵文字: 今の文（最後の句点・改行より後ろ）にキーワードが一番最近出てきたもの（[limit] 件）。
     * 前の文の語で反応しないよう文で区切る。
     */
    fun plainMatches(leftContext: String, limit: Int = MAX_SHOWN): List<MoodEmoji> {
        val sentence = leftContext.takeLast(MATCH_WINDOW)
            .substringAfterLast('\n')
            .let { s ->
                // 末尾の句読点は今の文の終わりなので、その手前の区切りから見る。
                val body = s.trimEnd('。', '！', '？', '!', '?', '、', ' ', '　')
                val cut = body.indexOfLast { it in "。！？!?" }
                if (cut >= 0) body.substring(cut + 1) else body
            }
        return matchesIn(sentence).distinctBy { it.emoji }.take(limit)
    }

    val size: Int get() = all.size

    /** 今回の確定で採点するキーワード（全キーワード）。左文脈に出てきた語は [rank] で閾値を緩める。 */
    @Synchronized
    fun prefilter(leftContext: String = ""): List<MoodEmoji> {
        matched = matchesIn(leftContext).toSet()
        return all
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
     * [targets]（[prefilter] の結果）と同じ並びの左文脈つきスコアから、絵文字ごとに PMI が一番高いキーワードを代表にして
     * 比べ、上位 [MAX_SHOWN] 件のインデックス（代表キーワードの位置）を返す（PMI 降順、閾値超のみ）。
     */
    @Synchronized
    fun rank(targets: List<MoodEmoji>, scores: FloatArray): List<Int> {
        if (scores.size != targets.size) return emptyList()
        return targets.indices.mapNotNull { i ->
            val prior = priors[targets[i].keyword] ?: return@mapNotNull null
            if (!scores[i].isFinite()) return@mapNotNull null
            val pmi = scores[i] - prior
            val threshold = if (targets[i] in matched) MIN_PMI_MATCHED else MIN_PMI
            if (pmi > threshold) i to pmi else null
        }.groupBy { targets[it.first].emoji }
            .values.map { hits -> hits.maxWith(compareBy<Pair<Int, Float>> { it.second }.thenByDescending { it.first }) }
            .sortedWith(compareByDescending<Pair<Int, Float>> { it.second }.thenBy { it.first })
            .take(MAX_SHOWN)
            .map { it.first }
    }
}

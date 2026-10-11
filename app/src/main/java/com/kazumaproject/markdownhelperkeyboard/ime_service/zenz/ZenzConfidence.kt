package com.kazumaproject.markdownhelperkeyboard.ime_service.zenz

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.Locale

/**
 * zenz の判定（candidate_evaluate）に付く信頼度。native が判定文字列の後ろに U+001F 区切りで付ける。
 *
 * - [minLogprob]: zenz が受け入れたドラフトのトークン（判定位置の手前まで）の対数確率の最小。
 * - [minMargin]: 同じトークンで、argmax と 2 番目の対数確率の差の最小（小さいほど迷っている）。
 * - [fixGap]: FIX/WHOLE の位置で、zenz の argmax とドラフトのトークンの対数確率の差（大きいほど確信して直している）。PASS は NaN。
 * - [fixMaxLogprob]: FIX/WHOLE の位置の argmax の対数確率。PASS は NaN。
 * - [acceptedTokens]: 受け入れたトークン数。
 */
data class ZenzConfidence(
    val minLogprob: Float,
    val minMargin: Float,
    val fixGap: Float,
    val fixMaxLogprob: Float,
    val acceptedTokens: Int,
) {
    fun describe(): String = String.format(
        Locale.ROOT,
        "minLp=%.2f minMargin=%.2f fixGap=%.2f fixMaxLp=%.2f tokens=%d",
        minLogprob, minMargin, fixGap, fixMaxLogprob, acceptedTokens,
    )

    companion object {
        const val SEPARATOR = '\u001F'
        private const val PREFIX = "conf:"

        /** native の判定文字列を (判定, 信頼度) に分ける。信頼度が無い・読めないときは null。 */
        fun split(raw: String): Pair<String, ZenzConfidence?> {
            val index = raw.lastIndexOf(SEPARATOR)
            if (index < 0) return raw to null
            val verdict = raw.substring(0, index)
            val body = raw.substring(index + 1)
            if (!body.startsWith(PREFIX)) return verdict to null
            val parts = body.removePrefix(PREFIX).split(',')
            if (parts.size != 5) return verdict to null
            fun f(s: String): Float = when (s.trim().lowercase(Locale.ROOT)) {
                "nan", "-nan" -> Float.NaN
                "inf" -> Float.POSITIVE_INFINITY
                "-inf" -> Float.NEGATIVE_INFINITY
                else -> s.trim().toFloatOrNull() ?: Float.NaN
            }
            return verdict to ZenzConfidence(
                minLogprob = f(parts[0]),
                minMargin = f(parts[1]),
                fixGap = f(parts[2]),
                fixMaxLogprob = f(parts[3]),
                acceptedTokens = parts[4].trim().toIntOrNull() ?: 0,
            )
        }
    }
}

/**
 * 信頼度の診断（文字列は保存しない。数値と種類だけ）。確定したときに、直前の Zenzai の判定が
 * 当たっていたか（ユーザーが確定した表記で判断）と一緒に最新 [MAX_SAMPLES] 件を残す。
 */
object ZenzConfidenceDiagnostics {
    private const val KEY_SAMPLES = "zenz_confidence_samples"
    const val MAX_SAMPLES = 200

    /** 判定の種類。 */
    enum class Kind(val key: String) { PASS("pass"), FIX("fix"), WHOLE("whole") }

    /**
     * 確定と照らした結果。PASS: ドラフトのまま確定=KEPT、違う表記=OTHER。
     * FIX/WHOLE: zenz の直しを確定=KEPT、直す前のドラフトを確定=REJECTED、どちらでもない=OTHER。
     */
    enum class Result(val key: String) { KEPT("kept"), REJECTED("rejected"), OTHER("other") }

    data class Sample(
        val kind: Kind,
        val result: Result,
        val confidence: ZenzConfidence,
        val sumireGap: Int?,
    )

    fun record(context: Context, sample: Sample) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val line = listOf(
            sample.kind.key,
            sample.result.key,
            num(sample.confidence.minLogprob),
            num(sample.confidence.minMargin),
            num(sample.confidence.fixGap),
            num(sample.confidence.fixMaxLogprob),
            sample.sumireGap?.toString().orEmpty(),
        ).joinToString(",")
        val existing = preferences.getString(KEY_SAMPLES, "").orEmpty()
            .split(';').filter { it.isNotBlank() }
        val updated = (existing + line).takeLast(MAX_SAMPLES).joinToString(";")
        preferences.edit().putString(KEY_SAMPLES, updated).apply()
    }

    fun samples(context: Context): List<Sample> {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return preferences.getString(KEY_SAMPLES, "").orEmpty()
            .split(';')
            .mapNotNull { parse(it) }
    }

    /** 設定の診断画面に出す要約: 種類×結果ごとの件数と各信号の中央値。 */
    fun summary(context: Context): String {
        val all = samples(context)
        if (all.isEmpty()) return ""
        val lines = ArrayList<String>()
        lines += "zenz の信頼度（確定 ${all.size} 件、中央値）"
        for (kind in Kind.values()) {
            for (result in Result.values()) {
                val group = all.filter { it.kind == kind && it.result == result }
                if (group.isEmpty()) continue
                val minLp = median(group.map { it.confidence.minLogprob })
                val margin = median(group.map { it.confidence.minMargin })
                val gap = median(group.map { it.confidence.fixGap })
                val sumire = median(group.mapNotNull { it.sumireGap?.toFloat() })
                lines += String.format(
                    Locale.ROOT,
                    "・%s/%s %d件: 最小logp %s 最小差 %s FIX差 %s Sumire差 %s",
                    kind.key, result.key, group.size, fmt(minLp), fmt(margin), fmt(gap), fmt(sumire),
                )
            }
        }
        return lines.joinToString("\n")
    }

    private fun parse(line: String): Sample? {
        val p = line.split(',')
        if (p.size != 7) return null
        val kind = Kind.values().firstOrNull { it.key == p[0] } ?: return null
        val result = Result.values().firstOrNull { it.key == p[1] } ?: return null
        fun f(s: String) = s.toFloatOrNull() ?: Float.NaN
        return Sample(
            kind = kind,
            result = result,
            confidence = ZenzConfidence(f(p[2]), f(p[3]), f(p[4]), f(p[5]), 0),
            sumireGap = p[6].toIntOrNull(),
        )
    }

    private fun num(value: Float): String =
        if (value.isNaN() || value.isInfinite()) "" else String.format(Locale.ROOT, "%.3f", value)

    private fun median(values: List<Float>): Float {
        val finite = values.filter { !it.isNaN() && !it.isInfinite() }.sorted()
        if (finite.isEmpty()) return Float.NaN
        val mid = finite.size / 2
        return if (finite.size % 2 == 1) finite[mid] else (finite[mid - 1] + finite[mid]) / 2f
    }

    private fun fmt(value: Float): String =
        if (value.isNaN()) "-" else String.format(Locale.ROOT, "%.2f", value)
}

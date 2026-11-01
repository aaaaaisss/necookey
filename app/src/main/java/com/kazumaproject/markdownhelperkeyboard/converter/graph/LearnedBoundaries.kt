package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.graph.Node

/**
 * チップで直した文節区切りの一般化（IDEAS_NEXT_ACCURACY 5、docs/LEARNING.md「区切りの学習」）。
 *
 * 確定したチップの区切りを最初に出したチップの区切りと比べ、増えた区切り（切った）・消えた区切り（くっつけた）を
 * 「境目の左 2 文字＋右 2 文字」の読みをキーに覚える。同じ文でなくても、同じ文字の並びの境目なら
 * 切った → その境目をまたぐノードのコストを足す（切れやすく）、くっつけた → またぐノードのコストを引く（つながりやすく、下限 0）。
 *
 * 値は「切った回数 − くっつけた回数」を ±[MAX_STEPS] で止めたもの。[HALF_LIFE_DAYS] で弱まり、
 * 弱まって 0 に丸まった行は使わない。行数は [MAX_ENTRIES] まで（古いものから捨てる）。
 * 状態はこのクラスの中だけ（純粋。保存・読み込みは呼び出し側が [serialize] / [parse] で行う）。
 */
class LearnedBoundaries {

    data class Entry(val steps: Int, val lastUsedAt: Long)

    private val entries = LinkedHashMap<String, Entry>(64, 0.75f, true)

    @Synchronized
    fun isEmpty(): Boolean = entries.isEmpty()

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun entry(key: String): Entry? = entries[key]

    /**
     * チップ確定の区切りを覚える。[initialBoundaries] / [committedBoundaries] は読みの中の切れ目の位置（0 と末尾は無視）。
     * @return 何か書いたら true。
     */
    @Synchronized
    fun record(
        reading: String,
        initialBoundaries: Collection<Int>,
        committedBoundaries: Collection<Int>,
        now: Long,
    ): Boolean {
        val inner = 1 until reading.length
        val initial = initialBoundaries.filter { it in inner }.toSet()
        val committed = committedBoundaries.filter { it in inner }.toSet()
        if (initial == committed) return false
        var changed = false
        for (p in committed - initial) changed = bump(keyAt(reading, p), +1, now) || changed
        for (p in initial - committed) changed = bump(keyAt(reading, p), -1, now) || changed
        while (entries.size > MAX_ENTRIES) {
            entries.remove(entries.keys.first())
        }
        return changed
    }

    private fun bump(key: String, delta: Int, now: Long): Boolean {
        val current = entries[key]?.let { decayedSteps(it, now) } ?: 0
        val next = (current + delta).coerceIn(-MAX_STEPS, MAX_STEPS)
        if (next == 0) {
            entries.remove(key)
        } else {
            entries[key] = Entry(next, now)
        }
        return true
    }

    /**
     * [reading] の各境目のコスト調整（位置 → 加えるコスト。正 = 切れやすく、負 = つながりやすく）。
     */
    @Synchronized
    fun adjustments(reading: String, now: Long): List<Pair<Int, Int>> {
        if (entries.isEmpty() || reading.length < 2) return emptyList()
        val out = ArrayList<Pair<Int, Int>>()
        for (p in 1 until reading.length) {
            val e = entries[keyAt(reading, p)] ?: continue
            val steps = decayedSteps(e, now)
            if (steps != 0) out += p to steps * COST_PER_STEP
        }
        return out
    }

    /**
     * ラティスに反映する。境目 p をまたぐ（開始 < p < 終了の）ノードのコストに調整を足す（下限 0）。
     * グラフのキーは終了位置（読みの先頭からの文字数）、ノードの [Node.sPos] は開始位置。
     * @return 調整した境目のうち一番左の位置（無ければ -1）。前向き DP の再利用はこの位置までに下げる。
     */
    fun applyTo(graph: MutableMap<Int, MutableList<Node>>, reading: String, now: Long): Int {
        val adjustments = adjustments(reading, now)
        if (adjustments.isEmpty()) return -1
        for ((endIndex, nodes) in graph) {
            if (endIndex <= 0 || endIndex > reading.length) continue
            for (node in nodes) {
                val start = node.sPos
                var delta = 0
                for ((p, cost) in adjustments) {
                    if (start < p && p < endIndex) delta += cost
                }
                if (delta == 0) continue
                val score = (node.score + delta).coerceAtLeast(0)
                node.score = score
                node.f = score
                node.g = score
                node.adjustedScore = score
            }
        }
        return adjustments.minOf { it.first }
    }

    @Synchronized
    fun serialize(): String = buildString {
        entries.forEach { (k, e) -> append(k).append('\t').append(e.steps).append('\t').append(e.lastUsedAt).append('\n') }
    }

    @Synchronized
    fun loadFrom(text: String) {
        entries.clear()
        text.lineSequence().forEach { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@forEach
            val steps = parts[1].toIntOrNull() ?: return@forEach
            val at = parts[2].toLongOrNull() ?: return@forEach
            if (steps == 0 || parts[0].isEmpty()) return@forEach
            entries[parts[0]] = Entry(steps.coerceIn(-MAX_STEPS, MAX_STEPS), at)
        }
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
    }

    companion object {
        /** 1 回分のコスト（Sumire の単語コストの尺度）。 */
        const val COST_PER_STEP = 1000
        const val MAX_STEPS = 3
        const val HALF_LIFE_DAYS = 30.0
        const val MAX_ENTRIES = 1000
        private const val DAY_MS = 86_400_000.0

        /** 境目 p のキー: 左 2 文字（文頭なら短い）＋ "|" ＋ 右 2 文字（文末なら短い）。 */
        fun keyAt(reading: String, p: Int): String =
            reading.substring((p - 2).coerceAtLeast(0), p) + "|" + reading.substring(p, (p + 2).coerceAtMost(reading.length))

        internal fun decayedSteps(e: Entry, now: Long): Int {
            val days = ((now - e.lastUsedAt).coerceAtLeast(0L)) / DAY_MS
            val v = e.steps * Math.pow(0.5, days / HALF_LIFE_DAYS)
            return Math.round(v).toInt()
        }

        /** アプリ全体で 1 つ（IMEService が読み込み・保存し、GraphBuilder が反映する）。 */
        @JvmStatic
        val shared: LearnedBoundaries = LearnedBoundaries()

        /** 反映するか（無効にすると GraphBuilder は何もしない）。 */
        @Volatile
        @JvmStatic
        var enabled: Boolean = true
    }
}

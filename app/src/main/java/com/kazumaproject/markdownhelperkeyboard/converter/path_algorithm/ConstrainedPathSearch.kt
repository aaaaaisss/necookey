package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.graph.Node
import java.util.IdentityHashMap

/**
 * Zenzai 方式の投機的デコーディング用ドラフト探索。
 *
 * 出力表層の先頭が [prefix] と一致する経路だけを対象に、通常と同じ
 * 単語コスト＋連接コストで最良経路を1本求める。状態は（ノード, 一致済み prefix 長）。
 * prefix を満たし終えた状態は通常の Viterbi と同じになるので、状態数は小さい。
 */
object ConstrainedPathSearch {

    data class Result(
        val surface: String,
        val cost: Int,
        val segments: List<CandidateConversionSegment>,
    )

    private class States(size: Int) {
        val cost = IntArray(size) { Int.MAX_VALUE }
        val prevNode = arrayOfNulls<Node>(size)
        val prevK = IntArray(size) { -1 }
    }

    fun search(
        graph: Map<Int, List<Node>>,
        length: Int,
        connectionMatrix: ConnectionMatrix.CostTable,
        prefix: String,
        cancellationCheck: () -> Unit = {},
    ): Result? {
        if (length <= 0) return null
        val p = prefix.length
        val table = IdentityHashMap<Node, States>()
        val bosStates = States(p + 1).also { it.cost[0] = 0 }
        table[BOS] = bosStates

        for (end in 1..length) {
            cancellationCheck()
            val nodes = graph[end] ?: continue
            for (node in nodes) {
                if (node.tango == "EOS" || node.tango == "BOS") continue
                val start = end - node.len
                if (start < 0) continue
                val prevNodes: List<Node> = if (start == 0) listOf(BOS) else graph[start] ?: continue
                val surface = node.tango
                var states: States? = null
                for (prev in prevNodes) {
                    val prevStates = table[prev] ?: continue
                    val edge = connectionMatrix.cost(prev.r.toInt(), node.l.toInt())
                    for (k in 0..p) {
                        val base = prevStates.cost[k]
                        if (base == Int.MAX_VALUE) continue
                        val nk = advance(prefix, k, surface)
                        if (nk < 0) continue
                        val c = base + node.adjustedScore + edge
                        val s = states ?: States(p + 1).also { states = it }
                        if (c < s.cost[nk]) {
                            s.cost[nk] = c
                            s.prevNode[nk] = prev
                            s.prevK[nk] = k
                        }
                    }
                }
                states?.let { table[node] = it }
            }
        }

        var bestNode: Node? = null
        var bestCost = Int.MAX_VALUE
        for (node in graph[length].orEmpty()) {
            if (node.tango == "EOS" || node.tango == "BOS") continue
            if (node.len.toInt() > length) continue
            val s = table[node] ?: continue
            val base = s.cost[p]
            if (base == Int.MAX_VALUE) continue
            val c = base + connectionMatrix.cost(node.r.toInt(), 0)
            if (c < bestCost) {
                bestCost = c
                bestNode = node
            }
        }
        val last = bestNode ?: return null

        val path = ArrayList<Node>()
        var node: Node = last
        var k = p
        while (node !== BOS) {
            path.add(node)
            val s = table[node] ?: return null
            val prev = s.prevNode[k] ?: return null
            k = s.prevK[k]
            node = prev
        }
        path.reverse()

        val segments = ArrayList<CandidateConversionSegment>(path.size)
        var pos = 0
        val sb = StringBuilder()
        for (n in path) {
            val nextPos = pos + n.len
            segments.add(CandidateConversionSegment(inputStart = pos, inputEnd = nextPos, output = n.tango))
            sb.append(n.tango)
            pos = nextPos
        }
        return Result(surface = sb.toString(), cost = bestCost, segments = segments)
    }

    /** prefix の k 文字目まで一致済みの状態から surface を足したときの一致長。不一致なら -1。 */
    private fun advance(prefix: String, k: Int, surface: String): Int {
        val p = prefix.length
        if (k >= p) return p
        val remaining = p - k
        return if (surface.length >= remaining) {
            if (surface.regionMatches(0, prefix, k, remaining)) p else -1
        } else {
            if (prefix.regionMatches(k, surface, 0, surface.length)) k + surface.length else -1
        }
    }
}

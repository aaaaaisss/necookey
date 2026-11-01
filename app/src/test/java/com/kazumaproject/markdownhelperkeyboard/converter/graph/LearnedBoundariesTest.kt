package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.graph.Node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedBoundariesTest {
    private fun node(tango: String, start: Int, len: Int, score: Int) = Node(
        l = 1, r = 1, score = score, f = score, g = score,
        tango = tango, len = len.toShort(), yomiUsed = tango, sPos = start,
    )

    @Test
    fun cutAndMergeAreKeyedByTwoCharsEachSide() {
        val b = LearnedBoundaries()
        // ここではきものを: 最初 [ここでは][きものを] → 確定 [ここで][はきものを]
        assertTrue(b.record("ここではきものを", listOf(4, 8), listOf(3, 8), now = 0L))
        assertEquals(1, b.entry("こで|はき")!!.steps)
        assertEquals(-1, b.entry("では|きも")!!.steps)
        // 別の文でも同じ文字の並びの境目に効く
        val adj = b.adjustments("あそこではきます", now = 0L).toMap()
        assertEquals(LearnedBoundaries.COST_PER_STEP, adj[4])
        // 変化なしは書かない
        assertFalse(b.record("ここで", listOf(2), listOf(2), now = 0L))
    }

    @Test
    fun stepsAreCappedAndDecay() {
        val b = LearnedBoundaries()
        repeat(10) { b.record("あいうえ", emptyList(), listOf(2), now = 0L) }
        assertEquals(LearnedBoundaries.MAX_STEPS, b.entry("あい|うえ")!!.steps)
        val later = (LearnedBoundaries.HALF_LIFE_DAYS * 86_400_000L * 10).toLong()
        assertTrue(b.adjustments("あいうえ", later).isEmpty())
        val restored = LearnedBoundaries().apply { loadFrom(b.serialize()) }
        assertEquals(LearnedBoundaries.MAX_STEPS, restored.entry("あい|うえ")!!.steps)
    }

    @Test
    fun applyPenalizesOrRewardsOnlyNodesCrossingTheBoundary() {
        val b = LearnedBoundaries()
        b.record("あいうえ", emptyList(), listOf(2), now = 0L) // cut at 2 → +1000
        b.record("かきくけ", listOf(2), emptyList(), now = 0L) // merge at 2 → -1000
        val crossing = node("あいう", 0, 3, 3000)
        val left = node("あい", 0, 2, 3000)
        val right = node("うえ", 2, 2, 3000)
        val graph = mutableMapOf(2 to mutableListOf(left), 3 to mutableListOf(crossing), 4 to mutableListOf(right))
        assertEquals(2, b.applyTo(graph, "あいうえ", now = 0L))
        assertEquals(4000, crossing.score)
        assertEquals(4000, crossing.f)
        assertEquals(3000, left.score)
        assertEquals(3000, right.score)

        val cheap = node("かきく", 0, 3, 500)
        val g2 = mutableMapOf(3 to mutableListOf(cheap))
        assertEquals(2, b.applyTo(g2, "かきくけ", now = 0L))
        assertEquals(0, cheap.score) // 下限 0

        assertEquals(-1, b.applyTo(mutableMapOf(), "さしすせ", now = 0L))
    }
}

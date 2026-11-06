package com.kazumaproject.markdownhelperkeyboard.converter

import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.bitset.rank1GetIntArray
import com.kazumaproject.bitset.rank1GetShortArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId as EnglishLOUDSWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.louds.loudsChildPosition
import com.kazumaproject.prefix.with_term_id.PrefixTreeWithTermId
import com.kazumaproject.preprocessLBSIntoBooleanArray
import com.kazumaproject.toBooleanArray
import java.util.BitSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Upstream Sumire PR #1216: compact dictionaries drop trailing zero words, so select0 on the
 * last leaves fails (-1); the old `select0(...) + 1` re-entered the root and looped (OOM on ちゅう).
 */
class LOUDSBoundaryTest {
    // rank0[i] = [0, i) の 0 の数（rank1GetIntArray と同じ形）
    private fun BitSet.rank0GetIntArray(): IntArray {
        val n = size()
        return IntArray(n + 1).also { r -> for (i in 1..n) r[i] = r[i - 1] + if (this[i - 1]) 0 else 1 }
    }

    private fun BitSet.rank0GetShortArray(): ShortArray {
        val n = size()
        return ShortArray(n + 1).also { r -> for (i in 1..n) r[i] = (r[i - 1] + if (this[i - 1]) 0 else 1).toShort() }
    }

    private val words = (0 until 32).map { "ちゅう${('あ'.code + it).toChar()}" }

    private fun dictionary(): LOUDSWithTermId {
        val tree = PrefixTreeWithTermId().apply { words.forEach(::insert) }
        val converted = ConverterWithTermId().convert(tree.root).apply { convertListToBitSet() }
        // Same as compact asset loading: valueOf drops unused trailing zero words.
        val bits = BitSet.valueOf(converted.LBS.toLongArray())
        return LOUDSWithTermId(bits, converted.getAllLabels(), converted.isLeaf, converted.termIds.toIntArray())
    }

    @Test fun childPositionRejectsFailedSelectAndBackwardJumps() {
        val bits = BitSet().apply { set(0); set(2); set(3) }
        assertEquals(-1, loudsChildPosition(bits, 5, -1))
        assertEquals(-1, loudsChildPosition(bits, 5, 1)) // child 2 is before parent 5
        assertEquals(-1, loudsChildPosition(bits, -1, 1))
        assertEquals(2, loudsChildPosition(bits, 0, 1))
        assertEquals(-1, loudsChildPosition(bits, 0, 3)) // bit 4 is not set
    }

    @Test fun predictionStopsAtFinalLeavesAcrossAllIndexes() {
        val trie = dictionary()
        val bits = trie.LBS
        val index = SuccinctBitVector(bits)
        val english = EnglishLOUDSWithTermId(bits, trie.getAllLabels(), trie.isLeaf, trie.getAllTermIds())
        for (prefix in listOf("ちゅう", words.last())) {
            val expected = words.filter { it.startsWith(prefix) }
            assertEquals(expected, trie.predictiveSearch(prefix, index))
            assertEquals(expected, trie.predictiveSearch(prefix, bits.rank0GetIntArray(), bits.rank1GetIntArray()))
            assertEquals(expected, trie.predictiveSearch(prefix, bits.rank0GetShortArray(), bits.rank1GetShortArray()))
            assertEquals(expected, english.predictiveSearch(prefix, index))
            assertEquals(expected, english.predictiveSearch(prefix, bits.rank0GetIntArray(), bits.rank1GetIntArray()))
        }
    }

    @Test fun exactLookupCannotContinueFromFinalLeafViaRootSentinel() {
        val common = dictionary()
        val english = EnglishLOUDSWithTermId(common.LBS, common.getAllLabels(), common.isLeaf, common.getAllTermIds())
        val lbs = SuccinctBitVector(common.LBS)
        val ints = common.LBS.rank1GetIntArray()
        val shorts = common.LBS.rank1GetShortArray()
        val preprocess = common.LBS.toBooleanArray().preprocessLBSIntoBooleanArray()
        val lookups = listOf<(String) -> Int>(
            { common.getNodeIndex(it, lbs) }, { english.getNodeIndex(it, lbs) },
            { common.getNodeIndex(it, ints, preprocess) }, { english.getNodeIndex(it, ints, preprocess) },
            { common.getNodeIndex(it, shorts, preprocess) }, { english.getNodeIndex(it, shorts, preprocess) },
        )
        for (lookup in lookups) {
            assertTrue(lookup(words.last()) > 0)
            for (missing in listOf(words.last() + "!", words.last() + "ちゅうあ")) {
                assertEquals(missing, -1, lookup(missing))
            }
        }
    }
}

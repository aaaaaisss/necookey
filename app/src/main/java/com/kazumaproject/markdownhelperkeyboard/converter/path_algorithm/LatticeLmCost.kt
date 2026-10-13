package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import java.io.DataInputStream
import java.io.InputStream

/**
 * 案2: 経路探索の辺コストに足す単語 bigram LM 補正（SUMIRE-LM、既定無効）。
 *
 *   corr(prev -> node) = rint(LAM * (-lp(node | prev)) + MU + OMEGA * [node が語彙外])   （node = EOS なら 0）
 *   lp(w | v) = 対 (v, w) があれば q(log p2(w|v))、なければ q(log alpha(v)) + q(log p1(w))、prev = BOS なら q(log p1(w))
 *   語彙外の w は p1 = floor、語彙外の v は alpha = 1。q() は 1/QS nat 単位の int8。
 *
 * 語のキーはノード表記の [wordHash]（FNV-1a + fmix32。端末に語彙文字列は不要。String.hashCode は短い CJK 文字列で衝突が多すぎる）、
 * 対のキーは [pairKey] の 32bit 指紋。
 * 表は tools/sumire_lm/lcsim.py `run ... export=PATH` が書く SLC2 形式（ビッグエンディアン）:
 *   "SLC2", int nWords, int nPairs, float LAM, MU, OMEGA, QS, byte lfloorQ,
 *   int[nWords] 語ハッシュ, byte[nWords] lp1Q, byte[nWords] laQ, int[nPairs] 対指紋, byte[nPairs] lp2Q
 * [table] が null（既定）のときは FindPath の辺コストは従来と完全に同じ。
 */
object LatticeLmCost {
    @Volatile
    @JvmStatic
    var table: Table? = null

    /** FindPath.getEdgeCost から呼ぶ。表なし・EOS への辺は 0。 */
    @JvmStatic
    fun correction(prev: Node, node: Node): Int {
        val t = table ?: return 0
        if (node.mozcNodeType == MozcNodeType.EOS || node.tango == "EOS") return 0
        val bos = prev.mozcNodeType == MozcNodeType.BOS || prev.tango == "BOS"
        return t.correction(if (bos) null else prev.tango, node.tango)
    }

    @JvmStatic
    fun wordHash(s: String): Int {
        var h = 0x811C9DC5.toInt()
        for (i in s.indices) {
            h = (h xor s[i].code) * 0x01000193
        }
        h = h xor (h ushr 16)
        h *= 0x85EBCA6B.toInt()
        h = h xor (h ushr 13)
        h *= 0xC2B2AE35.toInt()
        return h xor (h ushr 16)
    }

    @JvmStatic
    fun pairKey(hv: Int, hw: Int): Int {
        var x = hv * 0x9E3779B1.toInt() + hw
        x = x xor (x ushr 16)
        x *= 0x85EBCA6B.toInt()
        x = x xor (x ushr 13)
        x *= 0xC2B2AE35.toInt()
        x = x xor (x ushr 16)
        return if (x == 0) 1 else x
    }

    @JvmStatic
    fun load(input: InputStream): Table = Table.read(input)

    class Table internal constructor(
        val lam: Float,
        val mu: Float,
        val omega: Float,
        val qs: Float,
        private val lfloorQ: Int,
        private val wordKeys: IntArray,   // open addressing, 0 = empty slot (hash 0 stored in zeroWordIndex)
        private val wordIdx: IntArray,
        private val zeroWordIndex: Int,
        private val lp1: ByteArray,
        private val la: ByteArray,
        private val pairKeys: IntArray,   // open addressing, 0 = empty (fingerprints are never 0)
        private val pairVals: ByteArray,
        val wordCount: Int,
        val pairCount: Int,
    ) {
        private val wordMask = wordKeys.size - 1
        private val pairMask = pairKeys.size - 1

        /** 語ハッシュ h の語番号（なければ -1）。 */
        fun wordIndex(h: Int): Int {
            if (h == 0) return zeroWordIndex
            var i = mix(h) and wordMask
            while (true) {
                val k = wordKeys[i]
                if (k == 0) return -1
                if (k == h) return wordIdx[i]
                i = (i + 1) and wordMask
            }
        }

        /** 対の lp2Q（なければ Int.MIN_VALUE）。 */
        fun pairValue(fp: Int): Int {
            var i = mix(fp) and pairMask
            while (true) {
                val k = pairKeys[i]
                if (k == 0) return Int.MIN_VALUE
                if (k == fp) return pairVals[i].toInt()
                i = (i + 1) and pairMask
            }
        }

        /** lp(w | v) を 1/QS nat 単位の整数で（v = null は BOS）。 */
        fun logProbQ(prev: String?, word: String): Int {
            val hw = wordHash(word)
            return logProbQ(prev, hw, wordIndex(hw))
        }

        private fun logProbQ(prev: String?, hw: Int, wi: Int): Int {
            val lp1w = if (wi >= 0) lp1[wi].toInt() else lfloorQ
            if (prev == null) return lp1w
            val hv = wordHash(prev)
            val vi = wordIndex(hv)
            if (wi >= 0 && vi >= 0) {
                val p = pairValue(pairKey(hv, hw))
                if (p != Int.MIN_VALUE) return p
            }
            return (if (vi >= 0) la[vi].toInt() else 0) + lp1w
        }

        fun isKnown(word: String): Boolean = wordIndex(wordHash(word)) >= 0

        fun correction(prev: String?, word: String): Int {
            val hw = wordHash(word)
            val wi = wordIndex(hw)
            val lpq = logProbQ(prev, hw, wi)
            val oov = if (wi >= 0) 0.0 else omega.toDouble()
            return Math.rint(lam.toDouble() * (-lpq / qs.toDouble()) + mu.toDouble() + oov).toInt()
        }

        companion object {
            private fun mix(h: Int): Int {
                var x = h * -0x61c88647
                x = x xor (x ushr 15)
                return x
            }

            private fun capacity(n: Int): Int {
                var c = 16
                while (c < n * 2) c = c shl 1
                return c
            }

            internal fun read(input: InputStream): Table {
                val din = DataInputStream(input.buffered(1 shl 16))
                val magic = ByteArray(4).also { din.readFully(it) }
                require(String(magic, Charsets.US_ASCII) == "SLC2") { "not an SLC2 table" }
                val nw = din.readInt()
                val np = din.readInt()
                val lam = din.readFloat()
                val mu = din.readFloat()
                val omega = din.readFloat()
                val qs = din.readFloat()
                val lfloor = din.readByte().toInt()
                val hashes = IntArray(nw) { din.readInt() }
                val lp1 = ByteArray(nw).also { din.readFully(it) }
                val la = ByteArray(nw).also { din.readFully(it) }
                val wk = IntArray(capacity(nw))
                val wi = IntArray(wk.size)
                val wm = wk.size - 1
                var zero = -1
                for (j in 0 until nw) {
                    val h = hashes[j]
                    if (h == 0) { zero = j; continue }
                    var i = mix(h) and wm
                    while (wk[i] != 0 && wk[i] != h) i = (i + 1) and wm
                    wk[i] = h; wi[i] = j
                }
                val pk = IntArray(capacity(np))
                val pv = ByteArray(pk.size)
                val pm = pk.size - 1
                val fps = IntArray(np) { din.readInt() }
                val vals = ByteArray(np).also { din.readFully(it) }
                for (j in 0 until np) {
                    val f = fps[j]
                    var i = mix(f) and pm
                    while (pk[i] != 0 && pk[i] != f) i = (i + 1) and pm
                    pk[i] = f; pv[i] = vals[j]
                }
                return Table(lam, mu, omega, qs, lfloor, wk, wi, zero, lp1, la, pk, pv, nw, np)
            }
        }
    }
}

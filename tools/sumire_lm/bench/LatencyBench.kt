// SUMIRE-LM on-device proxy: rerank Sumire top-50 with the non-lexical linear model using pruned/quantized tables.
// Feature definitions mirror rr.py (seg tokenization: candidate = Sumire's word split, context = unigram Viterbi).
// usage: kotlinc LatencyBench.kt -include-runtime -d bench.jar && java -jar bench.jar EXPORT_DIR [rounds=3]
// Reports top1 on held/eval, agreement with nothing (Python scores are compared offline), and per-sentence latency
// (context segmentation + features for all candidates + scoring), median / p95, after JIT warm-up.
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min

const val P: Long = -7046029254386353131L // 0x9E3779B97F4A7C15

class Table(dir: String, name: String, withNf: Boolean, withNl: Boolean) {
    val h: LongArray = readLongs(File(dir, "$name.h"))
    val c: FloatArray = readFloats(File(dir, "$name.c"))
    val nf: FloatArray? = if (withNf && File(dir, "$name.nf").exists()) readFloats(File(dir, "$name.nf")) else null
    val nl: FloatArray? = if (withNl && File(dir, "$name.nl").exists()) readFloats(File(dir, "$name.nl")) else null
    fun find(key: Long): Int { // unsigned binary search
        var lo = 0; var hi = h.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cmp = java.lang.Long.compareUnsigned(h[mid], key)
            if (cmp < 0) lo = mid + 1 else if (cmp > 0) hi = mid - 1 else return mid
        }
        return -1
    }
}
fun readLongs(f: File): LongArray { val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN); return LongArray(b.capacity() / 8) { b.getLong(it * 8) } }
fun readFloats(f: File): FloatArray { val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN); return FloatArray(b.capacity() / 4) { b.getFloat(it * 4) } }

class Cand(val s: String, val cost: Int, val rank: Int, val nseg: Int, val type: Int, val toks: List<String>)
class Sent(val id: String, val split: String, val ctx: String, val bos: Boolean, val exp: Set<String>, val cands: List<Cand>)

class LmOut(val lp: DoubleArray, val sb: Double, val oov: Int, val hit: IntArray)

fun main(args: Array<String>) {
    val dir = args[0]; val rounds = args.getOrNull(1)?.toInt() ?: 3
    val t0 = System.nanoTime()
    val w = (1..4).map { Table(dir, "w$it", it < 4, it == 1) }
    val c = (1..7).map { Table(dir, "c$it", false, false) }
    val pair = Table(dir, "pair", false, false)
    val meta = File(dir, "meta.json").readText()
    fun mv(k: String) = Regex("\"$k\": ([0-9.e+]+)").find(meta)!!.groupValues[1].toDouble()
    val Nw = mv("Nw"); val Nc = mv("Nc"); val nlt = mv("nlt"); val V = mv("V")
    val vocab = HashMap<String, Int>(1 shl 21); val lex = HashMap<String, Double>(1 shl 19); var maxL = 1
    File(dir, "vocab.tsv").forEachLine { l ->
        val f = l.split('\t'); vocab[f[0]] = f[1].toInt()
        val cst = f[2].toDouble(); if (cst >= 0) { lex[f[0]] = cst; maxL = max(maxL, f[0].length) }
    }
    val unkc = -ln(0.5 / Nw) + 5
    val model = File(dir, "model.tsv").readLines().map { it.split('\t') }
    val names = model.map { it[0] }; val mu = model.map { it[1].toDouble() }; val sd = model.map { it[2].toDouble() }; val wt = model.map { it[3].toDouble() }
    val sents = ArrayList<Sent>()
    run {
        val lines = File(dir, "sents.tsv").readLines(); var i = 0
        while (i < lines.size) {
            val f = lines[i].split('\t'); val n = f[5].toInt()
            val cs = (1..n).map { j -> val g = lines[i + j].split('\t'); Cand(g[0], g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), if (g[5].isEmpty()) emptyList() else g[5].split(' ')) }
            sents.add(Sent(f[1], f[2], f[3], f[4] == "1", f[6].split('|').toSet(), cs)); i += n + 1
        }
    }
    System.err.println("loaded in ${(System.nanoTime() - t0) / 1_000_000} ms; sentences ${sents.size}")

    fun viterbi(s: String): List<String> {
        val n = s.length; val best = DoubleArray(n + 1) { 1e18 }; val bp = IntArray(n + 1); best[0] = 0.0
        for (i in 0 until n) {
            if (best[i] >= 1e18) continue
            for (L in 1..min(maxL, n - i)) {
                val cst = lex[s.substring(i, i + L)] ?: if (L == 1) unkc else continue
                if (best[i] + cst < best[i + L]) { best[i + L] = best[i] + cst; bp[i + L] = i }
            }
        }
        val out = ArrayList<String>(); var j = n
        while (j > 0) { out.add(s.substring(bp[j], j)); j = bp[j] }
        return out.reversed()
    }
    // interpolated absolute discounting (word, KN unigram) / Dirichlet (char, raw unigram) + stupid backoff, mirrors lmscore.py
    fun lm(x: LongArray, tabs: List<Table>, K: Int, ds: DoubleArray, word: Boolean, kOrders: IntArray): Array<LmOut> {
        // returns one LmOut per requested order cap (kOrders), each with lp per discount (only for cap == K) ...
        val n = x.size; val res = Array(kOrders.size) { LmOut(DoubleArray(ds.size), 0.0, 0, IntArray(K)) }
        val hs = Array(K) { LongArray(n) } // hs[k-1][j] = hash of x[j..j+k-1]
        for (j in 0 until n) hs[0][j] = x[j]
        for (k in 2..K) for (j in 0..n - k) hs[k - 1][j] = hs[k - 2][j] * P + x[j + k - 1]
        val lpAcc = Array(kOrders.size) { DoubleArray(ds.size) }; val sbAcc = DoubleArray(kOrders.size); val hitAcc = Array(kOrders.size) { IntArray(K) }; var oov = 0
        val ci = IntArray(K); val cnt = DoubleArray(K)
        for (j in 0 until n) {
            if (j == 0 && x[0] == 0L) continue
            // counts of the k-grams ending at j and of their contexts
            for (k in 1..K) { if (j - k + 1 < 0) { ci[k - 1] = -2; continue }; val id = tabs[k - 1].find(hs[k - 1][j - k + 1]); ci[k - 1] = id; cnt[k - 1] = if (id >= 0) tabs[k - 1].c[id].toDouble() else 0.0 }
            val c1 = cnt[0]; if (c1 == 0.0) oov++
            val p1 = if (word) ((if (ci[0] >= 0) tabs[0].nl!![ci[0]].toDouble() else 0.0) + 0.5) / (nlt + 0.5 * V) else (c1 + 0.5) / (Nc + 0.5 * 30000)
            val N = if (word) Nw else Nc
            for ((oi, cap) in kOrders.withIndex()) {
                val dlist = if (cap == K) ds else doubleArrayOf(if (word) 0.75 else 2.0)
                for ((di, d) in dlist.withIndex()) {
                    var p = p1
                    for (k in 2..cap) {
                        if (j - k + 1 < 0) break
                        val cc = cnt[k - 2]; val cf = cnt[k - 1]
                        p = if (word) {
                            if (cc > 0) { val nf = tabs[k - 2].nf?.let { a -> if (ci[k - 2] >= 0) a[ci[k - 2]].toDouble() else 0.0 } ?: 0.0
                                max(cf - d, 0.0) / cc + d * nf / cc * p } else p
                        } else (cf + d * p) / (cc + d)
                    }
                    lpAcc[oi][di] += ln(p)
                }
                if (cap == K) {
                    var sb = ln(max(c1, 0.5) / N); var best = if (c1 > 0) 1 else 0
                    for (k in 2..K) { if (j - k + 1 < 0) break; val cc = cnt[k - 2]; val cf = cnt[k - 1]
                        sb = if (cf > 0) ln(max(cf, 1.0) / max(cc, 1.0)) else ln(0.4) + sb; if (cf > 0) best = k }
                    sbAcc[oi] += sb; if (best > 0) hitAcc[oi][best - 1]++
                }
            }
        }
        for (oi in kOrders.indices) res[oi] = LmOut(lpAcc[oi], sbAcc[oi], oov, hitAcc[oi])
        return res
    }
    val KAN = Regex("[一-龠々〆ヵヶ]"); val HIRA = Regex("[ぁ-ゖ]"); val KATA = Regex("[ァ-ヺー]"); val DIG = Regex("[0-9０-９]"); val KNUM = Regex("[〇一二三四五六七八九十百千万億]")
    val NUM = Regex("^[0-9〇一二三四五六七八九十百千万億]+$"); val CONT = Regex("[一-龠々ァ-ヺー]"); val ASCII2 = Regex("[A-Za-z]{2,}")
    fun content(t: String) = !NUM.matches(t) && (CONT.containsMatchIn(t) || ASCII2.matches(t))
    fun nfkc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC)

    fun rerank(s: Sent): Int {
        val nctx = nfkc(s.ctx); val ct = if (nctx.isEmpty()) emptyList() else viterbi(nctx)
        val A = ct.filter { content(it) }.toSet().mapNotNull { vocab[it] }
        val csets = s.cands.map { cd -> cd.toks.filter { content(it) }.toSet() }
        val common = csets.reduce { a, b -> a intersect b }
        val c0 = s.cands[0].cost
        var bestI = 0; var bestS = Double.NEGATIVE_INFINITY
        val f = HashMap<String, Double>(64)
        for ((ri, cd) in s.cands.withIndex()) {
            f.clear()
            val wids = ArrayList<Long>(); if (s.bos) wids.add(0L)
            for (t in ct) wids.add((vocab[t] ?: -1).toLong() and 0xFFFFFFFFL)
            for (t in cd.toks) wids.add((vocab[t] ?: -1).toLong() and 0xFFFFFFFFL)
            val wx = wids.toLongArray()
            val wo = lm(wx, w, 4, doubleArrayOf(0.6, 0.75, 0.9), true, intArrayOf(4, 3, 2))
            f["w4_0.6"] = wo[0].lp[0]; f["w4_0.75"] = wo[0].lp[1]; f["w4_0.9"] = wo[0].lp[2]; f["w3"] = wo[1].lp[0]; f["w2"] = wo[2].lp[0]
            f["w4sb"] = wo[0].sb; f["woov"] = wo[0].oov.toDouble(); for (k in 1..4) f["whit$k"] = wo[0].hit[k - 1].toDouble()
            val chars = nfkc(s.ctx + cd.s); val cxs = ArrayList<Long>(); if (s.bos) cxs.add(0L)
            var i = 0; while (i < chars.length) { val cp = chars.codePointAt(i); cxs.add(cp.toLong()); i += Character.charCount(cp) }
            val cx = cxs.toLongArray()
            val co = lm(cx, c, 7, doubleArrayOf(0.5, 2.0, 8.0), false, intArrayOf(7, 5, 3))
            f["c7_0.5"] = co[0].lp[0]; f["c7_2"] = co[0].lp[1]; f["c7_8"] = co[0].lp[2]; f["c5"] = co[1].lp[0]; f["c3"] = co[2].lp[0]
            f["c7sb"] = co[0].sb; for (k in 1..7) f["chit$k"] = co[0].hit[k - 1].toDouble()
            // PMI over distinguishing content words
            var psum = 0.0; var pmax = Double.NEGATIVE_INFINITY; var pn = 0; var ls = 0.0
            for (wd in csets[ri] - common) {
                val b = vocab[wd] ?: continue; if (A.isEmpty()) continue
                var best = -1e9
                val ub = w[0].find(b.toLong()).let { if (it >= 0) w[0].c[it].toDouble() else 0.0 }
                for (a in A) {
                    val id = pair.find(a.toLong() * P + b.toLong()); val cab = if (id >= 0) pair.c[id].toDouble() else 0.0
                    val ua = w[0].find(a.toLong()).let { if (it >= 0) w[0].c[it].toDouble() else 0.0 }
                    val pmi = ln((cab + 0.1) * Nw / (max(ua, 1.0) * max(ub, 1.0) * 30)); best = max(best, pmi); ls += ln1p(cab)
                }
                psum += max(best, 0.0); pmax = max(pmax, best); pn++
            }
            f["pmi_sum"] = psum; f["pmi_max"] = if (pn > 0) pmax else 0.0; f["pmi_n"] = pn.toDouble(); f["cooc"] = ls
            val st = cd.s
            f["cost"] = cd.cost / 1000.0; f["rel"] = (cd.cost - c0) / 1000.0; f["rank"] = ln1p(cd.rank.toDouble())
            f["ntok"] = cd.toks.size.toDouble(); f["nseg"] = cd.nseg.toDouble(); f["nch"] = st.length.toDouble()
            f["nkan"] = KAN.findAll(st).count().toDouble(); f["nhira"] = HIRA.findAll(st).count().toDouble(); f["nkata"] = KATA.findAll(st).count().toDouble()
            f["ndig"] = DIG.findAll(st).count().toDouble(); f["nknum"] = KNUM.findAll(st).count().toDouble(); f["typ1"] = if (cd.type == 1) 1.0 else 0.0
            var sc = 0.0
            for (k in names.indices) sc += wt[k] * ((f[names[k]] ?: 0.0) - mu[k]) / sd[k]
            if (sc > bestS) { bestS = sc; bestI = ri }
        }
        return bestI
    }
    var lat = LongArray(0); val picks = IntArray(sents.size)
    for (r in 0 until rounds) {
        lat = LongArray(sents.size)
        for ((si, s) in sents.withIndex()) { val a = System.nanoTime(); picks[si] = rerank(s); lat[si] = System.nanoTime() - a }
    }
    val acc = HashMap<String, IntArray>()
    for ((si, s) in sents.withIndex()) { val a = acc.getOrPut(s.split) { IntArray(2) }; a[0]++; if (s.cands[picks[si]].s in s.exp) a[1]++ }
    acc.forEach { (k, v) -> println("$k top1 ${"%.3f".format(v[1].toDouble() / v[0])} (n=${v[0]})") }
    val sorted = lat.sorted()
    println("latency per sentence (ctx segmentation + features for ${"%.1f".format(sents.map { it.cands.size }.average())} cands + scoring), last of $rounds rounds:")
    println("  median ${"%.2f".format(sorted[sorted.size / 2] / 1e6)} ms  p95 ${"%.2f".format(sorted[(sorted.size * 95) / 100] / 1e6)} ms  max ${"%.2f".format(sorted.last() / 1e6)} ms")
    File(dir, "kotlin_picks.tsv").writeText(sents.indices.joinToString("") { "${sents[it].id}\t${picks[it]}\n" })
}

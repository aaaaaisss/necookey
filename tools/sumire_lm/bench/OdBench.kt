// SUMIRE-LM on-device latency bench (pruned + quantized + fingerprinted tables, export_od.py layout).
// Reranks Sumire top-50 with the non-lexical linear model; features mirror odsim.py (Sumire-seg variant: candidate tokens =
// Sumire's word split, context = unigram Viterbi over the word-unigram lexicon). Single thread.
// build: kotlinc OdBench.kt -include-runtime -d odbench.jar     run: java -Xss16m -jar odbench.jar EXPORT_DIR [rounds=5]
// Android (rough): d8 odbench.jar -> classes.dex, adb push classes.dex + EXPORT_DIR to /data/local/tmp, then
//   adb shell 'cd /data/local/tmp && dalvikvm -cp classes.dex OdBenchKt ./EXPORT_DIR 5'
// Prints load time + heap, top1 on held/eval, agreement with the Python picks (py_picks.tsv), latency median/p95 per sentence.
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min

const val P: Long = -7046029254386353131L // 0x9E3779B97F4A7C15 (n-gram hash multiplier)

fun splitmix(h: Long): Long {
    var x = h + -7046029254386353131L
    x = (x xor (x ushr 30)) * -4658895280553007687L
    x = (x xor (x ushr 27)) * -7723592293110705685L
    return x xor (x ushr 31)
}
fun bytes(f: File): ByteBuffer = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)

class HTable(dir: String, val name: String, fpBits: Int) {
    val base: IntArray; val rel: CharArray; val fp: CharArray
    val lp: ByteArray?; val bw: ByteArray?; val cnt: ByteArray?
    val B: Long; val n: Int; val mask = (1L shl fpBits) - 1
    init {
        val b = bytes(File(dir, "$name.dir")); base = IntArray(b.capacity() / 4) { b.getInt(it * 4) }
        val r = bytes(File(dir, "$name.rel")); rel = CharArray(r.capacity() / 2) { r.getChar(it * 2) }
        val f = bytes(File(dir, "$name.fp")); fp = CharArray(f.capacity() / 2) { f.getChar(it * 2) }
        fun opt(ext: String) = File(dir, "$name.$ext").let { if (it.exists()) it.readBytes() else null }
        lp = opt("lp"); bw = opt("bw"); cnt = opt("cnt"); B = rel.size.toLong(); n = fp.size
    }
    fun find(h: Long): Int {
        val m = splitmix(h); val b = (((m ushr 32) * B) ushr 32).toInt(); val f = (m and mask).toInt()
        val s = base[b ushr 8] + rel[b].code
        val e = if (b + 1 < B) base[(b + 1) ushr 8] + rel[b + 1].code else n
        for (i in s until e) if (fp[i].code == f) return i
        return -1
    }
    fun bytesUsed(): Long = base.size * 4L + rel.size * 2L + fp.size * 2L + (lp?.size ?: 0) + (bw?.size ?: 0) + (cnt?.size ?: 0)
}

class Cand(val s: String, val cost: Int, val rank: Int, val nseg: Int, val type: Int, val toks: List<String>)
class Sent(val id: String, val split: String, val ctx: String, val bos: Boolean, val exp: Set<String>, val cands: List<Cand>)

fun main(args: Array<String>) {
    val dir = args[0]; val rounds = args.getOrNull(1)?.toInt() ?: 5
    val meta = File(dir, "meta.tsv").readLines().associate { val f = it.split('\t'); f[0] to f[1] }
    val fpBits = meta["fp"]!!.toInt(); val Kc = meta["Kc"]!!.toInt()
    val Nw = meta["Nw"]!!.toDouble(); val unkW = meta["unk_w"]!!.toDouble(); val unkC = meta["unk_c"]!!.toDouble()
    val cb = File(dir, "cb.tsv").readLines().associate { val f = it.split('\t'); f[0] to f[1].split(',').map { v -> v.toDouble() }.toDoubleArray() }
    val rt = Runtime.getRuntime(); System.gc(); val mem0 = rt.totalMemory() - rt.freeMemory()
    val t0 = System.nanoTime()
    val wt = (1..4).map { HTable(dir, "w$it", fpBits) }; val ct = (1..Kc).map { HTable(dir, "c$it", fpBits) }; val pt = HTable(dir, "p", fpBits)
    val tLoad = (System.nanoTime() - t0) / 1e6
    System.gc(); val mem1 = rt.totalMemory() - rt.freeMemory()
    val tabBytes = (wt + ct + pt).sumOf { it.bytesUsed() }
    val wLp = (1..4).map { cb["w$it.lp"]!! }; val wBw = (1..3).map { cb["w$it.bw"]!! }
    val cLp = (1..Kc).map { cb["c$it.lp"]!! }; val cBw = (1 until Kc).map { cb["c$it.bw"]!! }
    val wCnt = cb["wcnt"]!!; val pCnt = cb["p.cnt"]!!
    val t1 = System.nanoTime()
    val vocab = HashMap<String, Int>(1 shl 21); val lex = HashMap<String, Double>(1 shl 19); var maxL = 1
    File(dir, "vocab.tsv").forEachLine { l ->
        val f = l.split('\t'); vocab[f[0]] = f[1].toInt()
        val cst = f[2].toDouble(); if (cst >= 0) { lex[f[0]] = cst; maxL = max(maxL, f[0].length) }
    }
    val tVocab = (System.nanoTime() - t1) / 1e6
    val unkc = -ln(0.5 / Nw) + 5
    val model = File(dir, "model.tsv").readLines().map { it.split('\t') }
    val names = model.map { it[0] }; val mu = model.map { it[1].toDouble() }; val sd = model.map { it[2].toDouble() }; val wv = model.map { it[3].toDouble() }
    val sents = ArrayList<Sent>()
    run {
        val lines = File(dir, "sents.tsv").readLines(); var i = 0
        while (i < lines.size) {
            val f = lines[i].split('\t'); val n = f[5].toInt()
            val cs = (1..n).map { j -> val g = lines[i + j].split('\t'); Cand(g[0], g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), if (g[5].isEmpty()) emptyList() else g[5].split(' ')) }
            sents.add(Sent(f[1], f[2], f[3], f[4] == "1", f[6].split('|').toSet(), cs)); i += n + 1
        }
    }
    println("tables loaded in %.0f ms, %.1f MB in memory (heap delta %.1f MB); vocab/lexicon map %.0f ms (bench only: on device the word key is a string hash)".format(tLoad, tabBytes / 1e6, (mem1 - mem0) / 1e6, tVocab))

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
    val LN04 = ln(0.4)
    // ARPA backoff scoring over positions >= from; f = feature map, pre = "w"|"c", caps e.g. [4,3,2]
    fun lm(x: LongArray, from: Int, tabs: List<HTable>, K: Int, lpc: List<DoubleArray>, bwc: List<DoubleArray>, unk: Double, caps: IntArray, pre: String, f: HashMap<String, Double>) {
        val n = x.size; val lo = max(0, from - 1)
        val found = Array(K) { IntArray(n) { -1 } }
        for (j in lo until n) {
            for (k in 1..min(K, j + 1)) {
                var h = x[j - k + 1]; for (t in 1 until k) h = h * P + x[j - k + 1 + t]
                found[k - 1][j] = tabs[k - 1].find(h)
            }
        }
        val acc = DoubleArray(caps.size); var sb = 0.0; val hit = IntArray(K); var oov = 0
        for (j in from until n) {
            if (j == 0 && x[0] == 0L) continue
            for ((ci, cap) in caps.withIndex()) {
                val Kj = min(cap, j + 1); var m = 0
                for (k in Kj downTo 1) if (found[k - 1][j] >= 0) { m = k; break }
                var v = if (m > 0) lpc[m - 1][tabs[m - 1].lp!![found[m - 1][j]].toInt() and 0xFF] else unk
                if (ci == 0) { sb += v + (Kj - m) * LN04; if (m > 0) hit[m - 1]++; if (found[0][j] < 0) oov++ }
                for (k in max(m, 1) until Kj) { val id = found[k - 1][j - 1]; if (id >= 0) v += bwc[k - 1][tabs[k - 1].bw!![id].toInt() and 0xFF] }
                acc[ci] += v
            }
        }
        for ((ci, cap) in caps.withIndex()) f["$pre$cap"] = acc[ci]
        f["${pre}sb"] = sb; for (k in 1..K) f["${pre}hit$k"] = hit[k - 1].toDouble(); if (pre == "w") f["woov"] = oov.toDouble()
    }
    val KAN = Regex("[一-龠々〆ヵヶ]"); val HIRA = Regex("[ぁ-ゖ]"); val KATA = Regex("[ァ-ヺー]"); val DIG = Regex("[0-9０-９]"); val KNUM = Regex("[〇一二三四五六七八九十百千万億]")
    val NUM = Regex("^[0-9〇一二三四五六七八九十百千万億]+$"); val CONT = Regex("[一-龠々ァ-ヺー]"); val ASCII2 = Regex("[A-Za-z]{2,}")
    fun content(t: String) = !NUM.matches(t) && (CONT.containsMatchIn(t) || ASCII2.matches(t))
    fun nfkc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC)
    fun wid(t: String) = (vocab[t]?.toLong() ?: 0xFFFFFFFFL)
    fun ucount(id: Long): Double { val i = wt[0].find(id); return if (i >= 0) exp(wCnt[wt[0].cnt!![i].toInt() and 0xFF]) else 0.0 }
    val wcaps = intArrayOf(4, 3, 2); val ccaps = if (Kc > 5) intArrayOf(Kc, 5, 3) else intArrayOf(Kc, 3)

    fun rerank(s: Sent): Int {
        val nctx = nfkc(s.ctx); val ctxT = if (nctx.isEmpty()) emptyList() else viterbi(nctx)
        val A = ctxT.filter { content(it) }.mapNotNull { vocab[it] }.toSortedSet().map { it.toLong() }
        val uA = A.map { ucount(it) }
        val csets = s.cands.map { cd -> cd.toks.filter { content(it) }.toSet() }
        val common = csets.reduce { a, b -> a intersect b }
        val ctxIds = ArrayList<Long>(); if (s.bos) ctxIds.add(0L); for (t in ctxT) ctxIds.add(wid(t))
        val cctx = ArrayList<Long>(); if (s.bos) cctx.add(0L); run { var i = 0; while (i < nctx.length) { val cp = nctx.codePointAt(i); cctx.add(cp.toLong()); i += Character.charCount(cp) } }
        val c0 = s.cands[0].cost; var bestI = 0; var bestS = Double.NEGATIVE_INFINITY
        val f = HashMap<String, Double>(64)
        for ((ri, cd) in s.cands.withIndex()) {
            f.clear()
            val wx = LongArray(ctxIds.size + cd.toks.size); for (i in ctxIds.indices) wx[i] = ctxIds[i]; for ((i, t) in cd.toks.withIndex()) wx[ctxIds.size + i] = wid(t)
            lm(wx, ctxIds.size, wt, 4, wLp, wBw, unkW, wcaps, "w", f)
            val chars = nfkc(s.ctx + cd.s); val cx = ArrayList<Long>(cctx.size + 16); if (s.bos) cx.add(0L)
            run { var i = 0; while (i < chars.length) { val cp = chars.codePointAt(i); cx.add(cp.toLong()); i += Character.charCount(cp) } }
            lm(cx.toLongArray(), cctx.size, ct, Kc, cLp, cBw, unkC, ccaps, "c", f)
            var psum = 0.0; var pmax = Double.NEGATIVE_INFINITY; var pn = 0; var ls = 0.0
            if (A.isNotEmpty()) for (wd in (csets[ri] - common).sorted()) {
                val b = vocab[wd]?.toLong() ?: continue; var best = -1e9; val ub = ucount(b)
                for ((ai, a) in A.withIndex()) {
                    val id = pt.find(a * P + b); val cq = if (id >= 0) exp(pCnt[pt.cnt!![id].toInt() and 0xFF]) - 1 else 0.0
                    val pmi = ln((cq + 0.1) * Nw / (max(uA[ai], 1.0) * max(ub, 1.0) * 30)); best = max(best, pmi); ls += ln1p(cq)
                }
                psum += max(best, 0.0); pmax = max(pmax, best); pn++
            }
            f["pmi_sum"] = psum; f["pmi_max"] = if (pn > 0) pmax else 0.0; f["pmi_n"] = pn.toDouble(); f["cooc"] = ls
            val st = cd.s
            f["cost"] = cd.cost / 1000.0; f["rel"] = (cd.cost - c0) / 1000.0; f["rank"] = ln1p(cd.rank.toDouble())
            f["ntok"] = cd.toks.size.toDouble(); f["nseg"] = cd.nseg.toDouble(); f["nch"] = st.codePointCount(0, st.length).toDouble()
            f["nkan"] = KAN.findAll(st).count().toDouble(); f["nhira"] = HIRA.findAll(st).count().toDouble(); f["nkata"] = KATA.findAll(st).count().toDouble()
            f["ndig"] = DIG.findAll(st).count().toDouble(); f["nknum"] = KNUM.findAll(st).count().toDouble(); f["typ1"] = if (cd.type == 1) 1.0 else 0.0
            var sc = 0.0
            for (k in names.indices) sc += wv[k] * ((f[names[k]] ?: 0.0) - mu[k]) / sd[k]
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
    acc.toSortedMap().forEach { (k, v) -> println("$k top1 ${"%.3f".format(v[1].toDouble() / v[0])} (n=${v[0]})") }
    val py = File(dir, "py_picks.tsv").readLines().associate { val f = it.split('\t'); f[0] to f[1].toInt() }
    val agree = sents.indices.count { py[sents[it].id] == picks[it] }
    println("agreement with Python picks: $agree / ${sents.size} = ${"%.4f".format(agree.toDouble() / sents.size)}")
    val sorted = lat.sorted()
    println("latency per sentence (ctx Viterbi + features for ${"%.1f".format(sents.map { it.cands.size }.average())} cands + scoring), round $rounds of $rounds, single thread:")
    println("  median %.3f ms  p95 %.3f ms  max %.3f ms".format(sorted[sorted.size / 2] / 1e6, sorted[(sorted.size * 95) / 100] / 1e6, sorted.last() / 1e6))
    File(dir, "kotlin_picks.tsv").writeText(sents.indices.joinToString("") { "${sents[it].id}\t${picks[it]}\n" })
}

package com.kazumaproject.markdownhelperkeyboard.converter.sumirelm

import java.io.File
import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min

/**
 * SUMIRE-LM v3 汎用表形式（meta.tsv の format = typed1）。表は packed1 と同じ詰め込み表（[SumireLmReranker.PTable]）で、
 * どの表をどの特徴量に使うかは feats.tsv の行で決まる（新しい特徴量は表と 1 行を足すだけで、アプリのコード変更は不要）。
 *
 * feats.tsv（タブ区切り、1 行 = 1 ブロック、key=value）:
 *   lm     out=wlp tok=word tables=w1,w2,w3,w4 caps=4,3,2,1 unk=U     語/文字 n-gram の ARPA 後退（lp/bw は表の欄、値は cb.tsv の
 *                                                                        "表名.lp"/"表名.bw" 符号表）→ out{cap}, out_hit{k}, out_oov
 *   lm     out=xlp tok=char tables=x1,..,x5 caps=5,3 unk=U
 *   frame  out=fr table=fr uni=w1 lognt=X                              格フレーム（名詞+格助詞+述語 ≤4 語先、名詞+の+名詞 ≤2、副詞/形容詞+述語 ≤3）
 *   spmi   out=sp table=pr uni=w1 lognt=X                              候補内の差分内容語の最大自己 PMI → out_sum, out_max, out_n
 *   rs     out=rs table=rs                                             表記|読み の対数確率（読みは Input.readings）→ out_sum, out_min, out_kana
 * 語彙 vf: 鍵 = strHash(語)、欄 id, cls（1 名詞 2 格/係助詞 3 動詞 4 形容詞 5 副詞）, cost（文脈分割用 1/128 nat、0 = 分割に使わない）。
 * vf に無い語の id は先頭文字の種別（1 カタカナ 2 英字 3 数字 4 漢字 5 その他）。文脈は vf の cost で単語 Viterbi。
 * 候補ごとの特徴量は名前で model.tsv（name, mu, sd, w）に渡す（知らない名前は 0）。
 */
class SumireLmTyped private constructor(
    private val meta: Map<String, String>,
    private val tabs: Map<String, SumireLmReranker.PTable>,
    private val cb: Map<String, DoubleArray>,
    private val blocks: List<Map<String, String>>,
    /** feats.tsv の sub ブロック（dir=下位ディレクトリ pre=接頭辞）: packed1 の v2 表の特徴量を名前に接頭辞をつけて加える。 */
    private val subs: List<Pair<String, SumireLmReranker>>,
    private val names: Array<String>,
    private val mu: DoubleArray,
    private val sd: DoubleArray,
    private val wv: DoubleArray,
) : CandidateReranker {
    private val vf = tabs.getValue("vf")
    private val maxLex = meta["maxlex"]?.toInt() ?: 12
    private val unkCost = meta["unk_cost"]?.toDouble() ?: 20.0
    private val caseP = setOf("が", "を", "に", "で", "と", "へ", "から", "より", "まで", "は", "も")

    private class Tok(val s: String, val id: Long, val cls: Int, val r: String)

    private fun charCls(c: Char): Int = when {
        c in 'ァ'..'ヺ' || c == 'ー' -> 1; c in 'A'..'Z' || c in 'a'..'z' -> 2; c in '0'..'9' -> 3
        c in '一'..'龠' || c == '々' -> 4; else -> 5
    }

    private fun tok(s: String, r: String): Tok {
        val i = vf.find(SumireLmReranker.strHash(s, 0, s.length))
        return if (i >= 0) Tok(s, vf.field("id", i).toLong(), vf.field("cls", i), r)
        else Tok(s, if (s.isEmpty()) 5L else charCls(s[0]).toLong(), 0, r)
    }

    private fun viterbi(s: String): List<String> {
        val n = s.length
        val best = DoubleArray(n + 1) { 1e18 }; val bp = IntArray(n + 1); best[0] = 0.0
        for (i in 0 until n) {
            if (best[i] >= 1e18) continue
            for (len in 1..min(maxLex, n - i)) {
                val j = vf.find(SumireLmReranker.strHash(s, i, i + len))
                val c = if (j >= 0) vf.field("cost", j) else 0
                val cst = if (c > 0) c / 128.0 else if (len == 1) unkCost else continue
                if (best[i] + cst < best[i + len]) { best[i + len] = best[i] + cst; bp[i + len] = i }
            }
        }
        val out = ArrayList<String>(); var j = n
        while (j > 0) { out.add(s.substring(bp[j], j)); j = bp[j] }
        out.reverse(); return out
    }

    private fun lp(t: String, i: Int) = cb.getValue("$t.lp")[tabs.getValue(t).field("lp", i)]
    private fun bw(t: String, i: Int) = cb.getValue("$t.bw")[tabs.getValue(t).field("bw", i)]
    private fun cnt(t: String, i: Int) = cb.getValue("$t.cnt")[tabs.getValue(t).field("cnt", i)]
    private fun ucount(id: Long, t: String): Double { val i = tabs.getValue(t).find(id); return if (i >= 0) cnt(t, i) else 0.0 }

    /** ARPA 後退（学習側 train3.py の補間値をそのまま lp/bw にしたもの）。x[from..] の各位置の log p を caps ごとに合計。 */
    private fun lm(b: Map<String, String>, x: LongArray, from: Int, f: HashMap<String, Double>) {
        val tn = b.getValue("tables").split(','); val k0 = tn.size; val caps = b.getValue("caps").split(',').map { it.toInt() }
        val unk = b.getValue("unk").toDouble(); val out = b.getValue("out"); val n = x.size
        val found = Array(k0) { IntArray(n) { -1 } }
        for (j in max(0, from - 1) until n) for (k in 1..min(k0, j + 1)) {
            var h = x[j - k + 1]
            for (t in 1 until k) h = h * P + x[j - k + 1 + t]
            found[k - 1][j] = tabs.getValue(tn[k - 1]).find(h)
        }
        val acc = DoubleArray(caps.size); val hit = IntArray(k0 + 1); var oov = 0
        for (j in from until n) {
            for ((ci, cap) in caps.withIndex()) {
                val kj = min(cap, j + 1); var m = 0
                for (k in kj downTo 1) if (found[k - 1][j] >= 0) { m = k; break }
                var v = if (m > 0) lp(tn[m - 1], found[m - 1][j]) else unk
                for (k in max(m, 1) until kj) { val id = found[k - 1][j - 1]; if (id >= 0) v += bw(tn[k - 1], id) }
                acc[ci] += v
                if (ci == 0) { hit[m]++; if (found[0][j] < 0) oov++ }
            }
        }
        for ((ci, cap) in caps.withIndex()) f["$out$cap"] = acc[ci]
        for (k in 1..k0) f["${out}_hit$k"] = hit[k].toDouble()
        f["${out}_oov"] = oov.toDouble()
    }

    private fun mixk(t: Long, a: Long, b: Long, c: Long): Long {
        var x = ((t * P + a) * P + b) * P + c
        x = x xor (x ushr 31); x *= -4658895280553007687L; x = x xor (x ushr 29); return x
    }

    private fun frame(b: Map<String, String>, seq: List<Tok>, p0: Int, f: HashMap<String, Double>) {
        val t = b.getValue("table"); val u = b.getValue("uni"); val lnt = b.getValue("lognt").toDouble(); val out = b.getValue("out")
        val n = seq.size; var fn = 0; var fh = 0; var lc = 0.0; var pm = 0.0
        fun nxt(st: Int, maxd: Int, ok: (Int) -> Boolean): Int { for (d in 1..maxd) { val j = st + d; if (j < n && ok(j)) return j }; return -1 }
        fun add(k: Long, a: Long, c: Long) {
            val i = tabs.getValue(t).find(k); val x = if (i >= 0) cnt(t, i) else 0.0
            fn++; if (x > 0) fh++; lc += ln1p(x); pm += ln(x + 0.5) + lnt - ln(ucount(a, u) + 1) - ln(ucount(c, u) + 1)
        }
        val isP = { j: Int -> seq[j].cls == 3 || seq[j].cls == 4 }
        for (i in 0 until n - 1) {
            if (seq[i].cls == 1 && seq[i + 1].cls == 2 && seq[i + 1].s in caseP) {
                val j = nxt(i + 1, 4, isP); if (j >= p0) add(mixk(1, seq[i].id, seq[i + 1].id, seq[j].id), seq[i].id, seq[j].id)
            }
            if (seq[i].cls == 1 && seq[i + 1].s == "の") {
                val j = nxt(i + 1, 2) { seq[it].cls == 1 }; if (j >= p0) add(mixk(2, seq[i].id, seq[i + 1].id, seq[j].id), seq[i].id, seq[j].id)
            }
            if (seq[i].cls == 4 || seq[i].cls == 5) {
                val j = nxt(i, 3, isP); if (j >= p0) add(mixk(3, seq[i].id, 0, seq[j].id), seq[i].id, seq[j].id)
            }
        }
        f["${out}_n"] = fn.toDouble(); f["${out}_hit"] = fh.toDouble(); f["${out}_lc"] = lc; f["${out}_pmi"] = pm
    }

    private fun isContent(t: Tok) = (t.cls == 1 || t.cls == 3 || t.cls == 4) && t.id >= 6 && CONT.containsMatchIn(t.s) && !NUM.matches(t.s)

    private fun spmi(b: Map<String, String>, cand: List<Tok>, ctx: List<Tok>, common: Set<Long>, f: HashMap<String, Double>) {
        val t = b.getValue("table"); val u = b.getValue("uni"); val lnt = b.getValue("lognt").toDouble(); val out = b.getValue("out")
        val cw = cand.filter { isContent(it) }.map { it.id }; val all = (cw + ctx.filter { isContent(it) }.map { it.id }).toSortedSet()
        var ss = 0.0; var sm = 0.0; var sn = 0
        for (bw in cw.toSortedSet()) {
            if (bw in common) continue
            var best = Double.NEGATIVE_INFINITY
            for (a in all) {
                if (a == bw) continue
                val k = ((min(a, bw) shl 23) or max(a, bw)) * MULT
                val i = tabs.getValue(t).find(k); if (i < 0) continue
                val x = cnt(t, i); if (x <= 0) continue
                best = max(best, ln(x) + lnt - ln(ucount(a, u) + 1) - ln(ucount(bw, u) + 1) - ln(15.0))
            }
            if (best > Double.NEGATIVE_INFINITY) { ss += best; sm = max(sm, best); sn++ }
        }
        f["${out}_sum"] = ss; f["${out}_max"] = sm; f["${out}_n"] = sn.toDouble()
    }

    private fun rs(b: Map<String, String>, cand: List<Tok>, f: HashMap<String, Double>) {
        val t = b.getValue("table"); val out = b.getValue("out"); var s = 0.0; var mn = 0.0; var kana = 0; var any = false
        for (x in cand) {
            if (x.r.isEmpty()) continue
            val key = SumireLmReranker.strHash(x.r + "\t" + x.s, 0, x.r.length + 1 + x.s.length)
            val i = tabs.getValue(t).find(key)
            val v = if (i >= 0) lp(t, i) else 0.0
            s += v; mn = if (!any) v else min(mn, v); any = true
            if (i >= 0 && x.s.all { it in 'ぁ'..'ゖ' }) kana++
        }
        f["${out}_sum"] = s; f["${out}_min"] = mn; f["${out}_kana"] = kana.toDouble()
    }

    override fun score(context: String, cands: List<SumireLmReranker.Input>): DoubleArray = run(context, cands, null)

    /** 候補ごとの特徴量（名前 → 値、sub の特徴量は接頭辞つき）。評価・検証用。 */
    fun features(context: String, cands: List<SumireLmReranker.Input>): List<Map<String, Double>> {
        val res = ArrayList<Map<String, Double>>(cands.size)
        run(context, cands) { _, f -> res.add(HashMap(f)) }
        return res
    }

    private fun run(context: String, cands: List<SumireLmReranker.Input>, sink: ((Int, HashMap<String, Double>) -> Unit)?): DoubleArray {
        if (cands.isEmpty()) return DoubleArray(0)
        val subF = subs.map { (pre, r) -> pre to r.features(context, cands) }
        val nctx = nfkc(context)
        val ctxT = (if (nctx.isEmpty()) emptyList() else viterbi(nctx)).map { tok(it, "") }
        val pre = if (ctxT.isEmpty()) listOf(Tok("", 0L, 0, "")) else ctxT.takeLast(3)
        val toks = cands.map { c ->
            val norm = nfkc(c.surface); val t = c.tokens.map { nfkc(it) }
            val ok = t.isNotEmpty() && t.joinToString("") == norm
            val ts = if (ok) t else viterbi(norm)
            ts.mapIndexed { i, s -> tok(s, if (ok && c.readings.size == ts.size) c.readings[i] else "") }
        }
        val common = toks.map { t -> t.map { it.id }.toSet() }.reduce { a, b -> a intersect b }
        val cch = nctx.codePoints().toArray().takeLast(4).map { it.toLong() }
        val c0 = cands[0].cost; val f = HashMap<String, Double>(96); val out = DoubleArray(cands.size)
        for ((ri, cd) in cands.withIndex()) {
            f.clear(); val ct = toks[ri]; val seq = pre + ct
            val st = nfkc(cd.surface)
            for (b in blocks) when (b.getValue("type")) {
                "lm" -> if (b["tok"] == "char") {
                    val x = (cch + st.codePoints().toArray().map { it.toLong() }).toLongArray(); lm(b, x, cch.size, f)
                } else lm(b, seq.map { it.id }.toLongArray(), pre.size, f)
                "frame" -> frame(b, seq, pre.size, f)
                "spmi" -> spmi(b, ct, ctxT.takeLast(3), common, f)
                "rs" -> rs(b, ct, f)
            }
            f["cost"] = cd.cost / 1000.0; f["dcost"] = (cd.cost - c0) / 1000.0; f["lrank"] = ln1p(cd.rank.toDouble())
            f["ntok"] = ct.size.toDouble(); f["nch"] = st.length.toDouble()
            f["nkan"] = st.count { it in '一'..'龠' || it == '々' }.toDouble(); f["nkata"] = st.count { it in 'ァ'..'ヺ' }.toDouble()
            f["nhira"] = ct.count { it.s.isNotEmpty() && it.s.all { c -> c in 'ぁ'..'ゖ' } }.toDouble()
            for ((pre, fl) in subF) for ((k, v) in fl[ri]) f[pre + k] = v
            if (sink != null) { sink(ri, f); continue }
            var sc = 0.0
            for (k in names.indices) sc += wv[k] * ((f[names[k]] ?: 0.0) - mu[k]) / sd[k]
            out[ri] = sc
        }
        return out
    }

    companion object {
        private const val P: Long = -7046029254386353131L
        private const val MULT: Long = -2960836687051489901L // 0xD6E8FEB86659FD93
        private val NUM = Regex("^[0-9〇一二三四五六七八九十百千万億]+$")
        private val CONT = Regex("[一-龠々ァ-ヺ]")
        val REQUIRED_FILES = listOf("meta.tsv", "feats.tsv", "cb.tsv", "model.tsv", "vf.pe")
        private fun nfkc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC)

        fun load(dir: File): SumireLmTyped? {
            if (REQUIRED_FILES.any { !File(dir, it).isFile }) return null
            return try {
                val meta = File(dir, "meta.tsv").readLines().filter { '\t' in it }.associate { val f = it.split('\t'); f[0] to f[1] }
                if (meta["format"] != "typed1") return null
                val g = meta["G"]?.toInt() ?: 64
                val tabs = meta.filterKeys { it.startsWith("tab.") }.map { (k, v) -> k.removePrefix("tab.") to SumireLmReranker.PTable(dir, k.removePrefix("tab."), v, g) }.toMap()
                val cb = File(dir, "cb.tsv").readLines().filter { '\t' in it }.associate { val f = it.split('\t'); f[0] to f[1].split(',').map { v -> v.toDouble() }.toDoubleArray() }
                val blocks = File(dir, "feats.tsv").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { l ->
                    val f = l.split('\t'); mapOf("type" to f[0]) + f.drop(1).associate { val (k, v) = it.split('=', limit = 2); k to v }
                }
                val model = File(dir, "model.tsv").readLines().filter { '\t' in it }.map { it.split('\t') }
                val subs = blocks.filter { it["type"] == "sub" }.map { b ->
                    val pre = b.getValue("pre")
                    val r = SumireLmReranker.load(File(dir, b.getValue("dir"))) ?: return null
                    r.forceSpmi(model.any { it[0] == pre + "spmi_sum" })
                    pre to r
                }
                SumireLmTyped(meta, tabs, cb, blocks, subs, model.map { it[0] }.toTypedArray(), model.map { it[1].toDouble() }.toDoubleArray(),
                    model.map { it[2].toDouble() }.toDoubleArray(), model.map { it[3].toDouble() }.toDoubleArray())
            } catch (e: Exception) { null }
        }
    }
}

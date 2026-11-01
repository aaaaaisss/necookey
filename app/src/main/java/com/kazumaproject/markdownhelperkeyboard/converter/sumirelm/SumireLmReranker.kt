package com.kazumaproject.markdownhelperkeyboard.converter.sumirelm

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.Normalizer
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min

/**
 * SUMIRE-LM リランカー（端末向け採用設定: 値 4bit・指紋 12bit・文字 5-gram・語彙なし線形モデル）。
 * tools/sumire_lm/bench/OdBench.kt の rerank() を移植したもの（特徴量・計算順は同じ）。
 * Sumire の全読み候補（上位 50）を、左文脈つきの単語 4-gram / 文字 n-gram / PMI / 構造特徴の線形和で並べ直す。
 *
 * 表は tools/sumire_lm/export_od.py の出力ディレクトリ（旧形式）または詰め込み形式（meta.tsv の format = packed1: 各表の .pd .pr .pe と
 * 語彙 vf / vr。export2.py）。旧形式は（meta.tsv, cb.tsv, model.tsv, vocab.tsv,
 * w1..w4 / c1..cK / p の .dir .rel .fp .lp .bw .cnt）。表のファイルは mmap し、語彙は 64bit 文字列ハッシュ →
 * id の開番地表に詰めて持つ（文字列は保持しない）。表が無い・壊れているときは [load] が null を返し、呼び出し側は
 * リランカーなしで動く。Android に依存しない（JVM 単体テストで検証）。
 */
class SumireLmReranker private constructor(
    private val fpBits: Int,
    private val kc: Int,
    private val nw: Double,
    private val unkW: Double,
    private val unkC: Double,
    private val wt: List<Table>,
    private val ct: List<Table>,
    private val pt: Table,
    private val cb: Map<String, DoubleArray>,
    private val vocab: Lexicon,
    private val names: Array<String>,
    private val mu: DoubleArray,
    private val sd: DoubleArray,
    private val wv: DoubleArray,
) : CandidateReranker {
    /** 候補 1 つ。[tokens] は Sumire の語分割（conversionSegments の出力）。空なら文字列を語彙で分割する。 */
    class Input(
        val surface: String,
        val cost: Int,
        val rank: Int,
        val nseg: Int,
        val type: Int,
        val tokens: List<String>,
    )

    private val wLp = (1..4).map { cb.getValue("w$it.lp") }
    private val wBw = (1..3).map { cb.getValue("w$it.bw") }
    private val cLp = (1..kc).map { cb.getValue("c$it.lp") }
    private val cBw = (1 until kc).map { cb.getValue("c$it.bw") }
    private val wCnt = cb.getValue("wcnt")
    private val pCnt = cb.getValue("p.cnt")
    private val unkc = -ln(0.5 / nw) + 5
    private val wcaps = intArrayOf(4, 3, 2)
    private val ccaps = if (kc > 5) intArrayOf(kc, 5, 3) else intArrayOf(kc, 3)

    /**
     * 各候補のスコア（大きいほど良い）。[context] は左文脈（生の文字列）。候補は Sumire の順（rank 0 が先頭）で渡す。
     * 文頭扱いは学習時と同じく左文脈が 20 字未満のとき。
     */
    private val useSpmi = names.contains("spmi_sum")
    private val RARE = 1L shl 23

    override fun score(context: String, cands: List<Input>): DoubleArray {
        if (cands.isEmpty()) return DoubleArray(0)
        val bos = context.length < 20
        val nctx = nfkc(context)
        val ctxT = if (nctx.isEmpty()) emptyList() else viterbi(nctx)
        val a = ctxT.filter { content(it) }.mapNotNull { vocab.id(it) }.toSortedSet().map { it.toLong() }
        val uA = a.map { ucount(it) }
        val toks = cands.map { tokensOf(it) }
        val csets = toks.map { t -> t.filter { content(it) }.toSet() }
        val common = csets.reduce { x, y -> x intersect y }
        val ctxIds = ArrayList<Long>()
        if (bos) ctxIds.add(0L)
        for (t in ctxT) ctxIds.add(wid(t))
        val cctxSize = (if (bos) 1 else 0) + nctx.codePointCount(0, nctx.length)
        val c0 = cands[0].cost
        val f = HashMap<String, Double>(64)
        val out = DoubleArray(cands.size)
        for ((ri, cd) in cands.withIndex()) {
            f.clear()
            val ct0 = toks[ri]
            val wx = LongArray(ctxIds.size + ct0.size)
            for (i in ctxIds.indices) wx[i] = ctxIds[i]
            for ((i, t) in ct0.withIndex()) wx[ctxIds.size + i] = wid(t)
            lm(wx, ctxIds.size, wt, 4, wLp, wBw, unkW, wcaps, "w", f)
            val chars = nfkc(context + cd.surface)
            val cx = ArrayList<Long>(chars.length + 1)
            if (bos) cx.add(0L)
            run {
                var i = 0
                while (i < chars.length) {
                    val cp = chars.codePointAt(i); cx.add(cp.toLong()); i += Character.charCount(cp)
                }
            }
            lm(cx.toLongArray(), cctxSize, ct, kc, cLp, cBw, unkC, ccaps, "c", f)
            var psum = 0.0
            var pmax = Double.NEGATIVE_INFINITY
            var pn = 0
            var ls = 0.0
            if (a.isNotEmpty()) for (wd in (csets[ri] - common).sorted()) {
                val b = vocab.id(wd)?.toLong() ?: continue
                var best = -1e9
                val ub = ucount(b)
                for ((ai, av) in a.withIndex()) {
                    val id = pt.find(av * P + b)
                    val cq = if (id >= 0) exp(pCnt[pt.cnt(id)]) - 1 else 0.0
                    val pmi = ln((cq + 0.1) * nw / (max(uA[ai], 1.0) * max(ub, 1.0) * 30))
                    best = max(best, pmi); ls += ln1p(cq)
                }
                psum += max(best, 0.0); pmax = max(pmax, best); pn++
            }
            f["pmi_sum"] = psum; f["pmi_max"] = if (pn > 0) pmax else 0.0; f["pmi_n"] = pn.toDouble(); f["cooc"] = ls
            if (useSpmi) {
                // 自己 PMI: 候補内で他候補と異なる内容語 b と、同じ候補内の他の内容語 a との最大 PMI（左文脈が無くても効く）
                // 頻度表に無い語（vr の仮 id）は数えない（学習側と同じ）
                val ids = csets[ri].mapNotNull { vocab.id(it)?.toLong() }.filter { it < RARE }.toSortedSet()
                var ss = 0.0
                var sm = Double.NEGATIVE_INFINITY
                var sn = 0
                for (wd in (csets[ri] - common).sorted()) {
                    val b = vocab.id(wd)?.toLong() ?: continue
                    if (b >= RARE) continue
                    val ub = ucount(b)
                    var best = -1e9
                    var any = false
                    for (av in ids) {
                        if (av == b) continue
                        any = true
                        val id = pt.find(av * P + b)
                        val cq = if (id >= 0) exp(pCnt[pt.cnt(id)]) - 1 else 0.0
                        best = max(best, ln((cq + 0.1) * nw / (max(ucount(av), 1.0) * max(ub, 1.0) * 30)))
                    }
                    if (!any) continue
                    ss += max(best, 0.0); sm = max(sm, best); sn++
                }
                f["spmi_sum"] = ss; f["spmi_max"] = if (sn > 0) sm else 0.0
            }
            val st = cd.surface
            f["cost"] = cd.cost / 1000.0; f["rel"] = (cd.cost - c0) / 1000.0; f["rank"] = ln1p(cd.rank.toDouble())
            f["ntok"] = ct0.size.toDouble(); f["nseg"] = cd.nseg.toDouble()
            f["nch"] = st.codePointCount(0, st.length).toDouble()
            f["nkan"] = KAN.findAll(st).count().toDouble(); f["nhira"] = HIRA.findAll(st).count().toDouble()
            f["nkata"] = KATA.findAll(st).count().toDouble(); f["ndig"] = DIG.findAll(st).count().toDouble()
            f["nknum"] = KNUM.findAll(st).count().toDouble(); f["typ1"] = if (cd.type == 1) 1.0 else 0.0
            var sc = 0.0
            for (k in names.indices) sc += wv[k] * ((f[names[k]] ?: 0.0) - mu[k]) / sd[k]
            out[ri] = sc
        }
        return out
    }

    /** 学習時（segitems.py）と同じ: Sumire の語分割（NFKC）が表記と合わなければ語彙のユニグラム Viterbi で分割。 */
    private fun tokensOf(c: Input): List<String> {
        val norm = nfkc(c.surface)
        val t = c.tokens.filter { it.isNotEmpty() }.map { nfkc(it) }
        return if (t.isNotEmpty() && t.joinToString("") == norm) t else viterbi(norm)
    }

    private fun viterbi(s: String): List<String> {
        val n = s.length
        val best = DoubleArray(n + 1) { 1e18 }
        val bp = IntArray(n + 1)
        best[0] = 0.0
        for (i in 0 until n) {
            if (best[i] >= 1e18) continue
            for (len in 1..min(vocab.maxLexLen, n - i)) {
                val c = vocab.lexCost(s, i, i + len)
                val cst = if (c >= 0) c else if (len == 1) unkc else continue
                if (best[i] + cst < best[i + len]) { best[i + len] = best[i] + cst; bp[i + len] = i }
            }
        }
        val out = ArrayList<String>()
        var j = n
        while (j > 0) { out.add(s.substring(bp[j], j)); j = bp[j] }
        out.reverse()
        return out
    }

    private fun lm(
        x: LongArray, from: Int, tabs: List<Table>, k0: Int, lpc: List<DoubleArray>, bwc: List<DoubleArray>,
        unk: Double, caps: IntArray, pre: String, f: HashMap<String, Double>,
    ) {
        val n = x.size
        val lo = max(0, from - 1)
        val found = Array(k0) { IntArray(n) { -1 } }
        for (j in lo until n) {
            for (k in 1..min(k0, j + 1)) {
                var h = x[j - k + 1]
                for (t in 1 until k) h = h * P + x[j - k + 1 + t]
                found[k - 1][j] = tabs[k - 1].find(h)
            }
        }
        val acc = DoubleArray(caps.size)
        var sb = 0.0
        val hit = IntArray(k0)
        var oov = 0
        for (j in from until n) {
            if (j == 0 && x[0] == 0L) continue
            for ((ci, cap) in caps.withIndex()) {
                val kj = min(cap, j + 1)
                var m = 0
                for (k in kj downTo 1) if (found[k - 1][j] >= 0) { m = k; break }
                var v = if (m > 0) lpc[m - 1][tabs[m - 1].lp(found[m - 1][j])] else unk
                if (ci == 0) {
                    sb += v + (kj - m) * LN04
                    if (m > 0) hit[m - 1]++
                    if (found[0][j] < 0) oov++
                }
                for (k in max(m, 1) until kj) {
                    val id = found[k - 1][j - 1]
                    if (id >= 0) v += bwc[k - 1][tabs[k - 1].bw(id)]
                }
                acc[ci] += v
            }
        }
        for ((ci, cap) in caps.withIndex()) f["$pre$cap"] = acc[ci]
        f["${pre}sb"] = sb
        for (k in 1..k0) f["${pre}hit$k"] = hit[k - 1].toDouble()
        if (pre == "w") f["woov"] = oov.toDouble()
    }

    private fun wid(t: String): Long = vocab.id(t)?.toLong() ?: 0xFFFFFFFFL

    private fun ucount(id: Long): Double {
        val i = wt[0].find(id)
        return if (i >= 0) exp(wCnt[wt[0].cnt(i)]) else 0.0
    }

    /** n-gram / PMI 表: 鍵ハッシュ → 項目番号（無ければ -1）と、項目の量子化値コード。 */
    internal interface Table {
        fun find(h: Long): Int
        fun lp(i: Int): Int
        fun bw(i: Int): Int
        fun cnt(i: Int): Int
    }

    /** 語彙: 語 → id（無ければ null）と、文脈分割用のユニグラムコスト（-1 = 分割辞書にない）。 */
    internal interface Lexicon {
        val maxLexLen: Int
        fun id(word: String): Int?
        fun lexCost(s: String, from: Int, to: Int): Double
    }

    /** export_od.py のバケット付き指紋ハッシュ表（mmap、旧形式: 指紋 2 バイト・値 1 バイトずつ）。 */
    internal class HTable(dir: File, name: String, fpBits: Int) : Table {
        private val base = map(File(dir, "$name.dir"))
        private val rel = map(File(dir, "$name.rel"))
        private val fp = map(File(dir, "$name.fp"))
        val lp: ByteBuffer? = File(dir, "$name.lp").takeIf { it.isFile }?.let { map(it) }
        val bw: ByteBuffer? = File(dir, "$name.bw").takeIf { it.isFile }?.let { map(it) }
        val cnt: ByteBuffer? = File(dir, "$name.cnt").takeIf { it.isFile }?.let { map(it) }
        private val b = (rel.capacity() / 2).toLong()
        private val n = fp.capacity() / 2
        private val mask = (1L shl fpBits) - 1

        override fun find(h: Long): Int {
            if (b == 0L) return -1
            val m = splitmix(h)
            val bk = (((m ushr 32) * b) ushr 32).toInt()
            val f = (m and mask).toInt()
            val s = base.getInt((bk ushr 8) * 4) + rel.getChar(bk * 2).code
            val e = if (bk + 1 < b) base.getInt(((bk + 1) ushr 8) * 4) + rel.getChar((bk + 1) * 2).code else n
            for (i in s until e) if (fp.getChar(i * 2).code == f) return i
            return -1
        }

        private fun code(buf: ByteBuffer?, i: Int): Int = buf!!.get(i).toInt() and 0xFF
        override fun lp(i: Int) = code(lp, i)
        override fun bw(i: Int) = code(bw, i)
        override fun cnt(i: Int) = code(cnt, i)
    }

    /**
     * 詰め込み形式（format packed1, tools: real2/export2.py）の表。meta.tsv の `tab.NAME` = "B,n,fp,rb,rec,欄:ビット;..."。
     * NAME.pd: G バケットごとの先頭位置（int32、末尾に総数 n）、NAME.pr: グループ内のバケット先頭オフセット（rb ビット詰め）、
     * NAME.pe: 項目レコード（rec ビット詰め: 下位から指紋 fp ビット、続いて各欄）。ビット列はリトルエンディアン、末尾 8 バイトの余白つき。
     */
    internal class PTable(dir: File, name: String, spec: String, private val g: Int) : Table {
        private val pd = map(File(dir, "$name.pd"))
        private val pr = map(File(dir, "$name.pr"))
        private val pe = map(File(dir, "$name.pe"))
        private val b: Long
        private val n: Int
        private val fpBits: Int
        private val rb: Int
        private val rec: Long
        private val off = HashMap<String, Int>()
        private val wid = HashMap<String, Int>()
        private val lpO: Int; private val lpW: Int
        private val bwO: Int; private val bwW: Int
        private val cnO: Int; private val cnW: Int

        init {
            val f = spec.split(',')
            b = f[0].toLong(); n = f[1].toInt(); fpBits = f[2].toInt(); rb = f[3].toInt(); rec = f[4].toLong()
            var o = fpBits
            if (f.size > 5 && f[5].isNotEmpty()) for (fl in f[5].split(';')) {
                val (k, w) = fl.split(':'); off[k] = o; wid[k] = w.toInt(); o += w.toInt()
            }
            require(o.toLong() == rec && rec <= 57)
            lpO = off["lp"] ?: -1; lpW = wid["lp"] ?: 0
            bwO = off["bw"] ?: -1; bwW = wid["bw"] ?: 0
            cnO = off["cnt"] ?: -1; cnW = wid["cnt"] ?: 0
        }

        private fun start(bk: Int): Int = pd.getInt((bk / g) * 4) + bits(pr, bk.toLong() * rb, rb)

        override fun find(h: Long): Int {
            if (b == 0L || n == 0) return -1
            val m = splitmix(h)
            val bk = (((m ushr 32) * b) ushr 32).toInt()
            val f = (m and ((1L shl fpBits) - 1)).toInt()
            val s = start(bk)
            val e = if (bk + 1 < b) start(bk + 1) else n
            for (i in s until e) if (bits(pe, i * rec, fpBits) == f) return i
            return -1
        }

        fun field(name: String, i: Int): Int = bits(pe, i * rec + off.getValue(name), wid.getValue(name))
        override fun lp(i: Int) = bits(pe, i * rec + lpO, lpW)
        override fun bw(i: Int) = bits(pe, i * rec + bwO, bwW)
        override fun cnt(i: Int) = bits(pe, i * rec + cnO, cnW)
    }

    /**
     * 詰め込み語彙: vf = 頻出語（w1 に載る語: 欄 id とコスト 1/128 nat 単位、0 = 分割辞書にない）、vr = その他の語の有無だけ。
     * 鍵は [strHash]。vr の語の id は 2^23 + 項目番号（表の鍵にならない仮 id。PMI の内容語判定に使う）。
     */
    internal class PVocab(private val vf: PTable, private val vr: PTable, override val maxLexLen: Int) : Lexicon {
        override fun id(word: String): Int? {
            val h = strHash(word, 0, word.length)
            val i = vf.find(h)
            if (i >= 0) return vf.field("id", i)
            val j = vr.find(h)
            return if (j >= 0) (1 shl 23) + j else null
        }

        override fun lexCost(s: String, from: Int, to: Int): Double {
            val i = vf.find(strHash(s, from, to))
            if (i < 0) return -1.0
            val c = vf.field("cost", i)
            return if (c == 0) -1.0 else c / 128.0
        }
    }

    /** 語彙: 64bit 文字列ハッシュの開番地表 → id と、文脈分割用のユニグラムコスト（-1 = 分割辞書にない）。 */
    internal class Vocab(capacity: Int) : Lexicon {
        private val size = Integer.highestOneBit(max(16, capacity * 2 - 1)) * 2
        private val keys = LongArray(size)
        private val ids = IntArray(size) { -1 }
        private val costs = FloatArray(size) { -1f }
        override var maxLexLen = 1
            private set

        fun put(word: String, id: Int, cost: Float) {
            val h = strHash(word, 0, word.length)
            var i = (h.toInt() and Int.MAX_VALUE) and (size - 1)
            while (ids[i] >= 0 && keys[i] != h) i = (i + 1) and (size - 1)
            keys[i] = h; ids[i] = id; costs[i] = cost
            if (cost >= 0) maxLexLen = max(maxLexLen, word.length)
        }

        private fun slot(s: String, from: Int, to: Int): Int {
            val h = strHash(s, from, to)
            var i = (h.toInt() and Int.MAX_VALUE) and (size - 1)
            while (ids[i] >= 0) {
                if (keys[i] == h) return i
                i = (i + 1) and (size - 1)
            }
            return -1
        }

        override fun id(word: String): Int? = slot(word, 0, word.length).let { if (it >= 0) ids[it] else null }
        override fun lexCost(s: String, from: Int, to: Int): Double = slot(s, from, to).let { if (it >= 0) costs[it].toDouble() else -1.0 }
    }

    companion object {
        private const val P: Long = -7046029254386353131L // 0x9E3779B97F4A7C15
        private val LN04 = ln(0.4)
        private val KAN = Regex("[一-龠々〆ヵヶ]")
        private val HIRA = Regex("[ぁ-ゖ]")
        private val KATA = Regex("[ァ-ヺー]")
        private val DIG = Regex("[0-9０-９]")
        private val KNUM = Regex("[〇一二三四五六七八九十百千万億]")
        private val NUM = Regex("^[0-9〇一二三四五六七八九十百千万億]+$")
        private val CONT = Regex("[一-龠々ァ-ヺー]")
        private val ASCII2 = Regex("[A-Za-z]{2,}")

        /** 表一式の確認に使うファイル（これが揃っていれば読み込みを試す）。 */
        val REQUIRED_FILES = listOf("meta.tsv", "cb.tsv", "model.tsv", "vocab.tsv", "w1.fp", "p.fp")

        /** 詰め込み形式（meta.tsv の format = packed1）の必須ファイル。 */
        val REQUIRED_FILES_PACKED = listOf("meta.tsv", "cb.tsv", "model.tsv", "vf.pe", "vr.pe", "w1.pe", "p.pe")

        private fun map(f: File): ByteBuffer = RandomAccessFile(f, "r").use { raf ->
            if (raf.length() == 0L) return ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN)
            raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).order(ByteOrder.LITTLE_ENDIAN)
        }

        /** ビット列 [buf] の位置 [pos]（ビット）から [w] ビット（w <= 57）。 */
        private fun bits(buf: ByteBuffer, pos: Long, w: Int): Int =
            ((buf.getLong((pos ushr 3).toInt()) ushr (pos and 7L).toInt()) and ((1L shl w) - 1)).toInt()

        internal fun content(t: String) = !NUM.matches(t) && (CONT.containsMatchIn(t) || ASCII2.matches(t))
        private fun nfkc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC)

        internal fun splitmix(h: Long): Long {
            var x = h + -7046029254386353131L
            x = (x xor (x ushr 30)) * -4658895280553007687L
            x = (x xor (x ushr 27)) * -7723592293110705685L
            return x xor (x ushr 31)
        }

        /** UTF-16 の文字列区間の 64bit ハッシュ（FNV-1a + splitmix）。 */
        internal fun strHash(s: String, from: Int, to: Int): Long {
            var h = -3750763034362895579L
            for (i in from until to) { h = h xor s[i].code.toLong(); h *= 1099511628211L }
            return splitmix(h)
        }

        /** [dir] の表を読み込む。必要なファイルが無い・形式が違うときは null（リランカーなしで動かす）。 */
        fun load(dir: File): SumireLmReranker? {
            if (!dir.isDirectory || !File(dir, "meta.tsv").isFile) return null
            return try {
                val meta = File(dir, "meta.tsv").readLines().filter { '\t' in it }
                    .associate { val f = it.split('\t'); f[0] to f[1] }
                val packed = meta["format"] == "packed1"
                if ((if (packed) REQUIRED_FILES_PACKED else REQUIRED_FILES).any { !File(dir, it).isFile }) return null
                val fpBits = meta.getValue("fp").toInt()
                val g = meta["G"]?.toInt() ?: 64
                fun tab(name: String): Table = if (packed) PTable(dir, name, meta.getValue("tab.$name"), g) else HTable(dir, name, fpBits)
                val kc = meta.getValue("Kc").toInt()
                val cb = File(dir, "cb.tsv").readLines().filter { '\t' in it }.associate {
                    val f = it.split('\t'); f[0] to f[1].split(',').map { v -> v.toDouble() }.toDoubleArray()
                }
                val model = File(dir, "model.tsv").readLines().filter { '\t' in it }.map { it.split('\t') }
                val vocab: Lexicon = if (packed) {
                    PVocab(PTable(dir, "vf", meta.getValue("tab.vf"), g), PTable(dir, "vr", meta.getValue("tab.vr"), g), meta.getValue("maxlex").toInt())
                } else {
                    var lines = 0
                    File(dir, "vocab.tsv").forEachLine { lines++ }
                    val v = Vocab(lines)
                    File(dir, "vocab.tsv").forEachLine { l ->
                        val a = l.indexOf('\t'); val b = l.indexOf('\t', a + 1)
                        if (a > 0 && b > a) v.put(l.substring(0, a), l.substring(a + 1, b).toInt(), l.substring(b + 1).toFloat())
                    }
                    v
                }
                SumireLmReranker(
                    fpBits = fpBits,
                    kc = kc,
                    nw = meta.getValue("Nw").toDouble(),
                    unkW = meta.getValue("unk_w").toDouble(),
                    unkC = meta.getValue("unk_c").toDouble(),
                    wt = (1..4).map { tab("w$it") },
                    ct = (1..kc).map { tab("c$it") },
                    pt = tab("p"),
                    cb = cb,
                    vocab = vocab,
                    names = model.map { it[0] }.toTypedArray(),
                    mu = model.map { it[1].toDouble() }.toDoubleArray(),
                    sd = model.map { it[2].toDouble() }.toDoubleArray(),
                    wv = model.map { it[3].toDouble() }.toDoubleArray(),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ExactInputCandidatePromotionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BunsetsuAnalyzer
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.LatticeLmCost
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearningScorePolicy
import com.kazumaproject.markdownhelperkeyboard.learning.session.ConversionLearningSession
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import java.io.File

/**
 * 学習のシミュレーション（実験、tools/eval_border/EXP_OVERNIGHT.md）。NK_LEARN_OUT=<dir> のときだけ動く。
 * 学習辞書は in-memory（LearnDao の mock、findByInputPrefix だけ答える）で、実機と同じく LearnRepository 経由でエンジンに渡す。
 * 書き込み規則は LearnDao.upsertLearningEntries / touchExisting / applyIgnored と ConversionLearningSession.finish
 * （learnFirstCandidate = false、MAX_PHRASE 12）を写したもの。時間はシミュレーション時刻（日）で、変換時に
 * lastUsedAt = 実時刻 − (sim 現在 − sim 書込) に換算する（GraphBuilder は System.currentTimeMillis を使う）。
 *
 * A: 誤り文ごとに空の辞書から、間隔 Δ（0.01/1/3/7 日）で同じ文を打ち、1 位が正解でなければ正解に直して学習。何回直すと 1 位が正解になるか（上限 5）。
 * B1: 誤り文 1 つを 1 回直した直後、その学習語の読みを含む他の文（全セット）を変換し、壊れた／直った文を数える。
 * B2: 全誤り文を 1 回ずつ直した辞書で全文を変換（経過 0/1/3/7 日）。
 * C: B1 で壊れた文を直して（学習）から元の誤り文を打ち直す、を 3 往復（ピンポン）。
 */
class LearningSimTest {
    private data class Row(
        val score: Int, val l: Short?, val r: Short?, val count: Int, val last: Double, val phrase: Boolean,
    )

    private class Store {
        companion object {
            /** 実験: 回数の更新を round ではなく ceil（減衰後 + 1 の切り上げ）にする。 */
            var ceilCount = false
            fun count(existing: Int, lastMs: Long, nowMs: Long): Int =
                if (!ceilCount) LearningScorePolicy.reinforcedCount(existing, lastMs, nowMs)
                else kotlin.math.ceil(LearningScorePolicy.strength(existing, lastMs, nowMs) + 1.0).toInt().coerceIn(1, LearningScorePolicy.MAX_COUNT)
        }
        val rows = LinkedHashMap<Pair<String, String>, Row>()
        var now = 0.0 // sim days
        fun copy() = Store().also { s -> s.rows.putAll(rows); s.now = now }
        fun ms(d: Double) = (d * 86_400_000.0).toLong()

        /** sim 書込時刻を実時刻へ。 */
        fun entities(prefix: String, realNow: Long): List<LearnEntity> = rows.entries
            .filter { it.key.first.startsWith(prefix) }
            .map { (k, v) ->
                LearnEntity(
                    input = k.first, out = k.second, score = v.score, leftId = v.l, rightId = v.r,
                    usageCount = v.count, lastUsedAt = (realNow - ms(now - v.last)).coerceAtLeast(1L), isPhrase = v.phrase,
                )
            }

        fun upsert(e: LearnEntity) {
            val k = e.input to e.out
            val ex = rows[k]
            rows[k] = if (ex == null) Row(e.score, e.leftId, e.rightId, 1, now, e.isPhrase) else {
                val realNow = 10_000L * 86_400_000L
                val exLast = realNow - ms(now - ex.last)
                Row(
                    LearningScorePolicy.reinforce(ex.score, e.score), e.leftId ?: ex.l, e.rightId ?: ex.r,
                    count(ex.count, exLast, realNow), now, ex.phrase || e.isPhrase,
                )
            }
        }

        fun touch(k: Pair<String, String>) {
            val ex = rows[k] ?: return
            val realNow = 10_000L * 86_400_000L
            rows[k] = ex.copy(count = count(ex.count, realNow - ms(now - ex.last), realNow), last = now)
        }

        fun ignored(k: Pair<String, String>, factor: Double) {
            val ex = rows[k] ?: return
            val realNow = 10_000L * 86_400_000L
            val next = LearningScorePolicy.ignored(ex.count, realNow - ms(now - ex.last), realNow, factor)
            if (next == null) rows.remove(k) else rows[k] = ex.copy(count = next.first, last = now - (realNow - next.second) / 86_400_000.0)
        }
    }

    private lateinit var dc: DeviceConfigConversion
    private lateinit var engine: KanaKanjiEngine
    private var store = Store()
    private lateinit var repo: LearnRepository

    private fun bump() = runBlocking { repo.insertAll(emptyList()) } // mock の insertAll は何もしない、キャッシュ無効化だけ

    private class Conv(val top: String, val strings: List<String>, val segs: Map<String, List<CandidateConversionSegment>>, val splits: Map<String, List<Int>>, val ids: Map<String, Pair<Short?, Short?>>)

    private val cache = HashMap<String, Conv>()
    private var cacheKey = ""

    private fun convert(reading: String, n: Int = DeviceConfigConversion.DEVICE_N): Conv {
        val key = store.rows.toString() + store.now
        if (key != cacheKey) { cache.clear(); cacheKey = key; bump() }
        return cache.getOrPut("$n:$reading") {
            val segs = LinkedHashMap<String, List<CandidateConversionSegment>>()
            val res = runBlocking { dc.conversion(engine, reading, n, segs, learn = repo) }
            val seen = HashSet<String>()
            val full = ExactInputCandidatePromotionPolicy.promote(input = reading, candidates = res.candidates)
                .filter { it.length.toInt() == reading.length && seen.add(it.string) }
            Conv(
                full.firstOrNull()?.string ?: reading, full.map { it.string }, segs, res.splitPatternByCandidateString,
                full.associate { it.string to (it.leftId to it.rightId) },
            )
        }
    }

    private fun top1(reading: String) = convert(reading).top

    /** 正解に直して確定したときの学習（ConversionLearningSession.finish + IMEService.persistCompletedLearningSession を写す）。 */
    private fun commitCorrection(reading: String, gold: String): String {
        val c8 = convert(reading)
        val primary = c8.top
        val c = if (gold in c8.strings) c8 else convert(reading, 20)
        val goldSegs = c.segs[gold]
        val spans = goldSegs?.let { BunsetsuAnalyzer.spansOf(reading.length, it, c.splits[gold].orEmpty()) }
            ?.takeIf { sp -> sp.joinToString("") { it.output } == gold }
        val primarySegs = c8.segs[primary].orEmpty()
        data class Frag(val r: String, val o: String, val sel: Boolean, val l: Short?, val rr: Short?)
        val frags: List<Frag>
        val how: String
        if (spans != null) {
            frags = spans.map { sp ->
                val r = reading.substring(sp.start, sp.end)
                val pOut = BunsetsuAnalyzer.outputForRange(primarySegs, sp.start, sp.end)
                val ids = if (sp.start == 0 && sp.end == reading.length) c.ids[gold] else convert(r).ids[sp.output]
                Frag(r, sp.output, pOut != sp.output, ids?.first, ids?.second)
            }
            how = "split${spans.size}"
        } else {
            // 正解が N-best に無い／経路が取れない: 分けられない丸ごと確定（12 字以下だけ学習）
            frags = listOf(Frag(reading, gold, true, null, null))
            how = "whole"
        }
        val entries = ArrayList<LearnEntity>()
        val learnable = frags.filter { it.sel && (how != "whole" || it.r.length <= ConversionLearningSession.MAX_PHRASE_READING_LENGTH) }
        if (learnable.isNotEmpty()) {
            learnable.forEach { entries += LearnEntity(it.r, it.o, 3000, it.l, it.rr, 1, 1L, false) }
            for (last in 1 until frags.size) {
                val ph = frags.take(last + 1)
                val pr = ph.joinToString("") { it.r }
                if (pr.length > ConversionLearningSession.MAX_PHRASE_READING_LENGTH || ph.none { it.sel }) continue
                entries += LearnEntity(pr, ph.joinToString("") { it.o }, 3000, ph.first().l, ph.last().rr, 1, 1L, true)
            }
            if (reading.length <= ConversionLearningSession.MAX_PHRASE_READING_LENGTH) {
                entries += LearnEntity(reading, gold, 3000, frags.first().l, frags.last().rr, 1, 1L, frags.size > 1)
            }
        }
        val written = entries.distinctBy { it.input to it.out }
        written.forEach { store.upsert(it) }
        val wk = written.mapTo(HashSet()) { it.input to it.out }
        (frags.map { it.r to it.o } + (reading to gold)).distinct().filterNot { it in wk }.forEach { store.touch(it) }
        if (primary != gold) store.ignored(reading to primary, LearningScorePolicy.IGNORED_FACTOR_CONVERSION)
        return how + ":" + written.joinToString(",") { "${it.input}→${it.out}" }
    }

    /** 1 位が正解の文を確定（先頭候補なので新しくは学ばない、既にある語は「使った」）。 */
    private fun commitTop(reading: String) {
        val c = convert(reading)
        val segs = c.segs[c.top] ?: return
        val spans = BunsetsuAnalyzer.spansOf(reading.length, segs, c.splits[c.top].orEmpty()) ?: return
        (spans.map { reading.substring(it.start, it.end) to it.output } + (reading to c.top)).distinct().forEach { store.touch(it) }
    }

    private data class Sent(val set: String, val id: String, val reading: String, val golds: List<String>)

    @Test
    fun simulate() {
        val outDir = System.getenv("NK_LEARN_OUT")?.takeIf { it.isNotBlank() }
        assumeTrue(outDir != null)
        val out = File(outDir!!).apply { mkdirs() }
        val sets = (System.getenv("NK_LEARN_SETS") ?: "179=../tools/eval_border/in.tsv;reg=../tools/eval_border/reg.tsv")
            .split(';').filter { '=' in it }.map { it.substringBefore('=') to it.substringAfter('=') }
        LatticeLmCost.table = DeviceConfigConversion.latticeTableFile()?.inputStream()?.use { LatticeLmCost.load(it) }
        println("lattice table: ${LatticeLmCost.table != null}")
        dc = DeviceConfigConversion()
        engine = dc.newEngine()
        val dao = mock<LearnDao>()
        dao.stub {
            onBlocking { findByInputPrefix(any(), any()) } doSuspendableAnswer { inv ->
                store.entities(inv.getArgument<String>(0), System.currentTimeMillis())
            }
        }
        repo = LearnRepository(dao)

        // NK_LEARN_PROBES="名前=tsv;..." は副作用を見るだけの文（誤りでも直さない。学習語の読みを含むが正解は別の表記）
        val probeSets = (System.getenv("NK_LEARN_PROBES") ?: "").split(';').filter { '=' in it }
            .map { it.substringBefore('=') to it.substringAfter('=') }
        val probeNames = probeSets.map { it.first }.toSet()
        val skipA = System.getenv("NK_LEARN_SKIP_A") == "1"
        val sents = (sets + probeSets).flatMap { (name, path) ->
            File(path).readLines().mapNotNull { l ->
                val c = l.split('\t')
                if (c.size < 5 || c[3].isEmpty() || c[0] == "id") null
                else Sent(name, c[0], c[3], c[4].split('|').filter { it.isNotEmpty() })
            }
        }
        val t0 = System.nanoTime()
        fun el() = (System.nanoTime() - t0) / 1_000_000_000
        store = Store()
        val base = sents.associate { it.id to top1(it.reading) }
        val errs = sents.filter { base[it.id] !in it.golds && it.set !in probeNames }
        // 学習しても直らない文の候補を見る（NK_LEARN_DEBUG=id,id）
        (System.getenv("NK_LEARN_DEBUG") ?: "").split(',').filter { it.isNotBlank() }.forEach { did ->
            val s = sents.firstOrNull { it.id == did } ?: return@forEach
            store = Store()
            val how = commitCorrection(s.reading, s.golds.first())
            val segs = LinkedHashMap<String, List<CandidateConversionSegment>>()
            val res = runBlocking { dc.conversion(engine, s.reading, 8, segs, learn = repo) }
            println("debug ${s.id} learned $how")
            res.candidates.take(10).forEach { c -> println("  raw ${c.string} type=${c.type} score=${c.score} len=${c.length} l=${c.leftId} r=${c.rightId}") }
            ExactInputCandidatePromotionPolicy.promote(input = s.reading, candidates = res.candidates).take(5)
                .forEach { c -> println("  promoted ${c.string} type=${c.type} score=${c.score}") }
            println("  store ${store.rows}")
            store = Store()
        }
        println("sentences ${sents.size}, errors ${errs.size} (${el()}s)")

        // A
        val intervals = listOf(0.01, 1.0, 3.0, 7.0)
        fun runA(fileName: String) = File(out, fileName).bufferedWriter().use { w ->
            w.write("set\tid\treading\tgold\tbase\t" + intervals.joinToString("\t") { "fix_d$it" } + "\tlearned_first\ttops_d1\n")
            for (s in errs) {
                val gold = s.golds.first()
                val res = ArrayList<String>()
                var learnedFirst = ""
                var tops1 = ""
                for (d in intervals) {
                    store = Store()
                    var fixed = -1
                    val tops = ArrayList<String>()
                    for (k in 0..5) {
                        store.now = k * d
                        val t = top1(s.reading)
                        tops += t
                        if (t in s.golds) { fixed = k; break }
                        if (k == 5) break
                        val how = commitCorrection(s.reading, gold)
                        if (k == 0 && d == 1.0) learnedFirst = how
                    }
                    if (d == 1.0) tops1 = tops.joinToString(" / ")
                    res += fixed.toString()
                }
                w.write(listOf(s.set, s.id, s.reading, gold, base[s.id], res.joinToString("\t"), learnedFirst, tops1).joinToString("\t"))
                w.newLine(); w.flush()
            }
        }
        if (!skipA) runA("phaseA.tsv")
        if (System.getenv("NK_LEARN_CEIL") == "1") { Store.ceilCount = true; runA("phaseA_ceil.tsv"); Store.ceilCount = false }
        println("phase A done (${el()}s)")

        // B1 + C
        val pingRows = ArrayList<String>()
        File(out, "phaseB1.tsv").bufferedWriter().use { w ->
            w.write("err_set\terr_id\tlearned\tchecked\tbroken\tfixed\tbroken_detail\tchanged_detail\n")
            for (e in errs) {
                store = Store()
                val learned = commitCorrection(e.reading, e.golds.first())
                val inputs = store.rows.keys.map { it.first }.toSet()
                val affected = sents.filter { s -> s.id != e.id && inputs.any { s.reading.contains(it) } }
                val broken = ArrayList<Sent>()
                val fixed = ArrayList<Sent>()
                val detail = ArrayList<String>()
                val changed = ArrayList<String>()
                for (s in affected) {
                    val t = top1(s.reading)
                    val wasOk = base[s.id] in s.golds
                    val nowOk = t in s.golds
                    if (wasOk && !nowOk) { broken += s; detail += "${s.id}:${base[s.id]}→$t" }
                    if (!wasOk && nowOk) fixed += s
                    if (t != base[s.id] && !(wasOk && !nowOk) && !(!wasOk && nowOk)) changed += "${s.id}:${base[s.id]}→$t"
                }
                w.write(listOf(e.set, e.id, learned, affected.size, broken.size, fixed.joinToString(",") { it.id }, detail.joinToString(" ; "), changed.joinToString(" ; ")).joinToString("\t"))
                w.newLine(); w.flush()
                // C: ピンポン（壊れた文 b ごとに、b を直す → e を打つ → … 3 往復、時間は 0.01 日ずつ）
                for (b in broken.take(3)) {
                    val st = store.copy()
                    val seq = ArrayList<String>()
                    var cur = b
                    for (step in 0 until 6) {
                        store.now += 0.01
                        val t = top1(cur.reading)
                        val ok = t in cur.golds
                        seq += "${cur.id}${if (ok) "○" else "×($t)"}"
                        if (ok) commitTop(cur.reading) else commitCorrection(cur.reading, cur.golds.first())
                        cur = if (cur === b) e else b
                    }
                    pingRows += listOf(e.id, b.id, seq.joinToString(" ")).joinToString("\t")
                    store = st
                }
            }
        }
        File(out, "phaseC.tsv").writeText("err_id\tbroken_id\tsequence\n" + pingRows.joinToString("\n") + "\n")
        println("phase B1/C done (${el()}s)")

        // B2
        store = Store()
        errs.forEach { commitCorrection(it.reading, it.golds.first()); store.now += 0.001 }
        val learnedAll = store.copy()
        File(out, "phaseB2.tsv").bufferedWriter().use { w ->
            w.write("age\tset\tid\tbase_ok\tnow_ok\tbase\tnow\tattributed\n")
            for (age in listOf(0.0, 1.0, 3.0, 7.0)) {
                store = learnedAll.copy().also { it.now = learnedAll.now + age }
                for (s in sents) {
                    val t = top1(s.reading)
                    val bo = base[s.id] in s.golds
                    val no = t in s.golds
                    val attr = if (t != base[s.id]) store.rows.keys.filter { s.reading.contains(it.first) && t.contains(it.second) }
                        .joinToString(",") { "${it.first}→${it.second}" } else ""
                    w.write(listOf(age, s.set, s.id, bo, no, base[s.id], t, attr).joinToString("\t")); w.newLine()
                }
                w.flush()
                println("phase B2 age $age (${el()}s)")
            }
        }
        LatticeLmCost.table = null
    }
}

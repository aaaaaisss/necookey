// Offline ZENZ-GATE bench driver (outside the repo). Reproduces IMEService.runZenzaiStep + the
// post-stop recheck loop (ZENZAI_PAUSE_RECHECK_MAX = 8) for the final reading of each sentence, using
// the app's own Sumire engine (JVM) and zenz_bridge.cpp (zserve, threads=2, n_ctx=256).
// usage: ZenzBenchKt IN.tsv OUT.tsv CANDS.tsv [limit]
import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

fun kata(s: String) = buildString { s.forEach { c -> append(if (c in 'ぁ'..'ゖ') c + 0x60 else c) } }
fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\u001F', ' ')

class Zserve {
    private val p = ProcessBuilder("/workspace/work/ime/zg/zserve", "/workspace/work/ime/zg/model.gguf")
        .redirectError(ProcessBuilder.Redirect.INHERIT).start()
    private val w = p.outputStream.bufferedWriter(Charsets.UTF_8)
    private val r = p.inputStream.bufferedReader(Charsets.UTF_8)
    fun call(vararg f: String): Pair<String, Long> {
        val t0 = System.nanoTime()
        w.write(f.joinToString("\t")); w.write("\n"); w.flush()
        val line = r.readLine() ?: error("zserve died")
        val us = (System.nanoTime() - t0) / 1000
        return line.substringBeforeLast('\t') to us
    }
}

fun main(args: Array<String>) {
    val limit = args.getOrNull(3)?.toInt() ?: Int.MAX_VALUE
    val engine = TestEngineFactory.create()
    val userDict = mock<UserDictionaryRepository>()
    runBlocking { whenever(userDict.commonPrefixSearchInUserDict(any())).thenReturn(emptyList()) }
    val z = Zserve()
    val outF = File(args[1])
    val done = if (outF.exists()) outF.readLines().drop(1).map { it.substringBefore('\t') }.toHashSet() else hashSetOf()
    // drop partial candidate rows of sentences whose out row was not written
    val cF = File(args[2])
    if (cF.exists()) cF.writeText(cF.readLines().filter { it.substringBefore('\t') in done }.joinToString("") { it + "\n" })
    val fresh = done.isEmpty()
    val out = java.io.FileWriter(outF, !fresh).buffered()
    val cout = java.io.FileWriter(cF, !fresh).buffered()
    if (fresh) out.write(listOf("id", "split", "ctxlen", "rlen", "multi", "nfull", "rank", "top1", "gap", "final", "rounds", "evals",
        "kind1", "minlp", "minmargin", "fixgap", "fixmaxlp", "acc", "gen", "evalus", "genus", "scoreus", "scoretok", "sumireus").joinToString("\t"))
    if (fresh) out.newLine()
    var n = 0
    File(args[0]).forEachLine { line ->
        val c = line.split('\t')
        if (c.size < 5 || n >= limit || c[0] in done) return@forEachLine
        n++
        val id = c[0]; val split = c[1]; val ctx = clean(c[2]); val reading = c[3]
        val expected = c[4].split('|').toSet()
        val t0 = System.nanoTime()
        val res = runBlocking {
            engine.getCandidatesWithBunsetsuSeparation(
                input = reading, n = 50,
                mozcUtPersonName = false, mozcUTPlaces = false, mozcUTWiki = false,
                mozcUTNeologd = false, mozcUTWeb = false,
                userDictionaryRepository = userDict, learnRepository = null,
                isOmissionSearchEnable = false,
                typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 1900,
            )
        }
        val sumireUs = (System.nanoTime() - t0) / 1000
        val seen = HashSet<String>()
        val full = res.candidates.filter { it.length.toInt() == reading.length && seen.add(it.string) }
        val top = full.firstOrNull() ?: res.candidates.first()
        val gap = full.filter { it.type.toInt() == 1 && it.string != top.string }.minOfOrNull { it.score }?.let { it - top.score }
        val multi = res.primarySplitPositions.any { it in 1 until reading.length }
        val rank = full.indexOfFirst { it.string in expected } + 1
        fun constrained(prefix: String, exact: Boolean) = runBlocking {
            engine.getConstrainedBestCandidate(
                input = reading, prefix = prefix, exact = exact,
                mozcUtPersonName = false, mozcUTPlaces = false, mozcUTWiki = false,
                mozcUTNeologd = false, mozcUTWeb = false,
                userDictionaryRepository = userDict, learnRepository = null,
                isOmissionSearchEnable = false,
                typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 1900,
            )
        }
        // --- app loop (runZenzaiStep + rechecks) ---
        val input = kata(reading)
        var draft = top.string
        var constraint: String? = null   // carried FIX surface (same reading)
        var finalTop = top.string
        var rounds = 0; var evals = 0; var evalUs = 0L
        var kind1 = ""; var conf1 = listOf("", "", "", "", "")
        val seenDrafts = HashSet<String>()
        var unverified = true
        while (unverified && rounds <= 8) {
            // step: draft = constrained re-search for the carried constraint (already = finalTop here)
            if (constraint != null) draft = finalTop
            val (raw, us) = z.call("E", ctx, input, draft)
            evals++; evalUs += us
            val verdict = raw.substringBefore('\u001F')
            if (evals == 1) {
                kind1 = verdict.substringBefore(':')
                conf1 = raw.substringAfter("conf:", "").split(',').let { if (it.size == 5) it else listOf("", "", "", "", "") }
            }
            var fixedNow = false
            if (verdict.startsWith("FIX:") || verdict.startsWith("WHOLE:")) {
                val whole = verdict.startsWith("WHOLE:")
                val fix = verdict.substringAfter(':')
                if (fix.isEmpty() || fix == draft) {
                } else if (fix == constraint && !whole) {
                    constraint = null
                } else if (!whole && draft.startsWith(fix)) {
                } else {
                    val corrected = constrained(fix, whole)
                    if (corrected != null) {
                        finalTop = corrected.string; constraint = fix; fixedNow = true
                    } else constraint = null
                }
            }
            unverified = fixedNow
            if (rounds > 0 && !seenDrafts.add(finalTop)) break
            if (rounds == 0) seenDrafts.add(finalTop)
            rounds++
        }
        // --- free generation (diagnostic / hybrid) ---
        val (gen, genUs) = z.call("G", ctx, input, "48")
        // --- LM scores: Sumire full-length N-best (<=50) + gen + final + gold (gold only for diagnostics) ---
        data class Sc(val s: String, val src: String, val score: Int?)
        val pool = LinkedHashMap<String, Sc>()
        full.take(50).forEach { pool.putIfAbsent(it.string, Sc(it.string, "s", it.score)) }
        listOf(gen to "g", finalTop to "a").forEach { (s, src) -> if (s.isNotEmpty()) pool.putIfAbsent(s, Sc(s, src, null)) }
        expected.forEach { pool.putIfAbsent(it, Sc(it, "x", null)) }
        val list = pool.values.toList()
        val (sres, sUs) = z.call("S", ctx, input, list.joinToString("\u001F") { clean(it.s) })
        val parts = sres.split('\u001F')
        var scoreTok = 0
        list.forEachIndexed { i, sc ->
            val p = parts.getOrNull(i)?.split(',') ?: listOf("-inf", "0")
            scoreTok += p.getOrNull(1)?.toIntOrNull() ?: 0
            cout.write(listOf(id, i, sc.s, sc.src, sc.score ?: "", p[0], p.getOrNull(1) ?: "0").joinToString("\t")); cout.newLine()
        }
        out.write(listOf(id, split, ctx.length, reading.length, if (multi) 1 else 0, full.size, rank, top.string, gap ?: "",
            finalTop, rounds, evals, kind1, conf1[0], conf1[1], conf1[2], conf1[3], conf1[4], gen, evalUs, genUs, sUs, scoreTok, sumireUs)
            .joinToString("\t"))
        out.newLine()
        if (n % 10 == 0) { out.flush(); cout.flush(); System.err.println("done $n") }
    }
    out.close(); cout.close()
    System.exit(0)
}

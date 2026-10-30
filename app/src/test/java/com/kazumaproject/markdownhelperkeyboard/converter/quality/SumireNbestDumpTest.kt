package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ExactInputCandidatePromotionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.NecookeyCandidateBarConfig
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.SumireLmGate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.EmptySystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.LatticeLmCost
import com.kazumaproject.markdownhelperkeyboard.converter.sumirelm.CandidateRerankers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 変換精度評価用の N-best ダンプ（conv-eval ブランチ、ローカル JVM のみ。APK には入らない）。
 *
 * 実機（IMEService 既定設定）に合わせた点:
 *  - FindPath にシステム n-gram 辞書（assets/ngram/system_ngram.dat + system_ngram_unigram.dat、既定オン）を渡す。
 *    旧ハーネス（TestEngineFactory 既定）は EmptySystemNgramDictionary で、ここが実機との最大の差だった。
 *  - 2 段バー上段の変換結果 = CONVERSION モード = getCandidatesWithoutPredictionWithBunsetsu、
 *    n = max(n_best 既定 4, NecookeyCandidateBarConfig.conversionNBest 8) = 8、beam 20、省略検索なし、
 *    その後 ExactInputCandidatePromotionPolicy.promote。1 位 = 読み全体を覆う最初の候補。
 *  - リランカーのプール = queryRerankPool と同じ（同関数、n = SumireLmGate.POOL_SIZE = 50）。
 *  - ラティス辺コスト表（NK_LATTICE_COST）は実機既定オン。Mozc 境界チェックは buildEngine 内で ONLY_EDGE（実機と同じ）。
 *  - 人名・Wiki 辞書（実機では常時有効でラティスに入る）を入れる。NK_UTDICT=0 で外す。
 *  - 学習辞書・ユーザー辞書は使わない（新規インストール相当）。UT 候補リスト（mozcUT*）は既定オフ。
 *  - 旧ハーネス相当（.legacy）は同じエンジン（人名・Wiki あり）で n-gram だけ外す。完全な旧条件は NK_UTDICT=0 で別実行。
 *
 * 入力 NK_DUMP_IN: id, split, context, reading, expected...（先頭 4 列を使う。context 列はリランカーの左文脈）
 * 出力（すべて id, rank, surface, type, score, leftId, rightId, segs(読み:出力 を US 区切り)）:
 *  NK_DUMP_OUT           実機上段と同じ設定の候補（n=8、昇格後、読み全体の候補だけ）
 *  NK_DUMP_OUT.pool      リランカープール（n=50）
 *  NK_DUMP_OUT.legacy    旧ハーネス相当（n-gram なし、getCandidatesWithBunsetsuSeparation n=50、表は同じ）。NK_LEGACY=0 で省略
 *  NK_DUMP_OUT.rerank    NK_RERANK_DIR に CandidateRerankers が読める表（SUMIRE-LM なら meta.tsv ほか）があればプールの並べ替え結果（列 9 = スコア）
 * NK_NGRAM=0 でシステム n-gram を外す（比較用）。NK_DUMP_SHARD=i/n。1 呼び出し 30 秒で打ち切り。
 */
class SumireNbestDumpTest {
    /**
     * NK_CONFIGS="名前:キー=値,キー=値;名前2:..." なら設定ごとに 1 回ずつダンプする（出力は NK_DUMP_OUT.名前）。1 回の JVM で複数設定を比べる評価用。
     * キー: dinf（方言活用語尾ノード 0/1）dcost（語尾コスト）kdia（方言かな語 0/1）kpen（そのペナルティ）ha（は-merge ペナルティ）
     * hat（は-merge を時の名詞の後だけ 0/1）err（辞書表記の誤り修正 0/1）。
     */
    @Test
    fun dump() {
        // NK_DUMP_SETS="名前=入力tsv;名前2=..." と NK_DUMP_DIR なら入力ごとに DIR/名前/cands.tsv へ（tools/verify.sh 用。1 回の JVM で 179/reg/dia）
        val sets = System.getenv("NK_DUMP_SETS")?.takeIf { it.isNotBlank() }
        if (sets != null) {
            val dir = System.getenv("NK_DUMP_DIR")
            assumeTrue(!dir.isNullOrBlank())
            for (s in sets.split(';').filter { '=' in it }) {
                val out = File(dir, s.substringBefore('=')).apply { mkdirs() }
                dumpOne(File(out, "cands.tsv").path, s.substringAfter('='))
            }
            return
        }
        val configs = System.getenv("NK_CONFIGS")?.takeIf { it.isNotBlank() }
        if (configs == null) {
            dumpOne(System.getenv("NK_DUMP_OUT"))
            return
        }
        val base = System.getenv("NK_DUMP_OUT")
        assumeTrue(!base.isNullOrBlank())
        for (cfg in configs.split(';').filter { it.isNotBlank() }) {
            val name = cfg.substringBefore(':')
            ConversionKnobs.reset()
            cfg.substringAfter(':', "").split(',').filter { '=' in it }.forEach { kv ->
                ConversionKnobs.set(kv.substringBefore('='), kv.substringAfter('='))
            }
            println("config $name: ${ConversionKnobs.describe()}")
            dumpOne("$base.$name")
        }
        ConversionKnobs.reset()
    }

    private fun dumpOne(outPath: String?, inPath: String? = System.getenv("NK_DUMP_IN")) {
        assumeTrue(!inPath.isNullOrBlank() && !outPath.isNullOrBlank())
        val (si, sn) = (System.getenv("NK_DUMP_SHARD") ?: "0/1").split('/').map { it.toInt() }
        val ngramOn = System.getenv("NK_NGRAM") != "0"
        val legacyOn = System.getenv("NK_LEGACY") != "0"
        // NK_UTDICT=0 で人名・Wiki 辞書（実機では常時ラティスに入る）を外す（旧ハーネス相当）
        val utDict = System.getenv("NK_UTDICT") != "0"
        val dc = DeviceConfigConversion(useNgram = ngramOn, utDict = utDict)
        println("system ngram rules: ${dc.ngram.ruleCount}, use=$ngramOn")
        println("person/wiki dictionaries in lattice: $utDict")
        fun newEngine(): KanaKanjiEngine = dc.newEngine()
        var engine = newEngine()
        val pool = Executors.newCachedThreadPool { r -> Thread(r).also { it.isDaemon = true } }
        val userDict = dc.userDict
        val lcTable = System.getenv("NK_LATTICE_COST")?.takeIf { it.isNotBlank() }?.let { p ->
            File(p).inputStream().use { LatticeLmCost.load(it) }
                .also { println("lattice cost table: ${it.wordCount} words, ${it.pairCount} pairs") }
        }
        LatticeLmCost.table = lcTable
        val reranker = System.getenv("NK_RERANK_DIR")?.takeIf { it.isNotBlank() }?.let { CandidateRerankers.load(File(it)) }
        println("reranker: ${if (reranker != null) "loaded" else "none"}")
        val deviceN = DeviceConfigConversion.DEVICE_N

        val outDev = File(outPath!!).bufferedWriter()
        val outPool = File("$outPath.pool").bufferedWriter()
        val outLegacy = if (legacyOn) File("$outPath.legacy").bufferedWriter() else null
        val outRr = reranker?.let { File("$outPath.rerank").bufferedWriter() }
        var n = 0
        val t0 = System.nanoTime()

        fun call(id: String, reading: String, block: suspend (KanaKanjiEngine) -> BunsetsuCandidateResult): BunsetsuCandidateResult? {
            val eng = engine
            val fut = pool.submit<BunsetsuCandidateResult> { runBlocking { block(eng) } }
            return try {
                fut.get(30, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                fut.cancel(true); File("$outPath.skipped").appendText("$id\t$reading\n")
                engine = newEngine(); null
            } catch (e: Throwable) {
                System.err.println("fail $id: $e"); null
            }
        }

        fun write(w: BufferedWriter, id: String, reading: String, cands: List<Candidate>, segs: Map<String, List<CandidateConversionSegment>>, limit: Int, extra: (Int) -> String? = { null }) {
            val seen = HashSet<String>()
            cands.filter { it.length.toInt() == reading.length && seen.add(it.string) }.take(limit).forEachIndexed { r, cand ->
                val sg = segs[cand.string].orEmpty().ifEmpty { cand.conversionSegments }.joinToString("\u001F") {
                    reading.substring(it.inputStart.coerceIn(0, reading.length), it.inputEnd.coerceIn(0, reading.length)) + ":" + it.output
                }
                val cols = mutableListOf<Any>(id, r, cand.string.replace('\t', ' '), cand.type, cand.score, cand.leftId ?: "", cand.rightId ?: "", sg)
                extra(r)?.let { cols += it }
                w.write(cols.joinToString("\t")); w.newLine()
            }
        }

        File(inPath!!).readLines().forEachIndexed { li, line ->
            if (li % sn != si) return@forEachIndexed
            val c = line.split('\t')
            if (c.size < 4 || c[3].isEmpty()) return@forEachIndexed
            val id = c[0]
            val reading = c[3]
            val leftContext = c[2].takeLast(NecookeyCandidateBarConfig.DEFAULT.maxLeftContextChars)
            File("$outPath.progress").writeText("$id\t$reading\n")
            dc.useNgram = ngramOn

            // 1) 実機上段（CONVERSION, n=8）
            val segDev = LinkedHashMap<String, List<CandidateConversionSegment>>()
            call(id, reading) { e -> dc.conversion(e, reading, deviceN, segDev) }?.let { res ->
                val promoted = ExactInputCandidatePromotionPolicy.promote(input = reading, candidates = res.candidates)
                write(outDev, id, reading, promoted, segDev, deviceN)
            }
            // 2) リランカープール（n=50）
            val segPool = LinkedHashMap<String, List<CandidateConversionSegment>>()
            val poolRes = call(id, reading) { e -> dc.conversion(e, reading, SumireLmGate.POOL_SIZE, segPool) }
            poolRes?.let { write(outPool, id, reading, it.candidates, segPool, SumireLmGate.POOL_SIZE) }
            // 3) リランカー（表があれば。実機の適用条件: 2〜32 文字・ひらがな(+記号)のみ）
            if (reranker != null && poolRes != null && reading.length in 2..NecookeyCandidateBarConfig.DEFAULT.maxRerankInputLength) {
                val d = SumireLmGate.rerank(reranker, reading, leftContext, poolRes.candidates, { cand -> segPool[cand.string].orEmpty() })
                if (d != null) write(outRr!!, id, reading, d.ranked, segPool, SumireLmGate.POOL_SIZE) { r -> "%.4f".format(d.scores.getOrElse(r) { Double.NaN }) }
                else write(outRr!!, id, reading, poolRes.candidates, segPool, SumireLmGate.POOL_SIZE)
            }
            // 4) 旧ハーネス相当
            if (outLegacy != null) {
                dc.useNgram = false
                val segL = LinkedHashMap<String, List<CandidateConversionSegment>>()
                call(id, reading) { e ->
                    e.getCandidatesWithBunsetsuSeparation(
                        input = reading, n = 50,
                        mozcUtPersonName = false, mozcUTPlaces = false, mozcUTWiki = false,
                        mozcUTNeologd = false, mozcUTWeb = false,
                        userDictionaryRepository = userDict, learnRepository = null,
                        candidateSegmentCollector = segL,
                    )
                }?.let { write(outLegacy, id, reading, it.candidates, segL, 50) }
                dc.useNgram = ngramOn
            }
            n++
            if (n % 20 == 0) {
                listOfNotNull(outDev, outPool, outLegacy, outRr).forEach { it.flush() }
                println("dumped $n in ${(System.nanoTime() - t0) / 1_000_000_000}s")
            }
        }
        listOfNotNull(outDev, outPool, outLegacy, outRr).forEach { it.close() }
        LatticeLmCost.table = null
        println("dumped $n total in ${(System.nanoTime() - t0) / 1_000_000_000}s")
    }
}

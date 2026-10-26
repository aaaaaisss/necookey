package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.LatticeLmCost
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.SplitReconversionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.SplitReconversionResult
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * SUMIRE-LM 用の N-best ダンプ（CI の sumire-lm-dump ワークフローが実行時にだけ app/src/test へコピーする。
 * リポジトリの app には入れない）。学習/ユーザー/UT 辞書なし、本番と同じ文節分離経路。
 * 入力 NK_DUMP_IN: id, split, context, reading(ひらがな), expected... （先頭 4 列だけ使う）
 * 出力 NK_DUMP_OUT: id, rank(0..), surface, type, score(経路コスト), leftId, rightId, segs(読み:出力 を US 区切り)
 * NK_DUMP_SHARD=i/n なら行番号 % n == i の行だけ。1 文 20 秒で打ち切り（取消に応じない場合は .progress に最後の id）。
 * NK_SPLIT_RECONV=K,m,N[,F]（任意）: 区切り固定の再変換（上位 K 区切り・スパンあたり辞書上位 m 語・区切りあたり A* で N 文字列、
 *   ノード固定の最良経路を F 本）の追加候補を top-50 の後に rank 50.. で出力し、列 9 以降に X, 区切り順位, 語順位の最大,
 *   区切りが N-best 由来(1/0), 由来（A / F / AF）。
 *   .time に id, 読み長, 通常経路 ms, 追加処理 ms, 追加候補数, 区切り数, N-best 由来区切り数。
 * NK_HAIRU=P（任意）: はいる→入る 対策の追加コスト（PosPairPenalty.hairuPenalty、0 = 無効、未設定 = 既定値）。
 * NK_LATTICE_COST=PATH（任意、案2）: SLC2 表を読み込み、各文を表なし / 表ありで 1 回ずつ変換（順序は文ごとに交互）。
 *   表ありの top-50 を NK_DUMP_OUT、表なしを NK_DUMP_OUT.base に同じ形式で出力し、.time に id, 読み長, 表なし ms, 表あり ms
 *   （NK_SPLIT_RECONV と同時には使わない）。
 * NK_COLLOQ=P（任意）: 口語かな語ノードの追加コスト（ColloquialKanaNodes.penalty）、負の値で無効、未設定 = 既定。
 * NK_DIALECT=P（任意）: 方言かな語ノードの追加コスト（KanaDialectNodes.penalty）、負の値で無効、未設定 = 既定（有効、P=1000）。
 * NK_DIALECT_SKIP=語,語（任意）: その方言語だけ無効（評価用）。
 * NK_KANASPAN=base,perChar[,n|i[,maxLen]]（任意）: 汎用ひらがなスパンノード（KanaSpanNodes）、未設定 = 既定（無効）。
 */
class SumireNbestDumpTest {
    @Test
    fun dump() {
        val inPath = System.getenv("NK_DUMP_IN")
        val outPath = System.getenv("NK_DUMP_OUT")
        assumeTrue(!inPath.isNullOrBlank() && !outPath.isNullOrBlank())
        val (si, sn) = (System.getenv("NK_DUMP_SHARD") ?: "0/1").split('/').map { it.toInt() }
        var engine = TestEngineFactory.create()
        val pool = java.util.concurrent.Executors.newCachedThreadPool { r -> Thread(r).also { it.isDaemon = true } }
        val userDict = mock<UserDictionaryRepository>()
        runBlocking { whenever(userDict.commonPrefixSearchInUserDict(any())).thenReturn(emptyList()) }
        val rc = System.getenv("NK_SPLIT_RECONV")?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim().toInt() }
        val rcConfig = rc?.let {
            SplitReconversionConfig(maxPatterns = it[0], wordsPerSpan = it[1], perPattern = it[2], perPatternForced = it.getOrElse(3) { 0 })
        }
        System.getenv("NK_HAIRU")?.takeIf { it.isNotBlank() }?.let {
            com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.PosPairPenalty.hairuPenalty = it.trim().toInt()
        }
        // NK_COLLOQ=P（任意）: 口語かな語ノード（ColloquialKanaNodes）の追加コスト、負なら無効。未設定 = 既定。
        System.getenv("NK_COLLOQ")?.takeIf { it.isNotBlank() }?.trim()?.toInt()?.let { p ->
            com.kazumaproject.markdownhelperkeyboard.converter.graph.ColloquialKanaNodes.enabled = p >= 0
            if (p >= 0) com.kazumaproject.markdownhelperkeyboard.converter.graph.ColloquialKanaNodes.penalty = p
        }
        // NK_DIALECT=P（任意）: 方言かな語ノード（KanaDialectNodes）の追加コスト、負なら無効。未設定 = 既定。
        (System.getProperty("NK_DIALECT") ?: System.getenv("NK_DIALECT"))?.takeIf { it.isNotBlank() }?.trim()?.toInt()?.let { p ->
            com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaDialectNodes.enabled = p >= 0
            if (p >= 0) com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaDialectNodes.penalty = p
        }
        System.getenv("NK_DIALECT_SKIP")?.takeIf { it.isNotBlank() }?.let {
            com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaDialectNodes.skip = it.split(',').map { w -> w.trim() }.toSet()
        }
        System.getenv("NK_KANASPAN")?.takeIf { it.isNotBlank() }?.let {
            com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaSpanNodes.configure(it)
        }
        // NK_KANJIRUN=penalty,threshold[,a]（任意）: 連続漢字（継ぎはぎ）ペナルティ（KanjiRunPenalty）、未設定 = 既定（無効）。
        System.getenv("NK_KANJIRUN")?.takeIf { it.isNotBlank() }?.let {
            com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.KanjiRunPenalty.configure(it)
        }
        if (!System.getenv("NK_KANJIRUN_TRACE").isNullOrBlank()) {
            com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.KanjiRunPenalty.trace = HashMap()
        }
        println("kanjirun: ${com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.KanjiRunPenalty.enabled} ${com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.KanjiRunPenalty.penalty} ${com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.KanjiRunPenalty.threshold} ${com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.KanjiRunPenalty.bothLow}")
        println("kanaspan: ${com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaSpanNodes.enabled} ${com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaSpanNodes.base} ${com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaSpanNodes.perChar} ${com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaSpanNodes.posId}")
        println("dialect skip: ${com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaDialectNodes.skip}")
        println("dialect: ${com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaDialectNodes.enabled} ${com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaDialectNodes.penalty}")
        println("colloquial: ${com.kazumaproject.markdownhelperkeyboard.converter.graph.ColloquialKanaNodes.enabled} ${com.kazumaproject.markdownhelperkeyboard.converter.graph.ColloquialKanaNodes.penalty}")
        println("hairu penalty: ${com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.PosPairPenalty.hairuPenalty}")
        val lcTable = System.getenv("NK_LATTICE_COST")?.takeIf { it.isNotBlank() }?.let { p ->
            File(p).inputStream().use { LatticeLmCost.load(it) }.also { println("lattice cost table: ${it.wordCount} words, ${it.pairCount} pairs") }
        }
        val baseOut = lcTable?.let { File("$outPath.base").bufferedWriter() }
        val timeOut = (rcConfig ?: lcTable)?.let { File("$outPath.time").bufferedWriter() }
        var n = 0
        val t0 = System.nanoTime()
        File(outPath!!).bufferedWriter().use { out ->
            File(inPath!!).readLines().forEachIndexed { li, line ->
                if (li % sn != si) return@forEachIndexed
                val c = line.split('\t')
                if (c.size < 4 || c[3].isEmpty()) return@forEachIndexed
                val reading = c[3]
                File("$outPath.progress").writeText("${c[0]}\t$reading\n")
                fun conv(table: LatticeLmCost.Table?, segs: HashMap<String, List<CandidateConversionSegment>>, rcOut: SplitReconversionResult?): Pair<com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult?, Long> {
                    LatticeLmCost.table = table
                    val eng = engine
                    val callStart = System.nanoTime()
                    val fut = pool.submit<com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult> {
                        runBlocking {
                            eng.getCandidatesWithBunsetsuSeparation(
                                input = reading, n = 50,
                                mozcUtPersonName = false, mozcUTPlaces = false, mozcUTWiki = false,
                                mozcUTNeologd = false, mozcUTWeb = false,
                                userDictionaryRepository = userDict, learnRepository = null,
                                isOmissionSearchEnable = false,
                                typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 1900,
                                candidateSegmentCollector = segs,
                                splitReconversionConfig = rcConfig,
                                splitReconversionCollector = rcOut,
                            )
                        }
                    }
                    val r = try {
                        fut.get(20, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (e: java.util.concurrent.TimeoutException) {
                        // 取消に応じない経路探索は放置し、以後は新しいエンジンで続ける
                        fut.cancel(true); File("$outPath.skipped").appendText("${c[0]}\t$reading\n")
                        engine = TestEngineFactory.create(); null
                    } catch (e: Throwable) {
                        System.err.println("fail ${c[0]}: $e"); null
                    }
                    return r to (System.nanoTime() - callStart)
                }
                fun writeRows(w: java.io.BufferedWriter, res: com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult, segs: Map<String, List<CandidateConversionSegment>>) {
                    val seen = HashSet<String>()
                    res.candidates.filter { it.length.toInt() == reading.length && seen.add(it.string) }.take(50).forEachIndexed { r, cand ->
                        val sg = segs[cand.string].orEmpty().joinToString("\u001F") {
                            reading.substring(it.inputStart.coerceIn(0, reading.length), it.inputEnd.coerceIn(0, reading.length)) + ":" + it.output
                        }
                        w.write(listOf(c[0], r, cand.string.replace('\t', ' '), cand.type, cand.score, cand.leftId ?: "", cand.rightId ?: "", sg).joinToString("\t"))
                        w.newLine()
                    }
                }
                if (lcTable != null) {
                    val segB = HashMap<String, List<CandidateConversionSegment>>()
                    val segL = HashMap<String, List<CandidateConversionSegment>>()
                    val (rb, tb, rl, tl) = if (n % 2 == 0) {
                        val b = conv(null, segB, null); val l = conv(lcTable, segL, null); listOf(b.first, b.second, l.first, l.second)
                    } else {
                        val l = conv(lcTable, segL, null); val b = conv(null, segB, null); listOf(b.first, b.second, l.first, l.second)
                    }
                    LatticeLmCost.table = null
                    (rb as? com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult)?.let { writeRows(baseOut!!, it, segB) }
                    (rl as? com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult)?.let { writeRows(out, it, segL) }
                    timeOut?.write(listOf(c[0], reading.length, (tb as Long) / 1e6, (tl as Long) / 1e6).joinToString("\t")); timeOut?.newLine()
                    n++
                    if (n % 50 == 0) { timeOut?.flush(); out.flush(); baseOut?.flush(); println("dumped $n in ${(System.nanoTime() - t0) / 1_000_000_000}s") }
                    return@forEachIndexed
                }
                val segs = HashMap<String, List<CandidateConversionSegment>>()
                val rcOut = rcConfig?.let { SplitReconversionResult() }
                val (resOrNull, callNs) = conv(null, segs, rcOut)
                val res = resOrNull ?: return@forEachIndexed
                val seen = HashSet<String>()
                val full = res.candidates.filter { it.length.toInt() == reading.length && seen.add(it.string) }.take(50)
                full.forEachIndexed { r, cand ->
                    val sg = segs[cand.string].orEmpty().joinToString("\u001F") {
                        reading.substring(it.inputStart.coerceIn(0, reading.length), it.inputEnd.coerceIn(0, reading.length)) + ":" + it.output
                    }
                    out.write(
                        listOf(c[0], r, cand.string.replace('\t', ' '), cand.type, cand.score, cand.leftId ?: "", cand.rightId ?: "", sg)
                            .joinToString("\t"),
                    )
                    out.newLine()
                }
                if (rcOut != null) {
                    var r = full.size
                    val kinds = HashMap<String, String>()
                    rcOut.candidates.forEach { x ->
                        val k = if (x.forced) "F" else "A"
                        kinds[x.candidate.string] = (kinds[x.candidate.string] ?: "").let { if (k in it) it else it + k }
                    }
                    rcOut.candidates.forEach { x ->
                        val cand = x.candidate
                        if (!seen.add(cand.string)) return@forEach
                        val sg = x.segments.joinToString("\u001F") {
                            reading.substring(it.inputStart.coerceIn(0, reading.length), it.inputEnd.coerceIn(0, reading.length)) + ":" + it.output
                        }
                        out.write(
                            listOf(
                                c[0], 50 + (r++ - full.size), cand.string.replace('\t', ' '), cand.type, cand.score, cand.leftId ?: "", cand.rightId ?: "", sg,
                                "X", x.patternIndex, x.maxWordRank, if (x.patternFromNBest) 1 else 0, kinds[cand.string] ?: "",
                            ).joinToString("\t"),
                        )
                        out.newLine()
                    }
                    timeOut?.write(
                        listOf(
                            c[0], reading.length, (callNs - rcOut.elapsedNs) / 1e6, rcOut.elapsedNs / 1e6, r - full.size,
                            rcOut.patterns.size, rcOut.nBestPatternCount,
                        ).joinToString("\t"),
                    )
                    timeOut?.newLine()
                }
                n++
                if (n % 50 == 0) {
                    timeOut?.flush()
                    out.flush()
                    println("dumped $n in ${(System.nanoTime() - t0) / 1_000_000_000}s")
                }
            }
        }
        timeOut?.close()
        baseOut?.close()
        com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.KanjiRunPenalty.trace?.let { m ->
            File("$outPath.kanjirun").bufferedWriter().use { w -> m.entries.sortedByDescending { it.value }.forEach { w.write("${it.value}\t${it.key}\n") } }
        }
        println("dumped $n total in ${(System.nanoTime() - t0) / 1_000_000_000}s")
    }
}

package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
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
 * NK_SPLIT_RECONV=K,m,N（任意）: 区切り固定の再変換（上位 K 区切り・スパンあたり辞書上位 m 語・区切りあたり N 文字列）の追加候補を
 *   top-50 の後に rank 50.. で出力し、列 9 以降に X, 区切り順位, 語順位の最大, 区切りが N-best 由来(1/0)。
 *   .time に id, 読み長, 通常経路 ms, 追加処理 ms, 追加候補数, 区切り数, N-best 由来区切り数。
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
        val rcConfig = rc?.let { SplitReconversionConfig(maxPatterns = it[0], wordsPerSpan = it[1], perPattern = it[2]) }
        val timeOut = rcConfig?.let { File("$outPath.time").bufferedWriter() }
        var n = 0
        val t0 = System.nanoTime()
        File(outPath!!).bufferedWriter().use { out ->
            File(inPath!!).readLines().forEachIndexed { li, line ->
                if (li % sn != si) return@forEachIndexed
                val c = line.split('\t')
                if (c.size < 4 || c[3].isEmpty()) return@forEachIndexed
                val reading = c[3]
                File("$outPath.progress").writeText("${c[0]}\t$reading\n")
                val segs = HashMap<String, List<CandidateConversionSegment>>()
                val rcOut = rcConfig?.let { SplitReconversionResult() }
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
                val res = try {
                    fut.get(20, java.util.concurrent.TimeUnit.SECONDS)
                } catch (e: java.util.concurrent.TimeoutException) {
                    // 取消に応じない経路探索は放置し、以後は新しいエンジンで続ける
                    fut.cancel(true); File("$outPath.skipped").appendText("${c[0]}\t$reading\n")
                    engine = TestEngineFactory.create(); null
                } catch (e: Throwable) {
                    System.err.println("fail ${c[0]}: $e"); null
                } ?: return@forEachIndexed
                val callNs = System.nanoTime() - callStart
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
                    rcOut.candidates.forEach { x ->
                        val cand = x.candidate
                        if (!seen.add(cand.string)) return@forEach
                        val sg = x.segments.joinToString("\u001F") {
                            reading.substring(it.inputStart.coerceIn(0, reading.length), it.inputEnd.coerceIn(0, reading.length)) + ":" + it.output
                        }
                        out.write(
                            listOf(
                                c[0], 50 + (r++ - full.size), cand.string.replace('\t', ' '), cand.type, cand.score, cand.leftId ?: "", cand.rightId ?: "", sg,
                                "X", x.patternIndex, x.maxWordRank, if (x.patternFromNBest) 1 else 0,
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
        println("dumped $n total in ${(System.nanoTime() - t0) / 1_000_000_000}s")
    }
}

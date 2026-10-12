package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
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
                val eng = engine
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
                n++
                if (n % 50 == 0) {
                    out.flush()
                    println("dumped $n in ${(System.nanoTime() - t0) / 1_000_000_000}s")
                }
            }
        }
        println("dumped $n total in ${(System.nanoTime() - t0) / 1_000_000_000}s")
    }
}

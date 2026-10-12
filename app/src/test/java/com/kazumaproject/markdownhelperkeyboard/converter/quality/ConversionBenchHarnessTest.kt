package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * オフライン評価ハーネス（CI では入力が無いので skip）。データはリポジトリ外（CC BY-SA のため同梱しない）。
 *
 * 入力 TSV（NK_BENCH_IN）: id, split, context, reading(ひらがな), expected(許容解を | 区切り), wrong
 * 出力 TSV（NK_BENCH_OUT）: id, split, rank(期待が何位か, 0=圏外), top1, score1, score2, top5(| 区切り)
 * 集計は /workspace/work/ime/data/score.py。本番と同じ文節分離経路・学習/ユーザー/UT 辞書なし。
 * context は Sumire では使わない（zenz 評価用にそのまま運ぶ）。
 */
class ConversionBenchHarnessTest {

    @Test
    fun runBench() {
        val inPath = System.getenv("NK_BENCH_IN")
        val outPath = System.getenv("NK_BENCH_OUT")
        assumeTrue(!inPath.isNullOrBlank() && !outPath.isNullOrBlank())
        val engine = TestEngineFactory.create()
        val userDict = mock<UserDictionaryRepository>()
        runBlocking { whenever(userDict.commonPrefixSearchInUserDict(any())).thenReturn(emptyList()) }
        File(outPath!!).bufferedWriter().use { out ->
            File(inPath!!).forEachLine { line ->
                val c = line.split('\t')
                if (c.size < 5) return@forEachLine
                val expected = c[4].split('|').toSet()
                val cands = runBlocking {
                    engine.getCandidatesWithBunsetsuSeparation(
                        input = c[3], n = 20,
                        mozcUtPersonName = false, mozcUTPlaces = false, mozcUTWiki = false,
                        mozcUTNeologd = false, mozcUTWeb = false,
                        userDictionaryRepository = userDict, learnRepository = null,
                        isOmissionSearchEnable = false,
                        typoCorrectionOffsetScore = 3000, omissionSearchOffsetScore = 1900,
                    ).candidates
                }
                val strings = cands.map { it.string }
                val rank = strings.indexOfFirst { it in expected } + 1
                out.write(
                    listOf(
                        c[0], c[1], rank.toString(), strings.firstOrNull() ?: "",
                        (cands.getOrNull(0)?.score ?: 0).toString(), (cands.getOrNull(1)?.score ?: 0).toString(),
                        strings.take(5).joinToString("|"),
                    ).joinToString("\t"),
                )
                out.newLine()
            }
        }
    }
}

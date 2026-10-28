package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ExactInputCandidatePromotionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipBarConfig
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipBarModel
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipConversion
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipConverter
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.CurrentUiSolver
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.MemoChipConverter
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.NewUiSolver
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.SolveResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.SumireLmGate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.LatticeLmCost
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 文節チップ UI の手数評価（tools/eval_border/CHIP_UI_EVAL.md）。NK_CHIP_OUT=<dir> のときだけ動く。
 * 実機既定経路（DeviceConfigConversion、n=8、昇格後）で各文の部分読みを変換し、正解に届く最少操作数を
 * 新 UI（ChipBarModel）と現行 UI（変換ボタン区切り＋候補リスト）で比べる。上限 6 手。
 * 入力 NK_DUMP_IN（既定 ../tools/eval_border/in.tsv）、NK_CHIP_LIMIT で先頭 n 文だけ、NK_DUMP_SHARD=i/n。
 * 出力: chip_eval.tsv（文ごと）、cands.tsv / cands.tsv.pool（ev3.py で区切り誤り・ツギハギを判定する用、SumireNbestDumpTest と同形式）。
 */
class ChipUiEvalTest {

    @Test
    fun evaluate() {
        val outDir = System.getenv("NK_CHIP_OUT")?.takeIf { it.isNotBlank() }
        assumeTrue(outDir != null)
        // NK_CHIP_SETS="名前=入力tsv;..." なら OUT/名前/ に入力ごと（1 回の JVM で 179/reg/dia/long）
        val sets = System.getenv("NK_CHIP_SETS")?.takeIf { it.isNotBlank() }
            ?.split(';')?.filter { '=' in it }?.map { File(outDir, it.substringBefore('=')) to it.substringAfter('=') }
            ?: listOf(File(outDir!!) to (System.getenv("NK_DUMP_IN")?.takeIf { it.isNotBlank() } ?: "../tools/eval_border/in.tsv"))
        LatticeLmCost.table = DeviceConfigConversion.latticeTableFile()?.inputStream()?.use { LatticeLmCost.load(it) }
        println("lattice table: ${LatticeLmCost.table != null}")
        for ((dir, inPath) in sets) evaluateSet(dir.apply { mkdirs() }, inPath)
        LatticeLmCost.table = null
    }

    private fun evaluateSet(dir: File, inPath: String) {
        val limit = System.getenv("NK_CHIP_LIMIT")?.toIntOrNull() ?: Int.MAX_VALUE
        val (si, sn) = (System.getenv("NK_DUMP_SHARD") ?: "0/1").split('/').map { it.toInt() }
        val dc = DeviceConfigConversion()
        var engine: KanaKanjiEngine = dc.newEngine()
        val pool = Executors.newCachedThreadPool { r -> Thread(r).also { it.isDaemon = true } }

        fun engineConvert(reading: String, n: Int): Pair<ChipConversion, Map<String, List<CandidateConversionSegment>>> {
            val segs = LinkedHashMap<String, List<CandidateConversionSegment>>()
            val eng = engine
            val fut = pool.submit<ChipConversion> {
                val res = runBlocking { dc.conversion(eng, reading, n, segs) }
                val promoted = ExactInputCandidatePromotionPolicy.promote(input = reading, candidates = res.candidates)
                ChipConversion.fromEngine(reading, promoted, segs, res.splitPatternByCandidateString, res.primarySplitPositions, maxPaths = n.coerceAtLeast(20))
            }
            return try {
                fut.get(30, TimeUnit.SECONDS) to segs
            } catch (e: Throwable) {
                fut.cancel(true); System.err.println("convert fail $reading: $e"); engine = dc.newEngine()
                ChipConversion.unconverted(reading) to emptyMap()
            }
        }

        val tsv = File(dir, "chip_eval.tsv").bufferedWriter()
        tsv.write("id\treading\tgold\ttop1\tnew\tnew_adv\tcur\tnew_plan\tcur_plan\tchips\tcalls\tnew_nofull\tcur_nofull\tnew_nofull_plan\tcur_nofull_plan\n")
        val dev = File(dir, "cands.tsv").bufferedWriter()
        val devPool = File(dir, "cands.tsv.pool").bufferedWriter()
        fun dump(w: java.io.BufferedWriter, id: String, conv: ChipConversion, limitN: Int) {
            conv.paths.take(limitN).forEachIndexed { r, p ->
                val sg = p.words.joinToString("\u001F") { conv.reading.substring(it.start, it.end) + ":" + it.output }
                w.write(listOf(id, r, p.output.replace('\t', ' '), 1, 0, "", "", sg).joinToString("\t")); w.newLine()
            }
        }
        val t0 = System.nanoTime()
        var n = 0
        File(inPath).readLines().forEachIndexed { li, line ->
            if (n >= limit || li % sn != si) return@forEachIndexed
            val c = line.split('\t')
            if (c.size < 5 || c[3].isEmpty() || c[0] == "id") return@forEachIndexed
            val (id, reading) = c[0] to c[3]
            val golds = c[4].split('|').filter { it.isNotEmpty() }
            val memo = MemoChipConverter { r, _ -> engineConvert(r, DeviceConfigConversion.DEVICE_N).first }
            val whole = memo.convert(reading)
            dump(dev, id, whole, DeviceConfigConversion.DEVICE_N)
            dump(devPool, id, engineConvert(reading, SumireLmGate.POOL_SIZE).first, SumireLmGate.POOL_SIZE)

            fun best(solve: (String) -> SolveResult): SolveResult =
                golds.map(solve).minWithOrNull(compareBy { it.moves ?: Int.MAX_VALUE }) ?: SolveResult(null, emptyList())
            val model = ChipBarModel(memo)
            val newR = best { g -> NewUiSolver(model).solve(reading, g) }
            val advR = best { g -> NewUiSolver(ChipBarModel(memo, ChipBarConfig(advanceAfterPick = true))).solve(reading, g) }
            val curR = best { g -> CurrentUiSolver(memo).solve(reading, g) }
            val newNf = best { g -> NewUiSolver(model, useFullAlternatives = false).solve(reading, g) }
            val curNf = best { g -> CurrentUiSolver(memo, useFullAlternatives = false).solve(reading, g) }
            val chips = model.initial(reading).chips.joinToString("|") { it.output }
            tsv.write(
                listOf(
                    id, reading, golds.first(), whole.primary?.output ?: "",
                    newR.moves ?: -1, advR.moves ?: -1, curR.moves ?: -1,
                    newR.plan.joinToString(" > "), curR.plan.joinToString(" > "), chips, memo.calls,
                    newNf.moves ?: -1, curNf.moves ?: -1, newNf.plan.joinToString(" > "), curNf.plan.joinToString(" > "),
                ).joinToString("\t"),
            )
            tsv.newLine()
            n++
            if (n % 10 == 0) {
                listOf(tsv, dev, devPool).forEach { it.flush() }
                println("chip eval $n in ${(System.nanoTime() - t0) / 1_000_000_000}s")
            }
        }
        listOf(tsv, dev, devPool).forEach { it.close() }
        println("chip eval ${dir.name} done $n in ${(System.nanoTime() - t0) / 1_000_000_000}s")
    }
}

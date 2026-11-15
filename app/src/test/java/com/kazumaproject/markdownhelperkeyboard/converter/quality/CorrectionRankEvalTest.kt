package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ExactInputCandidatePromotionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BunsetsuAnalyzer
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.graph.ContextLexicon
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.LatticeLmCost
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearningScorePolicy
import com.kazumaproject.markdownhelperkeyboard.learning.session.ConversionLearningSession
import com.kazumaproject.markdownhelperkeyboard.learning.session.LearningFragment
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 変換ミスの記録（correction-log-all.tsv、リポジトリの外）の各行で、正解が実機上段の何位か。3 つの指標（2026-11-15 B1）:
 *  - **no-learning**: 学習なし（従来の指標）。
 *  - **replay**: 記録を時系列（ファイルは新しい順なので逆順）に流し、各行を測ってから、その行の確定を学習辞書（メモリ版）に書く。
 *    行の時刻は記録に無いので等間隔 [STEP_MS]（約 10 日で 180 行）と仮定。先頭が学習語で外れたら端末と同じく ×0.1（無視の減点）。
 *  - **leave-one-out**: ほかの全行を時系列で学習した状態（時計はその行の時刻、後の行は新しいまま）で各行を測る。
 * 学習の書き方は端末に寄せた近似: 正解の経路の文節（BunsetsuAnalyzer）ごとに、最初の 1 位と違う文節だけ選んだことにして
 * ConversionLearningSession に渡す（句・全体は 12 字以下）。正解が候補に無ければ全体を 1 つ（12 字以下だけ学習）。
 * 再現していないもの: 区切りの学習（LearnedBoundaries・LearnedChipSplit）・打ち直し・予測からの選択・後続語・画面文脈
 * （記録に文脈の文字列が無いので文脈の効果はこの評価では測れない）。
 */
class CorrectionRankEvalTest {
    private class Row(val reading: String, val shown: String, val correct: String, val kind: String)

    private class Measured(val list: List<Candidate>, val segs: Map<String, List<CandidateConversionSegment>>, val split: Map<String, List<Int>>) {
        val strings = list.map { it.string }
        fun rank(correct: String): Int = strings.indexOf(correct)
    }

    @Test
    fun rankCorrections() {
        val log = listOf(File("../../correction-log-all.tsv"), File("../correction-log-all.tsv")).firstOrNull { it.isFile }
        assumeTrue(log != null)
        val outDir = log!!.absoluteFile.parentFile.resolve("eval").also { it.mkdirs() }
        val emoji = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}]")
        val rows = log.readLines().mapNotNull { line ->
            val c = line.split('\t')
            if (c.size < 5) return@mapNotNull null
            val (reading, shown, correct, kind) = c
            if (reading.isBlank() || correct.isBlank() || emoji.containsMatchIn(correct)) null else Row(reading, shown, correct, kind)
        }
        val n = rows.size
        // ファイルは新しい順。時系列 = 逆順。chrono[k] = ファイルの行番号。
        val chrono = (n - 1 downTo 0).toList()
        val now0 = System.currentTimeMillis()
        fun timeOf(fileIdx: Int): Long = now0 - fileIdx.toLong() * STEP_MS // ファイル先頭（最新）が now0

        val table = DeviceConfigConversion.latticeTableFile()
        val conv = DeviceConfigConversion()
        val engine = conv.newEngine()
        LatticeLmCost.table = table?.inputStream()?.use { LatticeLmCost.load(it) }
        ContextLexicon.provider = null
        val savedClock = LearningScorePolicy.clock
        try {
            // 1) 学習なし。ついでに各行の学習内容（文節）を作る。
            val base = rows.map { measure(conv, engine, it.reading, null) }
            val fragments = rows.indices.map { i -> fragmentsOf(rows[i], base[i]) }

            // 2) replay
            val replayRank = IntArray(n)
            run {
                val repo = LearnRepository(InMemoryLearnDao())
                for (fi in chrono) {
                    val t = timeOf(fi)
                    LearningScorePolicy.clock = { t }
                    val m = measure(conv, engine, rows[fi].reading, repo)
                    replayRank[fi] = m.rank(rows[fi].correct)
                    runBlocking {
                        val top = m.list.firstOrNull()
                        if (top != null && top.string != rows[fi].correct && top.type == CANDIDATE_TYPE_LEARNED_DICTIONARY) {
                            repo.applyIgnoredReturningPrevious(listOf(rows[fi].reading to top.string), LearningScorePolicy.IGNORED_FACTOR_CONVERSION, t)
                        }
                        learn(repo, rows[fi].reading, fragments[fi], t)
                    }
                }
            }

            // 3) leave-one-out
            val looRank = IntArray(n)
            for (i in 0 until n) {
                val repo = LearnRepository(InMemoryLearnDao())
                runBlocking { for (fj in chrono) if (fj != i) learn(repo, rows[fj].reading, fragments[fj], timeOf(fj)) }
                val t = timeOf(i)
                LearningScorePolicy.clock = { t }
                looRank[i] = measure(conv, engine, rows[i].reading, repo).rank(rows[i].correct)
            }

            fun rankStr(idx: Int) = if (idx < 0) "NF" else (idx + 1).toString()
            fun counts(r: IntArray): String {
                val r1 = r.count { it == 0 }; val r3 = r.count { it in 0..2 }; val r10 = r.count { it in 0..9 }; val nf = r.count { it < 0 }
                return "rank1=$r1 top3=$r3 top10=$r10 notFound=$nf"
            }
            val baseRank = IntArray(n) { base[it].rank(rows[it].correct) }
            val sb = StringBuilder("reading\tcorrect\trank\ttop1\tkind\ttop10\n")
            val sbl = StringBuilder("reading\tcorrect\tnolearn\treplay\tloo\tkind\n")
            for (i in 0 until n) {
                val s = base[i].strings
                sb.append("${rows[i].reading}\t${rows[i].correct}\t${rankStr(baseRank[i])}\t${s.firstOrNull() ?: ""}\t${rows[i].kind}\t${s.take(10).joinToString(" | ")}\n")
                sbl.append("${rows[i].reading}\t${rows[i].correct}\t${rankStr(baseRank[i])}\t${rankStr(replayRank[i])}\t${rankStr(looRank[i])}\t${rows[i].kind}\n")
            }
            File(outDir, "corrections-rank-1115c.tsv").writeText(sb.toString())
            File(outDir, "corrections-rank-learn-1115c.tsv").writeText(sbl.toString())
            println("CORRECTION EVAL n=$n (table ${table != null})")
            println("CORRECTION EVAL no-learning ${counts(baseRank)}")
            println("CORRECTION EVAL replay ${counts(replayRank)}")
            println("CORRECTION EVAL leave-one-out ${counts(looRank)}")
        } finally {
            LearningScorePolicy.clock = savedClock
            LatticeLmCost.table = null
        }
    }

    private fun measure(conv: DeviceConfigConversion, engine: KanaKanjiEngine, reading: String, repo: LearnRepository?): Measured {
        val segs = HashMap<String, List<CandidateConversionSegment>>()
        val res = runCatching { runBlocking { conv.conversion(engine, reading, segs = segs, learnRepository = repo) } }.getOrNull()
            ?: return Measured(emptyList(), emptyMap(), emptyMap())
        val seen = HashSet<String>()
        val list = ExactInputCandidatePromotionPolicy.promote(input = reading, candidates = res.candidates)
            .filter { it.length.toInt() == reading.length && seen.add(it.string) }
        for (c in list) if (c.conversionSegments.isNotEmpty()) segs.putIfAbsent(c.string, c.conversionSegments)
        return Measured(list, segs, res.splitPatternByCandidateString)
    }

    /** 端末で「正解を選んだ」ときに学習に渡る文節（近似、クラスの説明）。 */
    private fun fragmentsOf(row: Row, m: Measured): List<LearningFragment> {
        val idx = m.rank(row.correct)
        val cand = m.list.getOrNull(idx)
        val segs = m.segs[row.correct]
        val spans = if (cand != null && segs != null) {
            BunsetsuAnalyzer.spansOf(row.reading.length, segs, m.split[row.correct].orEmpty())
                ?.takeIf { sp -> sp.joinToString("") { it.output } == row.correct }
        } else null
        if (cand == null || spans == null) {
            return listOf(
                LearningFragment(row.reading, row.correct, candidateScore = 3000, candidateIndex = 1, explicitlySelected = true, unsplitWhole = true),
            )
        }
        val topSegs = m.list.firstOrNull()?.let { m.segs[it.string] }.orEmpty()
        // 選び直した文節: 1 位と違う文節（いまの 1 位が正解なら、記録の最初の表記に無い文節。それも無ければ全部）
        var changed = if (idx > 0) spans.filter { BunsetsuAnalyzer.outputForRange(topSegs, it.start, it.end) != it.output } else emptyList()
        if (changed.isEmpty()) changed = spans.filter { !row.shown.contains(it.output) }
        if (changed.isEmpty()) changed = spans
        return spans.map { sp ->
            LearningFragment(
                reading = row.reading.substring(sp.start, sp.end),
                output = sp.output,
                candidateScore = cand.score,
                candidateIndex = idx.coerceAtLeast(1),
                explicitlySelected = sp in changed,
            )
        }
    }

    private suspend fun learn(repo: LearnRepository, reading: String, fragments: List<LearningFragment>, t: Long) {
        val session = ConversionLearningSession()
        session.beginIfNeeded(reading)
        fragments.forEach { session.record(it.copy(candidateIndex = if (it.explicitlySelected) it.candidateIndex.coerceAtLeast(1) else 0)) }
        val entries = session.finish(learnFirstCandidate = false, timestamp = t)
        if (entries.isNotEmpty()) repo.upsertLearnedDataBatch(entries, allowJapaneseWithSymbolsAndNumbers = true)
    }

    companion object {
        /** 1 行あたりの間隔（記録に時刻が無い。約 10 日で 180 行と仮定）。 */
        const val STEP_MS = 80L * 60 * 1000
    }
}

package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ExactInputCandidatePromotionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BunsetsuAnalyzer
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ScreenContextStore
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.SumireLmGate
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.TwoRowCandidateBarPlanner
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.graph.ContextLexicon
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.LatticeLmCost
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryRequest
import com.kazumaproject.markdownhelperkeyboard.converter.sumirelm.CandidateReranker
import com.kazumaproject.markdownhelperkeyboard.converter.sumirelm.CandidateRerankers
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * 端末の報告（2026-11-15）: 別のキーボードで入力欄に 中野 と打ち、necookey に切り替えて なかの と速く打つと 中の が 1 位。
 * IMEService の 2 段バーと同じ流れ（キーごとに 予測 → 変換、最後の読みで Sumire の 1 位 → リランカー → 2 段バーの 1 位）を
 * ScreenContextStore（実物）・ContextLexicon.provider・KanaKanjiConversionSession・ラティス表・（あれば）リランカー表で再現する。
 *
 * 切り替え直後の onStartInputView で入力欄の前後を読む（clean）→ な・なか・なかの。速く打つと途中のキーは表示まで行かないので、
 * 途中のキーでは何も記録しない（旧実装の「ラティスの後に記録」はここでは起きない = 新しい順序）。
 */
class KeyboardSwitchContextTest {
    private val pkg = "com.example.chat"

    @After
    fun tearDown() {
        ContextLexicon.provider = null
        LatticeLmCost.table = null
        ScreenContextStore.onKeyboardHidden()
        ScreenContextStore.clear()
    }

    private val dc by lazy { DeviceConfigConversion() }
    private val engine: KanaKanjiEngine by lazy { dc.newEngine() }

    private fun reranker(): CandidateReranker? {
        val dir = (System.getenv("NK_SUMIRE_LM")?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?: listOf(File("../slm_h56"), File("../../slm_h56")).firstOrNull { it.isDirectory })
            ?: return null
        return CandidateRerankers.load(dir)
    }

    private fun request(input: String, mode: CandidateQueryMode, learn: LearnRepository?) = KanaKanjiQueryRequest(
        input = input, mode = mode, bunsetsuSeparation = true, n = DeviceConfigConversion.DEVICE_N,
        mozcUtPersonName = false, mozcUtPlaces = false, mozcUtWiki = false, mozcUtNeologd = false, mozcUtWeb = false,
        userDictionaryRepository = dc.userDict, learnRepository = learn, beamWidth = 20,
        predictionConfig = PredictionConfig(), collectCandidateSegments = true,
    )

    private class Result(val primary: String, val sumire: String, val reranked: String?, val rerankedNoGuard: String?)

    /** 2 段バーの 1 位（キーごとに予測 → 変換、最後だけリランカー）。 */
    private fun typeFast(readings: List<String>, leftContext: String, learn: LearnRepository? = null, rr: CandidateReranker? = null): Result = runBlocking {
        val session = KanaKanjiConversionSession(engine)
        var conversion: List<Candidate> = emptyList()
        var segs: Map<String, List<CandidateConversionSegment>> = emptyMap()
        var splits: Map<String, List<Int>> = emptyMap()
        var primarySplits: List<Int> = emptyList()
        val input = readings.last()
        for (r in readings) {
            session.query(request(r, CandidateQueryMode.PREDICTION, learn))
            val core = session.query(request(r, CandidateQueryMode.CONVERSION, learn))
            conversion = ExactInputCandidatePromotionPolicy.promote(input = r, candidates = core.candidates)
            segs = core.candidateSegmentsByString
            splits = core.bunsetsuResult?.splitPatternByCandidateString.orEmpty()
            primarySplits = core.bunsetsuResult?.primarySplitPositions.orEmpty()
        }
        val enginePrimary = conversion.first { it.length.toInt() == input.length }
        val analysis = BunsetsuAnalyzer.analyze(input, conversion, segs, splits[enginePrimary.string] ?: primarySplits)
        var override: Candidate? = null
        var noGuard: String? = null
        if (rr != null) {
            val pool = session.queryRerankPool(request(input, CandidateQueryMode.CONVERSION, learn), SumireLmGate.POOL_SIZE)
            val segOf = { c: Candidate -> c.conversionSegments.ifEmpty { pool.candidateSegmentsByString[c.string].orEmpty() } }
            noGuard = SumireLmGate.rerank(rr, input, leftContext, pool.candidates, segOf)?.top?.string
            val lex = ContextLexicon.current()
            val d = SumireLmGate.rerank(rr, input, leftContext, pool.candidates, segOf,
                isContextWord = lex?.let { l -> { t: String -> l.wordBonus(t) > 0 } })
            override = d?.top?.takeIf { it.string != enginePrimary.string }
        }
        val bar = TwoRowCandidateBarPlanner.plan(
            input = input, conversionCandidates = conversion, analysis = analysis,
            primaryOverride = override, predictionCandidates = emptyList(),
        )
        Result(bar.primary?.string.orEmpty(), enginePrimary.string, override?.string, noGuard)
    }

    private val keys = listOf("な", "なか", "なかの")

    @Test
    fun editorContextAfterKeyboardSwitchMakesNakanoFirst() {
        LatticeLmCost.table = DeviceConfigConversion.latticeTableFile()?.inputStream()?.use { LatticeLmCost.load(it) }
        val rr = reranker()
        println("NKSW lattice table=${LatticeLmCost.table != null} reranker=${rr != null}")
        ContextLexicon.provider = { ScreenContextStore.lexicon(pkg, System.currentTimeMillis()) }

        // 文脈なし（比較用）
        ScreenContextStore.clear()
        val none = typeFast(keys, "", rr = rr)
        println("NKSW none: primary=${none.primary} sumire=${none.sumire} rerankNoGuard=${none.rerankedNoGuard}")

        // 切り替え直後（onStartInputView で変換中でないときに読む）: 入力欄に 中野
        ScreenContextStore.clear()
        ScreenContextStore.onKeyboardShown(pkg)
        ScreenContextStore.recordEditor(pkg, "中野", "", System.currentTimeMillis(), clean = true)
        val ed = typeFast(keys, "中野", rr = rr)
        println("NKSW editor: primary=${ed.primary} sumire=${ed.sumire} rerankNoGuard=${ed.rerankedNoGuard} lattice=${ScreenContextStore.lastLattice?.words}")
        // 変換中の読み取り（差し引きがずれて 野 を落とした before）は正確な記録を上書きしない
        ScreenContextStore.recordEditor(pkg, "中", "", System.currentTimeMillis(), clean = false)
        assertEquals(true, ScreenContextStore.hasCleanEditor(pkg, System.currentTimeMillis()))
        assertEquals("入力欄に 中野 → なかの の 1 位（ラティス）", "中野", ed.sumire)
        assertEquals("入力欄に 中野 → なかの の 1 位（2 段バー、リランカー後）", "中野", ed.primary)

        // 画面（ユーザー補助）だけ: チャットの吹き出しに 中野
        ScreenContextStore.clear()
        ScreenContextStore.onKeyboardShown(pkg)
        ScreenContextStore.recordScreen(pkg, listOf("中野さん、明日よろしく"), System.currentTimeMillis())
        val sc = typeFast(keys, "", rr = rr)
        println("NKSW screen: primary=${sc.primary} sumire=${sc.sumire} rerankNoGuard=${sc.rerankedNoGuard}")
        assertEquals("画面に 中野 → なかの の 1 位", "中野", sc.primary)
    }

    @Test
    fun contextWordBeatsLearnedWordNotInContext() {
        LatticeLmCost.table = DeviceConfigConversion.latticeTableFile()?.inputStream()?.use { LatticeLmCost.load(it) }
        val now = System.currentTimeMillis()
        val rows = listOf(LearnEntity(input = "なかの", out = "中の", usageCount = 3, lastUsedAt = now - 60_000L))
        val learn = mock<LearnRepository>().also { r ->
            whenever(r.conversionRevision) doAnswer { 1L }
            runBlocking {
                whenever(r.findCommonPrefixes(any())) doAnswer { inv -> rows.filter { (inv.arguments[0] as String).startsWith(it.input) } }
                whenever(r.findExactMatchesForConversion(any())) doAnswer { inv -> rows.filter { it.input == inv.arguments[0] as String } }
            }
        }
        ContextLexicon.provider = { ScreenContextStore.lexicon(pkg, System.currentTimeMillis()) }
        ScreenContextStore.clear()
        val learnedOnly = typeFast(keys, "", learn = learn)
        println("NKSW learned, no ctx: primary=${learnedOnly.primary}")
        assertEquals("学習だけ（文脈なし）は学習の語", "中の", learnedOnly.primary)
        ScreenContextStore.onKeyboardShown(pkg)
        ScreenContextStore.recordEditor(pkg, "中野", "", now, clean = true)
        val withCtx = typeFast(keys, "中野", learn = learn)
        println("NKSW learned + ctx: primary=${withCtx.primary}")
        assertEquals("入力欄の 中野 は文脈にない学習の 中の より上", "中野", withCtx.primary)
    }
}

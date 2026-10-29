package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ExactInputCandidatePromotionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.Chip
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipBarModel
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipBarPresenter
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipBarState
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipConversion
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipConverter
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipSegmentation
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.LearnedChipSplit
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.SeededChipConverter
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.SumireLmGate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.LatticeLmCost
import com.kazumaproject.markdownhelperkeyboard.converter.sumirelm.CandidateReranker
import com.kazumaproject.markdownhelperkeyboard.converter.sumirelm.SumireLmReranker
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.session.ConversionLearningSession
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * 学習の再現（docs/LEARNING.md「チップ候補欄の学習」）: チップで直して確定した文を、同じ読みでもう一度変換したとき
 * 文全体（1 位）と各チップ（区切り・表記）が再現されるか。実機既定の変換経路（DeviceConfigConversion）に
 * 学習辞書（メモリ上の表、LearnRepository のモック）をつなぎ、IMEService と同じ関数で学習・チップ化する。
 */
class ChipLearningReproductionTest {

    /** メモリ上の学習辞書（upsert は同じ読み・表記を 1 行にまとめる、句フラグは OR）。 */
    private class Store {
        val rows = LinkedHashMap<Pair<String, String>, LearnEntity>()
        var revision = 0L
        fun upsert(entries: List<LearnEntity>) {
            for (e in entries) {
                val k = e.input to e.out
                val old = rows[k]
                rows[k] = if (old == null) e else e.copy(isPhrase = old.isPhrase || e.isPhrase, usageCount = old.usageCount + 1)
            }
            revision++
        }

        fun repository(): LearnRepository = mock<LearnRepository>().also { r ->
            whenever(r.conversionRevision) doAnswer { revision }
            runBlocking {
                whenever(r.findCommonPrefixes(any())) doAnswer { inv ->
                    val term = inv.arguments[0] as String
                    rows.values.filter { term.startsWith(it.input) }
                }
                whenever(r.findExactMatchesForConversion(any())) doAnswer { inv ->
                    val term = inv.arguments[0] as String
                    rows.values.filter { it.input == term }
                }
            }
        }
    }

    /** IMEService の流れ: 変換 → 学習語で語分割を割る → 読み全体の変換 → 初期チップ。 */
    private fun chips(reading: String, store: Store): ChipBarState {
        val repo = store.repository()
        val segs = LinkedHashMap<String, List<CandidateConversionSegment>>()
        val res = runBlocking { dc.conversion(engine, reading, segs = segs, learnRepository = repo) }
        val conversion = ExactInputCandidatePromotionPolicy.promote(input = reading, candidates = res.candidates)
        val pieces = runBlocking {
            LearnedChipSplit.piecesFrom(reading, System.currentTimeMillis()) { repo.findCommonPrefixes(it) }
        }
        val seg = LearnedChipSplit.patch(
            reading,
            conversion,
            ChipSegmentation(segs, res.splitPatternByCandidateString),
            pieces,
        )
        val primary = conversion.firstOrNull { it.length.toInt() == reading.length }
        val whole = ChipBarPresenter.wholeConversion(
            reading = reading,
            primary = primary,
            conversion = conversion,
            segmentsByString = seg.segmentsByString,
            splitPatternByCandidateString = seg.splitPatternByCandidateString,
        )
        val model = ChipBarModel(SeededChipConverter(whole, ChipConverter { r, _ -> ChipConversion.unconverted(r) }))
        return model.initial(reading)
    }

    /** ユーザーが直した最終状態（[edited] の添字のチップは候補を選んだ／区切りを変えた）を確定して学習する。 */
    private fun commit(store: Store, reading: String, chips: List<Triple<Int, Int, String>>, edited: Set<Int>) {
        val before = chips(reading, store)
        val state = before.copy(
            chips = chips.mapIndexed { i, (s, e, o) ->
                Chip(s, e, o, if (i in edited) Chip.Origin.PICKED else Chip.Origin.BUNSETSU)
            },
            selected = -1,
            expanded = -1,
        )
        val session = ConversionLearningSession()
        session.beginIfNeeded(reading)
        ChipBarPresenter.learningSessionFragments(state, 3000).forEach(session::record)
        store.upsert(session.finish(learnFirstCandidate = false))
    }

    private fun labels(state: ChipBarState) = state.chips.map { it.output }

    @Test
    fun pickedChipIsReproducedForSameReading() {
        val store = Store()
        val reading = "きょうははいしゃにいく"
        commit(store, reading, listOf(Triple(0, 4, "今日は"), Triple(4, 9, "歯医者に"), Triple(9, 11, "行く")), setOf(1))
        val again = chips(reading, store)
        assertEquals("今日は歯医者に行く", again.text)
        assertEquals(listOf("今日は", "歯医者に", "行く"), labels(again))
        // 直したチップは別の文でも効く
        val other = chips("はいしゃにいった", store)
        assertTrue(other.text, other.text.startsWith("歯医者に"))
    }

    @Test
    fun movedBoundaryIsReproducedForLongReading() {
        // 12 文字超（文全体は 1 語として覚えない）: 区切りを動かしたチップと残りのチップで再現する
        val store = Store()
        val reading = "ここではきものをぬいでください"
        commit(
            store, reading,
            listOf(Triple(0, 3, "ここで"), Triple(3, 8, "履物を"), Triple(8, 11, "脱いで"), Triple(11, 15, "ください")),
            setOf(0, 1),
        )
        val again = chips(reading, store)
        assertEquals("ここで履物を脱いでください", again.text)
        assertEquals(listOf("ここで", "履物を"), labels(again).take(2))
    }

    @Test
    fun rerankerDoesNotOverrideLearnedTop() {
        val learned = Candidate("今日は歯医者に行く", CANDIDATE_TYPE_LEARNED_DICTIONARY, 11u, 500)
        val other = Candidate("今日は廃車に行く", 1, 11u, 9000)
        // 2 番目を好むリランカー
        val prefersSecond = object : CandidateReranker {
            override fun score(context: String, cands: List<SumireLmReranker.Input>) =
                DoubleArray(cands.size) { if (it == 1) 1.0 else 0.0 }
        }
        assertNull(SumireLmGate.rerank(prefersSecond, "きょうははいしゃにいく", "", listOf(learned, other), { emptyList() }))
    }

    companion object {
        private lateinit var dc: DeviceConfigConversion
        private lateinit var engine: KanaKanjiEngine

        @JvmStatic
        @BeforeClass
        fun setUpClass() {
            LatticeLmCost.table = DeviceConfigConversion.latticeTableFile()?.inputStream()?.use { LatticeLmCost.load(it) }
            dc = DeviceConfigConversion()
            engine = dc.newEngine()
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            LatticeLmCost.table = null
        }
    }
}

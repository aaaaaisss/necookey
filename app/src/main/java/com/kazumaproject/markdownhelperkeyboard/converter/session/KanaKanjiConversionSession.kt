package com.kazumaproject.markdownhelperkeyboard.converter.session

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CandidateQueryMode {
    NO_TAB_DEFAULT,
    PREDICTION,
    CONVERSION,
    EISUKANA,
}

data class KanaKanjiQueryRequest(
    val input: String,
    val mode: CandidateQueryMode,
    /** 互換のため残す。アプリは常に true で、セッションは常に文節分離経路を使う（false 用の旧経路は削除）。 */
    val bunsetsuSeparation: Boolean,
    val n: Int,
    val mozcUtPersonName: Boolean?,
    val mozcUtPlaces: Boolean?,
    val mozcUtWiki: Boolean?,
    val mozcUtNeologd: Boolean?,
    val mozcUtWeb: Boolean?,
    val userDictionaryRepository: UserDictionaryRepository,
    val learnRepository: LearnRepository?,
    val beamWidth: Int,
    val predictionConfig: PredictionConfig = PredictionConfig(),
    val collectCandidateSegments: Boolean = false,
)

data class KanaKanjiQueryResult(
    val candidates: List<Candidate>,
    val bunsetsuResult: BunsetsuCandidateResult? = null,
    val candidateSegmentsByString: Map<String, List<CandidateConversionSegment>> = emptyMap(),
)

/**
 * One InputConnection session's conversion entry point.
 *
 * Requests are serialized because GraphBuilder and FindPath mutate the graph nodes while searching.
 * (The former INCREMENTAL_SESSION backend with per-session lattice state was removed on 2026-10-30;
 * the engine's own one-character append cache still applies.)
 */
class KanaKanjiConversionSession(
    private val engine: KanaKanjiEngine,
) {
    private val mutex = Mutex()

    suspend fun query(request: KanaKanjiQueryRequest): KanaKanjiQueryResult = mutex.withLock {
        when (request.mode) {
            CandidateQueryMode.EISUKANA -> KanaKanjiQueryResult(
                candidates = engine.getCandidatesEnglishKana(
                    input = request.input,
                    predictionConfig = request.predictionConfig,
                ),
            )

            CandidateQueryMode.NO_TAB_DEFAULT -> queryOriginal(request)
            CandidateQueryMode.PREDICTION -> queryPrediction(request)
            CandidateQueryMode.CONVERSION -> queryConversion(request)
        }
    }

    private suspend fun queryOriginal(request: KanaKanjiQueryRequest): KanaKanjiQueryResult {
        val segmentCollector = request.newCandidateSegmentCollector()
        return engine.getCandidatesOriginalWithBunsetsu(
            input = request.input,
            n = request.n,
            mozcUtPersonName = request.mozcUtPersonName,
            mozcUTPlaces = request.mozcUtPlaces,
            mozcUTWiki = request.mozcUtWiki,
            mozcUTNeologd = request.mozcUtNeologd,
            mozcUTWeb = request.mozcUtWeb,
            userDictionaryRepository = request.userDictionaryRepository,
            learnRepository = request.learnRepository,
            beamWidth = request.beamWidth,
            predictionConfig = request.predictionConfig,
            candidateSegmentCollector = segmentCollector,
        ).asQueryResult(segmentCollector)
    }

    private suspend fun queryPrediction(request: KanaKanjiQueryRequest): KanaKanjiQueryResult {
        val segmentCollector = request.newCandidateSegmentCollector()
        return engine.getCandidatesWithBunsetsuSeparation(
            input = request.input,
            n = request.n,
            mozcUtPersonName = request.mozcUtPersonName,
            mozcUTPlaces = request.mozcUtPlaces,
            mozcUTWiki = request.mozcUtWiki,
            mozcUTNeologd = request.mozcUtNeologd,
            mozcUTWeb = request.mozcUtWeb,
            userDictionaryRepository = request.userDictionaryRepository,
            learnRepository = request.learnRepository,
            beamWidth = request.beamWidth,
            predictionConfig = request.predictionConfig,
            candidateSegmentCollector = segmentCollector,
        ).asQueryResult(segmentCollector)
    }

    /**
     * SUMIRE-LM リランカー用の候補プール（文節分離の変換候補 [n] 件と語分割）。表示用の問い合わせとは別に、
     * 計算する（予測なし、学習/ユーザー辞書は表示と同じ設定）。
     */
    suspend fun queryRerankPool(request: KanaKanjiQueryRequest, n: Int): KanaKanjiQueryResult =
        mutex.withLock {
            val segmentCollector = LinkedHashMap<String, List<CandidateConversionSegment>>()
            engine.getCandidatesWithoutPredictionWithBunsetsu(
                input = request.input,
                n = n,
                mozcUtPersonName = request.mozcUtPersonName,
                mozcUTPlaces = request.mozcUtPlaces,
                mozcUTWiki = request.mozcUtWiki,
                mozcUTNeologd = request.mozcUtNeologd,
                mozcUTWeb = request.mozcUtWeb,
                userDictionaryRepository = request.userDictionaryRepository,
                learnRepository = request.learnRepository,
                beamWidth = request.beamWidth,
                predictionConfig = request.predictionConfig.copy(
                    japanesePredictionEnabled = false,
                    englishPredictionEnabled = false,
                ),
                candidateSegmentCollector = segmentCollector,
            ).asQueryResult(segmentCollector)
        }

    private suspend fun queryConversion(request: KanaKanjiQueryRequest): KanaKanjiQueryResult {
        val segmentCollector = request.newCandidateSegmentCollector()
        return engine.getCandidatesWithoutPredictionWithBunsetsu(
            input = request.input,
            n = request.n,
            mozcUtPersonName = request.mozcUtPersonName,
            mozcUTPlaces = request.mozcUtPlaces,
            mozcUTWiki = request.mozcUtWiki,
            mozcUTNeologd = request.mozcUtNeologd,
            mozcUTWeb = request.mozcUtWeb,
            userDictionaryRepository = request.userDictionaryRepository,
            learnRepository = request.learnRepository,
            beamWidth = request.beamWidth,
            predictionConfig = request.predictionConfig.copy(
                japanesePredictionEnabled = false,
                englishPredictionEnabled = false,
            ),
            candidateSegmentCollector = segmentCollector,
        ).asQueryResult(segmentCollector)
    }

    private fun KanaKanjiQueryRequest.newCandidateSegmentCollector():
        MutableMap<String, List<CandidateConversionSegment>>? =
        if (collectCandidateSegments) LinkedHashMap() else null

    private fun BunsetsuCandidateResult.asQueryResult(
        segmentCollector: Map<String, List<CandidateConversionSegment>>?,
    ): KanaKanjiQueryResult = KanaKanjiQueryResult(
        candidates = candidates,
        bunsetsuResult = this,
        candidateSegmentsByString = segmentCollector.orEmpty(),
    )
}

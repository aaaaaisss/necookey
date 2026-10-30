package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.TestEngineFactory
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ExactInputCandidatePromotionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.NecookeyCandidateBarConfig
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.CompositeSystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.EmptySystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.PackedSystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramDictionary
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * 実機（IMEService 既定設定・新規インストール相当）と同じ変換経路をローカル JVM で再現する（評価・回帰ガード用）。
 *  - FindPath にシステム n-gram（assets/ngram、設定 system_ngram_dictionary_enable_preference 既定オン）
 *  - 人名・Wiki 辞書をラティスに（DictionaryOverrideStore.ALWAYS_ENABLED_OPTIONAL_DICTIONARIES）
 *  - 2 段バー上段 = CONVERSION モード getCandidatesWithoutPredictionWithBunsetsu、n = max(n_best 4, conversionNBest 8)、
 *    beam 20、予測オフ、ExactInputCandidatePromotionPolicy.promote 後に読み全体を覆う最初の候補が 1 位
 *  - ラティス表（LatticeLmCost.table）は呼び出し側で設定する（実機は lattice_lm_cost_preference 既定オンでアセットを読む）
 */
internal class DeviceConfigConversion(
    var useNgram: Boolean = true,
    utDict: Boolean = true,
) {
    val ngram: SystemNgramDictionary = loadSystemNgram()
    val userDict: UserDictionaryRepository = mock<UserDictionaryRepository>().also { u ->
        runBlocking { whenever(u.commonPrefixSearchInUserDict(any())).thenReturn(emptyList()) }
    }
    private val ut = utDict

    fun newEngine(): KanaKanjiEngine = TestEngineFactory.create(
        findPath = FindPath(systemNgramDictionaryProvider = { if (useNgram) ngram else EmptySystemNgramDictionary }),
    ).also { if (ut) TestEngineFactory.attachAlwaysOnOptionalDictionaries(it) }

    suspend fun conversion(
        engine: KanaKanjiEngine,
        reading: String,
        n: Int = DEVICE_N,
        segs: MutableMap<String, List<CandidateConversionSegment>>? = null,
        learnRepository: LearnRepository? = null,
    ): BunsetsuCandidateResult = engine.getCandidatesWithoutPredictionWithBunsetsu(
        input = reading, n = n,
        mozcUtPersonName = false, mozcUTPlaces = false, mozcUTWiki = false,
        mozcUTNeologd = false, mozcUTWeb = false,
        userDictionaryRepository = userDict, learnRepository = learnRepository,
        beamWidth = 20,
        predictionConfig = PredictionConfig().copy(japanesePredictionEnabled = false, englishPredictionEnabled = false),
        candidateSegmentCollector = segs,
    )

    /** 実機上段の候補（読み全体を覆うものだけ、重複なし）。先頭が実機の 1 位。 */
    fun deviceTopRow(engine: KanaKanjiEngine, reading: String): List<Candidate> {
        val res = runBlocking { conversion(engine, reading) }
        val seen = HashSet<String>()
        return ExactInputCandidatePromotionPolicy.promote(input = reading, candidates = res.candidates)
            .filter { it.length.toInt() == reading.length && seen.add(it.string) }
    }

    companion object {
        val DEVICE_N: Int = maxOf(4, NecookeyCandidateBarConfig.DEFAULT.conversionNBest)

        fun assetsDir(): File =
            listOf(File("app/src/main/assets"), File("src/main/assets")).first { it.exists() }

        fun loadSystemNgram(): SystemNgramDictionary {
            val dir = File(assetsDir(), "ngram")
            val dicts = listOf("system_ngram.dat", "system_ngram_unigram.dat")
                .map { File(dir, it) }.filter { it.isFile }
                .map { PackedSystemNgramDictionary.read(it.readBytes()) }
            return when (dicts.size) {
                0 -> EmptySystemNgramDictionary
                1 -> dicts.single()
                else -> CompositeSystemNgramDictionary(dicts)
            }
        }

        /** ラティス表の場所: 環境変数 NK_LATTICE_COST、無ければリポジトリ隣の eval/lc_np1m.slc（どちらも無ければ null）。 */
        fun latticeTableFile(): File? =
            (System.getenv("NK_LATTICE_COST")?.takeIf { it.isNotBlank() }?.let { File(it) }
                ?: listOf(File("../eval/lc_np1m.slc"), File("../../eval/lc_np1m.slc")).firstOrNull { it.isFile })
                ?.takeIf { it.isFile }
    }
}

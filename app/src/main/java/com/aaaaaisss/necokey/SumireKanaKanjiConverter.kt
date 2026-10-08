package com.aaaaaisss.necokey

import android.content.Context
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.connection_id.ConnectionIdBuilder
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.graph.GraphBuilder
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcNodeAttributeTable
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcNodeAttributeTableReader
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenter
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenterDataReader
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import java.io.BufferedInputStream
import java.io.ObjectInputStream
import java.util.zip.ZipInputStream

class SumireKanaKanjiConverter(context: Context) {
    private data class TripleDictionary(
        val tango: LOUDS,
        val yomi: LOUDSWithTermId,
        val token: TokenArray,
        val yomiLbs: SuccinctBitVector,
        val yomiLeaf: SuccinctBitVector,
        val tokenIndex: SuccinctBitVector,
        val tangoLbs: SuccinctBitVector,
    )

    private val engine = KanaKanjiEngine()
    private val userDictionary = UserDictionaryRepository()
    
    init {
        val connection = loadConnectionMatrix(context)
        val system = loadTriple(context, "system/tango.dat.zip", "system/yomi.dat.zip", "system/token.dat.zip", true)
        val singleKanji = loadTriple(context, "single_kanji/tango_singleKanji.dat", "single_kanji/yomi_singleKanji.dat", "single_kanji/token_singleKanji.dat", false)
        val emoji = loadTriple(context, "emoji/tango_emoji.dat", "emoji/yomi_emoji.dat", "emoji/token_emoji.dat", false)
        val emoticon = loadTriple(context, "emoticon/tango_emoticon.dat", "emoticon/yomi_emoticon.dat", "emoticon/token_emoticon.dat", false)
        val symbol = loadTriple(context, "symbol/tango_symbol.dat", "symbol/yomi_symbol.dat", "symbol/token_symbol.dat", false)
        val readingCorrection = loadTriple(context, "reading_correction/tango_reading_correction.dat", "reading_correction/yomi_reading_correction.dat", "reading_correction/token_reading_correction.dat", false)
        val kotowaza = loadTriple(context, "kotowaza/tango_kotowaza.dat", "kotowaza/yomi_kotowaza.dat", "kotowaza/token_kotowaza.dat", false)

        val graphBuilder = GraphBuilder()
        val findPath = FindPath()
        val (segmenter, nodeAttributes) = loadMozcAssets(context)

        engine.buildEngine(
            graphBuilder = graphBuilder,
            findPath = findPath,
            connectionMatrix = connection,
            systemTangoTrie = system.tango,
            systemYomiTrie = system.yomi,
            systemTokenArray = system.token,
            systemSuccinctBitVectorLBSYomi = system.yomiLbs,
            systemSuccinctBitVectorIsLeafYomi = system.yomiLeaf,
            systemSuccinctBitVectorTokenArray = system.tokenIndex,
            systemSuccinctBitVectorTangoLBS = system.tangoLbs,
            singleKanjiTangoTrie = singleKanji.tango,
            singleKanjiYomiTrie = singleKanji.yomi,
            singleKanjiTokenArray = singleKanji.token,
            singleKanjiSuccinctBitVectorLBSYomi = singleKanji.yomiLbs,
            singleKanjiSuccinctBitVectorIsLeafYomi = singleKanji.yomiLeaf,
            singleKanjiSuccinctBitVectorTokenArray = singleKanji.tokenIndex,
            singleKanjiSuccinctBitVectorTangoLBS = singleKanji.tangoLbs,
            emojiTangoTrie = emoji.tango,
            emojiYomiTrie = emoji.yomi,
            emojiTokenArray = emoji.token,
            emojiSuccinctBitVectorLBSYomi = emoji.yomiLbs,
            emojiSuccinctBitVectorIsLeafYomi = emoji.yomiLeaf,
            emojiSuccinctBitVectorTokenArray = emoji.tokenIndex,
            emojiSuccinctBitVectorTangoLBS = emoji.tangoLbs,
            emoticonTangoTrie = emoticon.tango,
            emoticonYomiTrie = emoticon.yomi,
            emoticonTokenArray = emoticon.token,
            emoticonSuccinctBitVectorLBSYomi = emoticon.yomiLbs,
            emoticonSuccinctBitVectorIsLeafYomi = emoticon.yomiLeaf,
            emoticonSuccinctBitVectorTokenArray = emoticon.tokenIndex,
            emoticonSuccinctBitVectorTangoLBS = emoticon.tangoLbs,
            symbolTangoTrie = symbol.tango,
            symbolYomiTrie = symbol.yomi,
            symbolTokenArray = symbol.token,
            symbolSuccinctBitVectorLBSYomi = symbol.yomiLbs,
            symbolSuccinctBitVectorIsLeafYomi = symbol.yomiLeaf,
            symbolSuccinctBitVectorTokenArray = symbol.tokenIndex,
            symbolSuccinctBitVectorTangoLBS = symbol.tangoLbs,
            readingCorrectionTangoTrie = readingCorrection.tango,
            readingCorrectionYomiTrie = readingCorrection.yomi,
            readingCorrectionTokenArray = readingCorrection.token,
            readingCorrectionSuccinctBitVectorLBSYomi = readingCorrection.yomiLbs,
            readingCorrectionSuccinctBitVectorIsLeafYomi = readingCorrection.yomiLeaf,
            readingCorrectionSuccinctBitVectorTokenArray = readingCorrection.tokenIndex,
            readingCorrectionSuccinctBitVectorTangoLBS = readingCorrection.tangoLbs,
            kotowazaTangoTrie = kotowaza.tango,
            kotowazaYomiTrie = kotowaza.yomi,
            kotowazaTokenArray = kotowaza.token,
            kotowazaSuccinctBitVectorLBSYomi = kotowaza.yomiLbs,
            kotowazaSuccinctBitVectorIsLeafYomi = kotowaza.yomiLeaf,
            kotowazaSuccinctBitVectorTokenArray = kotowaza.tokenIndex,
            kotowazaSuccinctBitVectorTangoLBS = kotowaza.tangoLbs,
            engineEngine = EnglishEngine(),
            mozcSegmenter = segmenter,
            mozcNodeAttributeTable = nodeAttributes,
            mozcDictionaryActive = segmenter != null && nodeAttributes != null,
        )
    }

    suspend fun candidates(input: String, n: Int = 12): List<Candidate> {
        if (input.isEmpty()) return emptyList()
        return engine.getCandidatesWithoutPrediction(
            input = input,
            n = n,
            mozcUtPersonName = false,
            mozcUTPlaces = false,
            mozcUTWiki = false,
            mozcUTNeologd = false,
            mozcUTWeb = false,
            userDictionaryRepository = userDictionary,
            learnRepository = null,
            typoCorrectionOffsetScore = 0,
            omissionSearchOffsetScore = 0,
            predictionConfig = PredictionConfig(
                japanesePredictionEnabled = false,
                englishPredictionEnabled = false,
                symbolEmojiEnabled = false,
                showSymbolCandidates = false,
                showEmojiCandidates = false,
                showEmoticonCandidates = false,
            ),
        )
    }

    private fun loadTriple(
        context: Context,
        tangoPath: String,
        yomiPath: String,
        tokenPath: String,
        zipped: Boolean,
    ): TripleDictionary {
        val tango = LOUDS()
        openAsset(context, tangoPath, zipped).use { input ->
            ObjectInputStream(BufferedInputStream(input)).use { tango.readExternalNotCompress(it) }
        }

        val yomi = LOUDSWithTermId()
        openAsset(context, yomiPath, zipped).use { input ->
            ObjectInputStream(BufferedInputStream(input)).use { yomi.readExternalNotCompress(it) }
        }

        val token = TokenArray()
        openAsset(context, tokenPath, zipped).use { input ->
            ObjectInputStream(BufferedInputStream(input)).use { token.readExternal(it) }
        }
        context.assets.open("pos_table.dat").use { input ->
            ObjectInputStream(BufferedInputStream(input)).use { token.readPOSTable(it) }
        }

        return TripleDictionary(
            tango = tango,
            yomi = yomi,
            token = token,
            yomiLbs = SuccinctBitVector(yomi.LBS),
            yomiLeaf = SuccinctBitVector(yomi.isLeaf),
            tokenIndex = SuccinctBitVector(token.bitvector),
            tangoLbs = SuccinctBitVector(tango.LBS),
        )
    }

    private fun loadConnectionMatrix(context: Context): ConnectionMatrix.CostTable {
        ZipInputStream(context.assets.open("connectionId.dat.zip")).use { zip ->
            require(zip.nextEntry != null) { "connectionId.dat.zip is empty" }
            val values = ConnectionIdBuilder().readShortArrayFromBytes(zip)
            return ConnectionMatrix.fromShortArray(values)
        }
    }

    private fun openAsset(
        context: Context,
        path: String,
        zipped: Boolean,
    ) = if (zipped) {
        ZipInputStream(context.assets.open(path)).also {
            require(it.nextEntry != null) { "Asset ZIP is empty: $path" }
        }
    } else {
        context.assets.open(path)
    }

    private fun loadMozcAssets(context: Context): Pair<MozcSegmenter?, MozcNodeAttributeTable?> {
        return runCatching {
            val segmenter = context.assets.open("mozc/segmenter.dat").use {
                MozcSegmenter(MozcSegmenterDataReader().read(it))
            }
            val attributes = context.assets.open("mozc/node_attribute_by_lid.dat").use {
                MozcNodeAttributeTableReader().read(it)
            }
            segmenter to attributes
        }.getOrElse { null to null }
    }
}

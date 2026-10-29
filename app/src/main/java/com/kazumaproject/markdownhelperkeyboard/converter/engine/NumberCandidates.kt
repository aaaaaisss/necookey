package com.kazumaproject.markdownhelperkeyboard.converter.engine

import android.content.Context
import android.os.Build
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.convertFullWidthToHalfWidth
import com.kazumaproject.core.domain.extensions.toHankakuAlphabet
import com.kazumaproject.core.domain.extensions.toHankakuKatakana
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoticon.Emoticon
import com.kazumaproject.data.symbol.Symbol
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.domain.categorizeEmoji
import com.kazumaproject.domain.sortByEmojiCategory
import com.kazumaproject.domain.toEmoticonCategory
import com.kazumaproject.domain.toSymbolCategory
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.ConnectionMatrix
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.graph.GraphBuilder
import com.kazumaproject.markdownhelperkeyboard.converter.graph.GraphNodeDedupMode
import com.kazumaproject.markdownhelperkeyboard.converter.graph.SystemDictionaryErrata
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcBoundaryMode
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcNodeAttributeTable
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcSegmenter
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.SplitReconversionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.SplitReconversionResult
import com.kazumaproject.markdownhelperkeyboard.converter.trace.BoundaryTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.CandidateTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.ConversionTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.ForwardDpTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.GraphNodeTrace
import com.kazumaproject.markdownhelperkeyboard.converter.trace.PenaltyTrace
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategoryLoadState
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileKey
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileRole
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryFileSpecs
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideValidator
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.addCommasToNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.containsDigit
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.containsFullWidthNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertFullWidthAlnumToHalfWidth
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertFullWidthNumbersToHalfWidth
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.convertToKanjiNotation
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.createValueBasedSymbolCandidates
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllEnglishLetters
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllFullWidthAscii
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHalfWidthAscii
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.replaceJapaneseCharactersForEnglish
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toFullWidth
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toKanji
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumber
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toNumberExponent
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toSubscriptDigits
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.toSuperscriptDigits
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.toFullWidthDigitsEfficient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.util.Calendar
import java.util.Locale

// 数字入力の候補（漢数字・全角・記号つき）と半角候補（KanaKanjiEngine から分離、中身は無変更）。

internal fun generateNumberCandidates(
    input: String,
    showSymbolCandidates: Boolean = true,
): List<Candidate> {
    val numPair = input.toNumber()
    val expoPair = input.toNumberExponent()

    return if (numPair != null) {
        val (firstNum, secondNum) = numPair // firstNum: 全角, secondNum: 半角
        val numberAsLong = secondNum.toLongOrNull()

        // 候補リストを構築
        val candidates = mutableListOf<Candidate>()

        // 1. 伝統的な漢数字 (例: 十, 百二十三)
        if (numberAsLong != null) {
            candidates.add(
                Candidate(
                    string = numberAsLong.toKanji(), type = 32, // 新しいタイプ
                    length = input.length.toUByte(), score = 8000, // 優先度を調整
                    leftId = POS_ID_NUMBER_KANJI, rightId = POS_ID_NUMBER_KANJI
                )
            )
        }

        // 2. 単位付き漢数字 (例: 1億2345万)
        if (numberAsLong != null) {
            candidates.add(
                Candidate(
                    string = numberAsLong.convertToKanjiNotation(),
                    type = 17,
                    length = input.length.toUByte(),
                    score = 8000,
                    leftId = POS_ID_NUMBER_KANJI,
                    rightId = POS_ID_NUMBER_KANJI
                )
            )
        }

        // 3. 全角・半角数字 (例: １２３, 123)
        listOf(firstNum, secondNum).forEach {
            candidates.add(
                Candidate(
                    string = it,
                    type = if (it == firstNum) (30).toByte() else (31).toByte(),
                    length = input.length.toUByte(),
                    score = 8002,
                    leftId = POS_ID_NUMBER_ARABIC,
                    rightId = POS_ID_NUMBER_ARABIC
                )
            )
        }

        // 4. カンマ区切り数字 (例: 123,456)
        candidates.add(
            Candidate(
                string = secondNum.addCommasToNumber(),
                type = 19,
                length = input.length.toUByte(),
                score = 8001,
                leftId = POS_ID_NUMBER_SEPARATED,
                rightId = POS_ID_NUMBER_SEPARATED
            )
        )

        // 5. 指数表記 (例: 10⁸)
        if (expoPair != null) {
            candidates.add(
                Candidate(
                    string = expoPair.first,
                    type = 20,
                    length = input.length.toUByte(),
                    score = 8003,
                    leftId = POS_ID_NUMBER_ARABIC,
                    rightId = POS_ID_NUMBER_ARABIC
                )
            )
        }

        candidates += createJapaneseNumberValueBasedCandidates(
            input = input,
            showSymbolCandidates = showSymbolCandidates,
        )

        candidates

    } else {
        emptyList()
    }
}

/**
 * Candidate リストを処理し、
 * string に全角数字が含まれる場合、それを半角数字に変換した
 * 新しい Candidate をリストに追加します。
 *
 * @param originalData 元の Pair<List<Candidate>, List<Int>>
 * @return 元の候補と、半角変換された候補の両方を含む新しい Pair
 */
internal fun addHalfWidthCandidates(
    originalData: Pair<List<Candidate>, List<Int>>
): Pair<List<Candidate>, List<Int>> {

    val originalList = originalData.first
    val intList = originalData.second

    // flatMap を使ってリストを変換・拡張します
    val newList = originalList.flatMap { candidate ->
        // 1. string に全角数字が含まれているかチェック
        if (candidate.string.containsFullWidthNumber()) {

            // 2. 全角数字を半角に変換
            val newString = candidate.string.convertFullWidthNumbersToHalfWidth()

            // 3. 元の候補のコピーを作成し、string だけを新しい文字列に差し替え
            val newCandidate = candidate.copy(
                string = newString, type = 31, score = candidate.score + 6000
                // type, length, score など他のプロパティはそのままコピーされます
            )

            // 4. 元の候補 (例: １８時) と、
            //    新しい候補 (例: 18時) の両方をリストとして返す
            listOf(candidate, newCandidate)

        } else {
            // 5. 全角数字を含まない場合は、元の候補だけをリストとして返す
            listOf(candidate)
        }
    }.distinct() // 最後にリスト全体の重複を除去します

    // 6. 新しく作成した候補リストと、元の Int リストで Pair を再構築して返す
    return Pair(newList, intList)
}

internal fun addHalfWidthCandidates(
    originalData: BunsetsuCandidateResult
): BunsetsuCandidateResult {
    val newList = addHalfWidthCandidates(originalData.candidates)
    return BunsetsuCandidateResult(
        candidates = newList,
        splitPatterns = originalData.splitPatterns,
        splitPatternByCandidateString = originalData.splitPatternByCandidateString
    )
}

/**
 * Candidate リストを処理し、
 * string に全角数字が含まれる場合、それを半角数字に変換した
 * 新しい Candidate をリストに追加します。
 *
 * @param originalList 元の List<Candidate>
 * @return 元の候補と、半角変換された候補の両方を含む新しい List
 */
internal fun addHalfWidthCandidates(
    originalList: List<Candidate>
): List<Candidate> {

    // flatMap を使ってリストを変換・拡張します
    val newList = originalList.flatMap { candidate ->
        // 1. string に全角数字が含まれているかチェック
        if (candidate.string.containsFullWidthNumber()) {

            // 2. 全角数字を半角に変換
            val newString = candidate.string.convertFullWidthAlnumToHalfWidth()

            // 3. 元の候補のコピーを作成し、string だけを新しい文字列に差し替え
            val newCandidate = candidate.copy(
                string = newString, type = 31, score = candidate.score + 6000
                // type, length, score など他のプロパティはそのままコピーされます
            )

            // 4. 元の候補 (例: １８時) と、
            //    新しい候補 (例: 18時) の両方をリストとして返す
            listOf(candidate, newCandidate)

        } else {
            // 5. 全角数字を含まない場合は、元の候補だけをリストとして返す
            listOf(candidate)
        }
    }.distinct() // 最後にリスト全体の重複を除去します

    // 6. 新しく作成した候補リストを返す
    return newList
}

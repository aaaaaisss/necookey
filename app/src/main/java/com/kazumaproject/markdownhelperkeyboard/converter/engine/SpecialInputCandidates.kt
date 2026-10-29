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
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_ERA
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
import java.util.Locale

// 年号・数字・助数詞など、読みから決まる特殊候補（KanaKanjiEngine から分離）。
// 「いま」→現在時刻・「ことし」→西暦/令和、数字 4 桁→時刻・月日のような日付・時刻の特殊候補は 2026-10-29 に削除（ユーザー要望）。

internal fun createDigitCandidates(inputDigits: String, inputLength: UByte): List<Candidate> {
    val halfWidthDigits = inputDigits.convertFullWidthNumbersToHalfWidth()
    val fullWidthDigits = halfWidthDigits.toFullWidthDigitsEfficient()

    val fullWidth = Candidate(
        string = fullWidthDigits,
        type = 22,
        length = inputLength,
        score = 8000,
        leftId = POS_ID_NUMBER_ARABIC,
        rightId = POS_ID_NUMBER_ARABIC
    )
    val halfWidth = Candidate(
        string = halfWidthDigits.convertFullWidthToHalfWidth(),
        type = 31,
        length = inputLength,
        score = 8000,
        leftId = POS_ID_NUMBER_ARABIC,
        rightId = POS_ID_NUMBER_ARABIC
    )

    val numberValue = halfWidthDigits.toLongOrNull()
    val numberCandidates = if (numberValue != null) {
        buildList {
            add(
                Candidate(
                    string = numberValue.toKanji(),
                    type = 17,
                    score = 2000,
                    length = inputLength,
                    leftId = POS_ID_NUMBER_KANJI,
                    rightId = POS_ID_NUMBER_KANJI
                )
            )
            add(
                Candidate(
                    string = halfWidthDigits.addCommasToNumber(),
                    type = 19,
                    score = 8001,
                    length = inputLength,
                    leftId = POS_ID_NUMBER_SEPARATED,
                    rightId = POS_ID_NUMBER_SEPARATED
                )
            )
            add(
                Candidate(
                    string = halfWidthDigits,
                    type = 18,
                    score = 8002,
                    length = inputLength,
                    leftId = POS_ID_NUMBER_ARABIC,
                    rightId = POS_ID_NUMBER_ARABIC
                )
            )
            add(
                Candidate(
                    string = numberValue.convertToKanjiNotation(),
                    type = 23,
                    score = 7900,
                    length = inputLength,
                    leftId = POS_ID_NUMBER_KANJI,
                    rightId = POS_ID_NUMBER_KANJI
                )
            )
        }
    } else {
        emptyList()
    }

    return listOf(fullWidth, halfWidth) + numberCandidates
}

internal fun createCandidatesForJapaneseNumberWithUnit(input: String): List<Candidate> {
    val unitMappings = listOf(
        "にん" to "人", "えん" to "円", "ぷん" to "分", "ふん" to "分", "じ" to "時"
    )

    for ((readingSuffix, unit) in unitMappings) {
        if (!input.endsWith(readingSuffix) || input.length <= readingSuffix.length) continue

        val numberReading = normalizeJapaneseNumberReadingForCounter(
            input.removeSuffix(readingSuffix),
            readingSuffix,
        ) ?: continue
        val number = numberReading.toNumber() ?: continue
        val isTimeLike = unit == "時" || unit == "分"
        val rightId = if (unit == "時") POS_ID_COUNTER_TIME else POS_ID_COUNTER_GENERIC

        return listOf(
            Candidate(
                string = "${number.second}$unit",
                type = if (isTimeLike) CANDIDATE_TYPE_TIME else 18,
                length = input.length.toUByte(),
                score = 8000,
                leftId = POS_ID_NUMBER_ARABIC,
                rightId = rightId
            ), Candidate(
                string = "${number.first}$unit",
                type = if (isTimeLike) 30 else 22,
                length = input.length.toUByte(),
                score = 8001,
                leftId = POS_ID_NUMBER_ARABIC,
                rightId = rightId
            )
        )
    }

    return emptyList()
}

internal fun normalizeJapaneseNumberReadingForCounter(
    numberReading: String,
    counterReading: String,
): String? {
    fun isStandaloneOrAfterPlace(alias: String): Boolean {
        if (numberReading == alias) return true
        val prefix = numberReading.dropLast(alias.length)
        return listOf("じゅう", "ひゃく", "せん", "まん", "おく", "ちょう")
            .any(prefix::endsWith)
    }

    // 「し」は単独の四としては有効だが、現在扱っている助数詞の
    // 直前では通常語との衝突が大きい（しじ、しえん、しにん等）。
    if (numberReading.endsWith("し") && isStandaloneOrAfterPlace("し")) return null
    if (counterReading != "じ") return numberReading

    return when {
        numberReading.endsWith("よ") && isStandaloneOrAfterPlace("よ") ->
            numberReading.dropLast(1) + "よん"
        numberReading.endsWith("く") && isStandaloneOrAfterPlace("く") ->
            numberReading.dropLast(1) + "きゅう"
        else -> numberReading
    }
}

internal fun createCandidatesForEra(year: Int, input: String): List<Candidate> {
    // 元号名、開始年、終了年（null は現在まで）
    data class Era(val name: String, val start: Int, val end: Int?)

    val eras = listOf(
        Era("令和", 2019, null),    // 令和は継続中
        Era("平成", 1989, 2019),    // 平成は1989～2019
        Era("昭和", 1926, 1989),    // 昭和は1926～1989
        Era("大正", 1912, 1926),    // 大正は1912～1926
        Era("明治", 1868, 1912)     // 明治は1868～1912
    )

    val length = input.length.toUByte()

    fun formatEra(eraName: String, eraYear: Int) =
        eraName + if (eraYear == 1) "元年" else "${eraYear}年"

    return eras.filter { (_, start, end) ->
        year >= start && (end == null || year <= end)
    }.map { (name, start, _) ->
        val eraYear = year - start + 1
        Candidate(
            string = formatEra(name, eraYear),
            type = CANDIDATE_TYPE_ERA,
            length = length,
            score = 70000,
            leftId = 1851,
            rightId = 1851
        )
    }
}

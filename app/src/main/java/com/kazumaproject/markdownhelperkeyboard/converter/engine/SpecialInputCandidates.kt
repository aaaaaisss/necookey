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
import java.util.Calendar
import java.util.Locale

// 日付・時刻・年・数字・助数詞など、読みから決まる特殊候補（KanaKanjiEngine から分離、中身は無変更）。

internal fun createTemporalDictionaryCandidates(input: String): List<Candidate> = when (input) {
    "いま" -> {
        val now = Calendar.getInstance()
        createCandidatesForTime(now, input)
    }

    "ことし" -> createCandidatesForRelativeYear(input, yearOffset = 0)
    "きょねん" -> createCandidatesForRelativeYear(input, yearOffset = -1)
    "らいねん" -> createCandidatesForRelativeYear(input, yearOffset = 1)
    else -> emptyList()
}

internal fun createCandidatesForRelativeYear(input: String, yearOffset: Int): List<Candidate> {
    val calendar = Calendar.getInstance().apply { add(Calendar.YEAR, yearOffset) }
    val year = calendar.get(Calendar.YEAR)
    val reiwaYear = year - 2018
    val zodiac = listOf(
        "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"
    )[Math.floorMod(year - 4, 12)]
    val length = input.length.toUByte()

    val baseCandidates = mutableListOf(
        Candidate(
            string = "${year}年",
            type = 14,
            length = length,
            score = 7000,
            leftId = 1851,
            rightId = 1851
        ), Candidate(
            string = "${zodiac}年",
            type = 14,
            length = length,
            score = 7002,
            leftId = 1851,
            rightId = 1851
        )
    )

    if (reiwaYear > 0) {
        baseCandidates += Candidate(
            string = "令和${reiwaYear}年",
            type = 14,
            length = length,
            score = 7001,
            leftId = 1851,
            rightId = 1851
        )
        baseCandidates += Candidate(
            string = "R${reiwaYear}",
            type = 14,
            length = length,
            score = 7003,
            leftId = 1851,
            rightId = 1851
        )
    }

    return baseCandidates
}

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
    val timeConversion = createCandidatesForTime(halfWidthDigits)
    val dateConversion = createCandidatesForDateInDigit(halfWidthDigits)

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

    return listOf(fullWidth, halfWidth) + timeConversion + dateConversion + numberCandidates
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

/**
 * 4桁の数字を時刻の候補に変換する。
 *
 * @param input "0000"から"2959"までの4桁の数字文字列。
 * @return 時刻の候補リスト。条件に合わない場合は空のリストを返す。
 */
internal fun createCandidatesForTime(input: String): List<Candidate> {
    // 入力が4桁の数字でない場合は早期リターン
    if (!input.matches(Regex("""\d{4}"""))) {
        return emptyList()
    }

    val number = input.toInt()

    // 全体の数値が 0 から 2959 の範囲内かチェック
    if (number !in 0..2959) {
        return emptyList()
    }

    // 下2桁（分）が 0 から 59 の範囲内かチェック
    val minutes = number % 100
    if (minutes > 59) { // Redundant 'minutes < 0' check removed
        return emptyList()
    }

    // 時間と分を2桁の文字列として取り出す
    val hoursStr = input.substring(0, 2)
    val minutesStr = input.substring(2, 4)

    val length = input.length.toUByte()

    // 2つのフォーマットの候補を作成
    val candidate1 = Candidate(
        string = "$hoursStr:$minutesStr",
        type = CANDIDATE_TYPE_TIME,
        length = length,
        score = 8000,
        leftId = 1851,
        rightId = 1851
    )

    val candidate2 = Candidate(
        string = "${hoursStr}時${minutesStr}分",
        type = CANDIDATE_TYPE_TIME,
        length = length,
        score = 8000,
        leftId = 1851,
        rightId = 1851
    )

    return listOf(candidate1, candidate2)
}

/**
 * 3桁または4桁の数字を月日の候補に変換する。
 *
 * @param input "101"から"1231"のような3桁または4桁の数字文字列。
 * @return 月日の候補リスト。条件に合わない場合は空のリストを返す。
 */
internal fun createCandidatesForDateInDigit(input: String): List<Candidate> {
    // 入力が3桁または4桁の数字でない場合は早期リターン
    if (!input.matches(Regex("""\d{3,4}"""))) {
        return emptyList()
    }

    // 最後の2桁を「日」、それより前を「月」として分割
    val dayStr = input.substring(input.length - 2)
    val monthStr = input.substring(0, input.length - 2)

    val month = monthStr.toInt()
    val day = dayStr.toInt()

    // 月が1から12の範囲内かチェック
    if (month !in 1..12) {
        return emptyList()
    }

    // 日が1から31の範囲内かチェック（簡略版）
    // ※より厳密にする場合は、月ごとの日数（30日、31日、閏年など）を考慮する必要があります。
    if (day !in 1..31) {
        return emptyList()
    }

    // 候補の文字列を作成（例: 5月12日）
    // .toInt()で変換しているため、"05"のような先頭のゼロは自動的に除去されます。
    val dateString = "${month}月${day}日"

    val length = input.length.toUByte()

    // 候補を作成
    val candidate = Candidate(
        string = dateString, type = 40,
        length = length, score = 8000, leftId = 1851, // 必要に応じて日付用のIDに変更
        rightId = 1851  // 必要に応じて日付用のIDに変更
    )

    return listOf(candidate)
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

internal fun createCandidatesForTime(cal: Calendar, input: String): List<Candidate> {
    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val minutePadded = minute.toString().padStart(2, '0')

    // 12時間表記と午前/午後
    val meridiem = if (hour24 < 12) "午前" else "午後"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }

    return listOf(
        // 例: "14時5分"
        Candidate(
            string = "${hour24}時${minute}分",
            type = 14,
            length = input.length.toUByte(),
            score = 7000,
            leftId = 1851,
            rightId = 1851
        ),
        // 例: "14:05"
        Candidate(
            string = "${
                hour24.toString().padStart(2, '0')
            }:$minutePadded",
            type = 14,
            length = input.length.toUByte(),
            score = 7001,
            leftId = 1851,
            rightId = 1851
        ),
        // 例: "午後2時5分"
        Candidate(
            string = "$meridiem${hour12}時${minute}分",
            type = 14,
            length = input.length.toUByte(),
            score = 7003,
            leftId = 1851,
            rightId = 1851
        ),
        Candidate(
            string = "${hour12}時${minute}分",
            type = 14,
            length = input.length.toUByte(),
            score = 7004,
            leftId = 1851,
            rightId = 1851
        ),
        Candidate(
            string = "${
                hour12.toString().padStart(2, '0')
            }:$minutePadded",
            type = 14,
            length = input.length.toUByte(),
            score = 7002,
            leftId = 1851,
            rightId = 1851
        ),
    )
}

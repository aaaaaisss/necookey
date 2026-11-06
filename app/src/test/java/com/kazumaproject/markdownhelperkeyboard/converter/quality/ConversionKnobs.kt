package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.graph.DialectInflectionNodes
import com.kazumaproject.markdownhelperkeyboard.converter.graph.KanaDialectNodes
import com.kazumaproject.markdownhelperkeyboard.converter.graph.LongVowelNodes
import com.kazumaproject.markdownhelperkeyboard.converter.graph.RenyouMotionNodes
import com.kazumaproject.markdownhelperkeyboard.converter.graph.SystemDictionaryErrata
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.PosPairPenalty
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.TsugihagiKanaRepair

/** 評価用: 変換の調整値をキーで切り替える（SumireNbestDumpTest の NK_CONFIGS）。reset() で既定値に戻す。 */
internal object ConversionKnobs {
    fun reset() {
        DialectInflectionNodes.enabled = true
        DialectInflectionNodes.suffixCost = DialectInflectionNodes.DEFAULT_SUFFIX_COST
        KanaDialectNodes.enabled = true
        KanaDialectNodes.penalty = KanaDialectNodes.DEFAULT_PENALTY
        PosPairPenalty.haMergePenalty = PosPairPenalty.HA_MERGE_PENALTY
        PosPairPenalty.haMergeTimeOnly = false
        SystemDictionaryErrata.enabled = true
        DialectInflectionNodes.lmStemEnabled = true
        TsugihagiKanaRepair.position = TsugihagiKanaRepair.DEFAULT_POSITION
        TsugihagiKanaRepair.minWordCost = TsugihagiKanaRepair.DEFAULT_MIN_WORD_COST
        TsugihagiKanaRepair.maxPieceYomi = TsugihagiKanaRepair.DEFAULT_MAX_PIECE_YOMI
        TsugihagiKanaRepair.minRun = 2
        LongVowelNodes.enabled = true
        LongVowelNodes.penalty = LongVowelNodes.DEFAULT_PENALTY
        RenyouMotionNodes.enabled = true
        RenyouMotionNodes.penalty = RenyouMotionNodes.DEFAULT_PENALTY
        DialectInflectionNodes.potentialNegativeEnabled = true
    }

    fun set(key: String, value: String) {
        when (key) {
            "dinf" -> DialectInflectionNodes.enabled = value == "1"
            "dcost" -> DialectInflectionNodes.suffixCost = value.toInt()
            "kdia" -> KanaDialectNodes.enabled = value == "1"
            "kpen" -> KanaDialectNodes.penalty = value.toInt()
            "ha" -> PosPairPenalty.haMergePenalty = value.toInt()
            "hat" -> PosPairPenalty.haMergeTimeOnly = value == "1"
            "err" -> SystemDictionaryErrata.enabled = value == "1"
            "lms" -> DialectInflectionNodes.lmStemEnabled = value == "1"
            "tp" -> TsugihagiKanaRepair.position = value.toInt()
            "tc" -> TsugihagiKanaRepair.minWordCost = value.toInt()
            "ty" -> TsugihagiKanaRepair.maxPieceYomi = value.toInt()
            "tr" -> TsugihagiKanaRepair.minRun = value.toInt()
            "lv" -> LongVowelNodes.enabled = value == "1"
            "lvp" -> LongVowelNodes.penalty = value.toInt()
            "rm" -> RenyouMotionNodes.enabled = value == "1"
            "rmp" -> RenyouMotionNodes.penalty = value.toInt()
            "ren" -> DialectInflectionNodes.potentialNegativeEnabled = value == "1"
            else -> error("unknown knob $key")
        }
    }

    fun describe(): String =
        "dinf=${DialectInflectionNodes.enabled} dcost=${DialectInflectionNodes.suffixCost} kdia=${KanaDialectNodes.enabled} " +
            "kpen=${KanaDialectNodes.penalty} ha=${PosPairPenalty.haMergePenalty} hat=${PosPairPenalty.haMergeTimeOnly} err=${SystemDictionaryErrata.enabled} " +
            "lms=${DialectInflectionNodes.lmStemEnabled} tp=${TsugihagiKanaRepair.position} tc=${TsugihagiKanaRepair.minWordCost} " +
            "ty=${TsugihagiKanaRepair.maxPieceYomi} tr=${TsugihagiKanaRepair.minRun} lv=${LongVowelNodes.enabled}/${LongVowelNodes.penalty} " +
            "rm=${RenyouMotionNodes.enabled}/${RenyouMotionNodes.penalty}"
}

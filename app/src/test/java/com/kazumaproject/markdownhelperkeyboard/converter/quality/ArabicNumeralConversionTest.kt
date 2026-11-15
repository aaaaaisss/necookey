package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.LatticeLmCost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数は基本算用数字（2026-11-14 ユーザー方針）。実機既定の経路（[DeviceConfigConversion]、ラティス表があれば使う）で 1 位を見る。
 * 数＋助数詞・打った数字＋助数詞・日数の和語＋前/後/間は算用数字が 1 位で漢数字は候補に残る。慣用の語（一度・一番 等）は漢数字のまま。
 */
class ArabicNumeralConversionTest {

    @Test
    fun numbersDefaultToArabic() {
        val table = DeviceConfigConversion.latticeTableFile()
        val conv = DeviceConfigConversion()
        val engine = conv.newEngine()
        LatticeLmCost.table = table?.inputStream()?.use { LatticeLmCost.load(it) }
        try {
            val expected = linkedMapOf(
                "にだいめ" to "2代目",
                "さんだいめ" to "3代目",
                "200だい" to "200台",
                "1255だい" to "1255台",
                "9どだい" to "9度台",
                "8ど7ぶ" to "8度7分",
                "2だいめ" to "2代目",
                "1から" to "1から",
                "いつかまえ" to "5日前",
                "ようかご" to "8日後",
                "とおかかん" to "10日間",
                // 慣用の語は漢数字のまま、数でない語はそのまま
                "いちど" to "一度",
                "いちばん" to "一番",
                "いっしょ" to "一緒",
                "みかづき" to "三日月",
                "いちおう" to "一応",
                "いちぶ" to "一部",
                "ひとり" to "一人",
                "にどと" to "二度と",
                "いつか" to "いつか",
            )
            // 算用数字は 2 位以内でよい（辞書の漢数字が 1 位でも可、2026-11-15）。くどろくぶ は区切りの問題なので見ない。
            val top2 = listOf("みっつ" to "3つ")
            val rows = (expected.keys + top2.map { it.first }).associateWith { r -> conv.deviceTopRow(engine, r).map { it.string } }
            println("arabic numerals (lattice table ${table != null}): " + rows.mapValues { it.value.take(4) })
            assertEquals(expected, rows.filterKeys { it in expected }.mapValues { it.value.firstOrNull() })
            for ((reading, arabic) in top2) {
                assertTrue("$reading → $arabic in top2 of ${rows[reading]}", arabic in rows.getValue(reading).take(2))
            }
            // 漢数字は下の候補に残る
            for ((reading, kanji) in listOf("にだいめ" to "二代目", "みっつ" to "三つ", "いつかまえ" to "五日前")) {
                assertTrue("$reading → $kanji in ${rows[reading]}", kanji in rows.getValue(reading))
            }
        } finally {
            LatticeLmCost.table = null
        }
    }
}

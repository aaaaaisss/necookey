package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.LatticeLmCost
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 回帰ガード（買って／県庁／困難／広島県／三年）を実機既定の経路（[DeviceConfigConversion]、ラティス表あり）で確認する。
 * 表 lc_np1m.slc（lm-data 63732f8、リポジトリ外）が無ければスキップ。県庁は 2026-10-26 時点で既知の誤り（堅調）なので
 * 現状値を固定し、直ったらこのテストが落ちて気付けるようにしている（変換エンジンを直したら期待値を更新する）。
 */
class DeviceConfigRegressionGuardTest {
    @Test
    fun guards() {
        val table = DeviceConfigConversion.latticeTableFile()
        assumeTrue("lattice table not found (set NK_LATTICE_COST)", table != null)
        val conv = DeviceConfigConversion()
        val engine = conv.newEngine()
        LatticeLmCost.table = table!!.inputStream().use { LatticeLmCost.load(it) }
        try {
            val expected = linkedMapOf(
                "しーでぃーかってきた" to "CD買ってきた",
                "それはこんなんやな" to "それは困難やな",
                "ひろしまけんにすんでる" to "広島県に住んでる",
                "さんねんぶりにあった" to "三年ぶりにあった",
                // 既知の誤り（正解 県庁に行った は 3 位）
                "けんちょうにいった" to "堅調に行った",
            )
            val actual = expected.keys.associateWith { conv.deviceTopRow(engine, it).firstOrNull()?.string }
            println("regression guards: $actual")
            assertEquals(expected, actual)
            val kencho = conv.deviceTopRow(engine, "けんちょうにいった").map { it.string }
            assertEquals(true, "県庁に行った" in kencho.take(5))
        } finally {
            LatticeLmCost.table = null
        }
    }
}

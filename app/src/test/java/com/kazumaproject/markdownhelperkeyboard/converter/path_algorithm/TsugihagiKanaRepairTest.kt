package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/** ツギハギ抑制（短い低頻度の漢字片が続く区間をかなに戻した候補を足す）。 */
class TsugihagiKanaRepairTest {
    @Before
    fun setUp() {
        // 片の判定の単体テストは旧既定（語コスト 4000・読み 3 字）の閾値で書いてある。既定値そのものは defaults で見る。
        TsugihagiKanaRepair.position = 2
        TsugihagiKanaRepair.minWordCost = 4000
        TsugihagiKanaRepair.maxPieceYomi = 3
        TsugihagiKanaRepair.minRun = 2
    }

    @After
    fun tearDown() {
        TsugihagiKanaRepair.position = TsugihagiKanaRepair.DEFAULT_POSITION
        TsugihagiKanaRepair.minWordCost = TsugihagiKanaRepair.DEFAULT_MIN_WORD_COST
        TsugihagiKanaRepair.maxPieceYomi = TsugihagiKanaRepair.DEFAULT_MAX_PIECE_YOMI
        TsugihagiKanaRepair.minRun = 2
    }

    @Test
    fun defaults() {
        // 2026-10-29 本採用: 修復候補を 1 位・語コスト 3000 以上・読み 4 字以下。
        assertEquals(1, TsugihagiKanaRepair.DEFAULT_POSITION)
        assertEquals(3000, TsugihagiKanaRepair.DEFAULT_MIN_WORD_COST)
        assertEquals(4, TsugihagiKanaRepair.DEFAULT_MAX_PIECE_YOMI)
        // 新しい閾値では 4 字の トマチン も片になり、地位|トマチン|祭 がかなに戻る。
        TsugihagiKanaRepair.minWordCost = TsugihagiKanaRepair.DEFAULT_MIN_WORD_COST
        TsugihagiKanaRepair.maxPieceYomi = TsugihagiKanaRepair.DEFAULT_MAX_PIECE_YOMI
        val (s, _) = TsugihagiKanaRepair.repair(listOf(n("地位", "ちい", 4586), n("トマチン", "とまちん", 8013), n("祭", "さい", 4500)))!!
        assertEquals("ちいとまちんさい", s)
    }

    private fun n(tango: String, yomi: String, cost: Int, id: Short = 1851) =
        Node(l = id, r = id, score = cost, f = cost, tango = tango, len = yomi.length.toShort(), yomiUsed = yomi, sPos = 0)

    private fun cand(s: String, score: Int) = Candidate(string = s, type = 1, length = 8u, score = score)

    @Test
    fun repairsRunOfRareShortKanjiPieces() {
        // 地位|トマチン|祭 の 地位(ちい)・祭(さい) は短いが トマチン は 4 字 → 片の連続は無い
        assertNull(TsugihagiKanaRepair.repair(listOf(n("地位", "ちい", 4586), n("トマチン", "とまちん", 8013), n("祭", "さい", 4500))))
        // 何|も|胃炎|買っ|た: 胃炎(いえん 6010)・買っ(かっ 4200) が続く → いえんかっ
        val path = listOf(n("何", "なに", 1000), n("も", "も", 0, 284), n("胃炎", "いえん", 6010), n("買っ", "かっ", 4200, 841), n("た", "た", 0, 142))
        val (s, segs) = TsugihagiKanaRepair.repair(path)!!
        assertEquals("何もいえんかった", s)
        assertEquals(listOf("何", "も", "いえんかっ", "た"), segs.map { it.output })
        assertEquals(listOf(0, 2, 3, 8), segs.map { it.inputStart })
    }

    @Test
    fun keepsFrequentOrNumericOrSinglePieces() {
        // 頻出（語コストが低い）漢語複合は対象外
        assertNull(TsugihagiKanaRepair.repair(listOf(n("日本", "にほん", 2000), n("語", "ご", 3000))))
        // 片が 1 つだけ
        assertNull(TsugihagiKanaRepair.repair(listOf(n("移管", "いかん", 5000), n("？", "？", 0))))
        // 名詞,数 は片にしない
        assertNull(TsugihagiKanaRepair.repair(listOf(n("三", "さん", 5000, 2045), n("年", "ねん", 5000))))
    }

    @Test
    fun insertsAtConfiguredPosition() {
        val path = listOf(n("胃炎", "いえん", 6010), n("買っ", "かっ", 4200, 841), n("た", "た", 0, 142))
        val ranked = listOf(cand("胃炎買った", 100), cand("いえん勝った", 120), cand("言えんかった", 130))
        val out = TsugihagiKanaRepair.apply(ranked, "胃炎買った", path, null)
        assertEquals(listOf("胃炎買った", "いえんかった", "いえん勝った", "言えんかった"), out.map { it.string })
        assertEquals(listOf(100, 101, 120, 130), out.map { it.score })
        TsugihagiKanaRepair.position = 1
        val first = TsugihagiKanaRepair.apply(ranked, "胃炎買った", path, null).first()
        assertEquals("いえんかった" to 99, first.string to first.score)
        // 1 位が経路と違えば（n-gram 並べ替え後など）触らない、無効なら触らない
        assertSame(ranked, TsugihagiKanaRepair.apply(ranked, "別の文", path, null))
        TsugihagiKanaRepair.position = 0
        assertSame(ranked, TsugihagiKanaRepair.apply(ranked, "胃炎買った", path, null))
    }
}

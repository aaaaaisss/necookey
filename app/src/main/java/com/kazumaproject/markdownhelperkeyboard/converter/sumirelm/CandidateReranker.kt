package com.kazumaproject.markdownhelperkeyboard.converter.sumirelm

import java.io.File

/**
 * 全読み候補の再採点器（リランカー）の差し替え口。実装は Android 非依存にして JVM テストで評価できるようにする。
 *
 * 入力: 左文脈 [context]（最大 NecookeyCandidateBarConfig.maxLeftContextChars 字）と、Sumire の全読み候補 pool
 *       （文節分離 N-best の上位 SumireLmGate.POOL_SIZE = 50、重複なし、Sumire の順）。各候補は [SumireLmReranker.Input]
 *       （表記・Sumire コスト・順位・文節数・候補種別・語分割の出力列）。
 * 出力: [cands] と同じ順・同じ長さのスコア（大きいほど良い）。並べ替え・同点処理・ユーザー辞書の保護は SumireLmGate が行う。
 * 手順は docs/RERANKER_HOOKUP.md。
 */
interface CandidateReranker {
    fun score(context: String, cands: List<SumireLmReranker.Input>): DoubleArray
}

/**
 * 表ディレクトリからリランカーを読む。端末（IMEService: files/sumire_lm/）と評価（NK_RERANK_DIR）の両方がここを通る。
 * 新しい表形式は [formats] に 1 行足す（先に一致したものを使う）。どれにも当たらなければ null（リランカーなし）。
 */
object CandidateRerankers {
    class Format(
        val name: String,
        /** この形式の表に必須のファイル名（アセットからのコピー判定にも使う）。 */
        val requiredFiles: List<String>,
        val load: (File) -> CandidateReranker?,
    )

    val formats: List<Format> = listOf(
        Format("sumire-lm-typed", SumireLmTyped.REQUIRED_FILES) { SumireLmTyped.load(it) },
        Format("sumire-lm-packed", SumireLmReranker.REQUIRED_FILES_PACKED) { SumireLmReranker.load(it) },
        Format("sumire-lm-od", SumireLmReranker.REQUIRED_FILES) { SumireLmReranker.load(it) },
    )

    fun load(dir: File): CandidateReranker? =
        if (!dir.isDirectory) null
        else formats.firstNotNullOfOrNull { f -> if (f.requiredFiles.all { File(dir, it).isFile }) f.load(dir) else null }

    /** アセット等のファイル名一覧が、いずれかの形式の表として揃っているか。 */
    fun isCompleteTable(names: Collection<String>): Boolean = formats.any { f -> f.requiredFiles.all { it in names } }
}

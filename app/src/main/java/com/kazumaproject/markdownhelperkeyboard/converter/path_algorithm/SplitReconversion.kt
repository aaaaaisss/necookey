package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment

/**
 * 区切りを固定した再変換（split-constrained re-conversion、SUMIRE-LM 実験・既定では無効）。
 *
 * 通常の N-best（文字列の K-best）に出る文節区切りは少数（中央値 4 種類）に偏るため、
 * 区切りが正しい候補が N-best の外に落ちる。そこで
 *  1. N-best に出た区切り（出現順）と、文節区切りだけを状態に持つ K-best 探索で得た区切りを合わせて上位 [maxPatterns] 種類、
 *  2. 各区切りについて、各スパン（読みの区間）の辞書上位 [wordsPerSpan] 語（Sumire の語コスト順、同表記は同順位）だけを使い、
 *     区切りに合うノードだけのラティスで前向き Viterbi（正確なヒューリスティック）と後ろ向き A* により
 *     Sumire の経路コスト（連接コスト + 語コスト + n-gram 補正）で上位 [perPattern] 文字列、
 * を作って追加候補として返す。候補表示順は変えない（呼び出し側が collector を渡したときだけ計算する）。
 *
 * UI で文節を伸縮したときの再変換も「区切りを固定してスパンごとに語を選び直す」同じ操作なので、
 * [FindPath.splitConstrainedReconversion] に区切りを 1 つ渡せば共通化できる（HANDOFF.md の設計メモ参照）。
 */
data class SplitReconversionConfig(
    val maxPatterns: Int = 8,
    val wordsPerSpan: Int = 3,
    val perPattern: Int = 8,
    /**
     * 区切りごとに「各ノードを必ず通る最良経路」（区切り固定の前向き/後ろ向き Viterbi、n-gram 補正なし）を
     * コスト順に最大この数だけ追加する（0 = 使わない）。1 語だけ入れ替えた候補を、組み合わせの K-best より深く拾う。
     */
    val perPatternForced: Int = 0,
    /** 区切り探索・各区切りの A* で取り出す要素数の上限（長い入力での安全弁） */
    val popBudget: Int = 40_000,
)

data class SplitReconversionCandidate(
    val candidate: Candidate,
    val segments: List<CandidateConversionSegment>,
    /** 区切りの順位（0 始まり。N-best 由来が先、その後に区切り探索由来） */
    val patternIndex: Int,
    /** 経路で使った語のスパン内順位の最大値（0 始まり） */
    val maxWordRank: Int,
    val splitPositions: List<Int>,
    /** その区切りが通常の N-best に出ていたか */
    val patternFromNBest: Boolean,
    /** true = ノード固定の最良経路（perPatternForced）、false = 区切り内 A* の K-best。同じ文字列が両方に出ることがある */
    val forced: Boolean = false,
)

/** 呼び出し側が渡す受け皿。candidates に追加候補、elapsedNs に追加処理の所要時間、patterns に使った区切り。 */
class SplitReconversionResult {
    val candidates = mutableListOf<SplitReconversionCandidate>()
    val patterns = mutableListOf<List<Int>>()
    var nBestPatternCount: Int = 0
    var elapsedNs: Long = 0
}

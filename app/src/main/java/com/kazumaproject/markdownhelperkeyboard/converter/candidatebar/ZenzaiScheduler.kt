package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

/**
 * Zenzai の推論をいつ始めるか。
 *
 * 1 語を打つのに 5 打鍵以上かかるので、毎打鍵すぐ推論すると大半は次の打鍵で中断されて捨てられる
 * （中断までに使った CPU は無駄）。そこで:
 *
 * - 打鍵後すぐではなく、その人の打鍵間隔より少し長く入力が止まったら始める（[startDelayMs]）。
 *   打鍵が続けば推論は始まらず、手が止まった瞬間に 1 回だけ走る。止まった後は PASS まで続けて判定するので、
 *   見るときの候補の質は毎打鍵推論と同じ。
 * - ただし長い文を止まらずに打ち続けると、前の文節の直し（FIX 制約）が溜まらない。[FORCE_AFTER] 打鍵
 *   推論していなければ待たずに始め、その推論は後続の打鍵で中断しない（[isForced]）。
 */
class ZenzaiScheduler {
    companion object {
        /** 待ち時間の下限・上限（ms）。 */
        const val MIN_DELAY_MS = 90L
        const val MAX_DELAY_MS = 260L

        /** 打鍵間隔の何倍止まったら「止まった」とみなすか。 */
        const val GAP_FACTOR = 1.3f

        /** これより長い間隔は打鍵リズムに数えない（考えている時間）。 */
        const val RHYTHM_GAP_MAX_MS = 900L

        /** 推論せずに続いた打鍵がこれ以上なら待たずに推論する。 */
        const val FORCE_AFTER = 8
    }

    private var rhythmMs = -1f
    private var lastKeyAt = -1L
    private var lastInput = ""
    private var sinceInference = 0

    /** 読みが変わった（打鍵・削除）ときに呼ぶ。同じ読みの再描画は数えない。 */
    @Synchronized
    fun onInput(input: String, now: Long) {
        if (input == lastInput) return
        val gap = now - lastKeyAt
        if (lastKeyAt >= 0 && gap in 1 until RHYTHM_GAP_MAX_MS) {
            rhythmMs = if (rhythmMs < 0) gap.toFloat() else rhythmMs * 0.7f + gap * 0.3f
        }
        lastKeyAt = now
        lastInput = input
        if (input.isEmpty()) sinceInference = 0 else sinceInference++
    }

    /** 待たずに始める（後続の打鍵で中断しない）推論か。 */
    @Synchronized
    fun isForced(): Boolean = sinceInference >= FORCE_AFTER

    /** 推論を始めるまでの待ち時間。 */
    @Synchronized
    fun startDelayMs(): Long {
        if (sinceInference >= FORCE_AFTER) return 0L
        if (rhythmMs < 0) return MIN_DELAY_MS
        return (rhythmMs * GAP_FACTOR).toLong().coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    /** 推論を実際に始めたときに呼ぶ。 */
    @Synchronized
    fun onInferenceStarted() {
        sinceInference = 0
    }
}

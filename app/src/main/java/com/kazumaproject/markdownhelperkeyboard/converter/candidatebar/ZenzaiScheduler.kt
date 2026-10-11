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
 * - 読みが [BACKGROUND_AFTER_CHARS] 文字を超えたら、打鍵中も裏で推論を回す（[runsInBackground]）。
 *   結果は変換欄に出さず、FIX の直しだけを次の推論へ引き継ぐ。推論中に来た打鍵では中断せず、
 *   終わったら最新の読みでもう一度走らせる。手が止まったときの仕上げは 1〜2 回で済み、表示が速い。
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

        /** 読みがこの文字数を超えたら、打鍵中も裏で推論する。 */
        const val BACKGROUND_AFTER_CHARS = 3
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

    /** 打鍵中も裏で（待たず、後続の打鍵で中断せず、表示せずに）推論する読みか。 */
    fun runsInBackground(input: String): Boolean = input.length > BACKGROUND_AFTER_CHARS

    /** 最後の打鍵から「手が止まった」とみなすまでの残り時間（[now] 時点）。 */
    @Synchronized
    fun remainingPauseMs(now: Long): Long =
        if (lastKeyAt < 0) 0L else (startDelayMs() - (now - lastKeyAt)).coerceAtLeast(0L)

    /** 推論を始めるまでの待ち時間。 */
    @Synchronized
    fun startDelayMs(): Long {
        if (rhythmMs < 0) return MIN_DELAY_MS
        return (rhythmMs * GAP_FACTOR).toLong().coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    /** 推論を実際に始めたときに呼ぶ。 */
    @Synchronized
    fun onInferenceStarted() {
        sinceInference = 0
    }
}

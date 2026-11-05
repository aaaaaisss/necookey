package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

/**
 * 画面文脈（ユーザー承認 2026-11-03）。すべてメモリだけで保存しない・送らない（アプリにネットワーク権限なし）。
 *
 * 3 つの層:
 *  - [Source.EDITOR] 入力欄のカーソル前後（各 [EDITOR_CHARS] 字、変換中の文字列は除く）。最新 1 件だけ。
 *  - [Source.COMMIT] 最近確定した表記（アプリ = パッケージごと、[COMMIT_TTL_MS] = 30 分）。会話の中で一度 県庁 に直したら県庁のまま。
 *  - [Source.SCREEN] ユーザー補助サービス（任意・既定オフ）が拾った、いま入力しているアプリの画面の語（漢字・カタカナ 2 字以上だけ、
 *    元の文字列は残さない。キーボードが出ている間だけ。隠れたら捨てる。[SCREEN_TTL_MS] = 3 分）。
 *
 * 使い方は [ScreenContextBonus]: 候補の語（Sumire の語分割の出力）が画面文脈の文字列に含まれていれば、リランカーの
 * スコアに小さく加点する。読みは候補自身が入力の読みの変換なので「読み一致」は常に満たす（逆引き辞書は不要）。
 * 加点は上位 [ScreenContextBonus.TOP_N] 件・1 位との差 [ScreenContextBonus.MAX_GAP] 以内の候補だけ、1 候補あたり
 * [ScreenContextBonus.CAP] まで。2 字以上で漢字かカタカナを含む語だけ（1 字漢字は 今日 の 日 などに当たるので数えない）。
 * 既定の重みはオフライン調整なしの控えめな値（端末で確認する）。
 */
object ScreenContextStore {
    enum class Source { EDITOR, COMMIT, SCREEN }

    const val EDITOR_CHARS = 200
    const val EDITOR_TTL_MS = 10 * 60 * 1000L
    const val COMMIT_TTL_MS = 30 * 60 * 1000L
    const val SCREEN_TTL_MS = 3 * 60 * 1000L
    const val MAX_COMMITS_PER_APP = 200
    const val MAX_SCREEN_CHARS = 6000
    const val MAX_APPS = 8

    private class Entry(val text: String, val time: Long)

    private val lock = Any()
    private var editorPkg: String = ""
    private var editor: Entry? = null
    private val commits = LinkedHashMap<String, ArrayDeque<Entry>>()
    private val screens = LinkedHashMap<String, Entry>()

    /** 入力欄の前後（パスワード欄・学習しない欄では呼ばない）。 */
    fun recordEditor(pkg: String, before: String, after: String, now: Long) = synchronized(lock) {
        editorPkg = pkg
        editor = Entry(before.takeLast(EDITOR_CHARS) + "\n" + after.take(EDITOR_CHARS), now)
    }

    fun recordCommit(pkg: String, text: String, now: Long) {
        if (text.isBlank() || !text.any(::isKanjiOrKatakana)) return
        synchronized(lock) {
            val q = commits.remove(pkg) ?: ArrayDeque()
            commits[pkg] = q
            q.addLast(Entry(text, now))
            while (q.size > MAX_COMMITS_PER_APP) q.removeFirst()
            while (commits.size > MAX_APPS) commits.remove(commits.keys.first())
        }
    }

    /**
     * ユーザー補助サービスから: 前面アプリの見えている文字列（呼ぶたびに置き換え）。**語だけを残す**: 漢字・カタカナの 2 字以上の
     * 並び（[extractWords]）を取り出し、元の文字列（ひらがな・数字・記号を含む全文）はここで捨てる。キーボードが出ていないときは何もしない。
     */
    fun recordScreen(pkg: String, texts: List<String>, now: Long) {
        if (!keyboardVisible) return
        val words = LinkedHashSet<String>()
        var chars = 0
        for (t in texts) {
            if (chars >= MAX_SCREEN_CHARS) break
            for (w in extractWords(t)) if (words.add(w)) chars += w.length + 1
        }
        synchronized(lock) {
            screens.remove(pkg)
            if (words.isNotEmpty()) screens[pkg] = Entry(words.joinToString("\n"), now)
            while (screens.size > MAX_APPS) screens.remove(screens.keys.first())
        }
    }

    // ---- キーボードの表示とユーザー補助の接続（ユーザー補助はキーボードが出ている間だけ読む） ----

    /** necookey のキーボードが出ている間 true（IMEService の onStartInputView / onFinishInputView）。 */
    @Volatile var keyboardVisible: Boolean = false
        private set
    /** いま入力しているアプリ（EditorInfo.packageName）。ユーザー補助はこのアプリのウィンドウだけ読む。 */
    @Volatile var inputPackage: String = ""
        private set
    /** ユーザー補助サービスが接続中か（設定の状態表示用）。 */
    @Volatile var serviceConnected: Boolean = false

    fun onKeyboardShown(pkg: String) {
        inputPackage = pkg
        keyboardVisible = true
    }

    /** キーボードが隠れた: 画面の語を捨てる（入力欄の前後・確定の記録は残す）。 */
    fun onKeyboardHidden() {
        keyboardVisible = false
        clearScreen()
    }

    // ---- 確認用の数（メモリだけ。設定の状態表示） ----

    private val recent = ArrayDeque<Boolean>()
    private var recentApplied = 0
    @Volatile private var lastInput: String = ""
    @Volatile private var lastApplied: Boolean = false

    /** リランカー 1 回分: 画面文脈の加点が付いたか [applied]・それで 1 位が変わったか [changedTop]（読み [input]）。 */
    fun noteConversion(input: String, applied: Boolean, changedTop: Boolean) {
        lastInput = input
        lastApplied = applied
        synchronized(lock) {
            recent.addLast(changedTop)
            if (changedTop) recentApplied++
            while (recent.size > RECENT_CONVERSIONS) if (recent.removeFirst()) recentApplied--
        }
    }

    /** 読み [input] の直前の変換で画面文脈の加点が付いたか（変換ミスの記録の ctx 列）。 */
    fun contextAppliedFor(input: String): Boolean = lastInput == input && lastApplied

    data class Stats(
        val serviceConnected: Boolean,
        val keyboardVisible: Boolean,
        val screenWords: Int,
        val editorWords: Int,
        val commitEntries: Int,
        val conversions: Int,
        val changedByContext: Int,
    )

    fun stats(now: Long): Stats = synchronized(lock) {
        val sc = screens.values.filter { now - it.time <= SCREEN_TTL_MS }.sumOf { e -> e.text.split('\n').count { it.isNotEmpty() } }
        val ed = editor?.takeIf { now - it.time <= EDITOR_TTL_MS }?.let { extractWords(it.text).size } ?: 0
        val cm = commits.values.sumOf { q -> q.count { now - it.time <= COMMIT_TTL_MS } }
        Stats(serviceConnected, keyboardVisible, sc, ed, cm, recent.size, recentApplied)
    }

    const val RECENT_CONVERSIONS = 100

    /** 漢字（々 含む）・カタカナ（ー 含む）が 2 字以上続く並び。ひらがな・数字・記号・英字は捨てる。 */
    fun extractWords(text: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            if (!isKanjiOrKatakana(text[i])) {
                i++
                continue
            }
            var j = i + 1
            while (j < text.length && (isKanjiOrKatakana(text[j]) || text[j] == 'ー')) j++
            if (j - i >= 2) out.add(text.substring(i, j))
            i = j
        }
        return out
    }

    /** 端末を閉じた・設定を切ったときなど。 */
    fun clear() = synchronized(lock) {
        editor = null; editorPkg = ""; commits.clear(); screens.clear()
        recent.clear(); recentApplied = 0
    }

    fun clearScreen() = synchronized(lock) { screens.clear() }

    /** [pkg] のいまの画面文脈。何もなければ null。期限切れはここで捨てる。 */
    fun snapshot(pkg: String, now: Long): ScreenContextBonus? = synchronized(lock) {
        val ed = editor?.takeIf { editorPkg == pkg && now - it.time <= EDITOR_TTL_MS }?.text.orEmpty()
        val q = commits[pkg]
        q?.removeAll { now - it.time > COMMIT_TTL_MS }
        val cm = q?.joinToString("\n") { it.text }.orEmpty()
        val iter = screens.entries.iterator()
        while (iter.hasNext()) if (now - iter.next().value.time > SCREEN_TTL_MS) iter.remove()
        val sc = screens[pkg]?.text.orEmpty()
        if (ed.isEmpty() && cm.isEmpty() && sc.isEmpty()) null else ScreenContextBonus(editor = ed, commits = cm, screen = sc)
    }

    internal fun isKanjiOrKatakana(c: Char): Boolean =
        c in '\u4E00'..'\u9FFF' || c == '々' || c in 'ァ'..'ヺ'
}

/** 画面文脈の文字列（[ScreenContextStore.snapshot]）に基づくリランカースコアへの加点。 */
class ScreenContextBonus(
    private val editor: String,
    private val commits: String,
    private val screen: String,
    private val wEditor: Double = W_EDITOR,
    private val wCommit: Double = W_COMMIT,
    private val wScreen: Double = W_SCREEN,
) {
    companion object {
        /** 1 語あたりの加点（リランカーの線形スコアの単位。旧 zenz ゲートのマージン既定 0.5 と同じ尺度）。 */
        const val W_EDITOR = 0.4
        const val W_COMMIT = 0.6
        const val W_SCREEN = 0.3
        const val CAP = 0.8
        const val TOP_N = 5
        const val MAX_GAP = 1.5
    }

    private fun eligible(token: String): Boolean =
        token.length >= 2 && token.any(ScreenContextStore::isKanjiOrKatakana)

    /** 語 [token] の加点（どの層に出てくるかの最大）。 */
    fun tokenBonus(token: String): Double {
        if (!eligible(token)) return 0.0
        var b = 0.0
        if (commits.contains(token)) b = maxOf(b, wCommit)
        if (editor.contains(token)) b = maxOf(b, wEditor)
        if (screen.contains(token)) b = maxOf(b, wScreen)
        return b
    }

    /**
     * 候補ごとの加点（[scores] と同じ順・長さ）。[tokens] は各候補の語分割（空なら加点なし）。
     * 加点するのは、スコア上位 [TOP_N] 件かつ 1 位との差 [MAX_GAP] 以内の候補で、全候補に共通な語は数えない。
     */
    fun bonuses(tokens: List<List<String>>, scores: DoubleArray): DoubleArray {
        val n = scores.size
        val out = DoubleArray(n)
        if (n < 2) return out
        val order = (0 until n).sortedByDescending { scores[it] }
        val best = scores[order[0]]
        val eligibleIdx = order.take(TOP_N).filter { best - scores[it] <= MAX_GAP }
        if (eligibleIdx.size < 2) return out
        val common = eligibleIdx.map { tokens[it].toSet() }.reduce { a, b -> a intersect b }
        for (i in eligibleIdx) {
            var s = 0.0
            for (t in tokens[i].toSet()) if (t !in common) s += tokenBonus(t)
            out[i] = minOf(s, CAP)
        }
        return out
    }
}

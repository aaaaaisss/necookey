package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import com.kazumaproject.markdownhelperkeyboard.converter.graph.ContextLexicon

/**
 * 画面文脈（ユーザー承認 2026-11-03）。すべてメモリだけで保存しない・送らない（アプリにネットワーク権限なし）。
 *
 * 3 つの層:
 *  - [Source.EDITOR] 入力欄のカーソル前後（各 [EDITOR_CHARS] 字、変換中の文字列は除く）。最新 1 件だけ。
 *  - [Source.COMMIT] 最近確定した表記（アプリ = パッケージごと、[COMMIT_TTL_MS] = 30 分）。会話の中で一度 県庁 に直したら県庁のまま。
 *  - [Source.SCREEN] ユーザー補助サービス（アプリ設定は既定オン、端末のユーザー補助で有効にしたときだけ）が拾った、いま入力しているアプリの画面の語（漢字・カタカナ 2 字以上だけ、
 *    元の文字列は残さない。キーボードが出ている間だけ。隠れたら捨てる。[SCREEN_TTL_MS] = 3 分）。
 *
 * 使い方（2026-11-15〜）: [lexicon] の語の集合（ContextLexicon）をラティスに入れ、表記が文脈にあるノードを安くする（A1）・
 * 文脈のカタカナ語を一時辞書としてノードと前方一致の予測に入れる（A2）。以下の [ScreenContextBonus]（リランカーへの加点）は
 * 二重計上になるので端末では使っていない（テスト・比較用に残す）。旧: 候補の語（Sumire の語分割の出力）が画面文脈の文字列に含まれていれば、リランカーの
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
        val text = before.takeLast(EDITOR_CHARS) + "\n" + after.take(EDITOR_CHARS)
        // 変換のたびに呼ばれる。中身が同じなら語の集合（[lexicon]）は作り直さない（時刻だけ更新）。
        if (editorPkg != pkg || editor?.text != text) revision++
        editorPkg = pkg
        editor = Entry(text, now)
    }

    fun recordCommit(pkg: String, text: String, now: Long) {
        if (text.isBlank() || !text.any(::isKanjiOrKatakana)) return
        synchronized(lock) {
            val q = commits.remove(pkg) ?: ArrayDeque()
            commits[pkg] = q
            q.addLast(Entry(text, now))
            revision++
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
            val joined = words.joinToString("\n")
            val prev = screens.remove(pkg)
            if (prev?.text != joined.takeIf { words.isNotEmpty() }) revision++
            if (words.isNotEmpty()) screens[pkg] = Entry(joined, now)
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

    /** ユーザー補助サービスへの「今すぐ読んで」（接続中だけ入る）。 */
    @Volatile var captureRequester: (() -> Unit)? = null

    /** 画面の語がこれより古ければ変換のときに読み直しを頼む。 */
    const val SCREEN_REFRESH_MS = 4000L

    fun onKeyboardShown(pkg: String) {
        inputPackage = pkg
        keyboardVisible = true
        // 出た瞬間に 1 回読む（対象アプリのイベントを待つと、変化のない画面では語が空のままだった）
        captureRequester?.invoke()
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
        val sc = screens.values.filter { now - it.time <= SCREEN_TTL_MS }.sumOf { e -> e.text.split('\n').count { it.isNotEmpty() && !it.endsWith(STEM_MARK) } }
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
        // 活用する語の漢字の部分（溜める → 溜、聞いてる → 聞）。語だけで、送り仮名は残さない（2026-11-07: 画面に 溜める が
        // あっても ためる/たまんない が 溜 にならなかった。漢字 2 字以上の並びしか残しておらず、動詞・形容詞が一つも効いていなかった）。
        for (k in stemsOf(text)) out.add(k + STEM_MARK)
        return out
    }

    /** 語の漢字の部分の印（画面の語の中で、漢字のあとにひらがなが続いた並び）。 */
    const val STEM_MARK = '〜'

    /** [text] の中で、漢字の並び（1 字以上）のすぐあとにひらがなが続くものの漢字の部分。 */
    fun stemsOf(text: String): Set<String> {
        val out = LinkedHashSet<String>()
        var i = 0
        while (i < text.length) {
            if (!isKanji(text[i])) { i++; continue }
            var j = i + 1
            while (j < text.length && isKanji(text[j])) j++
            if (j < text.length && isHiragana(text[j])) out.add(text.substring(i, j))
            i = j
        }
        return out
    }

    /** 語 [token] が「漢字 + ひらがな」（溜める・聞いてる・寝れた）ならその漢字の部分。違えば null。 */
    fun stemOfToken(token: String): String? {
        if (token.isEmpty() || !isKanji(token[0])) return null
        var j = 1
        while (j < token.length && isKanji(token[j])) j++
        if (j >= token.length) return null
        for (k in j until token.length) if (!isHiragana(token[k])) return null
        return token.substring(0, j)
    }

    internal fun isKanji(c: Char): Boolean = c in '\u4E00'..'\u9FFF' || c == '々'
    internal fun isHiragana(c: Char): Boolean = c in 'ぁ'..'ゖ'

    /** 端末を閉じた・設定を切ったときなど。 */
    fun clear() = synchronized(lock) {
        editor = null; editorPkg = ""; commits.clear(); screens.clear()
        recent.clear(); recentApplied = 0
        revision++; lexiconCache = null
    }

    fun clearScreen() = synchronized(lock) {
        if (screens.isNotEmpty()) revision++
        screens.clear()
    }

    // ---- ラティスの文脈ノード加点・画面のカタカナ語（ContextLexicon、2026-11-15） ----

    /** 文脈の中身が変わるたびに増える（語の集合の作り直しの合図）。 */
    private var revision = 0L
    private var lexiconCache: ContextLexicon? = null
    private var lexiconPkg: String = ""

    /**
     * [pkg] のいまの文脈の語の集合。文脈が変わったとき・どれかの層の期限が来たときだけ作り直す（変換ごとには作らない）。
     * 何もなければ null。画面の語が古ければユーザー補助に読み直しを頼む（[snapshot] と同じ）。
     */
    fun lexicon(pkg: String, now: Long): ContextLexicon? = synchronized(lock) {
        val scEntry = screens[pkg]
        if (keyboardVisible && (scEntry == null || now - scEntry.time > SCREEN_REFRESH_MS)) captureRequester?.invoke()
        val cached = lexiconCache
        if (cached != null && lexiconPkg == pkg && cached.revision == revision && now < cached.expiresAt) {
            return@synchronized cached.takeIf { !it.isEmpty }
        }
        var expires = Long.MAX_VALUE
        val texts = HashMap<Int, String>()
        editor?.takeIf { editorPkg == pkg && now - it.time <= EDITOR_TTL_MS }?.let {
            texts[ContextLexicon.LAYER_EDITOR] = it.text
            expires = minOf(expires, it.time + EDITOR_TTL_MS)
        }
        commits[pkg]?.filter { now - it.time <= COMMIT_TTL_MS }?.takeIf { it.isNotEmpty() }?.let { list ->
            texts[ContextLexicon.LAYER_COMMIT] = list.joinToString("\n") { it.text }
            expires = minOf(expires, list.minOf { it.time } + COMMIT_TTL_MS)
        }
        val other = commits.entries.filter { it.key != pkg }.flatMap { e -> e.value.filter { now - it.time <= COMMIT_TTL_MS } }
        if (other.isNotEmpty()) {
            texts[ContextLexicon.LAYER_OTHER_COMMIT] = other.joinToString("\n") { it.text }
            expires = minOf(expires, other.minOf { it.time } + COMMIT_TTL_MS)
        }
        var screenStems: List<String> = emptyList()
        scEntry?.takeIf { now - it.time <= SCREEN_TTL_MS }?.let { e ->
            val lines = e.text.split('\n')
            texts[ContextLexicon.LAYER_SCREEN] = lines.filter { !it.endsWith(STEM_MARK) }.joinToString("\n")
            screenStems = lines.filter { it.endsWith(STEM_MARK) }.map { it.dropLast(1) }
            expires = minOf(expires, e.time + SCREEN_TTL_MS)
        }
        val built = ContextLexicon.build(revision, expires, texts, screenStems)
        lexiconCache = built
        lexiconPkg = pkg
        built.takeIf { !it.isEmpty }
    }

    /** 読み [input] の直前の変換で、ラティスの文脈ノード加点が付いたか（GraphBuilder が呼ぶ）。 */
    fun noteLatticeContext(input: String, applied: Boolean) {
        noteConversion(input, applied, applied)
    }

    /**
     * 確定した表記 [chosen]（最初の表記 [shown]）の、直した部分の語（漢字・カタカナの連続・活用語の漢字部）が各層の文脈の語の集合に
     * あったか。EDITOR / COMMIT（他アプリの確定を含む）/ SCREEN の順の "0"/"1" 3 字（変換ミスの記録の ctxl 列、文字列は残さない）。
     */
    fun contextLayersFor(pkg: String, shown: String, chosen: String, now: Long): String {
        val lex = lexicon(pkg, now) ?: return "000"
        val shownRuns = ContextLexicon.runsOf(shown).toSet()
        val shownStems = ContextLexicon.stemRunsOf(shown)
        val m = lex.layersOf(
            ContextLexicon.runsOf(chosen).filter { it !in shownRuns },
            ContextLexicon.stemRunsOf(chosen).filter { it !in shownStems },
        )
        fun bit(b: Int) = if (m and b != 0) '1' else '0'
        return "${bit(ContextLexicon.LAYER_EDITOR)}${bit(ContextLexicon.LAYER_COMMIT or ContextLexicon.LAYER_OTHER_COMMIT)}${bit(ContextLexicon.LAYER_SCREEN)}"
    }

    /** [pkg] のいまの画面文脈。何もなければ null。期限切れはここで捨てる。 */
    fun snapshot(pkg: String, now: Long): ScreenContextBonus? = synchronized(lock) {
        val ed = editor?.takeIf { editorPkg == pkg && now - it.time <= EDITOR_TTL_MS }?.text.orEmpty()
        val q = commits[pkg]
        q?.removeAll { now - it.time > COMMIT_TTL_MS }
        val cm = q?.joinToString("\n") { it.text }.orEmpty()
        val iter = screens.entries.iterator()
        while (iter.hasNext()) if (now - iter.next().value.time > SCREEN_TTL_MS) iter.remove()
        val scEntry = screens[pkg]
        if (keyboardVisible && (scEntry == null || now - scEntry.time > SCREEN_REFRESH_MS)) captureRequester?.invoke()
        val sc = scEntry?.text.orEmpty()
        // ほかのアプリで最近確定した語（同じ話題をアプリをまたいで打つことが多い）。重みは画面と同じ控えめ。
        val other = commits.entries.filter { it.key != pkg }.flatMap { e -> e.value.filter { now - it.time <= COMMIT_TTL_MS }.map { it.text } }
            .joinToString("\n")
        if (ed.isEmpty() && cm.isEmpty() && sc.isEmpty() && other.isEmpty()) null
        else ScreenContextBonus(editor = ed, commits = cm, screen = sc, otherCommits = other)
    }

    internal fun isKanjiOrKatakana(c: Char): Boolean =
        c in '\u4E00'..'\u9FFF' || c == '々' || c in 'ァ'..'ヺ'
}

/** 画面文脈の文字列（[ScreenContextStore.snapshot]）に基づくリランカースコアへの加点。 */
class ScreenContextBonus(
    private val editor: String,
    private val commits: String,
    private val screen: String,
    private val otherCommits: String = "",
    private val wEditor: Double = W_EDITOR,
    private val wCommit: Double = W_COMMIT,
    private val wScreen: Double = W_SCREEN,
) {
    companion object {
        /** 1 語あたりの加点（リランカーの線形スコアの単位。旧 zenz ゲートのマージン既定 0.5 と同じ尺度）。 */
        const val W_EDITOR = 0.4
        const val W_COMMIT = 0.6
        const val W_SCREEN = 0.3
        const val W_OTHER_COMMIT = 0.3
        const val CAP = 0.8
        // 2026-11-07: 上位 5・差 1.5 では正解が届かず、端末で 1 位を変えたのは 100 回に 1 回未満だった。少し広げる。
        const val TOP_N = 10
        const val MAX_GAP = 2.5
    }

    private fun eligible(token: String): Boolean =
        token.length >= 2 && token.any(ScreenContextStore::isKanjiOrKatakana)

    // 活用する語の漢字の部分（[ScreenContextStore.stemsOf]）。画面の層は語の一覧に印つきで入っている。
    private val editorStems by lazy { ScreenContextStore.stemsOf(editor) }
    private val commitStems by lazy { ScreenContextStore.stemsOf(commits) }
    private val otherStems by lazy { ScreenContextStore.stemsOf(otherCommits) }
    private val screenStems by lazy {
        screen.split('\n').filter { it.endsWith(ScreenContextStore.STEM_MARK) }.map { it.dropLast(1) }.toSet()
    }

    /** 語 [token] の加点（どの層に出てくるかの最大）。活用違い（溜める と 溜まん）は漢字の部分が同じなら同じ語として数える。 */
    fun tokenBonus(token: String): Double {
        val stem = ScreenContextStore.stemOfToken(token)
        if (!eligible(token) && stem == null) return 0.0
        fun has(text: String, stems: () -> Set<String>): Boolean =
            (eligible(token) && text.contains(token)) || (stem != null && stem in stems())
        var b = 0.0
        if (has(commits) { commitStems }) b = maxOf(b, wCommit)
        if (has(editor) { editorStems }) b = maxOf(b, wEditor)
        if (has(screen) { screenStems }) b = maxOf(b, wScreen)
        if (b == 0.0 && has(otherCommits) { otherStems }) b = W_OTHER_COMMIT
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

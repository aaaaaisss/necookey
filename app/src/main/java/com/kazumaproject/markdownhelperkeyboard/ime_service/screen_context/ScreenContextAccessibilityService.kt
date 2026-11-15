package com.kazumaproject.markdownhelperkeyboard.ime_service.screen_context

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ScreenContextStore

/**
 * 画面の文脈（設定「画面の文脈」は既定オン（2026-11-15）。ただしユーザー補助はユーザーが端末の設定で有効にしたときだけ動く）。読む範囲は最小限（2026-11-05 絞り込み）:
 *  - necookey のキーボードが出ている間だけ（[ScreenContextStore.keyboardVisible]）。それ以外のイベントは無視し、隠れたら語を捨てる。
 *  - いま入力しているアプリ（EditorInfo.packageName）のウィンドウだけ。通知・システム UI・ランチャー・ほかのアプリのウィンドウは読まない。
 *  - パスワード管理・銀行・決済・認証アプリ（[isExcludedPackage]）は読まない。パスワード欄・入力欄・このアプリ自身の画面も読まない。
 *  - 残すのは漢字・カタカナ 2 字以上の語だけ（[ScreenContextStore.extractWords]）。元の文字列はすぐ捨てる。メモリだけ（3 分）、保存・送信しない。
 *  - 取り出した語は変換のラティス（文脈ノードの加点）とカタカナ語の一時辞書に使う（ContextLexicon、期限は同じ 3 分）。
 * 読み取りは画面の変化から [THROTTLE_MS] 待ってまとめて 1 回。
 */
class ScreenContextAccessibilityService : AccessibilityService() {
    companion object {
        const val PREF_KEY = "screen_context_accessibility_preference"
        private const val THROTTLE_MS = 500L
        private const val MAX_NODES = 600
        private const val MAX_DEPTH = 40
        private const val MAX_CHARS = ScreenContextStore.MAX_SCREEN_CHARS

        /** 読まないアプリ（パッケージ名に含まれる語）: パスワード管理・銀行・決済・認証。 */
        private val EXCLUDED_KEYWORDS = listOf(
            "bank", "pay", "wallet", "1password", "bitwarden", "keepass", "lastpass", "dashlane", "enpass",
            "authenticator", "password", "credit", "securities", "finance", "crypto",
        )

        /** 接続中のサービス（キーボードが出た瞬間に読み取りを頼むため）。 */
        @Volatile private var instance: java.lang.ref.WeakReference<ScreenContextAccessibilityService>? = null

        /** すぐ 1 回読む（キーボードが出た・画面の語が古い）。サービスが無ければ何もしない。 */
        fun requestCapture() {
            instance?.get()?.scheduleNow()
        }

        fun isExcludedPackage(pkg: String): Boolean {
            val p = pkg.lowercase()
            return EXCLUDED_KEYWORDS.any { p.contains(it) }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var scheduled = false
    private var lastRun = 0L
    private val runnable = Runnable {
        scheduled = false
        lastRun = SystemClock.uptimeMillis()
        captureSafely()
    }

    private fun enabled(): Boolean =
        PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_KEY, true)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = java.lang.ref.WeakReference(this)
        ScreenContextStore.serviceConnected = true
        ScreenContextStore.captureRequester = { requestCapture() }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ScreenContextStore.serviceConnected = false
        ScreenContextStore.captureRequester = null
        instance = null
        ScreenContextStore.clearScreen()
        return super.onUnbind(intent)
    }

    /** イベントを待たずに読む（キーボードが出ても対象アプリのイベントが来ないと語が空のままだった）。 */
    private fun scheduleNow() {
        handler.post {
            handler.removeCallbacks(runnable)
            scheduled = false
            lastRun = SystemClock.uptimeMillis()
            captureSafely()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || scheduled) return
        if (!ScreenContextStore.keyboardVisible) return
        val target = ScreenContextStore.inputPackage
        if (target.isEmpty() || event.packageName?.toString() != target) return
        val wait = (lastRun + THROTTLE_MS - SystemClock.uptimeMillis()).coerceIn(0L, THROTTLE_MS)
        scheduled = true
        handler.postDelayed(runnable, wait)
    }

    private fun captureSafely() {
        try {
            capture()
        } catch (e: Exception) {
            ScreenContextStore.noteCaptureSkipped(ScreenContextStore.inputPackage, "error:${e.javaClass.simpleName}", System.currentTimeMillis())
            // ウィンドウが消えた等。次の変化で読み直す。
        }
    }

    private fun capture() {
        if (!enabled()) {
            ScreenContextStore.clearScreen()
            return
        }
        val now = System.currentTimeMillis()
        if (!ScreenContextStore.keyboardVisible) return ScreenContextStore.noteCaptureSkipped("", "hidden", now)
        val target = ScreenContextStore.inputPackage
        if (target.isEmpty()) return ScreenContextStore.noteCaptureSkipped("", "no-target", now)
        if (target == packageName || isExcludedPackage(target)) return ScreenContextStore.noteCaptureSkipped(target, "excluded", now)
        // いま入力しているアプリのウィンドウだけ。キーボードが出ていると前面（active）がずれることがあるので、
        // まず対話ウィンドウ一覧からそのアプリの通常ウィンドウを探し、無ければ前面のウィンドウ。
        val root = targetRoot(target) ?: return ScreenContextStore.noteCaptureSkipped(target, "no-window", now)
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg != target) return ScreenContextStore.noteCaptureSkipped(target, "other-window:$pkg", now)
        val texts = ArrayList<String>()
        var chars = 0
        var nodes = 0
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            nodes++
            if (nodes > MAX_NODES || chars >= MAX_CHARS) continue
            if (node.isPassword || node.isEditable) continue
            if (!node.isVisibleToUser) continue
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
                texts.add(it)
                chars += it.length
            }
            // Flutter・React Native・一部の WebView は表示の文字を text ではなく contentDescription に出す
            // （2026-11-15: チャットアプリの画面に 中野 が見えていても画面の語が 0 だった）
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() && it != node.text?.toString() }?.let {
                texts.add(it)
                chars += it.length
            }
            if (depth < MAX_DEPTH) {
                for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { stack.addLast(it to depth + 1) }
            }
        }
        // 語だけを残し、元の文字列はここで捨てる（recordScreen が語を取り出す）
        ScreenContextStore.recordScreen(pkg, texts, System.currentTimeMillis())
        texts.clear()
    }

    private fun targetRoot(target: String): AccessibilityNodeInfo? {
        try {
            for (w in windows) {
                if (w.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val r = w.root ?: continue
                if (r.packageName?.toString() == target) return r
            }
        } catch (_: Exception) {
        }
        return rootInActiveWindow
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        instance = null
        ScreenContextStore.captureRequester = null
        ScreenContextStore.serviceConnected = false
        ScreenContextStore.clearScreen()
        super.onDestroy()
    }
}

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
 * 画面の文脈（任意・既定オフ、設定「画面の文脈」とユーザー補助の両方でオン）。読む範囲は最小限（2026-11-05 絞り込み）:
 *  - necookey のキーボードが出ている間だけ（[ScreenContextStore.keyboardVisible]）。それ以外のイベントは無視し、隠れたら語を捨てる。
 *  - いま入力しているアプリ（EditorInfo.packageName）のウィンドウだけ。通知・システム UI・ランチャー・ほかのアプリのウィンドウは読まない。
 *  - パスワード管理・銀行・決済・認証アプリ（[isExcludedPackage]）は読まない。パスワード欄・入力欄・このアプリ自身の画面も読まない。
 *  - 残すのは漢字・カタカナ 2 字以上の語だけ（[ScreenContextStore.extractWords]）。元の文字列はすぐ捨てる。メモリだけ（3 分）、保存・送信しない。
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
        PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_KEY, false)

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
        } catch (_: Exception) {
            // ウィンドウが消えた等。次の変化で読み直す。
        }
    }

    private fun capture() {
        if (!enabled()) {
            ScreenContextStore.clearScreen()
            return
        }
        if (!ScreenContextStore.keyboardVisible) return
        val target = ScreenContextStore.inputPackage
        if (target.isEmpty() || target == packageName || isExcludedPackage(target)) return
        // いま入力しているアプリのウィンドウだけ。キーボードが出ていると前面（active）がずれることがあるので、
        // まず対話ウィンドウ一覧からそのアプリの通常ウィンドウを探し、無ければ前面のウィンドウ。
        val root = targetRoot(target) ?: return
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg != target) return
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

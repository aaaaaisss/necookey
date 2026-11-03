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
 * 画面の文脈（任意・既定オフ、設定「画面の文脈」とユーザー補助の両方でオン）。
 * 前面アプリの見えている文字列を読み、[ScreenContextStore] にメモリだけで数分置く（IME と同じプロセスの共有オブジェクト）。
 * 保存・送信はしない。パスワード欄・入力欄（変換中の文字が入る）・このアプリ自身の画面は読まない。
 * 読み取りは画面の変化から [THROTTLE_MS] 待ってまとめて 1 回。
 */
class ScreenContextAccessibilityService : AccessibilityService() {
    companion object {
        const val PREF_KEY = "screen_context_accessibility_preference"
        private const val THROTTLE_MS = 500L
        private const val MAX_NODES = 600
        private const val MAX_DEPTH = 40
        private const val MAX_CHARS = ScreenContextStore.MAX_SCREEN_CHARS
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || scheduled) return
        if (event.packageName?.toString() == packageName) return
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
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg.isEmpty() || pkg == packageName) return
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
        ScreenContextStore.recordScreen(pkg, texts, System.currentTimeMillis())
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        ScreenContextStore.clearScreen()
        super.onDestroy()
    }
}

package com.kazumaproject.markdownhelperkeyboard.diagnostics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * メインスレッドの固まり（フリーズ）を記録する軽い見張り（ANR-watchdog 方式）。
 *
 * 見張りスレッドが 2 秒ごとにメインの Looper へ小さな tick を投げ、2 秒以内に処理されなければ
 * メインスレッドのスタック・全スレッドの状態・時刻・そのとき生きていた画面（IME／設定画面）を
 * アプリの files/freeze_log.txt（上限 [MAX_LOG_BYTES]、古い記録から捨てる）に追記する。
 * 解除されたら、どれだけ止まっていたかを 1 行足す。
 *
 * - 見張るのは「IME のキーボードが表示中」「アクティビティが前面」「IME の起動直後 10 秒」のときだけ。
 *   それ以外は見張りスレッドが wait で眠るので、IME が常駐していても電池を使わない。
 * - メインスレッドでは tick（数値の代入）と状態フラグの更新しかしない。ファイルの読み書きは別スレッド。
 * - debug / release とも有効。デバッガ接続中は記録しない。
 *
 * 設定の「その他 → フリーズ記録」で表示・コピー・消去できる（MainPreferenceFragment）。
 */
object FreezeWatchdog {
    const val LOG_FILE_NAME = "freeze_log.txt"
    private const val FREEZE_THRESHOLD_MS = 2_000L
    private const val RECOVERY_POLL_MS = 500L
    private const val RECOVERY_GIVE_UP_MS = 120_000L
    private const val IME_STARTUP_WATCH_MS = 10_000L
    internal const val MAX_LOG_BYTES = 200 * 1024
    internal const val KEEP_ENTRIES_ON_TRIM = 30
    internal const val ENTRY_MARKER = "===== "
    private const val MAIN_STACK_MAX_FRAMES = 60
    private const val OTHER_THREAD_MAX = 60
    private const val OTHER_THREAD_MAX_FRAMES = 6
    private const val CRASH_TRACE_MAX_CHARS = 12_000

    /**
     * 全スレッドのスタック（Thread.getAllStackTraces）を取る最短の間隔。ART では全スレッドを一時停止するので、
     * 固まっている最中に何度も取ると固まりを長引かせる（2026-11-08: 設定画面の 6〜9 秒の固まりで、他スレッドが
     * メインの Looper へ投げる処理と重なっていた）。1 回の固まりにつき 1 回まで、続く固まりでは間隔を空ける。
     */
    private const val ALL_STACKS_MIN_INTERVAL_MS = 60_000L

    private val lock = Object()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FreezeLogWriter").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    @Volatile private var logFile: File? = null
    @Volatile private var started = false

    // 生きている画面（メインスレッドから更新、見張りスレッドから読む）
    @Volatile private var imeAlive = false
    @Volatile private var imeInputViewShown = false
    @Volatile private var imeStartupWatchUntil = 0L
    @Volatile private var resumedActivity: String? = null

    /** 最後に全スレッドのスタックを取った時刻（見張りスレッドだけが読み書き）。 */
    private var lastAllStacksAt = -ALL_STACKS_MIN_INTERVAL_MS

    @Volatile private var handledSeq = 0L
    @Volatile private var handledAtUptime = 0L

    /** Application.onCreate から呼ぶ。何度呼んでもよい。例外は外へ出さない。 */
    fun start(application: Application) {
        runCatching {
            if (started) return
            started = true
            logFile = File(application.filesDir, LOG_FILE_NAME)
            application.registerActivityLifecycleCallbacks(ActivityTracker)
            installCrashRecorder()
            Thread(::watchLoop, "FreezeWatchdog").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }.start()
        }
    }

    fun onImeCreated() = update {
        imeAlive = true
        imeStartupWatchUntil = SystemClock.uptimeMillis() + IME_STARTUP_WATCH_MS
    }

    fun onImeDestroyed() = update {
        imeAlive = false
        imeInputViewShown = false
    }

    fun onImeInputViewShown(shown: Boolean) = update { imeInputViewShown = shown }

    /**
     * メインスレッドが待たされたことを記録する（例: AppPreference の初期化待ち）。呼び出し側は時間を測るだけで、
     * 書き込みは別スレッド。
     */
    fun note(message: String) {
        if (!started) return
        val text = buildString {
            append(ENTRY_MARKER).append(timestamp()).append(" メモ: ").append(message).append('\n')
            append("生きていた画面: ").append(componentsSummary()).append('\n')
        }
        appendAsync(text)
    }

    fun logFile(context: Context): File = File(context.filesDir, LOG_FILE_NAME)

    private fun update(block: () -> Unit) {
        runCatching {
            block()
            synchronized(lock) { lock.notifyAll() }
        }
    }

    private fun shouldWatch(now: Long): Boolean =
        imeInputViewShown || resumedActivity != null || (imeAlive && now < imeStartupWatchUntil)

    private fun watchLoop() {
        var seq = 0L
        while (true) {
            try {
                synchronized(lock) {
                    while (true) {
                        val now = SystemClock.uptimeMillis()
                        if (shouldWatch(now)) break
                        lock.wait()
                    }
                }
                val mySeq = ++seq
                val postedAt = SystemClock.uptimeMillis()
                mainHandler.post {
                    handledAtUptime = SystemClock.uptimeMillis()
                    handledSeq = mySeq
                }
                Thread.sleep(FREEZE_THRESHOLD_MS)
                if (handledSeq >= mySeq || Debug.isDebuggerConnected()) continue

                // 2 秒以上処理されていない = メインスレッドが固まっている
                appendAsync(buildFreezeEntry(SystemClock.uptimeMillis() - postedAt))
                var waited = 0L
                while (handledSeq < mySeq && waited < RECOVERY_GIVE_UP_MS) {
                    Thread.sleep(RECOVERY_POLL_MS)
                    waited += RECOVERY_POLL_MS
                }
                appendAsync(
                    if (handledSeq >= mySeq) {
                        "→ 解除: 約 ${handledAtUptime - postedAt} ms 止まっていた\n"
                    } else {
                        "→ ${RECOVERY_GIVE_UP_MS / 1000} 秒以上解除されず（記録をやめて見張りを続ける）\n"
                    },
                )
            } catch (_: InterruptedException) {
                return
            } catch (_: Throwable) {
                // 見張りが原因で落ちないようにする
                runCatching { Thread.sleep(FREEZE_THRESHOLD_MS) }
            }
        }
    }

    private fun buildFreezeEntry(blockedMs: Long): String = buildString {
        append(ENTRY_MARKER).append(timestamp())
            .append(" フリーズ（メインスレッドが ").append(blockedMs).append(" ms 以上応答なし）\n")
        append("アプリ: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE)
            .append(") ").append(BuildConfig.BUILD_TYPE).append('\n')
        append("生きていた画面: ").append(componentsSummary()).append('\n')
        val main = Looper.getMainLooper().thread
        append("メインスレッド: ").append(main.state).append('\n')
        val frames = main.stackTrace
        frames.take(MAIN_STACK_MAX_FRAMES).forEach { append("  at ").append(it).append('\n') }
        if (frames.size > MAIN_STACK_MAX_FRAMES) {
            append("  …（あと ").append(frames.size - MAIN_STACK_MAX_FRAMES).append(" 行）\n")
        }
        // 全スレッドのスタックは見張りスレッドで、間隔を空けて 1 回だけ取る（メインスレッドでは取らない）
        val now = SystemClock.uptimeMillis()
        if (Looper.myLooper() == Looper.getMainLooper() || now - lastAllStacksAt < ALL_STACKS_MIN_INTERVAL_MS) {
            append("他のスレッド: （直近に記録済みのため省略）\n")
            return@buildString
        }
        lastAllStacksAt = now
        append("他のスレッド:\n")
        val all = runCatching { Thread.getAllStackTraces() }.getOrNull().orEmpty()
        all.entries
            .filter { it.key !== main }
            .sortedWith(compareBy({ stateOrder(it.key.state) }, { it.key.name }))
            .take(OTHER_THREAD_MAX)
            .forEach { (thread, stack) ->
                append("  [").append(thread.state).append("] ").append(thread.name).append('\n')
                if (thread.state == Thread.State.RUNNABLE || thread.state == Thread.State.BLOCKED) {
                    stack.take(OTHER_THREAD_MAX_FRAMES).forEach { append("      at ").append(it).append('\n') }
                }
            }
        if (all.size - 1 > OTHER_THREAD_MAX) {
            append("  …（あと ").append(all.size - 1 - OTHER_THREAD_MAX).append(" スレッド）\n")
        }
    }

    private fun stateOrder(state: Thread.State): Int = when (state) {
        Thread.State.BLOCKED -> 0
        Thread.State.RUNNABLE -> 1
        Thread.State.WAITING -> 2
        Thread.State.TIMED_WAITING -> 3
        else -> 4
    }

    private fun componentsSummary(): String {
        val parts = mutableListOf<String>()
        if (imeAlive) parts += if (imeInputViewShown) "IME（キーボード表示中）" else "IME（常駐・非表示）"
        resumedActivity?.let { parts += "アクティビティ $it（前面）" }
        return if (parts.isEmpty()) "なし" else parts.joinToString(", ")
    }

    /**
     * 落ちたときの例外（スレッド名・例外・cause の連鎖・スタック）を同じ記録ファイルへ同期で書いてから、
     * 元のハンドラ（システムのクラッシュ処理）へ渡す。落ちる直前なので別スレッドには頼らない。
     */
    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val file = logFile
                if (file != null) {
                    val text = buildString {
                        append(ENTRY_MARKER).append(timestamp()).append(" クラッシュ（スレッド: ")
                            .append(thread.name).append("）\n")
                        append("アプリ: ").append(BuildConfig.VERSION_NAME).append(" (")
                            .append(BuildConfig.VERSION_CODE).append(")\n")
                        append("生きていた画面: ").append(componentsSummary()).append('\n')
                        append(android.util.Log.getStackTraceString(error).take(CRASH_TRACE_MAX_CHARS))
                        append('\n')
                    }
                    FreezeLogFile.append(file, text)
                }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.JAPAN).format(Date())

    private fun appendAsync(text: String) {
        val file = logFile ?: return
        runCatching { writer.execute { runCatching { FreezeLogFile.append(file, text) } } }
    }

    private object ActivityTracker : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) =
            update { resumedActivity = activity.javaClass.simpleName }

        override fun onActivityPaused(activity: Activity) = update {
            if (resumedActivity == activity.javaClass.simpleName) resumedActivity = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}

/** フリーズ記録ファイルの読み書き（メインスレッド以外から呼ぶ）。 */
object FreezeLogFile {
    @Synchronized
    fun append(file: File, text: String) {
        file.appendText(text, Charsets.UTF_8)
        if (file.length() > FreezeWatchdog.MAX_LOG_BYTES) {
            file.writeText(trim(file.readText(Charsets.UTF_8)), Charsets.UTF_8)
        }
    }

    /** 古い記録から捨てて、最後の [FreezeWatchdog.KEEP_ENTRIES_ON_TRIM] 件・上限の半分以下にする。 */
    internal fun trim(content: String): String {
        val marker = "\n" + FreezeWatchdog.ENTRY_MARKER
        val starts = mutableListOf<Int>()
        if (content.startsWith(FreezeWatchdog.ENTRY_MARKER)) starts += 0
        var index = content.indexOf(marker)
        while (index >= 0) {
            starts += index + 1
            index = content.indexOf(marker, index + 1)
        }
        if (starts.isEmpty()) return content.takeLast(FreezeWatchdog.MAX_LOG_BYTES / 2)
        var from = starts.size - FreezeWatchdog.KEEP_ENTRIES_ON_TRIM
        if (from < 0) from = 0
        var kept = content.substring(starts[from])
        while (kept.toByteArray(Charsets.UTF_8).size > FreezeWatchdog.MAX_LOG_BYTES / 2 && from < starts.size - 1) {
            from++
            kept = content.substring(starts[from])
        }
        return kept
    }

    @Synchronized
    fun read(file: File): String = if (file.exists()) file.readText(Charsets.UTF_8) else ""

    @Synchronized
    fun clear(file: File) {
        if (file.exists()) file.delete()
    }
}

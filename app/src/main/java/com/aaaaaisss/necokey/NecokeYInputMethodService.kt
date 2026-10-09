package com.aaaaaisss.necokey

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Keep IME startup independent from the converter and optional native model.
 * The first priority is that Android can bind and display a stable input view.
 */
class NecokeYInputMethodService : InputMethodService() {
    private var composing = ""
    private var composingView: TextView? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "IME service created")
    }

    override fun onCreateInputView(): View {
        Log.i(TAG, "Creating input view")
        return try {
            createKeyboardView()
        } catch (error: Throwable) {
            Log.e(TAG, "Input view creation failed; returning emergency view", error)
            createEmergencyView()
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composing = ""
        updateComposingView()
        Log.i(TAG, "Input started; restarting=$restarting inputType=${attribute?.inputType}")
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.i(TAG, "Input view shown; restarting=$restarting")
    }

    override fun onDestroy() {
        Log.i(TAG, "IME service destroyed")
        composingView = null
        super.onDestroy()
    }

    private fun createKeyboardView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        composingView = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            minHeight = dp(36)
        }
        root.addView(composingView, LinearLayout.LayoutParams(-1, dp(40)))

        val rows = listOf(
            listOf("あ", "か", "さ", "た", "な"),
            listOf("は", "ま", "や", "ら", "わ"),
            listOf("、", "。", "？", "！", "ー"),
            listOf("小", "゛", "゜", "空白", "削除"),
            listOf("変換", "確定", "改行")
        )
        rows.forEach { labels ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            labels.forEach { label ->
                val button = Button(this).apply {
                    text = label
                    isAllCaps = false
                    textSize = 15f
                    setOnClickListener { handleKey(label) }
                }
                row.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            root.addView(row, LinearLayout.LayoutParams(-1, dp(48)))
        }
        Log.i(TAG, "Stable native keyboard view created")
        return root
    }

    private fun createEmergencyView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.BLACK)
        addView(TextView(this@NecokeYInputMethodService).apply {
            text = "necookey 起動復旧モード"
            setTextColor(Color.WHITE)
        })
        addView(Button(this@NecokeYInputMethodService).apply {
            text = "あ"
            setOnClickListener { currentInputConnection?.commitText("あ", 1) }
        })
    }

    private fun handleKey(label: String) {
        when (label) {
            "削除" -> {
                if (composing.isNotEmpty()) {
                    composing = composing.dropLast(1)
                    updateComposingView()
                } else {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
            }
            "空白" -> composing += " "
            "小" -> composing += "ゃ"
            "゛" -> composing += "゛"
            "゜" -> composing += "゜"
            "変換", "確定" -> commitComposing()
            "改行" -> {
                commitComposing()
                currentInputConnection?.commitText("\n", 1)
            }
            else -> composing += label
        }
        updateComposingView()
    }

    private fun commitComposing() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.commitText(composing, 1)
            composing = ""
        }
        updateComposingView()
    }

    private fun updateComposingView() {
        composingView?.text = composing
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "necookey"
    }
}

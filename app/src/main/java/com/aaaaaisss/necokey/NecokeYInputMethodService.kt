package com.aaaaaisss.necokey

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class NecokeYInputMethodService : InputMethodService() {
    private val engine = CandidateEngine()
    private val roman = StringBuilder()
    private var composing = ""
    private lateinit var composingView: TextView
    private lateinit var candidatesView: LinearLayout

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(35, 35, 35))
        }
        composingView = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(20, 12, 20, 12)
            text = ""
        }
        root.addView(composingView, LinearLayout.LayoutParams(-1, 52))
        val scroll = HorizontalScrollView(this)
        candidatesView = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scroll.addView(candidatesView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 58))
        val rows = listOf(
            "あいうえおかきくけこ",
            "さしすせそたちつてと",
            "なにぬねのはひふへほ",
            "まみむめもやゆよらりる",
            "るれろわをん"
        )
        rows.forEach { chars ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chars.forEach { ch ->
                row.addView(key(ch.toString()) { commitKana(ch.toString()) },
                    LinearLayout.LayoutParams(0, 56, 1f))
            }
            root.addView(row)
        }
        val controls = LinearLayout(this)
        controls.addView(key("変換") { showCandidates() }, LinearLayout.LayoutParams(0, 56, 1f))
        controls.addView(key("空白") { commitText(" ") }, LinearLayout.LayoutParams(0, 56, 1f))
        controls.addView(key("⌫") { backspace() }, LinearLayout.LayoutParams(0, 56, 1f))
        controls.addView(key("確定") { commitComposing() }, LinearLayout.LayoutParams(0, 56, 1f))
        root.addView(controls)
        return root
    }

    private fun key(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        setOnClickListener { action() }
    }

    private fun commitKana(kana: String) {
        composing += kana
        composingView.text = composing
        updateCandidates()
    }

    private fun updateCandidates() {
        candidatesView.removeAllViews()
        engine.candidates(composing).take(6).forEach { candidate ->
            candidatesView.addView(key(candidate) {
                currentInputConnection?.commitText(candidate, 1)
                composing = ""
                composingView.text = ""
                candidatesView.removeAllViews()
            })
        }
    }

    private fun showCandidates() = updateCandidates()

    private fun commitComposing() {
        if (composing.isNotEmpty()) currentInputConnection?.commitText(composing, 1)
        composing = ""
        composingView.text = ""
        candidatesView.removeAllViews()
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun backspace() {
        if (composing.isNotEmpty()) {
            composing = composing.dropLast(1)
            composingView.text = composing
            updateCandidates()
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        roman.clear()
        composing = ""
    }
}

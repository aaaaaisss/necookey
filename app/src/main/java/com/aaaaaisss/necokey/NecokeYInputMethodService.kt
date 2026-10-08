package com.aaaaaisss.necokey

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView

class NecokeYInputMethodService : InputMethodService() {
    private val engine = CandidateEngine()
    private var composing = ""
    private lateinit var composingView: TextView
    private lateinit var candidatesView: LinearLayout
    private lateinit var keyboardView: FlickKeyboardView

    override fun onCreate() {
        super.onCreate()
        engine.initialize(this)
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(32, 32, 32))
        }

        composingView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(20, 12, 20, 12)
            text = ""
        }
        candidatesView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 4, 8, 4)
        }

        keyboardView = FlickKeyboardView(this).apply {
            setOnKeyboardActionListener(ActionListener())
            setKeyboard(KeyboardDefaultLayouts.defaultLayout())
            setBackgroundColor(Color.rgb(48, 48, 48))
        }

        root.addView(composingView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(candidatesView, LinearLayout.LayoutParams(-1, 0, 0.55f))
        root.addView(keyboardView, LinearLayout.LayoutParams(-1, 0, 3.45f))
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composing = ""
        refresh()
    }

    private fun appendText(text: String) {
        if (text.isEmpty()) return
        composing += text
        refresh()
    }

    private fun deleteLast() {
        if (composing.isNotEmpty()) {
            composing = composing.dropLast(1)
            refresh()
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun convert() {
        refresh(showCandidates = true)
    }

    private fun commitComposing() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.commitText(composing, 1)
            composing = ""
        }
        refresh()
    }

    private fun commitCandidate(text: String) {
        currentInputConnection?.commitText(text, 1)
        composing = ""
        refresh()
    }

    private fun refresh(showCandidates: Boolean = false) {
        composingView.text = composing
        candidatesView.removeAllViews()
        if (!showCandidates || composing.isEmpty()) return

        engine.candidates(composing).forEach { candidate ->
            TextView(this).apply {
                text = candidate
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(20, 8, 20, 8)
                setOnClickListener { commitCandidate(candidate) }
                candidatesView.addView(this)
            }
        }
    }

    private inner class ActionListener : FlickKeyboardView.OnKeyboardActionListener {
        override fun onPress(action: KeyAction) = Unit

        override fun onAction(action: KeyAction, isFlick: Boolean) {
            when (action) {
                is KeyAction.Text -> appendText(action.text)
                is KeyAction.InputText -> appendText(action.text)
                KeyAction.Delete, KeyAction.Backspace -> deleteLast()
                KeyAction.Convert, KeyAction.Space -> convert()
                KeyAction.Confirm, KeyAction.Enter -> commitComposing()
                KeyAction.NewLine, KeyAction.ForceNewLine -> {
                    commitComposing()
                    currentInputConnection?.commitText("\n", 1)
                }
                else -> Unit
            }
        }

        override fun onActionLongPress(action: KeyAction) = Unit
        override fun onActionUpAfterLongPress(action: KeyAction) = Unit
        override fun onFlickDirectionChanged(direction: com.kazumaproject.custom_keyboard.data.FlickDirection) = Unit
        override fun onFlickActionLongPress(action: KeyAction) = Unit
        override fun onFlickActionUpAfterLongPress(
            action: KeyAction,
            isFlick: Boolean
        ) = Unit
    }
}

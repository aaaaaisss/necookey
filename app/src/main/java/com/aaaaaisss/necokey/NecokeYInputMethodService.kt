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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class NecokeYInputMethodService : InputMethodService() {
    private val engine = CandidateEngine()
    private val zenzScorer = ZenzCandidateScorer()
    private val zenzReranker = ZenzSegmentReranker(zenzScorer)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshGeneration = 0L
    private var composing = ""
    private lateinit var composingView: TextView
    private lateinit var conversionRow: LinearLayout
    private lateinit var predictionRow: LinearLayout
    private lateinit var keyboardView: FlickKeyboardView

    override fun onCreate() {
        super.onCreate()

        // Dictionary initialization is CPU-heavy, so never block IME startup.
        serviceScope.launch {
            engine.initialize(this@NecokeYInputMethodService)
        }

        // Model loading is optional. A missing model keeps normal conversion intact.
        val model = File(filesDir, "zenz/zenz.gguf")
        if (model.isFile) {
            serviceScope.launch(Dispatchers.Default) {
                zenzScorer.loadModel(model.absolutePath)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
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
        conversionRow = createCandidateRow()
        predictionRow = createCandidateRow()

        val candidatesView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 2, 8, 2)
            addView(conversionRow, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(predictionRow, LinearLayout.LayoutParams(-1, 0, 1f))
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
        refresh(showCandidates = true)
    }

    private fun deleteLast() {
        if (composing.isNotEmpty()) {
            composing = composing.dropLast(1)
            refresh(showCandidates = true)
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
        val generation = ++refreshGeneration
        composingView.text = composing
        conversionRow.removeAllViews()
        predictionRow.removeAllViews()
        if (!showCandidates || composing.isEmpty()) return

        val input = composing
        serviceScope.launch(Dispatchers.Default) {
            val detailed = engine.detailedCandidates(input, 16)
            val prediction = engine.predictionCandidates(input, 16)
            val reranked = zenzReranker.rerank(input, detailed)
            val mainCandidate = reranked.firstOrNull() ?: detailed.firstOrNull()
            val firstSegment = mainCandidate?.conversionSegments?.firstOrNull()

            val alternatives = if (mainCandidate != null && firstSegment != null) {
                detailed.asSequence()
                    .mapNotNull { candidate ->
                        val first = candidate.conversionSegments.firstOrNull() ?: return@mapNotNull null
                        if (first.inputStart != firstSegment.inputStart ||
                            first.inputEnd != firstSegment.inputEnd ||
                            first.output == firstSegment.output) {
                            return@mapNotNull null
                        }
                        first
                    }
                    .distinctBy { it.output }
                    .take(3)
                    .toList()
            } else {
                emptyList()
            }

            val topStrings = buildSet {
                mainCandidate?.let { add(it.string) }
                alternatives.forEach { add(it.output) }
            }
            val predictionCandidates = prediction
                .filter { it.string !in topStrings }
                .take(8)

            withContext(Dispatchers.Main.immediate) {
                if (generation != refreshGeneration || input != composing) return@withContext
                conversionRow.removeAllViews()
                predictionRow.removeAllViews()
                mainCandidate?.let { candidate -> addCandidateView(conversionRow, candidate.string) { commitCandidate(candidate.string) } }
                alternatives.forEach { first ->
                    addCandidateView(conversionRow, first.output) {
                        commitSegmentAlternative(first.output, mainCandidate!!, input)
                    }
                }
                predictionCandidates.forEach { addCandidateView(predictionRow, it.string) }
            }
        }
    }

    private fun createCandidateRow(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
        }

    private fun addCandidateView(
        row: LinearLayout,
        text: String,
        onClick: (() -> Unit)? = null
    ) {
        TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(16, 6, 16, 6)
            setSingleLine(true)
            setOnClickListener { (onClick ?: { commitCandidate(text) })() }
            row.addView(this, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun commitSegmentAlternative(firstSegmentOutput: String, mainCandidate: Candidate, input: String) {
        val text = buildString {
            append(firstSegmentOutput)
            mainCandidate.conversionSegments.drop(1).forEach { append(it.output) }
        }
        commitCandidate(text)
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

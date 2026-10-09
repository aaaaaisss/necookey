package com.aaaaaisss.necokey

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.View
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
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
    private var conversionMode = false
    private var selectedBunsetsuEnd: Int? = null
    private var availableBunsetsuBoundaries: List<Int> = emptyList()
    private lateinit var composingView: TextView
    private lateinit var conversionRow: LinearLayout
    private lateinit var predictionRow: LinearLayout
    private lateinit var keyboardView: FlickKeyboardView

    override fun onCreate() {
        super.onCreate()

        // Dictionary initialization is CPU-heavy, so never block IME startup.
        serviceScope.launch {
            runCatching {
                engine.initialize(this@NecokeYInputMethodService)
            }.onFailure {
                Log.e("necookey", "Failed to initialize conversion engine", it)
            }
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
        runCatching { zenzScorer.closeModel() }
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

        // The IME input frame can measure this root with WRAP_CONTENT height.
        // Give its children real heights so the keyboard cannot collapse to 0 px.
        root.addView(composingView, LinearLayout.LayoutParams(-1, dp(40)))
        root.addView(candidatesView, LinearLayout.LayoutParams(-1, dp(56)))
        root.addView(keyboardView, LinearLayout.LayoutParams(-1, dp(240)))
        refresh()
        return root
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composing = ""
        conversionMode = false
        selectedBunsetsuEnd = null
        availableBunsetsuBoundaries = emptyList()
        refresh()
    }

    private fun appendText(text: String) {
        if (text.isEmpty()) return
        conversionMode = false
        selectedBunsetsuEnd = null
        composing += text
        // Keep the reading in Android's composing region, not only in our candidate strip.
        currentInputConnection?.setComposingText(composing, 1)
        refresh(showCandidates = true)
    }

    private fun deleteLast() {
        if (composing.isNotEmpty()) {
            composing = composing.dropLast(1)
            if (composing.isEmpty()) {
                currentInputConnection?.finishComposingText()
                refresh()
            } else {
                currentInputConnection?.setComposingText(composing, 1)
                refresh(showCandidates = true)
            }
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun convert() {
        conversionMode = true
        selectedBunsetsuEnd = null
        refresh(showCandidates = true)
    }

    private fun moveBunsetsuSelection(direction: Int) {
        if (composing.isEmpty()) return
        if (!conversionMode) {
            convert()
            return
        }
        val boundaries = availableBunsetsuBoundaries
        if (boundaries.size < 2) return
        val current = selectedBunsetsuEnd ?: boundaries.getOrElse(1) { composing.length }
        val currentIndex = boundaries.indexOf(current).let { if (it < 0) 1.coerceAtMost(boundaries.lastIndex) else it }
        val nextIndex = (currentIndex + direction).coerceIn(1, boundaries.lastIndex)
        val next = boundaries[nextIndex]
        if (next != current) {
            selectedBunsetsuEnd = next
            refresh(showCandidates = true)
        }
    }

    private fun commitComposing() {
        if (composing.isNotEmpty()) {
            currentInputConnection?.commitText(composing, 1)
            composing = ""
        }
        currentInputConnection?.finishComposingText()
        conversionMode = false
        selectedBunsetsuEnd = null
        availableBunsetsuBoundaries = emptyList()
        refresh()
    }

    private fun commitCandidate(text: String) {
        // commitText replaces the composing region previously created by setComposingText.
        currentInputConnection?.commitText(text, 1)
        composing = ""
        conversionMode = false
        selectedBunsetsuEnd = null
        availableBunsetsuBoundaries = emptyList()
        currentInputConnection?.finishComposingText()
        refresh()
    }

    private fun refresh(showCandidates: Boolean = false) {
        // onStartInput may run before Android asks for the input view.
        if (!::composingView.isInitialized ||
            !::conversionRow.isInitialized ||
            !::predictionRow.isInitialized
        ) return

        val generation = ++refreshGeneration
        composingView.text = composing
        conversionRow.removeAllViews()
        predictionRow.removeAllViews()
        if (!showCandidates || composing.isEmpty()) return

        val input = composing
        serviceScope.launch(Dispatchers.Default) {
            val bunsetsuResult = engine.detailedCandidatesWithBunsetsu(input, 16)
            val detailed = bunsetsuResult.candidates
            val prediction = engine.predictionCandidates(input, 16)
            val baseSplits = bunsetsuResult.splitPatternByCandidateString
            val baseMain = detailed.firstOrNull()
            val baseBoundaries = (listOf(0) +
                baseMain?.let { baseSplits[it.string].orEmpty() }.orEmpty()
                    .filter { it > 0 && it < input.length } +
                input.length).distinct().sorted()
            val activeEnd = if (conversionMode) {
                (selectedBunsetsuEnd ?: baseBoundaries.getOrElse(1) { input.length })
                    .coerceIn(1, input.length)
            } else null
            val activeSplits = if (activeEnd != null) {
                detailed.associate { candidate ->
                    candidate.string to (
                        listOf(activeEnd) +
                            baseSplits[candidate.string].orEmpty()
                                .filter { it > activeEnd && it < input.length }
                                .distinct()
                                .sorted()
                        )
                }
            } else baseSplits
            val rerankResult = zenzReranker.rerankDetailed(
                input = input,
                candidates = detailed,
                splitPatternByCandidateString = activeSplits,
            )
            val reranked = rerankResult.candidates
            val mainCandidate = reranked.firstOrNull() ?: detailed.firstOrNull()
            val firstAlternatives = rerankResult.firstSegmentAlternatives

            val alternatives = if (mainCandidate != null) {
                firstAlternatives
            } else {
                emptyList()
            }

            val topStrings = buildSet {
                mainCandidate?.let { add(it.string) }
                alternatives.forEach { add(it) }
            }
            val predictionCandidates = prediction
                .filter { it.string !in topStrings }
                .take(8)

            withContext(Dispatchers.Main.immediate) {
                if (generation != refreshGeneration || input != composing) return@withContext
                conversionRow.removeAllViews()
                predictionRow.removeAllViews()
                if (conversionMode) {
                    availableBunsetsuBoundaries = baseBoundaries.filter { it > 0 }
                    selectedBunsetsuEnd = activeEnd
                }
                mainCandidate?.let { candidate -> addCandidateView(conversionRow, candidate.string) { commitCandidate(candidate.string) } }
                alternatives.forEach { firstOutput ->
                    addCandidateView(conversionRow, firstOutput) {
                        commitSegmentAlternative(
                            firstOutput,
                            mainCandidate!!,
                            input,
                            activeSplits[mainCandidate.string].orEmpty(),
                        )
                    }
                }
                predictionCandidates.forEach { addCandidateView(predictionRow, it.string) }
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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

    private fun commitSegmentAlternative(
        firstSegmentOutput: String,
        mainCandidate: Candidate,
        input: String,
        splitPositions: List<Int>,
    ) {
        val boundaries = (listOf(0) + splitPositions.filter { it > 0 && it < input.length }.distinct().sorted() + input.length)
            .zipWithNext()
        val nodes = mainCandidate.conversionSegments.sortedBy { it.inputStart }
        val firstEnd = boundaries.firstOrNull()?.second ?: input.length
        val laterText = nodes
            .filter { it.inputStart >= firstEnd && it.inputEnd <= input.length }
            .joinToString(separator = "") { it.output }
        commitCandidate(firstSegmentOutput + laterText)
    }

    private inner class ActionListener : FlickKeyboardView.OnKeyboardActionListener {
        override fun onPress(action: KeyAction) = Unit

        override fun onAction(action: KeyAction, isFlick: Boolean) {
            when (action) {
                is KeyAction.Text -> appendText(action.text)
                is KeyAction.InputText -> appendText(action.text)
                KeyAction.Delete, KeyAction.Backspace -> deleteLast()
                KeyAction.Convert -> convert()
                KeyAction.MoveCursorLeft -> moveBunsetsuSelection(-1)
                KeyAction.MoveCursorRight -> moveBunsetsuSelection(1)
                KeyAction.Space -> {
                    if (composing.isNotEmpty()) {
                        convert()
                    } else {
                        currentInputConnection?.commitText(" ", 1)
                    }
                }
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

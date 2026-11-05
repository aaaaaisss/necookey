package com.kazumaproject.markdownhelperkeyboard.ime_service.candidatebar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.widget.OverScroller
import android.widget.PopupWindow
import androidx.core.graphics.ColorUtils
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripData
import kotlin.math.abs
import kotlin.math.roundToInt
import timber.log.Timber

/**
 * 文節チップ候補欄の上段（チップ列）。描画とジェスチャだけで、状態は持たない（[setData] で受け取り、操作は [Listener] へ）。
 * docs/CHIP_BAR.md「2026-11-05 長押しの区切りポップアップ」。
 *
 * - タップ = チップの選択 / 選択解除（下段にそのチップの候補）。
 * - 2 つのチップの間をタップ = くっつけて再変換（[Listener.onJoin]、軽い振動。[ChipStripLayout.joinAt]）。
 * - 右端の確定ボタン = いま見えている表記で確定。
 * - チップを長押し = チップ列の上にポップアップ（読みのかなを大きく、今の区切りは縦の棒）。押した所に一番近い文字の境目に
 *   カーソル。**指を置いたまま**横に動かすとカーソルが 1 境目ずつ相対的に動く（指の移動 [HOLD_STEP_DP]dp ≒ 1 字、文字は動かない、
 *   磁石なし、1 つ動くごとに軽い振動）。離す = カーソルの所の区切りを入れる（区切りの上なら消す）→ 再変換（[Listener.onSegmentation]）。
 *   指を上へポップアップの外まで、または下へ大きく外すと「やめる」（離しても何も変えない）。
 * - はみ出したとき（文字は最小 [MIN_TEXT_SCALE] 倍まで縮めてから）は横に動かすとスクロール（離すと慣性）。
 */
class ChipStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onChipTap(index: Int)
        /** チップ [index] と右隣をくっつける（チップの間のタップ）。 */
        fun onJoin(index: Int)
        /** 確定ボタン。 */
        fun onConfirm()
        /**
         * 長押しの区切り: 読み [reading] の区切りを [boundaries]（読みの UTF-16 位置、0 と末尾を除く）にする。
         * [select] = 選択するチップ（null = 変わった一番左のチップ、-1 = 選択なし）。
         */
        fun onSegmentation(reading: String, boundaries: Set<Int>, select: Int?)
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val layoutSpec = ChipStripLayout(
        chipPadding = 10f * density,
        gap = 10f * density,
    )
    /** くっつける帯がチップ端に食い込む幅（[ChipStripLayout.joinAt]）。 */
    private val joinEdgeSlop = JOIN_EDGE_SLOP_DP * density
    /** 右端の確定ボタンの幅（チップ列はこの左に並べる。スクロールしない）。 */
    private val confirmWidth = 34f * density

    /** チップ列の表示域の右端（この右が確定ボタン）。 */
    private fun contentRight(): Float = (width.toFloat() - confirmWidth).coerceAtLeast(0f)
    private val baseTextSize = 17f * density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = baseTextSize }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()

    private var data: ChipStripData = ChipStripData.EMPTY
    private var laid: ChipStripLayout.Laid = ChipStripLayout.Laid.EMPTY
    private var scroll = 0f
    /** 手でスクロールしたか（読みが変わるまで右端へ戻さない）。 */
    private var userScrolled = false
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null

    private var backgroundFill = Color.TRANSPARENT
    private var chipFill = Color.LTGRAY
    private var chipText = Color.BLACK
    private var selectedFill = Color.BLUE
    private var selectedText = Color.WHITE

    // 長押しの区切りポップアップ（View だけが持つ。離したときだけ Listener でモデルへ）
    private var holding = false
    private var holdReading = ""
    private var holdLetters: List<String> = emptyList()
    private var holdOffsets = IntArray(1)
    private var holdBounds: Set<Int> = emptySet()
    private var holdCursor: ChipStripLayout.HoldCursor? = null
    private var holdFirst = 0
    private var holdVisible = 0
    /** 指がポップアップの上の外・下の大きく外にある（離すとやめる）。 */
    private var holdCancelZone = false
    private var holdPopup: PopupWindow? = null
    private var holdPopupView: HoldPopupView? = null
    private var holdPopupShown = false
    private val holdRenderer = HoldPopupRenderer(density)

    // ジェスチャ
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
    /** 手でスクロール中か・スクロールを始めたときの指の x とスクロール量。 */
    private var scrolling = false
    private var scrollAnchorX = 0f
    private var scrollAnchor = 0f
    private var downX = 0f
    private var downY = 0f
    private var downOnButton = false
    /** 慣性スクロール中に触った（止めるだけ。タップ・長押しにしない）。 */
    private var downStoppedFling = false
    private var moved = false
    private var longPressed = false
    private val longPressRunnable = Runnable {
        if (moved || downOnButton || downStoppedFling) return@Runnable
        longPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        startHold(downX)
    }

    /** 既存の候補欄の色（[background] = 候補欄の背景、[chip] = 候補の背景、透明なら文字色を薄く混ぜて作る）。 */
    fun setColors(background: Int, chip: Int, text: Int, selection: Int, selectionText: Int) {
        backgroundFill = background
        chipFill = if (Color.alpha(chip) < 16) ColorUtils.setAlphaComponent(text, 0x22) else chip
        chipText = text
        selectedFill = selection
        selectedText = selectionText
        invalidate()
    }

    fun setData(newData: ChipStripData) {
        if (newData == data) return
        val old = data
        data = newData
        val readingChanged = old.items.joinToString("") { it.reading } != newData.items.joinToString("") { it.reading }
        // 長押し中: 同じ読みの再公開（リランカー・予測）なら続ける。読みが変わった・チップ列が空になったらやめる。
        if (holding && (readingChanged || newData.items.isEmpty())) cancelHold()
        if (readingChanged) userScrolled = false
        // 選択中のチップ（とその表記）が変わったときだけ寄せる。手でスクロールした位置は、それ以外の更新では保つ。
        val reveal = readingChanged || focusKey(old) != focusKey(newData)
        if (reveal || !userScrolled) scroller.forceFinished(true)
        relayout(reveal = reveal, followEnd = !userScrolled)
        invalidate()
    }

    /** 長押しのポップアップを（何も変えずに）閉じる。長押し中でなければ何もしない。 */
    fun cancelHold() {
        if (!holding) return
        holding = false
        holdCursor = null
        hideHoldPopup()
        invalidate()
    }

    /** 長押しの位置 [x]（画面上）でポップアップを出す。カーソルは押した所に一番近い文字の境目。 */
    private fun startHold(x: Float) {
        val (reading, bounds) = ChipStripLayout.segSource(data)
        val letters = ChipStripLayout.codePoints(reading)
        val start = ChipStripLayout.holdStartGap(laid, data, x + scroll) ?: return // 1 文字だけなら区切れる所が無い
        holding = true
        holdReading = reading
        holdLetters = letters
        holdOffsets = ChipStripLayout.segOffsets(letters)
        holdBounds = bounds
        holdCancelZone = false
        val cell = HOLD_CELL_DP * density
        holdCursor = ChipStripLayout.HoldCursor(letters.size, start, x, HOLD_STEP_DP * density)
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        holdVisible = ((screenW - 2f * HOLD_MARGIN_DP * density - 2f * holdRenderer.pad) / cell).toInt().coerceAtLeast(2)
        // 窓が要るときは、カーソルが押した指の所になるべく来るように
        val preferredSlot = ((x - holdRenderer.pad) / cell).roundToInt()
        holdFirst = ChipStripLayout.holdInitialFirst(letters.size, holdVisible, start, preferredSlot)
        scroller.forceFinished(true)
        parent?.requestDisallowInterceptTouchEvent(true)
        showHoldPopup(fingerX = x)
    }

    /** 指が動いた（長押し中）。 */
    private fun updateHold(x: Float, y: Float) {
        val cursor = holdCursor ?: return
        val popupH = holdRenderer.height(baseTextSize)
        val cancel = y < -(popupH + HOLD_POPUP_GAP_DP * density) || y > height + HOLD_CANCEL_BELOW_DP * density
        var changed = cancel != holdCancelZone
        holdCancelZone = cancel
        if (!cancel && cursor.move(x)) {
            changed = true
            holdFirst = ChipStripLayout.holdWindowFirst(holdLetters.size, holdVisible, cursor.gap, holdFirst)
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        if (changed) refreshHoldPopup()
    }

    /** 離した: やめる所でなければカーソルの区切りを入れる／消す。 */
    private fun finishHold() {
        val cursor = holdCursor
        val reading = holdReading
        val cancel = holdCancelZone
        val bounds = holdBounds
        val at = cursor?.let { holdOffsets.getOrNull(it.gap) }
        cancelHold()
        if (cancel || at == null || at <= 0 || at >= reading.length) return
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        listener?.onSegmentation(reading, ChipStripLayout.toggleBoundary(bounds, at), null)
    }

    private fun holdState(): HoldPopupRenderer.State = HoldPopupRenderer.State(
        letters = holdLetters,
        offsets = holdOffsets,
        bounds = holdBounds,
        first = holdFirst,
        visible = holdVisible.coerceAtMost(holdLetters.size),
        gap = holdCursor?.gap ?: -1,
        cancel = holdCancelZone,
        background = selectedFill,
        text = selectedText,
        textSize = baseTextSize * HOLD_TEXT_SCALE,
    )

    /** チップ列の上（候補欄の外、PopupWindow）にポップアップを出す。出せなければチップ列の中に描く（[onDraw]）。 */
    private fun showHoldPopup(fingerX: Float) {
        val state = holdState()
        val view = holdPopupView ?: HoldPopupView(context, holdRenderer).also { holdPopupView = it }
        view.state = state
        val w = holdRenderer.width(state).roundToInt().coerceAtLeast(1)
        val h = holdRenderer.height(baseTextSize).roundToInt().coerceAtLeast(1)
        holdPopupShown = runCatching {
            if (windowToken == null || !isAttachedToWindow) return@runCatching false
            val loc = IntArray(2)
            getLocationInWindow(loc)
            val screenW = resources.displayMetrics.widthPixels
            val margin = (HOLD_MARGIN_DP * density).roundToInt()
            // カーソルの境目が指の真上に来る位置（画面からはみ出さないように丸める）
            val cursorInPopup = holdRenderer.gapX(state, state.gap)
            val x = (loc[0] + fingerX - cursorInPopup).roundToInt().coerceIn(margin, (screenW - w - margin).coerceAtLeast(margin))
            val y = loc[1] - h - (HOLD_POPUP_GAP_DP * density).roundToInt()
            val popup = holdPopup ?: PopupWindow(view, w, h, false).apply {
                isTouchable = false
                isFocusable = false
                isClippingEnabled = false
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                animationStyle = 0
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            }.also { holdPopup = it }
            popup.width = w
            popup.height = h
            if (popup.isShowing) popup.update(x, y, w, h) else popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
            view.invalidate()
            popup.isShowing
        }.getOrElse { e ->
            Timber.w(e, "chip hold popup failed")
            false
        }
        invalidate()
    }

    private fun refreshHoldPopup() {
        val state = holdState()
        holdPopupView?.let {
            it.state = state
            it.invalidate()
        }
        if (!holdPopupShown) invalidate()
    }

    private fun hideHoldPopup() {
        runCatching { holdPopup?.dismiss() }
        holdPopupShown = false
    }

    private fun focusKey(d: ChipStripData) = d.selectedIndex to d.items.getOrNull(d.selectedIndex)

    private fun availableWidth(): Float = contentRight()

    private fun maxScroll(): Float = (laid.width - availableWidth()).coerceAtLeast(0f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun relayout(reveal: Boolean = true, followEnd: Boolean = !userScrolled) {
        val available = (width.toFloat() - confirmWidth).coerceAtLeast(0f)
        textPaint.textSize = baseTextSize
        var result = layoutSpec.layout(data) { textPaint.measureText(it) }
        if (available > 0f && result.width > available) {
            val scale = (available / result.width).coerceAtLeast(MIN_TEXT_SCALE)
            textPaint.textSize = baseTextSize * scale
            result = layoutSpec.layout(data) { textPaint.measureText(it) }
        }
        laid = result
        scroll = layoutSpec.scrollFor(result, data, available, scroll, reveal = reveal, followEnd = followEnd)
    }

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            scroll = scroller.currX.toFloat().coerceIn(0f, maxScroll())
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (Color.alpha(backgroundFill) != 0) canvas.drawColor(backgroundFill)
        if (data.items.isEmpty()) return
        val h = height.toFloat()
        val vPad = (h * 0.14f).coerceAtMost(8f * density)
        val radius = (h - vPad * 2f) / 2f
        val baseline = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        drawConfirm(canvas, h, vPad)
        canvas.save()
        canvas.clipRect(0f, 0f, contentRight(), h)
        canvas.translate(-scroll, 0f)
        data.items.forEachIndexed { i, item ->
            val box = laid.chips.getOrNull(i) ?: return@forEachIndexed
            fillPaint.color = if (item.selected) selectedFill else chipFill
            rect.set(box.left, vPad, box.right, h - vPad)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            textPaint.color = if (item.selected) selectedText else chipText
            canvas.drawText(item.label, box.left + layoutSpec.chipPadding, baseline, textPaint)
        }
        canvas.restore()
        if (holding && !holdPopupShown) {
            // ポップアップが出せないとき: チップ列の上に重ねて描く（高さは上段に合わせて縮める）
            val state = holdState().copy(textSize = (h * 0.5f).coerceAtMost(baseTextSize * HOLD_TEXT_SCALE))
            val w = holdRenderer.width(state).coerceAtMost(contentRight())
            canvas.save()
            canvas.translate(((contentRight() - w) / 2f).coerceAtLeast(0f), 0f)
            holdRenderer.draw(canvas, state, w, h)
            canvas.restore()
        }
    }

    /** 右端の確定ボタン（丸の中にチェック）。 */
    private fun drawConfirm(canvas: Canvas, h: Float, vPad: Float) {
        val cx = width - confirmWidth / 2f - 2f * density
        val cy = h / 2f
        val r = ((h - vPad * 2f) / 2f).coerceAtMost(confirmWidth / 2f - 2f * density)
        fillPaint.color = selectedFill
        canvas.drawCircle(cx, cy, r, fillPaint)
        checkPaint.color = selectedText
        val s = r * 0.5f
        canvas.drawLine(cx - s, cy, cx - s * 0.25f, cy + s * 0.7f, checkPaint)
        canvas.drawLine(cx - s * 0.25f, cy + s * 0.7f, cx + s, cy - s * 0.6f, checkPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            velocityTracker?.recycle()
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelHold()
                downX = event.x
                downY = event.y
                moved = false
                longPressed = false
                scrolling = false
                downStoppedFling = !scroller.isFinished
                scroller.forceFinished(true)
                downOnButton = event.x >= contentRight() && data.items.isNotEmpty()
                removeCallbacks(longPressRunnable)
                if (data.items.isNotEmpty() && !downOnButton && !downStoppedFling) {
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (holding) {
                    updateHold(event.x, event.y)
                    return true
                }
                val dx = event.x - downX
                val dy = event.y - downY
                if (!moved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    moved = true
                    removeCallbacks(longPressRunnable)
                    if (!downOnButton && !longPressed && abs(dx) >= abs(dy)) startScroll(event.x)
                }
                if (scrolling) {
                    scroll = (scrollAnchor - (event.x - scrollAnchorX)).coerceIn(0f, maxScroll())
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (holding) {
                    updateHold(event.x, event.y)
                    finishHold()
                    return true
                }
                if (downOnButton) {
                    if (!moved && event.x >= contentRight()) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        listener?.onConfirm()
                    }
                    return true
                }
                if (longPressed || downStoppedFling) return true
                if (scrolling) {
                    scrolling = false
                    fling()
                    return true
                }
                if (moved) return true
                val x = event.x + scroll
                val join = layoutSpec.joinAt(laid, x, joinEdgeSlop)
                if (join != null) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onJoin(join)
                } else {
                    val index = layoutSpec.chipAt(laid, x)
                    if (index >= 0) listener?.onChipTap(index)
                }
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                scrolling = false
                cancelHold()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** 手でのスクロールを始める（指の今の位置を基準に。切り替えた瞬間に中身が跳ばないように）。 */
    private fun startScroll(x: Float) {
        if (maxScroll() <= 0f) return
        scrolling = true
        userScrolled = true
        scrollAnchorX = x
        scrollAnchor = scroll
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    /** 指を離したときの慣性スクロール。 */
    private fun fling() {
        val tracker = velocityTracker ?: return
        tracker.computeCurrentVelocity(1000, maxFlingVelocity)
        val vx = tracker.xVelocity
        if (abs(vx) < minFlingVelocity) return
        scroller.fling(scroll.roundToInt(), 0, -vx.roundToInt(), 0, 0, maxScroll().roundToInt(), 0, 0)
        postInvalidateOnAnimation()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) cancelHold()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        cancelHold()
        holdPopup = null
        scroller.forceFinished(true)
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDetachedFromWindow()
    }

    companion object {
        const val MIN_TEXT_SCALE = 0.7f
        const val JOIN_EDGE_SLOP_DP = 6f
        /** ポップアップの 1 字のマスの幅。 */
        const val HOLD_CELL_DP = 30f
        /** カーソルを 1 境目動かす指の横の移動量（≒ 1 字のマス）。 */
        const val HOLD_STEP_DP = 26f
        /** ポップアップの文字の大きさ（チップの文字に対する倍率）。 */
        const val HOLD_TEXT_SCALE = 1.35f
        /** ポップアップとチップ列の間・画面の端との余白。 */
        const val HOLD_POPUP_GAP_DP = 6f
        const val HOLD_MARGIN_DP = 6f
        /** 指をチップ列の下へこれ以上外すと「やめる」。 */
        const val HOLD_CANCEL_BELOW_DP = 48f
    }
}

/**
 * 長押しの区切りポップアップの描画（PopupWindow の中と、出せないときのチップ列の中で共通）。
 * かなは同じ幅のマス、今の区切りは選択文字色の太い棒、区切りでない境目は薄い短い線、カーソルは太い棒＋上下の三角、
 * 右上に離したときの動作（切る / つなぐ / やめる）。
 */
internal class HoldPopupRenderer(private val density: Float) {
    data class State(
        val letters: List<String>,
        val offsets: IntArray,
        val bounds: Set<Int>,
        val first: Int,
        val visible: Int,
        val gap: Int,
        val cancel: Boolean,
        val background: Int,
        val text: Int,
        val textSize: Float,
    )

    val pad: Float = 10f * density
    private val cell: Float = ChipStripView.HOLD_CELL_DP * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val rect = RectF()
    private val path = android.graphics.Path()

    fun width(state: State): Float = pad * 2f + cell * state.visible
    fun height(baseTextSize: Float): Float = baseTextSize * ChipStripView.HOLD_TEXT_SCALE * 1.9f + 14f * density

    /** 境目 [gap] のポップアップ内の x。 */
    fun gapX(state: State, gap: Int): Float = pad + cell * (gap - state.first)

    fun draw(canvas: Canvas, state: State, w: Float, h: Float) {
        rect.set(0f, 0f, w, h)
        fill.color = state.background
        val r = 12f * density
        canvas.drawRoundRect(rect, r, r, fill)
        val hintSize = 11f * density
        hintPaint.textSize = hintSize
        val top = hintSize + 4f * density
        val last = (state.first + state.visible).coerceAtMost(state.letters.size)
        // かな
        paint.textSize = state.textSize
        val maxLetter = state.letters.subList(state.first, last).maxOfOrNull { paint.measureText(it) } ?: 0f
        if (maxLetter > cell - 2f * density) paint.textSize = state.textSize * ((cell - 2f * density) / maxLetter).coerceAtLeast(0.5f)
        val alpha = if (state.cancel) 0x80 else 0xFF
        paint.color = ColorUtils.setAlphaComponent(state.text, alpha)
        val mid = top + (h - top) / 2f
        val baseline = mid - (paint.descent() + paint.ascent()) / 2f
        for (k in state.first until last) {
            val left = pad + cell * (k - state.first)
            val letter = state.letters[k]
            canvas.drawText(letter, left + (cell - paint.measureText(letter)) / 2f, baseline, paint)
        }
        // 窓の外に続きがあれば端に「…」
        hintPaint.color = ColorUtils.setAlphaComponent(state.text, 0xAA)
        if (state.first > 0) canvas.drawText("…", 2f * density, baseline, hintPaint)
        if (last < state.letters.size) canvas.drawText("…", w - pad + 1f * density, baseline, hintPaint)
        // 境目
        val y0 = top + 3f * density
        val y1 = h - 4f * density
        for (k in (state.first + 1) until last) {
            val x = pad + cell * (k - state.first)
            if (state.offsets[k] in state.bounds) {
                line.color = ColorUtils.setAlphaComponent(state.text, alpha)
                line.strokeWidth = 2.5f * density
                canvas.drawLine(x, y0 + 4f * density, x, y1 - 4f * density, line)
            } else {
                line.color = ColorUtils.setAlphaComponent(state.text, 0x50)
                line.strokeWidth = 1f * density
                canvas.drawLine(x, y1 - 8f * density, x, y1 - 3f * density, line)
            }
        }
        // カーソル
        val onBoundary = state.gap in 1 until state.letters.size && state.offsets[state.gap] in state.bounds
        if (!state.cancel && state.gap in (state.first + 1) until last) {
            val x = gapX(state, state.gap)
            line.color = state.text
            line.strokeWidth = 4f * density
            if (onBoundary) {
                // 区切りの上: 離すと消す → 棒に × を重ねる
                canvas.drawLine(x, y0, x, y1, line)
                val s = 5f * density
                line.strokeWidth = 2.2f * density
                canvas.drawLine(x - s, mid - s, x + s, mid + s, line)
                canvas.drawLine(x - s, mid + s, x + s, mid - s, line)
            } else {
                canvas.drawLine(x, y0, x, y1, line)
            }
            val t = 5f * density
            fill.color = state.text
            path.reset()
            path.moveTo(x - t, y0 - t * 0.2f); path.lineTo(x + t, y0 - t * 0.2f); path.lineTo(x, y0 + t); path.close()
            canvas.drawPath(path, fill)
        }
        // 右上: 離したときの動作
        val hint = when {
            state.cancel -> HINT_CANCEL
            onBoundary -> HINT_JOIN
            else -> HINT_CUT
        }
        hintPaint.color = state.text
        canvas.drawText(hint, w - pad - hintPaint.measureText(hint), hintSize + 1f * density, hintPaint)
    }

    companion object {
        const val HINT_CUT = "離すと区切る"
        const val HINT_JOIN = "離すとつなぐ"
        const val HINT_CANCEL = "やめる"
    }
}

/** ポップアップの中身（PopupWindow に載せる。タッチは取らない）。 */
private class HoldPopupView(context: Context, private val renderer: HoldPopupRenderer) : View(context) {
    var state: HoldPopupRenderer.State? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = state ?: return
        renderer.draw(canvas, s, width.toFloat(), height.toFloat())
    }
}

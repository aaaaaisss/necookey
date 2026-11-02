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
import android.widget.OverScroller
import androidx.core.graphics.ColorUtils
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripData
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 文節チップ候補欄の上段（チップ列）。描画とジェスチャだけで、状態は持たない（[setData] で受け取り、操作は [Listener] へ）。
 * docs/CHIP_BAR.md「2026-11-02 区切り編集モード」。
 *
 * 普段:
 * - タップ = チップの選択 / 選択解除（下段にそのチップの候補）。
 * - 右端の確定ボタン = いま見えている表記で確定。
 * - 長押し（チップ列のどこでも）= 区切り編集モードに入る。
 * - はみ出したとき（文字は最小 [MIN_TEXT_SCALE] 倍まで縮めてから）は横に動かすとスクロール（離すと慣性）。
 *
 * 区切り編集モード:
 * - 読み全体をかな 1 文字ずつ、同じ幅のマスに並べ、今の区切りをはっきりした縦線で出す（線は幅を取らないので文字は動かない）。
 * - 文字の間をタップ（一番近い境目。磁石なし）= そこの区切りを入れる／消す（軽い振動）。その都度 [Listener.onSegmentation] で
 *   モデルに反映する（再変換は裏で。入力欄の表記もその都度変わる）。
 * - 右端の「完了」ボタン = 編集モードを抜ける（区切りはもう反映済み）。打鍵で読みが変わる・確定でチップ列が消えるときも抜ける。
 * - もう一度長押し = 入ったときの区切りに戻して抜ける（取り消し）。
 * - 横に動かすとスクロール。
 */
class ChipStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onChipTap(index: Int)
        /** 確定ボタン。 */
        fun onConfirm()
        /**
         * 区切り編集: 読み [reading] の区切りを [boundaries]（読みの UTF-16 位置、0 と末尾を除く）にする。
         * [select] = 選択するチップ（null = 変わった一番左のチップ、-1 = 選択なし）。
         */
        fun onSegmentation(reading: String, boundaries: Set<Int>, select: Int?)
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val layoutSpec = ChipStripLayout(
        chipPadding = 10f * density,
        gap = 6f * density,
    )
    /** 右端の確定ボタンの幅（チップ列はこの左に並べる。スクロールしない）。編集モードは「完了」を出すので広げる。 */
    private val confirmWidth = 34f * density
    private val doneWidth = 52f * density

    private fun buttonWidth(): Float = if (segEditing) doneWidth else confirmWidth

    /** チップ列の表示域の右端（この右が確定 / 完了ボタン）。 */
    private fun contentRight(): Float = (width.toFloat() - buttonWidth()).coerceAtLeast(0f)
    private val baseTextSize = 17f * density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = baseTextSize }
    private val segTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = baseTextSize }
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * density
        isFakeBoldText = true
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
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

    // 区切り編集モード（View だけが持つ。区切りはタップごとに Listener でモデルへ）
    private var segEditing = false
    private var segReading = ""
    private var segLetters: List<String> = emptyList()
    private var segOffsets = IntArray(1)
    /** いまの区切り（読みの UTF-16 位置）。モデルの結果を待たずに、ユーザーのタップをそのまま出す。 */
    private var segBounds: Set<Int> = emptySet()
    /** 入ったときの区切りと選択（長押しでの取り消し用）。 */
    private var segEntryBounds: Set<Int> = emptySet()
    private var segEntrySelected = -1
    /** マスの幅（全マス同じ。タップしやすい大きさ）と左右の余白。 */
    private val segCellWidth = 34f * density
    private val segStart = 8f * density

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
        if (segEditing) cancelSegmentEdit() else enterSegmentEdit(downX)
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
        if (segEditing) {
            // 同じ読みのまま（モデルの反映・リランカーの再公開など）なら編集を続ける。区切りの表示はユーザーのタップのまま。
            // 読みが変わった（打鍵・後退）・チップ列が空になった（確定など）なら抜ける（区切りはもう反映済み）。
            if (!readingChanged && newData.items.isNotEmpty()) {
                invalidate()
                return
            }
            exitSegmentEdit()
        }
        if (readingChanged) userScrolled = false
        // 選択中のチップ（とその表記）が変わったときだけ寄せる。手でスクロールした位置は、それ以外の更新では保つ。
        val reveal = readingChanged || focusKey(old) != focusKey(newData)
        if (reveal || !userScrolled) scroller.forceFinished(true)
        relayout(reveal = reveal, followEnd = !userScrolled)
        invalidate()
    }

    /** 区切り編集モードを（区切りはそのまま）抜ける。編集中でなければ何もしない。 */
    fun exitSegmentEdit() {
        if (!segEditing) return
        segEditing = false
        segLetters = emptyList()
        segOffsets = IntArray(1)
        segReading = ""
        scroller.forceFinished(true)
        relayout(reveal = true, followEnd = !userScrolled)
        invalidate()
    }

    /** 長押しの位置 [x]（画面上）で区切り編集モードに入る。押したチップの先頭の文字が、そのチップの左端があった所に来る。 */
    private fun enterSegmentEdit(x: Float) {
        val (reading, bounds) = ChipStripLayout.segSource(data)
        val letters = ChipStripLayout.codePoints(reading)
        if (letters.size < 2) return // 1 文字だけなら区切れる所が無い
        val pressed = layoutSpec.chipAt(laid, x + scroll).takeIf { it >= 0 } ?: nearestChip(x + scroll)
        val chipScreenLeft = laid.chips.getOrNull(pressed)?.left?.minus(scroll) ?: 0f
        val chipStart = data.items.take(pressed.coerceAtLeast(0)).sumOf { it.reading.length }
        segEditing = true
        segReading = reading
        segLetters = letters
        segOffsets = ChipStripLayout.segOffsets(letters)
        segBounds = bounds
        segEntryBounds = bounds
        segEntrySelected = data.selectedIndex
        scroller.forceFinished(true)
        parent?.requestDisallowInterceptTouchEvent(true)
        val k = segOffsets.indexOfFirst { it >= chipStart }.coerceAtLeast(0)
        scroll = ChipStripLayout.segEntryScroll(
            cellLeft = ChipStripLayout.segCellLeft(segStart, segCellWidth, k),
            chipScreenLeft = chipScreenLeft,
            maxScroll = maxScroll(),
        )
        invalidate()
    }

    /** もう一度長押し: 入ったときの区切り（と選択）に戻して抜ける。 */
    private fun cancelSegmentEdit() {
        val reading = segReading
        val changed = segBounds != segEntryBounds
        val entry = segEntryBounds
        val selected = segEntrySelected
        exitSegmentEdit()
        if (changed) listener?.onSegmentation(reading, entry, selected)
    }

    /** 文字の境目 [gap] の区切りを入れる／消す（軽い振動）。 */
    private fun toggleGap(gap: Int) {
        val at = segOffsets.getOrNull(gap) ?: return
        if (at <= 0 || at >= segReading.length) return
        segBounds = ChipStripLayout.toggleBoundary(segBounds, at)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        invalidate()
        listener?.onSegmentation(segReading, segBounds, null)
    }

    private fun nearestChip(x: Float): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        laid.chips.forEachIndexed { i, box ->
            val d = abs(x - box.center)
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }

    private fun focusKey(d: ChipStripData) = d.selectedIndex to d.items.getOrNull(d.selectedIndex)

    private fun availableWidth(): Float = contentRight()

    private fun contentWidth(): Float =
        if (segEditing) ChipStripLayout.segWidth(segStart, segCellWidth, segLetters.size) else laid.width

    private fun maxScroll(): Float = (contentWidth() - availableWidth()).coerceAtLeast(0f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
        if (segEditing) scroll = scroll.coerceIn(0f, maxScroll())
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
        if (!segEditing) {
            scroll = layoutSpec.scrollFor(result, data, available, scroll, reveal = reveal, followEnd = followEnd)
        }
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
        if (segEditing) {
            drawDone(canvas, h, vPad)
            drawSegmentEdit(canvas, h, vPad)
            return
        }
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
    }

    /**
     * 区切り編集モード: 区切りで分けた塊ごとに角丸の背景、かなは同じ幅のマスの中央、区切りは太い縦線（選択色）、
     * 区切りでない文字の間は薄い短い線（タップできる所の目印）。線はマスの境目に描くので幅を取らない。
     */
    private fun drawSegmentEdit(canvas: Canvas, h: Float, vPad: Float) {
        val n = segLetters.size
        if (n == 0) return
        canvas.save()
        canvas.clipRect(0f, 0f, contentRight(), h)
        canvas.translate(-scroll, 0f)
        val inset = 2.5f * density
        val radius = 8f * density
        // 塊の背景
        fillPaint.color = chipFill
        var segFrom = 0
        for (k in 1..n) {
            if (k == n || segOffsets[k] in segBounds) {
                rect.set(
                    ChipStripLayout.segCellLeft(segStart, segCellWidth, segFrom) + inset,
                    vPad,
                    ChipStripLayout.segCellLeft(segStart, segCellWidth, k) - inset,
                    h - vPad,
                )
                canvas.drawRoundRect(rect, radius, radius, fillPaint)
                segFrom = k
            }
        }
        // かな
        val maxLetter = segLetters.maxOf { segTextPaint.apply { textSize = baseTextSize }.measureText(it) }
        segTextPaint.textSize = if (maxLetter > segCellWidth - 2f * inset) {
            baseTextSize * ((segCellWidth - 2f * inset) / maxLetter).coerceAtLeast(0.5f)
        } else baseTextSize
        segTextPaint.color = chipText
        val baseline = h / 2f - (segTextPaint.descent() + segTextPaint.ascent()) / 2f
        segLetters.forEachIndexed { k, letter ->
            val left = ChipStripLayout.segCellLeft(segStart, segCellWidth, k)
            canvas.drawText(letter, left + (segCellWidth - segTextPaint.measureText(letter)) / 2f, baseline, segTextPaint)
        }
        // 境目の線
        for (k in 1 until n) {
            val x = ChipStripLayout.segCellLeft(segStart, segCellWidth, k)
            if (segOffsets[k] in segBounds) {
                linePaint.color = selectedFill
                linePaint.strokeWidth = 3f * density
                canvas.drawLine(x, 2f * density, x, h - 2f * density, linePaint)
            } else {
                linePaint.color = ColorUtils.setAlphaComponent(chipText, 0x40)
                linePaint.strokeWidth = 1f * density
                canvas.drawLine(x, h - vPad - 8f * density, x, h - vPad - 2f * density, linePaint)
            }
        }
        canvas.restore()
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

    /** 区切り編集モードの「完了」ボタン（確定ボタンの場所、角丸に文字）。 */
    private fun drawDone(canvas: Canvas, h: Float, vPad: Float) {
        val right = width - 2f * density
        val left = right - doneWidth + 4f * density
        rect.set(left, vPad, right, h - vPad)
        fillPaint.color = selectedFill
        val r = (h - vPad * 2f) / 2f
        canvas.drawRoundRect(rect, r, r, fillPaint)
        buttonTextPaint.color = selectedText
        val label = DONE_LABEL
        val baseline = h / 2f - (buttonTextPaint.descent() + buttonTextPaint.ascent()) / 2f
        canvas.drawText(label, rect.centerX() - buttonTextPaint.measureText(label) / 2f, baseline, buttonTextPaint)
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
                if (downOnButton) {
                    if (!moved && event.x >= contentRight()) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (segEditing) exitSegmentEdit() else listener?.onConfirm()
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
                if (segEditing) {
                    ChipStripLayout.segGapAt(event.x + scroll, segStart, segCellWidth, segLetters.size)?.let(::toggleGap)
                } else {
                    val index = layoutSpec.chipAt(laid, event.x + scroll)
                    if (index >= 0) listener?.onChipTap(index)
                }
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                scrolling = false
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
        if (!isVisible) exitSegmentEdit()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        segEditing = false
        scroller.forceFinished(true)
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDetachedFromWindow()
    }

    companion object {
        const val MIN_TEXT_SCALE = 0.7f
        const val DONE_LABEL = "完了"
    }
}

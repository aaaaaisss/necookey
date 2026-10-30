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
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripItem
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 文節チップ候補欄の上段（チップ列）。描画とジェスチャだけで、状態は持たない（[setData] で受け取り、操作は [Listener] へ）。
 *
 * - 右端の小さい確定ボタン = 確定キーと同じ（いま見えている表記で確定）。
 * - タップ = 選択 / 選択解除。チップ間（広めの当たり判定）のタップ = くっつける。
 * - 長押し = 押した文字の位置でそのチップを切る（表記の文字位置を読みの位置に割り当てる。[ChipStripLayout.cutOffsetForLabel]）。
 * - 区切り（チップ間）に指を置いて左右に滑らせる = 境界を 1 字ずつ吸い付かせて動かし、離したところで確定。動かしている間は
 *   指の位置に境界前後のかなを拡大したバブルを出す。
 * - チップ上の左右フリック = 境界を 1 字ずらす（右 = 伸ばす、左 = 縮める。区切りドラッグのフォールバック）。
 * - 展開中（旧操作。モデルに残っている）の文字の間のタップ = 切る。
 * はみ出したとき（文字は最小 [MIN_TEXT_SCALE] 倍まで縮めてから）は横にスクロールできる（docs/CHIP_BAR.md「チップ列のスクロール」）:
 * - 区切りの上以外で横に長く・ゆっくり動かす = 手でスクロール（離すと慣性）。チップ上の短く速い横の動きは従来どおりフリック
 *   （[ChipStripLayout.chipSwipeIsScroll]）。チップの無い所（右端の余白など）から動かせばすぐスクロール。
 * - 区切りドラッグで指が表示域の端に近づくと自動でスクロールし、そのまま見えていなかった所まで境界を動かせる。
 * - 選択・展開中のチップが変わったら（タップ・スペース・操作の結果）そのチップを見える位置まで寄せる。読みが変わったら
 *   （打ち足し・後退）右端（打っている所）へ。それ以外の更新では手でスクロールした位置を保つ。
 */
class ChipStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onChipTap(index: Int)
        fun onJoin(index: Int)
        fun onCut(gapIndex: Int)
        fun onFlick(index: Int, toRight: Boolean)
        /** 確定ボタン。 */
        fun onConfirm()
        /** 長押し: チップ [index] を読みの [offset]（チップ内の位置、1..読みの長さ−1）で切る。 */
        fun onLongPressCut(index: Int, offset: Int)
        /** 区切りドラッグ: チップ [index] と右隣の境界を [delta] 字動かす（正 = 右へ）。 */
        fun onBoundaryDrag(index: Int, delta: Int)
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val layoutSpec = ChipStripLayout(
        chipPadding = 10f * density,
        gap = 6f * density,
        letterPadding = 6f * density,
        joinSlop = 12f * density,
    )
    /** 右端の確定ボタンの幅（チップ列はこの左に並べる。スクロールしない）。 */
    private val confirmWidth = 34f * density

    /** チップ列の表示域の右端（この右が確定ボタン）。 */
    private fun contentRight(): Float = (width.toFloat() - confirmWidth).coerceAtLeast(0f)
    private val baseTextSize = 17f * density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = baseTextSize }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f * density }
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

    // ジェスチャ
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val flickDistance = 20f * density
    /** チップ上でこれ以上横に動かす（または [FLICK_MAX_MS] より長く動かし続ける）とフリックではなくスクロール。 */
    private val scrollStartDistance = 56f * density
    /** 区切りドラッグの自動スクロール: 表示域の端からこの幅に入ると動き出す。 */
    private val autoScrollEdge = 32f * density
    private val autoScrollMaxSpeed = 600f * density
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
    private var downTime = 0L
    /** 手でスクロール中か・スクロールを始めたときの指の x とスクロール量。 */
    private var scrolling = false
    private var scrollAnchorX = 0f
    private var scrollAnchor = 0f
    /** 区切りドラッグを始めたときの指の位置（チップ列の座標 = スクロール込み）。 */
    private var downContentX = 0f
    private var lastAutoScrollFrame = 0L
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (!dragging) return
            val now = System.nanoTime()
            val dt = ((now - lastAutoScrollFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastAutoScrollFrame = now
            val speed = autoScrollSpeed()
            if (speed == 0f) return
            val before = scroll
            scroll = (scroll + speed * dt).coerceIn(0f, maxScroll())
            if (scroll != before) {
                userScrolled = true
                updateDragDelta()
                invalidate()
            }
            postOnAnimation(this)
        }
    }
    private var downX = 0f
    private var downY = 0f
    private var downHit: ChipStripLayout.Hit = ChipStripLayout.Hit.None
    private var downOnConfirm = false
    private var moved = false
    private var longPressed = false
    /** 区切りドラッグ中か・動かした字数・指の x（バブルの位置）。 */
    private var dragging = false
    private var dragDelta = 0
    private var dragX = 0f
    private val longPressRunnable = Runnable {
        val hit = downHit
        if (moved || hit !is ChipStripLayout.Hit.Chip) return@Runnable
        val item = data.items.getOrNull(hit.index) ?: return@Runnable
        val box = laid.chips.getOrNull(hit.index) ?: return@Runnable
        val labelIndex = labelIndexAt(item.label, downX + scroll - box.left - layoutSpec.chipPadding)
        val offset = ChipStripLayout.cutOffsetForLabel(item.label, item.reading, labelIndex) ?: return@Runnable
        longPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        listener?.onLongPressCut(hit.index, offset)
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
        dragging = false
        removeCallbacks(autoScrollRunnable)
        val readingChanged = old.items.joinToString("") { it.reading } != newData.items.joinToString("") { it.reading }
        if (readingChanged) userScrolled = false
        // 選択・展開中のチップ（とその表記）が変わったときだけ寄せる。手でスクロールした位置は、それ以外の更新では保つ。
        val reveal = readingChanged || focusKey(old) != focusKey(newData)
        if (reveal || !userScrolled) scroller.forceFinished(true)
        relayout(reveal = reveal, followEnd = !userScrolled)
        invalidate()
    }

    private fun focusKey(d: ChipStripData): Pair<Int, ChipStripItem?> {
        val i = d.expandedIndex.takeIf { it >= 0 } ?: d.selectedIndex
        return i to d.items.getOrNull(i)
    }

    private fun availableWidth(): Float = (width.toFloat() - confirmWidth).coerceAtLeast(0f)

    private fun maxScroll(): Float = layoutSpec.maxScroll(laid, availableWidth())

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun relayout(reveal: Boolean = true, followEnd: Boolean = !userScrolled) {
        val available = availableWidth()
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

    private fun autoScrollSpeed(): Float = ChipStripLayout.edgeAutoScrollSpeed(
        x = dragX,
        left = 0f,
        right = contentRight(),
        edge = autoScrollEdge,
        maxSpeed = autoScrollMaxSpeed,
    )

    /** 区切りドラッグの字数を、始めた位置からの指の移動（自動スクロール分を含む）で決める。 */
    private fun updateDragDelta() {
        val hit = downHit as? ChipStripLayout.Hit.Join ?: return
        val left = data.items.getOrNull(hit.index)?.reading.orEmpty()
        val right = data.items.getOrNull(hit.index + 1)?.reading.orEmpty()
        val step = textPaint.measureText("あ").coerceAtLeast(8f * density)
        val contentX = dragX + scroll
        val delta = ChipStripLayout.clampDelta(left.length, right.length, ((contentX - downContentX) / step).roundToInt())
        if (delta != dragDelta) {
            dragDelta = delta
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** 表記の先頭から [x]（チップの文字の左端から）に一番近い文字の境目（0..表記の長さ）。 */
    private fun labelIndexAt(label: String, x: Float): Int {
        var best = 0
        var bestDistance = abs(x)
        for (k in 1..label.length) {
            val d = abs(x - textPaint.measureText(label, 0, k))
            if (d < bestDistance) {
                bestDistance = d
                best = k
            }
        }
        return best
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
            val fill = if (item.selected) selectedFill else chipFill
            val fg = if (item.selected) selectedText else chipText
            fillPaint.color = fill
            rect.set(box.left, vPad, box.right, h - vPad)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            textPaint.color = fg
            if (item.expanded) {
                val boxes = laid.letters.getOrNull(i).orEmpty()
                linePaint.color = ColorUtils.setAlphaComponent(fg, 0x99)
                item.letters.forEachIndexed { k, letter ->
                    val b = boxes.getOrNull(k) ?: return@forEachIndexed
                    canvas.drawText(letter, b.left + (b.width - textPaint.measureText(letter)) / 2f, baseline, textPaint)
                    if (k < item.letters.lastIndex) {
                        canvas.drawLine(b.right, vPad * 2f, b.right, h - vPad * 2f, linePaint)
                    }
                }
            } else {
                canvas.drawText(item.label, box.left + layoutSpec.chipPadding, baseline, textPaint)
            }
        }
        canvas.restore()
        if (dragging) drawBubble(canvas, h)
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

    /** 区切りドラッグ中: 指の位置に境界前後のかなを拡大したバブル（上段の高さの中に描く）。 */
    private fun drawBubble(canvas: Canvas, h: Float) {
        val hit = downHit as? ChipStripLayout.Hit.Join ?: return
        val left = data.items.getOrNull(hit.index)?.reading ?: return
        val right = data.items.getOrNull(hit.index + 1)?.reading ?: return
        val (before, after) = ChipStripLayout.boundaryPreview(left, right, dragDelta)
        bubblePaint.textSize = baseTextSize * BUBBLE_TEXT_SCALE
        val sep = " | "
        val wBefore = bubblePaint.measureText(before)
        val wSep = bubblePaint.measureText(sep)
        val w = wBefore + wSep + bubblePaint.measureText(after) + 16f * density
        val bh = (h - 2f * density).coerceAtMost(bubblePaint.textSize * 1.6f)
        val l = (dragX - w / 2f).coerceIn(0f, (width - w).coerceAtLeast(0f))
        rect.set(l, (h - bh) / 2f, l + w, (h + bh) / 2f)
        fillPaint.color = selectedFill
        canvas.drawRoundRect(rect, bh / 2f, bh / 2f, fillPaint)
        val baseline = h / 2f - (bubblePaint.descent() + bubblePaint.ascent()) / 2f
        var x = l + 8f * density
        bubblePaint.color = selectedText
        canvas.drawText(before, x, baseline, bubblePaint)
        x += wBefore
        bubblePaint.color = ColorUtils.setAlphaComponent(selectedText, 0xAA)
        canvas.drawText(sep, x, baseline, bubblePaint)
        x += wSep
        bubblePaint.color = selectedText
        canvas.drawText(after, x, baseline, bubblePaint)
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
                downTime = event.eventTime
                moved = false
                longPressed = false
                dragging = false
                scrolling = false
                dragDelta = 0
                removeCallbacks(autoScrollRunnable)
                // 慣性スクロール中のタッチは止めるだけ
                val wasFlinging = !scroller.isFinished
                scroller.forceFinished(true)
                downOnConfirm = event.x >= contentRight() && data.items.isNotEmpty()
                downContentX = event.x + scroll
                downHit = if (downOnConfirm || wasFlinging) ChipStripLayout.Hit.None
                else layoutSpec.hit(laid, downContentX)
                removeCallbacks(longPressRunnable)
                if (downHit is ChipStripLayout.Hit.Chip) {
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
                    if (!downOnConfirm && abs(dx) >= abs(dy)) {
                        when (downHit) {
                            is ChipStripLayout.Hit.Join -> {
                                dragging = true
                                parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            ChipStripLayout.Hit.None -> startScroll(event.x)
                            else -> Unit
                        }
                    }
                }
                // チップ上の横の動き: 短く速ければ離したときにフリック、長く・ゆっくりならスクロールに切り替える
                if (moved && !dragging && !scrolling && !longPressed && !downOnConfirm &&
                    (downHit is ChipStripLayout.Hit.Chip || downHit is ChipStripLayout.Hit.Cut) &&
                    abs(dx) > touchSlop && abs(dx) >= abs(dy) &&
                    ChipStripLayout.chipSwipeIsScroll(
                        absDx = abs(dx),
                        elapsedMs = event.eventTime - downTime,
                        canScroll = maxScroll() > 0f,
                        scrollDistance = scrollStartDistance,
                        flickMaxMs = FLICK_MAX_MS,
                    )
                ) {
                    startScroll(event.x)
                }
                if (scrolling) {
                    scroll = (scrollAnchor - (event.x - scrollAnchorX)).coerceIn(0f, maxScroll())
                    invalidate()
                    return true
                }
                if (dragging) {
                    dragX = event.x
                    updateDragDelta()
                    if (autoScrollSpeed() != 0f) {
                        removeCallbacks(autoScrollRunnable)
                        lastAutoScrollFrame = System.nanoTime()
                        postOnAnimation(autoScrollRunnable)
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                removeCallbacks(autoScrollRunnable)
                if (downOnConfirm) {
                    if (!moved && event.x >= contentRight()) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        listener?.onConfirm()
                    }
                    return true
                }
                if (longPressed) return true
                if (scrolling) {
                    scrolling = false
                    fling()
                    return true
                }
                if (dragging) {
                    dragging = false
                    invalidate()
                    val hit = downHit as ChipStripLayout.Hit.Join
                    if (dragDelta != 0) listener?.onBoundaryDrag(hit.index, dragDelta)
                    return true
                }
                val dx = event.x - downX
                val dy = event.y - downY
                if (moved) {
                    val index = when (val hit = downHit) {
                        is ChipStripLayout.Hit.Chip -> hit.index
                        is ChipStripLayout.Hit.Cut -> hit.index
                        else -> -1
                    }
                    if (index >= 0 && abs(dx) >= flickDistance && abs(dx) > abs(dy)) {
                        listener?.onFlick(index, dx > 0)
                    }
                    return true
                }
                when (val hit = downHit) {
                    is ChipStripLayout.Hit.Chip -> listener?.onChipTap(hit.index)
                    is ChipStripLayout.Hit.Join -> listener?.onJoin(hit.index)
                    is ChipStripLayout.Hit.Cut -> listener?.onCut(hit.gapIndex)
                    ChipStripLayout.Hit.None -> Unit
                }
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                removeCallbacks(autoScrollRunnable)
                scrolling = false
                if (dragging) {
                    dragging = false
                    invalidate()
                }
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

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        removeCallbacks(autoScrollRunnable)
        scroller.forceFinished(true)
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDetachedFromWindow()
    }

    companion object {
        const val MIN_TEXT_SCALE = 0.7f
        const val BUBBLE_TEXT_SCALE = 1.3f
        /** これより長く横に動かし続けるとフリックではなくスクロール（ms）。 */
        const val FLICK_MAX_MS = 220L
    }
}

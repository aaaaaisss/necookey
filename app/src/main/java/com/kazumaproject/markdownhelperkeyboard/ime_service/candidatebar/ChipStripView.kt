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
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripData
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 文節チップ候補欄の上段（チップ列）。描画とジェスチャだけで、状態は持たない（[setData] で受け取り、操作は [Listener] へ）。
 *
 * - 左端の小さい確定ボタン = 確定キーと同じ（いま見えている表記で確定）。
 * - タップ = 選択 / 選択解除。チップ間（広めの当たり判定）のタップ = くっつける。
 * - 長押し = 押した文字の位置でそのチップを切る（表記の文字位置を読みの位置に割り当てる。[ChipStripLayout.cutOffsetForLabel]）。
 * - 区切り（チップ間）に指を置いて左右に滑らせる = 境界を 1 字ずつ吸い付かせて動かし、離したところで確定。動かしている間は
 *   指の位置に境界前後のかなを拡大したバブルを出す。
 * - チップ上の左右フリック = 境界を 1 字ずらす（右 = 伸ばす、左 = 縮める。区切りドラッグのフォールバック）。
 * - 展開中（旧操作。モデルに残っている）の文字の間のタップ = 切る。
 * はみ出したときは選択・展開中のチップ（無ければ右端）が見える位置に寄せる。文字は最小 [MIN_TEXT_SCALE] 倍まで縮める。
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
    /** 左端の確定ボタンの幅（チップ列はこの右から並べる。スクロールしない）。 */
    private val confirmWidth = 34f * density
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

    private var backgroundFill = Color.TRANSPARENT
    private var chipFill = Color.LTGRAY
    private var chipText = Color.BLACK
    private var selectedFill = Color.BLUE
    private var selectedText = Color.WHITE

    // ジェスチャ
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val flickDistance = 20f * density
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
        val labelIndex = labelIndexAt(item.label, downX - confirmWidth + scroll - box.left - layoutSpec.chipPadding)
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
        data = newData
        dragging = false
        relayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun relayout() {
        val available = (width.toFloat() - confirmWidth).coerceAtLeast(0f)
        textPaint.textSize = baseTextSize
        var result = layoutSpec.layout(data) { textPaint.measureText(it) }
        if (available > 0f && result.width > available) {
            val scale = (available / result.width).coerceAtLeast(MIN_TEXT_SCALE)
            textPaint.textSize = baseTextSize * scale
            result = layoutSpec.layout(data) { textPaint.measureText(it) }
        }
        laid = result
        scroll = layoutSpec.scrollFor(result, data, available, scroll)
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
        canvas.clipRect(confirmWidth, 0f, width.toFloat(), h)
        canvas.translate(confirmWidth - scroll, 0f)
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

    /** 左端の確定ボタン（丸の中にチェック）。 */
    private fun drawConfirm(canvas: Canvas, h: Float, vPad: Float) {
        val cx = confirmWidth / 2f + 2f * density
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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                longPressed = false
                dragging = false
                dragDelta = 0
                downOnConfirm = event.x < confirmWidth && data.items.isNotEmpty()
                downHit = if (downOnConfirm) ChipStripLayout.Hit.None
                else layoutSpec.hit(laid, event.x - confirmWidth + scroll)
                removeCallbacks(longPressRunnable)
                if (downHit is ChipStripLayout.Hit.Chip) {
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!moved && (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)) {
                    moved = true
                    removeCallbacks(longPressRunnable)
                    if (downHit is ChipStripLayout.Hit.Join && abs(event.x - downX) >= abs(event.y - downY)) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                if (dragging) {
                    val hit = downHit as ChipStripLayout.Hit.Join
                    val left = data.items.getOrNull(hit.index)?.reading.orEmpty()
                    val right = data.items.getOrNull(hit.index + 1)?.reading.orEmpty()
                    val step = textPaint.measureText("あ").coerceAtLeast(8f * density)
                    val delta = ChipStripLayout.clampDelta(left.length, right.length, ((event.x - downX) / step).roundToInt())
                    dragX = event.x
                    if (delta != dragDelta) {
                        dragDelta = delta
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (downOnConfirm) {
                    if (!moved && event.x < confirmWidth) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        listener?.onConfirm()
                    }
                    return true
                }
                if (longPressed) return true
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
                if (dragging) {
                    dragging = false
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    companion object {
        const val MIN_TEXT_SCALE = 0.7f
        const val BUBBLE_TEXT_SCALE = 1.3f
    }
}

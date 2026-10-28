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

/**
 * 文節チップ候補欄の上段（チップ列）。描画とジェスチャだけで、状態は持たない（[setData] で受け取り、操作は [Listener] へ）。
 *
 * - タップ = 選択 / 選択解除。チップ間（広めの当たり判定）のタップ = くっつける。
 * - 長押し = かな 1 文字ずつに展開。展開中の文字の間をタップ = 切る。
 * - チップ上の左右フリック = 境界を 1 字ずらす（右 = 伸ばす、左 = 縮める）。
 * はみ出したときは選択・展開中のチップ（無ければ右端）が見える位置に寄せる。文字は最小 [MIN_TEXT_SCALE] 倍まで縮める。
 */
class ChipStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onChipTap(index: Int)
        fun onChipLongPress(index: Int)
        fun onJoin(index: Int)
        fun onCut(gapIndex: Int)
        fun onFlick(index: Int, toRight: Boolean)
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val layoutSpec = ChipStripLayout(
        chipPadding = 10f * density,
        gap = 6f * density,
        letterPadding = 6f * density,
        joinSlop = 12f * density,
    )
    private val baseTextSize = 17f * density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = baseTextSize }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f * density }
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
    private var moved = false
    private var longPressed = false
    private val longPressRunnable = Runnable {
        val hit = downHit
        if (!moved && hit is ChipStripLayout.Hit.Chip) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener?.onChipLongPress(hit.index)
        }
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
        relayout()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayout()
    }

    private fun relayout() {
        val available = width.toFloat()
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (Color.alpha(backgroundFill) != 0) canvas.drawColor(backgroundFill)
        if (data.items.isEmpty()) return
        val h = height.toFloat()
        val vPad = (h * 0.14f).coerceAtMost(8f * density)
        val radius = (h - vPad * 2f) / 2f
        val baseline = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.save()
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
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                longPressed = false
                downHit = layoutSpec.hit(laid, event.x + scroll)
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
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (longPressed) return true
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
    }
}

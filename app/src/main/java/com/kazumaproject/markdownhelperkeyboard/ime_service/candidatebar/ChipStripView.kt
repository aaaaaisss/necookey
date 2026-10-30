package com.kazumaproject.markdownhelperkeyboard.ime_service.candidatebar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import android.widget.PopupWindow
import androidx.core.graphics.ColorUtils
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripData
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ChipStripItem
import kotlin.math.abs
import kotlin.math.roundToInt
import timber.log.Timber

/**
 * 文節チップ候補欄の上段（チップ列）。描画とジェスチャだけで、状態は持たない（[setData] で受け取り、操作は [Listener] へ）。
 *
 * - 右端の小さい確定ボタン = 確定キーと同じ（いま見えている表記で確定）。
 * - タップ = 選択 / 選択解除。チップ間（広めの当たり判定）のタップ = くっつける。
 * - 長押し = そのチップをかな 1 文字ずつに展開し、指を置いたまま左右に滑らせると文字の間の「切るカーソル」が動く
 *   （語の境目に吸い付く磁石つき、動くたびに軽い振動。[ChipStripLayout.snapCutGap]）。指の上（候補欄の外）に読みと
 *   カーソルを拡大したバブル（例: 「とうきょう｜と」）を出し、離したところで切る。チップの両端・外で離すと取り消し（展開を閉じる）。
 * - 区切り（チップ間）に指を置いて左右に滑らせる = 境界を 1 字ずつ吸い付かせて動かし、離したところで確定。動かしている間は
 *   指の位置に境界前後のかなを拡大したバブルを出す。
 * - チップ上の左右フリック = 境界を 1 字ずらす（右 = 伸ばす、左 = 縮める。区切りドラッグのフォールバック）。
 * - 展開中の文字の間のタップ = 切る（展開は長押しの間だけなので、ふつうは使わない）。
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
        /** 長押し: チップ [index] をかな 1 文字ずつに展開する（切るカーソルの始まり。View は先に自分で展開して見せる）。 */
        fun onLongPressExpand(index: Int)
        /** 長押しのまま離した: チップ [index] を読みの [offset]（チップ内の位置、1..読みの長さ−1）で切る。 */
        fun onLongPressCut(index: Int, offset: Int)
        /** 長押しのまま切れない位置（チップの両端・外）で離した・取り消された: 展開を閉じる。 */
        fun onCutCancel(index: Int)
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
            if (!dragging && !cutting) return
            val now = System.nanoTime()
            val dt = ((now - lastAutoScrollFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastAutoScrollFrame = now
            val speed = autoScrollSpeed()
            if (speed == 0f) return
            val before = scroll
            scroll = (scroll + speed * dt).coerceIn(0f, maxScroll())
            if (scroll != before) {
                userScrolled = true
                if (cutting) updateCutCursor(haptic = true) else updateDragDelta()
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
    /** 長押しの切るカーソル中か・そのチップ・いまの切る位置（文字の境目の番号、null = 切らない）・指の x。 */
    private var cutting = false
    private var cutIndex = -1
    private var cutGap: Int? = null
    private var cutX = 0f
    private var cutPopup: PopupWindow? = null
    private var cutBubbleView: CutBubbleView? = null
    /** バブルを候補欄の外（指の上）に出せたか。出せないときは上段の中に描く。 */
    private var cutPopupShown = false
    private val longPressRunnable = Runnable {
        val hit = downHit
        if (moved || hit !is ChipStripLayout.Hit.Chip) return@Runnable
        val item = data.items.getOrNull(hit.index) ?: return@Runnable
        val letters = item.letters.ifEmpty { ChipStripLayout.codePoints(item.reading) }
        if (letters.size < 2) return@Runnable // 1 字のチップは切れない
        longPressed = true
        cutting = true
        cutIndex = hit.index
        cutGap = null
        cutX = downX
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        parent?.requestDisallowInterceptTouchEvent(true)
        // モデル（再変換の Mutex に並ぶ）を待たずに自分で展開して見せる。モデルの結果は同じ表示になる。
        if (!item.expanded) {
            data = withLocalExpansion(data, hit.index)
            relayout(reveal = false, followEnd = false)
        }
        listener?.onLongPressExpand(hit.index)
        updateCutCursor(haptic = false)
        invalidate()
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
        var incoming = newData
        if (cutting) {
            // 切るカーソル中: 同じチップ（読み）が残っていれば続ける（まだ展開していない更新なら自分で展開したまま）。
            val oldItem = old.items.getOrNull(cutIndex)
            val newItem = newData.items.getOrNull(cutIndex)
            if (oldItem != null && newItem != null && oldItem.reading == newItem.reading) {
                if (!newItem.expanded) incoming = withLocalExpansion(newData, cutIndex)
            } else {
                endCut(notify = false)
            }
            if (incoming == data) return
        }
        data = incoming
        dragging = false
        if (!cutting) removeCallbacks(autoScrollRunnable)
        val readingChanged = old.items.joinToString("") { it.reading } != incoming.items.joinToString("") { it.reading }
        if (readingChanged) userScrolled = false
        // 選択・展開中のチップ（とその表記）が変わったときだけ寄せる。手でスクロールした位置は、それ以外の更新では保つ。
        // 切るカーソル中は指の下の文字を動かさない。
        val reveal = !cutting && (readingChanged || focusKey(old) != focusKey(incoming))
        if (reveal || !userScrolled) scroller.forceFinished(true)
        relayout(reveal = reveal, followEnd = !userScrolled && !cutting)
        if (cutting) updateCutCursor(haptic = false)
        invalidate()
    }

    /** [d] のチップ [index] をかな 1 文字ずつに展開した表示（presenter の展開と同じ形）。 */
    private fun withLocalExpansion(d: ChipStripData, index: Int): ChipStripData {
        val item = d.items.getOrNull(index) ?: return d
        if (item.expanded) return d
        val letters = ChipStripLayout.codePoints(item.reading)
        if (letters.size < 2) return d
        return ChipStripData(d.items.mapIndexed { i, it -> if (i == index) it.copy(letters = letters) else it.copy(letters = emptyList()) })
    }

    /** 指の位置（[cutX]）から切るカーソルを決める（語の境目に吸い付く）。変わったら軽い振動とバブルの更新。 */
    private fun updateCutCursor(haptic: Boolean) {
        val item = data.items.getOrNull(cutIndex)
        val boxes = laid.letters.getOrNull(cutIndex).orEmpty()
        val gap = if (item == null || boxes.size < 2 || boxes.size != item.letters.size) null else {
            val xs = ArrayList<Float>(boxes.size + 1)
            xs += boxes.first().left
            boxes.forEach { xs += it.right }
            val step = (boxes.last().right - boxes.first().left) / boxes.size
            ChipStripLayout.snapCutGap(
                boundaryX = xs,
                wordGaps = ChipStripLayout.wordGapIndices(item.letters, item.wordBoundaries),
                x = cutX + scroll,
                snapBonus = step * SNAP_BONUS_CHARS,
            )
        }
        val changed = gap != cutGap
        cutGap = gap
        if (changed && haptic) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        showCutBubble()
    }

    /** 切るカーソルを終える。[notify] = true なら切る（位置があれば）か取り消しを [Listener] へ。 */
    private fun endCut(notify: Boolean) {
        if (!cutting) return
        val index = cutIndex
        val gap = cutGap
        val letters = data.items.getOrNull(index)?.letters.orEmpty()
        cutting = false
        cutIndex = -1
        cutGap = null
        removeCallbacks(autoScrollRunnable)
        hideCutBubble()
        if (!notify) return
        if (gap != null && gap in 1 until letters.size) {
            listener?.onLongPressCut(index, ChipStripLayout.gapOffset(letters, gap))
        } else {
            // 取り消し: モデルを待たずに閉じて見せる
            data = ChipStripData(data.items.map { if (it.expanded) it.copy(letters = emptyList()) else it })
            relayout(reveal = false, followEnd = false)
            listener?.onCutCancel(index)
        }
        invalidate()
    }

    private fun cutBubbleText(): String {
        val item = data.items.getOrNull(cutIndex) ?: return ""
        val letters = item.letters.ifEmpty { ChipStripLayout.codePoints(item.reading) }
        return ChipStripLayout.cutPreview(letters, cutGap)
    }

    /** 指の上（候補欄の外）にバブルを出す・動かす。出せなければ上段の中に描く（[onDraw]）。 */
    private fun showCutBubble() {
        val text = cutBubbleText()
        val bubble = cutBubbleView ?: CutBubbleView(context).also { cutBubbleView = it }
        bubble.set(text, cutGap != null, selectedFill, selectedText, baseTextSize * BUBBLE_TEXT_SCALE)
        val (w, h) = bubble.preferredSize()
        cutPopupShown = runCatching {
            if (windowToken == null || !isAttachedToWindow) return@runCatching false
            val loc = IntArray(2)
            getLocationInWindow(loc)
            val x = (loc[0] + cutX - w / 2f).roundToInt()
            val y = loc[1] - h - (6f * density).roundToInt()
            val popup = cutPopup ?: PopupWindow(bubble, w, h, false).apply {
                isTouchable = false
                isFocusable = false
                isClippingEnabled = false
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                animationStyle = 0
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            }.also { cutPopup = it }
            if (popup.isShowing) popup.update(x, y, w, h)
            else {
                popup.width = w
                popup.height = h
                popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
            }
            bubble.invalidate()
            popup.isShowing
        }.getOrElse { e ->
            Timber.w(e, "chip cut bubble popup failed")
            false
        }
        invalidate()
    }

    private fun hideCutBubble() {
        runCatching { cutPopup?.dismiss() }
        cutPopupShown = false
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
        x = if (cutting) cutX else dragX,
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
        if (cutting) drawCutCursor(canvas, h)
        canvas.restore()
        if (dragging) drawBubble(canvas, h)
        if (cutting && !cutPopupShown) drawInlineCutBubble(canvas, h)
    }

    /** 切るカーソル: 展開中の文字の間に太い縦線（チップ列の座標で描く）。 */
    private fun drawCutCursor(canvas: Canvas, h: Float) {
        val gap = cutGap ?: return
        val boxes = laid.letters.getOrNull(cutIndex) ?: return
        val x = boxes.getOrNull(gap - 1)?.right ?: return
        val item = data.items.getOrNull(cutIndex) ?: return
        linePaint.color = if (item.selected) selectedText else selectedFill
        val sw = linePaint.strokeWidth
        linePaint.strokeWidth = 3f * density
        canvas.drawLine(x, 2f * density, x, h - 2f * density, linePaint)
        linePaint.strokeWidth = sw
    }

    /** バブルを候補欄の外に出せなかったとき: 上段の中、指の位置に描く。 */
    private fun drawInlineCutBubble(canvas: Canvas, h: Float) {
        val text = cutBubbleText()
        bubblePaint.textSize = baseTextSize * BUBBLE_TEXT_SCALE
        val w = bubblePaint.measureText(text) + 16f * density
        val bh = (h - 2f * density).coerceAtMost(bubblePaint.textSize * 1.6f)
        val l = (cutX - w / 2f).coerceIn(0f, (width - w).coerceAtLeast(0f))
        rect.set(l, (h - bh) / 2f, l + w, (h + bh) / 2f)
        fillPaint.color = selectedFill
        canvas.drawRoundRect(rect, bh / 2f, bh / 2f, fillPaint)
        bubblePaint.color = if (cutGap != null) selectedText else ColorUtils.setAlphaComponent(selectedText, 0x88)
        val baseline = h / 2f - (bubblePaint.descent() + bubblePaint.ascent()) / 2f
        canvas.drawText(text, l + 8f * density, baseline, bubblePaint)
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
                if (cutting) endCut(notify = true)
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
                if (cutting) {
                    cutX = event.x
                    updateCutCursor(haptic = true)
                    if (ChipStripLayout.edgeAutoScrollSpeed(cutX, 0f, contentRight(), autoScrollEdge, autoScrollMaxSpeed) != 0f) {
                        removeCallbacks(autoScrollRunnable)
                        lastAutoScrollFrame = System.nanoTime()
                        postOnAnimation(autoScrollRunnable)
                    }
                    invalidate()
                    return true
                }
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
                if (cutting) {
                    cutX = event.x
                    updateCutCursor(haptic = false)
                    endCut(notify = true)
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
                if (cutting) endCut(notify = true) // 位置が残っていても取り消しにする

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
        cutting = false
        hideCutBubble()
        cutPopup = null
        removeCallbacks(autoScrollRunnable)
        scroller.forceFinished(true)
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDetachedFromWindow()
    }

    companion object {
        const val MIN_TEXT_SCALE = 0.7f
        const val BUBBLE_TEXT_SCALE = 1.3f
        /** 切るカーソルが語の境目に吸い付く幅（字数。[ChipStripLayout.snapCutGap] の snapBonus = 字幅 × これ）。 */
        const val SNAP_BONUS_CHARS = 0.6f
        /** これより長く横に動かし続けるとフリックではなくスクロール（ms）。 */
        const val FLICK_MAX_MS = 220L
    }
}

/** 切るカーソルのバブル（候補欄の外の PopupWindow に載せる）。読みとカーソル（｜）を拡大して出す。 */
private class CutBubbleView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var text = ""
    private var valid = true
    private var textColor = Color.WHITE

    fun set(text: String, valid: Boolean, background: Int, textColor: Int, textSize: Float) {
        this.text = text
        this.valid = valid
        this.textColor = textColor
        fill.color = background
        paint.textSize = textSize
    }

    fun preferredSize(): Pair<Int, Int> {
        val w = paint.measureText(text) + 20f * density
        val h = paint.textSize * 1.8f
        return w.roundToInt().coerceAtLeast(1) to h.roundToInt().coerceAtLeast(1)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        rect.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, fill)
        paint.color = if (valid) textColor else ColorUtils.setAlphaComponent(textColor, 0x88)
        val baseline = h / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, (width - paint.measureText(text)) / 2f, baseline, paint)
    }
}

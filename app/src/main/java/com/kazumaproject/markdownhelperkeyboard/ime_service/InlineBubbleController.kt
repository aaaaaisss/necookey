package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BubblePosition
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BubbleRequest
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.InlineChoices
import timber.log.Timber

/**
 * Small floating bubbles drawn over the editor (inline choices prototype). Each bubble is its own
 * non-focusable PopupWindow attached to the IME window, placed in screen coordinates from
 * CursorAnchorInfo. Positions are corrected after the first layout pass, because how a sub-window
 * of the IME window interprets showAtLocation offsets differs between devices.
 */
internal class InlineBubbleController(private val context: Context) {

    /** One bubble. [anchorX]/[anchorY] = left/top of the word in screen px. */
    data class Bubble(
        val labels: List<String>,
        /** Label drawn as the current choice (-1 = none). */
        val currentIndex: Int,
        val anchorX: Float,
        val anchorY: Float,
        val onTap: (Int) -> Unit,
    )

    data class Colors(val background: Int, val text: Int, val current: Int)

    private val popups = ArrayList<PopupWindow>()
    private var lastSignature: List<Any>? = null

    val isShowing: Boolean get() = popups.any { it.isShowing }

    fun dismissAll() {
        lastSignature = null
        popups.forEach { runCatching { it.dismiss() } }
        popups.clear()
    }

    /**
     * Shows exactly [bubbles] (dismissing the previous ones) above their words. [parent] is any
     * attached view of the IME window. Returns false when nothing could be shown.
     */
    fun show(parent: View, bubbles: List<Bubble>, colors: Colors): Boolean {
        if (bubbles.isEmpty()) {
            dismissAll()
            return false
        }
        val signature = bubbles.map { listOf(it.labels, it.currentIndex, it.anchorX.toInt(), it.anchorY.toInt()) }
        if (signature == lastSignature && isShowing) return true
        dismissAll()
        if (!parent.isAttachedToWindow || parent.windowToken == null) return false

        val views = bubbles.map { buildBubbleView(it, colors) }
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        views.forEach { it.measure(unspecified, unspecified) }
        val screenWidth = context.resources.displayMetrics.widthPixels
        val positions = InlineChoices.layout(
            bubbles.mapIndexed { i, b ->
                BubbleRequest(b.anchorX, b.anchorY, views[i].measuredWidth, views[i].measuredHeight)
            },
            screenWidth = screenWidth,
            gap = dp(GAP_DP),
            spacing = dp(SPACING_DP),
        )
        // Offset of the IME window on screen: showAtLocation offsets are taken relative to it on
        // most devices; the post-layout check below fixes the devices where they are absolute.
        val rootLocationOnScreen = IntArray(2)
        val rootLocationInWindow = IntArray(2)
        parent.getLocationOnScreen(rootLocationOnScreen)
        parent.getLocationInWindow(rootLocationInWindow)
        val windowLeft = rootLocationOnScreen[0] - rootLocationInWindow[0]
        val windowTop = rootLocationOnScreen[1] - rootLocationInWindow[1]

        var shown = false
        views.forEachIndexed { i, view ->
            val popup = PopupWindow(view, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
                isFocusable = false
                isTouchable = true
                isOutsideTouchable = false
                isClippingEnabled = false
                inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
                setBackgroundDrawable(null)
                elevation = dp(2).toFloat()
            }
            val target = positions[i]
            val ok = runCatching {
                popup.showAtLocation(parent, Gravity.TOP or Gravity.START, target.x - windowLeft, target.y - windowTop)
                true
            }.onFailure { Timber.w(it, "inline bubble: showAtLocation failed") }.getOrDefault(false)
            if (ok) {
                popups += popup
                shown = true
                view.post { correctPosition(popup, view, target, target.x - windowLeft, target.y - windowTop) }
            }
        }
        lastSignature = if (shown) signature else null
        return shown
    }

    private fun correctPosition(popup: PopupWindow, view: View, target: BubblePosition, x: Int, y: Int) {
        if (!popup.isShowing || !view.isAttachedToWindow) return
        val actual = IntArray(2)
        view.getLocationOnScreen(actual)
        val dx = target.x - actual[0]
        val dy = target.y - actual[1]
        if (kotlin.math.abs(dx) > 1 || kotlin.math.abs(dy) > 1) {
            runCatching { popup.update(x + dx, y + dy, -1, -1) }
        }
    }

    private fun buildBubbleView(bubble: Bubble, colors: Colors): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(colors.background)
                setStroke(dp(1), withAlpha(colors.text, 0x33))
            }
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        bubble.labels.forEachIndexed { index, label ->
            val isCurrent = index == bubble.currentIndex
            val chip = TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
                setTextColor(colors.text)
                if (isCurrent) setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                minWidth = dp(MIN_CHIP_DP)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                if (isCurrent) {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8).toFloat()
                        setColor(colors.current)
                    }
                }
                isClickable = true
                setOnClickListener { bubble.onTap(index) }
            }
            row.addView(chip)
        }
        return row
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val GAP_DP = 4
        private const val SPACING_DP = 6
        private const val MIN_CHIP_DP = 32
        private const val TEXT_SP = 15f
    }
}

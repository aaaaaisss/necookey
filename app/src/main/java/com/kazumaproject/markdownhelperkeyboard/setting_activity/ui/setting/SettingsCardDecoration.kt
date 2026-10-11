package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors

/**
 * 設定項目をカテゴリごとの「つながったカード」として描く（Android 14 の設定アプリ風）。
 * 各項目の間は細い隙間で区切り、グループの先頭と末尾だけ角を大きく丸める。
 * 入れ子の PreferenceScreen（詳細設定）は単独のカードにする。
 */
class SettingsCardDecoration(private val list: RecyclerView) : RecyclerView.ItemDecoration() {

    private val density = list.resources.displayMetrics.density
    private val sideInset = (16 * density).toInt()
    private val gap = (2 * density).toInt()
    private val groupGap = (12 * density).toInt()
    private val outerRadius = 24 * density
    private val innerRadius = 6 * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(
            list,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            MaterialColors.getColor(list, com.google.android.material.R.attr.colorSurfaceVariant),
        )
    }
    private val path = Path()
    private val rect = RectF()
    private val radii = FloatArray(8)

    private fun itemAt(parent: RecyclerView, position: Int): Preference? {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return null
        if (position < 0 || position >= adapter.itemCount) return null
        return adapter.getItem(position)
    }

    private fun isHeader(p: Preference?) = p is PreferenceCategory
    private fun isStandalone(p: Preference?) = p is PreferenceScreen

    private fun startsGroup(parent: RecyclerView, pos: Int): Boolean {
        val cur = itemAt(parent, pos)
        val prev = itemAt(parent, pos - 1)
        return prev == null || isHeader(prev) || isStandalone(prev) || isStandalone(cur)
    }

    private fun endsGroup(parent: RecyclerView, pos: Int): Boolean {
        val cur = itemAt(parent, pos)
        val next = itemAt(parent, pos + 1)
        return next == null || isHeader(next) || isStandalone(next) || isStandalone(cur)
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return
        val pref = itemAt(parent, pos) ?: return
        outRect.left = sideInset
        outRect.right = sideInset
        if (isHeader(pref)) {
            outRect.top = if (pos == 0) 0 else groupGap / 2
            return
        }
        outRect.top = if (startsGroup(parent, pos)) {
            if (pos == 0 || isStandalone(pref)) groupGap else 0
        } else {
            gap
        }
        if (pos == (parent.adapter?.itemCount ?: 0) - 1) outRect.bottom = groupGap * 2
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION) continue
            val pref = itemAt(parent, pos) ?: continue
            if (isHeader(pref)) continue
            val top = if (startsGroup(parent, pos)) outerRadius else innerRadius
            val bottom = if (endsGroup(parent, pos)) outerRadius else innerRadius
            radii[0] = top; radii[1] = top; radii[2] = top; radii[3] = top
            radii[4] = bottom; radii[5] = bottom; radii[6] = bottom; radii[7] = bottom
            rect.set(
                child.left.toFloat(),
                child.top + child.translationY,
                child.right.toFloat(),
                child.bottom + child.translationY,
            )
            path.reset()
            path.addRoundRect(rect, radii, Path.Direction.CW)
            paint.alpha = (child.alpha * 255).toInt()
            c.drawPath(path, paint)
        }
    }
}

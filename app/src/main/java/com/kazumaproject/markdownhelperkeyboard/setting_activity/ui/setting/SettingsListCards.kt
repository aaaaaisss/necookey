package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.kazumaproject.markdownhelperkeyboard.R

/**
 * 設定の一覧画面（ユーザー辞書・学習辞書・クリップボード・NG ワード・記号・配列・ライセンス）の行を、
 * 設定一覧と同じ「つながった角丸カード」にする（先頭と末尾だけ 20dp、間は 4dp・2dp の隙間、押下の波紋もカードの形の中）。
 *
 * 使い方: Adapter の onCreateViewHolder で [prepare]、RecyclerView に [SettingsListCardDecoration] を付ける。
 * 角の大きさ（先頭・末尾か）は描画のたびに今の位置から決めるので、追加・削除・並べ替えのあとも正しい。
 * 背景の Drawable は作り直さず形だけ変える（レイアウト中に requestLayout を起こさない）。
 */
internal object SettingsListCards {

    private const val STATE_UNSET = -1

    /** 行の背景を角丸カード（押下の波紋つき）にする。onCreateViewHolder で 1 回だけ呼ぶ。 */
    fun prepare(view: View, clickable: Boolean = true) {
        val fill = MaterialShapeDrawable(shape(view, first = false, last = false)).apply {
            fillColor = ColorStateList.valueOf(
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerHigh)
            )
        }
        view.background = if (clickable) {
            val ripple = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorControlHighlight)
            RippleDrawable(ColorStateList.valueOf(ripple), fill, null)
        } else {
            fill
        }
        view.setTag(R.id.settings_card_fill, fill)
        view.setTag(R.id.settings_card_state, STATE_UNSET)
    }

    internal fun update(view: View, first: Boolean, last: Boolean) {
        val fill = view.getTag(R.id.settings_card_fill) as? MaterialShapeDrawable ?: return
        val state = (if (first) 2 else 0) or (if (last) 1 else 0)
        if (view.getTag(R.id.settings_card_state) == state) return
        fill.shapeAppearanceModel = shape(view, first, last)
        view.setTag(R.id.settings_card_state, state)
    }

    private fun shape(view: View, first: Boolean, last: Boolean): ShapeAppearanceModel {
        val res = view.resources
        val outer = res.getDimension(R.dimen.settings_card_radius)
        val inner = res.getDimension(R.dimen.settings_card_inner_radius)
        val top = if (first) outer else inner
        val bottom = if (last) outer else inner
        return ShapeAppearanceModel.builder()
            .setTopLeftCorner(CornerFamily.ROUNDED, top)
            .setTopRightCorner(CornerFamily.ROUNDED, top)
            .setBottomLeftCorner(CornerFamily.ROUNDED, bottom)
            .setBottomRightCorner(CornerFamily.ROUNDED, bottom)
            .build()
    }

    /** 空の表示（アイコン＋短い 1 行）。layout/settings_empty_state.xml を include した View に使う。 */
    fun bindEmpty(root: View, @DrawableRes icon: Int, @StringRes text: Int) {
        root.findViewById<ImageView>(R.id.settings_empty_icon)?.setImageResource(icon)
        root.findViewById<TextView>(R.id.settings_empty_text)?.setText(text)
    }
}

/**
 * 一覧の行の左右 16dp・行の間 2dp・先頭の 8dp と、先頭／末尾の角（[SettingsListCards.update]）。
 * 下の余白は RecyclerView の paddingBottom（clipToPadding = false）で付ける（FAB のある画面は 96dp）。
 */
internal class SettingsListCardDecoration : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return
        val res = view.resources
        val side = res.getDimensionPixelSize(R.dimen.settings_screen_padding_h)
        outRect.left = side
        outRect.right = side
        outRect.top = if (pos == 0) {
            res.getDimensionPixelSize(R.dimen.settings_list_top)
        } else {
            res.getDimensionPixelSize(R.dimen.settings_card_gap)
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val count = parent.adapter?.itemCount ?: return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION) continue
            SettingsListCards.update(child, first = pos == 0, last = pos == count - 1)
        }
    }
}

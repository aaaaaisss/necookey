package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.kazumaproject.markdownhelperkeyboard.R

/**
 * 設定の行の背景。同じグループの行をつなげて 1 枚のカードに見せる（先頭と末尾だけ角を大きく、間は小さく）。
 * 押したときの波紋は角丸の形の中だけに出る（RippleDrawable のマスクは中身の形）。
 * 設定ホームの行と各画面の設定一覧で同じものを使う。
 */
internal object SettingsRowBackground {

    /** 毎回作り直す（PreferenceViewHolder.resetState が bind のたびに元の背景へ戻すため、使い回しの判定はしない）。 */
    fun apply(view: View, first: Boolean, last: Boolean, clickable: Boolean = true) {
        view.background = create(view, first, last, clickable)
    }

    private fun create(view: View, first: Boolean, last: Boolean, clickable: Boolean): Drawable {
        val res = view.resources
        val outer = res.getDimension(R.dimen.settings_card_radius)
        val inner = res.getDimension(R.dimen.settings_card_inner_radius)
        val top = if (first) outer else inner
        val bottom = if (last) outer else inner
        val shape = ShapeAppearanceModel.builder()
            .setTopLeftCorner(CornerFamily.ROUNDED, top)
            .setTopRightCorner(CornerFamily.ROUNDED, top)
            .setBottomLeftCorner(CornerFamily.ROUNDED, bottom)
            .setBottomRightCorner(CornerFamily.ROUNDED, bottom)
            .build()
        val fill = MaterialShapeDrawable(shape).apply {
            fillColor = ColorStateList.valueOf(
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerHigh)
            )
        }
        if (!clickable) return fill
        val ripple = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorControlHighlight)
        return RippleDrawable(ColorStateList.valueOf(ripple), fill, null)
    }
}

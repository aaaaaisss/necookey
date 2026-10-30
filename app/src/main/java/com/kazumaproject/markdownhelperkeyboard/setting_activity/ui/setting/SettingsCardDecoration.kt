package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.graphics.Rect
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R

/**
 * 設定一覧の見た目（設定ホームと同じ「つながったカード」）。
 * - 行: 左右 16dp の余白、同じグループの行の間は 2dp。背景は [SettingsRowBackground]（角丸＋押下の波紋）。
 * - 見出し（PreferenceCategory）: タイトルが無いグループは見出しを畳んで 16dp の間隔だけにする（区切り線は描かない）。
 * グループ = 見出しから次の見出しまで。
 */
internal class SettingsPreferenceAdapter(screen: PreferenceScreen) : PreferenceGroupAdapter(screen) {

    override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val pref = getItem(position) ?: return
        val view = holder.itemView
        if (pref is PreferenceCategory) {
            bindHeader(view, pref, position)
            return
        }
        val first = isGroupBoundary(getItem(position - 1).takeIf { position > 0 })
        val last = isGroupBoundary(getItem(position + 1).takeIf { position + 1 < itemCount })
        SettingsRowBackground.apply(view, first, last, clickable = pref.isSelectable)
    }

    private fun isGroupBoundary(neighbor: Preference?): Boolean =
        neighbor == null || neighbor is PreferenceCategory

    private fun bindHeader(view: View, pref: PreferenceCategory, position: Int) {
        val res = view.resources
        val hasTitle = !pref.title.isNullOrEmpty()
        view.findViewById<View>(android.R.id.title)?.visibility = if (hasTitle) View.VISIBLE else View.GONE
        val top = when {
            position == 0 -> res.getDimensionPixelSize(R.dimen.settings_list_top)
            hasTitle -> res.getDimensionPixelSize(R.dimen.settings_section_gap)
            else -> res.getDimensionPixelSize(R.dimen.settings_group_gap)
        }
        val bottom = if (hasTitle) res.getDimensionPixelSize(R.dimen.settings_section_label_bottom) else 0
        view.setPadding(view.paddingLeft, top, view.paddingRight, bottom)
        view.background = null
    }
}

/** 行の左右の余白と、同じグループの行の間の 2dp・一覧の最後の余白。 */
internal class SettingsCardDecoration : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        val res = view.resources
        val side = res.getDimensionPixelSize(R.dimen.settings_screen_padding_h)
        outRect.left = side
        outRect.right = side
        val pref = adapter.getItem(pos)
        if (pref !is PreferenceCategory && pos > 0 && adapter.getItem(pos - 1) !is PreferenceCategory) {
            outRect.top = res.getDimensionPixelSize(R.dimen.settings_card_gap)
        }
        if (pos == adapter.itemCount - 1) outRect.bottom = res.getDimensionPixelSize(R.dimen.settings_list_bottom)
    }
}

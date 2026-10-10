package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_size_setting.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.markdownhelperkeyboard.R

/**
 * Preview pages for the keyboard position/size editor. necookey only has the custom keyboard, so
 * there is a single page showing the default custom flick layout. The (former) tenkey size
 * preferences are what the custom keyboard uses for its size.
 */
class KeyboardViewPagerAdapter : RecyclerView.Adapter<KeyboardViewPagerAdapter.ViewHolder>() {

    companion object {
        const val TEN_KEY_PAGE_POSITION = 0
        /** No QWERTY page any more; kept only so callers compile (never selected). */
        const val QWERTY_PAGE_POSITION = -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_custom_keyboard_preview, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.preview?.let { preview ->
            runCatching {
                preview.setKeyboard(
                    KeyboardDefaultLayouts.createFlickKanaTemplateLayout(isDefaultKey = true)
                )
            }
            preview.setOnTouchListener { _, _ -> true }
        }
    }

    override fun getItemCount(): Int = 1

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val preview: FlickKeyboardView? = itemView.findViewById(R.id.custom_keyboard_preview)
    }
}

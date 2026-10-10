package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

fun Context.getKeyboardDisplayName(keyboardType: KeyboardType): String {
    return when (keyboardType) {
        KeyboardType.CUSTOM -> getString(R.string.keyboard_type_custom)
    }
}

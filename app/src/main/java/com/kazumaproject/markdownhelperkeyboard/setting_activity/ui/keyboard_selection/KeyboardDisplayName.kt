package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType

fun Context.getKeyboardDisplayName(keyboardType: KeyboardType): String {
    return when (keyboardType) {
        KeyboardType.TENKEY -> getString(R.string.keyboard_type_tenkey)
        KeyboardType.GOJUON -> getString(R.string.keyboard_type_gojuon)
        KeyboardType.QWERTY -> getString(R.string.keyboard_type_qwerty)
        KeyboardType.ROMAJI -> getString(R.string.keyboard_type_romaji)
        KeyboardType.SUMIRE -> getString(R.string.keyboard_type_sumire)
        KeyboardType.CUSTOM -> getString(R.string.keyboard_type_custom)
        KeyboardType.SPLIT -> getString(R.string.split_keyboard_title)
    }
}

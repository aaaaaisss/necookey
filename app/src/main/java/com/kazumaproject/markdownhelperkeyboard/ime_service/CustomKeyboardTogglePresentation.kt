package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView

/** Updates the custom keyboard's shift / direct-input / romaji toggle key icons. */
internal fun renderCustomKeyboardToggles(
    flickView: FlickKeyboardView,
    shift: CustomKeyboardShiftState,
    direct: Boolean,
    romaji: Boolean,
) {
    flickView.setKeyCharacterCase(shift.keyCharacterCase)
    flickView.updateKeyIconByAction(
        KeyAction.SwitchDirectMode,
        if (direct) {
            com.kazumaproject.core.R.drawable.language_japanese_kana_right_24px
        } else {
            com.kazumaproject.core.R.drawable.language_japanese_kana_left_24px
        }
    )
    flickView.updateKeyIconByAction(
        KeyAction.SwitchRomajiEnglish,
        if (romaji) {
            com.kazumaproject.core.R.drawable.language_japanese_kana_left_bold_24px
        } else {
            com.kazumaproject.core.R.drawable.language_japanese_kana_right_bold_24px
        }
    )
    flickView.updateKeyIconByAction(
        KeyAction.ShiftKey,
        when (shift) {
            CustomKeyboardShiftState.OFF ->
                com.kazumaproject.core.R.drawable.shift_24px
            CustomKeyboardShiftState.ONE_SHOT ->
                com.kazumaproject.core.R.drawable.shift_fill_24px
            CustomKeyboardShiftState.LOCKED ->
                com.kazumaproject.core.R.drawable.caps_lock
        }
    )
    flickView.updateKeyIconByAction(
        KeyAction.CapLockKey,
        if (shift == CustomKeyboardShiftState.LOCKED) {
            com.kazumaproject.core.R.drawable.caps_lock
        } else {
            com.kazumaproject.core.R.drawable.caps_lock_outline
        }
    )
}

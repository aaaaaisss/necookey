package com.kazumaproject.markdownhelperkeyboard.ime_service.state

/**
 * necookey: the custom keyboard is the only input keyboard (tenkey / QWERTY / gojuon / Sumire /
 * split keyboards were removed). Kept as an enum so stored keyboard-order JSON and the keyboard
 * switch list keep working; unknown legacy names are dropped when parsed.
 */
enum class KeyboardType {
    CUSTOM
}

val KeyboardType.isTenKeyFamily: Boolean
    get() = true

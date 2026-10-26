package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.domain.state.InputMode

fun resolveEmptySpaceForCurrentMode(
    isFlick: Boolean,
    currentInputMode: InputMode
): String {
    return if (currentInputMode == InputMode.ModeJapanese) {
        if (isFlick) " " else "　"
    } else {
        " "
    }
}

/** Returns the opposite of the width chosen for a normal space. */
internal fun oppositeSpace(normalSpaceIsFullWidth: Boolean): String =
    if (normalSpaceIsFullWidth) " " else "　"

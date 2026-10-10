package com.kazumaproject.core.domain.state

/** Which surface of the (custom) keyboard is active. */
sealed class TenKeyQWERTYMode {
    data object Sumire : TenKeyQWERTYMode()
    data object Custom : TenKeyQWERTYMode()
    data object Number : TenKeyQWERTYMode()
}

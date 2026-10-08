package com.kazumaproject.markdownhelperkeyboard.user_dictionary

object PosMapper {
    fun getContextIdForPos(posIndex: Int): Short = when (posIndex) {
        0 -> 1851
        1 -> 578
        2 -> 2194
        3 -> 12
        4 -> 29
        5 -> 433
        6 -> 2589
        7 -> 2591
        8 -> 2594
        9 -> 2642
        10 -> 2657
        11 -> 1
        else -> 1851
    }
}

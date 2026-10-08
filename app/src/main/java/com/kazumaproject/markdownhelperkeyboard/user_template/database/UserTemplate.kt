package com.kazumaproject.markdownhelperkeyboard.user_template.database

data class UserTemplate(
    val id: Int = 0,
    val word: String,
    val reading: String,
    val posIndex: Int,
    val posScore: Int,
)

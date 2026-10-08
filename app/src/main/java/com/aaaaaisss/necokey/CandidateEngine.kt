package com.aaaaaisss.necokey

class CandidateEngine {
    private val dictionary = mapOf(
        "きょう" to listOf("今日", "京都", "きょう"),
        "あした" to listOf("明日", "あした"),
        "こんにちは" to listOf("こんにちは", "今日は"),
        "ありがとう" to listOf("ありがとう", "有難う"),
        "にほん" to listOf("日本", "二本"),
        "がっこう" to listOf("学校", "がっこう"),
        "せいかつ" to listOf("生活", "せいかつ"),
        "くるま" to listOf("車", "くるま")
    )
    fun candidates(yomi: String): List<String> =
        dictionary[yomi] ?: listOf(yomi)
}

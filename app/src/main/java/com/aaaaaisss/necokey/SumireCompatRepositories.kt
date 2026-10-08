package com.kazumaproject.markdownhelperkeyboard.repository

data class UserWord(
    val word: String = "",
    val reading: String = "",
    val posIndex: Int = 0,
    val posScore: Int = 0,
)

class UserDictionaryRepository {
    @Volatile var conversionRevision: Long = 0
        private set

    suspend fun commonPrefixSearchInUserDict(@Suppress("UNUSED_PARAMETER") prefix: String): List<UserWord> = emptyList()
    suspend fun exactMatchesForConversion(@Suppress("UNUSED_PARAMETER") reading: String): List<UserWord> = emptyList()
}

data class LearnedWord(
    val input: String,
    val out: String,
    val leftId: Short? = null,
    val rightId: Short? = null,
    val score: Long = 0L,
)

class LearnRepository {
    @Volatile var conversionRevision: Long = 0
        private set

    suspend fun findCommonPrefixes(@Suppress("UNUSED_PARAMETER") input: String): List<LearnedWord> = emptyList()
    suspend fun findExactMatchesForConversion(@Suppress("UNUSED_PARAMETER") input: String): List<LearnedWord> = emptyList()
}

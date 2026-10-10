package com.kazumaproject.markdownhelperkeyboard.learning.nextword

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface NextWordDao {
    @Query(
        "SELECT * FROM next_word_table WHERE context IN (:contexts) " +
            "AND reading >= :readingPrefix AND reading < :readingPrefixUpperBound " +
            "AND length(reading) <= :maxReadingLength " +
            "ORDER BY length(context) DESC, usageCount DESC, lastUsedAt DESC LIMIT :limit"
    )
    suspend fun findByContexts(
        contexts: List<String>,
        readingPrefix: String,
        readingPrefixUpperBound: String,
        maxReadingLength: Int,
        limit: Int,
    ): List<NextWordEntity>

    @Query(
        "UPDATE next_word_table SET usageCount = usageCount + 1, lastUsedAt = :timestamp " +
            "WHERE context = :context AND reading = :reading AND output = :output"
    )
    suspend fun touch(context: String, reading: String, output: String, timestamp: Long): Int

    @Query(
        "INSERT OR IGNORE INTO next_word_table (context, reading, output, usageCount, lastUsedAt) " +
            "VALUES (:context, :reading, :output, 1, :timestamp)"
    )
    suspend fun insertIgnore(context: String, reading: String, output: String, timestamp: Long)

    @Transaction
    suspend fun upsertAll(entries: List<NextWordEntity>) {
        entries.forEach { entry ->
            if (touch(entry.context, entry.reading, entry.output, entry.lastUsedAt) == 0) {
                insertIgnore(entry.context, entry.reading, entry.output, entry.lastUsedAt)
            }
        }
    }

    @Query("DELETE FROM next_word_table WHERE reading = :reading AND output = :output")
    suspend fun deleteByReadingAndOutput(reading: String, output: String): Int

    @Query("DELETE FROM next_word_table")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM next_word_table")
    suspend fun count(): Int

    @Query("SELECT * FROM next_word_table ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<NextWordEntity>

    @Query("DELETE FROM next_word_table WHERE id = :id")
    suspend fun deleteById(id: Int): Int
}

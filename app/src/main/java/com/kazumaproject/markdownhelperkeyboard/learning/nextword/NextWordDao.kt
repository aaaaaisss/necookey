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

    /** 1 つの文脈の後続語（回数・新しさの順に多めに取り、並べ替えは [NextWordPolicy.rank]）。 */
    @Query(
        "SELECT * FROM next_word_table WHERE context = :context " +
            "AND reading >= :readingPrefix AND reading < :readingPrefixUpperBound " +
            "AND length(reading) <= :maxReadingLength " +
            "ORDER BY usageCount DESC, lastUsedAt DESC LIMIT :limit"
    )
    suspend fun findByContext(
        context: String,
        readingPrefix: String,
        readingPrefixUpperBound: String,
        maxReadingLength: Int,
        limit: Int,
    ): List<NextWordEntity>

    /** 新しい順に [keep] 件を残して古いものを消す。 */
    @Query(
        "DELETE FROM next_word_table WHERE id IN " +
            "(SELECT id FROM next_word_table ORDER BY lastUsedAt DESC LIMIT -1 OFFSET :keep)"
    )
    suspend fun pruneOldest(keep: Int): Int

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

    @Query(
        "SELECT * FROM next_word_table WHERE context = :context AND reading = :reading AND output = :output LIMIT 1"
    )
    suspend fun find(context: String, reading: String, output: String): NextWordEntity?

    @Query("UPDATE next_word_table SET usageCount = :usageCount, lastUsedAt = :lastUsedAt WHERE id = :id")
    suspend fun restore(id: Int, usageCount: Int, lastUsedAt: Long): Int

    @Query("DELETE FROM next_word_table WHERE context = :context AND reading = :reading AND output = :output")
    suspend fun deleteExact(context: String, reading: String, output: String): Int

    /** [upsertAll] と同じ書き込みをして、書く前の行（新しく入った行は null）を返す。 */
    @Transaction
    suspend fun upsertAllReturningPrevious(entries: List<NextWordEntity>): List<Pair<NextWordEntity, NextWordEntity?>> {
        val previous = entries.map { it to find(it.context, it.reading, it.output) }
        upsertAll(entries)
        return previous
    }

    /** [upsertAllReturningPrevious] の前の状態に戻す（新しく入った行は消す）。 */
    @Transaction
    suspend fun revert(previous: List<Pair<NextWordEntity, NextWordEntity?>>) {
        previous.forEach { (incoming, before) ->
            val id = before?.id
            if (before == null) {
                deleteExact(incoming.context, incoming.reading, incoming.output)
            } else if (id != null) {
                restore(id, before.usageCount, before.lastUsedAt)
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

package com.kazumaproject.markdownhelperkeyboard.learning.nextword

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntity(entity: NextWordEntity)

    /** 書くたびに回数は減衰後 + 1（[NextWordPolicy.reinforcedCount]）。 */
    @Transaction
    suspend fun upsertAll(entries: List<NextWordEntity>) {
        entries.forEach { entry ->
            val existing = find(entry.context, entry.reading, entry.output)
            val id = existing?.id
            if (existing == null || id == null) {
                insertEntity(entry.copy(usageCount = 1, id = null))
            } else {
                restore(
                    id = id,
                    usageCount = NextWordPolicy.reinforcedCount(existing, entry.lastUsedAt),
                    lastUsedAt = maxOf(existing.lastUsedAt, entry.lastUsedAt),
                )
            }
        }
    }

    /** 上位に出したのに使われなかった後続語を弱める／消す（[NextWordPolicy.ignored]）。書く前の行を返す。 */
    @Transaction
    suspend fun applyIgnoredReturningPrevious(
        entries: List<NextWordEntity>,
        now: Long,
    ): List<Pair<NextWordEntity, NextWordEntity?>> =
        entries.distinctBy { Triple(it.context, it.reading, it.output) }.mapNotNull { key ->
            val existing = find(key.context, key.reading, key.output) ?: return@mapNotNull null
            val id = existing.id ?: return@mapNotNull null
            val next = NextWordPolicy.ignored(existing, now)
            if (next == null) deleteById(id) else restore(id, next.usageCount, next.lastUsedAt)
            existing to existing
        }

    /** 忘れた後続語（[cutoff] より前に使われたもの）を消す。 */
    @Query("DELETE FROM next_word_table WHERE lastUsedAt < :cutoff")
    suspend fun deleteExpired(cutoff: Long): Int

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

    /** 書く前の状態に戻す（新しく入った行は消す、消した行は入れ直す）。リストの順に戻す。 */
    @Transaction
    suspend fun revert(previous: List<Pair<NextWordEntity, NextWordEntity?>>) {
        previous.forEach { (incoming, before) ->
            val id = before?.id
            if (before == null) {
                deleteExact(incoming.context, incoming.reading, incoming.output)
            } else if (id == null || restore(id, before.usageCount, before.lastUsedAt) == 0) {
                insertEntity(before) // 弱めて消した行は入れ直す
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

package com.kazumaproject.markdownhelperkeyboard.learning.nextword

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NextWordRepository @Inject constructor(
    private val dao: NextWordDao,
) {
    /** 前回の件数確認からの記録回数（件数上限の確認はたまにだけ）。 */
    private val recordsSincePrune = AtomicInteger(PRUNE_EVERY)

    /** 記録して、書く前の行を返す（確定直後の後退で [revert] に渡す）。 */
    suspend fun record(entries: List<NextWordEntity>): List<Pair<NextWordEntity, NextWordEntity?>> {
        if (entries.isEmpty()) return emptyList()
        val previous = dao.upsertAllReturningPrevious(entries)
        if (recordsSincePrune.incrementAndGet() >= PRUNE_EVERY) {
            recordsSincePrune.set(0)
            if (dao.count() > NextWordPolicy.MAX_ENTRIES) dao.pruneOldest(NextWordPolicy.MAX_ENTRIES)
        }
        return previous
    }

    /** 確定直後の後退: その確定で書いた後続語を書く前に戻す。 */
    suspend fun revert(previous: List<Pair<NextWordEntity, NextWordEntity?>>) {
        if (previous.isEmpty()) return
        dao.revert(previous)
    }

    /** Learned following words for [leftContext] whose reading starts with [readingPrefix]. */
    suspend fun lookup(
        leftContext: String,
        readingPrefix: String,
        limit: Int,
    ): List<NextWordEntity> {
        val contexts = NextWordPolicy.lookupContexts(leftContext)
        if (contexts.isEmpty() || limit <= 0) return emptyList()
        if (readingPrefix.length > NextWordPolicy.MAX_READING_LENGTH) return emptyList()
        // 文脈ごとに回数・新しさの順で多めに取り、回数（減衰つき）＋文脈の長さで並べ替える。
        val pool = contexts.flatMap { context ->
            dao.findByContext(
                context = context,
                readingPrefix = readingPrefix,
                readingPrefixUpperBound = readingPrefix + '\uFFFF',
                maxReadingLength = NextWordPolicy.MAX_READING_LENGTH,
                limit = limit * NextWordPolicy.POOL_FACTOR,
            )
        }
        return NextWordPolicy.rank(pool, System.currentTimeMillis(), limit)
    }

    suspend fun delete(reading: String, output: String): Int =
        dao.deleteByReadingAndOutput(reading, output)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun count(): Int = dao.count()

    /** Most recently used learned following words, for the 辞書 settings viewer. */
    suspend fun recent(limit: Int): List<NextWordEntity> = dao.recent(limit)

    suspend fun deleteById(id: Int): Int = dao.deleteById(id)

    private companion object {
        const val PRUNE_EVERY = 100
    }
}

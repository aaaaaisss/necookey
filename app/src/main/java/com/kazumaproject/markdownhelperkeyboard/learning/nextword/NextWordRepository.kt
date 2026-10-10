package com.kazumaproject.markdownhelperkeyboard.learning.nextword

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NextWordRepository @Inject constructor(
    private val dao: NextWordDao,
) {
    suspend fun record(entries: List<NextWordEntity>) {
        if (entries.isEmpty()) return
        dao.upsertAll(entries)
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
        return dao.findByContexts(
            contexts = contexts,
            readingPrefix = readingPrefix,
            readingPrefixUpperBound = readingPrefix + '\uFFFF',
            maxReadingLength = NextWordPolicy.MAX_READING_LENGTH,
            limit = limit,
        ).distinctBy { it.output }
    }

    suspend fun delete(reading: String, output: String): Int =
        dao.deleteByReadingAndOutput(reading, output)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun count(): Int = dao.count()

    /** Most recently used learned following words, for the 辞書 settings viewer. */
    suspend fun recent(limit: Int): List<NextWordEntity> = dao.recent(limit)

    suspend fun deleteById(id: Int): Int = dao.deleteById(id)
}

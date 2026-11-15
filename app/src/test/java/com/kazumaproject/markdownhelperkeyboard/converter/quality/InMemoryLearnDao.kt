package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnDao
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.model.LearnResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** 学習辞書（Room learn_table）のメモリ版。評価で記録を再生して学習を再現する（CorrectionRankEvalTest）。 */
internal class InMemoryLearnDao : LearnDao {
    private val entries = ArrayList<LearnEntity>()
    private var nextId = 1

    override suspend fun editEntry(id: Int, input: String, output: String, score: Int): Int {
        val i = entries.indexOfFirst { it.id == id }
        if (i < 0) return 0
        entries[i] = entries[i].copy(input = input, out = output, score = score)
        return 1
    }

    override suspend fun deleteById(id: Int) { entries.removeAll { it.id == id } }

    override suspend fun insertStrict(entry: LearnEntity) {
        check(entries.none { it.input == entry.input && it.out == entry.out }) { "duplicate" }
        entries.add(entry.copy(id = nextId++))
    }

    override suspend fun insert(learnData: LearnEntity) {
        if (entries.none { it.input == learnData.input && it.out == learnData.out }) entries.add(learnData.copy(id = nextId++))
    }

    override suspend fun findByInput(input: String): List<LearnResult>? =
        entries.filter { it.input == input }.map { LearnResult(it.out, it.score) }

    override suspend fun findByInputAndOutput(input: String, output: String): LearnEntity? =
        entries.firstOrNull { it.input == input && it.out == output }

    override suspend fun existsDuplicateForUpdate(input: String, output: String, excludeId: Int): Boolean =
        entries.any { it.input == input && it.out == output && it.id != excludeId }

    override fun all(): Flow<List<LearnEntity>> = flowOf(entries.toList())

    override suspend fun getAllSuspend(): List<LearnEntity> = entries.sortedBy { it.score }

    override suspend fun insertAll(learnDataList: List<LearnEntity>) { learnDataList.forEach { insert(it) } }

    override suspend fun predictiveSearchByInput(prefix: String, prefixUpperBound: String, limit: Int, maxReadingLength: Int): List<LearnEntity> =
        entries.filter { it.input.startsWith(prefix) && it.input.length <= maxReadingLength }
            .sortedWith(compareBy<LearnEntity> { if (it.input == prefix) 0 else 1 }.thenByDescending { it.lastUsedAt }.thenBy { it.score })
            .take(limit)

    override suspend fun findCommonPrefixes(searchTerm: String): List<LearnEntity> =
        entries.filter { searchTerm.startsWith(it.input) }.sortedByDescending { it.input.length }

    override suspend fun findByInputPrefix(prefix: String, prefixUpperBound: String): List<LearnEntity> =
        entries.filter { it.input.startsWith(prefix) }.sortedByDescending { it.input.length }

    override suspend fun updateLearnedData(learnData: LearnEntity) {
        val i = entries.indexOfFirst { it.id == learnData.id }
        if (i >= 0) entries[i] = learnData
    }

    override suspend fun deleteExpired(cutoff: Long): Int {
        val before = entries.size
        entries.removeAll { it.lastUsedAt in 1 until cutoff }
        return before - entries.size
    }

    override suspend fun delete(learnData: LearnEntity) { entries.removeAll { it.id == learnData.id } }

    override suspend fun deleteAll() = entries.clear()

    override suspend fun deleteByInput(input: String): Int {
        val before = entries.size
        entries.removeAll { it.input == input }
        return before - entries.size
    }

    override suspend fun findByOutputPrefix(outputPrefix: String, maxReadingLength: Int, limit: Int): List<LearnEntity> =
        entries.filter { it.out.startsWith(outputPrefix) && it.out.length > outputPrefix.length && it.input.length <= maxReadingLength }
            .sortedBy { it.score }.take(limit)

    override suspend fun deleteByInputAndOutput(input: String, output: String): Int {
        val before = entries.size
        entries.removeAll { it.input == input && it.out == output }
        return before - entries.size
    }
}

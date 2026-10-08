package com.kazumaproject.markdownhelperkeyboard.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UserWord(
    val word: String = "",
    val reading: String = "",
    val posIndex: Int = 0,
    val posScore: Int = 4000,
)

class UserDictionaryRepository(context: Context) {
    private val db = NecokeyDictionaryDatabase(context.applicationContext)

    @Volatile var conversionRevision: Long = 0
        private set

    suspend fun commonPrefixSearchInUserDict(prefix: String): List<UserWord> =
        withContext(Dispatchers.IO) { db.findUserWordsByCommonPrefix(prefix) }

    suspend fun exactMatchesForConversion(reading: String): List<UserWord> =
        withContext(Dispatchers.IO) { db.findUserWordsExact(reading) }

    suspend fun insert(word: String, reading: String, posIndex: Int = 0, posScore: Int = 4000) {
        withContext(Dispatchers.IO) { db.insertUserWord(UserWord(word, reading, posIndex, posScore)) }
        conversionRevision++
    }

    suspend fun delete(word: String, reading: String) {
        withContext(Dispatchers.IO) { db.deleteUserWord(word, reading) }
        conversionRevision++
    }
}

data class LearnedWord(
    val input: String,
    val out: String,
    val leftId: Short? = null,
    val rightId: Short? = null,
    val score: Long = 0L,
)

class LearnRepository(context: Context) {
    private val db = NecokeyDictionaryDatabase(context.applicationContext)

    @Volatile var conversionRevision: Long = 0
        private set

    suspend fun findCommonPrefixes(input: String): List<LearnedWord> =
        withContext(Dispatchers.IO) { db.findLearnedCommonPrefixes(input) }

    suspend fun findExactMatchesForConversion(input: String): List<LearnedWord> =
        withContext(Dispatchers.IO) { db.findLearnedExact(input) }

    suspend fun learn(input: String, output: String, score: Int = 2500) {
        if (input.isBlank() || output.isBlank() || input == output) return
        withContext(Dispatchers.IO) { db.learn(input, output, score) }
        conversionRevision++
    }
}

private class NecokeyDictionaryDatabase(context: Context) :
    SQLiteOpenHelper(context, "necookey_dictionary.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE user_word (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL,
                reading TEXT NOT NULL,
                posIndex INTEGER NOT NULL DEFAULT 0,
                posScore INTEGER NOT NULL DEFAULT 4000,
                UNIQUE(word, reading)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX user_word_reading_index ON user_word(reading)")
        db.execSQL("""
            CREATE TABLE learned_word (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                input TEXT NOT NULL,
                output TEXT NOT NULL,
                score INTEGER NOT NULL,
                usageCount INTEGER NOT NULL DEFAULT 1,
                lastUsedAt INTEGER NOT NULL DEFAULT 0,
                UNIQUE(input, output)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX learned_word_input_index ON learned_word(input)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun findUserWordsByCommonPrefix(input: String): List<UserWord> {
        if (input.isEmpty()) return emptyList()
        return readableDatabase.rawQuery(
            "SELECT word, reading, posIndex, posScore FROM user_word WHERE ? LIKE reading || '%' ORDER BY LENGTH(reading) DESC, posScore ASC",
            arrayOf(input)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(UserWord(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3)))
            }
        }
    }

    fun findUserWordsExact(reading: String): List<UserWord> {
        if (reading.isEmpty()) return emptyList()
        return readableDatabase.rawQuery(
            "SELECT word, reading, posIndex, posScore FROM user_word WHERE reading = ? ORDER BY posScore ASC, id DESC",
            arrayOf(reading)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(UserWord(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3)))
            }
        }
    }

    fun insertUserWord(word: UserWord) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO user_word(word, reading, posIndex, posScore) VALUES(?,?,?,?)",
            arrayOf(word.word, word.reading, word.posIndex, word.posScore)
        )
    }

    fun deleteUserWord(word: String, reading: String) {
        writableDatabase.execSQL("DELETE FROM user_word WHERE word = ? AND reading = ?", arrayOf(word, reading))
    }

    fun findLearnedCommonPrefixes(input: String): List<LearnedWord> {
        if (input.isEmpty()) return emptyList()
        return readableDatabase.rawQuery(
            "SELECT input, output, score FROM learned_word WHERE ? LIKE input || '%' ORDER BY LENGTH(input) DESC, score ASC, lastUsedAt DESC",
            arrayOf(input)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(LearnedWord(c.getString(0), c.getString(1), score = c.getLong(2)))
            }
        }
    }

    fun findLearnedExact(input: String): List<LearnedWord> {
        if (input.isEmpty()) return emptyList()
        return readableDatabase.rawQuery(
            "SELECT input, output, score FROM learned_word WHERE input = ? ORDER BY score ASC, lastUsedAt DESC",
            arrayOf(input)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(LearnedWord(c.getString(0), c.getString(1), score = c.getLong(2)))
            }
        }
    }

    fun learn(input: String, output: String, score: Int) {
        writableDatabase.execSQL("""
            INSERT INTO learned_word(input, output, score, usageCount, lastUsedAt)
            VALUES(?,?,?,1,?)
            ON CONFLICT(input, output) DO UPDATE SET
                score = MAX(0, MIN(learned_word.score, excluded.score) - 500),
                usageCount = learned_word.usageCount + 1,
                lastUsedAt = excluded.lastUsedAt
        """.trimIndent(), arrayOf(input, output, score, System.currentTimeMillis()))
    }
}
package com.kazumaproject.markdownhelperkeyboard.learning.nextword

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One learned "following word": after [context] (the tail of the text committed just before),
 * the user committed [output] for [reading].
 */
@Entity(
    tableName = "next_word_table",
    indices = [
        Index(value = ["context"], unique = false),
        Index(value = ["context", "reading", "output"], unique = true),
    ]
)
data class NextWordEntity(
    val context: String,
    val reading: String,
    val output: String,
    val usageCount: Int = 1,
    val lastUsedAt: Long = 0L,
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
)

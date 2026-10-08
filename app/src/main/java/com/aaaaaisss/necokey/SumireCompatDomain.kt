package com.kazumaproject.domain

import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.data.emoticon.Emoticon
import com.kazumaproject.data.emoticon.EmoticonCategory
import com.kazumaproject.data.symbol.Symbol
import com.kazumaproject.data.symbol.SymbolCategory

fun categorizeEmoji(@Suppress("UNUSED_PARAMETER") emoji: String): EmojiCategory = EmojiCategory.UNKNOWN

fun List<Emoji>.sortByEmojiCategory(): List<Emoji> = sortedBy { it.category.ordinal }

fun String.toEmoticonCategory(): EmoticonCategory = EmoticonCategory.UNKNOWN

fun String.toSymbolCategory(): SymbolCategory = SymbolCategory.GENERAL

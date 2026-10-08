package com.kazumaproject.data.emoji

data class Emoji(val symbol: String, val category: EmojiCategory)

enum class EmojiCategory {
    SMILEYS_EMOTION, PEOPLE_BODY, ANIMALS_NATURE, FOOD_DRINK, TRAVEL_PLACES,
    ACTIVITIES, OBJECTS, SYMBOLS, FLAGS, UNKNOWN
}

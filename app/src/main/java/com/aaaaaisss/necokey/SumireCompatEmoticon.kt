package com.kazumaproject.data.emoticon

data class Emoticon(val symbol: String, val category: EmoticonCategory)

enum class EmoticonCategory {
    SMILE, SWEAT, SURPRISE, SADNESS, DISPLEASURE, UNKNOWN
}

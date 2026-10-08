package com.aaaaaisss.necokey

object RomajiKana {
    private val table = linkedMapOf(
        "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ",
        "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ",
        "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ",
        "nya" to "にゃ", "nyu" to "にゅ", "nyo" to "にょ",
        "hya" to "ひゃ", "hyu" to "ひゅ", "hyo" to "ひょ",
        "mya" to "みゃ", "myu" to "みゅ", "myo" to "みょ",
        "rya" to "りゃ", "ryu" to "りゅ", "ryo" to "りょ",
        "gya" to "ぎゃ", "gyu" to "ぎゅ", "gyo" to "ぎょ",
        "bya" to "びゃ", "byu" to "びゅ", "byo" to "びょ",
        "pya" to "ぴゃ", "pyu" to "ぴゅ", "pyo" to "ぴょ",
        "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ",
        "tsu" to "つ", "shi" to "し", "chi" to "ち", "fu" to "ふ",
        "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お",
        "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
        "sa" to "さ", "su" to "す", "se" to "せ", "so" to "そ",
        "ta" to "た", "te" to "て", "to" to "と",
        "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
        "ha" to "は", "hi" to "ひ", "he" to "へ", "ho" to "ほ",
        "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
        "ya" to "や", "yu" to "ゆ", "yo" to "よ",
        "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
        "wa" to "わ", "wo" to "を", "nn" to "ん"
    )
    fun convert(input: String): String {
        var s = input.lowercase()
        val out = StringBuilder()
        while (s.isNotEmpty()) {
            var hit: String? = null
            for (key in table.keys.sortedByDescending { it.length }) {
                if (s.startsWith(key)) { hit = key; break }
            }
            if (hit != null) {
                out.append(table.getValue(hit)); s = s.drop(hit.length)
            } else {
                out.append(s.first()); s = s.drop(1)
            }
        }
        return out.toString()
    }
}

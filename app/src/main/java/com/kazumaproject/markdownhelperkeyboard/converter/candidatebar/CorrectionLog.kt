package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import java.io.File

/**
 * 変換ミスの記録（2026-11-05、端末だけ・送信なし）。チップ方式で確定した表記が最初に出した変換（1 位経路のチップ）と違うとき、
 * {時刻, 読み, 最初の表記, 確定した表記, 直し方, 画面文脈の加点} を 1 行にして files/[FILE_NAME] に最新 [MAX_ENTRIES] 件だけ残す。
 * アプリ名は残さない。パスワード欄・学習しない欄・シークレットでは記録しない（IMEService が判定）。設定「変換ミスの記録」で一覧・全部コピー・消去・オフ。
 */
object CorrectionLog {
    const val FILE_NAME = "correction_log.tsv"
    const val MAX_ENTRIES = 200
    const val PREF_KEY = "correction_log_preference"

    /** 直し方: 別の候補を選んだ / 区切りを変えた（切る・くっつける）/ かなに戻した。 */
    const val KIND_CANDIDATE = "候補"
    const val KIND_SEGMENT = "区切り"
    const val KIND_KANA = "かな"
    /** 下の候補一覧（予測・通常の候補）から選んだ（チップを使わずに確定）。 */
    const val KIND_LIST = "一覧"

    data class Entry(
        val time: Long,
        val reading: String,
        val shown: String,
        val chosen: String,
        val kind: String,
        val ctx: Boolean,
    ) {
        fun toLine(): String = listOf(time.toString(), clean(reading), clean(shown), clean(chosen), kind, if (ctx) "1" else "0")
            .joinToString("\t")

        /** コピー用の 1 行: 読み TAB 最初の表記 TAB 確定 TAB 直し方 TAB ctx=0/1。 */
        fun toCopyLine(): String = "${clean(reading)}\t${clean(shown)}\t${clean(chosen)}\t$kind\tctx=${if (ctx) 1 else 0}"

        companion object {
            fun parse(line: String): Entry? {
                val c = line.split('\t')
                if (c.size < 5) return null
                val t = c[0].toLongOrNull() ?: return null
                return Entry(t, c[1], c[2], c[3], c[4], c.getOrNull(5) == "1")
            }
        }
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    /**
     * 直し方（[initialBounds] / [initialText] = 最初に出したチップの区切りと表記、[bounds] / [text] = 確定するチップ）。
     * 表記が同じなら null（直していない）。区切りが違えば 区切り、確定がかなだけで最初がそうでなければ かな、ほかは 候補。
     */
    fun kindOf(initialBounds: List<Int>, initialText: String, bounds: List<Int>, text: String): String? {
        if (text == initialText || text.isEmpty()) return null
        if (initialBounds != bounds) return KIND_SEGMENT
        if (isKanaOnly(text) && !isKanaOnly(initialText)) return KIND_KANA
        return KIND_CANDIDATE
    }

    private fun isKanaOnly(s: String): Boolean = s.isNotEmpty() && s.all { it in '\u3041'..'\u309F' || it in '\u30A0'..'\u30FF' }

    // ---- ファイル（呼び出し側が IO スレッドで） ----

    private val lock = Any()

    fun append(file: File, entry: Entry) = synchronized(lock) {
        val lines = if (file.isFile) file.readLines().filter { it.isNotBlank() } else emptyList()
        val kept = (lines + entry.toLine()).takeLast(MAX_ENTRIES)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(kept.joinToString("\n", postfix = "\n"))
        if (!tmp.renameTo(file)) {
            file.writeText(kept.joinToString("\n", postfix = "\n"))
            tmp.delete()
        }
    }

    /** 新しい順。 */
    fun read(file: File): List<Entry> = synchronized(lock) {
        if (!file.isFile) emptyList() else file.readLines().mapNotNull(Entry::parse).asReversed()
    }

    fun clear(file: File) = synchronized(lock) { file.delete() }

    /** 全部コピーの文字列（新しい順、1 件 1 行）。 */
    fun copyText(entries: List<Entry>): String = entries.joinToString("\n") { it.toCopyLine() }
}

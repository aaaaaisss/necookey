package com.kazumaproject.markdownhelperkeyboard.converter.sumirelm

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * リランカー表（files/sumire_lm/）の読み込み・削除（設定「変換の表（リランカー）を読み込む」、2026-11-05）。
 * APK に表を入れない（CI は -PsumireLmTable=false）代わりに、ユーザーが選んだ sumire_lm_h56.zip を端末で展開する。
 * 既に files/sumire_lm/ にある表（以前の APK がアセットからコピーしたもの）はそのまま使える（IMEService の読み込み順は変えない）。
 * 展開後は [generation] を進め、IMEService が次の変換で読み直す（再起動不要）。Android 非依存（JVM テスト可）。
 */
object SumireLmTableStore {
    const val DIR_NAME = "sumire_lm"
    const val EXPECTED_SHA256 = "5b040b8f9401adcfefe19daf67e4717468b308fea0b9bc5e8003db68e57e8196"
    /** 1 ファイル・合計の上限（壊れた・別の zip で端末を埋めないため）。h56 は展開後 約 60 MB。 */
    const val MAX_ENTRY_BYTES = 200L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 400L * 1024 * 1024

    /** 表が入れ替わった回数（IMEService はこれが変わったらリランカーを読み直す）。 */
    @Volatile var generation: Int = 0
        private set

    fun dir(filesDir: File) = File(filesDir, DIR_NAME)

    /** 表が揃っていればその合計バイト数、無ければ null。 */
    fun installedBytes(filesDir: File): Long? {
        val d = dir(filesDir)
        val names = d.list()?.toList() ?: return null
        if (!CandidateRerankers.isCompleteTable(names)) return null
        return d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    class Result(val bytes: Long, val sha256: String) {
        val hashMatches: Boolean get() = sha256 == EXPECTED_SHA256
    }

    class InvalidTableException(message: String) : IOException(message)

    /**
     * zip [input]（全体 [totalBytes]、不明なら -1）を files/sumire_lm/ に展開する。一時ディレクトリに展開し、表として揃っている
     * （meta.tsv など、[CandidateRerankers.isCompleteTable]）ときだけ入れ替える。zip-slip（../ や絶対パス）は拒否。
     * [progress] は 0..100（全体が分かるとき）。揃っていなければ [InvalidTableException]。
     */
    fun importZip(filesDir: File, input: InputStream, totalBytes: Long, progress: (Int) -> Unit = {}): Result {
        val digest = MessageDigest.getInstance("SHA-256")
        val tmp = File(filesDir, "$DIR_NAME.import")
        tmp.deleteRecursively()
        tmp.mkdirs()
        val tmpCanon = tmp.canonicalFile
        var read = 0L
        var lastPct = -1
        val counting = object : FilterInputStream(input) {
            override fun read(): Int {
                val b = super.read()
                if (b >= 0) count(1, byteArrayOf(b.toByte()), 0) 
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n > 0) count(n, b, off)
                return n
            }

            private fun count(n: Int, b: ByteArray, off: Int) {
                digest.update(b, off, n)
                read += n
                if (totalBytes > 0) {
                    val pct = (read * 100 / totalBytes).toInt().coerceIn(0, 100)
                    if (pct != lastPct) {
                        lastPct = pct
                        progress(pct)
                    }
                }
            }
        }
        var total = 0L
        try {
            ZipInputStream(counting).use { zip ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name.replace('\\', '/')
                    if (name.isEmpty() || name.startsWith("/") || name.split('/').any { it == ".." }) {
                        throw InvalidTableException("bad entry name: ${e.name}")
                    }
                    val out = File(tmp, name).canonicalFile
                    if (!out.path.startsWith(tmpCanon.path + File.separator)) throw InvalidTableException("bad entry path: ${e.name}")
                    if (e.isDirectory) {
                        out.mkdirs()
                        continue
                    }
                    out.parentFile?.mkdirs()
                    var size = 0L
                    out.outputStream().use { os ->
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            size += n
                            total += n
                            if (size > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) throw InvalidTableException("table too large")
                            os.write(buf, 0, n)
                        }
                    }
                }
                // 末尾まで読み切って sha256 を全体にかける
                val rest = ByteArray(1 shl 16)
                while (counting.read(rest) >= 0) Unit
            }
            // zip の中が sumire_lm/ の下に入っている形も受け付ける
            val root = if (CandidateRerankers.isCompleteTable(tmp.list()?.toList().orEmpty())) tmp
            else tmp.listFiles()?.singleOrNull { it.isDirectory && CandidateRerankers.isCompleteTable(it.list()?.toList().orEmpty()) }
                ?: throw InvalidTableException("meta.tsv missing")
            val target = dir(filesDir)
            val old = File(filesDir, "$DIR_NAME.old")
            old.deleteRecursively()
            if (target.exists() && !target.renameTo(old)) target.deleteRecursively()
            if (!root.renameTo(target)) throw IOException("could not move table into place")
            old.deleteRecursively()
            generation++
            progress(100)
            return Result(total, digest.digest().joinToString("") { "%02x".format(it) })
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** 表を消す（変換は表なしで続く）。 */
    fun delete(filesDir: File): Boolean {
        val ok = dir(filesDir).deleteRecursively()
        generation++
        return ok
    }
}

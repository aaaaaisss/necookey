package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.util.zip.ZipInputStream

/**
 * 診断用（ローカル JVM のみ）: NK_DICT_DUMP=<出力 TSV> でシステム辞書（app/dictionary-src/system）の全語を
 * 「読み\t表記\t左ID\t右ID\t語コスト」で書き出す。表記は辞書そのまま（SystemDictionaryErrata は適用しない）。
 * tools/dict_check/check.py の入力。
 */
class SystemDictionaryDumpTest {
    @Test
    fun dump() {
        val out = System.getenv("NK_DICT_DUMP")
        assumeTrue(!out.isNullOrBlank())
        val app = listOf(File("."), File("app")).first { File(it, "dictionary-src/system/token.dat.zip").isFile }
        val src = System.getenv("NK_DICT_SRC")?.let { File(it) } ?: File(app, "dictionary-src/system")
        val tango = read(File(src, "tango.dat.zip")) { LOUDS().readExternalNotCompress(it) }
        val yomi = read(File(src, "yomi.dat.zip")) { LOUDSWithTermId().readExternalNotCompress(it) }
        val token = read(File(src, "token.dat.zip")) { input -> TokenArray().also { it.readExternal(input) } }
        read(File(app, "src/main/assets/pos_table.dat")) { token.readPOSTable(it) }
        val lbs = yomi.LBS
        val lbsV = SuccinctBitVector(lbs)
        val leafV = SuccinctBitVector(yomi.isLeaf)
        val tokV = SuccinctBitVector(token.bitvector)
        val tangoV = SuccinctBitVector(tango.LBS)
        val labels = yomi.getAllLabels()
        File(out!!).bufferedWriter().use { w ->
            // LOUDS を深さ優先でたどる（traverse と同じ子の数え方）
            val stack = ArrayDeque<Pair<Int, String>>()
            stack.addLast(0 to "")
            while (stack.isNotEmpty()) {
                val (pos, s) = stack.removeLast()
                if (s.isNotEmpty() && yomi.isLeaf[pos]) {
                    val termId = yomi.getTermId(pos, leafV)
                    if (termId >= 0) {
                        token.forEachDictionaryByYomiTermId(termId, tokV) { pti, cost, nodeId ->
                            val t = when (nodeId) {
                                -2 -> s
                                -1 -> s.map { if (it in 'ぁ'..'ゖ') it + 0x60 else it }.joinToString("")
                                else -> tango.getLetter(nodeId, succinctBitVector = tangoV)
                            }
                            w.write("$s\t$t\t${token.leftIds[pti.toInt()]}\t${token.rightIds[pti.toInt()]}\t$cost\n")
                        }
                    }
                }
                var c = lbsV.select0(lbsV.rank1(pos)) + 1
                while (c > 0 && c < lbs.size() && lbs[c]) {
                    val li = lbsV.rank1(c)
                    if (li in labels.indices) stack.addLast(c to s + labels[li])
                    c++
                }
            }
        }
    }

    private fun <T> read(file: File, block: (ObjectInputStream) -> T): T {
        val fin = BufferedInputStream(FileInputStream(file))
        val input = if (file.name.endsWith(".zip")) ZipInputStream(fin).also { z ->
            var e = z.nextEntry
            while (e != null && e.isDirectory) e = z.nextEntry
        } else fin
        return ObjectInputStream(BufferedInputStream(input)).use(block)
    }
}

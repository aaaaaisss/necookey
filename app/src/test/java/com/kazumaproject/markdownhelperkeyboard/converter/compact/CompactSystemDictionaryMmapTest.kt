package com.kazumaproject.markdownhelperkeyboard.converter.compact

import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.nio.channels.FileChannel
import java.util.zip.ZipInputStream

/** 圧縮辞書をメモリマップのまま読んだ値が、元のシリアライズ辞書と一致する。 */
class CompactSystemDictionaryMmapTest {
    private val appDir: File? = listOf(File("."), File("app")).firstOrNull {
        File(it, "dictionary-src/system/token.dat.zip").isFile
    }

    @Test
    fun mappedSystemDictionaryMatchesSerializedSource() {
        val app = appDir
        assumeTrue(app != null)
        val kdict = File(app, "build/generated/compactSystemDictionary/assets/system/system.compact.kdict")
        assumeTrue(kdict.isFile)
        val compact = FileInputStream(kdict).channel.use { channel ->
            CompactSystemDictionaryReader.read(channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()))
        }.get(CompactDictionaryKind.SYSTEM)

        val src = File(app!!, "dictionary-src/system")
        val tango = read(File(src, "tango.dat.zip")) { LOUDS().readExternalNotCompress(it) }
        val yomi = read(File(src, "yomi.dat.zip")) { LOUDSWithTermId().readExternalNotCompress(it) }
        val token = read(File(src, "token.dat.zip")) { input -> TokenArray().also { it.readExternal(input) } }

        assertArrayEquals(tango.getAllLabels(), compact.tangoTrie.getAllLabels())
        assertArrayEquals(yomi.getAllLabels(), compact.yomiTrie.getAllLabels())
        assertArrayEquals(yomi.getAllTermIds(), compact.yomiTrie.getAllTermIds())
        assertArrayEquals(token.getPosTableIndices(), compact.tokenArray.getPosTableIndices())
        assertArrayEquals(token.getWordCosts(), compact.tokenArray.getWordCosts())
    }

    private fun <T> read(file: File, block: (ObjectInputStream) -> T): T =
        ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null && entry.isDirectory) entry = zip.nextEntry
            ObjectInputStream(BufferedInputStream(zip)).use(block)
        }
}

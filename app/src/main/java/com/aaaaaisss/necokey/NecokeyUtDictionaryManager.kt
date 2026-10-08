package com.aaaaaisss.necokey

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.util.zip.ZipInputStream
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray

class NecokeyUtDictionaryManager(private val context: Context) {
    private val root = File(context.filesDir, "ut_dictionary_overrides")
    private val groups = mapOf(
        "person_name" to Triple("tango_person_names.dat", "yomi_person_names.dat", "token_person_names.dat"),
        "places" to Triple("tango_places.dat.zip", "yomi_places.dat.zip", "token_places.dat.zip"),
        "wiki" to Triple("tango_wiki.dat.zip", "yomi_wiki.dat.zip", "token_wiki.dat.zip"),
        "neologd" to Triple("tango_neologd.dat.zip", "yomi_neologd.dat.zip", "token_neologd.dat.zip"),
        "web" to Triple("tango_web.dat.zip", "yomi_web.dat.zip", "token_web.dat.zip"),
    )

    fun importZip(uri: Uri): Result {
        val entries = mutableMapOf<String, ByteArray>()
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name.substringAfterLast('/').lowercase()
                            if (groups.values.any { name == it.first || name == it.second || name == it.third }) {
                                entries[name] = zip.readBytes()
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("ZIPを開けませんでした")
            if (entries.isEmpty()) error("UT辞書ファイルが見つかりませんでした")

            val validated = mutableMapOf<String, ByteArray>()
            val updated = mutableListOf<String>()
            for ((category, names) in groups) {
                val files = listOf(names.first, names.second, names.third).map { entries[it.lowercase()] }
                if (files.all { it != null }) {
                    validateTriple(files[0]!!, files[1]!!, files[2]!!)
                    validated[names.first.lowercase()] = files[0]!!
                    validated[names.second.lowercase()] = files[1]!!
                    validated[names.third.lowercase()] = files[2]!!
                    updated += category
                }
            }
            if (updated.isEmpty()) error("完全なUT辞書3ファイル組がありませんでした")

            root.mkdirs()
            for ((name, bytes) in validated) {
                val tmp = File(root, "$name.tmp")
                FileOutputStream(tmp).use { it.write(bytes) }
                val target = File(root, name)
                if (target.exists()) target.delete()
                check(tmp.renameTo(target)) { "辞書の保存に失敗しました: $name" }
            }
            Result(updated, "更新済み: ${updated.joinToString("、")}")
        }.onFailure {
            root.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach(File::delete)
        }
    }

    fun openOverride(assetPath: String): FileInputStream? {
        val file = File(root, assetPath.substringAfterLast('/').lowercase())
        return if (file.isFile) FileInputStream(file) else null
    }

    private fun validateTriple(tangoBytes: ByteArray, yomiBytes: ByteArray, tokenBytes: ByteArray) {
        ObjectInputStream(tangoBytes.inputStream().buffered()).use { LOUDS().readExternalNotCompress(it) }
        ObjectInputStream(yomiBytes.inputStream().buffered()).use { LOUDSWithTermId().readExternalNotCompress(it) }
        ObjectInputStream(tokenBytes.inputStream().buffered()).use { TokenArray().readExternal(it) }
    }

    data class Result(val categories: List<String>, val message: String)
}

package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.import_export

import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutUsageMode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 実ユーザーのバックアップ（schemaVersion 4、かな/ABC の 5x4 配列）を本物のインポーターで読み込み、
 * 残しているキー種別・二段長押し・ダブルタップ・アイコンが落ちないことを確認する。
 */
class UserKeyboardBackupImportTest {

    private fun importBackup(): List<ImportableKeyboardLayout> {
        val json = requireNotNull(
            javaClass.classLoader?.getResource("custom_keyboard/user_keyboard_backup_v4.json")
        ) { "missing test resource" }.readText()
        return when (val result = KeyboardLayoutJsonImporter.parse(json)) {
            is KeyboardLayoutImportResult.Success -> result.layouts
            is KeyboardLayoutImportResult.PartialSuccess -> error("partial import: $result")
            is KeyboardLayoutImportResult.Failure -> error("import failed: ${result.error}")
        }
    }

    @Test
    fun importsBothLayoutsWithAllKeys() {
        val layouts = importBackup()

        assertEquals(listOf("かな", "ABC"), layouts.map { it.layout.name })
        assertEquals(
            listOf(KeyboardLayoutUsageMode.Normal, KeyboardLayoutUsageMode.Number),
            layouts.map { it.layout.usageMode }
        )
        layouts.forEach { layout ->
            assertEquals(5, layout.layout.columnCount)
            assertEquals(4, layout.layout.rowCount)
            assertEquals(20, layout.keysWithFlicks.size)
        }
    }

    @Test
    fun preservesKeyTypes() {
        val (kana, abc) = importBackup()

        fun countByType(layout: ImportableKeyboardLayout) =
            layout.keysWithFlicks.groupingBy { it.key.keyType }.eachCount()

        assertEquals(
            mapOf(
                KeyType.NORMAL to 5,
                KeyType.PETAL_FLICK to 7,
                KeyType.CROSS_FLICK to 2,
                KeyType.TWO_STEP_FLICK to 6,
            ),
            countByType(kana)
        )
        assertEquals(
            mapOf(
                KeyType.NORMAL to 7,
                KeyType.PETAL_FLICK to 12,
                KeyType.CROSS_FLICK to 1,
            ),
            countByType(abc)
        )
    }

    @Test
    fun preservesTwoStepAndTwoStepLongPressFlicks() {
        val kana = importBackup().first()
        val ya = kana.keysWithFlicks.single { it.key.label == "や" }

        assertEquals(KeyType.TWO_STEP_FLICK, ya.key.keyType)
        assertEquals(17, ya.twoStepFlicks.size)
        val longPress = ya.twoStepLongPressFlicks.associate {
            (it.firstDirection to it.secondDirection) to it.output
        }
        assertEquals(
            mapOf(
                (TfbiFlickDirection.LEFT to TfbiFlickDirection.LEFT) to "（",
                (TfbiFlickDirection.RIGHT to TfbiFlickDirection.RIGHT) to "）",
            ),
            longPress
        )
    }

    @Test
    fun preservesPetalCircularSlotsAndCrossFlicks() {
        val (kana, abc) = importBackup()

        val na = kana.keysWithFlicks.single { it.key.label == "な" }
        assertEquals(KeyType.PETAL_FLICK, na.key.keyType)
        assertEquals(5, na.flicks.size)
        assertEquals(5, na.circularFlicks.size)

        val abcKey = abc.keysWithFlicks.single { it.key.label == "ABC" }
        assertEquals(4, abcKey.circularFlicks.size)

        val delete = kana.keysWithFlicks.single { it.key.label == "Delete" }
        assertEquals(KeyType.CROSS_FLICK, delete.key.keyType)
        assertTrue(delete.key.isSpecialKey)
        assertEquals(2, delete.flicks.size)
    }

    @Test
    fun preservesDoubleTapPasteAndIcons() {
        importBackup().forEach { layout ->
            val pasteKeys = layout.keysWithFlicks.filter { it.key.doubleTapAction != null }
            assertEquals("layout ${layout.layout.name}", 1, pasteKeys.size)
            val key = pasteKeys.single().key
            assertEquals("Paste", key.doubleTapAction)
            assertEquals("EXCLUSIVE", key.doubleTapPolicy)
            assertEquals("DoNothing", key.action)
            assertEquals("content_paste_24px", key.iconValue)

            assertTrue(
                layout.keysWithFlicks.any { it.key.iconValue == "language_24dp" }
            )
            layout.keysWithFlicks.forEach { assertEquals("NORMAL", it.key.textInputBehavior) }
        }
    }
}

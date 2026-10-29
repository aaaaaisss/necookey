package com.kazumaproject.markdownhelperkeyboard.ime_service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IMEServiceCommitAndSpaceContractTest {

    @Test
    fun commitAndInsertSpaceUsesRawComposingTextAndKeepsTheRightTail() {
        val function = imeServiceSource().functionBody(
            start = "private fun handleCommitAndInsertSpace()",
            end = "private fun setSpaceKeyActionEnglishAndNumberEmpty"
        )

        assertTrue(function.contains("dispatchDirectSpaceIfNeeded()"))
        assertTrue(function.contains("commitRawTextAndInsertSpace(inputString.value, stringInTail.get())"))
        assertTrue(function.contains("clearSuggestionStateAfterCommit()"))
        assertTrue(function.contains("resetFlagsEnterKeyNotHenkan()"))
    }

    @Test
    fun commitAndInsertSpaceIsTapOnly() {
        val source = imeServiceSource()
        val longPress = source.functionBody(
            start = "override fun onActionLongPress",
            end = "override fun onActionUpAfterLongPress"
        )
        val flickLongPress = source.functionBody(
            start = "override fun onFlickActionLongPress",
            end = "override fun onFlickActionUpAfterLongPress"
        )

        assertTrue(longPress.contains("KeyAction.CommitAndInsertSpace -> {}"))
        assertTrue(flickLongPress.contains("KeyAction.CommitAndInsertSpace -> {}"))
    }

    /**
     * 未入力スペース（setSpaceKeyActionEnglishAndNumberEmpty）はトップレベルの resolveEmptySpaceForCurrentMode
     * （SpaceKeyResolver.kt）を呼ぶ。同じ名前・同じ引数のメンバー関数があるとそちらが優先され、
     * 中で自分自身を呼ぶ無限再帰になって IME が落ちた（9107a70 で混入）。
     */
    @Test
    fun emptySpaceUsesTopLevelResolverWithoutSelfRecursiveMember() {
        val source = imeServiceSource()
        assertTrue(!Regex("""fun\s+resolveEmptySpaceForCurrentMode\s*\(""").containsMatchIn(source))
        val emptySpace = source.functionBody(
            start = "private fun setSpaceKeyActionEnglishAndNumberEmpty",
            end = "\n    private fun "
        )
        assertTrue(emptySpace.contains("resolveEmptySpaceForCurrentMode("))
    }

    private fun imeServiceSource(): String =
        listOf(
            File("app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt"),
            File("src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt")
        ).first { it.isFile }.readText()

    private fun String.functionBody(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing start marker: $start" }
        val endIndex = indexOf(end, startIndex + start.length)
        require(endIndex >= 0) { "Missing end marker: $end" }
        return substring(startIndex, endIndex)
    }
}

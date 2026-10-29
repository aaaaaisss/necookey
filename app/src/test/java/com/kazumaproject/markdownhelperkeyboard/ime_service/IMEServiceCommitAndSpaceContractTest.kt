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

    /** チップ列の確定ボタンは、触っていなくても見えている表記（チップ列の表示）を学習付きの経路で確定する。 */
    @Test
    fun chipConfirmButtonCommitsVisibleChipTextEvenWhenUntouched() {
        val source = imeServiceSource()
        val confirm = source.functionBody(start = "private fun confirmChipBar()", end = "\n    private fun ")
        assertTrue(confirm.contains("commitChipBar(input, requireTouched = false)"))
        val commit = source.functionBody(start = "private fun commitChipBar(", end = "\n    private fun ")
        assertTrue(commit.contains("if (requireTouched && !ChipBarPresenter.isTouched(state)) return false"))
        assertTrue(commit.contains("recordChipBarLearning(state)"))
        assertTrue(commit.contains("model.commit(state)"))
        val enter = source.functionBody(start = "private fun handleNonEmptyInputEnterKey(", end = "\n    private fun ")
        assertTrue(enter.contains("commitChipBar(insertString, requireTouched = true)"))
    }

    /**
     * 入力中のスペース長押し: 見えている変換をそのまま確定してから、未入力のときと同じカーソル移動モードに入る
     * （カスタム配列のタップ長押し・フリック長押し・テンキーのサイドキー）。
     */
    @Test
    fun spaceLongPressWhileComposingCommitsVisibleTextThenEntersCursorMoveMode() {
        val source = imeServiceSource()
        val helper = source.functionBody(start = "private fun commitComposingForSpaceCursorMove()", end = "\n    private fun ")
        assertTrue(helper.contains("commitChipBar(insertString, requireTouched = false)"))
        assertTrue(helper.contains("handleNonEmptyInputEnterKey("))
        assertTrue(helper.contains("return inputString.value.isEmpty()"))

        val longPress = source.functionBody(start = "override fun onActionLongPress", end = "override fun onActionUpAfterLongPress")
        val flickLongPress = source.functionBody(start = "override fun onFlickActionLongPress", end = "override fun onFlickActionUpAfterLongPress")
        for ((body, src) in listOf(longPress to "SumireCustomSpace", flickLongPress to "SumireCustomFlickSpace")) {
            val space = body.substring(body.indexOf("KeyAction.Space -> {"))
            val commit = space.indexOf("if (!commitComposingForSpaceCursorMove()) return")
            val enter = space.indexOf("SpaceConvertCursorMoveSource.$src")
            assertTrue(commit >= 0 && enter > commit)
        }
        val side = source.functionBody(start = "private fun handleSpaceLongAction()", end = "\n    private fun ")
        assertTrue(side.contains("if (!commitComposingForSpaceCursorMove()) return"))
        assertTrue(side.contains("enterTenKeyCursorMoveMode()"))
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

package com.kazumaproject.markdownhelperkeyboard.ime_service

enum class SpaceConvertCursorMoveSource {
    SumireCustomSpace,
    SumireCustomConvert,
    SumireCustomFlickSpace,
    SumireCustomFlickConvert,
    QwertySpace
}

/**
 * スペース／変換キーの長押しでカーソル移動モードに入るか。未入力のときだけ入る
 * （スペース長押しは入力中でも先に見えている表記で確定してから呼ぶので、常にカーソル移動になる）。
 * 2026-10-30: 入力中でもカーソル移動にするスイッチ conversion_key_swipe_cursor_move_preference（既定オフ）を削除。
 */
object SpaceConvertCursorMovePolicy {
    @Suppress("UNUSED_PARAMETER")
    fun shouldEnterCursorMoveMode(
        hasInputString: Boolean,
        source: SpaceConvertCursorMoveSource
    ): Boolean = !hasInputString
}

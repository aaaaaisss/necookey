package com.kazumaproject.custom_keyboard.data

/**
 * 特殊キー (isSpecialKey=true && keyId が非空) で、layout 構築時の static な base flick
 * map を attach 時の現在の [KeyData.action] / [KeyData.label] / [KeyData.drawableResId] に
 * 合わせて refresh するためのヘルパー。
 *
 * `updateDynamicKey` などで keyData.action が更新されると、layout.flickKeyMaps[keyId] の
 * 内容は古いままになりうる。base flick map の TAP entry を keyData.action に揃えることで、
 * dynamicStates が変わっても CROSS_FLICK 経路の Tap fallback が正しい action を返す。
 *
 * 対象外のキー (isSpecialKey=false / keyId 空) はそのまま返す。
 */
fun Map<FlickDirection, FlickAction>.refreshSpecialKeyTap(
    keyData: KeyData
): Map<FlickDirection, FlickAction> {
    if (!keyData.isSpecialKey) return this
    if (keyData.keyId.isNullOrBlank()) return this
    val tapAction = keyData.action ?: return this

    val existingTap = this[FlickDirection.TAP]
    val existingTapAction = (existingTap as? FlickAction.Action)?.action
    val existingLabel = (existingTap as? FlickAction.Action)?.label
    val existingDrawable = (existingTap as? FlickAction.Action)?.drawableResId

    val resolvedLabel = if (tapAction == KeyAction.DoNothing) {
        null
    } else {
        keyData.label.takeIf { it.isNotBlank() } ?: existingLabel
    }
    val resolvedDrawable = if (tapAction == KeyAction.DoNothing) {
        null
    } else {
        keyData.drawableResId ?: KeyActionMapper.iconResIdForAction(tapAction)
    }

    if (existingTapAction == tapAction &&
        existingLabel == resolvedLabel &&
        existingDrawable == resolvedDrawable
    ) {
        return this
    }

    return this.toMutableMap().apply {
        this[FlickDirection.TAP] = FlickAction.Action(
            action = tapAction,
            label = resolvedLabel,
            drawableResId = resolvedDrawable
        )
    }
}

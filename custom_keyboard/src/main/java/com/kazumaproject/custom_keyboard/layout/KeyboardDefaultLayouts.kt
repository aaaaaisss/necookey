package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.DoubleTapBinding
import com.kazumaproject.custom_keyboard.data.DoubleTapPolicy
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutItem
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.copyWithItems
import com.kazumaproject.custom_keyboard.data.copyWithKeys
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection

object KeyboardDefaultLayouts {
    data class DeleteKeyFlickSettings(
        val left: Boolean = true,
        val up: Boolean = false,
        val down: Boolean = false
    ) {
        val hasFlickActions: Boolean
            get() = left || up || down
    }

    private fun createDeleteActionMap(
        deleteKeyFlickSettings: DeleteKeyFlickSettings
    ): Map<FlickDirection, FlickAction> = buildMap {
        put(
            FlickDirection.TAP,
            FlickAction.Action(
                KeyAction.Delete,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
            )
        )
        if (deleteKeyFlickSettings.left) {
            put(
                FlickDirection.UP_LEFT,
                FlickAction.Action(
                    KeyAction.DeleteUntilSymbol,
                    drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol
                )
            )
        }
        if (deleteKeyFlickSettings.up) {
            put(
                FlickDirection.UP,
                FlickAction.Action(
                    KeyAction.DeleteAfterCursorUntilSymbol,
                    drawableResId = com.kazumaproject.core.R.drawable.backspace_24px_until_symbol
                )
            )
        }
        if (deleteKeyFlickSettings.down) {
            put(
                FlickDirection.DOWN,
                FlickAction.Action(
                    KeyAction.UndoLastDelete,
                    drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
                )
            )
        }
    }

    fun applyDeleteKeyFlickSettings(
        layout: KeyboardLayout,
        deleteKeyFlickSettings: DeleteKeyFlickSettings
    ): KeyboardLayout {
        val deleteMapKeys = layout.flickKeyMaps
            .filterValues { states ->
                states.any { actionMap ->
                    (actionMap[FlickDirection.TAP] as? FlickAction.Action)?.action == KeyAction.Delete
                }
            }
            .keys

        val deleteKeyLookupKeys = layout.keys
            .filter { keyData ->
                keyData.action == KeyAction.Delete ||
                        keyData.keyId == "delete_key" ||
                        keyData.keyId in deleteMapKeys ||
                        (keyData.label.isNotBlank() && keyData.label in deleteMapKeys)
            }
            .flatMap { keyData -> listOfNotNull(keyData.keyId, keyData.label) }
            .toSet() + deleteMapKeys

        if (deleteKeyLookupKeys.isEmpty()) return layout

        fun shouldKeep(action: FlickAction): Boolean {
            val keyAction = (action as? FlickAction.Action)?.action ?: return true
            return when (keyAction) {
                KeyAction.Delete -> true
                KeyAction.DeleteUntilSymbol -> deleteKeyFlickSettings.left
                KeyAction.DeleteAfterCursorUntilSymbol -> deleteKeyFlickSettings.up
                KeyAction.UndoLastDelete -> deleteKeyFlickSettings.down
                else -> true
            }
        }

        val filteredFlickKeyMaps = layout.flickKeyMaps.mapValues { (key, states) ->
            if (key !in deleteKeyLookupKeys) {
                states
            } else {
                states.map { actionMap -> actionMap.filterValues(::shouldKeep) }
            }
        }
        val filteredCircularFlickKeyMaps = layout.circularFlickKeyMaps.mapValues { (key, states) ->
            if (key !in deleteKeyLookupKeys) {
                states
            } else {
                states.map { actionMap -> actionMap.filterValues(::shouldKeep) }
            }
        }

        val filteredKeys = if (deleteKeyFlickSettings.hasFlickActions) {
            layout.keys
        } else {
            layout.keys.map { keyData ->
                val isDeleteKey = keyData.action == KeyAction.Delete ||
                        keyData.keyId == "delete_key" ||
                        keyData.keyId in deleteKeyLookupKeys ||
                        (keyData.label.isNotBlank() && keyData.label in deleteKeyLookupKeys)
                if (isDeleteKey) {
                    keyData.copy(action = KeyAction.Delete, keyType = KeyType.NORMAL)
                } else {
                    keyData
                }
            }
        }

        return layout.copyWithKeys(filteredKeys).copy(
            flickKeyMaps = filteredFlickKeyMaps,
            circularFlickKeyMaps = filteredCircularFlickKeyMaps
        )
    }


    private val enterKeyStates = listOf(
        FlickAction.Action(KeyAction.NewLine, "改行"),
        FlickAction.Action(
            KeyAction.Confirm,
            drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_alt_24
        ),
        FlickAction.Action(
            KeyAction.Enter,
            drawableResId = com.kazumaproject.core.R.drawable.baseline_keyboard_return_24,
        ),
        FlickAction.Action(
            KeyAction.Enter, "検索",
        ),
        FlickAction.Action(KeyAction.Enter, "次"),
        FlickAction.Action(KeyAction.Enter, "確定"),
        FlickAction.Action(KeyAction.Enter, "前へ"),
        FlickAction.Action(KeyAction.Enter, "実行"),
        FlickAction.Action(KeyAction.Enter, "送信"),
    )


    private val dakutenToggleStates = listOf(
        FlickAction.Action(
            KeyAction.InputText("^_^"),
            label = "^_^",
            drawableResId = com.kazumaproject.core.R.drawable.ic_custom_icon
        ), FlickAction.Action(
            KeyAction.ToggleDakuten,
            label = " 小゛゜",
        )
    )


    private val spaceConvertStates = listOf(
        FlickAction.Action(KeyAction.Space, "空白"), FlickAction.Action(KeyAction.Convert, "変換")
    )


    fun createNumberLayout(
        deleteKeyFlickSettings: DeleteKeyFlickSettings = DeleteKeyFlickSettings()
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                label = "1",
                row = 0,
                column = 0,
                isFlickable = false,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                label = "4",
                row = 1,
                column = 0,
                isFlickable = false,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                label = "7",
                row = 2,
                column = 0,
                isFlickable = false,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                label = ",",
                row = 3,
                column = 0,
                isFlickable = false,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                "2", 0, 1, false, keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                "3", 0, 2, false, keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                label = "",
                row = 0,
                column = 3,
                isFlickable = false,
                action = KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyType = KeyType.NORMAL,
                keyId = "switch_next_ime"
            ),
            KeyData("5", 1, 1, false, keyType = KeyType.PETAL_FLICK),
            KeyData("6", 1, 2, false, keyType = KeyType.PETAL_FLICK),
            KeyData(
                "",
                1,
                3,
                false,
                spaceConvertStates[0].action,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24,
                rowSpan = 1,
                keyType = KeyType.NORMAL
            ),
            KeyData("8", 2, 1, false, keyType = KeyType.PETAL_FLICK),
            KeyData("9", 2, 2, false, keyType = KeyType.PETAL_FLICK),
            KeyData(
                "",
                2,
                3,
                false,
                KeyAction.Delete,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px,
                keyType = if (deleteKeyFlickSettings.hasFlickActions) {
                    KeyType.CROSS_FLICK
                } else {
                    KeyType.NORMAL
                },
                keyId = "delete_key"
            ),
            KeyData("0", 3, 1, false, keyType = KeyType.PETAL_FLICK),
            KeyData(".", 3, 2, false, keyType = KeyType.PETAL_FLICK),
            KeyData(
                label = enterKeyStates[0].label ?: "",
                row = 3,
                column = 3,
                isFlickable = false,
                action = enterKeyStates[0].action,
                rowSpan = 1,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_keyboard_return_24,
                keyId = "enter_key",
                dynamicStates = enterKeyStates,
            )
        )

        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> =
            mutableMapOf<String, List<Map<FlickDirection, FlickAction>>>(
                "1" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("1"))),
                "2" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("2"))),
                "3" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("3"))),
                "4" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("4"))),
                "5" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("5"))),
                "6" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("6"))),
                "7" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("7"))),
                "8" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("8"))),
                "9" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("9"))),
                "0" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("0"))),
                "," to listOf(
                    mapOf(
                        FlickDirection.TAP to FlickAction.Input(","),
                        FlickDirection.UP_LEFT_FAR to FlickAction.Input("+"),
                        FlickDirection.UP to FlickAction.Input("-"),
                        FlickDirection.UP_RIGHT_FAR to FlickAction.Input("*"),
                        FlickDirection.DOWN to FlickAction.Input("/"),
                    )
                ),
                "." to listOf(
                    mapOf(
                        FlickDirection.TAP to FlickAction.Input("."),
                        FlickDirection.UP_LEFT_FAR to FlickAction.Input("("),
                        FlickDirection.UP to FlickAction.Input("%"),
                        FlickDirection.UP_RIGHT_FAR to FlickAction.Input(")"),
                        FlickDirection.DOWN to FlickAction.Input("="),
                    )
                )
            ).toMutableMap().apply {
                if (deleteKeyFlickSettings.hasFlickActions) {
                    put("", listOf(createDeleteActionMap(deleteKeyFlickSettings)))
                }
            }

        return applyDeleteKeyFlickSettings(
            KeyboardLayout(keys, flickMaps, 4, 4),
            deleteKeyFlickSettings
        )
    }

    fun createFlickKanaTemplateLayout(
        isDefaultKey: Boolean
    ): KeyboardLayout {
        val keys = listOf(
            KeyData(
                "",
                0,
                0,
                false,
                KeyAction.SelectAll,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.text_select_start_24dp,
            ), KeyData(
                "",
                1,
                0,
                false,
                KeyAction.MoveCursorLeft,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            ), KeyData(
                "",
                2,
                0,
                false,
                isSpecialKey = true,
                action = KeyAction.Space,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24
            ), KeyData(
                "",
                3,
                0,
                false,
                KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyId = "switch_next_ime"
            ), KeyData(
                "あ",
                0,
                1,
                true,
                keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "か",
                0,
                2,
                true,
                keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "さ",
                0,
                3,
                true,
                keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "た",
                1,
                1,
                true,
                keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "な",
                1,
                2,
                true,
                keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "は",
                1,
                3,
                true,
                keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "ま",
                2,
                1,
                true,
                keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "や",
                2,
                2,
                true,
                keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "ら",
                2,
                3,
                true,
                keyType = KeyType.PETAL_FLICK
            ), KeyData(
                dakutenToggleStates[1].label ?: "",
                3,
                1,
                false,
                dakutenToggleStates[1].action,
                dynamicStates = dakutenToggleStates,
                keyId = "dakuten_toggle_key",
                keyType = KeyType.CROSS_FLICK
            ), KeyData(
                "わ",
                3,
                2,
                true,
                keyType = KeyType.PETAL_FLICK
            ), KeyData(
                "、。?!",
                3,
                3,
                true,
                keyType = KeyType.PETAL_FLICK
            ), // Label fixed to match map key
            KeyData(
                "Del",
                0,
                4,
                false,
                KeyAction.Delete,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px
            ), KeyData(
                "",
                1,
                4,
                false,
                KeyAction.MoveCursorRight,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            ), KeyData(
                "",
                2,
                4,
                false,
                KeyAction.Space,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24,
            ), KeyData(
                "",
                3,
                4,
                false,
                KeyAction.Enter,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_keyboard_return_24,
            )
        )

        val cursorMoveActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.MoveCursorRight,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24
            ), FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.MoveCursorLeft,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24
            )
        )

        val spaceActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Space,
            ), FlickDirection.UP_LEFT to FlickAction.Action(
                KeyAction.Space,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24

            )
        )

        val conversionActionMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.Convert,
            ),
        )

        // 状態0 (^_^): タップ操作のみを持つマップ
        val emojiStateFlickMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.InputText("^_^"), label = "^_^"
            )
            // この状態ではフリックアクションを定義しない
        )

        // 状態1 ( 小゛゜): タップとフリック操作を持つマップ
        val dakutenStateFlickMap = mapOf(
            FlickDirection.TAP to FlickAction.Action(
                KeyAction.ToggleDakuten, label = " 小゛゜"
            ),
        )

        val a = mapOf(
            FlickDirection.TAP to FlickAction.Input("あ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("い"),
            FlickDirection.UP to FlickAction.Input("う"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("え"),
            FlickDirection.DOWN to FlickAction.Input("お")
        )
        val ka = mapOf(
            FlickDirection.TAP to FlickAction.Input("か"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("き"),
            FlickDirection.UP to FlickAction.Input("く"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("け"),
            FlickDirection.DOWN to FlickAction.Input("こ")
        )
        val sa = mapOf(
            FlickDirection.TAP to FlickAction.Input("さ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("し"),
            FlickDirection.UP to FlickAction.Input("す"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("せ"),
            FlickDirection.DOWN to FlickAction.Input("そ")
        )
        val ta = mapOf(
            FlickDirection.TAP to FlickAction.Input("た"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ち"),
            FlickDirection.UP to FlickAction.Input("つ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("て"),
            FlickDirection.DOWN to FlickAction.Input("と")
        )
        val na = mapOf(
            FlickDirection.TAP to FlickAction.Input("な"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("に"),
            FlickDirection.UP to FlickAction.Input("ぬ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ね"),
            FlickDirection.DOWN to FlickAction.Input("の")
        )
        val ha = mapOf(
            FlickDirection.TAP to FlickAction.Input("は"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("ひ"),
            FlickDirection.UP to FlickAction.Input("ふ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("へ"),
            FlickDirection.DOWN to FlickAction.Input("ほ")
        )
        val ma = mapOf(
            FlickDirection.TAP to FlickAction.Input("ま"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("み"),
            FlickDirection.UP to FlickAction.Input("む"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("め"),
            FlickDirection.DOWN to FlickAction.Input("も")
        )
        val ya = mapOf(
            FlickDirection.TAP to FlickAction.Input("や"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("("),
            FlickDirection.UP to FlickAction.Input("ゆ"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input(")"),
            FlickDirection.DOWN to FlickAction.Input("よ")
        )
        val ra = mapOf(
            FlickDirection.TAP to FlickAction.Input("ら"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("り"),
            FlickDirection.UP to FlickAction.Input("る"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("れ"),
            FlickDirection.DOWN to FlickAction.Input("ろ")
        )
        val wa = mapOf(
            FlickDirection.TAP to FlickAction.Input("わ"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("を"),
            FlickDirection.UP to FlickAction.Input("ん"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("ー"),
            FlickDirection.DOWN to FlickAction.Input("〜")
        )
        val symbols = mapOf(
            FlickDirection.TAP to FlickAction.Input("、"),
            FlickDirection.UP to FlickAction.Input("？"),
            FlickDirection.UP_LEFT_FAR to FlickAction.Input("。"),
            FlickDirection.UP_RIGHT_FAR to FlickAction.Input("！"),
            FlickDirection.DOWN to FlickAction.Input("…")
        )

        val flickMaps: MutableMap<String, List<Map<FlickDirection, FlickAction>>> = mutableMapOf(
            "CursorMoveLeft" to listOf(cursorMoveActionMap),
            "あ" to listOf(a),
            "か" to listOf(ka),
            "さ" to listOf(sa),
            "た" to listOf(ta),
            "な" to listOf(na),
            "は" to listOf(ha),
            "ま" to listOf(ma),
            "や" to listOf(ya),
            "ら" to listOf(ra),
            "わ" to listOf(wa),
            "、。?!" to listOf(symbols),
        )

        dakutenToggleStates.getOrNull(0)?.label?.let { label ->
            flickMaps.put(label, listOf(emojiStateFlickMap))
        }
        dakutenToggleStates.getOrNull(1)?.label?.let { label ->
            flickMaps.put(label, listOf(dakutenStateFlickMap))
        }

        spaceConvertStates.getOrNull(0)?.label?.let { label ->
            flickMaps.put(label, listOf(spaceActionMap))
        }

        spaceConvertStates.getOrNull(1)?.label?.let { label ->
            flickMaps.put(label, listOf(conversionActionMap))
        }

        return KeyboardLayout(keys, flickMaps, 5, 4)
    }

    fun createNumberTemplateLayout(): KeyboardLayout {
        val keys = listOf(
            KeyData(
                label = "1",
                row = 0,
                column = 0,
                isFlickable = false,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                label = "4",
                row = 1,
                column = 0,
                isFlickable = false,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                label = "7",
                row = 2,
                column = 0,
                isFlickable = false,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                label = ",",
                row = 3,
                column = 0,
                isFlickable = false,
                keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                "2", 0, 1, false, keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                "3", 0, 2, false, keyType = KeyType.PETAL_FLICK
            ),
            KeyData(
                label = "",
                row = 0,
                column = 3,
                isFlickable = false,
                action = KeyAction.SwitchToNextIme,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
                keyType = KeyType.NORMAL,
                keyId = "switch_next_ime"
            ),
            KeyData("5", 1, 1, false, keyType = KeyType.PETAL_FLICK),
            KeyData("6", 1, 2, false, keyType = KeyType.PETAL_FLICK),
            KeyData(
                "",
                1,
                3,
                false,
                spaceConvertStates[0].action,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_space_bar_24,
                rowSpan = 1,
                keyType = KeyType.NORMAL
            ),
            KeyData("8", 2, 1, false, keyType = KeyType.PETAL_FLICK),
            KeyData("9", 2, 2, false, keyType = KeyType.PETAL_FLICK),
            KeyData(
                "",
                2,
                3,
                false,
                KeyAction.Delete,
                isSpecialKey = true,
                rowSpan = 1,
                drawableResId = com.kazumaproject.core.R.drawable.backspace_24px,
                keyType = KeyType.NORMAL
            ),
            KeyData("0", 3, 1, false, keyType = KeyType.PETAL_FLICK),
            KeyData(".", 3, 2, false, keyType = KeyType.PETAL_FLICK),
            KeyData(
                label = "",
                row = 3,
                column = 3,
                isFlickable = false,
                action = KeyAction.Enter,
                rowSpan = 1,
                isSpecialKey = true,
                drawableResId = com.kazumaproject.core.R.drawable.baseline_keyboard_return_24,
            )
        )

        val flickMaps: Map<String, List<Map<FlickDirection, FlickAction>>> = mapOf(
            "1" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("1"))),
            "2" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("2"))),
            "3" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("3"))),
            "4" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("4"))),
            "5" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("5"))),
            "6" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("6"))),
            "7" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("7"))),
            "8" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("8"))),
            "9" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("9"))),
            "0" to listOf(mapOf(FlickDirection.TAP to FlickAction.Input("0"))),
            "," to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input(","),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("+"),
                    FlickDirection.UP to FlickAction.Input("-"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input("*"),
                    FlickDirection.DOWN to FlickAction.Input("/"),
                )
            ),
            "." to listOf(
                mapOf(
                    FlickDirection.TAP to FlickAction.Input("."),
                    FlickDirection.UP_LEFT_FAR to FlickAction.Input("("),
                    FlickDirection.UP to FlickAction.Input("%"),
                    FlickDirection.UP_RIGHT_FAR to FlickAction.Input(")"),
                    FlickDirection.DOWN to FlickAction.Input("="),
                )
            )
        )

        return KeyboardLayout(keys, flickMaps, 4, 4)
    }

}

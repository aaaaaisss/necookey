package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyCharacterCase
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlickKeyboardViewCharacterCaseTest {

    @Test
    fun nonInputShortcutLabel_isNotChanged() {
        val keyboard = createView()
        keyboard.setKeyboard(
            KeyboardLayout(
                keys = listOf(
                    KeyData(
                        label = "paste",
                        row = 0,
                        column = 0,
                        isFlickable = false,
                        action = KeyAction.Paste,
                        isSpecialKey = false,
                        keyId = "paste",
                        keyType = KeyType.NORMAL
                    )
                ),
                flickKeyMaps = emptyMap(),
                columnCount = 1,
                rowCount = 1
            )
        )

        keyboard.setKeyCharacterCase(KeyCharacterCase.UPPERCASE)

        assertNotNull(keyboard.findButtonWithText("paste"))
        assertNull(keyboard.findButtonWithText("PASTE"))
    }

    @Test
    fun textActionLabel_isChangedEvenWhenItUsesSpecialKeyStyling() {
        val keyboard = createView()
        keyboard.setKeyboard(
            KeyboardLayout(
                keys = listOf(
                    KeyData(
                        label = "a",
                        row = 0,
                        column = 0,
                        isFlickable = false,
                        action = KeyAction.Text("a"),
                        isSpecialKey = true,
                        keyId = "styled_text",
                        keyType = KeyType.NORMAL
                    )
                ),
                flickKeyMaps = emptyMap(),
                columnCount = 1,
                rowCount = 1
            )
        )

        keyboard.setKeyCharacterCase(KeyCharacterCase.UPPERCASE)

        assertNotNull(keyboard.findButtonWithText("A"))
    }

    private fun createView(): FlickKeyboardView {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return FlickKeyboardView(
            ContextThemeWrapper(
                context,
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
            )
        )
    }

    private fun FlickKeyboardView.findButtonWithText(text: String): AutoSizeButton? {
        for (index in 0 until childCount) {
            val button = getChildAt(index) as? AutoSizeButton ?: continue
            if (button.text.toString() == text) return button
        }
        return null
    }
}

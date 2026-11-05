package com.kazumaproject.custom_keyboard.view

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.domain.flick.FlickThresholdShape
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
/** 感度・長押し時間を変えてもキーの View を作り直さない（standardFlickControllers は削除済みなので View の同一性だけを見る）。 */
class FlickKeyboardRuntimeSettingsTest {

    @Test
    fun sensitivityAndLongPressUpdatesPreserveExistingView() {
        val keyboardView = FlickKeyboardView(context())
        keyboardView.setKeyboard(standardLayout())
        val childBefore = keyboardView.getChildAt(0)

        keyboardView.setFlickSensitivityValue(1)
        keyboardView.setFlickThresholdShape(FlickThresholdShape.Rectangular)
        keyboardView.setLongPressTimeout(100L)
        keyboardView.setFlickSensitivityValue(200)
        keyboardView.setFlickThresholdShape(FlickThresholdShape.Radial)
        keyboardView.setLongPressTimeout(2_000L)

        assertSame(childBefore, keyboardView.getChildAt(0))
    }

    private fun standardLayout(): KeyboardLayout {
        val key = KeyData(
            label = "a",
            row = 0,
            column = 0,
            isFlickable = true,
            keyId = "a",
            keyType = KeyType.STANDARD_FLICK
        )
        return KeyboardLayout(
            keys = listOf(key),
            flickKeyMaps = mapOf(
                "a" to listOf(
                    mapOf(
                        FlickDirection.TAP to FlickAction.Input("a"),
                        FlickDirection.UP_RIGHT_FAR to FlickAction.Input("b")
                    )
                )
            ),
            columnCount = 1,
            rowCount = 1
        )
    }

    private fun context(): Context {
        return ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
        )
    }
}

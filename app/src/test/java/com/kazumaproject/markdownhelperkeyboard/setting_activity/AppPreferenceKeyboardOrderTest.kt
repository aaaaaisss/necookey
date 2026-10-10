package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferenceKeyboardOrderTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()
        AppPreference.init(context)
    }

    @Test
    fun freshInstallUsesOnlyTheCustomKeyboard() {
        assertEquals(listOf(KeyboardType.CUSTOM), AppPreference.keyboard_order)
    }

    @Test
    fun legacyStoredOrderWithRemovedKeyboardsResolvesToCustomOnly() {
        preferences.edit()
            .putString("keyboard_order_preference", "[\"TENKEY\",\"QWERTY\",\"CUSTOM\"]")
            .commit()
        AppPreference.init(context)

        assertEquals(listOf(KeyboardType.CUSTOM), AppPreference.keyboard_order)
    }
}

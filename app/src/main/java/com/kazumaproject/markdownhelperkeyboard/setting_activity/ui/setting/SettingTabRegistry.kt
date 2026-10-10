package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import androidx.annotation.IdRes
import androidx.annotation.XmlRes
import androidx.fragment.app.Fragment
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig

data class SettingTabSpec(
    val key: String,
    val title: (Context) -> String,
    @XmlRes val xmlRes: Int?,
    @IdRes val destinationId: Int,
    val fragmentFactory: () -> Fragment,
)

object SettingTabRegistry {
    const val TAB_COMMON = "common"
    const val TAB_ZENZ = "zenz"
    const val TAB_CONVERSION_ENGINE = "conversion_engine"
    const val TAB_DICTIONARY = "dictionary"

    fun createTabs(): List<SettingTabSpec> {
        val tabs = mutableListOf(
            SettingTabSpec(
                key = TAB_COMMON,
                title = { context -> context.getString(R.string.category_common) },
                xmlRes = R.xml.pref_common_legacy,
                destinationId = R.id.legacyCommonPreferenceFragment,
                fragmentFactory = { LegacyCommonPreferenceFragment() },
            ),
        )

        if (AppVariantConfig.hasZenz) {
            tabs += SettingTabSpec(
                key = TAB_ZENZ,
                title = { "zenz" },
                xmlRes = R.xml.pref_zenz,
                destinationId = R.id.zenzPreferenceFragment,
                fragmentFactory = { ZenzPreferenceFragment() },
            )
        }

        tabs += listOf(
            SettingTabSpec(
                key = TAB_CONVERSION_ENGINE,
                title = { context -> context.getString(R.string.conversion_engine_category_title) },
                xmlRes = R.xml.pref_conversion_engine,
                destinationId = R.id.conversionEnginePreferenceFragment,
                fragmentFactory = { ConversionEnginePreferenceFragment() },
            ),
            SettingTabSpec(
                key = TAB_DICTIONARY,
                title = { context -> context.getString(R.string.category_dictionary) },
                xmlRes = R.xml.pref_dictionary,
                destinationId = R.id.dictionaryPreferenceFragment,
                fragmentFactory = { DictionaryPreferenceFragment() },
            ),
        )

        return tabs
    }
}

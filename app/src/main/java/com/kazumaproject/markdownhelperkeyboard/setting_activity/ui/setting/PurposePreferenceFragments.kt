package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import com.kazumaproject.markdownhelperkeyboard.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ConversionEnginePreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_conversion_engine
}

@AndroidEntryPoint
class LegacyCommonPreferenceFragment : CommonPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_common_legacy
}

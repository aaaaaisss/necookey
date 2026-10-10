package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.XmlRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.color.colorChooser
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.CandidateReadingSizeLimits
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.SprayPaintSettings
import com.kazumaproject.markdownhelperkeyboard.local_font.LocalFontRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import dagger.hilt.android.AndroidEntryPoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
open class CommonPreferenceFragment : AsyncPreferenceFragment() {

    companion object {
        private const val LONG_PRESS_TIMEOUT_MIN_MS = 100
        private const val LONG_PRESS_TIMEOUT_MAX_MS = 2000
        private const val LONG_PRESS_TIMEOUT_DEFAULT_MS = 300
    }

    @get:XmlRes
    protected override val preferencesXmlRes: Int = R.xml.pref_common_legacy

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var localFontRepositoryProvider: javax.inject.Provider<LocalFontRepository>

    private lateinit var packageInfo: android.content.pm.PackageInfo

    override suspend fun preparePreferenceData(context: android.content.Context) {
        packageInfo = settingsIo(SettingsLoadStage.PACKAGE_INFO) {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        settingsIo(SettingsLoadStage.FONT) { localFontRepositoryProvider.get().loadIfNeeded() }
    }

    private var count = 0

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val json = AppPreference.exportAllToJson()
                writeTextToUri(uri, json)
            }.onSuccess {
                toast("Backup exported")
            }.onFailure {
                toast("Export failed: ${it.message}")
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val json = withContext(Dispatchers.IO) { readTextFromUri(uri) }
                    AppPreference.importAllFromJson(json, replaceAll = true)

                    // Reset device-local display assets only after the JSON has been applied.
                    settingsIo(SettingsLoadStage.FONT) { localFontRepositoryProvider.get().restoreStandard() }
                    AppPreference.migrateSumirePreferenceIfNeeded()
                    AppPreference.migratePredictionLookaheadPreferenceIfNeeded()
                    toast("Backup imported")
                    requireActivity().recreate()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    toast("Import failed: ${e.message}")
                }
            }
        }

    // ヘルパーを class 内に追記
    private fun readTextFromUri(uri: Uri): String {
        val cr = requireContext().contentResolver
        cr.openInputStream(uri).use { input ->
            if (input == null) error("Cannot open input stream")
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { br ->
                val sb = StringBuilder()
                var line: String?
                while (true) {
                    line = br.readLine() ?: break
                    sb.append(line).append('\n')
                }
                return sb.toString()
            }
        }
    }

    private fun writeTextToUri(uri: Uri, text: String) {
        val cr = requireContext().contentResolver
        cr.openOutputStream(uri).use { out ->
            if (out == null) error("Cannot open output stream")
            OutputStreamWriter(out, Charsets.UTF_8).use { w ->
                w.write(text)
                w.flush()
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        val guidePreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        val guideSettings = com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.ComposingGuideSettings(guidePreferences)
        fun updateGuideModeEnabled(text: Boolean = guideSettings.textEnabled, candidates: Boolean = guideSettings.enabled) {
            findPreference<ListPreference>("composing_guide_display_mode")?.isEnabled = text && candidates
        }
        updateGuideModeEnabled()
        findPreference<SwitchPreferenceCompat>("composing_guide_text_enabled")?.setOnPreferenceChangeListener { _, value ->
            updateGuideModeEnabled(text = value as Boolean)
            true
        }
        findPreference<SwitchPreferenceCompat>("composing_guide_enabled")?.setOnPreferenceChangeListener { _, value ->
            updateGuideModeEnabled(candidates = value as Boolean)
            true
        }
        findPreference<SeekBarPreference>("composing_guide_text_size_setting")?.apply {
            value = guideSettings.textSize.toInt()
            setOnPreferenceChangeListener { _, newValue -> guideSettings.textSize = (newValue as Int).toFloat(); true }
        }
        com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.GuideProfile.entries.forEach { profile ->
            findPreference<Preference>("composing_guide_${profile.key}_reset")?.setOnPreferenceClickListener {
                com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.ComposingGuideSettings.reset(guidePreferences, profile)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>(AppPreference.INLINE_SUGGESTION_ENABLED_KEY)?.let {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                it.isEnabled = false
                it.summary = getString(R.string.inline_suggestion_unsupported_summary)
            }
        }

        findPreference<Preference>("pref_backup_export")?.setOnPreferenceClickListener {
            val fileName = "sumire_prefs_backup_${System.currentTimeMillis()}.json"
            exportLauncher.launch(fileName)
            true
        }

        findPreference<Preference>("pref_backup_import")?.setOnPreferenceClickListener {
            importLauncher.launch(arrayOf("application/json", "text/*"))
            true
        }

        val candidateColumnListPreference =
            findPreference<ListPreference>("candidate_column_preference")
        candidateColumnListPreference?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue is String) {
                    appPreference.setCandidateColumnAndSyncHeight(
                        isLandscape = false,
                        column = newValue
                    )
                }
                true
            }
        }

        val appVersionPreference = findPreference<Preference>("app_version_preference")
        appVersionPreference?.apply {
            summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "version name: ${packageInfo.versionName}\nversion code: ${packageInfo.longVersionCode}"
            } else {
                "version name: ${packageInfo.versionName}\nversion code: ${packageInfo.versionCode}"
            }
            setOnPreferenceClickListener {
                count += 1
                true
            }
        }

        val keyboardLetterSizePreference =
            findPreference<Preference>("keyboard_key_letter_size_fragment_preference")

        keyboardLetterSizePreference?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.keyCandidateLetterSizeFragment
            )
            true
        }

        val candidateHeightFragmentSetting =
            findPreference<Preference>("candidate_view_height_setting_fragment_preference")
        candidateHeightFragmentSetting?.apply {
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.candidateViewHeightSettingFragment
                )
                true
            }
        }

        val clipBoardHistoryPreference =
            findPreference<Preference>("clipboard_history_preference_fragment")
        clipBoardHistoryPreference?.apply {
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.clipboardHistoryFragment
                )
                true
            }
        }

        val symbolKeyboardOrderPreference = findPreference<ListPreference>("symbol_mode_preference")
        symbolKeyboardOrderPreference?.apply {
            summary = when (value) {
                "EMOJI" -> getString(R.string.emoji)
                "EMOTICON" -> getString(R.string.emoticon)
                "SYMBOL" -> getString(R.string.symbol)
                "CLIPBOARD" -> getString(R.string.clipboard_history)
                else -> getString(R.string.choose_initial_symbol_keyboard_open)
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary = when (newValue) {
                    "EMOJI" -> getString(R.string.emoji)
                    "EMOTICON" -> getString(R.string.emoticon)
                    "SYMBOL" -> getString(R.string.symbol)
                    "CLIPBOARD" -> getString(R.string.clipboard_history)
                    else -> getString(R.string.choose_initial_symbol_keyboard_open)
                }
                true
            }
        }

        findPreference<ListPreference>("default_emoji_skin_tone_preference")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
        syncDefaultEmojiSkinTonePreference()

        findPreference<SeekBarPreference>("flick_sensitivity_preference")?.apply {
            summary = when (this.value) {
                in 0..50 -> getString(R.string.sensitivity_very_high)
                in 51..90 -> getString(R.string.sensitivity_high)
                in 91..110 -> getString(R.string.sensitivity_normal)
                in 111..150 -> getString(R.string.sensitivity_less)
                in 151..200 -> getString(R.string.sensitivity_low)
                else -> ""
            }
            setOnPreferenceChangeListener { pref, newValue ->
                val sbp = pref as SeekBarPreference
                val raw = (newValue as Int)
                val inc = sbp.seekBarIncrement
                val rounded = (raw + inc / 2) / inc * inc
                summary = when (rounded) {
                    in 0..50 -> getString(R.string.sensitivity_very_high)
                    in 51..90 -> getString(R.string.sensitivity_high)
                    in 91..110 -> getString(R.string.sensitivity_normal)
                    in 111..150 -> getString(R.string.sensitivity_less)
                    in 151..200 -> getString(R.string.sensitivity_low)
                    else -> ""
                }
                return@setOnPreferenceChangeListener if (rounded != raw) {
                    sbp.value = rounded
                    false
                } else {
                    true
                }
            }
        }

        findPreference<SeekBarPreference>("key_sound_volume_percent_preference")?.apply {
            updateKeySoundVolumeSummary(value)
            setOnPreferenceChangeListener { _, newValue ->
                updateKeySoundVolumeSummary(newValue as Int)
                true
            }
        }

        findPreference<Preference>("long_press_timeout_preference")?.apply {
            updateLongPressTimeoutSummary()
            setOnPreferenceClickListener {
                showLongPressTimeoutDialog()
                true
            }
        }

        val keyboardUndoEnablePreference =
            findPreference<SwitchPreferenceCompat>("undo_enable_preference")
        keyboardUndoEnablePreference?.apply {
            appPreference.undo_enable_preference?.let {
                this.summary = if (it) {
                    resources.getString(R.string.undo_enable_summary_on)
                } else {
                    resources.getString(R.string.undo_enable_summary_off)
                }
            }
            this.setOnPreferenceChangeListener { _, newValue ->
                this.summary = if (newValue == true) {
                    resources.getString(R.string.undo_enable_summary_on)
                } else {
                    resources.getString(R.string.undo_enable_summary_off)
                }
                true
            }
        }

        findPreference<Preference>("delete_key_flick_left_targets_preference")?.apply {
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.deleteKeyFlickTargetsFragment
                )
                true
            }
        }

        findPreference<Preference>("cursor_move_after_commit_target_pairs_preference")?.apply {
            updateCursorMoveTargetPairsSummary()
            setOnPreferenceClickListener {
                navigateSafely(
                    R.id.cursorMoveTargetPairsFragment
                )
                true
            }
        }

        val keyboardSettingPreference = findPreference<Preference>("keyboard_screen_preference")

        keyboardSettingPreference?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.keyboardSettingFragment
            )
            true
        }

        val openSourcePreference = findPreference<Preference>("preference_open_source")

        openSourcePreference?.setOnPreferenceClickListener {
            navigateSafely(
                R.id.openSourceFragment
            )
            true
        }

        val seedColorPickerPreference =
            findPreference<Preference>("keyboard_theme_fragment_preference")
        seedColorPickerPreference?.apply {
            isVisible = DynamicColors.isDynamicColorAvailable()
            setOnPreferenceClickListener {
                showColorPickerDialog()
                true
            }
        }

        setupRoutePreferences()
        onCommonPreferencesCreated()
    }

    protected open fun onCommonPreferencesCreated() = Unit

    private fun setupRoutePreferences() {
        val routeTargets = mapOf(
            "date_candidate_settings_preference" to R.id.dateCandidateSettingsFragment,
            "setting_route_utility_candidates" to R.id.utilityCandidatePreferenceFragment,
        )

        routeTargets.forEach { (key, destinationId) ->
            findPreference<Preference>(key)?.setOnPreferenceClickListener {
                navigateSafely(destinationId)
                true
            }
        }

    }

    override fun onPreferencesResumed() {
        val guideSettings = com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.ComposingGuideSettings(
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()))
        findPreference<ListPreference>("composing_guide_display_mode")?.isEnabled = guideSettings.textEnabled && guideSettings.enabled
        findPreference<SeekBarPreference>("composing_guide_text_size_setting")?.value = guideSettings.textSize.toInt()
        syncDefaultEmojiSkinTonePreference()
        updateCursorMoveTargetPairsSummary()
    }

    // リーク対策: RecyclerViewの参照を断ち切る
    override fun onDestroyView() {
        try {
            listView.adapter = null
        } catch (e: Exception) {
            // Viewが生成されていない場合などを考慮して例外は無視
        }
        super.onDestroyView()
    }

    private fun syncDefaultEmojiSkinTonePreference() {
        findPreference<ListPreference>("default_emoji_skin_tone_preference")?.apply {
            val savedSkinTone = appPreference.default_emoji_skin_tone_preference
            if (value != savedSkinTone) {
                value = savedSkinTone
            }
        }
    }

    private fun updateCursorMoveTargetPairsSummary() {
        findPreference<Preference>("cursor_move_after_commit_target_pairs_preference")?.summary =
            getString(
                R.string.cursor_move_target_pairs_summary_current,
                appPreference.cursor_move_after_commit_target_pairs_preference.joinToString(" ")
                    .ifBlank { getString(R.string.cursor_move_target_pairs_not_set) }
            )
    }

    @SuppressLint("CheckResult")
    private fun showColorPickerDialog() {
        val initialColor = appPreference.seedColor
        MaterialDialog(requireContext()).show {
            title(text = getString(R.string.keyboard_theme_dialog_title))
            colorChooser(
                colors = intArrayOf(
                    0x00000000,
                    ContextCompat.getColor(requireContext(), com.kazumaproject.core.R.color.violet),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.violet_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.violet_dark
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.mint_dark
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.sky_dark
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange2
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange_light
                    ),
                    ContextCompat.getColor(
                        requireContext(),
                        com.kazumaproject.core.R.color.orange_dark
                    ),
                ),
                initialSelection = initialColor,
                allowCustomArgb = true
            ) { _, color ->
                appPreference.seedColor = color
                requireActivity().recreate()
            }
            positiveButton(android.R.string.ok)
            negativeButton(android.R.string.cancel)
        }
    }

    @SuppressLint("CheckResult")
    private fun updateKeySoundVolumeSummary(value: Int) {
        findPreference<SeekBarPreference>("key_sound_volume_percent_preference")?.summary =
            if (value == 0) {
                getString(R.string.key_sound_volume_system_default)
            } else {
                getString(R.string.key_sound_volume_percent, value)
            }
    }

    private fun updateLongPressTimeoutSummary() {
        val value =
            (appPreference.long_press_timeout_preference ?: LONG_PRESS_TIMEOUT_DEFAULT_MS)
                .coerceIn(LONG_PRESS_TIMEOUT_MIN_MS, LONG_PRESS_TIMEOUT_MAX_MS)
        findPreference<Preference>("long_press_timeout_preference")?.summary =
            getString(R.string.long_press_timeout_preference_value, value)
    }

    private fun showLongPressTimeoutDialog() {
        val dialogView =
            layoutInflater.inflate(R.layout.dialog_long_press_timeout_preference, null)
        val valueText =
            dialogView.findViewById<TextView>(R.id.long_press_timeout_value_text)
        val seekBar =
            dialogView.findViewById<SeekBar>(R.id.long_press_timeout_seekbar)

        val initialValue =
            (appPreference.long_press_timeout_preference ?: LONG_PRESS_TIMEOUT_DEFAULT_MS)
                .coerceIn(LONG_PRESS_TIMEOUT_MIN_MS, LONG_PRESS_TIMEOUT_MAX_MS)

        fun updateLabel(value: Int) {
            valueText.text = getString(R.string.long_press_timeout_preference_value, value)
        }

        seekBar.max = LONG_PRESS_TIMEOUT_MAX_MS - LONG_PRESS_TIMEOUT_MIN_MS
        seekBar.progress = initialValue - LONG_PRESS_TIMEOUT_MIN_MS
        updateLabel(initialValue)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateLabel(progress + LONG_PRESS_TIMEOUT_MIN_MS)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.long_press_timeout_preference_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                appPreference.long_press_timeout_preference =
                    seekBar.progress + LONG_PRESS_TIMEOUT_MIN_MS
                updateLongPressTimeoutSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.reset_to_default, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                seekBar.progress = LONG_PRESS_TIMEOUT_DEFAULT_MS - LONG_PRESS_TIMEOUT_MIN_MS
            }
        }
        dialog.show()
    }

}

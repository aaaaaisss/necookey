package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.learning.nextword.NextWordEntity
import com.kazumaproject.markdownhelperkeyboard.learning.nextword.NextWordRepository
import com.kazumaproject.markdownhelperkeyboard.local_font.LocalFontRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject

/**
 * necookey の設定カテゴリ画面 (res/xml/pref_main.xml)。
 * 設定ホーム ([SettingMainFragment]) から rootKey = screen_* で開かれ、そのカテゴリの項目だけを表示する。
 * 他カテゴリの項目は findPreference が null を返すので bind* は何もしない。
 */
@AndroidEntryPoint
class MainPreferenceFragment : AsyncPreferenceFragment() {

    override val preferencesXmlRes: Int = R.xml.pref_main

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var nextWordRepository: NextWordRepository

    @Inject
    lateinit var localFontRepositoryProvider: javax.inject.Provider<LocalFontRepository>

    private lateinit var packageInfo: android.content.pm.PackageInfo

    override suspend fun preparePreferenceData(context: android.content.Context) {
        packageInfo = settingsIo(SettingsLoadStage.PACKAGE_INFO) {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        settingsIo(SettingsLoadStage.FONT) { localFontRepositoryProvider.get().loadIfNeeded() }
    }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                writeTextToUri(uri, AppPreference.exportAllToJson())
            }.onSuccess {
                toast(getString(R.string.settings_backup_exported))
            }.onFailure {
                toast(getString(R.string.settings_backup_export_failed, it.message ?: ""))
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
                    toast(getString(R.string.settings_backup_imported))
                    requireActivity().recreate()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    toast(getString(R.string.settings_backup_import_failed, e.message ?: ""))
                }
            }
        }

    private val openModelLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            onZenzModelUriSelected(uri)
        }

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        bindInputSection()
        bindCandidateSection()
        bindDictionarySection()
        bindZenzSection()
        bindSymbolSection()
        bindClipboardSection()
        bindBackupAndAboutSection()
    }

    override fun onPreferencesResumed() {
        syncDefaultEmojiSkinTonePreference()
        updateCursorMoveTargetPairsSummary()
        refreshNextWordSummary()
        syncZenzBunsetsuGateEnabled()
    }

    override fun onDestroyView() {
        runCatching { listView.adapter = null }
        super.onDestroyView()
    }

    // ---- 入力・キー操作 ----

    private fun bindInputSection() {
        findPreference<SeekBarPreference>("flick_sensitivity_preference")?.apply {
            summary = sensitivityLabel(value)
            setOnPreferenceChangeListener { pref, newValue ->
                val sbp = pref as SeekBarPreference
                val raw = newValue as Int
                val inc = sbp.seekBarIncrement
                val rounded = (raw + inc / 2) / inc * inc
                summary = sensitivityLabel(rounded)
                if (rounded != raw) {
                    sbp.value = rounded
                    false
                } else {
                    true
                }
            }
        }

        findPreference<SwitchPreferenceCompat>("undo_enable_preference")?.apply {
            fun label(on: Boolean) = getString(
                if (on) R.string.undo_enable_summary_on else R.string.undo_enable_summary_off
            )
            summary = label(appPreference.undo_enable_preference == true)
            setOnPreferenceChangeListener { _, newValue ->
                summary = label(newValue == true)
                true
            }
        }

        findPreference<Preference>("delete_key_flick_left_targets_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.deleteKeyFlickTargetsFragment)
            true
        }

        findPreference<Preference>("cursor_move_after_commit_target_pairs_preference")?.apply {
            updateCursorMoveTargetPairsSummary()
            setOnPreferenceClickListener {
                navigateSafely(R.id.cursorMoveTargetPairsFragment)
                true
            }
        }
    }

    private fun sensitivityLabel(value: Int): String = when (value) {
        in 0..50 -> getString(R.string.sensitivity_very_high)
        in 51..90 -> getString(R.string.sensitivity_high)
        in 91..110 -> getString(R.string.sensitivity_normal)
        in 111..150 -> getString(R.string.sensitivity_less)
        in 151..200 -> getString(R.string.sensitivity_low)
        else -> ""
    }

    private fun updateCursorMoveTargetPairsSummary() {
        findPreference<Preference>("cursor_move_after_commit_target_pairs_preference")?.summary =
            getString(
                R.string.cursor_move_target_pairs_summary_current,
                appPreference.cursor_move_after_commit_target_pairs_preference.joinToString(" ")
                    .ifBlank { getString(R.string.cursor_move_target_pairs_not_set) }
            )
    }

    // ---- 変換候補 ----

    private fun bindCandidateSection() {
        findPreference<SwitchPreferenceCompat>(AppPreference.INLINE_SUGGESTION_ENABLED_KEY)?.let {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                it.isEnabled = false
                it.summary = getString(R.string.inline_suggestion_unsupported_summary)
            }
        }

        findPreference<Preference>("candidate_view_height_setting_fragment_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.candidateViewHeightSettingFragment)
            true
        }

        findPreference<ListPreference>("candidate_column_preference")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue is String) {
                appPreference.setCandidateColumnAndSyncHeight(isLandscape = false, column = newValue)
            }
            true
        }
    }

    // ---- 辞書・学習 ----

    private fun bindDictionarySection() {
        findPreference<Preference>("custom_keyboard_list_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.keyboardListFragment)
            true
        }
        findPreference<Preference>("keyboard_screen_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.keyboardSettingFragment)
            true
        }
        findPreference<Preference>("keyboard_key_letter_size_fragment_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.keyCandidateLetterSizeFragment)
            true
        }
        findPreference<Preference>("home_learn_dictionary_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.navigation_learn_dictionary)
            true
        }
        findPreference<Preference>("home_user_dictionary_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.navigation_user_dictionary)
            true
        }
        findPreference<Preference>("ng_word_list_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.ngWordFragment)
            true
        }

        findPreference<Preference>("next_word_learning_view_preference")?.setOnPreferenceClickListener {
            showNextWordEntries()
            true
        }

        findPreference<Preference>("next_word_learning_clear_preference")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.next_word_learning_clear_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        nextWordRepository.deleteAll()
                        refreshNextWordSummary()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
        refreshNextWordSummary()

        findPreference<SeekBarPreference>("learn_prediction_preference")?.apply {
            summary = getString(
                R.string.learn_dictionary_prefix_match_summary,
                appPreference.learn_prediction_preference,
            )
            setOnPreferenceChangeListener { _, newValue ->
                summary = getString(R.string.learn_dictionary_prefix_match_summary, newValue as Int)
                true
            }
        }

        findPreference<SeekBarPreference>("user_dictionary_prefix_match_number")?.apply {
            appPreference.user_dictionary_prefix_match_number_preference?.let {
                summary = getString(R.string.user_dictionary_prefix_match_summary, it)
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary = getString(R.string.user_dictionary_prefix_match_summary, newValue as Int)
                true
            }
        }
    }

    private fun refreshNextWordSummary() {
        if (view == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val count = nextWordRepository.count()
            findPreference<Preference>("next_word_learning_view_preference")?.summary =
                getString(R.string.next_word_learning_view_summary, count)
        }
    }

    private fun showNextWordEntries() {
        viewLifecycleOwner.lifecycleScope.launch {
            val entries = nextWordRepository.recent(NEXT_WORD_VIEW_LIMIT)
            if (!isAdded) return@launch
            if (entries.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.next_word_learning_view_title)
                    .setMessage(R.string.next_word_learning_empty)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@launch
            }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.next_word_learning_view_title)
                .setItems(entries.map { it.label() }.toTypedArray()) { _, which ->
                    confirmDeleteNextWord(entries[which])
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun confirmDeleteNextWord(entry: NextWordEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(entry.label())
            .setMessage(R.string.next_word_learning_delete_entry_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    entry.id?.let { nextWordRepository.deleteById(it) }
                    refreshNextWordSummary()
                    showNextWordEntries()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun NextWordEntity.label(): String = "…$context → $output（$reading）×$usageCount"

    // ---- zenz ----

    private fun bindZenzSection() {
        if (!AppVariantConfig.hasZenz) {
            findPreference<Preference>("zenz_category")?.isVisible = false
            // The bunsetsu gate lives in the zenz section but depends on the two-row bar.
            return
        }
        findPreference<Preference>("zenz_diagnostics_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.zenzDiagnosticsFragment)
            true
        }
        findPreference<Preference>("zenz_model_select_preference")?.setOnPreferenceClickListener {
            showZenzModelSelectDialog()
            true
        }
        updateZenzModelSummary()
        syncZenzBunsetsuGateEnabled()
    }

    // 文節ゲートは「2段候補バー」(変換・予測カテゴリ) に依存する。別画面にあるため XML の
    // android:dependency ではなくコードで有効/無効を切り替える。
    private fun syncZenzBunsetsuGateEnabled() {
        findPreference<Preference>("necookey_zenz_bunsetsu_gate_preference")?.isEnabled =
            appPreference.necookey_two_row_candidate_bar_preference
    }

    private fun showZenzModelSelectDialog() {
        val items = arrayOf(
            getString(R.string.zenz_model_source_default),
            getString(R.string.zenz_model_source_pick),
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.zenz_model_source_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        AppPreference.zenz_model_uri_preference = ""
                        updateZenzModelSummary()
                    }

                    1 -> openModelLauncher.launch(arrayOf("*/*"))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onZenzModelUriSelected(uri: Uri) {
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            AppPreference.zenz_model_uri_preference = uri.toString()
            updateZenzModelSummary()
        } catch (e: Exception) {
            Timber.e(e, "Failed to handle selected zenz model uri.")
        }
    }

    private fun updateZenzModelSummary() {
        val pref = findPreference<Preference>("zenz_model_select_preference") ?: return
        val uriStr = AppPreference.zenz_model_uri_preference
        pref.summary = if (uriStr.isBlank()) {
            getString(R.string.zenz_model_source_default)
        } else {
            getString(R.string.zenz_model_source_selected, uriStr)
        }
    }

    // ---- 記号・クリップボード ----

    private fun bindSymbolSection() {
        findPreference<ListPreference>("symbol_mode_preference")?.apply {
            fun label(v: Any?) = when (v) {
                "EMOJI" -> getString(R.string.emoji)
                "EMOTICON" -> getString(R.string.emoticon)
                "SYMBOL" -> getString(R.string.symbol)
                "CLIPBOARD" -> getString(R.string.clipboard_history)
                else -> getString(R.string.choose_initial_symbol_keyboard_open)
            }
            summary = label(value)
            setOnPreferenceChangeListener { _, newValue ->
                summary = label(newValue)
                true
            }
        }
        findPreference<ListPreference>("default_emoji_skin_tone_preference")?.summaryProvider =
            ListPreference.SimpleSummaryProvider.getInstance()
        syncDefaultEmojiSkinTonePreference()
    }

    private fun syncDefaultEmojiSkinTonePreference() {
        findPreference<ListPreference>("default_emoji_skin_tone_preference")?.apply {
            val saved = appPreference.default_emoji_skin_tone_preference
            if (value != saved) value = saved
        }
    }

    private fun bindClipboardSection() {
        findPreference<Preference>("clipboard_history_preference_fragment")?.setOnPreferenceClickListener {
            navigateSafely(R.id.clipboardHistoryFragment)
            true
        }
    }

    // ---- バックアップ・アプリについて ----

    private fun bindBackupAndAboutSection() {
        findPreference<Preference>("pref_backup_export")?.setOnPreferenceClickListener {
            exportLauncher.launch("necookey_prefs_backup_${System.currentTimeMillis()}.json")
            true
        }
        findPreference<Preference>("pref_backup_import")?.setOnPreferenceClickListener {
            importLauncher.launch(arrayOf("application/json", "text/*"))
            true
        }
        findPreference<Preference>("preference_open_source")?.setOnPreferenceClickListener {
            navigateSafely(R.id.openSourceFragment)
            true
        }
        findPreference<Preference>("app_version_preference")?.summary =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "${packageInfo.versionName}（${packageInfo.longVersionCode}）"
            } else {
                @Suppress("DEPRECATION")
                "${packageInfo.versionName}（${packageInfo.versionCode}）"
            }
    }

    private fun readTextFromUri(uri: Uri): String {
        val input = requireContext().contentResolver.openInputStream(uri)
            ?: error("Cannot open input stream")
        return BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
    }

    private fun writeTextToUri(uri: Uri, text: String) {
        val out = requireContext().contentResolver.openOutputStream(uri)
            ?: error("Cannot open output stream")
        OutputStreamWriter(out, Charsets.UTF_8).use { w ->
            w.write(text)
            w.flush()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val NEXT_WORD_VIEW_LIMIT = 300

        /** ナビゲーション引数: ツールバーに出すカテゴリ名（mobile_navigation.xml の label="{title}"）。 */
        const val ARG_TITLE = "title"
    }
}

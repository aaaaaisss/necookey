package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ScreenContextStore
import com.kazumaproject.markdownhelperkeyboard.ime_service.screen_context.ScreenContextAccessibilityService
import com.kazumaproject.markdownhelperkeyboard.diagnostics.FreezeLogFile
import com.kazumaproject.markdownhelperkeyboard.diagnostics.FreezeWatchdog
import com.kazumaproject.markdownhelperkeyboard.learning.nextword.NextWordEntity
import com.kazumaproject.markdownhelperkeyboard.learning.nextword.NextWordRepository
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
 * 行の見た目: Theme.Necookey.Settings の preferenceTheme（settings_pref_*）＋ [SettingsPreferenceAdapter]（角丸カード）。
 * 他カテゴリの項目は findPreference が null を返すので bind* は何もしない。
 */
@AndroidEntryPoint
class MainPreferenceFragment : AsyncPreferenceFragment() {

    override val preferencesXmlRes: Int = R.xml.pref_main

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var nextWordRepository: NextWordRepository

    private lateinit var packageInfo: android.content.pm.PackageInfo

    override suspend fun preparePreferenceData(context: android.content.Context) {
        packageInfo = settingsIo(SettingsLoadStage.PACKAGE_INFO) {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
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
                    AppPreference.migratePredictionLookaheadPreferenceIfNeeded()
                    toast(getString(R.string.settings_backup_imported))
                    requireActivity().recreate()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    toast(getString(R.string.settings_backup_import_failed, e.message ?: ""))
                }
            }
        }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDivider(null)
        listView.clipToPadding = false
        listView.itemAnimator = null
        listView.addItemDecoration(SettingsCardDecoration())
    }

    /** 行の背景（角丸カード・押下の波紋）と見出しの間隔を付けるアダプタ。 */
    override fun onCreateAdapter(preferenceScreen: androidx.preference.PreferenceScreen): androidx.recyclerview.widget.RecyclerView.Adapter<*> =
        SettingsPreferenceAdapter(preferenceScreen)

    /** 入れ子の PreferenceScreen（各カテゴリの「詳細設定」）を同じ画面クラスで開く。 */
    override fun onNavigateToScreen(preferenceScreen: androidx.preference.PreferenceScreen) {
        // 同じ destination への遷移なので navigateSafely（自己遷移を無視する）は使わない。
        runCatching {
            androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(
            R.id.categorySettingsFragment,
            androidx.core.os.bundleOf(
                androidx.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT to preferenceScreen.key,
                ARG_TITLE to preferenceScreen.title?.toString().orEmpty(),
            ),
            settingsNavOptions,
            )
        }.onFailure { Timber.w(it, "Cannot open nested settings screen") }
    }

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {
        bindInputSection()
        bindCandidateSection()
        bindDictionarySection()
        bindClipboardSection()
        bindBackupAndAboutSection()
    }

    override fun onPreferencesResumed() {
        updateCursorMoveTargetPairsSummary()
        refreshNextWordSummary()
    }

    override fun onDestroyView() {
        runCatching { listView.adapter = null }
        super.onDestroyView()
    }

    // ---- 画面の文脈（ユーザー補助、既定オフ） ----

    private fun bindScreenContextSection() {
        findPreference<Preference>(ScreenContextAccessibilityService.PREF_KEY)?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true && !isScreenContextServiceEnabled()) {
                // ユーザー補助はアプリからオンにできないので設定画面を開く（そこで necookey の項目をオンにする）。
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (e: Exception) {
                    Timber.w(e, "accessibility settings not available")
                }
            }
            if (newValue == false) ScreenContextStore.clearScreen()
            true
        }
    }

    private fun isScreenContextServiceEnabled(): Boolean {
        val ctx = context ?: return false
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val name = ComponentName(ctx, ScreenContextAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == name }
    }

    // ---- 入力・キー操作 ----

    private fun bindInputSection() {
        bindScreenContextSection()

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

    private fun updateCursorMoveTargetPairsSummary() {
        // 説明は今の記号だけ（例「「」 （）」）。未設定なら「未設定」
        findPreference<Preference>("cursor_move_after_commit_target_pairs_preference")?.summary =
            appPreference.cursor_move_after_commit_target_pairs_preference.joinToString(" ")
                .ifBlank { getString(R.string.cursor_move_target_pairs_not_set) }
    }

    // ---- 変換候補 ----

    private fun bindCandidateSection() {
        findPreference<Preference>("candidate_view_height_setting_fragment_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.candidateViewHeightSettingFragment)
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


    }

    private fun refreshNextWordSummary() {
        if (view == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val count = nextWordRepository.count()
            findPreference<Preference>("next_word_learning_view_preference")?.summary =
                getString(R.string.st_next_word_count, count)
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

    // ---- 記号・クリップボード ----

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
        findPreference<Preference>("freeze_log_preference")?.setOnPreferenceClickListener {
            showFreezeLogDialog()
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

    /** フリーズ記録（FreezeWatchdog）を表示する。ファイルの読み書きは IO で行う。 */
    private fun showFreezeLogDialog() {
        val context = requireContext()
        val file = FreezeWatchdog.logFile(context)
        viewLifecycleOwner.lifecycleScope.launch {
            val log = withContext(Dispatchers.IO) { runCatching { FreezeLogFile.read(file) }.getOrDefault("") }
            if (!isAdded) return@launch
            val padding = (16 * resources.displayMetrics.density).toInt()
            val textView = TextView(context).apply {
                text = log.ifEmpty { getString(R.string.freeze_log_empty) }
                setTextIsSelectable(true)
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(padding, padding, padding, padding)
            }
            val scroll = ScrollView(context).apply { addView(textView) }
            AlertDialog.Builder(context)
                .setTitle(R.string.freeze_log_title)
                .setView(scroll)
                .setPositiveButton(R.string.freeze_log_copy) { _, _ ->
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("necookey freeze log", log))
                    Toast.makeText(context, R.string.freeze_log_copied, Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton(R.string.freeze_log_clear) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        runCatching { FreezeLogFile.clear(file) }
                    }
                    Toast.makeText(context, R.string.freeze_log_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.freeze_log_close, null)
                .show()
                .also { dialog ->
                    val empty = log.isEmpty()
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !empty
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = !empty
                }
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

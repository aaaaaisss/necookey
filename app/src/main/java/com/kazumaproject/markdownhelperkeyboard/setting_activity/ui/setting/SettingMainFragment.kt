package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSettingMainBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemSettingHomeRowBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 設定ホーム（2026-10-30 作り直し）。上から「アプリ名」「キーボードが使えないときだけの小さなお知らせ」
 * 「セクションごとの角丸カード（アイコン・短いタイトル・矢印）」。各行は [MainPreferenceFragment] を
 * pref_main.xml の PreferenceScreen(key=screen_*) を rootKey として開く。
 */
@AndroidEntryPoint
class SettingMainFragment : Fragment() {

    private var _binding: FragmentSettingMainBinding? = null
    private val binding get() = _binding!!

    private var loadingUi: SettingsLoadingUi? = null
    private var initializationJob: kotlinx.coroutines.Job? = null

    @Inject
    lateinit var settingDataInitializer: SettingDataInitializer

    private data class Entry(
        val screenKey: String,
        @StringRes val title: Int,
        @DrawableRes val icon: Int,
    )

    private data class Section(@StringRes val label: Int, val entries: List<Entry>)

    private val sections = listOf(
        Section(
            R.string.st_section_input,
            listOf(
                Entry("screen_keyboard", R.string.st_keyboard, R.drawable.ic_ms_keyboard),
                Entry("screen_dictionary", R.string.st_dictionary, R.drawable.ic_ms_dictionary),
            ),
        ),
        Section(
            R.string.st_section_manage,
            listOf(
                Entry("screen_clipboard", R.string.st_clipboard, R.drawable.ic_ms_content_paste),
                Entry("screen_backup", R.string.st_backup, R.drawable.ic_ms_backup),
                Entry("screen_other", R.string.st_other, R.drawable.ic_ms_more_horiz),
            ),
        ),
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingMainBinding.inflate(inflater, container, false)
        loadingUi = SettingsLoadingUi(requireContext(), ::loadInitialData, blocksContent = false)
        binding.settingRoot.addView(
            loadingUi!!.overlay, android.widget.FrameLayout.LayoutParams(-1, -1),
        )
        binding.settingProgressBar.isVisible = false
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buildSections()
        updateImeStatus()
        // 入力方法の選択ダイアログを閉じたとき（onResume が来ない）にも状態を更新する。
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)

        loadInitialData()

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!findNavController().popBackStack()) {
                        requireActivity().finish()
                    }
                }
            })
    }

    private fun buildSections() {
        val list = binding.sectionList
        list.removeAllViews()
        sections.forEachIndexed { sectionIndex, section ->
            val label = layoutInflater.inflate(R.layout.item_setting_home_section, list, false) as TextView
            label.setText(section.label)
            if (sectionIndex == 0) label.updatePadding(top = 0)
            list.addView(label)
            val gap = resources.getDimensionPixelSize(R.dimen.settings_card_gap)
            section.entries.forEachIndexed { index, entry ->
                val row = ItemSettingHomeRowBinding.inflate(layoutInflater, list, false)
                row.rowIcon.setImageResource(entry.icon)
                row.rowTitle.setText(entry.title)
                SettingsRowBackground.apply(
                    row.root,
                    first = index == 0,
                    last = index == section.entries.lastIndex,
                )
                row.root.setOnClickListener { openScreen(entry) }
                if (index > 0) {
                    (row.root.layoutParams as LinearLayout.LayoutParams).topMargin = gap
                }
                list.addView(row.root)
            }
        }
    }

    private fun openScreen(entry: Entry) {
        navigateSafely(
            R.id.categorySettingsFragment,
            bundleOf(
                PreferenceFragmentCompat.ARG_PREFERENCE_ROOT to entry.screenKey,
                MainPreferenceFragment.ARG_TITLE to getString(entry.title),
            ),
        )
    }

    private fun openSystemImeSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Timber.w(it, "Cannot open input method settings") }
    }

    private fun isImeEnabled(): Boolean {
        val context = context ?: return false
        return getSystemService(context, InputMethodManager::class.java)
            ?.enabledInputMethodList?.any { it.packageName == context.packageName } == true
    }

    private fun isImeSelected(): Boolean {
        val context = context ?: return false
        val current = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        return current.substringBefore('/') == context.packageName
    }

    /** 有効で選択済みならお知らせは出さない。無効 → 「オンにする」、別のキーボード → 「切り替える」。 */
    private fun updateImeStatus() {
        val b = _binding ?: return
        val enabled = isImeEnabled()
        val selected = enabled && isImeSelected()
        b.statusBanner.isVisible = !selected
        if (selected) return
        val (container, onContainer) = if (!enabled) {
            com.google.android.material.R.attr.colorErrorContainer to
                com.google.android.material.R.attr.colorOnErrorContainer
        } else {
            com.google.android.material.R.attr.colorSecondaryContainer to
                com.google.android.material.R.attr.colorOnSecondaryContainer
        }
        val containerColor = MaterialColors.getColor(b.statusBanner, container)
        val onColor = MaterialColors.getColor(b.statusBanner, onContainer)
        b.statusBanner.setCardBackgroundColor(containerColor)
        b.statusIcon.imageTintList = ColorStateList.valueOf(onColor)
        b.statusTitle.setTextColor(onColor)
        b.statusAction.setTextColor(onColor)
        b.statusTitle.setText(if (enabled) R.string.st_banner_not_selected else R.string.st_banner_disabled)
        b.statusAction.setText(if (enabled) R.string.st_banner_switch else R.string.st_banner_enable)
        b.statusAction.setOnClickListener {
            if (enabled) {
                getSystemService(requireContext(), InputMethodManager::class.java)?.showInputMethodPicker()
            } else {
                openSystemImeSettings()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateImeStatus()
    }

    private fun loadInitialData() {
        if (initializationJob?.isActive == true) return
        val ui = loadingUi ?: return
        initializationJob = viewLifecycleOwner.lifecycleScope.launch {
            ui.load {
                settingsIo(SettingsLoadStage.DATABASE) { settingDataInitializer.initializeIfNeeded() }
            }
        }
    }

    private val focusListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus) updateImeStatus()
    }

    override fun onDestroyView() {
        view?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(focusListener)
        initializationJob = null
        loadingUi = null
        super.onDestroyView()
        _binding = null
    }
}

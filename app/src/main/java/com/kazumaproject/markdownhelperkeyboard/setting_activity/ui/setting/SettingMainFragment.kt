package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.color.MaterialColors
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSettingMainBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemSettingCategoryBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 設定ホーム。上部にキーボードの状態カード（有効化/選択の確認とシステム設定への導線）、
 * 試し入力欄、その下にカテゴリ一覧。各カテゴリは [MainPreferenceFragment] を
 * pref_main.xml の PreferenceScreen(key=screen_*) を rootKey として開く。
 */
@AndroidEntryPoint
class SettingMainFragment : Fragment() {

    private var _binding: FragmentSettingMainBinding? = null
    private val binding get() = _binding!!

    private var loadingUi: SettingsLoadingUi? = null
    private var initializationJob: kotlinx.coroutines.Job? = null

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var settingDataInitializer: SettingDataInitializer

    private data class Category(
        val screenKey: String,
        @StringRes val title: Int,
        @StringRes val summary: Int,
        @DrawableRes val icon: Int,
    )

    private val categories: List<Category>
        get() = buildList {
            add(Category("screen_keyboard", R.string.home_category_keyboard, R.string.home_category_keyboard_summary, com.kazumaproject.core.R.drawable.keyboard_24px))
            add(Category("screen_conversion", R.string.home_category_conversion, R.string.home_category_conversion_summary, R.drawable.ic_home_conversion))
            add(Category("screen_dictionary", R.string.home_category_dictionary, R.string.home_category_dictionary_summary, com.kazumaproject.core.R.drawable.dictionary_24px))
            if (AppVariantConfig.hasZenz) {
                add(Category("screen_zenz", R.string.home_category_zenz, R.string.home_category_zenz_summary, R.drawable.ic_home_zenz))
            }
            add(Category("screen_symbol_clipboard", R.string.home_category_symbol_clipboard, R.string.home_category_symbol_clipboard_summary, com.kazumaproject.core.R.drawable.content_paste_24px))
            add(Category("screen_backup", R.string.home_category_backup, R.string.home_category_backup_summary, R.drawable.ic_home_backup))
            add(Category("screen_about", R.string.home_category_about, R.string.home_category_about_summary, R.drawable.ic_home_info))
        }

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

        buildCategoryList()
        binding.statusEnableButton.setOnClickListener { openSystemImeSettings() }
        binding.statusSwitchButton.setOnClickListener {
            getSystemService(requireContext(), InputMethodManager::class.java)?.showInputMethodPicker()
        }
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

    private fun buildCategoryList() {
        val list = binding.categoryList
        list.removeAllViews()
        categories.forEach { category ->
            val row = ItemSettingCategoryBinding.inflate(layoutInflater, list, false)
            row.categoryIcon.setImageResource(category.icon)
            row.categoryTitle.setText(category.title)
            row.categorySummary.setText(category.summary)
            row.root.setOnClickListener { openCategory(category) }
            list.addView(row.root)
        }
    }

    private fun openCategory(category: Category) {
        navigateSafely(
            R.id.categorySettingsFragment,
            bundleOf(
                PreferenceFragmentCompat.ARG_PREFERENCE_ROOT to category.screenKey,
                MainPreferenceFragment.ARG_TITLE to getString(category.title),
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

    private fun updateImeStatus() {
        val b = _binding ?: return
        val enabled = isImeEnabled()
        val selected = enabled && isImeSelected()
        val (title, message) = when {
            selected -> R.string.home_status_ready_title to R.string.home_status_ready_message
            enabled -> R.string.home_status_not_selected_title to R.string.home_status_not_selected_message
            else -> R.string.home_status_disabled_title to R.string.home_status_disabled_message
        }
        b.statusTitle.setText(title)
        b.statusMessage.setText(message)
        b.statusIcon.setImageResource(
            if (selected) R.drawable.ic_home_status_ok else R.drawable.ic_home_status_warning
        )
        val tintAttr = if (selected) {
            androidx.appcompat.R.attr.colorPrimary
        } else {
            androidx.appcompat.R.attr.colorError
        }
        b.statusIcon.imageTintList =
            ColorStateList.valueOf(MaterialColors.getColor(b.statusIcon, tintAttr))
        b.statusEnableButton.isVisible = !enabled
        b.statusSwitchButton.isVisible = enabled && !selected
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

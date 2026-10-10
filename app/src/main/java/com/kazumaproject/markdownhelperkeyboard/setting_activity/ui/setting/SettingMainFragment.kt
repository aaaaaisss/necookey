package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSettingMainBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

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

        loadInitialData()

        // 設定は 1 画面（セクション構成）。タブ/ViewPager は廃止。
        if (childFragmentManager.findFragmentById(R.id.setting_container) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.setting_container, MainPreferenceFragment())
                .commit()
        }

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

    private fun loadInitialData() {
        if (initializationJob?.isActive == true) return
        val ui = loadingUi ?: return
        val context = requireContext().applicationContext
        initializationJob = viewLifecycleOwner.lifecycleScope.launch {
            ui.load {
                settingsIo(SettingsLoadStage.DATABASE) { settingDataInitializer.initializeIfNeeded() }
                checkKeyboardEnabled(context)
            }
        }
    }

    private suspend fun checkKeyboardEnabled(context: android.content.Context) {
        val enabled = settingsIo(SettingsLoadStage.KEYBOARD_STATUS) {
            getSystemService(context, InputMethodManager::class.java)
                ?.enabledInputMethodList?.any { it.packageName == context.packageName }
        }
        if (enabled == false && isResumed) navigateSafely(R.id.enableKeyboardFragment)
    }

    override fun onResume() {
        super.onResume()
        if (initializationJob?.isActive != true) {
            val ui = loadingUi ?: return
            val context = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch { ui.load { checkKeyboardEnabled(context) } }
        }
    }

    override fun onDestroyView() {
        initializationJob = null
        loadingUi = null
        super.onDestroyView()
        _binding = null
    }

}

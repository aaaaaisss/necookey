package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.learning.nextword.NextWordEntity
import com.kazumaproject.markdownhelperkeyboard.learning.nextword.NextWordRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 辞書 tab: system n-gram, learning dictionary, following words (後続語), user dictionary and
 * the Mozc UT dictionary toggles (external dictionary settings screen).
 */
@AndroidEntryPoint
class DictionaryPreferenceFragment : AsyncPreferenceFragment() {
    override val preferencesXmlRes: Int = R.xml.pref_dictionary

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var nextWordRepository: NextWordRepository

    override fun onPreferencesReady(savedInstanceState: Bundle?, rootKey: String?) {

        findPreference<Preference>("external_dictionary_settings_preference")?.setOnPreferenceClickListener {
            navigateSafely(R.id.externalDictionarySettingsFragment)
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

        val learnDictionaryPrefixSeekBar =
            findPreference<SeekBarPreference>("learn_prediction_preference")
        learnDictionaryPrefixSeekBar?.apply {
            appPreference.learn_prediction_preference.let {
                summary = resources.getString(R.string.learn_dictionary_prefix_match_summary, it)
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary =
                    resources.getString(
                        R.string.learn_dictionary_prefix_match_summary,
                        newValue as Int
                    )
                true
            }
        }

        val userDictionaryPrefixSeekBar =
            findPreference<SeekBarPreference>("user_dictionary_prefix_match_number")
        userDictionaryPrefixSeekBar?.apply {
            appPreference.user_dictionary_prefix_match_number_preference?.let {
                summary = resources.getString(R.string.user_dictionary_prefix_match_summary, it)
            }
            setOnPreferenceChangeListener { _, newValue ->
                summary =
                    resources.getString(
                        R.string.user_dictionary_prefix_match_summary,
                        newValue as Int
                    )
                true
            }
        }
    }

    private fun refreshNextWordSummary() {
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

    private companion object {
        const val NEXT_WORD_VIEW_LIMIT = 300
    }
}

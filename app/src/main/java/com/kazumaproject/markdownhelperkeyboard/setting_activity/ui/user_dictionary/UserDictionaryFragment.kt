package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.user_dictionary

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.DictionaryWordInput
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentUserDictionaryBinding
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingsListCardDecoration
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingsListCards
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.adapter.UserWordAdapter
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileOutputStream
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UserDictionaryFragment : Fragment() {

    private val viewModel: UserDictionaryViewModel by viewModels()

    private var _binding: FragmentUserDictionaryBinding? = null
    private val binding get() = _binding!!
    private lateinit var userWordAdapter: UserWordAdapter
    private var allUserWords: List<UserWord> = emptyList()
    private var userDictionarySearchQuery: String = ""

    // Export
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    exportWords(uri)
                }
            }
        }

    // Import
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    importWords(uri)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserDictionaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()
        setupRecyclerView()
        setupSearch()
        setupListeners()
        observeViewModel()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.user_dictionary_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // [CHANGE] メニューからのトグルロジックはここには含めない
                return when (menuItem.itemId) {
                    R.id.action_export -> {
                        launchExportFilePicker()
                        true
                    }

                    R.id.action_import -> {
                        launchImportFilePicker()
                        true
                    }

                    R.id.action_other_dict_import ->{
                        showOtherDictImportDialog()
                        true
                    }

                    R.id.action_about -> {
                        showExplanationDialog()
                        true
                    }

                    R.id.action_delete_all -> {
                        showDeleteAllConfirmationDialog()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun launchExportFilePicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Set the type to plain text and the extension to .txt
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "user_dictionary_backup.txt")
        }
        exportLauncher.launch(intent)
    }

    private fun launchImportFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        importLauncher.launch(intent)
    }

    private fun showExplanationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.about_user_dictionary)
            .setMessage(R.string.user_dictionary_explanation)
            .setPositiveButton(R.string.st_close, null)
            .show()
    }

    private fun showDeleteAllConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.st_user_words_delete_all)
            .setPositiveButton(R.string.st_delete) { _, _ ->
                viewModel.deleteAll()
                Toast.makeText(
                    context,
                    getString(R.string.deleted_all_words_text),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.st_cancel, null)
            .show()
    }

    private fun exportWords(uri: Uri) {
        viewModel.allWords.value?.let { words ->
            if (words.isEmpty()) {
                Toast.makeText(
                    context,
                    getString(R.string.there_is_no_word_to_export),
                    Toast.LENGTH_SHORT
                )
                    .show()
                return
            }
            try {
                // Convert the list of words to a plain JSON string
                val jsonString = Gson().toJson(words)

                // Write the plain JSON string to the output file
                requireContext().contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { fos ->
                        fos.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                }
                Toast.makeText(
                    context,
                    getString(R.string.success_to_export_string),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    context,
                    getString(R.string.fail_to_export_string),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private var pendingOtherDictFormat: OtherDictFormat = OtherDictFormat.AUTO

    private val otherDictImportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    importOtherDict(uri, pendingOtherDictFormat)
                }
            }
        }

    private fun readTextWithCharsetGuess(uri: Uri): String {
        val bytes = requireContext().contentResolver.openInputStream(uri)!!.use { it.readBytes() }

        fun startsWith(vararg sig: Int): Boolean {
            if (bytes.size < sig.size) return false
            for (i in sig.indices) if (bytes[i].toInt() and 0xFF != sig[i]) return false
            return true
        }

        val charset = when {
            startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8                 // UTF-8 BOM
            startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE                     // UTF-16 LE BOM
            startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE                     // UTF-16 BE BOM
            // NUL混入が目立つならUTF-16系の可能性が高い
            bytes.count { it == 0.toByte() } > bytes.size / 10 -> Charsets.UTF_16LE
            else -> Charsets.UTF_8
        }

        // UTF-8でコケる/文字化けがひどい端末用に Shift_JIS をフォールバック
        return try {
            String(bytes, charset)
        } catch (_: Exception) {
            String(bytes, charset("Shift_JIS"))
        }
    }

    private fun showOtherDictImportDialog() {
        val items = arrayOf("自動判定", "Google 日本語入力", "Microsoft IME")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.st_other_ime_format)
            .setItems(items) { _, which ->
                pendingOtherDictFormat = when (which) {
                    1 -> OtherDictFormat.GOOGLE_JP_INPUT
                    2 -> OtherDictFormat.MICROSOFT_IME
                    else -> OtherDictFormat.AUTO
                }
                launchOtherDictFilePicker()
            }
            .show()
    }

    private fun importOtherDict(uri: Uri, format: OtherDictFormat) {
        try {
            val text = readTextWithCharsetGuess(uri)
            val words = parseOtherDictText(text, format)

            if (words.isEmpty()) {
                Toast.makeText(context, "辞書エントリが見つかりませんでした", Toast.LENGTH_LONG).show()
                return
            }

            viewModel.insertAll(words)
            Toast.makeText(context, "${words.size} 件インポートしました", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, getString(R.string.fail_to_import_string), Toast.LENGTH_LONG).show()
        }
    }

    private fun parseOtherDictText(text: String, format: OtherDictFormat): List<UserWord> {
        return OtherDictionaryUserWordParser.parse(text, format)
    }

    private fun launchOtherDictFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            // 端末によっては text/* だと出ないことがあるので保険で
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/tab-separated-values", "text/*"))
        }
        otherDictImportLauncher.launch(intent)
    }

    private fun importWords(uri: Uri) {
        try {
            // Read the bytes from the input file and convert to a string
            val jsonString = requireContext().contentResolver.openInputStream(uri)?.use {
                it.reader(Charsets.UTF_8).readText()
            }

            if (jsonString != null) {
                // Convert the JSON string back to a list of UserWord objects
                val type = object : TypeToken<List<UserWord>>() {}.type
                val words: List<UserWord> = Gson().fromJson(jsonString, type)

                // Insert the words into the database
                viewModel.insertAll(words.map {
                    UserWord(
                        id = 0,
                        word = it.word,
                        reading = it.reading,
                        posIndex = it.posIndex,
                        posScore = it.posScore
                    )
                })
                Toast.makeText(
                    context,
                    "${words.size} ${getString(R.string.import_text_string)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                getString(R.string.fail_to_import_string),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupRecyclerView() {
        userWordAdapter = UserWordAdapter { userWord ->
            showEditDialog(userWord)
        }
        binding.recyclerViewUserWords.adapter = userWordAdapter
        binding.recyclerViewUserWords.addItemDecoration(SettingsListCardDecoration())
        binding.userDictionaryFastScroller.attachToRecyclerView(binding.recyclerViewUserWords)
    }

    private fun setupSearch() {
        binding.editTextSearchUserDictionary.addTextChangedListener { text ->
            userDictionarySearchQuery = text?.toString().orEmpty()
            applyUserDictionaryFilter()
        }
    }

    private fun setupListeners() {
        binding.fabAddWord.setOnClickListener { showAddDialog() }
    }

    private fun observeViewModel() {
        viewModel.allWords.observe(viewLifecycleOwner) { words ->
            words?.let {
                allUserWords = it
                applyUserDictionaryFilter()
            }
        }
    }

    private fun applyUserDictionaryFilter() {
        val binding = _binding ?: return
        val query = userDictionarySearchQuery.trim()
        val filteredWords = if (query.isEmpty()) {
            allUserWords
        } else {
            allUserWords
                .filter {
                    it.word.startsWith(query, ignoreCase = true) ||
                        it.reading.startsWith(query, ignoreCase = true)
                }
                .sortedWith(compareBy<UserWord> {
                    userWordSearchRank(it, query)
                }.thenBy {
                    userWordMatchedLength(it, query)
                }.thenBy {
                    it.reading
                }.thenBy {
                    it.word
                })
        }
        userWordAdapter.submitList(filteredWords)
        binding.emptyState.root.isVisible = filteredWords.isEmpty()
        if (filteredWords.isEmpty()) {
            if (query.isEmpty()) {
                SettingsListCards.bindEmpty(binding.emptyState.root, R.drawable.ic_ms_dictionary, R.string.st_user_words_empty)
            } else {
                SettingsListCards.bindEmpty(binding.emptyState.root, R.drawable.ic_ms_search_off, R.string.st_no_results)
            }
        }
        binding.recyclerViewUserWords.post {
            _binding?.userDictionaryFastScroller?.invalidate()
        }
    }

    private fun userWordSearchRank(userWord: UserWord, query: String): Int {
        return when {
            userWord.word.equals(query, ignoreCase = true) ||
                userWord.reading.equals(query, ignoreCase = true) -> 0

            userWord.word.startsWith(query, ignoreCase = true) ||
                userWord.reading.startsWith(query, ignoreCase = true) -> 1

            else -> 2
        }
    }

    private fun userWordMatchedLength(userWord: UserWord, query: String): Int {
        return sequenceOf(userWord.reading, userWord.word)
            .filter { it.startsWith(query, ignoreCase = true) }
            .minOfOrNull { it.length } ?: Int.MAX_VALUE
    }

    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_word, null)
        val editWord = dialogView.findViewById<EditText>(R.id.edit_text_word)
        val editReading = dialogView.findViewById<EditText>(R.id.edit_text_reading)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.st_user_word_add)
            .setView(dialogView)
            .setPositiveButton(R.string.st_user_word_register, null)
            .setNegativeButton(R.string.st_cancel, null)
            .create()
        dialog.setOnShowListener {
            editWord.requestFocus()
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (addWord(editWord.text.toString(), editReading.text.toString())) {
                    dialog.dismiss()
                }
            }
        }
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    /** 登録できたら true（読み・単語が空なら案内を出してダイアログは閉じない）。 */
    private fun addWord(wordText: String, readingText: String): Boolean {
        val word = DictionaryWordInput.normalize(
            wordText,
            preserveWhitespace = false,
        )
        val reading = readingText.trim()

        if (word == null || reading.isEmpty()) {
            Toast.makeText(
                context,
                getString(R.string.enter_word_and_yomi_string),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        val newUserWord =
            UserWord(
                word = word,
                reading = reading,
                posIndex = viewModel.defaultPosIndex,
                posScore = 4000
            )
        viewModel.insert(newUserWord)

        Toast.makeText(
            context,
            "「$word${getString(R.string.is_registered_string)}",
            Toast.LENGTH_SHORT
        ).show()
        return true
    }

    private fun showEditDialog(userWord: UserWord) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_word, null)
        val editWord = dialogView.findViewById<EditText>(R.id.edit_text_word_dialog)
        val editReading = dialogView.findViewById<EditText>(R.id.edit_text_reading_dialog)
        val posDropdown = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.spinner_pos_dialog)
        val editPosScore = dialogView.findViewById<EditText>(R.id.edit_text_pos_score_dialog)

        val posList = viewModel.posList
        var selectedPosIndex = userWord.posIndex.takeIf { it in posList.indices } ?: 0
        posDropdown.setSimpleItems(posList.toTypedArray())
        posDropdown.setText(posList.getOrNull(selectedPosIndex).orEmpty(), false)
        posDropdown.setOnItemClickListener { _, _, position, _ -> selectedPosIndex = position }

        editWord.setText(userWord.word)
        editReading.setText(userWord.reading)
        editPosScore.setText(userWord.posScore.toString())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.st_user_word_edit)
            .setView(dialogView)
            .setPositiveButton(R.string.st_save) { _, _ ->
                val updatedWord = DictionaryWordInput.normalize(
                    editWord.text.toString(),
                    preserveWhitespace = false,
                )
                val updatedReading = editReading.text.toString().trim()
                val updatedPosIndex = selectedPosIndex
                val updatedPosScore = editPosScore.text.toString().toIntOrNull()
                    ?: UserDictionaryViewModel.DEFAULT_SCORE

                if (updatedWord != null && updatedReading.isNotEmpty()) {
                    val updatedUserWord = userWord.copy(
                        word = updatedWord,
                        reading = updatedReading,
                        posIndex = updatedPosIndex,
                        posScore = updatedPosScore
                    )
                    viewLifecycleOwner.lifecycleScope.launch {
                        when (viewModel.updateSafely(updatedUserWord)) {
                            UserWordUpdateResult.Updated -> {
                                Toast.makeText(
                                    context,
                                    getString(R.string.updated_string),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            UserWordUpdateResult.Duplicate -> {
                                Toast.makeText(
                                    context,
                                    getString(R.string.user_dictionary_duplicate_entry),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            UserWordUpdateResult.Error -> {
                                Toast.makeText(
                                    context,
                                    getString(R.string.update_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton(R.string.st_cancel, null)
            .setNeutralButton(R.string.st_delete) { _, _ ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.st_delete_confirm, userWord.word))
                    .setPositiveButton(R.string.st_delete) { _, _ ->
                        viewModel.delete(userWord.id)
                        Toast.makeText(
                            context,
                            getString(R.string.deleted_string),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .setNegativeButton(R.string.st_cancel, null)
                    .show()
            }
            .show()
    }

    override fun onDestroyView() {
        binding.userDictionaryFastScroller.detachFromRecyclerView()
        super.onDestroyView()
        _binding = null
    }
}

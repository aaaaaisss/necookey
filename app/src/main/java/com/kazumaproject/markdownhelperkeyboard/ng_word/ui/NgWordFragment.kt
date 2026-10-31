package com.kazumaproject.markdownhelperkeyboard.ng_word.ui

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentNgWordBinding
import com.kazumaproject.markdownhelperkeyboard.ng_word.NgWordBackup
import com.kazumaproject.markdownhelperkeyboard.ng_word.adapter.NgWordAdapter
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWordMatchMode
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingsListCardDecoration
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingsListCards
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileOutputStream

@AndroidEntryPoint
class NgWordFragment : Fragment() {

    private val matchModes = NgWordMatchMode.values().toList()

    private val viewModel: NgWordViewModel by viewModels()
    private var _binding: FragmentNgWordBinding? = null
    private val binding get() = _binding!!

    // Export launcher
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { exportToUri(it) }
        }
    }

    // Import launcher
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { importFromUri(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNgWordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupFab()
        observeViewModel()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.ng_word_menu, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_export -> {
                        launchExportPicker()
                        true
                    }

                    R.id.action_import -> {
                        launchImportPicker()
                        true
                    }

                    R.id.action_delete_all -> {
                        showDeleteAllDialog()
                        true
                    }

                    android.R.id.home -> {
                        findNavController().popBackStack()
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun launchExportPicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "ng_words.json")
        }
        exportLauncher.launch(intent)
    }

    private fun launchImportPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        importLauncher.launch(intent)
    }

    private fun showDeleteAllDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.st_ng_delete_all)
            .setPositiveButton(R.string.st_delete) { _, _ ->
                viewModel.deleteAll()
                Toast.makeText(context, getString(R.string.deleted_string), Toast.LENGTH_SHORT)
                    .show()
            }
            .setNegativeButton(R.string.st_cancel, null)
            .show()
    }

    private fun exportToUri(uri: Uri) {
        val list = viewModel.allNgWords.value ?: emptyList()
        if (list.isEmpty()) {
            Toast.makeText(
                context,
                getString(R.string.there_is_no_word_to_export),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val json = NgWordBackup.toJson(list)
        requireContext().contentResolver.openFileDescriptor(uri, "w")?.use {
            FileOutputStream(it.fileDescriptor).use { fos ->
                fos.write(json.toByteArray(Charsets.UTF_8))
            }
        }
        Toast.makeText(context, getString(R.string.success_to_export_string), Toast.LENGTH_SHORT)
            .show()
    }

    private fun importFromUri(uri: Uri) {
        try {
            val json = requireContext().contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.readText() ?: return
            val list = NgWordBackup.fromJson(json)
            viewModel.insertAll(list)
            Toast.makeText(
                context,
                "${list.size}${getString(R.string.import_text_string)}",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, getString(R.string.fail_to_import_string), Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun setupFab() {
        binding.fabAddNgWord.setOnClickListener { showAddDialog() }
    }

    /** 追加（読み・単語・照合。照合の初期値は部分一致）。読み・単語が空なら案内を出してダイアログは閉じない。 */
    private fun showAddDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_edit_ng_word, null)
        val etYomi = v.findViewById<EditText>(R.id.edit_text_yomi_dialog)
        val etTango = v.findViewById<EditText>(R.id.edit_text_tango_dialog)
        val matchMode = setupMatchModeDropdown(
            v.findViewById(R.id.spinner_ng_word_match_mode),
            NgWordMatchMode.PARTIAL,
        )
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.st_ng_add)
            .setView(v)
            .setPositiveButton(R.string.st_add, null)
            .setNegativeButton(R.string.st_cancel, null)
            .create()
        dialog.setOnShowListener {
            etYomi.requestFocus()
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val yomi = etYomi.text.toString().trim()
                val tango = etTango.text.toString().trim()
                if (yomi.isEmpty() || tango.isEmpty()) {
                    Toast.makeText(
                        context,
                        getString(R.string.enter_word_and_yomi_string),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                viewModel.insert(yomi, tango, matchMode())
                dialog.dismiss()
            }
        }
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun setupRecyclerView() {
        val adapter = NgWordAdapter { ng ->
            showEditDialog(ng)
        }
        binding.recyclerViewNgWords.adapter = adapter
        binding.recyclerViewNgWords.addItemDecoration(SettingsListCardDecoration())
        SettingsListCards.bindEmpty(binding.emptyState.root, R.drawable.ic_ms_block, R.string.st_ng_empty)
    }

    private fun observeViewModel() {
        viewModel.allNgWords.observe(viewLifecycleOwner) { list ->
            (binding.recyclerViewNgWords.adapter as NgWordAdapter)
                .submitList(list)
            binding.emptyState.root.isVisible = list.isNullOrEmpty()
        }
    }

    private fun showEditDialog(item: NgWord) {
        val v = layoutInflater.inflate(R.layout.dialog_edit_ng_word, null)
        val etYomi = v.findViewById<EditText>(R.id.edit_text_yomi_dialog)
        val etTango = v.findViewById<EditText>(R.id.edit_text_tango_dialog)
        val matchMode = setupMatchModeDropdown(
            v.findViewById(R.id.spinner_ng_word_match_mode),
            item.matchMode,
        )
        etYomi.setText(item.yomi)
        etTango.setText(item.tango)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.st_ng_edit)
            .setView(v)
            .setPositiveButton(R.string.st_save) { _, _ ->
                val newY = etYomi.text.toString().trim()
                val newT = etTango.text.toString().trim()
                if (newY.isNotEmpty() && newT.isNotEmpty()) {
                    viewModel.update(
                        item.copy(
                            yomi = newY,
                            tango = newT,
                            matchMode = matchMode(),
                        )
                    )
                }
            }
            .setNeutralButton(R.string.st_delete) { _, _ ->
                viewModel.delete(item)
            }
            .setNegativeButton(R.string.st_cancel, null)
            .show()
    }

    /** 照合方式のドロップダウン。今の選択を返す関数を返す。 */
    private fun setupMatchModeDropdown(
        dropdown: MaterialAutoCompleteTextView,
        selectedMode: NgWordMatchMode,
    ): () -> NgWordMatchMode {
        val entries = resources.getStringArray(R.array.ng_word_match_mode_entries)
        var selected = matchModes.indexOf(selectedMode).coerceAtLeast(0)
        dropdown.setSimpleItems(entries)
        dropdown.setText(entries.getOrNull(selected).orEmpty(), false)
        dropdown.setOnItemClickListener { _, _, position, _ -> selected = position }
        return { matchModeAt(selected) }
    }

    private fun matchModeAt(position: Int): NgWordMatchMode =
        matchModes.getOrNull(position) ?: NgWordMatchMode.PARTIAL

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

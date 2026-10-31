package com.kazumaproject.markdownhelperkeyboard.delete_key_flick.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingsListCardDecoration
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingsListCards
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentDeleteKeyFlickTargetsBinding
import com.kazumaproject.markdownhelperkeyboard.delete_key_flick.database.DeleteKeyFlickDeleteTarget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DeleteKeyFlickTargetsFragment : Fragment() {

    private val viewModel: DeleteKeyFlickTargetsViewModel by viewModels()
    private var _binding: FragmentDeleteKeyFlickTargetsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: DeleteKeyFlickTargetsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeleteKeyFlickTargetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupButtons()
        observeTargets()
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.settings_reset_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_reset_to_default -> {
                        showResetDialog()
                        true
                    }

                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerView() {
        adapter = DeleteKeyFlickTargetsAdapter(
            onItemClick = { target -> showSymbolDialog(target) },
            onDeleteClick = { target -> showDeleteDialog(target) }
        )
        binding.deleteKeyFlickTargetsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            SettingsListCards.bindEmpty(binding.emptyState.root, R.drawable.ic_ms_backspace, R.string.st_delete_targets_empty)
            adapter = this@DeleteKeyFlickTargetsFragment.adapter
            addItemDecoration(SettingsListCardDecoration())
        }
    }

    private fun setupButtons() {
        binding.addDeleteKeyFlickTargetButton.setOnClickListener {
            showSymbolDialog(target = null)
        }
    }

    private fun observeTargets() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.targets.collect { targets ->
                    adapter.submitList(targets)
                    binding.emptyState.root.isVisible = targets.isEmpty()
                }
            }
        }
    }

    private fun showSymbolDialog(target: DeleteKeyFlickDeleteTarget?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_single_field, null)
        dialogView.findViewById<TextInputLayout>(R.id.dialog_field_layout).setHint(R.string.st_delete_target_field)
        val editText = dialogView.findViewById<EditText>(R.id.dialog_field).apply {
            setText(target?.symbol.orEmpty())
            setSelection(text.length)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (target == null) {
                    R.string.st_delete_target_add
                } else {
                    R.string.st_delete_target_edit
                }
            )
            .setView(dialogView)
            .setPositiveButton(R.string.st_save, null)
            .setNegativeButton(R.string.st_cancel, null)
            .create()

        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setOnShowListener {
            editText.requestFocus()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val symbol = editText.text?.toString().orEmpty()
                if (!isOneCharacter(symbol)) {
                    toast(getString(R.string.delete_key_flick_target_invalid))
                    return@setOnClickListener
                }

                val onResult: (Boolean) -> Unit = { success ->
                    if (success) {
                        dialog.dismiss()
                    } else {
                        toast(getString(R.string.delete_key_flick_target_duplicate))
                    }
                }

                if (target == null) {
                    viewModel.addSymbol(symbol, onResult)
                } else {
                    viewModel.updateSymbol(target, symbol, onResult)
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteDialog(target: DeleteKeyFlickDeleteTarget) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.st_delete_confirm, target.symbol))
            .setPositiveButton(R.string.st_delete) { _, _ ->
                viewModel.delete(target) { success ->
                    if (!success) {
                        toast(getString(R.string.delete_key_flick_target_delete_last_error))
                    }
                }
            }
            .setNegativeButton(R.string.st_cancel, null)
            .show()
    }

    private fun showResetDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.st_delete_targets_reset)
            .setPositiveButton(R.string.st_reset) { _, _ ->
                viewModel.resetToDefault()
            }
            .setNegativeButton(R.string.st_cancel, null)
            .show()
    }

    private fun isOneCharacter(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.length == 1
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

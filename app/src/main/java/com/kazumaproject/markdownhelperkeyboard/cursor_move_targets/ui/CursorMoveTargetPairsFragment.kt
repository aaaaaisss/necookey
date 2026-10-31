package com.kazumaproject.markdownhelperkeyboard.cursor_move_targets.ui

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
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentCursorMoveTargetPairsBinding
import kotlinx.coroutines.launch

class CursorMoveTargetPairsFragment : Fragment() {

    private val viewModel: CursorMoveTargetPairsViewModel by viewModels()
    private var _binding: FragmentCursorMoveTargetPairsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CursorMoveTargetPairsAdapter

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
        _binding = FragmentCursorMoveTargetPairsBinding.inflate(inflater, container, false)
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
        adapter = CursorMoveTargetPairsAdapter(
            onItemClick = { target -> showTargetPairDialog(target) },
            onDeleteClick = { target -> showDeleteDialog(target) }
        )
        binding.cursorMoveTargetPairsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            SettingsListCards.bindEmpty(binding.emptyState.root, R.drawable.ic_ms_data_array, R.string.st_cursor_pairs_empty)
            adapter = this@CursorMoveTargetPairsFragment.adapter
            addItemDecoration(SettingsListCardDecoration())
        }
    }

    private fun setupButtons() {
        binding.addCursorMoveTargetPairButton.setOnClickListener {
            showTargetPairDialog(target = null)
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

    private fun showTargetPairDialog(target: String?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_single_field, null)
        dialogView.findViewById<TextInputLayout>(R.id.dialog_field_layout).setHint(R.string.st_cursor_pair_field)
        val editText = dialogView.findViewById<EditText>(R.id.dialog_field).apply {
            setText(target.orEmpty())
            setSelection(text.length)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (target == null) {
                    R.string.st_cursor_pair_add
                } else {
                    R.string.st_cursor_pair_edit
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
                val value = editText.text?.toString().orEmpty()
                if (!isTwoCharacters(value)) {
                    toast(getString(R.string.cursor_move_target_pair_invalid))
                    return@setOnClickListener
                }

                val onResult: (Boolean) -> Unit = { success ->
                    if (success) {
                        dialog.dismiss()
                    } else {
                        toast(getString(R.string.cursor_move_target_pair_duplicate))
                    }
                }

                if (target == null) {
                    viewModel.addTargetPair(value, onResult)
                } else {
                    viewModel.updateTargetPair(target, value, onResult)
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteDialog(target: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.st_delete_confirm, target))
            .setPositiveButton(R.string.st_delete) { _, _ ->
                viewModel.deleteTargetPair(target)
            }
            .setNegativeButton(R.string.st_cancel, null)
            .show()
    }

    private fun showResetDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.st_cursor_pairs_reset)
            .setPositiveButton(R.string.st_reset) { _, _ ->
                viewModel.resetToDefault()
            }
            .setNegativeButton(R.string.st_cancel, null)
            .show()
    }

    private fun isTwoCharacters(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.length == 2
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

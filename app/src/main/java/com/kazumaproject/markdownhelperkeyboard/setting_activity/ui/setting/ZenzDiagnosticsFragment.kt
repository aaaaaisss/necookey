package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.zenz.ZenzDiagnosticEntry
import com.kazumaproject.markdownhelperkeyboard.ime_service.zenz.ZenzDiagnosticsStore
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import java.text.DateFormat
import java.util.Date

class ZenzDiagnosticsFragment : Fragment(R.layout.fragment_zenz_diagnostics) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.zenz_diagnostics_refresh)
            .setOnClickListener { render(view) }
        render(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let(::render)
    }

    private fun render(view: View) {
        val context = requireContext()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled = AppVariantConfig.hasZenz &&
            preferences.getBoolean("necookey_two_row_candidate_bar_preference", true) &&
            preferences.getBoolean("necookey_zenz_bunsetsu_gate_preference", true)
        view.findViewById<MaterialTextView>(R.id.zenz_diagnostics_gate_state).setText(
            if (enabled) R.string.zenz_diagnostics_gate_enabled
            else R.string.zenz_diagnostics_gate_disabled,
        )

        val resultView = view.findViewById<MaterialTextView>(R.id.zenz_diagnostics_result)
        val timestampView = view.findViewById<MaterialTextView>(R.id.zenz_diagnostics_timestamp)
        val entry = ZenzDiagnosticsStore.latest(context)
        if (entry == null) {
            resultView.setText(R.string.zenz_diagnostics_not_run)
            timestampView.text = ""
            return
        }

        resultView.text = when (entry.outcome) {
            ZenzDiagnosticEntry.Outcome.CHANGED -> getString(
                R.string.zenz_diagnostics_changed,
                entry.requestedBunsetsuCount,
                entry.changedBunsetsuCount,
            )
            ZenzDiagnosticEntry.Outcome.UNCHANGED -> getString(
                R.string.zenz_diagnostics_unchanged,
                entry.requestedBunsetsuCount,
            )
            ZenzDiagnosticEntry.Outcome.NO_TARGETS -> getString(R.string.zenz_diagnostics_no_targets)
            ZenzDiagnosticEntry.Outcome.ERROR -> getString(R.string.zenz_diagnostics_error)
        }
        val recordedAt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(entry.recordedAtEpochMillis))
        timestampView.text = getString(R.string.zenz_diagnostics_timestamp, recordedAt)
    }
}

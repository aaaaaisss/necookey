package com.kazumaproject.markdownhelperkeyboard.ime_service.zenz

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.Locale

data class ZenzDiagnosticEntry(
    val recordedAtEpochMillis: Long,
    val outcome: Outcome,
    val requestedBunsetsuCount: Int,
    val changedBunsetsuCount: Int,
) {
    enum class Outcome { CHANGED, UNCHANGED, NO_TARGETS, ERROR }
}

/** Stores aggregate-only diagnostics; no text, context, candidate, or model payload is persisted. */
object ZenzDiagnosticsStore {
    private const val KEY_RECORDED_AT = "zenz_diagnostics_recorded_at"
    private const val KEY_OUTCOME = "zenz_diagnostics_outcome"
    private const val KEY_REQUESTED_COUNT = "zenz_diagnostics_requested_count"
    private const val KEY_CHANGED_COUNT = "zenz_diagnostics_changed_count"

    fun recordEvaluation(context: Context, requestedCount: Int, changedCount: Int) {
        val safeRequested = requestedCount.coerceAtLeast(0)
        val safeChanged = changedCount.coerceIn(0, safeRequested)
        write(
            context = context,
            outcome = when {
                safeRequested == 0 -> ZenzDiagnosticEntry.Outcome.NO_TARGETS
                safeChanged > 0 -> ZenzDiagnosticEntry.Outcome.CHANGED
                else -> ZenzDiagnosticEntry.Outcome.UNCHANGED
            },
            requestedCount = safeRequested,
            changedCount = safeChanged,
        )
    }

    fun recordFailure(context: Context) {
        write(context, ZenzDiagnosticEntry.Outcome.ERROR, 0, 0)
    }

    fun latest(context: Context): ZenzDiagnosticEntry? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val timestamp = preferences.getLong(KEY_RECORDED_AT, 0L)
        if (timestamp <= 0L) return null
        val outcome = when (preferences.getString(KEY_OUTCOME, "")?.lowercase(Locale.ROOT)) {
            "changed" -> ZenzDiagnosticEntry.Outcome.CHANGED
            "unchanged" -> ZenzDiagnosticEntry.Outcome.UNCHANGED
            "no_targets" -> ZenzDiagnosticEntry.Outcome.NO_TARGETS
            "error" -> ZenzDiagnosticEntry.Outcome.ERROR
            else -> return null
        }
        return ZenzDiagnosticEntry(
            recordedAtEpochMillis = timestamp,
            outcome = outcome,
            requestedBunsetsuCount = preferences.getInt(KEY_REQUESTED_COUNT, 0).coerceAtLeast(0),
            changedBunsetsuCount = preferences.getInt(KEY_CHANGED_COUNT, 0).coerceAtLeast(0),
        )
    }

    private fun write(
        context: Context,
        outcome: ZenzDiagnosticEntry.Outcome,
        requestedCount: Int,
        changedCount: Int,
    ) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        preferences.edit()
            .putLong(KEY_RECORDED_AT, System.currentTimeMillis())
            .putString(KEY_OUTCOME, outcome.name.lowercase(Locale.ROOT))
            .putInt(KEY_REQUESTED_COUNT, requestedCount)
            .putInt(KEY_CHANGED_COUNT, changedCount)
            .apply()
    }
}

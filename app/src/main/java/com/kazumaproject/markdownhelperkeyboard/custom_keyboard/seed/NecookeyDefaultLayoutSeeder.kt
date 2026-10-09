package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.seed

import android.content.Context
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import timber.log.Timber

/**
 * necookey: the custom keyboard is the main input. On the first launch of a fresh install (no
 * custom layouts yet) store the custom keyboard's built-in flick kana template as a normal custom
 * layout, so `KeyboardType.CUSTOM` (first in the default keyboard order) has something to show.
 * Runs once; a user who deletes the layout later is not re-seeded.
 */
object NecookeyDefaultLayoutSeeder {
    private const val PREFS = "necookey_seed"
    private const val KEY_SEEDED = "default_flick_layout_seeded_v1"

    fun needsSeeding(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SEEDED, false)

    suspend fun seedIfNeeded(context: Context, repository: KeyboardRepository) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        runCatching {
            if (repository.getLayoutsNotFlow().isEmpty()) {
                val id = repository.saveLayout(
                    layout = KeyboardDefaultLayouts.createFlickKanaTemplateLayout(isDefaultKey = true),
                    name = context.getString(R.string.template_flick_kana_cursor),
                    id = null,
                )
                Timber.i("necookey: seeded default flick layout id=%d", id)
            }
            prefs.edit().putBoolean(KEY_SEEDED, true).commit()
        }.onFailure { Timber.e(it, "necookey: default flick layout seeding failed") }
    }
}

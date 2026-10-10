package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.seed

import android.content.Context
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * necookey: the custom keyboard is the only input keyboard, so at least one custom layout must
 * always exist. [ensureDefaultLayout] stores the built-in flick kana template as a normal custom
 * layout whenever there are none: on a fresh install, for existing users upgrading from a version
 * that still had tenkey/QWERTY (checked once at app start via [needsStartupCheck]), and from the
 * IME whenever the user deletes their last layout.
 */
object NecookeyDefaultLayoutSeeder {
    private const val PREFS = "necookey_seed"
    // v2: re-check existing installs once after tenkey/QWERTY/gojuon were removed.
    private const val KEY_CHECKED = "default_flick_layout_checked_v2"
    private val mutex = Mutex()

    fun needsStartupCheck(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CHECKED, false)

    /** Startup variant: runs [ensureDefaultLayout] and records that the check happened. */
    suspend fun seedIfNeeded(context: Context, repository: KeyboardRepository) {
        if (ensureDefaultLayout(context, repository)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_CHECKED, true).commit()
        }
    }

    /**
     * Inserts the default layout when no custom layout exists.
     * @return true when the check completed (layout present or inserted), false on failure.
     */
    suspend fun ensureDefaultLayout(context: Context, repository: KeyboardRepository): Boolean =
        mutex.withLock {
            runCatching {
                if (repository.getLayoutsNotFlow().isEmpty()) {
                    val id = repository.saveLayout(
                        layout = KeyboardDefaultLayouts.createFlickKanaTemplateLayout(isDefaultKey = true),
                        name = context.getString(R.string.template_flick_kana_cursor),
                        id = null,
                    )
                    Timber.i("necookey: inserted default flick layout id=%d", id)
                }
                true
            }.getOrElse {
                Timber.e(it, "necookey: ensuring default flick layout failed")
                false
            }
        }
}

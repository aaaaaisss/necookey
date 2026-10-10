package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryOverrideStore private constructor(
    private val validator: DictionaryOverrideValidator,
    private val prefs: SharedPreferences,
    private val defaultPrefs: SharedPreferences,
    private val baseDirectory: File,
    private val streamOpener: (Uri) -> InputStream?,
    private val nameResolver: (Uri) -> String?,
) {
    private val gson = Gson()

    @Inject
    constructor(
        @ApplicationContext context: Context,
        validator: DictionaryOverrideValidator,
    ) : this(
        validator = validator,
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
        defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context),
        baseDirectory = File(context.filesDir, DIRECTORY_NAME),
        streamOpener = { uri -> context.contentResolver.openInputStream(uri) },
        nameResolver = { uri -> resolveDisplayName(context, uri) },
    )

    constructor(
        directory: File,
        prefs: SharedPreferences,
        defaultPrefs: SharedPreferences,
        validator: DictionaryOverrideValidator = DictionaryOverrideValidator(),
        streamOpener: (Uri) -> InputStream? = { null },
        nameResolver: (Uri) -> String? = { it.lastPathSegment },
    ) : this(
        validator = validator,
        prefs = prefs,
        defaultPrefs = defaultPrefs,
        baseDirectory = directory,
        streamOpener = streamOpener,
        nameResolver = nameResolver,
    )

    val directory: File
        get() = baseDirectory

    val currentRevision: Long
        get() = prefs.getLong(REVISION_PREF_KEY, 0L)

    init {
        // necookey: importing external dictionary files was removed together with the
        // 外部辞書ファイル screen. Drop anything imported earlier so only bundled dictionaries load.
        if (baseDirectory.exists()) baseDirectory.deleteRecursively()
    }

    /** External dictionary overrides are no longer supported (import UI removed). */
    fun hasOverride(@Suppress("UNUSED_PARAMETER") key: DictionaryFileKey): Boolean = false

    fun openOverride(key: DictionaryFileKey): InputStream = FileInputStream(overrideFile(key))

    fun removeOverride(key: DictionaryFileKey) {
        val fileChanged = overrideFile(key).delete()
        val metadataChanged = getOverrideMetadata(key) != null
        val category = DictionaryFileSpecs.get(key).category
        val keyEnabledPref = keyExternalEnabledKey(key)
        val categoryEnabledPref = categoryExternalEnabledKey(category)
        val removeCategoryEnabled =
            DictionaryFileSpecs.forCategory(category).all { it.partOfTripleDictionary }
        applyRevisionedEdit {
            remove(metadataPrefKey(key))
            remove(keyEnabledPref)
            if (removeCategoryEnabled) remove(categoryEnabledPref)
            fileChanged ||
                metadataChanged ||
                prefs.contains(keyEnabledPref) ||
                (removeCategoryEnabled && prefs.contains(categoryEnabledPref))
        }
    }

    fun removeAllOverrides() {
        val fileChanged = directory.exists() && directory.deleteRecursively()
        val stateChanged = prefs.all.any { (key, value) -> isResetAllStateChange(key, value) }
        val englishReadingPreferenceChanged =
            defaultPrefs.contains(ENGLISH_READING_ENABLED_PREFERENCE)
        if (fileChanged || stateChanged || englishReadingPreferenceChanged) {
            synchronized(this) {
                val nextRevision = currentRevision + 1L
                prefs.edit()
                    .clear()
                    .putLong(REVISION_PREF_KEY, nextRevision)
                    .apply()
            }
            if (englishReadingPreferenceChanged) {
                defaultPrefs.edit().remove(ENGLISH_READING_ENABLED_PREFERENCE).apply()
            }
        }
        OptionalDictionaryMigration(defaultPrefs, prefs).migrateIfNeeded(force = true)
    }

    fun getOverrideMetadata(key: DictionaryFileKey): DictionaryOverrideMetadata? {
        val json = prefs.getString(metadataPrefKey(key), null) ?: return null
        return runCatching {
            gson.fromJson<DictionaryOverrideMetadata>(json, metadataType)
        }.getOrNull()
    }

    fun listAllOverrideStates(): List<DictionaryOverrideState> =
        DictionaryFileSpecs.all.map { spec ->
            DictionaryOverrideState(
                spec = spec,
                hasOverride = hasOverride(spec.key),
                metadata = getOverrideMetadata(spec.key),
                externalEnabled = if (spec.partOfTripleDictionary) {
                    isExternalEnabledForCategory(spec.category)
                } else {
                    isExternalEnabledForKey(spec.key)
                },
            )
        }

    fun isExternalEnabledForCategory(@Suppress("UNUSED_PARAMETER") category: DictionaryCategory): Boolean =
        false

    fun isExternalEnabledForKey(@Suppress("UNUSED_PARAMETER") key: DictionaryFileKey): Boolean =
        false

    /**
     * necookey: the optional bundled dictionaries are no longer user toggles.
     * Mozc UT 人名 / 地名 / Wikipedia and 読み補正 are always on; NEologd and Web were dropped
     * (assets removed). The English reading dictionary keeps its own switch in 予測変換.
     */
    fun isOptionalBundledEnabled(category: DictionaryCategory): Boolean =
        if (category == DictionaryCategory.ENGLISH_READING) {
            defaultPrefs.getBoolean(ENGLISH_READING_ENABLED_PREFERENCE, true)
        } else {
            category in ALWAYS_ENABLED_OPTIONAL_DICTIONARIES
        }

    fun isValidOverride(@Suppress("UNUSED_PARAMETER") key: DictionaryFileKey): Boolean = false

    fun markInvalid(key: DictionaryFileKey, message: String) {
        val metadata = getOverrideMetadata(key) ?: return
        if (metadata.validationStatus == ValidationStatus.INVALID && metadata.validationMessage == message) return
        applyRevisionedEdit {
            putMetadata(
                metadata.copy(
                    validationStatus = ValidationStatus.INVALID,
                    validationMessage = message,
                )
            )
            true
        }
    }

    private fun SharedPreferences.Editor.putMetadata(metadata: DictionaryOverrideMetadata) {
        putString(metadataPrefKey(metadata.key), gson.toJson(metadata))
    }

    private fun putBooleanIfChanged(key: String, enabled: Boolean) {
        if (prefs.getBoolean(key, false) == enabled && prefs.contains(key)) return
        if (!enabled && !prefs.contains(key)) return
        applyRevisionedEdit {
            putBoolean(key, enabled)
            true
        }
    }

    private fun isResetAllStateChange(key: String, value: Any?): Boolean {
        if (key == REVISION_PREF_KEY || key == OptionalDictionaryMigration.MIGRATION_DONE_KEY) {
            return false
        }
        if (key.startsWith("metadata_") || key.startsWith("external_enabled_")) {
            return true
        }
        if (key.startsWith("optional_bundled_enabled_")) {
            val categoryName = key.removePrefix("optional_bundled_enabled_")
            val category = runCatching { DictionaryCategory.valueOf(categoryName) }.getOrNull()
                ?: return true
            return value as? Boolean != optionalBundledResetValue(category)
        }
        return true
    }

    private fun optionalBundledResetValue(category: DictionaryCategory): Boolean =
        when (category) {
            DictionaryCategory.READING_CORRECTION -> true
            DictionaryCategory.ENGLISH_READING -> true
            DictionaryCategory.PERSON_NAME ->
                defaultPrefs.getBoolean("mozc_ut_person_name_preference", false)
            DictionaryCategory.PLACES ->
                defaultPrefs.getBoolean("mozc_ut_places_preference", false)
            DictionaryCategory.WIKI ->
                defaultPrefs.getBoolean("mozc_ut_wiki_preference", false)
            DictionaryCategory.NEOLOGD ->
                defaultPrefs.getBoolean("mozc_ut_neologd_preference", false)
            DictionaryCategory.WEB ->
                defaultPrefs.getBoolean("mozc_ut_web_preference", false)
            else -> false
        }

    private inline fun applyRevisionedEdit(
        edit: SharedPreferences.Editor.() -> Boolean,
    ): Boolean {
        synchronized(this) {
            val editor = prefs.edit()
            val changed = edit(editor)
            if (changed) {
                editor.putLong(REVISION_PREF_KEY, currentRevision + 1L)
            }
            editor.apply()
            return changed
        }
    }

    private fun ensureDirectory() {
        if (!directory.exists()) directory.mkdirs()
    }

    private fun overrideFile(key: DictionaryFileKey): File = File(directory, savedFileNameForKey(key))

    companion object {
        private const val PREF_NAME = "dictionary_override_store"
        private const val DIRECTORY_NAME = "dictionary_overrides"
        const val REVISION_PREF_KEY = "dictionary_override_revision"
        val ALWAYS_ENABLED_OPTIONAL_DICTIONARIES = setOf(
            DictionaryCategory.READING_CORRECTION,
            DictionaryCategory.PERSON_NAME,
            DictionaryCategory.PLACES,
            DictionaryCategory.WIKI,
        )
        const val ENGLISH_READING_ENABLED_PREFERENCE =
            "english_reading_dictionary_enable_preference"
        private val metadataType = object : TypeToken<DictionaryOverrideMetadata>() {}.type

        fun metadataPrefKey(key: DictionaryFileKey) = "metadata_${key.name}"
        fun savedFileNameForKey(key: DictionaryFileKey) = "${key.name}.bin"
        fun keyExternalEnabledKey(key: DictionaryFileKey) = "external_enabled_key_${key.name}"
        fun categoryExternalEnabledKey(category: DictionaryCategory) =
            "external_enabled_category_${category.name}"
        fun optionalBundledEnabledKey(category: DictionaryCategory) =
            "optional_bundled_enabled_${category.name}"

        private fun resolveDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else {
                        null
                    }
                }
        }.getOrNull()
        }
    }
}

class OptionalDictionaryMigration(
    private val defaultPrefs: SharedPreferences,
    private val overridePrefs: SharedPreferences,
) {
    fun migrateIfNeeded(force: Boolean = false) {
        if (!force && overridePrefs.getBoolean(MIGRATION_DONE_KEY, false)) return

        val editor = overridePrefs.edit()
        LEGACY_KEYS.forEach { (category, legacyKey) ->
            val enabled = defaultPrefs.getBoolean(legacyKey, false)
            editor.putBoolean(DictionaryOverrideStore.optionalBundledEnabledKey(category), enabled)
        }
        editor.putBoolean(MIGRATION_DONE_KEY, true).apply()
    }

    companion object {
        const val MIGRATION_DONE_KEY = "optional_dictionary_migration_done_v1"
        private val LEGACY_KEYS = mapOf(
            DictionaryCategory.PERSON_NAME to "mozc_ut_person_name_preference",
            DictionaryCategory.PLACES to "mozc_ut_places_preference",
            DictionaryCategory.WIKI to "mozc_ut_wiki_preference",
            DictionaryCategory.NEOLOGD to "mozc_ut_neologd_preference",
            DictionaryCategory.WEB to "mozc_ut_web_preference",
        )
    }
}

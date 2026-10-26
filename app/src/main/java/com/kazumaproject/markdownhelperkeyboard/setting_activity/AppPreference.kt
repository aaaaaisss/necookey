package com.kazumaproject.markdownhelperkeyboard.setting_activity

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.core.domain.flick.FlickThresholdShape
import com.kazumaproject.core.domain.flick.TfbiDiagonalRecognitionMode
import com.kazumaproject.core.data.popup.TfbiFlickStartPositionMode
import com.kazumaproject.core.data.popup.TfbiPopupPresentationMode
import com.kazumaproject.custom_keyboard.data.CircularFlickDirection
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.buildEvenCircularRanges
import com.kazumaproject.domain.EmojiSkinToneSupport
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.backup.PrefBackup
import com.kazumaproject.markdownhelperkeyboard.setting_activity.backup.PrefEntry
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import com.kazumaproject.core.R as CoreR

internal object CustomThemeColorPreferenceKeys {
    const val CANDIDATE_TEXT_COLOR = "custom_theme_candidate_text_color"
    const val CANDIDATE_ITEM_BG_COLOR = "custom_theme_candidate_item_bg_color"
    const val CANDIDATE_ITEM_PRESSED_BG_COLOR = "custom_theme_candidate_item_pressed_bg_color"
    const val CANDIDATE_EMPTY_POPUP_BG_COLOR = "custom_theme_candidate_empty_popup_bg_color"
    const val CANDIDATE_EMPTY_POPUP_TEXT_COLOR = "custom_theme_candidate_empty_popup_text_color"
    const val SHORTCUT_ICON_COLOR = "custom_theme_shortcut_icon_color"
}

object AppPreference {


    internal const val GOJUON_KEYBOARD_TYPE_MIGRATION_KEY =
        "gojuon_keyboard_type_migrated_v1"


    const val DEFAULT_CUSTOM_THEME_CANDIDATE_ITEM_BG_COLOR = 0x00000000
    const val DEFAULT_CUSTOM_THEME_CANDIDATE_ITEM_PRESSED_BG_COLOR = 0xFFF0F0F3.toInt()
    const val SHORTCUT_TOOLBAR_HEIGHT_DEFAULT_DP = 36
    const val SHORTCUT_TOOLBAR_HEIGHT_MIN_DP = 32
    const val SHORTCUT_TOOLBAR_HEIGHT_MAX_DP = 72
    const val SHORTCUT_TOOLBAR_ICON_SIZE_DEFAULT_DP = 28
    const val SHORTCUT_TOOLBAR_ICON_SIZE_MIN_DP = 18
    const val SHORTCUT_TOOLBAR_ICON_SIZE_MAX_DP = 56
    const val PREDICTION_LOOKAHEAD_CHARACTER_COUNT_MIN =
        PredictionConfig.MIN_LOOKAHEAD_CHARACTER_COUNT
    const val PREDICTION_LOOKAHEAD_CHARACTER_COUNT_MAX =
        PredictionConfig.MAX_LOOKAHEAD_CHARACTER_COUNT
    const val PREDICTION_LOOKAHEAD_CHARACTER_COUNT_DEFAULT =
        PredictionConfig.DEFAULT_LOOKAHEAD_CHARACTER_COUNT
    const val FLICK_SENSITIVITY_KEY = "flick_sensitivity_preference"
    const val TFBI_DIAGONAL_RECOGNITION_MODE_KEY = "tfbi_diagonal_recognition_mode_preference"
    const val FLICK_TFBI_POPUP_PRESENTATION_KEY = "flick_tfbi_popup_presentation_preference"
    const val FLICK_TFBI_FLICK_START_POSITION_KEY =
        "flick_tfbi_flick_start_position_preference"
    const val TENKEY_USE_THREE_STATE_KEY = "tenkey_use_three_state_keyboard_preference"
    const val TENKEY_NUMBER_SYMBOL_KEY_GAP_KEY = "tenkey_number_symbol_key_gap_preference"
    const val STABILIZE_CANDIDATE_STRIP_HEIGHT_KEY = "stabilize_candidate_strip_height_preference"
    const val CUSTOM_DIRECT_INPUT_REPLACE_COMPOSING_KEY =
        "custom_direct_input_replace_composing_preference"
    const val CUSTOM_KEYMAP_GUIDE_KEY = "flick_keymap_guide"
    const val CUSTOM_KEYBOARD_INPUT_IN_EMPTY_AREAS_KEY =
        "custom_keyboard_input_in_empty_areas_preference"
    internal const val CANDIDATE_HEIGHT_DEFAULTS_MIGRATION_VERSION_KEY =
        "candidate_height_defaults_migration_version_preference"
    private const val MIN_CANDIDATE_VISIBLE_HEIGHT_DP = 30
    private const val MAX_CANDIDATE_VISIBLE_HEIGHT_DP = 300
    private const val CURRENT_CANDIDATE_HEIGHT_DEFAULTS_MIGRATION_VERSION = 1

    private val initializationLock = Any()
    @Volatile private var initialization = CompletableFuture<Unit>()
    private val initializationStartLock = Any()
    private val initializationStarted = AtomicBoolean(false)
    @Volatile private var initialized = false
    @Volatile private var initializingThread: Thread? = null
    private lateinit var loadedPreferences: SharedPreferences
    private val preferences: SharedPreferences
        get() {
            if (!initialized && Thread.currentThread() !== initializingThread) {
                check(initializationStarted.get()) { "AppPreference has not been initialized" }
                initialization.join()
            }
            return loadedPreferences
        }
    private lateinit var appContext: Context
    private var isTabletDevice: Boolean = false
    private val gson = Gson()
    private const val LEGACY_SYMBOL_EMOJI_CANDIDATE_ENABLE_KEY =
        "symbol_emoji_candidate_enable_preference"
    private val circularSlotActionEditableSlots = setOf(
        CircularFlickDirection.SLOT_4,
        CircularFlickDirection.SLOT_5,
        CircularFlickDirection.SLOT_6
    )

    private val CLIPBOARD_HISTORY_ENABLE = Pair("clipboard_history_preference", false)
    private val FLICK_SENSITIVITY = Pair(FLICK_SENSITIVITY_KEY, 100)
    private val TFBI_DIAGONAL_RECOGNITION_MODE = Pair(
        TFBI_DIAGONAL_RECOGNITION_MODE_KEY,
        TfbiDiagonalRecognitionMode.LEGACY.preferenceValue
    )
    private val LEARN_DICTIONARY_PREFERENCE = Pair("learn_dictionary_preference", true)
    private val USER_DICTIONARY_PREFERENCE = Pair("user_dictionary_preference", true)
    private val USER_DICTIONARY_PREFIX_PREFERENCE = Pair("user_dictionary_prefix_match_number", 2)
    private val SYSTEM_NGRAM_DICTIONARY_ENABLE_PREFERENCE =
        Pair("system_ngram_dictionary_enable_preference", true)
    private val N_BEST_PREFERENCE = Pair("n_best_preference", 4)
    private val INCREMENTAL_CONVERSION_SESSION_PREFERENCE =
        Pair("incremental_conversion_session_preference", false)
    private val JAPANESE_PREDICTION_ENABLE_PREFERENCE =
        Pair("japanese_prediction_enable_preference", true)
    private val ENGLISH_PREDICTION_ENABLE_PREFERENCE =
        Pair("english_prediction_enable_preference", true)
    private val SYSTEM_DICTIONARY_PREDICTION_ENABLE_PREFERENCE =
        Pair("system_dictionary_prediction_enable_preference", true)
    private val PREDICTION_MINIMUM_INPUT_LENGTH_PREFERENCE =
        Pair("prediction_minimum_input_length_preference", 3)
    private val SYSTEM_PREDICTION_CANDIDATE_LIMIT_PREFERENCE =
        Pair("system_prediction_candidate_limit_preference", 4)
    private val PREDICTION_LOOKAHEAD_CHARACTER_COUNT_PREFERENCE =
        Pair("prediction_lookahead_preference", PREDICTION_LOOKAHEAD_CHARACTER_COUNT_DEFAULT)
    private val PREDICTION_AGGRESSIVENESS_PREFERENCE =
        Pair("prediction_aggressiveness_preference", "standard")
    private val SYSTEM_USER_DICTIONARY_PREDICTION_ENABLE_PREFERENCE =
        Pair("system_user_dictionary_prediction_enable_preference", true)
    private val READING_CORRECTION_PREDICTION_ENABLE_PREFERENCE =
        Pair("reading_correction_prediction_enable_preference", true)
    private val EXTERNAL_MOZC_PREDICTION_ENABLE_PREFERENCE =
        Pair("external_mozc_prediction_enable_preference", true)
    private val SYMBOL_EMOJI_PREDICTION_ENABLE_PREFERENCE =
        Pair("symbol_emoji_prediction_enable_preference", true)
    private val SYMBOL_CANDIDATE_ENABLE_PREFERENCE =
        Pair("symbol_candidate_enable_preference", true)
    private val EMOJI_CANDIDATE_ENABLE_PREFERENCE =
        Pair("emoji_candidate_enable_preference", true)
    private val EMOTICON_CANDIDATE_ENABLE_PREFERENCE =
        Pair("emoticon_candidate_enable_preference", true)
    private val MOZCUT_PERSON_NAME = Pair("mozc_ut_person_name_preference", false)
    private val MOZCUT_PLACES = Pair("mozc_ut_places_preference", false)
    private val MOZCUT_WIKI = Pair("mozc_ut_wiki_preference", false)
    private val MOZCUT_NEOLOGD = Pair("mozc_ut_neologd_preference", false)
    private val MOZCUT_WEB = Pair("mozc_ut_web_preference", false)



    private val TENKEY_USE_THREE_STATE_KEYBOARD_PREFERENCE =
        Pair(TENKEY_USE_THREE_STATE_KEY, true)
    private val TENKEY_NUMBER_SYMBOL_KEY_GAP_PREFERENCE =
        Pair(TENKEY_NUMBER_SYMBOL_KEY_GAP_KEY, 4)


    private val TENKEY_LAST_INPUT_MODE_PREFERENCE =
        Pair("tenkey_last_input_mode_preference", "japanese")
    private val TENKEY_LAST_INPUT_MODE_PRESENTATION_PREFERENCE =
        Pair("tenkey_last_input_mode_presentation_preference", "native")
    private val TENKEY_LAST_QWERTY_NUMBER_RETURN_TARGET_PREFERENCE =
        Pair("tenkey_last_qwerty_number_return_target_preference", "japanese")
    private val TENKEY_LAST_INPUT_MODE_SAVED_AT_EPOCH_MILLIS_PREFERENCE =
        Pair("tenkey_last_input_mode_saved_at_epoch_millis_preference", 0L)



    private val FLICK_KEYMAP_GUIDE_PREFERENCE =
        Pair(CUSTOM_KEYMAP_GUIDE_KEY, false)
    private val FLICK_GUIDE_TEXT_SIZE_SP_PREFERENCE =
        Pair("flick_guide_text_size_sp_preference", 9)
    private val FLICK_GUIDE_MAX_CHARACTERS_PREFERENCE =
        Pair("flick_guide_max_characters_preference", 1)
    private val HIERARCHICAL_FLICK_MODE_SWITCH_ANGLE_MARGIN =
        Pair("hierarchical_flick_mode_switch_angle_margin_preference", 20)















    private val QWERTY_ZENKAKU_SPACE_PREFERENCE =
        Pair("qwerty_romaji_zenkaku_space_preference", false)

    private val QWERTY_ROMAJI_HANKAKU_NUMBER_PREFERENCE =
        Pair("qwerty_romaji_hankaku_number_preference", false)

    private val QWERTY_ROMAJI_HANKAKU_SYMBOL_PREFERENCE =
        Pair("qwerty_romaji_hankaku_symbol_preference", false)


    private val KEY_POPUP_USE_CUSTOM_COLOR =
        Pair("key_popup_use_custom_color_preference", false)
    private val KEY_POPUP_BACKGROUND_COLOR =
        Pair("key_popup_background_color_preference", Color.WHITE)
    private val KEY_POPUP_TEXT_COLOR =
        Pair("key_popup_text_color_preference", Color.BLACK)

    private val TENKEY_POPUP_SIZE_SCALE_PERCENT =
        Pair("tenkey_popup_size_scale_percent_preference", 100)
    private val TENKEY_POPUP_TEXT_SIZE_SP = Pair("tenkey_popup_text_size_sp_preference", 28.0f)



    private val CANDIDATE_IN_PASSWORD_COMPOSE = Pair("password_compose_preference", false)

    private val TYPE_NULL_INPUT_BEHAVIOR = Pair("type_null_input_behavior_preference", "default")


    private val KEYBOARD_HEIGHT = Pair("keyboard_height_preference", 220)
    private val KEYBOARD_WIDTH = Pair("keyboard_width_preference", 100)
    private val KEYBOARD_POSITION = Pair("keyboard_position_preference", true)
    private val KEYBOARD_VERTICAL_MARGIN_BOTTOM =
        Pair("keyboard_vertical_margin_bottom_preference", 0)

    private val KEYBOARD_HEIGHT_LANDSCAPE = Pair("keyboard_height_landscape_preference", 220)
    private val KEYBOARD_WIDTH_LANDSCAPE = Pair("keyboard_width_landscape_preference", 100)
    private val KEYBOARD_POSITION_LANDSCAPE = Pair("keyboard_position_landscape_preference", true)
    private val KEYBOARD_VERTICAL_MARGIN_BOTTOM_LANDSCAPE =
        Pair("keyboard_vertical_margin_bottom_landscape_preference", 0)

    private val CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE =
        Pair("candidate_view_height_dp_landscape_preference", 60)
    private val CANDIDATE_VIEW_EMPTY_HEIGHT_DP_LANDSCAPE =
        Pair("candidate_view_empty_height_dp_landscape_preference", 60)

    private val OMISSION_SEARCH = Pair("omission_search_preference", false)
    private val UNDO_ENABLE = Pair("undo_enable_preference", false)
    private val SPACE_HANKAKU_ENABLE = Pair("space_key_preference", false)
    private val CUSTOM_DIRECT_MODE_SPACE_HANKAKU_ENABLE =
        Pair("custom_direct_mode_space_hankaku_preference", true)
    const val CANDIDATE_YOMI_MODE_WHOLE = "whole"
    const val DEFAULT_LIVE_CONVERSION_CANDIDATE_YOMI_SIZE = 14
    const val PRESERVE_DICTIONARY_WORD_WHITESPACE_KEY =
        "preserve_dictionary_word_whitespace_preference"
    const val CANDIDATE_YOMI_MODE_RUBY = "ruby"
    const val MIN_LIVE_CONVERSION_CANDIDATE_YOMI_SIZE = 1
    const val MAX_LIVE_CONVERSION_CANDIDATE_YOMI_SIZE = 24
    private val SUMIRE_LAST_INPUT_MODE_PREFERENCE =
        Pair("sumire_last_input_mode_preference", "japanese")
    private val SUMIRE_LAST_INPUT_MODE_PRESENTATION_PREFERENCE =
        Pair("sumire_last_input_mode_presentation_preference", "native")
    private val SUMIRE_LAST_INPUT_MODE_SAVED_AT_EPOCH_MILLIS_PREFERENCE =
        Pair("sumire_last_input_mode_saved_at_epoch_millis_preference", 0L)

    private val DELETE_KEY_HIGH_LIGHT = Pair("henkan_delete_key_action_preference", true)
    private val CUSTOM_KEYBOARD_INPUT_IN_EMPTY_AREAS_PREFERENCE =
        Pair(CUSTOM_KEYBOARD_INPUT_IN_EMPTY_AREAS_KEY, false)
    private val LAST_USED_CUSTOM_KEYBOARD_STABLE_ID =
        Pair("last_used_custom_keyboard_stable_id", "")
    private val REMEMBER_CUSTOM_KEYBOARD_INPUT_MODE_PREFERENCE =
        Pair("remember_custom_keyboard_input_mode_preference", false)
    private const val CUSTOM_KEYBOARD_LAST_DIRECT_MODE_PREFIX =
        "custom_keyboard_last_direct_mode_"
    private const val CUSTOM_KEYBOARD_LAST_ROMAJI_MODE_PREFIX =
        "custom_keyboard_last_romaji_mode_"

    private val KEYBOARD_FLOATING_POSITION_X = Pair("keyboard_floating_position_x", -1)
    private val KEYBOARD_FLOATING_POSITION_Y = Pair("keyboard_floating_position_y", -1)

    private val defaultKeyboardOrderJson = gson.toJson(defaultKeyboardOrder(isTablet = false))
    private val KEYBOARD_ORDER = Pair("keyboard_order_preference", defaultKeyboardOrderJson)

    private val defaultCandidateTabJson = gson.toJson(
        listOf(
            CandidateTab.PREDICTION,
            CandidateTab.CONVERSION,
            CandidateTab.EISUKANA,
        )
    )

    private val CANDIDATE_TAB_ORDER_PREFERENCE =
        Pair("candidate_tab_preference", defaultCandidateTabJson)

    private val SYMBOL_MODE_PREFERENCE = Pair("symbol_mode_preference", "EMOJI")
    private val DEFAULT_EMOJI_SKIN_TONE_PREFERENCE =
        Pair("default_emoji_skin_tone_preference", EmojiSkinToneSupport.DEFAULT_SKIN_TONE)

    private val CANDIDATE_COLUMN_PREFERENCE = Pair("candidate_column_preference", "1")
    private val CANDIDATE_COLUMN_LANDSCAPE_PREFERENCE =
        Pair("candidate_column_landscape_preference", "1")


    private val SHORTCUT_TOOLBAR_HEIGHT_DP_PREFERENCE =
        Pair("shortcut_toolbar_height_dp_preference", SHORTCUT_TOOLBAR_HEIGHT_DEFAULT_DP)
    private val SHORTCUT_TOOLBAR_ICON_SIZE_DP_PREFERENCE =
        Pair("shortcut_toolbar_icon_size_dp_preference", SHORTCUT_TOOLBAR_ICON_SIZE_DEFAULT_DP)

    private val APP_THEME_SEED_COLOR = Pair("app_theme_seed_color_preference", 0x00000000)

    private val KEYBOARD_THEME_MODE =
        Pair("keyboard_theme_mode_preference", "default") // default, light, dark, custom
    private val CUSTOM_THEME_BG_COLOR = Pair("custom_theme_bg_color_preference", Color.WHITE)
    private val CUSTOM_THEME_KEY_COLOR = Pair("custom_theme_key_color_preference", Color.LTGRAY)
    private val CUSTOM_THEME_SPECIAL_KEY_COLOR =
        Pair("custom_theme_special_key_color_preference", Color.GRAY)
    private val CUSTOM_THEME_KEY_TEXT_COLOR =
        Pair("custom_theme_key_text_color_preference", Color.BLACK)
    private val CUSTOM_THEME_SPECIAL_KEY_TEXT_COLOR =
        Pair("custom_theme_special_key_text_color_preference", Color.BLACK)
    private val CUSTOM_THEME_CANDIDATE_TEXT_COLOR =
        Pair(CustomThemeColorPreferenceKeys.CANDIDATE_TEXT_COLOR, Color.BLACK)
    private val CUSTOM_THEME_CANDIDATE_ITEM_BG_COLOR =
        Pair(
            CustomThemeColorPreferenceKeys.CANDIDATE_ITEM_BG_COLOR,
            DEFAULT_CUSTOM_THEME_CANDIDATE_ITEM_BG_COLOR
        )
    private val CUSTOM_THEME_CANDIDATE_ITEM_PRESSED_BG_COLOR =
        Pair(
            CustomThemeColorPreferenceKeys.CANDIDATE_ITEM_PRESSED_BG_COLOR,
            DEFAULT_CUSTOM_THEME_CANDIDATE_ITEM_PRESSED_BG_COLOR
        )
    private val CUSTOM_THEME_CANDIDATE_EMPTY_POPUP_BG_COLOR =
        Pair(CustomThemeColorPreferenceKeys.CANDIDATE_EMPTY_POPUP_BG_COLOR, Color.GRAY)
    private val CUSTOM_THEME_CANDIDATE_EMPTY_POPUP_TEXT_COLOR =
        Pair(CustomThemeColorPreferenceKeys.CANDIDATE_EMPTY_POPUP_TEXT_COLOR, Color.BLACK)
    private val CUSTOM_THEME_SHORTCUT_ICON_COLOR =
        Pair(CustomThemeColorPreferenceKeys.SHORTCUT_ICON_COLOR, Color.BLACK)

    // New variables for Custom Border
    private val CUSTOM_THEME_BORDER_ENABLE = Pair("theme_custom_border_enable", false)
    private val CUSTOM_THEME_BORDER_COLOR = Pair("theme_custom_border_color", Color.BLACK)

    private val DELETE_KEY_LEFT_FLICK_PREFERENCE = Pair("delete_key_flick_left_preference", true)
    private val DEFAULT_CURSOR_MOVE_AFTER_COMMIT_TARGET_PAIRS = listOf(
        "()",
        "[]",
        "{}",
        "<>",
        "「」",
        "（）",
        "［］",
        "｛｝",
        "＜＞",
        "〔〕",
        "〘〙",
        "〚〛",
        "〈〉",
        "《》",
        "«»",
        "‹›",
        "『』",
        "【】"
    )
    private val CURSOR_MOVE_AFTER_COMMIT_TARGET_PAIRS_PREFERENCE = Pair(
        "cursor_move_after_commit_target_pairs_preference",
        gson.toJson(DEFAULT_CURSOR_MOVE_AFTER_COMMIT_TARGET_PAIRS)
    )

    private val KEY_LETTER_SIZE = Pair("key_letter_size_preference", 0.0f)
    private val FLICK_KEY_WIDTH_SCALE_PERCENT =
        Pair("flick_key_width_scale_percent_preference", 160)
    private val FLICK_KEY_HEIGHT_SCALE_PERCENT =
        Pair("flick_key_height_scale_percent_preference", 160)
    private val FLICK_KEY_ICON_SCALE_PERCENT = Pair("flick_key_icon_scale_percent_preference", 80)
    private val FLICK_KEY_TEXT_SIZE_SP = Pair("flick_key_text_size_sp_preference", 16.0f)

    private val FLICK_SPECIAL_KEY_TEXT_SIZE_SP =
        Pair("flick_special_key_text_size_sp_preference", 12.0f)

    private val FLICK_DIRECTIONAL_POPUP_SIZE_SCALE_PERCENT =
        Pair("flick_directional_popup_size_scale_percent_preference", 100)
    private val FLICK_DIRECTIONAL_POPUP_TEXT_SIZE_SP =
        Pair("flick_directional_popup_text_size_sp_preference", 28.0f)
    private val FLICK_CROSS_POPUP_SIZE_SCALE_PERCENT =
        Pair("flick_cross_popup_size_scale_percent_preference", 100)
    private val FLICK_CROSS_POPUP_TEXT_SIZE_SP =
        Pair("flick_cross_popup_text_size_sp_preference", 18.0f)
    private val FLICK_STANDARD_POPUP_SIZE_SCALE_PERCENT =
        Pair("flick_standard_popup_size_scale_percent_preference", 100)
    private val FLICK_STANDARD_POPUP_TEXT_SIZE_SP =
        Pair("flick_standard_popup_text_size_sp_preference", 19.0f)
    private val FLICK_TFBI_POPUP_SIZE_SCALE_PERCENT =
        Pair("flick_tfbi_popup_size_scale_percent_preference", 100)
    private val FLICK_TFBI_POPUP_TEXT_SIZE_SP =
        Pair("flick_tfbi_popup_text_size_sp_preference", 20.0f)
    private val FLICK_TFBI_POPUP_PRESENTATION =
        Pair(FLICK_TFBI_POPUP_PRESENTATION_KEY, TfbiPopupPresentationMode.LEGACY_GRID.preferenceValue)
    private val FLICK_TFBI_FLICK_START_POSITION =
        Pair(
            FLICK_TFBI_FLICK_START_POSITION_KEY,
            TfbiFlickStartPositionMode.TOUCH_POINT.preferenceValue
        )

    private val CANDIDATE_LETTER_SIZE = Pair("candidate_letter_size_preference", 14.0f)

    private val CANDIDATE_VIEW_HEIGHT_DP = Pair("candidate_view_height_dp_preference", 60)
    private val CANDIDATE_VIEW_EMPTY_HEIGHT_DP =
        Pair("candidate_view_empty_height_dp_preference", 60)
    private val CANDIDATE_VIEW_HEIGHT_PORTRAIT_COLUMN_1_DP =
        Pair("candidate_view_height_portrait_column_1_dp_preference", 60)
    private val CANDIDATE_VIEW_HEIGHT_PORTRAIT_COLUMN_2_DP =
        Pair("candidate_view_height_portrait_column_2_dp_preference", 80)
    private val CANDIDATE_VIEW_HEIGHT_PORTRAIT_COLUMN_3_DP =
        Pair("candidate_view_height_portrait_column_3_dp_preference", 100)
    private val CANDIDATE_VIEW_HEIGHT_LANDSCAPE_COLUMN_1_DP =
        Pair("candidate_view_height_landscape_column_1_dp_preference", 60)
    private val CANDIDATE_VIEW_HEIGHT_LANDSCAPE_COLUMN_2_DP =
        Pair("candidate_view_height_landscape_column_2_dp_preference", 80)
    private val CANDIDATE_VIEW_HEIGHT_LANDSCAPE_COLUMN_3_DP =
        Pair("candidate_view_height_landscape_column_3_dp_preference", 100)
    private val CANDIDATE_DEFAULT_HEIGHT_PORTRAIT_COLUMN_1_DP =
        Pair("candidate_default_height_portrait_column_1_dp_preference", 60)
    private val CANDIDATE_DEFAULT_HEIGHT_PORTRAIT_COLUMN_2_DP =
        Pair("candidate_default_height_portrait_column_2_dp_preference", 80)
    private val CANDIDATE_DEFAULT_HEIGHT_PORTRAIT_COLUMN_3_DP =
        Pair("candidate_default_height_portrait_column_3_dp_preference", 100)
    private val CANDIDATE_DEFAULT_HEIGHT_LANDSCAPE_COLUMN_1_DP =
        Pair("candidate_default_height_landscape_column_1_dp_preference", 60)
    private val CANDIDATE_DEFAULT_HEIGHT_LANDSCAPE_COLUMN_2_DP =
        Pair("candidate_default_height_landscape_column_2_dp_preference", 80)
    private val CANDIDATE_DEFAULT_HEIGHT_LANDSCAPE_COLUMN_3_DP =
        Pair("candidate_default_height_landscape_column_3_dp_preference", 100)
    private val CANDIDATE_DEFAULT_EMPTY_HEIGHT_DP =
        Pair(
            "candidate_default_empty_height_dp_preference",
            CANDIDATE_VIEW_EMPTY_HEIGHT_DP.second
        )
    private val CANDIDATE_DEFAULT_EMPTY_HEIGHT_DP_LANDSCAPE =
        Pair(
            "candidate_default_empty_height_dp_landscape_preference",
            CANDIDATE_VIEW_EMPTY_HEIGHT_DP_LANDSCAPE.second
        )
    private val CANDIDATE_HEIGHT_PER_COLUMN_MIGRATED =
        Pair("candidate_height_per_column_migrated_preference", false)

    private val CLIP_BOARD_PREVIEW_PREFERENCE =
        Pair("clipboard_preview_enable_preference", true)

    private val CLIP_BOARD_PREVIEW_TAP_DELETE_PREFERENCE =
        Pair("clipboard_preview_tap_delete_preference", false)

    private val CLIPBOARD_DELETE_UNPINNED_AFTER_HOURS_PREFERENCE =
        Pair("clipboard_delete_unpinned_after_hours_preference", false)

    private val CLIPBOARD_UNPINNED_RETENTION_HOURS_PREFERENCE =
        Pair("clipboard_unpinned_retention_hours_preference", 1)


    private val KEYBOARD_CORNER_RADIUS_DP_PREFERENCE =
        Pair("keyboard_corner_radius_dp_preference", 32)

    private val KEYBOARD_CORNER_TOP_LEFT_PREFERENCE =
        Pair("keyboard_corner_top_left_preference", true)

    private val KEYBOARD_CORNER_TOP_RIGHT_PREFERENCE =
        Pair("keyboard_corner_top_right_preference", true)

    private val KEYBOARD_CORNER_BOTTOM_LEFT_PREFERENCE =
        Pair("keyboard_corner_bottom_left_preference", true)

    private val KEYBOARD_CORNER_BOTTOM_RIGHT_PREFERENCE =
        Pair("keyboard_corner_bottom_right_preference", true)



    private val RECONVERSION_PREFERENCE =
        Pair("reconversion_preference", false)

    private val CONVERSION_KEY_SWIPE_CURSOR_MOVE_PREFERENCE =
        Pair("conversion_key_swipe_cursor_move_preference", false)

    private val PHYSICAL_KEYBOARD_INPUT_MODE_PREFERENCE =
        Pair("physical_keyboard_input_mode_preference", "romaji")

    private val ROMAJI_MAP_DATA_VERSION = Pair("romaji_map_data_version", 0)


    private val TABLET_GOJUON_LAYOUT_PREFERENCE =
        Pair("tablet_use_gojuon_layout_preference", true)

    private val LAST_PASTED_CLIPBOARD_TEXT_PREFERENCE =
        Pair("last_pasted_clipboard_text_preference", "")

    private val TENKEY_SHOW_IME_SWITCH_BUTTON =
        Pair("tenkey_show_switch_ime_button_preference", true)






    // necookey
    private val NECOOKEY_TWO_ROW_CANDIDATE_BAR_PREFERENCE =
        Pair("necookey_two_row_candidate_bar_preference", true)
    private val NECOOKEY_ZENZ_BUNSETSU_GATE_PREFERENCE =
        Pair("necookey_zenz_bunsetsu_gate_preference", true)






    private val LIQUID_GLASS_ENABLE = Pair("liquid_glass_preference", false)
    private val LIQUID_GLASS_BLUR_RADIUS = Pair("liquid_glass_blur_preference", 220)
    private val LIQUID_GLASS_KEY_ALPHA = Pair("liquid_glass_key_alpha_preference", 255)

    private val CUSTOM_THEME_INPUT_COLOR_ENABLE = Pair("theme_custom_input_color_enable", false)
    private val CUSTOM_THEME_PRE_EDIT_BG = Pair(
        "theme_custom_pre_edit_bg_color",
        "#440099CC".toColorInt()
    )
    private val CUSTOM_THEME_PRE_EDIT_TEXT = Pair("theme_custom_pre_edit_text_color", Color.WHITE)
    private val CUSTOM_THEME_POST_EDIT_BG =
        Pair("theme_custom_post_edit_bg_color", "#55FF8800".toColorInt())
    private val CUSTOM_THEME_POST_EDIT_TEXT = Pair("theme_custom_post_edit_text_color", Color.WHITE)


    private val CONVERSION_CANDIDATES_ROMAJI_ENABLE_PREFERENCE =
        Pair("conversion_candidates_romaji_enable_preference", false)

    private val LEARN_FIRST_CANDIDATE_DICTIONARY_PREFERENCE =
        Pair("learn_first_candidate_dictionary_preference", false)

    private val LEARN_DICTIONARY_ALLOW_MIXED_SYMBOLS_NUMBERS_PREFERENCE =
        Pair("learn_dictionary_allow_mixed_symbols_numbers_preference", true)

    private val ENABLE_PREDICTION_SEARCH_LEARN_DICTIONARY_PREFERENCE =
        Pair("enable_prediction_search_learn_dictionary_preference", false)

    private val LEARN_PREDICTION_PREFERENCE = Pair("learn_prediction_preference", 4)
    private val LEARN_PREDICTION_CANDIDATE_LIMIT_PREFERENCE =
        Pair("learn_dictionary_prediction_candidate_limit_preference", 4)
    private val USER_DICTIONARY_PREDICTION_CANDIDATE_LIMIT_PREFERENCE =
        Pair("user_dictionary_prediction_candidate_limit_preference", 4)

    private val PREF_UP_START = Pair("circular_flick_up_start", 225f)
    private val PREF_UP_SWEEP = Pair("circular_flick_up_sweep", 90f)

    private val PREF_RIGHT_START = Pair("circular_flick_right_start", 315f)
    private val PREF_RIGHT_SWEEP = Pair("circular_flick_right_sweep", 90f)

    private val PREF_DOWN_START = Pair("circular_flick_down_start", 45f)
    private val PREF_DOWN_SWEEP = Pair("circular_flick_down_sweep", 90f)

    private val PREF_LEFT_START = Pair("circular_flick_left_start", 135f)
    private val PREF_LEFT_SWEEP = Pair("circular_flick_left_sweep", 90f)

    private val CIRCULAR_FLICK_WINDOW_SCALE = Pair("circular_flick_window_scale", 1.0f)

    private val CUSTOM_THEME_BORDER_WIDTH = Pair("theme_custom_border_width", 1)

    private val PREF_FIVE_DIRECTIONS_ENABLE = Pair("circular_flick_five_directions_enable", false)
    private val PREF_UP_RIGHT_START = Pair("circular_flick_up_right_start", 90f)
    private val PREF_UP_RIGHT_SWEEP = Pair("circular_flick_up_right_sweep", 72f)
    private val PREF_SLOT_5_START = Pair("circular_flick_slot_5_start", 150f)
    private val PREF_SLOT_5_SWEEP = Pair("circular_flick_slot_5_sweep", 60f)
    private val PREF_SLOT_6_START = Pair("circular_flick_slot_6_start", 167.14285f)
    private val PREF_SLOT_6_SWEEP = Pair("circular_flick_slot_6_sweep", 51.42857f)
    private val PREF_CIRCULAR_DIRECTION_COUNT = Pair("circular_flick_direction_count", 4)


    private val KEYBOARD_MARGIN_START_DP = Pair("keyboard_margin_start_dp_preference", 0)
    private val KEYBOARD_MARGIN_END_DP = Pair("keyboard_margin_end_dp_preference", 0)

    private val KEYBOARD_MARGIN_START_DP_LANDSCAPE =
        Pair("keyboard_margin_start_dp_landscape_preference", 0)
    private val KEYBOARD_MARGIN_END_DP_LANDSCAPE =
        Pair("keyboard_margin_end_dp_landscape_preference", 0)

    /** 同音異義 UI の迷い印の判定にリランカーの文節マージン（SumireLmSpanMargin）を使う（既定オフ、設定画面なし）。 */
    private val SUMIRE_LM_HOMOPHONE_MARGIN_PREFERENCE = Pair("sumire_lm_homophone_margin_preference", false)
    /** 案2: 経路探索の辺コストに単語 bigram LM 補正（LatticeLmCost、表はアセット）を足す（既定オン）。 */
    private val LATTICE_LM_COST_PREFERENCE = Pair("lattice_lm_cost_preference", true)

    private val SAVE_LAST_USED_KEYBOARD = Pair("save_last_used_keyboard", false)
    private val SAVE_LAST_USED_KEYBOARD_POSITION = Pair("save_last_used_keyboard_int", 0)

    private val TYPO_CORRECTION_JA_FLICK =
        Pair("enable_typo_correction_japanese_flick_keyboard_preference", false)


    private val CUSTOM_ROMAJI_ZENKAKU_CONVERSION_ENABLE_PREFERENCE =
        Pair("custom_romaji_zenkaku_conversion_enable_preference", true)

    private val OMISSION_SEARCH_OFFSET_SCORE_PREFERENCE =
        Pair("omission_search_preference_offset_score_preference", 1900)

    private val TYPO_CORRECTION_JA_FLICK_OFFSET_SCORE_PREFERENCE =
        Pair("enable_typo_correction_japanese_flick_keyboard_offset_score_preference", 3000)

    fun startInitialization(context: Context) {
        synchronized(initializationStartLock) {
            if (initialization.isCompletedExceptionally) {
                initialization = CompletableFuture()
                initializationStarted.set(false)
            }
            if (!initializationStarted.compareAndSet(false, true)) return
            Thread({
                try {
                    init(context)
                } catch (_: Throwable) {
                    // init completed its future exceptionally; the loading UI owns retry.
                }
            }, "AppPreferenceInit").start()
        }
    }

    suspend fun awaitInitializationSuspending() = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
        // A cancelled screen must not remain retained by a slow initialization future.
        val waiting = java.util.concurrent.atomic.AtomicReference(continuation)
        continuation.invokeOnCancellation { waiting.set(null) }
        initialization.whenComplete { _, error ->
            waiting.getAndSet(null)?.resumeWith(
                if (error == null) Result.success(Unit) else Result.failure(error),
            )
        }
    }

    fun awaitInitialization() {
        initialization.join()
    }

    fun init(context: Context) {
        synchronized(initializationLock) {
            initializationStarted.set(true)
            initializingThread = Thread.currentThread()
            try {
                appContext = context.applicationContext
                // This startup filesystem maintenance used to run in Application.onCreate on main.
                appContext.codeCacheDir.setReadOnly()
                isTabletDevice = context.resources.getBoolean(CoreR.bool.isTablet)
                loadedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                migrateCandidateHeightDefaultsIfNeeded()
                migratePredictionLookaheadPreferenceIfNeeded()
                migrateSymbolEmojiCandidatePreferenceIfNeeded()
                initialized = true
                initialization.complete(Unit)
            } catch (failure: Throwable) {
                initialization.completeExceptionally(failure)
                throw failure
            } finally {
                initializingThread = null
            }
        }
    }

    /**
     * necookey: the custom keyboard is the only input keyboard (a default layout is always
     * ensured by NecookeyDefaultLayoutSeeder).
     */
    private fun defaultKeyboardOrder(isTablet: Boolean = isTabletDevice): List<KeyboardType> {
        return listOf(KeyboardType.CUSTOM)
    }

    private fun parseKeyboardOrder(
        json: String?,
        fallback: List<KeyboardType> = defaultKeyboardOrder(),
        preserveEmpty: Boolean = false,
    ): List<KeyboardType> {
        val type = object : TypeToken<List<KeyboardType?>>() {}.type
        return runCatching {
            val parsed = gson.fromJson<List<KeyboardType?>>(json, type)
                .orEmpty()
                .filterNotNull()
            if (preserveEmpty) parsed else parsed.ifEmpty { fallback }
        }.getOrElse { fallback }
    }

    fun migrateSymbolEmojiCandidatePreferenceIfNeeded() {
        if (!preferences.contains(LEGACY_SYMBOL_EMOJI_CANDIDATE_ENABLE_KEY)) return
        val legacyValue = preferences.getBoolean(
            LEGACY_SYMBOL_EMOJI_CANDIDATE_ENABLE_KEY,
            true,
        )
        preferences.edit {
            if (!preferences.contains(SYMBOL_CANDIDATE_ENABLE_PREFERENCE.first)) {
                it.putBoolean(SYMBOL_CANDIDATE_ENABLE_PREFERENCE.first, legacyValue)
            }
            if (!preferences.contains(EMOJI_CANDIDATE_ENABLE_PREFERENCE.first)) {
                it.putBoolean(EMOJI_CANDIDATE_ENABLE_PREFERENCE.first, legacyValue)
            }
            if (!preferences.contains(EMOTICON_CANDIDATE_ENABLE_PREFERENCE.first)) {
                it.putBoolean(EMOTICON_CANDIDATE_ENABLE_PREFERENCE.first, legacyValue)
            }
            it.remove(LEGACY_SYMBOL_EMOJI_CANDIDATE_ENABLE_KEY)
        }
    }

    private inline fun SharedPreferences.edit(operation: (SharedPreferences.Editor) -> Unit) {
        val editor = edit()
        operation(editor)
        editor.apply()
    }

    private fun normalizeCursorMoveTargetPair(value: String): String? {
        val trimmed = value.trim()
        return if (trimmed.length == 2) trimmed else null
    }



    private fun readIntPreference(key: String, defaultValue: Int): Int {
        return runCatching {
            preferences.getInt(key, defaultValue)
        }.getOrDefault(defaultValue)
    }

    fun resolveShortcutToolbarIconSizeDp(
        toolbarHeightDp: Int = shortcut_toolbar_height_dp_preference,
        iconSizeDp: Int = shortcut_toolbar_icon_size_dp_preference
    ): Int {
        val resolvedToolbarHeightDp = toolbarHeightDp.coerceIn(
            SHORTCUT_TOOLBAR_HEIGHT_MIN_DP,
            SHORTCUT_TOOLBAR_HEIGHT_MAX_DP
        )
        val resolvedIconSizeDp = iconSizeDp.coerceIn(
            SHORTCUT_TOOLBAR_ICON_SIZE_MIN_DP,
            SHORTCUT_TOOLBAR_ICON_SIZE_MAX_DP
        )
        val maxIconSizeForHeightDp = (resolvedToolbarHeightDp - 8)
            .coerceAtLeast(SHORTCUT_TOOLBAR_ICON_SIZE_MIN_DP)
        return resolvedIconSizeDp
            .coerceAtMost(maxIconSizeForHeightDp)
            .coerceIn(
                SHORTCUT_TOOLBAR_ICON_SIZE_MIN_DP,
                SHORTCUT_TOOLBAR_ICON_SIZE_MAX_DP
            )
    }

    private fun normalizeCandidateColumn(column: String): String =
        if (column in setOf("1", "2", "3")) column else "1"

    private fun candidateHeightPreferenceFor(
        isLandscape: Boolean,
        column: String
    ): Pair<String, Int> {
        return when (normalizeCandidateColumn(column)) {
            "2" -> if (isLandscape) {
                CANDIDATE_VIEW_HEIGHT_LANDSCAPE_COLUMN_2_DP
            } else {
                CANDIDATE_VIEW_HEIGHT_PORTRAIT_COLUMN_2_DP
            }

            "3" -> if (isLandscape) {
                CANDIDATE_VIEW_HEIGHT_LANDSCAPE_COLUMN_3_DP
            } else {
                CANDIDATE_VIEW_HEIGHT_PORTRAIT_COLUMN_3_DP
            }

            else -> if (isLandscape) {
                CANDIDATE_VIEW_HEIGHT_LANDSCAPE_COLUMN_1_DP
            } else {
                CANDIDATE_VIEW_HEIGHT_PORTRAIT_COLUMN_1_DP
            }
        }
    }

    private fun candidateDefaultHeightPreferenceFor(
        isLandscape: Boolean,
        column: String
    ): Pair<String, Int> {
        return when (normalizeCandidateColumn(column)) {
            "2" -> if (isLandscape) {
                CANDIDATE_DEFAULT_HEIGHT_LANDSCAPE_COLUMN_2_DP
            } else {
                CANDIDATE_DEFAULT_HEIGHT_PORTRAIT_COLUMN_2_DP
            }

            "3" -> if (isLandscape) {
                CANDIDATE_DEFAULT_HEIGHT_LANDSCAPE_COLUMN_3_DP
            } else {
                CANDIDATE_DEFAULT_HEIGHT_PORTRAIT_COLUMN_3_DP
            }

            else -> if (isLandscape) {
                CANDIDATE_DEFAULT_HEIGHT_LANDSCAPE_COLUMN_1_DP
            } else {
                CANDIDATE_DEFAULT_HEIGHT_PORTRAIT_COLUMN_1_DP
            }
        }
    }

    private fun candidateEmptyHeightPreferenceFor(isLandscape: Boolean): Pair<String, Int> =
        if (isLandscape) {
            CANDIDATE_VIEW_EMPTY_HEIGHT_DP_LANDSCAPE
        } else {
            CANDIDATE_VIEW_EMPTY_HEIGHT_DP
        }

    private fun candidateDefaultEmptyHeightPreferenceFor(isLandscape: Boolean): Pair<String, Int> =
        if (isLandscape) {
            CANDIDATE_DEFAULT_EMPTY_HEIGHT_DP_LANDSCAPE
        } else {
            CANDIDATE_DEFAULT_EMPTY_HEIGHT_DP
        }

    var clipboard_history_enable: Boolean?
        get() = preferences.getBoolean(
            CLIPBOARD_HISTORY_ENABLE.first, CLIPBOARD_HISTORY_ENABLE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CLIPBOARD_HISTORY_ENABLE.first, value ?: false)
        }


    var tenkey_use_three_state_keyboard_preference: Boolean
        get() = preferences.getBoolean(
            TENKEY_USE_THREE_STATE_KEYBOARD_PREFERENCE.first,
            TENKEY_USE_THREE_STATE_KEYBOARD_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(TENKEY_USE_THREE_STATE_KEYBOARD_PREFERENCE.first, value)
        }

    var tenkey_number_symbol_key_gap_preference: Int
        get() = preferences.getInt(
            TENKEY_NUMBER_SYMBOL_KEY_GAP_PREFERENCE.first,
            TENKEY_NUMBER_SYMBOL_KEY_GAP_PREFERENCE.second,
        ).coerceIn(0, 16)
        set(value) = preferences.edit {
            it.putInt(TENKEY_NUMBER_SYMBOL_KEY_GAP_PREFERENCE.first, value.coerceIn(0, 16))
        }





    // Publish a complete restart snapshot together, including the timeout timestamp.
    // Keep apply() semantics: readers and listeners see the new state immediately.
    internal fun saveTenkeyRestartInputMode(
        mode: String,
        presentation: String,
        numberReturnTarget: String,
        savedAtEpochMillis: Long,
    ) = preferences.edit {
        it.putString(TENKEY_LAST_INPUT_MODE_PREFERENCE.first, mode)
        it.putString(TENKEY_LAST_INPUT_MODE_PRESENTATION_PREFERENCE.first, presentation)
        it.putString(TENKEY_LAST_QWERTY_NUMBER_RETURN_TARGET_PREFERENCE.first, numberReturnTarget)
        it.putLong(TENKEY_LAST_INPUT_MODE_SAVED_AT_EPOCH_MILLIS_PREFERENCE.first, savedAtEpochMillis)
    }

    internal fun saveSumireRestartInputMode(
        mode: String,
        presentation: String,
        savedAtEpochMillis: Long,
    ) = preferences.edit {
        it.putString(SUMIRE_LAST_INPUT_MODE_PREFERENCE.first, mode)
        it.putString(SUMIRE_LAST_INPUT_MODE_PRESENTATION_PREFERENCE.first, presentation)
        it.putLong(SUMIRE_LAST_INPUT_MODE_SAVED_AT_EPOCH_MILLIS_PREFERENCE.first, savedAtEpochMillis)
    }









    var flick_keymap_guide_layout: Boolean?
        get() = preferences.getBoolean(
            FLICK_KEYMAP_GUIDE_PREFERENCE.first, FLICK_KEYMAP_GUIDE_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(FLICK_KEYMAP_GUIDE_PREFERENCE.first, value ?: false)
        }




    var flick_guide_text_size_sp_preference: Int?
        get() = preferences.getInt(
            FLICK_GUIDE_TEXT_SIZE_SP_PREFERENCE.first,
            FLICK_GUIDE_TEXT_SIZE_SP_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putInt(
                FLICK_GUIDE_TEXT_SIZE_SP_PREFERENCE.first,
                value ?: FLICK_GUIDE_TEXT_SIZE_SP_PREFERENCE.second
            )
        }

    var flick_guide_max_characters_preference: Int?
        get() = preferences.getInt(
            FLICK_GUIDE_MAX_CHARACTERS_PREFERENCE.first,
            FLICK_GUIDE_MAX_CHARACTERS_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putInt(
                FLICK_GUIDE_MAX_CHARACTERS_PREFERENCE.first,
                value ?: FLICK_GUIDE_MAX_CHARACTERS_PREFERENCE.second
            )
        }




















    var qwerty_enable_zenkaku_space_preference: Boolean?
        get() = preferences.getBoolean(
            QWERTY_ZENKAKU_SPACE_PREFERENCE.first, QWERTY_ZENKAKU_SPACE_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(QWERTY_ZENKAKU_SPACE_PREFERENCE.first, value ?: false)
        }

    var qwerty_romaji_hankaku_number_preference: Boolean?
        get() = preferences.getBoolean(
            QWERTY_ROMAJI_HANKAKU_NUMBER_PREFERENCE.first,
            QWERTY_ROMAJI_HANKAKU_NUMBER_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(QWERTY_ROMAJI_HANKAKU_NUMBER_PREFERENCE.first, value ?: false)
        }

    var qwerty_romaji_hankaku_symbol_preference: Boolean?
        get() = preferences.getBoolean(
            QWERTY_ROMAJI_HANKAKU_SYMBOL_PREFERENCE.first,
            QWERTY_ROMAJI_HANKAKU_SYMBOL_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(QWERTY_ROMAJI_HANKAKU_SYMBOL_PREFERENCE.first, value ?: false)
        }


    var key_popup_use_custom_color: Boolean
        get() = preferences.getBoolean(
            KEY_POPUP_USE_CUSTOM_COLOR.first,
            KEY_POPUP_USE_CUSTOM_COLOR.second
        )
        set(value) = preferences.edit {
            it.putBoolean(KEY_POPUP_USE_CUSTOM_COLOR.first, value)
        }

    var key_popup_background_color: Int
        get() = readIntPreference(
            KEY_POPUP_BACKGROUND_COLOR.first,
            KEY_POPUP_BACKGROUND_COLOR.second
        )
        set(value) = preferences.edit {
            it.putInt(KEY_POPUP_BACKGROUND_COLOR.first, value)
        }

    var key_popup_text_color: Int
        get() = readIntPreference(KEY_POPUP_TEXT_COLOR.first, KEY_POPUP_TEXT_COLOR.second)
        set(value) = preferences.edit {
            it.putInt(KEY_POPUP_TEXT_COLOR.first, value)
        }

    val show_candidates_password: Boolean? get() = true // S3: hardcoded, setting removed

    var show_candidates_password_compose: Boolean?
        get() = preferences.getBoolean(
            CANDIDATE_IN_PASSWORD_COMPOSE.first, CANDIDATE_IN_PASSWORD_COMPOSE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CANDIDATE_IN_PASSWORD_COMPOSE.first, value ?: false)
        }

    var type_null_input_behavior_preference: String?
        get() = preferences.getString(
            TYPE_NULL_INPUT_BEHAVIOR.first,
            TYPE_NULL_INPUT_BEHAVIOR.second
        )
        set(value) = preferences.edit {
            it.putString(TYPE_NULL_INPUT_BEHAVIOR.first, value ?: TYPE_NULL_INPUT_BEHAVIOR.second)
        }


    /** Only the custom keyboard exists; stored legacy orders (tenkey/QWERTY/...) are ignored. */
    var keyboard_order: List<KeyboardType>
        get() = listOf(KeyboardType.CUSTOM)
        set(value) = preferences.edit {
            it.putString(KEYBOARD_ORDER.first, gson.toJson(listOf(KeyboardType.CUSTOM)))
        }

    var candidate_tab_order: List<CandidateTab>
        get() {
            val json = preferences.getString(
                CANDIDATE_TAB_ORDER_PREFERENCE.first,
                CANDIDATE_TAB_ORDER_PREFERENCE.second
            )
            val type = object : TypeToken<List<CandidateTab>>() {}.type
            return gson.fromJson(json, type)
        }
        set(value) = preferences.edit {
            val json = gson.toJson(value)
            it.putString(CANDIDATE_TAB_ORDER_PREFERENCE.first, json)
        }

    var symbol_mode_preference: SymbolMode
        get() {
            val modeString = preferences.getString(
                SYMBOL_MODE_PREFERENCE.first, SYMBOL_MODE_PREFERENCE.second
            )
            return try {
                // 保存されている文字列からEnumを復元
                SymbolMode.valueOf(modeString ?: SYMBOL_MODE_PREFERENCE.second)
            } catch (e: IllegalArgumentException) {
                // 不正な値が保存されていた場合はデフォルト値を返す
                SymbolMode.valueOf(SYMBOL_MODE_PREFERENCE.second)
            }
        }
        set(value) = preferences.edit {
            // Enumを文字列として保存
            it.putString(SYMBOL_MODE_PREFERENCE.first, value.name)
        }

    var default_emoji_skin_tone_preference: String
        get() {
            val skinTone = preferences.getString(
                DEFAULT_EMOJI_SKIN_TONE_PREFERENCE.first,
                DEFAULT_EMOJI_SKIN_TONE_PREFERENCE.second
            ) ?: DEFAULT_EMOJI_SKIN_TONE_PREFERENCE.second
            return if (EmojiSkinToneSupport.isSupportedSkinToneValue(skinTone)) {
                skinTone
            } else {
                DEFAULT_EMOJI_SKIN_TONE_PREFERENCE.second
            }
        }
        set(value) = preferences.edit {
            val skinTone = if (EmojiSkinToneSupport.isSupportedSkinToneValue(value)) {
                value
            } else {
                DEFAULT_EMOJI_SKIN_TONE_PREFERENCE.second
            }
            it.putString(DEFAULT_EMOJI_SKIN_TONE_PREFERENCE.first, skinTone)
        }


    val ng_word_preference: Boolean get() = true // NG words always applied; edit list in settings (empty list = no effect)




    var flick_sensitivity_preference: Int?
        get() = preferences.getInt(
            FLICK_SENSITIVITY.first, FLICK_SENSITIVITY.second
        )
        set(value) = preferences.edit {
            it.putInt(FLICK_SENSITIVITY.first, value ?: 100)
        }

    val flick_threshold_shape_preference: String get() = "radial" // S3: hardcoded, setting removed

    var tfbi_diagonal_recognition_mode_preference: TfbiDiagonalRecognitionMode
        get() = TfbiDiagonalRecognitionMode.fromPreferenceValue(
            preferences.getString(
                TFBI_DIAGONAL_RECOGNITION_MODE.first,
                TFBI_DIAGONAL_RECOGNITION_MODE.second
            )
        )
        set(value) = preferences.edit {
            it.putString(TFBI_DIAGONAL_RECOGNITION_MODE.first, value.preferenceValue)
        }

    var hierarchical_flick_mode_switch_angle_margin_preference: Int
        get() = preferences.getInt(
            HIERARCHICAL_FLICK_MODE_SWITCH_ANGLE_MARGIN.first,
            HIERARCHICAL_FLICK_MODE_SWITCH_ANGLE_MARGIN.second
        ).coerceIn(0, 34)
        set(value) = preferences.edit {
            it.putInt(
                HIERARCHICAL_FLICK_MODE_SWITCH_ANGLE_MARGIN.first,
                value.coerceIn(0, 34)
            )
        }

    val independent_multi_touch_preference: Boolean get() = false // S3: hardcoded, setting removed

    val long_press_timeout_preference: Int get() = 300 // hardcoded: vibration/long-press group removed

    val delete_long_press_conversion_behavior: String get() = "deferred" // hardcoded: vibration/long-press group removed

    var n_best_preference: Int?
        get() = preferences.getInt(
            N_BEST_PREFERENCE.first, N_BEST_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putInt(N_BEST_PREFERENCE.first, value ?: 4)
        }

    val conversion_beam_width_preference: Int get() = 20 // S3: hardcoded, setting removed

    var incremental_conversion_session_preference: Boolean
        get() = preferences.getBoolean(
            INCREMENTAL_CONVERSION_SESSION_PREFERENCE.first,
            INCREMENTAL_CONVERSION_SESSION_PREFERENCE.second,
        )
        set(value) = preferences.edit {
            it.putBoolean(INCREMENTAL_CONVERSION_SESSION_PREFERENCE.first, value)
        }

    var japanese_prediction_enable_preference: Boolean
        get() = JAPANESE_PREDICTION_ENABLE_PREFERENCE.second // 予測設定UI削除: 既定値に固定
        set(value) = preferences.edit {
            it.putBoolean(JAPANESE_PREDICTION_ENABLE_PREFERENCE.first, value)
        }

    var english_prediction_enable_preference: Boolean
        get() = ENGLISH_PREDICTION_ENABLE_PREFERENCE.second // 予測設定UI削除: 既定値に固定
        set(value) = preferences.edit {
            it.putBoolean(ENGLISH_PREDICTION_ENABLE_PREFERENCE.first, value)
        }

    var system_dictionary_prediction_enable_preference: Boolean
        get() = SYSTEM_DICTIONARY_PREDICTION_ENABLE_PREFERENCE.second // 予測設定UI削除: 既定値に固定
        set(value) = preferences.edit {
            it.putBoolean(SYSTEM_DICTIONARY_PREDICTION_ENABLE_PREFERENCE.first, value)
        }

    var prediction_minimum_input_length_preference: Int
        get() = PREDICTION_MINIMUM_INPUT_LENGTH_PREFERENCE.second.coerceIn(3, 8) // 予測設定UI削除: 既定値に固定
        set(value) = preferences.edit {
            it.putInt(PREDICTION_MINIMUM_INPUT_LENGTH_PREFERENCE.first, value.coerceIn(3, 8))
        }

    var system_prediction_candidate_limit_preference: Int
        get() = SYSTEM_PREDICTION_CANDIDATE_LIMIT_PREFERENCE.second.coerceIn(1, 16) // 予測設定UI削除: 既定値に固定
        set(value) = preferences.edit {
            it.putInt(SYSTEM_PREDICTION_CANDIDATE_LIMIT_PREFERENCE.first, value.coerceIn(1, 16))
        }

    var prediction_lookahead_character_count_preference: Int
        get() = PREDICTION_LOOKAHEAD_CHARACTER_COUNT_PREFERENCE.second // 予測設定UI削除: 既定値に固定
        set(value) = preferences.edit {
            it.putInt(
                PREDICTION_LOOKAHEAD_CHARACTER_COUNT_PREFERENCE.first,
                value.coerceIn(
                    PREDICTION_LOOKAHEAD_CHARACTER_COUNT_MIN,
                    PREDICTION_LOOKAHEAD_CHARACTER_COUNT_MAX,
                ),
            )
        }

    fun migratePredictionLookaheadPreferenceIfNeeded() {
        val key = PREDICTION_LOOKAHEAD_CHARACTER_COUNT_PREFERENCE.first
        val storedValue = preferences.all[key] ?: return
        val normalizedValue = when (storedValue) {
            is Number -> storedValue.toInt()
            is String -> legacyPredictionLookaheadCharacterCount(storedValue)
            else -> PREDICTION_LOOKAHEAD_CHARACTER_COUNT_PREFERENCE.second
        }.coerceIn(
            PREDICTION_LOOKAHEAD_CHARACTER_COUNT_MIN,
            PREDICTION_LOOKAHEAD_CHARACTER_COUNT_MAX,
        )
        if (storedValue !is Int || storedValue != normalizedValue) {
            preferences.edit { it.putInt(key, normalizedValue) }
        }
    }

    private fun legacyPredictionLookaheadCharacterCount(value: String): Int =
        when (value) {
            "short" -> 1
            "standard" -> PREDICTION_LOOKAHEAD_CHARACTER_COUNT_DEFAULT
            "long" -> PREDICTION_LOOKAHEAD_CHARACTER_COUNT_MAX
            else -> value.toIntOrNull() ?: PREDICTION_LOOKAHEAD_CHARACTER_COUNT_DEFAULT
        }

    var prediction_aggressiveness_preference: String
        get() = PREDICTION_AGGRESSIVENESS_PREFERENCE.second // 予測設定UI削除: 既定値に固定
        set(value) = preferences.edit {
            it.putString(PREDICTION_AGGRESSIVENESS_PREFERENCE.first, value)
        }

    var system_user_dictionary_prediction_enable_preference: Boolean
        get() = SYSTEM_USER_DICTIONARY_PREDICTION_ENABLE_PREFERENCE.second // 予測設定UI削除: 既定値に固定
        set(value) = preferences.edit {
            it.putBoolean(SYSTEM_USER_DICTIONARY_PREDICTION_ENABLE_PREFERENCE.first, value)
        }

    var reading_correction_prediction_enable_preference: Boolean
        get() = READING_CORRECTION_PREDICTION_ENABLE_PREFERENCE.second // 予測設定UI削除: 既定値に固定
        set(value) = preferences.edit {
            it.putBoolean(READING_CORRECTION_PREDICTION_ENABLE_PREFERENCE.first, value)
        }

    var external_mozc_prediction_enable_preference: Boolean
        get() = EXTERNAL_MOZC_PREDICTION_ENABLE_PREFERENCE.second // 予測設定UI削除: 既定値に固定
        set(value) = preferences.edit {
            it.putBoolean(EXTERNAL_MOZC_PREDICTION_ENABLE_PREFERENCE.first, value)
        }

    var symbol_emoji_prediction_enable_preference: Boolean
        get() = SYMBOL_EMOJI_PREDICTION_ENABLE_PREFERENCE.second // 予測設定UI削除: 既定値に固定
        set(value) = preferences.edit {
            it.putBoolean(SYMBOL_EMOJI_PREDICTION_ENABLE_PREFERENCE.first, value)
        }

    var symbol_candidate_enable_preference: Boolean
        get() = preferences.getBoolean(
            SYMBOL_CANDIDATE_ENABLE_PREFERENCE.first,
            SYMBOL_CANDIDATE_ENABLE_PREFERENCE.second,
        )
        set(value) = preferences.edit {
            it.putBoolean(SYMBOL_CANDIDATE_ENABLE_PREFERENCE.first, value)
        }

    var emoji_candidate_enable_preference: Boolean
        get() = preferences.getBoolean(
            EMOJI_CANDIDATE_ENABLE_PREFERENCE.first,
            EMOJI_CANDIDATE_ENABLE_PREFERENCE.second,
        )
        set(value) = preferences.edit {
            it.putBoolean(EMOJI_CANDIDATE_ENABLE_PREFERENCE.first, value)
        }

    var emoticon_candidate_enable_preference: Boolean
        get() = preferences.getBoolean(
            EMOTICON_CANDIDATE_ENABLE_PREFERENCE.first,
            EMOTICON_CANDIDATE_ENABLE_PREFERENCE.second,
        )
        set(value) = preferences.edit {
            it.putBoolean(EMOTICON_CANDIDATE_ENABLE_PREFERENCE.first, value)
        }


    var learn_dictionary_preference: Boolean?
        get() = preferences.getBoolean(
            LEARN_DICTIONARY_PREFERENCE.first, LEARN_DICTIONARY_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(LEARN_DICTIONARY_PREFERENCE.first, value ?: true)
        }

    val incognito_mode_detection_preference: Boolean get() = true // S3: hardcoded, setting removed

    val show_learned_candidates_in_incognito_preference: Boolean get() = true // S3: hardcoded, setting removed

    var user_dictionary_preference: Boolean?
        get() = preferences.getBoolean(
            USER_DICTIONARY_PREFERENCE.first, USER_DICTIONARY_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(USER_DICTIONARY_PREFERENCE.first, value ?: true)
        }

    var user_dictionary_prefix_match_number_preference: Int?
        get() = preferences.getInt(
            USER_DICTIONARY_PREFIX_PREFERENCE.first, USER_DICTIONARY_PREFIX_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putInt(USER_DICTIONARY_PREFIX_PREFERENCE.first, value ?: 2)
        }

    var user_dictionary_prediction_candidate_limit_preference: Int
        get() = preferences.getInt(
            USER_DICTIONARY_PREDICTION_CANDIDATE_LIMIT_PREFERENCE.first,
            USER_DICTIONARY_PREDICTION_CANDIDATE_LIMIT_PREFERENCE.second,
        ).coerceIn(1, 8)
        set(value) = preferences.edit {
            it.putInt(
                USER_DICTIONARY_PREDICTION_CANDIDATE_LIMIT_PREFERENCE.first,
                value.coerceIn(1, 8),
            )
        }

    var system_ngram_dictionary_enable_preference: Boolean
        get() = preferences.getBoolean(
            SYSTEM_NGRAM_DICTIONARY_ENABLE_PREFERENCE.first,
            SYSTEM_NGRAM_DICTIONARY_ENABLE_PREFERENCE.second,
        )
        set(value) = preferences.edit {
            it.putBoolean(SYSTEM_NGRAM_DICTIONARY_ENABLE_PREFERENCE.first, value)
        }


    var preserve_dictionary_word_whitespace_preference: Boolean
        get() = preferences.getBoolean(PRESERVE_DICTIONARY_WORD_WHITESPACE_KEY, false)
        set(value) = preferences.edit {
            it.putBoolean(PRESERVE_DICTIONARY_WORD_WHITESPACE_KEY, value)
        }

    val show_dictionary_candidate_labels_preference: Boolean get() = false // S3: hardcoded, setting removed

    val suppress_hentaigana_candidates_preference: Boolean get() = false // S3: hardcoded, setting removed

    val time_same_pronounce_typing_preference: Int? get() = 1000 // S3: hardcoded, setting removed

    /** 設定「辞書と学習 › 詳細設定」の UT 辞書オン/オフ（既定オン）。 */
    val ut_person_name_enabled: Boolean
        get() = preferences.getBoolean("ut_person_name_enabled_preference", true)
    val ut_places_enabled: Boolean
        get() = preferences.getBoolean("ut_places_enabled_preference", true)
    val ut_wiki_enabled: Boolean
        get() = preferences.getBoolean("ut_wiki_enabled_preference", true)

    var mozc_ut_person_names_preference: Boolean?
        get() = preferences.getBoolean(MOZCUT_PERSON_NAME.first, MOZCUT_PERSON_NAME.second)
        set(value) = preferences.edit {
            it.putBoolean(MOZCUT_PERSON_NAME.first, value ?: false)
        }

    var mozc_ut_places_preference: Boolean?
        get() = preferences.getBoolean(MOZCUT_PLACES.first, MOZCUT_PLACES.second)
        set(value) = preferences.edit {
            it.putBoolean(MOZCUT_PLACES.first, value ?: false)
        }

    var mozc_ut_wiki_preference: Boolean?
        get() = preferences.getBoolean(MOZCUT_WIKI.first, MOZCUT_WIKI.second)
        set(value) = preferences.edit {
            it.putBoolean(MOZCUT_WIKI.first, value ?: false)
        }

    var mozc_ut_neologd_preference: Boolean?
        get() = preferences.getBoolean(MOZCUT_NEOLOGD.first, MOZCUT_NEOLOGD.second)
        set(value) = preferences.edit {
            it.putBoolean(MOZCUT_NEOLOGD.first, value ?: false)
        }

    var mozc_ut_web_preference: Boolean?
        get() = preferences.getBoolean(MOZCUT_WEB.first, MOZCUT_WEB.second)
        set(value) = preferences.edit {
            it.putBoolean(MOZCUT_WEB.first, value ?: false)
        }

    var keyboard_height: Int?
        get() = preferences.getInt(
            KEYBOARD_HEIGHT.first, KEYBOARD_HEIGHT.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_HEIGHT.first, value ?: 280)
        }

    var keyboard_width: Int?
        get() = preferences.getInt(
            KEYBOARD_WIDTH.first, KEYBOARD_WIDTH.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_WIDTH.first, value ?: 100)
        }




    var keyboard_position: Boolean?
        get() = preferences.getBoolean(KEYBOARD_POSITION.first, KEYBOARD_POSITION.second)
        set(value) = preferences.edit {
            it.putBoolean(KEYBOARD_POSITION.first, value ?: true)
        }


    var keyboard_vertical_margin_bottom: Int?
        get() = preferences.getInt(
            KEYBOARD_VERTICAL_MARGIN_BOTTOM.first, KEYBOARD_VERTICAL_MARGIN_BOTTOM.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_VERTICAL_MARGIN_BOTTOM.first, value ?: 0)
        }

    var keyboard_height_landscape: Int?
        get() = preferences.getInt(
            KEYBOARD_HEIGHT_LANDSCAPE.first, KEYBOARD_HEIGHT_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_HEIGHT_LANDSCAPE.first, value ?: 220)
        }

    var keyboard_width_landscape: Int?
        get() = preferences.getInt(
            KEYBOARD_WIDTH_LANDSCAPE.first, KEYBOARD_WIDTH_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_WIDTH_LANDSCAPE.first, value ?: 100)
        }

    var keyboard_position_landscape: Boolean?
        get() = preferences.getBoolean(
            KEYBOARD_POSITION_LANDSCAPE.first, KEYBOARD_POSITION_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(KEYBOARD_POSITION_LANDSCAPE.first, value ?: true)
        }

    var keyboard_vertical_margin_bottom_landscape: Int?
        get() = preferences.getInt(
            KEYBOARD_VERTICAL_MARGIN_BOTTOM_LANDSCAPE.first,
            KEYBOARD_VERTICAL_MARGIN_BOTTOM_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_VERTICAL_MARGIN_BOTTOM_LANDSCAPE.first, value ?: 0)
        }





    var candidate_view_height_dp_landscape: Int?
        get() = preferences.getInt(
            CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.first, CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(
                CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.first,
                value ?: CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.second
            )
        }

    var candidate_view_empty_height_dp_landscape: Int?
        get() = preferences.getInt(
            CANDIDATE_VIEW_EMPTY_HEIGHT_DP_LANDSCAPE.first,
            CANDIDATE_VIEW_EMPTY_HEIGHT_DP_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(
                CANDIDATE_VIEW_EMPTY_HEIGHT_DP_LANDSCAPE.first,
                value ?: CANDIDATE_VIEW_EMPTY_HEIGHT_DP_LANDSCAPE.second
            )
        }

    val flick_input_only_preference: Boolean? get() = false // S3: hardcoded, setting removed

    val flick_editor_preview_preference: Boolean get() = false // S3: hardcoded, setting removed

    val flick_editor_preview_delay_ms: Int get() = 0 // S3: hardcoded, setting removed

    var undo_enable_preference: Boolean?
        get() = preferences.getBoolean(UNDO_ENABLE.first, UNDO_ENABLE.second)
        set(value) = preferences.edit {
            it.putBoolean(UNDO_ENABLE.first, value ?: false)
        }

    var omission_search_preference: Boolean?
        get() = preferences.getBoolean(OMISSION_SEARCH.first, OMISSION_SEARCH.second)
        set(value) = preferences.edit {
            it.putBoolean(OMISSION_SEARCH.first, value ?: false)
        }

    var tenkey_space_flick_preference: Boolean
        get() = preferences.getBoolean("tenkey_space_flick_preference", true)
        set(value) = preferences.edit {
            it.putBoolean("tenkey_space_flick_preference", value)
        }



    var space_hankaku_preference: Boolean?
        get() = preferences.getBoolean(SPACE_HANKAKU_ENABLE.first, SPACE_HANKAKU_ENABLE.second)
        set(value) = preferences.edit {
            it.putBoolean(SPACE_HANKAKU_ENABLE.first, value ?: false)
        }

    var custom_direct_mode_space_hankaku_preference: Boolean?
        get() = preferences.getBoolean(
            CUSTOM_DIRECT_MODE_SPACE_HANKAKU_ENABLE.first,
            CUSTOM_DIRECT_MODE_SPACE_HANKAKU_ENABLE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(
                CUSTOM_DIRECT_MODE_SPACE_HANKAKU_ENABLE.first,
                value ?: CUSTOM_DIRECT_MODE_SPACE_HANKAKU_ENABLE.second
            )
        }

    val live_conversion_preference: Boolean? get() = false // S3: hardcoded, setting removed

    val live_conversion_start_length_preference: Int? get() = 1 // S3: hardcoded, setting removed

    val live_conversion_candidate_yomi_preference: Boolean? get() = false // S3: hardcoded, setting removed

    val live_conversion_candidate_yomi_mode: String get() = CANDIDATE_YOMI_MODE_WHOLE // S3: hardcoded, setting removed

    val live_conversion_candidate_yomi_size: Int get() = DEFAULT_LIVE_CONVERSION_CANDIDATE_YOMI_SIZE // S3: hardcoded, setting removed

    var delete_key_high_light_preference: Boolean?
        get() = preferences.getBoolean(DELETE_KEY_HIGH_LIGHT.first, DELETE_KEY_HIGH_LIGHT.second)
        set(value) = preferences.edit {
            it.putBoolean(DELETE_KEY_HIGH_LIGHT.first, value ?: true)
        }

    val custom_keyboard_suggestion_preference: Boolean? get() = false // 候補欄のキーボード一覧表示は廃止

    var custom_direct_input_replace_composing_preference: Boolean
        get() = preferences.getBoolean(CUSTOM_DIRECT_INPUT_REPLACE_COMPOSING_KEY, false)
        set(value) = preferences.edit {
            it.putBoolean(CUSTOM_DIRECT_INPUT_REPLACE_COMPOSING_KEY, value)
        }

    var custom_keyboard_input_in_empty_areas_preference: Boolean
        get() = preferences.getBoolean(
            CUSTOM_KEYBOARD_INPUT_IN_EMPTY_AREAS_PREFERENCE.first,
            CUSTOM_KEYBOARD_INPUT_IN_EMPTY_AREAS_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CUSTOM_KEYBOARD_INPUT_IN_EMPTY_AREAS_PREFERENCE.first, value)
        }

    val remember_last_custom_keyboard_preference: Boolean? get() = true // S3: hardcoded, setting removed

    var remember_custom_keyboard_input_mode_preference: Boolean?
        get() = preferences.getBoolean(
            REMEMBER_CUSTOM_KEYBOARD_INPUT_MODE_PREFERENCE.first,
            REMEMBER_CUSTOM_KEYBOARD_INPUT_MODE_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(REMEMBER_CUSTOM_KEYBOARD_INPUT_MODE_PREFERENCE.first, value ?: false)
        }

    var last_used_custom_keyboard_stable_id: String?
        get() = preferences.getString(
            LAST_USED_CUSTOM_KEYBOARD_STABLE_ID.first,
            LAST_USED_CUSTOM_KEYBOARD_STABLE_ID.second
        )
        set(value) = preferences.edit {
            it.putString(LAST_USED_CUSTOM_KEYBOARD_STABLE_ID.first, value.orEmpty())
        }

    fun saveCustomKeyboardLastDirectMode(key: String, value: Boolean) = preferences.edit {
        it.putBoolean("$CUSTOM_KEYBOARD_LAST_DIRECT_MODE_PREFIX$key", value)
    }

    fun getCustomKeyboardLastDirectMode(key: String): Boolean? {
        val preferenceKey = "$CUSTOM_KEYBOARD_LAST_DIRECT_MODE_PREFIX$key"
        return if (preferences.contains(preferenceKey)) {
            preferences.getBoolean(preferenceKey, false)
        } else {
            null
        }
    }

    fun saveCustomKeyboardLastRomajiMode(key: String, value: Boolean) = preferences.edit {
        it.putBoolean("$CUSTOM_KEYBOARD_LAST_ROMAJI_MODE_PREFIX$key", value)
    }

    fun getCustomKeyboardLastRomajiMode(key: String): Boolean? {
        val preferenceKey = "$CUSTOM_KEYBOARD_LAST_ROMAJI_MODE_PREFIX$key"
        return if (preferences.contains(preferenceKey)) {
            preferences.getBoolean(preferenceKey, false)
        } else {
            null
        }
    }




    val is_floating_mode: Boolean? get() = false // S3: hardcoded, setting removed

    var keyboard_floating_position_x: Int
        get() = preferences.getInt(
            KEYBOARD_FLOATING_POSITION_X.first, KEYBOARD_FLOATING_POSITION_X.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_FLOATING_POSITION_X.first, value)
        }

    var keyboard_floating_position_y: Int
        get() = preferences.getInt(
            KEYBOARD_FLOATING_POSITION_Y.first, KEYBOARD_FLOATING_POSITION_Y.second
        )
        set(value) = preferences.edit {
            it.putInt(KEYBOARD_FLOATING_POSITION_Y.first, value)
        }

    var candidate_view_height_dp: Int?
        get() = preferences.getInt(
            CANDIDATE_VIEW_HEIGHT_DP.first, CANDIDATE_VIEW_HEIGHT_DP.second
        )
        set(value) = preferences.edit {
            it.putInt(CANDIDATE_VIEW_HEIGHT_DP.first, value ?: CANDIDATE_VIEW_HEIGHT_DP.second)
        }

    var candidate_view_empty_height_dp: Int?
        get() = preferences.getInt(
            CANDIDATE_VIEW_EMPTY_HEIGHT_DP.first, CANDIDATE_VIEW_EMPTY_HEIGHT_DP.second
        )
        set(value) = preferences.edit {
            it.putInt(
                CANDIDATE_VIEW_EMPTY_HEIGHT_DP.first,
                value ?: CANDIDATE_VIEW_EMPTY_HEIGHT_DP.second
            )
        }

    var candidate_column_preference: String
        get() = preferences.getString(
            CANDIDATE_COLUMN_PREFERENCE.first, CANDIDATE_COLUMN_PREFERENCE.second
        ) ?: "1"
        set(value) = preferences.edit {
            it.putString(CANDIDATE_COLUMN_PREFERENCE.first, value)
        }

    val candidate_column_landscape_preference: String get() = "1" // S3: hardcoded, setting removed

    internal fun migrateCandidateHeightDefaultsIfNeeded() {
        if (preferences.getInt(
                CANDIDATE_HEIGHT_DEFAULTS_MIGRATION_VERSION_KEY,
                0
            ) >= CURRENT_CANDIDATE_HEIGHT_DEFAULTS_MIGRATION_VERSION
        ) {
            return
        }

        preferences.edit { editor ->
            listOf(false, true).forEach { isLandscape ->
                listOf("1", "2", "3").forEach { column ->
                    val heightPreference = candidateHeightPreferenceFor(isLandscape, column)
                    editor.putInt(heightPreference.first, heightPreference.second)

                    val defaultPreference = candidateDefaultHeightPreferenceFor(isLandscape, column)
                    editor.putInt(defaultPreference.first, defaultPreference.second)
                }

                val activeColumn = getCandidateColumn(isLandscape)
                val activeHeight = candidateHeightPreferenceFor(isLandscape, activeColumn).second
                editor.putInt(
                    if (isLandscape) {
                        CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.first
                    } else {
                        CANDIDATE_VIEW_HEIGHT_DP.first
                    },
                    activeHeight
                )

                val emptyPreference = candidateEmptyHeightPreferenceFor(isLandscape)
                editor.putInt(emptyPreference.first, emptyPreference.second)

                val defaultEmptyPreference = candidateDefaultEmptyHeightPreferenceFor(isLandscape)
                editor.putInt(defaultEmptyPreference.first, defaultEmptyPreference.second)
            }

            // The per-column values have been initialized above, so the older lazy migration
            // must not copy the active value over them later.
            editor.putBoolean(CANDIDATE_HEIGHT_PER_COLUMN_MIGRATED.first, true)
            editor.putInt(
                CANDIDATE_HEIGHT_DEFAULTS_MIGRATION_VERSION_KEY,
                CURRENT_CANDIDATE_HEIGHT_DEFAULTS_MIGRATION_VERSION
            )
        }
    }

    fun migrateCandidateHeightPerColumnPreferencesIfNeeded() {
        if (preferences.getBoolean(
                CANDIDATE_HEIGHT_PER_COLUMN_MIGRATED.first,
                CANDIDATE_HEIGHT_PER_COLUMN_MIGRATED.second
            )
        ) {
            return
        }

        val portraitColumn = normalizeCandidateColumn(candidate_column_preference)
        val landscapeColumn = normalizeCandidateColumn(candidate_column_landscape_preference)
        val portraitHeight = candidateHeightForPerColumnMigration(false, portraitColumn)
        val landscapeHeight = candidateHeightForPerColumnMigration(true, landscapeColumn)

        preferences.edit { editor ->
            candidateHeightPreferenceFor(isLandscape = false, column = "1").let { editor.putInt(it.first, it.second) }
            candidateHeightPreferenceFor(isLandscape = false, column = "2").let { editor.putInt(it.first, it.second) }
            candidateHeightPreferenceFor(isLandscape = false, column = "3").let { editor.putInt(it.first, it.second) }
            candidateHeightPreferenceFor(isLandscape = true, column = "1").let { editor.putInt(it.first, it.second) }
            candidateHeightPreferenceFor(isLandscape = true, column = "2").let { editor.putInt(it.first, it.second) }
            candidateHeightPreferenceFor(isLandscape = true, column = "3").let { editor.putInt(it.first, it.second) }
            editor.putInt(candidateHeightPreferenceFor(false, portraitColumn).first, portraitHeight)
            editor.putInt(candidateHeightPreferenceFor(true, landscapeColumn).first, landscapeHeight)
            editor.putInt(CANDIDATE_VIEW_HEIGHT_DP.first, portraitHeight)
            editor.putInt(CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.first, landscapeHeight)
            editor.putBoolean(CANDIDATE_HEIGHT_PER_COLUMN_MIGRATED.first, true)
        }
    }

    private fun candidateHeightForPerColumnMigration(
        isLandscape: Boolean,
        column: String
    ): Int {
        val activePreference = if (isLandscape) {
            CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE
        } else {
            CANDIDATE_VIEW_HEIGHT_DP
        }
        val heightDp = if (preferences.contains(activePreference.first)) {
            readIntPreference(activePreference.first, activePreference.second)
        } else {
            candidateHeightPreferenceFor(isLandscape, column).second
        }
        return heightDp.coerceIn(MIN_CANDIDATE_VISIBLE_HEIGHT_DP, MAX_CANDIDATE_VISIBLE_HEIGHT_DP)
    }

    fun getCandidateVisibleHeightDp(
        isLandscape: Boolean,
        column: String
    ): Int {
        migrateCandidateHeightPerColumnPreferencesIfNeeded()
        val preference = candidateHeightPreferenceFor(isLandscape, normalizeCandidateColumn(column))
        return readIntPreference(preference.first, preference.second)
            .coerceIn(MIN_CANDIDATE_VISIBLE_HEIGHT_DP, MAX_CANDIDATE_VISIBLE_HEIGHT_DP)
    }

    fun setCandidateVisibleHeightDp(
        isLandscape: Boolean,
        column: String,
        heightDp: Int
    ) {
        migrateCandidateHeightPerColumnPreferencesIfNeeded()
        val normalizedColumn = normalizeCandidateColumn(column)
        val clampedHeight = heightDp.coerceIn(
            MIN_CANDIDATE_VISIBLE_HEIGHT_DP,
            MAX_CANDIDATE_VISIBLE_HEIGHT_DP
        )
        val preference = candidateHeightPreferenceFor(isLandscape, normalizedColumn)
        preferences.edit { editor ->
            editor.putInt(preference.first, clampedHeight)
            if (normalizeCandidateColumn(getCandidateColumn(isLandscape)) == normalizedColumn) {
                editor.putInt(
                    if (isLandscape) {
                        CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.first
                    } else {
                        CANDIDATE_VIEW_HEIGHT_DP.first
                    },
                    clampedHeight
                )
            }
        }
    }

    fun getCandidateDefaultVisibleHeightDp(
        isLandscape: Boolean,
        column: String
    ): Int {
        val preference = candidateDefaultHeightPreferenceFor(
            isLandscape,
            normalizeCandidateColumn(column)
        )
        return readIntPreference(preference.first, preference.second)
            .coerceIn(MIN_CANDIDATE_VISIBLE_HEIGHT_DP, MAX_CANDIDATE_VISIBLE_HEIGHT_DP)
    }

    fun setCandidateDefaultVisibleHeightDp(
        isLandscape: Boolean,
        column: String,
        heightDp: Int
    ) {
        val preference = candidateDefaultHeightPreferenceFor(
            isLandscape,
            normalizeCandidateColumn(column)
        )
        preferences.edit { editor ->
            editor.putInt(
                preference.first,
                heightDp.coerceIn(
                    MIN_CANDIDATE_VISIBLE_HEIGHT_DP,
                    MAX_CANDIDATE_VISIBLE_HEIGHT_DP
                )
            )
        }
    }

    fun getCandidateDefaultEmptyHeightDp(isLandscape: Boolean): Int {
        val preference = candidateDefaultEmptyHeightPreferenceFor(isLandscape)
        return readIntPreference(preference.first, preference.second)
            .coerceIn(MIN_CANDIDATE_VISIBLE_HEIGHT_DP, MAX_CANDIDATE_VISIBLE_HEIGHT_DP)
    }

    fun setCandidateDefaultEmptyHeightDp(isLandscape: Boolean, heightDp: Int) {
        val preference = candidateDefaultEmptyHeightPreferenceFor(isLandscape)
        preferences.edit { editor ->
            editor.putInt(
                preference.first,
                heightDp.coerceIn(
                    MIN_CANDIDATE_VISIBLE_HEIGHT_DP,
                    MAX_CANDIDATE_VISIBLE_HEIGHT_DP
                )
            )
        }
    }

    fun resetCandidateHeightSettingsToUserDefaults(isLandscape: Boolean) {
        migrateCandidateHeightPerColumnPreferencesIfNeeded()
        val activeColumn = getCandidateColumn(isLandscape)
        val emptyHeight = getCandidateDefaultEmptyHeightDp(isLandscape)
        val emptyPreference = candidateEmptyHeightPreferenceFor(isLandscape)
        preferences.edit { editor ->
            listOf("1", "2", "3").forEach { column ->
                val heightDp = getCandidateDefaultVisibleHeightDp(isLandscape, column)
                editor.putInt(candidateHeightPreferenceFor(isLandscape, column).first, heightDp)
                if (column == activeColumn) {
                    editor.putInt(
                        if (isLandscape) {
                            CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.first
                        } else {
                            CANDIDATE_VIEW_HEIGHT_DP.first
                        },
                        heightDp
                    )
                }
            }
            editor.putInt(emptyPreference.first, emptyHeight)
        }
    }

    fun resetCandidateHeightDefaultsToFactoryDefaults(isLandscape: Boolean) {
        val emptyPreference = candidateDefaultEmptyHeightPreferenceFor(isLandscape)
        preferences.edit { editor ->
            listOf("1", "2", "3").forEach { column ->
                val preference = candidateDefaultHeightPreferenceFor(isLandscape, column)
                editor.putInt(preference.first, preference.second)
            }
            editor.putInt(emptyPreference.first, emptyPreference.second)
        }
    }

    fun copyCandidateHeightSettingsToUserDefaults(isLandscape: Boolean) {
        migrateCandidateHeightPerColumnPreferencesIfNeeded()
        val emptyPreference = candidateEmptyHeightPreferenceFor(isLandscape)
        val emptyHeight = readIntPreference(emptyPreference.first, emptyPreference.second)
            .coerceIn(MIN_CANDIDATE_VISIBLE_HEIGHT_DP, MAX_CANDIDATE_VISIBLE_HEIGHT_DP)
        val defaultEmptyPreference = candidateDefaultEmptyHeightPreferenceFor(isLandscape)
        preferences.edit { editor ->
            listOf("1", "2", "3").forEach { column ->
                val heightDp = getCandidateVisibleHeightDp(isLandscape, column)
                editor.putInt(
                    candidateDefaultHeightPreferenceFor(isLandscape, column).first,
                    heightDp
                )
            }
            editor.putInt(defaultEmptyPreference.first, emptyHeight)
        }
    }

    fun getCandidateColumn(isLandscape: Boolean): String =
        normalizeCandidateColumn(
            if (isLandscape) {
                candidate_column_landscape_preference
            } else {
                candidate_column_preference
            }
        )

    fun setCandidateColumnAndSyncHeight(
        isLandscape: Boolean,
        column: String
    ) {
        migrateCandidateHeightPerColumnPreferencesIfNeeded()
        val normalizedColumn = normalizeCandidateColumn(column)
        val heightDp = getCandidateVisibleHeightDp(isLandscape, normalizedColumn)
        preferences.edit { editor ->
            editor.putString(
                if (isLandscape) {
                    CANDIDATE_COLUMN_LANDSCAPE_PREFERENCE.first
                } else {
                    CANDIDATE_COLUMN_PREFERENCE.first
                },
                normalizedColumn
            )
            editor.putInt(
                if (isLandscape) {
                    CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.first
                } else {
                    CANDIDATE_VIEW_HEIGHT_DP.first
                },
                heightDp
            )
        }
    }

    fun syncActiveCandidateVisibleHeightToImePreference(isLandscape: Boolean) {
        migrateCandidateHeightPerColumnPreferencesIfNeeded()
        val column = getCandidateColumn(isLandscape)
        val heightDp = getCandidateVisibleHeightDp(isLandscape, column)
        preferences.edit { editor ->
            editor.putInt(
                if (isLandscape) {
                    CANDIDATE_VIEW_HEIGHT_DP_LANDSCAPE.first
                } else {
                    CANDIDATE_VIEW_HEIGHT_DP.first
                },
                heightDp
            )
        }
    }

    val candidate_tab_preference: Boolean get() = false // S3: hardcoded, setting removed

    val shortcut_toolbar_visibility_preference: Boolean get() = false // S3: hardcoded, setting removed

    val shortcut_toolbar_integrated_in_suggestion_preference: Boolean get() = false // S3: hardcoded, setting removed

    var stabilize_candidate_strip_height_preference: Boolean
        get() = preferences.getBoolean(STABILIZE_CANDIDATE_STRIP_HEIGHT_KEY, false)
        set(value) = preferences.edit {
            it.putBoolean(STABILIZE_CANDIDATE_STRIP_HEIGHT_KEY, value)
        }

    var shortcut_toolbar_height_dp_preference: Int
        get() = preferences.getInt(
            SHORTCUT_TOOLBAR_HEIGHT_DP_PREFERENCE.first,
            SHORTCUT_TOOLBAR_HEIGHT_DP_PREFERENCE.second
        ).coerceIn(
            SHORTCUT_TOOLBAR_HEIGHT_MIN_DP,
            SHORTCUT_TOOLBAR_HEIGHT_MAX_DP
        )
        set(value) = preferences.edit {
            it.putInt(
                SHORTCUT_TOOLBAR_HEIGHT_DP_PREFERENCE.first,
                value.coerceIn(
                    SHORTCUT_TOOLBAR_HEIGHT_MIN_DP,
                    SHORTCUT_TOOLBAR_HEIGHT_MAX_DP
                )
            )
        }

    var shortcut_toolbar_icon_size_dp_preference: Int
        get() = preferences.getInt(
            SHORTCUT_TOOLBAR_ICON_SIZE_DP_PREFERENCE.first,
            SHORTCUT_TOOLBAR_ICON_SIZE_DP_PREFERENCE.second
        ).coerceIn(
            SHORTCUT_TOOLBAR_ICON_SIZE_MIN_DP,
            SHORTCUT_TOOLBAR_ICON_SIZE_MAX_DP
        )
        set(value) = preferences.edit {
            it.putInt(
                SHORTCUT_TOOLBAR_ICON_SIZE_DP_PREFERENCE.first,
                value.coerceIn(
                    SHORTCUT_TOOLBAR_ICON_SIZE_MIN_DP,
                    SHORTCUT_TOOLBAR_ICON_SIZE_MAX_DP
                )
            )
        }

    var seedColor: Int
        get() = preferences.getInt(
            APP_THEME_SEED_COLOR.first, APP_THEME_SEED_COLOR.second
        )
        set(value) = preferences.edit {
            it.putInt(APP_THEME_SEED_COLOR.first, value)
        }

    var keyboardSkin: KeyboardSkinId
        get() = KeyboardSkinId.fromPreference(
            preferences.getString(KeyboardSkinId.PREFERENCE_KEY, null)
        )
        set(value) = preferences.edit {
            it.putString(KeyboardSkinId.PREFERENCE_KEY, value.preferenceValue)
        }

    var theme_mode: String
        get() = preferences.getString(KEYBOARD_THEME_MODE.first, KEYBOARD_THEME_MODE.second)
            ?: "default"
        set(value) = preferences.edit {
            it.putString(KEYBOARD_THEME_MODE.first, value)
        }

    var custom_theme_bg_color: Int
        get() = readIntPreference(CUSTOM_THEME_BG_COLOR.first, CUSTOM_THEME_BG_COLOR.second)
        set(value) = preferences.edit {
            it.putInt(CUSTOM_THEME_BG_COLOR.first, value)
        }

    var custom_theme_key_color: Int
        get() = readIntPreference(CUSTOM_THEME_KEY_COLOR.first, CUSTOM_THEME_KEY_COLOR.second)
        set(value) = preferences.edit {
            it.putInt(CUSTOM_THEME_KEY_COLOR.first, value)
        }

    var custom_theme_special_key_color: Int
        get() = readIntPreference(
            CUSTOM_THEME_SPECIAL_KEY_COLOR.first,
            CUSTOM_THEME_SPECIAL_KEY_COLOR.second
        )
        set(value) = preferences.edit {
            it.putInt(CUSTOM_THEME_SPECIAL_KEY_COLOR.first, value)
        }

    var custom_theme_key_text_color: Int
        get() = readIntPreference(
            CUSTOM_THEME_KEY_TEXT_COLOR.first,
            CUSTOM_THEME_KEY_TEXT_COLOR.second
        )
        set(value) = preferences.edit {
            it.putInt(CUSTOM_THEME_KEY_TEXT_COLOR.first, value)
        }

    var custom_theme_special_key_text_color: Int
        get() = readIntPreference(
            CUSTOM_THEME_SPECIAL_KEY_TEXT_COLOR.first,
            CUSTOM_THEME_SPECIAL_KEY_TEXT_COLOR.second
        )
        set(value) = preferences.edit {
            it.putInt(CUSTOM_THEME_SPECIAL_KEY_TEXT_COLOR.first, value)
        }

    var custom_theme_candidate_text_color: Int
        get() = getCustomThemeCandidateTextColor(custom_theme_key_text_color)
        set(value) = preferences.edit {
            it.putInt(CUSTOM_THEME_CANDIDATE_TEXT_COLOR.first, value)
        }

    fun getCustomThemeCandidateTextColor(defaultColor: Int): Int {
        return readIntPreference(CUSTOM_THEME_CANDIDATE_TEXT_COLOR.first, defaultColor)
    }

    var custom_theme_candidate_item_bg_color: Int
        get() = getCustomThemeCandidateItemBgColor(CUSTOM_THEME_CANDIDATE_ITEM_BG_COLOR.second)
        set(value) = preferences.edit {
            it.putInt(CUSTOM_THEME_CANDIDATE_ITEM_BG_COLOR.first, value)
        }

    fun getCustomThemeCandidateItemBgColor(defaultColor: Int): Int {
        return readIntPreference(CUSTOM_THEME_CANDIDATE_ITEM_BG_COLOR.first, defaultColor)
    }

    var custom_theme_candidate_item_pressed_bg_color: Int
        get() = getCustomThemeCandidateItemPressedBgColor(
            CUSTOM_THEME_CANDIDATE_ITEM_PRESSED_BG_COLOR.second
        )
        set(value) = preferences.edit {
            it.putInt(CUSTOM_THEME_CANDIDATE_ITEM_PRESSED_BG_COLOR.first, value)
        }

    fun getCustomThemeCandidateItemPressedBgColor(defaultColor: Int): Int {
        return readIntPreference(CUSTOM_THEME_CANDIDATE_ITEM_PRESSED_BG_COLOR.first, defaultColor)
    }

    var custom_theme_candidate_empty_popup_bg_color: Int
        get() = getCustomThemeCandidateEmptyPopupBgColor(custom_theme_special_key_color)
        set(value) = preferences.edit {
            it.putInt(CUSTOM_THEME_CANDIDATE_EMPTY_POPUP_BG_COLOR.first, value)
        }

    fun getCustomThemeCandidateEmptyPopupBgColor(defaultColor: Int): Int {
        return readIntPreference(CUSTOM_THEME_CANDIDATE_EMPTY_POPUP_BG_COLOR.first, defaultColor)
    }

    var custom_theme_candidate_empty_popup_text_color: Int
        get() = getCustomThemeCandidateEmptyPopupTextColor(custom_theme_special_key_text_color)
        set(value) = preferences.edit {
            it.putInt(CUSTOM_THEME_CANDIDATE_EMPTY_POPUP_TEXT_COLOR.first, value)
        }

    fun getCustomThemeCandidateEmptyPopupTextColor(defaultColor: Int): Int {
        return readIntPreference(CUSTOM_THEME_CANDIDATE_EMPTY_POPUP_TEXT_COLOR.first, defaultColor)
    }

    var custom_theme_shortcut_icon_color: Int
        get() = getCustomThemeShortcutIconColor(custom_theme_special_key_text_color)
        set(value) = preferences.edit {
            it.putInt(CUSTOM_THEME_SHORTCUT_ICON_COLOR.first, value)
        }

    fun getCustomThemeShortcutIconColor(defaultColor: Int): Int {
        return readIntPreference(CUSTOM_THEME_SHORTCUT_ICON_COLOR.first, defaultColor)
    }

    var delete_key_left_flick_preference: Boolean
        get() = preferences.getBoolean(
            DELETE_KEY_LEFT_FLICK_PREFERENCE.first,
            DELETE_KEY_LEFT_FLICK_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(DELETE_KEY_LEFT_FLICK_PREFERENCE.first, value)
        }

    val delete_key_up_flick_preference: Boolean get() = false // S3: hardcoded, setting removed

    val delete_key_down_flick_preference: Boolean get() = false // S3: hardcoded, setting removed

    var cursor_move_after_commit_target_pairs_preference: List<String>
        get() {
            val raw = preferences.getString(
                CURSOR_MOVE_AFTER_COMMIT_TARGET_PAIRS_PREFERENCE.first,
                CURSOR_MOVE_AFTER_COMMIT_TARGET_PAIRS_PREFERENCE.second
            ) ?: CURSOR_MOVE_AFTER_COMMIT_TARGET_PAIRS_PREFERENCE.second

            val parsed = runCatching {
                gson.fromJson<List<String>>(
                    raw,
                    object : TypeToken<List<String>>() {}.type
                )
            }.getOrNull()

            return parsed?.mapNotNull(::normalizeCursorMoveTargetPair)?.distinct()
                ?: DEFAULT_CURSOR_MOVE_AFTER_COMMIT_TARGET_PAIRS
        }
        set(value) = preferences.edit {
            val normalized = value.mapNotNull(::normalizeCursorMoveTargetPair).distinct()
            it.putString(
                CURSOR_MOVE_AFTER_COMMIT_TARGET_PAIRS_PREFERENCE.first,
                gson.toJson(normalized)
            )
        }

    fun defaultCursorMoveAfterCommitTargetPairs(): List<String> =
        DEFAULT_CURSOR_MOVE_AFTER_COMMIT_TARGET_PAIRS

    var key_letter_size: Float?
        get() = preferences.getFloat(KEY_LETTER_SIZE.first, KEY_LETTER_SIZE.second)
        set(value) = preferences.edit {
            it.putFloat(KEY_LETTER_SIZE.first, value ?: KEY_LETTER_SIZE.second)
        }

    var candidate_letter_size: Float?
        get() = preferences.getFloat(CANDIDATE_LETTER_SIZE.first, CANDIDATE_LETTER_SIZE.second)
        set(value) = preferences.edit {
            it.putFloat(CANDIDATE_LETTER_SIZE.first, value ?: CANDIDATE_LETTER_SIZE.second)
        }



    var flick_key_width_scale_percent: Int?
        get() = preferences.getInt(
            FLICK_KEY_WIDTH_SCALE_PERCENT.first,
            FLICK_KEY_WIDTH_SCALE_PERCENT.second
        )
        set(value) = preferences.edit {
            it.putInt(
                FLICK_KEY_WIDTH_SCALE_PERCENT.first,
                value ?: FLICK_KEY_WIDTH_SCALE_PERCENT.second
            )
        }

    var flick_key_height_scale_percent: Int?
        get() = preferences.getInt(
            FLICK_KEY_HEIGHT_SCALE_PERCENT.first,
            FLICK_KEY_HEIGHT_SCALE_PERCENT.second
        )
        set(value) = preferences.edit {
            it.putInt(
                FLICK_KEY_HEIGHT_SCALE_PERCENT.first,
                value ?: FLICK_KEY_HEIGHT_SCALE_PERCENT.second
            )
        }

    var flick_key_icon_scale_percent: Int?
        get() = preferences.getInt(
            FLICK_KEY_ICON_SCALE_PERCENT.first,
            FLICK_KEY_ICON_SCALE_PERCENT.second
        )
        set(value) = preferences.edit {
            it.putInt(
                FLICK_KEY_ICON_SCALE_PERCENT.first,
                value ?: FLICK_KEY_ICON_SCALE_PERCENT.second
            )
        }

    var flick_key_text_size_sp: Float?
        get() = preferences.getFloat(
            FLICK_KEY_TEXT_SIZE_SP.first,
            FLICK_KEY_TEXT_SIZE_SP.second
        )
        set(value) = preferences.edit {
            it.putFloat(
                FLICK_KEY_TEXT_SIZE_SP.first,
                value ?: FLICK_KEY_TEXT_SIZE_SP.second
            )
        }

    var flick_special_key_text_size_sp: Float?
        get() = preferences.getFloat(
            FLICK_SPECIAL_KEY_TEXT_SIZE_SP.first,
            FLICK_SPECIAL_KEY_TEXT_SIZE_SP.second
        )
        set(value) = preferences.edit {
            it.putFloat(
                FLICK_SPECIAL_KEY_TEXT_SIZE_SP.first,
                value ?: FLICK_SPECIAL_KEY_TEXT_SIZE_SP.second
            )
        }

    var tenkey_popup_size_scale_percent: Int?
        get() = preferences.getInt(
            TENKEY_POPUP_SIZE_SCALE_PERCENT.first,
            TENKEY_POPUP_SIZE_SCALE_PERCENT.second
        )
        set(value) = preferences.edit {
            it.putInt(
                TENKEY_POPUP_SIZE_SCALE_PERCENT.first,
                value ?: TENKEY_POPUP_SIZE_SCALE_PERCENT.second
            )
        }

    var tenkey_popup_text_size_sp: Float?
        get() = preferences.getFloat(
            TENKEY_POPUP_TEXT_SIZE_SP.first,
            TENKEY_POPUP_TEXT_SIZE_SP.second
        )
        set(value) = preferences.edit {
            it.putFloat(TENKEY_POPUP_TEXT_SIZE_SP.first, value ?: TENKEY_POPUP_TEXT_SIZE_SP.second)
        }





    var flick_directional_popup_size_scale_percent: Int?
        get() = preferences.getInt(
            FLICK_DIRECTIONAL_POPUP_SIZE_SCALE_PERCENT.first,
            FLICK_DIRECTIONAL_POPUP_SIZE_SCALE_PERCENT.second
        )
        set(value) = preferences.edit {
            it.putInt(
                FLICK_DIRECTIONAL_POPUP_SIZE_SCALE_PERCENT.first,
                value ?: FLICK_DIRECTIONAL_POPUP_SIZE_SCALE_PERCENT.second
            )
        }

    var flick_directional_popup_text_size_sp: Float?
        get() = preferences.getFloat(
            FLICK_DIRECTIONAL_POPUP_TEXT_SIZE_SP.first,
            FLICK_DIRECTIONAL_POPUP_TEXT_SIZE_SP.second
        )
        set(value) = preferences.edit {
            it.putFloat(
                FLICK_DIRECTIONAL_POPUP_TEXT_SIZE_SP.first,
                value ?: FLICK_DIRECTIONAL_POPUP_TEXT_SIZE_SP.second
            )
        }

    var flick_cross_popup_size_scale_percent: Int?
        get() = preferences.getInt(
            FLICK_CROSS_POPUP_SIZE_SCALE_PERCENT.first,
            FLICK_CROSS_POPUP_SIZE_SCALE_PERCENT.second
        )
        set(value) = preferences.edit {
            it.putInt(
                FLICK_CROSS_POPUP_SIZE_SCALE_PERCENT.first,
                value ?: FLICK_CROSS_POPUP_SIZE_SCALE_PERCENT.second
            )
        }

    var flick_cross_popup_text_size_sp: Float?
        get() = preferences.getFloat(
            FLICK_CROSS_POPUP_TEXT_SIZE_SP.first,
            FLICK_CROSS_POPUP_TEXT_SIZE_SP.second
        )
        set(value) = preferences.edit {
            it.putFloat(
                FLICK_CROSS_POPUP_TEXT_SIZE_SP.first,
                value ?: FLICK_CROSS_POPUP_TEXT_SIZE_SP.second
            )
        }

    var flick_standard_popup_size_scale_percent: Int?
        get() = preferences.getInt(
            FLICK_STANDARD_POPUP_SIZE_SCALE_PERCENT.first,
            FLICK_STANDARD_POPUP_SIZE_SCALE_PERCENT.second
        )
        set(value) = preferences.edit {
            it.putInt(
                FLICK_STANDARD_POPUP_SIZE_SCALE_PERCENT.first,
                value ?: FLICK_STANDARD_POPUP_SIZE_SCALE_PERCENT.second
            )
        }

    var flick_standard_popup_text_size_sp: Float?
        get() = preferences.getFloat(
            FLICK_STANDARD_POPUP_TEXT_SIZE_SP.first,
            FLICK_STANDARD_POPUP_TEXT_SIZE_SP.second
        )
        set(value) = preferences.edit {
            it.putFloat(
                FLICK_STANDARD_POPUP_TEXT_SIZE_SP.first,
                value ?: FLICK_STANDARD_POPUP_TEXT_SIZE_SP.second
            )
        }

    var flick_tfbi_popup_size_scale_percent: Int?
        get() = preferences.getInt(
            FLICK_TFBI_POPUP_SIZE_SCALE_PERCENT.first,
            FLICK_TFBI_POPUP_SIZE_SCALE_PERCENT.second
        )
        set(value) = preferences.edit {
            it.putInt(
                FLICK_TFBI_POPUP_SIZE_SCALE_PERCENT.first,
                value ?: FLICK_TFBI_POPUP_SIZE_SCALE_PERCENT.second
            )
        }

    var flick_tfbi_popup_text_size_sp: Float?
        get() = preferences.getFloat(
            FLICK_TFBI_POPUP_TEXT_SIZE_SP.first,
            FLICK_TFBI_POPUP_TEXT_SIZE_SP.second
        )
        set(value) = preferences.edit {
            it.putFloat(
                FLICK_TFBI_POPUP_TEXT_SIZE_SP.first,
                value ?: FLICK_TFBI_POPUP_TEXT_SIZE_SP.second
            )
        }

    var flick_tfbi_popup_presentation: TfbiPopupPresentationMode
        get() = TfbiPopupPresentationMode.fromPreference(
            preferences.getString(
                FLICK_TFBI_POPUP_PRESENTATION.first,
                FLICK_TFBI_POPUP_PRESENTATION.second
            )
        )
        set(value) = preferences.edit {
            it.putString(FLICK_TFBI_POPUP_PRESENTATION.first, value.preferenceValue)
        }

    var flick_tfbi_flick_start_position: TfbiFlickStartPositionMode
        get() = TfbiFlickStartPositionMode.fromPreference(
            preferences.getString(
                FLICK_TFBI_FLICK_START_POSITION.first,
                FLICK_TFBI_FLICK_START_POSITION.second
            )
        )
        set(value) = preferences.edit {
            it.putString(FLICK_TFBI_FLICK_START_POSITION.first, value.preferenceValue)
        }

    var clipboard_preview_preference: Boolean
        get() = preferences.getBoolean(
            CLIP_BOARD_PREVIEW_PREFERENCE.first,
            CLIP_BOARD_PREVIEW_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CLIP_BOARD_PREVIEW_PREFERENCE.first, value)
        }

    var clipboard_preview_tap_delete_preference: Boolean
        get() = preferences.getBoolean(
            CLIP_BOARD_PREVIEW_TAP_DELETE_PREFERENCE.first,
            CLIP_BOARD_PREVIEW_TAP_DELETE_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CLIP_BOARD_PREVIEW_TAP_DELETE_PREFERENCE.first, value)
        }

    var clipboard_delete_unpinned_after_hours_preference: Boolean
        get() = preferences.getBoolean(
            CLIPBOARD_DELETE_UNPINNED_AFTER_HOURS_PREFERENCE.first,
            CLIPBOARD_DELETE_UNPINNED_AFTER_HOURS_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CLIPBOARD_DELETE_UNPINNED_AFTER_HOURS_PREFERENCE.first, value)
        }

    var clipboard_unpinned_retention_hours_preference: Int
        get() = preferences.getInt(
            CLIPBOARD_UNPINNED_RETENTION_HOURS_PREFERENCE.first,
            CLIPBOARD_UNPINNED_RETENTION_HOURS_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putInt(CLIPBOARD_UNPINNED_RETENTION_HOURS_PREFERENCE.first, value.coerceIn(1, 72))
        }

    val keyboard_corner_round_preference: Boolean get() = false // S3: hardcoded, setting removed

    var keyboard_corner_radius_dp_preference: Int
        get() = preferences.getInt(
            KEYBOARD_CORNER_RADIUS_DP_PREFERENCE.first,
            KEYBOARD_CORNER_RADIUS_DP_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putInt(
                KEYBOARD_CORNER_RADIUS_DP_PREFERENCE.first,
                value.coerceIn(0, 64)
            )
        }

    var keyboard_corner_top_left_preference: Boolean
        get() = preferences.getBoolean(
            KEYBOARD_CORNER_TOP_LEFT_PREFERENCE.first,
            KEYBOARD_CORNER_TOP_LEFT_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(KEYBOARD_CORNER_TOP_LEFT_PREFERENCE.first, value)
        }

    var keyboard_corner_top_right_preference: Boolean
        get() = preferences.getBoolean(
            KEYBOARD_CORNER_TOP_RIGHT_PREFERENCE.first,
            KEYBOARD_CORNER_TOP_RIGHT_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(KEYBOARD_CORNER_TOP_RIGHT_PREFERENCE.first, value)
        }

    var keyboard_corner_bottom_left_preference: Boolean
        get() = preferences.getBoolean(
            KEYBOARD_CORNER_BOTTOM_LEFT_PREFERENCE.first,
            KEYBOARD_CORNER_BOTTOM_LEFT_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(KEYBOARD_CORNER_BOTTOM_LEFT_PREFERENCE.first, value)
        }

    var keyboard_corner_bottom_right_preference: Boolean
        get() = preferences.getBoolean(
            KEYBOARD_CORNER_BOTTOM_RIGHT_PREFERENCE.first,
            KEYBOARD_CORNER_BOTTOM_RIGHT_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(KEYBOARD_CORNER_BOTTOM_RIGHT_PREFERENCE.first, value)
        }

    var reconversion_preference: Boolean
        get() = preferences.getBoolean(
            RECONVERSION_PREFERENCE.first,
            RECONVERSION_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(RECONVERSION_PREFERENCE.first, value)
        }

    var conversion_key_swipe_cursor_move_preference: Boolean
        get() = preferences.getBoolean(
            CONVERSION_KEY_SWIPE_CURSOR_MOVE_PREFERENCE.first,
            CONVERSION_KEY_SWIPE_CURSOR_MOVE_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CONVERSION_KEY_SWIPE_CURSOR_MOVE_PREFERENCE.first, value)
        }

    var physical_keyboard_input_mode_preference: String
        get() = preferences.getString(
            PHYSICAL_KEYBOARD_INPUT_MODE_PREFERENCE.first,
            PHYSICAL_KEYBOARD_INPUT_MODE_PREFERENCE.second
        ) ?: PHYSICAL_KEYBOARD_INPUT_MODE_PREFERENCE.second
        set(value) = preferences.edit {
            it.putString(PHYSICAL_KEYBOARD_INPUT_MODE_PREFERENCE.first, value)
        }

    var romaji_map_data_version: Int
        get() = preferences.getInt(ROMAJI_MAP_DATA_VERSION.first, ROMAJI_MAP_DATA_VERSION.second)
        set(value) = preferences.edit {
            it.putInt(ROMAJI_MAP_DATA_VERSION.first, value)
        }


    var last_pasted_clipboard_text_preference: String
        get() = preferences.getString(
            LAST_PASTED_CLIPBOARD_TEXT_PREFERENCE.first,
            LAST_PASTED_CLIPBOARD_TEXT_PREFERENCE.second
        ) ?: ""
        set(value) = preferences.edit {
            it.putString(LAST_PASTED_CLIPBOARD_TEXT_PREFERENCE.first, value)
        }

    var tenkey_show_language_button_preference: Boolean
        get() = preferences.getBoolean(
            TENKEY_SHOW_IME_SWITCH_BUTTON.first,
            TENKEY_SHOW_IME_SWITCH_BUTTON.second
        )
        set(value) = preferences.edit {
            it.putBoolean(TENKEY_SHOW_IME_SWITCH_BUTTON.first, value)
        }

    var necookey_two_row_candidate_bar_preference: Boolean
        get() = preferences.getBoolean(
            NECOOKEY_TWO_ROW_CANDIDATE_BAR_PREFERENCE.first,
            NECOOKEY_TWO_ROW_CANDIDATE_BAR_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(NECOOKEY_TWO_ROW_CANDIDATE_BAR_PREFERENCE.first, value)
        }

    var necookey_zenz_bunsetsu_gate_preference: Boolean
        get() = preferences.getBoolean(
            NECOOKEY_ZENZ_BUNSETSU_GATE_PREFERENCE.first,
            NECOOKEY_ZENZ_BUNSETSU_GATE_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(NECOOKEY_ZENZ_BUNSETSU_GATE_PREFERENCE.first, value)
        }













    var liquid_glass_preference: Boolean
        get() = preferences.getBoolean(
            LIQUID_GLASS_ENABLE.first,
            LIQUID_GLASS_ENABLE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(LIQUID_GLASS_ENABLE.first, value)
        }

    var liquid_glass_blur_radius: Int
        get() = preferences.getInt(
            LIQUID_GLASS_BLUR_RADIUS.first,
            LIQUID_GLASS_BLUR_RADIUS.second
        )
        set(value) = preferences.edit {
            it.putInt(LIQUID_GLASS_BLUR_RADIUS.first, value)
        }

    var custom_theme_border_enable: Boolean
        get() = preferences.getBoolean(
            CUSTOM_THEME_BORDER_ENABLE.first,
            CUSTOM_THEME_BORDER_ENABLE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CUSTOM_THEME_BORDER_ENABLE.first, value)
        }

    var custom_theme_border_color: Int
        get() = preferences.getInt(
            CUSTOM_THEME_BORDER_COLOR.first,
            CUSTOM_THEME_BORDER_COLOR.second
        )
        set(value) = preferences.edit {
            it.putInt(CUSTOM_THEME_BORDER_COLOR.first, value)
        }

    var liquid_glass_key_alpha: Int
        get() = preferences.getInt(
            LIQUID_GLASS_KEY_ALPHA.first,
            LIQUID_GLASS_KEY_ALPHA.second
        )
        set(value) = preferences.edit {
            it.putInt(LIQUID_GLASS_KEY_ALPHA.first, value)
        }

    var custom_theme_input_color_enable: Boolean
        get() = preferences.getBoolean(
            CUSTOM_THEME_INPUT_COLOR_ENABLE.first,
            CUSTOM_THEME_INPUT_COLOR_ENABLE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CUSTOM_THEME_INPUT_COLOR_ENABLE.first, value)
        }

    var custom_theme_pre_edit_bg_color: Int
        get() = preferences.getInt(CUSTOM_THEME_PRE_EDIT_BG.first, CUSTOM_THEME_PRE_EDIT_BG.second)
        set(value) = preferences.edit { it.putInt(CUSTOM_THEME_PRE_EDIT_BG.first, value) }

    var custom_theme_pre_edit_text_color: Int
        get() = preferences.getInt(
            CUSTOM_THEME_PRE_EDIT_TEXT.first,
            CUSTOM_THEME_PRE_EDIT_TEXT.second
        )
        set(value) = preferences.edit { it.putInt(CUSTOM_THEME_PRE_EDIT_TEXT.first, value) }

    var custom_theme_post_edit_bg_color: Int
        get() = preferences.getInt(
            CUSTOM_THEME_POST_EDIT_BG.first,
            CUSTOM_THEME_POST_EDIT_BG.second
        )
        set(value) = preferences.edit { it.putInt(CUSTOM_THEME_POST_EDIT_BG.first, value) }

    var custom_theme_post_edit_text_color: Int
        get() = preferences.getInt(
            CUSTOM_THEME_POST_EDIT_TEXT.first,
            CUSTOM_THEME_POST_EDIT_TEXT.second
        )
        set(value) = preferences.edit { it.putInt(CUSTOM_THEME_POST_EDIT_TEXT.first, value) }








    var conversion_candidates_romaji_enable_preference: Boolean
        get() = preferences.getBoolean(
            CONVERSION_CANDIDATES_ROMAJI_ENABLE_PREFERENCE.first,
            CONVERSION_CANDIDATES_ROMAJI_ENABLE_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(CONVERSION_CANDIDATES_ROMAJI_ENABLE_PREFERENCE.first, value)
        }

    var learn_first_candidate_dictionary_preference: Boolean
        get() = preferences.getBoolean(
            LEARN_FIRST_CANDIDATE_DICTIONARY_PREFERENCE.first,
            LEARN_FIRST_CANDIDATE_DICTIONARY_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(LEARN_FIRST_CANDIDATE_DICTIONARY_PREFERENCE.first, value)
        }

    var learn_dictionary_allow_mixed_symbols_numbers_preference: Boolean
        get() = preferences.getBoolean(
            LEARN_DICTIONARY_ALLOW_MIXED_SYMBOLS_NUMBERS_PREFERENCE.first,
            LEARN_DICTIONARY_ALLOW_MIXED_SYMBOLS_NUMBERS_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(LEARN_DICTIONARY_ALLOW_MIXED_SYMBOLS_NUMBERS_PREFERENCE.first, value)
        }

    var enable_prediction_search_learn_dictionary_preference: Boolean
        get() = preferences.getBoolean(
            ENABLE_PREDICTION_SEARCH_LEARN_DICTIONARY_PREFERENCE.first,
            ENABLE_PREDICTION_SEARCH_LEARN_DICTIONARY_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(ENABLE_PREDICTION_SEARCH_LEARN_DICTIONARY_PREFERENCE.first, value)
        }

    var learn_prediction_preference: Int
        get() = preferences.getInt(
            LEARN_PREDICTION_PREFERENCE.first,
            LEARN_PREDICTION_PREFERENCE.second
        )
        set(value) = preferences.edit { it.putInt(LEARN_PREDICTION_PREFERENCE.first, value) }

    var learn_dictionary_prediction_candidate_limit_preference: Int
        get() = preferences.getInt(
            LEARN_PREDICTION_CANDIDATE_LIMIT_PREFERENCE.first,
            LEARN_PREDICTION_CANDIDATE_LIMIT_PREFERENCE.second,
        ).coerceIn(1, 8)
        set(value) = preferences.edit {
            it.putInt(
                LEARN_PREDICTION_CANDIDATE_LIMIT_PREFERENCE.first,
                value.coerceIn(1, 8),
            )
        }

    var circular_flickWindow_scale: Float
        get() = preferences.getFloat(
            CIRCULAR_FLICK_WINDOW_SCALE.first,
            CIRCULAR_FLICK_WINDOW_SCALE.second
        )
        set(value) = preferences.edit { it.putFloat(CIRCULAR_FLICK_WINDOW_SCALE.first, value) }

    var custom_theme_border_width: Int
        get() = preferences.getInt(
            CUSTOM_THEME_BORDER_WIDTH.first,
            CUSTOM_THEME_BORDER_WIDTH.second
        )
        set(value) = preferences.edit {
            it.putInt(CUSTOM_THEME_BORDER_WIDTH.first, value)
        }

    var circularFlick5DirectionsEnable: Boolean
        get() = preferences.getBoolean(
            PREF_FIVE_DIRECTIONS_ENABLE.first,
            PREF_FIVE_DIRECTIONS_ENABLE.second
        )
        set(value) = preferences.edit { it.putBoolean(PREF_FIVE_DIRECTIONS_ENABLE.first, value) }

    var circularFlickDirectionCount: Int
        get() {
            val hasNewPreference = preferences.contains(PREF_CIRCULAR_DIRECTION_COUNT.first)
            val raw = if (hasNewPreference) {
                preferences.getInt(
                    PREF_CIRCULAR_DIRECTION_COUNT.first,
                    PREF_CIRCULAR_DIRECTION_COUNT.second
                )
            } else if (circularFlick5DirectionsEnable) {
                5
            } else {
                PREF_CIRCULAR_DIRECTION_COUNT.second
            }
            return raw.coerceIn(4, 7)
        }
        set(value) = preferences.edit {
            it.putInt(PREF_CIRCULAR_DIRECTION_COUNT.first, value.coerceIn(4, 7))
        }

    var circularFlickUpStart: Float
        get() = preferences.getFloat(PREF_UP_START.first, PREF_UP_START.second)
        set(value) = preferences.edit { it.putFloat(PREF_UP_START.first, value) }

    var circularFlickUpSweep: Float
        get() = preferences.getFloat(PREF_UP_SWEEP.first, PREF_UP_SWEEP.second)
        set(value) = preferences.edit { it.putFloat(PREF_UP_SWEEP.first, value) }

    var circularFlickRightStart: Float
        get() = preferences.getFloat(PREF_RIGHT_START.first, PREF_RIGHT_START.second)
        set(value) = preferences.edit { it.putFloat(PREF_RIGHT_START.first, value) }

    var circularFlickRightSweep: Float
        get() = preferences.getFloat(PREF_RIGHT_SWEEP.first, PREF_RIGHT_SWEEP.second)
        set(value) = preferences.edit { it.putFloat(PREF_RIGHT_SWEEP.first, value) }

    var circularFlickDownStart: Float
        get() = preferences.getFloat(PREF_DOWN_START.first, PREF_DOWN_START.second)
        set(value) = preferences.edit { it.putFloat(PREF_DOWN_START.first, value) }

    var circularFlickDownSweep: Float
        get() = preferences.getFloat(PREF_DOWN_SWEEP.first, PREF_DOWN_SWEEP.second)
        set(value) = preferences.edit { it.putFloat(PREF_DOWN_SWEEP.first, value) }

    var circularFlickLeftStart: Float
        get() = preferences.getFloat(PREF_LEFT_START.first, PREF_LEFT_START.second)
        set(value) = preferences.edit { it.putFloat(PREF_LEFT_START.first, value) }

    var circularFlickLeftSweep: Float
        get() = preferences.getFloat(PREF_LEFT_SWEEP.first, PREF_LEFT_SWEEP.second)
        set(value) = preferences.edit { it.putFloat(PREF_LEFT_SWEEP.first, value) }

    var circularFlickUpRightStart: Float
        get() = preferences.getFloat(PREF_UP_RIGHT_START.first, PREF_UP_RIGHT_START.second)
        set(value) = preferences.edit { it.putFloat(PREF_UP_RIGHT_START.first, value) }

    var circularFlickUpRightSweep: Float
        get() = preferences.getFloat(PREF_UP_RIGHT_SWEEP.first, PREF_UP_RIGHT_SWEEP.second)
        set(value) = preferences.edit { it.putFloat(PREF_UP_RIGHT_SWEEP.first, value) }

    var circularFlickSlot5Start: Float
        get() = preferences.getFloat(PREF_SLOT_5_START.first, PREF_SLOT_5_START.second)
        set(value) = preferences.edit { it.putFloat(PREF_SLOT_5_START.first, value) }

    var circularFlickSlot5Sweep: Float
        get() = preferences.getFloat(PREF_SLOT_5_SWEEP.first, PREF_SLOT_5_SWEEP.second)
        set(value) = preferences.edit { it.putFloat(PREF_SLOT_5_SWEEP.first, value) }

    var circularFlickSlot6Start: Float
        get() = preferences.getFloat(PREF_SLOT_6_START.first, PREF_SLOT_6_START.second)
        set(value) = preferences.edit { it.putFloat(PREF_SLOT_6_START.first, value) }

    var circularFlickSlot6Sweep: Float
        get() = preferences.getFloat(PREF_SLOT_6_SWEEP.first, PREF_SLOT_6_SWEEP.second)
        set(value) = preferences.edit { it.putFloat(PREF_SLOT_6_SWEEP.first, value) }


    var keyboard_margin_start_dp: Int?
        get() = preferences.getInt(KEYBOARD_MARGIN_START_DP.first, KEYBOARD_MARGIN_START_DP.second)
        set(value) = preferences.edit { it.putInt(KEYBOARD_MARGIN_START_DP.first, value ?: 0) }

    var keyboard_margin_end_dp: Int?
        get() = preferences.getInt(KEYBOARD_MARGIN_END_DP.first, KEYBOARD_MARGIN_END_DP.second)
        set(value) = preferences.edit { it.putInt(KEYBOARD_MARGIN_END_DP.first, value ?: 0) }



    var keyboard_margin_start_dp_landscape: Int?
        get() = preferences.getInt(
            KEYBOARD_MARGIN_START_DP_LANDSCAPE.first,
            KEYBOARD_MARGIN_START_DP_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(
                KEYBOARD_MARGIN_START_DP_LANDSCAPE.first,
                value ?: 0
            )
        }

    var keyboard_margin_end_dp_landscape: Int?
        get() = preferences.getInt(
            KEYBOARD_MARGIN_END_DP_LANDSCAPE.first,
            KEYBOARD_MARGIN_END_DP_LANDSCAPE.second
        )
        set(value) = preferences.edit {
            it.putInt(
                KEYBOARD_MARGIN_END_DP_LANDSCAPE.first,
                value ?: 0
            )
        }



    var sumire_lm_homophone_margin_preference: Boolean
        get() = preferences.getBoolean(
            SUMIRE_LM_HOMOPHONE_MARGIN_PREFERENCE.first,
            SUMIRE_LM_HOMOPHONE_MARGIN_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(SUMIRE_LM_HOMOPHONE_MARGIN_PREFERENCE.first, value)
        }

    var lattice_lm_cost_preference: Boolean
        get() = preferences.getBoolean(
            LATTICE_LM_COST_PREFERENCE.first,
            LATTICE_LM_COST_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putBoolean(LATTICE_LM_COST_PREFERENCE.first, value)
        }

    var save_last_used_keyboard_enable_preference: Boolean
        get() = preferences.getBoolean(
            SAVE_LAST_USED_KEYBOARD.first,
            SAVE_LAST_USED_KEYBOARD.second
        )
        set(value) = preferences.edit {
            it.putBoolean(SAVE_LAST_USED_KEYBOARD.first, value)
        }

    var save_last_used_keyboard_position_preference: Int
        get() = preferences.getInt(
            SAVE_LAST_USED_KEYBOARD_POSITION.first,
            SAVE_LAST_USED_KEYBOARD_POSITION.second
        )
        set(value) = preferences.edit {
            it.putInt(SAVE_LAST_USED_KEYBOARD_POSITION.first, value)
        }

    var enable_typo_correction_japanese_flick_keyboard_preference: Boolean
        get() = preferences.getBoolean(
            TYPO_CORRECTION_JA_FLICK.first,
            TYPO_CORRECTION_JA_FLICK.second
        )
        set(value) = preferences.edit {
            it.putBoolean(TYPO_CORRECTION_JA_FLICK.first, value)
        }


    var custom_romaji_zenkaku_conversion_enable_preference: Boolean
        get() = CUSTOM_ROMAJI_ZENKAKU_CONVERSION_ENABLE_PREFERENCE.second // 設定UI削除済み: 保存値を無視して既定値に固定
        set(value) = preferences.edit {
            it.putBoolean(
                CUSTOM_ROMAJI_ZENKAKU_CONVERSION_ENABLE_PREFERENCE.first,
                value
            )
        }

    var omission_search_offset_score_preference: Int
        get() = OMISSION_SEARCH_OFFSET_SCORE_PREFERENCE.second // 設定UI削除済み: 保存値を無視して既定値に固定
        set(value) = preferences.edit {
            it.putInt(OMISSION_SEARCH_OFFSET_SCORE_PREFERENCE.first, value)
        }

    var enable_typo_correction_japanese_flick_keyboard_offset_score_preference: Int
        get() = preferences.getInt(
            TYPO_CORRECTION_JA_FLICK_OFFSET_SCORE_PREFERENCE.first,
            TYPO_CORRECTION_JA_FLICK_OFFSET_SCORE_PREFERENCE.second
        )
        set(value) = preferences.edit {
            it.putInt(TYPO_CORRECTION_JA_FLICK_OFFSET_SCORE_PREFERENCE.first, value)
        }

    /**
     * キーボード側で使用するためのマップ取得メソッド
     * 戻り値: Map<FlickDirection, Pair<StartAngle, SweepAngle>>
     */
    fun getCircularFlickSlotRanges(): Map<CircularFlickDirection, Pair<Float, Float>> {
        val count = circularFlickDirectionCount
        val defaults = getDefaultCircularFlickSlotRanges(count)

        val baseMap = mutableMapOf(
            CircularFlickDirection.SLOT_0 to getCircularFlickRange(
                CircularFlickDirection.SLOT_0,
                defaults.getValue(CircularFlickDirection.SLOT_0)
            ),
            CircularFlickDirection.SLOT_1 to getCircularFlickRange(
                CircularFlickDirection.SLOT_1,
                defaults.getValue(CircularFlickDirection.SLOT_1)
            ),
            CircularFlickDirection.SLOT_2 to getCircularFlickRange(
                CircularFlickDirection.SLOT_2,
                defaults.getValue(CircularFlickDirection.SLOT_2)
            ),
            CircularFlickDirection.SLOT_3 to getCircularFlickRange(
                CircularFlickDirection.SLOT_3,
                defaults.getValue(CircularFlickDirection.SLOT_3)
            )
        )

        if (count >= 5) {
            baseMap[CircularFlickDirection.SLOT_4] = getCircularFlickRange(
                CircularFlickDirection.SLOT_4,
                defaults.getValue(CircularFlickDirection.SLOT_4)
            )
        }

        if (count >= 6) {
            baseMap[CircularFlickDirection.SLOT_5] = getCircularFlickRange(
                CircularFlickDirection.SLOT_5,
                defaults.getValue(CircularFlickDirection.SLOT_5)
            )
        }

        if (count >= 7) {
            baseMap[CircularFlickDirection.SLOT_6] = getCircularFlickRange(
                CircularFlickDirection.SLOT_6,
                defaults.getValue(CircularFlickDirection.SLOT_6)
            )
        }

        return baseMap
    }

    private fun getDefaultCircularFlickSlotRanges(
        count: Int
    ): Map<CircularFlickDirection, Pair<Float, Float>> {
        return when (count.coerceIn(4, 7)) {
            4 -> mapOf(
                CircularFlickDirection.SLOT_0 to (225f to 90f),
                CircularFlickDirection.SLOT_1 to (315f to 90f),
                CircularFlickDirection.SLOT_2 to (45f to 90f),
                CircularFlickDirection.SLOT_3 to (135f to 90f)
            )

            5 -> mapOf(
                CircularFlickDirection.SLOT_0 to (234f to 72f),
                CircularFlickDirection.SLOT_1 to (306f to 72f),
                CircularFlickDirection.SLOT_2 to (18f to 72f),
                CircularFlickDirection.SLOT_3 to (90f to 72f),
                CircularFlickDirection.SLOT_4 to (162f to 72f)
            )

            else -> buildEvenCircularRanges(count)
        }
    }

    private fun getCircularFlickRange(
        direction: CircularFlickDirection,
        defaultRange: Pair<Float, Float>
    ): Pair<Float, Float> {
        val (startPreference, sweepPreference) = when (direction) {
            CircularFlickDirection.SLOT_0 -> PREF_UP_START to PREF_UP_SWEEP
            CircularFlickDirection.SLOT_1 -> PREF_RIGHT_START to PREF_RIGHT_SWEEP
            CircularFlickDirection.SLOT_2 -> PREF_DOWN_START to PREF_DOWN_SWEEP
            CircularFlickDirection.SLOT_3 -> PREF_LEFT_START to PREF_LEFT_SWEEP
            CircularFlickDirection.SLOT_4 -> PREF_UP_RIGHT_START to PREF_UP_RIGHT_SWEEP
            CircularFlickDirection.SLOT_5 -> PREF_SLOT_5_START to PREF_SLOT_5_SWEEP
            CircularFlickDirection.SLOT_6 -> PREF_SLOT_6_START to PREF_SLOT_6_SWEEP
            CircularFlickDirection.TAP -> return defaultRange
        }

        return preferences.getFloat(startPreference.first, defaultRange.first) to
                preferences.getFloat(sweepPreference.first, defaultRange.second)
    }

    fun getCircularFlickRanges(): Map<CircularFlickDirection, Pair<Float, Float>> {
        return getCircularFlickSlotRanges()
    }

    /**
     * SharedPreferences 全体を JSON として書き出す（将来キーが増えても自動対応）
     */
    fun exportAllToJson(): String {
        val all = preferences.all // Map<String, *>
        val entries = all.entries.map { (k, v) ->
            when (v) {
                null -> PrefEntry(k, "null", null)
                is Boolean -> PrefEntry(k, "boolean", v)
                is Int -> PrefEntry(k, "int", v)
                is Long -> PrefEntry(k, "long", v)
                is Float -> PrefEntry(k, "float", v)
                is String -> PrefEntry(k, "string", v)
                is Set<*> -> {
                    val list = v.filterIsInstance<String>()
                    PrefEntry(k, "string_set", list)
                }

                else -> {
                    // 想定外は string に落とす（壊さないため）
                    PrefEntry(k, "string", v.toString())
                }
            }
        }

        return gson.toJson(PrefBackup(entries = entries))
    }

    /**
     * JSON を読み込んで SharedPreferences に反映
     * @param replaceAll true の場合は一旦 clear() してから復元（バックアップ復元向け）
     */
    fun importAllFromJson(json: String, replaceAll: Boolean = true) {
        val backup = try {
            gson.fromJson(json, PrefBackup::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid backup json", e)
        }

        preferences.edit { editor ->
            if (replaceAll) editor.clear()

            backup.entries.forEach { e ->
                when (e.type) {
                    "null" -> editor.remove(e.key)
                    "boolean" -> editor.putBoolean(e.key, (e.value as Boolean))
                    "int" -> editor.putInt(e.key, (e.value as Number).toInt())
                    "long" -> editor.putLong(e.key, (e.value as Number).toLong())
                    "float" -> editor.putFloat(e.key, (e.value as Number).toFloat())
                    "string" -> editor.putString(e.key, e.value as String)
                    "string_set" -> {
                        val list = (e.value as List<*>).filterIsInstance<String>()
                        editor.putStringSet(e.key, list.toSet())
                    }

                    else -> {
                        // unknown type は無視（将来バージョン差分で落ちないように）
                    }
                }
            }
        }
    }

}

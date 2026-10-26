package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategoryLoadState
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionAggressiveness
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig

/** Appearance settings are grouped to keep snapshot copy/default calls within DEX's 255-word limit. */
data class ImeKeyboardAppearance(
    val keyboardThemeMode: String,
    val customThemeBgColor: Int,
    val customThemeKeyColor: Int,
    val customThemeSpecialKeyColor: Int,
    val customThemeKeyTextColor: Int,
    val customThemeSpecialKeyTextColor: Int,
    val customThemeCandidateTextColor: Int,
    val customThemeCandidateItemBgColor: Int,
    val customThemeCandidateItemPressedBgColor: Int,
    val customThemeCandidateEmptyPopupBgColor: Int,
    val customThemeCandidateEmptyPopupTextColor: Int,
    val customThemeShortcutIconColor: Int,
    val liquidGlassThemePreference: Boolean,
    val liquidGlassBlurRadiousPreference: Int,
    val liquidGlassKeyBlurRadiousPreference: Int,
    val customKeyBorderEnablePreference: Boolean,
    val customKeyBorderEnableColor: Int,
    val customComposingTextPreference: Boolean,
    val inputCompositionBackgroundColor: Int,
    val inputCompositionTextColor: Int,
    val inputConversionBackgroundColor: Int,
    val inputConversionTextColor: Int,
)

data class ImePreferencesSnapshot(
    val keyboardOrder: List<KeyboardType>,
    val candidateTabOrder: List<CandidateTab>,
    val mozcUTPersonName: Boolean,
    val mozcUTPlaces: Boolean,
    val mozcUTWiki: Boolean,
    val mozcUTNeologd: Boolean,
    val mozcUTWeb: Boolean,
    val isFlickOnlyMode: Boolean,
    val flickEditorPreviewPreference: Boolean,
    val flickEditorPreviewDelayMillis: Int,
    val isOmissionSearchEnable: Boolean,
    val delayTime: Int,
    val isLearnDictionaryMode: Boolean,
    val incognitoModeDetectionPreference: Boolean,
    val showLearnedCandidatesInIncognitoPreference: Boolean,
    val isUserDictionaryEnable: Boolean,
    val systemNgramDictionaryEnabled: Boolean,
    val showDictionaryCandidateLabels: Boolean,
    val suppressHentaiganaCandidates: Boolean,
    val hankakuPreference: Boolean,
    val isLiveConversionEnable: Boolean,
    val liveConversionStartLength: Int,
    val showLiveConversionCandidateYomi: Boolean,
    val liveConversionCandidateYomiTextSize: Int,
    val liveConversionCandidateYomiMode: String,
    val nBest: Int,
    val conversionBeamWidth: Int,
    val conversionBackend: ConversionBackend,
    val predictionConfig: PredictionConfig,
    val flickSensitivityPreferenceValue: Int,
    val flickThresholdShapePreferenceValue: String,
    val tfbiDiagonalRecognitionModePreferenceValue: String,
    val longPressTimeoutPreferenceValue: Int,
    val tenkeyShowIMEButtonPreference: Boolean,
    val tenkeySpaceFlickPreference: Boolean,
    val qwertyEnableZenkakuSpacePreference: Boolean,
    val qwertyRomajiHankakuNumberPreference: Boolean,
    val qwertyRomajiHankakuSymbolPreference: Boolean,
    val showCandidateInPasswordPreference: Boolean,
    val isNgWordEnable: Boolean,
    val deleteKeyHighLight: Boolean,
    val customKeyboardSuggestionPreference: Boolean,
    val userDictionaryPrefixMatchNumber: Int,
    val candidateColumns: String,
    val candidateColumnsLandscape: String,
    val candidateTabVisibility: Boolean,
    val stabilizeCandidateStripHeightPreference: Boolean,
    val symbolKeyboardFirstItem: SymbolMode,
    val defaultEmojiSkinTone: String,
    val tenkeyUseThreeStateKeyboard: Boolean,
    val independentMultiTouchEnabled: Boolean,
    val tenkeyNumberSymbolKeyGapDp: Int,
    val customKeymapGuide: Boolean,
    val flickGuideTextSizeSp: Int,
    val flickGuideMaxCharacters: Int,
    val isKeyboardFloatingMode: Boolean,
    val isKeyboardRounded: Boolean,
    val keyboardCornerRadiusDp: Int,
    val keyboardCornerTopLeft: Boolean,
    val keyboardCornerTopRight: Boolean,
    val keyboardCornerBottomLeft: Boolean,
    val keyboardCornerBottomRight: Boolean,
    val reconversionEnabled: Boolean,
    val conversionKeySwipePreference: Boolean,
    val physicalKeyboardInputMode: String,
    val shortcutTollbarVisibility: Boolean,
    val shortcutToolbarIntegratedInSuggestion: Boolean,
    val shortcutToolbarHeightDp: Int,
    val shortcutToolbarIconSizeDp: Int,
    val isDeleteLeftFlickPreference: Boolean,
    val isDeleteUpFlickPreference: Boolean,
    val isDeleteDownFlickPreference: Boolean,
    val clipboardPreviewVisibility: Boolean,
    val clipboardPreviewTapToDelete: Boolean,
    val tenkeyHeightPreferenceValue: Int,
    val tenkeyWidthPreferenceValue: Int,
    val candidateViewHeightPreferenceValue: Int,
    val candidateViewHeightEmptyPreferenceValue: Int,
    val tenkeyPositionPreferenceValue: Boolean,
    val tenkeyBottomMarginPreferenceValue: Int,
    val tenkeyStartMarginPreferenceValue: Int,
    val tenkeyEndMarginPreferenceValue: Int,
    val tenkeyLandscapeStartMarginPreferenceValue: Int,
    val tenkeyLandscapeEndMarginPreferenceValue: Int,
    val enableShowLastShownKeyboardInRestart: Boolean,
    val lastSavedKeyboardPosition: Int,
    val tenkeyHeightLandscapePreferenceValue: Int,
    val tenkeyWidthLandscapePreferenceValue: Int,
    val candidateViewLandscapeHeightPreferenceValue: Int,
    val candidateViewLandscapeHeightEmptyPreferenceValue: Int,
    val tenkeyLandscapePositionPreferenceValue: Boolean,
    val tenkeyLandscapeBottomMarginPreferenceValue: Int,
    val appearance: ImeKeyboardAppearance,
    val conversionCandidatesRomajiEnablePreference: Boolean,
    val learnFirstCandidateDictionaryPreference: Boolean,
    val learnDictionaryAllowMixedSymbolsNumbersPreference: Boolean,
    val enablePredictionSearchLearnDictionaryPreference: Boolean,
    val learnPredictionPreference: Int,
    val userDictionaryPredictionCandidateLimit: Int,
    val learnDictionaryPredictionCandidateLimit: Int,
    val customKeyBorderWidth: Int,
    val customRomajiZenkakuConversionEnablePreference: Boolean,
    val omissionSearchOffsetScorePreference: Int,
    val enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference: Int,
    val enableTypoCorrectionJapaneseFlickKeyboardPreference: Boolean,
    val keyboardSkin: KeyboardSkinId = KeyboardSkinId.DEFAULT,
) {
    val keyboardThemeMode get() = appearance.keyboardThemeMode
    val customThemeBgColor get() = appearance.customThemeBgColor
    val customThemeKeyColor get() = appearance.customThemeKeyColor
    val customThemeSpecialKeyColor get() = appearance.customThemeSpecialKeyColor
    val customThemeKeyTextColor get() = appearance.customThemeKeyTextColor
    val customThemeSpecialKeyTextColor get() = appearance.customThemeSpecialKeyTextColor
    val customThemeCandidateTextColor get() = appearance.customThemeCandidateTextColor
    val customThemeCandidateItemBgColor get() = appearance.customThemeCandidateItemBgColor
    val customThemeCandidateItemPressedBgColor get() = appearance.customThemeCandidateItemPressedBgColor
    val customThemeCandidateEmptyPopupBgColor get() = appearance.customThemeCandidateEmptyPopupBgColor
    val customThemeCandidateEmptyPopupTextColor get() = appearance.customThemeCandidateEmptyPopupTextColor
    val customThemeShortcutIconColor get() = appearance.customThemeShortcutIconColor
    val liquidGlassThemePreference get() = appearance.liquidGlassThemePreference
    val liquidGlassBlurRadiousPreference get() = appearance.liquidGlassBlurRadiousPreference
    val liquidGlassKeyBlurRadiousPreference get() = appearance.liquidGlassKeyBlurRadiousPreference
    val customKeyBorderEnablePreference get() = appearance.customKeyBorderEnablePreference
    val customKeyBorderEnableColor get() = appearance.customKeyBorderEnableColor
    val customComposingTextPreference get() = appearance.customComposingTextPreference
    val inputCompositionBackgroundColor get() = appearance.inputCompositionBackgroundColor
    val inputCompositionTextColor get() = appearance.inputCompositionTextColor
    val inputConversionBackgroundColor get() = appearance.inputConversionBackgroundColor
    val inputConversionTextColor get() = appearance.inputConversionTextColor

    /** Presentation overrides only: saved values and all input/layout fields remain intact. */
    fun withKeyboardSkinAppearance(): ImePreferencesSnapshot {
        val palette = KeyboardSkinRegistry.find(keyboardSkin)?.palette ?: return this
        return copy(
            appearance = appearance.copy(
                keyboardThemeMode = "custom",
                customThemeBgColor = palette.background,
                customThemeKeyColor = palette.key,
                customThemeSpecialKeyColor = palette.specialKey,
                customThemeKeyTextColor = palette.text,
                customThemeSpecialKeyTextColor = palette.specialText,
                customThemeCandidateTextColor = palette.text,
                customThemeCandidateItemBgColor = palette.background,
                customThemeCandidateItemPressedBgColor = palette.pressed,
                customThemeCandidateEmptyPopupBgColor = palette.key,
                customThemeCandidateEmptyPopupTextColor = palette.text,
                customThemeShortcutIconColor = palette.text,
                liquidGlassThemePreference = false,
                liquidGlassKeyBlurRadiousPreference = 255,
                customKeyBorderEnablePreference = false,
            ),
        )
    }

    companion object {
        fun from(
            appPreference: AppPreference,
            dictionarySourceResolver: DictionarySourceResolver? = null,
            customThemeCandidateItemPressedBgColorDefault: Int =
                AppPreference.DEFAULT_CUSTOM_THEME_CANDIDATE_ITEM_PRESSED_BG_COLOR
        ): ImePreferencesSnapshot {
            fun optionalEnabled(category: DictionaryCategory, legacyValue: Boolean?): Boolean {
                val state = dictionarySourceResolver?.resolveCategoryLoadState(category)
                return when (state) {
                    DictionaryCategoryLoadState.User,
                    DictionaryCategoryLoadState.Bundled -> true
                    DictionaryCategoryLoadState.Disabled,
                    DictionaryCategoryLoadState.Partial,
                    DictionaryCategoryLoadState.Invalid,
                    DictionaryCategoryLoadState.Missing -> false
                    null -> legacyValue ?: false
                }
            }
            return ImePreferencesSnapshot(
                keyboardOrder = appPreference.keyboard_order,
                candidateTabOrder = appPreference.candidate_tab_order,
                mozcUTPersonName = optionalEnabled(
                    DictionaryCategory.PERSON_NAME,
                    appPreference.mozc_ut_person_names_preference,
                ) && appPreference.ut_person_name_enabled,
                mozcUTPlaces = optionalEnabled(
                    DictionaryCategory.PLACES,
                    appPreference.mozc_ut_places_preference,
                ) && appPreference.ut_places_enabled,
                mozcUTWiki = optionalEnabled(
                    DictionaryCategory.WIKI,
                    appPreference.mozc_ut_wiki_preference,
                ) && appPreference.ut_wiki_enabled,
                mozcUTNeologd = optionalEnabled(
                    DictionaryCategory.NEOLOGD,
                    appPreference.mozc_ut_neologd_preference,
                ),
                mozcUTWeb = optionalEnabled(
                    DictionaryCategory.WEB,
                    appPreference.mozc_ut_web_preference,
                ),
                isFlickOnlyMode = appPreference.flick_input_only_preference ?: false,
                flickEditorPreviewPreference = appPreference.flick_editor_preview_preference,
                flickEditorPreviewDelayMillis = appPreference.flick_editor_preview_delay_ms,
                isOmissionSearchEnable = appPreference.omission_search_preference ?: false,
                delayTime = appPreference.time_same_pronounce_typing_preference ?: 1000,
                isLearnDictionaryMode = appPreference.learn_dictionary_preference ?: true,
                incognitoModeDetectionPreference =
                    appPreference.incognito_mode_detection_preference,
                showLearnedCandidatesInIncognitoPreference =
                    appPreference.show_learned_candidates_in_incognito_preference,
                isUserDictionaryEnable = appPreference.user_dictionary_preference ?: true,
                systemNgramDictionaryEnabled =
                    appPreference.system_ngram_dictionary_enable_preference,
                showDictionaryCandidateLabels =
                    appPreference.show_dictionary_candidate_labels_preference,
                suppressHentaiganaCandidates =
                    appPreference.suppress_hentaigana_candidates_preference,
                hankakuPreference = appPreference.space_hankaku_preference ?: false,
                isLiveConversionEnable = appPreference.live_conversion_preference ?: false,
                liveConversionStartLength =
                    appPreference.live_conversion_start_length_preference ?: 1,
                liveConversionCandidateYomiTextSize = appPreference.live_conversion_candidate_yomi_size,
                liveConversionCandidateYomiMode = appPreference.live_conversion_candidate_yomi_mode,
                showLiveConversionCandidateYomi =
                    appPreference.live_conversion_candidate_yomi_preference ?: false,
                nBest = appPreference.n_best_preference ?: 4,
                conversionBeamWidth = appPreference.conversion_beam_width_preference,
                conversionBackend = if (
                    appPreference.incremental_conversion_session_preference ||
                    appPreference.live_conversion_preference == true
                ) {
                    ConversionBackend.INCREMENTAL_SESSION
                } else {
                    ConversionBackend.LEGACY
                },
                predictionConfig = PredictionConfig(
                    japanesePredictionEnabled =
                        appPreference.japanese_prediction_enable_preference,
                    englishPredictionEnabled =
                        appPreference.english_prediction_enable_preference,
                    systemDictionaryEnabled =
                        appPreference.system_dictionary_prediction_enable_preference,
                    minimumInputLength =
                        appPreference.prediction_minimum_input_length_preference,
                    systemCandidateLimit =
                        appPreference.system_prediction_candidate_limit_preference,
                    lookaheadCharacterCount =
                        appPreference.prediction_lookahead_character_count_preference,
                    aggressiveness = PredictionAggressiveness.fromPreference(
                        appPreference.prediction_aggressiveness_preference,
                    ),
                    systemUserDictionaryEnabled =
                        appPreference.system_user_dictionary_prediction_enable_preference,
                    readingCorrectionEnabled =
                        appPreference.reading_correction_prediction_enable_preference,
                    externalMozcEnabled =
                        appPreference.external_mozc_prediction_enable_preference,
                    symbolEmojiEnabled =
                        appPreference.symbol_emoji_prediction_enable_preference,
                    showSymbolCandidates =
                        appPreference.symbol_candidate_enable_preference,
                    showEmojiCandidates =
                        appPreference.emoji_candidate_enable_preference,
                    showEmoticonCandidates =
                        appPreference.emoticon_candidate_enable_preference,
                ),
                flickSensitivityPreferenceValue = appPreference.flick_sensitivity_preference ?: 100,
                flickThresholdShapePreferenceValue =
                    appPreference.flick_threshold_shape_preference,
                tfbiDiagonalRecognitionModePreferenceValue =
                    appPreference.tfbi_diagonal_recognition_mode_preference.preferenceValue,
                longPressTimeoutPreferenceValue =
                    appPreference.long_press_timeout_preference ?: 300,
                tenkeyShowIMEButtonPreference =
                    appPreference.tenkey_show_language_button_preference,
                tenkeySpaceFlickPreference = appPreference.tenkey_space_flick_preference,
                qwertyEnableZenkakuSpacePreference =
                    appPreference.qwerty_enable_zenkaku_space_preference ?: false,
                qwertyRomajiHankakuNumberPreference =
                    appPreference.qwerty_romaji_hankaku_number_preference ?: false,
                qwertyRomajiHankakuSymbolPreference =
                    appPreference.qwerty_romaji_hankaku_symbol_preference ?: false,
                showCandidateInPasswordPreference = appPreference.show_candidates_password ?: true,
                isNgWordEnable = appPreference.ng_word_preference ?: true,
                deleteKeyHighLight = appPreference.delete_key_high_light_preference ?: true,
                customKeyboardSuggestionPreference =
                    appPreference.custom_keyboard_suggestion_preference ?: true,
                userDictionaryPrefixMatchNumber =
                    appPreference.user_dictionary_prefix_match_number_preference ?: 2,
                candidateColumns = appPreference.candidate_column_preference,
                candidateColumnsLandscape = appPreference.candidate_column_landscape_preference,
                candidateTabVisibility = appPreference.candidate_tab_preference,
                stabilizeCandidateStripHeightPreference =
                    appPreference.stabilize_candidate_strip_height_preference,
                symbolKeyboardFirstItem = appPreference.symbol_mode_preference,
                defaultEmojiSkinTone = appPreference.default_emoji_skin_tone_preference,
                independentMultiTouchEnabled = appPreference.independent_multi_touch_preference,
                tenkeyUseThreeStateKeyboard =
                    appPreference.tenkey_use_three_state_keyboard_preference,
                tenkeyNumberSymbolKeyGapDp =
                    appPreference.tenkey_number_symbol_key_gap_preference,
                customKeymapGuide = appPreference.flick_keymap_guide_layout ?: false,
                flickGuideTextSizeSp = appPreference.flick_guide_text_size_sp_preference ?: 9,
                flickGuideMaxCharacters =
                    appPreference.flick_guide_max_characters_preference ?: 1,
                isKeyboardFloatingMode = appPreference.is_floating_mode ?: false,
                isKeyboardRounded = appPreference.keyboard_corner_round_preference,
                keyboardCornerRadiusDp =
                    appPreference.keyboard_corner_radius_dp_preference.coerceIn(0, 64),
                keyboardCornerTopLeft = appPreference.keyboard_corner_top_left_preference,
                keyboardCornerTopRight = appPreference.keyboard_corner_top_right_preference,
                keyboardCornerBottomLeft = appPreference.keyboard_corner_bottom_left_preference,
                keyboardCornerBottomRight = appPreference.keyboard_corner_bottom_right_preference,
                reconversionEnabled = appPreference.reconversion_preference,
                conversionKeySwipePreference =
                    appPreference.conversion_key_swipe_cursor_move_preference,
                physicalKeyboardInputMode = appPreference.physical_keyboard_input_mode_preference,
                shortcutTollbarVisibility =
                    appPreference.shortcut_toolbar_visibility_preference,
                shortcutToolbarIntegratedInSuggestion =
                    appPreference.shortcut_toolbar_integrated_in_suggestion_preference,
                shortcutToolbarHeightDp =
                    appPreference.shortcut_toolbar_height_dp_preference,
                shortcutToolbarIconSizeDp =
                    appPreference.shortcut_toolbar_icon_size_dp_preference,
                isDeleteLeftFlickPreference = appPreference.delete_key_left_flick_preference,
                isDeleteUpFlickPreference = appPreference.delete_key_up_flick_preference,
                isDeleteDownFlickPreference = appPreference.delete_key_down_flick_preference,
                clipboardPreviewVisibility = appPreference.clipboard_preview_preference,
                clipboardPreviewTapToDelete = appPreference.clipboard_preview_tap_delete_preference,
                tenkeyHeightPreferenceValue = appPreference.keyboard_height ?: 280,
                tenkeyWidthPreferenceValue = appPreference.keyboard_width ?: 100,
                candidateViewHeightPreferenceValue = appPreference.candidate_view_height_dp ?: 60,
                candidateViewHeightEmptyPreferenceValue =
                    appPreference.candidate_view_empty_height_dp ?: 60,
                tenkeyPositionPreferenceValue = appPreference.keyboard_position ?: true,
                tenkeyBottomMarginPreferenceValue =
                    appPreference.keyboard_vertical_margin_bottom ?: 0,
                tenkeyStartMarginPreferenceValue =
                    appPreference.keyboard_margin_start_dp ?: 0,
                tenkeyEndMarginPreferenceValue = appPreference.keyboard_margin_end_dp ?: 0,
                tenkeyLandscapeStartMarginPreferenceValue =
                    appPreference.keyboard_margin_start_dp_landscape ?: 0,
                tenkeyLandscapeEndMarginPreferenceValue =
                    appPreference.keyboard_margin_end_dp_landscape ?: 0,
                enableShowLastShownKeyboardInRestart =
                    appPreference.save_last_used_keyboard_enable_preference,
                lastSavedKeyboardPosition =
                    appPreference.save_last_used_keyboard_position_preference,
                tenkeyHeightLandscapePreferenceValue =
                    appPreference.keyboard_height_landscape ?: 280,
                tenkeyWidthLandscapePreferenceValue =
                    appPreference.keyboard_width_landscape ?: 100,
                candidateViewLandscapeHeightPreferenceValue =
                    appPreference.candidate_view_height_dp_landscape ?: 60,
                candidateViewLandscapeHeightEmptyPreferenceValue =
                    appPreference.candidate_view_empty_height_dp_landscape ?: 60,
                tenkeyLandscapePositionPreferenceValue =
                    appPreference.keyboard_position_landscape ?: true,
                tenkeyLandscapeBottomMarginPreferenceValue =
                    appPreference.keyboard_vertical_margin_bottom_landscape ?: 0,
                keyboardSkin = appPreference.keyboardSkin,
                appearance = ImeKeyboardAppearance(
                    keyboardThemeMode = appPreference.theme_mode,
                    customThemeBgColor = appPreference.custom_theme_bg_color,
                    customThemeKeyColor = appPreference.custom_theme_key_color,
                    customThemeSpecialKeyColor = appPreference.custom_theme_special_key_color,
                    customThemeKeyTextColor = appPreference.custom_theme_key_text_color,
                    customThemeSpecialKeyTextColor = appPreference.custom_theme_special_key_text_color,
                    customThemeCandidateTextColor = appPreference.getCustomThemeCandidateTextColor(
                        appPreference.custom_theme_key_text_color
                    ),
                    customThemeCandidateItemBgColor = appPreference.getCustomThemeCandidateItemBgColor(
                        AppPreference.DEFAULT_CUSTOM_THEME_CANDIDATE_ITEM_BG_COLOR
                    ),
                    customThemeCandidateItemPressedBgColor =
                        appPreference.getCustomThemeCandidateItemPressedBgColor(
                            customThemeCandidateItemPressedBgColorDefault
                        ),
                    customThemeCandidateEmptyPopupBgColor =
                        appPreference.getCustomThemeCandidateEmptyPopupBgColor(
                            appPreference.custom_theme_special_key_color
                        ),
                    customThemeCandidateEmptyPopupTextColor =
                        appPreference.getCustomThemeCandidateEmptyPopupTextColor(
                            appPreference.custom_theme_special_key_text_color
                        ),
                    customThemeShortcutIconColor = appPreference.getCustomThemeShortcutIconColor(
                        appPreference.custom_theme_special_key_text_color
                    ),
                    liquidGlassThemePreference = appPreference.liquid_glass_preference,
                    liquidGlassBlurRadiousPreference = appPreference.liquid_glass_blur_radius,
                    liquidGlassKeyBlurRadiousPreference = appPreference.liquid_glass_key_alpha,
                    customKeyBorderEnablePreference = appPreference.custom_theme_border_enable,
                    customKeyBorderEnableColor = appPreference.custom_theme_border_color,
                    customComposingTextPreference = appPreference.custom_theme_input_color_enable,
                    inputCompositionBackgroundColor = appPreference.custom_theme_pre_edit_bg_color,
                    inputCompositionTextColor = appPreference.custom_theme_pre_edit_text_color,
                    inputConversionBackgroundColor = appPreference.custom_theme_post_edit_bg_color,
                    inputConversionTextColor = appPreference.custom_theme_post_edit_text_color,
                ),
                conversionCandidatesRomajiEnablePreference =
                    appPreference.conversion_candidates_romaji_enable_preference,
                learnFirstCandidateDictionaryPreference =
                    appPreference.learn_first_candidate_dictionary_preference,
                learnDictionaryAllowMixedSymbolsNumbersPreference =
                    appPreference.learn_dictionary_allow_mixed_symbols_numbers_preference,
                enablePredictionSearchLearnDictionaryPreference =
                    appPreference.enable_prediction_search_learn_dictionary_preference,
                learnPredictionPreference = appPreference.learn_prediction_preference,
                userDictionaryPredictionCandidateLimit =
                    appPreference.user_dictionary_prediction_candidate_limit_preference,
                learnDictionaryPredictionCandidateLimit =
                    appPreference.learn_dictionary_prediction_candidate_limit_preference,
                customKeyBorderWidth = appPreference.custom_theme_border_width,
                customRomajiZenkakuConversionEnablePreference =
                    appPreference.custom_romaji_zenkaku_conversion_enable_preference,
                omissionSearchOffsetScorePreference =
                    appPreference.omission_search_offset_score_preference,
                enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference =
                    appPreference.enable_typo_correction_japanese_flick_keyboard_offset_score_preference,
                enableTypoCorrectionJapaneseFlickKeyboardPreference =
                    appPreference.enable_typo_correction_japanese_flick_keyboard_preference,
            )
        }
    }
}

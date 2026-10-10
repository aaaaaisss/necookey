package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.candidate_view_height_setting

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.material.color.DynamicColors
import com.kazumaproject.core.data.popup.FlickPopupViewStyleSet
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.data.popup.QwertyPopupViewStyleSet
import com.kazumaproject.core.domain.flick.FlickThresholdShape
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.listener.LongPressListener
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.custom_keyboard.view.KeyHitTestMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.resolveInitialCustomKeyboardSelection
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.circular_slot.CircularSlotActionApplier
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionDisplayMetadata
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionDisplayOverrideApplier
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionResolver
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyPlacementOverrideApplier
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import timber.log.Timber
import kotlin.math.roundToInt

internal fun createCandidateHeightPreviewCandidates(): List<Candidate> {
    return listOf(
        "学校に行く" to "がっこうにいく",
        "変換候補" to "へんかんこうほ",
        "変換する" to "へんかんする",
        "日本語" to "にほんご",
        "入力" to "にゅうりょく",
        "候補欄" to "こうほらん",
        "設定" to "せってい",
        "予測" to "よそく",
        "学習なし" to "がくしゅうなし",
        "オフライン" to "おふらいん",
        "キーボード" to "きーぼーど",
        "Markdown" to "まーくだうん"
    ).mapIndexed { index, (text, yomi) ->
        Candidate(
            string = text,
            type = if (index == 0) 9.toByte() else 1.toByte(),
            length = text.length.toUByte(),
            score = 4000 - index,
            yomi = yomi,
            leftId = 0.toShort(),
            rightId = 0.toShort(),
            conversionSegments = listOf(CandidateConversionSegment(0, yomi.length, text))
        )
    }
}

internal fun clearItemDecorations(recyclerView: RecyclerView) {
    while (recyclerView.itemDecorationCount > 0) {
        recyclerView.removeItemDecorationAt(0)
    }
}

internal class CandidateHeightPreviewGridSpacingDecoration(
    private val spanCount: Int,
    private val spacing: Int,
    private val includeEdge: Boolean
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position < 0) {
            outRect.set(0, 0, 0, 0)
            return
        }

        val layoutManager = parent.layoutManager as? GridLayoutManager ?: return
        val layoutParams = view.layoutParams as GridLayoutManager.LayoutParams

        if (layoutManager.orientation == GridLayoutManager.HORIZONTAL) {
            val row = layoutParams.spanIndex
            val column = position / spanCount
            if (includeEdge) {
                outRect.top = spacing - row * spacing / spanCount
                outRect.bottom = (row + 1) * spacing / spanCount
                if (column == 0) {
                    outRect.left = spacing
                }
                outRect.right = spacing
            } else {
                outRect.top = row * spacing / spanCount
                outRect.bottom = spacing - (row + 1) * spacing / spanCount
                if (column > 0) {
                    outRect.left = spacing
                }
            }
        }
    }
}

internal data class CandidateKeyboardPreviewViews(
    val container: FrameLayout,
    val flick: FlickKeyboardView
)

internal fun renderCandidateKeyboardPreview(
    fragment: Fragment,
    appPreference: AppPreference,
    keyboardRepository: KeyboardRepository,
    sumireSpecialKeyRepository: SumireSpecialKeyRepository,
    views: CandidateKeyboardPreviewViews,
    isLandscape: Boolean,
    onPreviewLayoutChanged: () -> Unit
) {
    val previewKeyboardType = KeyboardType.CUSTOM
    applyCandidateKeyboardPreviewLayout(
        fragment = fragment,
        appPreference = appPreference,
        container = views.container,
        type = previewKeyboardType,
        isLandscape = isLandscape
    )
    onPreviewLayoutChanged()

    views.flick.isVisible = false

    when (previewKeyboardType) {
        KeyboardType.CUSTOM -> {
            views.flick.isVisible = true
            configureFlickKeyboardPreview(
                context = fragment.requireContext(),
                appPreference = appPreference,
                flickView = views.flick,
                guideEnabled = appPreference.flick_keymap_guide_layout ?: false,
                allowMultiCharacterGuideLabels = false
            )
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val customLayout = withContext(Dispatchers.IO) {
                    loadPreviewCustomKeyboardLayout(appPreference, keyboardRepository)
                }
                views.flick.clearSumireSpecialKeyActionResolver()
                if (customLayout == null) {
                    // No custom layout stored yet: preview the default layout that the IME inserts.
                    views.flick.setKeyboard(
                        applyDeleteKeyFlickPreferences(
                            appPreference,
                            KeyboardDefaultLayouts.createFlickKanaTemplateLayout(isDefaultKey = true)
                        )
                    )
                    return@launch
                }
                val finalLayout = applyDeleteKeyFlickPreferences(appPreference, customLayout.layout)
                views.flick.setKeyboard(finalLayout)
                syncCustomKeyboardToggleKeyIcons(
                    flickView = views.flick,
                    isDirectMode = customLayout.isDirectMode,
                    isRomaji = customLayout.isRomaji,
                    isShiftPressed = false,
                    isCapLock = false
                )
            }
        }

    }
}

internal fun candidateKeyboardPreviewHeightPx(container: FrameLayout): Int {
    val layoutParams = container.layoutParams
    return layoutParams.height.takeIf { it > 0 } ?: container.context.dpToPx(220)
}

private data class PreviewKeyboardLayoutConfig(
    val heightDp: Int,
    val widthPercent: Int,
    val bottomMarginDp: Int,
    val positionIsEnd: Boolean,
    val startMarginDp: Int,
    val endMarginDp: Int
)

private data class PreviewCustomKeyboardLayout(
    val layout: KeyboardLayout,
    val isRomaji: Boolean,
    val isDirectMode: Boolean
)

private fun applyCandidateKeyboardPreviewLayout(
    fragment: Fragment,
    appPreference: AppPreference,
    container: FrameLayout,
    type: KeyboardType,
    isLandscape: Boolean
) {
    val config = previewKeyboardLayoutConfig(
        appPreference = appPreference,
        type = type,
        isLandscape = isLandscape
    )
    val screenWidth = WindowMetricsCalculator.getOrCreate()
        .computeCurrentWindowMetrics(fragment.requireActivity()).bounds.width()
    val widthPercent = config.widthPercent.coerceIn(32, 100)
    val widthPx = if (widthPercent >= 98) {
        ViewGroup.LayoutParams.MATCH_PARENT
    } else {
        (screenWidth * (widthPercent / 100f)).roundToInt()
    }
    val heightPx = config.heightDp.coerceIn(100, 420).let(container.context::dpToPx)
    val horizontalGravity = if (config.positionIsEnd) Gravity.END else Gravity.START
    val startMarginPx = if (config.positionIsEnd) 0 else container.context.dpToPx(config.startMarginDp)
    val endMarginPx = if (config.positionIsEnd) container.context.dpToPx(config.endMarginDp) else 0
    val bottomMarginPx = container.context.dpToPx(config.bottomMarginDp)
    val layoutParams = when (val params = container.layoutParams) {
        is FrameLayout.LayoutParams -> params.apply {
            height = heightPx
            width = widthPx
            gravity = Gravity.BOTTOM or horizontalGravity
            marginStart = startMarginPx
            marginEnd = endMarginPx
            bottomMargin = bottomMarginPx
        }

        is LinearLayout.LayoutParams -> params.apply {
            height = heightPx
            width = widthPx
            gravity = horizontalGravity
            marginStart = startMarginPx
            marginEnd = endMarginPx
            bottomMargin = bottomMarginPx
        }

        is ViewGroup.MarginLayoutParams -> FrameLayout.LayoutParams(params).apply {
            height = heightPx
            width = widthPx
            gravity = Gravity.BOTTOM or horizontalGravity
            marginStart = startMarginPx
            marginEnd = endMarginPx
            bottomMargin = bottomMarginPx
        }

        else -> FrameLayout.LayoutParams(widthPx, heightPx, Gravity.BOTTOM or horizontalGravity).apply {
            marginStart = startMarginPx
            marginEnd = endMarginPx
            bottomMargin = bottomMarginPx
        }
    }
    container.layoutParams = layoutParams
}

private fun previewKeyboardLayoutConfig(
    appPreference: AppPreference,
    type: KeyboardType,
    isLandscape: Boolean
): PreviewKeyboardLayoutConfig {
    return if (false) {
        PreviewKeyboardLayoutConfig(
            heightDp = if (isLandscape) {
                appPreference.qwerty_keyboard_height_landscape ?: 220
            } else {
                appPreference.qwerty_keyboard_height ?: 220
            },
            widthPercent = if (isLandscape) {
                appPreference.qwerty_keyboard_width_landscape ?: 100
            } else {
                appPreference.qwerty_keyboard_width ?: 100
            },
            bottomMarginDp = if (isLandscape) {
                appPreference.qwerty_keyboard_vertical_margin_bottom_landscape ?: 0
            } else {
                appPreference.qwerty_keyboard_vertical_margin_bottom ?: 0
            },
            positionIsEnd = if (isLandscape) {
                appPreference.qwerty_keyboard_position_landscape ?: true
            } else {
                appPreference.qwerty_keyboard_position ?: true
            },
            startMarginDp = if (isLandscape) {
                appPreference.qwerty_keyboard_margin_start_dp_landscape ?: 0
            } else {
                appPreference.qwerty_keyboard_margin_start_dp ?: 0
            },
            endMarginDp = if (isLandscape) {
                appPreference.qwerty_keyboard_margin_end_dp_landscape ?: 0
            } else {
                appPreference.qwerty_keyboard_margin_end_dp ?: 0
            }
        )
    } else {
        PreviewKeyboardLayoutConfig(
            heightDp = if (isLandscape) {
                appPreference.keyboard_height_landscape ?: 220
            } else {
                appPreference.keyboard_height ?: 220
            },
            widthPercent = if (isLandscape) {
                appPreference.keyboard_width_landscape ?: 100
            } else {
                appPreference.keyboard_width ?: 100
            },
            bottomMarginDp = if (isLandscape) {
                appPreference.keyboard_vertical_margin_bottom_landscape ?: 0
            } else {
                appPreference.keyboard_vertical_margin_bottom ?: 0
            },
            positionIsEnd = if (isLandscape) {
                appPreference.keyboard_position_landscape ?: true
            } else {
                appPreference.keyboard_position ?: true
            },
            startMarginDp = if (isLandscape) {
                appPreference.keyboard_margin_start_dp_landscape ?: 0
            } else {
                appPreference.keyboard_margin_start_dp ?: 0
            },
            endMarginDp = if (isLandscape) {
                appPreference.keyboard_margin_end_dp_landscape ?: 0
            } else {
                appPreference.keyboard_margin_end_dp ?: 0
            }
        )
    }
}

private fun configureFlickKeyboardPreview(
    context: Context,
    appPreference: AppPreference,
    flickView: FlickKeyboardView,
    guideEnabled: Boolean,
    allowMultiCharacterGuideLabels: Boolean
) {
    flickView.setPopupWindowAnchorProvider(null)
    flickView.applyKeyboardTheme(
        themeMode = appPreference.theme_mode,
        currentNightMode = currentNightMode(context),
        isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
        customBgColor = appPreference.custom_theme_bg_color,
        customKeyColor = appPreference.custom_theme_key_color,
        customSpecialKeyColor = appPreference.custom_theme_special_key_color,
        customKeyTextColor = appPreference.custom_theme_key_text_color,
        customSpecialKeyTextColor = appPreference.custom_theme_special_key_text_color,
        liquidGlassEnable = appPreference.liquid_glass_preference,
        customBorderEnable = appPreference.custom_theme_border_enable,
        customBorderColor = appPreference.custom_theme_border_color,
        liquidGlassKeyAlphaEnable = appPreference.liquid_glass_key_alpha,
        borderWidth = appPreference.custom_theme_border_width
    )
    flickView.setAngleAndRange(
        appPreference.getCircularFlickRanges(),
        appPreference.circular_flickWindow_scale
    )
    flickView.setCircularFlickOptions(directionCount = appPreference.circularFlickDirectionCount)
    flickView.setHierarchicalFlickModeSwitchAngleMargin(
        appPreference.hierarchical_flick_mode_switch_angle_margin_preference.toDouble()
    )
    flickView.applyKeySizing(
        keyWidthScalePercent = appPreference.flick_key_width_scale_percent ?: 160,
        keyHeightScalePercent = appPreference.flick_key_height_scale_percent ?: 160,
        iconScalePercent = appPreference.flick_key_icon_scale_percent ?: 80,
        textSizeSp = appPreference.flick_key_text_size_sp ?: 16.0f,
        specialKeyTextSizeSp = appPreference.flick_special_key_text_size_sp ?: 16.0f
    )
    flickView.applyPopupViewStyleSet(
        FlickPopupViewStyleSet(
            directional = PopupViewStyle(
                sizeScalePercent = appPreference.flick_directional_popup_size_scale_percent ?: 100,
                textSizeSp = appPreference.flick_directional_popup_text_size_sp ?: 28.0f
            ),
            cross = PopupViewStyle(
                sizeScalePercent = appPreference.flick_cross_popup_size_scale_percent ?: 100,
                textSizeSp = appPreference.flick_cross_popup_text_size_sp ?: 18.0f
            ),
            standard = PopupViewStyle(
                sizeScalePercent = appPreference.flick_standard_popup_size_scale_percent ?: 100,
                textSizeSp = appPreference.flick_standard_popup_text_size_sp ?: 19.0f
            ),
            tfbi = PopupViewStyle(
                sizeScalePercent = appPreference.flick_tfbi_popup_size_scale_percent ?: 100,
                textSizeSp = appPreference.flick_tfbi_popup_text_size_sp ?: 20.0f
            )
        )
    )
    flickView.setFlickSensitivityValue(appPreference.flick_sensitivity_preference ?: 100)
    flickView.setFlickThresholdShape(
        FlickThresholdShape.fromPreferenceValue(appPreference.flick_threshold_shape_preference)
    )
    flickView.setLongPressTimeout((appPreference.long_press_timeout_preference ?: 300).toLong())
    flickView.setFlickGuideEnabled(
        enabled = guideEnabled,
        allowMultiCharacterLabels = allowMultiCharacterGuideLabels
    )
    flickView.setFlickGuideTextSizeSp(
        (appPreference.flick_guide_text_size_sp_preference ?: 9).coerceIn(6, 16).toFloat()
    )
    flickView.setFlickGuideMaxCodePoints(
        (appPreference.flick_guide_max_characters_preference ?: 1).coerceIn(1, 4)
    )
    flickView.setOnKeyboardActionListener(object : FlickKeyboardView.OnKeyboardActionListener {
        override fun onPress(action: KeyAction) = Unit
        override fun onAction(action: KeyAction, isFlick: Boolean) = Unit
        override fun onActionLongPress(action: KeyAction) = Unit
        override fun onActionUpAfterLongPress(action: KeyAction) = Unit
        override fun onFlickDirectionChanged(direction: FlickDirection) = Unit
        override fun onFlickActionLongPress(action: KeyAction) = Unit
        override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) = Unit
        override fun onLongPressActionCanceled(action: KeyAction) = Unit
    })
    flickView.isClickable = false
    flickView.isFocusable = false
}

private suspend fun loadPreviewCustomKeyboardLayout(
    appPreference: AppPreference,
    keyboardRepository: KeyboardRepository
): PreviewCustomKeyboardLayout? {
    val layouts = keyboardRepository.getLayoutsNotFlowEnsuringStableIds()
    val selection = resolveInitialCustomKeyboardSelection(
        layouts = layouts,
        rememberLast = appPreference.remember_last_custom_keyboard_preference ?: false,
        savedStableId = appPreference.last_used_custom_keyboard_stable_id
    ) ?: return null
    val layoutMeta = layouts.getOrNull(selection.index) ?: return null
    val dbLayout = runCatching { keyboardRepository.getFullLayout(layoutMeta.layoutId).first() }
        .getOrElse {
            Timber.w(
                it,
                "loadPreviewCustomKeyboardLayout: layout disappeared id=%d stableId=%s",
                layoutMeta.layoutId,
                layoutMeta.stableId
            )
            return null
        }
    val convertedLayout = keyboardRepository.convertLayout(dbLayout)
    val persistenceKey = layoutMeta.stableId.takeIf { it.isNotBlank() }
        ?: layoutMeta.layoutId.toString()
    return PreviewCustomKeyboardLayout(
        layout = convertedLayout,
        isRomaji = if (appPreference.remember_custom_keyboard_input_mode_preference == true) {
            appPreference.getCustomKeyboardLastRomajiMode(persistenceKey) ?: convertedLayout.isRomaji
        } else {
            convertedLayout.isRomaji
        },
        isDirectMode = if (appPreference.remember_custom_keyboard_input_mode_preference == true) {
            appPreference.getCustomKeyboardLastDirectMode(persistenceKey) ?: convertedLayout.isDirectMode
        } else {
            convertedLayout.isDirectMode
        }
    )
}

private fun applyDeleteKeyFlickPreferences(
    appPreference: AppPreference,
    layout: KeyboardLayout
): KeyboardLayout {
    return KeyboardDefaultLayouts.applyDeleteKeyFlickSettings(
        layout = layout,
        deleteKeyFlickSettings = currentDeleteKeyFlickSettings(appPreference)
    )
}

private fun currentDeleteKeyFlickSettings(
    appPreference: AppPreference
): KeyboardDefaultLayouts.DeleteKeyFlickSettings {
    return KeyboardDefaultLayouts.DeleteKeyFlickSettings(
        left = appPreference.delete_key_left_flick_preference,
        up = appPreference.delete_key_up_flick_preference,
        down = appPreference.delete_key_down_flick_preference
    )
}

private fun syncCustomKeyboardToggleKeyIcons(
    flickView: FlickKeyboardView,
    isDirectMode: Boolean,
    isRomaji: Boolean,
    isShiftPressed: Boolean,
    isCapLock: Boolean
) {
    flickView.updateKeyIconByAction(
        KeyAction.SwitchDirectMode,
        if (isDirectMode) {
            com.kazumaproject.core.R.drawable.language_japanese_kana_right_24px
        } else {
            com.kazumaproject.core.R.drawable.language_japanese_kana_left_24px
        }
    )
    flickView.updateKeyIconByAction(
        KeyAction.SwitchRomajiEnglish,
        if (isRomaji) {
            com.kazumaproject.core.R.drawable.language_japanese_kana_left_bold_24px
        } else {
            com.kazumaproject.core.R.drawable.language_japanese_kana_right_bold_24px
        }
    )
    flickView.updateKeyIconByAction(
        KeyAction.ShiftKey,
        if (isShiftPressed) {
            com.kazumaproject.core.R.drawable.shift_fill_24px
        } else {
            com.kazumaproject.core.R.drawable.shift_24px
        }
    )
    flickView.updateKeyIconByAction(
        KeyAction.CapLockKey,
        if (isCapLock) {
            com.kazumaproject.core.R.drawable.caps_lock
        } else {
            com.kazumaproject.core.R.drawable.caps_lock_outline
        }
    )
}

private fun sumireSpecialKeyActionDisplayMetadata(
    context: Context
): List<SumireSpecialKeyActionDisplayMetadata> {
    return KeyActionMapper.getDisplayActions(context).map {
        SumireSpecialKeyActionDisplayMetadata(
            action = it.action,
            displayName = it.displayName,
            iconResId = it.iconResId
        )
    }
}

private fun currentNightMode(context: Context): Int =
    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

private fun Context.dpToPx(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()

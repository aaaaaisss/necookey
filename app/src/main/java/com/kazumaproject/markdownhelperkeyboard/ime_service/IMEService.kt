package com.kazumaproject.markdownhelperkeyboard.ime_service

import android.annotation.SuppressLint
import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.icu.text.BreakIterator as AndroidBreakIterator
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.ResultReceiver
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.inline.InlineContentView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.android.flexbox.FlexDirection
import com.kazumaproject.android.flexbox.FlexboxLayoutManager
import com.kazumaproject.android.flexbox.JustifyContent
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.core.data.floating_candidate.CandidateItem
import com.kazumaproject.core.data.popup.FlickPopupViewStyleSet
import com.kazumaproject.core.data.popup.PopupViewStyle
import com.kazumaproject.core.data.popup.QwertyPopupViewStyleSet
import com.kazumaproject.core.data.popup.TfbiPopupPresentationMode
import com.kazumaproject.core.domain.extensions.dpToPx
import com.kazumaproject.core.domain.extensions.getThemeColorOrFallback
import com.kazumaproject.core.domain.extensions.hiraganaToKatakana
import com.kazumaproject.core.domain.extensions.isAsciiDigitForRomajiQwerty
import com.kazumaproject.core.domain.extensions.isAsciiSymbolForRomajiQwerty
import com.kazumaproject.core.domain.extensions.kanjiCount
import com.kazumaproject.core.domain.extensions.setDrawableAlpha
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.core.domain.extensions.setLayerTypeSolidColor
import com.kazumaproject.core.domain.extensions.toHankakuAlphabet
import com.kazumaproject.core.domain.extensions.toHankakuKatakana
import com.kazumaproject.core.domain.extensions.toHankakuKigou
import com.kazumaproject.core.domain.extensions.toHiragana
import com.kazumaproject.core.domain.extensions.toRomajiQwertyOutputChar
import com.kazumaproject.core.domain.extensions.toZenkaku
import com.kazumaproject.core.domain.extensions.toZenkakuAlphabet
import com.kazumaproject.core.domain.extensions.toZenkakuKatakana
import com.kazumaproject.core.domain.flick.FlickThresholdShape
import com.kazumaproject.core.domain.flick.FlickTextPreviewListener
import com.kazumaproject.core.domain.flick.MutableRuntimeGestureSettingsSource
import com.kazumaproject.core.domain.flick.RuntimeGestureSettings
import com.kazumaproject.core.domain.flick.TfbiDiagonalRecognitionMode
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.listener.KeyTouchCancelListener
import com.kazumaproject.core.domain.listener.KeyTouchCancelReason
import com.kazumaproject.core.domain.listener.LongPressListener
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.listener.QwertyKeyTouchCancelListener
import com.kazumaproject.core.domain.physical_keyboard.FloatingCandidateComposition
import com.kazumaproject.core.domain.physical_keyboard.PhysicalCandidateCompositionSession
import com.kazumaproject.core.domain.physical_keyboard.PhysicalCandidateCommit
import com.kazumaproject.core.domain.physical_keyboard.FloatingCandidateTailResolver
import com.kazumaproject.core.domain.physical_keyboard.KanaDakutenComposer
import com.kazumaproject.core.domain.physical_keyboard.PhysicalKanaMapper
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.QWERTYMode
import com.kazumaproject.core.domain.state.TenKeyQWERTYMode
import com.kazumaproject.core.domain.state.TwoStateNumberReturnTarget
import com.kazumaproject.core.domain.state.toInputMode
import com.kazumaproject.core.domain.state.toTwoStateNumberReturnTargetOrNull
import com.kazumaproject.core.domain.window.getScreenHeight
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyActionMapper
import com.kazumaproject.custom_keyboard.data.KeyCharacterCase
import com.kazumaproject.custom_keyboard.data.KeyboardInputMode
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutUsageMode
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts
import com.kazumaproject.custom_keyboard.layout.KeyboardDefaultLayouts.DeleteKeyFlickSettings
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.custom_keyboard.view.KeyHitTestMode
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoticon.Emoticon
import com.kazumaproject.data.symbol.Symbol
import com.kazumaproject.domain.EmojiSkinToneSupport
import com.kazumaproject.listeners.ClipboardHistoryToggleListener
import com.kazumaproject.listeners.ClipboardItemAction
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemLongClickListener
import com.kazumaproject.markdownhelperkeyboard.BuildConfig
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ClipboardHistoryItem
import com.kazumaproject.markdownhelperkeyboard.clipboard_history.database.ItemType
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.BunsetsuCandidateResult
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.ZenzaiConstraint
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BunsetsuAnalysis
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BunsetsuAnalyzer
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.BunsetsuRangeEditor
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.NecookeyCandidateBarConfig
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.NextWordExtras
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.NextWordZenzReranker
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.TwoRowCandidateBar
import com.kazumaproject.markdownhelperkeyboard.converter.candidatebar.TwoRowCandidateBarPlanner
import com.kazumaproject.markdownhelperkeyboard.learning.session.BunsetsuLearningSplitter
import com.kazumaproject.markdownhelperkeyboard.learning.session.LearnedBunsetsu
import com.kazumaproject.markdownhelperkeyboard.learning.session.LearningReadingGuard
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_ERA
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_LEARNED_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TIME
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_TEMPLATE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_TEXT_MACRO
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CandidateConversionSegment
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ExactInputCandidatePromotionPolicy
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_GLIDE_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ZenzCandidate
import com.kazumaproject.markdownhelperkeyboard.ime_service.zenz.ZenzDiagnosticsStore
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.buildRomajiCandidates
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.toUserTemplateCandidates
import com.kazumaproject.markdownhelperkeyboard.converter.engine.EnglishEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.converter.engine.PredictionConfig
import com.kazumaproject.markdownhelperkeyboard.converter.ngram.SystemNgramRuntime
import com.kazumaproject.markdownhelperkeyboard.converter.session.CandidateQueryMode
import com.kazumaproject.markdownhelperkeyboard.converter.session.ConversionBackend
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiConversionSession
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryRequest
import com.kazumaproject.markdownhelperkeyboard.converter.session.KanaKanjiQueryResult
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.databinding.MainLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategoryLoadState
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryOverrideStore
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionarySourceResolver
import com.kazumaproject.markdownhelperkeyboard.local_font.LocalFontRepository
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontGlyphDrawable
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.GridSpacingItemDecoration
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.InlineSuggestionStripState
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.ShortcutAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.resolveCandidateEmptyPopupThemeColors
import com.kazumaproject.markdownhelperkeyboard.ime_service.autofill.InlineAutofillController
import com.kazumaproject.markdownhelperkeyboard.ime_service.autofill.InlineSuggestionDisplayState
import com.kazumaproject.markdownhelperkeyboard.ime_service.autofill.InlineSuggestionSurface
import com.kazumaproject.markdownhelperkeyboard.ime_service.autofill.InlineSuggestionsRequestFactory
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContent
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripContentResolver
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateStripInputState
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.InlineSuggestionToggle
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateQueryModeResolver
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateRefreshCoordinator
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateRefreshRequest
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateRefreshTransitionPolicy
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateRequestToken
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateRequestTracker
import com.kazumaproject.markdownhelperkeyboard.ime_service.clipboard.ClipboardUtil
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.containsHentaigana
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.correctReading
import com.kazumaproject.markdownhelperkeyboard.ime_service.editor.EditorEnterAction
import com.kazumaproject.markdownhelperkeyboard.ime_service.editor.EditorEnterPolicy
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getCurrentInputTypeForIME2
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getEnterKeyIndexSumire
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getLastCharacterAsString
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getQWERTYReturnTextInEn
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.getQWERTYReturnTextInJp
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllEnglishLetters
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHiraganaWithSymbols
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isPassword
import com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview.ComposingTextArbiter
import com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview.FlickInputPreviewCoordinator
import com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview.FlickPreviewContext
import com.kazumaproject.markdownhelperkeyboard.ime_service.flick_preview.FlickPreviewSource
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.CinematicWaveEffectView
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.CinematicWaveSettings
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.FluidInkTransportMode
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.InkTouchDispatchFrameLayout
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.KeyboardTouchEffectQuality
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.KeyboardTouchEffectType
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.LiquidRippleEffectView
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.LuminousBlobEffectView
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.LuminousBlobSettings
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.SprayPaintEffectView
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.SprayPaintSettings
import com.kazumaproject.markdownhelperkeyboard.ime_service.image_effect.SuminagashiInkView
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.DirectCommitHandler
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.DirectCommitTransition
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.InputBehaviorResolver
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.KeyInputBehaviorDispatcher
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.QwertyEnglishDirectInputPolicy
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.ResolvedInputBehavior
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.RuntimeInputBehaviorPolicy
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.RuntimeInputBehaviorSafetyState
import com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior.TypeNullInputBehaviorSetting
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditConstraints
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditController
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditOrientation
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditOverlayView
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditState
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditSurface
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditTarget
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.KeyboardLayoutEditValues
import com.kazumaproject.markdownhelperkeyboard.ime_service.keyboard_layout_edit.NormalKeyboardLayoutEditSurface
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateEvaluationResult
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.CandidateShowFlag
import com.kazumaproject.markdownhelperkeyboard.ime_service.models.SymbolKeyboardState
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.CustomRomajiScreenConverter
import com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.RomajiKanaConverter
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.CandidateTab
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.InputTypeForIME
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.learning.database.LearnEntity
import com.kazumaproject.markdownhelperkeyboard.learning.nextword.NextWordPolicy
import com.kazumaproject.markdownhelperkeyboard.learning.nextword.NextWordRepository
import com.kazumaproject.markdownhelperkeyboard.learning.session.ConversionLearningSession
import com.kazumaproject.markdownhelperkeyboard.learning.session.LearningFragment
import com.kazumaproject.markdownhelperkeyboard.ng_word.NgWordMatcher
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWord
import com.kazumaproject.markdownhelperkeyboard.ng_word.database.NgWordMatchMode
import com.kazumaproject.markdownhelperkeyboard.repository.ClickedSymbolRepository
import com.kazumaproject.markdownhelperkeyboard.repository.ClipboardHistoryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.DeleteKeyFlickDeleteTargetRepository
import com.kazumaproject.markdownhelperkeyboard.repository.KeyboardRepository
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.NgWordRepository
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import com.kazumaproject.markdownhelperkeyboard.repository.ShortcutRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserTemplateRepository
import com.kazumaproject.markdownhelperkeyboard.repository.TextMacroRepository
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroCompiler
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroContext
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroContextRequirement
import com.kazumaproject.markdownhelperkeyboard.text_macro.ExpandedMacro
import com.kazumaproject.markdownhelperkeyboard.text_macro.TextMacroInputConnectionExecutor
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection.getKeyboardDisplayName
import com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity
import com.kazumaproject.markdownhelperkeyboard.setting_activity.circular_slot.CircularSlotActionApplier
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionDisplayMetadata
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionDisplayOverrideApplier
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyActionResolver
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyPlacementOverrideApplier
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.SumireSpecialKeyRepository
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyActionOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.sumire_special_key.database.SumireSpecialKeyPlacementOverrideEntity
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import com.kazumaproject.markdownhelperkeyboard.zeroquery.AndroidZeroQueryAssetReader
import com.kazumaproject.markdownhelperkeyboard.zeroquery.LazyZeroQueryProvider
import com.kazumaproject.markdownhelperkeyboard.zeroquery.ZeroQueryLookupUseCase
import com.kazumaproject.markdownhelperkeyboard.zeroquery.ZeroQueryProvider
import com.kazumaproject.markdownhelperkeyboard.zenz.runtime.ZenzRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.zenz.runtime.ZenzRuntimeConfig
import com.kazumaproject.symbol_keyboard.CustomSymbolKeyboardView
import com.kazumaproject.core.domain.extensions.getDakutenFlickLeft
import com.kazumaproject.core.domain.extensions.getDakutenFlickRight
import com.kazumaproject.core.domain.extensions.getDakutenFlickTop
import com.kazumaproject.core.domain.extensions.getDakutenSmallChar
import com.kazumaproject.core.domain.extensions.getNextInputChar
import com.kazumaproject.core.domain.extensions.getNextReturnInputChar
import com.kazumaproject.core.domain.extensions.isHiragana
import com.kazumaproject.core.domain.extensions.isLatinAlphabet
import com.kazumaproject.core.domain.extensions.toggleDakutenWithSeion
import com.kazumaproject.core.domain.extensions.toggleHandakutenWithSeion
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.BreakIterator
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Calendar
import kotlin.math.roundToInt
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executors
import java.util.function.Consumer
import javax.inject.Inject
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

@AndroidEntryPoint
class IMEService : InputMethodService(), LifecycleOwner, InputConnection,
    ClipboardHistoryToggleListener {

    private sealed class CandidateLongPressAction {
        object ForgetLearnedEntry : CandidateLongPressAction()
        object HideWord : CandidateLongPressAction()

        object Close : CandidateLongPressAction()
    }

    private enum class SuggestionProgressReason {
        VoiceInput,
        QwertyGlideDecode
    }

    private data class BunsetsuConversionSession(
        val rawInput: String,
        val conversionInput: String,
        val segments: List<BunsetsuSegmentState>,
        val generation: Long,
        val conversionSnapshot: BunsetsuConversionSnapshot?,
        val tailText: String = "",
        val focusedIndex: Int = 0,
        val splitPatterns: List<List<Int>> = emptyList(),
        val activeSplitPatternIndex: Int = 0
    )

    private sealed class BunsetsuDisplayedSelection {
        data class SegmentCandidate(
            val segmentIndex: Int,
            val candidate: Candidate
        ) : BunsetsuDisplayedSelection()
    }

    private data class ReconversionEntry(
        val committedText: String,
        val reading: String
    )

    private data class BunsetsuReconversionDraft(
        val originalReading: String,
        val committedText: String = ""
    )

    private data class TextMacroEditorSnapshot(
        val connection: InputConnection,
        val packageName: String,
        val input: String,
        val selectionStart: Int,
        val selectionEnd: Int,
        val selectedText: String,
    )

    private data class CustomToggleEditorSelection(
        val connection: InputConnection,
        val selectionStart: Int,
        val selectionEnd: Int,
    )

    private data class ZenzContext(
        val leftContext: String,
        val rightContext: String
    )

    private data class SuggestionLayoutKey(
        val isPortrait: Boolean,
        val columnNum: String,
        val layoutKind: SuggestionLayoutKind,
    )

    private enum class SuggestionLayoutKind {
        LinearHorizontal,
        GridHorizontal
    }

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var localFontRepository: LocalFontRepository

    @Inject
    lateinit var inputMethodManager: InputMethodManager

    @Inject
    lateinit var kanaKanjiEngineProvider: Lazy<KanaKanjiEngine>

    private lateinit var kanaKanjiEngine: KanaKanjiEngine
    private val kanaKanjiEngineReady = CompletableDeferred<KanaKanjiEngine>()


    @Inject
    lateinit var dictionarySourceResolver: DictionarySourceResolver

    @Inject
    lateinit var dictionaryOverrideStore: DictionaryOverrideStore

    @Inject
    lateinit var dictionaryBinaryReader: DictionaryBinaryReader

    @Inject
    lateinit var englishEngine: EnglishEngine

    @Inject
    lateinit var learnRepository: LearnRepository

    @Inject
    lateinit var nextWordRepository: NextWordRepository

    @Inject
    lateinit var userDictionaryRepository: UserDictionaryRepository

    @Inject
    lateinit var userTemplateRepository: UserTemplateRepository

    @Inject
    lateinit var textMacroRepository: TextMacroRepository



    @Inject
    lateinit var clickedSymbolRepository: ClickedSymbolRepository

    @Inject
    lateinit var deleteKeyFlickDeleteTargetRepository: DeleteKeyFlickDeleteTargetRepository

    @Inject
    lateinit var clipboardHistoryRepository: ClipboardHistoryRepository

    @Inject
    lateinit var keyboardRepository: KeyboardRepository

    @Inject
    lateinit var romajiMapRepository: RomajiMapRepository

    @Inject
    lateinit var ngWordRepository: NgWordRepository

    @Inject
    lateinit var shortCurRepository: ShortcutRepository

    @Inject
    lateinit var clipboardUtil: ClipboardUtil

    @Inject
    lateinit var zenzRuntimeClient: ZenzRuntimeClient

    @Inject
    lateinit var sumireSpecialKeyRepository: SumireSpecialKeyRepository

    private var shortcutAdapter: ShortcutAdapter? = null

    private var romajiConverter: RomajiKanaConverter? = null
    private var customRomajiScreenConverter: CustomRomajiScreenConverter? = null
    private val customScreenCandidateResult = MutableStateFlow<Pair<String, List<Candidate>>?>(null)

    private fun convertScreenQwerty(converter: RomajiKanaConverter, text: String): String =
        if (isDefaultRomajiHenkanMap) converter.convertQWERTYZenkaku(text)
        else customRomajiScreenConverter?.convert(text) ?: text

    private fun flushCustomScreenComposition() {
        if (isDefaultRomajiHenkanMap || isHenkan.get() ||
            currentInputModeForSession != InputMode.ModeJapanese) return
        val screenRomaji = false ||
            (qwertyMode.value == TenKeyQWERTYMode.Custom && isCustomLayoutRomajiMode)
        if (!screenRomaji) return
        val before = inputString.value
        val after = customRomajiScreenConverter?.flush(before) ?: before
        if (after != before) {
            _inputString.value = after
            setComposingText(after + stringInTail.get(), 1)
        }
    }

    private lateinit var clipboardManager: ClipboardManager

    private var isClipboardHistoryFeatureEnabled: Boolean = false
    private val clipboardMutex = Mutex()
    private val clipboardPreviewRequestId = AtomicLong(0L)
    private var clipboardPreviewLoadJob: Job? = null
    @Volatile
    private var cachedClipboardPreviewItem: ClipboardItem = ClipboardItem.Empty
    @Volatile
    private var cachedClipboardPreviewSensitive: Boolean = false
    private val zenzModelPathMutex = Mutex()
    private var cachedZenzModelSource: String? = null
    private var cachedZenzModelPath: String? = null
    private var tenkeyQWERTYSwitchNumber: Boolean? = false
    private var tenkeyUseThreeStateKeyboard: Boolean = true
    private var tenkeyNumberSymbolKeyGapDp: Int = 4
    private var tenkeySwitchNumberToQwertyNumberPreference: Boolean = false
    private var qwertyNumberOpenedFromTenkeyTwoStateNumberKey: Boolean = false
    private var tabletTenkeyQwertySwitchEnglish: Boolean = false
    private var tenkeyKeymapGuideSettings = ModeKeymapGuideSettings()
    private var sumireKeymapGuideSettings = ModeKeymapGuideSettings()
    private var customKeymapGuidePreference: Boolean = false
    private var flickGuideTextSizeSpPreference: Int? = 9
    private var flickGuideMaxCharactersPreference: Int? = 1

    private val inkRootLocation = IntArray(2)
    private val inkTargetLocation = IntArray(2)
    private val inkMappedPoint = FloatArray(2)
    private var isKeyboardRounded: Boolean? = false
    private var keyboardCornerRadiusDp: Int = 32
    private var keyboardCornerTopLeft: Boolean = true
    private var keyboardCornerTopRight: Boolean = true
    private var keyboardCornerBottomLeft: Boolean = true
    private var keyboardCornerBottomRight: Boolean = true
    private var reconversionEnabledPreference: Boolean = false
    private var bunsetsuPositionList: List<Int>? = emptyList()
    private var bunsetsuSplitPatterns: List<List<Int>> = emptyList()
    private var bunsetsuConversionSession: BunsetsuConversionSession? = null
    private var latestBunsetsuConversionSnapshot: BunsetsuConversionSnapshot? = null
    private var bunsetsuSessionGeneration = 0L
    private val bunsetsuOperationMutex = Mutex()
    private var pendingReconversionEntry: ReconversionEntry? = null
    private var pendingReconversionValid: Boolean = false
    private val reconversionValidationRequestId = AtomicLong(0L)
    private var bunsetsuReconversionDraft: BunsetsuReconversionDraft? = null
    private var preserveBunsetsuReconversionDraftOnNextProcessInput = false
    private var isRestoringReconversionInput = false

    private var henkanPressedWithBunsetsuDetect: Boolean = false
    private var conversionKeySwipePreference: Boolean? = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false


    /**
     * クリップボードの内容が変更されたときに呼び出されるリスナー。
     */
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        ioScope.launch {
            // 現在クリップボードにあるアイテムを取得 (ClipboardItem.Text or Image)
            val newItem = clipboardUtil.getPrimaryClipContent()
            val isSensitive = clipboardUtil.isPrimaryClipSensitive()
            clipboardMutex.withLock {
                if (newItem is ClipboardItem.Empty || isSensitive) return@withLock

                // 2. DBに保存されている最新のメタデータを取得
                val lastSavedItem = clipboardHistoryRepository.getLatestItem()

                // 3. 重複チェック
                // 最新の実データを取得して比較 (テキストのみ。画像はパス比較などで代用検討)
                val isDuplicate = if (lastSavedItem == null) {
                    false
                } else {
                    when {
                        newItem is ClipboardItem.Text && lastSavedItem.itemType == ItemType.TEXT -> {
                            // DBのpreviewではなくファイルの実体と、現在のクリップボードを比較
                            val lastFullText = clipboardHistoryRepository.getFullText(lastSavedItem)
                            newItem.text == lastFullText
                        }

                        else -> false // 画像の厳密な比較はコストが高いため、一旦 false
                    }
                }

                // 4. 重複していなければ保存
                if (!isDuplicate) {
                    if (isClipboardHistoryFeatureEnabled) {
                        Timber.d("Saving new clipboard item to file and DB.")
                        // ここで Repository の新メソッドを呼ぶ (ファイル保存 + DB挿入)
                        clipboardHistoryRepository.insertFromClipboard(newItem)
                        cleanupExpiredClipboardItemsIfNeededNow()
                    }
                }
            }
            withContext(Dispatchers.Main.immediate) {
                cachedClipboardPreviewItem = newItem
                cachedClipboardPreviewSensitive = isSensitive
                if (newItem is ClipboardItem.Empty) {
                    clearSelectedTextClipboardPreviewRefresh()
                } else {
                    markClipboardPreviewRefreshAfterPrimaryClipChanged()
                }
                updateClipboardPreview()
            }
            refreshClipboardPreviewSnapshot()
        }
    }

    private var suggestionAdapter: SuggestionAdapter? = null
    private var suggestionAdapterFull: SuggestionAdapter? = null
    private var inlineAutofillController: InlineAutofillController? = null
    private var inlineSuggestionEnabled: Boolean = true
    private val inlineSuggestionDisplayState = InlineSuggestionDisplayState()
    private var currentInlineSuggestionViews: List<View> = emptyList()
    private var currentCandidateStripCandidates: List<Candidate> = emptyList()
    private var currentCandidateStripFullCandidates: List<Candidate> = emptyList()
    private var currentCandidateStripContent: CandidateStripContent = CandidateStripContent.Empty
    private var pendingZeroQueryKeyAfterCommit: String? = null
    private var pendingZeroQueryReadingAfterCommit: String = ""
    private var zeroQueryCandidates: List<Candidate> = emptyList()
    private var zeroQueryVisible: Boolean = false
    private var zeroQuerySelectionUpdateSuppressCount: Int = 0
    private var zeroQueryLookupJob: Job? = null
    /** 後続語候補の zenz 並べ替え（1 推論・非同期、入力・再確定でキャンセル）。 */
    private var zeroQueryZenzJob: Job? = null
    private val zeroQueryProviderHolder = LazyZeroQueryProvider {
        ZeroQueryProvider(AndroidZeroQueryAssetReader(assets))
    }
    private val zeroQueryLookupUseCase: ZeroQueryLookupUseCase by lazy {
        ZeroQueryLookupUseCase(
            bundledProviderHolder = zeroQueryProviderHolder,
        )
    }
    private var configuredShortcutItems: List<ShortcutType> = emptyList()
    private var currentShortcutItems: List<ShortcutType> = emptyList()
    private var candidateStripIncognitoIconDrawable: Drawable? = null
    private var candidateStripIncognitoVisible: Boolean = false
    private var integratedShortcutEntryExpanded: Boolean = false
    private var lastSuggestionLayoutKey: SuggestionLayoutKey? = null
    private var mainSuggestionGridSpacingDecoration: RecyclerView.ItemDecoration? = null

    private data class ClipboardPreviewSnapshot(
        val text: String,
        val bitmap: Bitmap?,
        val textIsLastPasted: Boolean,
    )

    private var selectedTextClipboardPreviewRefreshText: String? = null
    /**
     * Selection state used by candidate-strip rendering.
     *
     * InputConnection is a remote Binder connection for most editors.  Reading selected text
     * synchronously from the main thread can therefore stall the whole IME while the editor
     * responds.  Keep the range state immediately and refresh the text snapshot off-main.
     */
    private var editorTextSelected: Boolean = false
    private var selectedEditorText: String = ""
    private val selectedEditorTextRequestId = AtomicLong(0L)
    private val editorConnectionReadMutex = Mutex()
    private val horizontalCursorSelectionRevision = AtomicLong(0L)
    private var systemUserDictionaryLoadJob: Job? = null
    private var kanaKanjiEngineLoadJob: Job? = null
    private var kanaKanjiEngineActivationJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val localFontScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val horizontalCursorMoveHandler by lazy {
        HorizontalCursorMoveHandler(
            scope = scope,
            currentConnection = { currentInputConnection },
            currentRevision = { editorMutationRevision.current() },
            currentSelectionRevision = { horizontalCursorSelectionRevision.get() },
            canCollapseSelection = { !selectMode.value },
            readMutex = editorConnectionReadMutex,
            setSelection = { connection, start, end ->
                if (currentInputConnection !== connection) false else setSelection(start, end)
            },
            sendDpad = { direction ->
                sendDownUpKeyEvents(
                    if (direction == HorizontalCursorMoveHandler.Direction.Left) {
                        KeyEvent.KEYCODE_DPAD_LEFT
                    } else {
                        KeyEvent.KEYCODE_DPAD_RIGHT
                    }
                )
            },
        )
    }
    private val forwardDeleteCoordinator by lazy {
        ForwardDeleteCoordinator(
            scope = scope,
            currentConnection = { currentInputConnection },
            currentRevision = { editorMutationRevision.current() },
            canDelete = { inputString.value.isEmpty() && stringInTail.get().isEmpty() },
            delete = { selectionWasActive -> performForwardDelete(selectionWasActive) },
            recordDeletion = { deletedText ->
                pushEditHistoryEntry(
                    EditHistoryEntry.DeleteCommittedText(deletedText, DeleteDirection.AfterCursor)
                )
            },
        )
    }
    private val kanaKanjiConversionDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
                runnable.run()
            },
            "KanaKanjiConversion",
        ).apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var imeWindowManager: WindowManager? = null
    private var crossWindowBlurEnabled: Boolean = false
    private var crossWindowBlurListenerRegistered: Boolean = false
    private val crossWindowBlurEnabledListener = Consumer<Boolean> { enabled ->
        if (Looper.myLooper() == Looper.getMainLooper()) {
            crossWindowBlurEnabled = enabled
            updateImeWindowBlurForCurrentMode()
        } else {
            mainHandler.post {
                crossWindowBlurEnabled = enabled
                updateImeWindowBlurForCurrentMode()
            }
        }
    }
    private val runtimeGestureSettingsSource = MutableRuntimeGestureSettingsSource(
        RuntimeGestureSettings()
    )
    private lateinit var runtimeInputSharedPreferences: SharedPreferences
    private var runtimeInputPreferenceListenerRegistered = false
    private val runtimeInputPreferenceKeys = setOf(
        AppPreference.INLINE_SUGGESTION_ENABLED_KEY,
        AppPreference.STABILIZE_CANDIDATE_STRIP_HEIGHT_KEY,
        AppPreference.FLICK_SENSITIVITY_KEY,
        AppPreference.TFBI_DIAGONAL_RECOGNITION_MODE_KEY,
        AppPreference.TENKEY_KEYMAP_GUIDE_JAPANESE_KEY,
        AppPreference.TENKEY_KEYMAP_GUIDE_ENGLISH_KEY,
        AppPreference.TENKEY_KEYMAP_GUIDE_NUMBER_KEY,
        AppPreference.TENKEY_USE_THREE_STATE_KEY,
        AppPreference.TENKEY_NUMBER_SYMBOL_KEY_GAP_KEY,
        AppPreference.SUMIRE_KEYMAP_GUIDE_JAPANESE_KEY,
        AppPreference.SUMIRE_KEYMAP_GUIDE_ENGLISH_KEY,
        AppPreference.SUMIRE_KEYMAP_GUIDE_NUMBER_KEY,
        AppPreference.CUSTOM_KEYMAP_GUIDE_KEY,
        AppPreference.CUSTOM_KEYBOARD_INPUT_IN_EMPTY_AREAS_KEY,
        AppPreference.CUSTOM_DIRECT_INPUT_REPLACE_COMPOSING_KEY,
        AppPreference.FLICK_TFBI_POPUP_PRESENTATION_KEY,
        AppPreference.FLICK_TFBI_FLICK_START_POSITION_KEY,
    )
    private val runtimeInputPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key in runtimeInputPreferenceKeys) {
                runOnMainThread {
                    syncRuntimeInputPreferences()
                }
            }
        }

    private fun assertMainThread(functionName: String) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "$functionName must be called on the main thread."
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post {
                action()
            }
        }
    }

    private fun resetEditorSelectionSnapshot() {
        selectedEditorTextRequestId.incrementAndGet()
        editorTextSelected = false
        selectedEditorText = ""
    }

    private fun refreshClipboardPreviewSnapshot() {
        val requestId = clipboardPreviewRequestId.incrementAndGet()
        clipboardPreviewLoadJob?.cancel()
        clipboardPreviewLoadJob = ioScope.launch {
            val item = runCatching {
                clipboardUtil.getPrimaryClipPreviewContent()
            }.getOrDefault(ClipboardItem.Empty)
            val isSensitive = runCatching {
                clipboardUtil.isPrimaryClipSensitive()
            }.getOrDefault(false)
            withContext(Dispatchers.Main.immediate) {
                if (clipboardPreviewRequestId.get() != requestId) return@withContext
                cachedClipboardPreviewItem = item
                cachedClipboardPreviewSensitive = isSensitive
                updateClipboardPreview()
            }
        }
    }

    private fun updateEditorSelectionSnapshot(newSelStart: Int, newSelEnd: Int) {
        val hasSelection =
            newSelStart >= 0 && newSelEnd >= 0 && newSelStart != newSelEnd
        editorTextSelected = hasSelection
        selectedEditorText = ""
        val requestId = selectedEditorTextRequestId.incrementAndGet()
        if (!hasSelection) return

        val connection = currentInputConnection ?: return
        ioScope.launch {
            val text = runCatching {
                connection.getSelectedText(0)?.toString().orEmpty()
            }.getOrDefault("")
            runOnMainThread {
                if (selectedEditorTextRequestId.get() != requestId || !editorTextSelected) {
                    return@runOnMainThread
                }
                selectedEditorText = text
                if (text.isNotEmpty()) {
                    handleSelectedTextSelection(text)
                } else {
                    clearSelectedTextClipboardPreviewRefresh()
                    if (selectionActionSession != null) {
                        clearSelectionActionSession(
                            clearSuggestions = hasSelectionActionCandidates()
                        )
                    }
                }
                refreshCandidateStripContent()
            }
        }
    }

    private fun setSuggestionAdapterSuggestionsOnMain(candidates: List<Candidate>) {
        runOnMainThread {
            measureDebugSection("IMEService.setSuggestionAdapterSuggestionsOnMain") {
                collapseShortcutEntryExpansion(refreshContent = false)
                currentCandidateStripCandidates = candidates
                refreshCandidateStripContent()
            }
        }
    }

    private fun setSuggestionAdaptersOnMain(
        candidates: List<Candidate>,
        fullCandidates: List<Candidate> = candidates
    ) {
        runOnMainThread {
            measureDebugSection("IMEService.setSuggestionAdaptersOnMain") {
                collapseShortcutEntryExpansion(refreshContent = false)
                currentCandidateStripCandidates = candidates
                currentCandidateStripFullCandidates = fullCandidates
                refreshCandidateStripContent()
            }
        }
    }

    private suspend fun updateSuggestionAdaptersOnMain(
        candidates: List<Candidate>,
        insertString: String,
        fullCandidates: List<Candidate> = candidates,
        token: CandidateRequestToken? = null,
    ) = measureDebugStage("IMEService.updateSuggestionAdaptersOnMain") {
        withContext(Dispatchers.Main.immediate) {
            if (!shouldApplyCandidateResult(insertString, token)) return@withContext
            collapseShortcutEntryExpansion(refreshContent = false)
            currentCandidateStripCandidates = candidates
            currentCandidateStripFullCandidates = fullCandidates
            refreshCandidateStripContent()
            customScreenCandidateResult.value = insertString to candidates
        }
    }

    private fun refreshCandidateStripContent(
        candidatesShown: Boolean = shortcutToolbarHiddenForCandidates,
        resetCandidateTabSelection: Boolean = false
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnMainThread {
                refreshCandidateStripContent(
                    candidatesShown = candidatesShown,
                    resetCandidateTabSelection = resetCandidateTabSelection
                )
            }
            return
        }

        // CandidateShowFlag.Updating can be emitted once with an empty input while the
        // editor/IME is being recreated (for example, after hiding and showing the keyboard).
        // That event must not make the empty strip behave like an active conversion strip.
        val effectiveCandidatesShown = isCandidateStripActive(
            candidatesShown = candidatesShown,
            inputStringEmpty = inputString.value.isEmpty(),
            suggestionsSuppressed = suppressSuggestions
        )

        val content = resolveCandidateStripContent(
            candidates = currentCandidateStripCandidates,
            candidatesShown = effectiveCandidatesShown,
            includeZeroQuery = true
        )
        val fullContent = resolveCandidateStripContent(
            candidates = currentCandidateStripFullCandidates,
            candidatesShown = effectiveCandidatesShown,
            includeZeroQuery = false,
            includeInlineSuggestionToggle = false,
        )
        currentCandidateStripContent = content
        val inlineSuggestionState = InlineSuggestionStripState(
            views = currentInlineSuggestionViews,
            showInlineSuggestions =
                inlineSuggestionDisplayState.surface == InlineSuggestionSurface.Inline &&
                    currentInlineSuggestionViews.isNotEmpty(),
            toggle = inlineSuggestionToggleForCandidateStrip(),
        )
        suggestionAdapter?.submitContent(content, inlineSuggestionState)
        syncNecookeyPredictionRow(effectiveCandidatesShown)
        mainLayoutBinding?.let { binding ->
            setMainSuggestionColumn(binding)
        }
        // The full candidate view is hidden during normal composing. Submitting to its
        // AsyncListDiffer on every keystroke still calculates a complete DiffUtil diff even
        // though the user cannot see it. Keep the state current, but submit only when that
        // view is actually visible; the visibility transition below refreshes it once.
        if (isFullCandidateViewVisible()) {
            suggestionAdapterFull?.submitContent(fullContent)
        }
        val presentation = resolveCandidateStripPresentation(
            candidatesShown = effectiveCandidatesShown,
            resetCandidateTabSelection = resetCandidateTabSelection,
            content = content
        )
        applyCandidateStripPresentation(presentation)
    }

    private fun isFullCandidateViewVisible(): Boolean {
        return mainLayoutBinding?.candidatesRowView?.isVisible == true
    }

    private fun resolveCandidateStripContent(
        candidates: List<Candidate>,
        candidatesShown: Boolean,
        includeZeroQuery: Boolean,
        includeInlineSuggestionToggle: Boolean = true,
    ): CandidateStripContent {
        val state = buildCandidateStripInputState(
            candidates = candidates,
            candidatesShown = candidatesShown,
            includeZeroQuery = includeZeroQuery,
        )
        return CandidateStripContentResolver.resolve(
            if (includeInlineSuggestionToggle) {
                state
            } else {
                state.copy(inlineSuggestionToggle = null)
            }
        )
    }

    private fun inlineSuggestionToggleForCandidateStrip(): InlineSuggestionToggle? {
        if (!inlineSuggestionEnabled || !inlineSuggestionDisplayState.hasSuggestions) {
            return null
        }
        val contentDescription = when (inlineSuggestionDisplayState.surface) {
            InlineSuggestionSurface.Inline ->
                R.string.inline_suggestion_show_normal_candidates_content_description

            InlineSuggestionSurface.NormalCandidates ->
                R.string.inline_suggestion_show_inline_candidates_content_description
        }
        return InlineSuggestionToggle(
            contentDescription = getString(contentDescription),
            badge = null,
            iconResId = when (inlineSuggestionDisplayState.surface) {
                InlineSuggestionSurface.Inline -> R.drawable.more_horiz_24px
                InlineSuggestionSurface.NormalCandidates -> R.drawable.inline_suggestion_key_24
            },
            iconBackgroundResId = when (inlineSuggestionDisplayState.surface) {
                InlineSuggestionSurface.Inline -> null
                InlineSuggestionSurface.NormalCandidates ->
                    com.kazumaproject.core.R.drawable.suggestion_icon_bg
            },
        )
    }

    private fun buildCandidateStripInputState(
        candidates: List<Candidate>,
        candidatesShown: Boolean,
        includeZeroQuery: Boolean
    ): CandidateStripInputState {
        val clipboardPreview = resolveClipboardPreviewSnapshot()
        val shouldSuppressClipboardPreviewForSelectedText =
            editorTextSelected &&
                (selectedEditorText.isEmpty() ||
                    selectedTextClipboardPreviewRefreshText != selectedEditorText)
        val hasUndoHistory = isEditHistoryEnabled() && deletedBuffer.hasUndoHistory()
        val hasRedoHistory = isEditHistoryEnabled() && deletedBuffer.hasRedoHistory()
        return CandidateStripInputState(
            candidates = candidates,
            zeroQueryVisible = zeroQueryVisible,
            zeroQueryCandidates = zeroQueryCandidates,
            includeZeroQuery = includeZeroQuery,
            inputStringEmpty = inputString.value.isEmpty(),
            tailEmpty = stringInTail.get().isEmpty(),
            candidatesShown = candidatesShown,
            symbolKeyboardShown = keyboardSymbolViewState.value.isShown,
            customLayoutPickerShown = isCustomLayoutPickerShownForCandidateStrip(),
            customLayouts = customLayouts,
            selectionActionsShown = candidates.isSelectionActionCandidates(),
            editorTextSelected = shouldSuppressClipboardPreviewForSelectedText,
            clipboardPreviewEnabled = clipboardPreviewVisibility == true,
            clipboardPreviewDescriptionShown = clipboardPreviewTapToDelete != true,
            clipboardPreviewTapToDelete = clipboardPreviewTapToDelete == true,
            clipboardText = clipboardPreview.text,
            clipboardBitmap = clipboardPreview.bitmap,
            clipboardTextIsLastPasted = clipboardPreview.textIsLastPasted,
            incognitoVisible = candidateStripIncognitoVisible,
            undoEnabled = hasUndoHistory,
            redoEnabled = hasRedoHistory,
            reconvertEnabled = shouldShowReconversionButton(),
            undoText = if (hasUndoHistory) {
                getString(com.kazumaproject.core.R.string.undo_action_label)
            } else {
                ""
            },
            redoText = if (hasRedoHistory) {
                getString(com.kazumaproject.core.R.string.redo_action_label)
            } else {
                ""
            },
            shortcutToolbarVisible = shortcutTollbarVisibility == true,
            shortcutToolbarIntegratedInSuggestion = shortcutToolbarIntegratedInSuggestion == true,
            integratedShortcutEntryExpanded = integratedShortcutEntryExpanded,
            shortcutItems = currentShortcutItems,
            inlineSuggestionToggle = inlineSuggestionToggleForCandidateStrip(),
        )
    }

    private fun resolveClipboardPreviewSnapshot(): ClipboardPreviewSnapshot {
        return when (val item = cachedClipboardPreviewItem) {
            is ClipboardItem.Image -> {
                if (cachedClipboardPreviewSensitive) {
                    ClipboardPreviewSnapshot(
                        text = getSensitiveClipboardPreviewText(),
                        bitmap = null,
                        textIsLastPasted = false
                    )
                } else {
                    ClipboardPreviewSnapshot(
                        text = "",
                        bitmap = item.bitmap,
                        textIsLastPasted = false
                    )
                }
            }

            is ClipboardItem.Text -> {
                ClipboardPreviewSnapshot(
                    text = getClipboardPreviewText(item.text),
                    bitmap = null,
                    textIsLastPasted =
                        appPreference.last_pasted_clipboard_text_preference == item.text
                )
            }

            is ClipboardItem.Empty -> {
                ClipboardPreviewSnapshot(
                    text = "",
                    bitmap = null,
                    textIsLastPasted = false
                )
            }
        }
    }

    // 候補欄のキーボード一覧表示は廃止（後続語・ゼロクエリ候補を常に優先）
    private fun isCustomLayoutPickerShownForCandidateStrip(): Boolean = false

    private fun List<Candidate>.isSelectionActionCandidates(): Boolean {
        return isNotEmpty() && all { isSelectionActionCandidate(it) }
    }

    private fun rememberZeroQueryKeyAfterCommit(committedText: String, reading: String = "") {
        if (committedText.isBlank()) return
        pendingZeroQueryKeyAfterCommit = committedText
        pendingZeroQueryReadingAfterCommit = reading
    }

    private fun cancelZeroQueryLookup() {
        zeroQueryLookupJob?.cancel()
        zeroQueryLookupJob = null
        zeroQueryZenzJob?.cancel()
        zeroQueryZenzJob = null
    }

    private fun clearZeroQueryShownState(refresh: Boolean = true) {
        val changed = zeroQueryVisible || zeroQueryCandidates.isNotEmpty() ||
            zeroQuerySelectionUpdateSuppressCount != 0
        cancelZeroQueryLookup()
        zeroQueryVisible = false
        zeroQueryCandidates = emptyList()
        zeroQuerySelectionUpdateSuppressCount = 0
        if (refresh && changed) {
            refreshCandidateStripContent()
        }
    }

    private fun clearZeroQueryAllState(refresh: Boolean = true) {
        val changed = pendingZeroQueryKeyAfterCommit != null ||
            zeroQueryVisible ||
            zeroQueryCandidates.isNotEmpty() ||
            zeroQuerySelectionUpdateSuppressCount != 0
        cancelZeroQueryLookup()
        pendingZeroQueryKeyAfterCommit = null
        zeroQueryVisible = false
        zeroQueryCandidates = emptyList()
        zeroQuerySelectionUpdateSuppressCount = 0
        if (refresh && changed) {
            refreshCandidateStripContent()
        }
    }

    private fun invalidateZeroQueryForEditorMutation() {
        editorMutationRevision.advance()
        clearZeroQueryAllState(refresh = true)
    }

    private fun toggleZeroQueryVisibility() {
        if (zeroQueryCandidates.isEmpty()) {
            clearZeroQueryAllState(refresh = true)
            return
        }

        zeroQueryVisible = !zeroQueryVisible
        zeroQuerySelectionUpdateSuppressCount = 0
        refreshCandidateStripContent(candidatesShown = false)
    }

    private fun canShowZeroQueryAfterCommit(committedText: String): Boolean {
        if (!zeroQuerySuggestionPreference) return false
        if (inputString.value.isNotEmpty()) return false
        if (stringInTail.get().isNotEmpty()) return false
        if (keyboardSymbolViewState.value.isShown) return false
        if (
            currentInputModeForSession != InputMode.ModeJapanese &&
            !committedText.isZeroQueryNumberKey()
        ) {
            return false
        }
        if (isCurrentInputTypePasswordOrEmailForZeroQuery()) return false
        // Custom layout picker is only a fallback for the empty strip; the resolver already
        // prefers zero-query (next-word) suggestions over it, so it must not block them here.
        if (isSelectionActionsShownForCandidateStrip()) return false
        if (editorTextSelected) {
            return false
        }
        return true
    }

    private fun String.isZeroQueryNumberKey(): Boolean {
        if (isEmpty()) return false

        var index = 0
        while (index < length) {
            val codePoint = codePointAt(index)
            if (Character.digit(codePoint, 10) < 0) {
                return false
            }
            index += Character.charCount(codePoint)
        }
        return true
    }

    private fun canCommitZeroQueryCandidate(): Boolean {
        if (!zeroQuerySuggestionPreference) return false
        if (zeroQueryCandidates.isEmpty()) return false
        if (inputString.value.isNotEmpty()) return false
        if (stringInTail.get().isNotEmpty()) return false
        if (isCurrentInputTypePasswordOrEmailForZeroQuery()) return false
        if (editorTextSelected) {
            return false
        }
        return true
    }

    private fun isCurrentInputTypePasswordOrEmailForZeroQuery(): Boolean {
        return currentInputType in passwordTypes ||
            currentInputType == InputTypeForIME.TextEmailAddress ||
            currentInputType == InputTypeForIME.TextWebEmailAddress
    }

    private fun isSelectionActionsShownForCandidateStrip(): Boolean {
        return currentCandidateStripCandidates.isSelectionActionCandidates() ||
            currentCandidateStripFullCandidates.isSelectionActionCandidates() ||
            currentCandidateStripContent is CandidateStripContent.SelectionActions
    }

    private fun consumePendingZeroQueryAfterCommit() {
        val key = pendingZeroQueryKeyAfterCommit ?: return
        val committedReading = pendingZeroQueryReadingAfterCommit
        pendingZeroQueryKeyAfterCommit = null
        pendingZeroQueryReadingAfterCommit = ""
        cancelZeroQueryLookup()

        if (!canShowZeroQueryAfterCommit(key)) {
            clearZeroQueryShownState(refresh = true)
            return
        }

        val learnedEnabled = isLearnDictionaryMode == true
        val leftContext = if (learnedEnabled) {
            currentInputConnection?.getTextBeforeCursor(NextWordPolicy.MAX_CONTEXT_LENGTH * 2, 0)
                ?.toString().orEmpty()
        } else {
            ""
        }
        val zenzLeftContext = if (
            necookeyZenzGateEnabled() && currentInputType !in passwordTypes
        ) {
            leftContext.ifEmpty {
                currentInputConnection
                    ?.getTextBeforeCursor(necookeyCandidateBarConfig.maxLeftContextChars, 0)
                    ?.toString().orEmpty()
            }
        } else {
            ""
        }
        zeroQueryLookupJob = scope.launch {
            val candidates = withContext(Dispatchers.IO) {
                val learned = if (learnedEnabled) learnedFollowingWords(key, leftContext) else emptyList()
                learned + zeroQueryLookupUseCase.lookup(key)
            }
                .filter { it.string.isNotBlank() }
                .distinctBy { it.string }
            val emoji = if (currentInputType !in passwordTypes) {
                withContext(Dispatchers.Default) { contextEmojiCandidates(committedReading, candidates) }
            } else {
                emptyList()
            }

            if (!canShowZeroQueryAfterCommit(key)) {
                clearZeroQueryShownState(refresh = true)
                return@launch
            }
            if (candidates.isEmpty() && emoji.isEmpty()) {
                clearZeroQueryShownState(refresh = true)
                return@launch
            }

            val shown = NextWordExtras.initial(candidates, emoji)
            zeroQueryCandidates = shown
            zeroQueryVisible = true
            zeroQuerySelectionUpdateSuppressCount += 1
            zeroQueryLookupJob = null
            refreshCandidateStripContent(candidatesShown = false)
            launchZeroQueryZenzRerank(key, zenzLeftContext, candidates, emoji, shown)
        }
    }

    /** 後続語の上位 [NextWordZenzReranker.TOP_K] 件を左文脈つきで zenz が 1 回採点し、並べ替える。 */
    private fun launchZeroQueryZenzRerank(
        key: String,
        leftContext: String,
        candidates: List<Candidate>,
        emoji: List<Candidate>,
        shown: List<Candidate>,
    ) {
        zeroQueryZenzJob?.cancel()
        val left = leftContext.takeLast(necookeyCandidateBarConfig.maxLeftContextChars)
        if (left.isBlank() || shown.size < 2) return
        val head = NextWordExtras.scoringTargets(candidates, emoji)
        zeroQueryZenzJob = scope.launch {
            val scores = try {
                withContext(Dispatchers.Default) {
                    val config = resolveZenzRuntimeConfig() ?: return@withContext null
                    zenzRuntimeClient.score(
                        config = config,
                        profile = "",
                        topic = "",
                        style = "",
                        preference = "",
                        leftContext = left,
                        rightContext = "",
                        input = "",
                        candidates = head.map { it.string }.toTypedArray(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "zenz next-word rerank failed")
                null
            } ?: return@launch
            val reranked = NextWordExtras.rerank(candidates, emoji, scores)
            if (!zeroQueryVisible || zeroQueryCandidates != shown ||
                !canShowZeroQueryAfterCommit(key) || reranked == shown
            ) {
                return@launch
            }
            Timber.d("zenz next-word rerank: %s -> %s", head.map { it.string }, reranked.take(head.size).map { it.string })
            zeroQueryCandidates = reranked
            zeroQueryZenzJob = null
            refreshCandidateStripContent(candidatesShown = false)
        }
    }

    /** 確定した読みのキーワードに一致する絵文字（zenz 採点用に [NextWordExtras.EMOJI_POOL] 件まで）。 */
    private fun contextEmojiCandidates(reading: String, base: List<Candidate>): List<Candidate> {
        if (reading.isBlank() || !::kanaKanjiEngine.isInitialized) return emptyList()
        val symbols = try {
            kanaKanjiEngine.emojiForExactReadings(
                NextWordExtras.emojiKeys(reading),
                NextWordExtras.EMOJI_POOL + base.size,
            )
        } catch (e: Exception) {
            Timber.w(e, "context emoji lookup failed")
            emptyList()
        }
        return NextWordExtras.emojiPool(base, symbols.map { symbol ->
            Candidate(
                string = symbol,
                type = CONTEXT_EMOJI_CANDIDATE_TYPE,
                length = 0u,
                score = 0,
                yomi = "",
            )
        })
    }

    /**
     * Following-word candidates from learning: learned next words for the left context, then
     * learned phrases that continue the committed text (their remainder).
     */
    private suspend fun learnedFollowingWords(committedText: String, leftContext: String): List<Candidate> {
        return try {
            val context = leftContext.ifEmpty { committedText }
            val nextWords = nextWordRepository.lookup(context, "", NEXT_WORD_CANDIDATE_LIMIT).map {
                Candidate(
                    string = it.output,
                    type = CANDIDATE_TYPE_NEXT_WORD,
                    length = it.reading.length.toUByte(),
                    score = 0,
                    yomi = it.reading,
                )
            }
            val tail = committedText.takeLast(NextWordPolicy.MAX_CONTEXT_LENGTH)
            val continuations = learnRepository.findByOutputPrefix(
                outputPrefix = tail,
                limit = NEXT_WORD_CANDIDATE_LIMIT,
            ).mapNotNull { entry ->
                NextWordPolicy.remainderAfter(tail, entry.out)?.let { rest ->
                    Candidate(
                        string = rest,
                        type = CANDIDATE_TYPE_LEARNED_DICTIONARY,
                        length = 0u,
                        score = entry.score,
                    )
                }
            }
            nextWords + continuations
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "learned following word lookup failed")
            emptyList()
        }
    }

    private fun handleZeroQueryOnUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
    ) {
        if (!zeroQueryVisible && zeroQueryCandidates.isEmpty() &&
            pendingZeroQueryKeyAfterCommit == null &&
            zeroQuerySelectionUpdateSuppressCount == 0
        ) {
            return
        }

        val inputAndSelectionAreEmpty =
            inputString.value.isEmpty() &&
                stringInTail.get().isEmpty() &&
                !editorTextSelected &&
                newSelStart == newSelEnd
        if (zeroQuerySelectionUpdateSuppressCount > 0 && inputAndSelectionAreEmpty) {
            zeroQuerySelectionUpdateSuppressCount -= 1
            return
        }

        val cursorMoved = oldSelStart != newSelStart || oldSelEnd != newSelEnd
        if (
            editorTextSelected ||
            newSelStart != newSelEnd ||
            inputString.value.isNotEmpty() ||
            stringInTail.get().isNotEmpty() ||
            cursorMoved
        ) {
            clearZeroQueryAllState(refresh = false)
        }
    }

    private fun commitZeroQueryCandidate(candidate: Candidate) {
        if (!canCommitZeroQueryCandidate()) {
            clearZeroQueryAllState(refresh = true)
            return
        }
        currentInputConnection?.commitText(candidate.string, 1)
        clearZeroQueryAllState(refresh = true)
    }

    private suspend fun applyFirstSuggestionOnMainIfCurrent(
        insertString: String,
        candidate: Candidate?
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!shouldApplyCandidateResult(insertString)) return@withContext false
        if (qwertyMode.value == TenKeyQWERTYMode.Custom) {
            // A tap may extend the deadline while a completed candidate is queued for Main.
            delayBeforeApplyingLiveConversion()
            if (!shouldApplyCandidateResult(insertString)) return@withContext false
        }
        isContinuousTapInputEnabled.set(true)
        lastFlickConvertedNextHiragana.set(true)
        if (!hasConvertedKatakana) {
            if (
                candidate != null &&
                candidate.type != CANDIDATE_TYPE_TEXT_MACRO
            ) {
                applyFirstSuggestion(candidate)
            } else {
                applyRawComposingFallback(insertString)
            }
        }
        if (BuildConfig.DEBUG && shouldStartLiveConversion(insertString)) {
            val request = candidateRefreshCoordinator.requests.value
            if (request.input == insertString) {
                Timber.d(
                    "liveConversionLatency inputLength=%d revision=%d requestToApplied=%.2fms",
                    insertString.length,
                    request.revision,
                    (System.nanoTime() -
                        request.publishedAtNanos) / 1_000_000.0,
                )
            }
        }
        true
    }

    private suspend fun updateBunsetsuSpaceKeyIfNeededOnMain(
        mainView: MainLayoutBinding,
        candidates: List<Candidate>,
        insertString: String
    ) {
        withContext(Dispatchers.Main.immediate) {
            if (!shouldApplyCandidateResult(insertString)) return@withContext
            updateBunsetsuSpaceKeyIfNeeded(mainView, candidates, insertString)
        }
    }

    @Volatile
    private var lastAppliedDictionaryOverrideRevision: Long = Long.MIN_VALUE

    @Volatile
    private var lastAppliedEnglishReadingEnabled: Boolean? = null

    @Volatile
    private var dictionaryOverrideApplyJob: Job? = null

    private var cachedEmoji: List<Emoji>? = null
    private var cachedEmoticons: List<Emoticon>? = null
    private var cachedSymbols: List<Symbol>? = null
    private var cachedClickedSymbolHistory: List<ClickedSymbol>? = null
    private var currentClipboardItems: List<ClipboardItem> = emptyList()
    private var sumireSpecialKeyActionOverrides: List<SumireSpecialKeyActionOverrideEntity> =
        emptyList()
    private var sumireSpecialKeyPlacementOverrides: List<SumireSpecialKeyPlacementOverrideEntity> =
        emptyList()

    private var deleteLongPressJob: Job? = null
    private val deleteLongPressConversionGate = DeleteLongPressConversionGate()
    private var rightLongPressJob: Job? = null
    private var leftLongPressJob: Job? = null
    private val selectionActionMenuRequestId = AtomicLong(0L)
    private val textMacroExecutionRequestId = AtomicLong(0L)
    private var selectionActionSession: SelectionActionSession? = null

    private var mainLayoutBinding: MainLayoutBinding? = null
    private var lastKeyboardLayoutRootView: View? = null
    private var lastKeyboardLayoutOrientation: Int? = null
    private var consumeKeyboardSelectionPopupBackKeyUp: Boolean = false
    private var keyboardSelectionPopupBackKeyTarget: PopupWindow? = null
    private var keyboardSelectionPopupBackInvokedCallback: OnBackInvokedCallback? = null
    private var isKeyboardSelectionPopupBackInvokedCallbackRegistered: Boolean = false
    private val suggestionProgressReasons = mutableSetOf<SuggestionProgressReason>()
    private var isInputViewActive: Boolean = false
    private val _inputString = MutableStateFlow("")
    private val inputString = _inputString.asStateFlow()
    private var stringInTail = AtomicReference("")
    private var functionKeyConversionSource: String? = null
    private var suppressedSelectionCleanupCount = 0
    private var preservePreEditOnNextSelectionUpdate: String? = null
    private val _dakutenPressed = MutableStateFlow(false)
    private val candidateRefreshCoordinator = CandidateRefreshCoordinator()
    private val candidateRefreshRequests = candidateRefreshCoordinator.requests
    private var defaultInputFinalizeJob: Job? = null
    private val _suggestionViewStatus = MutableStateFlow(true)
    private val suggestionViewStatus = _suggestionViewStatus.asStateFlow()
    private val _keyboardSymbolViewState = MutableStateFlow(SymbolKeyboardState())
    private val keyboardSymbolViewState: StateFlow<SymbolKeyboardState> =
        _keyboardSymbolViewState.asStateFlow()
    private val _keyboardLayoutEditState =
        MutableStateFlow<KeyboardLayoutEditState>(KeyboardLayoutEditState.Disabled)
    private val keyboardLayoutEditState: StateFlow<KeyboardLayoutEditState> =
        _keyboardLayoutEditState.asStateFlow()
    private var keyboardLayoutEditController: KeyboardLayoutEditController? = null
    private val _tenKeyQWERTYMode = MutableStateFlow<TenKeyQWERTYMode>(TenKeyQWERTYMode.Custom)
    private val qwertyMode = _tenKeyQWERTYMode.asStateFlow()

    private var currentInputType: InputTypeForIME = InputTypeForIME.Text
    private var baselineInputBehavior: ResolvedInputBehavior = ResolvedInputBehavior.COMPOSING_TEXT
    private var shortcutInputBehaviorOverride: ResolvedInputBehavior? = null
    private var currentInputBehavior: ResolvedInputBehavior = ResolvedInputBehavior.COMPOSING_TEXT
    private val inputBehaviorResolver by lazy {
        InputBehaviorResolver {
            TypeNullInputBehaviorSetting.fromPreferenceValue(
                appPreference.type_null_input_behavior_preference
            )
        }
    }
    private val directCommitHandler = DirectCommitHandler()
    private val keyInputBehaviorDispatcher = KeyInputBehaviorDispatcher(directCommitHandler)
    private val lastFlickConvertedNextHiragana = AtomicBoolean(false)
    private val isContinuousTapInputEnabled = AtomicBoolean(false)
    private val englishSpaceKeyPressed = AtomicBoolean(false)
    private var suggestionClickNum = 0
    private val isHenkan = AtomicBoolean(false)
    private val onLeftKeyLongPressUp = AtomicBoolean(false)
    private val onRightKeyLongPressUp = AtomicBoolean(false)
    private val onDeleteLongPressUp = AtomicBoolean(false)
    private var onKeyboardSwitchLongPressUp = false
    private val deleteKeyLongKeyPressed = AtomicBoolean(false)
    private val rightCursorKeyLongKeyPressed = AtomicBoolean(false)
    private val leftCursorKeyLongKeyPressed = AtomicBoolean(false)
    private var isFlickOnlyMode: Boolean? = false
    private var flickEditorPreviewPreference: Boolean = false
    private var flickEditorPreviewDelayMillis: Int = 0
    private var flickPreviewEditorSessionId: Long = 0L
    private val composingTextArbiter = ComposingTextArbiter(
        writeComposingText = { text, cursorPosition ->
            currentInputConnection?.setComposingText(text, cursorPosition) ?: false
        },
        finishComposingText = {
            currentInputConnection?.finishComposingText() ?: false
        },
        copyText = { text -> SpannableString(text) },
    )
    private val flickInputPreviewCoordinator = FlickInputPreviewCoordinator(
        composingTextArbiter = composingTextArbiter,
        createPreviewText = ::createFlickPreviewComposingText,
        schedulePreviewRender = { delayMillis, render ->
            val handler = Handler(Looper.getMainLooper())
            val callback = Runnable { render() }
            handler.postDelayed(callback, delayMillis)
            val cancel: () -> Unit = { handler.removeCallbacks(callback) }
            cancel
        },
        isContextCurrent = { context ->
            context.editorSessionId == flickPreviewEditorSessionId &&
                    context.inputConnectionToken === currentInputConnection
        },
    )
    private val tenKeyFlickTextPreviewListener = FlickTextPreviewListener { event ->
        flickInputPreviewCoordinator.onEvent(
            event = event,
            context = currentFlickPreviewContext(FlickPreviewSource.TENKEY),
        )
    }
    private val sumireFlickTextPreviewListener = FlickTextPreviewListener { event ->
        flickInputPreviewCoordinator.onEvent(
            event = event,
            context = currentFlickPreviewContext(FlickPreviewSource.SUMIRE),
        )
    }
    private var isOmissionSearchEnable: Boolean? = false
    private var delayTime: Int? = 1000
    private var isLearnDictionaryMode: Boolean? = false
    private var isUserDictionaryEnable: Boolean? = false
    private var isUserTemplateEnable: Boolean? = false
    private var isTextMacroCandidateEnable: Boolean = true
    private var suppressHentaiganaCandidates: Boolean = false
    private var zeroQuerySuggestionPreference: Boolean = false
    private var hankakuPreference: Boolean? = false
    private var customDirectModeSpaceHankakuPreference: Boolean = true
    private var isLiveConversionEnable: Boolean? = false
    private var liveConversionStartLength: Int = 1
    private var showLiveConversionCandidateYomi: Boolean = false
    private var nBest: Int? = 4
    private var conversionBeamWidth: Int = 20
    private var flickSensitivityPreferenceValue: Int? = 100
    private var flickThresholdShapePreferenceValue: FlickThresholdShape =
        FlickThresholdShape.Radial
    private var tfbiDiagonalRecognitionMode = TfbiDiagonalRecognitionMode.LEGACY
    private var longPressTimeoutPreferenceValue: Int? = 300
    private var deleteLongPressConversionBehavior =
        DeleteLongPressConversionBehavior.Default
    private var activeDeleteLongPressConversionBehavior:
        DeleteLongPressConversionBehavior? = null
    private var deleteLongPressFinalRefreshRequested = false
    private var tenkeyShowIMEButtonPreference: Boolean? = true
    private var qwertyShowIMEButtonPreference: Boolean? = true
    private var qwertyShowEmojiButtonPreference: Boolean? = false
    private var defaultEmojiSkinTonePreference: String = EmojiSkinToneSupport.DEFAULT_SKIN_TONE
    private var tenkeySpaceFlickPreference = true
    private var qwertyRomajiSpaceFlickPreference = false
    private var qwertyEnglishSpaceFlickPreference = false
    private var qwertyEnableFlickUpPreference: Boolean? = false
    private var qwertyEnableFlickDownPreference: Boolean? = false
    private var qwertyNumberKeyFlickUpChars: Map<String, String> = emptyMap()
    private var qwertyNumberKeyFlickDownChars: Map<String, String> = emptyMap()
    private var qwertyEnableZenkakuSpacePreference: Boolean? = false
    private var qwertyRomajiHankakuNumberPreference: Boolean? = false
    private var qwertyRomajiHankakuSymbolPreference: Boolean? = false
    private var qwertyShowPopupWindowPreference: Boolean? = true
    private var qwertyGlideInputPreference: Boolean = false
    private var qwertyGlideCommitPreviousCandidateOnNewGlidePreference: Boolean = false
    private var qwertyGlideInsertSpaceAfterCommittingPreviousCandidatePreference: Boolean = false
    private var qwertyShowCursorButtonsPreference: Boolean? = false
    private var qwertyShowNumberButtonsPreference: Boolean? = false
    private var qwertyShowSwitchRomajiEnglishPreference: Boolean? = false
    private var qwertyEnglishDirectInputPreference: Boolean = false
    private var qwertyShowKutoutenButtonsPreference: Boolean? = false
    private var qwertyShowKeymapSymbolsPreference: Boolean? = false
    private var qwertyRomajiShiftConversionPreference: Boolean? = false
    private var showCandidateInPasswordPreference: Boolean? = true
    private var mozcUTPersonName: Boolean? = false
    private var mozcUTPlaces: Boolean? = false
    private var mozcUTWiki: Boolean? = false
    private var mozcUTNeologd: Boolean? = false
    private var mozcUTWeb: Boolean? = false
    private var switchQWERTYPassword: Boolean? = false
    private var landscapeForceQwertyPreference: Boolean? = false
    private var landscapeForceQwertyRomajiPreference: Boolean? = false
    private var shortcutTollbarVisibility: Boolean? = false
    private var shortcutToolbarIntegratedInSuggestion: Boolean? = false
    private var shortcutToolbarHeightDp: Int = AppPreference.SHORTCUT_TOOLBAR_HEIGHT_DEFAULT_DP
    private var shortcutToolbarIconSizeDp: Int =
        AppPreference.SHORTCUT_TOOLBAR_ICON_SIZE_DEFAULT_DP
    private var shortcutToolbarHiddenForCandidates: Boolean = false
    private var clipboardPreviewVisibility: Boolean? = true
    private var clipboardPreviewTapToDelete: Boolean? = false
    private var isDeleteLeftFlickPreference: Boolean? = true
    private var isDeleteUpFlickPreference: Boolean? = false
    private var isDeleteDownFlickPreference: Boolean? = false

    @Volatile
    private var deleteKeyFlickTargetChars: Set<Char> = DEFAULT_DELETE_KEY_FLICK_TARGETS
    private var tenkeyHeightPreferenceValue: Int? = 280
    private var tenkeyWidthPreferenceValue: Int? = 100
    private var qwertyHeightPreferenceValue: Int? = 280
    private var qwertyWidthPreferenceValue: Int? = 100
    private var candidateViewHeightPreferenceValue: Int? = 60
    private var candidateViewHeightEmptyPreferenceValue: Int? = 60
    private var tenkeyPositionPreferenceValue: Boolean? = true
    private var tenkeyBottomMarginPreferenceValue: Int? = 0
    private var qwertyPositionPreferenceValue: Boolean? = true
    private var qwertyBottomMarginPreferenceValue: Int? = 0

    private var tenkeyHeightLandScapePreferenceValue: Int? = 280
    private var tenkeyWidthLandScapePreferenceValue: Int? = 100
    private var qwertyHeightLandScapePreferenceValue: Int? = 280
    private var qwertyWidthLandScapePreferenceValue: Int? = 100
    private var candidateViewLandScapeHeightPreferenceValue: Int? = 60
    private var candidateViewLandScapeHeightEmptyPreferenceValue: Int? = 60
    private var tenkeyLandScapePositionPreferenceValue: Boolean? = true
    private var tenkeyLandScapeBottomMarginPreferenceValue: Int? = 0
    private var qwertyLandScapePositionPreferenceValue: Boolean? = true
    private var qwertyLandScapeBottomMarginPreferenceValue: Int? = 0

    private var tenkeyStartMarginPreferenceValue: Int? = 0
    private var tenkeyEndMarginPreferenceValue: Int? = 0
    private var qwertyStartMarginPreferenceValue: Int? = 0
    private var qwertyEndMarginPreferenceValue: Int? = 0

    private var tenkeyLandScapeStartMarginPreferenceValue: Int? = 0
    private var tenkeyLandScapeEndMarginPreferenceValue: Int? = 0
    private var qwertyLandScapeStartMarginPreferenceValue: Int? = 0
    private var qwertyLandScapeEndMarginPreferenceValue: Int? = 0

    private var enableShowLastShownKeyboardInRestart: Boolean? = false
    private var lastSavedKeyboardPosition: Int? = 0
    private var tenkeyRestoreInputModeOnRestart: Boolean = false
    private var sumireRestoreInputModeOnRestart: Boolean = false
    private var tenkeyRestoreInputModeOnlyWithinTime: Boolean = false
    private var tenkeyRestoreInputModeTimeoutMinutes: Int = 5
    private var tenkeyLastInputModeSavedAtEpochMillis: Long = 0L
    private var sumireRestoreInputModeOnlyWithinTime: Boolean = false
    private var sumireRestoreInputModeTimeoutMinutes: Int = 5
    private var sumireLastInputModeSavedAtEpochMillis: Long = 0L
    private var tenkeyLastInputModePreference: String = "japanese"
    private var tenkeyLastInputModePresentationPreference: String = "native"
    private var tenkeyLastQwertyNumberReturnTargetPreference: String = "japanese"
    private var sumireLastInputModePreference: String = "japanese"
    private var sumireLastInputModePresentationPreference: String = "native"


    private var qwertyKeyVerticalMargin: Float? = 5.0f
    private var qwertyKeyHorizontalGap: Float? = 2.0f
    private var qwertyKeyIndentLarge: Float? = 23.0f
    private var qwertyKeyIndentSmall: Float? = 9.0f
    private var qwertyKeySideMargin: Float? = 4.0f
    private var qwertyKeyTextSize: Float? = 18.0f
    private var qwertySymbolKeymapTextSize: Float? = 9.0f
    private var qwertySpecialKeyTextSize: Float? = 12.0f
    private var qwertySpecialKeyIconSize: Float? = 18.0f

    private var keyboardSkinId = KeyboardSkinId.DEFAULT
    private var keyboardThemeMode: String? = "default"
    private var customThemeBgColor: Int? = Color.WHITE
    private var customThemeKeyColor: Int? = Color.LTGRAY
    private var customThemeSpecialKeyColor: Int? = Color.GRAY
    private var customThemeKeyTextColor: Int? = Color.BLACK
    private var customThemeSpecialKeyTextColor: Int? = Color.BLACK
    private var customThemeCandidateTextColor: Int? = Color.BLACK
    private var customThemeCandidateItemBgColor: Int? = Color.TRANSPARENT
    private var customThemeCandidateItemPressedBgColor: Int? = Color.WHITE
    private var customThemeCandidateEmptyPopupBgColor: Int? = Color.GRAY
    private var customThemeCandidateEmptyPopupTextColor: Int? = Color.BLACK
    private var customThemeShortcutIconColor: Int? = Color.BLACK

    private var liquidGlassThemePreference: Boolean? = false
    private var liquidGlassBlurRadiousPreference: Int? = 220
    private var liquidGlassKeyBlurRadiousPreference: Int? = 255

    private var keyboardTouchEffectTypePreference: String = KeyboardTouchEffectType.NONE
    private var keyboardTouchEffectQualityPreference: String = KeyboardTouchEffectQuality.HIGH
    private var keyboardTouchEffectColorModePreference: String = "random"
    private var keyboardTouchEffectPalettePreference: String = SprayPaintSettings.PALETTE_PAINT_SPLASH
    private var liquidInkDensityPreference: Int = 100
    private var auroraInkDensityPreference: Int = 100

    @ColorInt
    private var keyboardTouchEffectColorPreference: Int = Color.rgb(17, 17, 17)
    private var cinematicWaveColorModePreference: String =
        CinematicWaveSettings.COLOR_MODE_CINEMATIC_RANDOM

    @ColorInt
    private var cinematicWavePrimaryColorPreference: Int =
        CinematicWaveSettings.DEFAULT_PRIMARY_COLOR

    @ColorInt
    private var cinematicWaveSecondaryColorPreference: Int =
        CinematicWaveSettings.DEFAULT_SECONDARY_COLOR

    private var cinematicWaveSecondaryColorAutoPreference: Boolean = true
    private var cinematicWaveTypePreference: String =
        CinematicWaveSettings.WAVE_TYPE_AURORA_MEMBRANE
    private var cinematicWaveOpacityPercentPreference: Int = 46
    private var cinematicWaveIntensityPercentPreference: Int = 100
    private var cinematicWaveMotionPreference: String = CinematicWaveSettings.MOTION_ELEGANT
    private var cinematicWaveTouchResponsePreference: String =
        CinematicWaveSettings.TOUCH_RESPONSE_NORMAL
    private var cinematicWaveQualityPreference: String = CinematicWaveSettings.QUALITY_BALANCED

    private var customKeyBorderEnablePreference: Boolean? = false
    private var customKeyBorderEnableColor: Int? = Color.BLACK

    private var customComposingTextPreference: Boolean? = false

    private var inputCompositionBackgroundColor: Int? = "#440099CC".toColorInt()
    private var inputCompositionAfterBackgroundColor: Int? = "#770099CC".toColorInt()
    private var inputCompositionTextColor: Int? = Color.WHITE

    private var inputConversionBackgroundColor: Int? = "#55FF8800".toColorInt()
    private var inputConversionTextColor: Int? = Color.WHITE

    private var enableTypoCorrectionJapaneseFlickKeyboardPreference: Boolean? = false
    private var enableTypoCorrectionQwertyEnglishKeyboardPreference: Boolean? = false

    @Deprecated(
        message = "Use the new input key type management system instead. This field is kept only for backward compatibility."
    )
    private var sumireInputKeyType: String? = "flick-default"
    private var sumireInputKeyLayoutType: String? = "toggle"
    private var sumireInputStyle: String? = "default"
    private var candidateColumns: String? = "1"
    private var candidateColumnsLandscape: String? = "1"
    private var candidateViewHeight: String? = "2"
    private var candidateTabVisibility: Boolean? = false
    private var conversionBackend: ConversionBackend = ConversionBackend.LEGACY
    private var predictionConfig: PredictionConfig = PredictionConfig()
    @Volatile
    private var kanaKanjiConversionSession: KanaKanjiConversionSession? = null
    @Volatile
    private var lastKanaKanjiQueryRequest: KanaKanjiQueryRequest? = null
    /** Zenzai: 直前の入力・左文脈と、そのとき得た FIX 制約（読みが伸びる間は引き継ぐ）。 */
    @Volatile
    private var zenzaiCarry: ZenzaiConstraint? = null
    /** (文脈, 読み, ドラフト) -> zenz の判定。同じドラフトを二度推論しない。 */
    private val zenzaiVerdictCache = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > 64
    }
    private val candidateRequestTracker = CandidateRequestTracker()
    private val editorMutationRevision = EditorMutationRevision()
    private var symbolKeyboardFirstItem: SymbolMode? = SymbolMode.EMOJI
    private var userDictionaryPrefixMatchNumber: Int? = 2
    private var isTablet: Boolean? = false
    private var isNgWordEnable: Boolean? = false
    private var deleteKeyHighLight: Boolean? = true
    private var customKeyboardSuggestionPreference: Boolean? = true
    private var customDirectInputReplaceComposingPreference = false

    private var sumireEnglishQwertyPreference: Boolean? = false
    private var conversionCandidatesRomajiEnablePreference: Boolean? = false

    private var enableZenzRightContextPreference: Boolean? = false

    private var learnFirstCandidateDictionaryPreference: Boolean? = false
    private var learnDictionaryAllowMixedSymbolsNumbersPreference: Boolean = true
    private var enablePredictionSearchLearnDictionaryPreference: Boolean? = false
    private var learnPredictionPreference: Int? = 4
    private var userDictionaryPredictionCandidateLimit: Int = 4
    private var learnDictionaryPredictionCandidateLimit: Int = 4
    private val conversionLearningSession = ConversionLearningSession()
    private var learningPausedForSession: Boolean = false
    private var circularFlickWindowScale: Float? = 1.0f
    private var circularFlickDirectionCount: Int? = 4
    private var hierarchicalFlickModeSwitchAngleMargin: Int? = 20

    private var customKeyBorderWidth: Int? = 1

    private var qwertySwitchNumberKeyWithoutNumberPreference: Boolean? = false


    private var omissionSearchOffsetScorePreference: Int? = 1900
    private var enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference: Int? = 3000

    private val _ngWordsList = MutableStateFlow<List<NgWord>>(emptyList())
    private val ngWordsList: StateFlow<List<NgWord>> = _ngWordsList
    private var isPrivateMode = false
    private var incognitoModeDetectionPreference: Boolean = true
    private var showLearnedCandidatesInIncognitoPreference: Boolean = true

    private var keyboardContainer: FrameLayout? = null
    private var dockedCandidateContainerActive = false
    private var dockedCandidateHeightStabilized = false
    private var stabilizeCandidateStripHeightPreference = false

    private var isSpaceKeyLongPressed = false
    private var suppressSpaceConvertTapUntilUptimeMillis = 0L
    private val _selectMode = MutableStateFlow(false)
    private val selectMode: StateFlow<Boolean> = _selectMode

    private val _cursorMoveMode = MutableStateFlow(false)
    private val cursorMoveMode: StateFlow<Boolean> = _cursorMoveMode
    private var hasConvertedKatakana = false

    private val deletedBuffer = EditHistoryBuffer()
    private var activeDeleteHistoryBatch: DeleteHistoryBatch? = null

    private var keyboardOrder: List<KeyboardType> = emptyList()
    private var candidateTabOrder: List<CandidateTab> = emptyList()

    private var customLayouts: List<CustomKeyboardLayout> = emptyList()
    private var currentCustomKeyboardStableId: String? = null
    private var customKeyboardRenderJob: Job? = null
    private var numberKeyboardRenderJob: Job? = null

    private var currentNightMode: Int = 0

    private lateinit var lifecycleRegistry: LifecycleRegistry


    private val cachedSpaceDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_space_bar_24
        )
    }
    private val cachedLogoDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.language_24dp
        )
    }
    private val cachedKanaDrawable: Drawable?
        get() = KeyboardFontGlyphDrawable.create(
            applicationContext,
            com.kazumaproject.core.R.drawable.kana_small,
            KeyboardFontApplicator.processSnapshot,
        )
    private val cachedHenkanDrawable: Drawable?
        get() = KeyboardFontGlyphDrawable.create(
            applicationContext,
            com.kazumaproject.core.R.drawable.henkan,
            KeyboardFontApplicator.processSnapshot,
        )

    private val cachedNumberDrawable: Drawable?
        get() = KeyboardFontGlyphDrawable.create(
            applicationContext,
            com.kazumaproject.core.R.drawable.number_small,
            KeyboardFontApplicator.processSnapshot,
        )

    private val cachedArrowDropDownDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.outline_arrow_drop_down_24
        )
    }

    private val cachedArrowDropUpDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.outline_arrow_drop_up_24
        )
    }

    private val cachedArrowRightDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_arrow_right_alt_24
        )
    }

    private val cachedReturnDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_keyboard_return_24
        )
    }

    private val cachedTabDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.keyboard_tab_24px
        )
    }

    private val cachedCheckDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_check_24
        )
    }

    private val cachedSearchDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            applicationContext, com.kazumaproject.core.R.drawable.baseline_search_24
        )
    }

    private val cachedEnglishDrawable: Drawable?
        get() = KeyboardFontGlyphDrawable.create(
            applicationContext,
            com.kazumaproject.core.R.drawable.english_small,
            KeyboardFontApplicator.processSnapshot,
        )

    companion object {
        /** Learned following word (後続語) from the next-word table. */
        const val CANDIDATE_TYPE_NEXT_WORD: Byte = 53
        private const val CONTEXT_EMOJI_CANDIDATE_TYPE: Byte = 11
        /** zenz の文脈長。左文脈 40 字 + 読み 32 字 + 候補で十分収まる。 */
        private const val ZENZ_N_CTX = 256
        private const val NEXT_WORD_CANDIDATE_LIMIT = 6
        private const val LONG_DELAY_TIME = 64L
        private const val DEFAULT_DELAY_MS = 1000L
        private const val DEFAULT_LIVE_CONVERSION_APPLY_DELAY_MS = 120L
        private const val PAGE_SIZE: Int = 5
        /** Height of necookey's bottom (prediction) candidate row. */
        private const val NECOOKEY_PREDICTION_ROW_HEIGHT_DP = 44
        private const val ZENZ_LIVE_SLOT_EMPTY_TEXT = "..."
        private val ZENZ_LIVE_SLOT_TYPE = (33).toByte()
        private val ZENZ_LIVE_SLOT_TYPES = setOf(
            (33).toByte(),
            (36).toByte(),
            (37).toByte(),
            (38).toByte(),
            (39).toByte(),
            (40).toByte()
        )
        private val DEFAULT_DELETE_KEY_FLICK_TARGETS =
            DeleteKeyFlickDeleteTargetRepository.DEFAULT_TARGET_SYMBOLS.toSet()
        private val ALWAYS_DELETE_KEY_FLICK_BOUNDARIES = setOf(' ', '　', '\n')

        private val passwordTypes = setOf(
            InputTypeForIME.TextWebPassword,
            InputTypeForIME.TextPassword,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.TextVisiblePassword,
        )

        private val passwordTypesWithOutNumber = setOf(
            InputTypeForIME.TextWebPassword,
            InputTypeForIME.TextPassword,
            InputTypeForIME.TextVisiblePassword,
        )

        private val numberTypes = setOf(
            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
        )

    }

    private var currentHighlightIndex: Int = RecyclerView.NO_POSITION

    private var modeSwitchAwaitingCursorPosition = false

    private var dismissJob: Job? = null

    private var currentCustomKeyboardPosition = 0

    private var currentEnterKeyIndex: Int = 0 // 0:改行, 1:確定,
    private var currentDakutenKeyIndex: Int = 0 // 0:^_^, 1:゛゜
    private var currentSpaceKeyIndex: Int = 0 // 0: Space, 1: Convert
    private var currentKatakanaKeyIndex: Int = 0 // 0: SiwtchToNumber, 1: Katakana
    private var currentInputModeForSession: InputMode = InputMode.ModeJapanese
    private var currentQwertyRomajiModeForSession: Boolean = true

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var systemBottomInset = 0

    /**
     * Android 9 introduced the IME navigation-bar extension behavior that lets the
     * keyboard window draw into the navigation-bar area. Older releases keep the
     * legacy IME window bounds, so adding a navigation inset there would double-count
     * space that is already excluded from the window.
     */
    private val supportsNavbarExtension: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    private var suppressSuggestions: Boolean = false

    private var countToggleKatakana = 0

    private var hardKeyboardShiftPressd = false
    private var softwareQwertyShiftPressed = false
    /**
     * Software Caps Lock is kept separately from the one-shot/romaji Shift latch.
     * The latter intentionally lives until the current composition boundary, while
     * Caps Lock must survive committing that composition.
     */
    private var softwareQwertyCapsLockOn = false
    private val isRomajiShiftPressed: Boolean
        get() = hardKeyboardShiftPressd || softwareQwertyShiftPressed

    private var isDefaultRomajiHenkanMap = false

    private var bunsetusMultipleDetect = false

    private var lastCandidate: String? = ""

    private val lastLocalUpdatedInput = MutableStateFlow("")

    private var addUserDictionaryPopup: PopupWindow? = null

    private var filteredCandidateList: List<Candidate>? = emptyList()
    private var zenzRerankJob: Job? = null
    private var zenzRerankRequestToken: Long = 0L

    // necookey: two-row candidate bar + Zenzai (zenz verifies Sumire's draft, one inference per key).
    private val necookeyCandidateBarConfig = NecookeyCandidateBarConfig.DEFAULT
    private var necookeyZenzJob: Job? = null
    /** Bunsetsu analysis + paths of the latest two-row conversion; used to learn per bunsetsu. */
    /** Editor text left of the composition, captured for [necookeyLeftContextInput]. */
    @Volatile
    private var necookeyLeftContext: String = ""
    @Volatile
    private var necookeyLeftContextInput: String = ""
    /** Left context of the conversion the current learning session started with. */
    private var learningSessionLeftContext: String? = null
    /** Output of the last bunsetsu learned; the context of the next commit's first bunsetsu. */
    private var lastLearnedBunsetsuOutput: String? = null

    /** Reading the visible two-row top row was built for (it is not hidden while stale). */
    private var necookeyTopRowInput: String = ""
    /** Set only while a top-row candidate commit is being processed; see [recordCandidateLearning]. */
    private var learningCandidateListInput: String? = null
    @Volatile
    private var necookeyLearningAnalysis: BunsetsuAnalysis? = null
    @Volatile
    private var necookeyLearningSegmentsByString: Map<String, List<CandidateConversionSegment>> =
        emptyMap()
    private var necookeyPredictionAdapter: SuggestionAdapter? = null
    /** Input the bottom row currently belongs to; the row hides itself for any other input. */
    private var necookeyPredictionRowInput: String = ""
    private var necookeyPredictionRowCandidates: List<Candidate> = emptyList()
    /** (文脈, 読み, sumire 最良) -> (zenzai の結果（変更なしは null）, 引き継ぐ制約)。 */
    private val necookeyZenzOverrideCache =
        object : LinkedHashMap<String, Pair<Candidate?, ZenzaiConstraint?>>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, Pair<Candidate?, ZenzaiConstraint?>>?,
            ): Boolean = size > 32
        }
    @Volatile
    private var latestCandidateSegmentInput: String = ""
    @Volatile
    private var latestCandidateSegmentsByString:
        Map<String, List<CandidateConversionSegment>> = emptyMap()
    private var previousTenKeyQWERTYMode: TenKeyQWERTYMode? = null

    private var currentKeyboardOrder = 0

    private data class ImeItem(
        val id: String,                 // imeId (InputMethodInfo.getId())
        val packageName: String,
        val settingsActivity: String?,   // 例: "com.example.ime.SettingsActivity"
        val label: CharSequence
    )

    private sealed class RowItem {
        data class Internal(val type: KeyboardType, val title: String) : RowItem()
        data class External(val ime: ImeItem) : RowItem()
    }

    private enum class DeleteDirection {
        BeforeCursor,
        AfterCursor
    }

    private sealed interface EditHistoryEntry {
        val previewText: String

        data class DeleteCommittedText(
            val deletedText: String,
            val direction: DeleteDirection = DeleteDirection.BeforeCursor
        ) : EditHistoryEntry {
            override val previewText: String = deletedText
        }

        data class CompositionChange(
            val beforeInput: String,
            val beforeTail: String,
            val afterInput: String,
            val afterTail: String,
            override val previewText: String
        ) : EditHistoryEntry

        data class ReplaceCommittedText(
            val beforeText: String,
            val afterText: String
        ) : EditHistoryEntry {
            override val previewText: String = beforeText
        }

        data class MacroCommit(
            val beforeText: String,
            val prefix: String,
            val suffix: String,
        ) : EditHistoryEntry {
            override val previewText: String = beforeText
        }

    }

    private class EditHistoryBuffer {
        private val undoStack = ArrayDeque<EditHistoryEntry>()
        private val redoStack = ArrayDeque<EditHistoryEntry>()

        fun push(entry: EditHistoryEntry) {
            undoStack.addLast(entry)
            redoStack.clear()
        }

        fun popUndo(): EditHistoryEntry? {
            return if (undoStack.isEmpty()) null else undoStack.removeLast()
        }

        fun popRedo(): EditHistoryEntry? {
            return if (redoStack.isEmpty()) null else redoStack.removeLast()
        }

        fun pushRedo(entry: EditHistoryEntry) {
            redoStack.addLast(entry)
        }

        fun pushUndoFromRedo(entry: EditHistoryEntry) {
            undoStack.addLast(entry)
        }

        fun clear() {
            undoStack.clear()
            redoStack.clear()
        }

        fun isEmpty(): Boolean = undoStack.isEmpty() && redoStack.isEmpty()

        fun isNotEmpty(): Boolean = !isEmpty()

        fun hasUndoHistory(): Boolean = undoStack.isNotEmpty()

        fun hasRedoHistory(): Boolean = redoStack.isNotEmpty()

        fun peekUndoPreviewText(): String = undoStack.peekLast()?.previewText.orEmpty()

        fun peekRedoPreviewText(): String = redoStack.peekLast()?.previewText.orEmpty()
    }

    private data class DeleteHistoryBatch(
        val initialInput: String,
        val initialTail: String,
        val deletesCommittedText: Boolean,
        val deletedText: StringBuilder = StringBuilder()
    )

    // 設定値を保持するためのデータクラス
    private data class KeyboardSizePreferences(
        val heightPref: Int,
        val widthPref: Int,
        val bottomMargin: Int,
        val positionIsEnd: Boolean, // true: End, false: Start
        val candidateHeight: Int,
        val candidateEmptyHeight: Int,
        val qwertyHeightPref: Int,
        val qwertyWidthPref: Int,
        val qwertyBottomMargin: Int,
        val qwertyPositionIsEnd: Boolean,
        val keyboardMarginStart: Int,
        val keyboardMarginEnd: Int,
        val qwertyMarginStart: Int,
        val qwertyMarginEnd: Int,
    )


    private data class KeyboardSurface(
        val rootView: View,
        val customLayout: FlickKeyboardView?,
        val suggestionRecyclerView: RecyclerView?,
        val symbolKeyboard: CustomSymbolKeyboardView?
    )

    private fun startKanaKanjiEngineLoad() {
        if (kanaKanjiEngineReady.isCompleted || kanaKanjiEngineLoadJob?.isActive == true) return
        kanaKanjiEngineLoadJob = ioScope.launch {
            val startedAt = System.nanoTime()
            try {
                val engine = kanaKanjiEngineProvider.get()
                withContext(Dispatchers.Main.immediate) {
                    kanaKanjiEngine = engine
                }
                kanaKanjiEngineReady.complete(engine)
                Timber.d(
                    "KanaKanjiEngine core dictionary load complete: " +
                        "elapsed_ms=${(System.nanoTime() - startedAt) / 1_000_000.0} " +
                        "thread=${Thread.currentThread().name}"
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Timber.e(throwable, "Failed to load core dictionaries asynchronously")
                kanaKanjiEngineReady.completeExceptionally(throwable)
            }
        }
    }

    private suspend fun awaitKanaKanjiEngineOrNull(): KanaKanjiEngine? = try {
        kanaKanjiEngineReady.await()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Timber.e(throwable, "KanaKanjiEngine is unavailable")
        null
    }

    private fun activateKanaKanjiEngineWhenReady(
        preferences: ImePreferencesSnapshot,
        inputSessionId: Long,
    ) {
        kanaKanjiEngineActivationJob?.cancel()
        kanaKanjiEngineActivationJob = scope.launch {
            if (awaitKanaKanjiEngineOrNull() == null) return@launch
            if (inputSessionId != flickPreviewEditorSessionId) return@launch
            if (kanaKanjiConversionSession == null) {
                startKanaKanjiConversionSession(preferences.conversionBackend)
            }
            initializeMozcDictionaries(preferences)
            refreshCandidateStripContent()
        }
    }

    override fun onCreate() {
        super.onCreate()
        localFontScope.launch {
            runCatching { localFontRepository.loadIfNeeded() }
                .onFailure { Timber.w(it, "Unable to restore the saved local keyboard font") }
            localFontRepository.state.collect { state -> applyLocalKeyboardFont(state.snapshot) }
        }
        window.window?.let { imeWindow ->
            if (supportsNavbarExtension) {
                WindowCompat.setDecorFitsSystemWindows(imeWindow, false)
            }
        }
        Timber.d("onCreate")
        registerCrossWindowBlurListener()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlineAutofillController = InlineAutofillController(
                context = this,
                onViewsChanged = ::renderInlineSuggestionViews,
                maximumContentWidth = { null },
            )
        }
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        runtimeInputSharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        runtimeInputSharedPreferences.registerOnSharedPreferenceChangeListener(
            runtimeInputPreferenceListener
        )
        runtimeInputPreferenceListenerRegistered = true
        syncRuntimeInputPreferences()
        startKanaKanjiEngineLoad()

        observeDeleteKeyFlickTargets()
        observeSumireSpecialKeyOverrides()

        suggestionAdapter = SuggestionAdapter().apply {
            onListUpdated = {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    updateMainCandidateStripAfterListUpdated()
                } else {
                    mainHandler.post {
                        updateMainCandidateStripAfterListUpdated()
                    }
                }
            }
            onStartAnchoredContentCommitted = {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    anchorActiveSuggestionStripStartForLeadingContent()
                } else {
                    mainHandler.post {
                        anchorActiveSuggestionStripStartForLeadingContent()
                    }
                }
            }
        }
        suggestionAdapterFull = SuggestionAdapter()
        necookeyPredictionAdapter = SuggestionAdapter()
        shortcutAdapter = ShortcutAdapter()
        keyboardLayoutEditController = KeyboardLayoutEditController(this)
        currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        clipboardManager =
            applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        isClipboardHistoryFeatureEnabled = appPreference.clipboard_history_enable ?: false
        cleanupExpiredClipboardItemsIfNeeded()

        ioScope.launch {
            val initialCustomLayouts = keyboardRepository.getLayoutsNotFlowEnsuringStableIds()
            withContext(Dispatchers.Main) {
                customLayouts = initialCustomLayouts
                currentCustomKeyboardStableId = customLayouts
                    .getOrNull(currentCustomKeyboardPosition)
                    ?.stableId
                    ?.takeIf { it.isNotBlank() }
            }
            shortCurRepository.initDefaultShortcutsIfNeeded()
        }

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        setSuggestionProgressVisible(
                            reason = SuggestionProgressReason.VoiceInput,
                            visible = true
                        )
                    }

                    override fun onBeginningOfSpeech() {

                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        setSuggestionProgressVisible(
                            reason = SuggestionProgressReason.VoiceInput,
                            visible = false
                        )
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        setSuggestionProgressVisible(
                            reason = SuggestionProgressReason.VoiceInput,
                            visible = false
                        )
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches =
                            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: return
                        _inputString.update { text }
                        setSuggestionProgressVisible(
                            reason = SuggestionProgressReason.VoiceInput,
                            visible = false
                        )
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches =
                            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: return
                        _inputString.update { text }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun observeDeleteKeyFlickTargets() {
        ioScope.launch {
            deleteKeyFlickDeleteTargetRepository.ensureDefaultTargets()
            deleteKeyFlickDeleteTargetRepository.observeAll().collect { targets ->
                deleteKeyFlickTargetChars = targets.mapNotNull { target ->
                    target.symbol.singleOrNull()
                }.toSet()
            }
        }
    }

    private fun observeSumireSpecialKeyOverrides() {
        ioScope.launch {
            combine(
                sumireSpecialKeyRepository.observeAllPlacementOverrides(),
                sumireSpecialKeyRepository.observeAllActionOverrides()
            ) { placementOverrides, actionOverrides ->
                placementOverrides to actionOverrides
            }.collect { (placementOverrides, actionOverrides) ->
                sumireSpecialKeyPlacementOverrides = placementOverrides
                sumireSpecialKeyActionOverrides = actionOverrides
                withContext(Dispatchers.Main.immediate) {
                    if (qwertyMode.value == TenKeyQWERTYMode.Sumire) {
                        refreshActiveSumireLayoutIfNeeded()
                    } else {
                        renderCurrentKeyboardStateOnActiveSurface()
                    }
                }
            }
        }
    }

    private var dockedFullCandidateLayoutManager: RecyclerView.LayoutManager? = null

    private fun resolveCandidatePanelColors(): com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors {
        val context = mainLayoutBinding?.root?.context ?: this
        val custom = if (keyboardThemeMode == "custom") com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors(
            customThemeBgColor ?: Color.WHITE,
            customThemeCandidateItemBgColor ?: Color.TRANSPARENT,
            customThemeCandidateTextColor ?: Color.BLACK,
            customThemeCandidateItemPressedBgColor ?: context.getColor(com.kazumaproject.core.R.color.qwety_key_bg_color),
            customThemeSpecialKeyColor ?: Color.GRAY,
            customThemeSpecialKeyTextColor ?: Color.BLACK,
            customThemeShortcutIconColor ?: Color.BLACK,
        ) else null
        return com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CandidatePanelColors.resolve(
            context,
            KeyboardSkinRegistry.find(keyboardSkinId)?.palette,
            custom,
            cupertinoClassic = keyboardSkinId == KeyboardSkinId.CUPERTINO_CLASSIC,
        )
    }

    private var dictionaryInputConnection: InputConnection? = null
    private var dictionaryInputEditor: EditText? = null
    private var dictionaryEditorInfo: EditorInfo? = null
    private var dictionaryConfigurationChanging = false

    override fun getCurrentInputConnection(): InputConnection? =
        dictionaryInputConnection ?: super.getCurrentInputConnection()

    override fun getCurrentInputEditorInfo(): EditorInfo? =
        dictionaryEditorInfo ?: super.getCurrentInputEditorInfo()

    private fun switchDictionaryInputTarget(editor: EditText?) {
        if (ngWordRegistrationPopup != null && editor !in ngWordRegistrationEditors) {
            ngWordRegistrationPopup?.dismiss()
        }
        val targetChanged = dictionaryInputEditor !== editor
        if (targetChanged) {
            keyboardPopupRequests.invalidate()
            // Undo entries contain editor text and must never cross input targets.
            deletedBuffer.clear()
            activeDeleteHistoryBatch = null
        }
        forwardDeleteCoordinator.cancel()
        // Finish the old composition before changing targets; never transfer it to another field.
        flickInputPreviewCoordinator.cancel(restore = true)
        finishComposingText()
        candidateRequestTracker.invalidate()
        candidateRefreshCoordinator.invalidate()
        defaultInputFinalizeJob?.cancel()
        defaultInputFinalizeJob = null
        clearDirectCommitCompositionState("dictionary input target")
        dictionaryInputConnection?.closeConnection()
        dictionaryInputConnection = null
        dictionaryEditorInfo = null
        dictionaryInputEditor = editor
        if (editor != null) {
            val info = EditorInfo().apply { packageName = this@IMEService.packageName }
            dictionaryInputConnection = editor.onCreateInputConnection(info)
            dictionaryEditorInfo = info
        }
        editorMutationRevision.advance()
        // App selection may have changed while its callbacks were ignored. Let the
        // next extracted-text read establish it instead of reusing a stale range.
        forwardDeleteCoordinator.reset(editor?.selectionStart ?: -1, editor?.selectionEnd ?: -1)
        resetEditorSelectionSnapshot()
        resetCustomToggleState()
        clearZeroQueryAllState(refresh = false)
        currentInputType = getCurrentInputTypeForIME2(currentInputEditorInfo)
        suppressSuggestions = showCandidateInPasswordPreference == true && currentInputType.isPassword()
        if (targetChanged) {
            setCurrentInputModeForSession(defaultInputModeFor(currentInputType))
        }
        resetRuntimeInputBehaviorForCurrentInput()
        if (targetChanged) refreshEditHistoryUi()
    }

    private fun onDictionaryEditorSelectionChanged(editor: EditText, start: Int, end: Int) {
        if (dictionaryInputEditor !== editor) return
        horizontalCursorSelectionRevision.incrementAndGet()
        forwardDeleteCoordinator.onSelectionChanged(start, end)
    }

    override fun onCreateInputView(): View? {
        Timber.d("onCreateInputView")
        // もしコンテナがすでに存在している場合、システムが再追加できるように
        // 古い親から切り離す。
        keyboardContainer?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }

        // もしコンテナがまだ一度も作成されていない場合（初回起動時）のみ、
        // 作成とセットアップを行う。
        if (keyboardContainer == null) {
            isTablet = resources.getBoolean(com.kazumaproject.core.R.bool.isTablet)
            keyboardContainer = FrameLayout(this)

            // コンテナの内部にキーボードのUIをセットアップする
            setupKeyboardView()
            // 初回のみ実行したい他のセットアップ処理

            mainLayoutBinding?.let { mainView ->
                if (lifecycle.currentState == Lifecycle.State.CREATED) {
                    startScope(mainView)
                } else {
                    scope.coroutineContext.cancelChildren()
                    startScope(mainView)
                }
            }
        } else {
            scope.coroutineContext.cancelChildren()
            mainLayoutBinding?.let { mainView ->
                rebindMainKeyboardInputListeners(mainView)
                scheduleMainKeyboardInputListenerRebind(mainView)
                startScope(mainView)
            }
        }
        return keyboardContainer
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (!inlineSuggestionEnabled) return null
        return InlineSuggestionsRequestFactory.create(this)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (!inlineSuggestionEnabled) return false
        return inlineAutofillController?.handleResponse(response) ?: false
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        dismissKeyboardSelectionPopups()
        super.onStartInput(attribute, restarting)
        resetCustomToggleState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlineAutofillController?.startInputSession()
        }
        forwardDeleteCoordinator.reset(attribute?.initialSelStart ?: -1, attribute?.initialSelEnd ?: -1)
        resetEditorSelectionSnapshot()
        textMacroExecutionRequestId.incrementAndGet()
        flickPreviewEditorSessionId += 1L
        flickInputPreviewCoordinator.resetForEditorSession()
        Timber.d("onStartInput: ${Build.MANUFACTURER}")
        Timber.d("onUpdate onStartInput called $restarting ${attribute?.imeOptions}")
        isTablet = resources.getBoolean(com.kazumaproject.core.R.bool.isTablet)
        clearZeroQueryAllState(refresh = false)
        resetAllFlags()
        shortcutToolbarHiddenForCandidates = false
        modeSwitchAwaitingCursorPosition = false
        _suggestionViewStatus.update { true }
        val preferences = ImePreferencesSnapshot.from(
            appPreference = appPreference,
            dictionarySourceResolver = dictionarySourceResolver,
            customThemeCandidateItemPressedBgColorDefault = ContextCompat.getColor(
                this,
                com.kazumaproject.core.R.color.qwety_key_bg_color
            )
        )
        applyImePreferences(preferences)
        conversionBackend = preferences.conversionBackend
        kanaKanjiConversionSession = null
        editorMutationRevision.restart()
        candidateRequestTracker.restart(
            backend = preferences.conversionBackend,
            editorMutationRevision = editorMutationRevision.current(),
        )
        candidateRefreshCoordinator.restart()
        activateKanaKanjiEngineWhenReady(
            preferences = preferences,
            inputSessionId = flickPreviewEditorSessionId,
        )
        applyCandidateAppearance()
        applySymbolKeyboardAppearance()
        resetKeyboard()
        refreshClipboardPreviewSnapshot()
        syncCustomKeyboardSuggestionPreference()
        refreshCandidateStripContent()
    }

    private fun startKanaKanjiConversionSession(backend: ConversionBackend) {
        conversionBackend = backend
        kanaKanjiConversionSession = KanaKanjiConversionSession(
            engine = kanaKanjiEngine,
            backend = backend,
        ).also { session ->
            if (BuildConfig.DEBUG && backend == ConversionBackend.INCREMENTAL_SESSION) {
                session.enablePerformanceProbe()
            }
        }
        candidateRequestTracker.restart(
            backend = backend,
            editorMutationRevision = editorMutationRevision.current(),
        )
        candidateRefreshCoordinator.restart()
    }

    private fun syncCustomKeyboardSuggestionPreference() {
        val latestPreference = appPreference.custom_keyboard_suggestion_preference ?: true
        customKeyboardSuggestionPreference = latestPreference
        suggestionAdapter?.updateCustomTabVisibility(latestPreference)
    }

    private fun syncNgramDictionaryPreferences() {
        SystemNgramRuntime.setEnabled(
            this,
            appPreference.system_ngram_dictionary_enable_preference,
        )
    }

    /**
     * Keeps settings that users expect to take effect immediately in sync with every
     * already-inflated keyboard surface. The same AppPreference keys used by the settings
     * screen are read here; no second preference store is involved.
     */
    private fun syncRuntimeInputPreferences() {
        assertMainThread("syncRuntimeInputPreferences")

        val readingTextSize = appPreference.live_conversion_candidate_yomi_size.toFloat()
        listOfNotNull(suggestionAdapter, suggestionAdapterFull)
            .forEach { it.setCandidateYomiTextSize(readingTextSize) }

        val previousStabilizeCandidateStripHeight = stabilizeCandidateStripHeightPreference
        stabilizeCandidateStripHeightPreference =
            appPreference.stabilize_candidate_strip_height_preference
        if (previousStabilizeCandidateStripHeight != stabilizeCandidateStripHeightPreference && isInputViewActive) {
            mainLayoutBinding?.let { updateKeyboardLayout(it) }
        }

        customDirectInputReplaceComposingPreference =
            appPreference.custom_direct_input_replace_composing_preference

        val previousInlineSuggestionEnabled = inlineSuggestionEnabled
        applyInlineSuggestionEnabled(appPreference.inline_suggestion_enabled_preference)
        if (previousInlineSuggestionEnabled != inlineSuggestionEnabled) {
            refreshShortcutAvailability()
        }


        val sensitivity = (appPreference.flick_sensitivity_preference ?: 100).coerceIn(1, 200)
        val thresholdShape = FlickThresholdShape.fromPreferenceValue(
            appPreference.flick_threshold_shape_preference
        )
        val diagonalMode = appPreference.tfbi_diagonal_recognition_mode_preference
        val longPressTimeout =
            (appPreference.long_press_timeout_preference ?: 300).coerceIn(100, 2000)
        val tfbiPopupPresentationMode = appPreference.flick_tfbi_popup_presentation
        val tfbiFlickStartPositionMode = appPreference.flick_tfbi_flick_start_position

        flickSensitivityPreferenceValue = sensitivity
        flickThresholdShapePreferenceValue = thresholdShape
        tfbiDiagonalRecognitionMode = diagonalMode
        longPressTimeoutPreferenceValue = longPressTimeout
        flickEditorPreviewPreference = appPreference.flick_editor_preview_preference
        flickEditorPreviewDelayMillis = appPreference.flick_editor_preview_delay_ms
        deleteLongPressConversionBehavior =
            DeleteLongPressConversionBehavior.fromPreferenceValue(
                appPreference.delete_long_press_conversion_behavior
            )
        tenkeyKeymapGuideSettings = ModeKeymapGuideSettings(
            japanese = appPreference.tenkey_keymap_guide_layout ?: false,
            english = appPreference.tenkey_keymap_guide_english,
            number = appPreference.tenkey_keymap_guide_number
        )
        val latestTenkeyUseThreeStateKeyboard =
            appPreference.tenkey_use_three_state_keyboard_preference
        val latestTenkeyNumberSymbolKeyGapDp =
            appPreference.tenkey_number_symbol_key_gap_preference
        if (
            latestTenkeyUseThreeStateKeyboard != tenkeyUseThreeStateKeyboard ||
            latestTenkeyNumberSymbolKeyGapDp != tenkeyNumberSymbolKeyGapDp
        ) {
            tenkeyUseThreeStateKeyboard = latestTenkeyUseThreeStateKeyboard
            tenkeyNumberSymbolKeyGapDp = latestTenkeyNumberSymbolKeyGapDp
        }
        sumireKeymapGuideSettings = ModeKeymapGuideSettings(
            japanese = appPreference.sumire_keymap_guide_japanese,
            english = appPreference.sumire_keymap_guide_english,
            number = appPreference.sumire_keymap_guide_number
        )
        customKeymapGuidePreference = appPreference.flick_keymap_guide_layout ?: false
        runtimeGestureSettingsSource.update(
            flickSensitivity = sensitivity,
            flickThresholdShape = thresholdShape,
            tfbiDiagonalRecognitionMode = diagonalMode,
            independentMultiTouchEnabled = appPreference.independent_multi_touch_preference,
            longPressTimeoutMillis = longPressTimeout.toLong()
        )

        mainLayoutBinding?.apply {
            applyCurrentFlickGuidePreference(customLayoutDefault)
            customLayoutDefault.setTfbiPopupPresentationMode(tfbiPopupPresentationMode)
            customLayoutDefault.setTfbiFlickStartPositionMode(tfbiFlickStartPositionMode)
        }
        applyIndependentMultiTouchPreference()
        syncCustomKeyboardHitTestPreference()
    }

    private fun applyIndependentMultiTouchPreference() {
    }

    private fun customKeyboardHitTestMode(): KeyHitTestMode =
        if (appPreference.custom_keyboard_input_in_empty_areas_preference) {
            KeyHitTestMode.NEAREST_KEY
        } else {
            KeyHitTestMode.NEAREST_KEY_IN_KEY_CELLS
        }

    private fun syncCustomKeyboardHitTestPreference() {
        val hitTestMode = customKeyboardHitTestMode()

        val isCustomLayoutActive = qwertyMode.value == TenKeyQWERTYMode.Custom ||
            (qwertyMode.value == TenKeyQWERTYMode.Number &&
                numberUsageCustomKeyboardLayoutOrNull() != null)
        if (isCustomLayoutActive) {
            getActiveKeyboardSurface()?.customLayout
                ?.takeIf { it.activeKeyHitTestMode != KeyHitTestMode.KEY_BOUNDS }
                ?.setKeyHitTestMode(hitTestMode)
        }
    }

    private fun applyImePreferences(savedPreferences: ImePreferencesSnapshot) {
        val preferences = savedPreferences.withKeyboardSkinAppearance()
        keyboardSkinId = preferences.keyboardSkin
        val deleteKeyFlickPreferencesChanged =
            isDeleteLeftFlickPreference != preferences.isDeleteLeftFlickPreference ||
                    isDeleteUpFlickPreference != preferences.isDeleteUpFlickPreference ||
                    isDeleteDownFlickPreference != preferences.isDeleteDownFlickPreference

        keyboardOrder = preferences.keyboardOrder
        candidateTabOrder = preferences.candidateTabOrder
        conversionBackend = preferences.conversionBackend
        predictionConfig = preferences.predictionConfig
        mozcUTPersonName = preferences.mozcUTPersonName
        mozcUTPlaces = preferences.mozcUTPlaces
        mozcUTWiki = preferences.mozcUTWiki
        mozcUTNeologd = preferences.mozcUTNeologd
        mozcUTWeb = preferences.mozcUTWeb
        isFlickOnlyMode = preferences.isFlickOnlyMode
        flickEditorPreviewPreference = preferences.flickEditorPreviewPreference
        flickEditorPreviewDelayMillis = preferences.flickEditorPreviewDelayMillis
        isOmissionSearchEnable = preferences.isOmissionSearchEnable
        delayTime = preferences.delayTime
        isLearnDictionaryMode = preferences.isLearnDictionaryMode
        incognitoModeDetectionPreference = preferences.incognitoModeDetectionPreference
        showLearnedCandidatesInIncognitoPreference =
            preferences.showLearnedCandidatesInIncognitoPreference
        isUserDictionaryEnable = preferences.isUserDictionaryEnable
        isUserTemplateEnable = preferences.isUserTemplateEnable
        isTextMacroCandidateEnable = preferences.isTextMacroCandidateEnable
        SystemNgramRuntime.setEnabled(this, preferences.systemNgramDictionaryEnabled)
        listOfNotNull(suggestionAdapter, suggestionAdapterFull).forEach { adapter ->
            adapter.setShowDictionaryCandidateLabels(preferences.showDictionaryCandidateLabels)
        }
        suppressHentaiganaCandidates = preferences.suppressHentaiganaCandidates
        zeroQuerySuggestionPreference = preferences.zeroQuerySuggestionPreference
        if (!zeroQuerySuggestionPreference) {
            clearZeroQueryAllState(refresh = true)
        }
        hankakuPreference = preferences.hankakuPreference
        customDirectModeSpaceHankakuPreference =
            preferences.customDirectModeSpaceHankakuPreference
        isLiveConversionEnable = preferences.isLiveConversionEnable
        liveConversionStartLength = preferences.liveConversionStartLength.coerceIn(1, 10)
        showLiveConversionCandidateYomi = preferences.showLiveConversionCandidateYomi
        val shouldShowLiveConversionCandidateYomi =
            preferences.isLiveConversionEnable && preferences.showLiveConversionCandidateYomi
        listOfNotNull(suggestionAdapter, suggestionAdapterFull).forEach { adapter ->
            adapter.setCandidateYomiMode(preferences.liveConversionCandidateYomiMode)
            adapter.setCandidateYomiTextSize(preferences.liveConversionCandidateYomiTextSize.toFloat())
            adapter.setShowCandidateYomiForLiveConversion(shouldShowLiveConversionCandidateYomi)
        }
        nBest = preferences.nBest
        conversionBeamWidth = preferences.conversionBeamWidth
        flickSensitivityPreferenceValue = preferences.flickSensitivityPreferenceValue
        flickThresholdShapePreferenceValue = FlickThresholdShape.fromPreferenceValue(
            preferences.flickThresholdShapePreferenceValue
        )
        tfbiDiagonalRecognitionMode = TfbiDiagonalRecognitionMode.fromPreferenceValue(
            preferences.tfbiDiagonalRecognitionModePreferenceValue
        )
        longPressTimeoutPreferenceValue = preferences.longPressTimeoutPreferenceValue
        qwertyShowIMEButtonPreference = preferences.qwertyShowIMEButtonPreference
        qwertyShowEmojiButtonPreference = preferences.qwertyShowEmojiButtonPreference
        tenkeyShowIMEButtonPreference = preferences.tenkeyShowIMEButtonPreference
        qwertyShowCursorButtonsPreference = preferences.qwertyShowCursorButtonsPreference
        qwertyShowNumberButtonsPreference = preferences.qwertyShowNumberButtonsPreference
        qwertyShowSwitchRomajiEnglishPreference =
            preferences.qwertyShowSwitchRomajiEnglishPreference
        qwertyEnglishDirectInputPreference = preferences.qwertyEnglishDirectInputPreference
        qwertyShowPopupWindowPreference = preferences.qwertyShowPopupWindowPreference
        tenkeySpaceFlickPreference = preferences.tenkeySpaceFlickPreference
        qwertyRomajiSpaceFlickPreference = preferences.qwertyRomajiSpaceFlickPreference
        qwertyEnglishSpaceFlickPreference = preferences.qwertyEnglishSpaceFlickPreference
        qwertyEnableFlickUpPreference = preferences.qwertyEnableFlickUpPreference
        qwertyEnableFlickDownPreference = preferences.qwertyEnableFlickDownPreference
        qwertyNumberKeyFlickUpChars = preferences.qwertyNumberKeyFlickUpChars
        qwertyNumberKeyFlickDownChars = preferences.qwertyNumberKeyFlickDownChars
        qwertyEnableZenkakuSpacePreference = preferences.qwertyEnableZenkakuSpacePreference
        qwertyRomajiHankakuNumberPreference = preferences.qwertyRomajiHankakuNumberPreference
        qwertyRomajiHankakuSymbolPreference = preferences.qwertyRomajiHankakuSymbolPreference
        qwertyShowKutoutenButtonsPreference = preferences.qwertyShowKutoutenButtonsPreference
        showCandidateInPasswordPreference = preferences.showCandidateInPasswordPreference
        applyInlineSuggestionEnabled(preferences.inlineSuggestionEnabled)
        qwertyShowKeymapSymbolsPreference = preferences.qwertyShowKeymapSymbolsPreference
        qwertyRomajiShiftConversionPreference = preferences.qwertyRomajiShiftConversionPreference
        isNgWordEnable = preferences.isNgWordEnable
        deleteKeyHighLight = preferences.deleteKeyHighLight
        customKeyboardSuggestionPreference = preferences.customKeyboardSuggestionPreference
        customDirectInputReplaceComposingPreference =
            preferences.customDirectInputReplaceComposingPreference
        userDictionaryPrefixMatchNumber = preferences.userDictionaryPrefixMatchNumber
        sumireInputKeyType = preferences.sumireInputKeyType
        sumireInputKeyLayoutType = preferences.sumireInputKeyLayoutType
        sumireInputStyle = preferences.sumireInputStyle
        candidateColumns = preferences.candidateColumns
        candidateColumnsLandscape = preferences.candidateColumnsLandscape
        candidateTabVisibility = preferences.candidateTabVisibility
        stabilizeCandidateStripHeightPreference = preferences.stabilizeCandidateStripHeightPreference
        symbolKeyboardFirstItem = preferences.symbolKeyboardFirstItem
        defaultEmojiSkinTonePreference = preferences.defaultEmojiSkinTone
        tenkeyQWERTYSwitchNumber = preferences.tenkeyQWERTYSwitchNumber
        tenkeyUseThreeStateKeyboard = preferences.tenkeyUseThreeStateKeyboard
        tenkeyNumberSymbolKeyGapDp = preferences.tenkeyNumberSymbolKeyGapDp
        tenkeySwitchNumberToQwertyNumberPreference =
            preferences.tenkeySwitchNumberToQwertyNumberPreference
        tenkeyRestoreInputModeOnRestart =
            preferences.tenkeyRestoreInputModeOnRestart
        sumireRestoreInputModeOnRestart =
            preferences.sumireRestoreInputModeOnRestart
        tenkeyRestoreInputModeOnlyWithinTime =
            preferences.tenkeyRestoreInputModeOnlyWithinTime
        tenkeyRestoreInputModeTimeoutMinutes =
            preferences.tenkeyRestoreInputModeTimeoutMinutes
        tenkeyLastInputModeSavedAtEpochMillis =
            preferences.tenkeyLastInputModeSavedAtEpochMillis
        sumireRestoreInputModeOnlyWithinTime =
            preferences.sumireRestoreInputModeOnlyWithinTime
        sumireRestoreInputModeTimeoutMinutes =
            preferences.sumireRestoreInputModeTimeoutMinutes
        sumireLastInputModeSavedAtEpochMillis =
            preferences.sumireLastInputModeSavedAtEpochMillis
        tenkeyLastInputModePreference =
            preferences.tenkeyLastInputModePreference
        tenkeyLastInputModePresentationPreference =
            preferences.tenkeyLastInputModePresentationPreference
        tenkeyLastQwertyNumberReturnTargetPreference =
            preferences.tenkeyLastQwertyNumberReturnTargetPreference
        sumireLastInputModePreference =
            preferences.sumireLastInputModePreference
        sumireLastInputModePresentationPreference =
            preferences.sumireLastInputModePresentationPreference
        tabletTenkeyQwertySwitchEnglish = preferences.tabletTenkeyQwertySwitchEnglish
        tenkeyKeymapGuideSettings = ModeKeymapGuideSettings(
            japanese = preferences.tenkeyJapaneseKeymapGuide,
            english = preferences.tenkeyEnglishKeymapGuide,
            number = preferences.tenkeyNumberKeymapGuide
        )
        sumireKeymapGuideSettings = ModeKeymapGuideSettings(
            japanese = preferences.sumireJapaneseKeymapGuide,
            english = preferences.sumireEnglishKeymapGuide,
            number = preferences.sumireNumberKeymapGuide
        )
        customKeymapGuidePreference = preferences.customKeymapGuide
        flickGuideTextSizeSpPreference = preferences.flickGuideTextSizeSp
        flickGuideMaxCharactersPreference = preferences.flickGuideMaxCharacters
        isKeyboardRounded = preferences.isKeyboardRounded
        keyboardCornerRadiusDp = preferences.keyboardCornerRadiusDp.coerceIn(0, 64)
        keyboardCornerTopLeft = preferences.keyboardCornerTopLeft
        keyboardCornerTopRight = preferences.keyboardCornerTopRight
        keyboardCornerBottomLeft = preferences.keyboardCornerBottomLeft
        keyboardCornerBottomRight = preferences.keyboardCornerBottomRight
        reconversionEnabledPreference = preferences.reconversionEnabled
        conversionKeySwipePreference = preferences.conversionKeySwipePreference
        switchQWERTYPassword = preferences.switchQWERTYPassword
        landscapeForceQwertyPreference = preferences.landscapeForceQwertyPreference
        landscapeForceQwertyRomajiPreference = preferences.landscapeForceQwertyRomajiPreference
        shortcutTollbarVisibility = preferences.shortcutTollbarVisibility
        shortcutToolbarIntegratedInSuggestion = preferences.shortcutToolbarIntegratedInSuggestion
        shortcutToolbarHeightDp = preferences.shortcutToolbarHeightDp
        shortcutToolbarIconSizeDp = preferences.shortcutToolbarIconSizeDp
        mainLayoutBinding?.let {
            applyShortcutToolbarSize(
                mainView = it,
                forceLayout = true
            )
        }
        isDeleteLeftFlickPreference = preferences.isDeleteLeftFlickPreference
        isDeleteUpFlickPreference = preferences.isDeleteUpFlickPreference
        isDeleteDownFlickPreference = preferences.isDeleteDownFlickPreference
        if (deleteKeyFlickPreferencesChanged) {
            refreshDeleteKeyFlickPreferenceLayouts()
        }
        clipboardPreviewVisibility = preferences.clipboardPreviewVisibility
        clipboardPreviewTapToDelete = preferences.clipboardPreviewTapToDelete
        tenkeyHeightPreferenceValue = preferences.tenkeyHeightPreferenceValue
        tenkeyWidthPreferenceValue = preferences.tenkeyWidthPreferenceValue
        qwertyHeightPreferenceValue = preferences.qwertyHeightPreferenceValue
        qwertyWidthPreferenceValue = preferences.qwertyWidthPreferenceValue
        candidateViewHeightPreferenceValue = preferences.candidateViewHeightPreferenceValue
        candidateViewHeightEmptyPreferenceValue =
            preferences.candidateViewHeightEmptyPreferenceValue
        tenkeyPositionPreferenceValue = preferences.tenkeyPositionPreferenceValue
        tenkeyBottomMarginPreferenceValue = preferences.tenkeyBottomMarginPreferenceValue
        qwertyPositionPreferenceValue = preferences.qwertyPositionPreferenceValue
        qwertyBottomMarginPreferenceValue = preferences.qwertyBottomMarginPreferenceValue
        tenkeyStartMarginPreferenceValue = preferences.tenkeyStartMarginPreferenceValue
        tenkeyEndMarginPreferenceValue = preferences.tenkeyEndMarginPreferenceValue
        qwertyStartMarginPreferenceValue = preferences.qwertyStartMarginPreferenceValue
        qwertyEndMarginPreferenceValue = preferences.qwertyEndMarginPreferenceValue
        tenkeyLandScapeStartMarginPreferenceValue =
            preferences.tenkeyLandscapeStartMarginPreferenceValue
        tenkeyLandScapeEndMarginPreferenceValue =
            preferences.tenkeyLandscapeEndMarginPreferenceValue
        qwertyLandScapeStartMarginPreferenceValue =
            preferences.qwertyLandscapeStartMarginPreferenceValue
        qwertyLandScapeEndMarginPreferenceValue =
            preferences.qwertyLandscapeEndMarginPreferenceValue
        enableShowLastShownKeyboardInRestart =
            preferences.enableShowLastShownKeyboardInRestart
        lastSavedKeyboardPosition = preferences.lastSavedKeyboardPosition
        val keyboardSelection = if (preferences.enableShowLastShownKeyboardInRestart) {
            resolveKeyboardForDisplay(
                requestedType = null,
                savedPosition = preferences.lastSavedKeyboardPosition,
                source = "applyImePreferences.restoreLastShown",
                persistNormalizedPosition = true,
                applyOrientation = false
            )
        } else {
            resolveKeyboardForDisplay(
                requestedType = null,
                savedPosition = null,
                source = "applyImePreferences.firstKeyboard",
                applyOrientation = false
            )
        }
        currentKeyboardOrder = keyboardSelection.resolvedIndex ?: 0
        tenkeyHeightLandScapePreferenceValue = preferences.tenkeyHeightLandscapePreferenceValue
        tenkeyWidthLandScapePreferenceValue = preferences.tenkeyWidthLandscapePreferenceValue
        qwertyHeightLandScapePreferenceValue = preferences.qwertyHeightLandscapePreferenceValue
        qwertyWidthLandScapePreferenceValue = preferences.qwertyWidthLandscapePreferenceValue
        candidateViewLandScapeHeightPreferenceValue =
            preferences.candidateViewLandscapeHeightPreferenceValue
        candidateViewLandScapeHeightEmptyPreferenceValue =
            preferences.candidateViewLandscapeHeightEmptyPreferenceValue
        tenkeyLandScapePositionPreferenceValue =
            preferences.tenkeyLandscapePositionPreferenceValue
        tenkeyLandScapeBottomMarginPreferenceValue =
            preferences.tenkeyLandscapeBottomMarginPreferenceValue
        qwertyLandScapePositionPreferenceValue =
            preferences.qwertyLandscapePositionPreferenceValue
        qwertyLandScapeBottomMarginPreferenceValue =
            preferences.qwertyLandscapeBottomMarginPreferenceValue
        qwertyKeyVerticalMargin = preferences.qwertyKeyVerticalMargin
        qwertyKeyHorizontalGap = preferences.qwertyKeyHorizontalGap
        qwertyKeyIndentLarge = preferences.qwertyKeyIndentLarge
        qwertyKeyIndentSmall = preferences.qwertyKeyIndentSmall
        qwertyKeySideMargin = preferences.qwertyKeySideMargin
        qwertyKeyTextSize = preferences.qwertyKeyTextSize
        qwertySymbolKeymapTextSize = preferences.qwertySymbolKeymapTextSize
        qwertySpecialKeyTextSize = preferences.qwertySpecialKeyTextSize
        qwertySpecialKeyIconSize = preferences.qwertySpecialKeyIconSize
        keyboardThemeMode = preferences.keyboardThemeMode
        customThemeBgColor = preferences.customThemeBgColor
        customThemeKeyColor = preferences.customThemeKeyColor
        customThemeSpecialKeyColor = preferences.customThemeSpecialKeyColor
        customThemeKeyTextColor = preferences.customThemeKeyTextColor
        customThemeSpecialKeyTextColor = preferences.customThemeSpecialKeyTextColor
        customThemeCandidateTextColor = preferences.customThemeCandidateTextColor
        customThemeCandidateItemBgColor = preferences.customThemeCandidateItemBgColor
        customThemeCandidateItemPressedBgColor = preferences.customThemeCandidateItemPressedBgColor
        customThemeCandidateEmptyPopupBgColor =
            preferences.customThemeCandidateEmptyPopupBgColor
        customThemeCandidateEmptyPopupTextColor =
            preferences.customThemeCandidateEmptyPopupTextColor
        customThemeShortcutIconColor = preferences.customThemeShortcutIconColor
        liquidGlassThemePreference = preferences.liquidGlassThemePreference
        liquidGlassBlurRadiousPreference = preferences.liquidGlassBlurRadiousPreference
        liquidGlassKeyBlurRadiousPreference = preferences.liquidGlassKeyBlurRadiousPreference
        keyboardTouchEffectTypePreference =
            KeyboardTouchEffectType.normalize(preferences.keyboardTouchEffectTypePreference)
        keyboardTouchEffectQualityPreference =
            KeyboardTouchEffectQuality.normalize(preferences.keyboardTouchEffectQualityPreference)
        keyboardTouchEffectColorModePreference = preferences.keyboardTouchEffectColorModePreference
        keyboardTouchEffectColorPreference = preferences.keyboardTouchEffectColorPreference
        keyboardTouchEffectPalettePreference = preferences.keyboardTouchEffectPalettePreference
        liquidInkDensityPreference = preferences.liquidInkDensityPreference
        auroraInkDensityPreference = preferences.auroraInkDensityPreference
        cinematicWaveColorModePreference =
            CinematicWaveSettings.normalizeColorMode(preferences.cinematicWaveColorModePreference)
        cinematicWavePrimaryColorPreference = preferences.cinematicWavePrimaryColorPreference
        cinematicWaveSecondaryColorPreference = preferences.cinematicWaveSecondaryColorPreference
        cinematicWaveSecondaryColorAutoPreference =
            preferences.cinematicWaveSecondaryColorAutoPreference
        cinematicWaveTypePreference =
            CinematicWaveSettings.normalizeWaveType(preferences.cinematicWaveTypePreference)
        cinematicWaveOpacityPercentPreference = preferences.cinematicWaveOpacityPercentPreference
        cinematicWaveIntensityPercentPreference = preferences.cinematicWaveIntensityPercentPreference
        cinematicWaveMotionPreference =
            CinematicWaveSettings.normalizeMotion(preferences.cinematicWaveMotionPreference)
        cinematicWaveTouchResponsePreference =
            CinematicWaveSettings.normalizeTouchResponse(
                preferences.cinematicWaveTouchResponsePreference
            )
        cinematicWaveQualityPreference =
            CinematicWaveSettings.normalizeQuality(preferences.cinematicWaveQualityPreference)
        customKeyBorderEnablePreference = preferences.customKeyBorderEnablePreference
        customKeyBorderEnableColor = preferences.customKeyBorderEnableColor
        customComposingTextPreference = preferences.customComposingTextPreference
        inputCompositionBackgroundColor = preferences.inputCompositionBackgroundColor
        inputCompositionTextColor = preferences.inputCompositionTextColor
        inputConversionBackgroundColor = preferences.inputConversionBackgroundColor
        inputConversionTextColor = preferences.inputConversionTextColor
        inputCompositionAfterBackgroundColor =
            manipulateColor(preferences.inputCompositionBackgroundColor, 1.2f)
        sumireEnglishQwertyPreference = preferences.sumireEnglishQwertyPreference
        conversionCandidatesRomajiEnablePreference =
            preferences.conversionCandidatesRomajiEnablePreference
        enableZenzRightContextPreference = preferences.enableZenzRightContextPreference
        learnFirstCandidateDictionaryPreference =
            preferences.learnFirstCandidateDictionaryPreference
        learnDictionaryAllowMixedSymbolsNumbersPreference =
            preferences.learnDictionaryAllowMixedSymbolsNumbersPreference
        enablePredictionSearchLearnDictionaryPreference =
            preferences.enablePredictionSearchLearnDictionaryPreference
        learnPredictionPreference = preferences.learnPredictionPreference
        userDictionaryPredictionCandidateLimit =
            preferences.userDictionaryPredictionCandidateLimit.coerceIn(1, 8)
        learnDictionaryPredictionCandidateLimit =
            preferences.learnDictionaryPredictionCandidateLimit.coerceIn(1, 8)
        circularFlickWindowScale = preferences.circularFlickWindowScale
        circularFlickDirectionCount = preferences.circularFlickDirectionCount
        hierarchicalFlickModeSwitchAngleMargin = preferences.hierarchicalFlickModeSwitchAngleMargin
        customKeyBorderWidth = preferences.customKeyBorderWidth
        qwertySwitchNumberKeyWithoutNumberPreference =
            preferences.qwertySwitchNumberKeyWithoutNumberPreference
        omissionSearchOffsetScorePreference = preferences.omissionSearchOffsetScorePreference
        enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference =
            preferences.enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference
        enableTypoCorrectionJapaneseFlickKeyboardPreference =
            preferences.enableTypoCorrectionJapaneseFlickKeyboardPreference
        enableTypoCorrectionQwertyEnglishKeyboardPreference =
            preferences.enableTypoCorrectionQwertyEnglishKeyboardPreference
        refreshReconversionUi()
    }

    private fun initializeMozcDictionaries(@Suppress("UNUSED_PARAMETER") preferences: ImePreferencesSnapshot) {
        applyDictionaryOverrideRevisionIfNeeded()
        if (!kanaKanjiEngine.isSystemUserDictionaryInitialized() &&
            systemUserDictionaryLoadJob?.isActive != true
        ) {
            systemUserDictionaryLoadJob = ioScope.launch {
                runCatching {
                    kanaKanjiEngine.loadSystemUserDictionaryFromFiles(applicationContext)
                }.onFailure {
                    Timber.w(it, "Failed to load system user dictionary asynchronously")
                }
                withContext(Dispatchers.Main.immediate) {
                    if (isInputViewActive) {
                        requestCandidateRefresh(CandidateShowFlag.Updating)
                    }
                }
            }
        }
    }

    private suspend fun awaitSystemUserDictionaryLoad() {
        systemUserDictionaryLoadJob?.join()
    }

    private fun updateIncognitoModeState(editorInfo: EditorInfo?) {
        val detected = incognitoModeDetectionPreference &&
                editorInfo != null &&
                (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        isPrivateMode = detected
        candidateStripIncognitoVisible = detected
        candidateStripIncognitoIconDrawable = if (detected) {
            ContextCompat.getDrawable(this, com.kazumaproject.core.R.drawable.incognito)
        } else {
            null
        }
        suggestionAdapter?.setIncognitoIcon(candidateStripIncognitoIconDrawable)
        suggestionAdapterFull?.setIncognitoIcon(candidateStripIncognitoIconDrawable)
        refreshCandidateStripContent()
    }

    private fun learnedRepositoryForSuggestion(): LearnRepository? {
        if (isLearnDictionaryMode != true) return null
        if (isPrivateMode && !showLearnedCandidatesInIncognitoPreference) return null
        return learnRepository
    }

    private fun applyDictionaryOverrideRevisionIfNeeded() {
        val currentRevision = dictionaryOverrideStore.currentRevision
        val englishReadingEnabled =
            dictionarySourceResolver.resolveCategoryLoadState(DictionaryCategory.ENGLISH_READING) !=
                DictionaryCategoryLoadState.Disabled
        if (currentRevision == lastAppliedDictionaryOverrideRevision &&
            englishReadingEnabled == lastAppliedEnglishReadingEnabled
        ) return
        if (dictionaryOverrideApplyJob?.isActive == true) return

        // Asset opening and dictionary decoding must stay on IO. Only the lightweight candidate
        // refresh at the end of this job is dispatched back to the main thread.
        dictionaryOverrideApplyJob = ioScope.launch {
            val revisionToApply = dictionaryOverrideStore.currentRevision
            val englishReadingEnabledToApply =
                dictionarySourceResolver.resolveCategoryLoadState(
                    DictionaryCategory.ENGLISH_READING
                ) != DictionaryCategoryLoadState.Disabled
            val initialOptionalStateOnly =
                lastAppliedDictionaryOverrideRevision == Long.MIN_VALUE &&
                    !kanaKanjiEngine.isInitialOptionalDictionaryStateLoaded()
            val startedAt = System.nanoTime()
            val success = runCatching {
                if (initialOptionalStateOnly) {
                    kanaKanjiEngine.initializeOptionalDictionaryStateFromCurrentSources()
                } else {
                    kanaKanjiEngine.applyDictionaryOverrideState(applicationContext)
                    englishEngine.reloadDictionariesFromCurrentSources(dictionaryBinaryReader)
                }
            }.onFailure {
                Timber.w(it, "Failed to apply dictionary override revision $revisionToApply")
            }.isSuccess

            if (
                success &&
                dictionaryOverrideStore.currentRevision == revisionToApply &&
                (
                    dictionarySourceResolver.resolveCategoryLoadState(
                        DictionaryCategory.ENGLISH_READING
                    ) != DictionaryCategoryLoadState.Disabled
                    ) == englishReadingEnabledToApply
            ) {
                lastAppliedDictionaryOverrideRevision = revisionToApply
                lastAppliedEnglishReadingEnabled = englishReadingEnabledToApply
                if (initialOptionalStateOnly) {
                    Timber.d(
                        "Initial optional dictionary state loaded without core reload: " +
                            "elapsed_ms=${(System.nanoTime() - startedAt) / 1_000_000.0}"
                    )
                }
            }

            if (success) {
                withContext(Dispatchers.Main.immediate) {
                    if (isInputViewActive) {
                        requestCandidateRefresh(CandidateShowFlag.Updating)
                    }
                }
            }
        }
    }

    private fun applyKeyboardBackgroundIfNeeded(
        mainView: MainLayoutBinding,
        skipForFloatingMode: Boolean = true
    ) {
    }

    private fun setupSuminagashiInkEffect(mainView: MainLayoutBinding) {
        setupKeyboardTouchEffect(mainView)
    }

    private fun clearAndPauseSuminagashiInkEffects() {
        clearAndPauseKeyboardTouchEffects()
    }

    private fun releaseSuminagashiInkEffects() {
        releaseKeyboardTouchEffects()
    }

    private fun setupKeyboardTouchEffect(mainView: MainLayoutBinding) {
        refreshFluidInkDensityPreferences()
        setupMainKeyboardTouchEffect(mainView)
    }

    private fun refreshFluidInkDensityPreferences() {
        liquidInkDensityPreference =
            appPreference.keyboard_touch_effect_liquid_ink_density_preference
        auroraInkDensityPreference =
            appPreference.keyboard_touch_effect_aurora_ink_density_preference
    }

    private fun resolveFluidInkDensityPercent(effectType: String): Int {
        return when {
            KeyboardTouchEffectType.isLiquidInk(effectType) ->
                liquidInkDensityPreference.coerceIn(50, 300)

            KeyboardTouchEffectType.isAuroraInk(effectType) ->
                auroraInkDensityPreference.coerceIn(50, 300)

            else -> 100
        }
    }

    private fun clearAndPauseKeyboardTouchEffects() {
        mainLayoutBinding?.suminagashiInkView?.apply {
            clearInk()
            pauseInk()
        }
        mainLayoutBinding?.liquidRippleEffectView?.apply {
            clearRipple()
            pauseRipple()
        }
        mainLayoutBinding?.sprayPaintEffectView?.apply {
            clearSpray()
            pauseSpray()
        }
        mainLayoutBinding?.luminousBlobEffectView?.apply {
            clearBlob()
            pauseBlob()
        }
        mainLayoutBinding?.cinematicWaveEffectView?.apply {
            clearWave()
            pauseWave()
        }
    }

    private fun releaseKeyboardTouchEffects() {
        mainLayoutBinding?.suminagashiInkView?.releaseInk()
        mainLayoutBinding?.liquidRippleEffectView?.releaseRipple()
        mainLayoutBinding?.sprayPaintEffectView?.releaseSpray()
        mainLayoutBinding?.luminousBlobEffectView?.releaseBlob()
        mainLayoutBinding?.cinematicWaveEffectView?.releaseWave()
    }

    private fun setupMainKeyboardTouchEffect(mainView: MainLayoutBinding) {
        val effectType = KeyboardTouchEffectType.normalize(keyboardTouchEffectTypePreference)
        val mainSurfaceActive = true
        val liquidInkEnabled =
            mainSurfaceActive && KeyboardTouchEffectType.isLiquidInk(effectType)
        val auroraInkEnabled =
            mainSurfaceActive && KeyboardTouchEffectType.isAuroraInk(effectType)
        val inkEnabled = liquidInkEnabled || auroraInkEnabled
        val inkTransportMode = if (auroraInkEnabled) {
            FluidInkTransportMode.WATER_DRIFT
        } else {
            FluidInkTransportMode.PHYSICAL
        }
        val inkDensityPercent = resolveFluidInkDensityPercent(effectType)
        val liquidRippleEnabled =
            mainSurfaceActive && KeyboardTouchEffectType.isLiquidRipple(effectType)
        val sprayPaintEnabled =
            mainSurfaceActive && KeyboardTouchEffectType.isSprayPaint(effectType)
        val luminousBlobEnabled =
            mainSurfaceActive && KeyboardTouchEffectType.isLuminousBlob(effectType)
        val cinematicWaveEnabled =
            mainSurfaceActive && KeyboardTouchEffectType.isCinematicWave(effectType)
        val effectBaseColor = resolveKeyboardTouchEffectBaseColor(mainView.root)

        mainView.suminagashiInkView.configure(
            enabled = inkEnabled,
            colorMode = keyboardTouchEffectColorModePreference,
            fixedColor = keyboardTouchEffectColorPreference,
            quality = keyboardTouchEffectQualityPreference,
            transportMode = inkTransportMode,
            densityPercent = inkDensityPercent
        )
        mainView.liquidRippleEffectView.configure(
            enabled = liquidRippleEnabled,
            quality = keyboardTouchEffectQualityPreference
        )
        mainView.sprayPaintEffectView.configure(
            enabled = sprayPaintEnabled,
            colorMode = keyboardTouchEffectColorModePreference,
            fixedColor = keyboardTouchEffectColorPreference,
            palette = keyboardTouchEffectPalettePreference,
            quality = keyboardTouchEffectQualityPreference
        )
        mainView.luminousBlobEffectView.configure(
            enabled = luminousBlobEnabled,
            colorMode = keyboardTouchEffectColorModePreference,
            fixedColor = effectBaseColor,
            quality = keyboardTouchEffectQualityPreference
        )
        mainView.cinematicWaveEffectView.configure(
            enabled = cinematicWaveEnabled,
            colorMode = cinematicWaveColorModePreference,
            primaryColor = cinematicWavePrimaryColorPreference,
            secondaryColor = cinematicWaveSecondaryColorPreference,
            secondaryColorAuto = cinematicWaveSecondaryColorAutoPreference,
            waveType = cinematicWaveTypePreference,
            opacityPercent = cinematicWaveOpacityPercentPreference,
            intensityPercent = cinematicWaveIntensityPercentPreference,
            motion = cinematicWaveMotionPreference,
            touchResponse = cinematicWaveTouchResponsePreference,
            quality = cinematicWaveQualityPreference
        )

        val root = mainView.root as? InkTouchDispatchFrameLayout
        root?.touchEffectMotionEventListener = when {
            inkEnabled -> {
                { event ->
                    dispatchInkMotionEvent(
                        event = event,
                        sourceRoot = mainView.root,
                        targetContainer = mainView.keyboardTouchEffectContainer,
                        inkView = mainView.suminagashiInkView
                    )
                }
            }

            liquidRippleEnabled -> {
                { event ->
                    dispatchLiquidRippleMotionEvent(
                        event = event,
                        sourceRoot = mainView.root,
                        targetContainer = mainView.keyboardTouchEffectContainer,
                        rippleView = mainView.liquidRippleEffectView
                    )
                }
            }

            sprayPaintEnabled -> {
                { event ->
                    dispatchSprayPaintMotionEvent(
                        event = event,
                        sourceRoot = mainView.root,
                        targetContainer = mainView.keyboardTouchEffectContainer,
                        sprayView = mainView.sprayPaintEffectView
                    )
                }
            }

            luminousBlobEnabled -> {
                { event ->
                    dispatchLuminousBlobMotionEvent(
                        event = event,
                        sourceRoot = mainView.root,
                        targetContainer = mainView.luminousBlobEffectView,
                        blobView = mainView.luminousBlobEffectView
                    )
                }
            }

            cinematicWaveEnabled -> {
                { event ->
                    dispatchCinematicWaveMotionEvent(
                        event = event,
                        sourceRoot = mainView.root,
                        targetContainer = mainView.keyboardTouchEffectContainer,
                        waveView = mainView.cinematicWaveEffectView
                    )
                }
            }

            else -> null
        }
    }

    @ColorInt
    private fun resolveKeyboardTouchEffectBaseColor(host: View): Int {
        if (keyboardTouchEffectColorModePreference != LuminousBlobSettings.COLOR_MODE_THEME) {
            return keyboardTouchEffectColorPreference
        }
        val fallbackColor = LuminousBlobSettings.DEFAULT_BASE_COLOR
        return when (keyboardThemeMode) {
            "custom" -> customThemeSpecialKeyColor ?: fallbackColor
            else -> host.context.getThemeColorOrFallback(
                attrRes = AppCompatR.attr.colorPrimary,
                fallbackColor = fallbackColor
            )
        }
    }

    private fun dispatchInkMotionEvent(
        event: MotionEvent,
        sourceRoot: View,
        targetContainer: View,
        inkView: SuminagashiInkView
    ) {
        dispatchTouchEffectMotionEvent(
            event = event,
            sourceRoot = sourceRoot,
            targetContainer = targetContainer,
            isEffectShown = { inkView.isShown },
            onPointerDown = { pointerId, x, y ->
                inkView.onPointerDown(pointerId = pointerId, x = x, y = y)
            },
            onPointerMove = { pointerId, x, y ->
                inkView.onPointerMove(pointerId = pointerId, x = x, y = y)
            },
            onPointerUp = { pointerId, x, y ->
                inkView.onPointerUp(pointerId = pointerId, x = x, y = y)
            },
            onPointerUpOutside = { pointerId ->
                inkView.onPointerUp(pointerId)
            },
            onCancel = { inkView.onCancel() }
        )
    }

    private fun dispatchLiquidRippleMotionEvent(
        event: MotionEvent,
        sourceRoot: View,
        targetContainer: View,
        rippleView: LiquidRippleEffectView
    ) {
        dispatchTouchEffectMotionEvent(
            event = event,
            sourceRoot = sourceRoot,
            targetContainer = targetContainer,
            isEffectShown = { rippleView.isShown },
            onPointerDown = { pointerId, x, y ->
                rippleView.onPointerDown(pointerId = pointerId, x = x, y = y)
            },
            onPointerMove = { pointerId, x, y ->
                rippleView.onPointerMove(pointerId = pointerId, x = x, y = y)
            },
            onPointerUp = { pointerId, x, y ->
                rippleView.onPointerUp(pointerId = pointerId, x = x, y = y)
            },
            onPointerUpOutside = { pointerId ->
                rippleView.onPointerUp(pointerId)
            },
            onCancel = { rippleView.onCancel() }
        )
    }

    private fun dispatchSprayPaintMotionEvent(
        event: MotionEvent,
        sourceRoot: View,
        targetContainer: View,
        sprayView: SprayPaintEffectView
    ) {
        dispatchTouchEffectMotionEvent(
            event = event,
            sourceRoot = sourceRoot,
            targetContainer = targetContainer,
            isEffectShown = { sprayView.isShown },
            onPointerDown = { pointerId, x, y ->
                sprayView.onPointerDown(pointerId = pointerId, x = x, y = y)
            },
            onPointerMove = { pointerId, x, y ->
                sprayView.onPointerMove(pointerId = pointerId, x = x, y = y)
            },
            onPointerUp = { pointerId, x, y ->
                sprayView.onPointerUp(pointerId = pointerId, x = x, y = y)
            },
            onPointerUpOutside = { pointerId ->
                sprayView.onPointerUp(pointerId)
            },
            onCancel = { sprayView.onCancel() }
        )
    }

    private fun dispatchLuminousBlobMotionEvent(
        event: MotionEvent,
        sourceRoot: View,
        targetContainer: View,
        blobView: LuminousBlobEffectView
    ) {
        dispatchTouchEffectMotionEvent(
            event = event,
            sourceRoot = sourceRoot,
            targetContainer = targetContainer,
            isEffectShown = { blobView.isShown },
            onPointerDown = { pointerId, x, y ->
                blobView.onPointerDown(pointerId = pointerId, x = x, y = y)
            },
            onPointerMove = { pointerId, x, y ->
                blobView.onPointerMove(pointerId = pointerId, x = x, y = y)
            },
            onPointerUp = { pointerId, x, y ->
                blobView.onPointerUp(pointerId = pointerId, x = x, y = y)
            },
            onPointerUpOutside = { pointerId ->
                blobView.onPointerUp(pointerId)
            },
            onCancel = { blobView.onCancel() }
        )
    }

    private fun dispatchCinematicWaveMotionEvent(
        event: MotionEvent,
        sourceRoot: View,
        targetContainer: View,
        waveView: CinematicWaveEffectView
    ) {
        if (!waveView.isShown) return

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                if (!mapMotionEventToTarget(event, index, sourceRoot, targetContainer, inkMappedPoint)) {
                    return
                }
                waveView.onPointerDown(
                    pointerId = event.getPointerId(index),
                    x = inkMappedPoint[0],
                    y = inkMappedPoint[1],
                    pressure = event.getPressure(index)
                )
            }

            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    if (!mapMotionEventToTarget(
                            event,
                            index,
                            sourceRoot,
                            targetContainer,
                            inkMappedPoint
                        )
                    ) {
                        continue
                    }
                    waveView.onPointerMove(
                        pointerId = event.getPointerId(index),
                        x = inkMappedPoint[0],
                        y = inkMappedPoint[1],
                        pressure = event.getPressure(index)
                    )
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                if (mapMotionEventToTarget(event, index, sourceRoot, targetContainer, inkMappedPoint)) {
                    waveView.onPointerUp(
                        pointerId = event.getPointerId(index),
                        x = inkMappedPoint[0],
                        y = inkMappedPoint[1],
                        pressure = event.getPressure(index)
                    )
                } else {
                    waveView.onPointerUp(event.getPointerId(index))
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                waveView.onCancel()
            }
        }
    }

    private fun dispatchTouchEffectMotionEvent(
        event: MotionEvent,
        sourceRoot: View,
        targetContainer: View,
        isEffectShown: () -> Boolean,
        onPointerDown: (pointerId: Int, x: Float, y: Float) -> Unit,
        onPointerMove: (pointerId: Int, x: Float, y: Float) -> Unit,
        onPointerUp: (pointerId: Int, x: Float, y: Float) -> Unit,
        onPointerUpOutside: (pointerId: Int) -> Unit,
        onCancel: () -> Unit
    ) {
        if (!isEffectShown()) return

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                if (!mapMotionEventToTarget(event, index, sourceRoot, targetContainer, inkMappedPoint)) {
                    return
                }
                onPointerDown(
                    event.getPointerId(index),
                    inkMappedPoint[0],
                    inkMappedPoint[1]
                )
            }

            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    if (!mapMotionEventToTarget(
                            event,
                            index,
                            sourceRoot,
                            targetContainer,
                            inkMappedPoint
                        )
                    ) {
                        continue
                    }
                    onPointerMove(
                        event.getPointerId(index),
                        inkMappedPoint[0],
                        inkMappedPoint[1]
                    )
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                if (mapMotionEventToTarget(event, index, sourceRoot, targetContainer, inkMappedPoint)) {
                    onPointerUp(
                        event.getPointerId(index),
                        inkMappedPoint[0],
                        inkMappedPoint[1]
                    )
                } else {
                    onPointerUpOutside(event.getPointerId(index))
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                onCancel()
            }
        }
    }

    private fun mapMotionEventToTarget(
        event: MotionEvent,
        pointerIndex: Int,
        sourceRoot: View,
        target: View,
        outPoint: FloatArray
    ): Boolean {
        if (target.width <= 0 || target.height <= 0) return false

        sourceRoot.getLocationInWindow(inkRootLocation)
        target.getLocationInWindow(inkTargetLocation)

        val x = event.getX(pointerIndex) + inkRootLocation[0] - inkTargetLocation[0]
        val y = event.getY(pointerIndex) + inkRootLocation[1] - inkTargetLocation[1]

        if (x < 0f || y < 0f || x > target.width || y > target.height) {
            return false
        }

        outPoint[0] = x
        outPoint[1] = y
        return true
    }

    private fun applyInlineSuggestionEnabled(enabled: Boolean) {
        if (inlineSuggestionEnabled == enabled) return
        inlineSuggestionEnabled = enabled
        if (!enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                inlineAutofillController?.clear()
            }
            currentInlineSuggestionViews = emptyList()
            inlineSuggestionDisplayState.updateAvailability(false)
            updateShortcutActiveStates()
            refreshCandidateStripContent()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun renderInlineSuggestionViews(views: List<InlineContentView>) {
        assertMainThread("renderInlineSuggestionViews")
        if (!inlineSuggestionEnabled) {
            currentInlineSuggestionViews = emptyList()
            inlineSuggestionDisplayState.updateAvailability(false)
            updateShortcutActiveStates()
            refreshCandidateStripContent()
            return
        }
        currentInlineSuggestionViews = views
        views.forEach { view ->
            view.setZOrderedOnTop(true)
            view.clipBounds = null
        }
        inlineSuggestionDisplayState.updateAvailability(views.isNotEmpty())
        updateShortcutActiveStates()
        Timber.d("Rendering ${views.size} inline suggestion views")
        refreshCandidateStripContent()
    }

    private fun toggleInlineSuggestionSurface() {
        if (!inlineSuggestionEnabled) return
        if (!inlineSuggestionDisplayState.toggleSurface()) return
        refreshCandidateStripContent()
        mainLayoutBinding?.suggestionRecyclerView?.scrollToPosition(0)
        updateShortcutActiveStates()
    }

    private fun updateLuminousBlobEffectBounds(
        blobView: LuminousBlobEffectView,
        heightPx: Int
    ) {
        if (heightPx <= 0) return
        val currentParams = blobView.layoutParams as? FrameLayout.LayoutParams
        val params = currentParams ?: FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            heightPx,
            Gravity.BOTTOM
        )
        var changed = currentParams == null

        if (params.width != ViewGroup.LayoutParams.MATCH_PARENT) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            changed = true
        }
        if (params.height != heightPx) {
            params.height = heightPx
            changed = true
        }
        if (params.gravity != Gravity.BOTTOM) {
            params.gravity = Gravity.BOTTOM
            changed = true
        }
        if (changed) {
            blobView.layoutParams = params
        }
    }

    private fun createKeyboardBackgroundDrawable(
        @ColorInt color: Int,
        radiusDp: Int,
        topLeft: Boolean,
        topRight: Boolean,
        bottomRight: Boolean,
        bottomLeft: Boolean
    ): GradientDrawable {
        val radiusPx = radiusDp * resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadii = floatArrayOf(
                if (topLeft) radiusPx else 0f,
                if (topLeft) radiusPx else 0f,
                if (topRight) radiusPx else 0f,
                if (topRight) radiusPx else 0f,
                if (bottomRight) radiusPx else 0f,
                if (bottomRight) radiusPx else 0f,
                if (bottomLeft) radiusPx else 0f,
                if (bottomLeft) radiusPx else 0f
            )
        }
    }

    private fun applyCandidateExpandedSurfaceBackground(
        view: View,
        transparentWithLiquidGlass: Boolean = false,
    ) {
        view.background = if (keyboardSkinId == KeyboardSkinId.CUPERTINO_CLASSIC) {
            com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CupertinoClassicCandidateChrome.panelBackground(
                alpha = if (transparentWithLiquidGlass && liquidGlassThemePreference == true) 0 else 255,
            )
        } else {
            null
        }
    }

    private fun applyKeyboardContainerBackgrounds(mainView: MainLayoutBinding) {
        applyCandidateExpandedSurfaceBackground(
            mainView.candidatesRowView,
            transparentWithLiquidGlass = true,
        )
        KeyboardSkinRegistry.find(keyboardSkinId)?.let { skin ->
            val keyboardBackground = skin.keyboardDrawable(resources)
            if (keyboardSkinId == KeyboardSkinId.CUPERTINO_CLASSIC) {
                applyClassicKeyboardCornerRounding(
                    drawable = keyboardBackground,
                    rounded = isKeyboardRounded == true,
                    radiusDp = keyboardCornerRadiusDp,
                    density = resources.displayMetrics.density,
                    topLeft = keyboardCornerTopLeft,
                    topRight = keyboardCornerTopRight,
                    bottomRight = keyboardCornerBottomRight,
                    bottomLeft = keyboardCornerBottomLeft,
                )
                val chrome = com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CupertinoClassicCandidateChrome
                mainView.suggestionViewParent.background = chrome.panelBackground()
                mainView.candidateTabLayout.background = chrome.tabsBackground()
                mainView.shortcutToolbarRecyclerview.background = chrome.toolbarBackground(resources)
            } else {
                mainView.suggestionViewParent.background = skin.keyboardDrawable(resources)
                mainView.candidateTabLayout.setBackgroundColor(skin.palette.background)
                mainView.shortcutToolbarRecyclerview.background = null
            }
            mainView.root.background = keyboardBackground
            return
        }
        mainView.shortcutToolbarRecyclerview.background = null
        val isDynamic = DynamicColors.isDynamicColorAvailable()
        if (isKeyboardRounded == true) {
            val fallbackColor = getColor(com.kazumaproject.core.R.color.keyboard_bg)
            val defaultColor = if (isDynamic) {
                mainView.root.context.getThemeColorOrFallback(
                    attrRes = MaterialR.attr.colorSurfaceContainer,
                    fallbackColor = fallbackColor
                )
            } else {
                fallbackColor
            }
            val customColor = customThemeBgColor ?: Color.WHITE
            val backgroundColor = when (keyboardThemeMode) {
                "custom" -> customColor
                else -> defaultColor
            }
            mainView.root.background = createKeyboardBackgroundDrawable(
                color = backgroundColor,
                radiusDp = keyboardCornerRadiusDp,
                topLeft = keyboardCornerTopLeft,
                topRight = keyboardCornerTopRight,
                bottomRight = keyboardCornerBottomRight,
                bottomLeft = keyboardCornerBottomLeft
            )
            mainView.suggestionViewParent.background = createKeyboardBackgroundDrawable(
                color = backgroundColor,
                radiusDp = keyboardCornerRadiusDp,
                topLeft = keyboardCornerTopLeft,
                topRight = keyboardCornerTopRight,
                bottomRight = keyboardCornerBottomRight,
                bottomLeft = keyboardCornerBottomLeft
            )
            mainView.candidateTabLayout.background = createKeyboardBackgroundDrawable(
                color = backgroundColor,
                radiusDp = keyboardCornerRadiusDp,
                topLeft = keyboardCornerTopLeft,
                topRight = keyboardCornerTopRight,
                bottomRight = keyboardCornerBottomRight,
                bottomLeft = keyboardCornerBottomLeft
            )
            return
        }

        when (keyboardThemeMode) {
            "default" -> {
                if (isDynamic) {
                    mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                    mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                    mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                } else {
                    mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                    mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                    mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                }
            }

            "custom" -> {
                mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)

                mainView.root.setDrawableSolidColor(customThemeBgColor ?: Color.WHITE)
                mainView.suggestionViewParent.setDrawableSolidColor(
                    customThemeBgColor ?: Color.WHITE
                )
                mainView.candidateTabLayout.setDrawableSolidColor(
                    customThemeBgColor ?: Color.WHITE
                )
            }

            else -> {
                if (isDynamic) {
                    mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                    mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                    mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_material_root)
                } else {
                    mainView.root.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                    mainView.suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                    mainView.candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.square_corners_bg_root)
                }
            }
        }
    }

    private fun applyNormalKeyboardChrome(mainView: MainLayoutBinding) {
        applyKeyboardContainerBackgrounds(mainView)
        applyImeGlassSurfaceAlpha(mainView)

        mainView.root.outlineProvider = ViewOutlineProvider.BACKGROUND
        mainView.root.clipToOutline = keyboardSkinId != KeyboardSkinId.DEFAULT || isKeyboardRounded == true
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        flickInputPreviewCoordinator.cancel(restore = true)
        clearZeroQueryAllState(refresh = false)
        // The input view can restart without onStartInput() after returning from settings.
        // Re-read preferences that may change while the existing input session is retained.
        syncRuntimeInputPreferences()
        syncCustomKeyboardSuggestionPreference()
        syncNgramDictionaryPreferences()
        isInputViewActive = true
        // A hidden input view must not carry the previous candidate-display phase into
        // the next render. The editor can restart the view without onStartInput().
        shortcutToolbarHiddenForCandidates = false
        collapseShortcutEntryExpansion()
        shortcutInputBehaviorOverride = null
        dismissKeyboardSelectionPopups()
        addUserDictionaryPopup?.dismiss()
        mainLayoutBinding?.let { mainView ->
            rebindMainKeyboardInputListeners(mainView)
            scheduleMainKeyboardInputListenerRebind(mainView)
        }
        _keyboardSymbolViewState.update { SymbolKeyboardState() }
        _selectMode.update { false }
        _cursorMoveMode.update { false }
        setSuggestionAdapterSuggestionsOnMain(emptyList())
        val candidateTextSize = appPreference.candidate_letter_size ?: 14.0f
        suggestionAdapter?.setCandidateTextSize(candidateTextSize)
        suggestionAdapterFull?.setCandidateTextSize(candidateTextSize)
        suggestionClickNum = 0
        setCurrentInputType(editorInfo)
        suggestionAdapter?.setClipboardDescriptionTextVisibility(
            !(clipboardPreviewTapToDelete ?: false)
        )
        val isNumberInputType = currentInputType in numberTypes
        if (!isNumberInputType && qwertyMode.value == TenKeyQWERTYMode.Sumire) {
            mainLayoutBinding?.let { mainView ->
                Timber.d("TenKeyQWERTYMode.Sumire: ${currentInputModeForSession} ${switchQWERTYPassword}")
                when (currentInputModeForSession) {
                    InputMode.ModeJapanese -> {
                        customKeyboardMode = KeyboardInputMode.HIRAGANA
                        updateKeyboardLayout()
                    }

                    InputMode.ModeEnglish -> {
                        customKeyboardMode = KeyboardInputMode.ENGLISH
                        createNewKeyboardLayoutForSumire()
                    }

                    InputMode.ModeNumber -> {
                        customKeyboardMode = KeyboardInputMode.SYMBOLS
                        createNewKeyboardLayoutForSumire()
                    }
                }
            }
        }

        updateClipboardPreview()

        suppressSuggestions = if (showCandidateInPasswordPreference == true) {
            currentInputType.isPassword()
        } else {
            false
        }

        val splitSelected = false
        if (currentInputType in passwordTypesWithOutNumber) {
            Timber.d("current input type in OnStartView passwordTypesWithOutNumber else: [$currentInputType] [$restarting]")
            setCurrentInputModeForSession(InputMode.ModeEnglish)
        } else if (isNumberInputType) {
            Timber.d("current input type in OnStartView number: [$currentInputType] [$restarting]")
            showNumberKeyboardForCurrentInputType()
        } else {
            Timber.d("current input type in OnStartView not password: [$currentInputType] [$restarting]")
            resetKeyboard()
        }

        mainLayoutBinding?.let { mainView ->
            setKeyboardSizeSwitchKeyboard(mainView)
            mainView.apply {
                applyNormalKeyboardChrome(mainView)
                applyKeyboardBackgroundIfNeeded(mainView)
                setupSuminagashiInkEffect(mainView)

                suggestionRecyclerView.isVisible = true
                suggestionVisibility.isVisible = false
                val defaultLetterSize = when (currentInputModeForSession) {
                    InputMode.ModeJapanese -> 17f
                    InputMode.ModeEnglish -> 12f
                    InputMode.ModeNumber -> 16f
                    else -> 17f
                }
                if (tenkeyShowIMEButtonPreference == true) {
                } else {
                }


                setTabsToTabLayout(mainView)

                refreshSuggestionProgressVisibility()

                applyCurrentFlickGuidePreference(customLayoutDefault)
                customLayoutDefault.setFlickGuideTextSizeSp(
                    (flickGuideTextSizeSpPreference ?: 9).coerceIn(6, 16).toFloat()
                )
                customLayoutDefault.setFlickGuideMaxCodePoints(
                    (flickGuideMaxCharactersPreference ?: 1).coerceIn(1, 4)
                )
                suggestionRecyclerView.adapter = suggestionAdapter
                candidatesRowView.adapter = suggestionAdapterFull
                refreshCandidateStripContent(candidatesShown = false)
            }
            setMainSuggestionColumn(mainView)
        }
        updateIncognitoModeState(editorInfo)
        anchorActiveSuggestionStripStartIfLeadingContentExpected()
        refreshBaselineInputBehaviorForCurrentKeyboard("start input keyboard layout settled")
    }

    override fun onWindowShown() {
        super.onWindowShown()
        window.window?.decorView?.post {
        }
    }

    override fun onFinishInput() {
        dismissKeyboardSelectionPopups()
        modeSwitchAwaitingCursorPosition = false
        dismissJob?.cancel()
        forwardDeleteCoordinator.cancel()
        resetCustomToggleState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlineAutofillController?.clear()
        }
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        dismissKeyboardSelectionPopups()
        modeSwitchAwaitingCursorPosition = false
        dismissJob?.cancel()
        imeSwitchPopupWindow?.dismiss()
        forwardDeleteCoordinator.cancel()
        resetCustomToggleState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlineAutofillController?.clear()
        }
        flickInputPreviewCoordinator.cancel(restore = false)
        candidateRequestTracker.invalidate()
        candidateRefreshCoordinator.invalidate()
        defaultInputFinalizeJob?.cancel()
        defaultInputFinalizeJob = null
        super.onFinishInputView(finishingInput)
        Timber.d("onUpdate onFinishInputView")
        clearZeroQueryAllState(refresh = false)
        stopAllOngoingKeyLongPresses()
        disableKeyboardLayoutEditMode()
        persistCurrentCustomKeyboardInputModeIfEnabled()
        isInputViewActive = false
        clearAndPauseSuminagashiInkEffects()
        stopVoiceInput()
        collapseShortcutEntryExpansion()
        shortcutToolbarHiddenForCandidates = false
    }

    override fun onWindowHidden() {
        dismissKeyboardSelectionPopups()
        imeSwitchPopupWindow?.dismiss()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlineAutofillController?.clear()
        }
        flickInputPreviewCoordinator.cancel(restore = true)
        clearAndPauseSuminagashiInkEffects()
        super.onWindowHidden()
    }

    /**
     * The zenz model lives in the separate :zenz process and is loaded lazily on the first
     * rerank. Under real memory pressure (RUNNING_LOW / RUNNING_CRITICAL, or once we are in the
     * background LRU list) unbind it so that process and its model/KV buffers can be reclaimed;
     * the next rerank reconnects and re-initializes it. UI_HIDDEN alone does not release it.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val release = level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (AppVariantConfig.hasZenz && release) {
            zenzRuntimeClient.close()
        }
    }

    override fun onDestroy() {
        localFontScope.cancel()
        dismissKeyboardSelectionPopups()
        unregisterCrossWindowBlurListener()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inlineAutofillController?.destroy()
            inlineAutofillController = null
        }
        flickInputPreviewCoordinator.cancel(restore = false)
        resetEditorSelectionSnapshot()
        Timber.d("onUpdate onDestroy")
        if (runtimeInputPreferenceListenerRegistered) {
            runtimeInputSharedPreferences.unregisterOnSharedPreferenceChangeListener(
                runtimeInputPreferenceListener
            )
            runtimeInputPreferenceListenerRegistered = false
        }
        updateKeyboardSelectionPopupBackInvokedCallback(registered = false)
        clearZeroQueryAllState(refresh = false)
        stopAllOngoingKeyLongPresses()
        disableKeyboardLayoutEditMode(updateSurface = false)
        collapseShortcutEntryExpansion()
        isInputViewActive = false
        releaseSuminagashiInkEffects()
        super.onDestroy()
        mainLayoutBinding?.apply {
            keyboardSymbolView.release()
        }
        if (AppVariantConfig.hasZenz) {
            zenzRuntimeClient.close()
        }
        cachedZenzModelSource = null
        cachedZenzModelPath = null
        kanaKanjiEngineActivationJob?.cancel()
        suggestionAdapter?.release()
        suggestionAdapter = null
        necookeyZenzJob?.cancel()
        necookeyZenzJob = null
        necookeyPredictionAdapter?.release()
        necookeyPredictionAdapter = null
        shortcutAdapter = null
        suggestionAdapterFull = null
        keyboardLayoutEditController = null
        dismissJob = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        clearSymbols()
        keyboardSelectionPopupWindow = null
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        filteredCandidateList = null
        if (::kanaKanjiEngine.isInitialized) {
            if (mozcUTPersonName == true) kanaKanjiEngine.releasePersonNamesDictionary()
            if (mozcUTPlaces == true) kanaKanjiEngine.releasePlacesDictionary()
            if (mozcUTWiki == true) kanaKanjiEngine.releaseWikiDictionary()
            if (mozcUTNeologd == true) kanaKanjiEngine.releaseNeologdDictionary()
            if (mozcUTWeb == true) kanaKanjiEngine.releaseWebDictionary()
            if (kanaKanjiEngine.isSystemUserDictionaryInitialized()) {
                kanaKanjiEngine.releaseSystemUserDictionary()
            }
        }
        isFlickOnlyMode = null
        isOmissionSearchEnable = null
        delayTime = null
        isLearnDictionaryMode = null
        incognitoModeDetectionPreference = true
        showLearnedCandidatesInIncognitoPreference = true
        isUserDictionaryEnable = null
        isUserTemplateEnable = null
        isTextMacroCandidateEnable = true
        suppressHentaiganaCandidates = false
        hankakuPreference = null
        customDirectModeSpaceHankakuPreference = true
        isLiveConversionEnable = null
        nBest = null
        conversionBeamWidth = 20
        predictionConfig = PredictionConfig()
        lastCandidate = null
        flickSensitivityPreferenceValue = null
        flickThresholdShapePreferenceValue = FlickThresholdShape.Radial
        longPressTimeoutPreferenceValue = null
        qwertyShowIMEButtonPreference = null
        qwertyShowEmojiButtonPreference = null
        defaultEmojiSkinTonePreference = EmojiSkinToneSupport.DEFAULT_SKIN_TONE
        tenkeyShowIMEButtonPreference = null
        qwertyShowCursorButtonsPreference = null
        qwertyShowNumberButtonsPreference = null
        qwertyShowSwitchRomajiEnglishPreference = null
        qwertyEnglishDirectInputPreference = false
        qwertyGlideInputPreference = false
        qwertyRomajiShiftConversionPreference = null
        qwertyShowPopupWindowPreference = null
        qwertyEnableFlickUpPreference = null
        qwertyEnableFlickDownPreference = null
        qwertyNumberKeyFlickUpChars = emptyMap()
        qwertyNumberKeyFlickDownChars = emptyMap()
        qwertyEnableZenkakuSpacePreference = null
        qwertyRomajiHankakuNumberPreference = null
        qwertyRomajiHankakuSymbolPreference = null
        switchQWERTYPassword = null
        landscapeForceQwertyPreference = null
        landscapeForceQwertyRomajiPreference = null
        shortcutTollbarVisibility = null
        shortcutToolbarIntegratedInSuggestion = null
        shortcutToolbarHeightDp = AppPreference.SHORTCUT_TOOLBAR_HEIGHT_DEFAULT_DP
        shortcutToolbarIconSizeDp = AppPreference.SHORTCUT_TOOLBAR_ICON_SIZE_DEFAULT_DP
        shortcutToolbarHiddenForCandidates = false
        clipboardPreviewVisibility = null
        clipboardPreviewTapToDelete = null
        isDeleteLeftFlickPreference = null
        isDeleteUpFlickPreference = null
        isDeleteDownFlickPreference = null
        qwertyShowKutoutenButtonsPreference = null
        qwertyShowKeymapSymbolsPreference = null
        showCandidateInPasswordPreference = null
        tenkeyHeightPreferenceValue = null
        tenkeyWidthPreferenceValue = null
        qwertyHeightPreferenceValue = null
        candidateViewHeightPreferenceValue = null
        candidateViewHeightEmptyPreferenceValue = null
        qwertyWidthPreferenceValue = null
        tenkeyPositionPreferenceValue = null
        tenkeyBottomMarginPreferenceValue = null
        qwertyPositionPreferenceValue = null
        qwertyBottomMarginPreferenceValue = null

        tenkeyStartMarginPreferenceValue = null
        tenkeyEndMarginPreferenceValue = null
        qwertyStartMarginPreferenceValue = null
        qwertyEndMarginPreferenceValue = null

        tenkeyLandScapeStartMarginPreferenceValue = null
        tenkeyLandScapeEndMarginPreferenceValue = null
        qwertyLandScapeStartMarginPreferenceValue = null
        qwertyLandScapeEndMarginPreferenceValue = null

        enableShowLastShownKeyboardInRestart = null
        lastSavedKeyboardPosition = null

        tenkeyHeightLandScapePreferenceValue = null
        tenkeyWidthLandScapePreferenceValue = null
        qwertyHeightLandScapePreferenceValue = null
        candidateViewLandScapeHeightPreferenceValue = null
        candidateViewLandScapeHeightEmptyPreferenceValue = null
        qwertyWidthLandScapePreferenceValue = null
        tenkeyLandScapePositionPreferenceValue = null
        tenkeyLandScapeBottomMarginPreferenceValue = null
        qwertyLandScapePositionPreferenceValue = null
        qwertyLandScapeBottomMarginPreferenceValue = null


        qwertyKeyVerticalMargin = null
        qwertyKeyHorizontalGap = null
        qwertyKeyIndentLarge = null
        qwertyKeyIndentSmall = null
        qwertyKeySideMargin = null
        qwertyKeyTextSize = null
        qwertySymbolKeymapTextSize = null
        qwertySpecialKeyTextSize = null
        qwertySpecialKeyIconSize = null

        keyboardThemeMode = null
        customThemeBgColor = null
        customThemeKeyColor = null
        customThemeSpecialKeyColor = null
        customThemeKeyTextColor = null
        customThemeSpecialKeyTextColor = null
        customThemeCandidateTextColor = null
        customThemeCandidateItemBgColor = null
        customThemeCandidateItemPressedBgColor = null
        customThemeCandidateEmptyPopupBgColor = null
        customThemeCandidateEmptyPopupTextColor = null
        customThemeShortcutIconColor = null

        mozcUTPersonName = null
        romajiConverter = null
        mozcUTPlaces = null
        mozcUTWiki = null
        mozcUTNeologd = null
        mozcUTWeb = null
        sumireInputKeyType = null
        sumireInputKeyLayoutType = null
        sumireInputStyle = null
        candidateColumns = null
        candidateColumnsLandscape = null
        candidateViewHeight = null
        candidateTabVisibility = null
        stabilizeCandidateStripHeightPreference = false
        dockedCandidateHeightStabilized = false
        isTablet = null
        isNgWordEnable = null
        deleteKeyHighLight = null
        customKeyboardSuggestionPreference = null
        customDirectInputReplaceComposingPreference = false
        customKeymapGuidePreference = false
        sumireKeymapGuideSettings = ModeKeymapGuideSettings()
        flickGuideTextSizeSpPreference = null
        flickGuideMaxCharactersPreference = null
        symbolKeyboardFirstItem = null
        userDictionaryPrefixMatchNumber = null
        tenkeyQWERTYSwitchNumber = null
        tenkeyUseThreeStateKeyboard = true
        tenkeyNumberSymbolKeyGapDp = 4
        tenkeySwitchNumberToQwertyNumberPreference = false
        tenkeyRestoreInputModeOnRestart = false
        sumireRestoreInputModeOnRestart = false
        tenkeyRestoreInputModeOnlyWithinTime = false
        tenkeyRestoreInputModeTimeoutMinutes = 5
        tenkeyLastInputModeSavedAtEpochMillis = 0L
        sumireRestoreInputModeOnlyWithinTime = false
        sumireRestoreInputModeTimeoutMinutes = 5
        sumireLastInputModeSavedAtEpochMillis = 0L
        tenkeyLastInputModePreference = "japanese"
        tenkeyLastInputModePresentationPreference = "native"
        tenkeyLastQwertyNumberReturnTargetPreference = "japanese"
        sumireLastInputModePreference = "japanese"
        sumireLastInputModePresentationPreference = "native"
        qwertyNumberOpenedFromTenkeyTwoStateNumberKey = false
        tabletTenkeyQwertySwitchEnglish = false
        tenkeyKeymapGuideSettings = ModeKeymapGuideSettings()
        isKeyboardRounded = null
        reconversionEnabledPreference = false
        conversionKeySwipePreference = null
        bunsetsuPositionList = null
        bunsetsuSplitPatterns = emptyList()
        bunsetsuConversionSession = null
        pendingReconversionEntry = null
        bunsetsuReconversionDraft = null
        preserveBunsetsuReconversionDraftOnNextProcessInput = false
        isRestoringReconversionInput = false

        liquidGlassThemePreference = null
        liquidGlassBlurRadiousPreference = null
        liquidGlassKeyBlurRadiousPreference = null
        keyboardTouchEffectTypePreference = KeyboardTouchEffectType.NONE
        keyboardTouchEffectQualityPreference = KeyboardTouchEffectQuality.HIGH
        keyboardTouchEffectColorModePreference = "random"
        keyboardTouchEffectColorPreference = Color.rgb(17, 17, 17)
        keyboardTouchEffectPalettePreference = SprayPaintSettings.PALETTE_PAINT_SPLASH
        liquidInkDensityPreference = 100
        auroraInkDensityPreference = 100
        cinematicWaveColorModePreference = CinematicWaveSettings.COLOR_MODE_CINEMATIC_RANDOM
        cinematicWavePrimaryColorPreference = CinematicWaveSettings.DEFAULT_PRIMARY_COLOR
        cinematicWaveSecondaryColorPreference = CinematicWaveSettings.DEFAULT_SECONDARY_COLOR
        cinematicWaveSecondaryColorAutoPreference = true
        cinematicWaveTypePreference = CinematicWaveSettings.WAVE_TYPE_AURORA_MEMBRANE
        cinematicWaveOpacityPercentPreference = 46
        cinematicWaveIntensityPercentPreference = 100
        cinematicWaveMotionPreference = CinematicWaveSettings.MOTION_ELEGANT
        cinematicWaveTouchResponsePreference = CinematicWaveSettings.TOUCH_RESPONSE_NORMAL
        cinematicWaveQualityPreference = CinematicWaveSettings.QUALITY_BALANCED
        customKeyBorderEnablePreference = null
        customKeyBorderEnableColor = null

        customComposingTextPreference = null
        inputCompositionBackgroundColor = null
        inputCompositionTextColor = null
        inputCompositionAfterBackgroundColor = null
        inputConversionBackgroundColor = null
        inputConversionTextColor = null

        previousTenKeyQWERTYMode = null

        sumireEnglishQwertyPreference = null
        conversionCandidatesRomajiEnablePreference = null
        enableZenzRightContextPreference = null
        learnFirstCandidateDictionaryPreference = null
        learnDictionaryAllowMixedSymbolsNumbersPreference = true
        enablePredictionSearchLearnDictionaryPreference = null
        learnPredictionPreference = null
        userDictionaryPredictionCandidateLimit = 4
        learnDictionaryPredictionCandidateLimit = 4
        circularFlickWindowScale = null
        customKeyBorderWidth = null
        qwertySwitchNumberKeyWithoutNumberPreference = null
        omissionSearchOffsetScorePreference = null
        enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference = null

        enableTypoCorrectionJapaneseFlickKeyboardPreference = null
        enableTypoCorrectionQwertyEnglishKeyboardPreference = null

        actionInDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
        System.gc()
    }

    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        if (dockedCandidateContainerActive && !isFullscreenMode && outInsets != null) {
            val container = keyboardContainer ?: return
            applyDockedCandidateInsets(container, dockedCandidateHeightStabilized, outInsets)
        }
    }

    private fun registerCrossWindowBlurListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || crossWindowBlurListenerRegistered) {
            return
        }

        val manager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        imeWindowManager = manager
        crossWindowBlurEnabled = runCatching {
            manager.isCrossWindowBlurEnabled
        }.getOrDefault(false)

        runCatching {
            manager.addCrossWindowBlurEnabledListener(
                ContextCompat.getMainExecutor(this),
                crossWindowBlurEnabledListener,
            )
            crossWindowBlurListenerRegistered = true
        }.onFailure { throwable ->
            Timber.w(throwable, "Unable to observe cross-window blur state")
            crossWindowBlurEnabled = false
        }
    }

    private fun unregisterCrossWindowBlurListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !crossWindowBlurListenerRegistered) {
            return
        }

        runCatching {
            imeWindowManager?.removeCrossWindowBlurEnabledListener(
                crossWindowBlurEnabledListener
            )
        }.onFailure { throwable ->
            Timber.w(throwable, "Unable to remove cross-window blur listener")
        }
        crossWindowBlurListenerRegistered = false
        imeWindowManager = null
    }

    private fun currentImeGlassRenderDecision(): ImeGlassRenderDecision {
        return ImeGlassRenderPolicy.resolve(
            sdkInt = Build.VERSION.SDK_INT,
            glassEnabled = liquidGlassThemePreference == true,
            floatingMode = false,
            physicalKeyboardEnabled = false,
            hardwareKeyboardConnected = false,
            crossWindowBlurEnabled = crossWindowBlurEnabled,
        )
    }

    private fun applyImeGlassSurfaceAlpha(
        mainView: MainLayoutBinding,
        decision: ImeGlassRenderDecision = currentImeGlassRenderDecision(),
    ) {
        if (liquidGlassThemePreference != true) return

        val rootAlpha = when (decision.mode) {
            ImeGlassRenderMode.SYSTEM_BLUR -> liquidGlassBlurRadiousPreference ?: 220
            ImeGlassRenderMode.OPAQUE_BACKDROP,
            ImeGlassRenderMode.NO_BLUR,
                -> 255
        }
        mainView.root.setDrawableAlpha(rootAlpha)
        mainView.suggestionViewParent.setDrawableAlpha(0)
        mainView.candidateTabLayout.setDrawableAlpha(0)
        mainView.candidatesRowView.setDrawableAlpha(0)
    }

    private fun updateImeWindowBlurForCurrentMode(targetWindow: Window? = window.window) {
        val decision = currentImeGlassRenderDecision()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            targetWindow?.setBackgroundBlurRadius(decision.windowBlurRadius)
        }

        mainLayoutBinding?.let { mainView ->
            applyImeGlassSurfaceAlpha(mainView, decision)
        }
    }

    override fun onConfigureWindow(win: Window?, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        updateImeWindowBlurForCurrentMode(win)
    }

    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo?) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        dismissKeyboardSelectionPopups()
        dictionaryConfigurationChanging = true
        try {
            super.onConfigurationChanged(newConfig)
        } finally {
            dictionaryConfigurationChanging = false
        }
        clearZeroQueryAllState(refresh = false)
        collapseShortcutEntryExpansion()
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                finishComposingText()
                setComposingText("", 0)
                Timber.d("onConfigurationChanged: ORIENTATION_PORTRAIT")
            }

            Configuration.ORIENTATION_LANDSCAPE -> {
                finishComposingText()
                setComposingText("", 0)
                Timber.d("onConfigurationChanged: ORIENTATION_LANDSCAPE")
            }

            Configuration.ORIENTATION_UNDEFINED -> {
                finishComposingText()
                setComposingText("", 0)
                Timber.d("onConfigurationChanged: ORIENTATION_UNDEFINED")
            }

            else -> {
                finishComposingText()
                setComposingText("", 0)
                Timber.d("onConfigurationChanged: else")
            }
        }

        val newNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK

        if (newNightMode != currentNightMode) {
            setupKeyboardView()
            currentNightMode = newNightMode
        }

        lastKeyboardLayoutRootView = null
        lastKeyboardLayoutOrientation = null
        refreshKeyboardForCurrentOrientation()
        mainLayoutBinding?.let { mainView ->
            rebindMainKeyboardInputListeners(mainView)
            scheduleMainKeyboardInputListenerRebind(mainView)
            mainView.root.post {
                if (mainLayoutBinding?.root === mainView.root) {
                    lastKeyboardLayoutRootView = null
                    lastKeyboardLayoutOrientation = null
                    updateKeyboardLayout(mainView)
                    renderCurrentKeyboardStateOnActiveSurface()
                    rebindMainKeyboardInputListeners(mainView)
                }
            }
        }

    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }


    // necookey never uses the fullscreen (extract) editor; the setting was removed in S3.
    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun canShowPopupWindow(anchorView: View?): Boolean {
        if (!isInputViewActive) return false
        if (anchorView == null) return false
        if (!anchorView.isAttachedToWindow) return false
        if (anchorView.windowToken == null) return false
        return true
    }

    private fun resolveShowListPopupAnchor(mainView: MainLayoutBinding): View? {
        return requireActiveKeyboardSurface()?.rootView ?: mainView.root

        val mainRoot = mainLayoutBinding?.root
        if (mainRoot?.isAttachedToWindow == true && mainRoot.windowToken != null) {
            return mainRoot
        }

        val decorView = window.window?.decorView
        if (decorView?.isAttachedToWindow == true && decorView.windowToken != null) {
            return decorView
        }

        return null
    }

    private fun showPopupWindowSafely(
        popupWindow: PopupWindow,
        anchorView: View?,
        gravity: Int,
        x: Int,
        y: Int,
        source: String
    ): Boolean {
        if (!canShowPopupWindow(anchorView)) {
            Timber.w("$source: Skip showAtLocation because anchor is not attached.")
            return false
        }
        return runCatching {
            applyLocalFontToPopupContent(popupWindow.contentView)
            popupWindow.showAtLocation(anchorView, gravity, x, y)
            popupWindow.contentView.post { applyLocalFontToPopupContent(popupWindow.contentView) }
            true
        }.onFailure { throwable ->
            Timber.w(throwable, "$source: showAtLocation failed")
        }.getOrDefault(false)
    }

    private fun canUpdatePopupWindow(popupWindow: PopupWindow?): Boolean {
        if (popupWindow == null) return false
        if (!popupWindow.isShowing) return false
        if (popupWindow.contentView?.isAttachedToWindow != true) return false
        return true
    }

    private fun updatePopupWindowPositionSafely(
        popupWindow: PopupWindow?,
        x: Int,
        y: Int,
    ) {
        if (!canUpdatePopupWindow(popupWindow)) return

        runCatching {
            popupWindow?.update(x, y, -1, -1)
        }.onFailure { throwable ->
            Timber.w(throwable, "PopupWindow position update failed")
        }
    }

    private fun updateComposingText(text: String) {
        // 途中経過の表示用（変換中のようなイメージ）
        setComposingText(text, 1)
    }

    private fun commitRecognizedText(text: String) {
        // 確定時：composing を消してから確定文字列を commit
        currentInputConnection?.apply {
            finishComposingText()
            commitText(text, 1)
        }
    }

    private fun startVoiceInput(
        mainView: MainLayoutBinding
    ) {
        Timber.d("startVoiceInput: [$isListening] [$speechRecognizer]")
        setSuggestionProgressVisible(
            reason = SuggestionProgressReason.VoiceInput,
            visible = false
        )
        if (isListening) return
        if (speechRecognizer == null) return

        val languageValue: String = when {
            false -> {
                if (currentInputModeForSession == InputMode.ModeEnglish) "en-CA" else "ja-JP"
            }
            false -> {
                "en-CA"
            }

            false -> {
                if (currentQwertyRomajiModeForSession) {
                    "ja-JP"
                } else {
                    "en-CA"
                }
            }

            else -> {
                when (currentInputModeForSession) {
                    InputMode.ModeJapanese -> "ja-JP"
                    InputMode.ModeEnglish -> "en-CA"
                    InputMode.ModeNumber -> "ja-JP"
                }
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // 日本語固定にしたければ "ja-JP"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageValue)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: SecurityException) {
            // RECORD_AUDIO が許可されていないなど
            isListening = false
        }
    }

    private fun stopVoiceInput() {
        if (!isListening) return
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        } finally {
            isListening = false
        }
    }

    private fun applySymbolKeyboardAppearance() {
        mainLayoutBinding?.keyboardSymbolView?.let { view ->
            if (keyboardThemeMode == "custom") {
                val palette = KeyboardSkinRegistry.find(keyboardSkinId)?.palette
                val keyColor = customThemeKeyColor ?: Color.WHITE
                view.setKeyboardTheme(
                    skinId = keyboardSkinId,
                    backgroundColor = palette?.background ?: manipulateColor(keyColor, 1.2f),
                    iconColor = palette?.specialText ?: customThemeKeyTextColor ?: Color.BLACK,
                    selectedIconColor = palette?.selectionText
                        ?: manipulateColor(customThemeKeyTextColor ?: Color.BLACK, 0.6f),
                    keyBackgroundColor = palette?.specialKey ?: keyColor,
                    liquidGlassEnable = liquidGlassThemePreference ?: false,
                )
            } else {
                view.restoreDefaultKeyboardTheme()
            }
        }
    }

    private data class CandidateTabDefaultColors(
        val text: android.content.res.ColorStateList?,
        val indicator: Int,
    )

    private val candidateTabDefaultColors = java.util.WeakHashMap<TabLayout, CandidateTabDefaultColors>()

    private fun applyCandidateTabAppearance(tab: TabLayout) {
        val original = candidateTabDefaultColors.getOrPut(tab) {
            // Capture the inflated theme before applying any skin. The XML has no tab overrides.
            val attributes = tab.context.obtainStyledAttributes(
                null, intArrayOf(com.google.android.material.R.attr.tabIndicatorColor),
                com.google.android.material.R.attr.tabStyle,
                com.google.android.material.R.style.Widget_Design_TabLayout,
            )
            try {
                CandidateTabDefaultColors(tab.tabTextColors, attributes.getColor(0, Color.TRANSPARENT))
            } finally {
                attributes.recycle()
            }
        }
        val palette = KeyboardSkinRegistry.find(keyboardSkinId)?.palette
        if (palette != null) {
            if (keyboardSkinId == KeyboardSkinId.CUPERTINO_CLASSIC) {
                com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CupertinoClassicCandidateChrome.applyTabs(tab)
            } else {
                com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CupertinoClassicCandidateChrome.restoreTabs(tab)
                tab.setTabTextColors(palette.text, palette.selection)
                tab.setSelectedTabIndicatorColor(palette.selection)
            }
        } else if (keyboardThemeMode == "custom") {
            com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CupertinoClassicCandidateChrome.restoreTabs(tab)
            tab.setTabTextColors(customThemeKeyTextColor ?: Color.BLACK,
                customThemeSpecialKeyTextColor ?: Color.BLACK)
            tab.setSelectedTabIndicatorColor(customThemeSpecialKeyTextColor ?: Color.BLACK)
        } else {
            com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CupertinoClassicCandidateChrome.restoreTabs(tab)
            tab.setTabTextColors(original.text)
            tab.setSelectedTabIndicatorColor(original.indicator)
        }
    }

    /** Reapply appearance to reused candidate surfaces at every input session, not only inflation. */
    private fun applyCandidateAppearance() {
        mainLayoutBinding?.let {
            applyCandidateExpandedSurfaceBackground(
                it.candidatesRowView,
                transparentWithLiquidGlass = true,
            )
        }
        mainLayoutBinding?.candidateTabLayout?.let(::applyCandidateTabAppearance)
        val skin = KeyboardSkinRegistry.find(keyboardSkinId)
        val skinColors = skin?.let { resolveCandidatePanelColors() }
        val classic = keyboardSkinId == KeyboardSkinId.CUPERTINO_CLASSIC
        val custom = keyboardThemeMode == "custom" && skin == null
        val inlineBackgroundTint = skin?.palette?.let { palette ->
            android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                intArrayOf(palette.pressed, palette.key),
            )
        }
        listOfNotNull(suggestionAdapter, suggestionAdapterFull).forEach { adapter ->
            adapter.setCandidateTextColor(skinColors?.text ?: if (custom) customThemeCandidateTextColor ?: Color.BLACK else null)
            adapter.setInlineSuggestionIconBackgroundTint(inlineBackgroundTint)
            adapter.setCandidateItemColors(
                if (classic) Color.TRANSPARENT
                else skin?.palette?.background ?: if (custom) customThemeCandidateItemBgColor ?: Color.TRANSPARENT else null,
                skinColors?.pressed ?: if (custom) customThemeCandidateItemPressedBgColor ?: ContextCompat.getColor(
                    this, com.kazumaproject.core.R.color.qwety_key_bg_color
                ) else null,
                if (keyboardSkinId == KeyboardSkinId.CUPERTINO_CLASSIC) 0f else 16f,
            )
            adapter.setCandidateDividerColor(
                if (keyboardSkinId == KeyboardSkinId.CUPERTINO_CLASSIC)
                    com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CupertinoClassicCandidateChrome.dividerColor
                else null,
                verticalMarginDp = if (keyboardSkinId == KeyboardSkinId.CUPERTINO_CLASSIC)
                    com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CupertinoClassicCandidateChrome.candidateDividerVerticalInsetDp
                else null,
            )
        }
        val defaultButtonTint = ContextCompat.getColorStateList(
            this, com.kazumaproject.core.R.color.keyboard_icon_color
        )
        listOfNotNull(
            mainLayoutBinding?.let { it.suggestionVisibility to it.candidatesRowView },
        ).forEach { (button, expandedCandidates) ->
            button.isSelected = expandedCandidates.visibility == View.VISIBLE
            button.backgroundTintList = null
            if (classic) {
                val chrome = com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CupertinoClassicCandidateChrome
                button.background = chrome.expandButtonBackground(button.resources)
                button.imageTintList = chrome.expandButtonTint()
                button.clearColorFilter()
            } else {
                // setBackgroundResource may keep the same resource instance after a skin
                // mutated its fill. Load a fresh drawable to restore the themed XML colors.
                button.background = ContextCompat.getDrawable(
                    button.context,
                    if (DynamicColors.isDynamicColorAvailable()) com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material
                    else com.kazumaproject.core.R.drawable.recyclerview_size_button_bg
                )?.mutate()
                button.imageTintList = skinColors?.let { android.content.res.ColorStateList.valueOf(it.icon) }
                    ?: defaultButtonTint
                if (skin != null) {
                    button.backgroundTintList = android.content.res.ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                        intArrayOf(skin.palette.pressed, skin.palette.specialKey),
                    )
                    button.clearColorFilter()
                } else if (custom) {
                    button.setDrawableSolidColor(customThemeSpecialKeyColor ?: Color.GRAY)
                    button.setColorFilter(customThemeKeyTextColor ?: Color.BLACK)
                } else {
                    button.clearColorFilter()
                }
            }
        }
        val shortcutColor = resolveCandidateShortcutIconColor()
        shortcutAdapter?.setIconColor(shortcutColor)
        listOfNotNull(suggestionAdapter, suggestionAdapterFull).forEach { it.setShortcutIconColor(shortcutColor) }
        applyCandidateEmptyPopupThemeToAdapters()
        Unit
    }

    private fun resolveCandidateShortcutIconColor(): Int? =
        if (KeyboardSkinRegistry.find(keyboardSkinId) != null) resolveCandidatePanelColors().icon
        else if (keyboardThemeMode == "custom") customThemeShortcutIconColor ?: Color.BLACK
        else null

    private fun applyCandidateEmptyPopupThemeToAdapters() {
        val adapters = listOfNotNull(suggestionAdapter, suggestionAdapterFull)
        KeyboardSkinRegistry.find(keyboardSkinId)?.let { skin ->
            val colors = resolveCandidatePanelColors()
            adapters.forEach { it.setCandidateEmptyPopupColors(skin.palette.key, colors.icon) }
            return
        }
        if (keyboardThemeMode != "custom") {
            adapters.forEach { adapter ->
                adapter.clearCandidateEmptyPopupColors()
            }
            return
        }

        val colors = resolveCandidateEmptyPopupThemeColors(
            popupBackgroundColor = customThemeCandidateEmptyPopupBgColor,
            popupTextColor = customThemeCandidateEmptyPopupTextColor,
            specialKeyColor = customThemeSpecialKeyColor,
            specialKeyTextColor = customThemeSpecialKeyTextColor,
            defaultBackgroundColor = Color.WHITE,
            defaultTextColor = Color.BLACK,
        )
        adapters.forEach { adapter ->
            adapter.setCandidateEmptyPopupColors(
                backgroundColor = colors.backgroundColor,
                textColor = colors.textColor,
            )
        }
    }

    private fun applyLocalKeyboardFont(snapshot: KeyboardFontSnapshot) {
        mainLayoutBinding?.let { binding ->
            KeyboardFontApplicator.applyToKeyboardViews(binding.customLayoutDefault, snapshot)
            KeyboardFontApplicator.applyToKeyboardViews(binding.keyboardSymbolView, snapshot)
            listOfNotNull(suggestionAdapter, suggestionAdapterFull).forEach { it.setKeyboardFont(snapshot) }
        }
        shortcutAdapter?.setKeyboardFont(snapshot)
        listOfNotNull(keyboardSelectionPopupWindow, imeSwitchPopupWindow)
            .distinct()
            .forEach { applyLocalFontToPopupContent(it.contentView) }
    }

    private fun setupKeyboardView() {
        Timber.d("setupKeyboardView: Called")
        val isDynamicColorsEnable = DynamicColors.isDynamicColorAvailable()
        // Keep the saved theme context underneath presentation overrides. A cold launch
        // into Cupertino must still restore the user's dynamic/seed colors on return.
        val ctx = when (appPreference.theme_mode) {
            "default" -> {
                if (isDynamicColorsEnable) {
                    val seedColor = appPreference.seedColor

                    if (seedColor == 0x00000000) {
                        DynamicColors.wrapContextIfAvailable(this, R.style.Theme_MarkdownKeyboard)
                    } else {
                        val baseThemedContext =
                            ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
                        val options =
                            DynamicColorsOptions.Builder().setContentBasedSource(seedColor).build()
                        DynamicColors.wrapContextIfAvailable(baseThemedContext, options)
                    }
                } else {
                    ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
                }
            }

            "custom" -> {
                ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
            }

            else -> {
                if (isDynamicColorsEnable) {
                    val seedColor = appPreference.seedColor

                    if (seedColor == 0x00000000) {
                        DynamicColors.wrapContextIfAvailable(this, R.style.Theme_MarkdownKeyboard)
                    } else {
                        val baseThemedContext =
                            ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
                        val options =
                            DynamicColorsOptions.Builder().setContentBasedSource(seedColor).build()
                        DynamicColors.wrapContextIfAvailable(baseThemedContext, options)
                    }
                } else {
                    ContextThemeWrapper(this, R.style.Theme_MarkdownKeyboard)
                }
            }
        }

        mainLayoutBinding = MainLayoutBinding.inflate(LayoutInflater.from(ctx)).also { binding ->
            binding.root.fallbackTouchTargetProvider = {
                binding.customLayoutDefault.takeIf { it.isAttachedToWindow && it.isShown }
            }
        }

        applyLocalKeyboardFont(localFontRepository.state.value.snapshot)

        keyboardContainer?.let { container ->
            container.removeAllViews()
            mainLayoutBinding?.root?.let { newRootView ->
                container.addView(newRootView)
                mainLayoutBinding?.let { mainView ->
                    when (keyboardThemeMode) {
                        "default" -> {
                            if (isDynamicColorsEnable) {
                                mainView.apply {
                                    root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                    suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                    suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                                    candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                }
                            }
                        }

                        "custom" -> {
                            mainView.apply {
                                root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                                candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                root.setDrawableSolidColor(customThemeBgColor ?: Color.WHITE)
                                suggestionViewParent.setDrawableSolidColor(
                                    customThemeBgColor ?: Color.WHITE
                                )
                                suggestionVisibility.setDrawableSolidColor(
                                    customThemeSpecialKeyColor ?: Color.GRAY
                                )
                                candidateTabLayout.setLayerTypeSolidColor(
                                    customThemeBgColor ?: Color.WHITE
                                )

                                suggestionVisibility.setColorFilter(
                                    customThemeKeyTextColor ?: Color.BLACK
                                )
                            }
                        }

                        else -> {
                            if (isDynamicColorsEnable) {
                                mainView.apply {
                                    root.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                    suggestionViewParent.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                    suggestionVisibility.setBackgroundResource(com.kazumaproject.core.R.drawable.recyclerview_size_button_bg_material)
                                    candidateTabLayout.setBackgroundResource(com.kazumaproject.core.R.drawable.keyboard_root_material)
                                }
                            }
                        }
                    }
                    // The physical-keyboard candidate popup is rendered in a separate
                    // PopupWindow, so it does not inherit the candidate-strip TextView color.
                    applyCandidateAppearance()
                    applySymbolKeyboardAppearance()
                    mainView.root.outlineProvider = ViewOutlineProvider.BACKGROUND
                    mainView.root.clipToOutline = keyboardSkinId != KeyboardSkinId.DEFAULT || isKeyboardRounded == true
                    applyKeyboardBackgroundIfNeeded(mainView)
                    setupSuminagashiInkEffect(mainView)
                    ViewCompat.setOnApplyWindowInsetsListener(mainView.root) { _, windowInsets ->
                        val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

                        val normalizedBottomInset = if (supportsNavbarExtension) {
                            insets.bottom
                        } else {
                            0
                        }
                        val updated = systemBottomInset != normalizedBottomInset
                        systemBottomInset = normalizedBottomInset

                        if (updated) {
                            mainLayoutBinding?.let { mainView ->
                                updateKeyboardLayout(mainView)
                            }
                        }

                        windowInsets
                    }
                    setCandidateTabLayout(mainView)
                    setSuggestionRecyclerView(
                        mainView, FlexboxLayoutManager(applicationContext).apply {
                            flexDirection = FlexDirection.ROW
                            justifyContent = JustifyContent.FLEX_START
                        })
                    setShortCutAdapter(mainView)
                    setSymbolKeyboard(mainView)
                    rebindMainKeyboardInputListeners(mainView)
                    hideAllKeyboards()
                    setKeyboardSizeSwitchKeyboard(mainView)
                    updateClipboardPreview()
                    mainView.suggestionRecyclerView.isVisible = suggestionViewStatus.value
                }
            }
        }
    }

    /**
     * Reconnects input listeners after the IME input view is reused.
     *
     * InputMethodService may detach and reattach the same keyboard hierarchy during
     * rotation or an input-view restart. TenKey intentionally clears its listeners when
     * detached, while the service keeps reusing the existing hierarchy. Rebinding all
     * main-surface keyboard listeners here keeps the view lifecycle and the service
     * lifecycle synchronized.
     */
    private fun rebindMainKeyboardInputListeners(mainView: MainLayoutBinding) {
        setupCustomKeyboardListeners(mainView)
    }

    /**
     * Configuration callbacks and input-view detach/attach callbacks can be delivered in
     * either order. Run one more binding pass after the current main-loop turn so a detach
     * that clears view-owned listeners cannot win the race against the service rebind.
     */
    private fun scheduleMainKeyboardInputListenerRebind(mainView: MainLayoutBinding) {
        mainView.root.post {
            if (mainLayoutBinding?.root === mainView.root) {
                rebindMainKeyboardInputListeners(mainView)
            }
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        // Android reports selection from the underlying app, not our local dictionary editor.
        if (dictionaryInputConnection != null) return
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        if (oldSelStart != newSelStart || oldSelEnd != newSelEnd) {
            horizontalCursorSelectionRevision.incrementAndGet()
        }
        forwardDeleteCoordinator.onSelectionChanged(newSelStart, newSelEnd)
        invalidateCustomToggleStateForSelection(newSelStart, newSelEnd)
        // Skip if composing text is active
        if (candidatesStart != -1 || candidatesEnd != -1) {
            return
        }

        updateEditorSelectionSnapshot(
            newSelStart = newSelStart,
            newSelEnd = newSelEnd,
        )

        handleZeroQueryOnUpdateSelection(
            oldSelStart = oldSelStart,
            oldSelEnd = oldSelEnd,
            newSelStart = newSelStart,
            newSelEnd = newSelEnd,
        )

        if (suppressedSelectionCleanupCount > 0) {
            suppressedSelectionCleanupCount -= 1
            Timber.d("onUpdateSelection suppressed: [${inputString.value}] [${stringInTail.get()}]")
            return
        }

        if (editorTextSelected) {
            if (selectedEditorText.isNotEmpty()) {
                handleSelectedTextSelection(selectedEditorText)
            }
            return
        }
        clearSelectedTextClipboardPreviewRefresh()
        if (selectionActionSession != null) {
            clearSelectionActionSession(
                clearSuggestions = hasSelectionActionCandidates()
            )
        }

        val preservedPreEdit = preservePreEditOnNextSelectionUpdate
        if (preservedPreEdit != null) {
            preservePreEditOnNextSelectionUpdate = null
            if (_inputString.value == preservedPreEdit && preservedPreEdit.isNotEmpty()) {
                Timber.d("onUpdateSelection preserve preedit after cancel: $preservedPreEdit")
                refreshReconversionUi()
                return
            }
        }

        Timber.d("onUpdateSelection end called: [${inputString.value}] [${stringInTail.get()}] [${bunsetusMultipleDetect}]")
        if (stringInTail.get().isEmpty()) {
            bunsetusMultipleDetect = false
        }


        updateClipboardPreview()

        val tail = stringInTail.get()
        val hasTail = tail.isNotEmpty()
        val caretTop = newSelStart == 0 && newSelEnd == 0

        Timber.d("onUpdateSelection tail: $tail")

        when {

            hasTail && caretTop -> {
                Timber.d("onUpdateSelection hasTail && caretTop: $tail $caretTop")
                stringInTail.set("")
                if (_inputString.value.isNotEmpty()) {
                    _inputString.update { "" }
                    beginBatchEdit()
                    setComposingText("", 0)
                    endBatchEdit()
                }
                setSuggestionAdapterSuggestionsOnMain(
                    emptyList()
                ) // avoid unnecessary allocations elsewhere
            }

            // Caret moved while tail exists → commit tail
            hasTail -> {
                Timber.d("onUpdateSelection hasTail : $tail")
                scope.launch {
                    _inputString.update { tail }
                    stringInTail.set("")
                }
            }

            // No tail but still holding input → cleanup
            _inputString.value.isNotEmpty() -> {
                Timber.d("onUpdateSelection _inputString.value.isNotEmpty() : $tail")
                _inputString.update { "" }
                beginBatchEdit()
                setComposingText("", 0)
                endBatchEdit()
            }
        }

        // A cursor move can finish the editor's ComposingText without going through one of the
        // IME commit handlers. In that path inputString is already empty, but the candidate
        // adapters and the last refresh request can still describe the old composing session.
        // Clear that state before the next IME show/reopen so an empty editor cannot expose the
        // previous conversion tab or candidates.
        if (stringInTail.get().isEmpty() && _inputString.value.isEmpty()) {
            clearSuggestionStateAfterEditorSelectionChange()
        }
        refreshReconversionUi()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK &&
            consumeKeyboardSelectionPopupBackKeyUp
        ) {
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK &&
            keyboardSelectionPopupWindow?.isShowing == true
        ) {
            // Keep the overlay callback installed through key-up. Removing it on key-down
            // can route the same key-up to Android's default IME-hide callback.
            keyboardSelectionPopupBackKeyTarget = keyboardSelectionPopupWindow
            consumeKeyboardSelectionPopupBackKeyUp = true
            return true
        }
        dictionaryInputEditor?.let { editor ->
            if (event != null && keyCode != KeyEvent.KEYCODE_BACK) {
                switchDictionaryInputTarget(editor)
                editor.dispatchKeyEvent(event)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        dictionaryInputEditor?.let { editor ->
            if (event != null && keyCode != KeyEvent.KEYCODE_BACK) {
                editor.dispatchKeyEvent(event)
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && consumeKeyboardSelectionPopupBackKeyUp) {
            consumeKeyboardSelectionPopupBackKeyUp = false
            val popup = keyboardSelectionPopupBackKeyTarget
            keyboardSelectionPopupBackKeyTarget = null
            if (event?.isCanceled != true) popup?.dismiss()
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                Timber.d("onKeyUp KEYCODE_ENTER: ${inputString.value} ${isHenkan.get()}")
                if (isHenkan.get()) {
                    if (inputString.value.isNotEmpty()) {
                        return true
                    }
                    isHenkan.set(false)
                    henkanPressedWithBunsetsuDetect = false
                }
                return super.onKeyUp(keyCode, event)
            }
        }
        return super.onKeyUp(keyCode, event)
    }


    private fun getNormalKeyboardSurface(): KeyboardSurface? {
        val mainView = mainLayoutBinding ?: return null
        return KeyboardSurface(
            rootView = mainView.root,
            customLayout = mainView.customLayoutDefault,
            suggestionRecyclerView = mainView.suggestionRecyclerView,
            symbolKeyboard = mainView.keyboardSymbolView
        )
    }

    private fun getActiveKeyboardSurface(): KeyboardSurface? {
        return getNormalKeyboardSurface()
    }

    private fun requireActiveKeyboardSurface(): KeyboardSurface? {
        return getActiveKeyboardSurface()
    }

    private fun hideKeyboardViews(surface: KeyboardSurface) {
        surface.customLayout?.isVisible = false
    }

    private fun renderKeyboardMode(
        surface: KeyboardSurface,
        mode: TenKeyQWERTYMode,
        isFloating: Boolean
    ) {
        // Rendering the current mode must not cancel gestures on its already-visible surface.
        when (mode) {

            TenKeyQWERTYMode.Custom,
            TenKeyQWERTYMode.Sumire,
            TenKeyQWERTYMode.Number -> {
                surface.customLayout?.isVisible = true
            }
        }
    }

    private fun renderCurrentKeyboardSurface() {
        val surface = getActiveKeyboardSurface() ?: return
        renderKeyboardMode(
            surface = surface,
            mode = qwertyMode.value,
            isFloating = false
        )
    }

    private fun updateDynamicKeyOnActiveSurface(
        keyId: String,
        stateIndex: Int
    ) {
        getActiveKeyboardSurface()
            ?.customLayout
            ?.updateDynamicKey(
                keyId = keyId,
                stateIndex = stateIndex
            )
    }


    private fun renderDynamicKeysOnActiveSurface() {
        val customLayout = getActiveKeyboardSurface()?.customLayout ?: return
        customLayout.updateDynamicKey(
            keyId = "enter_key",
            stateIndex = currentEnterKeyIndex
        )
        customLayout.updateDynamicKey(
            keyId = "dakuten_toggle_key",
            stateIndex = currentDakutenKeyIndex
        )
        customLayout.updateDynamicKey(
            keyId = "space_convert_key",
            stateIndex = currentSpaceKeyIndex
        )
        customLayout.updateDynamicKey(
            keyId = "katakana_toggle_key",
            stateIndex = currentKatakanaKeyIndex
        )
    }

    private fun setInputModeOnActiveSurface(inputMode: InputMode) {
        // The custom keyboard reads the session input mode directly; nothing to mirror.
    }

    private fun setCurrentInputModeForSession(inputMode: InputMode) {
        currentInputModeForSession = inputMode
        setInputModeOnActiveSurface(inputMode)
    }

    private fun setCurrentQwertyRomajiModeForSession(enabled: Boolean) {
        currentQwertyRomajiModeForSession = enabled
    }

    private fun renderCurrentKeyboardStateOnActiveSurface() {
        renderCurrentKeyboardSurface()
        setInputModeOnActiveSurface(currentInputModeForSession)
        renderDynamicKeysOnActiveSurface()
        refreshBaselineInputBehaviorForCurrentKeyboard("keyboard state rendered")
    }

    private fun refreshActiveSumireLayoutIfNeeded() {
        if (qwertyMode.value != TenKeyQWERTYMode.Sumire) return
        val surface = getActiveKeyboardSurface() ?: return
        val customLayout = surface.customLayout ?: return
        setSumireLayoutTo(customLayout)
        renderDynamicKeysOnActiveSurface()
    }

    private fun defaultInputModeFor(inputType: InputTypeForIME): InputMode {
        return when (inputType) {
            InputTypeForIME.TextEmailAddress,
            InputTypeForIME.TextEditTextInWebView,
            InputTypeForIME.TextPostalAddress,
            InputTypeForIME.TextWebEmailAddress,
            InputTypeForIME.TextPassword,
            InputTypeForIME.TextVisiblePassword,
            InputTypeForIME.TextWebPassword -> InputMode.ModeEnglish

            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberPassword,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time -> InputMode.ModeNumber

            else -> InputMode.ModeJapanese
        }
    }

    private fun createSumireKeyboardLayout(): KeyboardLayout {
        val dynamicStates = mapOf(
            "enter_key" to currentEnterKeyIndex,
            "dakuten_toggle_key" to currentDakutenKeyIndex,
            "katakana_toggle_key" to currentKatakanaKeyIndex,
            "space_convert_key" to currentSpaceKeyIndex,
        )
        val layoutType = sumireInputKeyLayoutType ?: "toggle"
        return applyCircularSlotActionSettings(
            KeyboardDefaultLayouts.createFinalLayout(
                mode = customKeyboardMode,
                dynamicKeyStates = dynamicStates,
                inputLayoutType = layoutType,
                inputStyle = sumireInputStyle ?: "default",
                deleteKeyFlickSettings = currentDeleteKeyFlickSettings()
            ),
            customKeyboardMode
        ).let { baseLayout ->
            SumireSpecialKeyActionDisplayOverrideApplier.apply(
                layout = baseLayout,
                layoutType = layoutType,
                inputMode = customKeyboardMode.name,
                overrides = sumireSpecialKeyActionOverrides,
                displayMetadata = sumireSpecialKeyActionDisplayMetadata()
            )
        }.let { layout ->
            SumireSpecialKeyPlacementOverrideApplier.apply(
                layout = layout,
                layoutType = layoutType,
                inputMode = customKeyboardMode.name,
                overrides = sumireSpecialKeyPlacementOverrides
            )
        }
    }

    private fun sumireSpecialKeyActionDisplayMetadata(): List<SumireSpecialKeyActionDisplayMetadata> {
        return KeyActionMapper.getDisplayActions(this).map {
            SumireSpecialKeyActionDisplayMetadata(
                action = it.action,
                displayName = it.displayName,
                iconResId = it.iconResId
            )
        }
    }

    private fun currentDeleteKeyFlickSettings(): DeleteKeyFlickSettings {
        return DeleteKeyFlickSettings(
            left = isDeleteLeftFlickPreference ?: true,
            up = isDeleteUpFlickPreference ?: false,
            down = isDeleteDownFlickPreference ?: false
        )
    }

    private fun applyDeleteKeyFlickPreferences(layout: KeyboardLayout): KeyboardLayout {
        return KeyboardDefaultLayouts.applyDeleteKeyFlickSettings(
            layout = layout,
            deleteKeyFlickSettings = currentDeleteKeyFlickSettings()
        )
    }

    private fun setKeyboardWithDeleteKeyFlickPreferences(
        flickView: FlickKeyboardView,
        layout: KeyboardLayout,
        isUserDefinedCustomLayout: Boolean = false
    ) {
        flickView.clearSumireSpecialKeyActionResolver()
        flickView.setKeyboard(
            applyDeleteKeyFlickPreferences(layout),
            if (isUserDefinedCustomLayout) customKeyboardHitTestMode() else KeyHitTestMode.KEY_BOUNDS
        )
    }

    private fun syncCustomKeyboardTogglePresentation(
        flickView: FlickKeyboardView,
        shift: CustomKeyboardShiftState = customKeyboardShiftState,
        direct: Boolean = isCustomLayoutDirectMode,
        romaji: Boolean = isCustomLayoutRomajiMode,
    ) {
        renderCustomKeyboardToggles(flickView, shift, direct, romaji)
    }

    private fun syncCustomKeyboardTogglePresentationOnAvailableSurfaces() {
        getNormalKeyboardSurface()
            ?.customLayout
            ?.let { syncCustomKeyboardTogglePresentation(it) }
    }

    private fun applyCurrentFlickGuidePreference(flickView: FlickKeyboardView) {
        val surface = when (qwertyMode.value) {
            TenKeyQWERTYMode.Sumire -> FlickGuideSurface.Sumire
            TenKeyQWERTYMode.Custom -> FlickGuideSurface.Custom
            else -> FlickGuideSurface.Other
        }
        val config = resolveFlickGuideRuntimeConfig(
            surface = surface,
            mode = customKeyboardMode,
            sumireSettings = sumireKeymapGuideSettings,
            customGuideEnabled = customKeymapGuidePreference
        )
        flickView.setFlickGuideEnabled(
            enabled = config.enabled,
            allowMultiCharacterLabels = config.allowMultiCharacterLabels
        )
    }

    private fun setSumireLayoutTo(flickView: FlickKeyboardView) {
        val layoutType = sumireInputKeyLayoutType ?: "toggle"
        flickView.setKeyCharacterCase(KeyCharacterCase.AS_DEFINED)
        applyCurrentFlickGuidePreference(flickView)
        flickView.setSumireSpecialKeyActionResolver(
            resolver = SumireSpecialKeyActionResolver(sumireSpecialKeyActionOverrides)::resolve,
            layoutType = layoutType,
            inputMode = customKeyboardMode.name
        )
        flickView.setKeyboard(createSumireKeyboardLayout(), KeyHitTestMode.NEAREST_KEY)
    }

    private fun setNumberLayoutTo(flickView: FlickKeyboardView) {
        flickView.setKeyCharacterCase(KeyCharacterCase.AS_DEFINED)
        applyCurrentFlickGuidePreference(flickView)
        val numberCustomLayout = numberUsageCustomKeyboardLayoutOrNull()
        if (numberCustomLayout != null) {
            setKeyboardWithDeleteKeyFlickPreferences(
                flickView,
                KeyboardDefaultLayouts.createNumberLayout(currentDeleteKeyFlickSettings())
            )
            flickView.updateDynamicKey("enter_key", editorEnterKeyStateIndex())
            setNumberCustomLayoutTo(flickView, numberCustomLayout)
            return
        }
        numberKeyboardRenderJob?.cancel()
        setKeyboardWithDeleteKeyFlickPreferences(
            flickView,
            KeyboardDefaultLayouts.createNumberLayout(currentDeleteKeyFlickSettings())
        )
        flickView.updateDynamicKey("enter_key", editorEnterKeyStateIndex())
    }

    private fun numberUsageCustomKeyboardLayoutOrNull(): CustomKeyboardLayout? {
        return customLayouts.firstOrNull { layout ->
            layout.usageMode == KeyboardLayoutUsageMode.Number
        }
    }

    private fun setNumberCustomLayoutTo(
        flickView: FlickKeyboardView,
        layout: CustomKeyboardLayout
    ) {
        numberKeyboardRenderJob = scope.launch(Dispatchers.IO) {
            val id = layout.layoutId
            val expectedStableId = layout.stableId
            val dbLayout = runCatching { keyboardRepository.getFullLayout(id).first() }
                .getOrElse {
                    Timber.w(
                        it,
                        "setNumberCustomLayoutTo: layout disappeared id=$id stableId=$expectedStableId"
                    )
                    return@launch
                }
            val finalLayout = keyboardRepository.convertLayout(dbLayout)
            withContext(Dispatchers.Main) {
                val currentNumberLayout = numberUsageCustomKeyboardLayoutOrNull()
                val stillNumberLayout = qwertyMode.value == TenKeyQWERTYMode.Number &&
                        currentNumberLayout?.layoutId == id &&
                        (expectedStableId.isBlank() || currentNumberLayout.stableId == expectedStableId)
                if (!stillNumberLayout) {
                    Timber.d("setNumberCustomLayoutTo: skip stale render id=$id stableId=$expectedStableId")
                    return@withContext
                }
                setKeyboardWithDeleteKeyFlickPreferences(
                    flickView,
                    finalLayout,
                    isUserDefinedCustomLayout = true
                )
            }
        }
    }

    private fun showNumberKeyboardForCurrentInputType() {
        customKeyboardMode = KeyboardInputMode.SYMBOLS
        setCurrentInputModeForSession(InputMode.ModeNumber)
        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Number }
        suggestionAdapter?.updateState(TenKeyQWERTYMode.Number, emptyList())
        mainLayoutBinding?.apply {
            hideAllKeyboards()
            customLayoutDefault.isVisible = true
            setNumberLayoutTo(customLayoutDefault)
            suggestionRecyclerView.isVisible = true
            refreshCandidateStripContent()
        }
        renderCurrentKeyboardStateOnActiveSurface()
    }

    private fun setCurrentCustomLayoutTo(flickView: FlickKeyboardView) {
        applyCurrentFlickGuidePreference(flickView)
        val layout = selectedCustomKeyboardLayoutOrNull() ?: return
        scope.launch(Dispatchers.IO) {
            val id = layout.layoutId
            val expectedStableId = layout.stableId
            val dbLayout = runCatching { keyboardRepository.getFullLayout(id).first() }
                .getOrElse {
                    Timber.w(
                        it,
                        "setCurrentCustomLayoutTo: layout disappeared id=$id stableId=$expectedStableId"
                    )
                    return@launch
                }
            val finalLayout = keyboardRepository.convertLayout(dbLayout)
            isCustomLayoutRomajiMode = resolveInitialCustomKeyboardRomajiMode(
                layoutId = id,
                stableId = expectedStableId,
                defaultValue = finalLayout.isRomaji
            )
            isCustomLayoutDirectMode = resolveInitialCustomKeyboardDirectMode(
                layoutId = id,
                stableId = expectedStableId,
                defaultValue = finalLayout.isDirectMode
            )
            customKeyboardShiftState = CustomKeyboardShiftState.OFF
            withContext(Dispatchers.Main) {
                if (!isCurrentCustomKeyboardSelection(layoutId = id, stableId = expectedStableId)) {
                    return@withContext
                }
                setKeyboardWithDeleteKeyFlickPreferences(
                    flickView,
                    finalLayout,
                    isUserDefinedCustomLayout = true
                )
                syncCustomKeyboardTogglePresentation(flickView)
                refreshBaselineInputBehaviorForCurrentKeyboard("custom layout input mode loaded")
            }
        }
    }

    private fun setCustomLayoutOnActiveSurface(layout: KeyboardLayout) {
        getActiveKeyboardSurface()
            ?.customLayout
            ?.let { flickView ->
                applyCurrentFlickGuidePreference(flickView)
                setKeyboardWithDeleteKeyFlickPreferences(
                    flickView,
                    layout,
                    isUserDefinedCustomLayout = true
                )
            }
    }

    private fun setCustomLayoutOnAvailableSurfaces(layout: KeyboardLayout) {
        resetCustomToggleState()
        getNormalKeyboardSurface()
            ?.customLayout
            ?.let { flickView ->
                setKeyboardWithDeleteKeyFlickPreferences(
                    flickView,
                    layout,
                    isUserDefinedCustomLayout = true
                )
            }
    }

    private fun refreshDeleteKeyFlickPreferenceLayouts() {
        val customLayout = getActiveKeyboardSurface()?.customLayout ?: return
        when (qwertyMode.value) {
            TenKeyQWERTYMode.Sumire -> setSumireLayoutTo(customLayout)
            TenKeyQWERTYMode.Custom -> setCurrentCustomLayoutTo(customLayout)
            TenKeyQWERTYMode.Number -> setNumberLayoutTo(customLayout)
            else -> Unit
        }
    }


    private fun handleTapAndFlick(
        key: Key,
        char: Char?,
        insertString: String,
        sb: StringBuilder,
        isFlick: Boolean,
        gestureType: GestureType,
        suggestions: List<Candidate>,
        mainView: MainLayoutBinding
    ) {
        if (isKeyboardLayoutEditModeActive()) return
        if (deletedBuffer.isNotEmpty() && !selectMode.value && key != Key.SideKeyDelete) {
            clearDeletedBuffer()
            refreshEditHistoryUi()
        } else if (deletedBuffer.isNotEmpty() && selectMode.value && key == Key.SideKeySpace) {
            clearDeletedBufferWithoutResetLayout()
            refreshEditHistoryUi()
        }
        when (key) {
            Key.NotSelected -> {}
            Key.SideKeyEnter -> {
                if (insertString.isNotEmpty()) {
                    handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                } else {
                    handleEmptyInputEnterKey(mainView)
                }
            }

            Key.KeyDakutenSmall -> {
                handleDakutenSmallLetterKey(
                    sb = sb,
                    isFlick = isFlick,
                    char = char,
                    insertString = insertString,
                    mainView = mainView,
                    gestureType = gestureType
                )
            }

            Key.SideKeyCursorLeft -> {
                if (!leftCursorKeyLongKeyPressed.get()) {
                    if (moveFocusedBunsetsuSegment(delta = -1)) {
                    } else if (isHenkan.get()) {
                        handleDeleteKeyInHenkan(suggestions, insertString)
                    } else {
                        handleLeftCursor(gestureType, insertString)
                    }
                }
                cancelLeftLongPress()
            }

            Key.SideKeyCursorRight -> {
                if (!rightCursorKeyLongKeyPressed.get()) {
                    if (moveFocusedBunsetsuSegment(delta = 1)) {
                    } else if (isHenkan.get()) {
                        handleJapaneseModeSpaceKey(
                            mainView, suggestions, insertString
                        )
                    } else {
                        actionInRightKeyPressed(gestureType, insertString)
                    }
                }
                cancelRightLongPress()
            }

            Key.SideKeyDelete -> {
                if (!isFlick) {
                    if (!deleteKeyLongKeyPressed.get()) {
                        handleDeleteKeyTap(insertString, suggestions)
                    }
                } else {
                    when (gestureType) {
                        GestureType.FlickLeft -> {
                            if (isDeleteLeftFlickPreference == true) {
                                deleteWordOrSymbolsBeforeCursor(insertString)
                            }
                        }

                        GestureType.FlickTop -> {
                            if (isDeleteUpFlickPreference == true) {
                                deleteWordOrSymbolsAfterCursor(insertString)
                            }
                        }

                        GestureType.FlickBottom -> {
                            if (isDeleteDownFlickPreference == true) {
                                undoLastHistoryEntry()
                            }
                        }

                        else -> {}
                    }
                }
                stopDeleteLongPress()
            }

            Key.SideKeyInputMode -> {
            }

            Key.SideKeyPreviousChar -> {
                when (currentInputModeForSession) {
                    is InputMode.ModeNumber -> {

                    }

                    else -> {
                        if (!isFlick) setNextReturnInputCharacter(insertString)
                    }
                }
            }

            Key.SideKeySpace -> {
                if (shouldSuppressSpaceConvertTapAfterLongPress()) {
                    finishTenKeyCursorMoveModeAfterLongPressRelease()
                    return
                } else if (cursorMoveMode.value) {
                    _cursorMoveMode.update { false }
                } else {
                    if (!isSpaceKeyLongPressed) {
                        if (gestureType == GestureType.FlickLeft &&
                            cycleFocusedBunsetsuCandidate(delta = -1)
                        ) {
                        } else if (gestureType == GestureType.FlickLeft &&
                            (false || tenkeySpaceFlickPreference)
                        ) {
                            val isHankaku = hankakuPreference == true
                            if (isHankaku) {
                                handleSpaceKeyClick(false, insertString, suggestions, mainView)
                            } else {
                                handleSpaceKeyClick(true, insertString, suggestions, mainView)
                            }
                        } else {
                            val isHankaku = hankakuPreference == true
                            handleSpaceKeyClick(isHankaku, insertString, suggestions, mainView)
                        }
                    }
                }
                isSpaceKeyLongPressed = false
            }

            Key.SideKeySymbol -> {
                _keyboardSymbolViewState.value = SymbolKeyboardState(
                    isShown = !_keyboardSymbolViewState.value.isShown
                )
                stringInTail.set("")
                finishComposingText()
                setComposingText("", 0)
            }

            else -> {
                /** 選択モード **/
                if (selectMode.value) {
                    when (key) {
                        /** コピー **/
                        Key.KeyA -> {
                            copyAction()
                        }
                        /** 切り取り **/
                        Key.KeySA -> {
                            cutAction()
                        }
                        /** 全て選択 **/
                        Key.KeyMA -> {
                            selectAllText()
                        }

                        /** 共有 **/
                        Key.KeyRA -> {
                            shareSelectedTextAction()
                        }
                        /** その他 **/
                        else -> {

                        }
                    }
                } else {
                    if (isFlick) {
                        handleFlick(char, insertString, sb, mainView)
                    } else {
                        handleTap(char, insertString, sb, mainView)
                    }
                }
            }
        }
    }

    private fun handleFlick(
        char: Char?, insertString: String, sb: StringBuilder, _mainView: MainLayoutBinding
    ) {
        if (isHenkan.get()) {
            commitCurrentHenkanForNewInput(currentInputModeForSession)
            char?.let {
                sendCharFlick(
                    charToSend = it, insertString = "", sb = sb
                )
            }
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
        } else {
            char?.let {
                sendCharFlick(
                    charToSend = it, insertString = insertString, sb = sb
                )
            }
            isContinuousTapInputEnabled.set(true)
            lastFlickConvertedNextHiragana.set(true)
        }
    }

    private fun handleTap(
        char: Char?, insertString: String, sb: StringBuilder, _mainView: MainLayoutBinding
    ) {
        char?.let {
            if (dispatchDirectTextIfNeeded(it.toString())) return
        }
        if (isHenkan.get()) {
            commitCurrentHenkanForNewInput(currentInputModeForSession)
            char?.let {
                sendCharTap(
                    charToSend = it, insertString = "", sb = sb
                )
            }
        } else {
            char?.let {
                sendCharTap(
                    charToSend = it, insertString = insertString, sb = sb
                )
            }
        }
    }

    private fun Char.toRomajiQwertyOutputChar(): Char {
        return toRomajiQwertyOutputChar(
            useHankakuNumber = qwertyRomajiHankakuNumberPreference == true,
            useHankakuSymbol = qwertyRomajiHankakuSymbolPreference == true
        )
    }

    private fun Char.shouldApplyRomajiQwertyWidthPreference(): Boolean {
        return isLowerCase() ||
                isAsciiDigitForRomajiQwerty() ||
                isAsciiSymbolForRomajiQwerty()
    }

    private fun Char.shouldUseRomajiQwertyOutputCharAfterShift(): Boolean {
        return !isRomajiShiftPressed || shouldApplyRomajiQwertyWidthPreference()
    }

    private fun Char.toRomajiQwertyFlickOutputChar(): Char {
        return if (currentInputModeForSession == InputMode.ModeJapanese &&
            isDefaultRomajiHenkanMap
        ) {
            toRomajiQwertyOutputChar()
        } else {
            this
        }
    }

    private fun handleLongPress(
        key: Key
    ) {
        if (isKeyboardLayoutEditModeActive()) return
        when (key) {
            Key.NotSelected -> {}
            Key.SideKeyEnter -> {}
            Key.KeyDakutenSmall -> {
                if (tenkeyShowIMEButtonPreference == true) {
                    showListPopup()
                }
            }

            Key.SideKeyCursorLeft -> {
                handleLeftLongPress()
                leftCursorKeyLongKeyPressed.set(true)
                if (selectMode.value) {
                    clearDeletedBufferWithoutResetLayout()
                } else {
                    clearDeletedBuffer()
                }
                refreshEditHistoryUi()
            }

            Key.SideKeyCursorRight -> {
                handleRightLongPress()
                rightCursorKeyLongKeyPressed.set(true)
                if (selectMode.value) {
                    clearDeletedBufferWithoutResetLayout()
                } else {
                    clearDeletedBuffer()
                }
                refreshEditHistoryUi()
            }

            Key.SideKeyDelete -> {
                handleDeleteLongPress()
            }

            Key.SideKeyInputMode -> {}
            Key.SideKeyPreviousChar -> {}
            Key.SideKeySpace -> {
                handleSpaceLongAction()
            }

            Key.SideKeySymbol -> {}
            else -> {}
        }
    }

    private var keyboardSelectionPopupWindow: PopupWindow? = null
    private var imeSwitchPopupWindow: PopupWindow? = null
    private val keyboardPopupRequests = ImePopupRequestTracker()
    private var ngWordRegistrationPopup: PopupWindow? = null
    private var ngWordRegistrationEditors: Set<EditText> = emptySet()

    private data class KeyboardPopupRequest(
        val id: Long,
        val mainView: MainLayoutBinding,
        val connection: InputConnection,
        val revision: Long,
    )

    private fun dismissKeyboardSelectionPopups() {
        keyboardPopupRequests.invalidate()
        keyboardSelectionPopupBackKeyTarget = null
        keyboardSelectionPopupWindow?.dismiss()
        keyboardSelectionPopupWindow = null
        imeSwitchPopupWindow = null
        onKeyboardSwitchLongPressUp = false
        updateKeyboardSelectionPopupBackInvokedCallback(registered = false)
    }

    private fun beginKeyboardPopupRequest(): KeyboardPopupRequest? {
        dismissKeyboardSelectionPopups()
        val mainView = mainLayoutBinding ?: return null
        val connection = currentInputConnection ?: return null
        if (!canShowPopupWindow(resolveShowListPopupAnchor(mainView))) return null
        return KeyboardPopupRequest(
            keyboardPopupRequests.begin(), mainView, connection, editorMutationRevision.current(),
        )
    }

    private fun isKeyboardPopupRequestCurrent(request: KeyboardPopupRequest): Boolean =
        keyboardPopupRequests.isCurrent(request.id) && isInputViewActive &&
            mainLayoutBinding === request.mainView && currentInputConnection === request.connection &&
            editorMutationRevision.isCurrent(request.revision)

    private fun showKeyboardSelectionList(
        request: KeyboardPopupRequest,
        items: List<String>,
        source: String,
        placement: ImeSelectionPopupPlacement = ImeSelectionPopupPlacement.SCREEN_CENTER,
        maxVisibleItems: Int = 5,
        onSelected: (Int) -> Unit,
    ): Boolean {
        if (!isKeyboardPopupRequestCurrent(request)) return false
        val popupView = layoutInflater.inflate(R.layout.popup_list_layout, request.mainView.root, false)
        val list = popupView.findViewById<ListView>(R.id.popup_listview).apply {
            choiceMode = ListView.CHOICE_MODE_SINGLE
            adapter = createKeyboardFontArrayAdapter(this@IMEService, R.layout.list_item_layout, items)
        }
        val reference = when (placement) {
            ImeSelectionPopupPlacement.TOOLBAR_DROPDOWN -> resolveSelectionToolbarAnchor(request.mainView)
            ImeSelectionPopupPlacement.KEYBOARD_CENTER -> requireActiveKeyboardSurface()?.rootView
            else -> null
        }
        val popup = ImeSelectionPopupWindow(this, popupView, placement, reference, maxVisibleItems)
        list.setOnItemClickListener { _, _, position, _ ->
            val valid = isKeyboardPopupRequestCurrent(request)
            popup.dismiss()
            if (valid && position in items.indices) onSelected(position)
        }
        return showKeyboardSelectionPopup(
            popup, resolveShowListPopupAnchor(request.mainView), Gravity.CENTER, 0, source,
        )
    }

    private fun resolveKeyboardSelectionReferenceViews(mainView: MainLayoutBinding): List<View> {
        val surface = requireActiveKeyboardSurface()
        // Dictionary/split panels can expand the IME host to the full screen. Center in the
        // visible keyboard and candidate chrome rather than that transparent host.
        val visible = listOf(mainView.keyboardBackgroundContainer, mainView.suggestionViewParent,
            mainView.candidateTabLayout, mainView.shortcutToolbarRecyclerview).filter { it.isShown }
        return visible.ifEmpty { listOf(surface?.rootView ?: mainView.root) }
    }

    private fun resolveSelectionToolbarAnchor(mainView: MainLayoutBinding): View {
        val toolbar = mainView.shortcutToolbarRecyclerview
        val visible = Rect()
        if (toolbar.isShown && toolbar.getGlobalVisibleRect(visible)) return toolbar
        return requireActiveKeyboardSurface()?.suggestionRecyclerView?.takeIf {
            it.isShown && it.getGlobalVisibleRect(visible)
        } ?: requireActiveKeyboardSurface()?.rootView ?: mainView.root
    }

    private fun showKeyboardSelectionPopup(
        popup: PopupWindow,
        anchor: View?,
        gravity: Int,
        y: Int,
        source: String,
        x: Int = 0,
        onDismiss: () -> Unit = {},
    ): Boolean {
        replaceKeyboardSelectionPopupWindow(popup)
        popup.setOnDismissListener {
            onDismiss()
            if (keyboardSelectionPopupWindow === popup) {
                keyboardPopupRequests.invalidate()
                keyboardSelectionPopupWindow = null
                onKeyboardSwitchLongPressUp = false
                updateKeyboardSelectionPopupBackInvokedCallback(registered = false)
            }
            if (imeSwitchPopupWindow === popup) imeSwitchPopupWindow = null
        }
        val shown = showPopupWindowSafely(popup, anchor, gravity, x, y, source)
        if (shown && popup.isShowing) {
            onKeyboardSwitchLongPressUp = true
            updateKeyboardSelectionPopupBackInvokedCallback(registered = true, popupWindow = popup)
            return true
        }
        // PopupWindow.dismiss() is a no-op if the window never became visible.
        popup.dismiss()
        onDismiss()
        if (keyboardSelectionPopupWindow === popup) {
            keyboardSelectionPopupWindow = null
            keyboardPopupRequests.invalidate()
            onKeyboardSwitchLongPressUp = false
            updateKeyboardSelectionPopupBackInvokedCallback(registered = false)
        }
        return false
    }

    private fun replaceKeyboardSelectionPopupWindow(popupWindow: PopupWindow) {
        // These popups share one slot. Dismiss the old window before replacing its reference.
        keyboardSelectionPopupWindow?.dismiss()
        imeSwitchPopupWindow = null
        updateKeyboardSelectionPopupBackInvokedCallback(registered = false)
        keyboardSelectionPopupWindow = popupWindow
        applyLocalFontToPopupContent(popupWindow.contentView)
    }

    private fun applyLocalFontToPopupContent(root: View) {
        KeyboardFontApplicator.applyToKeyboardViews(root, KeyboardFontApplicator.processSnapshot)
    }

    /** ArrayAdapter creates rows lazily, so style each row when ListView/Spinner asks for it. */
    private fun <T> createKeyboardFontArrayAdapter(
        context: Context,
        layout: Int,
        items: List<T>,
    ): ArrayAdapter<T> = object : ArrayAdapter<T>(context, layout, items) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            super.getView(position, convertView, parent).also(::applyLocalFontToPopupContent)

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            super.getDropDownView(position, convertView, parent).also(::applyLocalFontToPopupContent)
    }

    private fun updateKeyboardSelectionPopupBackInvokedCallback(
        registered: Boolean,
        popupWindow: PopupWindow? = null,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val dispatcher = window.window?.onBackInvokedDispatcher ?: return
        if (registered) {
            if (isKeyboardSelectionPopupBackInvokedCallbackRegistered) return
            val targetPopupWindow = requireNotNull(popupWindow)
            val callback = OnBackInvokedCallback {
                // Predictive Back may complete without another key-up reaching the IME.
                consumeKeyboardSelectionPopupBackKeyUp = false
                keyboardSelectionPopupBackKeyTarget = null
                targetPopupWindow.takeIf { it.isShowing }?.dismiss()
            }.also { keyboardSelectionPopupBackInvokedCallback = it }
            dispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback,
            )
            isKeyboardSelectionPopupBackInvokedCallbackRegistered = true
        } else {
            if (!isKeyboardSelectionPopupBackInvokedCallbackRegistered) return
            keyboardSelectionPopupBackInvokedCallback?.let(dispatcher::unregisterOnBackInvokedCallback)
            keyboardSelectionPopupBackInvokedCallback = null
            isKeyboardSelectionPopupBackInvokedCallbackRegistered = false
        }
    }

    private fun shouldShowCandidateLongPressActions(candidate: Candidate): Boolean {
        if (candidate.type == CANDIDATE_TYPE_TEXT_MACRO) return false
        // Any text candidate may correspond to a learned entry (learning also feeds the
        // conversion lattice, so learned outputs do not always carry the learned type).
        return candidate.string.isNotEmpty()
    }

    private fun showCandidateLongPressActions(
        insertString: String, candidate: Candidate, candidatePosition: Int
    ) {
        val request = beginKeyboardPopupRequest() ?: return
        if (candidate.type == CANDIDATE_TYPE_NEXT_WORD) {
            val reading = candidate.yomi.orEmpty()
            val entry = LearnEntity(input = reading, out = candidate.string)
            showCandidateLongPressActionsPopup(insertString, candidate, request, entry)
            return
        }
        val readings = learnedCandidateReadings(insertString, candidate)
        val outputs = listOf(candidate.string, candidate.commitText).distinct()
        ioScope.launch {
            val learned = try {
                findLearnedEntryForCandidate(readings, outputs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "learned entry lookup failed")
                null
            }
            withContext(Dispatchers.Main) {
                if (!isKeyboardPopupRequestCurrent(request)) return@withContext
                if (learned == null && isNgWordEnable != true) return@withContext
                showCandidateLongPressActionsPopup(insertString, candidate, request, learned)
            }
        }
    }

    private suspend fun findLearnedEntryForCandidate(
        readings: List<String>,
        outputs: List<String>,
    ): LearnEntity? {
        for (reading in readings) {
            for (output in outputs) {
                learnRepository.findLearnDataByInputAndOutput(reading, output)?.let { return it }
            }
        }
        return null
    }

    private suspend fun reportKeyboardPopupFailure(request: KeyboardPopupRequest, exception: Exception) {
        withContext(Dispatchers.Main) {
            if (isKeyboardPopupRequestCurrent(request)) {
                dismissKeyboardSelectionPopups()
                Timber.w(exception, "IME popup could not be loaded")
                showToastMessage(getString(R.string.ime_popup_unavailable))
            }
        }
    }

    private fun showCandidateLongPressActionsPopup(
        insertString: String,
        candidate: Candidate,
        request: KeyboardPopupRequest,
        learnedEntry: LearnEntity?,
    ) {
        val actions = buildList {
            if (learnedEntry != null) {
                add(CandidateLongPressAction.ForgetLearnedEntry)
            }
            if (isNgWordEnable == true) {
                add(CandidateLongPressAction.HideWord)
            }
            add(CandidateLongPressAction.Close)
        }

        val items = actions.map { action ->
            when (action) {
                CandidateLongPressAction.ForgetLearnedEntry ->
                    getString(R.string.candidate_action_forget_learning)
                CandidateLongPressAction.HideWord -> getString(R.string.candidate_action_hide_word)
                CandidateLongPressAction.Close -> getString(R.string.candidate_action_close)
            }
        }

        showKeyboardSelectionList(request, items, "candidate long press") { position ->
            val selectedAction = actions.getOrNull(position)
            when (selectedAction) {
                CandidateLongPressAction.ForgetLearnedEntry -> {
                    val entry = learnedEntry ?: return@showKeyboardSelectionList
                    // Hide it right away: the refresh below can be skipped or superseded (henkan
                    // state, a newer request), and must never leave the deleted word visible.
                    removeCandidateFromVisibleLists(candidate)
                    ioScope.launch {
                        if (candidate.type == CANDIDATE_TYPE_NEXT_WORD) {
                            nextWordRepository.delete(reading = entry.input, output = entry.out)
                        } else {
                            learnRepository.deleteByInputAndOutput(
                                input = entry.input,
                                output = entry.out,
                            )
                        }
                        withContext(Dispatchers.Main) {
                            necookeyZenzOverrideCacheClear()
                            requestCandidateRefresh(CandidateShowFlag.Updating)
                        }
                    }
                }

                CandidateLongPressAction.HideWord -> {
                    showNgWordRegistrationPopup(insertString, candidate)
                }

                CandidateLongPressAction.Close, null -> Unit
            }
        }
    }

    private fun showNgWordRegistrationPopup(
        insertString: String,
        candidate: Candidate,
    ): Boolean {
        val request = beginKeyboardPopupRequest() ?: return false
        val mainView = request.mainView
        val anchor = resolveShowListPopupAnchor(mainView) ?: return false
        val popupView = LayoutInflater.from(mainView.root.context).inflate(
            R.layout.popup_ng_word_registration, mainView.root, false,
        )
        val yomi = popupView.findViewById<ImeLocalTextInputEditText>(R.id.edit_text_ng_word_yomi_registration)
        val tango = popupView.findViewById<ImeLocalTextInputEditText>(R.id.edit_text_ng_word_tango_registration)
        val modes = popupView.findViewById<RadioGroup>(R.id.ng_word_match_mode_registration)
        yomi.setText(insertString)
        tango.setText(candidate.string)
        modes.check(R.id.ng_word_match_partial)
        val editors = setOf<EditText>(yomi, tango)
        for (editor in listOf(yomi, tango)) {
            editor.showSoftInputOnFocus = false
            editor.onSelectionChangedListener = ::onDictionaryEditorSelectionChanged
            editor.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN && editor in ngWordRegistrationEditors &&
                    ngWordRegistrationPopup?.isShowing == true && dictionaryInputEditor !== editor) {
                    switchDictionaryInputTarget(editor)
                }
                false
            }
            editor.onFocusChangeListener = View.OnFocusChangeListener { _, focused ->
                if (focused && editor in ngWordRegistrationEditors && ngWordRegistrationPopup?.isShowing == true &&
                    dictionaryInputEditor !== editor) switchDictionaryInputTarget(editor)
            }
        }

        // Keep a bounded, scrollable form above the active keyboard so it remains usable for editing.
        val available = Rect().also(anchor::getWindowVisibleDisplayFrame)
        val keyboardLocation = IntArray(2)
        (requireActiveKeyboardSurface()?.rootView ?: mainView.root).getLocationOnScreen(keyboardLocation)
        available.bottom = minOf(available.bottom, keyboardLocation[1])
        val margin = (12 * resources.displayMetrics.density).toInt()
        available.inset(margin, margin)
        if (available.width() <= 0 || available.height() < margin * 4) {
            showToastMessage(getString(R.string.ime_popup_unavailable))
            return false
        }
        val formWidth = minOf((320 * resources.displayMetrics.density).toInt(), available.width())
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(formWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(available.height(), View.MeasureSpec.AT_MOST),
        )
        val popup = PopupWindow(popupView, formWidth, popupView.measuredHeight, false).apply {
            setBackgroundDrawable(popupView.background)
            isOutsideTouchable = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setIsLaidOutInScreen(true)
        }
        ngWordRegistrationPopup = popup
        ngWordRegistrationEditors = editors
        popupView.findViewById<View>(R.id.button_ng_word_registration_cancel)
            .setOnClickListener { popup.dismiss() }
        popupView.findViewById<View>(R.id.button_ng_word_registration_save)
            .setOnClickListener {
                if (ngWordRegistrationPopup !== popup) return@setOnClickListener
                finishComposingText()
                val reading = yomi.text.toString().trim()
                val word = tango.text.toString().trim()
                if (reading.isEmpty() || word.isEmpty()) {
                    showToastMessage(getString(R.string.ng_word_empty_input_message))
                    return@setOnClickListener
                }
                val mode = if (modes.checkedRadioButtonId == R.id.ng_word_match_exact) {
                    NgWordMatchMode.EXACT
                } else {
                    NgWordMatchMode.PARTIAL
                }
                popup.dismiss()
                registerNgWord(reading, word, mode)
            }
        var x = available.left + (available.width() - formWidth) / 2
        var y = available.top + (available.height() - popupView.measuredHeight) / 2
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val screen = IntArray(2).also(anchor::getLocationOnScreen)
            val inWindow = IntArray(2).also(anchor::getLocationInWindow)
            x -= screen[0] - inWindow[0]
            y -= screen[1] - inWindow[1]
        }
        val shown = showKeyboardSelectionPopup(
            popup, anchor, Gravity.TOP or Gravity.LEFT, y, "NG word registration", x = x,
            onDismiss = {
                if (ngWordRegistrationPopup === popup) {
                    ngWordRegistrationPopup = null
                    ngWordRegistrationEditors = emptySet()
                    if (dictionaryInputEditor in editors) switchDictionaryInputTarget(null)
                }
            },
        )
        if (shown) {
            yomi.requestFocus()
            switchDictionaryInputTarget(yomi)
        } else {
            showToastMessage(getString(R.string.ime_popup_unavailable))
        }
        return shown
    }

    private fun registerNgWord(
        yomi: String,
        tango: String,
        matchMode: NgWordMatchMode,
    ) {
        ioScope.launch {
            val exists = ngWordRepository.exists(yomi = yomi, tango = tango)
            if (exists) {
                showToastMessage(getString(R.string.ng_word_already_registered_message))
                return@launch
            }
            ngWordRepository.addNgWord(
                yomi = yomi,
                tango = tango,
                matchMode = matchMode,
            )
            withContext(Dispatchers.Main) {
                requestCandidateRefresh(CandidateShowFlag.Updating)
            }
        }
    }

    /**
     * Readings a learned entry for [candidate] may be stored under, most specific first. The
     * candidate's own yomi wins (prediction candidates are longer than the typed input, so the
     * typed prefix is not their reading).
     */
    private fun learnedCandidateReadings(
        insertString: String,
        candidate: Candidate,
    ): List<String> = buildList {
        candidate.yomi?.takeIf { it.isNotEmpty() }?.let(::add)
        val session = bunsetsuConversionSession
        if (session != null && isBunsetsuCursorMoveSessionActive() && session.segments.isNotEmpty()) {
            add(session.segments[session.focusedIndex.coerceIn(0, session.segments.lastIndex)].reading)
        }
        val readingLength = candidate.length.toInt().coerceAtMost(insertString.length)
        insertString.take(readingLength).takeIf { it.isNotEmpty() }?.let(::add)
    }.distinct()

    /** Drops [candidate] from every list currently on screen (top row, full list, bottom row). */
    private fun removeCandidateFromVisibleLists(candidate: Candidate) {
        val sameWord: (Candidate) -> Boolean = { it.string == candidate.string && it.type != CANDIDATE_TYPE_TEXT_MACRO }
        currentCandidateStripCandidates = currentCandidateStripCandidates.filterNot(sameWord)
        currentCandidateStripFullCandidates = currentCandidateStripFullCandidates.filterNot(sameWord)
        necookeyPredictionRowCandidates = necookeyPredictionRowCandidates.filterNot(sameWord)
        refreshCandidateStripContent()
    }

    private fun necookeyZenzOverrideCacheClear() {
        synchronized(necookeyZenzOverrideCache) { necookeyZenzOverrideCache.clear() }
    }

    private fun handleSelectedTextSelection(selectedText: String) {
        if (selectedTextClipboardPreviewRefreshText == selectedText) {
            clearSelectionActionSession(
                clearSuggestions = hasSelectionActionCandidates()
            )
            updateClipboardPreview()
            return
        }
        clearSelectedTextClipboardPreviewRefresh()
        if (selectionActionSession?.selectedText != null &&
            selectionActionSession?.selectedText != selectedText
        ) {
            clearSelectionActionSession(
                clearSuggestions = hasSelectionActionCandidates()
            )
        }
        if (isTextMacroCandidateEnable) {
            showSelectionActions(selectedText)
        } else {
            clearSelectionActionSession(
                clearSuggestions = hasSelectionActionCandidates()
            )
        }
    }

    private fun showSelectionActions(selectedText: String) {
        clearZeroQueryAllState(refresh = false)
        if (!isTextMacroCandidateEnable) {
            clearSelectionActionSession(
                clearSuggestions = hasSelectionActionCandidates()
            )
            return
        }
        if (selectionActionSession?.selectedText == selectedText &&
            currentCandidateStripCandidates.any { isSelectionActionCandidate(it) }
        ) {
            return
        }

        val requestId = selectionActionMenuRequestId.incrementAndGet()
        ioScope.launch {
            val localMacroEntries = if (
                isTextMacroCandidateEnable &&
                !isPrivateMode &&
                currentInputType !in passwordTypes
            ) {
                textMacroRepository.getEnabledSelectionMacros(limit = 8).mapNotNull { macro ->
                    val compiled = runCatching { TextMacroCompiler.compile(macro.body) }
                        .getOrNull() ?: return@mapNotNull null
                    if (TextMacroContextRequirement.SELECTION !in compiled.requirements) {
                        return@mapNotNull null
                    }
                    SelectionActionEntry(
                        candidate = Candidate(
                            string = macro.name,
                            type = CANDIDATE_TYPE_TEXT_MACRO,
                            length = selectedText.length
                                .coerceAtMost(UByte.MAX_VALUE.toInt())
                                .toUByte(),
                            score = Int.MAX_VALUE,
                            sourceId = macro.id,
                        ),
                        action = SelectionAction.TextMacro(macro.id),
                    )
                }
            } else {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                if (selectionActionMenuRequestId.get() != requestId) return@withContext
                val currentSelection = selectedEditorText
                if (currentSelection != selectedText) return@withContext

                val session = SelectionActionSessionComposer.compose(
                    selectedText = selectedText,
                    localMacros = localMacroEntries,
                )
                if (session == null) {
                    clearSelectionActionSession(
                        clearSuggestions = hasSelectionActionCandidates()
                    )
                    return@withContext
                }

                selectionActionSession = session
                setSuggestionAdaptersOnMain(
                    session.entries.map(SelectionActionEntry::candidate)
                )
                suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
                suggestionAdapterFull?.updateHighlightPosition(RecyclerView.NO_POSITION)
            }
        }
    }

    private fun hasSelectionActionCandidates(): Boolean {
        return currentCandidateStripCandidates.any(::isSelectionActionCandidate) ||
            currentCandidateStripFullCandidates.any(::isSelectionActionCandidate)
    }

    private fun markClipboardPreviewRefreshAfterPrimaryClipChanged() {
        selectedTextClipboardPreviewRefreshText =
            selectedEditorText.takeIf { it.isNotEmpty() }
        if (selectedTextClipboardPreviewRefreshText != null) {
            clearSelectionActionSession(
                clearSuggestions = hasSelectionActionCandidates()
            )
        }
    }

    private fun clearSelectedTextClipboardPreviewRefresh() {
        selectedTextClipboardPreviewRefreshText = null
    }

    private fun clearSelectionActionSession(clearSuggestions: Boolean) {
        clearZeroQueryAllState(refresh = false)
        selectionActionMenuRequestId.incrementAndGet()
        selectionActionSession = null
        if (!clearSuggestions) return
        setSuggestionAdaptersOnMain(emptyList())
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        suggestionAdapterFull?.updateHighlightPosition(RecyclerView.NO_POSITION)
    }

    private fun handleSelectionActionClick(candidate: Candidate, position: Int): Boolean {
        val session = selectionActionSession ?: return false
        val entry = session.entryFor(candidate, position) ?: return false
        when (val action = entry.action) {
            is SelectionAction.TextMacro -> executeTextMacro(action.id)
        }
        return true
    }

    private fun isSelectionActionCandidate(candidate: Candidate): Boolean {
        return candidate.type == CANDIDATE_TYPE_TEXT_MACRO && candidate.yomi == null
    }

    private fun setSuggestionProgressVisible(
        reason: SuggestionProgressReason,
        visible: Boolean
    ) {
        if (visible) {
            suggestionProgressReasons += reason
        } else {
            suggestionProgressReasons -= reason
        }
        refreshSuggestionProgressVisibility()
    }

    private fun refreshSuggestionProgressVisibility() {
        mainLayoutBinding?.suggestionProgressbar?.isVisible =
            suggestionProgressReasons.isNotEmpty()
    }

    private fun showToastMessage(message: String) {
        scope.launch(Dispatchers.Main) {
            Toast.makeText(this@IMEService, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showListPopup() {
        if (inputString.value.isNotEmpty()) return
        if (beginKeyboardPopupRequest() == null) return

        mainLayoutBinding?.let { mainView ->
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val popupView = inflater.inflate(R.layout.popup_list_layout, mainView.root, false)

            when (keyboardThemeMode) {
                "custom" -> popupView.setDrawableSolidColor(customThemeKeyColor ?: Color.WHITE)
            }

            val listView = popupView.findViewById<ListView>(R.id.popup_listview)
            listView.choiceMode = ListView.CHOICE_MODE_SINGLE

            // --- 1) 行データを構築（内部→外部の順） ---
            val internalOrder = keyboardOrder
            val internalRows: List<RowItem.Internal> = internalOrder.map { type ->
                val title = getKeyboardDisplayName(type)
                RowItem.Internal(type = type, title = title)
            }

            val externalRows: List<RowItem.External> = listEnabledImeItems()
                // 任意: 音声入力など除外したい場合は filter を足す
                // .filter { it.packageName != "com.google.android.tts" }
                .map { RowItem.External(it) }

            val rows: List<RowItem> = internalRows + externalRows
            val internalCount = internalRows.size

            Timber.d("Popup rows size=${rows.size}, internalCount=$internalCount")
            Timber.d("get all IME list: [${listEnabledImeItems()}]")

            // --- 2) 2行表示アダプタ ---
            val adapter = object : ArrayAdapter<RowItem>(
                this@IMEService,
                R.layout.list_item_keyboard_switch_popup,
                android.R.id.text1,
                rows
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val text1 = view.findViewById<TextView>(android.R.id.text1)

                    when (val item = getItem(position)!!) {
                        is RowItem.Internal -> {
                            text1.text = item.title
                        }

                        is RowItem.External -> {
                            text1.text = item.ime.label
                        }
                    }

                    // --- custom テーマ対応（選択色・文字色） ---
                    if (keyboardThemeMode == "custom") {
                        val baseBg = customThemeKeyColor ?: Color.WHITE
                        val baseText = customThemeKeyTextColor ?: Color.BLACK

                        val listParent = parent as ListView
                        val checked = listParent.isItemChecked(position)

                        if (checked) {
                            val highlightColor =
                                manipulateColor(customThemeSpecialKeyColor ?: Color.LTGRAY, 1.2f)
                            view.setBackgroundColor(highlightColor)
                            text1.setTextColor(customThemeSpecialKeyTextColor ?: baseText)
                        } else {
                            view.setBackgroundColor(baseBg)
                            text1.setTextColor(baseText)
                        }
                    }

                    applyLocalFontToPopupContent(view)
                    return view
                }
            }

            listView.adapter = adapter
            val popupWindow = ImeSelectionPopupWindow(this, popupView,
                ImeSelectionPopupPlacement.KEYBOARD_CENTER, requireActiveKeyboardSurface()?.rootView ?: mainView.root,
                referenceViews = resolveKeyboardSelectionReferenceViews(mainView))
            replaceKeyboardSelectionPopupWindow(popupWindow)
            imeSwitchPopupWindow = popupWindow
            onKeyboardSwitchLongPressUp = true

            // 既存の「内部キーボードの選択状態」を復元（範囲チェック必須）
            if (currentKeyboardOrder in 0 until internalCount) {
                listView.setItemChecked(currentKeyboardOrder, true)
            }

            // --- 4) クリック処理（内部→今まで通り / 外部→IME切替） ---
            listView.setOnItemClickListener { _, _, position, _ ->
                onKeyboardSwitchLongPressUp = false
                popupWindow.dismiss()

                when (val row = rows[position]) {
                    is RowItem.Internal -> {
                        // 既存挙動：内部キーボード切替
                        currentKeyboardOrder = position
                        if (enableShowLastShownKeyboardInRestart == true) {
                            appPreference.save_last_used_keyboard_position_preference = position
                        }

                        val nextType = row.type
                        when (nextType) {

                            KeyboardType.CUSTOM -> { /* 任意 */
                            }
                        }

                        showKeyboard(nextType, source = "showListPopup")
                        setKeyboardSizeSwitchKeyboard(mainView)
                    }

                    is RowItem.External -> {
                        // 外部IMEへ切替（API 28+ 推奨、失敗時はピッカーへ）
                        val imeId = row.ime.id
                        runCatching {
                            if (Build.VERSION.SDK_INT >= 28) {
                                switchInputMethod(imeId)
                            } else {
                                showKeyboardPicker()
                            }
                        }.onFailure {
                            showKeyboardPicker()
                        }
                    }
                }
            }

            popupWindow.setOnDismissListener {
                onKeyboardSwitchLongPressUp = false
                updateKeyboardSelectionPopupBackInvokedCallback(registered = false)
                if (keyboardSelectionPopupWindow === popupWindow) {
                    keyboardSelectionPopupWindow = null
                }
                if (imeSwitchPopupWindow === popupWindow) {
                    imeSwitchPopupWindow = null
                }
            }

            val shown = showPopupWindowSafely(
                popupWindow = popupWindow,
                anchorView = resolveShowListPopupAnchor(mainView),
                gravity = Gravity.CENTER,
                x = 0,
                y = 0,
                source = "showListPopup"
            )
            if (shown && popupWindow.isShowing) {
                updateKeyboardSelectionPopupBackInvokedCallback(
                    registered = true,
                    popupWindow = popupWindow,
                )
            } else {
                if (popupWindow.isShowing) {
                    runCatching { popupWindow.dismiss() }
                }
                onKeyboardSwitchLongPressUp = false
                if (keyboardSelectionPopupWindow === popupWindow) {
                    keyboardSelectionPopupWindow = null
                }
                if (imeSwitchPopupWindow === popupWindow) {
                    imeSwitchPopupWindow = null
                }
            }
        }
    }

    private fun InputMethodService.listEnabledImeItems(): List<ImeItem> {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val pm = packageManager

        return imm.enabledInputMethodList
            .asSequence()
            // 自分自身を除外（任意）
            .filter { it.packageName != packageName }
            .map { imi: InputMethodInfo ->
                ImeItem(
                    id = imi.id,
                    packageName = imi.packageName,
                    settingsActivity = imi.settingsActivity, // null のIMEもある
                    label = imi.loadLabel(pm)
                )
            }
            .sortedBy { it.label.toString() }
            .toList()
    }

    /**
     * 色の明るさを調整するヘルパー関数
     * @param color 元の色
     * @param factor 1.0より大＝明るく、1.0より小＝暗く
     */
    private fun manipulateColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }


    private fun showUserTemplateListPopup() {
        if (inputString.value.isNotEmpty()) return
        val request = beginKeyboardPopupRequest() ?: return
        ioScope.launch {
            try {
                val templates = userTemplateRepository.allTemplatesSuspend()
                withContext(Dispatchers.Main) {
                    if (!isKeyboardPopupRequestCurrent(request)) return@withContext
                    if (templates.isEmpty()) {
                        dismissKeyboardSelectionPopups()
                        showToastMessage(getString(R.string.ime_no_templates_registered))
                        return@withContext
                    }
                    showKeyboardSelectionList(request, templates.map { it.word }, "templates", ImeSelectionPopupPlacement.TOOLBAR_DROPDOWN) { position ->
                        commitText(templates[position].word, 1)
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                reportKeyboardPopupFailure(request, exception)
            }
        }
    }

    private fun showCurrentDateListPopup() {
        if (inputString.value.isNotEmpty()) return
        val request = beginKeyboardPopupRequest() ?: return
        val dates = createDateStrings(Calendar.getInstance())
        showKeyboardSelectionList(request, dates, "dates", ImeSelectionPopupPlacement.TOOLBAR_DROPDOWN) { position -> commitText(dates[position], 1) }
    }

    private fun createDateStrings(calendar: Calendar): List<String> {
        val formatter1 = SimpleDateFormat("M/d", Locale.getDefault())
        val formatter2 = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val formatter3 = SimpleDateFormat("M月d日(EEE)", Locale.getDefault())
        val formatterReiwa =
            "令和${calendar.get(Calendar.YEAR) - 2018}年${calendar.get(Calendar.MONTH) + 1}月${
                calendar.get(Calendar.DAY_OF_MONTH)
            }日"
        val formatterR06 = "R${calendar.get(Calendar.YEAR) - 2018}/${
            String.format(
                Locale.getDefault(), "%02d", calendar.get(Calendar.MONTH) + 1
            )
        }/${String.format(Locale.getDefault(), "%02d", calendar.get(Calendar.DAY_OF_MONTH))}"
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)

        // Candidateオブジェクトを作成せず、文字列を直接リストにして返す
        return listOf(
            formatter1.format(calendar.time),  // M/d
            formatter2.format(calendar.time),  // yyyy/MM/dd
            formatter3.format(calendar.time),  // M月d日(EEE)
            formatterReiwa,                    // 令和n年M月d日
            formatterR06,                      // Rxx/MM/dd
            dayOfWeek                          // EEEE (曜日)
        )
    }

    private fun handleDeleteLongPress() {
        if (isKeyboardLayoutEditModeActive()) return
        if (isHenkan.get()) {
            cancelHenkanByLongPressDeleteKey()
            hasConvertedKatakana = isLiveConversionEnable == true
            bunsetusMultipleDetect = false
        } else {
            onDeleteLongPressUp.set(true)
            deleteLongPress()
            _dakutenPressed.value = false
            englishSpaceKeyPressed.set(false)
            deleteKeyLongKeyPressed.set(true)
        }
    }

    private fun markSpaceConvertLongPressConsumed() {
        isSpaceKeyLongPressed = true
        suppressSpaceConvertTapUntilUptimeMillis = SystemClock.uptimeMillis() + 700L
    }

    private fun enterTenKeyCursorMoveMode() {
        _cursorMoveMode.update { true }
    }

    private fun finishTenKeyCursorMoveModeAfterLongPressRelease() {
        _cursorMoveMode.update { false }
    }

    private fun enterSpaceConvertCursorMoveMode(
        source: SpaceConvertCursorMoveSource,
        enterCursorMoveMode: () -> Unit
    ): Boolean {
        if (!SpaceConvertCursorMovePolicy.shouldEnterCursorMoveMode(
                conversionKeySwipeCursorMovePreference = conversionKeySwipePreference,
                hasInputString = inputString.value.isNotEmpty(),
                source = source
            )
        ) {
            return false
        }
        enterCursorMoveMode()
        return true
    }

    private fun shouldSuppressSpaceConvertTapAfterLongPress(): Boolean {
        if (suppressSpaceConvertTapUntilUptimeMillis <= 0L) return false
        val shouldSuppress = SystemClock.uptimeMillis() <= suppressSpaceConvertTapUntilUptimeMillis
        suppressSpaceConvertTapUntilUptimeMillis = 0L
        if (shouldSuppress) {
            isSpaceKeyLongPressed = false
        }
        return shouldSuppress
    }

    private fun handleSpaceLongAction() {
        Timber.d("SideKeySpace LongPress: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
        if (switchBunsetsuSplitPattern()) {
            markSpaceConvertLongPressConsumed()
            return
        }
        val insertString = inputString.value
        markSpaceConvertLongPressConsumed()
        if (insertString.isNotEmpty()) {
            if (conversionKeySwipePreference == true) {
                if (!isHenkan.get()) {
                    enterTenKeyCursorMoveMode()
                }
            } else {
                mainLayoutBinding?.let {
                    if (currentInputModeForSession == InputMode.ModeJapanese) {
                        if (isHenkan.get()) return
                        if (hasConvertedKatakana) {
                            if (isLiveConversionEnable == true) {
                                applyFirstSuggestion(
                                    Candidate(
                                        string = insertString.hiraganaToKatakana(),
                                        type = (3).toByte(),
                                        length = insertString.length.toUByte(),
                                        score = 4000
                                    )
                                )
                            } else {
                                applyFirstSuggestion(
                                    Candidate(
                                        string = insertString,
                                        type = (3).toByte(),
                                        length = insertString.length.toUByte(),
                                        score = 4000
                                    )
                                )
                            }
                        } else {
                            if (isLiveConversionEnable == true) {
                                applyFirstSuggestion(
                                    Candidate(
                                        string = insertString,
                                        type = (3).toByte(),
                                        length = insertString.length.toUByte(),
                                        score = 4000
                                    )
                                )
                            } else {
                                applyFirstSuggestion(
                                    Candidate(
                                        string = insertString.hiraganaToKatakana(),
                                        type = (3).toByte(),
                                        length = insertString.length.toUByte(),
                                        score = 4000
                                    )
                                )
                            }
                        }
                        hasConvertedKatakana = !hasConvertedKatakana
                    }
                }
            }

        } else {
            enterTenKeyCursorMoveMode()
        }
        Timber.d("SideKeySpace LongPress after: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
    }

    private fun handleSpaceLongActionSumire(source: SpaceConvertCursorMoveSource) {
        Timber.d("SideKeySpace LongPress: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
        if (switchBunsetsuSplitPattern()) {
            markSpaceConvertLongPressConsumed()
            return
        }
        val insertString = inputString.value
        markSpaceConvertLongPressConsumed()
        if (insertString.isNotEmpty()) {
            mainLayoutBinding?.let {
                if (currentInputModeForSession == InputMode.ModeJapanese) {
                    if (isHenkan.get()) return
                    if (hasConvertedKatakana) {
                        if (isLiveConversionEnable == true) {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString.hiraganaToKatakana(),
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        } else {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString,
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        }
                    } else {
                        if (isLiveConversionEnable == true) {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString,
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        } else {
                            applyFirstSuggestion(
                                Candidate(
                                    string = insertString.hiraganaToKatakana(),
                                    type = (3).toByte(),
                                    length = insertString.length.toUByte(),
                                    score = 4000
                                )
                            )
                        }
                    }
                    hasConvertedKatakana = !hasConvertedKatakana
                }
            }
        } else if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
            enterSpaceConvertCursorMoveMode(source) {
                _cursorMoveMode.update { true }
            }
        }
        Timber.d("SideKeySpace LongPress after: ${cursorMoveMode.value} $isSpaceKeyLongPressed")
    }

    /**
     * 全てのキーボードビューを確実に非表示にする
     */
    private fun hideAllKeyboards() {
        mainLayoutBinding?.apply {
            customLayoutDefault.isVisible = false
            keyboardSymbolView.isVisible = false
            candidatesRowView.isVisible = false
        }
    }

    /**
     * 指定されたキーボードを表示するための統一された関数
     */
    private fun showKeyboard(type: KeyboardType, source: String = "showKeyboard") {
        val resolution = resolveKeyboardForDisplay(
            requestedType = type,
            savedPosition = null,
            source = source,
            applyOrientation = true
        )
        showResolvedKeyboard(resolution.resolvedKeyboard)
    }

    private fun resolveKeyboardForDisplay(
        requestedType: KeyboardType?,
        savedPosition: Int? = null,
        source: String,
        persistNormalizedPosition: Boolean = false,
        applyOrientation: Boolean = true
    ): KeyboardDisplayResolution {
        val requestedFromPosition = if (requestedType == null) {
            savedPosition?.let { keyboardOrder.getOrNull(it) }
        } else {
            requestedType
        }
        val requestedForResolver = if (applyOrientation) {
            requestedFromPosition?.let(::resolveKeyboardTypeForCurrentOrientation)
        } else {
            requestedFromPosition
        }
        val resolution = resolveKeyboardDisplay(
            requested = requestedForResolver,
            keyboardOrder = keyboardOrder,
            savedPosition = savedPosition
        )

        Timber.d(
            "KeyboardDisplayResolver[$source]: requested=$requestedType " +
                    "effectiveRequested=$requestedForResolver " +
                    "savedPosition=$savedPosition savedPositionKeyboard=${resolution.savedPositionKeyboard} " +
                    "keyboardOrder=${resolution.keyboardOrder} resolved=${resolution.resolvedKeyboard} " +
                    "resolvedIndex=${resolution.resolvedIndex}"
        )
        if (resolution.requestedMissingFromOrder) {
            Timber.w(
                "KeyboardDisplayResolver[$source]: requested keyboard ${resolution.requested} " +
                        "is not in keyboardOrder=${resolution.keyboardOrder}; " +
                        "using ${resolution.resolvedKeyboard}"
            )
        }
        if (resolution.savedPositionOutOfRange) {
            Timber.w(
                "KeyboardDisplayResolver[$source]: savedPosition=$savedPosition is out of range " +
                        "for keyboardOrder=${resolution.keyboardOrder}; using ${resolution.resolvedKeyboard}"
            )
        }
        if (resolution.usedEmptyOrderFallback) {
            Timber.w(
                "KeyboardDisplayResolver[$source]: keyboardOrder is empty; " +
                        "falling back to ${KeyboardType.CUSTOM}"
            )
        }
        if (persistNormalizedPosition) {
            persistNormalizedKeyboardPositionIfNeeded(resolution)
        }
        return resolution
    }

    private fun persistNormalizedKeyboardPositionIfNeeded(
        resolution: KeyboardDisplayResolution
    ) {
        if (enableShowLastShownKeyboardInRestart != true) return
        val resolvedIndex = resolution.resolvedIndex ?: return
        if (lastSavedKeyboardPosition == resolvedIndex &&
            !resolution.requestedMissingFromOrder &&
            !resolution.savedPositionOutOfRange
        ) {
            return
        }
        lastSavedKeyboardPosition = resolvedIndex
        appPreference.save_last_used_keyboard_position_preference = resolvedIndex
    }

    private fun showResolvedKeyboard(type: KeyboardType) {
        if (qwertyMode.value == TenKeyQWERTYMode.Custom) resetCustomToggleState()
        hideAllKeyboards()
        Timber.d("showKeyboard called: resolved=$type")
        mainLayoutBinding?.apply {
            when (type) {

                KeyboardType.CUSTOM -> {
                    Timber.d("updateKeyboardLayout CUSTOM: $isFlickOnlyMode $sumireInputKeyType")
                    if (!selectInitialCustomKeyboardTab()) {
                        fallbackFromCustomKeyboardIfNeeded()
                        return@apply
                    }
                    if (qwertyMode.value != TenKeyQWERTYMode.Number) {
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Custom }
                    } else {
                        setNumberLayoutTo(customLayoutDefault)
                        //_tenKeyQWERTYMode.update { TenKeyQWERTYMode.Number }
                    }
                    customLayoutDefault.isVisible = true
                    setCurrentInputModeForSession(InputMode.ModeJapanese)
                }
            }
            suggestionRecyclerView.isVisible = true
            renderCurrentKeyboardStateOnActiveSurface()
            refreshBaselineInputBehaviorForCurrentKeyboard("keyboard layout change")
        }
    }

    private fun updateKeyboardLayout() {
        Timber.d("updateKeyboardLayout: ${qwertyMode.value} $currentEnterKeyIndex")
        when (qwertyMode.value) {
            TenKeyQWERTYMode.Custom -> {}

            TenKeyQWERTYMode.Sumire -> {
                Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")
                renderDynamicKeysOnActiveSurface()
            }

            TenKeyQWERTYMode.Number -> {

            }

        }
        renderCurrentKeyboardStateOnActiveSurface()
    }

    private fun InputMode.toSumireKeyboardInputMode(): KeyboardInputMode {
        return when (this) {
            InputMode.ModeJapanese -> KeyboardInputMode.HIRAGANA
            InputMode.ModeEnglish -> KeyboardInputMode.ENGLISH
            InputMode.ModeNumber -> KeyboardInputMode.SYMBOLS
        }
    }

    private fun applyCircularSlotActionSettings(
        layout: KeyboardLayout,
        mode: KeyboardInputMode
    ): KeyboardLayout {
        return CircularSlotActionApplier.apply(
            layout = layout,
            mode = mode,
            settings = appPreference.getCircularSlotActionSettings()
        )
    }

    private fun createNewKeyboardLayoutForSumire() {
        Timber.d("updateKeyboardLayout: ${qwertyMode.value} $currentEnterKeyIndex")
        when (qwertyMode.value) {
            TenKeyQWERTYMode.Custom -> {
                when (customKeyboardMode) {
                    KeyboardInputMode.HIRAGANA -> {
                        mainLayoutBinding?.let { mainView ->
                            if (!selectInitialCustomKeyboardTab()) {
                                fallbackFromCustomKeyboardIfNeeded()
                                return@let
                            }
                            mainView.customLayoutDefault.isVisible = true
                            // Re-render the user's layout: English / symbol modes replace it.
                            setCurrentCustomLayoutTo(mainView.customLayoutDefault)
                            setCurrentInputModeForSession(InputMode.ModeJapanese)
                        }
                    }

                    KeyboardInputMode.ENGLISH -> {
                        // necookey: the QWERTY keyboard was removed. English mode stays on the
                        // custom keyboard surface and shows the built-in English layout (rendered
                        // through the Sumire layout path) until the user switches back.
                        previousTenKeyQWERTYMode = TenKeyQWERTYMode.Custom
                        resetCustomToggleState()
                        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Sumire }
                        getActiveKeyboardSurface()?.customLayout?.let { flickView ->
                            setSumireLayoutTo(flickView)
                            flickView.isVisible = true
                        }
                        renderDynamicKeysOnActiveSurface()
                    }

                    KeyboardInputMode.SYMBOLS -> {
                        setCustomLayoutOnActiveSurface(
                            KeyboardDefaultLayouts.createNumberLayout(currentDeleteKeyFlickSettings())
                        )
                    }

                }
            }

            TenKeyQWERTYMode.Sumire -> {
                if (previousTenKeyQWERTYMode == TenKeyQWERTYMode.Custom &&
                    customKeyboardMode != KeyboardInputMode.ENGLISH
                ) {
                    // Leaving the built-in English layout: return to the user's custom keyboard.
                    previousTenKeyQWERTYMode = null
                    _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Custom }
                    createNewKeyboardLayoutForSumire()
                    return
                }
                when (customKeyboardMode) {
                    KeyboardInputMode.HIRAGANA -> {
                        Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")
                        getActiveKeyboardSurface()?.customLayout?.let(::setSumireLayoutTo)
                    }

                    KeyboardInputMode.ENGLISH -> {
                        Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")
                        getActiveKeyboardSurface()?.customLayout?.let(::setSumireLayoutTo)
                    }

                    KeyboardInputMode.SYMBOLS -> {
                        Timber.d("updateKeyboardLayout: $isFlickOnlyMode $sumireInputKeyType")
                        getActiveKeyboardSurface()?.customLayout?.let(::setSumireLayoutTo)
                    }
                }
            }

            TenKeyQWERTYMode.Number -> {

            }

        }
    }

    private fun switchFlickKeyboardInputMode(mode: KeyboardInputMode) {
        customKeyboardMode = mode
        setCurrentInputModeForSession(
            when (mode) {
                KeyboardInputMode.HIRAGANA -> InputMode.ModeJapanese
                KeyboardInputMode.ENGLISH -> InputMode.ModeEnglish
                KeyboardInputMode.SYMBOLS -> InputMode.ModeNumber
            }
        )
        createNewKeyboardLayoutForSumire()
    }

    private fun cycleFlickKeyboardInputMode() {
        switchFlickKeyboardInputMode(
            when (customKeyboardMode) {
                KeyboardInputMode.HIRAGANA -> KeyboardInputMode.ENGLISH
                KeyboardInputMode.ENGLISH -> KeyboardInputMode.SYMBOLS
                KeyboardInputMode.SYMBOLS -> KeyboardInputMode.HIRAGANA
            }
        )
    }

    private var isCustomLayoutRomajiMode = false
    private var isCustomLayoutDirectMode = false
    private var customKeyboardShiftState = CustomKeyboardShiftState.OFF
    private val customToggleInputState = CustomToggleInputState()
    private var customToggleFinalizeJob: Job? = null
    private var customToggleWasDirect: Boolean = false
    private val isCustomLayoutShiftPressed: Boolean
        get() = customKeyboardShiftState == CustomKeyboardShiftState.ONE_SHOT
    private val isCustomLayoutCapLock: Boolean
        get() = customKeyboardShiftState == CustomKeyboardShiftState.LOCKED

    private fun customKeyboardInputModePersistenceKey(layoutId: Long, stableId: String): String {
        return stableId.takeIf { it.isNotBlank() } ?: layoutId.toString()
    }

    private fun resolveInitialCustomKeyboardDirectMode(
        layoutId: Long,
        stableId: String,
        defaultValue: Boolean
    ): Boolean {
        if (appPreference.remember_custom_keyboard_input_mode_preference != true) {
            return defaultValue
        }
        val key = customKeyboardInputModePersistenceKey(layoutId, stableId)
        return appPreference.getCustomKeyboardLastDirectMode(key) ?: defaultValue
    }

    private fun resolveInitialCustomKeyboardRomajiMode(
        layoutId: Long,
        stableId: String,
        defaultValue: Boolean
    ): Boolean {
        if (appPreference.remember_custom_keyboard_input_mode_preference != true) {
            return defaultValue
        }
        val key = customKeyboardInputModePersistenceKey(layoutId, stableId)
        return appPreference.getCustomKeyboardLastRomajiMode(key) ?: defaultValue
    }

    private fun persistCurrentCustomKeyboardInputModeIfEnabled() {
        if (appPreference.remember_custom_keyboard_input_mode_preference != true) {
            return
        }
        if (qwertyMode.value != TenKeyQWERTYMode.Custom) {
            return
        }
        val layout = selectedCustomKeyboardLayoutOrNull() ?: return
        val key = customKeyboardInputModePersistenceKey(
            layoutId = layout.layoutId,
            stableId = layout.stableId
        )
        appPreference.saveCustomKeyboardLastDirectMode(key, isCustomLayoutDirectMode)
        appPreference.saveCustomKeyboardLastRomajiMode(key, isCustomLayoutRomajiMode)
    }

    private fun selectedCustomKeyboardLayoutOrNull(): CustomKeyboardLayout? {
        currentCustomKeyboardStableId
            ?.let { stableId -> resolveCustomKeyboardIndexByStableId(customLayouts, stableId) }
            ?.let { index ->
                currentCustomKeyboardPosition = index
                return customLayouts[index]
            }

        return customLayouts.getOrNull(currentCustomKeyboardPosition)?.also { layout ->
            currentCustomKeyboardStableId = layout.stableId.takeIf { it.isNotBlank() }
        }
    }

    private fun isCurrentCustomKeyboardSelection(layoutId: Long, stableId: String): Boolean {
        val selected = selectedCustomKeyboardLayoutOrNull() ?: return false
        if (stableId.isNotBlank()) {
            return selected.stableId == stableId
        }
        return selected.layoutId == layoutId
    }

    private fun currentCustomKeyboardStableIdCandidate(): String? {
        currentCustomKeyboardStableId
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        customLayouts
            .getOrNull(currentCustomKeyboardPosition)
            ?.stableId
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return appPreference.last_used_custom_keyboard_stable_id
            ?.takeIf { it.isNotBlank() }
    }

    private fun selectInitialCustomKeyboardTab(): Boolean {
        Timber.d("selectInitialCustomKeyboardTab")
        val initialSelection = resolveInitialCustomKeyboardSelection(
            layouts = customLayouts,
            rememberLast = appPreference.remember_last_custom_keyboard_preference == true,
            savedStableId = appPreference.last_used_custom_keyboard_stable_id
        ) ?: run {
            clearCurrentCustomKeyboardSelection()
            return false
        }
        selectCustomKeyboardTab(
            index = initialSelection.index,
            reason = initialSelection.reason
        )
        return true
    }

    private fun selectCustomKeyboardTab(
        index: Int,
        reason: CustomKeyboardSelectionReason
    ) {
        val layout = customLayouts.getOrNull(index) ?: run {
            Timber.d("selectCustomKeyboardTab: invalid index=$index, size=${customLayouts.size}, reason=$reason")
            return
        }
        currentCustomKeyboardPosition = index
        currentCustomKeyboardStableId = layout.stableId.takeIf { it.isNotBlank() }
        if (shouldPersistCustomKeyboardSelection(
                layout = layout,
                rememberLast = appPreference.remember_last_custom_keyboard_preference == true,
                reason = reason
            )
        ) {
            appPreference.last_used_custom_keyboard_stable_id = layout.stableId
        }
        renderCustomKeyboardLayout(layout)
    }

    private fun renderCustomKeyboardLayout(layout: CustomKeyboardLayout) {
        customKeyboardRenderJob?.cancel()
        customKeyboardRenderJob = scope.launch(Dispatchers.IO) {
            val id = layout.layoutId
            val expectedStableId = layout.stableId
            val dbLayout = runCatching { keyboardRepository.getFullLayout(id).first() }
                .getOrElse {
                    Timber.w(
                        it,
                        "renderCustomKeyboardLayout: layout disappeared id=$id stableId=$expectedStableId"
                    )
                    return@launch
                }
            Timber.d("renderCustomKeyboardLayout: $id $dbLayout")
            val finalLayout = keyboardRepository.convertLayout(dbLayout)
            Timber.d("renderCustomKeyboardLayout: ${dbLayout.isRomaji} ${finalLayout.isRomaji}")
            isCustomLayoutRomajiMode = resolveInitialCustomKeyboardRomajiMode(
                layoutId = id,
                stableId = expectedStableId,
                defaultValue = finalLayout.isRomaji
            )
            isCustomLayoutDirectMode = resolveInitialCustomKeyboardDirectMode(
                layoutId = id,
                stableId = expectedStableId,
                defaultValue = finalLayout.isDirectMode
            )
            customKeyboardShiftState = CustomKeyboardShiftState.OFF
            withContext(Dispatchers.Main) {
                if (!isCurrentCustomKeyboardSelection(layoutId = id, stableId = expectedStableId)) {
                    Timber.d("renderCustomKeyboardLayout: skip stale render id=$id stableId=$expectedStableId")
                    return@withContext
                }
                setCustomLayoutOnAvailableSurfaces(finalLayout)
                syncCustomKeyboardTogglePresentationOnAvailableSurfaces()
                refreshBaselineInputBehaviorForCurrentKeyboard("custom layout input mode loaded")
            }
        }
    }

    private fun clearCurrentCustomKeyboardSelection() {
        currentCustomKeyboardPosition = 0
        currentCustomKeyboardStableId = null
        customKeyboardRenderJob?.cancel()
        customKeyboardRenderJob = null
    }

    /**
     * necookey: set when CUSTOM had to fall back only because the custom layouts were not loaded
     * yet (first frame after process start). Cleared once the layouts arrive and CUSTOM is shown.
     */
    private var pendingCustomKeyboardRecovery = false

    /**
     * The custom keyboard is the only input keyboard. When no custom layout exists (e.g. the user
     * deleted the last one), re-insert the default flick layout; the layouts flow then delivers it
     * and [onCustomKeyboardLayoutsChanged] shows it via pendingCustomKeyboardRecovery.
     */
    private fun fallbackFromCustomKeyboardIfNeeded() {
        if (customLayouts.isEmpty()) {
            pendingCustomKeyboardRecovery = true
            hideAllKeyboards()
            scope.launch(Dispatchers.IO) {
                com.kazumaproject.markdownhelperkeyboard.custom_keyboard.seed.NecookeyDefaultLayoutSeeder
                    .ensureDefaultLayout(applicationContext, keyboardRepository)
            }
        }
    }

    private fun onCustomKeyboardLayoutsChanged(newLayouts: List<CustomKeyboardLayout>) {
        val selectedStableId = currentCustomKeyboardStableIdCandidate()
        val previousIndex = currentCustomKeyboardPosition
        val selection = resolveCustomKeyboardSelectionAfterLayoutsChanged(
            layouts = newLayouts,
            selectedStableId = selectedStableId,
            previousIndex = previousIndex
        )

        customLayouts = newLayouts

        if (selection == null) {
            clearCurrentCustomKeyboardSelection()
            if (appPreference.remember_last_custom_keyboard_preference == true) {
                appPreference.last_used_custom_keyboard_stable_id = ""
            }
            if (qwertyMode.value == TenKeyQWERTYMode.Custom) {
                suggestionAdapter?.updateState(TenKeyQWERTYMode.Custom, emptyList())
                fallbackFromCustomKeyboardIfNeeded()
            } else if (qwertyMode.value == TenKeyQWERTYMode.Number && currentInputType in numberTypes) {
                showNumberKeyboardForCurrentInputType()
            }
            return
        }

        currentCustomKeyboardPosition = selection.index
        currentCustomKeyboardStableId = selection.stableId.takeIf { it.isNotBlank() }

        if (pendingCustomKeyboardRecovery) {
            pendingCustomKeyboardRecovery = false
            if (qwertyMode.value != TenKeyQWERTYMode.Number &&
                inputString.value.isEmpty()
            ) {
                keyboardOrder.indexOf(KeyboardType.CUSTOM)
                    .takeIf { it >= 0 }
                    ?.let { currentKeyboardOrder = it }
                showKeyboard(KeyboardType.CUSTOM, source = "necookey.customLayoutsLoaded")
                return
            }
        }

        val selectedLayout = customLayouts.getOrNull(selection.index) ?: run {
            clearCurrentCustomKeyboardSelection()
            fallbackFromCustomKeyboardIfNeeded()
            return
        }

        if (shouldPersistCustomKeyboardSelection(
                layout = selectedLayout,
                rememberLast = appPreference.remember_last_custom_keyboard_preference == true,
                reason = selection.reason
            )
        ) {
            appPreference.last_used_custom_keyboard_stable_id = selectedLayout.stableId
        }

        if (qwertyMode.value == TenKeyQWERTYMode.Custom) {
            suggestionAdapter?.updateState(TenKeyQWERTYMode.Custom, customLayouts)
            refreshCandidateStripContent()
            renderCustomKeyboardLayout(selectedLayout)
            renderCurrentKeyboardStateOnActiveSurface()
        } else if (qwertyMode.value == TenKeyQWERTYMode.Number && currentInputType in numberTypes) {
            showNumberKeyboardForCurrentInputType()
        }
    }

    private fun moveToCustomKeyboardByStableId(stableId: String) {
        val targetIndex = resolveCustomKeyboardIndexByStableId(customLayouts, stableId) ?: run {
            Timber.d("moveToCustomKeyboardByStableId: target not found stableId=$stableId")
            return
        }
        selectCustomKeyboardTab(
            index = targetIndex,
            reason = CustomKeyboardSelectionReason.MoveToStableId
        )
    }

    private fun updateSumireDakutenKeyForCurrentInput() {
        val isSumireJapaneseInput =
            qwertyMode.value == TenKeyQWERTYMode.Sumire &&
                currentInputModeForSession == InputMode.ModeJapanese
        val canTransformLastCharacter =
            inputString.value.lastOrNull()?.getDakutenSmallChar() != null
        currentDakutenKeyIndex = if (isSumireJapaneseInput && canTransformLastCharacter) 1 else 0
        updateDynamicKeyOnActiveSurface("dakuten_toggle_key", currentDakutenKeyIndex)
    }

    private fun setSumireKeyboardEnterKey(index: Int) {
        currentEnterKeyIndex = index
        updateDynamicKeyOnActiveSurface("enter_key", currentEnterKeyIndex)
    }

    private fun setSumireKeyboardSpaceKey(index: Int) {
        currentSpaceKeyIndex = index
        updateDynamicKeyOnActiveSurface("space_convert_key", currentSpaceKeyIndex)
    }

    private fun setSumireKeyboardSwitchNumberAndKatakanaKey(index: Int) {
        currentKatakanaKeyIndex = index
        updateDynamicKeyOnActiveSurface("katakana_toggle_key", currentKatakanaKeyIndex)
    }

    private fun resetSumireKeyboardDakutenMode() {
        currentDakutenKeyIndex = 0
        currentEnterKeyIndex = editorEnterKeyStateIndex()
        currentSpaceKeyIndex = 0
        Timber.d("resetSumireKeyboardDakutenMode called: $currentEnterKeyIndex")
        renderDynamicKeysOnActiveSurface()
    }

    private fun showKeyboardPicker() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showInputMethodPicker()
    }

    private fun launchSettingsActivity(navigationRequest: String) {
        // Create the Intent to launch your MainActivity
        val intent = Intent(this, MainActivity::class.java)

        // Add the flag required to start an Activity from a Service
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Add the specific request as an extra
        intent.putExtra("openSettingActivity", navigationRequest)

        // Start the activity
        startActivity(intent)
    }

    // ▼▼▼ ADD THIS VARIABLE ▼▼▼
    private var customKeyboardMode = KeyboardInputMode.HIRAGANA

    private fun clearDeleteBufferWithView() {
        appPreference.undo_enable_preference?.let {
            if (it && deletedBuffer.isNotEmpty()) {
                clearDeletedBufferWithoutResetLayout()
                refreshEditHistoryUi()
            }
        }
    }

    private fun setupCustomKeyboardListeners(mainView: MainLayoutBinding) {
        configureFlickKeyboardView(mainView.customLayoutDefault, mainView)
    }

    private fun popupBackgroundColorOrNull(): Int? =
        KeyboardSkinRegistry.find(keyboardSkinId)?.palette?.key
            ?: if (appPreference.key_popup_use_custom_color) appPreference.key_popup_background_color else null

    private fun popupTextColorOrNull(): Int? =
        KeyboardSkinRegistry.find(keyboardSkinId)?.palette?.text
            ?: if (appPreference.key_popup_use_custom_color) appPreference.key_popup_text_color else null

    private fun currentTenKeyPopupViewStyle(): PopupViewStyle {
        return PopupViewStyle(
            sizeScalePercent = if (keyboardSkinId == KeyboardSkinId.DEFAULT) appPreference.tenkey_popup_size_scale_percent ?: 100 else 100,
            textSizeSp = appPreference.tenkey_popup_text_size_sp ?: 28.0f,
            backgroundColor = popupBackgroundColorOrNull(),
            textColor = popupTextColorOrNull(),
            skinId = keyboardSkinId
        )
    }

    private fun currentFlickPopupViewStyleSet(): FlickPopupViewStyleSet {
        return FlickPopupViewStyleSet(
            directional = PopupViewStyle(
                sizeScalePercent = if (keyboardSkinId == KeyboardSkinId.DEFAULT) appPreference.flick_directional_popup_size_scale_percent ?: 100 else 100,
                textSizeSp = appPreference.flick_directional_popup_text_size_sp ?: 28.0f,
                backgroundColor = popupBackgroundColorOrNull(),
                textColor = popupTextColorOrNull(),
                skinId = keyboardSkinId
            ),
            cross = PopupViewStyle(
                sizeScalePercent = if (keyboardSkinId == KeyboardSkinId.DEFAULT) appPreference.flick_cross_popup_size_scale_percent ?: 100 else 100,
                textSizeSp = appPreference.flick_cross_popup_text_size_sp ?: 18.0f,
                backgroundColor = popupBackgroundColorOrNull(),
                textColor = popupTextColorOrNull(),
                skinId = keyboardSkinId
            ),
            standard = PopupViewStyle(
                sizeScalePercent = if (keyboardSkinId == KeyboardSkinId.DEFAULT) appPreference.flick_standard_popup_size_scale_percent ?: 100 else 100,
                textSizeSp = appPreference.flick_standard_popup_text_size_sp ?: 19.0f,
                backgroundColor = popupBackgroundColorOrNull(),
                textColor = popupTextColorOrNull(),
                skinId = keyboardSkinId
            ),
            tfbi = PopupViewStyle(
                sizeScalePercent = if (keyboardSkinId == KeyboardSkinId.DEFAULT) appPreference.flick_tfbi_popup_size_scale_percent ?: 100 else 100,
                textSizeSp = appPreference.flick_tfbi_popup_text_size_sp ?: 20.0f,
                backgroundColor = popupBackgroundColorOrNull(),
                textColor = popupTextColorOrNull(),
                skinId = keyboardSkinId
            )
        )
    }

    private fun configureFlickKeyboardView(
        flickView: FlickKeyboardView,
        mainView: MainLayoutBinding,
    ) {
        flickView.setOnFlickTextPreviewListener(sumireFlickTextPreviewListener)
        flickView.bindRuntimeGestureSettings(runtimeGestureSettingsSource)
        val tfbiPopupPresentationMode = appPreference.flick_tfbi_popup_presentation
        val tfbiFlickStartPositionMode = appPreference.flick_tfbi_flick_start_position
        flickView.setTfbiPopupPresentationMode(tfbiPopupPresentationMode)
        flickView.setTfbiFlickStartPositionMode(tfbiFlickStartPositionMode)
        // Popups anchor to each key's own window.
        flickView.setPopupWindowAnchorProvider(null)
        flickView.applyKeyboardTheme(
            skinId = keyboardSkinId,
            themeMode = keyboardThemeMode ?: "default",
            currentNightMode = currentNightMode,
            isDynamicColorEnabled = DynamicColors.isDynamicColorAvailable(),
            customBgColor = customThemeBgColor ?: Color.WHITE,
            customKeyColor = customThemeKeyColor ?: Color.WHITE,
            customSpecialKeyColor = customThemeSpecialKeyColor ?: Color.GRAY,
            customKeyTextColor = customThemeKeyTextColor ?: Color.BLACK,
            customSpecialKeyTextColor = customThemeSpecialKeyTextColor ?: Color.BLACK,
            liquidGlassEnable = liquidGlassThemePreference ?: false,
            customBorderEnable = customKeyBorderEnablePreference ?: false,
            customBorderColor = customKeyBorderEnableColor ?: Color.BLACK,
            liquidGlassKeyAlphaEnable = liquidGlassKeyBlurRadiousPreference ?: 255,
            borderWidth = customKeyBorderWidth ?: 1
        )

        flickView.setAngleAndRange(
            appPreference.getCircularFlickRanges(),
            circularFlickWindowScale ?: 1.0f
        )
        flickView.setCircularFlickOptions(
            directionCount = circularFlickDirectionCount
                ?: appPreference.circularFlickDirectionCount
        )
        flickView.setHierarchicalFlickModeSwitchAngleMargin(
            (hierarchicalFlickModeSwitchAngleMargin
                ?: appPreference.hierarchical_flick_mode_switch_angle_margin_preference).toDouble()
        )

        flickView.applyKeySizing(
            keyWidthScalePercent = appPreference.flick_key_width_scale_percent ?: 160,
            keyHeightScalePercent = appPreference.flick_key_height_scale_percent ?: 160,
            iconScalePercent = appPreference.flick_key_icon_scale_percent ?: 80,
            textSizeSp = appPreference.flick_key_text_size_sp ?: 16.0f,
            specialKeyTextSizeSp = appPreference.flick_special_key_text_size_sp ?: 16.0f
        )
        flickView.applyPopupViewStyleSet(currentFlickPopupViewStyleSet())
        applyCurrentFlickGuidePreference(flickView)
        flickView.setFlickGuideTextSizeSp(
            (flickGuideTextSizeSpPreference ?: 9).coerceIn(6, 16).toFloat()
        )
        flickView.setFlickGuideMaxCodePoints(
            (flickGuideMaxCharactersPreference ?: 1).coerceIn(1, 4)
        )

        flickView.setOnKeyboardActionListener(object :
            com.kazumaproject.custom_keyboard.view.FlickKeyboardView.OnKeyboardActionListener {

            override fun onPress(action: KeyAction) {
                if (isKeyboardLayoutEditModeActive()) return
                if (action == KeyAction.DoNothing) return
            }

            override fun onLongPressActionCanceled(action: KeyAction) {
                // Rebuilding an inactive pane also emits cancellation; it is not an input gesture.
                cancelOngoingLongPressForAction(action)
            }

            override fun onActionLongPress(action: KeyAction) {
                if (isKeyboardLayoutEditModeActive()) return
                finishCustomToggleForAction()
                if (action != KeyAction.DoNothing) {
                    clearDeleteBufferWithView()
                }
                Timber.d("onActionLongPress: $action")
                when (action) {
                    KeyAction.DoNothing -> Unit
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> cycleFlickKeyboardInputMode()

                    KeyAction.Convert -> {
                        val insertString = inputString.value
                        if (switchBunsetsuSplitPattern()) {
                            markSpaceConvertLongPressConsumed()
                            return
                        }
                        markSpaceConvertLongPressConsumed()
                        if (insertString.isEmpty()) {
                            enterSpaceConvertCursorMoveMode(
                                SpaceConvertCursorMoveSource.SumireCustomConvert
                            ) {
                                flickView.setCursorMode(true)
                            }
                        } else {
                            if (conversionKeySwipePreference == true) {
                                if (!isHenkan.get()) {
                                    enterSpaceConvertCursorMoveMode(
                                        SpaceConvertCursorMoveSource.SumireCustomConvert
                                    ) {
                                        flickView.setCursorMode(true)
                                    }
                                }
                            } else {
                                handleSpaceLongActionSumire(
                                    SpaceConvertCursorMoveSource.SumireCustomConvert
                                )
                            }
                        }
                    }

                    KeyAction.Space -> {
                        if (switchBunsetsuSplitPattern()) {
                            markSpaceConvertLongPressConsumed()
                            return
                        }
                        enterSpaceConvertCursorMoveMode(
                            SpaceConvertCursorMoveSource.SumireCustomSpace
                        ) {
                            flickView.setCursorMode(true)
                        }
                        markSpaceConvertLongPressConsumed()
                    }

                    KeyAction.Copy -> {

                    }

                    KeyAction.Cut -> {}

                    KeyAction.Delete -> {
                        handleDeleteLongPress()
                    }

                    KeyAction.NewLine, KeyAction.Enter, KeyAction.Confirm -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (insertString.isNotEmpty()) {
                            handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                        } else {
                            handleEmptyInputEnterKey(mainView)
                        }
                    }

                    is KeyAction.InputText -> {
                        if (action.text == "^_^") {
                            val insertString = inputString.value
                            Timber.d("InputText: emoji: $insertString")
                            if (insertString.isNotEmpty()) {
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickTop()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            } else {
                                _keyboardSymbolViewState.value = SymbolKeyboardState(
                                    isShown = !_keyboardSymbolViewState.value.isShown
                                )
                                stringInTail.set("")
                                finishComposingText()
                                setComposingText("", 0)
                            }
                        }
                    }

                    KeyAction.MoveCursorLeft -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleLeftLongPress()
                        leftCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            if (moveFocusedBunsetsuSegment(delta = -1)) {
                            } else if (isHenkan.get()) {
                                handleDeleteKeyInHenkan(suggestions, insertString)
                            } else {
                                clearDeletedBuffer()
                            }
                        }
                        refreshEditHistoryUi()
                    }

                    KeyAction.MoveCursorRight -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleRightLongPress()
                        rightCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            if (moveFocusedBunsetsuSegment(delta = 1)) {
                            } else if (isHenkan.get()) {
                                handleJapaneseModeSpaceKey(
                                    mainView, suggestions, insertString
                                )
                            } else {
                                clearDeletedBuffer()
                            }
                        }
                        refreshEditHistoryUi()
                    }

                    KeyAction.Paste -> {}
                    KeyAction.SelectAll -> {}
                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}

                    KeyAction.SwitchToNextIme -> {
                        showListPopup()
                    }

                    KeyAction.ToggleCase -> {}
                    KeyAction.ToggleDakuten -> {}
                    KeyAction.ToggleDakutenOnly -> {}
                    KeyAction.ToggleHandakutenOnly -> {}
                    KeyAction.SwitchToEnglishLayout -> switchFlickKeyboardInputMode(KeyboardInputMode.ENGLISH)

                    KeyAction.SwitchToKanaLayout -> switchFlickKeyboardInputMode(KeyboardInputMode.HIRAGANA)

                    KeyAction.SwitchToNumberLayout -> switchFlickKeyboardInputMode(KeyboardInputMode.SYMBOLS)

                    KeyAction.ShiftKey -> {}
                    KeyAction.MoveCustomKeyboardTab -> {}
                    is KeyAction.MoveToCustomKeyboard -> {}
                    KeyAction.ToggleKatakana -> {}
                    KeyAction.DeleteUntilSymbol -> {}
                    KeyAction.MoveCursorDown -> {
                        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
                            refreshEditHistoryUi()
                        }
                    }

                    KeyAction.MoveCursorUp -> {
                        if (cycleFocusedBunsetsuCandidate(delta = -1)) {
                            refreshEditHistoryUi()
                        }
                    }

                    KeyAction.Cancel -> {}
                    KeyAction.VoiceInput -> {}
                    is KeyAction.Text -> Unit
                    KeyAction.DeleteAfterCursorUntilSymbol -> {}
                    KeyAction.UndoLastDelete -> {}
                    KeyAction.SwitchRomajiEnglish -> {}
                    KeyAction.ForceNewLine -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (insertString.isEmpty()) {
                            forceNewLine(mainView)
                        } else {
                            handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                        }
                    }

                    KeyAction.SwitchDirectMode -> {}
                    KeyAction.CapLockKey -> {}
                    KeyAction.ForceHalfWidthSpace -> {}
                    KeyAction.ForceFullWidthSpace -> {}
                    KeyAction.DeleteAfterCursor -> {}
                    KeyAction.CommitAndInsertSpace -> {}
                }
            }

            override fun onActionUpAfterLongPress(action: KeyAction) {
                if (isKeyboardLayoutEditModeActive()) return
                Timber.d("onActionUpAfterLongPress: $action")
                when (action) {
                    KeyAction.DoNothing -> Unit
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {}
                    KeyAction.Confirm -> {}
                    KeyAction.Convert, KeyAction.Space -> {
                        isSpaceKeyLongPressed = false
                    }

                    KeyAction.Copy -> {}
                    KeyAction.Cut -> {}
                    KeyAction.Delete -> {
                        stopDeleteLongPress()
                    }

                    KeyAction.Enter -> {}
                    is KeyAction.InputText -> {}
                    KeyAction.MoveCursorLeft -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.MoveCursorRight -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.NewLine -> {}
                    KeyAction.Paste -> {}
                    KeyAction.SelectAll -> {}
                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}
                    KeyAction.SwitchToNextIme -> {}
                    KeyAction.ToggleCase -> {}
                    KeyAction.ToggleDakuten -> {}
                    KeyAction.ToggleDakutenOnly -> {}
                    KeyAction.ToggleHandakutenOnly -> {}
                    KeyAction.SwitchToEnglishLayout -> {}
                    KeyAction.SwitchToKanaLayout -> {}
                    KeyAction.SwitchToNumberLayout -> {}
                    KeyAction.ShiftKey -> {}
                    KeyAction.MoveCustomKeyboardTab -> {}
                    is KeyAction.MoveToCustomKeyboard -> {}
                    KeyAction.ToggleKatakana -> {}
                    KeyAction.DeleteUntilSymbol -> {}
                    KeyAction.MoveCursorDown -> {}
                    KeyAction.MoveCursorUp -> {}
                    KeyAction.Cancel -> {
                        stopDeleteLongPress()
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.VoiceInput -> {}
                    is KeyAction.Text -> Unit
                    KeyAction.DeleteAfterCursorUntilSymbol -> {}
                    KeyAction.UndoLastDelete -> {}
                    KeyAction.ForceNewLine -> {}
                    KeyAction.SwitchDirectMode -> {}
                    KeyAction.SwitchRomajiEnglish -> {}
                    KeyAction.CapLockKey -> {}
                    KeyAction.ForceHalfWidthSpace -> {
                        handleForceHalfWidthSpaceOrConvert(
                            mainView)
                    }

                    KeyAction.ForceFullWidthSpace -> {
                        handleForceFullWidthSpaceOrConvert(
                            mainView)
                    }
                    KeyAction.DeleteAfterCursor -> {}
                    KeyAction.CommitAndInsertSpace -> {}
                }
            }

            override fun onFlickDirectionChanged(direction: FlickDirection) {
                if (isKeyboardLayoutEditModeActive()) return
                Timber.d("onFlickDirectionChanged: $direction")
            }

            override fun onFlickActionLongPress(action: KeyAction) {
                if (isKeyboardLayoutEditModeActive()) return
                finishCustomToggleForAction()
                Timber.d("onFlickActionLongPress: $action")
                when (action) {
                    KeyAction.DoNothing -> Unit
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {}
                    KeyAction.Confirm -> {}
                    KeyAction.Convert -> {
                        if (switchBunsetsuSplitPattern()) {
                            markSpaceConvertLongPressConsumed()
                            return
                        }
                        markSpaceConvertLongPressConsumed()
                        if (conversionKeySwipePreference == true) {
                            if (!isHenkan.get()) {
                                enterSpaceConvertCursorMoveMode(
                                    SpaceConvertCursorMoveSource.SumireCustomFlickConvert
                                ) {
                                    flickView.setCursorMode(true)
                                }
                            }
                        } else {
                            handleSpaceLongActionSumire(
                                SpaceConvertCursorMoveSource.SumireCustomFlickConvert
                            )
                        }
                    }

                    KeyAction.Copy -> {
                        copyAction()
                    }

                    KeyAction.Cut -> {
                        cutAction()
                    }

                    KeyAction.Delete -> {
                        handleDeleteLongPress()
                    }

                    KeyAction.Enter -> {}
                    is KeyAction.InputText -> {}
                    KeyAction.MoveCursorLeft -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleLeftLongPress()
                        leftCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            clearDeletedBuffer()
                        }
                        refreshEditHistoryUi()
                    }

                    KeyAction.MoveCursorRight -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        handleRightLongPress()
                        rightCursorKeyLongKeyPressed.set(true)
                        if (selectMode.value) {
                            clearDeletedBufferWithoutResetLayout()
                        } else {
                            clearDeletedBuffer()
                        }
                        refreshEditHistoryUi()
                    }

                    KeyAction.NewLine -> {}
                    KeyAction.Paste -> {
                        pasteAction()
                    }

                    KeyAction.SelectAll -> {
                        selectAllText()
                    }

                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}
                    KeyAction.Space -> {
                        if (switchBunsetsuSplitPattern()) {
                            markSpaceConvertLongPressConsumed()
                            return
                        }
                        enterSpaceConvertCursorMoveMode(
                            SpaceConvertCursorMoveSource.SumireCustomFlickSpace
                        ) {
                            flickView.setCursorMode(true)
                        }
                        markSpaceConvertLongPressConsumed()
                    }

                    KeyAction.SwitchToNextIme -> {
                        showListPopup()
                    }

                    KeyAction.ToggleCase -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakuten -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakutenOnly -> {}

                    KeyAction.ToggleHandakutenOnly -> {}

                    KeyAction.SwitchToEnglishLayout -> {}
                    KeyAction.SwitchToKanaLayout -> {}
                    KeyAction.SwitchToNumberLayout -> {}
                    KeyAction.ShiftKey -> {}
                    KeyAction.MoveCustomKeyboardTab -> {}
                    is KeyAction.MoveToCustomKeyboard -> {}
                    KeyAction.ToggleKatakana -> {}
                    KeyAction.DeleteUntilSymbol -> {}
                    KeyAction.MoveCursorDown -> {

                    }

                    KeyAction.MoveCursorUp -> {}
                    KeyAction.Cancel -> {}
                    KeyAction.VoiceInput -> {}
                    is KeyAction.Text -> Unit
                    KeyAction.DeleteAfterCursorUntilSymbol -> {}
                    KeyAction.UndoLastDelete -> {}
                    KeyAction.ForceNewLine -> {}
                    KeyAction.SwitchDirectMode -> {}
                    KeyAction.SwitchRomajiEnglish -> {}
                    KeyAction.CapLockKey -> {}
                    KeyAction.ForceHalfWidthSpace -> {}
                    KeyAction.ForceFullWidthSpace -> {}
                    KeyAction.DeleteAfterCursor -> {}
                    KeyAction.CommitAndInsertSpace -> {}
                }
            }

            override fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean) {
                if (isKeyboardLayoutEditModeActive()) return
                Timber.d("onFlickActionUpAfterLongPress: $action $isFlick")
                when (action) {
                    KeyAction.DoNothing -> Unit
                    KeyAction.Backspace -> {}
                    KeyAction.ChangeInputMode -> {}
                    KeyAction.Confirm -> {}
                    KeyAction.Copy -> {}
                    KeyAction.Cut -> {}
                    KeyAction.Delete -> {
                        stopDeleteLongPress()
                    }

                    KeyAction.Enter -> {}
                    is KeyAction.InputText -> {
                        when (action.text) {
                            "ひらがな小文字" -> {
                                val insertString = inputString.value
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickTop()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                            "濁点" -> {
                                val insertString = inputString.value
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickLeft()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                            "半濁点" -> {
                                val insertString = inputString.value
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickRight()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                        }
                    }

                    KeyAction.MoveCursorLeft -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.MoveCursorRight -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.NewLine -> {}
                    KeyAction.Paste -> {}
                    KeyAction.SelectAll -> {}
                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {}
                    KeyAction.Convert, KeyAction.Space -> {
                        flickView.setCursorMode(false)
                        if (shouldSuppressSpaceConvertTapAfterLongPress()) {
                            return
                        }
                        isSpaceKeyLongPressed = false
                        if (inputString.value.isEmpty()) {
                            val isHankaku = hankakuPreference == true
                            val insertString = inputString.value
                            val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                            if (isHankaku) {
                                if (isFlick) {
                                    handleSpaceKeyClick(false, insertString, suggestions, mainView)
                                } else {
                                    handleSpaceKeyClick(true, insertString, suggestions, mainView)
                                }
                            } else {
                                if (isFlick) {
                                    handleSpaceKeyClick(true, insertString, suggestions, mainView)
                                } else {
                                    handleSpaceKeyClick(false, insertString, suggestions, mainView)
                                }
                            }
                        }
                    }

                    KeyAction.SwitchToNextIme -> {}
                    KeyAction.ToggleCase -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakuten -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakutenOnly -> {
                        toggleDakutenOnlyForCustomKeyboard()
                    }

                    KeyAction.ToggleHandakutenOnly -> {
                        toggleHandakutenOnlyForCustomKeyboard()
                    }

                    KeyAction.SwitchToEnglishLayout -> switchFlickKeyboardInputMode(KeyboardInputMode.ENGLISH)

                    KeyAction.SwitchToKanaLayout -> switchFlickKeyboardInputMode(KeyboardInputMode.HIRAGANA)

                    KeyAction.SwitchToNumberLayout -> switchFlickKeyboardInputMode(KeyboardInputMode.SYMBOLS)

                    KeyAction.ShiftKey -> {
                        handleCustomKeyboardShiftTap()
                    }

                    KeyAction.MoveCustomKeyboardTab -> {
                        scope.launch {
                            if (customLayouts.isNotEmpty()) {
                                val position =
                                    (currentCustomKeyboardPosition + 1) % customLayouts.size
                                selectCustomKeyboardTab(
                                    index = position,
                                    reason = CustomKeyboardSelectionReason.UserNextTab
                                )
                            }
                        }
                    }

                    is KeyAction.MoveToCustomKeyboard -> {
                        moveToCustomKeyboardByStableId(action.stableId)
                    }

                    KeyAction.ToggleKatakana -> {
                        when (countToggleKatakana) {
                            0 -> {
                                _inputString.update {
                                    it.hiraganaToKatakana()
                                }
                                countToggleKatakana++
                            }

                            1 -> {
                                _inputString.update {
                                    it.toHankakuKatakana()
                                }
                                countToggleKatakana++
                            }

                            2 -> {
                                _inputString.update {
                                    it.toHiragana()
                                }
                                countToggleKatakana = 0
                            }
                        }
                    }

                    KeyAction.DeleteUntilSymbol -> {
                        if (isDeleteLeftFlickPreference == true) {
                            val insertString = inputString.value
                            deleteWordOrSymbolsBeforeCursor(insertString)
                        }
                        stopDeleteLongPress()
                    }

                    KeyAction.DeleteAfterCursorUntilSymbol -> {
                        if (isDeleteUpFlickPreference == true) {
                            val insertString = inputString.value
                            deleteWordOrSymbolsAfterCursor(insertString)
                        }
                        stopDeleteLongPress()
                    }

                    KeyAction.UndoLastDelete -> {
                        if (isDeleteDownFlickPreference == true) {
                            undoLastHistoryEntry()
                        }
                        stopDeleteLongPress()
                    }

                    KeyAction.MoveCursorDown -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        val insertString = inputString.value
                        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
                        } else if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                        }
                    }

                    KeyAction.MoveCursorUp -> {
                        cancelLeftLongPress()
                        cancelRightLongPress()
                        val insertString = inputString.value
                        if (cycleFocusedBunsetsuCandidate(delta = -1)) {
                        } else if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                        }
                    }

                    KeyAction.Cancel -> {
                        stopDeleteLongPress()
                        cancelLeftLongPress()
                        cancelRightLongPress()
                    }

                    KeyAction.VoiceInput -> {}
                    is KeyAction.Text -> Unit
                    KeyAction.ForceNewLine -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (insertString.isEmpty()) {
                            forceNewLine(mainView)
                        } else {
                            handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                        }
                    }

                    KeyAction.SwitchDirectMode -> {
                        isCustomLayoutDirectMode = !isCustomLayoutDirectMode
                        persistCurrentCustomKeyboardInputModeIfEnabled()
                        shortcutInputBehaviorOverride = null
                        baselineInputBehavior = resolveBaselineInputBehavior()
                        applyEffectiveInputBehavior("custom direct mode key")

                        Handler(mainLooper).post {
                            getActiveKeyboardSurface()?.customLayout?.updateKeyIconByAction(
                                KeyAction.SwitchDirectMode,
                                if (isCustomLayoutDirectMode) com.kazumaproject.core.R.drawable.language_japanese_kana_right_24px
                                else com.kazumaproject.core.R.drawable.language_japanese_kana_left_24px
                            )
                        }
                    }

                    KeyAction.SwitchRomajiEnglish -> {
                        isCustomLayoutRomajiMode = !isCustomLayoutRomajiMode
                        persistCurrentCustomKeyboardInputModeIfEnabled()
                        Handler(mainLooper).post {
                            getActiveKeyboardSurface()?.customLayout?.updateKeyIconByAction(
                                KeyAction.SwitchRomajiEnglish,
                                if (isCustomLayoutRomajiMode) com.kazumaproject.core.R.drawable.language_japanese_kana_left_bold_24px
                                else com.kazumaproject.core.R.drawable.language_japanese_kana_right_bold_24px
                            )
                        }
                    }

                    KeyAction.CapLockKey -> {
                        handleCustomKeyboardCapsLockTap()
                    }

                    KeyAction.ForceHalfWidthSpace -> {
                        handleForceHalfWidthSpaceOrConvert(
                            mainView)
                    }

                    KeyAction.ForceFullWidthSpace -> {
                        handleForceFullWidthSpaceOrConvert(
                            mainView)
                    }

                    KeyAction.DeleteAfterCursor -> {}
                    KeyAction.CommitAndInsertSpace -> {}
                }
            }

            override fun onToggleText(keyIdentity: String, values: List<String>) {
                if (isKeyboardLayoutEditModeActive()) return
                clearDeleteBufferWithView()
                val isDirect = isCustomToggleDirectInput()
                if (customToggleWasDirect != isDirect) {
                    resetCustomToggleState()
                }
                val canonicalValues = values.filter { it.length == 1 }
                val outputValues = canonicalValues.map(::applyCustomLayoutShiftAndCapLock)
                handleCustomToggleText(
                    keyIdentity = keyIdentity,
                    values = canonicalValues,
                    outputValues = outputValues,
                    mainView = mainView
                )
                consumeCustomKeyboardOneShotShift()
            }

            override fun onAction(action: KeyAction, isFlick: Boolean) {
                if (isKeyboardLayoutEditModeActive()) return
                finishCustomToggleForAction()

                Timber.d("onAction: $action $isFlick")
                if (!shouldPreserveDeleteHistoryForAction(action)) {
                    clearDeleteBufferWithView()
                }
                when (action) {
                    KeyAction.DoNothing -> Unit
                    is KeyAction.Text -> {
                        val text = action.text
                        Timber.d("onAction Text: [$text] [${qwertyMode.value}] [$isDefaultRomajiHenkanMap]")
                        when (qwertyMode.value) {
                            TenKeyQWERTYMode.Custom -> {
                                if (text.isEmpty()) return
                                val shiftedText = applyCustomLayoutShiftAndCapLock(text)
                                if (dispatchDirectTextIfNeeded(shiftedText)) {
                                    consumeCustomKeyboardOneShotShift()
                                    return
                                }
                                if (isCustomLayoutDirectMode) {
                                    finishComposingText()
                                    setComposingText("", 0)
                                    commitText(shiftedText, 1)
                                    consumeCustomKeyboardOneShotShift()
                                    return
                                }
                                if (text.length == 1) {
                                    if (isCustomLayoutRomajiMode) {
                                        val insertString = inputString.value
                                        val sb = StringBuilder()
                                        sb.append(insertString).append(text)
                                        romajiConverter?.let { converter ->
                                            if (isDefaultRomajiHenkanMap) {
                                                if (!isCustomLayoutShiftPressed && !isCustomLayoutCapLock) {
                                                    _inputString.update {
                                                        converter.convertCustomLayout(
                                                            sb.toString()
                                                        )
                                                    }
                                                } else {
                                                    _inputString.update {
                                                        applyCustomLayoutShiftAndCapLock(
                                                            sb.toString()
                                                        )
                                                    }
                                                }

                                            } else {
                                                _inputString.update {
                                                    applyCustomLayoutShiftAndCapLock(
                                                        customRomajiScreenConverter?.convert(sb.toString())
                                                            ?: sb.toString()
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        handleCustomKeyboardText(
                                            shiftedText,
                                            mainView,
                                            isFlick
                                        )
                                    }

                                } else {
                                    if (isCustomLayoutRomajiMode) {
                                        val insertString = inputString.value
                                        val sb = StringBuilder()
                                        sb.append(insertString).append(shiftedText)
                                        romajiConverter?.let { converter ->
                                            if (isDefaultRomajiHenkanMap) {
                                                _inputString.update {
                                                    converter.convertCustomLayout(sb.toString())
                                                }
                                            } else {
                                                _inputString.update {
                                                    customRomajiScreenConverter?.convert(sb.toString())
                                                        ?: sb.toString()
                                                }
                                            }
                                        }
                                    } else {
                                        val insertString = inputString.value
                                        val sb = StringBuilder()
                                        sb.append(insertString).append(shiftedText)
                                        _inputString.update { sb.toString() }
                                    }
                                }
                                consumeCustomKeyboardOneShotShift()
                            }

                            TenKeyQWERTYMode.Sumire -> {
                                Timber.d("TenKeyQWERTYMode.Sumire: $text $isFlick")
                                handleOnKeyForSumire(text, mainView, isFlick)
                                updateSumireDakutenKeyForCurrentInput()
                            }

                            TenKeyQWERTYMode.Number -> {
                                handleOnKeyForSumire(text, mainView, isFlick)
                            }

                            else -> {}
                        }
                    }

                    is KeyAction.InputText -> {
                        when (action.text) {
                            "^_^" -> {
                                val insertString = inputString.value
                                Timber.d("InputText: emoji: $insertString")
                                if (insertString.isNotEmpty()) {
                                    val sb = StringBuilder()
                                    val c = insertString.last()
                                    c.getDakutenFlickTop()?.let { dakutenChar ->
                                        setStringBuilderForConvertStringInHiragana(
                                            dakutenChar, sb, insertString
                                        )
                                    }
                                } else {
                                    _keyboardSymbolViewState.value = SymbolKeyboardState(
                                        isShown = !_keyboardSymbolViewState.value.isShown
                                    )
                                    stringInTail.set("")
                                    finishComposingText()
                                    setComposingText("", 0)
                                }
                            }

                            ":", "-" -> {
                                if (dispatchDirectTextIfNeeded(action.text)) return
                                val insertString = inputString.value
                                val sb = StringBuilder()
                                sb.append(insertString).append(action.text)
                                _inputString.update {
                                    sb.toString()
                                }
                            }

                            "ひらがな小文字" -> {
                                val insertString = inputString.value
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickTop()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                            "濁点" -> {
                                val insertString = inputString.value
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickLeft()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                            "半濁点" -> {
                                val insertString = inputString.value
                                if (insertString.isEmpty()) return
                                val sb = StringBuilder()
                                val c = insertString.last()
                                c.getDakutenFlickRight()?.let { dakutenChar ->
                                    setStringBuilderForConvertStringInHiragana(
                                        dakutenChar, sb, insertString
                                    )
                                }
                            }

                        }
                    }

                    KeyAction.SwitchToNextIme -> {
                        if (!onKeyboardSwitchLongPressUp) {
                            switchNextKeyboard()
                            _inputString.update { "" }
                            finishComposingText()
                            setComposingText("", 0)
                        }
                    }

                    KeyAction.ChangeInputMode -> cycleFlickKeyboardInputMode()

                    KeyAction.Delete -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        handleDeleteKeyTap(insertString, suggestions)
                        stopDeleteLongPress()
                    }

                    KeyAction.CommitAndInsertSpace -> {
                        handleCommitAndInsertSpace()
                    }

                    KeyAction.ForceNewLine -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (insertString.isEmpty()) {
                            forceNewLine(mainView)
                        } else {
                            handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                        }
                    }

                    KeyAction.NewLine, KeyAction.Enter, KeyAction.Confirm -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (insertString.isNotEmpty()) {
                            handleNonEmptyInputEnterKey(suggestions, mainView, insertString)
                        } else {
                            handleEmptyInputEnterKey(mainView)
                        }
                    }

                    KeyAction.Convert, KeyAction.Space -> {
                        if (shouldSuppressSpaceConvertTapAfterLongPress()) {
                            return
                        }
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (cursorMoveMode.value) {
                            _cursorMoveMode.update { false }
                        } else {
                            if (!isSpaceKeyLongPressed) {
                                if (isFlick && cycleFocusedBunsetsuCandidate(delta = -1)) {
                                } else {
                                    val isHankaku = hankakuPreference == true
                                    if (isHankaku) {
                                        if (isFlick) {
                                            handleSpaceKeyClick(
                                                false, insertString, suggestions, mainView
                                            )
                                        } else {
                                            handleSpaceKeyClick(
                                                true, insertString, suggestions, mainView
                                            )
                                        }
                                    } else {
                                        if (isFlick) {
                                            handleSpaceKeyClick(
                                                true, insertString, suggestions, mainView
                                            )
                                        } else {
                                            handleSpaceKeyClick(
                                                false, insertString, suggestions, mainView
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        isSpaceKeyLongPressed = false
                    }

                    KeyAction.MoveCursorLeft -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (!leftCursorKeyLongKeyPressed.get()) {
                            if (handleBunsetsuArrowTap(delta = -1)) {
                            } else if (isHenkan.get()) {
                                handleDeleteKeyInHenkan(suggestions, insertString)
                            } else {
                                handleLeftCursor(GestureType.Tap, insertString)
                            }
                        }
                        cancelRightLongPress()
                        cancelLeftLongPress()
                    }

                    KeyAction.MoveCursorRight -> {
                        val insertString = inputString.value
                        val suggestions = suggestionAdapter?.suggestions ?: emptyList()
                        if (!rightCursorKeyLongKeyPressed.get()) {
                            if (handleBunsetsuArrowTap(delta = 1)) {
                            } else if (isHenkan.get()) {
                                handleJapaneseModeSpaceKey(
                                    mainView, suggestions, insertString
                                )
                            } else {
                                actionInRightKeyPressed(GestureType.Tap, insertString)
                            }
                        }
                        cancelRightLongPress()
                        cancelLeftLongPress()
                    }

                    KeyAction.Backspace -> {}
                    KeyAction.Cut -> {
                        cutAction()
                    }
                    KeyAction.Copy -> {
                        copyAction()
                    }

                    KeyAction.Paste -> {
                        pasteAction()
                    }

                    KeyAction.SelectAll -> {
                        selectAllText()
                    }

                    KeyAction.SelectLeft -> {}
                    KeyAction.SelectRight -> {}
                    KeyAction.ShowEmojiKeyboard -> {
                        toggleEmojiKeyboard()
                    }

                    KeyAction.ToggleCase -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakuten -> {
                        dakutenSmallActionForSumire()
                    }

                    KeyAction.ToggleDakutenOnly -> {
                        toggleDakutenOnlyForCustomKeyboard()
                    }

                    KeyAction.ToggleHandakutenOnly -> {
                        toggleHandakutenOnlyForCustomKeyboard()
                    }

                    KeyAction.SwitchToEnglishLayout -> switchFlickKeyboardInputMode(KeyboardInputMode.ENGLISH)

                    KeyAction.SwitchToKanaLayout -> switchFlickKeyboardInputMode(KeyboardInputMode.HIRAGANA)

                    KeyAction.SwitchToNumberLayout -> switchFlickKeyboardInputMode(KeyboardInputMode.SYMBOLS)

                    KeyAction.ShiftKey -> {
                        handleCustomKeyboardShiftTap()
                    }

                    KeyAction.SwitchDirectMode -> {
                        isCustomLayoutDirectMode = !isCustomLayoutDirectMode
                        persistCurrentCustomKeyboardInputModeIfEnabled()
                        shortcutInputBehaviorOverride = null
                        baselineInputBehavior = resolveBaselineInputBehavior()
                        applyEffectiveInputBehavior("custom direct mode key")

                        Handler(mainLooper).post {
                            getActiveKeyboardSurface()?.customLayout?.updateKeyIconByAction(
                                KeyAction.SwitchDirectMode,
                                if (isCustomLayoutDirectMode) com.kazumaproject.core.R.drawable.language_japanese_kana_right_24px
                                else com.kazumaproject.core.R.drawable.language_japanese_kana_left_24px
                            )
                        }
                    }

                    KeyAction.CapLockKey -> {
                        handleCustomKeyboardCapsLockTap()
                    }

                    KeyAction.SwitchRomajiEnglish -> {
                        isCustomLayoutRomajiMode = !isCustomLayoutRomajiMode
                        persistCurrentCustomKeyboardInputModeIfEnabled()
                        Handler(mainLooper).post {
                            getActiveKeyboardSurface()?.customLayout?.updateKeyIconByAction(
                                KeyAction.SwitchRomajiEnglish,
                                if (isCustomLayoutRomajiMode) com.kazumaproject.core.R.drawable.language_japanese_kana_left_bold_24px
                                else com.kazumaproject.core.R.drawable.language_japanese_kana_right_bold_24px
                            )
                        }
                    }

                    KeyAction.MoveCustomKeyboardTab -> {
                        scope.launch {
                            if (customLayouts.isNotEmpty()) {
                                val position =
                                    (currentCustomKeyboardPosition + 1) % customLayouts.size
                                selectCustomKeyboardTab(
                                    index = position,
                                    reason = CustomKeyboardSelectionReason.UserNextTab
                                )
                            }
                        }
                    }

                    is KeyAction.MoveToCustomKeyboard -> {
                        moveToCustomKeyboardByStableId(action.stableId)
                    }

                    KeyAction.ToggleKatakana -> {
                        when (countToggleKatakana) {
                            0 -> {
                                _inputString.update {
                                    it.hiraganaToKatakana()
                                }
                                countToggleKatakana++
                            }

                            1 -> {
                                _inputString.update {
                                    it.toHankakuKatakana()
                                }
                                countToggleKatakana++
                            }

                            2 -> {
                                _inputString.update {
                                    it.toHiragana()
                                }
                                countToggleKatakana = 0
                            }
                        }
                    }

                    KeyAction.DeleteUntilSymbol -> {
                        if (isDeleteLeftFlickPreference == true) {
                            val insertString = inputString.value
                            deleteWordOrSymbolsBeforeCursor(insertString)
                        }
                    }

                    KeyAction.DeleteAfterCursorUntilSymbol -> {
                        val insertString = inputString.value
                        deleteWordOrSymbolsAfterCursor(insertString)
                    }

                    KeyAction.DeleteAfterCursor -> {
                        handleDeleteAfterCursor()
                    }

                    KeyAction.UndoLastDelete -> {
                        if (isDeleteDownFlickPreference == true) {
                            undoLastHistoryEntry()
                        }
                    }

                    KeyAction.MoveCursorDown -> {
                        val insertString = inputString.value
                        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
                        } else if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                        }
                    }

                    KeyAction.MoveCursorUp -> {
                        val insertString = inputString.value
                        if (cycleFocusedBunsetsuCandidate(delta = -1)) {
                        } else if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                        }
                    }

                    KeyAction.Cancel -> {}
                    KeyAction.VoiceInput -> {
                        startVoiceInput(mainView)
                    }

                    KeyAction.ForceHalfWidthSpace -> {
                        handleForceHalfWidthSpaceOrConvert(
                            mainView)
                    }

                    KeyAction.ForceFullWidthSpace -> {
                        handleForceFullWidthSpaceOrConvert(
                            mainView)
                    }

                    KeyAction.CommitAndInsertSpace -> {}
                }
            }
        })
    }

    private fun handleCustomKeyboardShiftTap() {
        customKeyboardShiftState = customKeyboardShiftState.onShiftTap()
        Handler(mainLooper).post(::syncCustomKeyboardTogglePresentationOnAvailableSurfaces)
    }

    private fun handleCustomKeyboardCapsLockTap() {
        customKeyboardShiftState = customKeyboardShiftState.onCapsLockTap()
        Handler(mainLooper).post(::syncCustomKeyboardTogglePresentationOnAvailableSurfaces)
    }

    private fun consumeCustomKeyboardOneShotShift() {
        val consumedState = customKeyboardShiftState.consumeOneShot()
        if (consumedState == customKeyboardShiftState) return
        customKeyboardShiftState = consumedState
        Handler(mainLooper).post(::syncCustomKeyboardTogglePresentationOnAvailableSurfaces)
    }

    private fun applyCustomLayoutShiftAndCapLock(text: String): String {
        return customKeyboardShiftState.transformAsciiLetters(text)
    }

    private fun isCustomToggleDirectInput(): Boolean {
        return currentInputBehavior != ResolvedInputBehavior.COMPOSING_TEXT ||
            isCustomLayoutDirectMode
    }

    private fun resetCustomToggleState() {
        customToggleFinalizeJob?.cancel()
        customToggleFinalizeJob = null
        customToggleInputState.reset()
        customToggleWasDirect = false
        customToggleExpectedEditorSelection = null
    }

    private var customToggleExpectedEditorSelection: CustomToggleEditorSelection? = null
    private var customToggleEditInProgress = false

    private fun finishCustomToggleForAction() {
        val shouldUpdateColor = qwertyMode.value == TenKeyQWERTYMode.Custom &&
            customToggleRemainingMillis() > 0 && !isCustomToggleDirectInput() &&
            inputString.value.isNotEmpty() && !isHenkan.get()
        resetCustomToggleState()
        if (shouldUpdateColor) applyRawComposingFallback(inputString.value)
    }

    private fun customToggleRemainingMillis(): Long =
        customToggleInputState.remainingMillis(SystemClock.elapsedRealtime())

    private fun renderCustomKeyboardComposingText(string: String) {
        if (customToggleRemainingMillis() > 0 && !isCustomToggleDirectInput()) {
            setComposingTextPreEdit(
                inputString = string,
                spannableString = createSpannableWithTail(string),
                backgroundColor = if (customComposingTextPreference == true) {
                    inputCompositionBackgroundColor
                        ?: getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                } else {
                    getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                },
                textColor = if (customComposingTextPreference == true) inputCompositionTextColor else null,
            )
        } else {
            applyRawComposingFallback(string)
        }
    }

    private fun scheduleCustomToggleFinalize() {
        customToggleFinalizeJob?.cancel()
        customToggleFinalizeJob = null
        if (qwertyMode.value != TenKeyQWERTYMode.Custom || currentInputConnection == null) return
        val remaining = customToggleRemainingMillis()
        if (remaining <= 0 || isCustomToggleDirectInput()) return
        val string = inputString.value
        if (string.isEmpty()) return
        // Render here as well: cycling equal output values does not emit a StateFlow update.
        renderCustomKeyboardComposingText(string)
        val revision = editorMutationRevision.current()
        customToggleFinalizeJob = scope.launch {
            delay(customToggleRemainingMillis())
            if (qwertyMode.value != TenKeyQWERTYMode.Custom ||
                inputString.value != string || !editorMutationRevision.isCurrent(revision) ||
                isCustomToggleDirectInput() || isHenkan.get()
            ) return@launch
            applyRawComposingFallback(string)
        }
    }

    private fun captureCustomToggleEditorSelection(
        connection: InputConnection
    ): CustomToggleEditorSelection? {
        val extracted = runCatching {
            connection.getExtractedText(ExtractedTextRequest(), 0)
        }.getOrNull() ?: return null
        if (
            extracted.text == null ||
            extracted.startOffset < 0 ||
            extracted.selectionStart < 0 ||
            extracted.selectionEnd < 0
        ) {
            return null
        }
        return CustomToggleEditorSelection(
            connection = connection,
            selectionStart = extracted.startOffset + extracted.selectionStart,
            selectionEnd = extracted.startOffset + extracted.selectionEnd,
        )
    }

    private fun invalidateCustomToggleStateForSelection(
        newSelStart: Int,
        newSelEnd: Int,
    ) {
        if (customToggleEditInProgress) return
        val expected = customToggleExpectedEditorSelection ?: return
        if (newSelStart < 0 || newSelEnd < 0) return
        if (
            currentInputConnection !== expected.connection ||
            newSelStart != expected.selectionStart ||
            newSelEnd != expected.selectionEnd
        ) {
            resetCustomToggleState()
        }
    }

    private fun replaceCustomToggleDirectText(
        previous: String,
        next: String,
    ): Boolean {
        val connection = currentInputConnection ?: return false
        val expected = customToggleExpectedEditorSelection ?: return false
        if (expected.connection !== connection) return false

        val selection = captureCustomToggleEditorSelection(connection) ?: return false
        if (
            selection.selectionStart != expected.selectionStart ||
            selection.selectionEnd != expected.selectionEnd
        ) {
            return false
        }

        val textBeforeCursor = runCatching {
            connection.getTextBeforeCursor(previous.length, 0)?.toString()
        }.getOrNull() ?: return false
        if (textBeforeCursor != previous) return false

        var batchStarted = false
        var committed = false
        customToggleEditInProgress = true
        try {
            batchStarted = runCatching { connection.beginBatchEdit() }.getOrDefault(false)
            if (batchStarted) {
                val deleted = runCatching {
                    connection.deleteSurroundingText(previous.length, 0)
                }.getOrDefault(false)
                committed = deleted && runCatching {
                    connection.commitText(next, 1)
                }.getOrDefault(false)
            }
        } finally {
            if (batchStarted) {
                runCatching { connection.endBatchEdit() }
            }
            customToggleEditInProgress = false
        }

        if (!committed) return false
        // Both values are one UTF-16 code unit, so replacing the preceding value leaves the
        // collapsed cursor at the same absolute position. Keep the validated position even if
        // the editor does not expose a second extracted-text snapshot after the batch edit.
        customToggleExpectedEditorSelection = expected
        return true
    }

    private fun customToggleAppendWasCommitted(
        before: CustomToggleEditorSelection,
        after: CustomToggleEditorSelection,
        text: String,
    ): Boolean {
        if (before.connection !== after.connection) return false
        if (before.selectionStart != before.selectionEnd) return false
        val expectedCursor = before.selectionStart + text.length
        if (after.selectionStart != expectedCursor || after.selectionEnd != expectedCursor) {
            return false
        }
        val textBeforeCursor = runCatching {
            after.connection.getTextBeforeCursor(text.length, 0)?.toString()
        }.getOrNull()
        return textBeforeCursor == text
    }

    private fun handleCustomToggleText(
        keyIdentity: String,
        values: List<String>,
        outputValues: List<String>,
        mainView: MainLayoutBinding
    ) {
        if (values.size != outputValues.size) {
            resetCustomToggleState()
            return
        }
        val validPairs = values.zip(outputValues)
            .filter { (value, output) -> value.length == 1 && output.length == 1 }
        if (validPairs.isEmpty()) {
            resetCustomToggleState()
            return
        }
        val canonicalValues = validPairs.map { it.first }
        val emittedValues = validPairs.map { it.second }
        when (
            val mutation = customToggleInputState.next(
                keyIdentity = keyIdentity,
                values = canonicalValues,
                outputValues = emittedValues,
                nowMillis = SystemClock.elapsedRealtime(),
                timeoutMillis = delayTime?.toLong() ?: DEFAULT_DELAY_MS,
            )
        ) {
            null -> return
            is CustomToggleInputState.Mutation.Append -> {
                val isDirect = isCustomToggleDirectInput()
                val before = if (isDirect) {
                    currentInputConnection?.let(::captureCustomToggleEditorSelection)
                } else {
                    null
                }
                customToggleEditInProgress = true
                try {
                    // A new toggle sequence must append, bypassing the legacy kana tap cycle.
                    handleCustomKeyboardText(mutation.text, mainView, isFlick = true)
                } finally {
                    customToggleEditInProgress = false
                }
                if (isDirect) {
                    val after = currentInputConnection?.let(::captureCustomToggleEditorSelection)
                    if (
                        before == null ||
                        after == null ||
                        !customToggleAppendWasCommitted(before, after, mutation.text)
                    ) {
                        resetCustomToggleState()
                        return
                    }
                    customToggleWasDirect = true
                    customToggleExpectedEditorSelection = after
                } else {
                    customToggleWasDirect = false
                    customToggleExpectedEditorSelection = null
                }
            }
            is CustomToggleInputState.Mutation.Replace -> {
                val previous = mutation.previous
                val next = mutation.next
                if (customToggleWasDirect) {
                    if (!replaceCustomToggleDirectText(previous, next)) {
                        resetCustomToggleState()
                        handleCustomToggleText(
                            keyIdentity = keyIdentity,
                            values = canonicalValues,
                            outputValues = emittedValues,
                            mainView = mainView,
                        )
                        return
                    }
                } else {
                    val current = inputString.value
                    if (!current.endsWith(previous)) {
                        resetCustomToggleState()
                        handleCustomToggleText(
                            keyIdentity = keyIdentity,
                            values = canonicalValues,
                            outputValues = emittedValues,
                            mainView = mainView,
                        )
                        return
                    }
                    _inputString.update { current.dropLast(previous.length) + next }
                }
            }
        }
        scheduleCustomToggleFinalize()
    }

    private fun handleCustomKeyboardText(
        text: String,
        mainView: MainLayoutBinding,
        isFlick: Boolean
    ) {
        if (dispatchDirectTextIfNeeded(text)) return
        if (isCustomLayoutDirectMode) {
            finishComposingText()
            setComposingText("", 0)
            commitText(text, 1)
            return
        }
        if (applyPendingFlickTextMutation(text, isFlick)) return
        if (text.length == 1) {
            // Only onToggleText cycles custom keys; ordinary taps always append.
            handleFlick(text.first(), inputString.value, StringBuilder(), mainView)
        } else {
            _inputString.update { it + text }
        }
    }

    private fun handleOnKeyForSumire(
        text: String, mainView: MainLayoutBinding, isFlick: Boolean
    ) {
        if (dispatchDirectTextIfNeeded(text)) return
        if (applyPendingFlickTextMutation(text, isFlick)) return
        val insertString = inputString.value
        val sb = StringBuilder()
        if (text.isNotEmpty()) {
            if (text.length == 1) {
                text.first().let {
                    if (isFlickOnlyMode == true) {
                        handleFlick(char = it, insertString, sb, mainView)
                    } else {
                        if (isFlick) {
                            handleFlick(char = it, insertString, sb, mainView)
                        } else {
                            handleTap(char = it, insertString, sb, mainView)
                        }
                    }
                }
            } else {
                sb.append(insertString).append(text)
                _inputString.update {
                    sb.toString()
                }
            }
        }

    }

    private fun cancelLeftLongPress() {
        onLeftKeyLongPressUp.set(true)
        leftCursorKeyLongKeyPressed.set(false)
        leftLongPressJob?.cancel()
        leftLongPressJob = null
    }

    private fun cancelRightLongPress() {
        onRightKeyLongPressUp.set(true)
        rightCursorKeyLongKeyPressed.set(false)
        rightLongPressJob?.cancel()
        rightLongPressJob = null
    }

    private fun stopAllOngoingKeyLongPresses() {
        stopDeleteLongPress()
        cancelLeftLongPress()
        cancelRightLongPress()
        stopSpaceLongPressState()
        onKeyboardSwitchLongPressUp = false
    }

    private fun stopSpaceLongPressState() {
        isSpaceKeyLongPressed = false
        _cursorMoveMode.update { false }

        mainLayoutBinding?.customLayoutDefault?.setCursorMode(false)
    }

    private fun cancelOngoingLongPressForKey(key: Key) {
        when (key) {
            Key.SideKeyDelete -> stopDeleteLongPress()
            Key.SideKeyCursorLeft -> cancelLeftLongPress()
            Key.SideKeyCursorRight -> cancelRightLongPress()
            Key.SideKeySpace -> stopSpaceLongPressState()
            Key.SideKeyInputMode,
            Key.KeyDakutenSmall -> {
                onKeyboardSwitchLongPressUp = false
            }

            Key.NotSelected -> Unit
            else -> Unit
        }
    }

    private fun cancelOngoingLongPressForAction(action: KeyAction) {
        when (action) {
            KeyAction.Delete,
            KeyAction.Backspace,
            KeyAction.DeleteUntilSymbol,
            KeyAction.DeleteAfterCursorUntilSymbol,
            KeyAction.UndoLastDelete -> {
                stopDeleteLongPress()
            }

            // DeleteAfterCursor is intentionally tap-only; it never owns a repeat job.
            KeyAction.DeleteAfterCursor -> Unit

            KeyAction.MoveCursorLeft -> cancelLeftLongPress()
            KeyAction.MoveCursorRight -> cancelRightLongPress()
            KeyAction.Space,
            KeyAction.Convert,
            KeyAction.ForceHalfWidthSpace,
            KeyAction.ForceFullWidthSpace -> {
                stopSpaceLongPressState()
            }

            KeyAction.SwitchToNextIme -> {
                onKeyboardSwitchLongPressUp = false
            }

            KeyAction.Cancel -> stopAllOngoingKeyLongPresses()
            else -> Unit
        }
    }

    private fun copyAction() {
        readSelectedTextOffMain { selectedText ->
            if (selectedText.isNotEmpty()) {
                copySelectedTextToClipboard(selectedText)
            }
        }
    }

    private fun readSelectedTextOffMain(onRead: (String) -> Unit) {
        val inputConnection = currentInputConnection ?: return
        ioScope.launch {
            val selectedText = runCatching {
                inputConnection.getSelectedText(0)?.toString().orEmpty()
            }.getOrDefault("")
            runOnMainThread {
                if (currentInputConnection === inputConnection) {
                    onRead(selectedText)
                }
            }
        }
    }

    private fun shareSelectedTextAction() {
        readSelectedTextOffMain { selectedText ->
            if (selectedText.isEmpty()) return@readSelectedTextOffMain
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, selectedText)
            }
            val chooser = Intent.createChooser(sendIntent, "Share text via").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(chooser)
            clearSelection()
        }
    }

    private fun copySelectedTextToClipboard(selectedText: CharSequence) {
        val text = selectedText.toString()
        val isSensitive = currentInputType.isPassword()
        clipboardUtil.setClipBoard(text, isSensitive = isSensitive)
        appPreference.last_pasted_clipboard_text_preference = ""
        editorTextSelected = text.isNotEmpty()
        selectedEditorText = text
        markClipboardPreviewRefreshAfterPrimaryClipChanged()
        updateClipboardPreview()
    }

    /**
     * クリップボードからの貼り付けアクション。テキストと画像の両方に対応。
     */
    private fun pasteAction() {
        clearZeroQueryAllState(refresh = false)
        val inputConnection = currentInputConnection ?: return
        scope.launch {
            val item = withContext(Dispatchers.IO) {
                clipboardUtil.getPrimaryClipContent()
            }
            if (currentInputConnection !== inputConnection) return@launch
            when (item) {
                is ClipboardItem.Image -> {
                    commitBitmap(item.bitmap)
                }

                is ClipboardItem.Text -> {
                    if (item.text.isNotEmpty()) {
                        commitText(item.text, 1)
                        appPreference.last_pasted_clipboard_text_preference = item.text
                    }
                }

                is ClipboardItem.Empty -> {
                    // Do nothing
                }
            }
            clearDeletedBufferWithoutResetLayout()
            refreshEditHistoryUi()
        }
    }

    private fun cutAction() {
        readSelectedTextOffMain { selectedText ->
            if (selectedText.isNotEmpty()) {
                copySelectedTextToClipboard(selectedText)
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
        }
    }

    private fun showOrHideKeyboard() {
        if (isInputViewShown) {
            requestHideSelf(0)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                requestShowSelf(0)
            } else {
                val token = window?.window?.attributes?.token
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInputFromInputMethod(
                    token, InputMethodManager.SHOW_IMPLICIT
                )
            }
        }
        refreshCandidateStripContent()
    }

    private fun pasteImageAction(bitmap: Bitmap) {
        clearZeroQueryAllState(refresh = false)
        commitBitmap(bitmap)
        clearDeletedBufferWithoutResetLayout()
        refreshEditHistoryUi()
    }

    private fun pasteClipboardHistoryItem(item: ClipboardItem) {
        scope.launch {
            val fullContent = if (item.clipboardId() > 0) {
                withContext(Dispatchers.IO) {
                    clipboardHistoryRepository.getFullContentById(item.clipboardId())
                }
            } else {
                item
            }
            pasteClipboardItemContent(fullContent)
        }
    }

    private fun pasteClipboardItemContent(item: ClipboardItem) {
        clearZeroQueryAllState(refresh = false)
        when (item) {
            is ClipboardItem.Image -> {
                commitBitmap(item.bitmap)
            }

            is ClipboardItem.Text -> {
                if (item.text.isNotEmpty()) {
                    commitText(item.text, 1)
                    appPreference.last_pasted_clipboard_text_preference = item.text
                }
            }

            ClipboardItem.Empty -> Unit
        }
        clearDeletedBufferWithoutResetLayout()
        refreshEditHistoryUi()
    }

    private fun handleClipboardHistoryItemAction(item: ClipboardItem, action: ClipboardItemAction) {
        when (action) {
            ClipboardItemAction.PASTE -> pasteClipboardHistoryItem(item)
            ClipboardItemAction.PIN -> updateClipboardHistoryPin(item, isPinned = true)
            ClipboardItemAction.UNPIN -> updateClipboardHistoryPin(item, isPinned = false)
            ClipboardItemAction.DELETE -> deleteClipboardHistoryItem(item)
        }
    }

    private fun updateClipboardHistoryPin(item: ClipboardItem, isPinned: Boolean) {
        val id = item.clipboardId()
        if (id <= 0) return
        ioScope.launch {
            clipboardHistoryRepository.setPinned(id, isPinned)
            if (!isPinned) {
                cleanupExpiredClipboardItemsIfNeededNow()
            }
        }
    }

    private fun deleteClipboardHistoryItem(item: ClipboardItem) {
        val id = item.clipboardId()
        if (id <= 0) return
        ioScope.launch {
            clipboardHistoryRepository.deleteById(id)
        }
    }

    private fun ClipboardItem.clipboardId(): Long {
        return when (this) {
            is ClipboardItem.Image -> id
            is ClipboardItem.Text -> id
            ClipboardItem.Empty -> 0L
        }
    }

    private fun cleanupExpiredClipboardItemsIfNeeded() {
        if (!isClipboardUnpinnedAutoDeleteEnabled()) return
        ioScope.launch {
            cleanupExpiredClipboardItemsIfNeededNow()
        }
    }

    private suspend fun cleanupExpiredClipboardItemsIfNeededNow() {
        if (!isClipboardUnpinnedAutoDeleteEnabled()) return
        clipboardHistoryRepository.deleteExpiredUnpinnedItems(clipboardUnpinnedRetentionHours())
    }

    private fun filterClipboardHistoryListByRetention(
        historyList: List<ClipboardHistoryItem>
    ): List<ClipboardHistoryItem> {
        if (!isClipboardUnpinnedAutoDeleteEnabled()) return historyList
        val threshold = System.currentTimeMillis() -
                clipboardUnpinnedRetentionHours() * 60L * 60L * 1000L
        return historyList.filter { item ->
            item.isPinned || item.timestamp >= threshold
        }
    }

    private fun isClipboardUnpinnedAutoDeleteEnabled(): Boolean {
        return appPreference.clipboard_delete_unpinned_after_hours_preference
    }

    private fun clipboardUnpinnedRetentionHours(): Int {
        return appPreference.clipboard_unpinned_retention_hours_preference.coerceIn(1, 72)
    }

    /**
     * Bitmapを入力先アプリに送信します。
     * この関数を呼び出す前に、FileProviderが正しく設定されている必要があります。
     *
     * @param bitmap 送信するBitmapオブジェクト。
     */
    private fun commitBitmap(bitmap: Bitmap) {
        // ▼▼▼ ログ追加 ▼▼▼
        Timber.d("commitBitmap: 開始")

        // APIレベルが低い場合は何もせずに終了
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            Timber.w("このAPIレベルではcommitContentはサポートされていません。")
            return
        }

        // ▼▼▼ ログ追加 ▼▼▼
        // InputConnectionとEditorInfoが有効か確認
        val inputConnection = currentInputConnection
        val editorInfo = currentInputEditorInfo
        if (inputConnection == null || editorInfo == null) {
            Timber.e("commitBitmap: InputConnectionまたはEditorInfoがnullです。処理を中断します。")
            return
        }

        // ▼▼▼ ログ追加 ▼▼▼
        // ターゲットエディタがサポートするMIMEタイプをログに出力
        val supportedMimeTypes = editorInfo.contentMimeTypes ?: emptyArray()
        if (supportedMimeTypes.isEmpty()) {
            Timber.w("commitBitmap: ターゲットエディタはどのMIMEタイプもサポートしていません。")
        } else {
            Timber.d("commitBitmap: ターゲットエディタがサポートするMIMEタイプ: ${supportedMimeTypes.joinToString()}")
        }

        // ▼▼▼ ログ追加 ▼▼▼
        // "image/png"をサポートしているか確認
        val isPngSupported = supportedMimeTypes.any { mimeType ->
            ClipDescription.compareMimeTypes(mimeType, "image/png")
        }
        if (!isPngSupported) {
            Timber.w("commitBitmap: ターゲットエディタは 'image/png' をサポートしていません。")
            // ここで処理を中断するか、別の形式（例: "image/jpeg"）を試すか判断できます
        }

        ioScope.launch {
            val imageFile = withContext(Dispatchers.IO) {
                val cachePath = File(cacheDir, "images")
                cachePath.mkdirs()
                val file = File(cachePath, "clipboard_image.png")
                runCatching {
                    FileOutputStream(file).use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    file
                }.onFailure {
                    Timber.e(it, "Bitmapのファイルへの保存に失敗しました")
                }.getOrNull()
            } ?: return@launch

            runOnMainThread {
                if (currentInputConnection !== inputConnection) return@runOnMainThread
                commitBitmapContent(inputConnection, editorInfo, imageFile)
            }
        }
    }

    private fun commitBitmapContent(
        inputConnection: InputConnection,
        editorInfo: EditorInfo,
        imageFile: File,
    ) {
        // 2. FileProviderを使用してContent URIを取得
        val contentUri: Uri
        try {
            val authority = "${applicationContext.packageName}.fileprovider"
            contentUri = FileProvider.getUriForFile(this, authority, imageFile)
            // ▼▼▼ ログ追加 ▼▼▼
            Timber.d("commitBitmap: Content URIを取得しました: $contentUri")
        } catch (e: IllegalArgumentException) {
            Timber.e(
                e, "FileProviderが正しく設定されていません。AndroidManifest.xmlを確認してください。"
            )
            return
        }

        // 3. InputContentInfoCompatを作成
        val mimeType = "image/png"
        val description = ClipDescription("Image from keyboard", arrayOf(mimeType))

        // ★★★ 修正点 ★★★
        // linkUri（3番目の引数）にはnullを渡します。
        // この引数はhttp/httpsのウェブURIを要求するため、content:// URIを渡すとクラッシュします。
        val inputContentInfo = InputContentInfoCompat(
            contentUri, description, null // linkUriはnullにする
        )

        // 4. 読み取り権限をターゲットアプリに付与
        val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION

        // ▼▼▼ ログ追加 ▼▼▼
        Timber.d("commitBitmap: commitContentを呼び出します...")

        // 5. コンテンツをコミット (InputConnectionCompatを使用)
        val didCommit = InputConnectionCompat.commitContent(
            inputConnection,
            editorInfo,
            inputContentInfo,
            flags,
            null // opts (Bundle) は通常nullで問題ありません
        )

        // ▼▼▼ ログ追加 ▼▼▼
        if (didCommit) {
            Timber.d("commitBitmap: コンテンツのコミットに成功しました。")
        } else {
            // このログは元のコードにもありますが、ここに来た場合の直前のログが重要になります
            Timber.e("commitBitmap: コンテンツのコミットに失敗しました。エディタが画像の挿入をサポートしていない可能性があります。")
            commitBitmapViaClipboard(contentUri)
        }
    }

    /**
     * クリップボード経由でBitmapを貼り付けます。
     * commitContentが失敗した場合のフォールバックとして使用します。
     *
     * @param contentUri 貼り付ける画像のContent URI
     */
    private fun commitBitmapViaClipboard(contentUri: Uri) {
        Timber.d("commitBitmapViaClipboard: 開始")
        try {
            clipboardUtil.setClipBoardUri(
                uri = contentUri,
                label = "Image",
                isSensitive = false
            )

            // 2. ターゲットアプリに読み取り権限を一時的に付与
            // (FileProviderのgrantUriPermissions属性がtrueなら不要な場合もあるが、明示的に行うのが安全)
            grantUriPermission(
                currentInputEditorInfo?.packageName ?: return,
                contentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Timber.d("commitBitmapViaClipboard: クリップボードにコピー完了")

            // 3. 「貼り付け」コマンドを実行
            val didPaste =
                currentInputConnection?.performContextMenuAction(android.R.id.paste) ?: false
            if (didPaste) {
                Timber.d("commitBitmapViaClipboard: 貼り付けコマンドの実行に成功")
            } else {
                Timber.w("commitBitmapViaClipboard: 貼り付けコマンドの実行に失敗")
                // ここでユーザーに「クリップボードにコピーしました。手動で貼り付けてください」と通知するのも良い
            }

        } catch (e: Exception) {
            Timber.e(e, "commitBitmapViaClipboard: 処理中に例外が発生")
        }
    }

    /**
     * ★新しい関数: クリップボードのプレビューUIを更新します。
     * 画像とテキストの両方を判定して、正しくプレビューの状態を設定します。
     */
    private fun updateClipboardPreview() {
        Timber.d("SuggestionAdapter Clipboard: updateClipboardPreview")
        runOnMainThread {
            refreshCandidateStripContent()
        }
    }

    private fun getClipboardPreviewText(text: String): String {
        return if (clipboardUtil.isPrimaryClipSensitive()) {
            getSensitiveClipboardPreviewText()
        } else {
            text
        }
    }

    private fun getSensitiveClipboardPreviewText(text: CharSequence? = null): String = "********"

    private fun dakutenSmallActionForSumire() {
        val insertString = inputString.value
        val sb = StringBuilder()
        if (insertString.isNotEmpty()) {
            if (insertString.last().isLatinAlphabet()) {
                smallConversionEnglish(sb, insertString)
            } else if (insertString.last().isHiragana()) {
                dakutenSmallLetter(
                    sb, insertString, GestureType.Tap
                )
            }
        }
    }


    private fun smallConversionEnglish(
        sb: StringBuilder, insertString: String,
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)

        if (insertString.isNotEmpty()) {
            val insertPosition = insertString.last()
            insertPosition.let { c ->
                if (!c.isHiragana()) {
                    c.getDakutenSmallChar()?.let { dakutenChar ->
                        setStringBuilderForConvertStringInHiragana(dakutenChar, sb, insertString)
                    }
                }
            }
        }
    }

    private fun resetKeyboard() {
        Timber.d("resetKeyboard called for showKeyboard")
        val resolution = if (enableShowLastShownKeyboardInRestart == true) {
            resolveKeyboardForDisplay(
                requestedType = null,
                savedPosition = lastSavedKeyboardPosition ?: 0,
                source = "resetKeyboard.restoreLastShown",
                persistNormalizedPosition = true,
                applyOrientation = false
            )
        } else {
            resolveKeyboardForDisplay(
                requestedType = null,
                savedPosition = null,
                source = "resetKeyboard.firstKeyboard",
                applyOrientation = false
            )
        }
        currentKeyboardOrder = resolution.resolvedIndex ?: 0
        val requestedType = resolution.resolvedKeyboard
        showKeyboard(requestedType, source = "resetKeyboard.display")
    }

    private fun isLandscapeOrientation(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun resolveKeyboardTypeForCurrentOrientation(requestedType: KeyboardType): KeyboardType {
        return requestedType
    }

    private fun refreshKeyboardForCurrentOrientation() {
        val mainView = mainLayoutBinding ?: return
        val resolution = resolveKeyboardForDisplay(
            requestedType = keyboardOrder.getOrNull(currentKeyboardOrder),
            savedPosition = lastSavedKeyboardPosition,
            source = "refreshKeyboardForCurrentOrientation",
            applyOrientation = false
        )
        currentKeyboardOrder = resolution.resolvedIndex ?: 0
        showKeyboard(
            resolution.resolvedKeyboard,
            source = "refreshKeyboardForCurrentOrientation.display"
        )
        setKeyboardSizeSwitchKeyboard(mainView)
    }

    private fun handleLeftCursor(gestureType: GestureType, insertString: String) {
        if (selectMode.value) {
            extendOrShrinkLeftOneChar()
        } else {
            handleLeftKeyPress(gestureType, insertString)
        }
        onLeftKeyLongPressUp.set(true)
    }

    /**
     * テキスト中の「offset」位置から見て、一つ前のグラフェムクラスタ開始位置を返す。
     * 文字列先頭または取得失敗時は 0 を返す。
     */
    private fun previousGraphemeOffset(text: String, offset: Int): Int {
        if (offset <= 0) return 0
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val pos = it.preceding(offset)
        return if (pos == BreakIterator.DONE) 0 else pos
    }

    /**
     * テキスト中の「offset」位置から見て、一つ次のグラフェムクラスタ開始位置を返す。
     * 文字列末尾または取得失敗時は text.length を返す。
     */
    private fun nextGraphemeOffset(text: String, offset: Int): Int {
        if (offset >= text.length) return text.length
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val pos = it.following(offset)
        return if (pos == BreakIterator.DONE) text.length else pos
    }

    /**
     * Returns the end of the first Unicode grapheme cluster in [text]. Android ICU includes
     * emoji ZWJ sequences, regional-indicator flags, variation selectors, and combining marks in
     * one boundary, which is the boundary required by forward-delete editing.
     */
    private fun nextUnicodeGraphemeOffset(text: String, offset: Int): Int {
        if (offset >= text.length) return text.length
        val iterator = AndroidBreakIterator.getCharacterInstance()
        iterator.setText(text)
        val boundary = iterator.following(offset)
        return if (boundary == AndroidBreakIterator.DONE) text.length else boundary
    }

////////////////////////////////////////////////////////////////////////////////
// ─────────────────────────────────────────────────────────────────────────────
//    extendOrShrinkLeftOneChar / extendOrShrinkSelectionRight の修正版
//    （グラフェムクラスタ単位で選択範囲を伸縮）
// ─────────────────────────────────────────────────────────────────────────────
////////////////////////////////////////////////////////////////////////////////

    /** 選択開始時の固定端（アンカー）。-1 は「未設定」を示す */
    private var anchorPos = -1

    /**
     * Shift + ← 相当：左へ「拡張グラフェムクラスタ」1つ分だけ伸ばす／縮める
     */
    private fun extendOrShrinkLeftOneChar() {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return
        val textStr = extracted.text?.toString() ?: return
        val selStart = extracted.selectionStart
        val selEnd = extracted.selectionEnd

        // 0) まだ選択がない（キャレットのみ）
        if (selStart == selEnd) {
            // キャレットが先頭なら何もしない
            if (selStart == 0) return

            // アンカーを現在位置に固定
            anchorPos = selStart

            // 前のグラフェムクラスタ開始位置を取得して選択開始
            val newStart = previousGraphemeOffset(textStr, selStart)

            beginBatchEdit()
            finishComposingText()
            setSelection(newStart, selEnd)
            endBatchEdit()
            return
        }

        // 1) すでに選択がある
        val cursorOnLeft = (anchorPos == selEnd)   // カーソルが選択範囲の左端にあるか
        val cursorOnRight = (anchorPos == selStart) // カーソルが選択範囲の右端にあるか

        when {
            // 1-A: カーソルが左端 → さらに左へ1文字（グラフェム）分伸ばす
            cursorOnLeft -> {
                if (selStart == 0) return
                val newStart = previousGraphemeOffset(textStr, selStart)
                beginBatchEdit()
                finishComposingText()
                setSelection(newStart, selEnd)
                endBatchEdit()
            }

            // 1-B: カーソルが右端 → 右端を1文字（グラフェム）分縮める
            cursorOnRight -> {
                val newEnd = previousGraphemeOffset(textStr, selEnd)
                if (newEnd <= selStart) {
                    // 選択範囲がなくなるのでキャレットのみの状態に戻す
                    beginBatchEdit()
                    finishComposingText()
                    setSelection(selStart, selStart)
                    endBatchEdit()
                    anchorPos = -1
                } else {
                    beginBatchEdit()
                    finishComposingText()
                    setSelection(selStart, newEnd)
                    endBatchEdit()
                }
            }

            else -> {
                // 状態不整合ならアンカーをリセット
                anchorPos = -1
            }
        }
    }

    /**
     * Shift + → 相当：右へ「拡張グラフェムクラスタ」1つ分だけ伸ばす／縮める
     */
    private fun extendOrShrinkSelectionRight() {
        val extracted = getExtractedText(ExtractedTextRequest(), 0) ?: return
        val textStr = extracted.text?.toString() ?: return
        val selStart = extracted.selectionStart
        val selEnd = extracted.selectionEnd
        val textLen = textStr.length

        // 0) まだ選択がない（キャレットのみ）
        if (selStart == selEnd) {
            anchorPos = selStart
            if (selEnd < textLen) {
                val newEnd = nextGraphemeOffset(textStr, selEnd)
                beginBatchEdit()
                finishComposingText()
                setSelection(selStart, newEnd)
                endBatchEdit()
            }
            return
        }

        // 1) すでに選択がある
        val cursorIsOnRight = (anchorPos == selStart)
        if (cursorIsOnRight) {
            // 1-A: カーソルが右端 → さらに右へ1文字（グラフェム）分伸ばす
            if (selEnd < textLen) {
                val newEnd = nextGraphemeOffset(textStr, selEnd)
                beginBatchEdit()
                finishComposingText()
                setSelection(anchorPos, newEnd)
                endBatchEdit()
            }
        } else {
            // 1-B: カーソルが左端 → 左端を1文字（グラフェム）分縮める
            val newStart = nextGraphemeOffset(textStr, selStart)
            if (newStart >= selEnd) {
                // 選択範囲がなくなるのでキャレットのみの状態に戻す
                beginBatchEdit()
                finishComposingText()
                setSelection(selEnd, selEnd)
                endBatchEdit()
                anchorPos = -1
            } else {
                beginBatchEdit()
                finishComposingText()
                setSelection(newStart, selEnd)
                endBatchEdit()
            }
        }
    }

    /**
     * 入力フィールドの全文を全選択する
     */
    private fun selectAllText() {
        if (inputString.value.isNotEmpty()) return
        val request = ExtractedTextRequest()
        // 必要に応じて request.flags を設定（デフォルトで OK）
        val extracted: ExtractedText? = getExtractedText(request, 0)
        val fullText: CharSequence = extracted?.text ?: return
        // 3. テキスト長を取得
        val textLen = fullText.length
        if (textLen == 0) return
        // 4. 選択開始：先頭(0) → 選択終了：全文長
        // ※ beginBatchEdit() / endBatchEdit() で一連の編集をまとめると滑らか
        beginBatchEdit()
        finishComposingText() // もし変換中の文字列があれば確定しておく
        setSelection(0, textLen)
        endBatchEdit()
    }

    private fun clearSelection() {
        // 1. Get the current InputConnection
        val ic = currentInputConnection ?: return

        // 2. Request the extracted text so we know where the selection is
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return

        // 3. Determine where to collapse the cursor.
        //    If there is a selection, `selectionEnd` is the index after the last selected char.
        //    If there is no selection, selStart == selEnd, so this just keeps the cursor where it is.
        val collapsePos = extracted.selectionEnd

        if (collapsePos < 0) return

        // 4. Do a batch edit: finish any composing text, then collapse
        beginBatchEdit()
        finishComposingText()
        ic.setSelection(collapsePos, collapsePos)
        endBatchEdit()
    }

    private fun shouldPreserveDeleteHistoryForAction(action: KeyAction): Boolean {
        return when (action) {
            KeyAction.Delete,
            KeyAction.DeleteUntilSymbol,
            KeyAction.DeleteAfterCursorUntilSymbol,
            KeyAction.DeleteAfterCursor,
            KeyAction.UndoLastDelete,
            KeyAction.DoNothing -> true

            else -> false
        }
    }

    private fun cancelHenkanByLongPressDeleteKey() {
        val insertString = inputString.value
        val selectedSuggestion = suggestionAdapter?.suggestions?.getOrNull(suggestionClickNum)

        deleteKeyLongKeyPressed.set(true)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        suggestionClickNum = 0
        isFirstClickHasStringTail = false
        isContinuousTapInputEnabled.set(true)
        lastFlickConvertedNextHiragana.set(true)
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        clearBunsetsuConversionSession()

        val spannableString = if (insertString.length == selectedSuggestion?.length?.toInt()) {
            SpannableString(insertString + stringInTail)
        } else {
            stringInTail.set("")
            SpannableString(insertString)
        }
        setComposingTextAfterEdit(
            inputString = insertString,
            spannableString = spannableString,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
        )
        mainLayoutBinding?.suggestionRecyclerView?.apply {
            scrollToPosition(0)
        }
    }


    private fun displayedIndexToBunsetsuSelection(
        displayedCandidates: List<Candidate>,
        segmentCandidates: List<Candidate>,
        displayedIndex: Int,
    ): BunsetsuDisplayedSelection? {
        val displayedCandidate = displayedCandidates.getOrNull(displayedIndex) ?: return null
        val segmentIndex = displayedIndex
        val segmentCandidate = segmentCandidates.getOrNull(segmentIndex) ?: return null
        if (segmentCandidate != displayedCandidate) return null

        return BunsetsuDisplayedSelection.SegmentCandidate(
            segmentIndex = segmentIndex,
            candidate = segmentCandidate
        )
    }

    private fun displayedIndexToBunsetsuCandidateIndex(
        displayedCandidates: List<Candidate>,
        segmentCandidates: List<Candidate>,
        displayedIndex: Int,
    ): Int? {
        return when (
            val selection = displayedIndexToBunsetsuSelection(
                displayedCandidates = displayedCandidates,
                segmentCandidates = segmentCandidates,
                displayedIndex = displayedIndex
            )
        ) {
            is BunsetsuDisplayedSelection.SegmentCandidate -> selection.segmentIndex
            else -> null
        }
    }

    private fun bunsetsuCandidateIndexToDisplayedIndex(
        segmentIndex: Int,
        displayedCandidates: List<Candidate>,
    ): Int {
        if (segmentIndex < 0) return RecyclerView.NO_POSITION
        val displayedIndex = segmentIndex
        return if (displayedIndex in displayedCandidates.indices) {
            displayedIndex
        } else {
            RecyclerView.NO_POSITION
        }
    }

    private fun refreshCurrentBunsetsuSuggestionViews(): Boolean {
        val mainView = mainLayoutBinding ?: return false
        val session = bunsetsuConversionSession ?: return false
        if (!isBunsetsuCursorMoveSessionActive()) return false
        if (session.segments.isEmpty()) return false

        val focusedIndex = session.focusedIndex.coerceIn(0, session.segments.lastIndex)
        updateSuggestionViewsForBunsetsuSegment(
            session = session,
            focusedIndex = focusedIndex,
            mainView = mainView
        )
        return true
    }

    private fun resolveNonLoadingCandidateIndex(
        suggestions: List<Candidate>,
        insertString: String,
        requestedIndex: Int
    ): Int? {
        if (suggestions.isEmpty()) return null
        return requestedIndex.coerceIn(0, suggestions.lastIndex)
    }

    @OptIn(FlowPreview::class)
    private fun startScope(mainView: MainLayoutBinding) = scope.launch {
        launch {
            var prevFlag: CandidateShowFlag? = null
            candidateRefreshRequests.collectLatest { request ->
                deleteLongPressConversionGate.onCandidateRefreshStarted()
                val currentFlag = request.flag
                val insertString = request.input
                Timber.d(
                    "candidateRefresh: session=%d revision=%d input=[%s] tail=[%s] previous=%s current=%s",
                    request.sessionId,
                    request.revision,
                    insertString,
                    stringInTail,
                    prevFlag,
                    currentFlag,
                )
                if (!suppressSuggestions && CandidateRefreshTransitionPolicy.shouldEnterActiveCandidatePhase(
                        previousFlag = prevFlag,
                        currentFlag = currentFlag,
                        input = insertString,
                    )
                ) {
                    clearZeroQueryAllState(refresh = false)
                    shortcutToolbarHiddenForCandidates = true
                    refreshCandidateStripContent(candidatesShown = true)
                    if (mainView.customLayoutDefault.isVisible) {
                        animateSuggestionImageViewVisibility(
                            mainView.suggestionVisibility, true
                        )
                    }
                    updateUIinHenkan(mainView, insertString)
                    setSumireKeyboardSwitchNumberAndKatakanaKey(1)
                    if (mainView.customLayoutDefault.isVisible) {
                        updateSumireDakutenKeyForCurrentInput()
                        setSumireKeyboardEnterKey(5)
                        when (currentInputModeForSession) {
                            InputMode.ModeJapanese -> {
                                setSumireKeyboardSpaceKey(1)
                            }

                            else -> {}
                        }
                    }
                }
                when (currentFlag) {
                    CandidateShowFlag.Idle -> {
                        var resetCandidateTabSelection = false
                        setSuggestionAdaptersOnMain(emptyList())
                        if (stringInTail.get().isEmpty()) {
                            shortcutToolbarHiddenForCandidates = false
                            resetCandidateTabSelection = true
                            if (mainView.suggestionVisibility.isVisible) {
                                animateSuggestionImageViewVisibility(
                                    mainView.suggestionVisibility, false
                                )
                            }
                            if (mainView.customLayoutDefault.isVisible) {
                                resetSumireKeyboardDakutenMode()
                            }
                            setKeyboardHeightDefault(mainView)
                            setSumireKeyboardSwitchNumberAndKatakanaKey(0)
                            countToggleKatakana = 0
                        }
                        refreshCandidateStripContent(
                            candidatesShown = false,
                            resetCandidateTabSelection = resetCandidateTabSelection
                        )
                    }

                    CandidateShowFlag.Updating -> {
                        val candidateStripActive = isCandidateStripActive(
                            candidatesShown = true,
                            inputStringEmpty = insertString.isEmpty(),
                            suggestionsSuppressed = suppressSuggestions,
                        )
                        clearZeroQueryAllState(refresh = false)
                        shortcutToolbarHiddenForCandidates = candidateStripActive
                        refreshCandidateStripContent(candidatesShown = candidateStripActive)
                        val normalKeyboardSurfaceVisible = mainView.customLayoutDefault.isVisible
                        if (candidateStripActive && normalKeyboardSurfaceVisible) {
                            // セッション最初の Updating でも候補欄の高さを保証する。
                            setKeyboardHeightWithAdditional(mainView)
                        } else if (!candidateStripActive && normalKeyboardSurfaceVisible) {
                            setKeyboardHeightDefault(mainView)
                        }
                        try {
                            setSuggestionOnView(insertString, mainView)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(
                                e,
                                "Candidate refresh failed: session=%d revision=%d input=[%s]",
                                request.sessionId,
                                request.revision,
                                insertString,
                            )
                            restoreRawComposingTextAfterCandidateFailure(request)
                        }
                    }
                }
                prevFlag = CandidateRefreshTransitionPolicy.nextUiPreviousFlag(
                    currentFlag = currentFlag,
                    input = insertString,
                )
            }
        }

        launch {
            suggestionViewStatus.collectLatest { isVisible ->
                Timber.d("suggestionViewStatus: $isVisible")
                updateSuggestionViewVisibility(mainView, isVisible)
            }
        }

        launch {
            keyboardSymbolViewState.collectLatest { isSymbolKeyboardShow ->
                Timber.d("keyboardSymbolViewState: $isSymbolKeyboardShow")
                clearZeroQueryAllState(refresh = false)
                applySymbolKeyboardAppearance()
                setKeyboardSizeSwitchKeyboard(mainView)
                setKeyboardSizeForHeightSymbol(mainView, isSymbolKeyboardShow.isShown)
                mainView.apply {
                    refreshCandidateStripContent()
                    if (isSymbolKeyboardShow.isShown) {
                        when {
                            customLayoutDefault.isVisible -> {
                                customLayoutDefault.visibility = View.INVISIBLE
                            }



                        }
                        animateViewVisibility(keyboardSymbolView, true)
                        suggestionRecyclerView.isVisible = false
                        if (isSymbolKeyboardShow.mode == SymbolMode.CLIPBOARD) {
                            setSymbolsClipboard(mainView = mainView)
                        } else {
                            setSymbols(mainView)
                        }
                    } else {
                        when {


                            customLayoutDefault.isInvisible -> {
                                customLayoutDefault.isVisible = true
                            }
                        }
                        animateViewVisibility(keyboardSymbolView, false)
                        suggestionRecyclerView.isVisible = true
                        if (customLayoutDefault.isInvisible) customLayoutDefault.visibility =
                            View.VISIBLE
                    }
                }
            }
        }

        launch {
            selectMode.collectLatest { selectMode ->
                Unit
            }
        }

        launch {
            cursorMoveMode.collect { isCursorMoveMode ->
                Unit
            }
        }

        launch {
            qwertyMode.collectLatest {
                Timber.d("qwertyMode value: $it")
                when (it) {

                    TenKeyQWERTYMode.Custom -> {
                        if (customLayouts.isEmpty()) {
                            clearCurrentCustomKeyboardSelection()
                            fallbackFromCustomKeyboardIfNeeded()
                            return@collectLatest
                        } else {
                            if (selectedCustomKeyboardLayoutOrNull() == null) {
                                currentCustomKeyboardPosition = 0
                                currentCustomKeyboardStableId =
                                    customLayouts.first().stableId.takeIf { stableId -> stableId.isNotBlank() }
                            }
                            suggestionAdapter?.updateState(
                                TenKeyQWERTYMode.Custom, customLayouts
                            )
                        }
                    }

                    TenKeyQWERTYMode.Sumire -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.Sumire, emptyList()
                        )
                    }

                    TenKeyQWERTYMode.Number -> {
                        suggestionAdapter?.updateState(
                            TenKeyQWERTYMode.Number, emptyList()
                        )
                    }
                }
                refreshCandidateStripContent()
                // Routing to an already rendered pane is not a layout change. Hiding and
                // showing its keys here cancels the DOWN currently being processed.
                renderCurrentKeyboardStateOnActiveSurface()
            }
        }

        launch {
            clipboardHistoryRepository.allHistory.collectLatest { historyList ->
                cleanupExpiredClipboardItemsIfNeeded()
                val visibleHistoryList = filterClipboardHistoryListByRetention(historyList)
                // 1. DBモデル(軽量メタデータ)のリストからUIモデルのリストに変換する
                //    CursorWindowクラッシュを避けるため、ここでは実データ(全文/Bitmap)を読み込まず
                //    プレビュー用のテキストを保持させる、または ID のみの器を作る。
                val uiItems = visibleHistoryList.map { entity ->
                    when (entity.itemType) {
                        ItemType.TEXT -> {
                            // 一覧表示には DB の preview を使用する
                            ClipboardItem.Text(
                                id = entity.id,
                                text = entity.preview,
                                isPinned = entity.isPinned
                            )
                        }

                        ItemType.IMAGE -> {
                            // 画像の場合、一覧では Bitmap は null (または読み込み専用の器) にする
                            // ※ 必要に応じて placeholder 用の空 Bitmap を渡すか、
                            //    UI 側 (CustomSymbolKeyboardView) で path からロードするように変更します。
                            val content = clipboardHistoryRepository.getThumbnail(entity)
                            if (content is ClipboardItem.Image) {
                                content // 正しい Bitmap が入った ClipboardItem.Image
                            } else {
                                ClipboardItem.Text(
                                    id = entity.id,
                                    text = "[画像の読み込み失敗]",
                                    isPinned = entity.isPinned
                                )
                            }
                        }
                    }
                }

                // 2. 最新のリストをクラスのプロパティにキャッシュする
                currentClipboardItems = uiItems

                // 3. CustomSymbolKeyboardViewの表示を更新する
                mainView.keyboardSymbolView.updateClipboardItems(uiItems)
            }
        }

        launch {
            romajiMapRepository.getActiveMap().map { entity ->
                // Update software settings before distinctUntilChanged. A settings-only update
                // must not recreate/reset the existing physical keyboard converter.
                customRomajiScreenConverter = entity?.takeIf { it.isDeletable }?.let {
                    CustomRomajiScreenConverter(it.mapData, it.autoSokuon, it.autoN)
                }
                entity?.let {
                    Pair(it.mapData, it.isDeletable)
                } ?: Pair(romajiMapRepository.getDefaultMapData(), false)
            }.distinctUntilChanged().collectLatest { (activeMapData, isDeletable) ->
                val converterMap = if (!isDeletable) {
                    activeMapData.mapKeys { (key, _) -> key.toZenkaku() }
                } else {
                    activeMapData
                }
                isDefaultRomajiHenkanMap = !isDeletable
                romajiConverter = RomajiKanaConverter(converterMap)
            }
        }

        launch {
            keyboardRepository.getLayouts().distinctUntilChanged().collectLatest { layouts ->
                val normalizedLayouts = if (layouts.any { it.stableId.isBlank() }) {
                    keyboardRepository.ensureStableIds()
                    keyboardRepository.getLayoutsNotFlow()
                } else {
                    layouts
                }
                onCustomKeyboardLayoutsChanged(normalizedLayouts)
            }
        }

        launch {
            ngWordRepository.getAllNgWordsFlow().collectLatest { ngWords ->
                _ngWordsList.value = ngWords.distinct()
                if (isInputViewActive && inputString.value.isNotEmpty()) {
                    withContext(Dispatchers.Main.immediate) {
                        requestCandidateRefresh(CandidateShowFlag.Updating)
                    }
                }
            }
        }


        launch {
            shortCurRepository.enabledShortcutsFlow.collectLatest {
                configuredShortcutItems = it
                refreshShortcutAvailability()
            }
        }



        launch {
            inputString.collect { string ->
                // 確定・全消去・モード切替で読みが空になったら Zenzai の引き継ぎ制約も捨てる。
                if (string.isEmpty()) zenzaiCarry = null
                try {
                    measureDebugStage("IMEService.input.immediate") {
                        processInputString(string, mainView)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Immediate input processing failed: input=[%s]", string)
                    if (string.isNotEmpty() && inputString.value == string) {
                        applyRawComposingFallback(string)
                    }
                } finally {
                }
            }
        }

    }


    private fun beginZenzRerankRequest(): Long {
        zenzRerankJob?.cancel()
        zenzRerankJob = null
        zeroQueryZenzJob?.cancel()
        zeroQueryZenzJob = null
        zenzRerankRequestToken += 1L
        return zenzRerankRequestToken
    }

    private suspend fun getZenzContext(
        insertString: String,
        leftContextOverride: String? = null
    ): ZenzContext {
        return try {
            val (inputConnection, lastCandidateLength, enableRightContext) =
                withContext(Dispatchers.Main.immediate) {
                    Triple(
                        currentInputConnection,
                        if (isLiveConversionEnable == true) {
                            lastCandidate?.length ?: 0
                        } else {
                            insertString.length
                        },
                        enableZenzRightContextPreference == true,
                    )
                }
            val leftContext = leftContextOverride ?: getLeftContext(
                inputConnection = inputConnection,
                inputLength = lastCandidateLength,
            ).dropLast(lastCandidateLength)
            val rawRightContext = if (enableRightContext) {
                getRightContext(
                    inputConnection = inputConnection,
                    inputLength = lastCandidateLength,
                )
            } else {
                ""
            }
            withContext(Dispatchers.Main.immediate) {
                val resolvedContext = resolveZenzContext(
                    leftContext = leftContext,
                    rawRightContext = rawRightContext,
                    enableRightContext = enableRightContext
                )
                ZenzContext(
                    leftContext = resolvedContext.leftContext,
                    rightContext = resolvedContext.rightContext
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getZenzContext")
            ZenzContext("", "")
        }
    }

    private suspend fun updateDisplayedCandidates(
        insertString: String,
        candidates: List<Candidate>,
        token: CandidateRequestToken? = null,
    ) {
        if (!shouldApplyCandidateResult(insertString, token)) {
            return
        }
        val localCandidates = candidates
        val displayedCandidatesWithZenz = localCandidates
        val displayedCandidates = displayedCandidatesWithZenz
        if (!suppressSuggestions) {
            updateSuggestionAdaptersOnMain(
                candidates = displayedCandidates,
                insertString = insertString,
                fullCandidates = localCandidates,
                token = token,
            )
        }

    }

    private fun shouldApplyCandidateResult(
        requestInput: String,
        token: CandidateRequestToken? = null,
    ): Boolean {
        return deleteLongPressConversionGate.mayApplyCandidateResult() &&
                !suppressSuggestions &&
                requestInput.isNotEmpty() &&
                inputString.value == requestInput &&
                (token == null || (
                    candidateRequestTracker.isCurrent(token) &&
                        editorMutationRevision.isCurrent(token.editorMutationRevision)
                    ))
    }

    private fun clearSuggestionStateAfterCommit() {
        setSuggestionAdaptersOnMain(emptyList())
        filteredCandidateList = emptyList()
        requestCandidateRefresh(CandidateShowFlag.Idle)
        refreshReconversionUi()
    }

    private fun clearSuggestionStateAfterEditorSelectionChange() {
        val hasStaleCandidateState =
            currentCandidateStripCandidates.isNotEmpty() ||
                currentCandidateStripFullCandidates.isNotEmpty() ||
                filteredCandidateList?.isNotEmpty() == true ||
                currentCandidateStripContent is CandidateStripContent.Candidates ||
                currentCandidateStripContent is CandidateStripContent.SelectionActions ||
                shortcutToolbarHiddenForCandidates ||
                candidateRefreshRequests.value.flag != CandidateShowFlag.Idle
        if (hasStaleCandidateState) {
            clearSuggestionStateAfterCommit()
        }
    }

    private fun updateBunsetsuSpaceKeyIfNeeded(
        mainView: MainLayoutBinding,
        candidates: List<Candidate>,
        insertString: String
    ) {
        bunsetsuPositionList?.let {
            if (bunsetusMultipleDetect && it.isNotEmpty()) {
                handleJapaneseModeSpaceKeyWithBunsetsu(
                    mainView, candidates, insertString
                )
            }
        }
    }

    private data class ZenzaiOutcome(
        val top: Candidate,
        val changed: Boolean,
        val inferred: Boolean,
    )

    /** zenz を回す対象か（唯一の設定は zenz の ON/OFF）。 */
    private fun isZenzaiEligible(insertString: String): Boolean =
        necookeyZenzGateEnabled() &&
            insertString.length >= 2 &&
            insertString.length <= necookeyCandidateBarConfig.maxZenzInputLength &&
            insertString.isAllHiraganaWithSymbols() &&
            currentInputType !in passwordTypes

    private fun zenzaiKey(context: ZenzContext, input: String, draft: String): String =
        listOf(context.leftContext, context.rightContext, input, draft)
            .joinToString("\u0001") { "${it.length}:$it" }

    /**
     * Zenzai 方式（1 キー入力につき zenz 推論は最大 1 回）。
     * 1. ドラフト = sumire の最良経路。前回の FIX 制約が今回の読みにも使える（読みが伸びただけ）なら
     *    その制約付きで sumire が再探索したものをドラフトにする（推論なし）。
     * 2. ドラフトを zenz が 1 回評価（同じ文脈・読み・ドラフトは判定キャッシュで推論を省略）。
     * 3. FIX:prefix なら sumire が prefix 制約付きで再探索した結果を採用し、制約を次の入力へ引き継ぐ。
     * 古い推論は呼び出し元ジョブのキャンセルで中断される（最新入力優先）。
     */
    private suspend fun runZenzaiStep(
        insertString: String,
        sumireTop: Candidate,
        context: ZenzContext,
        onProvisional: suspend (Candidate) -> Unit,
    ): ZenzaiOutcome? {
        val session = kanaKanjiConversionSession ?: return null
        val request = lastKanaKanjiQueryRequest?.copy(input = insertString) ?: return null
        val config = withContext(Dispatchers.Default) { resolveZenzRuntimeConfig() } ?: return null

        // ユーザー辞書から来た文節は zenz に書き換えさせない（学習語は守らない）。候補全体がそうなら推論しない。
        val topSegments = sumireTop.conversionSegments.ifEmpty {
            if (latestCandidateSegmentInput == insertString) {
                latestCandidateSegmentsByString[sumireTop.string].orEmpty()
            } else {
                emptyList()
            }
        }
        val protectedSegments = zenzaiProtectedSegments(request, insertString, sumireTop, topSegments)
        if (protectedSegments == null) {
            zenzaiCarry = null
            return null
        }

        // 前回の制約は、その読みが今の読みの先頭に残っている範囲だけ引き継ぐ（後退・途中編集で縮める/捨てる）。
        var constraint = zenzaiCarry?.carriedFor(insertString, context.leftContext)
        var draft = sumireTop
        if (constraint != null) {
            if (!constraint.isRealizedBy(topSegments)) {
                val carried = constraint
                val constrained = withContext(Dispatchers.Default) {
                    session.queryConstrained(request, carried.surface)
                }
                // 表層が一致しても読みの範囲がずれた経路は制約として出さない。
                if (constrained != null && carried.isRealizedBy(constrained.conversionSegments) &&
                    ZenzaiConstraint.preserves(constrained.conversionSegments, protectedSegments)
                ) {
                    draft = constrained.copy(zenzAdjusted = true)
                    onProvisional(draft)
                } else {
                    constraint = null
                }
            }
        }
        val prefix = constraint?.surface.orEmpty()

        val key = zenzaiKey(context, insertString, draft.string)
        val cached = synchronized(zenzaiVerdictCache) { zenzaiVerdictCache[key] }
        val verdict = cached ?: withContext(Dispatchers.Default) {
            zenzRuntimeClient.evaluate(
                config = config,
                profile = "",
                topic = "",
                style = "",
                preference = "",
                leftContext = context.leftContext
                    .takeLast(necookeyCandidateBarConfig.maxLeftContextChars),
                rightContext = context.rightContext
                    .take(necookeyCandidateBarConfig.maxRightContextChars),
                input = insertString.hiraganaToKatakana(),
                candidate = draft.string,
            )
        }
        if (cached == null && !verdict.startsWith("ERROR")) {
            synchronized(zenzaiVerdictCache) { zenzaiVerdictCache[key] = verdict }
        }

        var top = draft
        var fixRejected = false
        if (verdict.startsWith("FIX:")) {
            val fix = verdict.removePrefix("FIX:")
            if (fix.isNotEmpty() && fix != prefix && !draft.string.startsWith(fix)) {
                val corrected = withContext(Dispatchers.Default) {
                    session.queryConstrained(request, fix)
                }
                val fixed = corrected?.let {
                    ZenzaiConstraint.from(context.leftContext, insertString, fix, it.conversionSegments)
                }
                if (corrected != null && fixed != null &&
                    ZenzaiConstraint.preserves(corrected.conversionSegments, protectedSegments)
                ) {
                    top = corrected.copy(zenzAdjusted = true)
                    constraint = fixed
                } else if (corrected != null && fixed != null) {
                    // 直すとユーザー辞書の語が消える: FIX を採らない（印も付けない）。
                    fixRejected = true
                }
            }
        }
        // zenz が評価した（推論・判定キャッシュ再利用）が直さなかった変換には [z] を付ける。
        if (!verdict.startsWith("ERROR") && !top.zenzAdjusted && !fixRejected) {
            top = top.copy(zenzChecked = true)
        }
        zenzaiCarry = constraint
        Timber.d(
            "zenzai: input=[%s] draft=[%s] verdict=[%s] top=[%s] constraint=[%s/%s] inferred=%s",
            insertString, draft.string, verdict, top.string, constraint?.surface, constraint?.reading, cached == null,
        )
        return ZenzaiOutcome(
            top = top,
            changed = top.string != sumireTop.string,
            inferred = cached == null,
        )
    }

    /**
     * [candidate] のうちユーザー辞書の登録語そのもの（読み範囲と出力が一致）の文節。学習語は守らない。
     * 経路の分かれ目が分からないユーザー辞書候補（文節情報なし）は全体を守るため null。
     */
    private suspend fun zenzaiProtectedSegments(
        request: KanaKanjiQueryRequest,
        input: String,
        candidate: Candidate,
        segments: List<CandidateConversionSegment>,
    ): List<CandidateConversionSegment>? {
        val fromUserData = ZenzaiConstraint.isProtectedCandidateType(candidate.type)
        if (segments.isEmpty()) return if (fromUserData) null else emptyList()
        return withContext(Dispatchers.IO) {
            segments.filter { segment ->
                if (segment.inputStart < 0 || segment.inputEnd > input.length) return@filter false
                val reading = input.substring(segment.inputStart, segment.inputEnd)
                isUserDictionaryEnable == true &&
                    request.userDictionaryRepository.exactMatchesForConversion(reading)
                        .any { it.word == segment.output }
            }
        }
    }

    /** ユーザー辞書の候補: 読みが入力と完全一致する語を先に、その中はスコア（小さいほど強い）順。 */
    private fun userDictionaryExactFirst(input: String): Comparator<Candidate> =
        compareBy<Candidate>({ it.length.toInt() != input.length }, { it.score })

    private fun recordZenzaiDiagnostics(outcome: ZenzaiOutcome?) {
        if (outcome == null || !outcome.inferred) return
        ZenzDiagnosticsStore.recordEvaluation(applicationContext, 1, if (outcome.changed) 1 else 0)
    }

    private fun promoteZenzaiCandidate(candidates: List<Candidate>, top: Candidate?): List<Candidate> {
        if (top == null) return candidates
        val index = candidates.indexOfFirst { it.string == top.string }
        if (index == 0) {
            // 同じ候補でも [z]/[Z] の印を反映するため差し替える。
            return if (candidates[0] == top) candidates else listOf(top) + candidates.drop(1)
        }
        val result = candidates.toMutableList()
        if (index > 0) result.removeAt(index)
        result.add(0, top)
        return result
    }

    /** 1 段バー（従来の候補欄）用。結果で先頭を差し替えて再表示する。 */
    private fun launchZenzaiForList(
        requestToken: Long,
        insertString: String,
        baseCandidates: List<Candidate>,
        context: ZenzContext,
        mainView: MainLayoutBinding,
        candidateToken: CandidateRequestToken,
    ) {
        val sumireTop = baseCandidates.firstOrNull {
            it.type != CANDIDATE_TYPE_TEXT_MACRO && it.length.toInt() == insertString.length
        } ?: return
        zenzRerankJob = scope.launch {
            suspend fun show(top: Candidate) {
                if (requestToken != zenzRerankRequestToken ||
                    !shouldApplyCandidateResult(insertString, candidateToken)
                ) return
                val reranked = promoteZenzaiCandidate(baseCandidates, top)
                if (reranked == baseCandidates) return
                updateDisplayedCandidates(
                    insertString = insertString,
                    candidates = reranked,
                    token = candidateToken,
                )
                updateBunsetsuSpaceKeyIfNeeded(mainView, reranked, insertString)
                if (
                    shouldStartLiveConversion(insertString) &&
                    !hasConvertedKatakana &&
                    inputString.value == insertString
                ) {
                    if (getCandidateCommitString(reranked.first()) != lastCandidate) {
                        applyFirstSuggestion(reranked.first())
                    }
                }
            }
            val outcome = try {
                runZenzaiStep(insertString, sumireTop, context, ::show)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "zenzai (list) failed")
                ZenzDiagnosticsStore.recordFailure(applicationContext)
                null
            } ?: return@launch
            recordZenzaiDiagnostics(outcome)
            show(outcome.top)
        }
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val n = minOf(a.length, b.length)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return i
    }

    private fun String.duplicateCharCount(): Int {
        val counts = this.groupingBy { it }.eachCount()
        return counts.values.sumOf { (it - 1).coerceAtLeast(0) } // 2回目以降の総数
    }

    private fun ZenzCandidate.rank(prefix: String): Int {
        val prefixScore = commonPrefixLength(this.string, prefix) * 10
        val kanjiScore = this.string.kanjiCount() * 3

        // ★ここを強めに：重複が多い候補は大きく減点
        val duplicatePenalty = this.string.duplicateCharCount() * 50

        val typeBonus = if (this.type == (40).toByte()) 2 else 0

        return prefixScore + kanjiScore + typeBonus - duplicatePenalty
    }

    private fun getKeyboardSizePreferences(): KeyboardSizePreferences {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return if (isPortrait) {
            KeyboardSizePreferences(
                heightPref = tenkeyHeightPreferenceValue ?: 280,
                widthPref = tenkeyWidthPreferenceValue ?: 100,
                bottomMargin = tenkeyBottomMarginPreferenceValue ?: 0,
                positionIsEnd = tenkeyPositionPreferenceValue ?: true,
                candidateHeight = (candidateViewHeightPreferenceValue ?: 60) + necookeyPredictionRowBudgetDp(),
                candidateEmptyHeight = candidateViewHeightEmptyPreferenceValue ?: 60,
                qwertyHeightPref = qwertyHeightPreferenceValue ?: 280,
                qwertyWidthPref = qwertyWidthPreferenceValue ?: 100,
                qwertyBottomMargin = qwertyBottomMarginPreferenceValue ?: 0,
                qwertyPositionIsEnd = qwertyPositionPreferenceValue ?: true,
                keyboardMarginStart = tenkeyStartMarginPreferenceValue ?: 0,
                keyboardMarginEnd = tenkeyEndMarginPreferenceValue ?: 0,
                qwertyMarginStart = qwertyStartMarginPreferenceValue ?: 0,
                qwertyMarginEnd = qwertyEndMarginPreferenceValue ?: 0
            )
        } else {
            KeyboardSizePreferences(
                heightPref = tenkeyHeightLandScapePreferenceValue ?: 280,
                widthPref = tenkeyWidthLandScapePreferenceValue ?: 100,
                bottomMargin = tenkeyLandScapeBottomMarginPreferenceValue ?: 0,
                positionIsEnd = tenkeyLandScapePositionPreferenceValue ?: true,
                candidateHeight = (candidateViewLandScapeHeightPreferenceValue ?: 60) + necookeyPredictionRowBudgetDp(),
                candidateEmptyHeight = candidateViewLandScapeHeightEmptyPreferenceValue ?: 60,
                qwertyHeightPref = qwertyHeightLandScapePreferenceValue ?: 280,
                qwertyWidthPref = qwertyWidthLandScapePreferenceValue ?: 100,
                qwertyBottomMargin = qwertyLandScapeBottomMarginPreferenceValue ?: 0,
                qwertyPositionIsEnd = qwertyLandScapePositionPreferenceValue ?: true,
                keyboardMarginStart = tenkeyLandScapeStartMarginPreferenceValue ?: 0,
                keyboardMarginEnd = tenkeyLandScapeEndMarginPreferenceValue ?: 0,
                qwertyMarginStart = qwertyLandScapeStartMarginPreferenceValue ?: 0,
                qwertyMarginEnd = qwertyLandScapeEndMarginPreferenceValue ?: 0
            )
        }
    }

    /**
     * キーボードのレイアウトサイズを計算し、ビューに適用する統合関数
     * @param mainView バインディングオブジェクト
     * @param isSymbolOverride シンボルキーボード状態を強制的に上書きする場合にtrue/falseを指定
     * @param isFloating フローティングモードの場合にtrue
     * @param addCandidateTabHeight 候補タブの高さを追加する場合にtrue
     */
    private fun updateKeyboardLayout(
        mainView: MainLayoutBinding,
        isSymbolOverride: Boolean? = null,
        isFloating: Boolean = false,
        addCandidateTabHeight: Boolean = false
    ) {
        // 1. 設定値の読み込み
        val prefs = getKeyboardSizePreferences()
        val orientation = resources.configuration.orientation
        val isPortrait = orientation == Configuration.ORIENTATION_PORTRAIT
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val isSymbol = isSymbolOverride ?: keyboardSymbolViewState.value.isShown
        val forceFullLayout = !addCandidateTabHeight && (
            lastKeyboardLayoutRootView !== mainView.root ||
                lastKeyboardLayoutOrientation != orientation
            )
        applyShortcutToolbarSize(
            mainView = mainView,
            forceLayout = forceFullLayout
        )

        // 2. ピクセル値の計算
        val heightPx = when {
            false || false -> {
                val clampedHeight = if (isPortrait) {
                    prefs.qwertyHeightPref.coerceIn(100, 420)
                } else if (isFloating) {
                    prefs.qwertyHeightPref
                } else {
                    prefs.qwertyHeightPref.coerceIn(100, 420)
                }
                (clampedHeight * density).toInt()
            }

            else -> {
                val clampedHeight = if (isPortrait) {
                    prefs.heightPref.coerceIn(100, 420)
                } else if (isFloating) {
                    prefs.heightPref
                } else {
                    prefs.heightPref.coerceIn(100, 420)
                }
                (clampedHeight * density).toInt()
            }
        }

        val widthPx = when {
            prefs.widthPref == 100 -> ViewGroup.LayoutParams.MATCH_PARENT
            else -> (screenWidth * (prefs.widthPref / 100f)).toInt()
        }

        val qwertyWidthPx = when {
            prefs.qwertyWidthPref == 100 -> ViewGroup.LayoutParams.MATCH_PARENT
            else -> (screenWidth * (prefs.qwertyWidthPref / 100f)).toInt()
        }

        // 3. 最終的な高さ、幅、Gravity、マージンの決定
        val candidatesShown = isCandidateStripActive(
            candidatesShown = addCandidateTabHeight || shortcutToolbarHiddenForCandidates,
            inputStringEmpty = inputString.value.isEmpty(),
            suggestionsSuppressed = suppressSuggestions
        )
        val presentation = resolveCandidateStripPresentation(
            candidatesShown = candidatesShown,
            symbolKeyboardShown = false,
        )
        val configuredCandidateStripHeightDp = resolveCandidateStripHeightDp(
            candidatesShown = candidatesShown,
            candidateHeightDp = prefs.candidateHeight,
            emptyHeightDp = prefs.candidateEmptyHeight
        )
        val candidateStripHeightDp = if (
            !false && keyboardSkinId == KeyboardSkinId.CUPERTINO_CLASSIC
        ) {
            com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CupertinoClassicCandidateChrome
                .resolveDockedStripHeightDp(configuredCandidateStripHeightDp)
        } else {
            configuredCandidateStripHeightDp
        }
        val baseKeyboardHeight = heightPx + if (false) 0 else applicationContext.dpToPx(candidateStripHeightDp)

        // Insets や画面構成の変化による再計算でも、現在表示中の候補タブ領域を
        // 失わないよう、呼び出し元のフラグではなく実際の表示状態から決定する。
        val candidateTabHeightPx = if (false) {
            0
        } else {
            candidateTabHeightPx(mainView)
        }
        val candidateTabOffset = resolveCandidateTabOffsetPx(
            presentation = presentation,
            candidateTabHeightPx = candidateTabHeightPx
        )
        val dockedCandidateChromeHeight = if (false) {
            0
        } else {
            resolveDockedCandidateChromeHeightPx(
                presentation = presentation,
                candidateTabHeightPx = candidateTabHeightPx,
                shortcutToolbarHeightPx = shortcutToolbarHeightPx()
            )
        }
        val contentKeyboardHeight = if (false) {
            heightPx
        } else {
            baseKeyboardHeight + dockedCandidateChromeHeight
        }
        // Keep the candidate/body budget independent from the navigation-bar safe area.
        // The visible input view includes the safe area, while its bottom padding keeps content
        // above it. This prevents the inset from shrinking the configured candidate view.
        val inputViewHeight = contentKeyboardHeight + systemBottomInset
        fun candidateHeightPx(configuredHeightDp: Int): Int = applicationContext.dpToPx(
            if (keyboardSkinId == KeyboardSkinId.CUPERTINO_CLASSIC) {
                com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.CupertinoClassicCandidateChrome
                    .resolveDockedStripHeightDp(configuredHeightDp)
            } else {
                configuredHeightDp
            }
        )
        val independentToolbarEnabled = shortcutTollbarVisibility == true &&
            shortcutToolbarIntegratedInSuggestion != true
        val reserveCandidateDrawingSpace = !isSymbol && !false &&
            true &&
            true && !isFullscreenMode &&
            (stabilizeCandidateStripHeightPreference || presentation.showIndependentShortcutToolbar ||
                presentation.reserveIndependentShortcutToolbarSpace)
        val transparentContainerHeight = if (reserveCandidateDrawingSpace) {
            resolveDockedCandidateContainerHeightPx(
                keyboardBodyHeightPx = heightPx,
                emptyCandidateHeightPx = candidateHeightPx(prefs.candidateEmptyHeight),
                activeCandidateHeightPx = candidateHeightPx(prefs.candidateHeight),
                candidateTabHeightPx = if (candidateTabVisibility == true) candidateTabHeightPx else 0,
                shortcutToolbarHeightPx = if (independentToolbarEnabled) shortcutToolbarHeightPx() else 0,
                bottomInsetPx = systemBottomInset
            )
        } else {
            null
        }
        val backgroundSurfaceHeight = if (false) {
            heightPx
        } else {
            contentKeyboardHeight - candidateTabOffset
        }

        val finalKeyboardWidth =
            widthPx

        val finalStartMargin =
            dpToPx(prefs.keyboardMarginStart)

        val finalEndMargin =
            dpToPx(prefs.keyboardMarginEnd)

        val finalBottomMargin =
            prefs.bottomMargin

        updateDockedCandidateContainerHeight(
            heightPx = transparentContainerHeight?.plus(finalBottomMargin),
            stabilizeInsets = stabilizeCandidateStripHeightPreference
        )

        val positionIsEnd =
            prefs.positionIsEnd
        val gravity =
            if (positionIsEnd) (Gravity.BOTTOM or Gravity.END) else (Gravity.BOTTOM or Gravity.START)

        // 4. レイアウトパラメータの適用
        applyKeyboardLayoutParameters(
            mainView = mainView,
            heightPx = heightPx,
            candidateStripHeightPx = when {
                false -> null
                keyboardSkinId == KeyboardSkinId.CUPERTINO_CLASSIC ->
                    applicationContext.dpToPx(candidateStripHeightDp)
                else -> ViewGroup.LayoutParams.WRAP_CONTENT
            },
            finalKeyboardHeight = inputViewHeight,
            backgroundSurfaceHeight = backgroundSurfaceHeight,
            finalKeyboardWidth = finalKeyboardWidth,
            gravity = gravity,
            finalBottomMargin = finalBottomMargin,
            finalStartMargin = finalStartMargin,
            finalEndMargin = finalEndMargin,
            forceLayout = forceFullLayout
        )

        if (isSymbol) {
            (mainView.keyboardSymbolView.layoutParams as? FrameLayout.LayoutParams)?.let { param ->
                // The root reserves the navigation-bar inset as bottom padding, so the
                // symbol surface should consume the complete content budget. Shrinking
                // it by a fixed portrait-only amount leaves an empty strip above the
                // bottom-aligned view.
                param.height = contentKeyboardHeight.coerceAtLeast(0)
                param.width = finalKeyboardWidth
                mainView.keyboardSymbolView.layoutParams = param
            }
        }

        // 5. 個別処理
        if (addCandidateTabHeight) {
            val params = mainView.suggestionVisibility.layoutParams as ConstraintLayout.LayoutParams
            if (params.bottomToBottom != ConstraintLayout.LayoutParams.UNSET) {
                params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                mainView.suggestionVisibility.layoutParams = params
            }
        }

        lastKeyboardLayoutRootView = mainView.root
        lastKeyboardLayoutOrientation = orientation
    }

    /**
     * 計算されたレイアウトパラメータを各ビューに適用するヘルパー関数
     */
    private fun updateNormalKeyboardBackgroundBounds(
        mainView: MainLayoutBinding,
        heightPx: Int
    ) {
        if (heightPx <= 0) return

        (mainView.keyboardBackgroundContainer.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            var changed = false

            if (params.height != heightPx) {
                params.height = heightPx
                changed = true
            }

            if (params.gravity != Gravity.BOTTOM) {
                params.gravity = Gravity.BOTTOM
                changed = true
            }

            if (changed) {
                mainView.keyboardBackgroundContainer.layoutParams = params
            }
        }
    }

    private fun updateNormalKeyboardTouchEffectBounds(
        mainView: MainLayoutBinding,
        keyboardBodyHeightPx: Int
    ) {
        if (keyboardBodyHeightPx <= 0) return

        val container = mainView.keyboardTouchEffectContainer
        val params = container.layoutParams as? FrameLayout.LayoutParams ?: return
        var changed = false

        if (params.height != keyboardBodyHeightPx) {
            params.height = keyboardBodyHeightPx
            changed = true
        }

        if (params.gravity != Gravity.BOTTOM) {
            params.gravity = Gravity.BOTTOM
            changed = true
        }

        if (changed) {
            container.layoutParams = params
        }
    }

    private fun updateDockedCandidateContainerHeight(heightPx: Int?, stabilizeInsets: Boolean = false) {
        val container = keyboardContainer ?: return
        dockedCandidateContainerActive = heightPx != null
        dockedCandidateHeightStabilized = heightPx != null && stabilizeInsets
        val height = heightPx ?: ViewGroup.LayoutParams.WRAP_CONTENT
        val params = container.layoutParams ?: FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, height
        )
        if (params.height != height || container.layoutParams == null) {
            params.height = height
            container.layoutParams = params
        }
    }

    private fun applyKeyboardLayoutParameters(
        mainView: MainLayoutBinding,
        heightPx: Int,
        candidateStripHeightPx: Int?,
        finalKeyboardHeight: Int,
        backgroundSurfaceHeight: Int,
        finalKeyboardWidth: Int,
        gravity: Int,
        finalBottomMargin: Int,
        finalStartMargin: Int,
        finalEndMargin: Int,
        forceLayout: Boolean
    ) {
        if (shouldUseIndependentShortcutToolbar()) {
            (mainView.shortcutToolbarRecyclerview.layoutParams as? FrameLayout.LayoutParams)?.let { param ->
                val bottomMargin = heightPx + mainView.suggestionViewParent.height
                if (forceLayout || param.bottomMargin != bottomMargin) {
                    param.bottomMargin = bottomMargin
                    mainView.shortcutToolbarRecyclerview.layoutParams = param
                }
            }
        }

        listOf(
            mainView.suggestionViewParent,
            mainView.customLayoutDefault,
            mainView.candidatesRowView
        ).forEach { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                var changed = false
                if (view != mainView.suggestionViewParent) {
                    if (forceLayout || params.height != heightPx) {
                        params.height = heightPx
                        changed = true
                    }
                } else {
                    candidateStripHeightPx?.let { candidateHeight ->
                        if (forceLayout || params.height != candidateHeight) {
                            params.height = candidateHeight
                            changed = true
                        }
                    }
                    if (forceLayout || params.bottomMargin != heightPx) {
                        params.bottomMargin = heightPx
                        changed = true
                    }
                }
                if (forceLayout || params.gravity != gravity) {
                    params.gravity = gravity
                    changed = true
                }
                if (changed) {
                    view.layoutParams = params
                }
            }
        }

        (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            var changed = false
            // All themes share the same content budget and bottom padding.
            val windowHeight = finalKeyboardHeight
            if (forceLayout || params.height != windowHeight) {
                params.height = windowHeight
                changed = true
            }
            if (forceLayout || params.width != finalKeyboardWidth) {
                params.width = finalKeyboardWidth
                changed = true
            }
            if (forceLayout || params.bottomMargin != finalBottomMargin) {
                params.bottomMargin = finalBottomMargin
                changed = true
            }
            if (forceLayout || params.leftMargin != finalStartMargin) {
                params.leftMargin = finalStartMargin
                changed = true
            }
            if (forceLayout || params.rightMargin != finalEndMargin) {
                params.rightMargin = finalEndMargin
                changed = true
            }
            if (forceLayout || params.gravity != gravity) {
                params.gravity = gravity
                changed = true
            }
            if (changed) {
                mainView.root.layoutParams = params
            }
        }

        updateNormalKeyboardBackgroundBounds(
            mainView = mainView,
            heightPx = backgroundSurfaceHeight
        )
        updateNormalKeyboardTouchEffectBounds(
            mainView = mainView,
            keyboardBodyHeightPx = heightPx
        )
        updateLuminousBlobEffectBounds(
            blobView = mainView.luminousBlobEffectView,
            heightPx = heightPx
        )

        if (
            mainView.root.paddingLeft != 0 ||
            mainView.root.paddingTop != 0 ||
            mainView.root.paddingRight != 0 ||
            mainView.root.paddingBottom != systemBottomInset
        ) {
            mainView.root.setPadding(0, 0, 0, systemBottomInset)
        }
    }

    private fun setKeyboardHeightWithAdditionalOriginal(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardHeightWithAdditional called")
        if (currentInputType.isPassword()) return
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val screenWidth = resources.displayMetrics.widthPixels
        val density = resources.displayMetrics.density

        val heightPref = if (isPortrait) {
            tenkeyHeightPreferenceValue ?: 280
        } else {
            tenkeyHeightLandScapePreferenceValue ?: 220
        }
        val widthPref = if (isPortrait) {
            tenkeyWidthPreferenceValue ?: 100
        } else {
            tenkeyWidthLandScapePreferenceValue ?: 100
        }
        val keyboardBottomMargin = if (isPortrait) {
            tenkeyBottomMarginPreferenceValue ?: 0
        } else {
            tenkeyLandScapeBottomMarginPreferenceValue ?: 0
        }

        val positionPref = if (isPortrait) {
            tenkeyPositionPreferenceValue ?: true
        } else {
            tenkeyLandScapePositionPreferenceValue ?: true
        }
        val qwertyHeightPref = if (isPortrait) {
            qwertyHeightPreferenceValue ?: 280
        } else {
            qwertyHeightLandScapePreferenceValue ?: 220
        }
        val qwertyWidthPref = if (isPortrait) {
            qwertyWidthPreferenceValue ?: 100
        } else {
            qwertyWidthLandScapePreferenceValue ?: 100
        }
        val qwertyPositionPref = if (isPortrait) {
            qwertyPositionPreferenceValue ?: true
        } else {
            qwertyLandScapePositionPreferenceValue ?: true
        }
        val qwertyKeyboardMarginBottomPref = if (isPortrait) {
            qwertyBottomMarginPreferenceValue ?: 0
        } else {
            qwertyLandScapeBottomMarginPreferenceValue ?: 0
        }

        val heightPx = when {
            keyboardSymbolViewState.value.isShown -> {
                val height = if (isPortrait) 320 else 220
                (height * density).toInt()
            }

            false || false -> {
                val clampedHeight = qwertyHeightPref.coerceIn(60, 420)
                (clampedHeight * density).toInt()
            }

            else -> {
                val clampedHeight = heightPref.coerceIn(60, 420)
                (clampedHeight * density).toInt()
            }
        }

        val widthPx = when {
            widthPref == 100 -> {
                ViewGroup.LayoutParams.MATCH_PARENT
            }

            else -> {
                (screenWidth * (widthPref / 100f)).toInt()
            }
        }

        val qwertyWidthPx = when {
            qwertyWidthPref == 100 -> {
                ViewGroup.LayoutParams.MATCH_PARENT
            }

            else -> {
                (screenWidth * (qwertyWidthPref / 100f)).toInt()
            }
        }

        val suggestionHeightInDp = if (isPortrait) {
            candidateViewHeightPreferenceValue ?: 60
        } else {
            candidateViewLandScapeHeightPreferenceValue ?: 60
        }

        val keyboardHeight = if (isPortrait) {
            if (keyboardSymbolViewState.value.isShown) heightPx + applicationContext.dpToPx(50) else heightPx + applicationContext.dpToPx(
                suggestionHeightInDp
            )
        } else {
            if (keyboardSymbolViewState.value.isShown) heightPx else heightPx + applicationContext.dpToPx(
                suggestionHeightInDp
            )
        }

        val presentation = resolveCandidateStripPresentation(candidatesShown = true)
        val candidateTabOffset = resolveCandidateTabOffsetPx(
            presentation = presentation,
            candidateTabHeightPx = candidateTabHeightPx(mainView)
        )
        val finalKeyboardHeight = keyboardHeight + candidateTabOffset
        val backgroundSurfaceHeight = finalKeyboardHeight - candidateTabOffset

        val finalKeyboardWidth =
            widthPx

        val gravity =
            if (positionPref) {
                Gravity.BOTTOM or Gravity.END
            } else {
                Gravity.BOTTOM or Gravity.START
            }

        val finalBottomMargin =
            keyboardBottomMargin

        listOf(
            mainView.suggestionViewParent,
            mainView.customLayoutDefault,
        ).forEach { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                if (view != mainView.suggestionViewParent) params.height = heightPx
                else params.bottomMargin = heightPx
                params.gravity = gravity
                view.layoutParams = params
            }
        }

        (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.height = finalKeyboardHeight
            params.width = finalKeyboardWidth
            params.bottomMargin = finalBottomMargin
            mainView.root.layoutParams = params
        }

        updateNormalKeyboardBackgroundBounds(
            mainView = mainView,
            heightPx = backgroundSurfaceHeight
        )
        updateNormalKeyboardTouchEffectBounds(
            mainView = mainView,
            keyboardBodyHeightPx = heightPx
        )
        updateLuminousBlobEffectBounds(
            blobView = mainView.luminousBlobEffectView,
            heightPx = heightPx
        )

        // Adjust suggestion view constraints since it's no longer attached to the parent bottom
        val params = mainView.suggestionVisibility.layoutParams as ConstraintLayout.LayoutParams
        params.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        mainView.suggestionVisibility.layoutParams = params

        mainView.root.setPadding(0, 0, 0, systemBottomInset)
    }

    private fun setKeyboardSizeForHeightSymbol(mainView: MainLayoutBinding, isSymbol: Boolean) {
        Timber.d("Keyboard Height: setKeyboardSizeForHeightSymbol called")
        updateKeyboardLayout(mainView, isSymbolOverride = isSymbol)
    }

    private fun setKeyboardHeightWithAdditional(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardHeightWithAdditional called")
        // Password fields can allow candidates. Their tab offset and content height
        // must be updated together, just as for any other composing field.
        if (suppressSuggestions) return
        updateKeyboardLayout(
            mainView = mainView,
            addCandidateTabHeight = true
        )
    }

    private fun setKeyboardHeightDefault(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardHeightDefault called")
        updateKeyboardLayout(mainView)
    }

    private fun setKeyboardSizeSwitchKeyboard(mainView: MainLayoutBinding) {
        Timber.d("Keyboard Height: setKeyboardSizeSwitchKeyboard called")
        updateKeyboardLayout(mainView)
    }

    private fun updateSuggestionViewVisibility(
        mainView: MainLayoutBinding, isVisible: Boolean
    ) {
        applyCandidateExpandedSurfaceBackground(
            mainView.candidatesRowView,
            transparentWithLiquidGlass = true,
        )
        animateViewVisibility(mainView.candidatesRowView, !isVisible)
        mainView.candidatesRowView.scrollToPosition(0)
        hideFirstRowCandidatesInFullScreen(mainView)
        if (isVisible) {
            mainLayoutBinding?.apply {
                when {
                    customLayoutDefault.isInvisible -> {
                        animateViewVisibility(
                            customLayoutDefault, isVisible = true, true
                        )
                    }



                }
            }
        } else {
            mainLayoutBinding?.apply {
                when {
                    customLayoutDefault.isVisible -> customLayoutDefault.visibility = View.INVISIBLE
                }
            }
        }
        mainView.suggestionVisibility.apply {
            this.setImageDrawable(if (isVisible) cachedArrowDropDownDrawable else cachedArrowDropUpDrawable)
            isSelected = !isVisible
        }
        if (!isVisible) {
            // The full candidate view was intentionally not diffed while hidden. Submit the
            // latest state after it becomes the active surface.
            refreshCandidateStripContent(candidatesShown = true)
        }
    }

    private fun animateViewVisibility(
        mainView: View, isVisible: Boolean, withAnimation: Boolean = true
    ) {
        mainView.animate().cancel()

        if (isVisible) {
            mainView.visibility = View.VISIBLE

            if (withAnimation) {
                mainView.translationY = mainView.height.toFloat() // Start from hidden position
                mainView.animate().translationY(0f) // Animate to visible position
                    .setDuration(150).setInterpolator(AccelerateDecelerateInterpolator()).start()
            } else {
                mainView.translationY = 0f
            }
        } else {
            if (withAnimation) {
                mainView.translationY = 0f
                mainView.animate().translationY(mainView.height.toFloat()).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                        mainView.visibility = View.GONE
                    }.start()
            } else {
                mainView.visibility = View.GONE
            }
        }
    }

    private fun animateSuggestionImageViewVisibility(
        mainView: View, isVisible: Boolean
    ) {
        mainView.post {
            mainView.pivotX = mainView.width / 2f
            mainView.pivotY = mainView.height / 2f

            if (isVisible) {
                mainView.visibility = View.VISIBLE
                mainView.scaleX = 0f
                mainView.scaleY = 0f

                mainView.animate().scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                        mainView.scaleX = 1f
                        mainView.scaleY = 1f
                    }.start()
            } else {
                mainView.visibility = View.VISIBLE
                mainView.scaleX = 1f
                mainView.scaleY = 1f

                mainView.animate().scaleX(0f).scaleY(0f).setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
                        mainView.visibility = View.GONE
                        mainView.scaleX = 1f
                        mainView.scaleY = 1f
                    }.start()
            }
        }
    }

    private fun hideFirstRowCandidatesInFullScreen(
        mainView: MainLayoutBinding
    ) {
        assertMainThread("hideFirstRowCandidatesInFullScreen")
        mainView.candidatesRowView.post {
            if (!mainView.candidatesRowView.canScrollVertically(-1)) {
                val flexboxManager =
                    mainView.candidatesRowView.layoutManager as? FlexboxLayoutManager ?: return@post
                val flexLines = flexboxManager.flexLines

                if (flexLines.isNotEmpty()) {
                    val firstRowHeight = flexLines[0].crossSize
                    mainView.candidatesRowView.scrollBy(0, firstRowHeight)
                }
            }
        }
    }

    private fun processInputString(
        string: String, mainView: MainLayoutBinding,
    ) {
        defaultInputFinalizeJob?.cancel()
        defaultInputFinalizeJob = null
        if (string.isNotEmpty()) {
            clearZeroQueryAllState(refresh = false)
            hasConvertedKatakana = false
            if (isRestoringReconversionInput) {
                isRestoringReconversionInput = false
            } else {
                clearPendingReconversionEntry()
            }
            if (preserveBunsetsuReconversionDraftOnNextProcessInput) {
                preserveBunsetsuReconversionDraftOnNextProcessInput = false
            } else {
                clearBunsetsuReconversionDraft()
            }
            if (deleteLongPressConversionGate.shouldRenderRawComposing(string)) {
                Timber.d("deleteLongPress: render raw composing input=[%s]", string)
                applyRawComposingFallback(string)
                refreshReconversionUi()
                return
            }
            if (suppressSuggestions) {
                setComposingText(string, 1)
                refreshReconversionUi()
                return
            }
            handleDefaultInput(string)
        } else {
            if (stringInTail.get().isNotEmpty()) {
                setComposingText(stringInTail.get(), 1)
                onLeftKeyLongPressUp.set(true)
                onDeleteLongPressUp.set(true)
            } else {
                setDrawableToEnterKeyCorrespondingToImeOptions(mainView)
                onLeftKeyLongPressUp.set(true)
                onRightKeyLongPressUp.set(true)
                onDeleteLongPressUp.set(true)
            }
            hasConvertedKatakana = false
            filteredCandidateList = emptyList()
            resetInputString()
            lastCandidate = ""
            hardKeyboardShiftPressd = false

        }
        refreshReconversionUi()
    }

    /**
     * TenKeyQWERTY以外のモードの入力処理を担当します。
     */
    private fun handleDefaultInput(string: String) {
        if (qwertyMode.value == TenKeyQWERTYMode.Custom) {
            renderCustomKeyboardComposingText(string)
            requestCandidateRefresh(CandidateShowFlag.Updating, string)
            return
        }
        val spannable = createSpannableWithTail(string)
        if (!(shouldStartLiveConversion(string) && isFlickOnlyMode == true)) {
            setComposingTextPreEdit(
                inputString = string,
                spannableString = spannable,
                backgroundColor = if (customComposingTextPreference == true) {
                    inputCompositionBackgroundColor
                        ?: getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                } else {
                    getColor(com.kazumaproject.core.R.color.char_in_edit_color)
                },
                textColor = if (customComposingTextPreference == true) inputCompositionTextColor else null
            )
        }
        requestCandidateRefresh(CandidateShowFlag.Updating, string)
        if (isLiveConversionEnable != true) {
            scheduleDefaultInputFinalize(string)
        }
    }

    private fun scheduleDefaultInputFinalize(string: String) {
        if (qwertyMode.value == TenKeyQWERTYMode.Custom) return
        // フリック専用入力はすでに編集後の背景で表示されており、トグル待機は不要。
        if (isFlickOnlyMode == true) return

        val timeToDelay = delayTime?.toLong() ?: DEFAULT_DELAY_MS
        val mutationRevision = editorMutationRevision.current()
        defaultInputFinalizeJob = scope.launch {
            measureDebugStage("IMEService.input.finalizeDelay") {
                delay(timeToDelay)
            }

            if (
                inputString.value != string ||
                !editorMutationRevision.isCurrent(mutationRevision) ||
                isLiveConversionEnable == true
            ) {
                return@launch
            }

            val shouldCommitOriginalText =
                inputString.value.isNotEmpty() && !isHenkan.get() && !onDeleteLongPressUp.get() && !englishSpaceKeyPressed.get() && !deleteKeyLongKeyPressed.get() && !hasConvertedKatakana

            if (shouldCommitOriginalText) {
                isContinuousTapInputEnabled.set(true)
                lastFlickConvertedNextHiragana.set(true)
                setComposingTextAfterEdit(
                    inputString = string,
                    spannableString = createSpannableWithTail(string),
                    backgroundColor = if (customComposingTextPreference == true) {
                        inputCompositionAfterBackgroundColor
                            ?: getColor(com.kazumaproject.core.R.color.blue)
                    } else {
                        getColor(com.kazumaproject.core.R.color.blue)
                    },
                    textColor = if (customComposingTextPreference == true) {
                        inputCompositionTextColor
                    } else {
                        null
                    }
                )
            }
        }
    }

    /**
     * サジェスト候補リストの先頭にある文字列を取得し、編集後のテキストとして設定します。
     * このロジックは複数箇所で使われるため、関数として抽出しました。
     */
    private fun getCandidateCommitString(candidate: Candidate): String {
        return if (candidate.type == (15).toByte()) {
            candidate.string.correctReading().first
        } else {
            candidate.commitText
        }
    }

    private fun shouldStartLiveConversion(input: String): Boolean {
        return isLiveConversionEnable == true && input.length >= liveConversionStartLength
    }

    private fun requestCandidateRefresh(
        flag: CandidateShowFlag,
        input: String = inputString.value,
    ) {
        candidateRefreshCoordinator.request(input = input, flag = flag)
    }

    private fun restoreRawComposingTextAfterCandidateFailure(
        request: CandidateRefreshRequest,
    ) {
        if (!candidateRefreshCoordinator.isCurrent(request)) return
        if (request.input.isEmpty() || inputString.value != request.input) return
        if (!shouldStartLiveConversion(request.input) || isFlickOnlyMode != true) return

        applyRawComposingFallback(request.input)
    }

    private fun applyRawComposingFallback(input: String) {
        setComposingTextAfterEdit(
            inputString = input,
            spannableString = createSpannableWithTail(input),
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            },
        )
    }

    private fun liveConversionApplyDelayMillis(): Long {
        if (qwertyMode.value == TenKeyQWERTYMode.Custom) {
            return customToggleRemainingMillis()
        }
        val originalDelay = delayTime?.toLong() ?: DEFAULT_DELAY_MS

        if (isFlickOnlyMode == true) {
            return 0L
        }

        return if (requiresOriginalInputProtectionDelay()) {
            originalDelay
        } else {
            minOf(originalDelay, DEFAULT_LIVE_CONVERSION_APPLY_DELAY_MS)
        }
    }

    private fun requiresOriginalInputProtectionDelay(): Boolean {
        if (stringInTail.get().isNotEmpty()) return true
        if (hasConvertedKatakana) return true
        if (isBunsetsuCursorMoveSessionActive()) return true
        if ((bunsetusMultipleDetect || henkanPressedWithBunsetsuDetect)) {
            return true
        }

        return when (qwertyMode.value) {
            TenKeyQWERTYMode.Sumire, TenKeyQWERTYMode.Custom, TenKeyQWERTYMode.Number -> true
        }
    }

    private suspend fun delayBeforeApplyingLiveConversion() {
        if (qwertyMode.value == TenKeyQWERTYMode.Custom) {
            // Equal toggle outputs do not restart the candidate request. Recheck the deadline
            // after waking so another tap cannot leave that request using an older timeout.
            while (qwertyMode.value == TenKeyQWERTYMode.Custom) {
                val remaining = customToggleRemainingMillis()
                if (remaining <= 0L) break
                delay(remaining)
            }
            return
        }
        val applyDelay = liveConversionApplyDelayMillis()
        measureDebugStage("IMEService.liveConversionApplyDelay") {
            if (applyDelay > 0L) {
                delay(applyDelay)
            }
        }
    }

    private fun applyFirstSuggestion(
        candidate: Candidate
    ) {
        // Once a candidate owns the composing display, an old toggle timer must not restore kana.
        if (qwertyMode.value == TenKeyQWERTYMode.Custom) resetCustomToggleState()
        beginBatchEdit()
        val commitString = getCandidateCommitString(candidate)
        lastCandidate = commitString
        val newSpannable = createSpannableWithTail(commitString)
        setComposingTextAfterEdit(
            inputString = commitString,
            spannableString = newSpannable,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
        )
        endBatchEdit()
    }

    /**
     * 末尾文字列を結合したSpannableStringを生成します。
     */
    private fun createSpannableWithTail(text: String): SpannableString {
        return SpannableString(text + stringInTail.get())
    }

    private fun isBunsetsuCursorMoveSessionActive(): Boolean {
        return isHenkan.get() &&
                bunsetsuConversionSession != null
    }

    private fun sanitizeSplitPositions(
        input: String,
        splitPositions: List<Int>
    ): List<Int> {
        return splitPositions
            .filter { it in 1 until input.length }
            .distinct()
            .sorted()
    }

    private fun normalizeBunsetsuSplitPatterns(
        input: String,
        splitPatterns: List<List<Int>>,
        initialSplitPositions: List<Int>,
    ): List<List<Int>> {
        val initialPattern = sanitizeSplitPositions(input, initialSplitPositions)
        val normalizedPatterns = splitPatterns
            .map { sanitizeSplitPositions(input, it) }
            .distinct()

        return buildList {
            add(initialPattern)
            normalizedPatterns.forEach { pattern ->
                if (pattern != initialPattern) {
                    add(pattern)
                }
            }
        }
    }

    private fun resolveInitialBunsetsuSplitPositions(
        input: String,
        mergedCandidates: List<Candidate>,
        engineResult: BunsetsuCandidateResult?
    ): List<Int> {
        val firstCandidate = mergedCandidates.firstOrNull() ?: return emptyList()
        val result = engineResult ?: return emptyList()
        if (!result.candidates.contains(firstCandidate)) {
            return emptyList()
        }

        val candidatePattern = result.splitPatternByCandidateString[firstCandidate.string]
            ?: if (result.candidates.firstOrNull() == firstCandidate) {
                result.primarySplitPositions
            } else {
                emptyList()
            }

        return sanitizeSplitPositions(input, candidatePattern)
    }

    private fun updateBunsetsuStateAfterCandidateMerge(
        input: String,
        mergedCandidates: List<Candidate>,
        engineResult: BunsetsuCandidateResult?,
        candidateSegments: Map<String, List<CandidateConversionSegment>>,
    ) {
        if (engineResult == null) {
            latestBunsetsuConversionSnapshot = null
            bunsetsuSplitPatterns = emptyList()
            bunsetsuPositionList = emptyList()
            return
        }

        bunsetsuSplitPatterns = engineResult.splitPatterns
            .map { sanitizeSplitPositions(input, it) }
            .distinct()
        bunsetsuPositionList = resolveInitialBunsetsuSplitPositions(
            input = input,
            mergedCandidates = mergedCandidates,
            engineResult = engineResult
        )
        latestBunsetsuConversionSnapshot = BunsetsuConversionSnapshot(
            input, mergedCandidates, candidateSegments, bunsetsuSplitPatterns,
            bunsetsuPositionList.orEmpty(),
        )
    }

    private fun buildBunsetsuSegments(
        input: String,
        splitPositions: List<Int>,
        snapshot: BunsetsuConversionSnapshot?,
    ): List<BunsetsuSegmentState> {
        val ngWords = if (isNgWordEnable == true) ngWordsList.value else emptyList()
        return buildConvertedBunsetsuSegments(
            input, sanitizeSplitPositions(input, splitPositions), snapshot, ::displayTextFromCandidate,
        ).map { segment ->
            // A full-input candidate can pass an exact NG rule that matches one of its segments.
            // Invalidate before preparation so even unfocused segments load a permitted alternative.
            if (NgWordMatcher.matchesAny(segment.reading, segment.displayText, ngWords)) {
                segment.copy(displayText = segment.reading, hasConvertedDisplay = false)
            } else {
                segment
            }
        }
    }

    private fun displayTextFromCandidate(candidate: Candidate): String {
        return if (candidate.type == (15).toByte()) {
            candidate.string.correctReading().first
        } else {
            candidate.string
        }
    }

    private suspend fun queryBunsetsuConversion(input: String): KanaKanjiQueryResult {
        // Query the converter directly: suggestion lookup also mutates the whole-input split state.
        val result = withContext(kanaKanjiConversionDispatcher) {
            queryKanaKanjiCore(
                input = input,
                mode = CandidateQueryMode.CONVERSION,
                learnRepository = learnedRepositoryForSuggestion(),
            )
        }
        val romajiCandidates = if (conversionCandidatesRomajiEnablePreference == true) {
            getRomajiCandidates(input)
        } else {
            emptyList()
        }
        val templateCandidates = getLegacyUserTemplateCandidates(input)
        val ngWords = if (isNgWordEnable == true) ngWordsList.value else emptyList()
        val candidates = (templateCandidates + result.candidates + romajiCandidates).filter {
            it.length.toInt() == input.length &&
                !NgWordMatcher.matchesAny(input, it.string, ngWords)
        }.withoutHentaiganaCandidatesIfNeeded().distinctBy { it.string }
        return result.copy(candidates = candidates)
    }

    private suspend fun loadCandidatesForBunsetsuSegment(
        session: BunsetsuConversionSession,
        segmentIndex: Int,
    ): BunsetsuConversionSession {
        val targetSegment = session.segments.getOrNull(segmentIndex) ?: return session
        if (targetSegment.candidatesLoaded) return session

        val candidates = queryBunsetsuConversion(targetSegment.reading).candidates
        val updatedSegments = session.segments.toMutableList()
        updatedSegments[segmentIndex] = mergeBunsetsuCandidates(
            targetSegment, candidates, ::displayTextFromCandidate,
        )
        return session.copy(segments = updatedSegments)
    }

    private suspend fun prepareBunsetsuSegments(
        session: BunsetsuConversionSession,
    ): BunsetsuConversionSession {
        var prepared = session
        for (index in session.segments.indices) {
            if (index == session.focusedIndex || !session.segments[index].hasConvertedDisplay) {
                prepared = loadCandidatesForBunsetsuSegment(prepared, index)
            }
        }
        return prepared
    }

    private fun launchBunsetsuOperation(
        operation: suspend (BunsetsuConversionSession) -> Unit,
    ) {
        val generation = bunsetsuConversionSession?.generation ?: return
        scope.launch {
            bunsetsuOperationMutex.withLock {
                val current = bunsetsuConversionSession ?: return@withLock
                if (current.generation != generation || !isBunsetsuCursorMoveSessionActive()) {
                    return@withLock
                }
                operation(current)
            }
        }
    }

    private suspend fun activateBunsetsuConversionSession(
        input: String,
        mainView: MainLayoutBinding,
    ): Boolean {
        val requestedGeneration = bunsetsuSessionGeneration
        return bunsetsuOperationMutex.withLock {
            if (requestedGeneration != bunsetsuSessionGeneration || inputString.value != input) {
                return@withLock true
            }
            if (isBunsetsuCursorMoveSessionActive()) return@withLock true

            val tailText = stringInTail.get()
            val snapshot = latestBunsetsuConversionSnapshot?.takeIf { it.input == input }
                ?: queryBunsetsuConversion(input).let { result ->
                    BunsetsuConversionSnapshot(
                        input = input,
                        candidates = result.candidates,
                        paths = result.candidateSegmentsByString,
                        splitPatterns = result.bunsetsuResult?.splitPatterns.orEmpty(),
                        initialSplitPositions = resolveInitialBunsetsuSplitPositions(
                            input, result.candidates, result.bunsetsuResult,
                        ),
                    )
                }
            if (requestedGeneration != bunsetsuSessionGeneration || inputString.value != input) {
                return@withLock true
            }
            val splitPatterns = normalizeBunsetsuSplitPatterns(
                input, snapshot.splitPatterns, snapshot.initialSplitPositions,
            )
            val initialSplitPositions = splitPatterns.firstOrNull().orEmpty()
            val initialSegments = buildBunsetsuSegments(input, initialSplitPositions, snapshot)
            if (initialSegments.isEmpty()) {
                clearBunsetsuConversionSession()
                return@withLock false
            }

            val initialSession = BunsetsuConversionSession(
                rawInput = input + tailText,
                generation = ++bunsetsuSessionGeneration,
                conversionSnapshot = snapshot,
                conversionInput = input,
                segments = initialSegments,
                tailText = tailText,
                focusedIndex = 0,
                splitPatterns = splitPatterns,
                activeSplitPatternIndex = 0
            )

            isHenkan.set(true)
            henkanPressedWithBunsetsuDetect = true
            bunsetusMultipleDetect = true
            stringInTail.set("")
            suggestionClickNum = 0
            currentHighlightIndex = RecyclerView.NO_POSITION
            bunsetsuPositionList = initialSplitPositions
            bunsetsuSplitPatterns = splitPatterns
            bunsetsuConversionSession = initialSession
            val prepared = prepareBunsetsuSegments(initialSession)
            if (bunsetsuConversionSession !== initialSession || !isBunsetsuCursorMoveSessionActive()) {
                return@withLock true
            }
            bunsetsuConversionSession = prepared
            renderBunsetsuConversionSession(mainView)
            true
        }
    }

    private fun buildBunsetsuSegmentRanges(
        segments: List<BunsetsuSegmentState>
    ): List<IntRange> {
        var start = 0
        return segments.map { segment ->
            val endExclusive = start + segment.reading.length
            val range = start until endExclusive
            start = endExclusive
            range
        }
    }

    private fun overlapLength(first: IntRange, second: IntRange): Int {
        val start = maxOf(first.first, second.first)
        val endExclusive = minOf(first.last + 1, second.last + 1)
        return (endExclusive - start).coerceAtLeast(0)
    }

    private fun findFocusedSegmentIndexForSplitPattern(
        currentSegments: List<BunsetsuSegmentState>,
        currentFocusedIndex: Int,
        nextSegments: List<BunsetsuSegmentState>
    ): Int {
        if (currentSegments.size == 1 && nextSegments.size > 1) {
            return 0
        }

        val currentRanges = buildBunsetsuSegmentRanges(currentSegments)
        val currentRange = currentRanges.getOrNull(currentFocusedIndex) ?: return 0
        val nextRanges = buildBunsetsuSegmentRanges(nextSegments)

        return nextRanges.indices.maxWithOrNull(
            compareBy<Int> { index ->
                overlapLength(currentRange, nextRanges[index])
            }.thenByDescending { index ->
                -kotlin.math.abs(nextRanges[index].first - currentRange.first)
            }
        ) ?: 0
    }

    private fun switchBunsetsuSplitPattern(
        delta: Int = 1,
    ): Boolean {
        if (!isBunsetsuCursorMoveSessionActive()) return false
        val mainView = mainLayoutBinding ?: return false
        if ((bunsetsuConversionSession?.splitPatterns?.size ?: 0) <= 1) return false

        launchBunsetsuOperation { session ->
            val nextPatternIndex =
                ((session.activeSplitPatternIndex + delta) % session.splitPatterns.size + session.splitPatterns.size) % session.splitPatterns.size
            val nextSplitPositions = session.splitPatterns[nextPatternIndex]
            val rebuiltSegments = buildBunsetsuSegments(
                input = session.conversionInput,
                splitPositions = nextSplitPositions,
                snapshot = session.conversionSnapshot,
            )
            if (rebuiltSegments.isEmpty()) {
                return@launchBunsetsuOperation
            }

            val nextFocusedIndex = findFocusedSegmentIndexForSplitPattern(
                currentSegments = session.segments,
                currentFocusedIndex = session.focusedIndex,
                nextSegments = rebuiltSegments
            )

            val switchedSession = session.copy(
                segments = rebuiltSegments,
                focusedIndex = nextFocusedIndex,
                activeSplitPatternIndex = nextPatternIndex
            )
            val prepared = prepareBunsetsuSegments(switchedSession)
            if (bunsetsuConversionSession !== session || !isBunsetsuCursorMoveSessionActive()) {
                return@launchBunsetsuOperation
            }
            bunsetsuPositionList = nextSplitPositions
            bunsetsuSplitPatterns = session.splitPatterns
            bunsetsuConversionSession = prepared
            renderBunsetsuConversionSession(mainView)
        }
        return true
    }

    private fun updateSuggestionViewsForBunsetsuSegment(
        session: BunsetsuConversionSession,
        focusedIndex: Int,
        mainView: MainLayoutBinding,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                updateSuggestionViewsForBunsetsuSegment(
                    session = session,
                    focusedIndex = focusedIndex,
                    mainView = mainView
                )
            }
            return
        }
        if (session.segments.isEmpty()) return
        val safeFocusedIndex = focusedIndex.coerceIn(0, session.segments.lastIndex)
        val segment = session.segments[safeFocusedIndex]
        val displayedCandidates = segment.candidates
        setSuggestionAdaptersOnMain(
            candidates = displayedCandidates,
            fullCandidates = segment.candidates
        )

        val segmentHighlightIndex = if (segment.candidates.isEmpty()) {
            RecyclerView.NO_POSITION
        } else {
            segment.selectedIndex.coerceIn(0, segment.candidates.lastIndex)
        }
        val displayedHighlightIndex = when {
            segmentHighlightIndex == RecyclerView.NO_POSITION -> RecyclerView.NO_POSITION
            else -> bunsetsuCandidateIndexToDisplayedIndex(
                segmentIndex = segmentHighlightIndex,
                displayedCandidates = displayedCandidates
            )
        }
        suggestionAdapter?.updateHighlightPosition(displayedHighlightIndex)

        if (displayedHighlightIndex != RecyclerView.NO_POSITION) {
            mainView.suggestionRecyclerView.smoothScrollToPosition(displayedHighlightIndex)
        }

        if (false
        ) {
        }
    }

    private fun renderBunsetsuConversionSession(
        mainView: MainLayoutBinding,
    ) {
        val session = bunsetsuConversionSession ?: return
        val focusedIndex = session.focusedIndex.coerceIn(0, session.segments.lastIndex)
        val segments = session.segments
        val convertedText = segments.joinToString(separator = "") { it.displayText }
        val text = convertedText + session.tailText
        val highlightStart = segments
            .take(focusedIndex)
            .sumOf { it.displayText.length }
        val focusedSegment = segments[focusedIndex]
        val highlightEnd = highlightStart + focusedSegment.displayText.length

        updateSuggestionViewsForBunsetsuSegment(
            session = session,
            focusedIndex = focusedIndex,
            mainView = mainView
        )

        applyBunsetsuComposingText(
            text = text,
            segments = segments,
            tailText = session.tailText,
            highlightStart = highlightStart,
            highlightEnd = highlightEnd,
            backgroundColor = if (customComposingTextPreference == true) {
                inputConversionBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.orange)
            } else {
                getColor(com.kazumaproject.core.R.color.orange)
            },
            textColor = if (customComposingTextPreference == true) {
                inputConversionTextColor
            } else {
                null
            }
        )
        updateUIinHenkan(mainView, session.rawInput)
    }

    private fun applyBunsetsuComposingText(
        text: String,
        segments: List<BunsetsuSegmentState>,
        tailText: String,
        highlightStart: Int,
        highlightEnd: Int,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null
    ) {
        val spannableString = SpannableString(text)
        val safeStart = highlightStart.coerceIn(0, text.length)
        val safeEnd = highlightEnd.coerceIn(safeStart, text.length)
        val spanFlag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING

        spannableString.apply {
            setSpan(
                BackgroundColorSpan(backgroundColor),
                safeStart,
                safeEnd,
                spanFlag
            )

            textColor?.let { color ->
                setSpan(
                    ForegroundColorSpan(color),
                    safeStart,
                    safeEnd,
                    spanFlag
                )
            }

            var segmentStart = 0
            segments.forEach { segment ->
                val segmentEnd = (segmentStart + segment.displayText.length).coerceAtMost(length)
                if (segmentEnd > segmentStart) {
                    setSpan(
                        UnderlineSpan(),
                        segmentStart,
                        segmentEnd,
                        spanFlag
                    )
                }
                segmentStart = segmentEnd
            }

            if (tailText.isNotEmpty()) {
                val tailStart = (text.length - tailText.length).coerceAtLeast(0)
                if (tailStart < length) {
                    setSpan(
                        UnderlineSpan(),
                        tailStart,
                        length,
                        spanFlag
                    )
                }
            }
        }

        setComposingText(spannableString, 1)
    }

    private fun clearBunsetsuConversionSession() {
        bunsetsuSessionGeneration++
        bunsetsuConversionSession = null
        bunsetusMultipleDetect = false
    }

    private fun restoreRawInputFromBunsetsuSession() {
        val session = bunsetsuConversionSession ?: return
        clearZeroQueryAllState(refresh = false)
        val rawInput = session.rawInput
        val shouldForceRefresh = inputString.value == rawInput
        clearBunsetsuConversionSession()
        val spannableString = SpannableString(rawInput)
        setComposingTextAfterEdit(
            inputString = rawInput,
            spannableString = spannableString,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
        )
        setSuggestionAdaptersOnMain(emptyList())
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        if (false
        ) {
            currentHighlightIndex = RecyclerView.NO_POSITION
        }
        _inputString.update { rawInput }
        if (shouldForceRefresh) {
            mainLayoutBinding?.let { mainView ->
                scope.launch {
                    processInputString(rawInput, mainView)
                }
            }
        }
    }

    private fun exitBunsetsuCursorMoveSessionToRawInput(): String {
        val rawInput = bunsetsuConversionSession?.rawInput ?: inputString.value
        restoreRawInputFromBunsetsuSession()
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        if (false
        ) {
            currentHighlightIndex = RecyclerView.NO_POSITION
        }
        isFirstClickHasStringTail = false
        return rawInput
    }

    /**
     * necookey: Mozc/ATOK-style bunsetsu resizing. Moves the end of the focused bunsetsu by one
     * character; the reading after it is re-converted and re-split by Sumire. Bunsetsu before the
     * focused one keep their current choice. Returns false when no bunsetsu session is active.
     */
    private fun resizeFocusedBunsetsuSegment(
        delta: Int,
    ): Boolean {
        if (!isBunsetsuCursorMoveSessionActive()) return false
        val mainView = mainLayoutBinding ?: return false
        launchBunsetsuOperation { session ->
            val reading = session.conversionInput
            val readings = session.segments.map { it.reading }
            if (readings.joinToString(separator = "") != reading) return@launchBunsetsuOperation
            val focusedIndex = session.focusedIndex.coerceIn(0, session.segments.lastIndex)
            val resize = BunsetsuRangeEditor.resizeFocused(
                reading = reading,
                boundaries = BunsetsuRangeEditor.boundariesOf(readings),
                focusedIndex = focusedIndex,
                delta = delta,
            ) ?: return@launchBunsetsuOperation

            val focusedReading = reading.substring(resize.focusedStart, resize.focusedEnd)
            val remainder = reading.substring(resize.remainderStart)
            val remainderSegments: List<BunsetsuSegmentState>
            val remainderSplits: List<Int>
            if (remainder.isEmpty()) {
                remainderSegments = emptyList()
                remainderSplits = emptyList()
            } else {
                val result = queryBunsetsuConversion(remainder)
                if (bunsetsuConversionSession !== session) return@launchBunsetsuOperation
                remainderSplits = resolveInitialBunsetsuSplitPositions(
                    remainder, result.candidates, result.bunsetsuResult,
                )
                val remainderSnapshot = BunsetsuConversionSnapshot(
                    input = remainder,
                    candidates = result.candidates,
                    paths = result.candidateSegmentsByString,
                    splitPatterns = result.bunsetsuResult?.splitPatterns.orEmpty(),
                    initialSplitPositions = remainderSplits,
                )
                remainderSegments = buildBunsetsuSegments(remainder, remainderSplits, remainderSnapshot)
                    .ifEmpty { listOf(BunsetsuSegmentState(reading = remainder, displayText = remainder)) }
            }
            val segments = session.segments.take(focusedIndex) +
                BunsetsuSegmentState(reading = focusedReading, displayText = focusedReading) +
                remainderSegments
            if (segments.joinToString(separator = "") { it.reading } != reading) {
                Timber.w("resizeFocusedBunsetsuSegment: segment readings do not cover the input")
                return@launchBunsetsuOperation
            }
            val boundaries = BunsetsuRangeEditor.boundariesOf(segments.map { it.reading })
            val splits = BunsetsuRangeEditor.interiorSplits(boundaries)
            val resized = session.copy(
                segments = segments,
                focusedIndex = focusedIndex,
                splitPatterns = listOf(splits) + session.splitPatterns.filter { it != splits },
                activeSplitPatternIndex = 0,
            )
            val prepared = prepareBunsetsuSegments(resized)
            if (bunsetsuConversionSession !== session || !isBunsetsuCursorMoveSessionActive()) {
                return@launchBunsetsuOperation
            }
            bunsetsuPositionList = splits
            bunsetsuSplitPatterns = prepared.splitPatterns
            bunsetsuConversionSession = prepared
            renderBunsetsuConversionSession(mainView)
        }
        return true
    }

    /** Custom keyboard arrow tap during conversion always resizes the focused bunsetsu; long-press moves focus. */
    private fun handleBunsetsuArrowTap(delta: Int): Boolean = resizeFocusedBunsetsuSegment(delta)

    private fun moveFocusedBunsetsuSegment(
        delta: Int,
    ): Boolean {
        if (!isBunsetsuCursorMoveSessionActive()) return false
        val mainView = mainLayoutBinding ?: return false
        launchBunsetsuOperation { session ->
            val nextIndex = (session.focusedIndex + delta).coerceIn(0, session.segments.lastIndex)
            if (nextIndex == session.focusedIndex) return@launchBunsetsuOperation
            val movedSession = session.copy(focusedIndex = nextIndex)
            val loadedSession = loadCandidatesForBunsetsuSegment(
                movedSession,
                segmentIndex = nextIndex
            )
            if (bunsetsuConversionSession !== session || !isBunsetsuCursorMoveSessionActive()) {
                return@launchBunsetsuOperation
            }
            bunsetsuConversionSession = loadedSession
            renderBunsetsuConversionSession(mainView)
        }
        return true
    }

    private fun cycleFocusedBunsetsuCandidate(
        delta: Int,
    ): Boolean {
        if (!isBunsetsuCursorMoveSessionActive()) return false
        val mainView = mainLayoutBinding ?: return false
        launchBunsetsuOperation { session ->
            val loadedSession = loadCandidatesForBunsetsuSegment(
                session,
                segmentIndex = session.focusedIndex
            )
            if (bunsetsuConversionSession !== session || !isBunsetsuCursorMoveSessionActive()) {
                return@launchBunsetsuOperation
            }
            val segment = loadedSession.segments[loadedSession.focusedIndex]
            if (segment.candidates.isEmpty()) {
                bunsetsuConversionSession = loadedSession
                renderBunsetsuConversionSession(mainView)
                return@launchBunsetsuOperation
            }

            val candidateCount = segment.candidates.size
            val nextIndex =
                ((segment.selectedIndex + delta) % candidateCount + candidateCount) % candidateCount
            val updatedSegment = segment.copy(
                selectedIndex = nextIndex,
                displayText = displayTextFromCandidate(segment.candidates[nextIndex]),
                overrideDisplayCandidate = null,
                explicitlySelected = true,
            )
            val updatedSegments = loadedSession.segments.toMutableList()
            updatedSegments[loadedSession.focusedIndex] = updatedSegment
            bunsetsuConversionSession = loadedSession.copy(segments = updatedSegments)
            renderBunsetsuConversionSession(mainView)
        }
        return true
    }

    private fun commitBunsetsuConversionSession(explicitlySelected: Boolean): Boolean {
        val session = bunsetsuConversionSession ?: return false
        val commitString = session.segments.joinToString(separator = "") { it.displayText }
        val tailText = session.tailText
        recordBunsetsuLearning(
            originalReading = session.rawInput,
            segments = session.segments,
            complete = tailText.isEmpty(),
            explicitlySelected = explicitlySelected,
        )
        val shouldRememberZeroQuery = tailText.isEmpty() && commitString.isNotBlank()
        if (tailText.isEmpty()) {
            finalizeBunsetsuReconversion(
                originalReading = session.rawInput,
                committedText = commitString
            )
        }
        if (shouldRememberZeroQuery) {
            rememberZeroQueryKeyAfterCommit(commitString, session.rawInput)
        }
        beginBatchEdit()
        try {
            setComposingText("", 0)
            finishComposingText()
            commitText(commitString, 1)
            if (tailText.isNotEmpty()) {
                val spannableString = SpannableString(tailText)
                setComposingTextAfterEdit(
                    inputString = tailText,
                    spannableString = spannableString,
                    backgroundColor = if (customComposingTextPreference == true) {
                        inputCompositionAfterBackgroundColor
                            ?: getColor(com.kazumaproject.core.R.color.blue)
                    } else {
                        getColor(com.kazumaproject.core.R.color.blue)
                    },
                    textColor = if (customComposingTextPreference == true) {
                        inputCompositionTextColor
                    } else {
                        null
                    }
                )
            }
        } finally {
            endBatchEdit()
        }
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        setSuggestionAdaptersOnMain(emptyList())
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        if (false
        ) {
            currentHighlightIndex = RecyclerView.NO_POSITION
        }
        isFirstClickHasStringTail = false
        stringInTail.set("")
        _inputString.update { tailText }
        clearBunsetsuConversionSession()
        if (shouldRememberZeroQuery) {
            consumePendingZeroQueryAfterCommit()
        }
        return true
    }

    private fun resetInputString() {
        Timber.d("resetInputString detect: $bunsetusMultipleDetect [${stringInTail.get()}]")
        val henkanActive = isHenkan.get()
        val tailIsEmpty = stringInTail.get().isEmpty()
        val shouldCommitIdle = !bunsetusMultipleDetect || tailIsEmpty
        if (!henkanActive && shouldCommitIdle) {
            requestCandidateRefresh(CandidateShowFlag.Idle)
        }
    }

    private fun resolveBaselineInputBehavior(): ResolvedInputBehavior {
        return RuntimeInputBehaviorPolicy.resolveBaseline(
            qwertyMode = qwertyMode.value,
            isCustomLayoutDirectMode = isCustomLayoutDirectMode,
            resolvedInputBehavior = inputBehaviorResolver.resolve(currentInputType),
        )
    }

    private fun applyEffectiveInputBehavior(reason: String) {
        val previousInputBehavior = currentInputBehavior
        val forceQwertyEnglishDirectInput = false
        currentInputBehavior = RuntimeInputBehaviorPolicy.effective(
            baseline = baselineInputBehavior,
            shortcutOverride = shortcutInputBehaviorOverride,
            forceDirectCommit = forceQwertyEnglishDirectInput,
        )
        Timber.d(
            "applyEffectiveInputBehavior: reason=$reason baseline=$baselineInputBehavior " +
                    "override=$shortcutInputBehaviorOverride " +
                    "forceQwertyEnglishDirectInput=$forceQwertyEnglishDirectInput " +
                    "current=$currentInputBehavior"
        )

        when (
            RuntimeInputBehaviorPolicy.directCommitTransition(
                previous = previousInputBehavior,
                current = currentInputBehavior,
                startingNewInput = reason == "start input",
                canToggleSafely = canToggleRuntimeInputBehaviorSafely(),
                replaceComposingOnNextInput = customDirectInputReplaceComposingPreference &&
                        _tenKeyQWERTYMode.value == TenKeyQWERTYMode.Custom &&
                        isCustomLayoutDirectMode,
            )
        ) {
            DirectCommitTransition.NONE -> Unit
            DirectCommitTransition.CLEAR -> clearDirectCommitCompositionState("direct commit $reason")
            DirectCommitTransition.FINISH_AND_CLEAR -> {
                // commitText would replace the editor's active composing span.
                finishComposingText()
                clearDirectCommitCompositionState("direct commit $reason")
            }
        }
        updateShortcutActiveStates()
    }

    private fun resetRuntimeInputBehaviorForCurrentInput() {
        shortcutInputBehaviorOverride = null
        baselineInputBehavior = resolveBaselineInputBehavior()
        applyEffectiveInputBehavior("start input")
    }

    private fun refreshBaselineInputBehaviorForCurrentKeyboard(reason: String) {
        baselineInputBehavior = resolveBaselineInputBehavior()
        applyEffectiveInputBehavior(reason)
    }

    private fun toggleRuntimeInputBehaviorFromShortcut() {
        if (!canToggleRuntimeInputBehaviorSafely()) {
            return
        }

        shortcutInputBehaviorOverride =
            RuntimeInputBehaviorPolicy.toggledOverride(currentInputBehavior)
        applyEffectiveInputBehavior("shortcut input behavior toggle")
    }

    private fun canToggleRuntimeInputBehaviorSafely(): Boolean {
        return RuntimeInputBehaviorPolicy.canToggle(
            RuntimeInputBehaviorSafetyState(
                inputStringEmpty = inputString.value.isEmpty(),
                tailEmpty = stringInTail.get().isEmpty(),
                henkanActive = isHenkan.get(),
                bunsetsuMultipleDetect = bunsetusMultipleDetect,
                henkanPressedWithBunsetsuDetect = henkanPressedWithBunsetsuDetect,
                bunsetsuConversionSessionActive = bunsetsuConversionSession != null,
                bunsetsuCursorMoveSessionActive = isBunsetsuCursorMoveSessionActive(),
                candidateHighlightActive = currentHighlightIndex != RecyclerView.NO_POSITION,
            )
        )
    }

    private fun setCurrentInputType(attribute: EditorInfo?) {
        attribute?.apply {
            currentInputType = getCurrentInputTypeForIME2(this)
            currentInputModeForSession = defaultInputModeFor(currentInputType)
            Timber.d("setCurrentInputType: $currentInputType $inputType ${attribute.hintText} ${attribute.actionId} ${attribute.fieldName} ${attribute.inputType} ")
            when (currentInputType) {
                InputTypeForIME.Number,
                InputTypeForIME.NumberDecimal,
                InputTypeForIME.NumberPassword,
                InputTypeForIME.NumberSigned,
                InputTypeForIME.Phone,
                InputTypeForIME.Date,
                InputTypeForIME.Datetime,
                InputTypeForIME.Time,
                    -> _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Number }

                else -> setFirstKeyboardType()
            }

            resetRuntimeInputBehaviorForCurrentInput()
            if (inputString.value.isEmpty() && stringInTail.get().isEmpty() && !isHenkan.get()) {
                mainLayoutBinding?.let { setDrawableToEnterKeyCorrespondingToImeOptions(it) }
            }
        }
    }

    private fun setFirstKeyboardType() {
        val firstItem = resolveKeyboardForDisplay(
            requestedType = null,
            savedPosition = null,
            source = "setFirstKeyboardType",
            applyOrientation = false
        ).resolvedKeyboard
        when (firstItem) {
            KeyboardType.CUSTOM -> _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Custom }
        }
    }

    private fun setTabsToTabLayout(
        mainView: MainLayoutBinding
    ) {
        mainView.candidateTabLayout.removeAllTabs()

        candidateTabOrder.forEach { tabType ->
            val tab = mainView.candidateTabLayout.newTab()
            tab.text = getCandidateTabDisplayName(tabType)
            mainView.candidateTabLayout.addTab(tab)
        }
        // TabLayout recreates each TabView background; style the new views, not the old ones.
        applyCandidateTabAppearance(mainView.candidateTabLayout)
    }

    private fun getCandidateTabDisplayName(candidateTab: CandidateTab): String {
        return when (candidateTab) {
            CandidateTab.PREDICTION -> "予測"
            CandidateTab.CONVERSION -> "変換"
            CandidateTab.EISUKANA -> "英数カナ"
        }
    }

    private fun setCandidateTabLayout(
        mainView: MainLayoutBinding
    ) {
        mainView.candidateTabLayout.apply {
            applyCandidateTabAppearance(this)
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    tab?.let { t ->
                        val input = inputString.value
                        if (input.isNotEmpty()) {
                            val mode = CandidateQueryModeResolver.resolve(
                                tabVisible = true,
                                tabOrder = candidateTabOrder,
                                selectedPosition = t.position,
                            )
                            val token = candidateRequestTracker.begin(
                                input = input,
                                mode = mode,
                                backend = conversionBackend,
                                editorMutationRevision = editorMutationRevision.current(),
                            )
                            ioScope.launch {
                                setCandidatesForMode(input, mainView, mode, token)
                                withContext(Dispatchers.Main) {
                                    hideFirstRowCandidatesInFullScreen(mainView)
                                }
                            }
                        }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {

                }

                override fun onTabReselected(tab: TabLayout.Tab?) {

                }

            })
        }
    }

    private fun setSuggestionRecyclerView(
        mainView: MainLayoutBinding, flexboxLayoutManagerRow: FlexboxLayoutManager
    ) {
        suggestionAdapter?.let { adapter ->
            adapter.setOnItemClickListener { candidate, position ->
                val insertString = inputString.value
                val currentInputMode: InputMode = currentInputModeForSession
                setCandidateClick(
                    candidate = candidate,
                    insertString = insertString,
                    currentInputMode = currentInputMode,
                    position = position,
                    displayedCandidates = adapter.suggestions
                )
            }
            adapter.setOnItemLongClickListener { candidate, i ->
                Timber.d("Candidate long tap: $candidate $i")
                if (isSelectionActionCandidate(candidate)) return@setOnItemLongClickListener
                val insertString = inputString.value
                if (shouldShowCandidateLongPressActions(candidate)) {
                    val candidatePosition = resolveCandidateLongPressPosition(
                        candidate = candidate,
                        position = i,
                        displayedCandidates = adapter.suggestions
                    ) ?: return@setOnItemLongClickListener
                    showCandidateLongPressActions(
                        insertString = insertString,
                        candidate = candidate,
                        candidatePosition = candidatePosition
                    )
                }
            }

            adapter.setOnPhysicalKeyboardListener {
                mainView.apply {
                    if (customLayoutDefault.isVisible) {
                        disableKeyboardLayoutEditMode()
                        hideAllKeyboards()
                        val heightPx = dpToPx(40f)
                        val widthPx = ViewGroup.LayoutParams.MATCH_PARENT
                        (mainView.suggestionViewParent.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                            params.bottomMargin = heightPx
                            mainView.suggestionViewParent.layoutParams = params
                        }
                        (mainView.root.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                            params.width = widthPx
                            mainView.root.layoutParams = params
                        }
                    } else {
                        val resolution = resolveKeyboardForDisplay(
                            requestedType = null,
                            savedPosition = null,
                            source = "physicalKeyboardToggle",
                            applyOrientation = false
                        )
                        currentKeyboardOrder = resolution.resolvedIndex ?: 0
                        showKeyboard(resolution.resolvedKeyboard, source = "physicalKeyboardToggle.display")
                        setKeyboardSizeSwitchKeyboard(mainView)
                    }
                }
            }

            adapter.setOnItemHelperIconClickListener { helperIcon ->
                when (helperIcon) {
                    SuggestionAdapter.HelperIcon.UNDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconClickListener
                        undoLastHistoryEntry()
                    }

                    SuggestionAdapter.HelperIcon.REDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconClickListener
                        redoLastHistoryEntry()
                    }

                    SuggestionAdapter.HelperIcon.RECONVERT -> {
                        performPendingReconversion()
                    }

                    SuggestionAdapter.HelperIcon.PASTE -> {
                        Timber.d("SuggestionAdapter.HelperIcon.PASTE: clicked")
                        pasteAction()
                        if (clipboardPreviewTapToDelete == true) {
                            refreshCandidateStripContent()
                        }
                    }
                }
            }
            adapter.setOnItemHelperIconLongClickListener { helperIcon ->
                when (helperIcon) {
                    SuggestionAdapter.HelperIcon.UNDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconLongClickListener
                        undoAllHistoryEntries()
                    }

                    SuggestionAdapter.HelperIcon.REDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconLongClickListener
                        redoAllHistoryEntries()
                    }

                    SuggestionAdapter.HelperIcon.RECONVERT -> Unit

                    SuggestionAdapter.HelperIcon.PASTE -> {
                        clipboardUtil.clearClipboard()
                        refreshCandidateStripContent()
                    }
                }
            }
            adapter.setOnCustomLayoutItemClickListener { position ->
                clearZeroQueryAllState(refresh = false)
                selectCustomKeyboardTab(
                    index = position,
                    reason = CustomKeyboardSelectionReason.UserTabClick
                )
            }
            adapter.setOnShortcutItemClickListener { type ->
                handleShortcutAction(type, mainView)
            }
            adapter.setOnShortcutEntryClickListener {
                clearZeroQueryAllState(refresh = false)
                integratedShortcutEntryExpanded = !integratedShortcutEntryExpanded
                refreshCandidateStripContent()
            }
            adapter.setOnInlineSuggestionToggleClickListener {
                toggleInlineSuggestionSurface()
            }
            adapter.setOnZeroQueryCandidateClickListener { candidate ->
                commitZeroQueryCandidate(candidate)
            }
            adapter.setOnZeroQueryCloseClickListener {
                toggleZeroQueryVisibility()
            }
        }
        necookeyPredictionAdapter?.let { adapter ->
            adapter.setOnItemClickListener { candidate, position ->
                val insertString = inputString.value
                if (insertString.isEmpty()) return@setOnItemClickListener
                setCandidateClick(
                    candidate = candidate,
                    insertString = insertString,
                    currentInputMode = currentInputModeForSession,
                    position = position,
                    displayedCandidates = adapter.suggestions
                )
            }
            adapter.setOnItemLongClickListener { candidate, position ->
                val insertString = inputString.value
                if (insertString.isEmpty() || isNecookeyActionCandidate(candidate)) {
                    return@setOnItemLongClickListener
                }
                if (shouldShowCandidateLongPressActions(candidate)) {
                    showCandidateLongPressActions(
                        insertString = insertString,
                        candidate = candidate,
                        candidatePosition = position,
                    )
                }
            }
        }
        suggestionAdapterFull?.let { adapter ->
            adapter.setOnItemClickListener { candidate, position ->
                val insertString = inputString.value
                val currentInputMode: InputMode = currentInputModeForSession
                setCandidateClick(
                    candidate = candidate,
                    insertString = insertString,
                    currentInputMode = currentInputMode,
                    position = position,
                    displayedCandidates = adapter.suggestions
                )
            }
            adapter.setOnItemLongClickListener { candidate, i ->
                Timber.d("Candidate long tap: $candidate $i")
                if (isSelectionActionCandidate(candidate)) return@setOnItemLongClickListener
                val insertString = inputString.value
                if (shouldShowCandidateLongPressActions(candidate)) {
                    val candidatePosition = resolveCandidateLongPressPosition(
                        candidate = candidate,
                        position = i,
                        displayedCandidates = adapter.suggestions
                    ) ?: return@setOnItemLongClickListener
                    showCandidateLongPressActions(
                        insertString = insertString,
                        candidate = candidate,
                        candidatePosition = candidatePosition
                    )
                }
            }
            adapter.setOnItemHelperIconClickListener { helperIcon ->
                when (helperIcon) {
                    SuggestionAdapter.HelperIcon.UNDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconClickListener
                        undoLastHistoryEntry()
                    }

                    SuggestionAdapter.HelperIcon.REDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconClickListener
                        redoLastHistoryEntry()
                    }

                    SuggestionAdapter.HelperIcon.RECONVERT -> {
                        performPendingReconversion()
                    }

                    SuggestionAdapter.HelperIcon.PASTE -> {
                        pasteAction()
                    }
                }
            }
            adapter.setOnItemHelperIconLongClickListener { helperIcon ->
                when (helperIcon) {
                    SuggestionAdapter.HelperIcon.UNDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconLongClickListener
                        undoAllHistoryEntries()
                    }

                    SuggestionAdapter.HelperIcon.REDO -> {
                        if (!isEditHistoryEnabled()) return@setOnItemHelperIconLongClickListener
                        redoAllHistoryEntries()
                    }

                    SuggestionAdapter.HelperIcon.RECONVERT -> Unit

                    SuggestionAdapter.HelperIcon.PASTE -> {
                        clipboardUtil.clearClipboard()
                        refreshCandidateStripContent()
                    }
                }
            }
        }
        mainView.suggestionRecyclerView.apply {
            itemAnimator = null
            isFocusable = false
        }

        mainView.candidatesRowView.apply {
            itemAnimator = null
            isFocusable = false
        }
        suggestionAdapter.apply {
            mainView.candidatesRowView.layoutManager = flexboxLayoutManagerRow
        }
        mainView.suggestionVisibility.setOnClickListener {
            _suggestionViewStatus.update { !it }
        }
    }

    private fun updateMainCandidateStripAfterListUpdated() {
        assertMainThread("updateMainCandidateStripAfterListUpdated")
        val binding = mainLayoutBinding ?: return
        measureDebugSection("IMEService.updateMainCandidateStripAfterListUpdated") {
            measureDebugSection("IMEService.scrollToPosition0") {
                binding.suggestionRecyclerView.scrollToPosition(0)
            }
        }
    }

    private fun anchorActiveSuggestionStripStartForLeadingContent() {
        assertMainThread("anchorActiveSuggestionStripStartForLeadingContent")
        measureDebugSection("IMEService.anchorActiveSuggestionStripStartForLeadingContent") {
            val binding = mainLayoutBinding ?: return@measureDebugSection
            binding.suggestionRecyclerView.scrollToPosition(0)
        }
    }

    private fun anchorActiveSuggestionStripStartIfLeadingContentExpected() {
        assertMainThread("anchorActiveSuggestionStripStartIfLeadingContentExpected")
        if (suggestionAdapter?.isStartAnchoredContentExpected() != true) return
        anchorActiveSuggestionStripStartForLeadingContent()
    }

    private fun setMainSuggestionColumn(
        mainView: MainLayoutBinding
    ) {
        assertMainThread("setMainSuggestionColumn")
        measureDebugSection("IMEService.setMainSuggestionColumn") {
            val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

            val columnNum = if (isPortrait) {
                candidateColumns ?: "1"
            } else {
                candidateColumnsLandscape ?: "1"
            }

            val recyclerView = mainView.suggestionRecyclerView
            val adapter = recyclerView.adapter ?: run {
                // Do not cache a layout created before the active adapter is attached.
                // Grid span lookup needs the adapter to keep shortcut items on one row.
                lastSuggestionLayoutKey = null
                return@measureDebugSection
            }
            val inlineSuggestionStripShown =
                (adapter as? SuggestionAdapter)?.isInlineSuggestionStripShown() == true

            val key = SuggestionLayoutKey(
                isPortrait = isPortrait,
                columnNum = columnNum,
                layoutKind = if (
                    inlineSuggestionStripShown ||
                    columnNum == "1" ||
                    CandidateStripLayoutPolicy.shouldUseLinearHorizontalLayout(
                        currentCandidateStripContent
                    )
                ) {
                    SuggestionLayoutKind.LinearHorizontal
                } else {
                    SuggestionLayoutKind.GridHorizontal
                },
            )
            if (lastSuggestionLayoutKey == key && recyclerView.layoutManager != null) {
                return@measureDebugSection
            }

            lastSuggestionLayoutKey = key

            mainSuggestionGridSpacingDecoration?.let { decoration ->
                recyclerView.removeItemDecoration(decoration)
                mainSuggestionGridSpacingDecoration = null
            }

            if (key.layoutKind == SuggestionLayoutKind.LinearHorizontal) {
                recyclerView.layoutManager =
                    LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                return@measureDebugSection
            }

            when (columnNum) {
                "1" -> {
                    recyclerView.layoutManager =
                        LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                }

                "2", "3" -> {
                    val spanCount = columnNum.toInt()
                    val gridLayoutManager = GridLayoutManager(
                        this@IMEService, spanCount, GridLayoutManager.HORIZONTAL, false
                    )

                    gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            return when (adapter.getItemViewType(position)) {
                                SuggestionAdapter.VIEW_TYPE_EMPTY,
                                SuggestionAdapter.VIEW_TYPE_CLIPBOARD_PREVIEW,
                                SuggestionAdapter.VIEW_TYPE_SHORTCUT_ENTRY,
                                SuggestionAdapter.VIEW_TYPE_CUSTOM_LAYOUT_PICKER,
                                SuggestionAdapter.VIEW_TYPE_SHORTCUT,
                                SuggestionAdapter.VIEW_TYPE_INLINE_TOGGLE,
                                SuggestionAdapter.VIEW_TYPE_INLINE_SUGGESTION -> spanCount
                                else -> 1
                            }
                        }
                    }

                    val spacingInPixels =
                        resources.getDimensionPixelSize(com.kazumaproject.core.R.dimen.grid_spacing)
                    val decoration = GridSpacingItemDecoration(
                        spanCount, spacingInPixels, true
                    )

                    recyclerView.layoutManager = gridLayoutManager
                    recyclerView.addItemDecoration(decoration)
                    mainSuggestionGridSpacingDecoration = decoration
                }
            }
        }
    }

    private fun toggleKeyboardLayoutEditMode(mainView: MainLayoutBinding) {
        if (keyboardLayoutEditState.value is KeyboardLayoutEditState.Enabled) {
            disableKeyboardLayoutEditMode()
            return
        }

        val surface = KeyboardLayoutEditSurface.Normal

        if (surface == KeyboardLayoutEditSurface.Normal && mainView.root.alpha == 0f) {
            disableKeyboardLayoutEditMode()
            return
        }

        closeViewsThatConflictWithKeyboardLayoutEdit()

        val target = KeyboardLayoutEditTarget.from(qwertyMode.value)
        val orientation = currentKeyboardLayoutEditOrientation()
        val state = KeyboardLayoutEditState.Enabled(
            surface = surface,
            target = target,
            orientation = orientation,
            values = when (surface) {
                KeyboardLayoutEditSurface.Normal -> readNormalKeyboardLayoutEditValues(
                    target = target,
                    orientation = orientation,
                )

                KeyboardLayoutEditSurface.Floating -> return
            },
        )

        val surfaceAdapter = when (surface) {
            KeyboardLayoutEditSurface.Normal -> {
                val targetView = resolveNormalKeyboardLayoutEditTargetView(mainView) ?: return
                val overlayParent = keyboardContainer
                    ?: (mainView.root.parent as? FrameLayout)
                    ?: mainView.root
                NormalKeyboardLayoutEditSurface(
                    parent = overlayParent,
                    targetView = targetView,
                    availableWidthProvider = {
                        overlayParent.width.takeIf { it > 0 }
                            ?: resources.displayMetrics.widthPixels
                    },
                )
            }

            KeyboardLayoutEditSurface.Floating -> return
        }

        _keyboardLayoutEditState.value = state
        keyboardLayoutEditController?.start(
            state = state,
            surfaceAdapter = surfaceAdapter,
            callbacks = KeyboardLayoutEditOverlayView.Callbacks(
                onNormalDraftChanged = { values ->
                    applyNormalKeyboardLayoutEditValues(values, persist = false)
                },
                onNormalEditCommitted = { values ->
                    applyNormalKeyboardLayoutEditValues(values, persist = true)
                },
                onFloatingDraftChanged = { values ->
                    Unit
                },
                onFloatingEditCommitted = { values ->
                    Unit
                },
                onReset = {
                    resetKeyboardLayoutEditValues()
                },
                onDone = {
                    disableKeyboardLayoutEditMode()
                },
            ),
        )
        updateShortcutActiveStates()
    }

    private fun isKeyboardLayoutEditModeActive(): Boolean {
        return keyboardLayoutEditState.value is KeyboardLayoutEditState.Enabled
    }

    private fun disableKeyboardLayoutEditMode(updateSurface: Boolean = true) {
        val activeState = keyboardLayoutEditState.value as? KeyboardLayoutEditState.Enabled

        if (
            keyboardLayoutEditState.value == KeyboardLayoutEditState.Disabled &&
            keyboardLayoutEditController?.isActive != true
        ) {
            return
        }
        keyboardLayoutEditController?.stop()
        _keyboardLayoutEditState.value = KeyboardLayoutEditState.Disabled
        updateShortcutActiveStates()
        if (updateSurface) {
            reloadKeyboardLayoutEditCachesFromPreferences()
            mainLayoutBinding?.let { updateKeyboardLayout(it) }
        }
    }

    private fun resetKeyboardLayoutEditValues() {
        val enabled = keyboardLayoutEditState.value as? KeyboardLayoutEditState.Enabled ?: return
        when (enabled.surface) {
            KeyboardLayoutEditSurface.Normal -> {
                val values = KeyboardLayoutEditValues.Normal(
                    heightDp = KeyboardLayoutEditConstraints.DefaultHeightDp,
                    widthPercent = KeyboardLayoutEditConstraints.DefaultWidthPercent,
                    bottomMarginDp = KeyboardLayoutEditConstraints.DefaultMarginDp,
                    marginStartDp = KeyboardLayoutEditConstraints.DefaultMarginDp,
                    marginEndDp = KeyboardLayoutEditConstraints.DefaultMarginDp,
                    positionIsEnd = true,
                )
                applyNormalKeyboardLayoutEditValues(values, persist = true)
                keyboardLayoutEditController?.updateValues(values)
            }

            KeyboardLayoutEditSurface.Floating -> {
                val values = KeyboardLayoutEditValues.Floating(
                    heightDp = KeyboardLayoutEditConstraints.DefaultHeightDp,
                    widthPercent = KeyboardLayoutEditConstraints.DefaultWidthPercent,
                )
                keyboardLayoutEditController?.updateValues(values)
            }
        }
    }

    private fun updateShortcutActiveStates() {
        val activeTypes = resolveShortcutActiveTypes(
            keyboardLayoutEditActive = keyboardLayoutEditState.value is KeyboardLayoutEditState.Enabled,
            inputBehavior = currentInputBehavior,
            liveConversionEnabled = isLiveConversionEnable == true,
            learningPaused = learningPausedForSession,
        )

        shortcutAdapter?.setActiveShortcutTypes(activeTypes)
        suggestionAdapter?.setActiveShortcutTypes(activeTypes)
    }

    private fun refreshShortcutAvailability() {
        val visibleItems = configuredShortcutItems
        currentShortcutItems = visibleItems
        shortcutAdapter?.submitList(visibleItems) {
            updateShortcutActiveStates()
        }
        updateShortcutActiveStates()
        refreshCandidateStripContent()
    }

    private fun toggleLiveConversionFromShortcut() {
        val next = isLiveConversionEnable != true

        isLiveConversionEnable = next
        val shouldShowLiveConversionCandidateYomi =
            next && showLiveConversionCandidateYomi
        listOfNotNull(suggestionAdapter, suggestionAdapterFull).forEach { adapter ->
            adapter.setShowCandidateYomiForLiveConversion(shouldShowLiveConversionCandidateYomi)
        }
        updateShortcutActiveStates()
        refreshCandidateStripContent()
    }

    private fun toggleLearningPauseFromShortcut() {
        learningPausedForSession = !learningPausedForSession
        if (learningPausedForSession) {
            conversionLearningSession.cancel()
        }
        updateShortcutActiveStates()
        Toast.makeText(
            this,
            if (learningPausedForSession) {
                R.string.learning_paused_message
            } else {
                R.string.learning_resumed_message
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun closeViewsThatConflictWithKeyboardLayoutEdit() {
        if (keyboardSymbolViewState.value.isShown) {
            _keyboardSymbolViewState.value = SymbolKeyboardState()
        }
    }

    private fun currentKeyboardLayoutEditOrientation(): KeyboardLayoutEditOrientation {
        return KeyboardLayoutEditOrientation.from(
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        )
    }

    private fun resolveNormalKeyboardLayoutEditTargetView(mainView: MainLayoutBinding): View? {
        return listOf(
            mainView.customLayoutDefault,
            mainView.candidatesRowView,
        ).firstOrNull { it.isVisible } ?: mainView.customLayoutDefault
    }

    private fun readNormalKeyboardLayoutEditValues(
        target: KeyboardLayoutEditTarget,
        orientation: KeyboardLayoutEditOrientation,
    ): KeyboardLayoutEditValues.Normal {
        return when (orientation to target) {
            KeyboardLayoutEditOrientation.Portrait to KeyboardLayoutEditTarget.TenKeyFamily ->
                KeyboardLayoutEditValues.Normal(
                    heightDp = tenkeyHeightPreferenceValue ?: KeyboardLayoutEditConstraints.DefaultHeightDp,
                    widthPercent = tenkeyWidthPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultWidthPercent,
                    bottomMarginDp = tenkeyBottomMarginPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultMarginDp,
                    marginStartDp = tenkeyStartMarginPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultMarginDp,
                    marginEndDp = tenkeyEndMarginPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultMarginDp,
                    positionIsEnd = tenkeyPositionPreferenceValue ?: true,
                )

            KeyboardLayoutEditOrientation.Portrait to KeyboardLayoutEditTarget.QwertyFamily ->
                KeyboardLayoutEditValues.Normal(
                    heightDp = qwertyHeightPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultHeightDp,
                    widthPercent = qwertyWidthPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultWidthPercent,
                    bottomMarginDp = qwertyBottomMarginPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultMarginDp,
                    marginStartDp = qwertyStartMarginPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultMarginDp,
                    marginEndDp = qwertyEndMarginPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultMarginDp,
                    positionIsEnd = qwertyPositionPreferenceValue ?: true,
                )

            KeyboardLayoutEditOrientation.Landscape to KeyboardLayoutEditTarget.TenKeyFamily ->
                KeyboardLayoutEditValues.Normal(
                    heightDp = tenkeyHeightLandScapePreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultHeightDp,
                    widthPercent = tenkeyWidthLandScapePreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultWidthPercent,
                    bottomMarginDp = tenkeyLandScapeBottomMarginPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultMarginDp,
                    marginStartDp = tenkeyLandScapeStartMarginPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultMarginDp,
                    marginEndDp = tenkeyLandScapeEndMarginPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultMarginDp,
                    positionIsEnd = tenkeyLandScapePositionPreferenceValue ?: true,
                )

            else ->
                KeyboardLayoutEditValues.Normal(
                    heightDp = qwertyHeightLandScapePreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultHeightDp,
                    widthPercent = qwertyWidthLandScapePreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultWidthPercent,
                    bottomMarginDp = qwertyLandScapeBottomMarginPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultMarginDp,
                    marginStartDp = qwertyLandScapeStartMarginPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultMarginDp,
                    marginEndDp = qwertyLandScapeEndMarginPreferenceValue
                        ?: KeyboardLayoutEditConstraints.DefaultMarginDp,
                    positionIsEnd = qwertyLandScapePositionPreferenceValue ?: true,
                )
        }
    }

    private fun applyNormalKeyboardLayoutEditValues(
        values: KeyboardLayoutEditValues.Normal,
        persist: Boolean,
    ) {
        val enabled = keyboardLayoutEditState.value as? KeyboardLayoutEditState.Enabled ?: return
        val normalized = if (persist) {
            KeyboardLayoutEditConstraints.normalizeNormalForCommit(values)
        } else {
            KeyboardLayoutEditConstraints.normalizeNormalForDraft(values)
        }
        updateNormalKeyboardLayoutEditCaches(
            target = enabled.target,
            orientation = enabled.orientation,
            values = normalized,
        )
        if (persist) {
            saveNormalKeyboardLayoutEditValues(
                target = enabled.target,
                orientation = enabled.orientation,
                values = normalized,
            )
        }
        _keyboardLayoutEditState.value = enabled.copy(values = normalized)
        mainLayoutBinding?.let { updateKeyboardLayout(it) }
        keyboardLayoutEditController?.requestOverlayLayout()
    }

    private fun updateNormalKeyboardLayoutEditCaches(
        target: KeyboardLayoutEditTarget,
        orientation: KeyboardLayoutEditOrientation,
        values: KeyboardLayoutEditValues.Normal,
    ) {
        when (orientation to target) {
            KeyboardLayoutEditOrientation.Portrait to KeyboardLayoutEditTarget.TenKeyFamily -> {
                tenkeyHeightPreferenceValue = values.heightDp
                tenkeyWidthPreferenceValue = values.widthPercent
                tenkeyBottomMarginPreferenceValue = values.bottomMarginDp
                tenkeyStartMarginPreferenceValue = values.marginStartDp
                tenkeyEndMarginPreferenceValue = values.marginEndDp
                tenkeyPositionPreferenceValue = values.positionIsEnd
            }

            KeyboardLayoutEditOrientation.Portrait to KeyboardLayoutEditTarget.QwertyFamily -> {
                qwertyHeightPreferenceValue = values.heightDp
                qwertyWidthPreferenceValue = values.widthPercent
                qwertyBottomMarginPreferenceValue = values.bottomMarginDp
                qwertyStartMarginPreferenceValue = values.marginStartDp
                qwertyEndMarginPreferenceValue = values.marginEndDp
                qwertyPositionPreferenceValue = values.positionIsEnd
            }

            KeyboardLayoutEditOrientation.Landscape to KeyboardLayoutEditTarget.TenKeyFamily -> {
                tenkeyHeightLandScapePreferenceValue = values.heightDp
                tenkeyWidthLandScapePreferenceValue = values.widthPercent
                tenkeyLandScapeBottomMarginPreferenceValue = values.bottomMarginDp
                tenkeyLandScapeStartMarginPreferenceValue = values.marginStartDp
                tenkeyLandScapeEndMarginPreferenceValue = values.marginEndDp
                tenkeyLandScapePositionPreferenceValue = values.positionIsEnd
            }

            else -> {
                qwertyHeightLandScapePreferenceValue = values.heightDp
                qwertyWidthLandScapePreferenceValue = values.widthPercent
                qwertyLandScapeBottomMarginPreferenceValue = values.bottomMarginDp
                qwertyLandScapeStartMarginPreferenceValue = values.marginStartDp
                qwertyLandScapeEndMarginPreferenceValue = values.marginEndDp
                qwertyLandScapePositionPreferenceValue = values.positionIsEnd
            }
        }
    }

    private fun saveNormalKeyboardLayoutEditValues(
        target: KeyboardLayoutEditTarget,
        orientation: KeyboardLayoutEditOrientation,
        values: KeyboardLayoutEditValues.Normal,
    ) {
        when (orientation to target) {
            KeyboardLayoutEditOrientation.Portrait to KeyboardLayoutEditTarget.TenKeyFamily -> {
                appPreference.keyboard_height = values.heightDp
                appPreference.keyboard_width = values.widthPercent
                appPreference.keyboard_vertical_margin_bottom = values.bottomMarginDp
                appPreference.keyboard_margin_start_dp = values.marginStartDp
                appPreference.keyboard_margin_end_dp = values.marginEndDp
                appPreference.keyboard_position = values.positionIsEnd
            }

            KeyboardLayoutEditOrientation.Portrait to KeyboardLayoutEditTarget.QwertyFamily -> {
                appPreference.qwerty_keyboard_height = values.heightDp
                appPreference.qwerty_keyboard_width = values.widthPercent
                appPreference.qwerty_keyboard_vertical_margin_bottom = values.bottomMarginDp
                appPreference.qwerty_keyboard_margin_start_dp = values.marginStartDp
                appPreference.qwerty_keyboard_margin_end_dp = values.marginEndDp
                appPreference.qwerty_keyboard_position = values.positionIsEnd
            }

            KeyboardLayoutEditOrientation.Landscape to KeyboardLayoutEditTarget.TenKeyFamily -> {
                appPreference.keyboard_height_landscape = values.heightDp
                appPreference.keyboard_width_landscape = values.widthPercent
                appPreference.keyboard_vertical_margin_bottom_landscape = values.bottomMarginDp
                appPreference.keyboard_margin_start_dp_landscape = values.marginStartDp
                appPreference.keyboard_margin_end_dp_landscape = values.marginEndDp
                appPreference.keyboard_position_landscape = values.positionIsEnd
            }

            else -> {
                appPreference.qwerty_keyboard_height_landscape = values.heightDp
                appPreference.qwerty_keyboard_width_landscape = values.widthPercent
                appPreference.qwerty_keyboard_vertical_margin_bottom_landscape =
                    values.bottomMarginDp
                appPreference.qwerty_keyboard_margin_start_dp_landscape = values.marginStartDp
                appPreference.qwerty_keyboard_margin_end_dp_landscape = values.marginEndDp
                appPreference.qwerty_keyboard_position_landscape = values.positionIsEnd
            }
        }
    }

    private fun reloadKeyboardLayoutEditCachesFromPreferences() {
        tenkeyHeightPreferenceValue = appPreference.keyboard_height
        tenkeyWidthPreferenceValue = appPreference.keyboard_width
        qwertyHeightPreferenceValue = appPreference.qwerty_keyboard_height
        qwertyWidthPreferenceValue = appPreference.qwerty_keyboard_width
        tenkeyPositionPreferenceValue = appPreference.keyboard_position
        tenkeyBottomMarginPreferenceValue = appPreference.keyboard_vertical_margin_bottom
        qwertyPositionPreferenceValue = appPreference.qwerty_keyboard_position
        qwertyBottomMarginPreferenceValue = appPreference.qwerty_keyboard_vertical_margin_bottom
        tenkeyStartMarginPreferenceValue = appPreference.keyboard_margin_start_dp
        tenkeyEndMarginPreferenceValue = appPreference.keyboard_margin_end_dp
        qwertyStartMarginPreferenceValue = appPreference.qwerty_keyboard_margin_start_dp
        qwertyEndMarginPreferenceValue = appPreference.qwerty_keyboard_margin_end_dp
        tenkeyHeightLandScapePreferenceValue = appPreference.keyboard_height_landscape
        tenkeyWidthLandScapePreferenceValue = appPreference.keyboard_width_landscape
        qwertyHeightLandScapePreferenceValue = appPreference.qwerty_keyboard_height_landscape
        qwertyWidthLandScapePreferenceValue = appPreference.qwerty_keyboard_width_landscape
        tenkeyLandScapePositionPreferenceValue = appPreference.keyboard_position_landscape
        tenkeyLandScapeBottomMarginPreferenceValue =
            appPreference.keyboard_vertical_margin_bottom_landscape
        qwertyLandScapePositionPreferenceValue = appPreference.qwerty_keyboard_position_landscape
        qwertyLandScapeBottomMarginPreferenceValue =
            appPreference.qwerty_keyboard_vertical_margin_bottom_landscape
        tenkeyLandScapeStartMarginPreferenceValue =
            appPreference.keyboard_margin_start_dp_landscape
        tenkeyLandScapeEndMarginPreferenceValue =
            appPreference.keyboard_margin_end_dp_landscape
        qwertyLandScapeStartMarginPreferenceValue =
            appPreference.qwerty_keyboard_margin_start_dp_landscape
        qwertyLandScapeEndMarginPreferenceValue =
            appPreference.qwerty_keyboard_margin_end_dp_landscape
    }

    private fun resolvedFixedHeightPx(view: View, fallbackDp: Float): Int {
        val layoutHeight = view.layoutParams?.height ?: 0
        return when {
            view.height > 0 -> view.height
            view.measuredHeight > 0 -> view.measuredHeight
            layoutHeight > 0 -> layoutHeight
            else -> dpToPx(fallbackDp)
        }
    }

    private fun candidateTabHeightPx(mainView: MainLayoutBinding): Int {
        return resolvedFixedHeightPx(mainView.candidateTabLayout, fallbackDp = 36f)
    }

    private fun shortcutToolbarHeightPx(): Int {
        val toolbarHeightDp = shortcutToolbarHeightDp.coerceIn(
            AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MIN_DP,
            AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MAX_DP
        )
        return applicationContext.dpToPx(toolbarHeightDp)
    }

    private fun shortcutToolbarIconSizePx(toolbarHeightDp: Int = shortcutToolbarHeightDp): Int {
        val normalizedToolbarHeightDp = toolbarHeightDp.coerceIn(
            AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MIN_DP,
            AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MAX_DP
        )
        val iconSizeDp = appPreference.resolveShortcutToolbarIconSizeDp(
            toolbarHeightDp = normalizedToolbarHeightDp,
            iconSizeDp = shortcutToolbarIconSizeDp
        )
        return applicationContext.dpToPx(iconSizeDp)
    }

    private fun applyShortcutToolbarSize(
        mainView: MainLayoutBinding,
        forceLayout: Boolean
    ) {
        val toolbarHeightDp = shortcutToolbarHeightDp.coerceIn(
            AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MIN_DP,
            AppPreference.SHORTCUT_TOOLBAR_HEIGHT_MAX_DP
        )
        val toolbarHeightPx = applicationContext.dpToPx(toolbarHeightDp)
        val iconSizePx = shortcutToolbarIconSizePx(toolbarHeightDp)
        val toolbarLayoutParams = mainView.shortcutToolbarRecyclerview.layoutParams
        if (forceLayout || toolbarLayoutParams.height != toolbarHeightPx) {
            mainView.shortcutToolbarRecyclerview.layoutParams =
                toolbarLayoutParams.apply {
                    height = toolbarHeightPx
                }
        }
        shortcutAdapter?.setShortcutToolbarSize(
            toolbarHeightPx = toolbarHeightPx,
            iconSizePx = iconSizePx
        )
    }

    private fun applyCandidateTabSuggestionOffset(
        mainView: MainLayoutBinding,
        showCandidateTab: Boolean
    ) {
        val tabOffset = if (showCandidateTab) candidateTabHeightPx(mainView) else 0
        (mainView.suggestionViewParent.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.topMargin != tabOffset) {
                params.topMargin = tabOffset
                mainView.suggestionViewParent.layoutParams = params
            }
        }
    }

    private fun shouldUseIndependentShortcutToolbar(): Boolean {
        return resolveCandidateStripPresentation(
            content = currentCandidateStripContent
        ).showIndependentShortcutToolbar
    }

    private fun resolveCandidateStripPresentation(
        candidatesShown: Boolean = shortcutToolbarHiddenForCandidates,
        resetCandidateTabSelection: Boolean = false,
        content: CandidateStripContent = currentCandidateStripContent,
        symbolKeyboardShown: Boolean = keyboardSymbolViewState.value.isShown,
    ): CandidateStripPresentation {
        return CandidateStripPresentationPolicy.resolve(
            CandidateStripPresentationState(
                candidateTabVisible = candidateTabVisibility == true,
                candidatesShown = candidatesShown && !suppressSuggestions,
                resetCandidateTabSelection = resetCandidateTabSelection,
                shortcutToolbarVisible = shortcutTollbarVisibility == true,
                shortcutToolbarIntegratedInSuggestion = shortcutToolbarIntegratedInSuggestion == true,
                inputStringEmpty = inputString.value.isEmpty(),
                tailEmpty = stringInTail.get().isEmpty(),
                clipboardPreviewShown = content.hasClipboardPreview(),
                selectionActionsShown = content is CandidateStripContent.SelectionActions,
                suggestionsEmpty = content !is CandidateStripContent.Candidates &&
                    content !is CandidateStripContent.SelectionActions &&
                    content !is CandidateStripContent.ZeroQuerySuggestions,
                customLayoutPickerShown = content is CandidateStripContent.CustomLayoutPicker,
                symbolKeyboardShown = symbolKeyboardShown,
                shortcutToolbarHiddenForCandidates = shortcutToolbarHiddenForCandidates,
            )
        )
    }

    private fun updateCandidateStripPresentation(
        mainView: MainLayoutBinding,
        candidatesShown: Boolean = shortcutToolbarHiddenForCandidates,
        resetCandidateTabSelection: Boolean = false
    ) {
        assertMainThread("updateCandidateStripPresentation")
        val presentation = resolveCandidateStripPresentation(
            candidatesShown = candidatesShown,
            resetCandidateTabSelection = resetCandidateTabSelection,
            content = currentCandidateStripContent
        )
        applyCandidateStripPresentation(presentation)
    }

    private fun CandidateStripContent.hasClipboardPreview(): Boolean {
        return this is CandidateStripContent.EmptyState && clipboardPreview != null
    }

    private fun applyCandidateStripPresentation(presentation: CandidateStripPresentation) {
        val mainView = mainLayoutBinding ?: return
        applyCandidateTabSuggestionOffset(mainView, presentation.showCandidateTab)
        mainView.candidateTabLayout.isVisible = presentation.showCandidateTab
        if (presentation.resetCandidateTabSelection) {
            val tab = mainView.candidateTabLayout.getTabAt(0)
            mainView.candidateTabLayout.selectTab(tab)
        }
        if (presentation.showIndependentShortcutToolbar) {
            mainView.shortcutToolbarRecyclerview.isVisible = true
        } else {
            mainView.shortcutToolbarRecyclerview.isVisible = false
        }
    }

    private fun collapseShortcutEntryExpansion(refreshContent: Boolean = true) {
        if (!integratedShortcutEntryExpanded) return
        integratedShortcutEntryExpanded = false
        if (refreshContent) {
            refreshCandidateStripContent()
        }
    }

    private fun handleShortcutAction(type: ShortcutType, mainView: MainLayoutBinding) {
        clearZeroQueryAllState(refresh = false)
        when (type) {
            ShortcutType.SETTINGS -> {
                launchSettingsActivity("setting_fragment_request")
            }

            ShortcutType.EMOJI -> {
                _keyboardSymbolViewState.value = SymbolKeyboardState(
                    isShown = !_keyboardSymbolViewState.value.isShown
                )
                stringInTail.set("")
                finishComposingText()
                setComposingText("", 0)
            }

            ShortcutType.TEMPLATE -> {
                showUserTemplateListPopup()
            }

            ShortcutType.TEXT_MACRO -> {
                showTextMacroListPopup()
            }

            ShortcutType.KEYBOARD_PICKER -> {
                showKeyboardPicker()
            }

            ShortcutType.KEYBOARD_LAYOUT_EDIT -> {
                toggleKeyboardLayoutEditMode(mainView)
            }

            ShortcutType.INPUT_BEHAVIOR_TOGGLE -> {
                toggleRuntimeInputBehaviorFromShortcut()
            }

            ShortcutType.LIVE_CONVERSION_TOGGLE -> {
                toggleLiveConversionFromShortcut()
            }

            ShortcutType.LEARNING_PAUSE -> {
                toggleLearningPauseFromShortcut()
            }

            ShortcutType.SELECT_ALL -> {
                selectAllText()
            }

            ShortcutType.COPY -> {
                copyAction()
            }

            ShortcutType.PASTE -> {
                pasteAction()
            }

            ShortcutType.DATE_PICKER -> {
                showCurrentDateListPopup()
            }

            ShortcutType.VOICE_INPUT -> {
                startVoiceInput(mainView)
            }

            ShortcutType.CLIP_BOARD -> {
                _keyboardSymbolViewState.value = SymbolKeyboardState(
                    isShown = true,
                    mode = SymbolMode.CLIPBOARD
                )
                stringInTail.set("")
                finishComposingText()
                setComposingText("", 0)
            }
        }
    }

    private fun setShortCutAdapter(
        mainView: MainLayoutBinding
    ) {
        mainView.shortcutToolbarRecyclerview.apply {
            layoutManager =
                LinearLayoutManager(this@IMEService, LinearLayoutManager.HORIZONTAL, false)
            adapter = shortcutAdapter
        }
        val iconColor = resolveCandidateShortcutIconColor()
        shortcutAdapter?.setIconColor(iconColor)
        suggestionAdapter?.setShortcutIconColor(iconColor)
        shortcutAdapter?.onItemClicked = { type ->
            handleShortcutAction(type, mainView)
        }
    }

    private fun setSymbolKeyboard(
        mainView: MainLayoutBinding,
        symbolView: CustomSymbolKeyboardView = mainView.keyboardSymbolView,
    ) {
        symbolView.apply {
            setLifecycleOwner(this@IMEService)
            setOnReturnToTenKeyButtonClickListener(object : ReturnToTenKeyButtonClickListener {
                override fun onClick() {
                    _keyboardSymbolViewState.value = SymbolKeyboardState(
                        isShown = !_keyboardSymbolViewState.value.isShown
                    )
                    finishComposingText()
                    setComposingText("", 0)
                }
            })
            setOnDeleteButtonSymbolViewClickListener(object : DeleteButtonSymbolViewClickListener {
                override fun onClick() {
                    if (!deleteKeyLongKeyPressed.get()) {
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                    }
                    stopDeleteLongPress()
                }
            })

            setOnDeleteButtonFingerUpListener {
                stopDeleteLongPress()
            }
            setOnDeleteButtonSymbolViewLongClickListener(object :
                DeleteButtonSymbolViewLongClickListener {
                override fun onLongClickListener() {
                    onDeleteLongPressUp.set(true)
                    deleteLongPress()
                    _dakutenPressed.value = false
                    englishSpaceKeyPressed.set(false)
                    deleteKeyLongKeyPressed.set(true)
                }
            })
            /** ここで絵文字を追加 **/
            setOnSymbolRecyclerViewItemClickListener(object : SymbolRecyclerViewItemClickListener {
                override fun onClick(symbol: ClickedSymbol) {
                    commitText(symbol.symbol, 1)
                    CoroutineScope(Dispatchers.IO).launch {
                        clickedSymbolRepository.insert(
                            mode = symbol.mode, symbol = symbol.symbol
                        )
                    }
                }
            })
            setOnSymbolRecyclerViewItemLongClickListener(object :
                SymbolRecyclerViewItemLongClickListener {
                override fun onLongClick(symbol: ClickedSymbol, position: Int) {
                    CoroutineScope(Dispatchers.IO).launch {
                        clickedSymbolRepository.delete(
                            mode = symbol.mode, symbol = symbol.symbol
                        )
                    }
                }
            })
            setOnImageItemClickListener { bitmap -> pasteImageAction(bitmap) }
            setOnClipboardItemClickListener { item ->
                pasteClipboardHistoryItem(item)
            }
            setOnClipboardItemLongClickListener { item, action ->
                handleClipboardHistoryItemAction(item, action)
            }
            setClipboardHistoryEnabled(isClipboardHistoryFeatureEnabled)
            setOnClipboardHistoryToggleListener(this@IMEService)
            setDefaultEmojiSkinTone(defaultEmojiSkinTonePreference)
            setOnDefaultEmojiSkinToneChangeListener { skinTone ->
                defaultEmojiSkinTonePreference = skinTone
                appPreference.default_emoji_skin_tone_preference = skinTone
            }
        }

    }

    private suspend fun setSymbols(mainView: MainLayoutBinding) {
        val engine = awaitKanaKanjiEngineOrNull() ?: return
        coroutineScope {
            if (cachedEmoji == null || cachedEmoticons == null || cachedSymbols == null) {
                val emojiDeferred =
                    async(Dispatchers.Default) { engine.getSymbolEmojiCandidates() }
                val emoticonDeferred =
                    async(Dispatchers.Default) { engine.getSymbolEmoticonCandidates() }
                val symbolDeferred =
                    async(Dispatchers.Default) { engine.getSymbolCandidates() }
                cachedEmoji = emojiDeferred.await()
                cachedEmoticons = emoticonDeferred.await()
                cachedSymbols = symbolDeferred.await()
            }
            val historyDeferred = async(Dispatchers.Default) { clickedSymbolRepository.getAll() }
            cachedClickedSymbolHistory =
                historyDeferred.await().sortedByDescending { it.timestamp }.distinctBy { it.symbol }
        }
        Timber.d("setSymbols: ${cachedEmoji?.size}")
        mainView.keyboardSymbolView.setSymbolLists(
            emojiList = cachedEmoji ?: emptyList(),
            emoticons = cachedEmoticons ?: emptyList(),
            symbols = cachedSymbols ?: emptyList(),
            clipBoardItems = currentClipboardItems,
            symbolsHistory = cachedClickedSymbolHistory ?: emptyList(),
            symbolMode = symbolKeyboardFirstItem ?: SymbolMode.EMOJI,
            defaultEmojiSkinTone = defaultEmojiSkinTonePreference

        )
    }

    private suspend fun setSymbolsClipboard(mainView: MainLayoutBinding) {
        val engine = awaitKanaKanjiEngineOrNull() ?: return
        coroutineScope {
            if (cachedEmoji == null || cachedEmoticons == null || cachedSymbols == null) {
                val emojiDeferred =
                    async(Dispatchers.Default) { engine.getSymbolEmojiCandidates() }
                val emoticonDeferred =
                    async(Dispatchers.Default) { engine.getSymbolEmoticonCandidates() }
                val symbolDeferred =
                    async(Dispatchers.Default) { engine.getSymbolCandidates() }
                cachedEmoji = emojiDeferred.await()
                cachedEmoticons = emoticonDeferred.await()
                cachedSymbols = symbolDeferred.await()
            }
            val historyDeferred = async(Dispatchers.Default) { clickedSymbolRepository.getAll() }
            cachedClickedSymbolHistory =
                historyDeferred.await().sortedByDescending { it.timestamp }.distinctBy { it.symbol }
        }
        Timber.d("setSymbols: ${cachedEmoji?.size}")
        mainView.keyboardSymbolView.setSymbolLists(
            emojiList = cachedEmoji ?: emptyList(),
            emoticons = cachedEmoticons ?: emptyList(),
            symbols = cachedSymbols ?: emptyList(),
            clipBoardItems = currentClipboardItems,
            symbolsHistory = cachedClickedSymbolHistory ?: emptyList(),
            symbolMode = SymbolMode.CLIPBOARD,
            defaultEmojiSkinTone = defaultEmojiSkinTonePreference

        )
    }

    private fun clearSymbols() {
        cachedEmoji = null
        cachedEmoticons = null
        cachedSymbols = null
        cachedClickedSymbolHistory = null
    }

    private fun resolveCandidateLongPressPosition(
        candidate: Candidate,
        position: Int,
        displayedCandidates: List<Candidate>
    ): Int? {
        val session = bunsetsuConversionSession
        if (session == null || !isBunsetsuCursorMoveSessionActive()) {
            return position
        }
        if (session.segments.isEmpty()) return null

        val focusedIndex = session.focusedIndex.coerceIn(0, session.segments.lastIndex)
        val targetSegment = session.segments[focusedIndex]
        return when (
            val selection = displayedIndexToBunsetsuSelection(
                displayedCandidates = displayedCandidates,
                segmentCandidates = targetSegment.candidates,
                displayedIndex = position
            )
        ) {
            null -> null

            is BunsetsuDisplayedSelection.SegmentCandidate -> {
                if (selection.candidate == candidate) selection.segmentIndex else null
            }
        }
    }

    private fun setCandidateClick(
        candidate: Candidate,
        insertString: String,
        currentInputMode: InputMode,
        position: Int,
        displayedCandidates: List<Candidate>
    ) {
        Timber.d("setCandidateClick: $candidate")
        if (isSelectionActionCandidate(candidate) && handleSelectionActionClick(
                candidate,
                position,
            )
        ) {
            return
        }
        if (candidate.type == CANDIDATE_TYPE_TEXT_MACRO) {
            candidate.sourceId?.let(::executeTextMacro)
            return
        }
        if (
            handleBunsetsuCandidateClick(
                candidate = candidate,
                position = position,
                displayedCandidates = displayedCandidates
            )
        ) {
            if (!isBunsetsuCursorMoveSessionActive()) {
                setCursorLeftAfterCommitPair(candidate.string)
                restoreKeyboardFromFullSuggestionViewIfNeeded()
            }
            return
        }
        if (insertString.isNotEmpty()) {
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            withTopRowLearningInput(insertString) {
                processCandidate(
                    candidate = candidate,
                    insertString = insertString,
                    currentInputMode = currentInputMode,
                    position = position,
                    explicitlySelected = true,
                )
            }
            setCursorLeftAfterCommitPair(candidate.string)
        }
        resetFlagsSuggestionClick()
        consumePendingZeroQueryAfterCommit()
    }

    private fun handleBunsetsuCandidateClick(
        candidate: Candidate,
        position: Int,
        displayedCandidates: List<Candidate>
    ): Boolean {
        val session = bunsetsuConversionSession ?: return false
        if (!isBunsetsuCursorMoveSessionActive()) return false
        val mainView = mainLayoutBinding ?: return false

        val focusedIndex = session.focusedIndex.coerceIn(0, session.segments.lastIndex)
        val targetSegment = session.segments[focusedIndex]
        val selection = displayedIndexToBunsetsuSelection(
            displayedCandidates = displayedCandidates,
            segmentCandidates = targetSegment.candidates,
            displayedIndex = position
        )
        val segmentSelection = when (selection) {

            null -> {
                Timber.d("Bunsetsu candidate click ignored because displayed index is stale: %s", position)
                return true
            }

            is BunsetsuDisplayedSelection.SegmentCandidate -> selection
        }
        if (segmentSelection.candidate != candidate) {
            Timber.d("Bunsetsu candidate click ignored because candidate is stale: %s", position)
            return true
        }

        val segmentIndex = segmentSelection.segmentIndex
        val segmentCandidate = segmentSelection.candidate
        val candidateDisplayText = displayTextFromCandidate(segmentCandidate)

        val updatedSegments = session.segments.toMutableList()
        updatedSegments[focusedIndex] = targetSegment.copy(
            displayText = candidateDisplayText,
            selectedIndex = segmentIndex,
            overrideDisplayCandidate = null,
            explicitlySelected = true,
        )
        bunsetsuConversionSession = session.copy(segments = updatedSegments)
        commitBunsetsuConversionUntilFocusedSegment(
            mainView = mainView,
            session = session.copy(segments = updatedSegments)
        )
        return true
    }

    private fun commitBunsetsuConversionUntilFocusedSegment(
        mainView: MainLayoutBinding,
        session: BunsetsuConversionSession
    ): Boolean {
        if (session.segments.isEmpty()) return false

        val focusedIndex = session.focusedIndex.coerceIn(0, session.segments.lastIndex)
        val committedText = session.segments
            .take(focusedIndex + 1)
            .joinToString(separator = "") { it.displayText }
        val remainingSegmentInput = session.segments
            .drop(focusedIndex + 1)
            .joinToString(separator = "") { it.reading }
        val sessionTailText = session.tailText
        val nextInput = if (remainingSegmentInput.isNotEmpty()) {
            remainingSegmentInput
        } else {
            sessionTailText
        }
        val nextTailText = if (remainingSegmentInput.isNotEmpty()) {
            sessionTailText
        } else {
            ""
        }
        val shouldRememberZeroQuery = nextInput.isEmpty() && committedText.isNotBlank()

        recordBunsetsuLearning(
            originalReading = session.rawInput,
            segments = session.segments.take(focusedIndex + 1),
            complete = nextInput.isEmpty(),
        )

        if (nextInput.isEmpty()) {
            finalizeBunsetsuReconversion(
                originalReading = session.rawInput,
                committedText = committedText
            )
        } else {
            appendBunsetsuReconversionDraft(
                originalReading = session.rawInput,
                committedText = committedText
            )
            preserveBunsetsuReconversionDraftOnNextProcessInput = true
        }
        if (shouldRememberZeroQuery) {
            rememberZeroQueryKeyAfterCommit(committedText, session.rawInput)
        }

        beginBatchEdit()
        try {
            setComposingText("", 0)
            finishComposingText()
            if (committedText.isNotEmpty()) {
                commitText(committedText, 1)
            }

            if (nextInput.isNotEmpty()) {
                stringInTail.set(nextTailText)
                val spannableString = SpannableString(nextInput + nextTailText)
                setComposingTextAfterEdit(
                    inputString = nextInput,
                    spannableString = spannableString,
                    backgroundColor = if (customComposingTextPreference == true) {
                        inputCompositionAfterBackgroundColor
                            ?: getColor(com.kazumaproject.core.R.color.blue)
                    } else {
                        getColor(com.kazumaproject.core.R.color.blue)
                    },
                    textColor = if (customComposingTextPreference == true) {
                        inputCompositionTextColor
                    } else {
                        null
                    }
                )
            }
        } finally {
            endBatchEdit()
        }

        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        setSuggestionAdaptersOnMain(emptyList())
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        if (false
        ) {
            currentHighlightIndex = RecyclerView.NO_POSITION
        }
        isFirstClickHasStringTail = false
        _inputString.update { nextInput }
        clearBunsetsuConversionSession()

        if (nextInput.isNotEmpty()) {
            scope.launch {
                processInputString(nextInput, mainView)
            }
        } else {
            stringInTail.set("")
            if (shouldRememberZeroQuery) {
                consumePendingZeroQueryAfterCommit()
            }
        }
        return true
    }


    private fun isEditHistoryEnabled(): Boolean {
        return appPreference.undo_enable_preference == true
    }

    private fun isDeleteHistoryRecordingEnabled(): Boolean {
        return appPreference.undo_enable_preference == true ||
                appPreference.delete_key_down_flick_preference == true
    }

    private fun isDeleteKeyDownFlickUndoEnabled(): Boolean {
        return appPreference.delete_key_down_flick_preference == true
    }

    private fun updateSideKeyPreviousDrawableForHistory() {
        val drawableRes = if (deletedBuffer.hasUndoHistory()) {
            com.kazumaproject.core.R.drawable.baseline_delete_24
        } else {
            com.kazumaproject.core.R.drawable.undo_24px
        }
    }

    private fun refreshEditHistoryUi() {
        updateSideKeyPreviousDrawableForHistory()
        refreshCandidateStripContent()
    }

    private fun shouldShowReconversionButton(): Boolean {
        if (!reconversionEnabledPreference) return false
        if (inputString.value.isNotEmpty() || stringInTail.get().isNotEmpty()) return false
        if (isHenkan.get()) return false
        return pendingReconversionEntry != null && pendingReconversionValid
    }

    private fun refreshReconversionUi() {
        val entry = pendingReconversionEntry
        val requestId = reconversionValidationRequestId.incrementAndGet()
        pendingReconversionValid = false
        refreshCandidateStripContent()
        if (!reconversionEnabledPreference ||
            inputString.value.isNotEmpty() ||
            stringInTail.get().isNotEmpty() ||
            isHenkan.get() ||
            entry == null ||
            entry.committedText.isEmpty() ||
            entry.reading.isEmpty()
        ) {
            return
        }

        val inputConnection = currentInputConnection ?: return
        ioScope.launch {
            val valid = editorConnectionReadMutex.withLock {
                withContext(Dispatchers.IO) {
                    val textBeforeCursor = inputConnection
                        .getTextBeforeCursor(entry.committedText.length, 0)
                        ?.toString()
                        .orEmpty()
                    textBeforeCursor.endsWith(entry.committedText)
                }
            }
            withContext(Dispatchers.Main.immediate) {
                if (reconversionValidationRequestId.get() != requestId ||
                    pendingReconversionEntry !== entry ||
                    currentInputConnection !== inputConnection
                ) {
                    return@withContext
                }
                pendingReconversionValid = valid
                refreshCandidateStripContent()
            }
        }
    }

    private fun clearPendingReconversionEntry() {
        clearZeroQueryAllState(refresh = false)
        pendingReconversionEntry = null
        refreshReconversionUi()
    }

    private fun clearBunsetsuReconversionDraft() {
        bunsetsuReconversionDraft = null
        preserveBunsetsuReconversionDraftOnNextProcessInput = false
    }

    private fun rememberCommittedTextForReconversion(
        reading: String,
        committedText: String
    ) {
        if (reading.isEmpty() || committedText.isEmpty()) return
        val draft = bunsetsuReconversionDraft
        pendingReconversionEntry = if (draft != null) {
            ReconversionEntry(
                committedText = draft.committedText + committedText,
                reading = draft.originalReading
            )
        } else {
            ReconversionEntry(
                committedText = committedText,
                reading = reading
            )
        }
        clearBunsetsuReconversionDraft()
        refreshReconversionUi()
    }

    private fun appendBunsetsuReconversionDraft(
        originalReading: String,
        committedText: String
    ) {
        if (originalReading.isEmpty() || committedText.isEmpty()) return
        val existing = bunsetsuReconversionDraft
        bunsetsuReconversionDraft = if (existing != null) {
            existing.copy(committedText = existing.committedText + committedText)
        } else {
            BunsetsuReconversionDraft(
                originalReading = originalReading,
                committedText = committedText
            )
        }
    }

    private fun finalizeBunsetsuReconversion(
        originalReading: String,
        committedText: String
    ) {
        if (committedText.isEmpty()) return
        val draft = bunsetsuReconversionDraft
        val entry = if (draft != null) {
            ReconversionEntry(
                committedText = draft.committedText + committedText,
                reading = draft.originalReading
            )
        } else {
            ReconversionEntry(
                committedText = committedText,
                reading = originalReading
            )
        }
        pendingReconversionEntry = entry
        clearBunsetsuReconversionDraft()
        refreshReconversionUi()
    }

    private fun restoreReadingToPreEdit(
        reading: String,
        mainView: MainLayoutBinding
    ) {
        if (reading.isEmpty()) return
        resetHistoryInteractionFlags()
        stringInTail.set("")
        _inputString.update { reading }
        val spannable = createSpannableWithTail(reading)
        setComposingTextPreEdit(
            inputString = reading,
            spannableString = spannable,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.char_in_edit_color)
            } else {
                getColor(com.kazumaproject.core.R.color.char_in_edit_color)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            }
        )
        isRestoringReconversionInput = true
        scope.launch {
            processInputString(reading, mainView)
        }
    }

    private fun performPendingReconversion() {
        clearZeroQueryAllState(refresh = false)
        val entry = pendingReconversionEntry ?: return
        val mainView = mainLayoutBinding ?: return
        if (!pendingReconversionValid) {
            clearPendingReconversionEntry()
            return
        }

        var restored = false
        suppressedSelectionCleanupCount += 1
        beginBatchEdit()
        try {
            if (!deleteCommittedTextBeforeCursor(entry.committedText)) {
                return
            }
            restoreReadingToPreEdit(entry.reading, mainView)
            restored = true
        } finally {
            endBatchEdit()
        }

        if (restored) {
            clearPendingReconversionEntry()
        }
    }

    /**
     * 削除バッファをまるごとクリアしたいときに呼ぶ
     */
    private fun clearDeletedBuffer() {
        if (!isDeleteHistoryRecordingEnabled()) return
        deletedBuffer.clear()
        activeDeleteHistoryBatch = null
        updateSideKeyPreviousDrawableForHistory()
    }

    private fun clearDeletedBufferWithoutResetLayout() {
        if (!isDeleteHistoryRecordingEnabled()) return
        deletedBuffer.clear()
        activeDeleteHistoryBatch = null
    }

    private fun pushEditHistoryEntry(entry: EditHistoryEntry) {
        if (!isDeleteHistoryRecordingEnabled()) return
        deletedBuffer.push(entry)
        refreshEditHistoryUi()
    }

    private fun captureDeletedTextFromConnection(inputConnection: InputConnection?): String {
        val connection = inputConnection ?: return ""
        if (editorTextSelected && selectedEditorText.isNotEmpty()) {
            return selectedEditorText
        }
        return getLastCharacterAsString(connection)
    }

    private fun removedSuffixFromComposition(beforeInput: String, afterInput: String): String {
        return if (beforeInput.startsWith(afterInput)) {
            beforeInput.substring(afterInput.length)
        } else {
            beforeInput
        }
    }

    private fun createCompositionHistoryEntry(
        beforeInput: String,
        beforeTail: String,
        afterInput: String,
        afterTail: String,
        previewText: String = removedSuffixFromComposition(beforeInput, afterInput)
    ): EditHistoryEntry.CompositionChange? {
        if (beforeInput == afterInput && beforeTail == afterTail) return null
        val normalizedPreview = previewText.ifEmpty {
            (beforeInput + beforeTail).ifEmpty { afterInput + afterTail }
        }
        return EditHistoryEntry.CompositionChange(
            beforeInput = beforeInput,
            beforeTail = beforeTail,
            afterInput = afterInput,
            afterTail = afterTail,
            previewText = normalizedPreview
        )
    }

    private fun resetHistoryInteractionFlags() {
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        clearBunsetsuConversionSession()
        conversionLearningSession.cancel()
    }

    private fun restoreCompositionState(input: String, tail: String) {
        clearZeroQueryAllState(refresh = false)
        beginBatchEdit()
        try {
            _inputString.update { input }
            stringInTail.set(tail)
            resetHistoryInteractionFlags()
            if (input.isEmpty() && tail.isEmpty()) {
                setComposingText("", 0)
                finishComposingText()
            } else {
                val spannableString = SpannableString(input + tail)
                setComposingTextAfterEdit(
                    inputString = input,
                    spannableString = spannableString,
                    backgroundColor = if (customComposingTextPreference == true) {
                        inputCompositionAfterBackgroundColor
                            ?: getColor(com.kazumaproject.core.R.color.blue)
                    } else {
                        getColor(com.kazumaproject.core.R.color.blue)
                    },
                    textColor = if (customComposingTextPreference == true) {
                        inputCompositionTextColor
                    } else {
                        null
                    }
                )
            }
        } finally {
            endBatchEdit()
        }
    }

    private fun deleteCommittedTextBeforeCursor(text: String): Boolean {
        val inputConnection = currentInputConnection ?: return false
        if (text.isEmpty()) return false
        val textBeforeCursor = inputConnection.getTextBeforeCursor(text.length, 0)?.toString() ?: ""
        if (!textBeforeCursor.endsWith(text)) return false
        return deleteSurroundingText(text.length, 0)
    }

    private fun deleteCommittedTextAfterCursor(text: String): Boolean {
        val inputConnection = currentInputConnection ?: return false
        if (text.isEmpty()) return false
        val textAfterCursor = inputConnection.getTextAfterCursor(text.length, 0)?.toString() ?: ""
        if (!textAfterCursor.startsWith(text)) return false
        return deleteSurroundingText(0, text.length)
    }

    private fun performUndo(entry: EditHistoryEntry): Boolean {
        // History edits must invalidate pending reads even when the caret stays unchanged.
        forwardDeleteCoordinator.cancel()
        return when (entry) {
            is EditHistoryEntry.DeleteCommittedText -> {
                when (entry.direction) {
                    DeleteDirection.BeforeCursor -> {
                        commitText(entry.deletedText, 1)
                    }

                    DeleteDirection.AfterCursor -> {
                        commitText(entry.deletedText, 0)
                    }
                }
            }

            is EditHistoryEntry.ReplaceCommittedText -> {
                if (!deleteCommittedTextBeforeCursor(entry.afterText)) {
                    false
                } else {
                    commitText(entry.beforeText, 1)
                }
            }

            is EditHistoryEntry.CompositionChange -> {
                restoreCompositionState(entry.beforeInput, entry.beforeTail)
                true
            }

            is EditHistoryEntry.MacroCommit -> {
                val ic = currentInputConnection ?: return false
                ic.beginBatchEdit()
                try {
                    val prefixDeleted = entry.prefix.isEmpty() ||
                        deleteCommittedTextBeforeCursor(entry.prefix)
                    val suffixDeleted = entry.suffix.isEmpty() ||
                        deleteCommittedTextAfterCursor(entry.suffix)
                    prefixDeleted && suffixDeleted && commitText(entry.beforeText, 1)
                } finally {
                    ic.endBatchEdit()
                }
            }
        }
    }

    private fun performRedo(entry: EditHistoryEntry): Boolean {
        // History edits must invalidate pending reads even when the caret stays unchanged.
        forwardDeleteCoordinator.cancel()
        return when (entry) {
            is EditHistoryEntry.DeleteCommittedText -> {
                when (entry.direction) {
                    DeleteDirection.BeforeCursor -> {
                        deleteCommittedTextBeforeCursor(entry.deletedText)
                    }

                    DeleteDirection.AfterCursor -> {
                        deleteCommittedTextAfterCursor(entry.deletedText)
                    }
                }
            }

            is EditHistoryEntry.ReplaceCommittedText -> {
                if (!deleteCommittedTextBeforeCursor(entry.beforeText)) {
                    false
                } else {
                    commitText(entry.afterText, 1)
                }
            }

            is EditHistoryEntry.CompositionChange -> {
                restoreCompositionState(entry.afterInput, entry.afterTail)
                true
            }

            is EditHistoryEntry.MacroCommit -> {
                if (entry.beforeText.isNotEmpty() && !deleteCommittedTextBeforeCursor(entry.beforeText)) {
                    false
                } else {
                    currentInputConnection?.let { connection ->
                        TextMacroInputConnectionExecutor.commit(
                            connection,
                            ExpandedMacro(
                                text = entry.prefix + entry.suffix,
                                cursorOffset = entry.prefix.length,
                            ),
                        )
                    } == true
                }
            }
        }
    }

    private fun undoLastHistoryEntry() {
        clearZeroQueryAllState(refresh = false)
        val entry = deletedBuffer.popUndo() ?: return
        if (performUndo(entry)) {
            deletedBuffer.pushRedo(entry)
        } else {
            deletedBuffer.pushUndoFromRedo(entry)
        }
        refreshEditHistoryUi()
    }

    private fun redoLastHistoryEntry() {
        clearZeroQueryAllState(refresh = false)
        val entry = deletedBuffer.popRedo() ?: return
        if (performRedo(entry)) {
            deletedBuffer.pushUndoFromRedo(entry)
        } else {
            deletedBuffer.pushRedo(entry)
        }
        refreshEditHistoryUi()
    }

    private fun undoAllHistoryEntries() {
        clearZeroQueryAllState(refresh = false)
        while (deletedBuffer.hasUndoHistory()) {
            val entry = deletedBuffer.popUndo() ?: break
            if (performUndo(entry)) {
                deletedBuffer.pushRedo(entry)
            } else {
                deletedBuffer.pushUndoFromRedo(entry)
                break
            }
        }
        refreshEditHistoryUi()
    }

    private fun redoAllHistoryEntries() {
        clearZeroQueryAllState(refresh = false)
        while (deletedBuffer.hasRedoHistory()) {
            val entry = deletedBuffer.popRedo() ?: break
            if (performRedo(entry)) {
                deletedBuffer.pushUndoFromRedo(entry)
            } else {
                deletedBuffer.pushRedo(entry)
                break
            }
        }
        refreshEditHistoryUi()
    }

    private fun handleExactLengthMatch(
        insertString: String,
        candidateString: String,
        candidate: Candidate,
        currentInputMode: InputMode,
        position: Int,
        explicitlySelected: Boolean,
    ) {
        recordCandidateLearning(
            currentInputMode = currentInputMode,
            originalReading = insertString + stringInTail.get(),
            segmentReading = insertString,
            output = candidateString,
            candidate = candidate,
            candidateIndex = position,
            complete = stringInTail.get().isEmpty(),
            explicitlySelected = explicitlySelected,
        )
        commitLearnedCandidate(insertString, candidateString)
    }

    private fun commitAndClearInput(candidateString: String) {
        val reading = inputString.value
        if (reading.isNotEmpty() && stringInTail.get().isEmpty()) {
            rememberCommittedTextForReconversion(
                reading = reading,
                committedText = candidateString
            )
        }
        if (candidateString.isNotBlank() && stringInTail.get().isEmpty()) {
            rememberZeroQueryKeyAfterCommit(candidateString, reading)
        }
        _inputString.update { "" }
        commitText(candidateString, 1)
    }

    private fun resolveCurrentHenkanCommitText(): String {
        bunsetsuConversionSession?.let { session ->
            val convertedText = session.segments.joinToString(separator = "") { it.displayText }
            return convertedText + session.tailText
        }

        val suggestions = suggestionAdapter?.suggestions.orEmpty()
        if (suggestions.isNotEmpty()) {
            val requestedIndex = if (suggestionClickNum <= 0) {
                0
            } else {
                (suggestionClickNum - 1).coerceAtMost(suggestions.lastIndex)
            }
            val selectedIndex = resolveNonLoadingCandidateIndex(
                suggestions = suggestions,
                insertString = inputString.value,
                requestedIndex = requestedIndex
            ) ?: return inputString.value + stringInTail.get()
            if (suggestions[selectedIndex].type == CANDIDATE_TYPE_TEXT_MACRO) {
                return inputString.value + stringInTail.get()
            }
            return getCandidateCommitString(suggestions[selectedIndex]) + stringInTail.get()
        }

        return inputString.value + stringInTail.get()
    }

    private fun commitCurrentHenkanForNewInput(currentInputMode: InputMode) {
        if (!isHenkan.get()) return

        withTopRowLearningInput(inputString.value) {
            recordCurrentHenkanCandidateLearning(currentInputMode)
        }
        val currentHenkanText = resolveCurrentHenkanCommitText()
        suppressedSelectionCleanupCount += 1

        beginBatchEdit()
        try {
            setComposingText("", 0)
            finishComposingText()
            if (currentHenkanText.isNotEmpty()) {
                commitText(currentHenkanText, 1)
            }
        } finally {
            endBatchEdit()
        }

        resetFlagsEnterKeyNotHenkan()
    }

    private fun recordCurrentHenkanCandidateLearning(currentInputMode: InputMode) {
        val input = inputString.value
        if (input.isEmpty()) return
        val suggestions = suggestionAdapter?.suggestions.orEmpty()
        if (suggestions.isEmpty()) return
        val requestedIndex = if (suggestionClickNum <= 0) 0 else suggestionClickNum - 1
        val selectedIndex = resolveNonLoadingCandidateIndex(
            suggestions = suggestions,
            insertString = input,
            requestedIndex = requestedIndex,
        ) ?: return
        val candidate = suggestions[selectedIndex]
        val candidateLength = candidate.length.toInt()
        if (
            candidate.type == CANDIDATE_TYPE_TEXT_MACRO ||
            candidate.commitText.isBlank() ||
            candidateLength <= 0
        ) return
        val excludedType = when (candidate.type.toInt()) {
            9, 11, 12, 13, 14, 15, 28, 30,
            CANDIDATE_TYPE_TIME.toInt(),
            CANDIDATE_TYPE_ERA.toInt(),
            CANDIDATE_TYPE_USER_TEMPLATE.toInt() -> true
            else -> false
        }
        if (excludedType) return
        val reading = candidate.yomi?.takeIf { it.length == candidateLength }
            ?: input.takeIf { it.length == candidateLength }
            ?: return
        val tail = stringInTail.get()
        recordCandidateLearning(
            currentInputMode = currentInputMode,
            originalReading = reading + tail,
            segmentReading = reading,
            output = getCandidateCommitString(candidate),
            candidate = candidate,
            candidateIndex = selectedIndex,
            complete = tail.isEmpty(),
            explicitlySelected = selectedIndex != 0,
        )
    }

    private fun handlePartialOrExcessLength(
        insertString: String,
        candidate: Candidate,
        currentInputMode: InputMode,
        position: Int,
        explicitlySelected: Boolean,
    ) {
        val candidateLength = candidate.length.toInt()
        val candidateString = candidate.commitText
        if (insertString.length < candidateLength) {
            val fullReading = candidate.yomi?.takeIf { it.length == candidateLength }
            if (fullReading != null) {
                val tail = stringInTail.get()
                recordCandidateLearning(
                    currentInputMode = currentInputMode,
                    originalReading = fullReading + tail,
                    segmentReading = fullReading,
                    output = candidateString,
                    candidate = candidate,
                    candidateIndex = position,
                    complete = tail.isEmpty(),
                    explicitlySelected = explicitlySelected,
                )
            }
        } else if (insertString.length > candidateLength) {
            recordCandidateLearning(
                currentInputMode = currentInputMode,
                originalReading = insertString,
                segmentReading = insertString.substring(0, candidateLength),
                output = candidateString,
                candidate = candidate,
                candidateIndex = position,
                complete = false,
                explicitlySelected = explicitlySelected,
            )
            stringInTail.set(insertString.substring(candidateLength))
        }
        commitAndClearInput(candidateString)
    }

    private fun processCandidate(
        candidate: Candidate,
        insertString: String,
        currentInputMode: InputMode,
        position: Int,
        explicitlySelected: Boolean,
    ) {
        Timber.d("processCandidate ${candidate.type.toInt()} ${insertString.length == candidate.length.toInt()}")
        when (candidate.type.toInt()) {


            15 -> {
                val readingCorrection = candidate.string.correctReading()
                commitAndClearInput(readingCorrection.first)
            }

            9,
            11,
            12,
            13,
            14,
            28,
            30,
            CANDIDATE_TYPE_TIME.toInt(),
            CANDIDATE_TYPE_ERA.toInt(),
            CANDIDATE_TYPE_USER_TEMPLATE.toInt() -> {
                commitAndClearInput(candidate.string)
            }

            else -> {
                if (insertString.length == candidate.length.toInt()) {
                    handleExactLengthMatch(
                        insertString = insertString,
                        candidateString = candidate.string,
                        candidate = candidate,
                        currentInputMode = currentInputMode,
                        position = position,
                        explicitlySelected = explicitlySelected,
                    )
                } else {
                    handlePartialOrExcessLength(
                        insertString = insertString,
                        candidate = candidate,
                        currentInputMode = currentInputMode,
                        position = position,
                        explicitlySelected = explicitlySelected,
                    )
                }
            }
        }
    }

    private fun commitLearnedCandidate(insertString: String, candidateString: String) {
        if (insertString.isNotEmpty() && stringInTail.get().isEmpty()) {
            rememberCommittedTextForReconversion(
                reading = insertString,
                committedText = candidateString
            )
        }
        if (candidateString.isNotBlank() && stringInTail.get().isEmpty()) {
            rememberZeroQueryKeyAfterCommit(candidateString, insertString)
        }
        _inputString.update { "" }
        commitText(candidateString, 1)
    }

    /**
     * Runs a commit of a candidate taken from the main candidate strip. With the two-row bar the
     * strip keeps showing the previous reading's candidates until the new result is published, so
     * the reading the list was built for is handed to the learning guard.
     */
    private inline fun withTopRowLearningInput(insertString: String, block: () -> Unit) {
        learningCandidateListInput = if (shouldUseNecookeyTwoRowBar() && insertString.isNotEmpty()) {
            necookeyTopRowInput
        } else {
            null
        }
        try {
            block()
        } finally {
            learningCandidateListInput = null
        }
    }

    private fun isLearningWriteEnabled(): Boolean =
        isLearnDictionaryMode == true && !isPrivateMode && !learningPausedForSession && dictionaryInputConnection == null

    private fun recordCandidateLearning(
        currentInputMode: InputMode,
        originalReading: String,
        segmentReading: String,
        output: String,
        candidate: Candidate,
        candidateIndex: Int,
        complete: Boolean,
        explicitlySelected: Boolean,
    ) {
        if (currentInputMode != InputMode.ModeJapanese || !isLearningWriteEnabled()) {
            conversionLearningSession.cancel()
            return
        }
        if (!LearningReadingGuard.candidateMatchesReading(
                candidate = candidate,
                reading = segmentReading,
                candidateInput = learningCandidateListInput,
            )
        ) {
            // The candidate was built for another reading (stale list after a same-length edit
            // such as a dakuten toggle). Learning it would pair this reading with that output.
            Timber.d("learning skipped: candidate %s does not belong to %s", candidate.string, segmentReading)
            conversionLearningSession.cancel()
            return
        }
        captureLearningSessionLeftContext(originalReading)
        conversionLearningSession.beginIfNeeded(originalReading)
        val bunsetsu = learningBunsetsuFor(segmentReading, output, candidate)
        if (bunsetsu != null && bunsetsu.size > 1) {
            bunsetsu.forEachIndexed { index, part ->
                conversionLearningSession.record(
                    LearningFragment(
                        reading = part.reading,
                        output = part.output,
                        candidateScore = candidate.score,
                        candidateIndex = candidateIndex,
                        leftId = if (index == 0) candidate.leftId else null,
                        rightId = if (index == bunsetsu.lastIndex) candidate.rightId else null,
                        explicitlySelected = explicitlySelected,
                    )
                )
            }
        } else {
            conversionLearningSession.record(
                LearningFragment(
                    reading = segmentReading,
                    output = output,
                    candidateScore = candidate.score,
                    candidateIndex = candidateIndex,
                    leftId = candidate.leftId,
                    rightId = candidate.rightId,
                    explicitlySelected = explicitlySelected,
                    // Proven single bunsetsu only when the analysis of this reading says so.
                    unsplitWhole = bunsetsu == null,
                )
            )
        }
        if (complete) persistCompletedLearningSession()
    }

    /**
     * Bunsetsu of a committed candidate, from its own conversion path and the bunsetsu
     * boundaries of the analysis made for the same reading. Null when either is unavailable.
     */
    private fun learningBunsetsuFor(
        reading: String,
        output: String,
        candidate: Candidate,
    ): List<LearnedBunsetsu>? {
        val analysis = necookeyLearningAnalysis?.takeIf { it.input.startsWith(reading) }
            ?: return null
        val segments = candidate.conversionSegments.takeIf { it.isNotEmpty() }
            ?: necookeyLearningSegmentsByString[candidate.string]
                ?.takeIf { analysis.input.length == reading.length }
            ?: return null
        return BunsetsuLearningSplitter.split(
            reading = reading,
            output = output,
            segments = segments,
            bunsetsuBoundaries = analysis.slots.map { it.span.end },
        )
    }

    private fun recordBunsetsuLearning(
        originalReading: String,
        segments: List<BunsetsuSegmentState>,
        complete: Boolean,
        explicitlySelected: Boolean = false,
    ) {
        if (!isLearningWriteEnabled()) {
            conversionLearningSession.cancel()
            return
        }
        captureLearningSessionLeftContext(originalReading)
        conversionLearningSession.beginIfNeeded(originalReading)
        segments.forEach { segment ->
            val candidate = segment.overrideDisplayCandidate
                ?: segment.candidates.getOrNull(segment.selectedIndex)
            conversionLearningSession.record(
                LearningFragment(
                    reading = segment.reading,
                    output = segment.displayText,
                    candidateScore = candidate?.score ?: 3000,
                    candidateIndex = segment.selectedIndex,
                    leftId = candidate?.leftId,
                    rightId = candidate?.rightId,
                    explicitlySelected = explicitlySelected || segment.explicitlySelected ||
                        segment.overrideDisplayCandidate != null,
                )
            )
        }
        if (complete) persistCompletedLearningSession()
    }

    private fun captureLearningSessionLeftContext(originalReading: String) {
        if (conversionLearningSession.isActive) return
        learningSessionLeftContext = necookeyLeftContext.takeIf {
            necookeyLeftContextInput.isNotEmpty() && originalReading.startsWith(necookeyLeftContextInput)
        }
    }

    private fun persistCompletedLearningSession() {
        val fragments = conversionLearningSession.recordedFragments()
        val sessionLeftContext = learningSessionLeftContext
        learningSessionLeftContext = null
        val entries = conversionLearningSession.finish(
            learnFirstCandidate = learnFirstCandidateDictionaryPreference == true,
        )
        persistNextWords(sessionLeftContext, fragments)
        if (entries.isEmpty()) return
        val allowMixedSymbolsAndNumbers = learnDictionaryAllowMixedSymbolsNumbersPreference
        ioScope.launch {
            try {
                learnRepository.upsertLearnedDataBatch(
                    learnDataList = entries,
                    allowJapaneseWithSymbolsAndNumbers = allowMixedSymbolsAndNumbers,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Saving conversion learning session failed")
            }
        }
    }

    /** Learns (preceding bunsetsu -> next bunsetsu) pairs; never runs in private mode. */
    private fun persistNextWords(leftContext: String?, fragments: List<LearningFragment>) {
        val previous = lastLearnedBunsetsuOutput
        lastLearnedBunsetsuOutput = fragments.lastOrNull()?.output
        if (fragments.isEmpty() || !isLearningWriteEnabled()) return
        val preceding = previous?.takeIf { leftContext != null && leftContext.endsWith(it) }
        val pairs = NextWordPolicy.pairs(
            precedingBunsetsu = preceding,
            bunsetsu = fragments.map { it.reading to it.output },
            timestamp = System.currentTimeMillis(),
        )
        if (pairs.isEmpty()) return
        ioScope.launch {
            try {
                nextWordRepository.record(pairs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Saving next words failed")
            }
        }
    }

    private fun resetAllFlags() {
        Timber.d("onUpdate resetAllFlags called")
        clearZeroQueryAllState(refresh = false)
        customKeyboardRenderJob?.cancel()
        customKeyboardRenderJob = null
        _inputString.update { "" }
        _tenKeyQWERTYMode.update { TenKeyQWERTYMode.Custom }
        setSuggestionAdapterSuggestionsOnMain(emptyList())
        isPrivateMode = false
        candidateStripIncognitoVisible = false
        candidateStripIncognitoIconDrawable = null
        integratedShortcutEntryExpanded = false
        suggestionAdapter?.setIncognitoIcon(null)
        suggestionAdapterFull?.setIncognitoIcon(null)
        stringInTail.set("")
        suggestionClickNum = 0
        currentCustomKeyboardPosition = 0
        currentCustomKeyboardStableId = null
        filteredCandidateList = emptyList()
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        isContinuousTapInputEnabled.set(false)
        leftCursorKeyLongKeyPressed.set(false)
        rightCursorKeyLongKeyPressed.set(false)
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        lastFlickConvertedNextHiragana.set(false)
        onDeleteLongPressUp.set(false)
        isSpaceKeyLongPressed = false
        onKeyboardSwitchLongPressUp = false
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        lastCandidate = ""
        _keyboardSymbolViewState.update { SymbolKeyboardState() }
        conversionLearningSession.cancel()
        stopDeleteLongPress()
        clearDeletedBuffer()
        refreshEditHistoryUi()
        _selectMode.update { false }
        hasConvertedKatakana = false
        romajiConverter?.clear()
        hardKeyboardShiftPressd = false
        softwareQwertyShiftPressed = false
        softwareQwertyCapsLockOn = false
        resetSumireKeyboardDakutenMode()
        countToggleKatakana = 0
        currentEnterKeyIndex = 0
        currentSpaceKeyIndex = 0
        currentKatakanaKeyIndex = 0
        currentDakutenKeyIndex = 0
        clearPendingReconversionEntry()
        clearBunsetsuReconversionDraft()
        bunsetsuPositionList = emptyList()
        bunsetsuSplitPatterns = emptyList()
        clearBunsetsuConversionSession()
        henkanPressedWithBunsetsuDetect = false
        bunsetusMultipleDetect = false
    }

    private fun clearDirectCommitCompositionState(reason: String) {
        clearZeroQueryAllState(refresh = false)
        _inputString.update { "" }
        stringInTail.set("")
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        bunsetusMultipleDetect = false
        suggestionClickNum = 0
        filteredCandidateList = emptyList()
        lastCandidate = ""
        hasConvertedKatakana = false
        bunsetsuPositionList = emptyList()
        bunsetsuSplitPatterns = emptyList()
        currentHighlightIndex = RecyclerView.NO_POSITION
        clearBunsetsuConversionSession()
        clearPendingReconversionEntry()
        clearBunsetsuReconversionDraft()
        clearSelectionActionSession(clearSuggestions = true)
        setSuggestionAdaptersOnMain(emptyList())
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        suggestionAdapterFull?.updateHighlightPosition(RecyclerView.NO_POSITION)
        requestCandidateRefresh(CandidateShowFlag.Idle)
        refreshReconversionUi()
    }

    private fun dispatchDirectTextIfNeeded(text: String): Boolean {
        val handled = keyInputBehaviorDispatcher.dispatchText(
            behavior = currentInputBehavior,
            inputConnection = currentInputConnection,
            text = text,
            composingPipeline = {}
        )
        if (handled) {
            clearDirectCommitCompositionState("direct commit text")
        }
        return handled
    }

    private fun dispatchDirectSpaceIfNeeded(): Boolean {
        return dispatchDirectTextIfNeeded(" ")
    }

    private fun dispatchDirectEnterIfNeeded(editorFacing: Boolean = false): Boolean {
        // TYPE_NULL defaults to direct *text* input. Its default Enter still honors
        // EditorInfo; explicitly selected direct modes keep their raw Enter behavior.
        if (editorFacing && usesEditorEnterPresentation() &&
            currentInputBehavior == ResolvedInputBehavior.DIRECT_COMMIT && defaultTypeNullUsesEditorEnter()
        ) {
            setEnterKeyPress()
            clearDirectCommitCompositionState("default TYPE_NULL editor enter")
            return true
        }
        val handled = keyInputBehaviorDispatcher.dispatchEnter(
            behavior = currentInputBehavior,
            inputConnection = currentInputConnection,
            composingPipeline = {}
        )
        if (handled) {
            clearDirectCommitCompositionState("direct commit enter")
        }
        return handled
    }

    private fun dispatchDirectBackspaceIfNeeded(): Boolean {
        val handled = keyInputBehaviorDispatcher.dispatchBackspace(
            behavior = currentInputBehavior,
            inputConnection = currentInputConnection,
            composingPipeline = {}
        )
        if (handled) {
            editorMutationRevision.advance()
            clearDirectCommitCompositionState("direct commit backspace")
        }
        return handled
    }

    private fun actionInDestroy() {
        mainLayoutBinding?.suggestionRecyclerView?.apply {
            layoutManager = null
            adapter = null
        }
        lastSuggestionLayoutKey = null
        mainSuggestionGridSpacingDecoration = null
        mainLayoutBinding = null
        closeConnection()
        scope.cancel()
        ioScope.cancel()
        kanaKanjiConversionDispatcher.close()
    }

    private fun resetFlagsSuggestionClick() {
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        restoreKeyboardFromFullSuggestionViewIfNeeded()
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        suggestionAdapterFull?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        clearBunsetsuConversionSession()
        if (stringInTail.get().isEmpty()) {
            clearSuggestionStateAfterCommit()
        }
        _inputString.update { "" }
        refreshReconversionUi()
    }

    private fun restoreKeyboardFromFullSuggestionViewIfNeeded() {
        _suggestionViewStatus.update { true }
        mainLayoutBinding?.let { mainView ->
            mainView.root.post {
                updateSuggestionViewVisibility(mainView, true)
            }
        }
    }

    private fun resetFlagsEnterKey() {
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        clearBunsetsuConversionSession()
        _inputString.update { "" }
        refreshReconversionUi()
    }

    private fun resetFlagsEnterKeyNotHenkan() {
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        suggestionClickNum = 0
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        _inputString.update { "" }
        stringInTail.set("")
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        clearBunsetsuConversionSession()
        conversionLearningSession.cancel()
        refreshReconversionUi()
    }

    private fun resetFlagsKeySpace() {
        onDeleteLongPressUp.set(false)
        _dakutenPressed.value = false
        isContinuousTapInputEnabled.set(false)
        lastFlickConvertedNextHiragana.set(false)
        englishSpaceKeyPressed.set(false)
    }

    private fun resetFlagsDeleteKey() {
        conversionLearningSession.cancel()
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        isHenkan.set(false)
        henkanPressedWithBunsetsuDetect = false
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
        isFirstClickHasStringTail = false
        clearBunsetsuConversionSession()
    }

    /** Down/Moveプレビューにも通常入力と同じPreEdit装飾を適用する。 */
    private fun createFlickPreviewComposingText(
        text: String,
        composingTail: String,
    ): CharSequence {
        return applyPreEditComposingSpans(
            spannableString = SpannableString(text + composingTail),
            inputLength = text.length,
            underlineEnd = text.length + composingTail.length,
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.char_in_edit_color)
            } else {
                getColor(com.kazumaproject.core.R.color.char_in_edit_color)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            },
        )
    }

    /**
     * 編集前（PreEdit）のテキスト装飾を設定する
     * * @param backgroundColor 背景色 (Color Int)
     * @param textColor テキスト色 (Color Int, nullの場合は適用しない)
     */
    private fun setComposingTextPreEdit(
        inputString: String,
        spannableString: SpannableString,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null
    ) {
        val inputLength = inputString.length
        val tailLength = stringInTail.get().length
        applyPreEditComposingSpans(
            spannableString = spannableString,
            inputLength = inputLength,
            underlineEnd = inputLength + tailLength,
            backgroundColor = backgroundColor,
            textColor = textColor,
        )

        Timber.d("launchInputString: setComposingTextPreEdit $spannableString")
        setComposingText(spannableString, 1)
    }

    private fun applyPreEditComposingSpans(
        spannableString: SpannableString,
        inputLength: Int,
        underlineEnd: Int,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null,
    ): SpannableString {
        val spanFlag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
        // フリック専用入力にはトグル待機がないため、最初から編集後の背景を使う。
        val useAfterEditColor = if (qwertyMode.value == TenKeyQWERTYMode.Custom) {
            customToggleRemainingMillis() == 0L
        } else {
            isFlickOnlyMode == true
        }
        val resolvedBackgroundColor = if (useAfterEditColor) {
            if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            }
        } else {
            backgroundColor
        }
        return spannableString.apply {
            setSpan(
                BackgroundColorSpan(resolvedBackgroundColor),
                0,
                inputLength,
                spanFlag,
            )
            textColor?.let { color ->
                setSpan(
                    ForegroundColorSpan(color),
                    0,
                    inputLength,
                    spanFlag,
                )
            }
            setSpan(
                UnderlineSpan(),
                0,
                underlineEnd,
                spanFlag,
            )
        }
    }

    /**
     * 編集後（AfterEdit）のテキスト装飾を設定する
     */
    private fun setComposingTextAfterEdit(
        inputString: String,
        spannableString: SpannableString,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null
    ) {
        // stringInTail が空でなければ、下線の終了位置を延長する
        val underlineEnd = if (stringInTail.get().isNotEmpty()) {
            inputString.length + stringInTail.get().length
        } else {
            inputString.length
        }

        spannableString.apply {
            // 背景色は inputString の部分だけ
            setSpan(
                BackgroundColorSpan(backgroundColor),
                0,
                inputString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
            )

            // テキスト色の設定
            textColor?.let { color ->
                setSpan(
                    ForegroundColorSpan(color),
                    0,
                    inputString.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
                )
            }

            // 下線は stringInTail があればそこまで含める
            setSpan(
                UnderlineSpan(),
                0,
                underlineEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING
            )
        }
        setComposingText(spannableString, 1)
    }

    private fun setEnterKeyAction(
        suggestions: List<Candidate>, currentInputMode: InputMode, insertString: String
    ) {
        Timber.d("setEnterKeyAction: $insertString ${stringInTail.get()} $bunsetsuPositionList $henkanPressedWithBunsetsuDetect")
        val index = resolveNonLoadingCandidateIndex(
            suggestions = suggestions,
            insertString = insertString,
            requestedIndex = (suggestionClickNum - 1).coerceAtLeast(0)
        ) ?: return
        suggestionClickNum = index + 1
        val nextSuggestion = suggestions[index]
        if (nextSuggestion.type == CANDIDATE_TYPE_TEXT_MACRO) {
            nextSuggestion.sourceId?.let(::executeTextMacro)
            return
        }
        withTopRowLearningInput(insertString) {
            processCandidate(
                candidate = nextSuggestion,
                insertString = insertString,
                currentInputMode = currentInputMode,
                position = index,
                explicitlySelected = true,
            )
        }
        clearSuggestionStateAfterCommit()
        resetFlagsEnterKey()
        consumePendingZeroQueryAfterCommit()
    }

    private fun updateUIinHenkan(mainView: MainLayoutBinding, insertString: String) {
    }

    private suspend fun setSuggestionOnView(
        inputString: String, mainView: MainLayoutBinding
    ) {
        Timber.d("setSuggestionOnView: tabPosition first: $inputString $suggestionClickNum")
        if (inputString.isEmpty() || suppressSuggestions || suggestionClickNum > 0) return
        val tabPosition = mainView.candidateTabLayout.selectedTabPosition
        Timber.d("setSuggestionOnView: tabPosition: $tabPosition $bunsetsuPositionList")
        val mode = CandidateQueryModeResolver.resolve(
            tabVisible = candidateTabVisibility == true,
            tabOrder = candidateTabOrder,
            selectedPosition = tabPosition,
        )
        val token = candidateRequestTracker.begin(
            input = inputString,
            mode = mode,
            backend = conversionBackend,
            editorMutationRevision = editorMutationRevision.current(),
        )
        setCandidatesForMode(inputString, mainView, mode, token)
        Timber.d("setSuggestionOnView auto: $inputString $stringInTail $tabPosition $bunsetsuPositionList ${isHenkan.get()} $henkanPressedWithBunsetsuDetect $bunsetusMultipleDetect")
    }

    private suspend fun setCandidatesForMode(
        input: String,
        mainView: MainLayoutBinding,
        mode: CandidateQueryMode,
        token: CandidateRequestToken,
    ) {
        if (
            (mode == CandidateQueryMode.NO_TAB_DEFAULT || mode == CandidateQueryMode.CONVERSION) &&
            shouldUseNecookeyTwoRowBar()
        ) {
            setCandidatesNecookeyTwoRow(input, mainView, token)
            return
        }
        cancelNecookeyTwoRowBar()
        when (mode) {
            CandidateQueryMode.NO_TAB_DEFAULT -> setCandidatesOriginal(input, mainView, token)
            CandidateQueryMode.PREDICTION -> setCandidates(input, mainView, token)
            CandidateQueryMode.CONVERSION -> setCandidatesWithoutPrediction(input, mainView, token)
            CandidateQueryMode.EISUKANA -> setCandidatesEnglishKana(input, token)
        }
    }

    private suspend fun setCandidates(
        insertString: String,
        mainView: MainLayoutBinding,
        token: CandidateRequestToken,
    ) {
        val requestToken = beginZenzRerankRequest()
        val candidates = getSuggestionList(insertString, mainView, token)
        val filtered = if (stringInTail.get().isNotEmpty()) {
            candidates.filter { it.length.toInt() == insertString.length }
        } else {
            candidates
        }
        val zenzaiContext = if (isZenzaiEligible(insertString)) getZenzContext(insertString) else null
        val displayedCandidates = filtered
        if (!shouldApplyCandidateResult(insertString, token)) {
            return
        }
        val shouldApplyLiveConversion =
            shouldStartLiveConversion(insertString) && !hasConvertedKatakana
        val applyLiveConversionBeforeCandidateStrip =
            shouldApplyLiveConversion && liveConversionApplyDelayMillis() == 0L
        if (applyLiveConversionBeforeCandidateStrip) {
            // This is the completed conversion candidate, not a raw composing fallback. Keep the
            // candidate-strip DiffUtil work out of the visible live-conversion critical path.
            delayBeforeApplyingLiveConversion()
            if (!shouldApplyCandidateResult(insertString, token)) return
            if (!applyFirstSuggestionOnMainIfCurrent(
                    insertString = insertString,
                    candidate = displayedCandidates.firstOrNull(),
                )
            ) return
        }
        updateDisplayedCandidates(
            insertString = insertString,
            candidates = displayedCandidates,
            token = token,
        )

        if (shouldApplyLiveConversion && !applyLiveConversionBeforeCandidateStrip) {
            delayBeforeApplyingLiveConversion()
            if (!shouldApplyCandidateResult(insertString, token)) {
                return
            }
            if (!applyFirstSuggestionOnMainIfCurrent(
                    insertString = insertString,
                    candidate = displayedCandidates.firstOrNull()
                )
            ) {
                return
            }
        } else if (isLiveConversionEnable != true && !hasConvertedKatakana && henkanPressedWithBunsetsuDetect) {
            if (!shouldApplyCandidateResult(insertString, token)) {
                return
            }
            if (!applyFirstSuggestionOnMainIfCurrent(
                    insertString = insertString,
                    candidate = displayedCandidates.firstOrNull()
                )
            ) {
                return
            }
        }
        Timber.d("setCandidates called: $bunsetusMultipleDetect $bunsetsuPositionList i:[$insertString] s:[$stringInTail]")
        updateBunsetsuSpaceKeyIfNeededOnMain(mainView, displayedCandidates, insertString)

        if (zenzaiContext != null) {
            launchZenzaiForList(
                requestToken = requestToken,
                insertString = insertString,
                baseCandidates = filtered,
                context = zenzaiContext,
                mainView = mainView,
                candidateToken = token,
            )
        }

    }

    private suspend fun setCandidatesOriginal(
        insertString: String,
        mainView: MainLayoutBinding,
        token: CandidateRequestToken,
    ) {
        val requestToken = beginZenzRerankRequest()
        val candidates = getSuggestionListOriginal(insertString, mainView, token)
        val filtered = if (stringInTail.get().isNotEmpty()) {
            candidates.filter { it.length.toInt() == insertString.length }
        } else {
            candidates
        }
        val zenzaiContext = if (isZenzaiEligible(insertString)) getZenzContext(insertString) else null
        val displayedCandidates = filtered
        if (!shouldApplyCandidateResult(insertString, token)) {
            return
        }
        val shouldApplyLiveConversion =
            shouldStartLiveConversion(insertString) && !hasConvertedKatakana
        val applyLiveConversionBeforeCandidateStrip =
            shouldApplyLiveConversion && liveConversionApplyDelayMillis() == 0L
        if (applyLiveConversionBeforeCandidateStrip) {
            delayBeforeApplyingLiveConversion()
            if (!shouldApplyCandidateResult(insertString, token)) return
            if (!applyFirstSuggestionOnMainIfCurrent(
                    insertString = insertString,
                    candidate = displayedCandidates.firstOrNull(),
                )
            ) return
        }
        updateDisplayedCandidates(
            insertString = insertString,
            candidates = displayedCandidates,
            token = token,
        )

        if (shouldApplyLiveConversion && !applyLiveConversionBeforeCandidateStrip) {
            delayBeforeApplyingLiveConversion()
            if (!shouldApplyCandidateResult(insertString, token)) {
                return
            }
            if (!applyFirstSuggestionOnMainIfCurrent(
                    insertString = insertString,
                    candidate = displayedCandidates.firstOrNull()
                )
            ) {
                return
            }
        } else if (isLiveConversionEnable != true && !hasConvertedKatakana && henkanPressedWithBunsetsuDetect) {
            if (!shouldApplyCandidateResult(insertString, token)) {
                return
            }
            if (!applyFirstSuggestionOnMainIfCurrent(
                    insertString = insertString,
                    candidate = displayedCandidates.firstOrNull()
                )
            ) {
                return
            }
        }
        Timber.d("setCandidates called: $bunsetusMultipleDetect $bunsetsuPositionList i:[$insertString] s:[$stringInTail]")
        updateBunsetsuSpaceKeyIfNeededOnMain(mainView, displayedCandidates, insertString)

        if (zenzaiContext != null) {
            launchZenzaiForList(
                requestToken = requestToken,
                insertString = insertString,
                baseCandidates = filtered,
                context = zenzaiContext,
                mainView = mainView,
                candidateToken = token,
            )
        }
    }

    private suspend fun setCandidatesWithoutPrediction(
        insertString: String,
        mainView: MainLayoutBinding,
        token: CandidateRequestToken,
    ) {
        beginZenzRerankRequest()
        val candidates = getSuggestionListWithoutPrediction(insertString, token)
        val filtered = if (stringInTail.get().isNotEmpty()) {
            candidates.filter { it.length.toInt() == insertString.length }
        } else {
            candidates
        }
        val displayedCandidates = filtered
        if (!shouldApplyCandidateResult(insertString, token)) {
            return
        }
        if (!suppressSuggestions) {
            updateSuggestionAdaptersOnMain(
                candidates = displayedCandidates,
                insertString = insertString,
                token = token,
            )
        }

        if (shouldStartLiveConversion(insertString) && !hasConvertedKatakana) {
            delayBeforeApplyingLiveConversion()
            if (!shouldApplyCandidateResult(insertString, token)) {
                return
            }
            if (!applyFirstSuggestionOnMainIfCurrent(
                    insertString = insertString,
                    candidate = filtered.firstOrNull()
                )
            ) {
                return
            }
        } else if (isLiveConversionEnable != true && !hasConvertedKatakana && henkanPressedWithBunsetsuDetect) {
            if (!shouldApplyCandidateResult(insertString, token)) {
                return
            }
            if (!applyFirstSuggestionOnMainIfCurrent(
                    insertString = insertString,
                    candidate = displayedCandidates.firstOrNull()
                )
            ) {
                return
            }
        }
        Timber.d("setCandidates called: $bunsetusMultipleDetect $bunsetsuPositionList i:[$insertString] s:[$stringInTail]")
        bunsetsuPositionList?.let {
            if (bunsetusMultipleDetect && it.isNotEmpty()) {
                withContext(Dispatchers.Main.immediate) {
                    if (!shouldApplyCandidateResult(insertString, token)) return@withContext
                    handleJapaneseModeSpaceKeyWithBunsetsu(
                        mainView, filtered, insertString
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // necookey: two-row candidate bar
    //
    // Top row:    [Sumire bunsetsu conversion, ambiguous bunsetsu re-chosen by zenz]
    //             + alternatives for the first bunsetsu (same reading range), Sumire order.
    // Bottom row: prediction candidates (queryPrediction) not already in the top row.
    //
    // Only the docked keyboard with a software keyboard uses it, and only while composing (no
    // henkan / bunsetsu-session / tail state). Every other state falls back to Sumire's own bar.
    // ------------------------------------------------------------------------------------------

    private fun isNecookeyTwoRowSurfaceAvailable(): Boolean =
        appPreference.necookey_two_row_candidate_bar_preference &&
            true &&
            !false &&
            true &&
            true &&
            true

    private fun shouldUseNecookeyTwoRowBar(): Boolean =
        isNecookeyTwoRowSurfaceAvailable() &&
            stringInTail.get().isEmpty() &&
            !isHenkan.get() &&
            suggestionClickNum == 0 &&
            !isBunsetsuCursorMoveSessionActive()

    /** Extra candidate-strip height reserved for the bottom row (0 when the bar is off). */
    private fun necookeyPredictionRowBudgetDp(): Int =
        if (isNecookeyTwoRowSurfaceAvailable()) NECOOKEY_PREDICTION_ROW_HEIGHT_DP else 0

    private fun necookeyZenzGateEnabled(): Boolean =
        AppVariantConfig.hasZenz && appPreference.necookey_zenz_bunsetsu_gate_preference

    private fun isNecookeyActionCandidate(candidate: Candidate): Boolean =
        candidate.type == CANDIDATE_TYPE_TEXT_MACRO || isSelectionActionCandidate(candidate)

    private fun cancelNecookeyTwoRowBar() {
        necookeyZenzJob?.cancel()
        necookeyZenzJob = null
        if (necookeyPredictionRowInput.isNotEmpty() || necookeyPredictionRowCandidates.isNotEmpty()) {
            necookeyPredictionRowInput = ""
            necookeyPredictionRowCandidates = emptyList()
            runOnMainThread { syncNecookeyPredictionRow(candidatesShown = false) }
        }
    }

    private suspend fun setCandidatesNecookeyTwoRow(
        insertString: String,
        mainView: MainLayoutBinding,
        token: CandidateRequestToken,
    ) {
        beginZenzRerankRequest()
        necookeyZenzJob?.cancel()
        necookeyZenzJob = null

        // Prediction first: both lookups update the shared bunsetsu state, and the conversion
        // result must be the one left behind for space / henkan handling.
        val predictions = getSuggestionList(insertString, mainView, token)
        if (!shouldApplyCandidateResult(insertString, token)) return

        var core: KanaKanjiQueryResult? = null
        val conversion = getSuggestionListWithoutPrediction(
            insertString = insertString,
            token = token,
            nBestOverride = maxOf(nBest ?: 4, necookeyCandidateBarConfig.conversionNBest),
            coreResultSink = { core = it },
        )
        if (!shouldApplyCandidateResult(insertString, token)) return

        val baseAnalysis = core?.let { result -> analyzeNecookeyBunsetsu(insertString, result) }
        necookeyLearningAnalysis = baseAnalysis
        necookeyLearningSegmentsByString = core?.candidateSegmentsByString.orEmpty()
        val zenzaiOn = baseAnalysis != null && isZenzaiEligible(insertString)
        // Capture editor context before publishing the initial bar, which may apply live
        // conversion and mutate the composing text.
        val context = if (zenzaiOn) getZenzContext(insertString) else null
        if (!shouldApplyCandidateResult(insertString, token)) return
        val leftContext = when {
            isLearnDictionaryMode != true -> ""
            context != null -> context.leftContext
            else -> getZenzContext(insertString).leftContext
        }
        necookeyLeftContext = leftContext
        necookeyLeftContextInput = insertString
        val nextWordCandidates = lookupNextWordCandidates(leftContext, insertString)
        if (!shouldApplyCandidateResult(insertString, token)) return

        val sumireTop = baseAnalysis?.primary
        val cacheKey = if (sumireTop != null && context != null) {
            zenzaiKey(context, insertString, sumireTop.string)
        } else {
            null
        }
        val cached = cacheKey?.let { key ->
            synchronized(necookeyZenzOverrideCache) { necookeyZenzOverrideCache[key] }
        }
        cached?.let { (_, carry) -> zenzaiCarry = carry }
        val cachedOverride = cached?.first

        val barPredictions = nextWordCandidates + predictions
        val bar = TwoRowCandidateBarPlanner.plan(
            input = insertString,
            conversionCandidates = conversion,
            analysis = baseAnalysis,
            primaryOverride = cachedOverride,
            predictionCandidates = barPredictions,
            config = necookeyCandidateBarConfig,
            isActionCandidate = ::isNecookeyActionCandidate,
        )
        publishNecookeyTwoRowBar(insertString, bar, token, applyLiveConversion = true)

        if (sumireTop != null && cacheKey != null && cached == null && context != null) {
            launchZenzaiForTwoRow(
                insertString = insertString,
                analysis = baseAnalysis,
                sumireTop = if (sumireTop.conversionSegments.isNotEmpty()) sumireTop else sumireTop.copy(
                    conversionSegments = core?.candidateSegmentsByString?.get(sumireTop.string).orEmpty(),
                ),
                cacheKey = cacheKey,
                context = context,
                conversion = conversion,
                predictions = barPredictions,
                token = token,
            )
        }
    }

    private fun launchZenzaiForTwoRow(
        insertString: String,
        analysis: BunsetsuAnalysis,
        sumireTop: Candidate,
        cacheKey: String,
        context: ZenzContext,
        conversion: List<Candidate>,
        predictions: List<Candidate>,
        token: CandidateRequestToken,
    ) {
        necookeyZenzJob = scope.launch {
            var shown: Candidate? = null
            suspend fun show(top: Candidate) {
                if (inputString.value != insertString || !shouldApplyCandidateResult(insertString, token)) return
                if (!shouldUseNecookeyTwoRowBar()) return
                val override = top.takeIf {
                    it.string != sumireTop.string || it.zenzChecked || it.zenzAdjusted
                }
                if (override == shown) return
                val bar = TwoRowCandidateBarPlanner.plan(
                    input = insertString,
                    conversionCandidates = conversion,
                    analysis = analysis,
                    primaryOverride = override,
                    predictionCandidates = predictions,
                    config = necookeyCandidateBarConfig,
                    isActionCandidate = ::isNecookeyActionCandidate,
                )
                shown = override
                publishNecookeyTwoRowBar(insertString, bar, token, applyLiveConversion = false)
                if (
                    shouldStartLiveConversion(insertString) && !hasConvertedKatakana &&
                    inputString.value == insertString
                ) {
                    val primary = bar.primary
                    if (primary != null && getCandidateCommitString(primary) != lastCandidate) {
                        applyFirstSuggestion(primary)
                    }
                }
            }
            val outcome = try {
                runZenzaiStep(insertString, sumireTop, context, ::show)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "zenzai (two-row) failed")
                ZenzDiagnosticsStore.recordFailure(applicationContext)
                null
            } ?: return@launch
            recordZenzaiDiagnostics(outcome)
            if (inputString.value != insertString || !shouldApplyCandidateResult(insertString, token)) {
                return@launch
            }
            synchronized(necookeyZenzOverrideCache) {
                necookeyZenzOverrideCache[cacheKey] =
                    (outcome.top.takeIf { outcome.changed || it.zenzChecked || it.zenzAdjusted }) to zenzaiCarry
            }
            show(outcome.top)
        }
    }

    /** Learned following words for [leftContext] whose reading starts with the current input. */
    private suspend fun lookupNextWordCandidates(
        leftContext: String,
        insertString: String,
    ): List<Candidate> {
        if (isLearnDictionaryMode != true || leftContext.isBlank()) return emptyList()
        if (insertString.length > NextWordPolicy.MAX_READING_LENGTH) return emptyList()
        return try {
            withContext(Dispatchers.IO) {
                nextWordRepository.lookup(leftContext, insertString, NEXT_WORD_CANDIDATE_LIMIT)
            }.map { entry ->
                Candidate(
                    string = entry.output,
                    type = CANDIDATE_TYPE_NEXT_WORD,
                    length = entry.reading.length.toUByte(),
                    score = 0,
                    yomi = entry.reading,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "next word lookup failed")
            emptyList()
        }
    }

    private fun analyzeNecookeyBunsetsu(
        input: String,
        result: KanaKanjiQueryResult,
    ): BunsetsuAnalysis? {
        val enginePrimary = result.candidates.firstOrNull { it.length.toInt() == input.length }
            ?: return null
        val splits = result.bunsetsuResult?.splitPatternByCandidateString?.get(enginePrimary.string)
            ?: result.bunsetsuResult?.primarySplitPositions.orEmpty()
        return BunsetsuAnalyzer.analyze(
            input = input,
            nBest = result.candidates,
            segmentsByString = result.candidateSegmentsByString,
            splitPositions = splits,
        )
    }

    private suspend fun publishNecookeyTwoRowBar(
        insertString: String,
        bar: TwoRowCandidateBar,
        token: CandidateRequestToken,
        applyLiveConversion: Boolean,
    ) {
        val topRow = bar.topRow
        if (!shouldApplyCandidateResult(insertString, token)) return
        withContext(Dispatchers.Main.immediate) {
            if (!shouldApplyCandidateResult(insertString, token)) return@withContext
            necookeyPredictionRowInput = insertString
            necookeyTopRowInput = insertString
            necookeyPredictionRowCandidates = bar.predictions
        }
        if (!suppressSuggestions) {
            updateSuggestionAdaptersOnMain(
                candidates = topRow,
                insertString = insertString,
                fullCandidates = topRow + bar.predictions,
                token = token,
            )
        }
        if (applyLiveConversion && shouldStartLiveConversion(insertString) && !hasConvertedKatakana) {
            delayBeforeApplyingLiveConversion()
            if (!shouldApplyCandidateResult(insertString, token)) return
            applyFirstSuggestionOnMainIfCurrent(
                insertString = insertString,
                candidate = bar.primary,
            )
        }
    }

    /** Main thread. Shows the bottom row only for the input it was built for. */
    private fun syncNecookeyPredictionRow(candidatesShown: Boolean) {
        val binding = mainLayoutBinding ?: return
        val row = binding.necookeyPredictionRow
        val adapter = necookeyPredictionAdapter ?: return
        val show = candidatesShown &&
            necookeyPredictionRowInput.isNotEmpty() &&
            necookeyPredictionRowInput == inputString.value &&
            shouldUseNecookeyTwoRowBar()
        if (!show) {
            if (row.visibility != View.GONE) row.visibility = View.GONE
            if (adapter.suggestions.isNotEmpty()) adapter.suggestions = emptyList()
            return
        }
        if (row.adapter !== adapter) {
            row.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            row.itemAnimator = null
            row.isFocusable = false
            row.adapter = adapter
        }
        val colors = resolveCandidatePanelColors()
        adapter.setCandidateTextColor(colors.text)
        val heightPx = applicationContext.dpToPx(NECOOKEY_PREDICTION_ROW_HEIGHT_DP)
        row.layoutParams?.let { params ->
            if (params.height != heightPx) {
                params.height = heightPx
                row.layoutParams = params
            }
        }
        if (adapter.suggestions != necookeyPredictionRowCandidates) {
            adapter.suggestions = necookeyPredictionRowCandidates
            row.scrollToPosition(0)
        }
        if (row.visibility != View.VISIBLE) row.visibility = View.VISIBLE
    }

    private suspend fun setCandidatesEnglishKana(
        insertString: String,
        token: CandidateRequestToken,
    ) {
        beginZenzRerankRequest()
        val candidates = getSuggestionListEnglishKana(insertString)
        val filtered = if (stringInTail.get().isNotEmpty()) {
            candidates.filter { it.length.toInt() == insertString.length }
        } else {
            candidates
        }
        val displayedCandidates = filtered
        if (!shouldApplyCandidateResult(insertString, token)) {
            return
        }
        if (!suppressSuggestions) {
            updateSuggestionAdaptersOnMain(
                candidates = displayedCandidates,
                insertString = insertString,
                token = token,
            )
        }

        if (shouldStartLiveConversion(insertString) && !hasConvertedKatakana) {
            delayBeforeApplyingLiveConversion()
            if (!shouldApplyCandidateResult(insertString, token)) {
                return
            }
            if (!applyFirstSuggestionOnMainIfCurrent(
                    insertString = insertString,
                    candidate = filtered.firstOrNull()
                )
            ) {
                return
            }
        }
    }

    private suspend fun getSuggestionListOriginal(
        insertString: String,
        mainView: MainLayoutBinding,
        token: CandidateRequestToken,
    ): List<Candidate> {
        val resultFromUserDictionary = if (isUserDictionaryEnable == true) {
            withContext(Dispatchers.IO) {
                val prefixMatchNumber = (userDictionaryPrefixMatchNumber ?: 2) - 1
                if (insertString.length <= prefixMatchNumber) return@withContext emptyList<Candidate>()
                userDictionaryRepository.searchByReadingPrefixSuspend(
                    prefix = insertString, limit = userDictionaryPredictionCandidateLimit
                ).map {
                    Candidate(
                        string = it.word,
                        type = CANDIDATE_TYPE_USER_DICTIONARY,
                        length = (it.reading.length).toUByte(),
                        score = it.posScore
                    )
                }.sortedWith(userDictionaryExactFirst(insertString))
            }
        } else {
            emptyList()
        }

        val resultFromUserTemplate = getUserTemplateCandidates(insertString)

        val suggestionLearnRepository = learnedRepositoryForSuggestion()
        val resultFromLearnDictionary =
            if (enablePredictionSearchLearnDictionaryPreference == true &&
                suggestionLearnRepository != null
            ) {
                withContext(Dispatchers.IO) {
                    val prefixMatchNumber = (learnPredictionPreference ?: 4) - 1
                    if (insertString.length <= prefixMatchNumber) return@withContext emptyList<Candidate>()
                    suggestionLearnRepository.predictiveSearchByInput(
                        prefix = insertString, limit = learnDictionaryPredictionCandidateLimit
                    ).map {
                        Candidate(
                            string = it.out,
                            type = CANDIDATE_TYPE_LEARNED_DICTIONARY,
                            length = (it.input.length).toUByte(),
                            score = it.score,
                            yomi = it.input,
                        )
                    }.sortedBy { it.score }
                }
            } else {
                emptyList()
            }

        val ngWords: List<NgWord> =
            if (isNgWordEnable == true) ngWordsList.value else emptyList()

        val enableFlickPref = (enableTypoCorrectionJapaneseFlickKeyboardPreference == true)
        val enableTypoCorrectionJapaneseFlick =
            enableFlickPref && qwertyMode.value in setOf(
                TenKeyQWERTYMode.Sumire,
            )
        val enableTypoCorrectionQwertyEnglish =
            (enableTypoCorrectionQwertyEnglishKeyboardPreference == true) &&
                    (false || (false && !currentQwertyRomajiModeForSession))

        val coreResult = withContext(kanaKanjiConversionDispatcher) {
            queryKanaKanjiCore(
                input = insertString,
                mode = CandidateQueryMode.NO_TAB_DEFAULT,
                learnRepository = suggestionLearnRepository,
                omissionSearchEnabled = isOmissionSearchEnable ?: false,
                typoCorrectionJapaneseFlickEnabled = enableTypoCorrectionJapaneseFlick,
                typoCorrectionQwertyEnglishEnabled = enableTypoCorrectionQwertyEnglish,
            )
        }
        val engineResult = coreResult.bunsetsuResult
        val engineCandidates = coreResult.candidates

        val result = if (conversionCandidatesRomajiEnablePreference == true) {
            val romajiConversionResultList: List<Candidate> = withContext(Dispatchers.Default) {
                getRomajiCandidates(insertString = insertString)
            }
            resultFromLearnDictionary + resultFromUserTemplate + resultFromUserDictionary + engineCandidates + romajiConversionResultList
        } else {
            resultFromLearnDictionary + resultFromUserTemplate + resultFromUserDictionary + engineCandidates
        }

        val filteredCandidates = result.filter { candidate ->
            !NgWordMatcher.matchesAny(insertString, candidate.string, ngWords)
        }.withoutHentaiganaCandidatesIfNeeded().distinctIncludingTextMacroActions()

        val orderedCandidates = applyMergedCandidateOrder(
            input = insertString,
            candidates = filteredCandidates,
            candidateSegmentsByString = coreResult.candidateSegmentsByString,
        )

        if (candidateRequestTracker.isCurrent(token)) {
            updateBunsetsuStateAfterCandidateMerge(
                input = insertString,
                mergedCandidates = orderedCandidates,
                engineResult = engineResult,
                candidateSegments = coreResult.candidateSegmentsByString,
            )
        }

        return orderedCandidates
    }

    private suspend fun getSuggestionList(
        insertString: String,
        mainView: MainLayoutBinding,
        token: CandidateRequestToken? = null,
    ): List<Candidate> = measureDebugStage("IMEService.getSuggestionList") { coroutineScope {
        // These lookups do not depend on one another. Starting them together moves Room and
        // template latency under the converter's CPU time instead of paying it serially first.
        val userDictionaryDeferred = async(Dispatchers.IO) {
            if (isUserDictionaryEnable == true) {
                measureDebugStage("IMEService.getSuggestionList.userDictionary") {
                    val prefixMatchNumber = (userDictionaryPrefixMatchNumber ?: 2) - 1
                    if (insertString.length <= prefixMatchNumber) {
                        emptyList()
                    } else {
                        userDictionaryRepository.searchByReadingPrefixSuspend(
                            prefix = insertString,
                            limit = userDictionaryPredictionCandidateLimit,
                        ).map {
                            Candidate(
                                string = it.word,
                                type = CANDIDATE_TYPE_USER_DICTIONARY,
                                length = it.reading.length.toUByte(),
                                score = it.posScore,
                            )
                        }.sortedWith(userDictionaryExactFirst(insertString))
                    }
                }
            } else {
                emptyList()
            }
        }

        val userTemplateDeferred = async {
            measureDebugStage("IMEService.getSuggestionList.userTemplate") {
                getUserTemplateCandidates(insertString)
            }
        }

        val suggestionLearnRepository = learnedRepositoryForSuggestion()
        val learnDictionaryDeferred = async(Dispatchers.IO) {
            if (enablePredictionSearchLearnDictionaryPreference == true &&
                suggestionLearnRepository != null
            ) {
                measureDebugStage("IMEService.getSuggestionList.learnDictionary") {
                    val prefixMatchNumber = (learnPredictionPreference ?: 4) - 1
                    if (insertString.length <= prefixMatchNumber) {
                        emptyList()
                    } else {
                        suggestionLearnRepository.predictiveSearchByInput(
                            prefix = insertString,
                            limit = learnDictionaryPredictionCandidateLimit,
                        ).map {
                            Candidate(
                                string = it.out,
                                type = CANDIDATE_TYPE_LEARNED_DICTIONARY,
                                length = (it.input.length).toUByte(),
                                score = it.score,
                                yomi = it.input,
                            )
                        }.sortedBy { it.score }
                    }
                }
            } else {
                emptyList()
            }
        }

        val ngWords: List<NgWord> = measureDebugStage("IMEService.getSuggestionList.ngWordSnapshot") {
            if (isNgWordEnable == true) ngWordsList.value else emptyList()
        }

        val enableFlickPref = (enableTypoCorrectionJapaneseFlickKeyboardPreference == true)
        val enableTypoCorrectionJapaneseFlick =
            enableFlickPref && (false || qwertyMode.value == TenKeyQWERTYMode.Sumire)

        val enableTypoCorrectionQwertyEnglish =
            (enableTypoCorrectionQwertyEnglishKeyboardPreference == true) &&
                    (false || (false && !currentQwertyRomajiModeForSession))

        val coreResultDeferred = async(kanaKanjiConversionDispatcher) {
            measureDebugStage("IMEService.getSuggestionList.kanaKanjiEngine") {
                queryKanaKanjiCore(
                    input = insertString,
                    mode = CandidateQueryMode.PREDICTION,
                    learnRepository = suggestionLearnRepository,
                    omissionSearchEnabled = isOmissionSearchEnable ?: false,
                    typoCorrectionJapaneseFlickEnabled = enableTypoCorrectionJapaneseFlick,
                    typoCorrectionQwertyEnglishEnabled = enableTypoCorrectionQwertyEnglish,
                )
            }
        }
        val coreResult = coreResultDeferred.await()
        val resultFromUserDictionary = userDictionaryDeferred.await()
        val resultFromUserTemplate = userTemplateDeferred.await()
        val resultFromLearnDictionary = learnDictionaryDeferred.await()
        val engineResult = coreResult.bunsetsuResult
        val engineCandidates = coreResult.candidates
        engineResult?.let {
            Timber.d("handleJapaneseModeSpaceKeyWithBunsetsu: ${it.primarySplitPositions} ${isHenkan.get()} $ngWords $insertString ${it.splitPatterns}")
        }
        val result = if (conversionCandidatesRomajiEnablePreference == true) {
            val romajiConversionResultList: List<Candidate> =
                measureDebugStage("IMEService.getSuggestionList.romajiCandidates") {
                    withContext(Dispatchers.Default) {
                        getRomajiCandidates(insertString = insertString)
                    }
                }
            resultFromLearnDictionary + resultFromUserTemplate + resultFromUserDictionary + engineCandidates + romajiConversionResultList
        } else {
            resultFromLearnDictionary + resultFromUserTemplate + resultFromUserDictionary + engineCandidates
        }
        val filteredCandidates = measureDebugStage("IMEService.getSuggestionList.ngWordFilterDistinct") {
            result.filter { candidate ->
                !NgWordMatcher.matchesAny(insertString, candidate.string, ngWords)
            }.withoutHentaiganaCandidatesIfNeeded().distinctIncludingTextMacroActions()
        }

        val orderedCandidates = applyMergedCandidateOrder(
            input = insertString,
            candidates = filteredCandidates,
            candidateSegmentsByString = coreResult.candidateSegmentsByString,
        )

        if (token == null || candidateRequestTracker.isCurrent(token)) {
            updateBunsetsuStateAfterCandidateMerge(
                input = insertString,
                mergedCandidates = orderedCandidates,
                engineResult = engineResult,
                candidateSegments = coreResult.candidateSegmentsByString,
            )
        }

        orderedCandidates
    } }

    private suspend fun getLeftContext(
        inputConnection: InputConnection?,
        inputLength: Int,
    ): String = withContext(Dispatchers.IO) {
        val ic = inputConnection ?: return@withContext ""
        val lengthToGetTextBeforeCursor = (8 + inputLength).coerceAtMost(64)
        // カーソル前のテキストを取得
        val charSequence = ic.getTextBeforeCursor(lengthToGetTextBeforeCursor, 0)
        val text = charSequence?.toString() ?: ""

        Timber.d("getLeftContext: inputLength [$inputLength] text: [$text]")
        // 改行記号 '\n' があれば、それより後ろの部分だけを返す。
        // 改行がない場合は、テキスト全体が返されます。
        text.substringAfterLast('\n')
    }

    private suspend fun getRightContext(
        inputConnection: InputConnection?,
        inputLength: Int,
    ): String = withContext(Dispatchers.IO) {
        val ic = inputConnection ?: return@withContext ""
        val lengthToGetTextAfterCursor = (8 + inputLength).coerceAtMost(64)

        // カーソル後のテキストを取得
        val charSequence = ic.getTextAfterCursor(lengthToGetTextAfterCursor, 0)
        val text = charSequence?.toString() ?: ""

        Timber.d("getRightContext: inputLength [$inputLength] text: [$text]")

        text.substringBefore('\n')
    }

    private suspend fun getSuggestionListWithoutPrediction(
        insertString: String,
        token: CandidateRequestToken,
        nBestOverride: Int? = null,
        coreResultSink: ((KanaKanjiQueryResult) -> Unit)? = null,
    ): List<Candidate> {
        val resultFromUserDictionary = if (isUserDictionaryEnable == true) {
            withContext(Dispatchers.IO) {
                val prefixMatchNumber = (userDictionaryPrefixMatchNumber ?: 2) - 1
                if (insertString.length <= prefixMatchNumber) return@withContext emptyList<Candidate>()
                userDictionaryRepository.searchByReadingPrefixSuspend(
                    prefix = insertString, limit = userDictionaryPredictionCandidateLimit
                ).map {
                    Candidate(
                        string = it.word,
                        type = CANDIDATE_TYPE_USER_DICTIONARY,
                        length = (it.reading.length).toUByte(),
                        score = it.posScore
                    )
                }.sortedWith(userDictionaryExactFirst(insertString))
            }
        } else {
            emptyList()
        }

        val resultFromUserTemplate = getUserTemplateCandidates(insertString)

        val suggestionLearnRepository = learnedRepositoryForSuggestion()
        val resultFromLearnDictionary =
            if (enablePredictionSearchLearnDictionaryPreference == true &&
                suggestionLearnRepository != null
            ) {
                withContext(Dispatchers.IO) {
                    val prefixMatchNumber = (learnPredictionPreference ?: 4) - 1
                    if (insertString.length <= prefixMatchNumber) return@withContext emptyList<Candidate>()
                    suggestionLearnRepository.predictiveSearchByInput(
                        prefix = insertString, limit = learnDictionaryPredictionCandidateLimit
                    ).map {
                        Candidate(
                            string = it.out,
                            type = CANDIDATE_TYPE_LEARNED_DICTIONARY,
                            length = (it.input.length).toUByte(),
                            score = it.score,
                            yomi = it.input,
                        )
                    }.sortedBy { it.score }
                }
            } else {
                emptyList()
            }

        val ngWords: List<NgWord> =
            if (isNgWordEnable == true) ngWordsList.value else emptyList()
        val coreResult = withContext(kanaKanjiConversionDispatcher) {
            queryKanaKanjiCore(
                input = insertString,
                mode = CandidateQueryMode.CONVERSION,
                learnRepository = suggestionLearnRepository,
                nOverride = nBestOverride,
            )
        }
        coreResultSink?.invoke(coreResult)
        val engineResult = coreResult.bunsetsuResult
        val engineCandidates = coreResult.candidates

        val result = if (conversionCandidatesRomajiEnablePreference == true) {
            val romajiConversionResultList: List<Candidate> = withContext(Dispatchers.Default) {
                getRomajiCandidates(insertString = insertString)
            }
            resultFromLearnDictionary + resultFromUserTemplate + resultFromUserDictionary + engineCandidates + romajiConversionResultList
        } else {
            resultFromLearnDictionary + resultFromUserTemplate + resultFromUserDictionary + engineCandidates
        }

        val filteredCandidates = result.filter { candidate ->
            !NgWordMatcher.matchesAny(insertString, candidate.string, ngWords)
        }.withoutHentaiganaCandidatesIfNeeded().distinctIncludingTextMacroActions()

        val orderedCandidates = applyMergedCandidateOrder(
            input = insertString,
            candidates = filteredCandidates,
            candidateSegmentsByString = coreResult.candidateSegmentsByString,
        )

        if (candidateRequestTracker.isCurrent(token)) {
            updateBunsetsuStateAfterCandidateMerge(
                input = insertString,
                mergedCandidates = orderedCandidates,
                engineResult = engineResult,
                candidateSegments = coreResult.candidateSegmentsByString,
            )
        }

        return orderedCandidates
    }

    private fun shouldCollectCandidateRubySegments(): Boolean =
        isLiveConversionEnable == true && showLiveConversionCandidateYomi &&
            appPreference.live_conversion_candidate_yomi_mode == AppPreference.CANDIDATE_YOMI_MODE_RUBY

    private suspend fun applyMergedCandidateOrder(
        input: String,
        candidates: List<Candidate>,
        candidateSegmentsByString: Map<String, List<CandidateConversionSegment>> = emptyMap(),
    ): List<Candidate> {
        val promotedCandidates = measureDebugStage("IMEService.exactInputPromotion") {
            ExactInputCandidatePromotionPolicy.promote(
                input = input,
                candidates = candidates,
            )
        }
        return promotedCandidates
    }

    /**
     * A macro action may deliberately have the same display label as a conversion candidate.
     * Keep it as a separate executable item while preserving the legacy string de-duplication
     * behavior for ordinary conversion candidates.
     */
    private fun List<Candidate>.distinctIncludingTextMacroActions(): List<Candidate> =
        distinctBy { candidate ->
            if (candidate.type == CANDIDATE_TYPE_TEXT_MACRO) {
                "text-macro:${candidate.sourceId}"
            } else {
                "text:${candidate.string}"
            }
        }

    private suspend fun getSuggestionListEnglishKana(
        insertString: String,
    ): List<Candidate> {
        val engineCandidates = withContext(kanaKanjiConversionDispatcher) {
            queryKanaKanjiCore(
                input = insertString,
                mode = CandidateQueryMode.EISUKANA,
                learnRepository = null,
            ).candidates
        }
        return engineCandidates.withoutHentaiganaCandidatesIfNeeded().distinctBy { it.string }
    }

    private suspend fun queryKanaKanjiCore(
        input: String,
        mode: CandidateQueryMode,
        learnRepository: LearnRepository?,
        omissionSearchEnabled: Boolean = false,
        typoCorrectionJapaneseFlickEnabled: Boolean = false,
        typoCorrectionQwertyEnglishEnabled: Boolean = false,
        nOverride: Int? = null,
    ): KanaKanjiQueryResult {
        val engine = awaitKanaKanjiEngineOrNull()
            ?: return KanaKanjiQueryResult(candidates = emptyList())
        awaitSystemUserDictionaryLoad()
        val session = kanaKanjiConversionSession ?: KanaKanjiConversionSession(
            engine = engine,
            backend = conversionBackend,
        ).also {
            kanaKanjiConversionSession = it
        }
        val result = session.query(
            KanaKanjiQueryRequest(
                input = input,
                mode = mode,
                bunsetsuSeparation = true,
                n = nOverride ?: nBest ?: 4,
                mozcUtPersonName = mozcUTPersonName,
                mozcUtPlaces = mozcUTPlaces,
                mozcUtWiki = mozcUTWiki,
                mozcUtNeologd = mozcUTNeologd,
                mozcUtWeb = mozcUTWeb,
                userDictionaryRepository = userDictionaryRepository,
                learnRepository = learnRepository,
                omissionSearchEnabled = omissionSearchEnabled,
                typoCorrectionJapaneseFlickEnabled = typoCorrectionJapaneseFlickEnabled,
                typoCorrectionQwertyEnglishEnabled = typoCorrectionQwertyEnglishEnabled,
                typoCorrectionOffsetScore =
                    enableTypoCorrectionJapaneseFlickKeyboardOffsetScorePreference ?: 3000,
                omissionSearchOffsetScore = omissionSearchOffsetScorePreference ?: 1900,
                beamWidth = conversionBeamWidth,
                predictionConfig = predictionConfig,
                collectCandidateSegments = true, // bunsetsu cursor-move session is always on (S3)
            ).also { lastKanaKanjiQueryRequest = it }
        )
        if (BuildConfig.DEBUG) {
            session.performanceSnapshot()?.let { snapshot ->
                Timber.d(
                    "conversionStages inputLength=%d graph=%.2fms penalty=%.2fms forward=%.2fms backward=%.2fms graphReused=%s forwardReused=%s",
                    input.length,
                    snapshot.graphNs / 1_000_000.0,
                    snapshot.penaltyNs / 1_000_000.0,
                    snapshot.forwardDpNs / 1_000_000.0,
                    snapshot.backwardSearchNs / 1_000_000.0,
                    snapshot.graphAppendReused,
                    snapshot.forwardDpReused,
                )
            }
        }
        // Attach the path before merging other sources, which can have the same display
        // text but a different reading. Each async result retains its own correspondence.
        return if (shouldCollectCandidateRubySegments()) {
            result.copy(candidates = result.candidates.map { candidate ->
                val segments = result.candidateSegmentsByString[candidate.string].orEmpty()
                if (segments.isEmpty()) candidate else candidate.copy(conversionSegments = segments)
            })
        } else result
    }

    private fun List<Candidate>.withoutHentaiganaCandidatesIfNeeded(): List<Candidate> {
        if (!suppressHentaiganaCandidates) return this
        return filterNot { it.string.containsHentaigana() }
    }

    private suspend fun getLegacyUserTemplateCandidates(input: String): List<Candidate> {
        if (isUserTemplateEnable != true) return emptyList()
        return withContext(Dispatchers.IO) {
            userTemplateRepository.searchByReading(reading = input, limit = 8)
                .toUserTemplateCandidates()
        }
    }

    private suspend fun getUserTemplateCandidates(insertString: String): List<Candidate> {
        return withContext(Dispatchers.IO) {
            val legacyTemplates = getLegacyUserTemplateCandidates(insertString)
            val contextualMacrosAllowed = !isPrivateMode && currentInputType !in passwordTypes
            val macros = if (isTextMacroCandidateEnable) {
                textMacroRepository.getEnabledByReading(insertString, limit = 8)
                    .mapNotNull { macro ->
                        val compiled = runCatching { TextMacroCompiler.compile(macro.body) }
                            .getOrNull() ?: return@mapNotNull null
                        if (TextMacroContextRequirement.SELECTION in compiled.requirements) {
                            return@mapNotNull null
                        }
                        if (!contextualMacrosAllowed && compiled.requirements.isNotEmpty()) {
                            return@mapNotNull null
                        }
                        Candidate(
                            string = macro.name,
                            type = CANDIDATE_TYPE_TEXT_MACRO,
                            length = insertString.length.coerceAtMost(UByte.MAX_VALUE.toInt()).toUByte(),
                            score = Int.MIN_VALUE + 52,
                            yomi = macro.reading,
                            sourceId = macro.id,
                        )
                    }
            } else {
                emptyList()
            }
            legacyTemplates + macros
        }
    }

    private fun showTextMacroListPopup() {
        val request = beginKeyboardPopupRequest() ?: return
        ioScope.launch {
            try {
                val macros = textMacroRepository.getAllEnabled()
                withContext(Dispatchers.Main.immediate) {
                    if (!isKeyboardPopupRequestCurrent(request)) return@withContext
                    if (macros.isEmpty()) {
                        dismissKeyboardSelectionPopups()
                        showToastMessage(getString(R.string.text_macro_context_unavailable))
                        return@withContext
                    }
                    showKeyboardSelectionList(request, macros.map { it.name }, "text macros", maxVisibleItems = 8) { position ->
                        executeTextMacro(macros[position].id)
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                reportKeyboardPopupFailure(request, exception)
            }
        }
    }

    /**
     * Re-fetches and re-compiles by stable ID, then revalidates the editor immediately before
     * committing. Definition/context text is intentionally never logged or sent to any model.
     */
    private fun executeTextMacro(id: Long) {
        val connection = currentInputConnection ?: return
        val packageName = currentInputEditorInfo?.packageName.orEmpty()
        val input = inputString.value
        val requestId = textMacroExecutionRequestId.incrementAndGet()
        val sensitiveEditor = isPrivateMode || currentInputType in passwordTypes

        ioScope.launch {
            val result = runCatching {
                val initial = readTextMacroEditorSnapshot(connection, packageName, input)
                val macro = textMacroRepository.getById(id)
                    ?.takeIf { it.enabled }
                    ?: error("Macro is unavailable")
                val compiled = TextMacroCompiler.compile(macro.body)
                if (sensitiveEditor && compiled.requirements.isNotEmpty()) {
                    error(getString(R.string.text_macro_sensitive_context_blocked))
                }

                val clipboard = if (TextMacroContextRequirement.CLIPBOARD in compiled.requirements) {
                    if (clipboardUtil.isPrimaryClipSensitive()) {
                        error(getString(R.string.text_macro_clipboard_sensitive))
                    }
                    clipboardUtil.getFirstClipboardTextOrNull()
                        ?.takeIf(String::isNotEmpty)
                        ?: error(getString(R.string.text_macro_context_unavailable))
                } else {
                    null
                }
                val selection = if (TextMacroContextRequirement.SELECTION in compiled.requirements) {
                    initial.selectedText.takeIf(String::isNotEmpty)
                        ?: error(getString(R.string.text_macro_context_unavailable))
                } else {
                    null
                }
                val expanded = compiled.expand(
                    TextMacroContext(
                        selection = selection,
                        clipboard = clipboard,
                    )
                )
                val latest = readTextMacroEditorSnapshot(connection, packageName, input)
                require(initial.selectionStart == latest.selectionStart) { "Selection changed" }
                require(initial.selectionEnd == latest.selectionEnd) { "Selection changed" }
                require(initial.selectedText == latest.selectedText) { "Selection changed" }
                Triple(initial, expanded, compiled.requirements.isNotEmpty())
            }

            withContext(Dispatchers.Main.immediate) {
                if (requestId != textMacroExecutionRequestId.get()) return@withContext
                result.onFailure { exception ->
                    Toast.makeText(
                        this@IMEService,
                        exception.message ?: getString(R.string.text_macro_operation_failed, ""),
                        Toast.LENGTH_SHORT,
                    ).show()
                }.onSuccess { (snapshot, expanded, readsSensitiveContext) ->
                    if (
                        currentInputConnection !== snapshot.connection ||
                        currentInputEditorInfo?.packageName.orEmpty() != snapshot.packageName ||
                        inputString.value != snapshot.input ||
                        (readsSensitiveContext &&
                            (isPrivateMode || currentInputType in passwordTypes))
                    ) {
                        return@onSuccess
                    }
                    val finalSnapshot = runCatching {
                        readTextMacroEditorSnapshot(
                            snapshot.connection,
                            snapshot.packageName,
                            snapshot.input,
                        )
                    }.getOrNull() ?: return@onSuccess
                    if (
                        finalSnapshot.selectionStart != snapshot.selectionStart ||
                        finalSnapshot.selectionEnd != snapshot.selectionEnd ||
                        finalSnapshot.selectedText != snapshot.selectedText
                    ) {
                        return@onSuccess
                    }
                    commitExpandedTextMacro(
                        connection = snapshot.connection,
                        expanded = expanded,
                        replacedText = snapshot.selectedText.ifEmpty { snapshot.input },
                    )
                }
            }
        }
    }

    private fun readTextMacroEditorSnapshot(
        connection: InputConnection,
        packageName: String,
        input: String,
    ): TextMacroEditorSnapshot {
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
            ?: error("Editor state is unavailable")
        return TextMacroEditorSnapshot(
            connection = connection,
            packageName = packageName,
            input = input,
            selectionStart = extracted.selectionStart,
            selectionEnd = extracted.selectionEnd,
            selectedText = connection.getSelectedText(0)?.toString().orEmpty(),
        )
    }

    private fun commitExpandedTextMacro(
        connection: InputConnection,
        expanded: ExpandedMacro,
        replacedText: String,
    ) {
        connection.beginBatchEdit()
        val committed = try {
            setComposingText("", 0)
            finishComposingText()
            TextMacroInputConnectionExecutor.commit(connection, expanded)
        } finally {
            connection.endBatchEdit()
        }
        if (!committed) return

        val cursor = expanded.cursorOffset.coerceIn(0, expanded.text.length)
        pushEditHistoryEntry(
            EditHistoryEntry.MacroCommit(
                beforeText = replacedText,
                prefix = expanded.text.substring(0, cursor),
                suffix = expanded.text.substring(cursor),
            )
        )
        _inputString.update { "" }
        stringInTail.set("")
        clearSelectionActionSession(clearSuggestions = false)
        clearSuggestionStateAfterCommit()
        resetFlagsSuggestionClick()
        consumePendingZeroQueryAfterCommit()
    }

    private fun getRomajiCandidates(insertString: String): List<Candidate> {
        val romaji = romajiConverter?.hiraganaToRomaji(insertString) ?: return emptyList()
        return buildRomajiCandidates(
            readingLength = insertString.length,
            romaji = romaji
        )
    }

    /**
     * カーソル前の文字に応じて、単語または記号1つを削除します。
     * - カーソル直前の文字が指定記号の場合：その記号を1つだけ削除します。
     * - カーソル直前の文字がそれ以外の場合：その単語を末尾まで削除します。
     */
    private fun deleteWordOrSymbolsBeforeCursor(insertString: String) {
        invalidateZeroQueryForEditorMutation()
        val inputConnection = currentInputConnection ?: return
        if (isHenkan.get()) return
        if (stringInTail.get().isNotEmpty()) return

        if (insertString.isNotEmpty()) {
            _inputString.update { "" }
            setComposingText("", 0)
            finishComposingText()
        } else {
            scope.launch {
                val textBeforeCursor = editorConnectionReadMutex.withLock {
                    withContext(Dispatchers.IO) {
                        inputConnection.getTextBeforeCursor(100, 0)?.toString().orEmpty()
                    }
                }
                if (currentInputConnection !== inputConnection || isHenkan.get()) return@launch
                if (textBeforeCursor.isEmpty()) return@launch

                val charsToDelete = deleteKeyFlickTargetChars + ALWAYS_DELETE_KEY_FLICK_BOUNDARIES
                val deleteCount = if (textBeforeCursor.last() in charsToDelete) {
                    1
                } else {
                    textBeforeCursor.reversed().takeWhile {
                        !it.isWhitespace() && it !in charsToDelete
                    }.length
                }

                if (deleteCount > 0) {
                    val deletedText = textBeforeCursor.takeLast(deleteCount)
                    inputConnection.deleteSurroundingText(deleteCount, 0)
                    if (deletedText.isNotEmpty()) {
                        pushEditHistoryEntry(EditHistoryEntry.DeleteCommittedText(deletedText))
                    }
                }
            }
        }
    }

    /**
     * カーソル後の文字に応じて、単語または記号1つを削除します。
     * - カーソル直後の文字が指定記号の場合：その記号を1つだけ削除します。
     * - カーソル直後の文字がそれ以外の場合：その単語を末尾まで削除します。
     * - PreEdit / stringInTail がある場合は committed text を消さず、stringInTail を削除します。
     */
    private fun deleteWordOrSymbolsAfterCursor(insertString: String) {
        invalidateZeroQueryForEditorMutation()
        val inputConnection = currentInputConnection ?: return
        if (isHenkan.get()) return
        if (insertString.isNotEmpty()) {
            return
        }

        scope.launch {
            val textAfterCursor = editorConnectionReadMutex.withLock {
                withContext(Dispatchers.IO) {
                    inputConnection.getTextAfterCursor(100, 0)?.toString().orEmpty()
                }
            }
            if (currentInputConnection !== inputConnection || isHenkan.get()) return@launch
            if (textAfterCursor.isEmpty()) return@launch

            val charsToDelete = deleteKeyFlickTargetChars + ALWAYS_DELETE_KEY_FLICK_BOUNDARIES
            val deleteCount = if (textAfterCursor.first() in charsToDelete) {
                1
            } else {
                textAfterCursor.takeWhile {
                    !it.isWhitespace() && it !in charsToDelete
                }.length
            }

            if (deleteCount > 0) {
                val deletedText = textAfterCursor.take(deleteCount)
                inputConnection.deleteSurroundingText(0, deleteCount)
                if (deletedText.isNotEmpty()) {
                    pushEditHistoryEntry(
                        EditHistoryEntry.DeleteCommittedText(
                            deletedText = deletedText,
                            direction = DeleteDirection.AfterCursor
                        )
                    )
                }
            }
        }
    }

    private fun deleteLongPress() {
        invalidateZeroQueryForEditorMutation()
        if (isKeyboardLayoutEditModeActive()) return
        if (deleteLongPressJob?.isActive == true) return
        val behavior = deleteLongPressConversionBehavior
        activeDeleteLongPressConversionBehavior = behavior
        deleteLongPressFinalRefreshRequested = false
        if (behavior.suspendsCandidateResultsDuringRepeat) {
            if (!deleteLongPressConversionGate.beginRepeat()) {
                activeDeleteLongPressConversionBehavior = null
                return
            }
            candidateRequestTracker.invalidate()
        }
        activeDeleteHistoryBatch = DeleteHistoryBatch(
            initialInput = inputString.value,
            initialTail = stringInTail.get(),
            deletesCommittedText = inputString.value.isEmpty()
        )
        deleteLongPressJob = scope.launch {
            try {
                while (isActive && deleteKeyLongKeyPressed.get()) {
                    val current = inputString.value
                    val tailIsEmpty = stringInTail.get().isEmpty()

                    if (current.isEmpty()) {
                        if (tailIsEmpty) {
                            if (isEditHistoryEnabled()) {
                                val beforeChar =
                                    captureDeletedTextFromConnection(currentInputConnection)
                                if (beforeChar.isNotEmpty()) {
                                    activeDeleteHistoryBatch?.deletedText?.insert(0, beforeChar)
                                }
                            }
                            deleteLastGraphemeOrSelection()
                        } else {
                            break
                        }
                    } else {
                        val newString = current.dropLast(1)
                        _inputString.update { newString }
                        if (newString.isEmpty() && tailIsEmpty) {
                            clearStyledComposingTextAfterFinalDelete(current)
                        }
                    }

                    delay(LONG_DELAY_TIME)
                }
            } finally {
                // The key-up path cancels this job. Final candidate cleanup must therefore also
                // live in finally so cancellation cannot skip it.
                enableContinuousTapInput()
                requestFinalDeleteLongPressRefresh()
            }
        }
        deleteLongPressJob?.invokeOnCompletion {
            if (selectMode.value || !isEditHistoryEnabled()) {
                activeDeleteHistoryBatch = null
                return@invokeOnCompletion
            }
            val batch = activeDeleteHistoryBatch
            activeDeleteHistoryBatch = null
            batch?.let {
                if (it.deletesCommittedText) {
                    val deletedText = it.deletedText.toString()
                    if (deletedText.isNotEmpty()) {
                        pushEditHistoryEntry(EditHistoryEntry.DeleteCommittedText(deletedText))
                    }
                }
            }
        }
    }

    private fun requestFinalDeleteLongPressRefresh() {
        if (deleteLongPressFinalRefreshRequested) return

        val behavior = activeDeleteLongPressConversionBehavior ?: return
        val finalInput = inputString.value
        val refreshFlag = when (behavior) {
            DeleteLongPressConversionBehavior.Deferred ->
                deleteLongPressConversionGate.finishRepeat(finalInput)

            DeleteLongPressConversionBehavior.Continuous ->
                if (finalInput.isEmpty()) {
                    CandidateShowFlag.Idle
                } else {
                    CandidateShowFlag.Updating
                }
        } ?: return

        deleteLongPressFinalRefreshRequested = true
        candidateRequestTracker.invalidate()
        Timber.d(
            "deleteLongPress: final candidate refresh input=[%s] flag=%s",
            finalInput,
            refreshFlag,
        )
        requestCandidateRefresh(refreshFlag, finalInput)
    }

    /**
     * Removes the final composing character without collapsing composing spans to length zero.
     *
     * Some editor implementations warn when a styled composing range is replaced directly with an
     * empty string. Finishing and deleting inside one batch removes those spans first and does not
     * expose the temporary committed state to the display.
     */
    private fun clearStyledComposingTextAfterFinalDelete(previousInput: String) {
        beginBatchEdit()
        try {
            finishComposingText()
            val codePointCount = previousInput.codePointCount(0, previousInput.length)
            if (codePointCount > 0) {
                deleteSurroundingTextInCodePoints(codePointCount, 0)
            }
        } finally {
            endBatchEdit()
        }
    }

    private fun stopDeleteLongPress() {
        deleteKeyLongKeyPressed.set(false)
        onDeleteLongPressUp.set(true)
        deleteLongPressJob?.cancel()
        deleteLongPressJob = null
        requestFinalDeleteLongPressRefresh()
        activeDeleteLongPressConversionBehavior = null
    }

    private fun enableContinuousTapInput() {
        isContinuousTapInputEnabled.set(true)
        lastFlickConvertedNextHiragana.set(true)
    }

    private fun setEnterKeyPress() {
        EditorEnterPolicy.dispatch(
            action = EditorEnterPolicy.resolve(currentInputEditorInfo),
            sendKey = {
                if (currentInputConnection != null) {
                    sendDownUpKeyEvents(it)
                    editorMutationRevision.advance()
                }
            },
            performAction = { performEditorAction(it) },
            commitNewline = { commitText("\n", 1) },
        )
    }

    private fun usesEditorEnterPresentation(): Boolean =
        inputString.value.isEmpty() && stringInTail.get().isEmpty() && !isHenkan.get()

    private fun defaultTypeNullUsesEditorEnter(): Boolean =
        EditorEnterPolicy.usesEditorActionForDefaultTypeNull(
            inputType = currentInputEditorInfo?.inputType,
            setting = TypeNullInputBehaviorSetting.fromPreferenceValue(appPreference.type_null_input_behavior_preference),
            hasExplicitDirectOverride = shortcutInputBehaviorOverride != null ||
                (qwertyMode.value == TenKeyQWERTYMode.Custom && isCustomLayoutDirectMode),
        )

    private fun editorEnterAction(): EditorEnterAction =
        if (currentInputBehavior == ResolvedInputBehavior.DIRECT_COMMIT && !defaultTypeNullUsesEditorEnter()) EditorEnterAction.Enter
        else EditorEnterPolicy.resolve(currentInputEditorInfo)

    private fun editorEnterKeyStateIndex(): Int =
        if (usesEditorEnterPresentation()) EditorEnterPolicy.keyStateIndex(editorEnterAction())
        else currentInputType.getEnterKeyIndexSumire()

    private fun editorEnterLabel(japanese: Boolean): String {
        if (!usesEditorEnterPresentation()) {
            return if (japanese) currentInputType.getQWERTYReturnTextInJp()
            else currentInputType.getQWERTYReturnTextInEn()
        }
        return EditorEnterPolicy.label(editorEnterAction(), japanese)
    }

    // Presentation only: input classification still controls privacy, layout and conversion.
    // Preserve composition labels until the existing confirmation pipeline has finished.
    private fun editorEnterPresentationType(): InputTypeForIME {
        if (!usesEditorEnterPresentation()) {
            return currentInputType
        }
        return when (val action = editorEnterAction()) {
            EditorEnterAction.Enter, EditorEnterAction.Newline -> InputTypeForIME.TextMultiLine
            is EditorEnterAction.Action -> when (action.id) {
                EditorInfo.IME_ACTION_SEARCH -> InputTypeForIME.TextSearchView
                EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS -> InputTypeForIME.TextNextLine
                EditorInfo.IME_ACTION_DONE -> InputTypeForIME.TextDone
                EditorInfo.IME_ACTION_SEND -> InputTypeForIME.TextSend
                else -> InputTypeForIME.TextUri
            }
        }
    }

    private fun handleDeleteKeyTap(insertString: String, suggestions: List<Candidate>) {
        clearZeroQueryAllState(refresh = false)
        if (dispatchDirectBackspaceIfNeeded()) {
            stopDeleteLongPress()
            return
        }
        when {
            insertString.isNotEmpty() -> {
                if (isHenkan.get()) {
                    if (isBunsetsuCursorMoveSessionActive()) {
                        restoreRawInputFromBunsetsuSession()
                        hasConvertedKatakana = isLiveConversionEnable == true
                        resetFlagsDeleteKey()
                    } else if (deleteKeyHighLight == true) {
                        handleDeleteKeyInHenkan(suggestions, insertString)
                    } else {
                        cancelHenkanByLongPressDeleteKey()
                        hasConvertedKatakana = isLiveConversionEnable == true
                    }
                } else {
                    deleteStringCommon(insertString)
                    resetFlagsDeleteKey()
                }
            }

            else -> {
                if (stringInTail.get().isNotEmpty()) return
                if (!selectMode.value) {
                    val beforeChar = captureDeletedTextFromConnection(currentInputConnection)
                    if (beforeChar.isNotEmpty()) {
                        if (isEditHistoryEnabled()) {
                            Timber.d("delete: $beforeChar")
                            pushEditHistoryEntry(EditHistoryEntry.DeleteCommittedText(beforeChar))
                        }
                    }
                }
                deleteLastGraphemeOrSelection()
            }
        }
    }

    private fun handleSpaceKeyClick(
        isFlick: Boolean,
        insertString: String,
        suggestions: List<Candidate>,
        mainView: MainLayoutBinding,
        fromPhysicalKeyboard: Boolean = false
    ) {
        if (!fromPhysicalKeyboard && !isDefaultRomajiHenkanMap && insertString.isNotEmpty() &&
            qwertyMode.value == TenKeyQWERTYMode.Custom && isCustomLayoutRomajiMode &&
            currentInputModeForSession == InputMode.ModeJapanese) {
            handleSpaceKeyClickInCustomRomaji(insertString, mainView, suggestions)
            return
        }
        clearZeroQueryAllState(refresh = false)
        if (dispatchDirectSpaceIfNeeded()) {
            resetFlagsKeySpace()
            return
        }
        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
            resetFlagsKeySpace()
            return
        }

        if (insertString.isNotBlank()) {
            when (currentInputModeForSession) {
                InputMode.ModeJapanese -> if (suggestions.isNotEmpty()) {
                    handleJapaneseModeSpaceKeyWithBunsetsu(mainView, suggestions, insertString)
                }

                else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
            }
        } else {
            if (stringInTail.get().isNotEmpty()) return
            setSpaceKeyActionEnglishAndNumberEmpty(isFlick)
        }
        resetFlagsKeySpace()
    }

    /** Space key for a custom keyboard in romaji mode that uses a custom romaji map. */
    private fun handleSpaceKeyClickInCustomRomaji(
        insertString: String, mainView: MainLayoutBinding, suggestions: List<Candidate>,
    ) {
        val englishSpace = " "
        clearZeroQueryAllState(refresh = false)
        if (dispatchDirectTextIfNeeded(englishSpace)) {
            resetFlagsKeySpace()
            return
        }
        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
            resetFlagsKeySpace()
            return
        }

        if (insertString.isNotBlank()) {
            mainView.apply {
                when (currentInputModeForSession) {
                    InputMode.ModeJapanese -> {
                        val insertStringEndWithN = if (isDefaultRomajiHenkanMap) {
                            romajiConverter?.flushZenkaku(insertString)?.first
                        } else {
                            customRomajiScreenConverter?.flush(insertString)
                        }
                        if (insertStringEndWithN == null) {
                            _inputString.update { insertString }
                            if (suggestions.isNotEmpty()) {
                                handleJapaneseModeSpaceKeyWithBunsetsu(
                                    this, suggestions, insertString
                                )
                            }
                        } else if (!isDefaultRomajiHenkanMap && isHenkan.get()) {
                            // Subsequent Space presses cycle the active candidate selection.
                            // Only the first press should wait for candidates for the reading.
                            if (suggestions.isNotEmpty()) {
                                handleJapaneseModeSpaceKeyWithBunsetsu(mainView, suggestions, insertString)
                            }
                        } else if (!isDefaultRomajiHenkanMap) {
                            val converter = customRomajiScreenConverter
                            val connection = currentInputConnection
                            if (insertStringEndWithN != insertString) customScreenCandidateResult.value = null
                            _inputString.value = insertStringEndWithN
                            setComposingText(insertStringEndWithN + stringInTail.get(), 1)
                            scope.launch {
                                val result = withTimeoutOrNull(2_000) {
                                    combine(customScreenCandidateResult, inputString) { result, current ->
                                        result to current
                                    }.first { (result, current) ->
                                        current != insertStringEndWithN || result?.first == insertStringEndWithN
                                    }
                                }
                                if (currentInputConnection !== connection ||
                                    customRomajiScreenConverter !== converter ||
                                    inputString.value != insertStringEndWithN || isHenkan.get()) return@launch
                                val candidates = result?.first?.second.orEmpty()
                                if (candidates.isNotEmpty()) {
                                    handleJapaneseModeSpaceKeyWithBunsetsu(mainView, candidates, insertStringEndWithN)
                                }
                            }
                        } else {
                            _inputString.update { insertStringEndWithN }
                            scope.launch {
                                delay(64)
                                val newSuggestionList =
                                    suggestionAdapter?.suggestions ?: emptyList()
                                if (newSuggestionList.isNotEmpty()) {
                                    handleJapaneseModeSpaceKeyWithBunsetsu(
                                        mainView, newSuggestionList, insertString
                                    )
                                }
                            }
                        }
                    }

                    else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString, englishSpace)
                }
            }
        } else {
            if (stringInTail.get().isNotEmpty()) return
            val romajiMode = currentQwertyRomajiModeForSession
            Timber.d("handleSpaceKeyClickInCustomRomaji: $romajiMode")
            if (romajiMode && qwertyEnableZenkakuSpacePreference == true) {
                handleSpaceKeyClick(false, insertString, suggestions, mainView)
            } else {
                setSpaceKeyActionEnglishAndNumberNotEmpty(insertString, englishSpace)
            }
        }
        resetFlagsKeySpace()
    }


    private fun handleForceHalfWidthSpaceOrConvert(
        mainView: MainLayoutBinding,
    ) {
        handleForceSpaceOrConvert(space = " ", mainView = mainView)
    }

    private fun handleForceFullWidthSpaceOrConvert(
        mainView: MainLayoutBinding,
    ) {
        handleForceSpaceOrConvert(space = "　", mainView = mainView)
    }

    private fun handleForceSpaceOrConvert(
        space: String,
        mainView: MainLayoutBinding,
    ) {
        clearZeroQueryAllState(refresh = false)
        if (dispatchDirectSpaceIfNeeded()) {
            resetFlagsKeySpace()
            return
        }
        val insertString = inputString.value
        val suggestions = suggestionAdapter?.suggestions ?: emptyList()

        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
            resetFlagsKeySpace()
            return
        }

        if (insertString.isNotEmpty()) {
            when (currentInputModeForSession) {
                InputMode.ModeJapanese -> {
                    if (suggestions.isNotEmpty()) {
                        handleJapaneseModeSpaceKeyWithBunsetsu(mainView, suggestions, insertString)
                    }
                }

                else -> setSpaceKeyActionEnglishAndNumberNotEmpty(insertString)
            }
        } else {
            if (stringInTail.get().isNotEmpty()) return
            commitText(space, 1)
            _inputString.update { "" }
            if (isHenkan.get()) {
                setSuggestionAdapterSuggestionsOnMain(emptyList())
                isHenkan.set(false)
                henkanPressedWithBunsetsuDetect = false
                suggestionClickNum = 0
                suggestionAdapter?.updateHighlightPosition(-1)
            }
        }
        resetFlagsKeySpace()
    }


    private fun handleJapaneseModeSpaceKey(
        mainView: MainLayoutBinding, suggestions: List<Candidate>, insertString: String
    ) {
        if (cycleFocusedBunsetsuCandidate(delta = 1)) {
            return
        }

        isHenkan.set(true)
        suggestionClickNum += 1
        suggestionClickNum = suggestionClickNum.coerceAtMost(suggestions.size + 1)
        mainView.suggestionRecyclerView.apply {
            smoothScrollToPosition(
                (suggestionClickNum - 1 + 2).coerceAtLeast(0).coerceAtMost(suggestions.size - 1)
            )
            suggestionAdapter?.updateHighlightPosition((suggestionClickNum - 1).coerceAtLeast(0))
        }
        setConvertLetterInJapaneseFromButton(suggestions, true, mainView, insertString)
    }

    private fun handleJapaneseModeSpaceKeyWithBunsetsu(
        mainView: MainLayoutBinding, suggestions: List<Candidate>, insertString: String
    ) {
        scope.launch {
            val activated = activateBunsetsuConversionSession(
                input = insertString,
                mainView = mainView
            )
            if (!activated) {
                handleJapaneseModeSpaceKey(mainView, suggestions, insertString)
            }
        }
    }

    private fun handleNonEmptyInputEnterKey(
        suggestions: List<Candidate>, mainView: MainLayoutBinding, insertString: String,
        fromPhysicalKeyboard: Boolean = false
    ) {
        if (!fromPhysicalKeyboard) flushCustomScreenComposition()
        if (dispatchDirectEnterIfNeeded()) return
        if (commitBunsetsuConversionSession(explicitlySelected = true)) {
            return
        }
        when (val inputMode = currentInputModeForSession) {
            InputMode.ModeJapanese -> {
                if (isHenkan.get()) {
                    handleHenkanModeEnterKey(suggestions, inputMode, insertString)
                } else {
                    finishInputEnterKey()
                    setCursorLeftAfterCommitPair(insertString)
                }
            }

            else -> {
                finishInputEnterKey()
                setCursorLeftAfterCommitPair(insertString)
            }
        }
    }

    private fun setCursorLeftAfterCommitPair(insertString: String) {
        if (appPreference.cursor_move_after_commit_target_pairs_preference.contains(insertString)) {
            moveCursorLeftBySelection()
        }
    }

    private fun moveCursorLeftBySelection() {
        val inputConnection = currentInputConnection ?: return
        scope.launch {
            val start = editorConnectionReadMutex.withLock {
                withContext(Dispatchers.IO) {
                    val req = ExtractedTextRequest().apply {
                        token = 0
                        flags = 0
                    }
                    runCatching {
                        inputConnection.getExtractedText(req, 0)?.selectionStart
                    }.getOrNull()
                }
            }
            if (currentInputConnection !== inputConnection) return@launch

            beginBatchEdit()
            try {
                if (start != null) {
                    if (start > 0) {
                        setSelection(start - 1, start - 1)
                    }
                } else {
                    sendDpadLeftIfPossible()
                }
            } catch (e: Exception) {
                Timber.e(e)
            } finally {
                endBatchEdit()
            }
        }
    }

    private fun handleLeftCursorMoveAction() {
        Timber.d("handleLeftCursorMoveAction: called")
        clearZeroQueryAllState(refresh = false)
        sendDpadLeftIfPossible()
    }

    private fun handleRightCursorMoveAction() {
        clearZeroQueryAllState(refresh = false)
        sendDpadRightIfPossible()
    }

    private fun handleDeleteKeyInHenkan(suggestions: List<Candidate>, insertString: String) {
        suggestionClickNum -= 1
        mainLayoutBinding?.let { mainView ->
            mainView.suggestionRecyclerView.apply {
                smoothScrollToPosition(
                    if (suggestionClickNum == 1) 1 else (suggestionClickNum - 1).coerceAtLeast(
                        0
                    )
                )
                suggestionAdapter?.updateHighlightPosition(
                    if (suggestionClickNum == 1) 1 else (suggestionClickNum - 1).coerceAtLeast(
                        0
                    )
                )
            }
            setConvertLetterInJapaneseFromButton(suggestions, false, mainView, insertString)
        }
    }

    private fun handleHenkanModeEnterKey(
        suggestions: List<Candidate>, currentInputMode: InputMode, insertString: String
    ) {
        if (suggestionClickNum !in suggestions.indices) {
            suggestionClickNum = 0
        }
        setEnterKeyAction(suggestions, currentInputMode, insertString)
    }

    private fun handleEmptyInputEnterKey(mainView: MainLayoutBinding) {
        clearZeroQueryAllState(refresh = false)
        if (dispatchDirectEnterIfNeeded(editorFacing = true)) {
            refreshCandidateStripContent(
                candidatesShown = false,
                resetCandidateTabSelection = candidateTabVisibility == true
            )
            setDrawableToEnterKeyCorrespondingToImeOptions(mainView)
            return
        }
        if (stringInTail.get().isNotEmpty()) {
            finishComposingText()
            setComposingText("", 0)
            stringInTail.set("")
        } else {
            setEnterKeyPress()
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
            isFirstClickHasStringTail = false
        }
        refreshCandidateStripContent(
            candidatesShown = false,
            resetCandidateTabSelection = candidateTabVisibility == true
        )
        setDrawableToEnterKeyCorrespondingToImeOptions(mainView)
    }

    private fun forceNewLine(mainView: MainLayoutBinding) {
        clearZeroQueryAllState(refresh = false)
        if (dispatchDirectEnterIfNeeded()) {
            refreshCandidateStripContent(
                candidatesShown = false,
                resetCandidateTabSelection = candidateTabVisibility == true
            )
            setDrawableToEnterKeyCorrespondingToImeOptions(mainView)
            return
        }
        if (stringInTail.get().isNotEmpty()) {
            finishComposingText()
            setComposingText("", 0)
            stringInTail.set("")
        } else {
            commitText("\n", 1)
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
            isFirstClickHasStringTail = false
        }
        refreshCandidateStripContent(
            candidatesShown = false,
            resetCandidateTabSelection = candidateTabVisibility == true
        )
        setDrawableToEnterKeyCorrespondingToImeOptions(mainView)
    }

    private fun editorEnterDrawable(): Drawable? {
        if (usesEditorEnterPresentation() && editorEnterAction() == EditorEnterAction.Action(EditorInfo.IME_ACTION_PREVIOUS)) {
            return AppCompatResources.getDrawable(this, com.kazumaproject.core.R.drawable.baseline_arrow_left_24)
        }
        return when (editorEnterPresentationType()) {
            InputTypeForIME.TextWebSearchView, InputTypeForIME.TextWebSearchViewFireFox, InputTypeForIME.TextSearchView -> {
                cachedSearchDrawable
            }

            InputTypeForIME.TextMultiLine, InputTypeForIME.TextImeMultiLine, InputTypeForIME.TextShortMessage, InputTypeForIME.TextLongMessage -> {
                cachedReturnDrawable
            }

            InputTypeForIME.TextEmailAddress, InputTypeForIME.TextEmailSubject, InputTypeForIME.TextNextLine -> {
                cachedTabDrawable
            }

            InputTypeForIME.TextDone -> {
                cachedCheckDrawable
            }

            InputTypeForIME.TextSend -> {
                cachedArrowRightDrawable
            }

            else -> {
                cachedArrowRightDrawable
            }
        }
    }

    private fun setDrawableToEnterKeyCorrespondingToImeOptions(mainView: MainLayoutBinding) {
        val currentDrawable = editorEnterDrawable()
    }

    private fun finishInputEnterKey() {
        clearZeroQueryAllState(refresh = false)
        _inputString.update { "" }
        finishComposingText()
        clearSuggestionStateAfterCommit()
        resetFlagsEnterKeyNotHenkan()
    }

    /**
     * Deletes the current selection, one committed character after the cursor, or one
     * grapheme from the right side of the composing text.
     *
     * The editor owns committed-text deletion semantics, so committed text is deleted through
     * KEYCODE_FORWARD_DEL.  The right side of our composing text is held separately in
     * [stringInTail], and must therefore be edited before delegating to the editor.
     */
    private fun handleDeleteAfterCursor() {
        clearZeroQueryAllState(refresh = false)
        if (currentInputConnection == null) return
        if (forwardDeleteCoordinator.hasSelection) {
            forwardDeleteCoordinator.enqueue()
            return
        }
        val beforeInput = inputString.value
        val beforeTail = stringInTail.get()
        if (beforeTail.isNotEmpty()) {
            forwardDeleteCoordinator.cancel()
            deleteAfterCursorInComposition(beforeInput, beforeTail)
            return
        }
        if (beforeInput.isNotEmpty()) return
        forwardDeleteCoordinator.enqueue()
    }

    private fun deleteAfterCursorInComposition(
        beforeInput: String,
        beforeTail: String,
    ) {
        val nextOffset = nextUnicodeGraphemeOffset(beforeTail, 0)
        if (nextOffset <= 0 || nextOffset > beforeTail.length) return

        val deletedText = beforeTail.substring(0, nextOffset)
        val afterTail = beforeTail.substring(nextOffset)
        invalidateZeroQueryForEditorMutation()
        stringInTail.set(afterTail)
        setComposingTextAfterEdit(
            inputString = beforeInput,
            spannableString = SpannableString(beforeInput + afterTail),
            backgroundColor = if (customComposingTextPreference == true) {
                inputCompositionAfterBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.blue)
            } else {
                getColor(com.kazumaproject.core.R.color.blue)
            },
            textColor = if (customComposingTextPreference == true) {
                inputCompositionTextColor
            } else {
                null
            },
        )
        resetFlagsDeleteKey()
        clearSuggestionStateAfterCommit()
        if (beforeInput.isNotEmpty()) {
            requestCandidateRefresh(CandidateShowFlag.Updating, beforeInput)
        }
        createCompositionHistoryEntry(
            beforeInput = beforeInput,
            beforeTail = beforeTail,
            afterInput = beforeInput,
            afterTail = afterTail,
            previewText = deletedText,
        )?.let(::pushEditHistoryEntry)
    }

    private fun performForwardDelete(selectionWasActive: Boolean) {
        invalidateZeroQueryForEditorMutation()
        if (selectionWasActive) {
            clearSelectionActionSession(clearSuggestions = true)
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_FORWARD_DEL)
        resetEditorSelectionSnapshot()
        clearSuggestionStateAfterCommit()
        resetFlagsDeleteKey()
    }

    /**
     * Deletes the last grapheme cluster before the cursor or deletes the current selection.
     * This correctly handles complex emojis and user text selections.
     */
    private fun deleteLastGraphemeOrSelection() {
        invalidateZeroQueryForEditorMutation()
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun handleLeftKeyPress(gestureType: GestureType, insertString: String) {
        Timber.d("called handleLeftKeyPress $insertString ${stringInTail.get()} $gestureType")
        clearZeroQueryAllState(refresh = false)
        if (insertString.isEmpty() && stringInTail.get().isEmpty()) {
            when (gestureType) {
                GestureType.FlickRight -> {
                    sendDpadRightIfPossible()
                }

                GestureType.FlickTop -> {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                }

                GestureType.FlickLeft -> {
                    sendDpadLeftIfPossible()
                }

                GestureType.FlickBottom -> {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                }

                GestureType.Null -> {}
                GestureType.Down -> {}
                GestureType.Tap -> {
                    sendDpadLeftIfPossible()
                }
            }
        } else if (!isHenkan.get()) {
            lastFlickConvertedNextHiragana.set(true)
            isContinuousTapInputEnabled.set(true)
            englishSpaceKeyPressed.set(false)
            suggestionClickNum = 0
            if (insertString.isNotEmpty()) {
                val tail = stringInTail.get()
                val stringBuilder = StringBuilder(tail)
                if (insertString.length == 1) {
                    stringInTail.set(stringBuilder.insert(0, insertString.last()).toString())
                    _inputString.update { "" }
                    setSuggestionAdapterSuggestionsOnMain(emptyList())
                    mainLayoutBinding?.let { mainView ->
                        animateSuggestionImageViewVisibility(
                            mainView.suggestionVisibility, false
                        )
                    }
                } else {
                    stringInTail.set(stringBuilder.insert(0, insertString.last()).toString())
                    _inputString.update { it.dropLast(1) }
                }
            }
        }
    }

    private fun handleLeftLongPress() {
        if (isKeyboardLayoutEditModeActive()) return
        if (moveFocusedBunsetsuSegment(delta = -1)) return
        if (!isHenkan.get()) {
            lastFlickConvertedNextHiragana.set(true)
            isContinuousTapInputEnabled.set(true)
            onLeftKeyLongPressUp.set(false)
            suggestionClickNum = 0
            asyncLeftLongPress()
        }
    }

    private fun handleRightLongPress() {
        if (isKeyboardLayoutEditModeActive()) return
        if (moveFocusedBunsetsuSegment(delta = 1)) return
        if (!isHenkan.get()) {
            onRightKeyLongPressUp.set(false)
            suggestionClickNum = 0
            lastFlickConvertedNextHiragana.set(true)
            isContinuousTapInputEnabled.set(true)
            asyncRightLongPress()
        }
    }

    private fun asyncLeftLongPress() {
        Timber.d("asyncLeftLongPress called")
        if (leftLongPressJob?.isActive == true) return
        leftLongPressJob = scope.launch {
            var finalSuggestionFlag: CandidateShowFlag? = null

            while (isActive && leftCursorKeyLongKeyPressed.get() && !onLeftKeyLongPressUp.get()) {

                val insertString = inputString.value

                Timber.d("asyncLeftLongPress called while loop")

                // tail があり composing が空 → Idle で抜ける
                if (stringInTail.get().isNotEmpty() && insertString.isEmpty()) {
                    finalSuggestionFlag = CandidateShowFlag.Idle
                    handleLeftCursorMoveAction()
                    break
                }

                if (insertString.isNotEmpty()) {
                    updateLeftInputString(insertString)
                } else if (stringInTail.get().isEmpty() && selectMode.value) {
                    extendOrShrinkLeftOneChar()
                } else {
                    handleLeftCursorMoveAction()
                }

                delay(LONG_DELAY_TIME)
            }
            requestCandidateRefresh(
                finalSuggestionFlag
                    ?: if (inputString.value.isEmpty()) CandidateShowFlag.Idle else CandidateShowFlag.Updating
            )
        }
    }

    private fun asyncRightLongPress() {
        if (rightLongPressJob?.isActive == true) return
        rightLongPressJob = scope.launch {
            var finalSuggestionFlag: CandidateShowFlag? = null
            while (isActive && rightCursorKeyLongKeyPressed.get() && !onRightKeyLongPressUp.get()) {
                val insertString = inputString.value
                if (stringInTail.get().isEmpty() && insertString.isNotEmpty()) {
                    finalSuggestionFlag = CandidateShowFlag.Updating
                    break
                }
                actionInRightKeyPressed(insertString)
                delay(LONG_DELAY_TIME)
            }
            requestCandidateRefresh(
                finalSuggestionFlag
                    ?: if (inputString.value.isNotEmpty()) CandidateShowFlag.Updating else CandidateShowFlag.Idle
            )

        }
    }

    private fun updateLeftInputString(insertString: String) {
        if (insertString.isNotEmpty()) {
            if (insertString.length == 1) {
                stringInTail.set(insertString + stringInTail.get())
                _inputString.update { "" }
                setSuggestionAdapterSuggestionsOnMain(emptyList())
                mainLayoutBinding?.let { mainView ->
                    animateSuggestionImageViewVisibility(
                        mainView.suggestionVisibility, false
                    )
                }
            } else {
                stringInTail.set(insertString.last() + stringInTail.get())
                _inputString.update { it.dropLast(1) }
            }
        }
    }

    private fun actionInRightKeyPressed(gestureType: GestureType, insertString: String) {
        clearZeroQueryAllState(refresh = false)
        when {
            insertString.isEmpty() -> {
                if (selectMode.value) {
                    extendOrShrinkSelectionRight()
                } else {
                    handleEmptyInputString(gestureType)
                }
            }

            !isHenkan.get() -> handleNonHenkanTap(insertString)
        }
    }

    private fun actionInRightKeyPressed(insertString: String) {
        clearZeroQueryAllState(refresh = false)
        when {
            insertString.isEmpty() -> handleEmptyInputString()
            !isHenkan.get() -> handleNonHenkan(insertString)
        }
    }

    private fun handleEmptyInputString(gestureType: GestureType) {
        if (stringInTail.get().isEmpty()) {

            when (gestureType) {
                GestureType.FlickRight -> {
                    sendDpadRightIfPossible()
                }

                GestureType.FlickTop -> {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                }

                GestureType.FlickLeft -> {
                    sendDpadRightIfPossible()
                }

                GestureType.FlickBottom -> {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                }

                GestureType.Null -> {}
                GestureType.Down -> {}
                GestureType.Tap -> {
                    sendDpadRightIfPossible()
                }
            }
        } else {
            val dropString = stringInTail.get().first()
            stringInTail.set(stringInTail.get().drop(1))
            _inputString.update { dropString.toString() }
        }
    }

    private fun sendDpadLeftIfPossible() {
        horizontalCursorMoveHandler.move(HorizontalCursorMoveHandler.Direction.Left)
    }

    private fun sendDpadRightIfPossible() {
        horizontalCursorMoveHandler.move(HorizontalCursorMoveHandler.Direction.Right)
    }

    private fun handleEmptyInputString() {
        if (stringInTail.get().isEmpty()) {
            if (selectMode.value) {
                extendOrShrinkSelectionRight()
            } else {
                handleRightCursorMoveAction()
            }
        } else {
            val dropString = stringInTail.get().first()
            stringInTail.set(stringInTail.get().drop(1))
            _inputString.update { dropString.toString() }
        }
    }

    private fun handleNonHenkanTap(insertString: String) {
        englishSpaceKeyPressed.set(false)
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionClickNum = 0
        if (stringInTail.get().isNotEmpty()) {
            _inputString.update { insertString + stringInTail.get().first() }
            stringInTail.set(stringInTail.get().drop(1))
        }
    }

    private fun handleNonHenkan(insertString: String) {
        Timber.d("handleNonHenkan: $insertString ${stringInTail.get()}")
        englishSpaceKeyPressed.set(false)
        lastFlickConvertedNextHiragana.set(true)
        isContinuousTapInputEnabled.set(true)
        suggestionClickNum = 0
        if (stringInTail.get().isNotEmpty()) {
            _inputString.update { insertString + stringInTail.get()[0] }
            stringInTail.set(stringInTail.get().substring(1))
        }
    }

    private fun appendCharToStringBuilder(
        char: Char, insertString: String, stringBuilder: StringBuilder
    ) {
        if (insertString.length == 1) {
            stringBuilder.append(char)
            _inputString.update { stringBuilder.toString() }
        } else {
            try {
                stringBuilder.append(insertString).deleteCharAt(insertString.lastIndex).append(char)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            _inputString.update {
                stringBuilder.toString()
            }
        }
    }

    private fun deleteStringCommon(insertString: String) {
        invalidateZeroQueryForEditorMutation()
        val length = insertString.length
        when {
            length > 1 -> {
                _inputString.update {
                    it.dropLast(1)
                }
            }

            else -> {
                _inputString.update { "" }
                if (stringInTail.get().isEmpty()) setComposingText("", 0)
            }
        }
    }

    private fun setCurrentInputCharacterContinuous(
        char: Char, insertString: String, sb: StringBuilder
    ) {
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        if (insertString.isNotEmpty()) {
            sb.append(insertString).append(char)
            _inputString.update {
                sb.toString()
            }
        } else {
            _inputString.update {
                char.toString()
            }
        }
    }

    private fun setCurrentInputCharacter(
        char: Char, inputForInsert: String, sb: StringBuilder,
    ) {

        if (inputForInsert.isNotEmpty()) {
            val hiraganaAtInsertPosition = inputForInsert.last()
            val nextChar = hiraganaAtInsertPosition.getNextInputChar(char)
            if (nextChar == null) {
                _inputString.update {
                    sb.append(inputForInsert).append(char).toString()
                }
            } else {
                appendCharToStringBuilder(nextChar, inputForInsert, sb)
            }
        } else {
            _inputString.update {
                char.toString()
            }
        }
    }

    private fun currentFlickPreviewContext(source: FlickPreviewSource): FlickPreviewContext {
        val surfaceEligible = when (source) {
            FlickPreviewSource.TENKEY -> false
            FlickPreviewSource.SUMIRE -> qwertyMode.value == TenKeyQWERTYMode.Sumire ||
                    qwertyMode.value == TenKeyQWERTYMode.Number
        }
        return FlickPreviewContext(
            source = source,
            editorSessionId = flickPreviewEditorSessionId,
            settingEnabled = flickEditorPreviewPreference,
            surfaceEligible = surfaceEligible && !isKeyboardLayoutEditModeActive(),
            inputBehaviorUsesComposingText =
                currentInputBehavior == ResolvedInputBehavior.COMPOSING_TEXT,
            safeInputType = currentInputType !in passwordTypes &&
                    currentInputType !in numberTypes &&
                    currentInputType != InputTypeForIME.None,
            isHenkan = isHenkan.get(),
            selectMode = selectMode.value,
            cursorMoveMode = cursorMoveMode.value,
            composingTail = stringInTail.get(),
            hasInputConnection = currentInputConnection != null,
            baseInput = inputString.value,
            isFlickOnlyMode = isFlickOnlyMode == true,
            isContinuousTapInputEnabled = isContinuousTapInputEnabled.get(),
            lastFlickConvertedNextHiragana = lastFlickConvertedNextHiragana.get(),
            previewDelayMillis = flickEditorPreviewDelayMillis.toLong(),
            inputConnectionToken = currentInputConnection,
        )
    }

    private fun applyPendingFlickTextMutation(text: String, isFlick: Boolean): Boolean {
        val mutation = flickInputPreviewCoordinator.consumePendingCommit(text, isFlick)
            ?: return false
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        onDeleteLongPressUp.set(false)
        mutation.effects.continuousTapInputEnabled?.let(isContinuousTapInputEnabled::set)
        mutation.effects.lastFlickConvertedNextHiragana?.let(
            lastFlickConvertedNextHiragana::set
        )
        _inputString.update { mutation.resultInput }
        return true
    }

    private fun sendCharTap(
        charToSend: Char, insertString: String, sb: StringBuilder
    ) {
        if (dispatchDirectTextIfNeeded(charToSend.toString())) return
        if (applyPendingFlickTextMutation(charToSend.toString(), isFlick = false)) return
        when (currentInputType) {
            InputTypeForIME.None,
            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
                -> {
                sendKeyChar(charToSend)
            }

            in passwordTypes -> {
                if (showCandidateInPasswordPreference == true) {
                    sendKeyChar(charToSend)
                } else {
                    setCurrentInputCharacterContinuous(
                        charToSend, insertString, sb
                    )
                }
            }

            else -> {
                if (isFlickOnlyMode == true) {
                    sendCharFlick(charToSend, insertString, sb)
                    isContinuousTapInputEnabled.set(true)
                    lastFlickConvertedNextHiragana.set(true)
                } else {
                    if (isContinuousTapInputEnabled.get() && lastFlickConvertedNextHiragana.get()) {
                        setCurrentInputCharacterContinuous(
                            charToSend, insertString, sb
                        )
                        lastFlickConvertedNextHiragana.set(false)
                    } else {
                        setKeyTouch(
                            charToSend, insertString, sb
                        )
                    }
                }
            }
        }
    }

    private fun sendCharFlick(
        charToSend: Char, insertString: String, sb: StringBuilder
    ) {
        if (dispatchDirectTextIfNeeded(charToSend.toString())) return
        if (applyPendingFlickTextMutation(charToSend.toString(), isFlick = true)) return
        when (currentInputType) {
            InputTypeForIME.None,
            InputTypeForIME.Number,
            InputTypeForIME.NumberDecimal,
            InputTypeForIME.NumberSigned,
            InputTypeForIME.Phone,
            InputTypeForIME.Date,
            InputTypeForIME.Datetime,
            InputTypeForIME.Time,
                -> {
                sendKeyChar(charToSend)
            }

            in passwordTypes -> {
                if (showCandidateInPasswordPreference == true) {
                    sendKeyChar(charToSend)
                } else {
                    setCurrentInputCharacterContinuous(
                        charToSend, insertString, sb
                    )
                }
            }

            else -> {
                setCurrentInputCharacterContinuous(
                    charToSend, insertString, sb
                )
            }
        }
    }

    private fun setStringBuilderForConvertStringInHiragana(
        inputChar: Char, sb: StringBuilder, insertString: String
    ) {
        if (insertString.length == 1) {
            sb.append(inputChar)
            _inputString.update {
                sb.toString()
            }
        } else {
            sb.append(insertString).deleteAt(insertString.length - 1).append(inputChar)
            _inputString.update {
                sb.toString()
            }
        }
    }

    private fun toggleDakutenOnlyForCustomKeyboard() {
        val insertString = inputString.value
        if (insertString.isEmpty()) return

        insertString.last().toggleDakutenWithSeion()?.let { toggled ->
            setStringBuilderForConvertStringInHiragana(
                toggled,
                StringBuilder(),
                insertString
            )
        }
    }

    private fun toggleHandakutenOnlyForCustomKeyboard() {
        val insertString = inputString.value
        if (insertString.isEmpty()) return

        insertString.last().toggleHandakutenWithSeion()?.let { toggled ->
            setStringBuilderForConvertStringInHiragana(
                toggled,
                StringBuilder(),
                insertString
            )
        }
    }

    private fun dakutenSmallLetter(
        sb: StringBuilder, insertString: String, gestureType: GestureType
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)
        if (insertString.isNotEmpty()) {
            val insertPosition = insertString.last()
            insertPosition.let { c ->
                if (c.isHiragana()) {
                    when (gestureType) {
                        GestureType.Tap, GestureType.FlickBottom -> {
                            c.getDakutenSmallChar()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar, sb, insertString
                                )
                            }
                        }

                        GestureType.FlickLeft -> {
                            c.getDakutenFlickLeft()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar, sb, insertString
                                )
                            }
                        }

                        GestureType.FlickRight -> {
                            c.getDakutenFlickRight()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar, sb, insertString
                                )
                            }
                        }

                        GestureType.FlickTop -> {
                            c.getDakutenFlickTop()?.let { dakutenChar ->
                                setStringBuilderForConvertStringInHiragana(
                                    dakutenChar, sb, insertString
                                )
                            }
                        }

                        else -> {}
                    }
                }
            }
        } else {
            if (!onKeyboardSwitchLongPressUp && qwertyMode.value != TenKeyQWERTYMode.Custom && tenkeyShowIMEButtonPreference == true) {
                switchNextKeyboard()
            }
        }
    }

    // 2) 次のモードに切り替える関数
    fun switchNextKeyboard() {
        if (keyboardOrder.isEmpty()) return

        val currentResolution = resolveKeyboardForDisplay(
            requestedType = keyboardOrder.getOrNull(currentKeyboardOrder),
            savedPosition = null,
            source = "switchNextKeyboard.current",
            applyOrientation = false
        )
        currentKeyboardOrder = currentResolution.resolvedIndex ?: 0

        // モジュール演算で自動的に 0 に戻る
        val nextIndex = (currentKeyboardOrder + 1) % keyboardOrder.size
        val nextResolution = resolveKeyboardForDisplay(
            requestedType = keyboardOrder[nextIndex],
            savedPosition = null,
            source = "switchNextKeyboard.next",
            applyOrientation = false
        )
        val nextType = nextResolution.resolvedKeyboard

        when (nextType) {

            KeyboardType.CUSTOM -> {}
        }

        // 統一された showKeyboard 関数を呼び出す
        showKeyboard(nextType, source = "switchNextKeyboard.display")

        currentKeyboardOrder = nextResolution.resolvedIndex ?: 0
        if (enableShowLastShownKeyboardInRestart == true) {
            nextResolution.resolvedIndex?.let { resolvedIndex ->
                appPreference.save_last_used_keyboard_position_preference = resolvedIndex
                lastSavedKeyboardPosition = resolvedIndex
            }
        }

        if (qwertyMode.value == TenKeyQWERTYMode.Number) {
            val type = when (nextType) {
                KeyboardType.CUSTOM -> TenKeyQWERTYMode.Custom
            }
            _tenKeyQWERTYMode.update { type }
        }

        mainLayoutBinding?.let { mainView ->
            setKeyboardSizeSwitchKeyboard(mainView)
        }
    }

    private fun smallBigLetterConversionEnglish(
        sb: StringBuilder, insertString: String,
    ) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)

        if (insertString.isNotEmpty()) {
            val insertPosition = insertString.last()
            insertPosition.let { c ->
                if (!c.isHiragana()) {
                    c.getDakutenSmallChar()?.let { dakutenChar ->
                        setStringBuilderForConvertStringInHiragana(dakutenChar, sb, insertString)
                    }
                }
            }
        } else {
            if (!onKeyboardSwitchLongPressUp && tenkeyShowIMEButtonPreference == true) {
                switchNextKeyboard()
            }
        }
    }

    private fun handleDakutenSmallLetterKey(
        sb: StringBuilder,
        isFlick: Boolean,
        char: Char?,
        insertString: String,
        mainView: MainLayoutBinding,
        gestureType: GestureType
    ) {
    }

    private fun setKeyTouch(
        key: Char, insertString: String, sb: StringBuilder,
    ) {
        suggestionClickNum = 0
        _dakutenPressed.value = false
        englishSpaceKeyPressed.set(false)
        lastFlickConvertedNextHiragana.set(false)
        onDeleteLongPressUp.set(false)
        isContinuousTapInputEnabled.set(false)
        if (isHenkan.get()) {
            clearBunsetsuConversionSession()
            finishComposingText()
            setComposingText("", 0)
            _inputString.update {
                key.toString()
            }
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            suggestionAdapter?.updateHighlightPosition(RecyclerView.NO_POSITION)
            isFirstClickHasStringTail = false
        } else {
            setCurrentInputCharacter(
                key, insertString, sb
            )
        }
    }

    private fun setConvertLetterInJapaneseFromButton(
        suggestions: List<Candidate>,
        isSpaceKey: Boolean,
        mainView: MainLayoutBinding,
        insertString: String
    ) {
        if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
        val listIterator = suggestions.listIterator((suggestionClickNum - 1).coerceAtLeast(0))
        Timber.d("setConvertLetterInJapaneseFromButton ${listIterator.hasPrevious()} ${listIterator.hasNext()} $isSpaceKey")
        when {
            !listIterator.hasPrevious() && isSpaceKey -> {
                setSuggestionComposingText(suggestions, insertString)
                val selectedIndex = (suggestionClickNum - 1).coerceAtLeast(0)
                mainView.suggestionRecyclerView.smoothScrollToPosition(selectedIndex)
                suggestionAdapter?.updateHighlightPosition(selectedIndex)
            }

            !listIterator.hasPrevious() && !isSpaceKey -> {
                setSuggestionComposingText(suggestions, insertString)
                val selectedIndex = (suggestionClickNum - 1).coerceAtLeast(0)
                mainView.suggestionRecyclerView.smoothScrollToPosition(selectedIndex)
                suggestionAdapter?.updateHighlightPosition(selectedIndex)
            }

            listIterator.hasNext() && isSpaceKey -> {
                if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
                setSuggestionComposingText(suggestions, insertString)
            }

            listIterator.hasNext() && !isSpaceKey -> {
                if (suggestionClickNum > suggestions.size) suggestionClickNum = 0
                setSuggestionComposingText(suggestions, insertString)
            }
        }
    }

    private fun setSpaceKeyActionEnglishAndNumberNotEmpty(insertString: String, space: String = " ") {
        Timber.d("setSpaceKeyActionEnglishAndNumberNotEmpty: $insertString ${stringInTail.get()}")
        if (stringInTail.get().isNotEmpty()) {
            val extractedText = getExtractedText(ExtractedTextRequest(), 0)
            val currentCursorPosition = extractedText?.selectionEnd ?: 0
            commitText("$insertString$space$stringInTail", 1)
            val newCursorPosition =
                (currentCursorPosition - stringInTail.get().length + 1).coerceAtLeast(0)
            stringInTail.set("")
            setSelection(newCursorPosition, newCursorPosition)
            Timber.d("setSpaceKeyActionEnglishAndNumberNotEmpty: $currentCursorPosition ${extractedText?.text}")
        } else {
            commitText("$insertString$space", 1)
        }
        _inputString.update {
            ""
        }
        if (isHenkan.get()) {
            setSuggestionAdapterSuggestionsOnMain(emptyList())
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(-1)
        }
    }

    /**
     * Commits the raw composing text and inserts one half-width space at the
     * logical cursor position. The helper replaces the rendered composition
     * with its tail, then inserts the left text and space before that tail.
     */
    private fun handleCommitAndInsertSpace() {
        if (dispatchDirectSpaceIfNeeded()) return

        if (currentInputConnection == null) return
        if (!commitRawTextAndInsertSpace(inputString.value, stringInTail.get())) return

        clearSelectionActionSession(clearSuggestions = false)
        clearSuggestionStateAfterCommit()
        resetFlagsEnterKeyNotHenkan()
        consumePendingZeroQueryAfterCommit()
    }

    private fun setSpaceKeyActionEnglishAndNumberEmpty(isFlick: Boolean) {
        Timber.d("setSpaceKeyActionEnglishAndNumberEmpty: $isFlick ${stringInTail.get()}")
        if (stringInTail.get().isNotEmpty()) {
            commitText(" $stringInTail", 1)
            stringInTail.set("")
        } else {
            mainLayoutBinding?.let { mainView ->
                commitText(
                    resolveEmptySpaceForCurrentMode(
                        isFlick = isFlick,
                        currentInputMode = currentInputModeForSession
                    ),
                    1
                )
            }
        }
        _inputString.update { "" }
        if (isHenkan.get()) {
            setSuggestionAdapterSuggestionsOnMain(emptyList())
            isHenkan.set(false)
            henkanPressedWithBunsetsuDetect = false
            suggestionClickNum = 0
            suggestionAdapter?.updateHighlightPosition(-1)
        }
    }

    private fun resolveEmptySpaceForCurrentMode(
        isFlick: Boolean,
        currentInputMode: InputMode
    ): String {
        return resolveEmptySpaceForCurrentMode(
            isCustomLayoutDirectMode = isCustomLayoutDirectMode,
            customDirectModeSpaceHankakuPreference = customDirectModeSpaceHankakuPreference,
            isFlick = isFlick,
            currentInputMode = currentInputMode
        )
    }

    private var isFirstClickHasStringTail = false

    private fun setSuggestionComposingText(suggestions: List<Candidate>, insertString: String) {
        if (suggestionClickNum == 1 && stringInTail.get().isNotEmpty()) {
            isFirstClickHasStringTail = true
        }

        Timber.d("setSuggestionComposingText: $isFirstClickHasStringTail $suggestionClickNum ${stringInTail.get()}")

        val index = resolveNonLoadingCandidateIndex(
            suggestions = suggestions,
            insertString = insertString,
            requestedIndex = (suggestionClickNum - 1).coerceAtLeast(0)
        ) ?: return
        if (suggestionClickNum <= 0) suggestionClickNum = 1
        suggestionClickNum = index + 1
        suggestionAdapter?.updateHighlightPosition(index)

        val nextSuggestion = suggestions[index]
        val candidateType = nextSuggestion.type.toInt()
        if (nextSuggestion.type == CANDIDATE_TYPE_TEXT_MACRO) {
            stringInTail.set("")
            applyComposingText(
                text = insertString,
                highlightLength = insertString.length,
                backgroundColor = if (customComposingTextPreference == true) {
                    inputConversionBackgroundColor
                        ?: getColor(com.kazumaproject.core.R.color.orange)
                } else {
                    getColor(com.kazumaproject.core.R.color.orange)
                },
                textColor = if (customComposingTextPreference == true) {
                    inputCompositionTextColor
                } else {
                    null
                },
            )
            return
        }
        val suggestionText = nextSuggestion.string
        val suggestionLength = nextSuggestion.length.toInt()
        if (candidateType == 5 || candidateType == 7 || candidateType == 8) {
            val tail = insertString.substring(suggestionLength)
            if (!isFirstClickHasStringTail) stringInTail.set(tail)
        } else if (candidateType == 15) {
            val (correctedReading) = nextSuggestion.string.correctReading()
            val fullText = correctedReading + stringInTail
            applyComposingText(
                text = fullText,
                highlightLength = correctedReading.length,
                backgroundColor = if (customComposingTextPreference == true) {
                    inputConversionBackgroundColor
                        ?: getColor(com.kazumaproject.core.R.color.orange)
                } else {
                    getColor(com.kazumaproject.core.R.color.orange)
                },
                textColor = if (customComposingTextPreference == true) {
                    inputConversionTextColor
                } else {
                    null
                }
            )
            return
        }
        val fullText = suggestionText + stringInTail
        applyComposingText(
            text = fullText,
            highlightLength = suggestionText.length,
            backgroundColor = if (customComposingTextPreference == true) {
                inputConversionBackgroundColor
                    ?: getColor(com.kazumaproject.core.R.color.orange)
            } else {
                getColor(com.kazumaproject.core.R.color.orange)
            },
            textColor = if (customComposingTextPreference == true) {
                inputConversionTextColor
            } else {
                null
            }
        )
    }

    /**
     * ComposingTextを適用する（ハイライト指定あり）
     */
    private fun applyComposingText(
        text: String,
        highlightLength: Int,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null
    ) {
        applyComposingTextRange(
            text = text,
            highlightStart = 0,
            highlightEnd = highlightLength,
            backgroundColor = backgroundColor,
            textColor = textColor
        )
    }

    private fun applyComposingTextRange(
        text: String,
        highlightStart: Int,
        highlightEnd: Int,
        @ColorInt backgroundColor: Int,
        @ColorInt textColor: Int? = null
    ) {
        val spannableString = SpannableString(text)
        val safeStart = highlightStart.coerceIn(0, text.length)
        val safeEnd = highlightEnd.coerceIn(safeStart, text.length)
        val spanFlag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE or Spannable.SPAN_COMPOSING

        spannableString.apply {
            // 背景色
            setSpan(
                BackgroundColorSpan(backgroundColor),
                safeStart,
                safeEnd,
                spanFlag
            )

            // テキスト色
            textColor?.let { color ->
                setSpan(
                    ForegroundColorSpan(color),
                    safeStart,
                    safeEnd,
                    spanFlag
                )
            }

            if (text.isNotEmpty()) {
                setSpan(
                    UnderlineSpan(),
                    0,
                    text.length,
                    spanFlag
                )
            }
        }

        setComposingText(spannableString, 1)
    }

    private fun setNextReturnInputCharacter(insertString: String) {
        _dakutenPressed.value = true
        englishSpaceKeyPressed.set(false)
        val sb = StringBuilder()
        if (insertString.isNotEmpty()) {
            val insertPosition = insertString.last()
            insertPosition.let { c ->
                c.getNextReturnInputChar()?.let { charForReturn ->
                    appendCharToStringBuilder(
                        charForReturn, insertString, sb
                    )
                }
            }
        }
    }

    private fun toggleEmojiKeyboard() {
        _keyboardSymbolViewState.value = SymbolKeyboardState(
            isShown = !_keyboardSymbolViewState.value.isShown
        )
        stringInTail.set("")
        finishComposingText()
        setComposingText("", 0)
        _inputString.update { "" }
    }

    /**
     * Resolves the model in the IME process, but does not load native code here. The returned
     * internal-file path is readable by the private `:zenz` app process because it shares the
     * application UID.
     */
    private suspend fun resolveZenzRuntimeConfig(): ZenzRuntimeConfig? {
        if (!AppVariantConfig.hasZenz) return null

        val modelSource = AppPreference.zenz_model_uri_preference
        val modelPath = zenzModelPathMutex.withLock {
            cachedZenzModelPath
                ?.takeIf { cachedZenzModelSource == modelSource && File(it).isFile }
                ?.let { return@withLock it }

            val resolvedPath = withContext(Dispatchers.IO) {
                resolveZenzModelPath(modelSource)
            }
            cachedZenzModelSource = modelSource
            cachedZenzModelPath = resolvedPath
            resolvedPath
        } ?: return null

        return ZenzRuntimeConfig(
            modelPath = modelPath,
            nCtx = ZENZ_N_CTX,
            nThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4),
        )
    }

    private fun resolveZenzModelPath(customUri: String): String? {
        val defaultAssetFileName = "ggml-model-Q5_K_M.gguf"
        val defaultDestFile = File(filesDir, defaultAssetFileName)

        fun ensureDefaultModelCopied(): File {
            if (!defaultDestFile.exists()) {
                assets.open(defaultAssetFileName).use { input ->
                    FileOutputStream(defaultDestFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return defaultDestFile
        }

        fun copyUriToInternalFile(uriString: String): File {
            val uri = uriString.toUri()
            val dest = File(filesDir, "zenz_custom_model.gguf")
            val temporary = File(filesDir, "zenz_custom_model.gguf.tmp")

            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "openInputStream returned null for uri=$uri" }
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                }
            }
            if (dest.exists() && !dest.delete()) {
                temporary.delete()
                error("Could not replace the existing custom Zenz model.")
            }
            if (!temporary.renameTo(dest)) {
                temporary.delete()
                error("Could not install the custom Zenz model.")
            }
            return dest
        }

        if (customUri.isNotBlank()) {
            try {
                val customFile = copyUriToInternalFile(customUri)
                Timber.d("Zenz custom model prepared: %s", customFile.absolutePath)
                return customFile.absolutePath
            } catch (e: Exception) {
                Timber.e(e, "Failed to prepare custom Zenz model. Falling back to default.")
            }
        }

        return try {
            val defaultFile = ensureDefaultModelCopied()
            Timber.d("Zenz default model prepared: %s", defaultFile.absolutePath)
            defaultFile.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "Failed to prepare the default Zenz model.")
            null
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun getTextBeforeCursor(p0: Int, p1: Int): CharSequence? {
        return currentInputConnection?.getTextBeforeCursor(p0, p1)
    }

    override fun getTextAfterCursor(p0: Int, p1: Int): CharSequence? {
        return currentInputConnection?.getTextAfterCursor(p0, p1)
    }

    override fun getSelectedText(p0: Int): CharSequence? {
        return currentInputConnection?.getSelectedText(p0)
    }

    override fun getCursorCapsMode(p0: Int): Int {
        val connection = currentInputConnection ?: return 0
        return connection.getCursorCapsMode(p0)
    }

    override fun getExtractedText(p0: ExtractedTextRequest?, p1: Int): ExtractedText? {
        return currentInputConnection?.getExtractedText(p0, p1)
    }

    override fun deleteSurroundingText(p0: Int, p1: Int): Boolean {
        val connection = currentInputConnection ?: return false
        flickInputPreviewCoordinator.cancel(restore = true)
        val deleted = connection.deleteSurroundingText(p0, p1)
        if (deleted) editorMutationRevision.advance()
        return deleted
    }

    override fun deleteSurroundingTextInCodePoints(p0: Int, p1: Int): Boolean {
        val connection = currentInputConnection ?: return false
        flickInputPreviewCoordinator.cancel(restore = true)
        val deleted = connection.deleteSurroundingTextInCodePoints(p0, p1)
        if (deleted) editorMutationRevision.advance()
        return deleted
    }

    override fun setComposingText(p0: CharSequence?, p1: Int): Boolean {
        val connection = currentInputConnection ?: return false
        val applied = composingTextArbiter.setCanonical(p0, p1)
        if (applied && qwertyMode.value == TenKeyQWERTYMode.Custom &&
            !isCustomToggleDirectInput() && customToggleRemainingMillis() > 0
        ) {
            customToggleExpectedEditorSelection =
                captureCustomToggleEditorSelection(connection)
        }
        return applied
    }

    override fun setComposingRegion(p0: Int, p1: Int): Boolean {
        val connection = currentInputConnection ?: return false
        flickInputPreviewCoordinator.cancel(restore = true)
        return connection.setComposingRegion(p0, p1)
    }

    override fun finishComposingText(): Boolean {
        if (!customToggleEditInProgress) resetCustomToggleState()
        val connection = currentInputConnection ?: return false
        flickInputPreviewCoordinator.cancel(restore = true)
        val finished = composingTextArbiter.finishCanonical()
        return finished
    }

    override fun commitText(p0: CharSequence?, p1: Int): Boolean {
        if (!customToggleEditInProgress) resetCustomToggleState()
        val connection = currentInputConnection ?: return false
        flickInputPreviewCoordinator.cancel(restore = true)
        val committed = connection.commitText(p0, p1)
        if (committed) {
            editorMutationRevision.advance()
            composingTextArbiter.markCanonicalFinished()
        }
        return committed
    }

    override fun commitCompletion(p0: CompletionInfo?): Boolean {
        val connection = currentInputConnection ?: return false
        flickInputPreviewCoordinator.cancel(restore = true)
        val committed = connection.commitCompletion(p0)
        if (committed) editorMutationRevision.advance()
        return committed
    }

    override fun commitCorrection(p0: CorrectionInfo?): Boolean {
        val connection = currentInputConnection ?: return false
        flickInputPreviewCoordinator.cancel(restore = true)
        val committed = connection.commitCorrection(p0)
        if (committed) editorMutationRevision.advance()
        return committed
    }

    override fun setSelection(p0: Int, p1: Int): Boolean {
        val connection = currentInputConnection ?: return false
        flickInputPreviewCoordinator.cancel(restore = true)
        val changed = connection.setSelection(p0, p1)
        if (changed) editorMutationRevision.advance()
        return changed
    }

    override fun performEditorAction(p0: Int): Boolean {
        val connection = currentInputConnection ?: return false
        return connection.performEditorAction(p0)
    }

    override fun performContextMenuAction(p0: Int): Boolean {
        val connection = currentInputConnection ?: return false
        return connection.performContextMenuAction(p0)
    }

    override fun beginBatchEdit(): Boolean {
        val connection = currentInputConnection ?: return false
        return connection.beginBatchEdit()
    }

    override fun endBatchEdit(): Boolean {
        val connection = currentInputConnection ?: return false
        return connection.endBatchEdit()
    }

    override fun sendKeyEvent(p0: KeyEvent?): Boolean {
        val connection = currentInputConnection ?: return false
        return connection.sendKeyEvent(p0)
    }

    override fun clearMetaKeyStates(p0: Int): Boolean {
        val connection = currentInputConnection ?: return false
        return connection.clearMetaKeyStates(p0)
    }

    override fun reportFullscreenMode(p0: Boolean): Boolean {
        val connection = currentInputConnection ?: return false
        return connection.reportFullscreenMode(p0)
    }

    override fun performPrivateCommand(p0: String?, p1: Bundle?): Boolean {
        val connection = currentInputConnection ?: return false
        return connection.performPrivateCommand(p0, p1)
    }

    override fun requestCursorUpdates(p0: Int): Boolean {
        val connection = currentInputConnection ?: return false
        return connection.requestCursorUpdates(p0)
    }

    override fun getHandler(): Handler? {
        return currentInputConnection?.handler
    }

    override fun closeConnection() {
        val connection = currentInputConnection ?: return
        return connection.closeConnection()
    }

    override fun commitContent(
        inputContent: InputContentInfo, flags: Int, opts: Bundle?
    ): Boolean {
        val connection = currentInputConnection ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            connection.commitContent(inputContent, flags, opts)
        } else {
            false
        }
    }

    override fun onToggled(isEnabled: Boolean) {
        isClipboardHistoryFeatureEnabled = isEnabled
        appPreference.clipboard_history_enable = isEnabled
    }

}

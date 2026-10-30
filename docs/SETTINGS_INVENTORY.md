# 設定画面の棚卸し（2026-10-30。下の「値の固定と設定画面の作り直し」が現在の形。その下は経緯）

## 値の固定と設定画面の作り直し（2026-10-30・2 回目、ユーザー判断）
### 値を固定して設定画面から削除した key
保存値は読まない（`AppPreference` の getter が固定値を返す。setter は無い）。key は `AppPreference.REMOVED_PREFERENCE_KEYS` に入れたので、
古いバックアップに入っていてもインポートで読み飛ばす。

| 固定値 | key（タイトル） | コード側の整理 |
|---|---|---|
| オン | `necookey_two_row_candidate_bar_preference`（2段候補バー） | `isNecookeyTwoRowSurfaceAvailable()` = true |
| オン | `necookey_chip_candidate_bar_preference`（文節チップ） | `isChipBarMode()` = 2段バーの条件だけ、チップ学習の分岐・`FloatingCandidateBand.shouldFloat(chipBarPreference = true)` |
| オン | `necookey_zenz_bunsetsu_gate_preference`（リランカー） | `necookeyRerankEnabled()` = true |
| オン | `lattice_lm_cost_preference`（ラティス LM） | `syncLatticeLmCost` のオフ分岐を削除（IMEService.onCreate で AppPreference を読まなくなった） |
| オン | `learn_dictionary_preference`（学習辞書を使う） | |
| オン | `learn_dictionary_allow_mixed_symbols_numbers_preference`（記号・数字を含む語を学習） | |
| オン | `system_ngram_dictionary_enable_preference`（システム n-gram） | |
| オン | `user_dictionary_preference`（ユーザー辞書を使う） | |
| オン | `delete_key_flick_left_preference`（削除キーの左フリック） | 「左フリックで消す記号」画面は残す |
| オン | `save_last_used_keyboard`（最後に使ったキーボードを表示） | |
| オン | （スペース長押しでカーソル移動） | もともと常時オン・UI なし（前回 `conversion_key_swipe_cursor_move_preference` 削除済み） |
| オフ | `stabilize_candidate_strip_height_preference`（変換候補欄の高さを安定させる） | IMEService の `stabilizeCandidateStripHeightPreference`・`dockedCandidateHeightStabilized`・`updateDockedCandidateContainerHeight(stabilizeInsets)` を削除。浮いた候補帯（e59940e）の経路は常に「高さ固定なし」（`applyDockedCandidateInsets(..., stabilizeHeight = false)`）。スナップショットの項目も削除 |
| オフ | `english_reading_dictionary_enable_preference`（英語読み辞書） | `DictionaryOverrideStore.isOptionalBundledEnabled(ENGLISH_READING)` = false |
| オフ | `conversion_candidates_romaji_enable_preference`（ローマ字変換候補） | |
| オフ | `learn_first_candidate_dictionary_preference`（先頭の変換候補を学習） | |
| オフ | `preserve_dictionary_word_whitespace_preference`（単語の前後の空白を保持） | ユーザー辞書画面のメニューからも削除。登録・編集は常に前後の空白を削る |
| オフ | `space_key_preference`（半角スペースの利用） | `space_hankaku_preference` = false |
| 4 | `n_best_preference`（N-Best） | |
| オン / 2 / 4 | `enable_prediction_search_learn_dictionary_preference` / `learn_prediction_preference` / `learn_dictionary_prediction_candidate_limit_preference`（学習辞書の予測・前方一致の文字数・候補数） | |
| 2 / 4 | `user_dictionary_prefix_match_number` / `user_dictionary_prediction_candidate_limit_preference`（ユーザー辞書の予測） | |
| 0.0 / 14sp | `key_letter_size_preference` / `candidate_letter_size_preference`（キー・変換候補の文字サイズ） | 文字サイズ画面（`KeyCandidateLetterSizeFragment`）を削除。「変換欄の高さ」画面の文字サイズ欄も削除 |
| 100 | `flick_sensitivity_preference`（フリック感度） | |
| オン | `henkan_delete_key_action_preference`（候補ハイライト時の削除キー動作） | |
| default | `default_emoji_skin_tone_preference`（絵文字の既定の肌色） | 記号キーボードで肌色を変えると、その入力の間だけ効く（保存しない） |
| 1 | `candidate_column_preference`（候補の列数） | 「変換欄の高さ」画面の列数ボタンも削除 |

### 設定画面の構成（作り直し後）
ダーク固定（`Theme.Necookey.Settings` = Material3 Dark ＋ `localNightMode = YES`、システムバーは透明・明るいアイコン）。動的カラーはそのまま（Android 12+）。
行は角丸カード（グループの先頭・末尾だけ 20dp、間は 4dp・2dp の隙間、押下の波紋もカードの形の中）、8dp グリッド、
アイコンは Material Symbols Rounded、スイッチは MaterialSwitch、別画面へ進む行だけ右端に矢印。画面遷移は右からの shared axis 風。

```
設定ホーム（上部バーなし。アプリ名＋キーボードが無効／未選択のときだけ小さなお知らせ「オンにする」「切り替える」）
├─ 入力
│   ├─ キーボード（screen_keyboard）
│   │   ├─ 配列 → カスタムキーボード一覧
│   │   ├─ 位置とサイズ → KeyboardSettingFragment
│   │   ├─ 変換欄の高さ → CandidateViewHeightSettingFragment
│   │   ├─ 入力: 確定を元に戻す（undo_enable）/ 読みに戻す（reconversion）/ 左フリックで消す記号 → / 確定後にカーソルを戻す記号 →（説明 = 今の記号）
│   │   └─ 詳細 → 記号キーボードの初期表示（symbol_mode）/ ターミナルなどの入力欄（type_null_input_behavior）
│   └─ 辞書（screen_dictionary）
│       ├─ ユーザー辞書 → / 学習辞書 → / NG ワード →
│       ├─ 候補に出す: 絵文字 / 記号 / 顔文字
│       ├─ 追加の辞書: 人名 / 地名 / Wikipedia
│       └─ 次の言葉の学習: 学習した組み合わせ（説明 = 件数）/ すべて削除
└─ 管理
    ├─ クリップボード（screen_clipboard）: 履歴 → / 変換欄に表示 / タップで削除 / 自動で削除 / 削除までの時間
    ├─ バックアップ（screen_backup）: 書き出す / 読み込む
    └─ その他（screen_other）: フリーズ記録 / ライセンス → / バージョン
```
- 旧「変換」カテゴリ（`screen_conversion`）は無くなった（候補の種類は「辞書」、読みに戻すは「キーボード」へ）。
- 見た目（テーマ）の画面はもともと無い（`theme_mode` などの値は IME が読むが設定 UI は無い）ので、ホームにも置いていない。
- 説明文は値・件数など必要なものだけ（記号キーボードの初期表示・TYPE_NULL は選んだ値、カーソルを戻す記号は今の記号、学習した組み合わせは件数、バージョン）。
- 実装: `res/xml/pref_main.xml`、`values/settings_theme.xml`（テーマ・文字の階層・preferenceTheme）、`values/strings_settings.xml`（短い文言）、
  `layout/settings_pref_*`・`settings_widget_*`（行・見出し・スライダー・スイッチ・矢印）、`SettingsPreferenceAdapter` / `SettingsCardDecoration` / `SettingsRowBackground`（角丸カード）、
  `SettingMainFragment` ＋ `fragment_setting_main.xml` / `item_setting_home_row.xml` / `item_setting_home_section.xml`（ホーム）、`SafeNavigation.settingsNavOptions`（遷移アニメ）。
- 配列エディタ・位置とサイズ画面の中身は今回は触っていない（入口だけ）。

## 経緯（1 回目の整理と最初の棚卸し）

## 2026-10-30 実施した整理（ユーザー判断にもとづく）
| 対象 | 実施内容 |
|---|---|
| `incremental_conversion_session_preference`「セッション型変換エンジン」 | 設定と **INCREMENTAL_SESSION の経路ごと削除**（常に従来経路 = 評価の基準と同じ）。`ConversionBackend`・`CandidateRequestToken.backend`・`KanaKanjiEngine.IncrementalSessionState`/`createIncrementalSessionState`/性能プローブ・`GraphBuilder.SessionState`/`FindPath.SessionState` と `sessionState` 分岐（セッション用の差分探索・ペナルティ／展開キャッシュ経路）・IMEService の DEBUG 計測フックを削除。エンジン内の「1 文字追記キャッシュ」（従来経路の `cachedGraph` / `forwardDpCache`）は残る。インクリメンタル専用テストは削除、`DialectIncrementalParityTest` は従来の追記キャッシュの確認に置き換え |
| タイピングミス補正（`enable_typo_correction_japanese_flick_keyboard_preference`＋しきい値）・修飾キー省略入力（`omission_search_preference`＋しきい値） | **機能ごと削除**（GraphBuilder の誤り補正・省略検索ノード、LOUDSWithTermId の省略／誤り補正検索、EnglishEngine の英語タイプミス候補、エンジン・`KanaKanjiQueryRequest` の引数）。どちらも既定オフ（タイピングミス補正は IMEService で常に false だった）なのでオフ時の変換は同じ |
| `conversion_key_swipe_cursor_move_preference`「スペース長押しでカーソル移動」 | スイッチを削除。スペース長押しのカーソル移動は常時のまま。入力中の変換キー長押しは「文節パターン切り替え → できなければ `handleSpaceLongActionSumire`（カタカナ切り替え等）」に固定（スイッチ既定オフの動作。オン時の「入力中でもカーソル移動」は無くなった） |
| 試し入力欄 `try_input` | 設定ホームから削除 |
| `preserve_dictionary_word_whitespace_preference` | 「辞書と学習」から**ユーザー辞書画面のメニュー（チェック項目）**へ移動（key は同じ） |
| 説明 | `keyboard_screen_preference`（タイトル「位置とサイズ（縦画面）」・横画面は変えられない・チップ方式の候補欄は常に画面幅）、`keyboard_key_letter_size_fragment_preference`（キーの文字にも触れる）、`henkan_delete_key_action_preference`（オン／オフの summary を書き直し。※ もともと summaryOn/Off はあった） |
| 位置とサイズ画面の「TenKey」ボタン | 削除（1 ページしかないプレビューの 0 ページへ移るだけだった） |
| N-Best・列数・読みを戻す | **残した**（コード上まだ効く）: `n_best_preference` は予測候補数と変換の n（`max(nBest, conversionNBest)`）に効く。`candidate_column_preference` は `setMainSuggestionColumn`（候補の RecyclerView の列数）が読む。`reconversion_preference` は未入力時の候補欄のクイック操作「読みを戻す」（`shouldShowReconversionButton` → `CandidateStripContentResolver`）。チップ方式での見え方は要実機確認 |
| 設定のインポート | 機能ごと削除した key（`AppPreference.REMOVED_PREFERENCE_KEYS`）は古いバックアップに入っていても読み飛ばす（その他の未知の key は従来どおり保存されるだけで使われない） |
| フリーズ記録（新規） | 下の「フリーズ調査」の続き。メインスレッドの見張り（`diagnostics/FreezeWatchdog`）と「その他 → アプリについて → フリーズ記録」（表示・コピー・消去） |

以下の表は棚卸し時点（整理前）の内容。削除・移動した項目には「→ 削除済み」等を付けた。

対象: 設定ホーム（`SettingMainFragment`）→ カテゴリ画面（`MainPreferenceFragment` = `res/xml/pref_main.xml` の `screen_*`）と、
そこから開く「位置とサイズ」画面（`KeyboardSettingFragment`）。カスタムキーボード編集・辞書画面などの個別画面の中身は対象外。

判定: **使用中** = IME が読んで効いている / **要見直し** = 効いているが名前・説明・置き場所が古い・ほかと重なる /
**死に設定候補** = 効いていない・常に同じ値・機能削除で意味が無くなった。「参照」は値を読む場所（`ImePreferencesSnapshot` = 入力開始時に IME が読むスナップショット）。

## 設定ホーム
| 項目 | 内容 | 判定 |
|---|---|---|
| キーボードの状態カード | 有効化／選択の確認とシステム設定・入力方法の選択への導線（`isImeEnabled` / `isImeSelected`、onResume・フォーカス復帰で更新） | 使用中 |
| 試し入力欄（TextInputEditText `try_input`） | 設定画面の中で入力を試す | **→ 削除済み**。使用中（※ 下の「フリーズ調査」: IME が同じプロセスなので、ここにフォーカスが入ると IME の起動処理が設定画面と同じメインスレッドで走る） |
| カテゴリタイル 4 つ | キーボード / 変換 / 辞書と学習 / その他 | 使用中 |

## キーボード（`screen_keyboard`）
### 見た目とサイズ
| key | タイトル | 内容 | 判定 |
|---|---|---|---|
| `custom_keyboard_list_preference` | カスタムキーボード | 配列の追加・編集・並べ替え（画面遷移） | 使用中 |
| `keyboard_screen_preference` | 位置とサイズの設定(縦画面) | `KeyboardSettingFragment` へ（下の「位置とサイズ画面」） | 使用中・**要見直し**（縦画面だけ。横画面の値 `keyboard_height_landscape` 等は IME が読むが設定 UI が無い） |
| `keyboard_key_letter_size_fragment_preference` | キー・変換候補の文字サイズ | 文字サイズ画面へ（summary が「変換候補に表示される文字の大きさ」だけでキーの文字サイズに触れていない） | 使用中（説明だけ要見直し） |
| `candidate_view_height_setting_fragment_preference` | 変換欄の高さ | 候補欄の高さ画面へ | 使用中 |

### 入力
| key | タイトル | 内容 | 判定 |
|---|---|---|---|
| `flick_sensitivity_preference` | フリック入力の感度 | フリック判定距離（`syncRuntimeInputPreferences`） | 使用中 |
| `undo_enable_preference` | 元に戻す機能 | 確定の取り消し。summary が「元に戻す機能を無効にする」固定で、コードが on/off で summary を差し替える | 使用中 |
| `space_key_preference` | 半角スペースの利用 | 常に半角スペース（`space_hankaku_preference`） | 使用中 |
| `conversion_key_swipe_cursor_move_preference` | スペース長押しでカーソル移動（**→ 削除済み**） | 09b3320 で「入力中のスペース長押し＝見えている表記で確定してカーソル移動」が常時になったため、**今はスペース長押しには効かない**。効くのは変換キー（`KeyAction.Convert`）の長押しで文節パターン切り替えができなかったとき・カスタム配列の変換キーだけ（IMEService 5970 / 6242 付近） | **要見直し**（タイトルが実際と違う。「変換キー長押しでカーソル移動」に改名するか削除） |
| `delete_key_flick_left_preference` | 削除キーの左フリック | 記号までまとめて削除 | 使用中 |
| `delete_key_flick_left_targets_preference` | 削除対象の記号 | 記号の一覧画面へ | 使用中 |

### 詳細設定（`screen_keyboard_advanced`）
| key | タイトル | 内容 | 判定 |
|---|---|---|---|
| `henkan_delete_key_action_preference` | 候補ハイライト時の削除キー動作 | 候補を選んでいる間の削除キー（`deleteKeyHighLight`、IMEService 14966 付近）。summary 無しで何が起きるか分からない | 使用中（説明を足したい）。チップ方式では候補ハイライトの状態がほぼ無いので効く場面は旧2段/旧文節変換だけ |
| `cursor_move_after_commit_target_pairs_preference` | 確定後にカーソル移動する記号ペア | 「」等の間へ | 使用中 |
| `save_last_used_keyboard` | 最後に使ったキーボードを表示 | 複数のカスタム配列の最後に使ったものを出す | 使用中（配列が 1 つなら意味なし） |
| `type_null_input_behavior_preference` | TYPE_NULL 入力欄の動作 | ターミナル等の TYPE_NULL 欄の扱い | 使用中 |
| `symbol_mode_preference` | 記号キーボードの初期画面 | 絵文字/顔文字/記号/クリップボード | 使用中 |
| `default_emoji_skin_tone_preference` | 絵文字のデフォルト肌色 | | 使用中 |

## 変換（`screen_conversion`）
### 候補
| key | タイトル | 内容 | 判定 |
|---|---|---|---|
| `necookey_two_row_candidate_bar_preference` | 2段の候補バー | 2段バー（チップ方式の前提） | 使用中 |
| `necookey_chip_candidate_bar_preference` | 文節チップ方式 | dependency = 2段バー | 使用中 |
| `candidate_column_preference` | 変換候補の列数 | 候補一覧の列数（＋高さの同期 `setCandidateColumnAndSyncHeight`） | 使用中・**要見直し**（2段バー／チップ方式では上段・下段が横 1 列固定なので、効くのは展開した候補一覧だけのはず。要実機確認） |
| `emoji_candidate_enable_preference` | 絵文字の変換候補 | | 使用中 |
| `symbol_candidate_enable_preference` | 記号の変換候補 | | 使用中 |
| `emoticon_candidate_enable_preference` | 顔文字の変換候補 | | 使用中 |

### SUMIRE-LM（category key は `zenz_category`）
| key | タイトル | 内容 | 判定 |
|---|---|---|---|
| `necookey_zenz_bunsetsu_gate_preference` | リランカーで変換を補正（SUMIRE-LM） | リランカーの on/off。2段バーがオフだと無効化（コードで） | 使用中（key・category に zenz の名前が残っているだけ。キーは変えない方が安全） |
| `lattice_lm_cost_preference` | 単語のつながりで変換を補正（ラティス LM） | 経路探索に bigram LM 補正 | 使用中 |

### 詳細設定（`screen_conversion_advanced`）
| key | タイトル | 内容 | 判定 |
|---|---|---|---|
| `stabilize_candidate_strip_height_preference` | 変換候補欄の高さを安定させる | | 使用中 |
| `n_best_preference` | N-Best | 表示する変換候補の最大数（`nBest`） | 使用中・**要見直し**（チップ方式の再変換は `CHIP_RECONVERT_N_BEST` 固定。旧表示の候補数にだけ効く） |
| `reconversion_preference` | 読みを戻す | 候補が空のとき「読みを戻す」ボタンを出し、直前に確定した読みを PreEdit に戻す（IMEService 11810 付近） | 使用中・要見直し（チップ方式の候補欄に「読みを戻す」ボタンが出る場面があるか要実機確認。確定直後の後退での学習取り消しとは別機能） |
| `conversion_candidates_romaji_enable_preference` | ローマ字変換候補を表示 | QWERTY 削除後、ローマ字入力はカスタム配列のローマ字モードだけ | 使用中（ローマ字配列を使わない人には不要） |
| `english_reading_dictionary_enable_preference` | 英語読み辞書 | 「かー」→「car」。AppPreference ではなく `DictionaryOverrideStore.isOptionalBundledEnabled` が直接読む | 使用中 |
| `incremental_conversion_session_preference` | セッション型変換エンジン（**→ 経路ごと削除済み**） | `live_conversion_preference` が常に false（S3 で設定削除）なので、変換バックエンドはこのスイッチだけで決まる | 使用中・**要見直し**（開発者向け。既定 false のままでよければ隠す候補） |

### タイピングミス補正（**→ 4 項目とも機能ごと削除済み**）
| key | タイトル | 内容 | 判定 |
|---|---|---|---|
| `enable_typo_correction_japanese_flick_keyboard_preference` | フリック（日本語） | | 使用中 |
| `enable_typo_correction_japanese_flick_keyboard_offset_score_preference` | タイピングミス補正のしきい値 | | 使用中（上級者向け） |
| `omission_search_preference` | 修飾キー省略入力 | 濁点・小書きを省いても候補 | 使用中 |
| `omission_search_preference_offset_score_preference` | 修飾キー省略入力のしきい値 | key 名とプロパティ名が違う（`omission_search_offset_score_preference`） | 使用中（上級者向け） |

## 辞書と学習（`screen_dictionary`）
| key | タイトル | 内容 | 判定 |
|---|---|---|---|
| `home_user_dictionary_preference` / `home_learn_dictionary_preference` / `ng_word_list_preference` | ユーザー辞書 / 学習辞書 / NG ワード | 画面遷移 | 使用中 |
| `next_word_learning_view_preference` / `next_word_learning_clear_preference` | 後続語の学習を表示 / すべて削除 | Room（`NextWordRepository`） | 使用中 |
| `ut_person_name_enabled_preference` / `ut_places_enabled_preference` / `ut_wiki_enabled_preference` | 人名 / 地名 / Wikipedia 辞書 | `ImePreferencesSnapshot` で `optionalEnabled(...) && ut_*`（optional 側は常にオン＝実質このスイッチだけ） | 使用中 |
| `learn_dictionary_preference` | 学習辞書 | 学習 on/off（子の dependency 元） | 使用中 |
| `learn_first_candidate_dictionary_preference` | 先頭の変換候補を学習 | | 使用中 |
| `learn_dictionary_allow_mixed_symbols_numbers_preference` | 記号・数字を含む日本語を学習 | | 使用中 |
| `enable_prediction_search_learn_dictionary_preference` | 学習辞書で予測変換 | | 使用中 |
| `learn_prediction_preference` | 予測変換（前方一致の文字数） | | 使用中 |
| `learn_dictionary_prediction_candidate_limit_preference` | 予測候補数 | | 使用中 |
| `system_ngram_dictionary_enable_preference` | システムN-gram辞書を使用 | `SystemNgramRuntime.setEnabled`（AppModule でも読む） | 使用中 |
| `user_dictionary_preference` | ユーザー辞書 | | 使用中 |
| `user_dictionary_prefix_match_number` | 予測変換（ユーザー辞書） | key だけ `_preference` が付かない | 使用中 |
| `user_dictionary_prediction_candidate_limit_preference` | 予測候補数（ユーザー辞書） | | 使用中 |
| `preserve_dictionary_word_whitespace_preference` | 単語の前後の空白を保持する（**→ ユーザー辞書画面のメニューへ移動済み**） | ユーザー辞書の登録画面だけが読む | 使用中（**要見直し**: ユーザー辞書画面の中に置く方が自然） |

## その他（`screen_other`）
| key | タイトル | 内容 | 判定 |
|---|---|---|---|
| `clipboard_history_preference_fragment` | クリップボードの履歴 | 画面遷移 | 使用中 |
| `clipboard_preview_enable_preference` / `clipboard_preview_tap_delete_preference` | 変換欄に表示 / タップで削除 | | 使用中 |
| `clipboard_delete_unpinned_after_hours_preference` / `clipboard_unpinned_retention_hours_preference` | 自動削除 / 削除までの時間 | | 使用中 |
| `pref_backup_export` / `pref_backup_import` | 設定のエクスポート / インポート | `AppPreference.exportAllToJson`。インポート後 recreate | 使用中 |
| `preference_open_source` / `app_version_preference` | OSS ライブラリ / バージョン | | 使用中 |

## まとめ: 消せそう・直したいもの
pref_main.xml に出ている項目は、**値をまったく読んでいない完全な死に設定は見つからなかった**（sumire/QWERTY/zenz/日付の削除で、画面側の項目は一緒に消えている）。
残っているのは「効き方が変わった・名前が古い・重なる」もの:
1. `conversion_key_swipe_cursor_move_preference`「スペース長押しでカーソル移動」— スペース長押しは常にカーソル移動になったので、タイトルが嘘。変換キー長押し用に改名するか削除（削除するなら変換キー長押しの分岐も整理）。**一番の削除候補**。
2. `incremental_conversion_session_preference`「セッション型変換エンジン」— 開発者向けのバックエンド切替。隠す／削除候補（既定 false 固定でよいか判断が要る）。
3. `n_best_preference`「N-Best」・`candidate_column_preference`「列数」— チップ方式（既定）では効く場面が少ない。チップ方式専用にするなら隠す候補（要実機確認）。
4. `reconversion_preference`「読みを戻す」— 候補が空のときのボタン。2段バー／チップ方式でボタンが出る場面があるか要実機確認（出ないなら死に設定）。
5. しきい値 2 つ（タイピングミス・修飾キー省略）— 上級者向け。詳細に残すか隠すか。
6. `preserve_dictionary_word_whitespace_preference` — ユーザー辞書画面へ移す候補。
7. 説明の直し: `keyboard_key_letter_size_fragment_preference`（キーの文字サイズに触れていない）、`henkan_delete_key_action_preference`（summary 無し）、`keyboard_screen_preference`（縦画面だけ）。
8. key に `zenz` が残る（`necookey_zenz_bunsetsu_gate_preference` / `zenz_category`）— 表示上は SUMIRE-LM。キー変更はバックアップ互換が要るので変えない方がよい。

AppPreference 側には、UI を消した後もプロパティ・定数・移行コードが多数残っている（例: `live_conversion_preference` は `get() = false` 固定、
`custom_keyboard_suggestion_preference` は `false` 固定、`SUMIRE_LM_HOMOPHONE_MARGIN_PREFERENCE` は画面なし）。今回の棚卸しは画面に出ている項目が対象で、
AppPreference の未使用プロパティの掃除は別作業。

## 位置とサイズ画面（`KeyboardSettingFragment` = `keyboard_size_setting/KeyboardSizeSettingFragment.kt`）が崩れて見える理由（推定）
実機の画面を見ていないので、コードから考えられる原因:
1. **プレビューが論理サイズ（画面全体の px）で測ってから縮小表示**: `KeyboardPreviewViewport.onMeasure` が子（`keyboard_preview_canvas`）を画面サイズ（例 1080×2400）で
   measure/layout し、`scaleX/scaleY`（pivot 0）で 480dp 以下に縮める。子の実際の境界は親よりずっと大きく、`clipChildren=false` に頼っている。
   ScrollView（`fillViewport`）・ConstraintLayout の親は既定でクリップするので、端末・倍率によってはプレビューの一部が切れる・ハンドルの当たり判定と見た目がずれる。
2. **onMeasure の中で子のハンドルの LayoutParams を書き換える**（`expandHandleHitTargets` → `handle.layoutParams = params`）: measure 中の requestLayout になり、
   初回フレームでハンドルが古い大きさ・位置で描かれる／レイアウトが 2 回走る。`positionHandleInsideCanvas` もレイアウト変更リスナーで translation を動かすので、最初の表示でハンドルがずれやすい。
3. **プレビューの中身が実際のキーボードと違う**: `KeyboardViewPagerAdapter` は常に `KeyboardDefaultLayouts.createFlickKanaTemplateLayout` の既定配列を描くだけで、
   ユーザーのカスタム配列・テーマ／スキン・候補欄（変換欄の高さ）が入っていない。IME の実際の高さは「キーボードの高さ＋候補欄」なのにプレビューはキーボードだけ。
   `setKeyboard` は `runCatching` で包まれていて、失敗すると空白のまま何も出ない。
4. **QWERTY・テンキー時代の名残**: 「TenKey」と書かれたボタン（`tenkey_tooltip_button`、文字列がハードコード）が残り、押すと 1 ページしかない ViewPager2 の 0 ページへ移るだけ。
   `isUserInputEnabled = false` の ViewPager2 に 1 ページだけ、`currentPage` 未使用の警告（480 / 502 行）もある。
5. **縦画面だけ**: `refreshPreviewGeometry` は `targetSize(..., isLandscape = false)` 固定で、横画面で開いても縦の比率で描く。横画面の値（`keyboard_height_landscape` など）は IME が読むが、この画面からは変えられない。
6. `computeCurrentWindowMetrics(activity)` はアクティビティのウィンドウの大きさなので、分割画面・ポップアップ表示では画面全体と違う比率になり、保存値の換算もずれる。

7. **（2026-10-30 追記）文節チップ方式では候補欄が画面幅の浮いた帯**（docs/CHIP_BAR.md「浮いた候補帯」）: この画面の幅・位置・左右の余白はキーボード本体だけに効き、候補欄は常に画面幅。プレビューを直すときはこの違いも反映する。

直すなら: 子を論理サイズでレイアウトする方式をやめて、保存値（dp・%）をプレビューの実寸に比例配分して直接配置する／ハンドルの大きさは onMeasure の外で決める／
プレビューに実際のカスタム配列と候補欄の高さを入れる／TenKey ボタンと ViewPager2 を外す。実機のスクリーンショットがあると原因を絞れる。

## フリーズ調査（アイコンから設定を開くと、ときどきタップに反応しなくなる。2026-10-30、コードを読んだだけ・修正なし）
設定側（`setting_activity/MainActivity.kt`）は、AppPreference の初期化（別スレッド `AppPreferenceInit`）を待つ間は読み込み表示を出し、
設定 XML の読み込み・パッケージ情報・DB 初期化（`SettingDataInitializer`）も IO で行う作りに既になっている。設定画面のコードだけでメインスレッドを
長く止める所は見つからなかった。疑わしいのは「設定画面以外が同じメインスレッドを使っている」こと:

1. **（最有力）IME と設定画面が同じプロセス・同じメインスレッド**: `AndroidManifest.xml` の `.ime_service.IMEService`（72 行）にも MainActivity にも
   `android:process` が無い。IME が動いている間に設定を開く／IME のプロセスが死んでいて設定を開いたときに IME も起き直すと、
   `IMEService.onCreate`（1828 行〜）、`onCreateInputView` → `setupKeyboardView()`（2042 / 2057 行、キーボード全体のインフレートとテーマ適用）、
   `onStartInput` / `onStartInputView`（2079 / 2712 行〜、設定の読み直し・キーボードの組み直し・候補欄の再設定）が設定画面と同じメインスレッドで走る。
   設定ホームには試し入力欄（`fragment_setting_main.xml` の `try_input`）があり、ここにフォーカスが入るたびに IME の起動処理が走る。
   その間のタップは全部待たされる（＝「反応しない」）。元のアプリ（sumire / KazumaProject の IME）も同じ 1 プロセス構成なので同じ症状になる、という報告とも合う。
2. **AppPreference の初期化待ち（`AppPreference.kt` 83〜90 行の `preferences` getter → `initialization.join()`）**: 初期化が終わる前にメインスレッドから
   AppPreference を読むと、別スレッドの初期化（SharedPreferences の読み込み＋移行 3 つ）が終わるまでメインが止まる。設定画面は待たずに済むよう
   になっているが、同じプロセスの `IMEService.onCreate` は `syncLatticeLmCost()`（1837 行、`AppPreference.lattice_lm_cost_preference`）や
   `appPreference.clipboard_history_enable`（1879 行）でメインから直接読む。コールドスタート直後に IME がバインドされると、ここで止まる可能性。
   SharedPreferences のファイルが大きい（学習・配列の JSON などを入れている）ほど長い。
3. **初回だけ**: `Application.onCreate` の `runBlocking(Dispatchers.IO)`（`Application.kt` 31 行、既定のカスタム配列を Room に入れる）。
   メインスレッドで DB 書き込みを待つ。2 回目以降はフラグで飛ぶので「ときどき」には合わない。

修正はしていない（安全で小さい修正が無い）。根本的には IME を別プロセスにする（`android:process`）のが効くが、SharedPreferences・Room・
`AppPreference`（object のシングルトン）をプロセス間で共有している前提が崩れるので大きな作業。次の一手は、固まったときのメインスレッドのスタックを取ること:
`adb shell kill -3 <pid>` か `adb bugreport`（ANR になれば `/data/anr/` に出る）、または debug ビルドで StrictMode（`detectDiskReads` / `penaltyLog`）を
`Application.onCreate` に入れて、IME の起動処理と設定画面のどちらが止めているかを確かめる。

## フリーズ記録（2026-10-30 追加）
- **仕組み**: `Application.onCreate` で `FreezeWatchdog.start` → 優先度最低のデーモンスレッドが 2 秒ごとにメインの Looper へ tick を投げ、
  2 秒以内に処理されなければ「メインスレッドのスタック（最大 60 行）・他スレッドの状態（BLOCKED/RUNNABLE は先頭 6 行）・時刻・アプリのバージョン・
  そのとき生きていた画面（IME 表示中／常駐、前面のアクティビティ名）」を `files/freeze_log.txt` に追記。解除されたら止まっていた時間を 1 行足す（120 秒で打ち切り）。
- **軽さ**: 見張るのは「IME のキーボード表示中（onStartInputView〜onFinishInputView）」「アクティビティが前面（onResume〜onPause）」「IME の onCreate から 10 秒」だけ。
  それ以外は wait で眠る。メインスレッドでは tick の代入と状態フラグの更新だけ。ファイル I/O は別スレッド（FreezeLogWriter）。デバッガ接続中は記録しない。debug / release とも有効。
- **ファイル**: 上限 200KB。超えたら最後の 30 件・100KB 以下まで古い記録から捨てる（`FreezeLogFile.trim`、`FreezeLogFileTest`）。
- **AppPreference の初期化待ち**: メインスレッドが `initialization.join()` で 300ms 以上待ったら、待った時間と呼び出し元のスタックを「メモ」として同じ記録に残す。
- **見方**: 設定 → その他 → アプリについて → **フリーズ記録**。コピー（クリップボードへ）／消去／閉じる。記録が無いときは「記録はありません」。
- **未修正のまま（調査結果）**: `AppPreference.preferences` の `join()` はメインから呼ばれうる（IMEService.onCreate の設定読み込み）。IME の起動処理を非同期化しないと
  安全に外せないので今回は記録だけ。`Application.onCreate` の `runBlocking(Dispatchers.IO)`（既定配列の初回シード）は初回起動だけで、
  「IME が既定配列より先に起動しない」ための意図的な待ちなので変更なし。記録が集まったら、メインのスタックで IME の起動処理か設定画面かを切り分ける。

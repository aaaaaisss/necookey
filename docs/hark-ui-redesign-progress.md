# 設定アプリ UI 再設計 進捗（Hark）

対象: 設定/コンパニオンアプリの Activity 画面（IME のキーボードビューは対象外）。
定型文・Room マイグレーションの作業には触れていない（下記「計画にあったが未実施」参照）。
候補バー（IME 内の候補表示）の見た目・レイアウトはユーザーが作り込んだものなので一切変更しない方針。

## 設計概要

- **ホーム画面**（`SettingMainFragment` / `fragment_setting_main.xml`）
  - 状態カード（Material 3 Filled Card）: 「有効」「現在の入力方法として選択中」を判定して表示。
    - 未有効 → 「有効にする」ボタン（`Settings.ACTION_INPUT_METHOD_SETTINGS`）
    - 有効だが未選択 → 「切り替える」ボタン（`InputMethodManager.showInputMethodPicker()`）
    - 使用中 → チェックアイコンのみ
    - onResume とウィンドウフォーカス復帰時に再判定（ピッカーを閉じた後も更新）。
  - 試し入力欄（TextInputLayout）。
  - カテゴリ一覧（Outlined Card 内の行。アイコン・タイトル・概要・シェブロン）:
    キーボード / 変換・予測 / 辞書・学習 / zenz（zenz 版のみ）/ クリップボード・記号 / バックアップ / アプリについて
- **カテゴリ画面**: 既存の `MainPreferenceFragment` を再利用。`pref_main.xml` の各セクションを
  `PreferenceScreen(key=screen_*)` で包み、ナビゲーション先 `categorySettingsFragment` に
  `PreferenceFragmentCompat.ARG_PREFERENCE_ROOT = screen_*` と `title`（ツールバー表示, label="{title}"）を渡して開く。
  - 設定キー・挙動は変更なし。bind* は存在しない項目では何もしない（findPreference が null）。
  - 画面キー: screen_keyboard（キーボード + 入力・キー操作）, screen_conversion（変換候補の表示 / 予測 / 候補源 / 入力ミス補正）,
    screen_dictionary（辞書の管理: 学習辞書・ユーザー辞書への導線 + 辞書・学習設定 + NGワード）, screen_zenz,
    screen_symbol_clipboard, screen_backup, screen_about
  - 例外: zenz の「文節ゲート」は別画面の「2段候補バー」に依存していたため、XML の android:dependency を外し
    コードで isEnabled を同期（`syncZenzBunsetsuGateEnabled`）。
- **ナビゲーション**: ボトムナビゲーションを廃止（`bottom_nav_menu.xml`, `view_legacy_bottom_navigation.xml` 削除）。
  学習辞書・ユーザー辞書は「辞書・学習」カテゴリから開く。トップレベルはホームのみで、他は共通ツールバーの戻る矢印。
- 起動時に未有効なら `enableKeyboardFragment` へ自動遷移していた処理は、状態カードで代替したため削除（画面自体はナビグラフに残存）。

## 完了

- 上記ホーム/カテゴリ/ナビ変更（5dcbbb0）
- それ以前の整理（本セッション）: 外部辞書画面削除・Mozc UT 常時有効・NEologd/Web 辞書削除（1462643）、振動/キー音削除（fe35162）、
  設定 1 画面化（605cd86）、カスタムキーボード編集 UI 復元（0fb8aac）、テンキー/QWERTY/五十音・物理キーボード・フロート・グライド削除（0b239c2）
- ようこそ画面（`EnableKeyboardFragment` / `fragment_enable_keyboard.xml` / ナビ項目 / 専用文字列）を削除（ec166d1）
- **後続語が表示されないバグ修正**（同コミット）
  - 原因: 9cc8895 で `zero_query_suggestion_preference` を `false` 固定にしたため、確定後の
    `consumePendingZeroQueryAfterCommit()` → `canShowZeroQueryAfterCommit()` が常に false となり、
    確定直後に出すべき後続語（`learnedFollowingWords` → `nextWordRepository.lookup`）が一度も表示されなかった。
    学習（`persistNextWords`）と入力中の 2 段目への混入（`lookupNextWordCandidates`）は配線済みだったが、
    入力中は読みの前方一致が必要なため実用上ほぼ目に入らなかった。
  - 修正: `zero_query_suggestion_preference` を `true` 固定に変更（確定後に後続語＋同梱ゼロクエリ候補を既存の候補バー表示で出す）。
    候補バーの描画・レイアウトは変更なし。単体テストは追加せず（IMEService 依存のため安価でない）。実機未検証。
- **旧「予測変換」設定の削除**（同コミット）
  - 判断: 現在の 2 段目（`setCandidatesNecookeyTwoRow`）は `getSuggestionList()` 経由で旧予測実装をそのまま使っており、
    `ImePreferencesSnapshot` が以下の設定値を読む。そのため実装は削除せず**再利用**し、`AppPreference` の getter を既定値に固定、
    設定 UI（`pref_main.xml`）からのみ項目を削除した。保存済みのユーザー値（例: 予測オフ）で 2 段目が空になる事故も防げる。
  - 削除した UI 項目（すべて既定値固定）: 日本語予測（on）、英語予測（on）、予測開始文字数（3）、システム予測候補数（4）、
    先読み文字数（既定）、予測の積極度（standard）、予測の候補源: システム辞書/システムユーザー辞書/読み補正/Mozc UT/記号・絵文字（すべて on）
  - 残した項目: 英語読み辞書、インクリメンタル変換、記号候補（予測専用ではないため）。他カテゴリの no-op 設定探索は今回スコープ外（未実施）。

## 残り・アイデア

- 実機での見た目確認（スクリーンショット未確認。ビルドはコンパイルまで）
- 各詳細画面（カスタムキーボード一覧/編集、位置とサイズ等）の見た目統一は未着手（既存のまま）
- 通知/ショートカットから `dictionary_fragment_request` で学習辞書を直接開く処理は従来どおり

## 計画にあったが未実施（理由）

- 定型文の削除: 未着手（今回の指示範囲外として後回し）
- Room マイグレーション: 未実施。物理キーボード関連・削除済み機能のテーブルが DB v50 に残存（スキーマ変更はリスクが高く、実機での移行検証ができないため）
- 他の設定カテゴリ画面の no-op 設定の洗い出し: 使用量削減のため未実施
- 後続語の単体テスト追加: 未実施（修正は設定値 1 行、ロジック側は既存 `NextWordPolicyTest` のみ）

## 既知の問題

- androidTest がコンパイル不可（削除したフロート/物理キーボードを参照）
- 実機テスト未実施（ビルドはコンパイル確認まで。見た目・後続語表示とも未確認）
- キーボードサイズのリセット懸念（キーボード種別削除に伴い、保存済みサイズ設定が既定に戻る可能性。未検証）
- release 用 asset 除外フック（生辞書を APK から除外）は CI での release ビルド検証が必要
- エンジンに NEologd/Web 辞書の休眠スロットが残存（辞書は削除済み、コード上の枠のみ）
- 確定後ゼロクエリ候補が常時表示になったため、同梱ゼロクエリ候補も出る（従来既定はオフ）。煩ければ後続語のみに絞る調整が必要

## 履歴

- ec166d1 ようこそ画面削除・後続語バグ修正・旧予測設定 UI 削除。`:app:compileFullStandardDebugKotlin` と `:app:compileFullStandardDebugUnitTestKotlin` 成功（ローカルでは Hilt/KSP の増分キャッシュに削除済み Fragment が残り失敗したため、ksp 出力を消して --no-build-cache で再確認）
- 5dcbbb0 ホーム（状態カード・試し入力・カテゴリ一覧）、カテゴリ別サブ画面、ボトムナビ廃止。`:app:compileFullStandardDebugKotlin` 成功


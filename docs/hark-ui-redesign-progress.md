# 設定アプリ UI 再設計 進捗（Hark）

対象: 設定/コンパニオンアプリの Activity 画面（IME のキーボードビューは対象外）。
定型文・Room マイグレーションの作業には触れていない。

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

## 残り・アイデア

- 実機での見た目確認（スクリーンショット未確認。ビルドはコンパイルまで）
- 各詳細画面（カスタムキーボード一覧/編集、位置とサイズ等）の見た目統一は未着手（既存のまま）
- `EnableKeyboardFragment`（ようこそ画面）は現在どこからも自動では開かれない。不要なら削除可
- 通知/ショートカットから `dictionary_fragment_request` で学習辞書を直接開く処理は従来どおり

## 既知の問題

- なし（未検証項目は上記）

## 履歴

- 5dcbbb0 ホーム（状態カード・試し入力・カテゴリ一覧）、カテゴリ別サブ画面、ボトムナビ廃止。`:app:compileFullStandardDebugKotlin` 成功


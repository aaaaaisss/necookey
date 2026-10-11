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


- 後続語がカスタムキーボード一覧に置き換わる不具合: カスタム配列使用中は `canShowZeroQueryAfterCommit` が `isCustomLayoutPickerShownForCandidateStrip()` で false を返し後続語検索自体をしていなかった。この判定を削除（Resolver は元々後続語を一覧より優先、後続語なしなら従来通り一覧）。`:app:compileFullStandardDebugKotlin` 成功。

- 4b363df 候補欄のキーボード一覧表示を廃止（custom_keyboard_suggestion を false 固定、isCustomLayoutPickerShownForCandidateStrip を常に false）。コンパイル未確認・実機未確認。

- f09a935 Zenzai方式を追加：ConstrainedPathSearch（prefix制約付きViterbi）、KanaKanjiEngine.getConstrainedBestCandidate、Session.queryConstrained、IMEService.runZenzaiDecoding（candidateEvaluate使用、最大3回、zenzaiCarryで制約引継ぎ）。結果は並び替え後の先頭に昇格。発動条件は既存zenz並び替えと同じ。コンパイル成功・実機未確認。後続語のzenz並び替えは未着手。

## Zenzai パイプライン（2026-10-11）

- 5e83d12 native: `candidate_evaluate` が毎回 `llama_kv_cache_clear` していたのをやめ、前回トークン列との共通接頭辞を KV に残して `llama_kv_cache_seq_rm(ctx,0,start,-1)` で末尾だけ再デコード。位置ごとの予測（argmax・対数確率）も保持し再利用。score/生成側の KV 使用時はこのキャッシュを無効化。スレッド数を 2〜4 に制限（以前は 1〜8、既定 4）。既存: score 経路にはプロンプト接頭辞の再利用が元からあった。
- 328526c Zenzai を 1 キー 1 推論に作り替え。ドラフト=sumire 最良経路（前回の FIX 制約が使える＝読みが伸びただけ、なら制約付き再探索結果をドラフトに、推論なし）→ zenz evaluate 1 回（同じ文脈・読み・ドラフトは判定キャッシュで省略）→ FIX:prefix なら sumire が制約付き再探索して採用、制約を次キーへ引継ぎ。新しいキーでジョブをキャンセル（Binder キャンセル→native abort）、sumire ドラフトは即時表示。2 段バーと従来バーの両方で動作。旧 zenz 並び替え（enable_zenz_rerank、最大 3+4 推論）と自信度ゲート（ZenzBunsetsuReselector、文節ごと最大 3 推論）は削除。zenz 設定は ON/OFF（necookey_zenz_bunsetsu_gate_preference）・右文脈・モデル選択・診断のみ。[Z] 表示は zenz が変えた候補に付く。
- 353899e n_ctx=256・スレッド=コア数/2（2〜4）固定、文字数/コンテキスト数/スレッド数設定を削除。
- 0d5398b 後続語: 確定後のゼロクエリ候補上位 10 件を左文脈つきで zenz score 1 回（非同期、入力・次の変換でキャンセル）。空の読みでの採点は学習分布外なので削除はせず、元順位ペナルティ 0.15 nats/位 つきの並べ替えのみ（NextWordZenzReranker）。
- 推論回数: 1 キー入力あたり最大 1 回（判定キャッシュヒット時 0 回）。後続語は確定ごとに 1 回。
- 未確認: 実機（体感速度・FIX の質）。量子化は Q5_K_M のまま。

## 2026-10-11 後半（Zenzai 制約・APK サイズ・mmap・Room 整理）

- 7e33a76 Zenzai の引き継ぎ FIX 制約に読み範囲を持たせた（ZenzaiConstraint）。読みが先頭一致しなくなったら（後退・途中編集・濁点切替）一致している文節境界まで縮め、なければ捨てる。左文脈が変わる・拗音/促音/長音の手前で切れる・読みが空（確定・全消去・モード切替）でも捨てる。制約付き再探索の結果は同じ読み範囲で制約表層を出している場合だけ表示。1 キー 1 推論の設計は変更なし。
- 06b8a91 system/*.dat.zip（約 11MB）と connectionId.dat.zip（約 1.9MB）を app/dictionary-src に移し、圧縮辞書・圧縮連接表の生成入力だけにした。実行時のフォールバックは削除（圧縮版が読めなければログ＋例外）。夜間版 APK 55,334,948 バイト（前 81,283,338）。英語の reading/token.dat.zip は残置（S4 で英語を判断）。
- 182ce82 システム圧縮辞書の詰め込み配列（ラベル・termId・nodeId）と品詞・コスト（short）をメモリマップ領域のビューから直接読む（ヒープ約 15MB 減）。LOUDS のビット列と rank 索引（約 3.5MB）は複写のまま。要実機確認: 変換速度。
- Room v51: 削除済み機能の表（candidate_order_override, ngram_rule, custom_zero_query_entries, system_user_dictionary_entry, physical_keyboard_shortcut_items）を MIGRATION_50_51 で DROP。関連コード・レイアウト・文字列・テストも削除。delete_key_flick（左フリック削除の設定が残存）、ローマ字表・Sumire 特殊キー（S4 まで）、shortcut 表（候補欄コードが型を使用）は残す。
- ユーザー報告（2026-10-11）: 設定アプリ起動時のフリーズは解消。
- 7afcc7e Zenzai の印: [Z]=zenz が直した、[z]=zenz が評価して同意（判定キャッシュ再利用を含む）、印なし=zenz が動かなかった。
- ユーザー辞書・学習の語を zenz から保護: 原因はスコアではなく（posScore はそのまま単語コストで 1 が最強、既定 4000）、Zenzai の FIX 制約付き再探索がユーザー辞書の語（例 杏里）を置き換えていたこと。登録語と読み範囲・出力が一致する文節を消す FIX/引き継ぎ制約は採らない。文節情報のないユーザー辞書・学習候補では zenz を回さない。ユーザー辞書候補は読み完全一致を先頭に。

## 2026-10-11 夕方（絵文字辞書・確定後の絵文字/助詞・英語アセット）
- 14f1a08 絵文字読み辞書を CLDR ja 注釈（annotations + annotationsDerived）＋ sen-ltd/emoji-search-jp（MIT、口語タグ cost 5900）から再生成。漢字キーワードはシステム辞書逆引きで読み付け（同形異音は旧辞書の読みを優先）、読めないものは除外。Emoji 15.1 まで・肌色除外・FE0F 正規化、1,898 字 / 8,836 読み。生成手順 tools/emoji_dict/README.md、MIT 表記 assets/licenses/emoji_dictionary_NOTICE.txt。
- 4f5732f 確定後の後続語スロットに文脈絵文字（≤3）: 確定読みの末尾/先頭キーワードで完全一致検索 → 既存の後続語 zenz 採点 1 回に同梱。先頭 2 件は学習済み後続語のまま。
- 0a2feca 助詞・助動詞・句読点 21 語を同じ採点で上位 3 件だけ表示（句読点直後は出さない）。zenz 無効時は固定順。
- c684eef 英語 reading/token/word を app/dictionary-src/english へ（english.compact.kdict の生成入力のみ）。実行時フォールバック削除。
- 見送り: LOUDS BitSet + rank 索引の mmap 化（java.util.BitSet が 11 ファイル・95 箇所に配線、rank は探索の最内ループで ART の DirectByteBuffer 読みは配列より遅い。182ce82 の端末計測待ち）。
- 要端末確認: 採点候補が最大 10+21+5 件に増えた後続語 rerank の遅延、絵文字/助詞の並び。

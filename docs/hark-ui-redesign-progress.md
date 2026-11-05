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

- 6980d7b 設定UI刷新：4カテゴリ＋詳細設定（入れ子PreferenceScreenはonNavigateToScreenで同Fragmentを開く）、角丸カード装飾、ホーム2列タイル、UT辞書スイッチ（ut_*_enabled_preference）。コンパイル成功・実機未確認。
- 9f8f8cf Zenzai本家照合（WHOLE/PASS/同一制約）・文脈絵文字が出ない問題の修正

## 2026-10-11 夜: 後続語の引き継ぎ・入力停止後の追加判定・絵文字位置・電池

- 後続語の引き継ぎ（`NextWordCarry`）: 確定後に表示した後続語（zenz 並べ替え後）を保持し、そのまま打ち始めたら読みが入力で始まる語を予測欄の先頭に出す（例: 「ありがとう」→「ご」で「ございます」）。読みがかなの候補のみ。入力前の文字列が確定文字列の末尾で終わっているときだけ使い、入力なしのカーソル移動で捨てる。
- Zenzai の入力停止後の追加判定: FIX/WHOLE で直した結果が未確認のまま残るとき、350ms 入力がなければ同じ読みで再判定（最大 2 回、打鍵で即キャンセル）。同じ打鍵内の頭の再判定は native の KV/予測キャッシュで計算済みのため追加実装なし。
- 文脈絵文字の位置: 後続語 1 位 → 絵文字 1 → 後続語 2 位 → 絵文字 2・3 → 残り。
- 電池: 絵文字の事前スコア（空文脈採点、約 150 語）を `files/context_emoji_priors.tsv` に保存し、プロセス再起動のたびに採点し直さない。モデルのパス・サイズ・更新時刻で照合。
- 保留: 学習語優先（native の評価関数に学習語範囲を渡す必要あり）、予測欄の zenz 並べ替え（電池を見て判断）、辞書に無い語の記録、絵文字リスト（ユーザーから受け取り予定）。

## 2026-10-11 夜 2: 推論の回数と AI なしの仕組み

- Zenzai の開始タイミング（`ZenzaiScheduler`）: 毎打鍵すぐではなく、打鍵間隔（指数移動平均）の 1.3 倍（90〜260ms）入力が止まったら開始。打鍵が続けば待機中にキャンセルされ推論は走らない。止まった後は FIX で直した結果を続けて最大 3 回追加判定（合計最大 4 回）。8 打鍵推論していなければ待たずに開始し、その推論は読みが伸びるだけなら中断しない（次の推論が join して FIX 制約を引き継ぐ）。
- native: KV キャッシュを 2 系列に分け（seq 0 = 変換判定、seq 1 = 後続語採点、各 n_ctx 256）、後続語採点が変換判定のキャッシュを消さないようにした。確定直後の打鍵で文脈全体を再計算しない。
- AI なし: 後続語辞書を確定文字列全体で引いて無ければ末尾の語（長い順、ひらがな始まりは 3 字以上）で引き直す（`ZeroQueryBackoff`）。zenz を使わないときは今の文に出てきた語の絵文字をそのまま出す（`ContextEmojiSelector.plainMatches`、句点・改行で区切る）。

## 2026-10-11 夜 3: 助詞を接続コストで・絵文字 8 種・1 段バー

- 助詞・句読点: 確定した最後の語（候補の文節情報から読み・表記、ひらがな確定は読み＝表記）をシステム辞書のラティスで引き、その右文脈 ID から「は が を に で と も の 、 。 ね よ」への接続コスト＋単語コストで並べて上位 3 件（`KanaKanjiEngine.followingWordCosts`、`NextWordExtras.functionWordsByConnection`）。文節なら末尾で終わる最長の語（「行きました」→「た」）。結果は語ごとにキャッシュ。zenz の採点対象から外れ、後続語の head が増える（合計約 20 件）。辞書で引けなければ従来どおり zenz 採点。
- 文脈絵文字: ユーザー指定の 😭 🤔 😘 ✨ 🙏 🙌 💪 😏 の 8 種だけ。1 絵文字に 5〜7 キーワード（group = 絵文字）。キーワード照合の語幹は送り仮名を 1 字残す（悲しい→悲し、泣く→泣く）。
- 1 段バー: 2 段バーと同じ開始タイミング（`ZenzaiScheduler`）と手が止まった後の追加判定。後続語の引き継ぎは 1 段バーでは変換候補の先頭を崩すため入れていない。

## 2026-10-11 夜 4: 打鍵中の裏推論（2 段バー）

- 読みが 3 文字を超えたら、打鍵中も Zenzai を裏で回す（`ZenzaiScheduler.runsInBackground`）。変換欄には出さず、推論中の打鍵では中断しない。終わったら最新の読みで待たずにもう一度走らせる（打鍵ごとのジョブは裏の推論を join して待つ）。FIX の直しは次の推論へ引き継がれる。
- 裏の推論の間に打鍵がなければ、最後の打鍵から「止まった」とみなす時間（`remainingPauseMs`）だけ待ってから表示・仕上げ（PASS まで最大 3 回の追加判定）。待っている間の打鍵ではキャンセルされる。
- 3 文字以下は従来どおり手が止まってから。旧 `FORCE_AFTER`（8 打鍵で強制）は裏推論に置き換えて削除。1 段バーは止まってから判定のまま。
- 追記: 待ち時間を撤廃（ユーザー要望）。短い読みは打鍵ごとにすぐ推論・次の打鍵でキャンセル。裏の推論の間に打鍵がなければ、その結果を待たずに表示して仕上げる。1 段バーも待たずに開始。

## 2026-10-11 夜 5: 文脈絵文字は雰囲気に合う 1 個

- 確定ごとに全キーワード（8 絵文字 × 5〜7 語、48 語）を後続語の zenz 採点 1 回に混ぜる。以前は 1 回 10 語で、各絵文字から 1 語ずつ巡回していたため、合う語がその回の採点に入らず外れていた。
- 絵文字ごとに PMI が一番高いキーワードを代表にして比べ、上位 1 個だけ表示（`MAX_SHOWN = 1`、後続語 1 位の直後）。巡回・hot 枠は削除。左文脈に出た語の緩い閾値（-1.0）は維持。zenz なしの `plainMatches` も 1 個。
- 絵文字キーワードは採点枠（`MAX_SCORED`）の外: 学習済み後続語の head を削らない。採点件数は約 20 → 約 68 件（要端末確認: 確定後の遅延・電池）。
- Zenzai の打鍵中の動作は変更なし（ユーザー判断）。

## 2026-10-11 夜 6: 後続語の文脈絵文字を廃止（ユーザー判断）

- zenz（かな漢字変換モデル）は「次に続く語の確率」しか測れず、雰囲気で絵文字を選ぶのは苦手だったため、後続語スロットの文脈絵文字を全部削除: `ContextEmojiSelector`・`assets/emoji/emoji_mood.tsv`・事前スコア（`files/context_emoji_priors.tsv` は端末に残っても読まない）・`NextWordExtras.placeEmoji`・絵文字キーワードの zenz 採点。後続語の zenz 採点は head＋助詞だけ（最大 25 件）に戻った。
- 絵文字の読み辞書（変換で出る絵文字）はそのまま。
- 後回し: ML-Ask 感情語辞書（BSD-3）で選ぶ案。

## 2026-10-13: sumire-lm-app を main にマージ（dbec2cd）

- 変換エンジンの既定を「Sumire＋SUMIRE-LM リランカー（迷った読みだけ読み込んだ zenz で判断）」に。JINEN / zenz モードは設定「変換エンジン」に残す。
- 案2 ラティス辺コスト（`LatticeLmCost`、単語 bigram 補正、`FindPath.getEdgeCost` に加算）を既定オン。表（5.8MB, `lc_np1m.slc`）はリポジトリに入れず、ビルド時に孤立ブランチ `lm-data` の固定コミット 63732f8 から取り出し sha256 照合（不一致でビルド失敗）してアセット `lattice_lm/` に同梱。IME 起動時に IO スレッドで読み込み（ヒープ約 11MB）。設定「単語のつながりで変換を補正」で無効化、表が無ければ従来どおり。CI ランナーで +1.8ms/文、**実機速度は未確認（遅ければ既定オフに戻す）**。
- はいる→入る 対策（`PosPairPenalty.hairuCost`）: 「わかって入る」→「わかってはいる」、「そう入っても」→「そうは言っても」型。評価で悪化 0。
- Sumire top1（評価用データ）: JWTD held .319→.361、AJIMEE .525→.560、日常文 held .555→.583。詳細は `sumire-lm` ブランチの tools/sumire_lm/HANDOFF.md。

## 2026-10-26: slim ブランチ（機能削除による軽量化、origin/main 66c912f から）

kt 行数（`git ls-files '*.kt'` の合計）: 204,548 → 176,083（**-28,465 行**）。全ファイルの削除行合計は約 41,400 行（追加 131 行）。

| commit | 削除した機能 | kt 行の増減 | 全体の削除行 |
|---|---|---|---|
| 74e76be | キーボードのタッチエフェクト（image_effect: 墨流し/リキッドインク/波紋/スプレー/光の膜/シネマティックウェーブ） | -16,329 | -16,577 |
| d39d10c | Enter 挙動比較用の端末ラボ（enter-parity-lab モジュール、tools/enter_parity） | -40 | -9,318（java/py/データ含む） |
| 72dee5c | ローカルフォント切り替え（local_font） | -1,329 | -1,549 |
| ab71a15 | 自動入力のインライン候補（ime_service/autofill） | -1,738 | -1,945 |
| f1057a0 | テキストマクロ（動的定型文 text_macro）と選択文字アクション。DB 51→52 で `text_macro` 表を破棄 | -1,966 | -2,063 |
| 930b1ee | 定型文（user_template: ツールバー/候補/設定画面）。DB 52→53 で `user_template` 表を破棄 | -2,010 | -2,290 |
| eb75d87 | ゼロクエリ（未入力時・確定直後の後続語/予測の表示、mozc zero_query アセット、後続語の zenz 並べ替え、打ち始めの後続語引き継ぎ NextWordCarry） | -2,379 | -2,423 |
| f73d7d3 | zenz: `:zenz` モジュール、llama.cpp サブモジュール（.gitmodules）、ネイティブビルド（CMake/zenz_bridge.cpp）、prepareZenzModel、ZenzRuntimeService（full フレーバー）、zenz 設定（エンジン選択・モデル選択・診断画面・右文脈） | -1,000 | -3,545 |
| 7321b2d | zenz の実行クライアント（ZenzRuntimeClient）、jinen 変換（JinenConversion）、zenz/jinen モデルのライセンス表示 | -1,674 | -1,686 |

残したもの: NG ワード、カスタムキーボード、記号キーボード、クリップボード履歴、ショートカットツールバー、カスタムローマ字、ユーザー辞書、学習（後続語の学習と入力中の後続語予測を含む）、サイズ設定、変換エンジン（Sumire＋ラティス辺コスト補正＋SUMIRE-LM リランカー）。

- 変換エンジン: 設定「変換エンジン」は削除。保存済みの `zenz`/`jinen` は読み出し時に `sumire_lm` として扱う（`AppPreference.zenz_engine_preference` は常に `sumire_lm`、`zenz_model_uri_preference` は常に空）。SUMIRE-LM リランカーは純 Kotlin で zenz モデルなしで動く（旧 zenz ゲート設定 `necookey_zenz_bunsetsu_gate_preference` が経路のオン/オフ。`HAS_ZENZ` に依存しなくなった）。リランカーが迷っても zenz には回さず、リランカーの 1 位を採用。設定の「zenz」カテゴリは「SUMIRE-LM」に改名し、文節ゲートとラティス補正だけ残した。
- `HAS_ZENZ` は full/lite とも false。full/lite・standard/fdroid のフレーバー構成自体は変えていない。
- ネイティブライブラリ: llama.cpp（libzenz / zenz_bridge）を削除。アプリに残るネイティブコードはなし（要 APK 確認）。
- 未実施: 英語の単語変換・予測（EnglishEngine、英語読み辞書）。かな漢字変換のグラフ構築（english_reading 辞書）・辞書差し替え（dictionary_override の ENGLISH カテゴリ）・英字直接入力と絡み合っていて境界が明確でないため見送り。
- 残課題: zenz 系の死んだコード（Zenzai の FIX 制約・ZenzDiagnosticsStore・ZenzConfidence・`necookey_zenz_*` 設定名など、リランカー経路と共用の部分）は残っている。CI ワークフロー（.github/workflows）の zenz モデルキャッシュ手順・`submodules: recursive` は未整理。androidTest は ImePopupInstrumentedTest（定型文ポップアップ前提）を削除、他は該当部分のみ修正（androidTest はコンパイル未確認）。
- 確認: 各コミットで `:app:compileFullStandardReleaseUnitTestKotlin` が通ることを確認（テスト実行・実機確認は未実施）。

## 2026-11-02: rerank-packed ブランチ（SUMIRE-LM リランカー表 h56、origin/prep-cleanup から）

- 表 h56 = v2 p35（packed1、`v2/` 下位ディレクトリ）+ v3 p25（typed1、`feats.tsv` の `sub dir=v2 pre=v2.` で v2 の特徴量を接頭辞つきで混ぜる混成モデル）。実行時ファイル 100 個、展開後 56.17 MB。評価（held）: JWTD .450 / 日常文 .627 / AJIMEE .680 / 日常文 lenient .929 / 境界 170/179（出荷済み v2 p45: .455/.584/.680/.907/168）。同梱前に梱包ディレクトリを appharness4 で検証し Python の選択と 6409/6409 一致。
- 表はリポジトリに入れず、孤立ブランチ `lm-data` のコミット **56c1823**（`56c1823729aa30c27e8c15364c462e94a0ce440c`）の `sumire_lm_h56.zip`（sha256 `5b040b8f9401adcfefe19daf67e4717468b308fea0b9bc5e8003db68e57e8196`）。`app/build.gradle` の `fetchSumireLmTable` が取得・sha256 照合（不一致でビルド失敗）してアセット `sumire_lm/` に展開。`-PsumireLmTable=false` で同梱しない。CI は zip を sha256 キーで actions/cache し `-PsumireLmTableFile` で渡す。
- 端末: IMEService は初回に アセット `sumire_lm/` を内部 `files/sumire_lm/` へ再帰コピー（v2/ を含む）。**既に内部または外部ファイル領域に `sumire_lm/meta.tsv` がある端末は古い表を使い続ける**（入れ替えるにはアプリのデータ削除か、そのディレクトリを消す）。
- commits: adf6c22（packed1 読み込み）、a37e270（自己 PMI 特徴）、87219f5（typed1 + sub ブロック）、f0b2699（アセットの再帰コピー）、d1363ed（ビルド時取得 + CI）。f0b2699 以降は CI で初コンパイル。
- 6a1380e: SumireLmTyped の KDoc `x[from..]` が Kotlin 1.9.10 でコンパイルエラー（closing bracket expected）になっていたのを文言だけ修正。
- CI（necookey debug APK, workflow_dispatch）: run 41406421357（4e60a4c）は上記 KDoc で ksp 失敗。run 41407292443（6a1380e）は `:app:assembleFullStandardRelease` 成功（Kotlin コンパイル・R8 通過、表 100 ファイル sha256 ok）、ただし後段の JVM ユニットテストで `:custom_keyboard:testDebugUnitTest` が 139 件中 1 件失敗（このブランチは custom_keyboard を変更していない。workflow_dispatch では paths-filter が全モジュールを「変更あり」とする）ため APK アップロードはスキップされた。
- af623fd: CI ワークフローを変更（Kotlin コードは不変）。APK の確認・アップロードはアセンブル成功なら単体テスト失敗時も実行、失敗テスト名を出力し `unit-test-reports` を保存。run 41408746068（af623fd）: assemble 成功、APK アーティファクト `necookey-fullStandard-release-arm64` あり。失敗テストは `custom_keyboard` の `FlickKeyboardRuntimeSettingsTest.sensitivityAndLongPressUpdatesPreserveExistingViewAndController`（`NoSuchFieldException: standardFlickControllers`。テストがリフレクションで読む `FlickKeyboardView` のフィールドが既にない＝テスト側が古い）。

## 2026-11-02 バックアップから sumire_lm を除外（c29d7bf）
- backup_rules.xml / data_extraction_rules.xml がサンプルのままで全データが Google 自動バックアップ対象だった（再インストールでユーザー辞書が戻るのはこのため）。
- h56 表（約56MB）が files/sumire_lm に入ると上限 25MB を超えてアプリ全体のバックアップが止まるので、file/external の sumire_lm/ を cloud-backup と device-transfer から除外。ユーザー辞書・設定は従来どおりバックアップされる。
- CI 未実行（次回ビルドで反映）。

## 2026-11-02 seg-edit ブランチ: チップ列の区切り編集モード（rerank-packed 3a190dd から）
- 実機の不満（区切りの動かし方・足し方が分からない、長押しで切る位置がかな展開と磁石でずれる）に対し、区切りの操作を 1 つにした:
  長押し → 読み全体をかな 1 文字ずつ固定幅のマスに並べ、文字の間のタップで区切りを入れる／消す（磁石なし、文字は動かない）→「完了」。
  タップごとに `ChipBarModel.applySegmentation` で反映（作った塊は RESIZED、自動では割らない）。詳細と実機確認 41〜50 は docs/CHIP_BAR.md「2026-11-02 区切り編集モード」。
- View から削除: くっつける（隙間タップ）、フリックで 1 字ずらす、区切りドラッグ、長押しの切るカーソル（磁石・バブル）。モデル関数はテスト・評価用に残す。
- commit: f582270（コード・テスト・CHIP_BAR.md）。
- 確認: APK ビルド・CI は未実行（指示どおり）。gradle は使わず、変更した純 Kotlin（ChipBar / ChipBarPresenter / ChipStripLayout / ChipStripView を android-all 13 の jar に対して）を kotlinc 2.0.21 で単体コンパイルし、ChipBarTest・ChipBarPresenterTest・ChipStripLayoutTest（46 件）が通ることを確認。IMEService.kt はコンパイル未確認（差分 +16/−12 を目視確認のみ）。

## 2026-11-03 ctx-clean ブランチ: 変な上位候補の根本修正と画面文脈（seg-edit 04d09d4 から）

### A. 半角カナ・稀な 1 字漢字・絵文字が 1 位になる（端末報告: おけ→岡、くさ→日、むりょう→🈚、半角カナが上位）
- 原因: リランカーの候補プール（`SumireLmGate.rerank`、`queryRerankPool` = `getCandidatesWithoutPredictionWithBunsetsu` の全候補から読み全体を覆うもの）に、
  ラティス経路でない候補が混ざっていた: かなそのまま/カタカナ（種別 3/4）・半角カナ（31）はコスト 6000 固定、絵文字/顔文字/記号（11/12/13/21）・
  単漢字辞書（7）・読み一致の単語（2）・読み誤り補正（15）は語コストそのまま。表は経路コスト（N-best、種別 1 だけ: h56 の学習・評価 32 万候補はすべて種別 1、
  プールは常に 50 件で埋まる）で学習していて、`dcost`（1 位とのコスト差、重み -0.87/sd 1.84）が尺度違いの小さいコストに大きな加点をしていた。
  短い読みは N-best が 50 件に満たないのでこれらがプールに入る。半角カナが勝つのは、全角カナ・かなは経路側の同じ文字列（本来のコスト）が先に残るのに、
  半角カナだけは文字列が違うのでコスト 6000 のまま残るため（NFKC で特徴量はカタカナと同じ）。
- 再現（`/workspace/work/nk-ctx/Repro.kt`、h56 表、経路コストは仮の値）: 旧プールの 1 位 おけ→ｵｹ、くさ→日、むりょう→🈚。修正後 おけ／草／無料。
- 修正（72cf830）:
  - `SumireLmGate.isRerankable`: 経路由来の種別（1・28・34・30・31）だけ採点、半角カナを含む表記は採点しない。採点しない候補は Sumire の並びのまま。
  - 稀な 1 字漢字: 2〜3 字の読みで、Sumire 1 位でない 1 字漢字には差し替えない（Sumire 1 位も 1 字漢字で上位 4 件以内なら可: はし→橋/箸）。
  - 一覧の順（KanaKanjiEngine の 3 経路）: かな・カタカナを絵文字/記号/単漢字より前、半角カナは最後。
  - `KanaCandidateOrder`: 2〜4 字のひらがな読みは、かなそのままを上位 3 件に（無ければ差し込む）。リランカーで先頭を替えた後も同じ。1 字の読み（め・て・き）は対象外。
  - 回帰用の短語セット `tools/sumire_lm/regress_short.tsv`（おけ・くさ・むりょう・はし・め・て・き・ひと・けんちょう 等、border 形式）。
- 評価: ユーザー指示（17:06）で held/AJIMEE/境界の再評価は省略（端末で確認）。h56 の評価データは全候補が種別 1・プール 50 件なので、プールの絞り込みでは
  評価値は変わらない見込み（未計測）。

### B. 画面文脈（すべてメモリだけ、保存・送信なし）
- 6313041: `ScreenContextStore`（共有シングルトン）と `ScreenContextBonus`。
  - 層 1: 入力欄のカーソル前後 200 字（変換中の文字列を除く）。リランカーの左文脈も同じ行の最大 40 字に（従来は `getLeftContext` の 8＋入力長から入力分を除いた約 8 字。学習の左文脈は従来のまま）。
  - 層 2: 確定した表記（`persistCompletedLearningSession` で記録、アプリ＝パッケージごと 30 分、最大 200 件）。学習が書ける時だけ記録。
  - 加点: リランカー上位 5 件かつ 1 位との差 1.5 以内の候補で、Sumire の語分割の語（2 字以上、漢字かカタカナを含む、全候補共通の語は除く）が文脈の文字列に
    含まれれば、確定 0.6・入力欄 0.4・画面 0.3（語ごとに最大、候補あたり上限 0.8）。読みは候補自身の変換なので一致は常に満たす（逆引き辞書は不要）。
    1 字の語は数えない（今日 の 日 で くさ→日 を押さないため）。重みはオフライン調整なしの控えめな既定。
  - パスワード欄・`IME_FLAG_NO_PERSONALIZED_LEARNING`・シークレットモードでは記録も使用もしない。IME 終了で消去。
- d5d7e80: 層 3 `ScreenContextAccessibilityService`（任意・既定オフ）。設定「辞書 › 画面の文脈 › 画面に見えている語を変換に使う」をオンにするとユーザー補助の設定画面を開く
  （両方オンで動く）。画面変化を 0.5 秒まとめで前面アプリの見えるノードの text を最大 600 ノード/6000 字読み、3 分だけ置く。パスワード・入力欄・自アプリは読まない。
  `res/xml/screen_context_accessibility.xml`、マニフェストに BIND_ACCESSIBILITY_SERVICE 保護のサービス。ネットワーク権限は追加なし。
- 確認: kotlinc 2.0.21 で純 Kotlin（SumireLmGate・ScreenContext・KanaCandidateOrder・リランカー）とテスト SumireLmGateTest・KanaCandidateOrderTest・ScreenContextTest（16 件）が通る。
  ScreenContextAccessibilityService は android-36 の android.jar＋androidx.preference でコンパイル可。**IMEService.kt・KanaKanjiEngine.kt・MainPreferenceFragment.kt は未コンパイル**（CI で確認）。

## 2026-11-03 クラッシュ記録（ctx-clean）
- 実機のフリーズ記録にメインスレッドが `handleApplicationCrash` 中（＝クラッシュ処理中）のものが出たが、例外本体が残っていなかった。
- `FreezeWatchdog.start` で既定の UncaughtExceptionHandler を包み、例外（スレッド名・cause 連鎖・スタック、12,000 字まで）を `files/freeze_log.txt` に同期で書いてから元のハンドラへ渡すようにした。設定「その他 → フリーズ記録」で見られる。

## 2026-11-04 ctx-clean: 端末の誤変換 4 文（方言の動詞形・語末の伸ばし・う落ち語幹）の根本修正、チップの間のタップでくっつけるを復活

### 原因（アプリの変換経路 SumireNbestDumpTest = 上段 N-best ＋ h56 リランカー、ラティス表 lc_np1m で確認）
1. あしたかってきたげる → 明日買ってきたゲル: 〜てあげる の縮約 たげる が、補助動詞 き（くる 非自立 連用形）に付く形として無く、き＋た＋ゲル に割れていた。
2. よくないのおお → よくないの大: 語末の伸ばしの母音 おお を受けるノードが無く 大（おお）に。伸ばしノードを足すとラティスは正しくなるが、
   リランカー（表は伸ばしの母音を語彙に持たない）が の＋大 を僅差で選んでいた。
3. あいだに(おやつ)かってこようとおもた → 間に親使って雇用と重た: 広島・関西のウ音便過去 おもた（思た）が辞書に無く 重た に。文末が崩れたため
   左の こようと も 雇用と に引っ張られていた（端末の出力から、実際の読みは おやつ を含む。両方確認）。
4. かわいそすぎやろ → カワイソスぎやろ: かわいそ(う) の う 落ちを受けるノードが無く、システム辞書の名詞 カワイソス＋ぎやろ（どちらも辞書にある語）が最安。
   かわいそ ノードを足しても、リランカーはネット語のカタカナ語 カワイソス を語彙に持ち僅差で選んでいた。

### 修正（辞書・表の追加なし、語ごとの例外なし）
- DialectInflectionNodes: 語尾 たげる/たげた/たげて（だ〜 も）を て/で が付く語幹に。語幹に動詞,非自立 の くる/来る・みる・いる 連用形、おく・いく/行く 連用タ接続を追加（買ってき＋たげる、〜てきとる も）。
- 新 UOnbinPastNodes（GraphBuilder 3.055）: 五段ワ行の基本形（自立・非自立）＋た/たら/て = 思うた・言うて、o 段の読み替え こうた（買うた）・ゆうた（言うた）・もろうた、
  縮約 おもた（思た）・もろた（た/たら だけ。おもて＝表 と衝突するので て は作らない）。左 ID = 連用タ接続、コスト = 基本形＋1500（縮約 +1000、読み替え +300）。
  ラティス LM は標準の連用タ接続（思っ）で引く（MozcNodeAttributes.U_ONBIN_PAST）。
- 新 ElongationNodes（3.057）: 直前の字と同じ母音が 2 字以上続き（ー 可）入力末尾か記号の前で終わる並びを、かなのまま 終助詞（ぁぃぅぇぉ, id 405..409）のノードに（コスト 500）。
  SumireLmGate.keepTrailingElongation: リランカー 1 位が語末の伸ばしを崩していて、プールに残す候補があれば、その中の最上位を 1 位に。
- 新 DroppedUStemNodes（3.056）: 読み（3〜6 字、末字 o 段）＋う が 形容動詞語幹（1931）なら、ひらがなのノード（語コスト＋300、後ろに字が続くときだけ）。
  SumireLmGate.keepHiraganaOverKatakanaSwap: リランカー 1 位が Sumire 1 位のひらがなをカタカナにしただけ（畳むと同じ）で差が 1.5 未満なら Sumire 1 位のまま。
- 「短い漢字語の連続（親使って雇用と）への罰」は入れていない（以前の KanjiRunPenalty は JWTD の漢語複合を壊して不採用）。今回は文末の 思た が直ると左も直った。

### 確認（ユーザー指示で held/AJIMEE/境界の全評価は省略、端末で確認）
アプリの変換経路（リランカー後の 1 位）、4 文＋回帰（regress_short 16 語・境界の鍵語 買って/県庁/困難/広島県/三年 の c001–c011・r046–r051）＋確認用 26 文:
| 読み | 修正前 | 修正後 |
|---|---|---|
| あしたかってきたげる | 明日買ってきたゲル | 明日買ってきたげる |
| よくないのおお | よくないの大 | 良くないのおお |
| あいだにかってこようとおもた | 間に買って雇用と重た | 間に買って来ようと思た |
| あいだにおやつかってこようとおもた | 間に親使って雇用と重た | 間におやつ買って来ようと思た |
| かわいそすぎやろ | カワイソスぎやろ | かわいそすぎやろ |
| そうおもた / かってもろた / なんてゆうたん / そうなのおお | そう重た / 買って諸田 / なんてユウタン / そうなの大 | そう思た / 買ってもろた / なんて言うたん / そうなのおお |
- 回帰語・鍵語は全件不変（県庁に行った が 堅調 になるのは修正前から同じ）。変わったのは だめやろ: ダメやろ → だめやろ（カタカナ差し替えの抑止、ひらがなは可）。
  表に出る・新たな世界・遠くへ行く・大きい犬・思った通り・親に使ってもらう 等の確認文は不変。
- ユニットテスト: ColloquialFormNodesTest（新）、SumireLmGateTest（2 件追加）、ChipStripLayoutTest（1 件追加）ほか関連テスト。

### チップの間のタップ = くっつける（普段のモードに復活）
- ChipStripLayout.joinAt（隙間＋両側のチップ端 min(6dp, 幅の 1/5)）を chipAt より先に判定、ChipStripView が Listener.onJoin → IMEService が ChipBarModel.join。軽い振動。隙間 6dp→10dp。長押しの区切り編集モードはそのまま。docs/CHIP_BAR.md「2026-11-04」。
- commits: 2db5a91（変換）、738474f（チップの間のタップ）。
- コンパイル: gradle `:app:testLiteStandardDebugUnitTest`（lite の本体・テストを全部コンパイル。IMEService・ChipStripView 含む）で通過、関連テスト 75 件通過。
  fullStandard フレーバーは CI で確認（変更ファイルはフレーバー共通）。
- リスク: 縮約 おもた 型（読み 2 字以上＋た/たら）は あらた・おおた 等の語と競う（コスト +2500 で辞書語が勝つ想定、確認文 新たな世界 は不変）。
  カタカナ差し替えの抑止で だめやろ → ダメやろ のようなカタカナ表記がひらがなに戻る（差 1.5 未満のとき）。伸ばしの並べ替えは入力末尾が同じ母音 2 字以上のときだけ。

## 2026-11-05 ctx-clean: 端末の誤変換 3 件、長押しの区切りポップアップ、変換ミスの記録、画面の文脈の絞り込み、表を APK の外へ

### 誤変換（アプリの変換経路 SumireNbestDumpTest lite ＋ h56 リランカー、ラティス表 lc_np1m）
| 読み | 修正前 | 修正後 | |
|---|---|---|---|
| もうついた？ | もう一？ | もう着いた？ | 直した |
| もうついた / もうついたよ | もう一 / もう一世 | もう着いた / もう着いたよ | 同じ原因 |
| いわかんでないように | 違和感でないように（Sumire 1 位は 違和感で内容に） | 変えず | 未修正 |
| きれいとはいえんけど | 綺麗とは言えんけど（ハーネスでは正しい） | 変えず | 端末だけの再現（学習・入力途中の状態の可能性） |
| そうはいえん | 総肺炎 | 変えず | 未修正（確認文として記録） |
- 原因と修正: システム辞書に 読み ついた・表記 一 の語（一＝ついたち の読み欠け）があり、文末の 着いた/付いた より安かった。
  `SystemDictionaryErrata.isDropped`（読み→表記の誤り語の表、今は ついた→一 の 1 件）をラティスの辞書引き（GraphBuilder）で外す。辞書の誤りなので副作用なし。
- 違和感出ないように: 1 位の差は でない（ひらがな）／出ない／内容に の語の選び方で、一般的な直し方（副作用なし）が見つからず見送り（ユーザー方針: 無理に 1 文ごとの例外は入れない）。
- そうはいえん → 総肺炎: 文末の はいえん が 肺炎 に取られる。は＋言え＋ん のノードはあり（何も言えんかった・きれいとは言えんけど は正しい）。一般的な直し方が無く見送り。
- 回帰（regress_short 16、regress_device_1104 9、鍵語 c001–c011・r046–r051）は全件不変。確認文 tools/sumire_lm/regress_device_1105.tsv（15 文）。
- commit 901f6aa

### 長押しの区切りポップアップ（docs/CHIP_BAR.md「2026-11-05」）
- チップを長押し → チップ列の上（PopupWindow）に読みのかなを大きく、今の区切りは棒、押した所に一番近い境目にカーソル。押したまま横に動かすと相対移動（26dp ≒ 1 境目、文字は動かない・磁石なし・振動）、
  離すと区切りを入れる／消す（つなぐ）→ 再変換。上へ外す・下へ 48dp 外すと やめる。区切り編集モード（完了ボタン）は削除。タップ = 選択・間のタップ = くっつける は従来どおり。
- commit e49ea96

### 変換ミスの記録・画面の文脈・変換の表（commit 817e526）
- 設定 → 辞書 →「変換ミスの記録」: 既定オン。記録は files/correction_log.tsv（最新 200 件、時刻・読み・最初の変換・確定・直し方 候補/区切り/かな・ctx）。
  一覧・全部コピー（`読み<TAB>最初<TAB>確定<TAB>直し方<TAB>ctx=0/1`、新しい順）・消去。アプリ名は残さない。パスワード・学習しない・シークレット欄では残さない。チップ方式の確定だけが対象。
- 同じ所に「画面の文脈の状態」: ユーザー補助の接続、保持している語の数（画面・入力欄の前後・最近の確定）、直近 100 回の変換で文脈の加点が 1 位を変えた回数（メモリだけ）。
- 画面の文脈（ユーザー補助）を絞った: キーボードが出ている間だけ（IMEService onStartInputView/onFinishInputView → ScreenContextStore）、入力中のアプリ（EditorInfo.packageName）のウィンドウだけ、
  漢字・カタカナ 2 字以上の語だけ残す（元の文字列は捨てる）、隠れたら消去、パスワード管理・銀行・決済・認証アプリ（パッケージ名の語で判定）は読まない。XML は従来どおり 3 種のイベント＋ウィンドウ内容のみ。
- 設定 → 辞書 →「変換の表（リランカー）を読み込む」: SAF で sumire_lm_h56.zip を選ぶ → sha256 照合（違えば警告、meta.tsv があれば読む）→ zip-slip を防いで files/sumire_lm/ に展開（進み具合）→
  `SumireLmTableStore.generation` で IMEService が次の変換で読み直す（再起動不要）。削除ボタン。既に files/sumire_lm/ にある表はそのまま使う。
  zip の URL（公開リポジトリ）: https://raw.githubusercontent.com/aaaaaisss/necookey/56c1823729aa30c27e8c15364c462e94a0ce440c/sumire_lm_h56.zip （54,919,872 バイト）

### CI（commit c4ccd77）
- necookey-debug-apk.yml: `-PsumireLmTable=false`（表の取得・キャッシュの手順を削除）。単体テストは release バリアント（assemble 済みのクラスを再利用）、新しいテストを対象に追加。
  FlickKeyboardRuntimeSettingsTest（削除済みの standardFlickControllers を参照していた）を修正。ほかのワークフローは手動・タグ起動なので変更なし。
- APK の大きさの見込み: 前回 84.6 MB（artifact）→ 表 55 MB が抜けて 約 30 MB。

### 確認
- ローカル: `:app:testLiteStandardDebugUnitTest`（lite の本体・テストを全部コンパイル）で ChipStripLayoutTest 12・CorrectionLogTest 2・SumireLmTableStoreTest 2・ScreenContextTest 6・
  SystemDictionaryErrataTest 2・SumireLmGateTest 9・ChipBarTest 26 通過。`:custom_keyboard:compileReleaseUnitTestKotlin` 通過。fullStandard・release 単体テストは CI で確認。

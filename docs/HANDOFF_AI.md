# necookey 引き継ぎメモ（AI 向け、2026-11-01 全面書き直し）

初めてこのリポジトリを触る AI が、これだけ読めば作業を始められるように書いた。細部は各 docs へのリンク先。

## プロジェクト
- Android の日本語 IME **necookey**（markdownhelperkeyboard のフォーク）。個人利用、GitHub 配布のみ、ネットワーク権限なし。
- 目標: AI モデルなし（zenz/jinen/llama.cpp は削除済み）で「日常で困らない変換」。主な悩みは文節区切りミスとツギハギ。
  同音異義はリランカー（メンバー作成中）と UI（文節チップ）で直しやすく。速度は問題にしない。
- 変換構成: Sumire（辞書＋ラティス）＋ラティス LM 辺コスト表（`lc_np1m.slc`、lm-data ブランチ固定コミット）＋
  SUMIRE-LM リランカー差し替え口（`CandidateReranker`、表は未同梱）。
- ユーザーへの返答はくだけた日本語でよい（アシスタントの一人称は「あたし」）。

## ユーザーの方針（必ず守る）
- **push しない。** すべてローカルコミット。成果物は APK と `git bundle` で渡す。
- 手順は最小に（gradle は重いので回数を減らす。まとめられるタスクは 1 回の gradle に入れる）。
- 設定画面は**ダーク固定**（ライトテーマは作らない）。
- 変換の評価は **lenient を主指標**にする（exact は参考）。かなで出る出力は許容（漢字にならないのは誤りにしない）。
- **辞書に語を足さない**（地域地名辞書なども却下済み。辞書の作り直しは別メンバー、`docs/DICT_HANDOFF.md`）。
- 精度アイデア（`docs/IDEAS_NEXT_ACCURACY.md`）: **2・3 は不採用**。4・5 は実装済み（下記）、1 はリランカー到着後、6 は後で（データ源未定）。
- 設定・UI の作業は**コンパイル確認だけ**（テスト・評価は回さない）。変換・学習を変えたときだけ関係テスト＋評価を 1 回。

## ブランチ（リモートは古い。正はローカル）
| ブランチ | 内容 |
|---|---|
| **prep-cleanup** | **主作業ブランチ**（conv-dialect 1f22e7b から）。10 月後半の変更はすべてここ。 |
| conv-eval | 351c708。境界テスト 179 文の実機設定ハーネスを入れた時点（prep-cleanup に含まれる）。 |
| conv-dialect | 1f22e7b。方言ノード・辞書誤記修正・かな修復など（prep-cleanup に含まれる）。 |
| exp-overnight | ea122ed＋1（81926dd、夜間実験: 切るの hard セット・学習シミュレーション LearningSimTest など）。worktree `../necookey-exp` に checkout 済み。結果のうち採用分（かな修復の既定値）は prep-cleanup に反映済み。 |
| main / slim / ほか | 古い（10 月下旬以前）。main への統合は未定（ユーザーと相談）。 |

## ビルド環境（aarch64 サンドボックス、2 コア・メモリ約 7GB）
- 環境変数: `. /workspace/tools/env.sh`（JDK17・Android SDK・`GRADLE_USER_HOME=/workspace/tools/gradle-home`）。
- aapt2 は arm64 版に差し替え: `/workspace/tools/gradle-home/gradle.properties` の
  `android.aapt2FromMavenOverride=/workspace/tools/sdk/build-tools/36.0.0/aapt2`（Maven の aapt2 は x86_64 で動かない）。
- 実行は `/workspace/work/g/run.sh <ログ名> --no-daemon <タスク…>`（env.sh を読み、`-I /workspace/work/g/init.gradle`、
  `--max-workers=1`、gradle/kotlin デーモン `-Xmx2g`、ログは `/workspace/work/g/<ログ名>.log`、最後に `EXIT <code>`）。
  init.gradle はテスト JVM を 1 本・ヒープ 1536m（`-Dnk.testHeap`）にする。
- **gradle は同時に 1 つだけ**（2 つ動かすとメモリ不足でサンドボックスごと落ちる）。起動前に `pgrep -fa "[G]radleWrapperMain"` で確認。
- **`pkill -f gradle` は禁止**（自分のシェルも死ぬ）。止めるなら PID を指定して kill。
- 長いコマンドは `setsid nohup … &` で裏に回し、ログを見て待つ（シェルの 1 コマンドは 120 秒で切れる）。
- 所要: Kotlin コンパイル約 30 分、APK 組み立てはさらに数分〜十数分。
- KSP/Hilt が消したクラスの古い生成物を拾って失敗したら（例: `Could not get element for …_GeneratedInjector`）、
  `app/build/kspCaches/<variant>` と `app/build/generated/ksp/<variant>` を消して `--no-build-cache` で回す。
- まとめて検証するなら `tools/verify.sh`（コンパイル 4 種＋全 JVM テスト、`VERIFY_EVAL=1` で評価も。重いので普段は使わず、関係テストだけ `--tests` で回す）。
  aarch64 では Robolectric ネイティブ非対応のテストを `tools/robolectric_aarch64_excludes.txt` で除外している。

## 変換評価
- ダンプは JVM テスト `SumireNbestDumpTest`（実機既定の変換経路）。環境変数:
  `NK_LATTICE_COST=<…/eval/lc_np1m.slc>`（`git show origin/lm-data:lc_np1m.slc`、リポジトリに入れない。今は `../eval/lc_np1m.slc` にある）、
  `NK_DUMP_DIR=<出力>`、`NK_LEGACY=0`、`NK_DUMP_SETS="in=tools/eval_border/in.tsv;reg=…/reg.tsv;dia=…/dia.tsv"`。
  `:app:testFullStandardDebugUnitTest --rerun --tests '*SumireNbestDumpTest*'` で回し、採点は `tools/verify.sh` の末尾と同じく
  `tools/eval_border/ev3.py`（`judge` / `row`）で `tools/eval_border/baseline/` と比べる。
- **基準値**（学習なし、2026-10-29 新既定、`tools/eval_border/RESULTS.md`）:
  179 文 exact 118 / lenient 160 / 致命 3 / ツギハギ 1、回帰 reg lenient 97（壊れ 0）、方言 dia lenient 18 / ツギハギ 0。
- 2026-11-01（アイデア 4・5 入り）: 下の「11 月 1 日の作業」に結果。

## 10 月の変更まとめ（prep-cleanup）
- **変換**: 方言かな語ノード（KanaDialectNodes）・方言活用ノード（DialectInflectionNodes）・システム辞書の表記誤り修正（SystemDictionaryErrata）・
  ツギハギかな修復（TsugihagiKanaRepair、既定 1 位・語コスト 3000・読み 4 字）。文節分離なしの旧経路・日付/時刻/年号の特殊候補・
  セッション型変換エンジン・タイピングミス補正・省略入力を削除。リランカー差し替え口（`docs/RERANKER_HOOKUP.md`）。
- **文節チップ候補欄**（`docs/CHIP_BAR.md`）: 上段に文節チップ、下段に予測／選んだチップの候補。タップで候補、長押しで切るカーソル、
  チップ間タップでくっつける、フリックで伸縮、確定ボタン（右端）、スクロール、スペースでチップ選択、浮いた画面幅の候補帯。
- **学習**（`docs/LEARNING.md`）: 「極端に覚えて極端に忘れる」（半減期 3 日・1 回で先頭・ceil 更新）、無視された学習語・後続語の減点、
  確定直後の後退で学習を取り消し、後続語を予測の並べ替えに統合、チップで直した文の再現（リランカーが学習 1 位を上書きしない・区切りの割り直し）。
  11/1 にアイデア 4（打ち直し）・5（区切りの一般化）。
- **設定**（`docs/SETTINGS_INVENTORY.md`）: 多くの値を固定して画面から削除（key は `AppPreference.REMOVED_PREFERENCE_KEYS`、古いバックアップは読み飛ばす）、
  設定画面をダーク固定の Material3 で作り直し（角丸カード・短い文言・ホームは「入力」「管理」）、一覧画面（ユーザー辞書・学習辞書・クリップボード・NG ワード・記号・配列・ライセンス）も同じ見た目に。
- **フリーズ記録**: `FreezeWatchdog`（メインスレッドが 2 秒止まったらスタックを `files/freeze_log.txt` へ）、設定 → その他 → フリーズ記録で見る。
- 掃除: 未使用文字列 49 個削除など。

## 11 月 1 日の作業
- 409e9fd test: 削除した設定・機能を参照していた古いテストを整理。ユニットテストのコンパイルは成功。
  androidTest（slim 以降コンパイルしていなかった）は、削除済み機能を使う端末テスト 8 ファイル＋テスト 2 件を削除して直した（端末では未実行）。
- 5cfe565 learning: アイデア 4（`RetypeCorrection`）・5（`LearnedBoundaries`）。新テスト＋既存のチップ・学習テストは通過。
  評価（学習なし）は基準と同じ: 179 118/160/3/1、reg 97、dia 18/0、壊れ 0（どちらも学習状態が無いと何もしないため）。
- docs: LEARNING.md（4・5 の仕組み）、この HANDOFF の書き直し、SETTINGS_INVENTORY.md の最終確認（フリーズ記録の場所を新しい構成に、旧節に注記）。
- 成果物: `necookey-prep-cleanup-<HEAD>.apk`（:app:assembleFullStandardDebug）と `necookey-all-<HEAD>.bundle`（`git bundle --all`）をプロジェクト直下に置いた。

## 保留・次にやること
1. **実機確認**（ユーザーが APK で）:
   - ダーク設定画面全体の見た目、一覧画面の検索欄の高さ、学習辞書のチップの端（切れていないか）、ライセンス WebView の白いちらつき、
     フリーズ記録（実際に止まったときに記録されるか）、`docs/CHIP_BAR.md` の実機確認リスト（13 は設定が固定オンになったので不要）。
   - 学習のアイデア 4・5（`docs/LEARNING.md`「実機で見る点」）。
2. **リランカー**: メンバーから表が届いたら `docs/RERANKER_HOOKUP.md` の手順で差し込み・評価 → アイデア 1（端末上のオンライン学習）。
3. **辞書の作り直し**: 別メンバー担当（`docs/DICT_HANDOFF.md`）。こちらでは辞書に語を足さない。
4. アイデア 6（かな書き判定器）は後で（データ源を別途検討。口語コーパス生成＝アイデア 2 は不採用なので別の方法で）。
5. 既知の制約: `docs/CHIP_BAR.md`「未対応・既知の制約」、`docs/LEARNING.md` の「残り」「既知の制約」。

## 注意
- CI（`.github/workflows/necookey-debug-apk.yml` など）は push で動くが、push しない方針なので使っていない。APK はローカルで組み立てる。
- 実験は別ブランチ。安定版を壊さない。削除・大きな変更はユーザーに確認してから。
- `AppPreference` の固定値の getter に setter は無い。テストで値を代入しない。
- 評価用のラティス表・APK・bundle はリポジトリに入れない。

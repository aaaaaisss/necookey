# necookey 引き継ぎメモ（2026-10-26）

## プロジェクト
- Android 日本語IME necookey（markdownhelperkeyboard のフォーク）。個人利用、GitHub配布のみ、ネットワーク権限なし。
- 目標：AIなし（zenz/jinen不使用）で日常変換を最良に。誤変換・文節区切りミス・ありえない変換を減らす。同音異義語はUIで直しやすく。速度は問題にしない。
- 変換構成：Sumire（辞書＋ラティス）＋ラティスLM辺コスト表（lm-data ブランチ固定コミット）＋SUMIRE-LM リランカー（純Kotlin）。
- 返答はくだけた日本語（広島・関西弁OK）でOK。

## ブランチ
- main：安定版。変更はコミット→push→ docs/hark-ui-redesign-progress.md に追記（コミットハッシュ付き）。
- slim：機能削減ブランチ（main 66c912f ベース）。CI成功（run 40239568367）。実機確認後に main へマージ予定。
  - 削除済み：image_effect（タッチエフェクト）、enter-parity-lab、フォント差し替え、オートフィル、テキストマクロ（DB 51→52）、ユーザーテンプレート（DB 52→53）、入力前予測 zeroquery（NextWordCarry も消えた＝確定後の次単語を打ち始めで上位にする挙動が無くなった可能性、要実機確認）、zenz/jinen/llama.cpp/ネイティブビルド。Kotlin 204,548→176,083 行。
  - 未完：英語変換は EnglishEngine がかな漢字グラフ構築・辞書上書きに絡むため見送り。リランカー経路に残る zenz の死にコード（Zenzai FIX 制約、ZenzDiagnosticsStore、ZenzConfidence、necookey_zenz_* 設定名）。他ワークフロー（android.yml, *-regression.yml）に zenz キャッシュ手順が残る。androidTest は未コンパイル。
- homophone-ui：main＋3コミット（同音異義語UI、リランカーマージン表示）。未マージ。
- inline-ui（063c0a6）：保留、要相談。sumire-lm：リランカー学習（tools/sumire_lm/HANDOFF.md）。

## 残す機能（ユーザー決定）
NGワード、キーボード自作・編集（メイン利用）、記号キーボード、クリップボード履歴、ショートカットツールバー、カスタムローマ字、ユーザー辞書、学習、サイズ設定、変換エンジン。

## 次にやること
1. slim を実機確認 → main マージ。
2. zenz 死にコードと残りワークフローの zenz 手順を掃除。
3. IMEService.kt（約2万行）を入力・変換・候補表示などに段階分割。変換エンジンを独立モジュール化。
4. カスタムキーボード作り直し：ドーナツフリック、フリック長押し、トグル入力、長押し入力は削除OK。使いにくい点をユーザーに聞いてから設計。
5. キーボードサイズ・候補欄高さ設定の簡素化。
6. クリップボード履歴：入力中キーボードから過去履歴へ簡単にアクセスできるUI。
7. 変換精度（11月）：境界テスト179文（lenient .866、致命7件）。まずテスト読みの誤記と評価ハーネス対実機の差を確認（u015 は読み誤記だった、b014「痛く注がない」は実機で出ない）。その後：リランカーありで再評価、方言否定（ん/んかった/もうた/んさい）のラティス節点、格フレーム特徴（名詞＋助詞＋動詞）、表サイズ 10〜60MB の刈り込み曲線、訂正→正しいユーザー辞書登録で次回上位に。地域地名辞書の追加は不可（ユーザー却下）。

## ルール・注意
- 実験は別ブランチ。安定版を壊さない。削除は確認してから。
- AIコード（zenz/jinen/llama.cpp）は改変しない方針だった→今は slim で削除済み。
- `pkill -f gradle` は禁止（自分のシェルが死ぬ）。KSP が古い生成物を復元したら app/build/kspCaches と generated/ksp を消して --no-build-cache。
- ローカルビルドは重い。APK は CI（necookey-debug-apk.yml、main/slim 等への push で起動）を使う。

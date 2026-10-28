# リランカー差し替え・評価手順（2026-10-28、prep-cleanup）

11 月に完成予定のリランカー（メンバー担当）を入れてすぐ評価・実機確認するためのメモ。本体はここでは作らない。

## 経路（現状）

```
IMEService.runSumireLmStep（読み 2〜32 字・ひらがな＋記号のみ・設定 necookey_zenz_bunsetsu_gate_preference=ON のとき）
  └ session.queryRerankPool(request, SumireLmGate.POOL_SIZE=50)   … 文節分離・予測なしの全読み候補 50 件＋語分割
  └ SumireLmGate.rerank(reranker, input, leftContext, pool, segmentsOf)
       ├ 読み全体を覆う候補だけ・重複なし・Sumire 順で最大 50 件（先頭がユーザー辞書候補なら何もしない＝null）
       ├ CandidateReranker.score(leftContext, inputs) → 候補ごとのスコア（大きいほど良い）
       └ スコア降順（同点は Sumire 順）で 1 位・マージン・並び順を返す
```

- 差し替え口: `converter/sumirelm/CandidateReranker.kt`
  - `interface CandidateReranker { fun score(context: String, cands: List<SumireLmReranker.Input>): DoubleArray }`
  - `Input` = surface（表記）/ cost（Sumire スコア）/ rank（Sumire 順位 0..49）/ nseg（文節数）/ type（候補種別）/ tokens（語分割の出力列）
  - 左文脈は最大 `NecookeyCandidateBarConfig.maxLeftContextChars` 字。
- 読み込み: `CandidateRerankers.load(dir)`。端末（IMEService）も評価（`NK_RERANK_DIR`）もここを通る。
  形式は `CandidateRerankers.formats` のリスト（いまは `sumire-lm-od` = `SumireLmReranker`、必須ファイル meta.tsv, cb.tsv, model.tsv, vocab.tsv, w1.fp, p.fp）。
- 表の置き場（端末）: 外部 `/sdcard/Android/data/<package>/files/sumire_lm/` → 内部 `files/sumire_lm/` → アセット `assets/sumire_lm/`（内部へコピー）。
  見つからなければリランカーなしで動き、一定時間後に再探索。

## 新しいリランカーを入れる

1. **同じ表形式（export_od.py の出力）なら**コード変更なし。表ディレクトリを用意するだけ。
2. **別形式・別モデルなら** `CandidateReranker` を実装したクラスを `converter/sumirelm/` に置き（Android 非依存に）、
   `CandidateRerankers.formats` に `Format("名前", 必須ファイル一覧) { dir -> 新クラス.load(dir) }` を 1 行足す（先頭に置けば優先）。
   並べ替え・同点処理・ユーザー辞書保護は `SumireLmGate` 側なので、実装はスコアを返すだけでよい。
3. 単体テストは `app/src/test/.../converter/sumirelm/` に（既存の SumireLmReranker のテストが例）。

## 評価

```sh
. /workspace/tools/env.sh
export NK_LATTICE_COST=$(realpath ../eval/lc_np1m.slc) NK_RERANK_DIR=/path/to/table_dir NK_LEGACY=0
tools/eval_border/run.sh /tmp/rr_in tools/eval_border/in.tsv      # reg.tsv / dia.tsv も同様（gradle は 1 本ずつ）
python3 tools/eval_border/ev3.py tools/eval_border/in.tsv /tmp/rr_in/cands.tsv /tmp/rr_in/cands.tsv.rerank
```

- `cands.tsv` = リランカーなし（実機上段 n=8）、`cands.tsv.pool` = リランカー入力（50 件）、`cands.tsv.rerank` = 並べ替え後（列 9 がスコア）。
  ev3.py の 2 つ目以降に `.rerank` を渡すと、なし→ありの直った／壊れた文が出る（`EV_DIFF=1` で top-1 の変化一覧）。
- 基準（リランカーなし既定構成）: 179 文 exact 117 / lenient 158 / 致命 4 / ツギハギ 1、reg lenient 96、dia lenient 18（RESULTS.md）。
  同音異義 16 件（classified.tsv）がリランカーの主な狙い。
- コンパイル＋全 JVM テストは `tools/verify.sh`（`VERIFY_EVAL=1` で上の 179/reg/dia 評価も同時に、ただしリランカーなし）。

## 実機で試す

APK に同梱しない場合は adb で `files/sumire_lm/` に表を置く（`adb push table_dir/. /sdcard/Android/data/<package>/files/sumire_lm/`）。
設定「SUMIRE-LM リランカー」（キー名は互換のため旧名 necookey_zenz_bunsetsu_gate_preference）が ON であること。
logcat の `sumire-lm: reranker loaded` / `sumire-lm: input=[…] sumire=[…] rerank=[…] margin=…` で確認できる。

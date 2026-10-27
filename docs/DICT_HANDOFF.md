# システム辞書 作り直し 引き継ぎ（2026-10-28、conv-dialect 190c387 時点）

11 月の辞書再生成担当向け。対象は `app/dictionary-src/system/{tango,yomi,token}.dat.zip`（LOUDS 3 点セット。ビルド時に
`generateCompactSystemDictionary` が `build/generated/compactSystemDictionary/assets/system/system.compact.kdict` に変換）。

## 1. 事例「おこってる → 怒ってる が pool50 に無い」の原因

LatticeProbeTest（`NK_PROBE=おこってる`）と辞書ダンプで確認した。

- **怒る（おこる）自体はある**：おこる/怒る 837（五段 基本形）コスト 2600（起こる 2610 とほぼ同じ）。怒ら 2767・怒り 4100・怒れ・怒ろ もある。
- **連用タ接続 おこっ/怒っ（id 841）だけが欠落**。`いかっ/怒っ` 841 はあるが `おこっ/怒っ` が無い。
  → 「おこって…」のラティスに 怒っ ノードが生えないので、どの設定でも pool に出ない（コストの問題ではない）。
- **煽る に おこ の読みが付いている**：おこる/煽る 5478、おこっ/煽っ 5491、おこら/煽ら 5354 … 活用 10 形すべて＋語幹 おこ/煽（577）4488。
  正しい あおっ/煽っ 3994 も別にある。これにより実機上段は 1 位 起こってる、2 位 **煽ってる**（13104 / 15664）。
  同様に `あふ`（煽る の歴史的仮名）も 10 形ある。
- 修正方針（再生成時）：元データに `おこっ 怒っ 841`（必要なら おこん 怒ん 等の撥音形も）を追加、`煽` の読み おこ 系を削除。
  再生成せずに直すなら SystemDictionaryErrata と同様の読み込み時パッチが要るが、追加は表記トライの再構築が要るので非推奨。

## 2. 読みズレ・欠落の簡易チェック（tools/dict_check/）

```sh
. /workspace/tools/env.sh
# 1) 全語ダンプ（読み\t表記\t左ID\t右ID\tコスト、約 129 万行・60MB、gradle 込み 5 分弱）
NK_DICT_DUMP=/tmp/dc/dict.tsv ./gradlew --no-daemon :app:testFullStandardDebugUnitTest \
  --tests '*SystemDictionaryDumpTest*' --max-workers=2 > /tmp/dc/log 2>&1
# 2) チェック（15 秒）。出力は dict.tsv と同じディレクトリ
python3 tools/dict_check/check.py /tmp/dc/dict.tsv /tmp/dc
```

ダンプは `app/src/test/.../quality/SystemDictionaryDumpTest.kt`（表記は辞書そのまま、Errata 非適用）。品詞名は `assets/id.def`。
新辞書でも同じ手順で回せるので、**再生成前後で件数が減っていること**を確認に使う。誤検出が多いので目視前提。

2026-10-28 の結果（1,291,710 語）:

| 出力 | 件数 | 中身 |
|---|---|---|
| basic_missing.tsv | 1 | `basic_words.tsv`（基本語リスト、追記して使う）の欠落/コスト>9000。**おこっ/怒っ MISSING** |
| godan_gap.tsv | 143 / 五段基本形 13,285 | 五段で基本形はあるのに連用タ接続が無い（サ行と〜ゆく は除外）。**1 位が おこる/怒る**、次が こう/買う（「買う」を こう と読む関西形）。以降は 言うまい・柿食う・頑張るぞ 等のフレーズ登録で誤検出 |
| reading_suspects.tsv | 1,957（score≥2: 1,201） | 漢字 1 字＋送り仮名の語で、その語幹読みが他の語でほぼ使われず、同じ読み＋送り仮名の別漢字語がある。score→コスト順 |

reading_suspects の例：煽/おこ（270 位、別漢字語 怒る）・煽/あふ、続/つず（つずく 続く 1198、づ→ず の誤読み入力救済の可能性あり）、
寂/さみ・付/ず・逝/ゆ・損/そこ などは正当な読みも混ざる（来/こ・言/ゆ・得/う は誤検出）。漢字の音訓表を入れれば精度は上がる（未実装）。

## 3. ベースアプリ（upstream）の辞書更新を取り込む場合の手順案

1. upstream の該当コミットから `app/dictionary-src/system/*.dat.zip`（＋変わっていれば `connectionId.dat.zip`・`assets/pos_table.dat`・`id.def`）だけを持ってくる。
   左右 ID 体系（id.def）が変わると、ID 直書きのコード（VerbInflectionCost・PosPairPenalty・方言ノード等）を全部見直すこと。
2. `./gradlew --no-daemon :app:testFullStandardDebugUnitTest --tests '*CompactSystemDictionaryMmapTest*'` 等で読み込みを確認。
3. 2 章のダンプ＋check.py を新旧で回し、件数・おこっ/怒っ・煽/おこ を確認。
4. 4 章の前後比較。悪化文は LatticeProbeTest でノード有無を確認。
5. 独自に直した誤り（1 章・Errata）を upstream 側にも報告しておくと次回から楽。

## 4. 179 文 / reg 109 / dia 20 で前後比較

- 入力：`tools/eval_border/in.tsv`（179）・`reg.tsv`（109）・`dia.tsv`（20）。評価は `ev3.py`、定義と基準値は `tools/eval_border/RESULTS.md`（既定構成 exact 117 / lenient 158 / 致命 4 / ツギハギ 1、reg 96、dia 18）。
- ラティス表 `lc_np1m.slc` は**コミット対象外**：`git show origin/lm-data:lc_np1m.slc > ../eval/lc_np1m.slc`（lm-data 63732f8）。`eval/` もリポジトリに入れない。

```sh
. /workspace/tools/env.sh
export NK_LATTICE_COST=$PWD/../eval/lc_np1m.slc
for s in in reg dia; do tools/eval_border/run.sh /tmp/new_$s tools/eval_border/$s.tsv; done   # 1 本ずつ（gradle 同時起動しない）
python3 tools/eval_border/ev3.py tools/eval_border/in.tsv /tmp/old_in/cands.tsv /tmp/new_in/cands.tsv
```

- 旧辞書側（/tmp/old_*）は辞書差し替え前に同じコマンドで取っておく。
- 1 回の JVM で複数設定を出すなら `NK_CONFIGS="base:;noerr:err=0"`（キーは SumireNbestDumpTest の KDoc：dinf dcost kdia kpen ha hat err）。出力は `cands.tsv.名前`。
- 回帰ガード `DeviceConfigRegressionGuardTest`（買って／県庁／困難／広島県／三年）も流す（表が無いとスキップ）。

## 5. SystemDictionaryErrata は再生成後に削除

`app/src/main/java/.../converter/graph/SystemDictionaryErrata.kt` は `季も桃も桃のうち → 李も桃も桃のうち`（b005）を読み込み時に置換しているだけ。
元データで直して再生成したら、クラス・GraphBuilder の `SystemDictionaryErrata.fix(...)` 呼び出し・`SystemDictionaryErrataTest`・
SumireNbestDumpTest の `err` キーをまとめて削除する。

## 6. 残課題（辞書以外）

- 県庁ガード NG：けんちょうにいった → 1 位 堅調に行った（正解 3 位、ガードは top5 で許容中）。同音異義。
- 境界 b001（ここではきものを… → では着物）・b014（きのうはいたくつがない → 機能は痛く注がない、正解 8 位）。
- 同音異義 16 件はリランカー待ち（表は APK 非同梱・ローカルにも無い）。

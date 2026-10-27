# 境界テスト一式（変換精度評価、conv-eval）
- in.tsv: 179 文（id, split, 左文脈, 読み, 正解|別解）。カテゴリは id 先頭 u/b/f/h/c。
- run.sh: 実機既定経路のダンプ（app/src/test/.../quality/SumireNbestDumpTest.kt、経路は DeviceConfigConversion.kt）。
- ev2.py: split 別 exact / lenient / oracle@5 / oracle@50（bdiag 非依存）。EV_ERR=path で誤り一覧。
- classified.tsv: 2026-10-26 の誤り 24 件の分類（境界・方言・同音異義・未知語）と正解順位。
- RESULTS.md: 2026-10-26 の結果。errs_2026-10-24.txt: 旧結果。
- 回帰ガード: quality/DeviceConfigRegressionGuardTest（表が無ければスキップ、NK_LATTICE_COST か ../eval/lc_np1m.slc）。

## ev3.py（2026-10-27）
`python3 ev3.py <dump...>` で exact/lenient/致命/ツギハギ/oracle@2,3 と直った/壊れた文を出す。
`reg.tsv`（回帰リスト 109 文）・`dia.tsv`（方言追加 20 文）も同形式。複数設定は SumireNbestDumpTest の `NK_CONFIGS` で 1 回のビルドで出せる。定義は RESULTS.md。

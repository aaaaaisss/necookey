# 境界テスト一式（変換精度評価）
- in.tsv：179文（カテゴリ u/b/f/h/c）。読み誤記の可能性あり、まず全件見直し（u015 は修正済み）。
- errs.txt / RESULTS.md：2026-10-24 時点の誤り一覧と結果（Sumire+ラティス、リランカーなし lenient .860→読み修正後 .866、oracle@5 .983）。
- SumireNbestDumpTest.kt：N-best を TSV に吐く JVM テスト。app/src/test/java/.../converter/quality/ に置いて実行。環境変数 NK_DUMP_IN, NK_DUMP_OUT, NK_DUMP_SHARD=i/n, NK_LATTICE_COST=<lc_np1m.slc>（lm-data ブランチ 63732f8 から git show で取得）。
- run.sh：Hark 環境用のシャード実行スクリプト（パスは要書き換え、`-x :zenz:prepareZenzModel` は slim では不要）。
- ev.py：dump と in.tsv から lenient/strict 正解率を計算。
- 注意：ハーネスと実機で出力が違う例あり（b014「痛く注がない」は実機で出ない）。まず約20文を実機と比較して差を潰してから評価。
- 回帰ガード：買って／県庁／困難／広島県／三年。

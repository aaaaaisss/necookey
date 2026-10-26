#!/bin/bash
# run.sh OUTDIR [INPUT] [ENV=...] : 実機既定経路のダンプ（SumireNbestDumpTest）。ラティス表は NK_LATTICE_COST=<lc_np1m.slc>
#   （lm-data 63732f8 から git show origin/lm-data:lc_np1m.slc > ...、リポジトリには入れない）。
# 例: NK_LATTICE_COST=/path/lc_np1m.slc tools/eval_border/run.sh /tmp/out
#     python3 tools/eval_border/ev2.py tools/eval_border/in.tsv /tmp/out/cands.tsv /tmp/out/cands.tsv.pool
# 任意: NK_NGRAM=0（n-gram 無効）NK_UTDICT=0（人名・Wiki 辞書なし）NK_LEGACY=0（旧ハーネス相当 .legacy を省略）
#       NK_RERANK_DIR=<meta.tsv 等のある dir>（リランカー適用結果 .rerank）
set -e
D=$(cd "$(dirname "$0")" && pwd); R=$D/../..
OUT=$(realpath -m "$1"); IN=$(realpath "${2:-$D/in.tsv}"); shift; [ $# -gt 0 ] && shift
[ -n "$JAVA_HOME" ] || { [ -f /workspace/tools/env.sh ] && . /workspace/tools/env.sh; }
: "${NK_LATTICE_COST:?set NK_LATTICE_COST to lc_np1m.slc}"
mkdir -p "$OUT"; cd "$R"
export NK_DUMP_IN=$IN NK_DUMP_OUT=$OUT/cands.tsv NK_LATTICE_COST
for a in "$@"; do export "$a"; done
./gradlew --no-daemon :app:testFullStandardDebugUnitTest --rerun --tests '*SumireNbestDumpTest*' -i --max-workers=2 > "$OUT/log" 2>&1
echo "EXIT $?" >> "$OUT/log"

#!/bin/bash
# run.sh OUTDIR INPUT NSHARD [ENV=...]  : dump with lattice table, shards as separate gradle test runs (sequential JVMs, 2g heap each)
. /workspace/work/tools/env.sh
OUT=$1; IN=$2; NS=$3; shift 3
mkdir -p $OUT
cd /workspace/work/nk-dialect
for ((s=0; s<NS; s++)); do
 ( export NK_DUMP_IN=$IN NK_DUMP_OUT=$OUT/cands.$s.tsv NK_DUMP_SHARD=$s/$NS NK_LATTICE_COST=/workspace/work/sumire_lm/lcost/lc_np1m.slc
   for a in "$@"; do export "$a"; done
   ./gradlew :app:testLiteStandardDebugUnitTest --rerun --tests '*SumireNbestDumpTest*' -i --max-workers=2 $(/workspace/work/tools/proxyargs.sh) -x :zenz:prepareZenzModel > $OUT/log.$s 2>&1; echo "EXIT $?" >> $OUT/log.$s )
done
touch $OUT/DONE

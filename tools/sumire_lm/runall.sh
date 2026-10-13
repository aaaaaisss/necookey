#!/bin/bash
cd /workspace/work/ime/zg
./run.sh /workspace/work/ime/data/ajimee.tsv /workspace/work/ime/zg/aj.z.out /workspace/work/ime/zg/aj.z.cands > aj.log 2>&1
./run.sh /workspace/work/ime/data/jwtd_kc.tsv /workspace/work/ime/zg/jw.z.out /workspace/work/ime/zg/jw.z.cands > jw.log 2>&1
echo ALLDONE >> jw.log

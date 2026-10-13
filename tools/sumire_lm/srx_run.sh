#!/bin/bash
# srx_run.sh : after the dump artifacts are in $WORK/srx/cands, rebuild items/features for the split re-conversion pool
# (only n-grams/pairs missing from the old need sets are counted; ~10 min on 2 CPUs). Run detached: setsid nohup bash srx_run.sh &
WORK=${SLM_WORK0:-/workspace/work/sumire_lm}; W2=$WORK/srx; D=$W2/delta; T=$(dirname $(realpath $0))
export PYTHONPATH=$WORK/pylib
cd $W2 && rm -f $D/lm/part_* $D/lm/pair_* $D/lm/stats_* $D/lm/pair.npz
python3 $T/srx.py prep > run.log 2>&1
python3 $T/feats.py all $W2 >> run.log 2>&1 && python3 $T/cooc.py need $W2 >> run.log 2>&1 && python3 $T/srx.py delta >> run.log 2>&1 || exit 1
( for i in 0 1 2 3 4 5 6 7 8; do python3 $T/ngcount.py c $i $D --noF > $D/cc$i.log 2>&1; done
  python3 $T/ngmerge.py c 0-8 stats_c $D > $D/mc.log 2>&1 ) &
for i in 0 1 2 3 4 5 6 7 8; do python3 $T/ngcount.py w $i $D > $D/cw$i.log 2>&1; done
python3 $T/ngmerge.py w 0-8 stats_w $D > $D/mw.log 2>&1
for i in 0 1 2 3 4 5 6 7 8; do python3 $T/cooc.py count $i $D > $D/cp$i.log 2>&1; done
python3 $T/cooc.py merge 0-8 $D > $D/mp.log 2>&1
wait
python3 $T/srx.py combine >> run.log 2>&1 && SLM_WORK=$W2 python3 $T/rr.py feats >> run.log 2>&1 && python3 $T/srx.py xflags >> run.log 2>&1
echo ALLDONE >> run.log

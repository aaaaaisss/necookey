#!/bin/sh
# folrun.sh KIND KMAX [KMIN] : resumable driver for follow.py (skips pieces / orders already done); run it again after an interruption
W=${SLM_WORK:-/workspace/work/sumire_lm}; T=$(dirname "$0"); K=$1; KM=$2; K0=${3:-2}
for I in 0 1 2 3 4 5 6 7 8; do
  [ -f "$W/od/fol/${K}_${KM}_${I}_7.npz" ] || python3 "$T/follow.py" count "$K" "$I" "$KM" "$K0" || exit 1
done
k=$K0; while [ $k -le "$KM" ]; do
  [ -f "$W/od/fol_${K}${k}.npz" ] || python3 "$T/follow.py" merge "$K" "$k" || exit 1
  k=$((k+1)); done
echo ALLDONE

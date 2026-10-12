#!/bin/sh
# odsweep.sh VARIANT "cfg1" "cfg2" ... : run odsim.py configs sequentially (each ~40 s; keep a call < 1200 s), log to WORK/od/sweep.log
W=${SLM_WORK:-/workspace/work/sumire_lm}; T=$(dirname "$0"); V=$1; shift
for c in "$@"; do python3 "$T/odsim.py" "$V" $c 2>&1 | tail -1 | tee -a "$W/od/sweep.log"; done

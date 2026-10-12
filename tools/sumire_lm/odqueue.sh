#!/bin/sh
# odqueue.sh QUEUEFILE : run odsim.py for each line "VARIANT key=val ... tag=NAME" unless NAME is already in od/results.jsonl
# (resumable; run in the background and re-launch after an interruption)
W=${SLM_WORK:-/workspace/work/sumire_lm}; T=$(dirname "$0")
while read -r line; do
  [ -z "$line" ] && continue
  tag=$(echo "$line" | sed -n 's/.*tag=\([^ ]*\).*/\1/p')
  grep -q "\"tag\": \"$tag\"" "$W/od/results.jsonl" 2>/dev/null && continue
  python3 "$T/odsim.py" $line 2>&1 | tail -1 >> "$W/od/sweep.log"
done < "$1"
echo QDONE >> "$W/od/sweep.log"

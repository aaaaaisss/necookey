#!/bin/bash
# usage: probe.sh "reading1 reading2 ..." [override .kt files]  -> top5 per reading
R="$1"; shift; i=0; : > /tmp/probe.tsv
for r in $R; do echo -e "p$i\tp\t\t$r\t-\t" >> /tmp/probe.tsv; i=$((i+1)); done
/workspace/work/ime/data/fast.sh /tmp/probe.tsv /tmp/probe.out "$@" | grep -v "^OK"
paste <(cut -f4 /tmp/probe.tsv) <(cut -f5-7 /tmp/probe.out)

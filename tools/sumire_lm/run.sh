#!/bin/bash
# usage: run.sh IN OUT CANDS [limit]
T=/workspace/work/ime/tools; export JAVA_HOME=$T/jdk-17.0.20.1+1 PATH=$T/jdk-17.0.20.1+1/bin:$PATH
CP=$(cat /workspace/work/ime/data/testcp.txt)
cd /workspace/work/ime/nk/app && java -Xmx2g -cp /workspace/work/ime/zg/kcls:/workspace/work/ime/nk/app/src/main/resources:$CP ZenzBenchKt "$@"

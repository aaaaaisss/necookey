#!/bin/bash
# usage: fast.sh IN.tsv OUT.tsv [main/src/File.kt ...]
# Runs ConversionBenchHarnessTest without Gradle against the last Gradle-built classes (testcp.txt from
# printcp.gradle). Given .kt files are recompiled (module app_fullStandardDebug) and put first on the classpath.
D=/workspace/work/ime/data; T=/workspace/work/ime/tools
export JAVA_HOME=$T/jdk-17.0.20.1+1 PATH=$T/jdk-17.0.20.1+1/bin:$T/kotlinc/bin:$PATH
IN=$(realpath $1); OUT=$(realpath -m $2); shift 2
CP=$(cat $D/testcp.txt); rm -rf /tmp/ovr; mkdir -p /tmp/ovr
if [ $# -gt 0 ]; then kotlinc -jvm-target 17 -module-name app_fullStandardDebug -cp $CP "$@" -d /tmp/ovr 2>&1 | grep -E "^error|error:" ; fi
cd /workspace/work/ime/nk/app && NK_BENCH_IN=$IN NK_BENCH_OUT=$OUT java -Xmx2g -cp /tmp/ovr:/workspace/work/ime/nk/app/src/main/resources:$CP org.junit.runner.JUnitCore com.kazumaproject.markdownhelperkeyboard.converter.quality.ConversionBenchHarnessTest 2>&1 | grep -E "^OK|Tests run|Exception|^[0-9]+\)" | head -5

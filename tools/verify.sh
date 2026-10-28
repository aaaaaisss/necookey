#!/bin/bash
# tools/verify.sh [OUTDIR] : コンパイル 4 種＋全 JVM テストを gradle 1 回で回して PASS/FAIL を出す。
#   VERIFY_EVAL=1 なら変換評価（179 / reg 109 / dia 20）も同じ gradle 実行で回す（任意。大きな変更の最後に 1 回で十分）。
#
#   コンパイル: :app:compileFullStandardDebugKotlin / :app:compileFullStandardDebugUnitTestKotlin /
#               :app:compileLiteStandardDebugKotlin / :app:compileLiteFdroidDebugKotlin（VERIFY_QUICK=1 で lite 2 種を省略）
#   テスト    : :app:testFullStandardDebugUnitTest（評価ダンプ SumireNbestDumpTest も同じ JVM で走る）
#               :core / :custom_keyboard / :symbol_keyboard の testDebugUnitTest
#   評価（任意）: ev3.py の数値を基準値（tools/eval_border/RESULTS.md の既定構成）と比較。
#               179: exact 117 / lenient 158 / 致命 4 / ツギハギ 1、reg・dia: 基準ダンプ（tools/eval_border/baseline/、conv-dialect 1f22e7b）から壊れ 0
#               （reg lenient 96、dia lenient 18 / ツギハギ 0 も確認）。
#   VERIFY_NO_TEST=1 でコンパイルだけ。
# 評価に必要: ラティス表 NK_LATTICE_COST（既定 ../eval/lc_np1m.slc。git show origin/lm-data:lc_np1m.slc で取得、リポジトリに入れない）。
# --max-workers は既定 1（2 だとアプリのテスト JVM とモジュールのテストが並んでメモリ 7GB で gradle デーモンが落ちた。VERIFY_WORKERS で変更可）。
# 1 本ずつ実行すること（gradle を同時に起動しない）。所要: コンパイル＋テストで 20〜30 分、評価込みで +10 分前後（aarch64・2 コア）。
set -u
D=$(cd "$(dirname "$0")" && pwd); R=$(cd "$D/.." && pwd); E=$D/eval_border
OUT=$(realpath -m "${1:-/tmp/nk_verify}")
mkdir -p "$OUT"; cd "$R"
[ -n "${JAVA_HOME:-}" ] || { [ -f /workspace/tools/env.sh ] && . /workspace/tools/env.sh; }
export NK_LATTICE_COST=${NK_LATTICE_COST:-$(realpath -m "$R/../eval/lc_np1m.slc")}

TASKS=(:app:compileFullStandardDebugKotlin :app:compileFullStandardDebugUnitTestKotlin)
[ "${VERIFY_QUICK:-0}" = 1 ] || TASKS+=(:app:compileLiteStandardDebugKotlin :app:compileLiteFdroidDebugKotlin)
EVAL=${VERIFY_EVAL:-0}
if [ "${VERIFY_NO_TEST:-0}" = 1 ]; then EVAL=0
else
  TASKS+=(:app:testFullStandardDebugUnitTest --rerun :core:testDebugUnitTest :custom_keyboard:testDebugUnitTest :symbol_keyboard:testDebugUnitTest)
  if [ "$EVAL" = 1 ]; then
    [ -f "$NK_LATTICE_COST" ] || { echo "FAIL: lattice table not found: $NK_LATTICE_COST"; exit 2; }
    rm -rf "$OUT/in" "$OUT/reg" "$OUT/dia"
    export NK_DUMP_DIR=$OUT NK_LEGACY=0 NK_DUMP_SETS="in=$E/in.tsv;reg=$E/reg.tsv;dia=$E/dia.tsv"
  fi
fi
for d in app core custom_keyboard symbol_keyboard; do rm -rf "$d/build/test-results"/test*UnitTest; done

echo "gradle: ${TASKS[*]}"
t0=$(date +%s)
./gradlew --no-daemon --continue "${TASKS[@]}" --max-workers="${VERIFY_WORKERS:-1}" > "$OUT/gradle.log" 2>&1
GX=$?
echo "gradle exit $GX ($(( ($(date +%s) - t0) / 60 )) min, log $OUT/gradle.log)"
FAIL=0
for t in compileFullStandardDebugKotlin compileFullStandardDebugUnitTestKotlin compileLiteStandardDebugKotlin compileLiteFdroidDebugKotlin; do
  if grep -q "Task :app:$t FAILED" "$OUT/gradle.log"; then echo "FAIL compile $t"; FAIL=1; fi
done
grep -E "^e: " "$OUT/gradle.log" | head -20

if [ "${VERIFY_NO_TEST:-0}" != 1 ]; then
  python3 - "$R" "$OUT" <<'PY' || FAIL=1
import sys, glob, os, xml.etree.ElementTree as ET
R, OUT = sys.argv[1:3]
tot = fail = skip = 0; bad = []; skipped = []
for d in ('app', 'core', 'custom_keyboard', 'symbol_keyboard'):
    for f in glob.glob(f'{R}/{d}/build/test-results/test*UnitTest/*.xml'):
        for tc in ET.parse(f).getroot().iter('testcase'):
            tot += 1; name = f"{d}:{tc.get('classname')}.{tc.get('name')}"
            if tc.find('failure') is not None or tc.find('error') is not None: fail += 1; bad.append(name)
            elif tc.find('skipped') is not None: skip += 1; skipped.append(name)
open(f'{OUT}/tests_failed.txt', 'w').write('\n'.join(bad) + '\n')
open(f'{OUT}/tests_skipped.txt', 'w').write('\n'.join(skipped) + '\n')
print(f'tests: {tot} total, {tot - fail - skip} passed, {fail} failed, {skip} skipped')
import platform
if platform.system() == 'Linux' and platform.machine() == 'aarch64':
    n = sum(1 for l in open(f'{R}/tools/robolectric_aarch64_excludes.txt') if l.strip() and not l.startswith('#'))
    print(f'  (Linux aarch64: Robolectric ネイティブランタイム非対応の {n} 件は除外済み、tools/robolectric_aarch64_excludes.txt)')
for b in bad[:40]: print('  FAILED', b)
if tot == 0: print('FAIL tests: no results'); sys.exit(1)
sys.exit(1 if fail else 0)
PY
fi

if [ "$EVAL" = 1 ]; then
  python3 - "$E" "$OUT" <<'PY' || FAIL=1
import sys, os
E, OUT = sys.argv[1:3]
sys.path.insert(0, E)
from ev2 import load_meta, load
from ev3 import judge, row
# 基準値（RESULTS.md 2026-10-27 既定構成: 方言活用ノード既定・かな修復 2 位・は-merge オフ）
EXP = {'in': dict(exact=117, len=158, fatal=4, tg=1), 'reg': dict(len=96), 'dia': dict(len=18, tg=0)}
ok = True
for s in ('in', 'reg', 'dia'):
    cur = f'{OUT}/{s}/cands.tsv'
    if not os.path.exists(cur) or os.path.getsize(cur) == 0:
        print(f'FAIL eval {s}: no dump ({cur})'); ok = False; continue
    meta = load_meta(f'{E}/{s}.tsv')
    pool = load(cur + '.pool') if os.path.exists(cur + '.pool') else {}
    rc = judge(meta, load(cur), pool)
    bp = f'{E}/baseline/{s}.cands.tsv'
    rb = judge(meta, load(bp), {}) if os.path.exists(bp) else rc
    n, ex, le, fa, tg, o2, o3 = row(rc, lambda i: True)
    missing = [i for i in meta if rc.get(i) is None]
    brk = sorted(i for i in rc if rc[i] and rb.get(i) and rb[i]['le'] and not rc[i]['le'])
    fix = sorted(i for i in rc if rc[i] and rb.get(i) and rc[i]['le'] and not rb[i]['le'])
    got = dict(exact=ex, len=le, fatal=fa, tg=tg)
    errs = [f'{k} {got[k]}≠{v}' for k, v in EXP[s].items() if got[k] != v]
    if brk: errs.append('壊れ ' + ','.join(brk))
    if missing: errs.append(f'出力なし {len(missing)}')
    print(f'eval {s:3s}: n={n} exact={ex} lenient={le} 致命={fa} ツギハギ={tg} o@2={o2} o@3={o3}  壊れ={len(brk)} 直り={len(fix)}'
          + (f'  -> FAIL ({"; ".join(errs)})' if errs else '  -> OK'))
    ok &= not errs
sys.exit(0 if ok else 1)
PY
fi

[ $GX -ne 0 ] && [ $FAIL -eq 0 ] && { echo "FAIL gradle exit $GX"; grep -E "FAILED|What went wrong" -A3 "$OUT/gradle.log" | head -20; FAIL=1; }
if [ $FAIL -eq 0 ]; then echo "VERIFY PASS"; else echo "VERIFY FAIL"; fi
exit $FAIL

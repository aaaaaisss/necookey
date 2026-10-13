#!/usr/bin/env python3
"""hcmp.py DIR_OFF DIR_ON : はいる→入る 対策（PosPairPenalty.hairuPenalty, NK_HAIRU）の前後比較。
各 DIR は SumireNbestDumpTest を NK_LATTICE_COST 付きで回した出力（cands.*.tsv = 表あり, cands.*.tsv.base = 表なし）。
入力の正解は data/jwtd_kc.tsv（tune/held）, data/ajimee.tsv（eval）, ci/probe_hairu.tsv（probe）, daily.tsv（dtune/dheld）。
出力: 4 条件（表なし/あり × 対策なし/あり）の Sumire top1（split 別）、はいる→入る 誤り数
（読みに「はい」、top1 に「入」、正解のどれにも「入」が無い）、対策で top1 が変わった文（改善 / 悪化）。
判断は tune（JWTD tune・daily tune）のみ。held / AJIMEE は報告のみ。"""
import sys, os, glob, collections
W0 = os.environ.get('SLM_WORK0', '/workspace/work/sumire_lm'); T = os.path.dirname(os.path.abspath(__file__))
meta = {}
for fn, pre in ((f'{W0}/data/jwtd_kc.tsv', ''), (f'{W0}/data/ajimee.tsv', ''), (f'{T}/ci/probe_hairu.tsv', ''), (f'{W0}/daily.tsv', 'd')):
    if not os.path.exists(fn): continue
    for l in open(fn, encoding='utf-8'):
        f = l.rstrip('\n').split('\t')
        if len(f) >= 5 and f[3]: meta[f[0]] = dict(sp=pre + f[1], rd=f[3], exp=f[4].split('|'))


def top1(d, pat):
    t = {}
    for fn in glob.glob(f'{d}/{pat}'):
        for l in open(fn, encoding='utf-8'):
            f = l.split('\t', 3)
            if len(f) >= 3 and f[1] == '0' and f[0] in meta: t[f[0]] = f[2]
    return t


C = {}
for name, d in (('off', sys.argv[1]), ('on', sys.argv[2])):
    C[f'base/{name}'] = top1(d, 'cands.*.tsv.base'); C[f'lc/{name}'] = top1(d, 'cands.*.tsv')
SPL = ('tune', 'held', 'eval', 'dtune', 'dheld', 'probe')
ok = lambda sid, s: s in meta[sid]['exp']
hai = lambda sid, s: 'はい' in meta[sid]['rd'] and '入' in s and not any('入' in e for e in meta[sid]['exp'])
print('cond        ' + '  '.join(f'{sp:>13}' for sp in SPL) + '   はいる→入る ' + ' '.join(SPL))
for c, t in C.items():
    acc = collections.defaultdict(lambda: [0, 0]); h = collections.Counter()
    for sid, s in t.items():
        sp = meta[sid]['sp']; acc[sp][0] += 1; acc[sp][1] += ok(sid, s); h[sp] += hai(sid, s)
    print(f'{c:10s}  ' + '  '.join(f'{acc[sp][1]:5d}/{acc[sp][0]:4d} {acc[sp][1] / max(1, acc[sp][0]):.3f}'[-13:] for sp in SPL)
          + '   ' + ' '.join(str(h[sp]) for sp in SPL))
for v in ('base', 'lc'):
    a, b = C[f'{v}/off'], C[f'{v}/on']
    print(f'\n[{v}] 対策で top1 が変わった文（+ 改善 / - 悪化 / = どちらも誤り）')
    for sid in sorted(set(a) & set(b), key=lambda x: (meta[x]['sp'], x)):
        if a[sid] == b[sid]: continue
        m = '+' if ok(sid, b[sid]) and not ok(sid, a[sid]) else '-' if ok(sid, a[sid]) and not ok(sid, b[sid]) else '='
        print(f'  {m} {meta[sid]["sp"]:5s} {sid:6s} {a[sid][:28]} -> {b[sid][:28]}  (gold {meta[sid]["exp"][0][:28]})')
    for sp in SPL:
        ids = sorted(s for s in b if meta[s]['sp'] == sp and hai(s, b[s]))
        if ids: print(f'  残るはいる→入る {sp}: ' + ', '.join(f'{s} {b[s][:24]}' for s in ids[:6]))

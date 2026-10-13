#!/usr/bin/env python3
"""hc_cover.py RESFILE [PREP] : coverage curve of homophone errors (in-list) by Sumire cost margin and gold rank, before and after hc."""
import sys, os, pickle
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hc_common import *
from hc_run import grp, G, homo_err
nb = pickle.load(open(f'{HC}/eval.pkl', 'rb')); B, QB, KALT, LAM, TH, res = pickle.load(open(sys.argv[1], 'rb'))
TH_ = [250, 500, 1000, 2000, 3000]
for g in ('jwtd-tune', 'jwtd-held', 'daily-tune', 'daily-held', 'ajimee'):
    for lab in ('before', 'after'):
        m = []
        for i in nb:
            if grp(i) != g: continue
            p = 0 if lab == 'before' else (res[i][1] if res[i][1] is not None else 0)
            if not homo_err(nb, i, p): continue
            rows = nb[i]; gi = next(k for k, r in enumerate(rows) if r[1] in G[i][3])
            # margin vs the shown top1 (cost of gold - cost of displayed pick); rank among the list with the pick moved to the front
            rk = gi if gi > p else gi + 1 if p != 0 else gi
            m.append((rows[gi][2] - rows[p][2], rk))
        n = len(m)
        print(f'{g:11s} {lab:6s} n={n:3d} ' + ' '.join(f'<={t}:{sum(x <= t for x, _ in m) / n:.2f}' for t in TH_) + ' | gold rank<=1 %.2f <=2 %.2f <=4 %.2f <=9 %.2f' % tuple(sum(r <= k for _, r in m) / n for k in (1, 2, 4, 9)))
print('UI chip view: among homophone errors, gold = k-th pure-homophone variant of the shown pick (variants in Sumire rank order)')
for g in ('jwtd-tune', 'jwtd-held', 'daily-tune', 'daily-held', 'ajimee'):
    for lab in ('before', 'after'):
        ks = []
        for i in nb:
            if grp(i) != g: continue
            p = 0 if lab == 'before' else (res[i][1] if res[i][1] is not None else 0)
            if not homo_err(nb, i, p): continue
            rows = nb[i]; vs = [k for k, r in enumerate(rows) if k != p and classify(rows[p][3], r[3])[0] == 'homophone']
            gi = next(k for k, r in enumerate(rows) if r[1] in G[i][3]); ks.append(vs.index(gi) + 1 if gi in vs else 99)
        n = len(ks); print(f'{g:11s} {lab:6s} n={n:3d} gold is variant #1 {sum(k<=1 for k in ks)/n:.2f}  <=2 {sum(k<=2 for k in ks)/n:.2f}  <=3 {sum(k<=3 for k in ks)/n:.2f}  <=5 {sum(k<=5 for k in ks)/n:.2f}')

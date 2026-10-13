#!/usr/bin/env python3
"""hc_base.py : baseline error classes on eval.pkl (Sumire top1, lattice cost on): top1, in-list homophone errors, margin coverage curve."""
import sys, os, pickle, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hc_common import *
G = gold_table(); ev = pickle.load(open(f'{HC}/eval.pkl', 'rb'))
def grp(i, sp): return ('daily-' if i[0] == 'd' else 'jwtd-' if i[0] == 'j' else 'ajimee') + ('' if i[0] == 'a' else sp)
st = collections.defaultdict(collections.Counter); marg = collections.defaultdict(list)
for i, rows in ev.items():
    sp, ctx, rd, gold = G[i]; g = grp(i, sp); c = st[g]; c['n'] += 1
    t = rows[0]
    if t[1] in gold: c['ok'] += 1; continue
    gi = next((k for k, r in enumerate(rows) if r[1] in gold), None)
    if gi is None: c['out'] += 1; continue
    cl, dc = classify(t[3], rows[gi][3])
    c[cl] += 1
    if cl == 'homophone':
        marg[g].append((rows[gi][2] - t[2], gi))
for g in sorted(st):
    c = st[g]; print(g, dict(c), 'top1 %.3f' % (c['ok'] / c['n']))
print('coverage of in-list homophone errors by cost margin (gold - top1) / by gold rank')
TH = [100, 250, 500, 1000, 1500, 2000, 3000, 5000]
for g in sorted(marg):
    m = marg[g]; n = len(m)
    print(g, n, ' '.join(f'<={t}:{sum(x <= t for x, _ in m) / n:.2f}' for t in TH), '| rank1:%.2f r<=2:%.2f r<=4:%.2f r<=9:%.2f' % tuple(sum(r <= k for _, r in m) / n for k in (1, 2, 4, 9)))
pickle.dump(dict(marg), open(f'{HC}/base_marg.pkl', 'wb'))

#!/usr/bin/env python3
"""hc_rr.py TAG : additivity over the adopted reranker (lin base+lmw+lmc+pmi, tune) and overlap with its PMI feature.
Uses the OLD lattice-off N-best (cands/*.tsv -> old.pkl) because reranker scores (scores/lin_base+lmw+lmc+pmi_tune.npy, rows of ftab.npz)
exist only for it. JWTD + AJIMEE only (no daily rows in ftab).
 A) Sumire-off top1 + hc            B) rr                C) rr + hc (base = rr pick, cost diff = rr score diff * S, S tuned)"""
import sys, os, pickle, itertools, collections, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hc_common import *
import hc_eval as E
from hc_run import grp, summary, fmt, restrict, homo_err, G
TAG = sys.argv[1]; tab = pickle.load(open(f'{HC}/tab_{TAG}.pkl', 'rb'))
old = pickle.load(open(f'{HC}/old.pkl', 'rb')); old = {i: v for i, v in old.items() if i[0] in 'ja'}
z = np.load(f'{W}/ftab.npz', allow_pickle=True); s = np.load(f'{W}/scores/lin_base+lmw+lmc+pmi_tune.npy')
names = list(z['names']); X = z['X']; IDS = z['IDS']
items = pickle.load(open(f'{W}/items.pkl', 'rb'))
pos = 0; rrs = {}; pmi = {}; ip = names.index('pmi_sum'); im = names.index('pmi_max')
for (i, sp, exp, ctx, rows) in items:
    n = len(rows); assert IDS[pos] == i
    if i[0] in 'ja': rrs[i] = {r[0]: float(s[pos + k]) for k, r in enumerate(rows)}; pmi[i] = {r[0]: (X[pos + k, ip], X[pos + k, im]) for k, r in enumerate(rows)}
    pos += n
# old rows: (rank, surface, cost, segs) ; keep rows with rr score
nbA = {i: [(r[0], r[1], r[2], parse_segs(r[3])) for r in rows if r[0] in rrs.get(i, {})] for i, rows in old.items()}
nbA = {i: v for i, v in nbA.items() if v}
prepA = E.prepare(nbA, G, 50)
def best_grid(prep, nb, label):
    best = None
    for KALT in (5, 10, 20, 50):
        pr = restrict(prep, KALT); tune = {i: v for i, v in pr.items() if grp(i) == 'jwtd-tune'}
        for LAM in (0.05, 0.1, 0.2, 0.3, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0):
            r = E.decide(tune, tab, LAM); sc = sum(r[i][0] for i in tune)
            if best is None or sc > best[0]: best = (sc, KALT, LAM)
    sc, KALT, LAM = best; pr = restrict(prep, KALT); res = E.decide(pr, tab, LAM)
    print(label, 'tune-best KALT', KALT, 'LAM', LAM); print(fmt({k: v for k, v in summary(nb, pr, res).items() if k in ('jwtd-tune', 'jwtd-held', 'ajimee')}), flush=True)
    return pr, res
prA, resA = best_grid(prepA, nbA, 'A Sumire(lattice-off) + hc')
# B/C: re-sort by rr score; cost := -rr*1000 so that dcost/1000 = rr score difference (LAM absorbs the scale)
nbC = {i: sorted([(r[0], r[1], -rrs[i][r[0]] * 1000, r[3]) for r in v], key=lambda x: x[2]) for i, v in nbA.items()}
prepC = E.prepare(nbC, G, 50)
rrok = {i: prepC[i][1] for i in prepC}
prC, resC = best_grid(prepC, nbC, 'C rr + hc (B = rr is the "before" column)')
# overlap: sentences fixed by hc over Sumire-off; how many does rr already get right?
for g in ('jwtd-tune', 'jwtd-held', 'ajimee'):
    ids = [i for i in prA if grp(i) == g]
    fixA = [i for i in ids if resA[i][0] and not prA[i][1]]
    print(g, 'hc fixes over Sumire-off', len(fixA), 'of which rr also correct', sum(rrok[i] for i in fixA),
          '| rr fixes over Sumire-off', sum(rrok[i] and not prA[i][1] for i in ids), '| rr+hc fixes over rr', sum(resC[i][0] and not rrok[i] for i in ids))
# correlation of the hc vote with the reranker PMI feature difference (alt - base) on all mapped homophone alternatives (Sumire-off base)
v, dp, dm = [], [], []
for i, (b, ok, alts) in prepA.items():
    rb = nbA[i][b][0]
    for k, dcost, mps, ctxt, okk in alts:
        vs = [E.vote(tab, ma, mb, ctxt) for ma, mb in mps]
        if any(x is None for x in vs): continue
        ra = nbA[i][k][0]; v.append(sum(vs)); dp.append(pmi[i][ra][0] - pmi[i][rb][0]); dm.append(pmi[i][ra][1] - pmi[i][rb][1])
v, dp, dm = map(np.array, (v, dp, dm))
nz = v != 0
print('pairs', len(v), 'hc vote nonzero %.2f' % nz.mean(), 'pmi_sum diff nonzero %.2f' % (dp != 0).mean(),
      'corr(vote, d pmi_sum) %.3f' % np.corrcoef(v, dp)[0, 1], 'corr(vote, d pmi_max) %.3f' % np.corrcoef(v, dm)[0, 1],
      'sign agreement where both nonzero %.3f' % (np.sign(v) == np.sign(dp))[(v != 0) & (dp != 0)].mean())

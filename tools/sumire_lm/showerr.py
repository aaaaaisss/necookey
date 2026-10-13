#!/usr/bin/env python3
"""showerr.py SCORE_TAG [SPLIT=tune] [N=40] : print reranker errors (gold in top-50) as  ctx | gold <- chosen  (rank of gold)"""
import sys, os, pickle, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rr import load, group_first, W
T = load(); s = np.load(f'{W}/scores/{sys.argv[1]}.npy').astype(float); G = T['G']
sp = sys.argv[2] if len(sys.argv) > 2 else 'tune'; N = int(sys.argv[3]) if len(sys.argv) > 3 else 40
items = pickle.load(open(f'{W}/items.pkl', 'rb'))
o = np.lexsort((-s, G)); f = o[group_first(G[o])]
k = 0
for g, (i, spl, exp, ctx, rows) in enumerate(items):
    if spl != sp: continue
    r0 = T['gst'][g]; lab = T['LAB'][r0:r0 + len(rows)]
    if T['LAB'][f[g]] or not lab.any(): continue
    gi = int(np.nonzero(lab)[0][0]); ch = f[g] - r0
    print(f"{ctx[-12:]:>12s} | {rows[gi][1]} <- {rows[ch][1]}  (gold rank {gi}, chosen rank {ch})")
    k += 1
    if k >= N: break

#!/usr/bin/env python3
"""errstat.py SCORE_TAG : for a saved score vector (scores/TAG.npy), count errors per split and how many of them have
no gold in the Sumire top-50 (recall errors, unfixable by reranking)."""
import sys, os, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rr import load, group_first, W
T = load(); s = np.load(f'{W}/scores/{sys.argv[1]}.npy').astype(float); G = T['G']
o = np.lexsort((-s, G)); f = o[group_first(G[o])]; ok = T['LAB'][f]
orc = np.zeros(T['ng'], bool); np.logical_or.at(orc, G, T['LAB'])
top0 = T['LAB'][T['gst']]
for sp in ('tune', 'held', 'eval'):
    m = T['gsp'] == sp; n = m.sum(); err = m & ~ok
    print(f"{sp}: n={n} errors={err.sum()} gold-outside-top50={(err & ~orc).sum()} ({(err & ~orc).sum()/max(1,err.sum()):.0%} of errors) "
          f"rerank-fixable-but-wrong={(err & orc).sum()} | vs Sumire: fixed={(m & ok & ~top0).sum()} broken={(m & ~ok & top0).sum()}")

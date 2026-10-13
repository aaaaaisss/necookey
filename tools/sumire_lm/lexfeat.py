#!/usr/bin/env python3
"""lexfeat.py : per-row lexical strings for sparse features, same row order as ftab.npz -> WORK/lex.npz
DW = candidate tokens (surface) that are not shared by every candidate of the sentence (space-joined)."""
import os, sys, pickle, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__))); from common import rowdict
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm')
items = pickle.load(open(f'{W}/items.pkl', 'rb')); DW = []
for i, sp, exp, ctx, rows in items:
    rows = [rowdict(t) for t in rows]
    toks = [set(r['wt'][r['nc']:]) for r in rows]; common = set.intersection(*toks)
    for t in toks: DW.append(' '.join(sorted(t - common)))
np.savez(f'{W}/lex.npz', DW=np.array(DW, dtype=object)); print(len(DW))

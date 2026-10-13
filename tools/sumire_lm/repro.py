#!/usr/bin/env python3
"""Reproduce the 2026-10-12 baseline: word 3-gram, interpolated absolute discounting D=0.75, add-0.5 unigram,
first 1.5 GB of CC-100 (pieces 0-2 = lm/stats_w3p.npz) ; and the same LM on the full 4.5 GB (stats_w.npz)."""
import sys, os, pickle, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lmscore import score
from rr import load, col, acc_by_split, fmt
W = '/workspace/work/sumire_lm'
T = load(); z = np.load(f'{W}/lm/seqs.npz'); seqs = (z['wx'], z['wl'])
nw = np.load(f'{W}/lm/need_w.npz'); need = [nw[f'k{k}'] for k in range(1, 5)]
V = len(pickle.load(open(f'{W}/lm/voc.pkl', 'rb')))
for name, stf, K, uni in [('3g raw-uni 1.5GB', 'stats_w3p', 3, 'raw'), ('3g raw-uni 4.5GB', 'stats_w', 3, 'raw'),
                          ('4g raw-uni 4.5GB', 'stats_w', 4, 'raw'), ('4g KN-uni 4.5GB', 'stats_w', 4, 'kn'), ('4g KN-uni 1.5GB', 'stats_w3p', 4, 'kn')]:
    st = np.load(f'{W}/lm/{stf}.npz')
    lp = score(seqs, need[:K], st, K, 0.75, 443481 if '1.5' in name else V, uni=uni)['lp']
    print(f'{name:28s} LM only        {fmt(acc_by_split(lp, T))}')
    best = max(((acc_by_split(lp - l * col(T, "cost") - p * col(T, "ntok"), T)['tune'], l, p) for l in [0, .1, .2, .3, .5, .7, 1] for p in [-1, -.5, 0, .5, 1]))
    _, l, p = best
    print(f'{name:28s} +{l}*cost -{p}*ntok {fmt(acc_by_split(lp - l * col(T, "cost") - p * col(T, "ntok"), T))}', flush=True)

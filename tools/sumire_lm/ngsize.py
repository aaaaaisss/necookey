#!/usr/bin/env python3
"""ngsize.py KIND K [PIECE=0] : distinct n-grams with count >= t (t in TH) in the first N/4, N/2, N tokens of one corpus piece,
per order; fits f ~ N^beta per (order, t) and extrapolates to the full 9-piece corpus. -> WORK/lm/ngsize_{KIND}.json
(used for the on-device size estimate; exact full-corpus count-of-counts would need a disk-based sort)."""
import sys, os, json, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm'); kind, K = sys.argv[1], int(sys.argv[2]); I = int(sys.argv[3]) if len(sys.argv) > 3 else 0
P = np.uint64(0x9E3779B97F4A7C15); TH = [1, 2, 3, 5, 10, 20, 50, 100, 200, 500]
seq = np.load(f'{W}/lm/{"wg" if kind.startswith("w") else "c"}{I}.npy', mmap_mode='r'); N0 = len(seq); FULL = {'w': 963425212, 'c': 1548741764}[kind[0]]
res = {}
FR = [int(x) for x in os.environ.get('SLM_FRACS', '4,2,1').split(',')]
for frac in FR:
    n = N0 // frac; x = np.asarray(seq[:n]).astype(np.uint64); h = x.copy()
    for k in range(1, K + 1):
        if k > 1: h = h[:-1] * P + x[k - 1:]
        _, c = np.unique(h, return_counts=True)
        cc = np.bincount(np.minimum(c, 10**6))[::-1].cumsum()[::-1]  # cc[t] = #(count >= t)
        res[(frac, k)] = [int(cc[t]) if t < len(cc) else 0 for t in TH]
        del c
        print(kind, frac, k, res[(frac, k)][:5], flush=True)
    del x, h
out = {}
for k in range(1, K + 1):
    row = {}
    for j, t in enumerate(TH):
        f = [res[(fr, k)][j] for fr in FR]
        if min(f) <= 0: row[t] = 0; continue
        beta = np.polyfit(np.log([N0 / fr for fr in FR]), np.log(f), 1)[0]
        row[t] = int(f[-1] * (FULL / (N0 / FR[-1])) ** beta)
    out[k] = row
json.dump({'TH': TH, 'raw': {f'{a}_{b}': v for (a, b), v in res.items()}, 'full_est': out}, open(f'{W}/lm/ngsize_{kind}.json', 'w'), indent=1)
print(json.dumps(out))

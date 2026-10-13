#!/usr/bin/env python3
"""Vectorized scoring of many sequences with restricted stats (ngcount/ngmerge).
score(seqs, need, st, K, D, kind) -> dict of per-sequence arrays:
  lp   interpolated absolute discounting (per-order discount D[k]) with KN unigram (continuation counts; uni='raw' = add-0.5 unigram as in the 2026-10-12 baseline)
  (sm='dir': Dirichlet-prior interpolation, D = prior strength; needs counts only)
  sb   stupid backoff (alpha 0.4) log score
  hit{k}  #positions whose longest matched n-gram has order k ; oov  #positions with unseen unigram
First element of a sequence is predicted only if it is not <s> (id 0)."""
import numpy as np
from ngcount import hashes, lookup
def score(seqs, need, st, K, D, V=None, uni='kn', sm='ad', batch=150_000):
    """batched over sequences to bound memory"""
    x, L = seqs if isinstance(seqs, tuple) else (np.concatenate(seqs), np.array([len(s) for s in seqs]))
    st_ = {k: st[k] for k in st.files} if hasattr(st, 'files') else st  # npz or dict
    off = np.r_[0, np.cumsum(L)]; res = []
    for b in range(0, len(L), batch):
        e = min(len(L), b + batch)
        res.append(_score((x[off[b]:off[e]], L[b:e]), need, st_, K, D, V, uni, sm))
    return {k: np.concatenate([r[k] for r in res]) for k in res[0]}
def _score(seqs, need, st, K, D, V=None, uni='kn', sm='ad'):
    x, L = seqs if isinstance(seqs, tuple) else (np.concatenate(seqs), np.array([len(s) for s in seqs]))
    x = x.astype(np.uint32); start = np.cumsum(L) - L
    pos = np.arange(len(x)) - np.repeat(start, L)
    hs = hashes(x, K); n = len(x)
    def get(k, arr_name, h):  # value of stats array for hash h of order k (0 if absent)
        i, m = lookup(need[k - 1], h); v = st[arr_name][i]; return np.where(m, v, 0)
    c1 = get(1, 'c1', hs[0]); nl1 = get(1, 'nl1', hs[0]) if uni == 'kn' else None
    N = float(st['n']); nlt = float(st['nl_tot']) if uni == 'kn' else 0.0
    Vt = V or len(need[0])
    if uni == 'kn': pk = (nl1 + 0.5) / (nlt + 0.5 * Vt)
    else: pk = (c1 + 0.5) / (N + 0.5 * Vt)
    sb = np.log(np.maximum(c1, 0.5) / N)
    best = np.where(c1 > 0, 1, 0)
    for k in range(2, K + 1):
        ok = pos >= k - 1
        j = np.nonzero(ok)[0]; s0 = j - k + 1
        cc = get(k - 1, f'c{k-1}', hs[k - 2][s0]); cf = get(k, f'c{k}', hs[k - 1][s0])
        d = D[k] if isinstance(D, dict) else D
        prev = pk[j]
        if sm == 'ad':
            i, m = lookup(need[k - 2], hs[k - 2][s0]); nf = np.where(m, st[f'nf{k}'][i], 0)
            p = np.where(cc > 0, np.maximum(cf - d, 0) / np.maximum(cc, 1) + d * nf / np.maximum(cc, 1) * prev, prev)
        else:  # Dirichlet prior (no follower counts needed): (c(ctx w) + d * p_lower) / (c(ctx) + d)
            p = (cf + d * prev) / (cc + d)
        pk[j] = p
        sbk = np.where(cf > 0, np.log(np.maximum(cf, 1) / np.maximum(cc, 1)), np.log(0.4) + sb[j])
        sb[j] = sbk
        best[j] = np.where(cf > 0, k, best[j])
    pred = ~((pos == 0) & (x == 0))
    seg = np.repeat(np.arange(len(L)), L)
    def ssum(v): return np.bincount(seg[pred], weights=v[pred], minlength=len(L))
    out = dict(lp=ssum(np.log(pk)), sb=ssum(sb), oov=ssum((c1 == 0).astype(float)))
    for k in range(1, K + 1): out[f'hit{k}'] = ssum((best == k).astype(float))
    return out

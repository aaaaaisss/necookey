#!/usr/bin/env python3
"""ngmerge.py KIND PIECES OUT [WORK] : merge part_{KIND}_{I}.npz for I in PIECES (e.g. 0-8 or 0,1,2) -> WORK/lm/OUT.npz
  c{k}: counts of need k ; nf{k}: N1+(need_{k-1} .) distinct followers (k>=2) ; nl1: N1+(. need_1) ; n: tokens ;
  nl_tot: distinct bigrams whose first token is needed (~ N1+(..)). One order at a time to bound memory."""
import sys, numpy as np
kind, ps, outn = sys.argv[1], sys.argv[2], sys.argv[3]; W = sys.argv[4] if len(sys.argv) > 4 else '/workspace/work/sumire_lm'
P = list(range(int(ps.split('-')[0]), int(ps.split('-')[1]) + 1)) if '-' in ps else [int(x) for x in ps.split(',')]
nd = np.load(f'{W}/lm/need_{kind}.npz'); K = len(nd.files); sizes = [len(nd[f'k{k}']) for k in range(1, K + 1)]
parts = [np.load(f'{W}/lm/part_{kind}_{i}.npz') for i in P]
out = {'n': np.array(sum(int(z['n']) for z in parts))}
for k in range(1, K + 1): out[f'c{k}'] = sum(z[f'c{k}'] for z in parts)
def union(fk, pk):
    u = p = None
    for z in parts:
        a, b = z[fk], z[pk]
        if u is None: u, p = a, b; continue
        uu = np.concatenate([u, a]); pp = np.concatenate([p, b]); del u, p
        uu, j = np.unique(uu, return_index=True); u, p = uu, pp[j]; del pp, j
    return u, p
if 'F2' in parts[0].files:
    for k in range(2, K + 1):
        u, p = union(f'F{k}', f'fp{k}'); out[f'nf{k}'] = np.bincount(p, minlength=sizes[k - 2]).astype(np.int64)
        if k == 2: out['nl_tot'] = np.array(len(u))
        del u, p; print('nf', k, flush=True)
    u, p = union('L2', 'lp2'); out['nl1'] = np.bincount(p, minlength=sizes[0]).astype(np.int64); del u, p
np.savez(f'{W}/lm/{outn}.npz', **out); print('done', int(out['n']))

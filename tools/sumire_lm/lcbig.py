#!/usr/bin/env python3
"""lcbig.py : full-corpus word bigram counts (fugashi tokens of the 4.5 GB CC-100 part, lm/wg0..8.npy, id 0 = sentence start)
for the general (not eval-restricted) lattice bigram table of 案2.
lcbig.py count I     -> $WORK/lcost/bg/p{I}_{j}.npz  (j = key % 8; keys = a<<20 | b, sentence-start pairs dropped)
lcbig.py merge J     -> $WORK/lcost/bg/m{J}.npz      (summed counts over pieces)
lcbig.py stats       -> $WORK/lcost/bg/wstats.npz    (per word: c_ctx(v) = sum_w c(vw), n1f(v) = N1+(v .), n1l(w) = N1+(. w))
                        and bg/pairs.npz (pairs with count >= 2: key, count)"""
import sys, os, numpy as np
WORK = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm'); B = f'{WORK}/lcost/bg'; os.makedirs(B, exist_ok=True)
mode = sys.argv[1]; NP = 8; V = 1 << 20
if mode == 'count':
    for i in sys.argv[2].split(','):
        a = np.load(f'{WORK}/lm/wg{i}.npy')
        x = a[:-1]; y = a[1:]; m = (x != 0) & (y != 0)
        k = (x[m].astype(np.uint64) << np.uint64(20)) | y[m].astype(np.uint64); del a, x, y, m
        k.sort(); st = np.r_[0, np.nonzero(k[1:] != k[:-1])[0] + 1]; u = k[st]; c = np.diff(np.r_[st, len(k)]).astype(np.uint32); del k
        for j in range(NP):
            s = (u & np.uint64(NP - 1)) == j; np.savez(f'{B}/p{i}_{j}.npz', k=u[s], c=c[s])
        print('piece', i, len(u), flush=True)
elif mode == 'merge':
    for j in sys.argv[2].split(','):
        ks = []; cs = []
        for i in range(9):
            z = np.load(f'{B}/p{i}_{j}.npz'); ks.append(z['k']); cs.append(z['c'])
        k = np.concatenate(ks); c = np.concatenate(cs); del ks, cs
        o = np.argsort(k, kind='stable'); k = k[o]; c = c[o]; del o
        st = np.r_[0, np.nonzero(k[1:] != k[:-1])[0] + 1]; u = k[st]; s = np.add.reduceat(c.astype(np.uint64), st).astype(np.uint32)
        np.savez(f'{B}/m{j}.npz', k=u, c=s); print('part', j, len(u), flush=True)
elif mode == 'stats':
    cctx = np.zeros(V, np.uint64); n1f = np.zeros(V, np.uint32); n1l = np.zeros(V, np.uint32); pk = []; pc = []
    for j in range(NP):
        z = np.load(f'{B}/m{j}.npz'); k = z['k']; c = z['c']
        v = (k >> np.uint64(20)).astype(np.int64); w = (k & np.uint64(V - 1)).astype(np.int64)
        cctx += np.bincount(v, c.astype(np.float64), V).astype(np.uint64); n1f += np.bincount(v, minlength=V).astype(np.uint32)
        n1l += np.bincount(w, minlength=V).astype(np.uint32)
        m = c >= 2; pk.append(k[m]); pc.append(c[m]); print('part', j, len(k), int(m.sum()), flush=True)
    np.savez(f'{B}/wstats.npz', cctx=cctx, n1f=n1f, n1l=n1l)
    k = np.concatenate(pk); c = np.concatenate(pc); o = np.argsort(k); np.savez(f'{B}/pairs.npz', k=k[o], c=c[o])
    print('pairs>=2', len(k), 'N1+ total', int(n1l.sum()))

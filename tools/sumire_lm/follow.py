#!/usr/bin/env python3
"""follow.py count KIND I [KMAX [KMIN]]  /  follow.py merge KIND k
Follower-count statistics of every needed context, so that a count-cutoff pruned model can be renormalized exactly:
for each context h in need_{KIND} order k-1 and each cutoff t in TH: S_t(h) = sum of c(h w) over followers w with c(h w) >= t,
N_t(h) = number of such followers (full 9-piece corpus counts).
count: piece I, orders 2..KMAX -> WORK/od/fol/{KIND}_{k}_{I}_{r}.npz (r = 8 hash partitions: unique k-gram hash, count, prefix index)
merge: all pieces of order k, partition by partition -> WORK/od/fol_{KIND}{k}.npz : S (n_ctx x len(TH)) int64, N int32, TH
KIND: w (fugashi need set), ws (Sumire-seg need set), c (chars). Corpus token ids are always the fugashi ones."""
import sys, os, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ngcount import hashes, lookup
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm'); TH = [1, 2, 3, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 4000]
NP = 8; D = f'{W}/od/fol'; os.makedirs(D, exist_ok=True)
mode, kind = sys.argv[1], sys.argv[2]
nd = np.load(f'{W}/lm/need_{kind}.npz')
def consolidate(lst):
    u = np.concatenate([a[0] for a in lst]); c = np.concatenate([a[1] for a in lst]); p = np.concatenate([a[2] for a in lst])
    uu, inv = np.unique(u, return_inverse=True); cc = np.bincount(inv, c).astype(np.uint32); pp = np.zeros(len(uu), np.uint32); pp[inv] = p
    return uu, cc, pp
if mode == 'count':
    I = int(sys.argv[3]); KM = int(sys.argv[4]) if len(sys.argv) > 4 else len(nd.files); K0 = int(sys.argv[5]) if len(sys.argv) > 5 else 2
    need = [nd[f'k{k}'] for k in range(1, KM)]
    seq = np.load(f'{W}/lm/{"wg" if kind.startswith("w") else "c"}{I}.npy', mmap_mode='r'); N = len(seq); CH = 12_000_000
    acc = {k: [] for k in range(K0, KM + 1)}
    for st in range(0, N, CH):
        x = np.asarray(seq[st:min(N, st + CH + KM - 1)]); hs = hashes(x, KM); lim = min(CH, N - st)
        for k in range(K0, KM + 1):
            hk = hs[k - 1][:lim]; n_ = len(hk)
            i, m = lookup(need[k - 2], hs[k - 2][:n_])
            u, inv, c = np.unique(hk[m], return_inverse=True, return_counts=True)
            p = np.zeros(len(u), np.uint32); p[inv] = i[m]
            acc[k].append((u, c.astype(np.uint32), p))
            if len(acc[k]) >= 4: acc[k] = [consolidate(acc[k])]  # bound memory (chars)
        del hs, x
        print(kind, I, st, flush=True)
    for k in range(K0, KM + 1):
        uu, cc, pp = consolidate(acc[k]); acc[k] = None
        r = (uu >> np.uint64(61)).astype(np.int64)
        for q in range(NP):
            m = r == q; np.savez(f'{D}/{kind}_{k}_{I}_{q}.npz', h=uu[m], c=cc[m], p=pp[m])
        print('saved', kind, k, I, len(uu), flush=True)
else:
    k = int(sys.argv[3]); nctx = len(nd[f'k{k-1}'])
    S = np.zeros((nctx, len(TH)), np.int64); Nn = np.zeros((nctx, len(TH)), np.int32)
    for q in range(NP):
        parts = [np.load(f'{D}/{kind}_{k}_{I}_{q}.npz') for I in range(9)]
        h = np.concatenate([z['h'] for z in parts]); c = np.concatenate([z['c'] for z in parts]).astype(np.int64)
        p = np.concatenate([z['p'] for z in parts]); del parts
        uu, inv = np.unique(h, return_inverse=True); cc = np.bincount(inv, c).astype(np.int64); pp = np.zeros(len(uu), np.int64); pp[inv] = p
        del h, c, p, inv
        for j, t in enumerate(TH):
            m = cc >= t
            S[:, j] += np.bincount(pp[m], cc[m], nctx).astype(np.int64); Nn[:, j] += np.bincount(pp[m], None, nctx).astype(np.int32)
        print('merged', kind, k, q, len(uu), flush=True)
    np.savez(f'{W}/od/fol_{kind}{k}.npz', S=S, N=Nn, TH=np.array(TH))
    print('done', kind, k)

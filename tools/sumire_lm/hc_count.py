#!/usr/bin/env python3
"""hc_count.py members        -> $HC/members.npz (member strings / voc ids from pairs.pkl, content-word mask over voc)
   hc_count.py count I        -> $HC/cnt_I.npz : for occurrences of member tokens in lm/wg{I}.npy (cap CAP per member per chunk),
                                 counts of content tokens within +-WIN (same line); key = midx<<20 | ctx_id; separate counts for |d|<=NEAR
   hc_count.py merge 0-8      -> $HC/cnt.npz"""
import sys, os, pickle, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hc_common import *
WIN, NEAR, CAP = 15, 5, int(os.environ.get('HC_CAP', 2500))
mode = sys.argv[1]
if mode == 'members':
    voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb'))
    pairs = pickle.load(open(f'{HC}/pairs.pkl', 'rb'))
    M = sorted({m for k in pairs for m in k}); ids = np.array([voc[m] for m in M], np.int64)
    V = len(voc); content = np.zeros(V, bool); kata = re.compile(r'^[\u30a0-\u30ffー]+$')
    for w, i in voc.items():
        if len(w) >= 2 and (has_kan(w) or kata.match(w)): content[i] = True
    np.savez(f'{HC}/members.npz', M=np.array(M, dtype=object), ids=ids, content=content)
    print('members', len(M), 'content vocab', content.sum())
elif mode == 'count':
    I = int(sys.argv[2]); z = np.load(f'{HC}/members.npz', allow_pickle=True); ids = z['ids']; content = z['content']
    midx = np.full(len(content), -1, np.int64); midx[ids] = np.arange(len(ids))
    x = np.load(f'{W}/lm/wg{I}.npy', mmap_mode='r'); N = len(x); CH = 20_000_000
    seen = np.zeros(len(ids), np.int64); keysA = []; keysN = []
    for st in range(0, N, CH):
        lo = max(0, st - WIN); y = np.asarray(x[lo:min(N, st + CH + WIN)]).astype(np.int64)
        line = np.cumsum(y == 0)
        pos = np.nonzero((midx[y] >= 0))[0]; pos = pos[(pos >= st - lo) & (pos < st - lo + CH)]
        m = midx[y[pos]]
        # cap: keep first CAP-seen occurrences per member
        o = np.argsort(m, kind='stable'); ms = m[o]; first = np.r_[0, np.nonzero(np.diff(ms))[0] + 1]
        rk = np.arange(len(ms)) - np.repeat(first, np.diff(np.r_[first, len(ms)]))
        keep = o[rk + seen[ms] < CAP]; np.add.at(seen, m[keep], 1)
        pos = pos[keep]; m = midx[y[pos]]
        for d in list(range(-WIN, 0)) + list(range(1, WIN + 1)):
            q = pos + d; ok = (q >= 0) & (q < len(y))
            q2 = np.where(ok, q, 0); ok &= (line[q2] == line[pos]) & content[y[q2]]
            k = (m[ok] << 20) | y[q2[ok]]
            keysA.append(k)
            if abs(d) <= NEAR: keysN.append(k)
        print(I, st, len(pos), flush=True)
    def uc(ks):
        k = np.concatenate(ks) if ks else np.zeros(0, np.int64); u, c = np.unique(k, return_counts=True); return u, c
    ua, ca = uc(keysA); un, cn = uc(keysN)
    np.savez(f'{HC}/cnt_{I}.npz', ua=ua, ca=ca.astype(np.int32), un=un, cn=cn.astype(np.int32), occ=seen)
    print('done', I, len(ua), len(un), seen.sum())
else:
    a, b = map(int, sys.argv[2].split('-')); occ = 0
    # merge per member range (memory: the sandbox has ~3 GB free) -> $HC/cnt_r{j}.npz, j = 0..NR-1
    NR = 6; nm = len(np.load(f'{HC}/members.npz', allow_pickle=True)['ids']); edges = np.linspace(0, nm, NR + 1).astype(np.int64)
    occ = sum(np.load(f'{HC}/cnt_{i}.npz')['occ'] for i in range(a, b + 1)); np.save(f'{HC}/cnt_occ.npy', occ)
    for j in range(NR):
        lo, hi = edges[j] << 20, edges[j + 1] << 20; out = {}
        for kind in ('a', 'n'):
            ks, cs = [], []
            for i in range(a, b + 1):
                z = np.load(f'{HC}/cnt_{i}.npz'); u = z['u' + kind]; s0, s1 = np.searchsorted(u, [lo, hi])
                ks.append(u[s0:s1]); cs.append(z['c' + kind][s0:s1]); del z, u
            k = np.concatenate(ks); c = np.concatenate(cs); del ks, cs
            o = np.argsort(k, kind='stable'); k = k[o]; c = c[o]; del o
            f = np.r_[True, k[1:] != k[:-1]]; idx = np.cumsum(f) - 1
            out['u' + kind] = k[f]; out['c' + kind] = np.bincount(idx, weights=c).astype(np.int32); del k, c, f, idx
        np.savez(f'{HC}/cnt_r{j}.npz', **out); print('range', j, len(out['ua']), len(out['un']), flush=True)

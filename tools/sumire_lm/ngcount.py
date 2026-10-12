#!/usr/bin/env python3
"""Restricted n-gram statistics with 64-bit polynomial hashed keys (no V limit, any order).
usage: ngcount.py KIND I [WORK] [--noF]   (--noF: counts only, no follower/continuation sets; used for chars, memory)   KIND = w (word ids, lm/wg{I}.npy) | c (code points, lm/c{I}.npy)
needs WORK/lm/need_{KIND}.npz (arrays k1..kK: sorted unique hashes of every k-gram the candidates need; made by feats.py)
writes WORK/lm/part_{KIND}_{I}.npz:
  c{k}  int64 counts for need k
  F{k}  unique hashes of corpus k-grams whose (k-1)-prefix is needed (k>=2), with fp{k} = prefix index  -> N1+(ctx .)
  L2/lp2 same for the suffix unigram of bigrams (KN unigram continuation N1+(. w))
  n     tokens in piece
Hash: h1 = id, hk = h(k-1)*P + id (mod 2^64)."""
import sys, numpy as np
P = np.uint64(0x9E3779B97F4A7C15)
def hashes(x, K):
    """list of arrays; hs[k-1][j] = hash of x[j:j+k]"""
    hs = [x.astype(np.uint64)]
    for k in range(2, K + 1):
        hs.append(hs[-1][:-1] * P + hs[0][k - 1:])
    return hs
def lookup(need, h):
    i = np.searchsorted(need, h); i[i == len(need)] = 0
    return i, need[i] == h
if __name__ == '__main__':
    noF = '--noF' in sys.argv; sys.argv = [a for a in sys.argv if a != '--noF']
    kind, I = sys.argv[1], int(sys.argv[2]); W = sys.argv[3] if len(sys.argv) > 3 else '/workspace/work/sumire_lm'
    nd = np.load(f'{W}/lm/need_{kind}.npz'); K = len(nd.files)
    need = [nd[f'k{k}'] for k in range(1, K + 1)]
    seq = np.load(f'{W}/lm/{"wg" if kind.startswith("w") else "c"}{I}.npy', mmap_mode='r')  # kind ws = word ids, Sumire-seg need set
    N = len(seq); CH = 15_000_000
    cnt = [np.zeros(len(n), np.int64) for n in need]
    F = [[] for _ in range(K + 1)]; FP = [[] for _ in range(K + 1)]; L2 = []
    for st in range(0, N, CH):
        x = np.asarray(seq[st:min(N, st + CH + K - 1)])
        hs = hashes(x, K)
        lim = min(CH, N - st)  # windows starting in [st, st+lim)
        idx = []
        for k in range(1, K + 1):
            h = hs[k - 1][:lim]
            i, m = lookup(need[k - 1], h)
            cnt[k - 1] += np.bincount(i[m], minlength=len(need[k - 1]))
            idx.append((i, m))
        for k in range(2, K + 1 if not noF else 1):
            hk = hs[k - 1][:lim]; n_ = len(hk)
            i, m = idx[k - 2][0][:n_], idx[k - 2][1][:n_]   # prefix (k-1)-gram at same start
            u, j = np.unique(hk[m], return_index=True)
            F[k].append(u); FP[k].append(i[m][j].astype(np.uint32))
        if noF: print(kind, I, st, flush=True); continue
        # suffix of bigram = unigram at start+1
        h2 = hs[1][:lim]; n_ = len(h2)
        i1, m1 = lookup(need[0], hs[0][1:n_ + 1])
        u, j = np.unique(h2[m1], return_index=True)
        L2.append((u, i1[m1][j].astype(np.uint32)))
        print(kind, I, st, flush=True)
    out = {'n': np.array(N)}
    for k in range(1, K + 1): out[f'c{k}'] = cnt[k - 1]
    for k in range(2, K + 1 if not noF else 1):
        u = np.concatenate(F[k]); p = np.concatenate(FP[k]); u, j = np.unique(u, return_index=True)
        out[f'F{k}'] = u; out[f'fp{k}'] = p[j]
    if noF: np.savez(f'{W}/lm/part_{kind}_{I}.npz', **out); print('done', kind, I, N); sys.exit()
    u = np.concatenate([a for a, b in L2]); p = np.concatenate([b for a, b in L2]); u, j = np.unique(u, return_index=True)
    out['L2'] = u; out['lp2'] = p[j]
    np.savez(f'{W}/lm/part_{kind}_{I}.npz', **out)
    print('done', kind, I, N)

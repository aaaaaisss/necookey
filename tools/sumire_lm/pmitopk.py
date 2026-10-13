#!/usr/bin/env python3
"""pmitopk.py count | flags : top-K-partners pruning of the PMI pair table.
count : unordered content-word pair counts (window 15, same line; content = heuristic of pairsize.py) on corpus piece 0
        (~1/9 of the 4.5GB corpus), chunked -> od/pairs_s0.npz (key = lo<<23|hi, count)
flags : per word a, rank its partners b by PMI (= log c(a,b) - log c(b) + const, counts scaled x N_full/N_piece0) among pairs with
        scaled count >= floor; a pair is kept iff it is in the top-K of either word. Applied to the need pairs of od/prep_{fug,seg}.npz
        with their full-corpus counts -> od/pmi_topk_{VAR}.npz (bool per need pair, key '{floor}_{K}');
        size estimate (#kept unordered pairs in the scaled sample) -> od/pmi_size_{VAR}.json (same for both variants)."""
import sys, os, re, json, pickle, numpy as np
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm'); WIN = 15
FLOORS = [5, 10, 20, 50]; KS = [4, 8, 16, 32, 64, 128]
if sys.argv[1] == 'count':
    voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb')); isc = np.zeros(1 << 23, bool)
    NUM = re.compile(r'^[0-9〇一二三四五六七八九十百千万億]+$'); CONT = re.compile(r'[一-龠々ァ-ヺー]')
    for w, i in voc.items():
        if not NUM.match(w) and (CONT.search(w) or re.fullmatch(r'[A-Za-z]{2,}', w)): isc[i] = True
    seq = np.load(f'{W}/lm/wg0.npy', mmap_mode='r'); N0 = len(seq); CH = 12_000_000; acc_k = acc_c = None
    for st in range(0, N0, CH):
        y = np.asarray(seq[st:min(N0, st + CH + WIN)]).astype(np.int64); lim = min(CH, N0 - st)
        if len(y) < lim + WIN: y = np.concatenate([y, np.zeros(lim + WIN - len(y), np.int64)])
        line = np.cumsum(y == 0); c = isc[y]; ks = []
        for d in range(1, WIN + 1):
            m = c[:lim] & c[d:lim + d] & (line[:lim] == line[d:lim + d]); a = y[:lim][m]; b = y[d:lim + d][m]
            ks.append((np.minimum(a, b) << 23) | np.maximum(a, b))
        k, cnt = np.unique(np.concatenate(ks), return_counts=True); del ks
        if acc_k is None: acc_k, acc_c = k, cnt
        else:
            kk = np.concatenate([acc_k, k]); cc = np.concatenate([acc_c, cnt]); acc_k, inv = np.unique(kk, return_inverse=True)
            acc_c = np.bincount(inv, cc).astype(np.int64); del kk, cc, inv
        print(st, len(acc_k), flush=True)
    np.savez(f'{W}/od/pairs_s0.npz', k=acc_k, c=acc_c, n=np.array(N0)); print('pairs', len(acc_k))
else:
    z = np.load(f'{W}/od/pairs_s0.npz'); k = z['k']; c = z['c'].astype(np.float64); N0 = float(z['n'])
    uni = np.load(f'{W}/lm/uni_full.npy').astype(np.float64); FULL = 963425212.0; sc = FULL / N0
    lo = (k >> 23).astype(np.int64); hi = (k & ((1 << 23) - 1)).astype(np.int64); cs = c * sc
    ul = np.maximum(uni[np.minimum(lo, len(uni) - 1)], 1); uh = np.maximum(uni[np.minimum(hi, len(uni) - 1)], 1)
    thr = {}; sizes = {}
    for f in FLOORS:
        m = cs >= f; l_, h_, cc = lo[m], hi[m], cs[m]; sizes[f'{f}_0'] = int(m.sum())
        # directed views: (word, partner score)
        wv = np.r_[l_, h_]; sv = np.r_[np.log(cc) - np.log(uh[m]), np.log(cc) - np.log(ul[m])]; pid = np.r_[np.arange(len(l_)), np.arange(len(l_))]
        o = np.lexsort((-sv, wv)); wv_o = wv[o]; start = np.r_[0, np.nonzero(np.diff(wv_o))[0] + 1]
        rank = np.arange(len(o)) - np.repeat(start, np.diff(np.r_[start, len(o)]))
        for K in KS:
            kept = np.zeros(len(l_), bool); kept[pid[o][rank < K]] = True; sizes[f'{f}_{K}'] = int(kept.sum())
            # per-word threshold = score of its K-th partner (-inf if fewer)
            t = np.full(1 << 23, -np.inf); sel = rank == K - 1; t[wv_o[sel]] = sv[o][sel]; thr[(f, K)] = t
        print('floor', f, 'pairs', int(m.sum()), {K: sizes[f'{f}_{K}'] for K in KS}, flush=True)
    for VAR in ('fug', 'seg'):
        Z = np.load(f'{W}/od/prep_{VAR}.npz'); a = Z['pa']; b = Z['pb']; cab = Z['pcnt'].astype(np.float64)
        ua = np.maximum(Z['pua'], 1).astype(np.float64); ub = np.maximum(Z['pub'], 1).astype(np.float64)
        sa = np.log(np.maximum(cab, 1e-9)) - np.log(ub); sb = np.log(np.maximum(cab, 1e-9)) - np.log(ua)
        out = {}
        for (f, K), t in thr.items(): out[f'{f}_{K}'] = (cab >= f) & ((sa >= t[a]) | (sb >= t[b]))
        np.savez(f'{W}/od/pmi_topk_{VAR}.npz', **out)
        json.dump(sizes, open(f'{W}/od/pmi_size_{VAR}.json', 'w'), indent=1)
    print(json.dumps(sizes))

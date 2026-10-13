#!/usr/bin/env python3
"""Context-word co-occurrence for topical PMI features.
cooc.py need [WORK]     (items_{s}.pkl for s in env SLM_SHARDS, else items.pkl) -> lm/need_pair.npz : hashes a*P+b for (a = content word in context, b = content word that
                           distinguishes the candidates of the same sentence, i.e. not in every candidate)
cooc.py count I [WORK]  -> lm/pair_{I}.npz : #times a and b occur within WIN tokens of each other in the same line (either order)
cooc.py merge 0-8 [WORK]-> lm/pair.npz"""
import sys, os, pickle, numpy as np
PS = os.environ.get('SLM_PAIR', '')  # output suffix (e.g. _seg)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__))); from common import rowdict, content
P = np.uint64(0x9E3779B97F4A7C15); WIN = 15
mode = sys.argv[1]; W = sys.argv[3] if len(sys.argv) > 3 else (sys.argv[2] if mode == 'need' and len(sys.argv) > 2 else '/workspace/work/sumire_lm')
if mode == 'need':
    voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb'))
    sh = os.environ.get('SLM_SHARDS'); files = [f'{W}/items_{s}.pkl' for s in sh.split(',')] if sh else [f'{W}/items.pkl']
    keys = set()
    for i, sp, exp, ctx, rows in (it for fn in files for it in pickle.load(open(fn, 'rb'))):
        if not rows: continue
        rows = [rowdict(t) for t in rows]
        r0 = rows[0]; A = {voc[w] for w in content(r0, 0, r0['nc']) if w in voc}
        if not A: continue
        cs = [content(r, r['nc'], len(r['wt'])) for r in rows]
        common = set.intersection(*cs) if cs else set()
        B = {voc[w] for c in cs for w in c - common if w in voc}
        for a in A:
            for b in B: keys.add((a, b))
    a = np.array([k[0] for k in keys], np.uint64); b = np.array([k[1] for k in keys], np.uint64)
    h = np.unique(a * P + b); np.savez(f'{W}/lm/need_pair{PS}.npz', h=h, a=np.unique(a), b=np.unique(b)); print('pairs', len(h))
elif mode == 'count':
    I = int(sys.argv[2]); nd = np.load(f'{W}/lm/need_pair{PS}.npz'); need = nd['h']
    inA = np.zeros(1 << 23, bool); inB = np.zeros(1 << 23, bool)
    inA[nd['a'].astype(np.int64)] = True; inB[nd['b'].astype(np.int64)] = True
    x = np.load(f'{W}/lm/wg{I}.npy', mmap_mode='r'); N = len(x); CH = 20_000_000
    cnt = np.zeros(len(need), np.int64)
    for st in range(0, N, CH):
        y = np.asarray(x[st:min(N, st + CH + WIN)]).astype(np.int64)
        if len(y) < CH + WIN: y = np.concatenate([y, np.zeros(CH + WIN - len(y), np.int64)])  # pad with line breaks
        line = np.cumsum(y == 0)
        a_ = inA[y]; b_ = inB[y]; lim = min(CH, N - st)
        for d in range(1, WIN + 1):
            l, r = y[:lim], y[d:lim + d]; same = line[:lim] == line[d:lim + d]
            for m, u, v in ((a_[:lim] & b_[d:lim + d] & same, l, r), (b_[:lim] & a_[d:lim + d] & same, r, l)):
                h = u[m].astype(np.uint64) * P + v[m].astype(np.uint64)
                i = np.searchsorted(need, h); i[i == len(need)] = 0; ok = need[i] == h
                cnt += np.bincount(i[ok], minlength=len(need))
        print(I, st, flush=True)
    np.savez(f'{W}/lm/pair{PS}_{I}.npz', c=cnt)
else:
    a, b = sys.argv[2].split('-'); c = sum(np.load(f'{W}/lm/pair{PS}_{i}.npz')['c'] for i in range(int(a), int(b) + 1))
    np.savez(f'{W}/lm/pair{PS}.npz', c=c); print('merged', c.sum())

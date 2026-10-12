#!/usr/bin/env python3
"""feats.py [SHARD] [WORK] : SHARD = e (AJIMEE + JWTD) | g0, g1, ... (auto ids g<n> with n // 20000 == K) | all (default, legacy names)
Outputs get the suffix _{SHARD} (items_{SHARD}.pkl, lm/seqs_{SHARD}.npz, lm/need_{w,c}_{SHARD}.npz); union the need files with needmerge.py.
 read data/{ajimee,jwtd_kc,auto}.tsv + cands/cands.*.tsv (SumireNbestDumpTest output)
-> WORK/items.pkl : list of (id, split, expected(set), ctx, rows); row = tuple ROWK (rank, s, type, cost, lid, rid, nseg,
     wt (space-joined word tokens of NFKC(ctx+s), fugashi unidic-lite), pos (space-joined pos1-pos2), nc (#tokens fully inside ctx), bos)
-> WORK/lm/need_w.npz (k1..k4) and need_c.npz (k1..k7): hashes of every k-gram of [<s>?]+ctx+s (word ids / code points)
bos: <s> (id 0) is prefixed only when len(ctx) < 20 (the clause is near the line start; ctx is cut at 20 chars)."""
import sys, os, glob, pickle, unicodedata, numpy as np, fugashi
sys.path.insert(0, os.path.dirname(__file__)); from ngcount import hashes
SH = sys.argv[1] if len(sys.argv) > 1 else 'all'
W = sys.argv[2] if len(sys.argv) > 2 else '/workspace/work/sumire_lm'
SUF = '' if SH == 'all' else f'_{SH}'
def in_shard(i):
    if SH == 'all': return True
    if SH == 'e': return i[0] in 'aj'
    return i[0] == 'g' and int(i[1:]) // 20000 == int(SH[1:])
ROWK = ('rank', 's', 'type', 'cost', 'lid', 'rid', 'nseg', 'wt', 'pos', 'nc', 'bos')  # row tuple layout (wt/pos space-joined)
KW, KC = 4, 7; UNK = np.uint32(0xFFFFFFFF)
t = fugashi.Tagger()
meta = {}
for fn in ['ajimee', 'jwtd_kc', 'auto']:
    p = f'{W}/data/{fn}.tsv'
    if not os.path.exists(p): continue
    for l in open(p):
        f = l.rstrip('\n').split('\t'); meta[f[0]] = (f[1], f[2], set(f[4].split('|')))
items = {}
for fn in sorted(glob.glob(f'{W}/cands/cands.*.tsv')):
    for l in open(fn):
        f = l.rstrip('\n').split('\t')
        if len(f) < 8 or f[0] not in meta or not in_shard(f[0]): continue
        items.setdefault(f[0], []).append((int(f[1]), f[2], int(f[3]), int(f[4]),
            int(f[5]) if f[5] else -1, int(f[6]) if f[6] else -1, f[7].count('\x1f') + 1 if f[7] else 0))
voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb'))
out = []; wseqs = []; cseqs = []
for n_, i in enumerate(list(items)):
    raw = sorted(items.pop(i)); rows = []
    sp, ctx, exp = meta[i]
    nctx = unicodedata.normalize('NFKC', ctx); bos = len(ctx) < 20
    for rk, sf, ty, co, li, ri, ns in raw:
        r = dict(rank=rk, s=sf, type=ty, cost=co, lid=li, rid=ri, nseg=ns)
        s = unicodedata.normalize('NFKC', ctx + sf)
        ws = t(s); wt = [w.surface for w in ws]
        # tokens fully inside ctx
        acc = 0; nc = 0
        for w in wt:
            if acc + len(w) <= len(nctx): acc += len(w); nc += 1
            else: break
        r['wt'] = ' '.join(wt); r['pos'] = ' '.join(f'{w.feature.pos1}-{w.feature.pos2}' for w in ws); r['nc'] = nc; r['bos'] = bos
        rows.append(tuple(r[k] for k in ROWK))
        ids = [voc.get(w, UNK) for w in wt]
        wseqs.append(np.array(([0] if bos else []) + ids, np.uint32))
        cseqs.append(np.array(([0] if bos else []) + [ord(ch) for ch in s], np.uint32))
    out.append((i, sp, exp, ctx, rows))
    if n_ % 2000 == 0: print(n_, flush=True)
pickle.dump(out, open(f'{W}/items{SUF}.pkl', 'wb')); nitems = len(out); del out, items
wseqs = (np.concatenate(wseqs), np.array([len(a) for a in wseqs])); cseqs = (np.concatenate(cseqs), np.array([len(a) for a in cseqs]))
np.savez(f'{W}/lm/seqs{SUF}.npz', wx=wseqs[0], wl=wseqs[1], cx=cseqs[0], cl=cseqs[1])
P = np.uint64(0x9E3779B97F4A7C15)
def need(seqs, K):
    x, L = seqs; st = np.repeat(np.cumsum(L) - L, L)
    rem = np.repeat(L, L) - (np.arange(len(x)) - st); del st  # windows starting here that fit: k <= rem
    x = x.astype(np.uint64); h = x.copy(); res = {}
    for k in range(1, K + 1):
        if k > 1: h = h[:-1] * P + x[k - 1:]
        res[f'k{k}'] = np.unique(h[rem[:len(h)] >= k])
    return res
np.savez(f'{W}/lm/need_w{SUF}.npz', **need(wseqs, KW)); np.savez(f'{W}/lm/need_c{SUF}.npz', **need(cseqs, KC))
print('items', nitems, 'rows', len(wseqs[1]))

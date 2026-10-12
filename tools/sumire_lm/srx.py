#!/usr/bin/env python3
"""Split-constrained re-conversion (修正案1) evaluation on top of the existing rerank pipeline.
Work dir W2 = $WORK/srx (env SLM_SRX). New dump (SumireNbestDumpTest with NK_SPLIT_RECONV) in W2/cands/cands.*.tsv:
top-50 rows (rank 0..49) + extra rows (rank 50.., col 9 = 'X', col 10 = pattern index, col 11 = max word rank, col 12 = pattern from N-best).
Only the n-grams / pairs that are NOT already in the old need sets are counted again over the corpus (W2/delta), then merged.

srx.py prep            : W2 dirs + symlinks (data, corpus pieces, vocab)
  (then)  python3 feats.py all W2            -> W2/items.pkl, W2/lm/seqs.npz, need_w.npz, need_c.npz
          SLM_WORK=W2 ... cooc.py need W2   -> W2/lm/need_pair.npz
srx.py delta           : W2/delta/lm/need_{w,c}.npz, need_pair.npz = keys missing from the old need sets
  (then)  ngcount.py w I W2/delta ; ngcount.py c I W2/delta --noF ; cooc.py count I W2/delta   (I = 0..8)
          ngmerge.py w 0-8 stats_w W2/delta ; ngmerge.py c 0-8 stats_c W2/delta ; cooc.py merge 0-8 W2/delta
srx.py combine         : W2/lm/stats_w.npz, stats_c.npz, pair.npz aligned to the W2 need sets (old values for old keys)
  (then)  SLM_WORK=W2 rr.py feats            -> W2/ftab.npz
srx.py xflags          : W2/xflags.npz (per ftab row: extra?, pattern index, max word rank, pattern from N-best)
srx.py view K m [kinds]: W2/view_K{K}_m{m}/ (filtered cands, ftab.npz, scores/lin_base+lmw+lmc+pmi_tune.npy) for bunseg.py / bunsum.py
srx.py eval            : Sumire top1 / adopted linear model (base+lmw+lmc+pmi, trained on JWTD tune, l2 by inner CV on tune)
                         for the original pool and for pools with extras of pattern index < K and word rank < m.
                         AJIMEE (split eval) is reported only."""
import sys, os, glob, json, numpy as np
WORK = os.environ.get('SLM_WORK0', '/workspace/work/sumire_lm')
W2 = os.environ.get('SLM_SRX', f'{WORK}/srx')
D = f'{W2}/delta'
mode = sys.argv[1]

def link(src, dst):
    if not os.path.lexists(dst): os.symlink(src, dst)

if mode == 'prep':
    for d in (W2, f'{W2}/lm', f'{W2}/cands', D, f'{D}/lm'): os.makedirs(d, exist_ok=True)
    link(f'{WORK}/data', f'{W2}/data')
    for w in (f'{W2}/lm', f'{D}/lm'):
        link(f'{WORK}/lm/voc.pkl', f'{w}/voc.pkl')
        for f in glob.glob(f'{WORK}/lm/wg*.npy') + glob.glob(f'{WORK}/lm/c[0-9].npy'): link(f, f'{w}/{os.path.basename(f)}')
    print('ok')

elif mode == 'delta':
    for kind in 'wc':
        old = np.load(f'{WORK}/lm/need_{kind}.npz'); new = np.load(f'{W2}/lm/need_{kind}.npz')
        out = {k: np.setdiff1d(new[k], old[k], assume_unique=True) for k in new.files}
        np.savez(f'{D}/lm/need_{kind}.npz', **out)
        print(kind, 'new', [len(new[k]) for k in new.files], 'delta', [len(v) for v in out.values()])
    old = np.load(f'{WORK}/lm/need_pair.npz'); new = np.load(f'{W2}/lm/need_pair.npz')
    P = np.uint64(0x9E3779B97F4A7C15)
    h = np.setdiff1d(new['h'], old['h'], assume_unique=True)
    # a/b index arrays only restrict which tokens are looked at: keep the new ones (superset is fine)
    np.savez(f'{D}/lm/need_pair.npz', h=h, a=new['a'], b=new['b']); print('pairs new', len(new['h']), 'delta', len(h))

elif mode == 'combine':
    def take(newk, oldk, oldv, dk, dv):
        """values for sorted unique keys newk: from (oldk, oldv) if present else (dk, dv)"""
        out = np.zeros(len(newk), oldv.dtype)
        i = np.searchsorted(oldk, newk); i[i == len(oldk)] = 0; m = oldk[i] == newk
        out[m] = oldv[i[m]]
        if (~m).any():
            j = np.searchsorted(dk, newk[~m]); j[j == len(dk)] = 0
            assert (dk[j] == newk[~m]).all(), 'delta key missing'
            out[~m] = dv[j]
        return out
    for kind, K in (('w', 4), ('c', 7)):
        on = np.load(f'{WORK}/lm/need_{kind}.npz'); nn = np.load(f'{W2}/lm/need_{kind}.npz'); dn = np.load(f'{D}/lm/need_{kind}.npz')
        os_ = np.load(f'{WORK}/lm/stats_{kind}.npz'); ds = np.load(f'{D}/lm/stats_{kind}.npz')
        out = {'n': os_['n']}
        if 'nl_tot' in os_.files: out['nl_tot'] = os_['nl_tot']
        for k in range(1, K + 1):
            out[f'c{k}'] = take(nn[f'k{k}'], on[f'k{k}'], os_[f'c{k}'], dn[f'k{k}'], ds[f'c{k}'])
            if f'nf{k+1}' in os_.files:
                out[f'nf{k+1}'] = take(nn[f'k{k}'], on[f'k{k}'], os_[f'nf{k+1}'], dn[f'k{k}'], ds[f'nf{k+1}'])
        if 'nl1' in os_.files: out['nl1'] = take(nn['k1'], on['k1'], os_['nl1'], dn['k1'], ds['nl1'])
        np.savez(f'{W2}/lm/stats_{kind}.npz', **out); print(kind, 'ok', int(out['n']), int(ds['n']))
    on = np.load(f'{WORK}/lm/need_pair.npz'); nn = np.load(f'{W2}/lm/need_pair.npz'); dn = np.load(f'{D}/lm/need_pair.npz')
    c = take(nn['h'], on['h'], np.load(f'{WORK}/lm/pair.npz')['c'], dn['h'], np.load(f'{D}/lm/pair.npz')['c'])
    np.savez(f'{W2}/lm/pair.npz', c=c); print('pair ok')

elif mode == 'xflags':
    import pickle
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    fl = {}
    for fn in glob.glob(f'{W2}/cands/cands.*.tsv'):
        for l in open(fn):
            f = l.rstrip('\n').split('\t')
            if len(f) >= 12 and f[8] == 'X': fl[(f[0], f[2])] = (int(f[9]), int(f[10]), int(f[11]), f[12] if len(f) > 12 else 'A')
    items = pickle.load(open(f'{W2}/items.pkl', 'rb'))
    X, PI, WR, NB, KD = [], [], [], [], []
    for i, sp, exp, ctx, rows in items:
        for t in rows:
            v = fl.get((i, t[1])) if t[0] >= 50 else None
            X.append(v is not None); PI.append(v[0] if v else -1); WR.append(v[1] if v else -1); NB.append(v[2] if v else -1)
            KD.append(v[3] if v else '')
    np.savez(f'{W2}/xflags.npz', X=np.array(X), PI=np.array(PI), WR=np.array(WR), NB=np.array(NB), KD=np.array(KD)); print('rows', len(X), 'extra', sum(X))

elif mode == 'eval':
    os.environ['SLM_WORK'] = W2
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import rr
    T0 = rr.load(); xf = np.load(f'{W2}/xflags.npz')
    assert len(xf['X']) == len(T0['G'])
    names = rr.dense_names(T0, ['base', 'lmw', 'lmc', 'pmi'])
    # SRX_GRID = 'K:m:kinds,...' (kinds A = A* K-best in the split, F = best path through each node, AF = both)
    grid = [(0, 0, '')] + [(int(a), int(b), c) for a, b, c in (g.split(':') for g in os.environ.get('SRX_GRID', '4:3:A,8:3:A,10:8:A').split(','))]
    KD = xf['KD'] if 'KD' in xf.files else np.where(xf['X'], 'A', '')
    res = []
    for K, m, kinds in grid:
        kind_ok = np.array([any(k in d for k in kinds) for d in KD]) if kinds else np.zeros(len(KD), bool)
        keep = ~xf['X'] if K == 0 else (~xf['X']) | ((xf['PI'] < K) & (xf['WR'] < m) & kind_ok)
        T = {k: (v[keep] if isinstance(v, np.ndarray) and len(v) == len(keep) else v) for k, v in T0.items()}
        gs = rr.group_first(T['G']); T['gst'] = gs; T['gsp'] = T['SPL'][gs]; T['ng'] = len(gs)
        assert T['ng'] == T0['ng']
        top1 = rr.acc_by_split(-rr.col(T, 'rank'), T)
        os.environ['SLM_TAG'] = f'srx_K{K}_m{m}{kinds}'
        s, w, l2 = rr.linear(T, names, ['tune'])
        a = rr.acc_by_split(s, T)
        orc = np.zeros(T['ng'], bool); np.logical_or.at(orc, T['G'], T['LAB'])
        o = {sp: float(orc[T['gsp'] == sp].mean()) for sp in ('tune', 'held', 'eval')}
        ncand = {sp: float(np.bincount(T['G'])[T['gsp'] == sp].mean()) for sp in ('tune', 'held', 'eval')}
        nx = {sp: float(np.bincount(T['G'], weights=xf['X'][keep].astype(float))[T['gsp'] == sp].mean()) for sp in ('tune', 'held', 'eval')}
        # gold only reachable through an extra candidate
        np.save(f'{W2}/score_K{K}_m{m}{kinds}.npy', s.astype(np.float32)); np.save(f'{W2}/keep_K{K}_m{m}{kinds}.npy', keep)
        r = dict(K=K, m=m, kinds=kinds, l2=l2, top1={k: float(top1[k]) for k in ('tune', 'held', 'eval')}, rr={k: float(a[k]) for k in ('tune', 'held', 'eval')},
                 oracle=o, ncand=ncand, nextra=nx)
        res.append(r); print(json.dumps(r), flush=True)
    json.dump(res, open(f'{W2}/eval.json', 'w'), indent=1)

elif mode == 'view':
    K, m = int(sys.argv[2]), int(sys.argv[3]); kinds = sys.argv[4] if len(sys.argv) > 4 else ''; V = f'{W2}/view_K{K}_m{m}{kinds}'
    for d in (V, f'{V}/cands', f'{V}/scores', f'{V}/diag'): os.makedirs(d, exist_ok=True)
    link(f'{WORK}/data', f'{V}/data')
    keep = np.load(f'{W2}/keep_K{K}_m{m}{kinds}.npy'); s = np.load(f'{W2}/score_K{K}_m{m}{kinds}.npy')
    z = np.load(f'{W2}/ftab.npz', allow_pickle=True)
    np.savez(f'{V}/ftab.npz', **{k: (z[k][keep] if z[k].ndim and len(z[k]) == len(keep) else z[k]) for k in z.files})
    np.save(f'{V}/scores/lin_base+lmw+lmc+pmi_tune.npy', s)
    gi = 0
    for fn in sorted(glob.glob(f'{W2}/cands/cands.*.tsv')):
        with open(f'{V}/cands/{os.path.basename(fn)}', 'w') as o:
            for l in open(fn):
                if keep[gi]: o.write(l)
                gi += 1
    assert gi == len(keep); print(V, int(keep.sum()))

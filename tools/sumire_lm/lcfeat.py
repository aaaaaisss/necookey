#!/usr/bin/env python3
"""lcfeat.py : lattice-local LM features of each top-50 row (no left context, so they decompose into node / edge costs).
  wl1/wl2/wl3 : word 1/2/3-gram log-prob (abs. discount .75, KN unigram) over Sumire's own word split (items_seg.pkl, stats_ws)
  woov        : #Sumire words not in the LM vocabulary
  cl3/cl5     : char 3/5-gram log-prob (Dirichlet d=2) of the candidate string (stats_c)
lcfeat.py feats  -> $WORK/lcost/lcfeat.npz
lcfeat.py fit [FEATS] [l2] -> linear listwise model on JWTD tune (cost + FEATS), prints tune/held/eval top1"""
import sys, os, pickle, unicodedata, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
WORK = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm'); O = f'{WORK}/lcost'
mode = sys.argv[1]
if mode == 'feats':
    from lmscore import score
    voc = pickle.load(open(f'{WORK}/lm/voc.pkl', 'rb')); UNK = 0xFFFFFFFF
    items = pickle.load(open(f'{WORK}/items_seg.pkl', 'rb'))
    wx = []; wl = []; cx = []; cl = []; nw = []; nch = []; cost = []
    for i, sp, exp, ctx, rows in items:
        for t in rows:
            wt = t[7].split(' ') if t[7] else []; nc = t[9]
            ids = [voc.get(w, UNK) for w in wt[nc:]]
            wx.extend(ids); wl.append(len(ids)); nw.append(len(ids))
            c = [ord(ch) for ch in t[1]]; cx.extend(c); cl.append(len(c)); nch.append(len(c)); cost.append(t[3])
    del items
    ws = (np.array(wx, np.uint32), np.array(wl)); cs = (np.array(cx, np.uint32), np.array(cl)); del wx, cx
    F = dict(nw=np.array(nw, float), nch=np.array(nch, float), cost=np.array(cost, float))
    nwk = np.load(f'{WORK}/lm/need_ws.npz'); needw = [nwk[f'k{k}'] for k in range(1, 5)]
    sw = np.load(f'{WORK}/lm/stats_ws.npz'); Vw = len(voc)
    for K in (1, 2, 3):
        o = score(ws, needw[:K], sw, K, 0.75, Vw); F[f'wl{K}'] = o['lp']
        if K == 1: F['woov'] = o['oov']
    del sw, needw
    nck = np.load(f'{WORK}/lm/need_c.npz'); needc = [nck[f'k{k}'] for k in range(1, 8)]
    sc = np.load(f'{WORK}/lm/stats_c.npz')
    for K in (3, 5):
        F[f'cl{K}'] = score(cs, needc[:K], sc, K, 2.0, None, uni='raw', sm='dir')['lp']
    np.savez(f'{O}/lcfeat.npz', **F); print({k: float(v.mean()) for k, v in F.items()})
elif mode == 'feats2':
    # node-local char LM: each Sumire word scored alone (history truncated at the word start) -> ci3/ci5;
    # boundary: log p(first char of w | last 1/2 chars of v) - log p(first char) -> cb2/cb3 (edge-local)
    from lmscore import score
    import glob
    segs = {}
    for fn in sorted(glob.glob(f'{WORK}/cands/cands.*.tsv')):
        for l in open(fn, encoding='utf-8'):
            f = l.rstrip('\n').split('\t')
            if len(f) >= 8 and f[4] != '' and int(f[1]) < 50: segs[(f[0], int(f[1]))] = '\x1f'.join(x.split(':', 1)[1] for x in f[7].split('\x1f')) if f[7] else ''
    items = pickle.load(open(f'{WORK}/items.pkl', 'rb'))
    from array import array
    wx = array('I'); wl = array('I'); wrow = array('I'); bx = array('I'); brow = array('I'); row = 0
    for i, sp, exp, ctx, rows in items:
        for t in rows:
            ws = [w for w in segs.get((i, t[0]), '').split('\x1f') if w] or [t[1]]
            for w in ws: wx.extend(ord(c) for c in w); wl.append(len(w)); wrow.append(row)
            for v, w in zip(ws[:-1], ws[1:]):
                h = v[-2:]; bx.extend(ord(c) for c in (h if len(h) == 2 else '\x00' + h)); bx.append(ord(w[0])); brow.append(row)
            row += 1
    del items, segs
    nrow = row; wrow = np.array(wrow); brow = np.array(brow)
    ws_ = (np.array(wx, np.uint32), np.array(wl)); del wx
    bs = np.array(bx, np.uint32).reshape(-1, 3); del bx
    nck = np.load(f'{WORK}/lm/need_c.npz'); needc = [nck[f'k{k}'] for k in range(1, 8)]; sc = np.load(f'{WORK}/lm/stats_c.npz')
    F = dict(np.load(f'{O}/lcfeat.npz'))
    for K in (3, 5):
        F[f'ci{K}'] = np.bincount(wrow, score(ws_, needc[:K], sc, K, 2.0, 30000, uni='raw', sm='dir')['lp'], nrow)
    one = (bs[:, 2].copy(), np.ones(len(bs), int)); p1 = score(one, needc[:1], sc, 1, 2.0, 30000, uni='raw', sm='dir')['lp']
    two = (bs[:, 1:].ravel().copy(), np.full(len(bs), 2)); p2 = score(two, needc[:2], sc, 2, 2.0, 30000, uni='raw', sm='dir')['lp']
    tw1 = (bs[:, 1].copy(), np.ones(len(bs), int)); q1 = score(tw1, needc[:1], sc, 1, 2.0, 30000, uni='raw', sm='dir')['lp']
    thr = (bs.ravel().copy(), np.full(len(bs), 3)); p3 = score(thr, needc[:3], sc, 3, 2.0, 30000, uni='raw', sm='dir')['lp']
    tw2 = (bs[:, :2].ravel().copy(), np.full(len(bs), 2)); q2 = score(tw2, needc[:2], sc, 2, 2.0, 30000, uni='raw', sm='dir')['lp']
    F['cb2'] = np.bincount(brow, (p2 - q1) - p1, nrow); F['cb3'] = np.bincount(brow, (p3 - q2) - p1, nrow)
    np.savez(f'{O}/lcfeat.npz', **F); print({k: float(F[k].mean()) for k in ('ci3', 'ci5', 'cb2', 'cb3')})
elif mode == 'feats3':
    # edge-local char 5-gram: chars of w predicted with history = previous word's last <=4 chars (truncated there) -> ce5
    from lmscore import score
    from array import array
    import glob
    segs = {}
    for fn in sorted(glob.glob(f'{WORK}/cands/cands.*.tsv')):
        for l in open(fn, encoding='utf-8'):
            f = l.rstrip('\n').split('\t')
            if len(f) >= 8 and f[4] != '' and int(f[1]) < 50: segs[(f[0], int(f[1]))] = '\x1f'.join(x.split(':', 1)[1] for x in f[7].split('\x1f')) if f[7] else ''
    items = pickle.load(open(f'{WORK}/items.pkl', 'rb'))
    ax = array('I'); al = array('I'); arow = array('I'); hx = array('I'); hl = array('I'); row = 0
    for i, sp, exp, ctx, rows in items:
        for t in rows:
            ws = [w for w in segs.get((i, t[0]), '').split('\x1f') if w] or [t[1]]
            prev = ''
            for w in ws:
                h = prev[-4:]; ax.extend(ord(c) for c in h + w); al.append(len(h) + len(w)); arow.append(row)
                hx.extend(ord(c) for c in h); hl.append(len(h)); prev = w
            row += 1
    del items, segs
    nck = np.load(f'{WORK}/lm/need_c.npz'); needc = [nck[f'k{k}'] for k in range(1, 8)]; sc = np.load(f'{WORK}/lm/stats_c.npz')
    F = dict(np.load(f'{O}/lcfeat.npz'))
    a = score((np.array(ax, np.uint32), np.array(al)), needc[:5], sc, 5, 2.0, 30000, uni='raw', sm='dir')['lp']
    hl = np.array(hl); hx = np.array(hx, np.uint32)
    hh = np.zeros(len(hl)); m = hl > 0
    hh[m] = score((hx, hl[m]), needc[:5], sc, 5, 2.0, 30000, uni='raw', sm='dir')['lp']
    F['ce5'] = np.bincount(np.array(arow), a - hh, row)
    np.savez(f'{O}/lcfeat.npz', **F); print('ce5', float(F['ce5'].mean()), 'cl5', float(F['cl5'].mean()))
elif mode == 'fit':
    from rr import softmax_train
    feats = (sys.argv[2] if len(sys.argv) > 2 else 'wl2,cl5').split(','); l2s = [float(x) for x in (sys.argv[3] if len(sys.argv) > 3 else '0.01,0.1,1').split(',')]
    z = np.load(f'{WORK}/ftab.npz', allow_pickle=True); G = z['G']; LAB = z['LAB']; SPL = z['SPL']
    F = np.load(f'{O}/lcfeat.npz')
    cols = [F['cost'] / 1000] + [F[f] for f in feats if f]
    X = np.stack(cols, 1); mu = X[SPL == 'tune'].mean(0); sd = X[SPL == 'tune'].std(0) + 1e-9; Xs = (X - mu) / sd
    gl = np.zeros(G.max() + 1, bool); np.logical_or.at(gl, G, LAB)
    def acc(s, sp):
        m = SPL == sp; g = G[m]; ss = s[m]; lab = LAB[m]
        order = np.lexsort((-ss, g)); first = np.r_[True, g[order][1:] != g[order][:-1]]
        return lab[order][first].mean()
    best = None
    for l2 in l2s:
        cv = []
        for k in (0, 1):
            rows = (SPL == 'tune') & gl[G] & (G % 2 == k)
            w = softmax_train(Xs, G, LAB, rows, l2); s = Xs @ w
            m = (SPL == 'tune') & (G % 2 == 1 - k); cv.append(acc(np.where(m, s, -1e9), 'tune') if False else None)
            gg = G[m]; ss = s[m]; lab = LAB[m]; order = np.lexsort((-ss, gg)); first = np.r_[True, gg[order][1:] != gg[order][:-1]]
            cv.append(lab[order][first].mean())
        cvm = np.mean([c for c in cv if c is not None])
        if best is None or cvm > best[0]: best = (cvm, l2)
    w = softmax_train(Xs, G, LAB, (SPL == 'tune') & gl[G], best[1]); s = Xs @ w
    wr = w / sd; print('feats', feats, 'l2', best[1], 'cv %.4f' % best[0], 'tune %.3f held %.3f eval %.3f' % (acc(s, 'tune'), acc(s, 'held'), acc(s, 'eval')),
                       'w(raw, per cost unit)', np.round(-wr / wr[0] * 1, 4).tolist() if wr[0] != 0 else wr)

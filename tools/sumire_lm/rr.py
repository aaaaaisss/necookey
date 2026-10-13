#!/usr/bin/env python3
"""Discriminative reranking of Sumire top-50 (exact-match top1).
rr.py feats  [WORK]  : build feature table WORK/ftab.npz from items.pkl + LM stats (lm/stats_w.npz, lm/stats_c.npz, lm/pair.npz)
rr.py eval   [WORK]  : baselines + LM interpolations + linear listwise model + LightGBM lambdarank; prints the table
Splits: tune/held (JWTD), eval (AJIMEE, report only), auto (CC-100 hold-out clauses, training only)."""
import sys, os, re, pickle, math, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm')
KAN = re.compile(r'[一-龠々〆ヵヶ]'); HIRA = re.compile(r'[ぁ-ゖ]'); KATA = re.compile(r'[ァ-ヺー]'); DIG = re.compile(r'[0-9０-９]')
KNUM = re.compile(r'[〇一二三四五六七八九十百千万億]')
P = np.uint64(0x9E3779B97F4A7C15)

def prune_spec():
    """env SLM_PRUNE e.g. 'w=1,2,3,3;c=1,2,3,4,5,6,8;p=5;q=1' : drop n-grams (pairs) with corpus count < threshold per order,
    q=1 quantizes every stored count to 8 bits on a log scale (simulates the on-device table)"""
    sp = {}
    for part in filter(None, os.environ.get('SLM_PRUNE', '').split(';')):
        k, v = part.split('='); sp[k] = [float(x) for x in v.split(',')]
    return sp
def quant8(a):
    a = np.asarray(a, np.float64); mx = a.max() if a.size else 1
    if mx <= 1: return a
    b = mx ** (1 / 254); q = np.where(a > 0, np.round(np.log(np.maximum(a, 1)) / np.log(b)), -1)
    return np.where(q >= 0, np.round(b ** q), 0)
def apply_prune(st, th, q):
    st = {k: st[k].copy() if np.ndim(st[k]) else st[k] for k in st.files}
    K = max(int(k[1:]) for k in st if k.startswith('c') and k[1:].isdigit())
    for k in range(1, K + 1):
        t = th[k - 1] if th else 0
        keep = st[f'c{k}'] >= t
        st[f'c{k}'] = np.where(keep, st[f'c{k}'], 0)
        if f'nf{k+1}' in st: st[f'nf{k+1}'] = np.where(keep, st[f'nf{k+1}'], 0)
        if k == 1 and 'nl1' in st: st['nl1'] = np.where(keep, st['nl1'], 0)
    if q:
        for k in list(st):
            if (k[0] == 'c' and k[1:].isdigit()) or k.startswith('nf') or k == 'nl1': st[k] = quant8(st[k])
    return st

def build(SH='all'):
    SUF = '' if SH == 'all' else f'_{SH}'
    ISUF = os.environ.get('SLM_ITEMS', SUF)  # items/seqs suffix (prune runs reuse the unpruned items)
    PR = prune_spec(); QU = PR.get('q', [0])[0] > 0
    from lmscore import score
    from common import rowdict, content
    items = pickle.load(open(f'{W}/items{ISUF}.pkl', 'rb')); z = np.load(f'{W}/lm/seqs{ISUF}.npz'); wseqs = (z['wx'], z['wl']); cseqs = (z['cx'], z['cl'])
    voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb'))
    WK = os.environ.get('SLM_WKIND', 'w'); PS = os.environ.get('SLM_PAIR', '')  # analyzer-free variant: ws / _seg
    nw = np.load(f'{W}/lm/need_{WK}.npz'); nc = np.load(f'{W}/lm/need_c.npz')
    needw = [nw[f'k{k}'] for k in range(1, 5)]; needc = [nc[f'k{k}'] for k in range(1, 8)]
    F = {}
    sw = np.load(f'{W}/lm/stats_{WK}.npz'); sc = np.load(f'{W}/lm/stats_c.npz')
    if PR: sw = apply_prune(sw, PR.get('w'), QU); sc = apply_prune(sc, PR.get('c'), QU)
    Vw = len(voc)
    for D in (0.6, 0.75, 0.9):
        o = score(wseqs, needw, sw, 4, D, Vw); F[f'w4_{D}'] = o['lp']
        if D == 0.75:
            F['w4sb'] = o['sb']; F['woov'] = o['oov']
            for k in range(1, 5): F[f'whit{k}'] = o[f'hit{k}']
    F['w3'] = score(wseqs, needw[:3], sw, 3, 0.75, Vw)['lp']; F['w2'] = score(wseqs, needw[:2], sw, 2, 0.75, Vw)['lp']
    for D in (0.5, 2, 8):  # chars: Dirichlet prior (follower sets too big for chars)
        o = score(cseqs, needc, sc, 7, D, 30000, uni='raw', sm='dir'); F[f'c7_{D}'] = o['lp']
        if D == 2:
            F['c7sb'] = o['sb']
            for k in range(1, 8): F[f'chit{k}'] = o[f'hit{k}']
    F['c5'] = score(cseqs, needc[:5], sc, 5, 2, 30000, uni='raw', sm='dir')['lp']; F['c3'] = score(cseqs, needc[:3], sc, 3, 2, 30000, uni='raw', sm='dir')['lp']
    print('lm done', flush=True)
    # PMI
    pr = np.load(f'{W}/lm/need_pair{PS}.npz'); pc = np.load(f'{W}/lm/pair{PS}.npz')['c']; ph = pr['h']
    if 'p' in PR: pc = np.where(pc >= PR['p'][0], pc, 0)
    if QU: pc = quant8(pc).astype(np.int64)
    c1 = sw['c1']; N = float(sw['n'])
    u1 = dict(zip(needw[0].tolist(), c1.tolist())); pairc = dict(zip(ph.tolist(), pc.tolist())); PI = int(P); M64 = (1 << 64) - 1
    def uc(w): return u1.get(w, 0)
    pmi_sum, pmi_max, pmi_n, cooc_l = [], [], [], []
    # structural
    G, LAB, SPL, IDS = [], [], [], []
    cost, rel, rank, ntok, nseg, nkan, nhira, nkata, ndig, nknum, typ1, nch = ([] for _ in range(12))
    posb = []; DW = []
    for g, (i, sp, exp, ctx, rows) in enumerate(items):
        rows = [rowdict(t) for t in rows]; r0 = rows[0]
        A = [voc[w] for w in content(r0, 0, r0['nc']) if w in voc]
        cs = [content(r, r['nc'], len(r['wt'])) for r in rows]; common = set.intersection(*cs) if cs else set()
        toks = [set(r['wt'][r['nc']:]) for r in rows]; tcommon = set.intersection(*toks)
        DW.extend(' '.join(sorted(t - tcommon)) for t in toks)
        ucache = {}
        for r, cset in zip(rows, cs):
            G.append(g); LAB.append(r['s'] in exp); SPL.append(sp); IDS.append(i)
            cost.append(r['cost'] / 1000); rel.append((r['cost'] - r0['cost']) / 1000); rank.append(math.log1p(r['rank']))
            ntok.append(len(r['wt']) - r['nc']); nseg.append(r['nseg']); s = r['s']; nch.append(len(s))
            nkan.append(len(KAN.findall(s))); nhira.append(len(HIRA.findall(s))); nkata.append(len(KATA.findall(s)))
            ndig.append(len(DIG.findall(s))); nknum.append(len(KNUM.findall(s))); typ1.append(r['type'] == 1)
            prev = r['pos'][r['nc'] - 1] if r['nc'] > 0 else 'BOS'
            posb.append(' '.join(f'{a}>{b}' for a, b in zip([prev] + r['pos'][r['nc']:], r['pos'][r['nc']:] + ['EOS'])))
            # PMI over distinguishing content words
            ps = []; ls = 0.0
            for w in cset - common:
                b = voc.get(w)
                if b is None or not A: continue
                best = -1e9
                for a in A:
                    cab = pairc.get((a * PI + b) & M64, 0)
                    if a not in ucache: ucache[a] = uc(a)
                    if b not in ucache: ucache[b] = uc(b)
                    pmi = math.log((cab + 0.1) * N / (max(ucache[a], 1) * max(ucache[b], 1) * 30))
                    best = max(best, pmi); ls += math.log1p(cab)
                ps.append(best)
            pmi_sum.append(sum(max(p, 0) for p in ps)); pmi_max.append(max(ps) if ps else 0.0); pmi_n.append(len(ps)); cooc_l.append(ls)
        if g % 5000 == 0: print('feat', g, flush=True)
    F.update(cost=np.array(cost), rel=np.array(rel), rank=np.array(rank), ntok=np.array(ntok, float), nseg=np.array(nseg, float),
             nkan=np.array(nkan, float), nhira=np.array(nhira, float), nkata=np.array(nkata, float), ndig=np.array(ndig, float),
             nknum=np.array(nknum, float), typ1=np.array(typ1, float), nch=np.array(nch, float),
             pmi_sum=np.array(pmi_sum), pmi_max=np.array(pmi_max), pmi_n=np.array(pmi_n, float), cooc=np.array(cooc_l))
    np.savez(f'{W}/ftab{SUF}.npz', G=np.array(G), LAB=np.array(LAB), SPL=np.array(SPL), IDS=np.array(IDS), POSB=np.array(posb, dtype=object),
             DW=np.array(DW, dtype=object), names=np.array(sorted(F)), X=np.stack([F[k] for k in sorted(F)], 1).astype(np.float32))
    print('rows', len(G))

if __name__ == '__main__' and sys.argv[1] == 'feats': build(sys.argv[2] if len(sys.argv) > 2 else 'all')

# ---------------------------------------------------------------- evaluation
def group_first(G):
    return np.r_[0, np.nonzero(np.diff(G))[0] + 1]
def acc_by_split(s, T):
    G, LAB, gsp = T['G'], T['LAB'], T['gsp']
    o = np.lexsort((-s, G)); f = o[group_first(G[o])]  # best row per group (groups are contiguous 0..n-1)
    ok = LAB[f]
    return {sp: ok[gsp == sp].mean() for sp in ('tune', 'held', 'eval', 'auto')}
def fmt(a): return ' '.join(f"{a[k]:.3f}" for k in ('tune', 'held', 'eval', 'auto'))

def load():
    """concatenate ftab_e, ftab_g0, ftab_g1 ... (env SLM_FTAB = comma list of suffixes; default: legacy ftab.npz)"""
    sufs = os.environ.get('SLM_FTAB', '').split(',') if os.environ.get('SLM_FTAB') else ['']
    parts = [np.load(f'{W}/ftab{"_" + s if s else ""}.npz', allow_pickle=True) for s in sufs]
    off = 0; Gs = []
    for z in parts: Gs.append(z['G'] + off); off = Gs[-1].max() + 1
    cat = lambda k: np.concatenate([z[k] for z in parts])
    G = np.concatenate(Gs)
    T = dict(G=G, LAB=cat('LAB'), X=cat('X').astype(np.float64), names=list(parts[0]['names']), POSB=cat('POSB'), SPL=cat('SPL'),
             DW=cat('DW') if 'DW' in parts[0].files else None)
    gs = group_first(G); T['gsp'] = z['SPL'][gs]; T['gst'] = gs; T['ng'] = len(gs)
    return T
def col(T, n): return T['X'][:, T['names'].index(n)]

def rel_feats(T, names):
    """features minus their per-group max (for trees); linear softmax is invariant to it"""
    X = np.stack([col(T, n) for n in names], 1)
    G = T['G']; mx = np.full((T['ng'], X.shape[1]), -np.inf)
    np.maximum.at(mx, G, X)
    return X - mx[G]

def softmax_train(X, G, LAB, rows, l2, w0=None, iters=300, gw=None):
    """listwise conditional log-linear model (softmax over the N-best of each sentence); rows = mask of training rows
    (whole groups, each with >=1 gold). L-BFGS from scipy."""
    from scipy.optimize import minimize
    import scipy.sparse as sps
    Xr = X[rows]; Gr = G[rows]; Lr = LAB[rows].astype(float)
    ug, Gi = np.unique(Gr, return_inverse=True); ng = Gi.max() + 1
    wg = np.ones(ng) if gw is None else gw[ug]; wr = wg[Gi]
    def f(w):
        s = Xr @ w; m = np.full(ng, -np.inf); np.maximum.at(m, Gi, s); e = np.exp(s - m[Gi])
        Z = np.bincount(Gi, e, ng); Zg = np.bincount(Gi, e * Lr, ng)
        loss = -(wg * (np.log(Zg) - np.log(Z))).sum() + l2 * (w @ w)
        p = e / Z[Gi]; q = e * Lr / Zg[Gi]
        g = Xr.T @ (wr * (p - q)) + 2 * l2 * w
        return loss, np.asarray(g).ravel()
    w0 = np.zeros(X.shape[1]) if w0 is None else w0
    r = minimize(f, w0, jac=True, method='L-BFGS-B', options=dict(maxiter=iters))
    return r.x

def parse_splits(splits):
    """'tune*10' -> ('tune', 10.0); 'autohard' = auto groups whose Sumire top1 is wrong"""
    out = []
    for sp in splits:
        n, _, w = sp.partition('*'); out.append((n, float(w) if w else 1.0))
    return out
def group_weights(T, splits):
    gw = np.zeros(T['ng']); top_ok = T['LAB'][T['gst']]
    for n, w in parse_splits(splits):
        if n == 'autohard': m = (T['gsp'] == 'auto') & ~top_ok
        elif n == 'autoeasy': m = (T['gsp'] == 'auto') & top_ok
        else: m = T['gsp'] == n
        gw[m] = w
    return gw
def train_rows(T, splits, half=None):
    """rows of groups in splits that contain a gold; half = (k, 2) keeps groups with index%2==k (CV)"""
    G = T['G']; gl = np.zeros(T['ng'], bool); np.logical_or.at(gl, G, T['LAB'])
    gm = (group_weights(T, splits) > 0) & gl
    if half is not None: gm &= (np.arange(T['ng']) % half[1]) == half[0]
    return gm[G]

def evaluate():
    T = load(); G = T['G']; out = []
    def rep(name, s):
        a = acc_by_split(s, T); out.append((name, a)); print(f'{name:58s} {fmt(a)}', flush=True); return a
    print(f"{'method':58s} tune  held  eval  auto")
    rep('Sumire top1', -col(T, 'rank'))
    orc = np.zeros(T['ng'], bool); np.logical_or.at(orc, G, T['LAB'])
    a = {sp: orc[T['gsp'] == sp].mean() for sp in ('tune', 'held', 'eval', 'auto')}; out.append(('oracle@50', a)); print(f"{'oracle@50':58s} {fmt(a)}")
    # single LM + lambda*cost (+ length pen), tuned on tune
    def tuned(name, lm, extra=()):
        best = None
        for lam in [0, 0.1, 0.2, 0.3, 0.5, 0.7, 1, 1.5, 2, 3]:
            for pen in [-1, -0.5, 0, 0.5, 1, 2]:
                s = lm - lam * col(T, 'cost') - pen * col(T, 'ntok')
                a = acc_by_split(s, T)['tune']
                if best is None or a > best[0]: best = (a, lam, pen)
        _, lam, pen = best
        return rep(f'{name} + {lam}*cost - {pen}*ntok', lm - lam * col(T, 'cost') - pen * col(T, 'ntok'))
    for n in ['w2', 'w3', 'w4_0.6', 'w4_0.75', 'w4_0.9', 'w4sb', 'c3', 'c5', 'c7_0.5', 'c7_2', 'c7_8', 'c7sb']:
        if n in T['names']: tuned(n, col(T, n))
    # word + char interpolation
    bw = max(['w4_0.6', 'w4_0.75', 'w4_0.9'], key=lambda n: acc_by_split(col(T, n) - 0.2 * col(T, 'cost'), T)['tune'])
    bc = max(['c7_0.5', 'c7_2', 'c7_8'], key=lambda n: acc_by_split(col(T, n) - 0.2 * col(T, 'cost'), T)['tune'])
    best = None
    for a_ in [0.2, 0.35, 0.5, 0.65, 0.8]:
        lm = a_ * col(T, bw) + (1 - a_) * col(T, bc)
        for lam in [0, 0.1, 0.2, 0.3, 0.5, 1]:
            s = lm - lam * col(T, 'cost'); v = acc_by_split(s, T)['tune']
            if best is None or v > best[0]: best = (v, a_, lam)
    _, a_, lam = best
    rep(f'{a_}*{bw} + {1-a_:.2f}*{bc} - {lam}*cost', a_ * col(T, bw) + (1 - a_) * col(T, bc) - lam * col(T, 'cost'))
    return T, out

DENSE = None
def dense_names(T, groups):
    base = ['cost', 'rel', 'rank', 'ntok', 'nseg', 'nkan', 'nhira', 'nkata', 'ndig', 'nknum', 'typ1', 'nch']
    lmw = ['w2', 'w3', 'w4_0.6', 'w4_0.75', 'w4_0.9', 'w4sb', 'woov'] + [f'whit{k}' for k in range(1, 5)]
    lmc = ['c3', 'c5', 'c7_0.5', 'c7_2', 'c7_8', 'c7sb'] + [f'chit{k}' for k in range(1, 8)]
    pmi = ['pmi_sum', 'pmi_max', 'pmi_n', 'cooc']
    sel = {'base': base, 'lmw': lmw, 'lmc': lmc, 'pmi': pmi}
    return [n for g in groups for n in sel[g] if n in T['names']]

def pos_sparse(T, rows_fit, min_count=20):
    """sparse POS-bigram count features (boundary with the last context token, EOS); vocabulary from rows_fit"""
    import scipy.sparse as sps, collections
    cnt = collections.Counter()
    for s in T['POSB'][rows_fit]: cnt.update(set(s.split()))
    vocab = {k: i for i, k in enumerate(sorted(k for k, v in cnt.items() if v >= min_count))}
    r, c = [], []
    for i, s in enumerate(T['POSB']):
        for t in s.split():
            j = vocab.get(t)
            if j is not None: r.append(i); c.append(j)
    M = sps.csr_matrix((np.ones(len(r)), (r, c)), shape=(len(T['POSB']), len(vocab)))
    return M, vocab

def lex_sparse(T, rows_fit, min_count=5):
    """sparse indicators of distinguishing tokens (lex.npz), vocabulary = tokens seen in >= min_count training rows"""
    import scipy.sparse as sps, collections
    DW = T['DW'] if T.get('DW') is not None else np.load(f'{W}/lex.npz', allow_pickle=True)['DW']; cnt = collections.Counter()
    for s in DW[rows_fit]: cnt.update(s.split())
    vocab = {k: i for i, k in enumerate(sorted(k for k, v in cnt.items() if v >= min_count))}
    r, c = [], []
    for i, s in enumerate(DW):
        for t in s.split():
            j = vocab.get(t)
            if j is not None: r.append(i); c.append(j)
    return sps.csr_matrix((np.ones(len(r)), (r, c)), shape=(len(DW), len(vocab))), vocab

def linear(T, names, train_splits, use_pos=False, use_lex=False, l2s=(0.03, 0.1, 0.3, 1, 3), tag=''):
    import scipy.sparse as sps
    X = np.stack([col(T, n) for n in names], 1)
    tr = train_rows(T, train_splits)
    mu = X[tr].mean(0); sd = X[tr].std(0) + 1e-9; Xs = (X - mu) / sd
    if use_pos:
        M, vocab = pos_sparse(T, tr); Xs = sps.hstack([sps.csr_matrix(Xs), M]).tocsr()
    if use_lex:
        M, vocab = lex_sparse(T, tr); Xs = sps.hstack([sps.csr_matrix(Xs) if not sps.issparse(Xs) else Xs, M]).tocsr()
    gw = group_weights(T, train_splits)
    # choose l2 by 2-fold CV on the training groups only (CV accuracy = mean over held-out training groups, all weighted
    # splits pooled; if JWTD tune is in the training set, CV accuracy is measured on tune groups only)
    best = None
    for l2 in l2s:
        acc = []
        for k in range(2):
            w = softmax_train(Xs, T['G'], T['LAB'], train_rows(T, train_splits, (k, 2)), l2, gw=gw)
            vr = train_rows(T, train_splits, (1 - k, 2))
            if any(n == 'tune' for n, _ in parse_splits(train_splits)): vr = vr & (T['SPL'] == 'tune')
            s = Xs @ w; o = acc_by_split_rows(s, T, vr); acc.append(o)
        v = np.mean(acc)
        if best is None or v > best[0]: best = (v, l2)
    w = softmax_train(Xs, T['G'], T['LAB'], tr, best[1], gw=gw)
    s = Xs @ w
    if not use_pos and not use_lex:
        import json; os.makedirs(f'{W}/models', exist_ok=True)
        tag = os.environ.get('SLM_TAG', 'last')
        json.dump(dict(names=names, mu=mu.tolist(), sd=sd.tolist(), w=w.tolist(), l2=best[1], train=train_splits),
                  open(f'{W}/models/{tag}.json', 'w'), ensure_ascii=False, indent=1)
    return s, w, best[1]

def acc_by_split_rows(s, T, rows):
    """top1 accuracy over the groups covered by rows (rows = whole groups)"""
    G = T['G']; o = np.lexsort((-s, G)); f = o[group_first(G[o])]
    gm = np.zeros(T['ng'], bool); gm[G[rows]] = True
    return T['LAB'][f][gm].mean()

def lgb_rank(T, names, train_splits, tag=''):
    import lightgbm as lgb
    Xa = np.concatenate([np.stack([col(T, n) for n in names], 1), rel_feats(T, names)], 1)
    G = T['G']; tr = train_rows(T, train_splits)
    # early stopping on half of the training groups, then refit on all with that many rounds
    def fit(rows, n_est, valid=None):
        o = np.nonzero(rows)[0]; grp = np.bincount(G[o] - 0)[np.unique(G[o])]
        m = lgb.LGBMRanker(objective='lambdarank', n_estimators=n_est, learning_rate=0.05, num_leaves=15, min_child_samples=50,
                           subsample=0.8, subsample_freq=1, colsample_bytree=0.8, reg_lambda=1.0, verbose=-1, lambdarank_truncation_level=10)
        kw = {}
        if valid is not None:
            ov = np.nonzero(valid)[0]; gv = np.bincount(G[ov])[np.unique(G[ov])]
            kw = dict(eval_set=[(Xa[ov], T['LAB'][ov].astype(int))], eval_group=[gv], eval_at=[1],
                      callbacks=[lgb.early_stopping(50, verbose=False)])
        m.fit(Xa[o], T['LAB'][o].astype(int), group=grp, **kw)
        return m
    m = fit(train_rows(T, train_splits, (0, 2)), 2000, train_rows(T, train_splits, (1, 2)))
    n_best = max(50, m.best_iteration_ or 200)
    m = fit(tr, n_best)
    return m.predict(Xa), n_best, m

def save_scores(tag, s):
    os.makedirs(f'{W}/scores', exist_ok=True); np.save(f'{W}/scores/{tag}.npy', s.astype(np.float32))

if __name__ == '__main__' and sys.argv[1] == 'eval':
    evaluate()
if __name__ == '__main__' and sys.argv[1] in ('lin', 'linpos', 'linlex', 'linposlex', 'lgb'):
    # rr.py lin|linpos|lgb GROUPS(base+lmw+...) TRAIN(tune+auto)   (work dir: env SLM_WORK)
    groups = sys.argv[2].split('+'); tr = sys.argv[3].split('+')
    T = load(); names = dense_names(T, groups)
    if sys.argv[1] == 'lgb':
        s, nb, m = lgb_rank(T, names, tr); extra = f'n={nb}'
    else:
        os.environ['SLM_TAG'] = f"{sys.argv[1]}_{sys.argv[2]}_{sys.argv[3]}" + os.environ.get('SLM_FTAB', '').replace(',', '_')
        s, w, l2 = linear(T, names, tr, use_pos='pos' in sys.argv[1], use_lex='lex' in sys.argv[1]); extra = f'l2={l2}'
        if sys.argv[1] == 'lin': print(' '.join(f'{n}:{v:+.3f}' for n, v in zip(names, w)))
    tag = f"{sys.argv[1]}_{sys.argv[2]}_{sys.argv[3]}" + os.environ.get('SLM_FTAB', '').replace(',', '_'); save_scores(tag, s)
    print(f"{tag+' '+extra:58s} {fmt(acc_by_split(s, T))}", flush=True)

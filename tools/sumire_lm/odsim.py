#!/usr/bin/env python3
"""odsim.py VARIANT [key=value ...] : on-device simulation of the SUMIRE-LM reranker (pruned + quantized + fingerprinted tables),
retrain the non-lexical linear listwise model on JWTD tune only, report tune / held / eval(AJIMEE) top1 and the table size.
VARIANT: fug (fugashi tokens) | seg (Sumire's own word split + unigram-Viterbi context; corpus counts stay fugashi-tokenized)
keys (defaults = unpruned float reference):
  w=t1,t2,t3,t4      word n-gram count cutoffs per order (entry stored iff corpus count >= t; use the ngsize grid 1,2,3,5,10,20,50,100,200,500)
  c=t1,..,tK         char n-gram cutoffs (K = len, 7 or 5 -> char order)
  pf=F               PMI pair count floor ; pk=K  keep a pair only if it is in the top-K partners (by PMI) of either word (od/pmi_topk.npz)
  q=B                quantize every stored value (logprob, backoff, log count) to B bits, 1-D k-means per order and value kind (0 = float32)
  fp=B               key fingerprint bits (bucketed hashing, load L=8 keys/bucket): an absent key hits a stored key with prob 1-exp(-L/2^B)
                     and then returns a random stored value (0 = exact keys)
  tag=NAME           model/scores name
ARPA backoff scoring: p(w|h) = P[h w] if stored, else BOW[h] * p(w|h'); values are those of the unpruned interpolated model
(no backoff renormalization after pruning). Size: key bits = fp + 2 (bucket directory), values = q bits each
(lp for all entries, bow for orders < K, a log-count for word unigrams); entry counts are the full-corpus extrapolations
(lm/ngsize_{w,c}.json, od/pmi_size.json). AJIMEE (split eval) is never used for any fitting or selection here."""
import sys, os, json, math, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm')
import rr

def mix(x, salt):
    x = (x.astype(np.uint64) * np.uint64(0x9E3779B97F4A7C15) + np.uint64(salt * 0x632BE59BD9B4E019 & (2**64 - 1)))
    x ^= x >> np.uint64(31); x *= np.uint64(0xBF58476D1CE4E5B9); x ^= x >> np.uint64(29); x *= np.uint64(0x94D049BB133111EB); x ^= x >> np.uint64(32)
    return x
def unif(x, salt): return (mix(x, salt) >> np.uint64(11)).astype(np.float64) / float(1 << 53)

def kmeans1d(v, bits, iters=25):
    """returns quantizer f(values)->dequantized values, fitted on v (1-D Lloyd, quantile init)"""
    v = np.asarray(v, np.float64)
    if bits <= 0 or v.size == 0: return lambda a: a
    k = 1 << bits; u = np.unique(v)
    if len(u) <= k:
        cent = u
    else:
        cent = np.unique(np.quantile(v, (np.arange(k) + 0.5) / k))
        s = np.sort(v)
        for _ in range(iters):
            mid = (cent[1:] + cent[:-1]) / 2; lab = np.searchsorted(mid, s)
            sm = np.bincount(lab, s, len(cent)); n = np.bincount(lab, None, len(cent)); nz = n > 0
            new = cent.copy(); new[nz] = sm[nz] / n[nz]; new = np.unique(new)
            if len(new) == len(cent) and np.allclose(new, cent): break
            cent = new
    return Quant(cent)
class Quant:
    """scalar quantizer: q.code(a) -> centroid index, q(a) -> dequantized value, q.cent = codebook"""
    def __init__(self, cent): self.cent = np.asarray(cent, np.float64); self.mid = (self.cent[1:] + self.cent[:-1]) / 2
    def code(self, a): return np.searchsorted(self.mid, np.asarray(a, np.float64))
    def __call__(self, a): return self.cent[self.code(a)]

RENORM = True
EXPORT = None  # dict -> filled with the stored entries and quantizers (export_od.py)
FOLK = {'w': {'fug': 'w', 'seg': 'ws'}, 'c': {'fug': 'c', 'seg': 'c'}}
def renorm(Z, kind, th, K):
    """values of the count-cutoff pruned interpolated model, renormalized exactly with the follower statistics (follow.py):
    word: p(w|h) = [c(hw)>=t] (c(hw)-D)/c(h.) + g(h) p(w|h'),  g(h) = (c(h.) - S_t(h) + D N_t(h)) / c(h.)
    char: p(w|h) = [c(hw)>=t] c(hw)/(c(h.)+d) + g(h) p(w|h'),  g(h) = (c(h.) - S_t(h) + d) / (c(h.) + d)
    (c(h.) = S_1(h) = total follower count). Returns LP[k], BW[k] per position (BW of the k-gram ending at j as a context)."""
    pos = Z[f'{kind}pos'].astype(np.int64); n = len(pos); D = 0.75 if kind == 'w' else 2.0
    TH = [1, 2, 3, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 4000]
    c1 = Z[f'{kind}cnt1']; unk = float(Z[f'{kind}unk'])
    p = np.where(c1 >= max(th[0], 1), np.exp(Z[f'{kind}lp1'].astype(np.float64)), math.exp(unk))
    LP = {1: np.log(p)}; BW = {k: np.zeros(n) for k in range(1, K)}
    for k in range(2, K + 1):
        f = np.load(f'{W}/od/fol_{FOLK[kind][VAR]}{k}.npz', mmap_mode='r'); ti = TH.index(int(max(th[k - 1], 1)))
        j = np.nonzero(pos >= k - 1)[0]; ctx = Z[f'{kind}idx{k-1}'][j - 1].astype(np.int64)
        Sall = f['S'][:, 0][ctx].astype(np.float64); St = f['S'][:, ti][ctx].astype(np.float64); Nt = f['N'][:, ti][ctx].astype(np.float64)
        c = Z[f'{kind}cnt{k}'][j].astype(np.float64); kept = c >= max(th[k - 1], 1)
        if kind == 'w':
            g = np.where(Sall > 0, (Sall - St + D * Nt) / np.maximum(Sall, 1), 1.0)
            pk = np.where(kept, (c - D) / np.maximum(Sall, 1), 0.0) + g * p[j]
        else:
            g = (Sall - St + D) / (Sall + D); pk = np.where(kept, c / (Sall + D), 0.0) + g * p[j]
        g = np.clip(g, 1e-12, 1.0)
        BW[k - 1][j - 1] = np.log(g)
        p = p.copy(); p[j] = pk; lp = np.zeros(n); lp[j] = np.log(pk); LP[k] = lp
    return LP, BW

def lm_feats(Z, kind, th, K, q, fp, L=8):
    """vectorized ARPA-style scoring of every row; returns dict of row features and lookup stats"""
    pos = Z[f'{kind}pos'].astype(np.int64); n = len(pos); RL = Z[f'{kind}L']; rows = np.repeat(np.arange(len(RL)), RL)
    pr = 1 - math.exp(-L / 2 ** fp) if fp else 0.0
    S, LV, BV = {}, {}, {}; stats = dict(absent=0, fh=0)
    rn = RENORM and max(th) > 1  # unpruned: the prep values are already exact
    RLP, RBW = renorm(Z, kind, th, K) if rn else (None, None)
    for k in range(1, K + 1):
        idx = Z[f'{kind}idx{k}'].astype(np.int64); cnt = Z[f'{kind}cnt{k}']; valid = idx >= 0
        st = valid & (cnt >= max(th[k - 1], 1))
        lp = RLP[k] if rn else Z[f'{kind}lp{k}'].astype(np.float64)
        bw = (RBW[k] if rn else Z[f'{kind}bw{k}'].astype(np.float64)) if k < K else None
        # unique stored entries -> codebooks (fit on the stored values of the need set, one per order and kind)
        ui, first = np.unique(idx[st], return_index=True); sel = np.nonzero(st)[0][first]
        qlp = kmeans1d(lp[sel], q) if q else (lambda a: a)
        lpv = np.where(st, qlp(lp), 0.0)
        if bw is not None:
            qbw = kmeans1d(bw[sel], q) if q else (lambda a: a); bwv = np.where(st, qbw(bw), 0.0)
        fh = np.zeros(n, bool)
        if fp:
            ab = valid & ~st; fh = ab & (unif(idx, 100 + k) < pr)
            stats['absent'] += int(ab.sum()); stats['fh'] += int(fh.sum())
            if fh.any() and len(sel):
                r = (mix(idx[fh], 200 + k) % np.uint64(len(sel))).astype(np.int64); src = sel[r]
                lpv[fh] = qlp(lp[src])
                if bw is not None: bwv[fh] = qbw(bw[src])
        S[k] = st | fh; LV[k] = lpv; BV[k] = bwv if bw is not None else None
        if EXPORT is not None: EXPORT[(kind, k)] = dict(idx=ui, lp=lp[sel], bw=None if bw is None else bw[sel], qlp=qlp, qbw=qbw if bw is not None else None)
    unk = float(Z[f'{kind}unk'])
    # CB[k][j] = sum_{k'=1..k} BV_k'(j-1)
    CB = np.zeros((K, n)); 
    for k in range(1, K):
        sh = np.zeros(n); sh[1:] = np.where(S[k][:-1], BV[k][:-1], 0.0); CB[k] = CB[k - 1] + sh
    pred = ~((pos == 0) & Z[f'{kind}x0'])
    out = {}
    caps = [4, 3, 2] if kind == 'w' else [K] + ([5] if K > 5 else []) + [3]
    for cap in caps:
        Kj = np.minimum(cap, pos + 1); m = np.zeros(n, np.int64); val = np.full(n, unk)
        for k in range(1, cap + 1):
            hit = S[k] & (k <= Kj); m = np.where(hit, k, m); val = np.where(hit, LV[k], val)
        lo = np.maximum(m, 1) - 1; hi = Kj - 1
        lpj = val + CB[hi, np.arange(n)] - CB[lo, np.arange(n)]
        def rs(v): return np.bincount(rows[pred], v[pred], len(RL))
        nm = f'{kind}{cap}'; out[nm] = rs(lpj)
        if cap == K:
            out[f'{kind}sb'] = rs(val + (Kj - m) * math.log(0.4))
            for k in range(1, K + 1): out[f'{kind}hit{k}'] = rs((m == k).astype(float))
            if kind == 'w': out['woov'] = rs((~S[1]).astype(float))
    return out, stats

def pmi_feats(Z, pf, pk, q, fp, th1, L=8):
    cab = Z['pcnt'].astype(np.float64); keep = cab >= max(pf, 1)
    if pk:
        t = np.load(f'{W}/od/pmi_topk_{VAR}.npz'); thr = t[f'{pf}_{pk}']  # per-pair: in top-K of a or b (precomputed, see pmitopk.py)
        keep &= thr
    pr = 1 - math.exp(-L / 2 ** fp) if fp else 0.0
    lc = np.log1p(cab); sel = np.unique(Z['pidx'][keep], return_index=True)[1]; sel = np.nonzero(keep)[0][sel]
    ql = kmeans1d(lc[sel], q) if q else (lambda a: a)
    v = np.where(keep, ql(lc), 0.0); st = dict(absent=int((~keep).sum()), fh=0)
    if fp:
        fh = ~keep & (unif(Z['pidx'], 300) < pr); st['fh'] = int(fh.sum())
        if fh.any() and len(sel): v[fh] = ql(lc[sel[(mix(Z['pidx'][fh], 301) % np.uint64(len(sel))).astype(np.int64)]])
    cq = np.expm1(v)
    # unigram counts from the word unigram table (stored iff count >= th1), log-count quantized
    ua = Z['pua'].astype(np.float64); ub = Z['pub'].astype(np.float64)
    allu = np.unique(np.r_[ua[ua >= th1], ub[ub >= th1]])
    qu = kmeans1d(np.log(allu), q) if q else (lambda a: a)
    ua = np.where(ua >= th1, np.exp(qu(np.log(np.maximum(ua, 1)))), 0); ub = np.where(ub >= th1, np.exp(qu(np.log(np.maximum(ub, 1)))), 0)
    N = float(Z['Nw'])
    if EXPORT is not None: EXPORT['p'] = dict(pidx=Z['pidx'][sel], lc=lc[sel], ql=ql, qu=qu)
    pmi = np.log((cq + 0.1) * N / (np.maximum(ua, 1) * np.maximum(ub, 1) * 30))
    R = len(Z['b_cost']); row = Z['prow'].astype(np.int64); slot = Z['pslot'].astype(np.int64)
    key = row * 4096 + slot; uk, inv = np.unique(key, return_inverse=True)
    best = np.full(len(uk), -1e9); np.maximum.at(best, inv, pmi); brow = uk // 4096
    out = dict(pmi_sum=np.bincount(brow, np.maximum(best, 0), R), pmi_n=np.bincount(brow, None, R).astype(float),
               cooc=np.bincount(row, np.log1p(cq), R))
    mx = np.full(R, -np.inf); np.maximum.at(mx, brow, best); out['pmi_max'] = np.where(np.isfinite(mx), mx, 0.0)
    return out, st

def entries(kind, th):
    d = json.load(open(f'{W}/lm/ngsize_{kind}.json'))['full_est']; TH = [1, 2, 3, 5, 10, 20, 50, 100, 200, 500]
    def est(k, t):  # grid value, else log-log interpolation / extrapolation beyond 500 (from the 200->500 slope)
        row = [d[str(k)][str(x)] for x in TH]
        if t in TH: return row[TH.index(t)]
        j = max(i for i in range(len(TH) - 1) if TH[i] <= t) if t < 500 else len(TH) - 2
        sl = math.log(row[j + 1] / row[j]) / math.log(TH[j + 1] / TH[j]); return int(row[j] * (t / TH[j]) ** sl)
    return [est(k + 1, int(t)) for k, t in enumerate(th)]
def pmi_entries(pf, pk):
    d = json.load(open(f'{W}/od/pmi_size_{VAR}.json'))  # piece-0 sample with counts scaled to the full corpus
    if f'{pf}_{pk}' in d: return d[f'{pf}_{pk}']
    return json.load(open(f'{W}/lm/pairsize.json'))['full_est'][str(int(pf))]  # (power-law extrapolation; only for pf=1)
def size(tw, tc, pf, pk, q, fp):
    kb = (fp or 32) + (2 if fp else 0); vb = q or 32
    ew = entries('w', tw); ec = entries('c', tc); ep = pmi_entries(pf, pk)
    MB = lambda bits: bits / 8 / 1e6
    keys = MB(kb * (sum(ew) + sum(ec))); vals = MB(vb * (sum(ew) + sum(ew[:-1]) + ew[0] + sum(ec) + sum(ec[:-1])))
    pmi = MB((kb + vb) * ep)
    return dict(keys=keys, vals=vals, pmi=pmi, total=keys + vals + pmi, n_w=ew, n_c=ec, n_p=ep,
                w_MB=MB((kb + vb) * sum(ew) + vb * (sum(ew[:-1]) + ew[0])), c_MB=MB((kb + vb) * sum(ec) + vb * sum(ec[:-1])))

def run(cfg):
    global VAR
    VAR = cfg['var']; Z = np.load(f'{W}/od/prep_{VAR}.npz')
    tw = [int(x) for x in cfg.get('w', '1,1,1,1').split(',')]; tc = [int(x) for x in cfg.get('c', '1,1,1,1,1,1,1').split(',')]
    global RENORM; RENORM = cfg.get('rn', '1') == '1'
    q = int(cfg.get('q', 0)); fp = int(cfg.get('fp', 0)); pf = int(cfg.get('pf', 1)); pk = int(cfg.get('pk', 0))
    F = {}; fw, sw = lm_feats(Z, 'w', tw, 4, q, fp); fc, sc = lm_feats(Z, 'c', tc, len(tc), q, fp); fpm, sp = pmi_feats(Z, pf, pk, q, fp, tw[0])
    F.update(fw); F.update(fc); F.update(fpm)
    for k in ('cost', 'rel', 'rank', 'ntok', 'nseg', 'nkan', 'nhira', 'nkata', 'ndig', 'nknum', 'typ1', 'nch'): F[k] = Z['b_' + k]
    names = sorted(F); G = Z['G']; gs = rr.group_first(G)
    T = dict(G=G, LAB=Z['LAB'], SPL=Z['SPL'], X=np.stack([F[k] for k in names], 1), names=names, gsp=Z['SPL'][gs], gst=gs, ng=len(gs), POSB=None, DW=None)
    base = ['cost', 'rel', 'rank', 'ntok', 'nseg', 'nkan', 'nhira', 'nkata', 'ndig', 'nknum', 'typ1', 'nch']
    feat = base + sorted(fw) + sorted(fc) + ['pmi_sum', 'pmi_max', 'pmi_n', 'cooc']
    if cfg.get('nopmi'): feat = feat[:-4]
    tag = cfg.get('tag', 'od_last'); os.environ['SLM_TAG'] = tag
    import warnings; warnings.filterwarnings('ignore')
    s, w, l2 = rr.linear(T, feat, ['tune'])
    os.makedirs(f'{W}/od/models', exist_ok=True); os.makedirs(f'{W}/od/scores', exist_ok=True)
    os.replace(f'{W}/models/{tag}.json', f'{W}/od/models/{tag}.json'); np.save(f'{W}/od/scores/{tag}.npy', s.astype(np.float32))
    a = rr.acc_by_split(s, T); sz = size(tw, tc, pf, pk, q, fp)
    fh = {kk: (v['fh'] / max(v['absent'], 1)) for kk, v in (('w', sw), ('c', sc), ('p', sp))}
    res = dict(cfg=cfg, tune=round(a['tune'], 4), held=round(a['held'], 4), eval=round(a['eval'], 4), l2=l2,
               size={k: (round(v, 2) if isinstance(v, float) else v) for k, v in sz.items()}, fh={k: round(v, 5) for k, v in fh.items()})
    with open(f'{W}/od/results.jsonl', 'a') as o: o.write(json.dumps(res, ensure_ascii=False) + '\n')
    print(f"{tag:40s} tune {a['tune']:.3f} held {a['held']:.3f} AJIMEE {a['eval']:.3f} | total {sz['total']:.1f}MB keys {sz['keys']:.1f} vals {sz['vals']:.1f} pmi {sz['pmi']:.1f} | fh {res['fh']}", flush=True)
    return res

if __name__ == '__main__':
    cfg = dict(var=sys.argv[1]); cfg.update(dict(a.split('=', 1) for a in sys.argv[2:])); run(cfg)

#!/usr/bin/env python3
"""rr2.py : SUMIRE-LM reranker v2 on the lattice-on N-best, lenient labels, optional homophone co-occurrence vote feature.
Offline only (no app export). Work dir R = env SLM_RR2 (default $WORK/rr2):
  R/data/jwtd_kc.tsv : id, split, context, reading, expected(|alts)  (JWTD tune/held, AJIMEE eval, daily dtune/dheld, colloquial ctune/cheld)
  R/cands/cands.*.tsv: SumireNbestDumpTest rows (id, rank, surface, type, cost, lid, rid, segs reading:surface joined by \\x1f)
  R/ftab.npz         : rr.py feats on R (built by `SLM_SRX=R srx_run.sh`: only n-grams/pairs missing from the old need sets are counted)
  R/hcx/*.hcx        : HCX1 co-occurrence tables (hc_export.py on branch homophone-ctx)
rr2.py run [hcx names...]  -> R/rr2_results.json + table on stdout
Models:
  old     : adopted v1 weights (lin base+lmw+lmc+pmi, exact labels, JWTD tune, l2=0.03) trained on the OLD lattice-off ftab ($WORK/ftab.npz),
            applied unchanged to the new N-best (= the baseline 'current reranker on lattice-on N-best').
  v2      : same dense features retrained on the new N-best, train = tune+dtune+ctune, positives = lenient-correct set
            (listwise softmax, loss = -log sum_{p in lenient-ok} P(p)), l2 by 2-fold CV on the training groups.
  v2+hv_X : v2 + co-occurrence vote features from table X (hv = sum of chunk votes of the candidate vs Sumire rank 0 when the
            candidate is a pure homophone variant and every differing chunk maps to a table pair; hvm = mapped indicator).
Lenient label: NFKC, whitespace removed, then difflib opcodes between candidate and each gold; every non-equal chunk must be hiragana-equal or have an all-kana side.
AJIMEE (eval) and all held splits are report-only."""
import sys, os, re, json, glob, struct, difflib, unicodedata, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
WORK = os.environ.get('SLM_WORK0', '/workspace/work/sumire_lm')
R = os.environ.get('SLM_RR2', f'{WORK}/rr2')
os.environ['SLM_WORK'] = R
import rr
TRAIN = ['tune', 'dtune', 'ctune']
SPLITS = ['tune', 'held', 'eval', 'dtune', 'dheld', 'ctune', 'cheld']

# ------------------------------------------------------------------ lenient
KANA = re.compile(r'^[ぁ-ゖァ-ヺーゝゞヽヾ]+$')
def hira(s): return ''.join(chr(ord(c) - 0x60) if 'ァ' <= c <= 'ヶ' else c for c in unicodedata.normalize('NFKC', s))
def lenient_eq(c, g):
    if c == g: return True
    c = re.sub(r'\s', '', nfkc(c)); g = re.sub(r'\s', '', nfkc(g))
    for op, i1, i2, j1, j2 in difflib.SequenceMatcher(None, c, g, autojunk=False).get_opcodes():
        if op == 'equal': continue
        a, b = c[i1:i2], g[j1:j2]
        if hira(a) == hira(b): continue
        if (a and KANA.match(a)) or (b and KANA.match(b)): continue
        return False
    return True

# ------------------------------------------------------------------ segments / homophone diff (as hc_common.py on homophone-ctx)
KAN = re.compile(r'[\u3400-\u9fff\u3005\uf900-\ufaff]')
def has_kan(s): return KAN.search(s) is not None
def nfkc(s): return unicodedata.normalize('NFKC', s)
def parse_segs(s):
    out = []
    for p in s.split('\x1f'):
        if p: r, _, w = p.partition(':'); out.append((r, w))
    return out
def cuts(ss):
    p = 0; o = []
    for r, _ in ss: p += len(r); o.append(p)
    return o
def chunks(sa, sb):
    ca, cb = cuts(sa), cuts(sb)
    if not ca or not cb or ca[-1] != cb[-1]: return None
    res = []; ia = ib = 0; lo = 0; rd = ''.join(r for r, _ in sa)
    for c in sorted(set(ca) & set(cb)):
        A = []; B = []; na = nb = 0
        while ia < len(sa) and ca[ia] <= c: A.append(sa[ia][1]); ia += 1; na += 1
        while ib < len(sb) and cb[ib] <= c: B.append(sb[ib][1]); ib += 1; nb += 1
        res.append((rd[lo:c], ''.join(A), ''.join(B), na, nb)); lo = c
    return res
def core(a, b):
    i = 0
    while i < min(len(a), len(b)) and a[i] == b[i]: i += 1
    j = 0
    while j < min(len(a), len(b)) - i and a[-1 - j] == b[-1 - j]: j += 1
    return a[:i], a[i:len(a) - j], b[i:len(b) - j], a[len(a) - j:]
def is_homophone(dc):
    if not dc: return False
    for r, a, b, *_ in dc:
        if not (has_kan(a) and has_kan(b)): return False
        p, x, y, s = core(a, b)
        if not (has_kan(x) and has_kan(y)) or nfkc(a) == nfkc(b): return False
    return True

# ------------------------------------------------------------------ HCX1 table + vote (device-style lookup, longest window first)
def read_hcx(fn):
    d = open(fn, 'rb').read(); assert d[:4] == b'HCX1'
    ver, nW, nM, nP, nE, lam, th, clip, qb = struct.unpack_from('<5I3fI', d, 4); p = 4 + struct.calcsize('<5I3fI'); L = 2 ** (qb - 1) - 1
    def rpool(p, n):
        off = np.frombuffer(d, '<u4', n + 1, p); p += 4 * (n + 1); base = p
        return [d[base + off[i]: base + off[i + 1]].decode() for i in range(n)], base + int(off[-1])
    Wd, p = rpool(p, nW); M, p = rpool(p, nM)
    pr = [struct.unpack_from('<HHI', d, p + 8 * j) for j in range(nP)]; p += 8 * nP; sent = struct.unpack_from('<I', d, p)[0]; p += 4
    wid = np.frombuffer(d, '<u2', nE, p); p += 2 * nE; pk = np.frombuffer(d, np.uint8, (nE + 1) // 2, p)
    nib = np.empty(len(pk) * 2, np.int8); nib[0::2] = pk & 0xF; nib[1::2] = pk >> 4; nib = np.where(nib > 7, nib - 16, nib)[:nE]
    fs = [x[2] for x in pr] + [sent]
    return {(M[a], M[b]): [(Wd[wid[k]], nib[k] * clip / L) for k in range(fs[j], fs[j + 1])] for j, (a, b, f) in enumerate(pr)}
def device_pair(a, b, tab, maxw=3):
    pre, x, y, suf = core(a, b)
    for tot in range(2 * maxw, -1, -1):
        for l in range(0, min(tot, maxw, len(pre)) + 1):
            r = tot - l
            if r > maxw or r > len(suf): continue
            ma = pre[len(pre) - l:] + x + suf[:r]; mb = pre[len(pre) - l:] + y + suf[:r]
            if ((ma, mb) if ma < mb else (mb, ma)) in tab: return ma, mb
    return None
def hv_feature(tab, ctx, base, alt):
    """base/alt = (surface, segs). -> (vote, mapped)"""
    ch = chunks(base[1], alt[1])
    if ch is None: return 0.0, 0.0
    dc = [c for c in ch if c[1] != c[2]]
    if not dc or not is_homophone(dc): return 0.0, 0.0
    off = 0; blanks = []; mps = []
    for rdg, sa, sb, *_ in ch:
        if sa != sb:
            blanks.append((off, off + len(sa))); m = device_pair(sb, sa, tab)
            if m is None: return 0.0, 0.0
            mps.append(m)
        off += len(sa)
    txt = list(base[0])
    for lo, hi in blanks:
        for q in range(lo, min(hi, len(txt))): txt[q] = '\u3000'
    ctxt = nfkc(ctx) + '\u3000' + nfkc(''.join(txt)); v = 0.0
    for ma, mb in mps:
        k = (ma, mb) if ma < mb else (mb, ma); s = sum(w for word, w in tab[k] if word in ctxt); v += s if k[0] == ma else -s
    return v, 1.0

# ------------------------------------------------------------------ data
def load_all():
    meta = {}
    for l in open(f'{R}/data/jwtd_kc.tsv', encoding='utf-8'):
        f = l.rstrip('\n').split('\t'); meta[f[0]] = (f[1], f[2], [x for x in f[4].split('|') if x])
    cand = {}
    for fn in sorted(glob.glob(f'{R}/cands/cands.*.tsv')):
        for l in open(fn, encoding='utf-8'):
            f = l.rstrip('\n').split('\t')
            if len(f) < 8 or f[4] == '': continue
            cand.setdefault(f[0], {}).setdefault(int(f[1]), (f[2], parse_segs(f[7])))
    T = rr.load(); z = np.load(f'{R}/ftab.npz', allow_pickle=True); T['IDS'] = z['IDS']
    T['RANK'] = np.rint(np.expm1(rr.col(T, 'rank'))).astype(int)
    T['SURF'] = np.array([cand[i][k][0] for i, k in zip(T['IDS'], T['RANK'])], dtype=object)
    LEN = np.array([any(lenient_eq(s, g) for g in meta[i][2]) for i, s in zip(T['IDS'], T['SURF'])])
    T['EXACT'] = T['LAB'].copy(); T['LEN'] = LEN
    return T, meta, cand

def hv_table(T, cand, meta, tab):
    hv = np.zeros(len(T['G'])); hm = np.zeros(len(T['G'])); base = {}
    for r in range(len(T['G'])):
        i = T['IDS'][r]
        if i not in base: base[i] = cand[i][min(cand[i])]
        if T['RANK'][r] == min(cand[i]): continue
        hv[r], hm[r] = hv_feature(tab, meta[i][1], base[i], cand[i][T['RANK'][r]])
    return hv, hm

def picks(s, T):
    o = np.lexsort((-s, T['G'])); return o[rr.group_first(T['G'][o])]
def accs(s, T):
    f = picks(s, T); out = {}
    for sp in SPLITS:
        m = T['gsp'] == sp
        if m.any(): out[sp] = (float(T['EXACT'][f][m].mean()), float(T['LEN'][f][m].mean()), int(m.sum()))
    return out, f

def train_lin(T, X, lab, splits, l2s=(0.01, 0.03, 0.1, 0.3, 1)):
    T2 = dict(T); T2['LAB'] = lab
    tr = rr.train_rows(T2, splits); mu = X[tr].mean(0); sd = X[tr].std(0) + 1e-9; Xs = (X - mu) / sd
    gw = rr.group_weights(T2, splits); best = None
    for l2 in l2s:
        acc = []
        for k in range(2):
            w = rr.softmax_train(Xs, T['G'], lab, rr.train_rows(T2, splits, (k, 2)), l2, gw=gw)
            acc.append(rr.acc_by_split_rows(Xs @ w, T2, rr.train_rows(T2, splits, (1 - k, 2))))
        if best is None or np.mean(acc) > best[0]: best = (np.mean(acc), l2)
    w = rr.softmax_train(Xs, T['G'], lab, tr, best[1], gw=gw)
    return Xs @ w, dict(mu=mu.tolist(), sd=sd.tolist(), w=w.tolist(), l2=best[1])

def mix_train(X, G, EX, LE, rows, l2, beta, w0=None):
    """listwise softmax, loss = -sum_g [ log P(exact gold) + beta * log P(lenient-ok set) ] + l2|w|^2
    (groups without an exact gold contribute only the lenient term). Pure-lenient training (beta->inf) collapses onto
    all-kana candidates (every all-kana chunk is lenient-ok), so the exact term anchors the notation."""
    from scipy.optimize import minimize
    Xr = X[rows]; Gr = G[rows]; ug, Gi = np.unique(Gr, return_inverse=True); ng = Gi.max() + 1
    E = EX[rows].astype(float); Lm = LE[rows].astype(float)
    hasE = np.bincount(Gi, E, ng) > 0
    def f(w):
        sc = Xr @ w; m = np.full(ng, -np.inf); np.maximum.at(m, Gi, sc); e = np.exp(sc - m[Gi])
        Z = np.bincount(Gi, e, ng); ZE = np.bincount(Gi, e * E, ng); ZL = np.bincount(Gi, e * Lm, ng)
        lZE = np.where(hasE, np.log(np.maximum(ZE, 1e-300)), 0.0)
        loss = -(lZE - hasE * np.log(Z)).sum() - beta * (np.log(ZL) - np.log(Z)).sum() + l2 * (w @ w)
        p = e / Z[Gi]; qE = np.where(hasE[Gi], e * E / np.maximum(ZE[Gi], 1e-300), 0.0); qL = e * Lm / ZL[Gi]
        g = Xr.T @ ((hasE[Gi] + beta) * p - qE - beta * qL) + 2 * l2 * w
        return loss, g
    return minimize(f, np.zeros(X.shape[1]) if w0 is None else w0, jac=True, method='L-BFGS-B', options=dict(maxiter=300)).x

def train_mix(T, X, splits, betas=(0.0, 0.3, 1.0, 3.0), l2s=(0.01, 0.1)):
    """beta, l2 by 2-fold CV on the training groups (groups with >=1 lenient-ok cand), criterion = (exact + lenient top1) / 2"""
    G = T['G']; gl = np.zeros(T['ng'], bool); np.logical_or.at(gl, G, T['LEN'])
    gm = np.isin(T['gsp'], splits) & gl; tr = gm[G]
    mu = X[tr].mean(0); sd = X[tr].std(0) + 1e-9; Xs = (X - mu) / sd
    half = [(gm & (np.arange(T['ng']) % 2 == k))[G] for k in range(2)]
    best = None; cvlog = []
    for beta in betas:
        for l2 in l2s:
            acc = []
            for k in range(2):
                w = mix_train(Xs, G, T['EXACT'], T['LEN'], half[k], l2, beta)
                f = picks(Xs @ w, T); vg = np.zeros(T['ng'], bool); vg[G[half[1 - k]]] = True
                acc.append((T['EXACT'][f][vg].mean() + T['LEN'][f][vg].mean()) / 2)
            cvlog.append((beta, l2, float(np.mean(acc))))
            if best is None or np.mean(acc) > best[0]: best = (np.mean(acc), beta, l2)
    w = mix_train(Xs, G, T['EXACT'], T['LEN'], tr, best[2], best[1])
    print('  cv', cvlog, flush=True)
    return Xs @ w, dict(mu=mu.tolist(), sd=sd.tolist(), w=w.tolist(), beta=best[1], l2=best[2])

def err_class(T, f, cand, meta):
    """remaining lenient errors per split: homophone / boundary (cut sets differ in a differing chunk) / other / missing (no lenient-ok cand)"""
    G = T['G']; okrow = {}
    for r in np.nonzero(T['LEN'])[0]:
        g = G[r]
        if g not in okrow or T['RANK'][r] < T['RANK'][okrow[g]]: okrow[g] = r
    out = {}
    for g, r in enumerate(f):
        sp = T['gsp'][g]
        if T['LEN'][r]: continue
        c = out.setdefault(sp, dict(homophone=0, boundary=0, other=0, missing=0))
        if g not in okrow: c['missing'] += 1; continue
        i = T['IDS'][r]; a = cand[i][T['RANK'][r]]; b = cand[i][T['RANK'][okrow[g]]]
        ch = chunks(a[1], b[1]); dc = [x for x in ch if x[1] != x[2]] if ch else None
        if dc and is_homophone(dc): c['homophone'] += 1
        elif dc and any(max(x[3], x[4]) > 1 for x in dc): c['boundary'] += 1  # chunks are cut at common cuts, so >1 seg = cuts differ
        else: c['other'] += 1
    return out

def main():
    hcx = sys.argv[2:] if len(sys.argv) > 2 else []
    T, meta, cand = load_all()
    names = rr.dense_names(T, ['base', 'lmw', 'lmc', 'pmi'])
    X = np.stack([rr.col(T, n) for n in names], 1)
    res = {}; P = {}
    def rep(name, s, info=None):
        a, f = accs(s, T); res[name] = dict(acc=a, info=info); P[name] = f
        print(f'{name:16s} ' + ' '.join(f'{sp}:{a[sp][0]:.3f}/{a[sp][1]:.3f}' for sp in SPLITS if sp in a), flush=True)
    rep('sumire', -rr.col(T, 'rank'))
    orc = {sp: (float(np.bincount(T['G'], T['EXACT'])[T['gsp'] == sp].astype(bool).mean()),
                float(np.bincount(T['G'], T['LEN'])[T['gsp'] == sp].astype(bool).mean())) for sp in SPLITS}
    res['oracle@50'] = dict(acc=orc); print('oracle@50       ', ' '.join(f'{sp}:{v[0]:.3f}/{v[1]:.3f}' for sp, v in orc.items()))
    # old adopted weights (trained on the old lattice-off ftab, exact labels, JWTD tune, l2=0.03)
    rr.W = WORK; To = rr.load(); rr.W = R
    Xo = np.stack([rr.col(To, n) for n in names], 1); tro = rr.train_rows(To, ['tune'])
    mu = Xo[tro].mean(0); sd = Xo[tro].std(0) + 1e-9
    wo = rr.softmax_train((Xo - mu) / sd, To['G'], To['LAB'], tro, 0.03, gw=rr.group_weights(To, ['tune']))
    so = ((Xo - mu) / sd) @ wo; print('old on old ftab', rr.fmt(rr.acc_by_split(so, To))); del To, Xo
    rep('old', ((X - mu) / sd) @ wo)
    s, m = train_lin(T, X, T['EXACT'], ['tune']); rep('retrain_exact_jw', s, m['l2'])
    s, m = train_lin(T, X, T['LEN'], TRAIN); rep('v2_lenonly', s, m['l2'])
    s, m = train_mix(T, X, TRAIN); rep('v2', s, (m['beta'], m['l2'])); models = {'v2': dict(names=names, **m)}
    for h in hcx:
        cf = f'{R}/hv_{h}.npz'
        if os.path.exists(cf): z = np.load(cf); hv, hm = z['hv'], z['hm']
        else: hv, hm = hv_table(T, cand, meta, read_hcx(f'{R}/hcx/{h}.hcx')); np.savez(cf, hv=hv, hm=hm)
        Xh = np.concatenate([X, hv[:, None], hm[:, None]], 1)
        s, m = train_mix(T, Xh, TRAIN); rep(f'v2+hv_{h}', s, (m['beta'], m['l2'])); models[f'v2+hv_{h}'] = dict(names=names + ['hv', 'hvm'], **m)
        print('  hv nonzero rows', int((hm > 0).sum()), 'w(hv,hvm)', [round(x, 3) for x in m['w'][-2:]])
    # fixes / regressions (lenient and exact) vs old, error classes
    for k in list(res):
        if k in ('sumire', 'old', 'oracle@50'): continue
        fr = {}
        for sp in SPLITS:
            gm = T['gsp'] == sp
            for lab in ('LEN', 'EXACT'):
                a = T[lab][P['old']][gm]; b = T[lab][P[k]][gm]
                fr[f'{sp}_{lab}'] = (int((b & ~a).sum()), int((a & ~b).sum()))
        res[k]['fixreg_vs_old'] = fr
    for k in P: res[k]['errclass'] = err_class(T, P[k], cand, meta)
    json.dump(dict(res=res, models=models), open(f'{R}/rr2_results.json', 'w'), ensure_ascii=False, indent=1, default=float)
    for k in res:
        if 'fixreg_vs_old' in res[k]: print(k, 'fix/reg vs old (lenient):', {sp: res[k]['fixreg_vs_old'][f'{sp}_LEN'] for sp in SPLITS})
    for k in P: print(k, 'lenient errors:', res[k]['errclass'])

if __name__ == '__main__' and sys.argv[1] == 'run': main()

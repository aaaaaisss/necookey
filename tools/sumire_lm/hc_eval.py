#!/usr/bin/env python3
"""hc_eval.py : pair-wise context-vote tables + rescoring of Sumire top1 within homophone spans.
Table (per confusable pair a<b): delta(w) = log P(w|a) - log P(w|b), P(w|m) = (c(m,w) + ALPHA*p_ab(w)) / (N_m + ALPHA),
p_ab(w) = (c(a,w)+c(b,w)) / (N_a+N_b); c = context-token counts within +-WIN (or +-NEAR) of member occurrences (hc_count.py);
keep top-KW words by |delta|*(c(a,w)+c(b,w)) with c(a,w)+c(b,w) >= CMIN.
Apply: alternatives = rows[1:KALT] that are pure homophone variants of the base pick and whose every differing chunk reduces to a table pair;
score = LAM * sum_chunks vote(member_alt vs member_base | context) - (cost_alt - cost_base)/1000 ; switch to the best alt if score > 0.
Context = NFKC(left context) + base surface with the differing chunks blanked; a table word votes if it occurs as a substring.
Usage:  hc_eval.py build KW CMIN ALPHA WINKIND(a|n) [MINPAIR]   -> $HC/tab_<tag>.pkl
        hc_eval.py grid  (env HC_TABS=tag1,tag2 ...)            -> tune grid, prints best per table + held/eval for the tune-best"""
import sys, os, pickle, math, collections, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hc_common import *

_CN = {}
def build(KW, CMIN, ALPHA, wk, MINPAIR=1):
    z = np.load(f'{HC}/members.npz', allow_pickle=True); M = list(z['M']); mi = {m: i for i, m in enumerate(M)}
    occ = np.load(f'{HC}/cnt_occ.npy').astype(np.float64)
    if wk not in _CN:
        _CN.clear(); us, cs = [], []
        for j in range(6):
            c = np.load(f'{HC}/cnt_r{j}.npz'); us.append(c['u' + wk]); cs.append(c['c' + wk])
        _CN[wk] = (np.concatenate(us), np.concatenate(cs)); del us, cs
    u, cn = _CN[wk]
    st = np.r_[0, np.searchsorted(u, np.arange(1, len(M) + 1, dtype=np.int64) << 20)]
    MASK = (1 << 20) - 1
    voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb')); iv = {}
    pairs = pickle.load(open(f'{HC}/pairs.pkl', 'rb'))
    tab = {}; nent = 0
    for (a, b), n in pairs.items():
        if n < MINPAIR: continue
        ia, ib = mi[a], mi[b]; Na, Nb = occ[ia], occ[ib]
        if Na < 5 or Nb < 5: continue
        wa, ca = u[st[ia]:st[ia + 1]] & MASK, cn[st[ia]:st[ia + 1]]; wb, cb = u[st[ib]:st[ib + 1]] & MASK, cn[st[ib]:st[ib + 1]]
        ws = np.union1d(wa, wb)
        A = np.zeros(len(ws)); B = np.zeros(len(ws))
        A[np.searchsorted(ws, wa)] = ca; B[np.searchsorted(ws, wb)] = cb
        tot = A + B; keep = tot >= CMIN
        if not keep.any(): continue
        ws, A, B, tot = ws[keep], A[keep], B[keep], tot[keep]
        p = tot / (Na + Nb)
        d = np.log((A + ALPHA * p) / (Na + ALPHA)) - np.log((B + ALPHA * p) / (Nb + ALPHA))
        sc = np.abs(d) * tot; o = np.argsort(-sc)[:KW]
        tab[(a, b)] = (Na, Nb, [(int(ws[j]), float(d[j])) for j in o])
        nent += len(o)
    # word strings
    need = {w for v in tab.values() for w, _ in v[2]}
    for s, i in voc.items():
        if i in need: iv[i] = s
    tab = {k: (Na, Nb, [(iv[w], d) for w, d in L]) for k, (Na, Nb, L) in tab.items()}
    tag = f'k{KW}c{CMIN}a{ALPHA}{wk}m{MINPAIR}'
    pickle.dump(tab, open(f'{HC}/tab_{tag}.pkl', 'wb'))
    words = {w for v in tab.values() for w, _ in v[2]}
    print(tag, 'pairs', len(tab), 'entries', nent, 'distinct words', len(words))
    return tag

# ------------------------------------------------------------ apply
_mp = None
DEV = None  # set of (a,b) sorted pairs -> tokenizer-free lookup (what the device would do)
def device_pair(a, b, P, maxw=3):
    """strip common prefix/suffix, then widen the core by l chars left / r chars right (same on both sides, l,r <= maxw, from the shared
    prefix/suffix) and return the first (smallest l+r) window pair that is in P"""
    pre, x, y, suf = core(a, b)
    for tot in (range(2 * maxw, -1, -1) if os.environ.get('HC_DEVORDER') == 'long' else range(0, 2 * maxw + 1)):
        for l in range(0, min(tot, maxw, len(pre)) + 1):
            r = tot - l
            if r > maxw or r > len(suf): continue
            ma = pre[len(pre) - l:] + x + suf[:r]; mb = pre[len(pre) - l:] + y + suf[:r]
            if ((ma, mb) if ma < mb else (mb, ma)) in P: return ('ok', ma, mb)
    return None
def member_pair_cached(a, b):
    global _mp
    if _mp is None:
        import hc_mine_lib; _mp = hc_mine_lib
    return _mp.member_pair(a, b)

def prepare(nb, G, KALT=50, base_idx=None):
    """for each sentence: base row index, list of (alt_idx, cost diff, [(ma, mb_base)], context text). base_idx: id -> row index (default 0)"""
    out = {}
    for i, rows in nb.items():
        sp, ctx, rd, gold = G[i]
        b = base_idx[i] if base_idx else 0
        t = rows[b]; ts = t[3] if isinstance(t[3], list) else parse_segs(t[3])
        alts = []
        for k, r in enumerate(rows[:KALT]):
            if k == b: continue
            rs = r[3] if isinstance(r[3], list) else parse_segs(r[3])
            dc = diff_chunks(ts, rs)
            if not dc or not is_homophone_diff(dc): continue
            # chunks with offsets in the base surface
            ch = chunks(ts, rs); off = 0; blanks = []; mps = []; ok = True
            for rdg, sa, sb in ch:
                if sa != sb:
                    blanks.append((off, off + len(sa)))
                    m = device_pair(sb, sa, DEV) if DEV is not None else member_pair_cached(sb, sa)  # (alt member, base member)
                    if not m or m[0] != 'ok': ok = False; break
                    mps.append((m[1], m[2]))
                off += len(sa)
            if not ok: continue
            s = t[1]; txt = list(s)
            for lo, hi in blanks:
                for q in range(lo, min(hi, len(txt))): txt[q] = '\u3000'
            ctxt = nfkc(ctx) + '\u3000' + nfkc(''.join(txt))
            alts.append((k, r[2] - t[2], mps, ctxt, r[1] in gold))
        out[i] = (b, t[1] in gold, alts)
    return out

def vote(tab, ma, mb, ctxt, prior=0.0):
    k = (ma, mb) if ma < mb else (mb, ma)
    v = tab.get(k)
    if v is None: return None
    Na, Nb, L = v; s = sum(d for w, d in L if w in ctxt)
    s += prior * math.log(Na / Nb)
    return s if k[0] == ma else -s

def decide(prep, tab, LAM, TH=0.0, prior=0.0, scale=1000.0):
    """-> dict id -> (correct_after, switched_alt_idx or None, best score)"""
    res = {}
    for i, (b, okb, alts) in prep.items():
        best = None
        for k, dcost, mps, ctxt, okk in alts:
            vs = [vote(tab, ma, mb, ctxt, prior) for ma, mb in mps]
            if any(v is None for v in vs): continue
            sc = LAM * sum(vs) - dcost / scale
            if sc > TH and (best is None or sc > best[0]): best = (sc, k, okk)
        res[i] = (best[2], best[1], best[0]) if best else (okb, None, None)
    return res

if __name__ == '__main__' and sys.argv[1] == 'build':
    # hc_eval.py build "KW,CMIN,ALPHA,WK,MINPAIR;..."
    for spec in sys.argv[2].split(';'):
        a = spec.split(','); build(int(a[0]), int(a[1]), float(a[2]), a[3], int(a[4]) if len(a) > 4 else 1)

# ------------------------------------------------------------ compact numpy table with per-entry score (for global budgets)
def build_np(KW, CMIN, ALPHA, wk, MINPAIR=1):
    z = np.load(f'{HC}/members.npz', allow_pickle=True); M = list(z['M']); mi = {m: i for i, m in enumerate(M)}
    occ = np.load(f'{HC}/cnt_occ.npy').astype(np.float64)
    us, cs = [], []
    for j in range(6):
        c = np.load(f'{HC}/cnt_r{j}.npz'); us.append(c['u' + wk]); cs.append(c['c' + wk])
    u = np.concatenate(us); cn = np.concatenate(cs); del us, cs
    st = np.r_[0, np.searchsorted(u, np.arange(1, len(M) + 1, dtype=np.int64) << 20)]; MASK = (1 << 20) - 1
    pairs = pickle.load(open(f'{HC}/pairs.pkl', 'rb'))
    PA, PB, NA, NB, NP, OFF, WS, DS, SS = [], [], [], [], [], [0], [], [], []
    for (a, b), n in sorted(pairs.items()):
        if n < MINPAIR: continue
        ia, ib = mi[a], mi[b]; Na, Nb = occ[ia], occ[ib]
        if Na < 5 or Nb < 5: continue
        wa, ca = u[st[ia]:st[ia + 1]] & MASK, cn[st[ia]:st[ia + 1]]; wb, cb = u[st[ib]:st[ib + 1]] & MASK, cn[st[ib]:st[ib + 1]]
        ws = np.union1d(wa, wb); A = np.zeros(len(ws)); B = np.zeros(len(ws))
        A[np.searchsorted(ws, wa)] = ca; B[np.searchsorted(ws, wb)] = cb
        tot = A + B; keep = tot >= CMIN
        if not keep.any(): continue
        ws, A, B, tot = ws[keep], A[keep], B[keep], tot[keep]; p = tot / (Na + Nb)
        d = np.log((A + ALPHA * p) / (Na + ALPHA)) - np.log((B + ALPHA * p) / (Nb + ALPHA))
        sc = np.abs(d) * tot; o = np.argsort(-sc)[:KW]
        PA.append(a); PB.append(b); NA.append(Na); NB.append(Nb); NP.append(n)
        WS.append(ws[o].astype(np.int32)); DS.append(d[o].astype(np.float32)); SS.append(sc[o].astype(np.float32)); OFF.append(OFF[-1] + len(o))
    WS = np.concatenate(WS); need = set(np.unique(WS).tolist())
    voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb')); words = {i: s for s, i in voc.items() if i in need}
    tag = f'np{KW}c{CMIN}a{ALPHA:g}{wk}m{MINPAIR}'
    np.savez(f'{HC}/tabnp_{tag}.npz', PA=np.array(PA, dtype=object), PB=np.array(PB, dtype=object), NA=np.array(NA), NB=np.array(NB), NP=np.array(NP),
             OFF=np.array(OFF), W=WS, D=np.concatenate(DS), S=np.concatenate(SS),
             WID=np.array(sorted(words)), WSTR=np.array([words[i] for i in sorted(words)], dtype=object))
    print(tag, 'pairs', len(PA), 'entries', len(WS), 'words', len(words), flush=True)

def load_np(tag, needed=None, thr=0.0, kmax=None):
    """-> tab dict (a,b) -> (Na, Nb, [(word, d)]) restricted to needed pairs, entries with S >= thr, first kmax per pair"""
    z = np.load(f'{HC}/tabnp_{tag}.npz', allow_pickle=True)
    ws = dict(zip(z['WID'].tolist(), z['WSTR'].tolist())); OFF = z['OFF']; tab = {}
    SS, WW, DD, NA, NB = z['S'], z['W'], z['D'], z['NA'], z['NB']
    for j, (a, b) in enumerate(zip(z['PA'], z['PB'])):
        if needed is not None and (a, b) not in needed: continue
        lo, hi = OFF[j], OFF[j + 1]; S = SS[lo:hi]; m = S >= thr
        Wd = WW[lo:hi][m]; Dd = DD[lo:hi][m]
        if kmax: Wd, Dd = Wd[:kmax], Dd[:kmax]
        tab[(a, b)] = (float(NA[j]), float(NB[j]), [(ws[int(w)], float(d)) for w, d in zip(Wd, Dd)])
    return tab

if __name__ == '__main__' and sys.argv[1] == 'buildnp':
    for spec in sys.argv[2].split(';'):
        a = spec.split(','); build_np(int(a[0]), int(a[1]), float(a[2]), a[3], int(a[4]) if len(a) > 4 else 1)

#!/usr/bin/env python3
"""odprep.py VARIANT  (fug | seg) : precompute everything the on-device simulation (odsim.py) needs for the JWTD tune/held and
AJIMEE rows (auto rows dropped), so every pruning/quantization setting is a cheap vectorized pass.
-> WORK/od/prep_{VARIANT}.npz
Per LM kind (w = word 4-gram, c = char 7-gram) and order k, per token position j (n-gram ENDING at j):
  {kind}idx{k}  index into need_{kind}{k} (-1: position too close to the row start)
  {kind}cnt{k}  corpus count (full 4.5GB CC-100, fugashi-tokenized corpus for both variants)
  {kind}lp{k}   log p_k of the unpruned interpolated model (word: abs. discount D=.75 + KN unigram; char: Dirichlet d=2, raw unigram)
  {kind}bw{k}   log backoff weight of the k-gram ending at j used as a context (word: D*N1+(h.)/c(h); char: d/(c(h)+d)); 0 if unseen
  ARPA view: p(w|h) = P[h w] if stored else BOW[h] * p(w|h'), which equals the interpolated model exactly when nothing is pruned.
Rows: G, LAB, SPL, base structural features (same as rr.py), PMI pair lists."""
import sys, os, math, pickle, re, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ngcount import hashes, lookup
from common import rowdict, content
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm'); V_ = sys.argv[1]
SUF = '' if V_ == 'fug' else '_seg'; WK = 'w' if V_ == 'fug' else 'ws'
os.makedirs(f'{W}/od', exist_ok=True)
KAN = re.compile(r'[一-龠々〆ヵヶ]'); HIRA = re.compile(r'[ぁ-ゖ]'); KATA = re.compile(r'[ァ-ヺー]'); DIG = re.compile(r'[0-9０-９]')
KNUM = re.compile(r'[〇一二三四五六七八九十百千万億]'); P = np.uint64(0x9E3779B97F4A7C15)
items = pickle.load(open(f'{W}/items{SUF}.pkl', 'rb')); voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb'))
z = np.load(f'{W}/lm/seqs{SUF}.npz')
nrow = [len(it[4]) for it in items]; keepg = [it[1] != 'auto' for it in items]
rowkeep = np.repeat(np.array(keepg), nrow)
out = {}
def subseq(x, L):
    off = np.r_[0, np.cumsum(L)]; m = rowkeep
    xs = np.concatenate([x[off[i]:off[i + 1]] for i in np.nonzero(m)[0]]); return xs, L[m]
for kind, K, st_name, need_name, xk, lk in (('w', 4, f'stats_{WK}', f'need_{WK}', 'wx', 'wl'), ('c', 7, 'stats_c', 'need_c', 'cx', 'cl')):
    x, L = subseq(z[xk], z[lk]); x = x.astype(np.uint32)
    st = np.load(f'{W}/lm/{st_name}.npz'); nd = np.load(f'{W}/lm/{need_name}.npz'); need = [nd[f'k{k}'] for k in range(1, K + 1)]
    start = np.cumsum(L) - L; pos = np.arange(len(x)) - np.repeat(start, L)
    hs = hashes(x, K); n = len(x); out[f'{kind}pos'] = pos.astype(np.int32); out[f'{kind}L'] = L.astype(np.int32)
    out[f'{kind}x0'] = (x == 0)
    N = float(st['n'])
    if kind == 'w': nlt = float(st['nl_tot']); Vt = len(voc); D = 0.75
    else: Vt = 30000; D = 2.0
    idxs, cnts = [], []
    for k in range(1, K + 1):
        ok = pos >= k - 1; j = np.nonzero(ok)[0]; s0 = j - k + 1
        i, m = lookup(need[k - 1], hs[k - 1][s0]); assert m.all(), (kind, k, (~m).sum())
        idx = np.full(n, -1, np.int32); idx[j] = i; c = np.zeros(n, np.int64); c[j] = st[f'c{k}'][i]
        idxs.append(idx); cnts.append(c); out[f'{kind}idx{k}'] = idx; out[f'{kind}cnt{k}'] = c.astype(np.int64)
    # unpruned probabilities
    c1 = cnts[0]
    if kind == 'w': nl1 = st['nl1'][idxs[0]]; pk = (nl1 + 0.5) / (nlt + 0.5 * Vt)
    else: pk = (c1 + 0.5) / (N + 0.5 * Vt)
    out[f'{kind}lp1'] = np.log(pk).astype(np.float32); out[f'{kind}unk'] = np.array(math.log(0.5 / ((nlt if kind == 'w' else N) + 0.5 * Vt)))
    for k in range(2, K + 1):
        j = np.nonzero(pos >= k - 1)[0]; cc = cnts[k - 2][j - 1]; cf = cnts[k - 1][j]; prev = pk[j]
        if kind == 'w':
            nf = st[f'nf{k}'][idxs[k - 2][j - 1]].astype(np.float64)
            p = np.where(cc > 0, np.maximum(cf - D, 0) / np.maximum(cc, 1) + D * np.maximum(nf, 0.5) / np.maximum(cc, 1) * prev, prev)
        else: p = (cf + D * prev) / (cc + D)
        pk = pk.copy(); pk[j] = p
        lp = np.zeros(n, np.float32); lp[j] = np.log(p); out[f'{kind}lp{k}'] = lp
    for k in range(1, K):
        c = cnts[k - 1]; v = np.zeros(n, np.float32); m = c > 0
        if kind == 'w':
            nf = st[f'nf{k+1}'][np.maximum(idxs[k - 1], 0)].astype(np.float64)
            v[m] = np.log(D * np.maximum(nf[m], 0.5) / c[m])
        else: v[m] = np.log(D / (c[m] + D))
        out[f'{kind}bw{k}'] = v
    out[f'{kind}N'] = np.array(N); print(kind, 'positions', n, flush=True)
    del hs
# rows
nw = np.load(f'{W}/lm/need_{WK}.npz'); c1all = np.load(f'{W}/lm/stats_{WK}.npz')['c1']; need1 = nw['k1']
u1 = dict(zip(need1.tolist(), c1all.tolist()))
pr = np.load(f'{W}/lm/need_pair{"_seg" if V_ == "seg" else ""}.npz')['h']
G, LAB, SPL, IDS = [], [], [], []
B = {k: [] for k in ('cost', 'rel', 'rank', 'ntok', 'nseg', 'nkan', 'nhira', 'nkata', 'ndig', 'nknum', 'typ1', 'nch')}
prow, pslot, pua, pub, pa, pb = [], [], [], [], [], []
ROW = 0; g = 0; CANDS = []
for (i, sp, exp, ctx, rows) in items:
    if sp == 'auto': continue
    rows = [rowdict(t) for t in rows]; r0 = rows[0]
    A = sorted({voc[w] for w in content(r0, 0, r0['nc']) if w in voc})
    cs = [content(r, r['nc'], len(r['wt'])) for r in rows]; common = set.intersection(*cs) if cs else set()
    for r, cset in zip(rows, cs):
        G.append(g); LAB.append(r['s'] in exp); SPL.append(sp); IDS.append(i); s = r['s']
        B['cost'].append(r['cost'] / 1000); B['rel'].append((r['cost'] - r0['cost']) / 1000); B['rank'].append(math.log1p(r['rank']))
        B['ntok'].append(len(r['wt']) - r['nc']); B['nseg'].append(r['nseg']); B['nch'].append(len(s))
        B['nkan'].append(len(KAN.findall(s))); B['nhira'].append(len(HIRA.findall(s))); B['nkata'].append(len(KATA.findall(s)))
        B['ndig'].append(len(DIG.findall(s))); B['nknum'].append(len(KNUM.findall(s))); B['typ1'].append(float(r['type'] == 1))
        slot = 0
        for w in sorted(cset - common):
            b = voc.get(w)
            if b is None or not A: continue
            for a in A:
                prow.append(ROW); pslot.append(slot); pua.append(u1.get(a, 0)); pub.append(u1.get(b, 0)); pa.append(a); pb.append(b)
            slot += 1
        ROW += 1
    g += 1
pc = np.load(f'{W}/lm/pair{"_seg" if V_ == "seg" else ""}.npz')['c']
ph = np.array(pa, np.uint64) * P + np.array(pb, np.uint64); k_ = np.searchsorted(pr, ph); k_[k_ == len(pr)] = 0
pidx = np.where(pr[k_] == ph, k_, -1).astype(np.int64); out.update(G=np.array(G), LAB=np.array(LAB), SPL=np.array(SPL), IDS=np.array(IDS),
    prow=np.array(prow, np.int32), pslot=np.array(pslot, np.int32), pidx=pidx, pcnt=np.where(pidx >= 0, pc[np.maximum(pidx, 0)], 0),
    pua=np.array(pua, np.int64), pub=np.array(pub, np.int64), pa=np.array(pa, np.int64), pb=np.array(pb, np.int64),
    phash=np.where(pidx >= 0, pr[np.maximum(pidx, 0)], 0), Nw=np.array(float(np.load(f'{W}/lm/stats_{WK}.npz')['n'])))
for k, v in B.items(): out['b_' + k] = np.array(v, np.float64)
assert ROW == len(out['wL']) == len(out['cL']), (ROW, len(out['wL']), len(out['cL']))
np.savez(f'{W}/od/prep_{V_}.npz', **out); print('rows', ROW, 'groups', g, 'pairs', len(prow), 'missing pair idx', int((pidx < 0).sum()))

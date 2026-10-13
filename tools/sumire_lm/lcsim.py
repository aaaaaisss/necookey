#!/usr/bin/env python3
"""lcsim.py : 案2 lattice-local word bigram cost (device simulation, top-50 re-ranking) + table export.
Edge cost added to the Sumire path (prev -> node, node != EOS):
    corr = LAM * (-lp(node | prev)) + MU + OMEGA * oov(node)        [cost units, rounded to int]
    lp(w | v) = q(log p2(w|v)) if the pair (v, w) is stored, else q(log alpha(v)) + q(log p1(w)); prev = BOS -> q(log p1(w))
    p1 = KN unigram (continuation counts), p2 = interpolated absolute discounting (D), alpha(v) = D N1+(v .) / c(v)
    words below the unigram cutoff are OOV (p1 = floor, alpha = 1); q() = rounding to 1/QS nats (int16 on device)
Keys: LatticeLmCost.wordHash (FNV-1a + fmix32) of the raw node surface (Sumire's own word split, no NFKC), so the device needs no vocabulary.
Pairs kept: the top NP pairs by c(vw) * |log p2 - log(alpha(v) p1(w))| (weighted log-ratio pruning), count >= 2.
LAM / MU / OMEGA: linear listwise model (cost + lp sum + #OOV + #words) fitted on JWTD tune (l2 by tune 2-fold CV).
lcsim.py run np=500000 cu=20 [qs=7] [d=0.75] [export=PATH]   -> prints tune/held/eval top1 (Sumire-student, top-50), sizes"""
import sys, os, glob, pickle, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
WORK = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm'); O = f'{WORK}/lcost'; B = f'{O}/bg'
P = dict(a.split('=', 1) for a in sys.argv[2:])
NPAIR = int(float(P.get('np', 500000))); CU = int(P.get('cu', 20)); QS = float(P.get('qs', 7)); D = float(P.get('d', 0.75))
VB = 1 << 20

def jhash(s):
    """word key, identical to LatticeLmCost.wordHash (Kotlin): FNV-1a 32 over UTF-16 code units, then murmur3 fmix32.
    (Java String.hashCode collides far too often on short CJK strings: 31*c1+c2.)"""
    h = 0x811C9DC5; b = s.encode('utf-16-le')
    for i in range(0, len(b), 2):
        h = ((h ^ (b[i] | (b[i + 1] << 8))) * 0x01000193) & 0xFFFFFFFF
    h ^= h >> 16; h = (h * 0x85EBCA6B) & 0xFFFFFFFF; h ^= h >> 13; h = (h * 0xC2B2AE35) & 0xFFFFFFFF; h ^= h >> 16
    return h - (1 << 32) if h >= 1 << 31 else h

voc = pickle.load(open(f'{WORK}/lm/voc.pkl', 'rb'))
uni = np.load(f'{WORK}/lm/uni_full.npy'); ws = np.load(f'{B}/wstats.npz')
cctx = ws['cctx'].astype(np.float64); n1f = ws['n1f'].astype(np.float64); n1l = ws['n1l'].astype(np.float64)
Vv = len(voc); p1 = (n1l + 0.5) / (n1l.sum() + 0.5 * Vv); lp1 = np.log(p1); lfloor = np.log(0.5 / (n1l.sum() + 0.5 * Vv))
inword = np.zeros(VB, bool); inword[:len(uni)] = uni >= CU
alpha = np.where(cctx > 0, D * n1f / np.maximum(cctx, 1), 1.0); la = np.log(np.maximum(alpha, 1e-300))
q = lambda x: np.clip(np.round(x * QS), -128, 127) / QS  # int8 on device
LP1 = np.where(inword, q(lp1), q(lfloor)); LA = np.where(inword, q(la), 0.0)
# pair selection
z = np.load(f'{B}/pairs.npz'); k = z['k']; c = z['c'].astype(np.float64)
v = (k >> np.uint64(20)).astype(np.int64); w = (k & np.uint64(VB - 1)).astype(np.int64)
ok = inword[v] & inword[w]; k, c, v, w = k[ok], c[ok], v[ok], w[ok]
lp2 = np.log((c - D) / cctx[v] + alpha[v] * p1[w])
gain = c * np.abs(lp2 - (la[v] + lp1[w]))
if NPAIR < len(k):
    sel = np.argpartition(-gain, NPAIR)[:NPAIR]; sel.sort()
else:
    sel = np.arange(len(k))
PK = k[sel]; PL = q(lp2[sel])
print('pairs kept', len(PK), 'of', len(k), 'words', int(inword.sum()), flush=True)

# features of every top-50 row from the raw segments
F = f'{O}/rows_ids.npz'
if not os.path.exists(F):
    from array import array
    segs = {}
    for fn in sorted(glob.glob(f'{WORK}/cands/cands.*.tsv')):
        for l in open(fn, encoding='utf-8'):
            f = l.rstrip('\n').split('\t')
            if len(f) >= 8 and f[4] != '' and int(f[1]) < 50: segs[(f[0], int(f[1]))] = f[7]
    items = pickle.load(open(f'{WORK}/items.pkl', 'rb'))
    ids = array('i'); nwr = array('i'); cost = array('q')
    for i, sp, exp, ctx, rows in items:
        for t in rows:
            sg = segs.get((i, t[0]), '')
            sw = [x.split(':', 1)[1] for x in sg.split('\x1f')] if sg else [t[1]]
            ids.extend(voc.get(x, VB - 1) for x in sw); nwr.append(len(sw)); cost.append(t[3])
    del items, segs
    np.savez(F, ids=np.array(ids, np.int64), nw=np.array(nwr, np.int64), cost=np.array(cost, np.int64))
R = np.load(F); ids = R['ids']; nw = R['nw']; cost = R['cost'].astype(np.float64)
nrow = len(nw); row = np.repeat(np.arange(nrow), nw); first = np.r_[0, np.cumsum(nw)[:-1]]
isfirst = np.zeros(len(ids), bool); isfirst[first] = True
known = (ids < VB - 1) & inword[np.minimum(ids, VB - 1)]
idk = np.where(known, ids, VB - 1)
prev = np.r_[VB - 1, idk[:-1]]
pkey = (prev.astype(np.uint64) << np.uint64(20)) | idk.astype(np.uint64)
pos = np.searchsorted(PK, pkey); pos = np.minimum(pos, len(PK) - 1); hit = (PK[pos] == pkey) & ~isfirst & known & (prev != VB - 1)
lpe = np.where(hit, PL[pos], np.where(isfirst, 0.0, LA[prev]) + LP1[idk])
X_lp = np.bincount(row, lpe, nrow); X_oov = np.bincount(row, (~known).astype(float), nrow); X_nw = nw.astype(float)
X_hit = np.bincount(row, hit.astype(float), nrow)
print('rows', nrow, 'oov rate %.3f' % (1 - known.mean()), 'pair hit rate %.3f' % hit[~isfirst].mean(), flush=True)

from rr import softmax_train
zf = np.load(f'{WORK}/ftab.npz', allow_pickle=True); G = zf['G']; LAB = zf['LAB']; SPL = zf['SPL']
X = np.stack([cost / 1000, X_lp, X_oov, X_nw], 1)
tm = SPL == 'tune'; mu_ = X[tm].mean(0); sd = X[tm].std(0) + 1e-9; Xs = (X - mu_) / sd
gl = np.zeros(G.max() + 1, bool); np.logical_or.at(gl, G, LAB)
def acc_mask(s, m):
    g = G[m]; ss = s[m]; lab = LAB[m]; o = np.lexsort((-ss, g)); f0 = np.r_[True, g[o][1:] != g[o][:-1]]; return lab[o][f0].mean()
best = None
for l2 in (0.01, 0.1, 1.0):
    cv = [acc_mask(Xs @ softmax_train(Xs, G, LAB, tm & gl[G] & (G % 2 == kk), l2), tm & (G % 2 == 1 - kk)) for kk in (0, 1)]
    if best is None or np.mean(cv) > best[0]: best = (np.mean(cv), l2)
wv = softmax_train(Xs, G, LAB, tm & gl[G], best[1]) / sd
# student cost = cost + LAM*(-lp) + OMEGA*oov + MU*nw   (scaled so the cost coefficient is exactly 1)
scale = -wv[0] / 1000
LAM, OMEGA, MU = wv[1] / scale, -wv[2] / scale, -wv[3] / scale
corr = np.round(LAM * (-lpe) + MU + OMEGA * (~known))  # per edge, integer like the device
sc = cost + np.bincount(row, corr, nrow)
res = {sp: acc_mask(-sc, SPL == sp) for sp in ('tune', 'held', 'eval')}
size_w = int(inword.sum()) * 6; size_p = len(PK) * 5  # word: int32 hash + 2 x int8, pair: int32 fingerprint + int8
print(f'RESULT np={len(PK)} cu={CU} qs={QS} d={D} l2={best[1]} cv={best[0]:.4f} LAM={LAM:.1f} MU={MU:.1f} OMEGA={OMEGA:.1f} '
      f'tune {res["tune"]:.3f} held {res["held"]:.3f} eval {res["eval"]:.3f} size {(size_w + size_p) / 1e6:.2f}MB (words {size_w / 1e6:.2f} pairs {size_p / 1e6:.2f})', flush=True)
np.save(f'{O}/student_np{len(PK)}_cu{CU}.npy', sc)

def fmix(hv, hw):
    """pair fingerprint, identical to LatticeLmCost.pairKey (Kotlin): murmur3 fmix32 of hv*0x9E3779B1 + hw (0 -> 1)"""
    x = (hv.astype(np.int64) * 0x9E3779B1 + (hw.astype(np.int64) & 0xFFFFFFFF)) & 0xFFFFFFFF
    x ^= x >> 16; x = (x * 0x85EBCA6B) & 0xFFFFFFFF; x ^= x >> 13; x = (x * 0xC2B2AE35) & 0xFFFFFFFF; x ^= x >> 16
    x = np.where(x == 0, 1, x); return np.where(x >= 1 << 31, x - (1 << 32), x)

if 'export' in P:
    import struct
    inv = {i: s for s, i in voc.items()}
    widx = np.nonzero(inword)[0]
    hw = {}
    for i in widx:  # Java hash collisions: keep the more frequent word
        h = jhash(inv[int(i)])
        if h not in hw or uni[hw[h]] < uni[i]: hw[h] = int(i)
    hs = np.array(sorted(hw), np.int64); wi = np.array([hw[h] for h in hs])
    h_of = np.full(VB, 0, np.int64); h_of[wi] = hs; valid = np.zeros(VB, bool); valid[wi] = True
    pv = (PK >> np.uint64(20)).astype(np.int64); pw = (PK & np.uint64(VB - 1)).astype(np.int64)
    m = valid[pv] & valid[pw]
    fp = fmix(h_of[pv[m]], h_of[pw[m]]); g = gain[sel][m]; pl = np.round(PL[m] * QS).astype(np.int8)
    o = np.lexsort((-g, fp)); fp = fp[o]; pl = pl[o]
    dup = np.r_[False, fp[1:] == fp[:-1]]; fp = fp[~dup]; pl = pl[~dup]   # fingerprint collisions: keep the larger gain
    with open(P['export'], 'wb') as f:
        f.write(b'SLC2'); f.write(struct.pack('>iiffff', len(hs), len(fp), LAM, MU, OMEGA, QS))
        f.write(struct.pack('>b', int(np.clip(np.round(lfloor * QS), -128, 127))))
        f.write(hs.astype('>i4').tobytes())
        f.write(np.round(LP1[wi] * QS).astype(np.int8).tobytes()); f.write(np.round(LA[wi] * QS).astype(np.int8).tobytes())
        f.write(fp.astype('>i4').tobytes()); f.write(pl.tobytes())
    print('exported', P['export'], os.path.getsize(P['export']), 'bytes; words', len(hs), 'pairs', len(fp), 'dropped dup fp', int(dup.sum()))
    # self-check values for the Kotlin unit test
    for wd in ('東京', 'に', '行く'):
        if wd in voc: print('CHECK', wd, jhash(wd), LP1[voc[wd]] * QS, LA[voc[wd]] * QS)

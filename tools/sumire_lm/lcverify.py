#!/usr/bin/env python3
"""lcverify.py TABLE.slc [STUDENT.npy] : round-trip check of an exported SLC2 table (lcsim.py export=).
Re-implements LatticeLmCost.kt exactly (wordHash keys, pair fingerprints, int8 values, rint) on the raw Sumire
segments of every top-50 row, and compares the row scores with lcsim.py's simulation (student_*.npy) and the top1 accuracy.
lcverify.py TABLE.slc pairs FILE   -> prints correction(prev, word) for each 'prev<TAB>word' line (prev '' = BOS), for Kotlin tests"""
import sys, os, glob, pickle, struct, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
WORK = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm'); O = f'{WORK}/lcost'


def jhash(s):
    """word key, identical to LatticeLmCost.wordHash (Kotlin): FNV-1a 32 over UTF-16 code units, then murmur3 fmix32.
    (Java String.hashCode collides far too often on short CJK strings: 31*c1+c2.)"""
    h = 0x811C9DC5; b = s.encode('utf-16-le')
    for i in range(0, len(b), 2):
        h = ((h ^ (b[i] | (b[i + 1] << 8))) * 0x01000193) & 0xFFFFFFFF
    h ^= h >> 16; h = (h * 0x85EBCA6B) & 0xFFFFFFFF; h ^= h >> 13; h = (h * 0xC2B2AE35) & 0xFFFFFFFF; h ^= h >> 16
    return h - (1 << 32) if h >= 1 << 31 else h


def fmix(hv, hw):
    x = (hv * 0x9E3779B1 + (hw & 0xFFFFFFFF)) & 0xFFFFFFFF
    x ^= x >> 16; x = (x * 0x85EBCA6B) & 0xFFFFFFFF; x ^= x >> 13; x = (x * 0xC2B2AE35) & 0xFFFFFFFF; x ^= x >> 16
    if x == 0: x = 1
    return x - (1 << 32) if x >= 1 << 31 else x


class Table:
    def __init__(self, path):
        b = open(path, 'rb').read(); assert b[:4] == b'SLC2'
        nw, np_, self.lam, self.mu, self.om, self.qs = struct.unpack('>iiffff', b[4:28]); self.lf = struct.unpack('>b', b[28:29])[0]
        o = 29; hs = np.frombuffer(b, '>i4', nw, o); o += 4 * nw
        lp1 = np.frombuffer(b, np.int8, nw, o); o += nw; la = np.frombuffer(b, np.int8, nw, o); o += nw
        fp = np.frombuffer(b, '>i4', np_, o); o += 4 * np_; pl = np.frombuffer(b, np.int8, np_, o); o += np_
        assert o == len(b), (o, len(b))
        self.w = {int(h): (int(a), int(c)) for h, a, c in zip(hs, lp1, la)}
        self.p = dict(zip(fp.tolist(), pl.tolist())); self.nw, self.np = nw, np_

    def lpq(self, prev, word):
        hw = jhash(word); wv = self.w.get(hw); l1 = wv[0] if wv else self.lf
        if prev is None: return l1
        hv = jhash(prev); vv = self.w.get(hv)
        if wv and vv:
            p = self.p.get(fmix(hv, hw))
            if p is not None: return p
        return (vv[1] if vv else 0) + l1

    def corr(self, prev, word):
        # float32 params like the device (Kotlin reads floats, computes in double)
        lam, mu, om, qs = (float(np.float32(x)) for x in (self.lam, self.mu, self.om, self.qs))
        oov = 0.0 if jhash(word) in self.w else om
        return int(np.rint(lam * (-self.lpq(prev, word) / qs) + mu + oov))


if __name__ == '__main__':
    T = Table(sys.argv[1]); print('words', T.nw, 'pairs', T.np, 'LAM %.1f MU %.1f OMEGA %.1f QS %g floorQ %d' % (T.lam, T.mu, T.om, T.qs, T.lf))
    if len(sys.argv) > 3 and sys.argv[2] == 'pairs':
        for l in open(sys.argv[3], encoding='utf-8'):
            a, w = l.rstrip('\n').split('\t'); print(f'{a}\t{w}\t{T.lpq(a or None, w)}\t{T.corr(a or None, w)}')
        sys.exit()
    stud = np.load(sys.argv[2] if len(sys.argv) > 2 else f'{O}/student_np1000000_cu20.npy')
    segs = {}
    for fn in sorted(glob.glob(f'{WORK}/cands/cands.*.tsv')):
        for l in open(fn, encoding='utf-8'):
            f = l.rstrip('\n').split('\t')
            if len(f) >= 8 and f[4] != '' and int(f[1]) < 50: segs[(f[0], int(f[1]))] = f[7]
    items = pickle.load(open(f'{WORK}/items.pkl', 'rb'))
    sc = []; cost = []; cache = {}
    for i, sp, exp, ctx, rows in items:
        for t in rows:
            sg = segs.get((i, t[0]), '')
            sw = [x.split(':', 1)[1] for x in sg.split('\x1f')] if sg else [t[1]]
            s = 0; prev = None
            for w in sw:
                k = (prev, w); c = cache.get(k)
                if c is None: c = cache[k] = T.corr(prev, w)
                s += c; prev = w
            sc.append(t[3] + s); cost.append(t[3])
    sc = np.array(sc, float); d = sc - stud
    print('rows', len(sc), 'identical %.4f' % (d == 0).mean(), 'mean |diff| %.2f' % np.abs(d).mean(), 'max |diff| %d' % np.abs(d).max())
    zf = np.load(f'{WORK}/ftab.npz', allow_pickle=True); G = zf['G']; LAB = zf['LAB']; SPL = zf['SPL']
    def acc(s, m):
        g = G[m]; ss = s[m]; lab = LAB[m]; o = np.lexsort((-ss, g)); f0 = np.r_[True, g[o][1:] != g[o][:-1]]; return lab[o][f0].mean()
    for name, s in (('table (Kotlin logic)', sc), ('lcsim simulation', stud)):
        print(name, ' '.join(f'{sp} {acc(-s, SPL == sp):.3f}' for sp in ('tune', 'held', 'eval')))

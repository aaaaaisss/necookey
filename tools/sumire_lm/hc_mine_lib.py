"""member_pair(a, b): reduce a differing chunk to single fugashi-token members (shared by hc_mine.py / hc_eval.py)."""
import os, sys, pickle, fugashi
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hc_common import *
tg = fugashi.Tagger('-Owakati')
voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb'))
_tc = {}
def toks(s):
    if s not in _tc:
        out = []; p = 0
        for w in tg.parse(s).split():
            q = s.find(w, p)
            if q < 0: return None
            out.append((q, q + len(w))); p = q + len(w)
        _tc[s] = out
    return _tc[s]
def member_pair(a, b):
    if nfkc(a) != a or nfkc(b) != b: return None
    pre, x, y, suf = core(a, b)
    ta, tb = toks(a), toks(b)
    if not ta or not tb: return None
    def span(t, lo, hi):
        ov = [(s, e) for s, e in t if e > lo and s < hi]
        return (ov[0][0], ov[-1][1]) if ov else None
    sa = span(ta, len(pre), len(pre) + len(x)); sb = span(tb, len(pre), len(pre) + len(y))
    if not sa or not sb: return None
    L = max(len(pre) - sa[0], len(pre) - sb[0]); R = max(sa[1] - len(pre) - len(x), sb[1] - len(pre) - len(y))
    ma = a[len(pre) - L: len(pre) + len(x) + R]; mb = b[len(pre) - L: len(pre) + len(y) + R]
    sta = {s for s, e in ta} | {len(a)}; stb = {s for s, e in tb} | {len(b)}
    oa = len(pre) - L; ob = len(pre) - L
    if not (oa in sta and oa + len(ma) in sta and ob in stb and ob + len(mb) in stb): return ('multi', ma, mb)
    na = sum(1 for s, e in ta if s >= oa and e <= oa + len(ma)); nb = sum(1 for s, e in tb if s >= ob and e <= ob + len(mb))
    if na != 1 or nb != 1: return ('multi', ma, mb)
    if ma not in voc or mb not in voc: return ('oov', ma, mb)
    if not (has_kan(ma) and has_kan(mb)): return ('nokan', ma, mb)
    return ('ok', ma, mb)

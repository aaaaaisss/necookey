"""Homophone-context (hc_*) shared helpers: N-best loading, candidate diff, homophone classification."""
import os, re, glob, pickle, unicodedata
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm')
HC = f'{W}/hctx'
KAN = re.compile(r'[\u3400-\u9fff\u3005\uf900-\ufaff]')
def has_kan(s): return KAN.search(s) is not None
def nfkc(s): return unicodedata.normalize('NFKC', s)

def gold_table():
    """id -> (split, context, reading, gold set)"""
    g = {}
    for fn in (f'{W}/data/jwtd_kc.tsv', f'{W}/data/ajimee.tsv', f'{W}/daily.tsv', f'{W}/data/auto.tsv'):
        for l in open(fn, encoding='utf-8'):
            c = l.rstrip('\n').split('\t')
            if len(c) < 5: continue
            g[c[0]] = (c[1], c[2], c[3], set(x for x in c[4].split('|') if x))
    return g

def parse_segs(s):
    out = []
    for p in s.split('\x1f'):
        if not p: continue
        r, _, w = p.partition(':')
        out.append((r, w))
    return out

def load_nbest(files, prefixes=None, maxrank=10**9, raw=False):
    """-> dict id -> list of (rank, surface, cost, segs) sorted by rank (rows with empty cost skipped, first occurrence of a rank kept)"""
    d = {}
    for fn in files:
        for l in open(fn, encoding='utf-8'):
            c = l.rstrip('\n').split('\t')
            if len(c) < 8 or c[4] == '': continue
            i = c[0]
            if int(c[1]) >= maxrank: continue
            if prefixes and not i.startswith(prefixes): continue
            d.setdefault(i, []).append((int(c[1]), c[2], int(c[4]), c[7] if raw else parse_segs(c[7])))
    for i in d:
        seen = set(); rows = []
        for r in sorted(d[i], key=lambda x: x[0]):
            if r[0] in seen: continue
            seen.add(r[0]); rows.append(r)
        d[i] = rows
    return d

def chunks(sa, sb):
    """segments (reading, surface) of two candidates with the same total reading -> list of (reading, surfA, surfB) chunks cut at common reading cuts"""
    def cuts(ss):
        p = 0; out = []
        for r, _ in ss: p += len(r); out.append(p)
        return out
    ca, cb = cuts(sa), cuts(sb)
    if not ca or not cb or ca[-1] != cb[-1]: return None
    common = sorted(set(ca) & set(cb)); res = []; ia = ib = 0; lo = 0
    rd = ''.join(r for r, _ in sa)
    for c in common:
        A = []; B = []
        while ia < len(sa) and ca[ia] <= c: A.append(sa[ia][1]); ia += 1
        while ib < len(sb) and cb[ib] <= c: B.append(sb[ib][1]); ib += 1
        res.append((rd[lo:c], ''.join(A), ''.join(B))); lo = c
    return res

def diff_chunks(sa, sb):
    ch = chunks(sa, sb)
    if ch is None: return None
    return [(r, a, b) for r, a, b in ch if a != b]

def core(a, b):
    """strip common prefix / suffix -> (pre, coreA, coreB, suf)"""
    i = 0
    while i < min(len(a), len(b)) and a[i] == b[i]: i += 1
    j = 0
    while j < min(len(a), len(b)) - i and a[-1 - j] == b[-1 - j]: j += 1
    return a[:i], a[i:len(a) - j], b[i:len(b) - j], a[len(a) - j:]

def is_homophone_diff(dc):
    """pure homophone variant: every differing chunk has kanji on both sides and differs in a kanji-bearing core
    (kana<->kanji, okurigana-only and width differences are not homophones)"""
    if not dc: return False
    for r, a, b in dc:
        if not (has_kan(a) and has_kan(b)): return False
        p, x, y, s = core(a, b)
        if not (has_kan(x) and has_kan(y)): return False
        if nfkc(a) == nfkc(b): return False
    return True

def classify(sa, sb):
    """'same' / 'homophone' / 'variant' (same cuts... mixed) / 'other' (different split that does not reduce to chunks with kanji both sides)"""
    dc = diff_chunks(sa, sb)
    if dc is None: return 'other', None
    if not dc: return 'same', dc
    if is_homophone_diff(dc): return 'homophone', dc
    return 'other', dc

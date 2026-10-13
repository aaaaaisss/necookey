#!/usr/bin/env python3
"""recomb.py [TAG] : oracle check of 'N-best lattice recombination' = re-combining the segments that already occur in the
Sumire top-K (any candidate, aligned by reading span) into new paths. For each JWTD/AJIMEE sentence whose gold is NOT in the
top-50, test whether some gold surface (| alternatives) can be spelled as a sequence of (reading-span, surface) segments taken
from the union of the top-K candidates' segments. Also counts how many paths the recombined lattice holds.
Uses diag/bunseg_<TAG>.jsonl for the error class of the adopted reranker (rr) top1."""
import sys, os, glob, json, collections, unicodedata
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm')
TAG = sys.argv[1] if len(sys.argv) > 1 else 'lin_base+lmw+lmc+pmi_tune'
meta = {}
for fn in ('jwtd_kc', 'ajimee'):
    for l in open(f'{W}/data/{fn}.tsv'):
        f = l.rstrip('\n').split('\t'); meta[f[0]] = dict(sp=f[1], rd=f[3], exp=f[4].split('|'))
rows = collections.defaultdict(list)
for fn in sorted(glob.glob(f'{W}/cands/cands.*.tsv')):
    for l in open(fn):
        f = l.rstrip('\n').split('\t')
        if f[0] in meta and len(f) >= 8 and f[7]:
            rows[f[0]].append((int(f[1]), f[2], [tuple(x.split(':', 1)) for x in f[7].split('\x1f')]))
cls = {}
for l in open(f'{W}/diag/bunseg_{TAG}.jsonl'):
    r = json.loads(l)
    if r['sys'] == 'rr':
        cls[r['sid']] = 'A' if (r['cls'] == 'a1' or r['det'] == 'nested-func') else ('C' if r['cls'] in ('a2', 'c') else r['cls'].upper())
N = lambda s: unicodedata.normalize('NFKC', s)
def spellable(lat, rd, gold):
    """lat: {start: {(end, surface)}}; DP over (reading pos, gold pos)"""
    gold = N(gold); seen = set(); st = [(0, 0)]
    while st:
        i, j = st.pop()
        if (i, j) in seen: continue
        seen.add((i, j))
        if i == len(rd) and j == len(gold): return True
        for e, s in lat.get(i, ()):
            s = N(s)
            if gold.startswith(s, j): st.append((e, j + len(s)))
    return False
def npaths(lat, n):
    c = [0] * (n + 1); c[0] = 1
    for i in range(n):
        if c[i]:
            for e, _ in lat.get(i, ()): c[e] += c[i]
    return c[n]
res = collections.Counter(); paths = collections.defaultdict(list)
for sid, rs in rows.items():
    m = meta[sid]; rs.sort()
    if any(N(r[1]) in {N(e) for e in m['exp']} for r in rs): continue  # gold in top-50
    for K in (10, 50):
        lat = collections.defaultdict(set)
        for _, _, segs in rs[:K]:
            p = 0
            for rd_, s in segs: lat[p].add((p + len(rd_), s)); p += len(rd_)
        ok = any(spellable(lat, m['rd'], e) for e in m['exp'])
        c = cls.get(sid, '?')
        res[(m['sp'], K, c, ok)] += 1
        if m['sp'] == 'tune': paths[K].append(npaths(lat, len(m['rd'])))
for sp in ('tune', 'held', 'eval'):
    for K in (10, 50):
        line = []
        for c in ('A', 'B', 'C', 'D'):
            y, n = res[(sp, K, c, True)], res[(sp, K, c, False)]
            if y + n: line.append(f'{c}:{y}/{y + n}')
        tot = sum(v for k, v in res.items() if k[0] == sp and k[1] == K and k[3]); all_ = sum(v for k, v in res.items() if k[0] == sp and k[1] == K)
        print(f'{sp} top-{K} recombination recovers gold for {tot}/{all_} gold-outside-top-50 sentences  by rr error class {" ".join(line)}')
for K, v in paths.items():
    v.sort(); print(f'tune recombined lattice paths (top-{K}): median {v[len(v) // 2]}, p90 {v[int(len(v) * .9)]}')

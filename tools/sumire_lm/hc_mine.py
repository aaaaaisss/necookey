#!/usr/bin/env python3
"""hc_mine.py K : mine confusable pairs from Sumire N-best (top1 vs ranks 1..K-1 that are pure homophone variants of top1).
Sources (tune-side only): eval.pkl jwtd-tune + daily-tune, old.pkl auto (g*, 24k corpus clauses).
A differing chunk (same reading span) is reduced to its kanji core and widened to fugashi token boundaries; kept when each side is one
corpus token (in lm/voc.pkl). -> $HC/pairs.pkl {(a,b) sorted: n_sentences}, $HC/mine_stats.txt"""
import sys, os, pickle, collections, fugashi
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hc_common import *
K = int(sys.argv[1]) if len(sys.argv) > 1 else 20
from hc_mine_lib import member_pair, voc
G = gold_table()
pairs = collections.Counter(); why = collections.Counter(); src = collections.Counter(); multi = collections.Counter()
def mine(rows, tag):
    t = rows[0]; seen = set()
    for r in rows[1:K]:
        sa = t[3] if isinstance(t[3], list) else parse_segs(t[3]); sb = r[3] if isinstance(r[3], list) else parse_segs(r[3])
        dc = diff_chunks(sa, sb)
        if not dc or not is_homophone_diff(dc): continue
        for rd, a, b in dc:
            m = member_pair(a, b)
            if m is None: why['tokfail'] += 1; continue
            why[m[0]] += 1
            if m[0] == 'multi': multi[tuple(sorted((m[1], m[2])))] += 1
            if m[0] != 'ok': continue
            k = tuple(sorted((m[1], m[2])))
            if k not in seen: seen.add(k); pairs[k] += 1; src[tag] += 1
ev = pickle.load(open(f'{HC}/eval.pkl', 'rb'))
for i, rows in ev.items():
    if G[i][0] == 'tune': mine(rows, i[0])
old = pickle.load(open(f'{HC}/old.pkl', 'rb'))
for i, rows in old.items():
    if i[0] == 'g': mine(rows, 'auto')
del old
pickle.dump(dict(pairs), open(f'{HC}/pairs.pkl', 'wb')); pickle.dump(dict(multi), open(f'{HC}/multi.pkl', 'wb'))
with open(f'{HC}/mine_stats.txt', 'w') as f:
    print('K', K, 'pairs', len(pairs), 'reduce', dict(why), 'src', dict(src), 'multi distinct', len(multi), file=f)
    print('top', pairs.most_common(60), file=f)
    print('multi top', multi.most_common(30), file=f)
print(open(f'{HC}/mine_stats.txt').read()[:3000])

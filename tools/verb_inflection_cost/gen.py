#!/usr/bin/env python3
"""Generate app/src/main/resources/converter/verb_inflection_cost.tsv from the Mozc OSS dictionary (BSD-3).

usage: gen.py MOZC_DICT_DIR [G=300] [D=500]
  MOZC_DICT_DIR holds dictionary00.txt..dictionary09.txt (google/mozc src/data/dictionary_oss).
For 動詞,自立 entries that share (reading, left id) but differ in surface (務め/努め), each inflected form is
mapped to its lemma's 基本形 (same kanji stem + stem reading + conjugation type). When the cheapest inflected
surface differs from the surface whose 基本形 is cheapest, the 基本形 gap is >= G, and the inflected gap is
<= D, the 基本形 winner gets a negative delta that puts it 1 below the inflected winner.
Output: reading \\t left_id \\t surface \\t delta
"""
import collections, glob, os, re, sys

src = sys.argv[1]
G = int(sys.argv[2]) if len(sys.argv) > 2 else 300
D = int(sys.argv[3]) if len(sys.argv) > 3 else 500
here = os.path.dirname(os.path.abspath(__file__))
app = os.path.join(here, '..', '..', 'app', 'src', 'main')
idf = {}
for l in open(os.path.join(app, 'assets', 'id.def'), encoding='utf-8'):
    i, p = l.split(' ', 1)
    idf[int(i)] = p.strip().split(',')
H = re.compile(r'[ぁ-ゖ]+$')
ents = []
for f in sorted(glob.glob(os.path.join(src, 'dictionary0*.txt'))):
    for l in open(f, encoding='utf-8'):
        y, lid, _rid, c, s = l.rstrip('\n').split('\t')[:5]
        lid = int(lid)
        p = idf[lid]
        if p[0] != '動詞' or p[1] != '自立':
            continue
        m = H.search(s)
        ok = m.group(0) if m else ''
        k = s[:len(s) - len(ok)]
        if not k or not y.endswith(ok):
            continue
        ents.append((y, lid, int(c), s, (k, y[:len(y) - len(ok)], p[4])))
base = {}
for y, lid, c, s, lemma in ents:
    if idf[lid][5] == '基本形':
        base[lemma] = min(c, base.get(lemma, 1 << 30))
grp = collections.defaultdict(dict)
for y, lid, c, s, lemma in ents:
    if idf[lid][5] != '基本形' and lemma in base:
        old = grp[(y, lid)].get(s)
        if old is None or c < old[0]:
            grp[(y, lid)][s] = (c, base[lemma])
out = []
for (y, lid), es in grp.items():
    if len(es) < 2:
        continue
    wo = min(es, key=lambda s: es[s][0])
    wn = min(es, key=lambda s: es[s][1])
    if wo == wn:
        continue
    if es[wn][0] - es[wo][0] > D or es[wo][1] - es[wn][1] < G:
        continue
    out.append((y, lid, wn, es[wo][0] - es[wn][0] - 1))
out.sort()
dst = os.path.join(app, 'resources', 'converter', 'verb_inflection_cost.tsv')
with open(dst, 'w', encoding='utf-8') as o:
    for y, lid, s, d in out:
        o.write(f'{y}\t{lid}\t{s}\t{d}\n')
print(len(out), 'entries ->', dst)

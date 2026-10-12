#!/usr/bin/env python3
"""Chunked corpus prep (each call < 1200 s). usage: prep_chunk.py CORPUS I [CHUNK_BYTES=500000000] [WORK=/workspace/work/sumire_lm]
Piece I = bytes [I*CB, (I+1)*CB) of CORPUS, aligned to line starts. Steps per piece:
  decon (drop lines containing any JWTD/AJIMEE target clause, same rule as decon.py), NFKC,
  fugashi unidic-lite wakati -> WORK/lm/w{I}.npy (uint32 LOCAL ids, 0 = sentence start) + w{I}.voc (local vocab list)
  chars -> WORK/lm/c{I}.npy (uint32 code points, 0 = line start)
Merge local vocabs with merge_vocab.py."""
import sys, os, re, pickle, unicodedata, numpy as np, fugashi, ahocorasick
src, I = sys.argv[1], int(sys.argv[2])
CB = int(sys.argv[3]) if len(sys.argv) > 3 else 500_000_000
W = sys.argv[4] if len(sys.argv) > 4 else '/workspace/work/sumire_lm'
KAN = re.compile(r'[一-龠々]')
A = ahocorasick.Automaton(); pats = set()
for l in open(f'{W}/data/jwtd_kc.tsv'):
    f = l.rstrip('\n').split('\t'); pats.update(f[4].split('|')); pats.update(f[5].split('|'))
for l in open(f'{W}/data/ajimee.tsv'):
    for x in l.rstrip('\n').split('\t'):
        for p in x.split('|'):
            if len(p) >= 6 and KAN.search(p): pats.add(p)
for p in pats:
    if len(p) >= 6: A.add_word(p, 1)
A.make_automaton()
t = fugashi.Tagger('-Owakati')
f = open(src, 'rb'); start = I * CB
if start: f.seek(start - 1); f.readline()  # move to the first line starting at/after start
voc = {'<s>': 0}; wids = []; cids = []; wch = []; cch = []; drop = n = 0
while f.tell() < start + CB:
    raw = f.readline()
    if not raw: break
    l = raw.decode('utf-8', 'ignore').strip()
    if not l: continue
    n += 1
    if next(A.iter(l), None) is not None: drop += 1; continue
    l = unicodedata.normalize('NFKC', l)
    wids.append(0)
    for w in t.parse(l).split():
        j = voc.get(w)
        if j is None: j = voc[w] = len(voc)
        wids.append(j)
    cids.append(0); cids.extend(map(ord, l))
    if len(wids) > 5_000_000: wch.append(np.array(wids, np.uint32)); wids = []
    if len(cids) > 5_000_000: cch.append(np.array(cids, np.uint32)); cids = []
wch.append(np.array(wids, np.uint32)); cch.append(np.array(cids, np.uint32))
os.makedirs(f'{W}/lm', exist_ok=True)
w = np.concatenate(wch); c = np.concatenate(cch)
np.save(f'{W}/lm/w{I}.npy', w); np.save(f'{W}/lm/c{I}.npy', c)
inv = [None] * len(voc)
for k, v in voc.items(): inv[v] = k
pickle.dump(inv, open(f'{W}/lm/w{I}.voc', 'wb'))
print(I, 'lines', n, 'dropped', drop, 'words', len(w), 'chars', len(c), 'V', len(voc), flush=True)

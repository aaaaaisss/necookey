#!/usr/bin/env python3
"""pairsize.py : distinct unordered content-word pairs (heuristic content = has kanji/katakana or 2+ ASCII letters, not numeral)
co-occurring within 15 tokens of the same line with count >= t, on 1/16, 1/8, 1/4 of piece 0; power-law extrapolation to the full
corpus -> lm/pairsize.json (size estimate of the on-device PMI table)."""
import os, re, json, pickle, numpy as np
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm'); WIN = 15; TH = [1, 2, 3, 5, 10, 20, 50, 100, 200, 500]
voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb')); isc = np.zeros(len(voc) + 1, bool)
NUM = re.compile(r'^[0-9〇一二三四五六七八九十百千万億]+$'); CONT = re.compile(r'[一-龠々ァ-ヺー]')
for w, i in voc.items():
    if not NUM.match(w) and (CONT.search(w) or re.fullmatch(r'[A-Za-z]{2,}', w)): isc[i] = True
P = np.uint64(0x9E3779B97F4A7C15); seq = np.load(f'{W}/lm/wg0.npy', mmap_mode='r'); N0 = len(seq); FULL = 963425212
res = {}; FR = [16, 8, 4]
for fr in FR:
    y = np.asarray(seq[:N0 // fr]).astype(np.int64); line = np.cumsum(y == 0); c = isc[y]; hs = []
    for d in range(1, WIN + 1):
        m = c[:-d] & c[d:] & (line[:-d] == line[d:]); a = y[:-d][m]; b = y[d:][m]
        lo = np.minimum(a, b).astype(np.uint64); hi = np.maximum(a, b).astype(np.uint64); hs.append(lo * P + hi)
    h = np.concatenate(hs); del hs; _, cnt = np.unique(h, return_counts=True); del h
    cc = np.bincount(np.minimum(cnt, 10**6))[::-1].cumsum()[::-1]
    res[fr] = [int(cc[t]) if t < len(cc) else 0 for t in TH]; print(fr, res[fr], flush=True)
est = {}
for j, t in enumerate(TH):
    f = [res[fr][j] for fr in FR]
    if min(f) <= 0: est[t] = 0; continue
    beta = np.polyfit(np.log([N0 / fr for fr in FR]), np.log(f), 1)[0]; est[t] = int(f[-1] * (FULL / (N0 / FR[-1])) ** beta)
json.dump(dict(TH=TH, raw=res, full_est=est), open(f'{W}/lm/pairsize.json', 'w'), indent=1); print(est)

#!/usr/bin/env python3
"""segitems.py : analyzer-free tokenization variant ("seg") of items.pkl for the on-device check.
- candidate tokens = Sumire's own word split (conversionSegments outputs from the N-best dump), NFKC
- context tokens   = unigram-Viterbi segmentation of NFKC(context) over the LM vocabulary (words with corpus count >= 5;
                     cost -log p(w), unknown single char = -log(0.5/N) - 5); implementable on device with the unigram table
- 'pos' replaced by a heuristic: token containing kanji/katakana (or >= 2 chars of hiragana-free text) = content ('名詞-x'),
  else '助詞-x' (so common.content() keeps working); numerals (only digits/kanji numerals) -> '名詞-数詞'
-> items_seg.pkl, lm/seqs_seg.npz (chars unchanged), lm/need_ws.npz"""
import os, sys, re, glob, math, pickle, unicodedata, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__))); from common import ROWK
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm')
voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb')); uni = np.load(f'{W}/lm/uni_full.npy'); N = float(uni.sum())
cost = {w: -math.log(uni[i] / N) for w, i in voc.items() if i < len(uni) and uni[i] >= 5 and len(w) <= 12}
UNKC = -math.log(0.5 / N) + 5; MAXL = max(len(w) for w in cost)
def viterbi(s):
    n = len(s); best = [0.0] + [1e18] * n; bp = [0] * (n + 1)
    for i in range(n):
        if best[i] >= 1e18: continue
        for L in range(1, min(MAXL, n - i) + 1):
            w = s[i:i + L]; c = cost.get(w)
            if c is None:
                if L == 1: c = UNKC
                else: continue
            if best[i] + c < best[i + L]: best[i + L] = best[i] + c; bp[i + L] = i
    out = []; j = n
    while j > 0: out.append(s[bp[j]:j]); j = bp[j]
    return out[::-1]
KANA_KATA = re.compile(r'[一-龠々ァ-ヺー]'); NUM = re.compile(r'^[0-9〇一二三四五六七八九十百千万億]+$')
def tag(w):
    if NUM.match(w): return '名詞-数詞'
    return '名詞-x' if KANA_KATA.search(w) or re.fullmatch(r'[A-Za-z]{2,}', w) else '助詞-x'
meta = {}
for fn in ['ajimee', 'jwtd_kc', 'auto']:
    for l in open(f'{W}/data/{fn}.tsv'):
        f = l.rstrip('\n').split('\t'); meta[f[0]] = f[2]
segs = {}
for fn in sorted(glob.glob(f'{W}/cands/cands.*.tsv')):
    for l in open(fn):
        f = l.rstrip('\n').split('\t')
        if len(f) >= 8: segs[(f[0], int(f[1]))] = [x.split(':', 1)[1] for x in f[7].split('\x1f')] if f[7] else []
items = pickle.load(open(f'{W}/items.pkl', 'rb')); UNK = 0xFFFFFFFF
out = []; wx = []; wl = []; z = np.load(f'{W}/lm/seqs.npz'); nfallback = 0
for g, (i, sp, exp, ctx, rows) in enumerate(items):
    ct = viterbi(unicodedata.normalize('NFKC', ctx)) if ctx else []
    nr = []
    for t in rows:
        r = dict(zip(ROWK, t)); sg = segs.get((i, r['rank'])) or []
        cand = [unicodedata.normalize('NFKC', x) for x in sg if x]
        if ''.join(cand) != unicodedata.normalize('NFKC', r['s']):  # segments missing/inconsistent -> Viterbi
            cand = viterbi(unicodedata.normalize('NFKC', r['s'])); nfallback += 1
        wt = ct + cand
        r['wt'] = ' '.join(wt); r['pos'] = ' '.join(tag(w) for w in wt); r['nc'] = len(ct)
        nr.append(tuple(r[k] for k in ROWK))
        ids = ([0] if r['bos'] else []) + [voc.get(w, UNK) for w in wt]; wx.extend(ids); wl.append(len(ids))
    out.append((i, sp, exp, ctx, nr))
    if g % 5000 == 0: print(g, flush=True)
pickle.dump(out, open(f'{W}/items_seg.pkl', 'wb')); del out, items
wx = np.array(wx, np.uint32); wl = np.array(wl)
np.savez(f'{W}/lm/seqs_seg.npz', wx=wx, wl=wl, cx=z['cx'], cl=z['cl'])
P = np.uint64(0x9E3779B97F4A7C15)
st = np.repeat(np.cumsum(wl) - wl, wl); rem = np.repeat(wl, wl) - (np.arange(len(wx)) - st); del st
x = wx.astype(np.uint64); h = x.copy(); res = {}
for k in range(1, 5):
    if k > 1: h = h[:-1] * P + x[k - 1:]
    res[f'k{k}'] = np.unique(h[rem[:len(h)] >= k])
np.savez(f'{W}/lm/need_ws.npz', **res); print('done', len(wl), 'segment fallbacks', nfallback, [len(v) for v in res.values()])

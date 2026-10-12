#!/usr/bin/env python3
"""export_bench.py OUTDIR MODEL_JSON : export pruned/quantized tables (env SLM_PRUNE, same semantics as rr.py) restricted to the
n-grams the eval sentences touch, plus model weights and the held+eval sentences (seg tokenization) for the Kotlin latency bench
(LatencyBench.kt). Tables: sorted uint64 hashes (.h, int64 LE) + float32 values; sizes on device are estimated separately."""
import os, sys, json, pickle, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rr import prune_spec, apply_prune, quant8
from common import rowdict
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm'); OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
WK = os.environ.get('SLM_WKIND', 'ws'); PS = os.environ.get('SLM_PAIR', '_seg'); IS = os.environ.get('SLM_ITEMS', '_seg')
PR = prune_spec(); QU = PR.get('q', [0])[0] > 0
def dump(prefix, need, st, K, ctxk):
    for k in range(1, K + 1):
        c = np.asarray(st[f'c{k}'], np.float64); m = c > 0; h = need[f'k{k}'][m]
        h.astype('<u8').tofile(f'{OUT}/{prefix}{k}.h'); c[m].astype('<f4').tofile(f'{OUT}/{prefix}{k}.c')
        if k < K and ctxk and f'nf{k+1}' in st: np.asarray(st[f'nf{k+1}'])[m].astype('<f4').tofile(f'{OUT}/{prefix}{k}.nf')
        if k == 1 and 'nl1' in st: np.asarray(st['nl1'])[m].astype('<f4').tofile(f'{OUT}/{prefix}1.nl')
        print(prefix, k, int(m.sum()), flush=True)
sw = apply_prune(np.load(f'{W}/lm/stats_{WK}.npz'), PR.get('w'), QU); sc = apply_prune(np.load(f'{W}/lm/stats_c.npz'), PR.get('c'), QU)
dump('w', np.load(f'{W}/lm/need_{WK}.npz'), sw, 4, True); dump('c', np.load(f'{W}/lm/need_c.npz'), sc, 7, False)
pr = np.load(f'{W}/lm/need_pair{PS}.npz'); pc = np.load(f'{W}/lm/pair{PS}.npz')['c'].astype(np.float64)
if 'p' in PR: pc = np.where(pc >= PR['p'][0], pc, 0)
if QU: pc = quant8(pc)
m = pc > 0; pr['h'][m].astype('<u8').tofile(f'{OUT}/pair.h'); pc[m].astype('<f4').tofile(f'{OUT}/pair.c')
json.dump(dict(Nw=float(sw['n']), Nc=float(sc['n']), nlt=float(sw['nl_tot']), V=len(pickle.load(open(f'{W}/lm/voc.pkl', 'rb')))), open(f'{OUT}/meta.json', 'w'))
# vocab: word \t id \t viterbi-cost (or -1 when not in the Viterbi lexicon)
import math
voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb')); uni = np.load(f'{W}/lm/uni_full.npy'); N = float(uni.sum())
with open(f'{OUT}/vocab.tsv', 'w') as o:
    for w, i in voc.items():
        if '\t' in w or '\n' in w: continue
        cst = -math.log(uni[i] / N) if i < len(uni) and uni[i] >= 5 and len(w) <= 12 else -1
        o.write(f'{w}\t{i}\t{cst:.6f}\n')
model = json.load(open(sys.argv[2]))
with open(f'{OUT}/model.tsv', 'w') as o:
    for n, mu, sd, w in zip(model['names'], model['mu'], model['sd'], model['w']): o.write(f'{n}\t{mu!r}\t{sd!r}\t{w!r}\n')
items = pickle.load(open(f'{W}/items{IS}.pkl', 'rb'))
with open(f'{OUT}/sents.tsv', 'w') as o:
    for i, sp, exp, ctx, rows in items:
        if sp not in ('held', 'eval'): continue
        rs = [rowdict(t) for t in rows]
        o.write(f"#\t{i}\t{sp}\t{ctx}\t{1 if rs[0]['bos'] else 0}\t{len(rs)}\t{'|'.join(sorted(exp))}\n")
        for r in rs: o.write(f"{r['s']}\t{r['cost']}\t{r['rank']}\t{r['nseg']}\t{r['type']}\t{' '.join(r['wt'][r['nc']:])}\n")
print('exported')

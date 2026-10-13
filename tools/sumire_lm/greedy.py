#!/usr/bin/env python3
"""greedy.py KIND K VAR [q=4 fp=12] : greedy per-order count-cutoff path for one LM (w: word 4-gram, c: char K-gram), selected on
JWTD tune only. Start unpruned (word unigram cutoff fixed at 5 = Viterbi lexicon); each step raises ONE order's cutoff to the next
grid value (keeping cutoffs non-decreasing with order) and takes the move with the smallest tune loss per MB saved.
Tune metric = top1 of (LM score + lambda*cost + pen*ntok), lambda/pen grid-searched on tune; LM score = the K-gram ARPA log-prob,
renormalized pruned model (odsim.renorm), q-bit values, fp-bit fingerprints. Resumable: appends to od/greedy_{KIND}{K}_{VAR}.jsonl."""
import sys, os, json, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import odsim, rr
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm')
kind, K, VAR = sys.argv[1], int(sys.argv[2]), sys.argv[3]; kw = dict(a.split('=') for a in sys.argv[4:]); q = int(kw.get('q', 4)); fp = int(kw.get('fp', 12))
odsim.VAR = VAR; Z = np.load(f'{W}/od/prep_{VAR}.npz'); G = Z['G']; gs = rr.group_first(G)
T = dict(G=G, LAB=Z['LAB'], gsp=Z['SPL'][gs], ng=len(gs))
GRID = [1, 2, 3, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 4000]
def mb(th):
    e = odsim.entries('w' if kind == 'w' else 'c', th); kb = fp + 2
    return (sum(e) * (kb + q) + sum(e[:-1]) * q + (e[0] * q if kind == 'w' else 0)) / 8e6
def score(th):
    f, _ = odsim.lm_feats(Z, kind, th, K, q, fp); s = f[f'{kind}{K}']; b = 0
    for lam in [0, 0.1, 0.2, 0.3, 0.5, 1, 2, 3]:
        for pen in [-1, -0.5, 0, 0.5, 1, 2]:
            b = max(b, rr.acc_by_split(s - lam * Z['b_cost'] - pen * Z['b_ntok'], T)['tune'])
    return b
fn = f'{W}/od/greedy_{kind}{K}_{VAR}.jsonl'
path = [json.loads(l) for l in open(fn)] if os.path.exists(fn) else []
import warnings; warnings.filterwarnings('ignore')
if not path:
    th = [5 if kind == 'w' else 1] + [1] * (K - 1); r = dict(th=th, tune=score(th), mb=mb(th)); path.append(r)
    open(fn, 'a').write(json.dumps(r) + '\n'); print(r, flush=True)
while True:
    cur = path[-1]; th = cur['th']; best = None
    for k in range(1, K):
        if th[k] >= GRID[-1]: continue
        nt = th.copy(); nt[k] = GRID[GRID.index(th[k]) + 1]
        for j in range(k + 1, K): nt[j] = max(nt[j], nt[k])
        m = mb(nt); a = score(nt); eff = (cur['tune'] - a) / max(cur['mb'] - m, 1e-6)
        if best is None or eff < best[0]: best = (eff, nt, a, m)
    if best is None: break
    r = dict(th=best[1], tune=best[2], mb=best[3]); path.append(r); open(fn, 'a').write(json.dumps(r) + '\n'); print(r, flush=True)
    if r['mb'] < 0.3: break
print('DONE')

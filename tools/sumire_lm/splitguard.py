#!/usr/bin/env python3
"""splitguard.py [TAG] : 'split guard' post-rule on saved reranker scores. If the reranker's top1 has a split that crosses
Sumire rank-0's split, keep the best candidate whose split does not cross rank-0 unless the score margin exceeds theta.
theta chosen on JWTD tune only; held / AJIMEE(eval) reported. Also reports the reverse margin rule (allow split change only when ...)."""
import sys, os, glob, numpy as np
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm')
TAG = sys.argv[1] if len(sys.argv) > 1 else 'lin_base+lmw+lmc+pmi_tune'
z = np.load(f'{W}/ftab.npz', allow_pickle=True); G = z['G']; LAB = z['LAB'].astype(bool); SPL = z['SPL']
S = np.load(f'{W}/scores/{TAG}.npy').astype(float)
cuts = []
for fn in sorted(glob.glob(f'{W}/cands/cands.*.tsv')):
    for l in open(fn):
        f = l.rstrip('\n').split('\t'); p = 0; c = []
        for x in (f[7].split('\x1f') if len(f) >= 8 and f[7] else []):
            p += len(x.split(':', 1)[0]); c.append(p)
        cuts.append(c)
def cross(A, B):
    sa = list(zip([0] + A[:-1], A)); sb = list(zip([0] + B[:-1], B))
    return any(x0 < y0 < x1 < y1 or y0 < x0 < y1 < x1 for x0, x1 in sa for y0, y1 in sb)
starts = np.r_[0, np.nonzero(np.diff(G))[0] + 1, len(G)]
grp = []  # per evaluation group: (split, labels, scores, crossing flags vs rank0)
for a, b in zip(starts[:-1], starts[1:]):
    sp = SPL[a]
    if sp == 'auto': continue
    cr = np.array([cross(cuts[i], cuts[a]) for i in range(a, b)])
    grp.append((sp, LAB[a:b], S[a:b], cr))
def run(theta):
    acc = {}
    for sp in ('tune', 'held', 'eval'):
        ok = []; changed = 0
        for s_, lab, sc, cr in grp:
            if s_ != sp: continue
            t = int(np.argmax(sc))
            if cr[t]:
                safe = np.where(~cr, sc, -np.inf); j = int(np.argmax(safe))
                if sc[t] - sc[j] < theta: t = j; changed += 1
            ok.append(lab[t])
        acc[sp] = (np.mean(ok), changed)
    return acc
best = None
for th in [0, 0.25, 0.5, 1, 1.5, 2, 3, 4, 6]:
    a = run(th); print(f"theta {th:4}: tune {a['tune'][0]:.3f} (changed {a['tune'][1]})  held {a['held'][0]:.3f} ({a['held'][1]})  eval {a['eval'][0]:.3f} ({a['eval'][1]})")
    if best is None or a['tune'][0] > best[0] + 1e-9: best = (a['tune'][0], th)
print('chosen on tune: theta', best[1])

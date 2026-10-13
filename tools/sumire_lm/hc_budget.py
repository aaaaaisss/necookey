#!/usr/bin/env python3
"""hc_budget.py TAG B1,B2,... [QBITS] : global entry budget (keep the B entries with the largest |delta|*count over all pairs), tune grid, all splits.
QBITS>0 quantizes delta to that many bits (uniform, symmetric, clip at the tune-chosen... fixed clip 8). Size estimate printed."""
import sys, os, pickle, itertools, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hc_common import *
import hc_eval as E
from hc_run import grp, summary, fmt, restrict, G
tag = sys.argv[1]; budgets = [int(x) for x in sys.argv[2].split(',')]; QB = int(sys.argv[3]) if len(sys.argv) > 3 else 0
nb = pickle.load(open(f'{HC}/eval.pkl', 'rb')); prep = pickle.load(open(f'{HC}/prep_eval{os.environ.get("HC_PREP", "")}.pkl', 'rb'))
needed = {tuple(sorted(mp)) for b, ok, alts in prep.values() for a in alts for mp in a[2]}
z = np.load(f'{HC}/tabnp_{tag}.npz', allow_pickle=True); S = z['S']; Wd = z['W']; OFF = z['OFF']
pid = np.repeat(np.arange(len(OFF) - 1), np.diff(OFF))
wlen = dict(zip(z['WID'].tolist(), [len(s.encode()) for s in z['WSTR']]))
for B in budgets:
    thr = np.partition(S, len(S) - B)[len(S) - B] if B < len(S) else 0.0
    keep = S >= thr; np_pairs = len(np.unique(pid[keep])); uw = np.unique(Wd[keep])
    wbytes = sum(wlen[int(w)] + 1 for w in uw)
    mem = set(z['PA'][np.unique(pid[keep])].tolist()) | set(z['PB'][np.unique(pid[keep])].tolist())
    pbytes = np_pairs * (2 * 2 + 4) + sum(len(m.encode()) + 1 for m in mem)  # member ids 2x16 bit + offset 32 bit + member strings
    ebytes = keep.sum() * (2 + (QB or 8) / 8)  # 16-bit word id + QB-bit weight
    tab = E.load_np(tag, needed, thr)
    if QB:
        CL = 8.0; L = 2 ** (QB - 1) - 1
        tab = {k: (a, b, [(w, np.clip(round(d / CL * L), -L, L) * CL / L) for w, d in Ls]) for k, (a, b, Ls) in tab.items()}
    best = None
    for KALT in (5, 10):
        pr = restrict(prep, KALT); tune = {i: v for i, v in pr.items() if grp(i) in ('jwtd-tune', 'daily-tune')}
        for LAM, TH in itertools.product((0.5, 0.75, 1.0, 1.5, 2.0, 3.0), (0.0, 0.5, 1.0)):
            r = E.decide(tune, tab, LAM, TH=TH); sc = sum(r[i][0] for i in tune)
            if best is None or sc > best[0]: best = (sc, KALT, LAM, TH)
    sc, KALT, LAM, TH = best; pr = restrict(prep, KALT); res = E.decide(pr, tab, LAM, TH=TH)
    tot = ebytes + wbytes + pbytes
    print(f'{tag} budget {B} q{QB} entries {keep.sum()} pairs {np_pairs} words {len(uw)} size {tot/1e6:.2f}MB (entries {ebytes/1e6:.2f} words {wbytes/1e6:.2f} pairs {pbytes/1e6:.2f}) | KALT {KALT} LAM {LAM} TH {TH} tune {sc}')
    print(fmt(summary(nb, pr, res)), flush=True)
    pickle.dump((B, QB, KALT, LAM, TH, res), open(f'{HC}/res_{tag}_{B}_q{QB}{os.environ.get("HC_PREP", "")}.pkl', 'wb'))

#!/usr/bin/env python3
"""hc_run.py prep                     -> $HC/prep_eval.pkl (lattice-on N-best, base = Sumire top1, KALT=50)
   hc_run.py grid TAG [TAG...]       -> tune grid (LAM, KALT, prior) on jwtd-tune + daily-tune; report all splits for tune-best
   hc_run.py show TAG LAM KALT PRIOR -> per-split table, homophone errors before/after, fixes/breaks, examples"""
import sys, os, pickle, collections, itertools, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hc_common import *
import hc_eval as E
G = gold_table()
def grp(i): sp = G[i][0]; return 'ajimee' if i[0] == 'a' else ('daily-' if i[0] == 'd' else 'jwtd-') + sp
GROUPS = ['jwtd-tune', 'jwtd-held', 'daily-tune', 'daily-held', 'ajimee']

def homo_err(nb, i, pick):
    rows = nb[i]; gold = G[i][3]
    if rows[pick][1] in gold: return False
    gi = next((k for k, r in enumerate(rows) if r[1] in gold), None)
    if gi is None: return False
    return classify(rows[pick][3], rows[gi][3])[0] == 'homophone'

def restrict(prep, KALT):
    return {i: (b, ok, [a for a in alts if a[0] < KALT]) for i, (b, ok, alts) in prep.items()}

def summary(nb, prep, res):
    out = {}
    for g in GROUPS:
        ids = [i for i in prep if grp(i) == g]
        n = len(ids)
        if n == 0: continue
        ok0 = sum(prep[i][1] for i in ids); ok1 = sum(res[i][0] for i in ids)
        he0 = sum(homo_err(nb, i, prep[i][0]) for i in ids)
        he1 = sum(homo_err(nb, i, res[i][1] if res[i][1] is not None else prep[i][0]) for i in ids)
        fix = sum(res[i][0] and not prep[i][1] for i in ids); brk = sum(prep[i][1] and not res[i][0] for i in ids)
        sw = sum(res[i][1] is not None for i in ids)
        out[g] = dict(n=n, top1_0=ok0 / n, top1_1=ok1 / n, he0=he0, he1=he1, fix=fix, brk=brk, sw=sw)
    return out
def fmt(o):
    return '\n'.join(f"  {g:11s} top1 {v['top1_0']:.3f}->{v['top1_1']:.3f}  homErr {v['he0']}->{v['he1']}  fix {v['fix']} brk {v['brk']} switched {v['sw']}" for g, v in o.items())

if __name__ == '__main__':
    mode = sys.argv[1]
    if mode == 'prep':
        nb = pickle.load(open(f'{HC}/eval.pkl', 'rb'))
        if len(sys.argv) > 2:  # device-style lookup against the pairs of a numpy table
            z = np.load(f'{HC}/tabnp_{sys.argv[2]}.npz', allow_pickle=True); E.DEV = {p for p in zip(z['PA'].tolist(), z['PB'].tolist()) if min(map(len, p)) >= int(os.environ.get('HC_MINLEN', 1))}
            prep = E.prepare(nb, G, 50); pickle.dump(prep, open(f'{HC}/prep_eval_dev{os.environ.get("HC_DEVORDER", "")}{os.environ.get("HC_MINLEN", "")}.pkl', 'wb'))
        else:
            prep = E.prepare(nb, G, 50); pickle.dump(prep, open(f'{HC}/prep_eval.pkl', 'wb'))
        na = collections.Counter(grp(i) for i, v in prep.items() if v[2])
        print('sentences with >=1 mapped homophone alt:', dict(na))
        sys.exit()
    nb = pickle.load(open(f'{HC}/eval.pkl', 'rb')); prep = pickle.load(open(f'{HC}/prep_eval{os.environ.get("HC_PREP", "")}.pkl', 'rb'))
    if mode == 'grid':
        for tag in sys.argv[2:]:
            tab = pickle.load(open(f'{HC}/tab_{tag}.pkl', 'rb')); best = None
            for KALT in (5, 10, 20, 50):
                pr = restrict(prep, KALT)
                tune = {i: v for i, v in pr.items() if grp(i) in ('jwtd-tune', 'daily-tune')}
                for LAM, prior, TH in itertools.product((0.3, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0), (0.0,), (0.0, 0.5, 1.0, 2.0)):
                    r = E.decide(tune, tab, LAM, TH=TH, prior=prior)
                    sc = sum(r[i][0] for i in tune)
                    if best is None or sc > best[0]: best = (sc, KALT, LAM, prior, TH)
            sc, KALT, LAM, prior, TH = best
            pr = restrict(prep, KALT); res = E.decide(pr, tab, LAM, TH=TH, prior=prior)
            print(tag, 'tune-best KALT', KALT, 'LAM', LAM, 'prior', prior, 'TH', TH, 'tune correct', sc); print(fmt(summary(nb, pr, res)), flush=True)
    elif mode == 'show':
        tag, LAM, KALT, TH = sys.argv[2], float(sys.argv[3]), int(sys.argv[4]), float(sys.argv[5])
        tab = pickle.load(open(f'{HC}/tab_{tag}.pkl', 'rb')); pr = restrict(prep, KALT); res = E.decide(pr, tab, LAM, TH=TH)
        print(fmt(summary(nb, pr, res)))
        for i in list(pr)[:4000]:
            if res[i][1] is not None and grp(i).endswith('tune'):
                print(i, 'FIX' if res[i][0] and not pr[i][1] else 'BRK' if pr[i][1] and not res[i][0] else '==', nb[i][0][1], '->', nb[i][res[i][1]][1], '%.2f' % res[i][2])

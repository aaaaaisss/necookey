#!/usr/bin/env python3
"""hc_prep.py : load eval N-best (lattice cost on) -> $HC/eval.pkl and baseline homophone stats.
JWTD/AJIMEE: lcx/ci/cands/cands.*.tsv ; daily: lcx/h1/cands.0.tsv (d*). Old lattice-off N-best + auto: cands/cands.*.tsv -> $HC/old.pkl"""
import sys, os, glob, pickle
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hc_common import *
os.makedirs(HC, exist_ok=True)
G = gold_table()
ev = load_nbest(sorted(glob.glob(f'{W}/lcx/ci/cands/cands.*.tsv')), ('j', 'a'))
ev.update(load_nbest([f'{W}/lcx/h1/cands.0.tsv'], ('d',)))
pickle.dump(ev, open(f'{HC}/eval.pkl', 'wb'))
old = load_nbest(sorted(glob.glob(f'{W}/cands/cands.*.tsv')), maxrank=50, raw=True)
pickle.dump(old, open(f'{HC}/old.pkl', 'wb'))
print('eval', len(ev), 'old', len(old))

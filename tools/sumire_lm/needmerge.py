#!/usr/bin/env python3
"""needmerge.py SHARD... : union lm/need_{w,c}_{SHARD}.npz -> lm/need_w.npz, lm/need_c.npz"""
import sys, os, numpy as np
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm')
for kind in 'wc':
    zs = [np.load(f'{W}/lm/need_{kind}_{s}.npz') for s in sys.argv[1:]]
    out = {f: np.unique(np.concatenate([z[f] for z in zs])) for f in zs[0].files}
    np.savez(f'{W}/lm/need_{kind}.npz', **out); print(kind, [len(v) for v in out.values()])

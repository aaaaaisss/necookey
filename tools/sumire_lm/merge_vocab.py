#!/usr/bin/env python3
"""merge_vocab.py N [WORK] : union local vocabs w0..w{N-1}.voc (prep_chunk.py) -> WORK/lm/voc.pkl (dict word->global id, <s>=0),
and rewrite each piece as WORK/lm/wg{I}.npy (global ids). Ids are assigned in first-seen order over pieces 0..N-1,
so the vocab of the first k pieces is a prefix (reproduction runs on pieces 0-2 = first 1.5 GB)."""
import sys, os, pickle, numpy as np
N = int(sys.argv[1]); W = sys.argv[2] if len(sys.argv) > 2 else '/workspace/work/sumire_lm'
voc = {'<s>': 0}
for i in range(N):
    inv = pickle.load(open(f'{W}/lm/w{i}.voc', 'rb'))
    m = np.empty(len(inv), np.uint32)
    for j, w in enumerate(inv):
        g = voc.get(w)
        if g is None: g = voc[w] = len(voc)
        m[j] = g
    out = f'{W}/lm/wg{i}.npy'
    if not os.path.exists(out):
        np.save(out, m[np.load(f'{W}/lm/w{i}.npy')])
    print(i, 'V so far', len(voc), flush=True)
pickle.dump(voc, open(f'{W}/lm/voc.pkl', 'wb'))

#!/usr/bin/env python3
"""merge_vocab_ext.py SUBDIR N : extend WORK/lm/voc.pkl with the local vocabs WORK/SUBDIR/lm/w{0..N-1}.voc (prep_chunk.py on another
corpus slice) and write WORK/SUBDIR/lm/wg{I}.npy in the same global id space. Existing ids are unchanged."""
import sys, os, pickle, numpy as np
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm'); sub, N = sys.argv[1], int(sys.argv[2])
voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb'))
for i in range(N):
    inv = pickle.load(open(f'{W}/{sub}/lm/w{i}.voc', 'rb')); m = np.empty(len(inv), np.uint32)
    for j, w in enumerate(inv):
        g = voc.get(w)
        if g is None: g = voc[w] = len(voc)
        m[j] = g
    out = f'{W}/{sub}/lm/wg{i}.npy'
    if not os.path.exists(out): np.save(out, m[np.load(f'{W}/{sub}/lm/w{i}.npy')])
    print(i, 'V', len(voc), flush=True)
pickle.dump(voc, open(f'{W}/lm/voc.pkl', 'wb'))

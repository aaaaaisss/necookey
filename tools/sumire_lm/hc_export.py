#!/usr/bin/env python3
"""hc_export.py TAG BUDGET OUT.hcx [LAM TH]  : write the compact homophone-context table (HCX1) and verify it by re-reading.
HCX1 layout (little endian):
  magic 'HCX1' | u32 version=1 | u32 nWords | u32 nMembers | u32 nPairs | u32 nEntries | f32 lam | f32 th | f32 clip | u32 qbits(4)
  words  : u32 off[nWords+1] + UTF-8 bytes   (context words, sorted)
  members: u32 off[nMembers+1] + UTF-8 bytes (pair members, sorted)
  pairs  : nPairs x (u16 memberA, u16 memberB, u32 firstEntry)  sorted by (A,B), A<B by string order; entries of pair j = [first_j, first_{j+1})
           (a final sentinel u32 = nEntries follows the pair array)
  entries: u16 wordId[nEntries] + u8 packed 4-bit weights [(nEntries+1)/2] (low nibble first; signed -7..7, weight = q*clip/7, + = evidence for A)
vote(alt vs base | ctx) = sum over entries of the pair with ctx.contains(word) of weight (sign flipped when alt is B)."""
import sys, os, struct, hashlib, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from hc_common import *
tag, B, out = sys.argv[1], int(sys.argv[2]), sys.argv[3]
LAM = float(sys.argv[4]) if len(sys.argv) > 4 else 1.0; TH = float(sys.argv[5]) if len(sys.argv) > 5 else 0.5
CLIP, QB = 8.0, 4; L = 2 ** (QB - 1) - 1
z = np.load(f'{HC}/tabnp_{tag}.npz', allow_pickle=True); S = z['S']; OFF = z['OFF']
thr = np.partition(S, len(S) - B)[len(S) - B] if B < len(S) else 0.0
ws = dict(zip(z['WID'].tolist(), z['WSTR'].tolist())); ZW, ZD = z['W'], z['D']
pairs = []
for j, (a, b) in enumerate(zip(z['PA'], z['PB'])):
    lo, hi = OFF[j], OFF[j + 1]; m = S[lo:hi] >= thr
    if not m.any(): continue
    ent = [(ws[int(w)], int(np.clip(round(float(d) / CLIP * L), -L, L))) for w, d in zip(ZW[lo:hi][m].tolist(), ZD[lo:hi][m].tolist())]
    ent = [(w, q) for w, q in ent if q != 0]
    if ent: pairs.append((a, b, ent))
words = sorted({w for _, _, e in pairs for w, _ in e}); wi = {w: i for i, w in enumerate(words)}
mems = sorted({m for a, b, _ in pairs for m in (a, b)}); mi = {m: i for i, m in enumerate(mems)}
assert len(words) < 65536 and len(mems) < 65536
pairs.sort(key=lambda p: (mi[p[0]], mi[p[1]]))
def pool(ss):
    bs = [s.encode() for s in ss]; off = np.r_[0, np.cumsum([len(b) for b in bs])].astype('<u4'); return off.tobytes() + b''.join(bs)
wid, q, first = [], [], []
for a, b, e in pairs:
    first.append(len(wid))
    for w, x in e: wid.append(wi[w]); q.append(x)
nE = len(wid); qa = (np.array(q) & 0xF).astype(np.uint8)
if nE % 2: qa = np.r_[qa, 0].astype(np.uint8)
packed = (qa[0::2] | (qa[1::2] << 4)).astype(np.uint8)
buf = b'HCX1' + struct.pack('<5I3fI', 1, len(words), len(mems), len(pairs), nE, LAM, TH, CLIP, QB)
buf += pool(words) + pool(mems)
buf += b''.join(struct.pack('<HHI', mi[a], mi[b], f) for (a, b, _), f in zip(pairs, first)) + struct.pack('<I', nE)
buf += np.array(wid, '<u2').tobytes() + packed.tobytes()
open(out, 'wb').write(buf)
print('wrote', out, len(buf), 'bytes', 'words', len(words), 'members', len(mems), 'pairs', len(pairs), 'entries', nE, 'sha256', hashlib.sha256(buf).hexdigest())

# ---- re-read and compare with the in-memory quantized table
def read(fn):
    d = open(fn, 'rb').read(); assert d[:4] == b'HCX1'
    ver, nW, nM, nP, nE, lam, th, clip, qb = struct.unpack_from('<5I3fI', d, 4); p = 4 + struct.calcsize('<5I3fI')
    def rpool(p, n):
        off = np.frombuffer(d, '<u4', n + 1, p); p += 4 * (n + 1); base = p
        return [d[base + off[i]: base + off[i + 1]].decode() for i in range(n)], base + int(off[-1])
    W_, p = rpool(p, nW); M_, p = rpool(p, nM)
    pr = [struct.unpack_from('<HHI', d, p + 8 * j) for j in range(nP)]; p += 8 * nP; sent = struct.unpack_from('<I', d, p)[0]; p += 4
    wid = np.frombuffer(d, '<u2', nE, p); p += 2 * nE; pk = np.frombuffer(d, np.uint8, (nE + 1) // 2, p)
    nib = np.empty(len(pk) * 2, np.int8); nib[0::2] = pk & 0xF; nib[1::2] = pk >> 4; nib = np.where(nib > 7, nib - 16, nib)[:nE]
    fs = [x[2] for x in pr] + [sent]; tab = {}
    for j, (a, b, f) in enumerate(pr):
        tab[(M_[a], M_[b])] = [(W_[wid[k]], nib[k] * clip / L) for k in range(fs[j], fs[j + 1])]
    return tab, lam, th
tab2, lam, th = read(out)
ref = {(a, b): [(w, x * CLIP / L) for w, x in e] for a, b, e in pairs}
assert tab2.keys() == ref.keys() and all(tab2[k] == ref[k] for k in ref), 'round trip mismatch'
print('round trip ok; lam', lam, 'th', th)

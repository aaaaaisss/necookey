#!/usr/bin/env python3
"""export_od.py OUTDIR VARIANT key=value ... (same keys as odsim.py; q>0 and fp>0 required)
Runs odsim.py with the config (retrains the linear model on JWTD tune), then writes the on-device tables for the Kotlin bench
(bench/OdBench.kt) in their real layout and real size:
  bucketed fingerprint hash tables, load 8 keys/bucket: bucket = hi32(mix(h)) * B >> 32, fingerprint = low fp bits of mix(h);
  directory = int32 base per 256 buckets + uint16 offset per bucket; per entry: uint16 fingerprint (fp bits used),
  uint8 codes (lp, bow for orders < K, log-count for word unigrams / pair log1p-count) + float32 codebooks.
  Entries = the stored n-grams the eval sentences need (true keys/values) + random filler entries up to the full-corpus entry count
  of the config, so table size, cache behaviour and false hits are those of the real table.
Also measures the empirical false-hit rate of absent keys (held+eval lookups) for fingerprint widths 12/14/16 in this layout.
-> OUTDIR/{w1..w4,c1..cK,p}.{dir,rel,fp,lp,bw,cnt} , meta.json, model.tsv, vocab.tsv, sents.tsv, py_picks.tsv"""
import os, sys, json, math, pickle, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import odsim
from common import rowdict
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm'); OUT = sys.argv[1]; VAR = sys.argv[2]; os.makedirs(OUT, exist_ok=True)
cfg = dict(var=VAR); cfg.update(dict(a.split('=', 1) for a in sys.argv[3:])); cfg.setdefault('tag', 'export_' + os.path.basename(OUT))
q = int(cfg['q']); FP = int(cfg['fp']); assert q > 0 and FP > 0 and q <= 8 and FP <= 16
odsim.EXPORT = {}; res = odsim.run(cfg); E = odsim.EXPORT; sz = res['size']
SUF = '' if VAR == 'fug' else '_seg'; WK = 'w' if VAR == 'fug' else 'ws'
M64 = (1 << 64) - 1
def splitmix(h):
    x = h.astype(np.uint64) + np.uint64(0x9E3779B97F4A7C15)
    x = (x ^ (x >> np.uint64(30))) * np.uint64(0xBF58476D1CE4E5B9); x = (x ^ (x >> np.uint64(27))) * np.uint64(0x94D049BB133111EB)
    return x ^ (x >> np.uint64(31))
def bucket_fp(h, B, fpb):
    m = splitmix(h); b = ((m >> np.uint64(32)) * np.uint64(B)) >> np.uint64(32); return b.astype(np.int64), (m & np.uint64((1 << fpb) - 1)).astype(np.int64)
rng = np.random.default_rng(0); meta = dict(q=q, fp=FP, cfg=cfg, tables={}, size=sz, fh_emp={})
def build(name, h, codes, n_full):
    """codes: dict field -> uint8 array aligned with h"""
    n_st = len(h); n = max(n_full, n_st); B = max(1, -(-n // 8)); b, f = bucket_fp(h, B, FP)
    nf = n - n_st
    if nf:
        b = np.r_[b, rng.integers(0, B, nf)]; f = np.r_[f, rng.integers(0, 1 << FP, nf)]
        codes = {k: np.r_[v, v[rng.integers(0, max(n_st, 1), nf)] if n_st else rng.integers(0, 1 << q, nf)].astype(np.uint8) for k, v in codes.items()}
    o = np.argsort(b, kind='stable'); b = b[o]; f = f[o]; codes = {k: v[o] for k, v in codes.items()}
    cnt = np.bincount(b, None, B).astype(np.int64); start = np.r_[0, np.cumsum(cnt)][:-1]
    nb = -(-B // 256); base = start[::256].astype(np.int32); rel = (start - np.repeat(base.astype(np.int64), 256)[:B]).astype(np.uint16)
    assert (start - np.repeat(base.astype(np.int64), 256)[:B]).max() < 65536
    base.astype('<i4').tofile(f'{OUT}/{name}.dir'); rel.astype('<u2').tofile(f'{OUT}/{name}.rel'); f.astype('<u2').tofile(f'{OUT}/{name}.fp')
    for k, v in codes.items(): v.astype(np.uint8).tofile(f'{OUT}/{name}.{k}')
    meta['tables'][name] = dict(B=int(B), n=int(n), stored=int(n_st), filler=int(nf))
    return b, f, B
def fh_rate(name, h_abs, h_st, n_full):
    """empirical false-hit rate of absent keys h_abs against a table of n_full keys (stored keys + random filler), per width"""
    out = {}
    for fpb in (12, 14, 16):
        n = max(n_full, len(h_st)); B = max(1, -(-n // 8)); b, f = bucket_fp(h_st, B, fpb)
        fb = rng.integers(0, B, n - len(h_st)); ff = rng.integers(0, 1 << fpb, n - len(h_st))
        keys = np.unique(np.r_[b * (1 << 16) + f, fb * (1 << 16) + ff])
        qb, qf = bucket_fp(h_abs, B, fpb); out[fpb] = float(np.isin(qb * (1 << 16) + qf, keys).mean()) if len(h_abs) else 0.0
    meta['fh_emp'][name] = out
cb = {}
Z = np.load(f'{W}/od/prep_{VAR}.npz'); held_eval_rows = np.isin(Z['SPL'], ['held', 'eval'])
for kind, K, nd_name, ne in (('w', 4, f'need_{WK}', sz['n_w']), ('c', len(sz['n_c']), 'need_c', sz['n_c'])):
    nd = np.load(f'{W}/lm/{nd_name}.npz')
    rows_of_pos = np.repeat(np.arange(len(Z[f'{kind}L'])), Z[f'{kind}L']); he = held_eval_rows[rows_of_pos]
    for k in range(1, K + 1):
        e = E[(kind, k)]; h = nd[f'k{k}'][e['idx']]; codes = {'lp': e['qlp'].code(e['lp']).astype(np.uint8)}
        cb[f'{kind}{k}.lp'] = e['qlp'].cent.tolist()
        if e['bw'] is not None: codes['bw'] = e['qbw'].code(e['bw']).astype(np.uint8); cb[f'{kind}{k}.bw'] = e['qbw'].cent.tolist()
        if kind == 'w' and k == 1:
            c1 = np.load(f'{W}/lm/stats_{WK}.npz')['c1'][e['idx']]; codes['cnt'] = E['p']['qu'].code(np.log(np.maximum(c1, 1))).astype(np.uint8)
        build(f'{kind}{k}', h, codes, ne[k - 1])
        idx = Z[f'{kind}idx{k}'].astype(np.int64); ab = he & (idx >= 0) & ~np.isin(idx, e['idx'])
        fh_rate(f'{kind}{k}', np.unique(nd[f'k{k}'][idx[ab]]), h, ne[k - 1])
        print('table', kind, k, meta['tables'][f'{kind}{k}'], meta['fh_emp'][f'{kind}{k}'], flush=True)
cb['wcnt'] = E['p']['qu'].cent.tolist()
pr = np.load(f'{W}/lm/need_pair{SUF}.npz')['h']; e = E['p']; hp = pr[e['pidx']]
build('p', hp, {'cnt': e['ql'].code(e['lc']).astype(np.uint8)}, sz['n_p']); cb['p.cnt'] = e['ql'].cent.tolist()
pid = Z['pidx']; ab = held_eval_rows[Z['prow']] & ~np.isin(pid, e['pidx']); fh_rate('p', np.unique(pr[pid[ab]]), hp, sz['n_p'])
print('table p', meta['tables']['p'], meta['fh_emp']['p'], flush=True)
meta['codebooks'] = cb
st = np.load(f'{W}/lm/stats_{WK}.npz'); voc = pickle.load(open(f'{W}/lm/voc.pkl', 'rb'))
meta.update(Nw=float(st['n']), Nc=float(np.load(f'{W}/lm/stats_c.npz')['n']), unk_w=float(Z['wunk']), unk_c=float(Z['cunk']),
            Kc=len(sz['n_c']), res=res)
json.dump(meta, open(f'{OUT}/meta.json', 'w'), indent=1)
with open(f'{OUT}/meta.tsv', 'w') as o:
    for k in ('q', 'fp', 'Kc', 'Nw', 'Nc', 'unk_w', 'unk_c'): o.write(f'{k}\t{meta[k]!r}\n')
with open(f'{OUT}/cb.tsv', 'w') as o:
    for k, v in cb.items(): o.write(k + '\t' + ','.join(repr(float(x)) for x in v) + '\n')
uni = np.load(f'{W}/lm/uni_full.npy'); N = float(uni.sum())
with open(f'{OUT}/vocab.tsv', 'w') as o:
    for w, i in voc.items():
        if '\t' in w or '\n' in w: continue
        cst = -math.log(uni[i] / N) if i < len(uni) and uni[i] >= 5 and len(w) <= 12 else -1
        o.write(f'{w}\t{i}\t{cst:.6f}\n')
model = json.load(open(f'{W}/od/models/{cfg["tag"]}.json'))
with open(f'{OUT}/model.tsv', 'w') as o:
    for n, mu, sd, w in zip(model['names'], model['mu'], model['sd'], model['w']): o.write(f'{n}\t{mu!r}\t{sd!r}\t{w!r}\n')
items = pickle.load(open(f'{W}/items{SUF}.pkl', 'rb')); s = np.load(f'{W}/od/scores/{cfg["tag"]}.npy'); G = Z['G']
gstart = np.r_[0, np.nonzero(np.diff(G))[0] + 1]; gi = 0
with open(f'{OUT}/sents.tsv', 'w') as o, open(f'{OUT}/py_picks.tsv', 'w') as pp:
    for i, sp, exp, ctx, rows in items:
        if sp == 'auto': continue
        a = gstart[gi]; gi += 1
        if sp not in ('held', 'eval'): continue
        rs = [rowdict(t) for t in rows]; pp.write(f'{i}\t{int(np.argmax(s[a:a + len(rs)]))}\n')
        ctoks = ' '.join(rs[0]['wt'][:rs[0]['nc']])
        o.write(f"#\t{i}\t{sp}\t{ctx}\t{1 if rs[0]['bos'] else 0}\t{len(rs)}\t{'|'.join(sorted(exp))}\t{ctoks}\n")
        for r in rs: o.write(f"{r['s']}\t{r['cost']}\t{r['rank']}\t{r['nseg']}\t{r['type']}\t{' '.join(r['wt'][r['nc']:])}\n")
print('exported', OUT, json.dumps(meta['fh_emp']))

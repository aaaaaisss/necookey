#!/usr/bin/env python3
"""lcx.py DIR [DIR2] : evaluate a lattice-cost dump (NK_LATTICE_COST, 案2). DIR holds the dump artifacts: cands.*.tsv (with table),
cands.*.tsv.base (without table), cands.*.tsv.time (id, reading length, ms without, ms with).
Per variant (base / lc): Sumire top1 (exact match with any '|' alternative) and oracle@50 per split (tune / held / eval=AJIMEE /
probe), はいる->入る errors (reading contains はい, top1 contains 入, no expected surface contains 入), and boundary misses via
bunseg.py / bunsum.py (run on a per-variant work dir DIR/v_<variant> with a minimal ftab.npz and score = -rank, so 'rr' = Sumire).
Speed from .time. Only JWTD tune may be used for decisions; held / AJIMEE are reported only."""
import sys, os, glob, json, subprocess, collections, numpy as np
D = os.path.abspath(sys.argv[1]); W0 = os.environ.get('SLM_WORK0', '/workspace/work/sumire_lm'); T = os.path.dirname(os.path.abspath(__file__))
meta = {}
for fn in [f'{W0}/data/jwtd_kc.tsv', f'{W0}/data/ajimee.tsv', f'{T}/ci/probe_hairu.tsv']:
    for l in open(fn, encoding='utf-8'):
        f = l.rstrip('\n').split('\t'); meta[f[0]] = dict(sp=f[1], rd=f[3], exp=f[4].split('|'))
# daily.tsv（homophone-ui 側の日常文 3000、split tune/held → dtune/dheld）。あれば読む。
DAILY = os.environ.get('LCX_DAILY', f'{W0}/daily.tsv')
if os.path.exists(DAILY):
    for l in open(DAILY, encoding='utf-8'):
        f = l.rstrip('\n').split('\t')
        if len(f) >= 5 and f[3]: meta[f[0]] = dict(sp='d' + f[1], rd=f[3], exp=f[4].split('|'))
res = {}; tops = {}
for var, pat in (('base', 'cands.*.tsv.base'), ('lc', 'cands.*.tsv')):
    files = sorted(glob.glob(f'{D}/{pat}'))
    V = f'{D}/v_{var}'; os.makedirs(f'{V}/cands', exist_ok=True); os.makedirs(f'{V}/scores', exist_ok=True); os.makedirs(f'{V}/diag', exist_ok=True)
    if not os.path.exists(f'{V}/data'): os.symlink(f'{W0}/data', f'{V}/data')
    rows = collections.defaultdict(list); G = []; LAB = []; SPL = []; RK = []; gid = {}
    with open(f'{V}/cands/cands.0.tsv', 'w', encoding='utf-8') as out:
        for fn in files:
            for l in open(fn, encoding='utf-8'):
                f = l.rstrip('\n').split('\t')
                if len(f) < 8 or f[0] not in meta: continue
                out.write(l); m = meta[f[0]]
                G.append(gid.setdefault(f[0], len(gid))); LAB.append(f[2] in m['exp']); SPL.append(m['sp']); RK.append(int(f[1]))
                rows[f[0]].append((int(f[1]), f[2]))
    np.savez(f'{V}/ftab.npz', G=np.array(G), LAB=np.array(LAB), SPL=np.array(SPL))
    np.save(f'{V}/scores/sumire.npy', -np.array(RK, float))
    acc = collections.defaultdict(lambda: [0, 0, 0]); hai = collections.defaultdict(list); top = {}
    for sid, rs in rows.items():
        rs.sort(); m = meta[sid]; t1 = rs[0][1]; top[sid] = t1
        a = acc[m['sp']]; a[0] += 1; a[1] += t1 in m['exp']; a[2] += any(s in m['exp'] for _, s in rs[:50])
        if 'はい' in m['rd'] and '入' in t1 and not any('入' in e for e in m['exp']): hai[m['sp']].append(sid)
    tops[var] = top
    res[var] = dict(acc={k: (v[0], v[1] / v[0], v[2] / v[0]) for k, v in acc.items()}, hai={k: len(v) for k, v in hai.items()}, hai_ids=dict(hai))
    if not os.environ.get('LCX_NOBUN') and any(meta[s]['sp'] in ('tune', 'held', 'eval') for s in rows):
        env = dict(os.environ, SLM_WORK=V)
        b = subprocess.run([sys.executable, f'{T}/bunseg.py', 'sumire'], env=env, capture_output=True, text=True)
        s = subprocess.run([sys.executable, f'{T}/bunsum.py', 'sumire'], env=env, capture_output=True, text=True)
        open(f'{V}/bunsum.txt', 'w').write(b.stdout + b.stderr + '\n' + s.stdout + s.stderr)
        R = [json.loads(l) for l in open(f'{V}/diag/bunseg_sumire.jsonl')] if os.path.exists(f'{V}/diag/bunseg_sumire.jsonl') else []
        A = collections.Counter(r.get('sp') for r in R if r.get('sys') == 'sumire' and (r['cls'] == 'a1' or r.get('det') == 'nested-func'))
        res[var]['boundary'] = dict(A)
for var in res:
    print(var, json.dumps({k: v for k, v in res[var].items() if k != 'hai_ids'}, ensure_ascii=False))
tm = []
for fn in glob.glob(f'{D}/cands.*.tsv.time'):
    for l in open(fn):
        f = l.split('\t')
        if len(f) >= 4: tm.append((float(f[2]), float(f[3])))
if tm:
    a = np.array(tm)
    for j, name in ((0, 'no table'), (1, 'with table')):
        x = a[:, j]; print(f'{name}: mean {x.mean():.2f} ms, median {np.median(x):.2f}, p90 {np.percentile(x, 90):.2f}, p99 {np.percentile(x, 99):.2f} (n={len(x)})')
# はいる -> 入る examples
for sp in ('probe', 'tune', 'held', 'eval', 'dtune', 'dheld'):
    b = set(res['base']['hai_ids'].get(sp, [])); l = set(res['lc']['hai_ids'].get(sp, []))
    print(f'はいる->入る {sp}: base {len(b)} -> lc {len(l)} (fixed {len(b - l)}, new {len(l - b)})')
    for sid in sorted(b | l)[:8]:
        print(f'   {sid} {meta[sid]["rd"][:30]} | base {tops["base"].get(sid, "")[:30]} | lc {tops["lc"].get(sid, "")[:30]} | gold {meta[sid]["exp"][0][:30]}')
print('\nprobe top1 (base -> lc):')
for sid in sorted(s for s in meta if meta[s]['sp'] == 'probe'):
    if sid in tops['base']: print(f'   {sid} {meta[sid]["rd"]}: {tops["base"][sid]} -> {tops["lc"].get(sid)}  (gold {meta[sid]["exp"][0]})')

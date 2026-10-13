#!/usr/bin/env python3
"""bunsum.py [TAG] : summary tables of diag/bunseg_<TAG>.jsonl (written by bunseg.py). True boundary errors = a1 (crossing)
+ a2 nested-func (particle/okurigana absorbed or split off); a2 nested-compound (same span, compound split differs, e.g.
大治|郎 vs 大二郎) is a homophone-type error at compound granularity and is reported under c."""
import sys, os, json, collections, random
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm')
TAG = sys.argv[1] if len(sys.argv) > 1 else 'lin_base+lmw+lmc+pmi_tune'
R = [json.loads(l) for l in open(f'{W}/diag/bunseg_{TAG}.jsonl')]
N = {'tune': 1500, 'held': 1500, 'eval': 200}
def cls(r):
    if r['cls'] == 'a1' or r['det'] == 'nested-func': return 'A boundary'
    if r['cls'] == 'a2': return 'C homophone (compound split)'
    return {'b': 'B variant', 'c': 'C homophone', 'd': 'D other'}[r['cls']]
for sp in ('tune', 'held', 'eval'):
    for sy in ('rr', 'sumire'):
        X = [r for r in R if r['sp'] == sp and r['sys'] == sy]
        c = collections.Counter((cls(r), r['in50']) for r in X)
        print(f'{sp} {sy}: errors {len(X)}/{N[sp]}')
        for k in ('A boundary', 'B variant', 'C homophone', 'C homophone (compound split)', 'D other'):
            i, o = c[(k, True)], c[(k, False)]
            print(f'   {k:30s} {i + o:4d} ({(i + o) / max(1, len(X)):5.1%} of errors, {(i + o) / N[sp]:5.1%} of sents)  in50 {i:4d} / out50 {o:4d}')
def bucket(x): return 'absent' if x < 0 else ('rank0' if x == 0 else 'rank1-4' if x < 5 else 'rank5-19' if x < 20 else 'rank20-49')
for sy in ('rr', 'sumire'):
    A = [r for r in R if r['sp'] == 'tune' and r['sys'] == sy and cls(r) == 'A boundary']
    print(f'\n== tune {sy}: boundary errors {len(A)} (gold surface in top-50: {sum(r["in50"] for r in A)})')
    print('  best rank of a top-50 candidate with the gold split:', sorted(collections.Counter(bucket(r['goldsplit_rank']) for r in A).items()))
    print('  distinct splits in top-50 (median):', sorted(r['nsplits'] for r in A)[len(A) // 2])
    print('  direction: top over-splits only', sum(r['over'] > 0 and r['under'] == 0 for r in A), '| under-splits only',
          sum(r['under'] > 0 and r['over'] == 0 for r in A), '| both (crossing)', sum(r['over'] > 0 and r['under'] > 0 for r in A))
    print('  gold chunk has proper noun:', sum('固有名詞' in r['chunk_pos'] for r in A), '| gold noun never output by Sumire in the whole dump:', sum(bool(r['unseen']) for r in A))
    pc = collections.Counter(r['pat'] for r in A)
    for p, n in sorted(pc.items()):
        sub = [r for r in A if r['pat'] == p]
        print(f'  {p:58s} {n:3d}  goldsplit in top-50 {sum(r["goldsplit_rank"] >= 0 for r in sub)}, gold in top-50 {sum(r["in50"] for r in sub)}')
    if sy == 'rr':
        gaps = sorted(r['cost_gap'] for r in A if r['in50'])
        print('  Sumire cost gap gold - top1 (gold in top-50):', gaps)
random.seed(0)
A = [r for r in R if r['sp'] == 'tune' and r['sys'] == 'sumire' and cls(r) == 'A boundary']
print('\n== examples (tune, Sumire top1 boundary errors): pattern | gold-split rank | gold chunk <- top chunk | gold clause')
for p in sorted({r['pat'] for r in A}):
    for r in [r for r in A if r['pat'] == p][:4]:
        print(f"  {p[:2]} | {r['goldsplit_rank']:3d} | {r['chunk_gold']} <- {r['chunk_top']} | {r['gold'][:28]} <- {r['top'][:28]}")

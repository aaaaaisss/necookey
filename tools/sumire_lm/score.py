#!/usr/bin/env python3
"""score.py IN.tsv OUT.tsv [OUT_BEFORE.tsv]  -> accuracy per split, CER, wrong-form reproduction, pattern counts.
--fails N prints N failures (split held excluded unless --held)."""
import sys, re, collections
args = [a for a in sys.argv[1:] if not a.startswith('--')]
nf = next((int(a.split('=')[1]) for a in sys.argv if a.startswith('--fails=')), 0)
inp = {l.split('\t')[0]: l.rstrip('\n').split('\t') for l in open(args[0])}

def ed(a, b):
    p = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        c = [i]
        for j, y in enumerate(b, 1): c.append(min(p[j] + 1, c[j - 1] + 1, p[j - 1] + (x != y)))
        p = c
    return p[-1]

KANJI = re.compile(r'[一-龠々]')
def pattern(exp, top, wrong, reading):
    """coarse failure class from the differing span."""
    if not top: return 'empty'
    if wrong and top == wrong: pat = 'homophone=human-typo'
    else: pat = None
    # differing span
    i = 0
    while i < min(len(exp), len(top)) and exp[i] == top[i]: i += 1
    j = 0
    while j < min(len(exp), len(top)) - i and exp[-1 - j] == top[-1 - j]: j += 1
    de, dt = exp[i:len(exp) - j], top[i:len(top) - j]
    nk = len(KANJI.findall(de))
    if re.fullmatch(r'[ぁ-ゖ]+', dt) and nk: p2 = 'kanji->hiragana'
    elif re.fullmatch(r'[ぁ-ゖ]+', de) and KANJI.search(dt): p2 = 'hiragana->kanji'
    elif re.search(r'[ァ-ヺー]', de + dt): p2 = 'katakana'
    elif len(de) != len(dt): p2 = 'segmentation/okurigana'
    elif nk == 1 and len(de) == 1: p2 = 'single-kanji'
    else: p2 = 'homophone-word'
    return (pat + '/' if pat else '') + p2

def run(out):
    res = collections.defaultdict(lambda: [0, 0, 0, 0.0, 0, 0, 0])  # n, top1, top5, cer, wrongrepro, chars, target-ok
    pats = collections.Counter(); fails = []
    for l in open(out):
        c = l.rstrip('\n').split('\t')
        i = inp[c[0]]; sp = i[1]; exps = i[4].split('|'); wrong = i[5] if len(i) > 5 else ''
        rank, top = int(c[2]), c[3]
        r = res[sp]; r[0] += 1; r[1] += rank == 1; r[2] += 1 <= rank <= 5
        e = min(exps, key=lambda x: ed(top, x)); r[3] += ed(top, e); r[5] += len(e)
        r[4] += bool(wrong) and top == wrong
        if len(i) > 7: r[6] += i[6] in top and not (i[7] and i[7] in top and i[7] not in i[6])
        if rank != 1:
            pt = pattern(e, top, wrong, i[3]); pats[(sp, pt)] += 1
            fails.append((sp, c[0], i[3], e, top, pt))
    return res, pats, fails

res, pats, fails = run(args[1])
for sp, r in sorted(res.items()):
    print(f"{sp}: n={r[0]} top1={r[1]/r[0]:.3f} top5={r[2]/r[0]:.3f} CER={r[3]/r[5]:.4f} wrong-form={r[4]} target-ok={r[6]/r[0]:.3f}")
for (sp, p), n in sorted(pats.items(), key=lambda x: (x[0][0], -x[1])): print(f"  {sp} {p}: {n}")
if len(args) > 2:
    b, _, _ = run(args[2])
    for sp in res: print(f"delta {sp}: top1 {b[sp][1]} -> {res[sp][1]}")
held = '--held' in sys.argv
for f in [f for f in fails if held or f[0] != 'held'][:nf]: print('\t'.join(f))

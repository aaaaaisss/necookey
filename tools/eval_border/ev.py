#!/usr/bin/env python3
"""ev.py META BASE_GLOB [NEW_GLOB] : top1 exact / lenient / 境界ミス（bdiag.py の定義）を split ごとに、NEW があれば直る/壊れる。
lenient = rr2.py と同じ（NFKC・空白除去、difflib の差分 chunk がひらがな化で一致 or どちらかが全部かななら OK）。EV_EX=N で例を N 件。"""
import sys, os, re, difflib, unicodedata
sys.path.insert(0, '/workspace/work/nk-bd')
import bdiag
KANA = re.compile(r'^[ぁ-ゖァ-ヺー・]+$')
nfkc = lambda s: unicodedata.normalize('NFKC', s)
def hira(s): return ''.join(chr(ord(c) - 0x60) if 'ァ' <= c <= 'ヶ' else c for c in nfkc(s))
def lenient_eq(c, g):
    if c == g: return True
    c = re.sub(r'\s', '', nfkc(c)); g = re.sub(r'\s', '', nfkc(g))
    for op, i1, i2, j1, j2 in difflib.SequenceMatcher(None, c, g, autojunk=False).get_opcodes():
        if op == 'equal': continue
        a, b = c[i1:i2], g[j1:j2]
        if hira(a) == hira(b): continue
        if (a and KANA.match(a)) or (b and KANA.match(b)): continue
        return False
    return True
SPN = {'dtune': 'daily tune', 'dheld': 'daily held', 'ctune': 'colloq ctune', 'cheld': 'colloq cheld', 'tune': 'JWTD tune', 'held': 'JWTD held', 'eval': 'AJIMEE', 'ktune': 'dialect tune', 'kheld': 'dialect held', 'probe': 'probe'}
def run(meta, pat):
    res = bdiag.analyse(meta, bdiag.load_cands(pat, meta))
    for i, r in res.items():
        e = meta[i]['exp']; r['ex'] = r['top'] in e or nfkc(r['top']) in {nfkc(x) for x in e}
        r['len'] = any(lenient_eq(r['top'], x) for x in e)
    return res
def summ(res):
    out = {}
    for sp in SPN:
        rr = [r for r in res.values() if r['sp'] == sp]
        if rr: out[sp] = (len(rr), sum(r['ex'] for r in rr), sum(r['len'] for r in rr), sum(r['bnd'] for r in rr))
    return out
if __name__ == '__main__':
    meta = bdiag.load_meta(sys.argv[1]); A = run(meta, sys.argv[2]); sa = summ(A)
    B = run(meta, sys.argv[3]) if len(sys.argv) > 3 else None; sb = summ(B) if B else {}
    NEX = int(os.environ.get('EV_EX', '0'))
    print(f'{"set":14s} {"n":>5s}  exact  lenient  bnd' + ('   ->  exact  lenient  bnd   ex+/-  len+/-  bnd fix/new' if B else ''))
    for sp, (n, ex, le, bd) in sa.items():
        s = f'{SPN[sp]:14s} {n:5d}  {ex / n:.3f}  {le / n:.3f}  {bd:4d}'
        if B:
            n2, ex2, le2, bd2 = sb[sp]; ids = [i for i in A if A[i]['sp'] == sp and i in B]
            f = lambda k, a, b: sum(1 for i in ids if B[i][k] == b and A[i][k] == a)
            s += f'   ->  {ex2 / n2:.3f}  {le2 / n2:.3f}  {bd2:4d}   +{f("ex", 0, 1)}/-{f("ex", 1, 0)}  +{f("len", 0, 1)}/-{f("len", 1, 0)}  {f("bnd", 1, 0)}/{f("bnd", 0, 1)}'
        print(s)
    if B and NEX:
        for tag, a, b in (('FIX', 0, 1), ('REG', 1, 0)):
            for i in [i for i in A if i in B and A[i]['ex'] == a and B[i]['ex'] == b][:NEX]:
                print(tag, i, A[i]['sp'], A[i]['gold'], '|', A[i]['top'], '->', B[i]['top'])

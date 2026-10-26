#!/usr/bin/env python3
"""ev2.py IN.TSV TOP_GLOB [POOL_GLOB] : split 別と Total の exact / lenient / oracle@5 / oracle@50（bdiag 非依存）。
TOP_GLOB = 1 位と oracle@5 を取るダンプ（例 out/x/cands.*.tsv = 実機上段 n=8、.rerank = リランカー後）。
POOL_GLOB = oracle@50 と正解順位を取るダンプ（既定 = TOP_GLOB）。
lenient = 旧 ev.py と同じ（NFKC・空白除去、difflib の差分 chunk がひらがな化で一致 or どちらかが全部かななら OK）。
oracle@k は lenient 一致。EV_ERR=path で誤り行（id, split, 読み, 正解, 出力, 正解順位 top5/pool）を TSV に書く。
EV_BND=1 で境界ミス数（正解が pool にあるとき、その語分割の読み境界と 1 位の境界が交差 = 片方にしか無い境界がある）も出す。"""
import sys, os, re, glob, difflib, unicodedata, collections
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
SPN = collections.OrderedDict([('u', 'u 未知語'), ('b', 'b 境界'), ('f', 'f 方言/強制漢字'), ('h', 'h 同音'), ('c', 'c 回帰')])
def load_meta(fn):
    m = collections.OrderedDict()
    for l in open(fn, encoding='utf-8'):
        f = l.rstrip('\n').split('\t')
        if len(f) >= 5 and f[3]: m[f[0]] = dict(sp=f[0][0], rd=f[3], exp=f[4].split('|'))
    return m
def load(pat):
    rows = collections.defaultdict(list)
    for fn in sorted(glob.glob(pat)):
        for l in open(fn, encoding='utf-8'):
            f = l.rstrip('\n').split('\t')
            if len(f) < 8: continue
            segs = [tuple(x.split(':', 1)) for x in f[7].split('\x1f')] if f[7] else []
            rows[f[0]].append(dict(rank=int(f[1]), s=f[2], segs=segs))
    for v in rows.values(): v.sort(key=lambda r: r['rank'])
    return rows
def cuts(segs):
    p = 0; out = set()
    for r, _ in segs: p += len(r); out.add(p)
    return out
def rank_of(rows, exp):
    for r in rows:
        if any(lenient_eq(r['s'], x) for x in exp): return r['rank'], r
    return None, None
def analyse(meta, top, pool):
    res = collections.OrderedDict()
    for i, m in meta.items():
        t = top.get(i, []); p = pool.get(i, [])
        s = t[0]['s'] if t else ''
        e = m['exp']
        ex = s in e or nfkc(s) in {nfkc(x) for x in e}
        le = any(lenient_eq(s, x) for x in e) if s else False
        r5, _ = rank_of(t[:5], e)
        rp, gr = rank_of(p, e)
        bnd = False
        if not le and t and gr is not None and t[0]['segs'] and gr['segs']:
            bnd = cuts(t[0]['segs']) != cuts(gr['segs'])
        res[i] = dict(sp=m['sp'], rd=m['rd'], gold=e[0], top=s, ex=ex, len=le, o5=r5 is not None, o50=rp is not None, rpool=rp, bnd=bnd,
                      tsegs='|'.join(x[1] for x in t[0]['segs']) if t else '', gsegs='|'.join(x[1] for x in gr['segs']) if gr else '')
    return res
def summ(res):
    out = collections.OrderedDict()
    for sp in list(SPN) + ['T']:
        rr = [r for r in res.values() if sp == 'T' or r['sp'] == sp]
        if rr: out[sp] = (len(rr), sum(r['ex'] for r in rr), sum(r['len'] for r in rr), sum(r['o5'] for r in rr), sum(r['o50'] for r in rr), sum(r['bnd'] for r in rr))
    return out
if __name__ == '__main__':
    meta = load_meta(sys.argv[1]); top = load(sys.argv[2]); pool = load(sys.argv[3]) if len(sys.argv) > 3 else top
    res = analyse(meta, top, pool); s = summ(res)
    print(f'{"set":16s} {"n":>4s} {"exact":>11s} {"lenient":>11s} {"oracle@5":>11s} {"oracle@50":>11s} bnd')
    for sp, (n, ex, le, o5, o50, bd) in s.items():
        name = SPN.get(sp, 'Total')
        print(f'{name:16s} {n:4d} {ex:4d} ({ex/n:.3f}) {le:4d} ({le/n:.3f}) {o5:4d} ({o5/n:.3f}) {o50:4d} ({o50/n:.3f}) {bd:3d}')
    missing = [i for i in meta if i not in top]
    if missing: print('missing in dump:', ' '.join(missing))
    if os.environ.get('EV_ERR'):
        with open(os.environ['EV_ERR'], 'w', encoding='utf-8') as w:
            w.write('id\tsplit\treading\tgold\toutput\trank_pool\tin_top5\tin_pool50\tboundary_diff\tout_segs\tgold_segs\n')
            for i, r in res.items():
                if not r['len']:
                    w.write('\t'.join(map(str, [i, r['sp'], r['rd'], r['gold'], r['top'], r['rpool'] if r['rpool'] is not None else '-', int(r['o5']), int(r['o50']), int(r['bnd']), r['tsegs'], r['gsegs']])) + '\n')

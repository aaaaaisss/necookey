#!/usr/bin/env python3
"""ev3.py IN.TSV BASE_DUMP DUMP [DUMP...] : 設定ごとの exact / lenient / 致命 / ツギハギ と、BASE からの直った・壊れた文。

lenient = ev2.py と同じ。split は id 先頭字（u/b/f/h/c = 境界テスト 179 文、r = 回帰リスト）。
誤片（wrong piece）= 1 位の語分割（segs）のうち、正解文に無い漢字かカタカナを含む語。
  ツギハギ = 1 位が lenient 不一致で、誤片が 2 つ以上（値落ち…歌、胃炎|買っ、地位|トマチン|祭、機能|痛く|注が）。
  致命     = 1 位が lenient 不一致で、ツギハギか、誤片の読み範囲が正解候補（pool 内の完全一致、無ければ lenient 一致の最上位）の
             語の読み範囲と一致しない（＝同じ読み範囲の表記違い＝同音異義ではない：区切りミス・方言崩れ・未知語崩れ）。
             誤片が無い誤り・正解候補が pool に無い誤りも致命。
oracle@2/@3 = 上位 2/3 位以内に lenient 一致（修正しやすさ）。
"""
import sys, os, re, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ev2 import load_meta, load, lenient_eq, nfkc

KJ = re.compile(r'[\u4e00-\u9fff々\u30a1-\u30fa]')


def spans(segs):
    p = 0; out = []
    for r, o in segs:
        out.append((p, p + len(r), o)); p += len(r)
    return out


def wrong_pieces(segs, gold):
    g = nfkc(gold)
    return [(a, b, o) for a, b, o in spans(segs) if any(KJ.match(c) and c not in g for c in o)]


def judge(meta, top, pool):
    res = {}
    for i, m in meta.items():
        t = top.get(i, [])
        if not t:
            res[i] = None; continue
        s = t[0]['s']; e = m['exp']
        ex = s in e or nfkc(s) in {nfkc(x) for x in e}
        le = any(lenient_eq(s, x) for x in e)
        o2 = any(any(lenient_eq(r['s'], x) for x in e) for r in t[:2])
        o3 = any(any(lenient_eq(r['s'], x) for x in e) for r in t[:3])
        wp = wrong_pieces(t[0]['segs'], e[0]) if not le else []
        pl = pool.get(i, [])
        gr = next((r for r in pl if nfkc(r['s']) in {nfkc(x) for x in e}), None) or \
            next((r for r in pl if any(lenient_eq(r['s'], x) for x in e)), None)
        fatal = False
        if not le:
            if gr is None or not gr['segs'] or not wp:
                fatal = True  # 正解が pool に無い／誤りが漢字片でない（かな・区切り）
            else:
                gsp = {(a, b) for a, b, _ in spans(gr['segs'])}
                fatal = len(wp) >= 2 or any((a, b) not in gsp for a, b, _ in wp)
        res[i] = dict(sp=i[0], s=s, ex=ex, le=le, o2=o2, o3=o3, tg=(not le and len(wp) >= 2), fatal=fatal, gold=e[0])
    return res


def row(res, sel):
    rr = [r for i, r in res.items() if r is not None and sel(i)]
    n = len(rr)
    return n, sum(r['ex'] for r in rr), sum(r['le'] for r in rr), sum(r['fatal'] for r in rr), sum(r['tg'] for r in rr), sum(r['o2'] for r in rr), sum(r['o3'] for r in rr)


if __name__ == '__main__':
    meta = load_meta(sys.argv[1])
    dumps = sys.argv[2:]
    R = {}
    for d in dumps:
        pool = load(d + '.pool') if os.path.exists(d + '.pool') else {}
        R[d] = judge(meta, load(d), pool)
    base = dumps[0]
    sets = [('179', lambda i: i[0] in 'ubfhc'), ('reg', lambda i: i[0] == 'r'), ('dia', lambda i: i[0] == 'd')]
    print(f'{"dump":28s} {"set":4s} {"n":>4s} {"exact":>5s} {"len":>4s} {"致命":>3s} {"継接":>3s} {"o@2":>4s} {"o@3":>4s}  +len/-len  +ex/-ex')
    for d in dumps:
        for name, sel in sets:
            n, ex, le, fa, tg, o2, o3 = row(R[d], sel)
            if n == 0: continue
            fix = [i for i in R[d] if sel(i) and R[d][i] and R[base][i] and R[d][i]['le'] and not R[base][i]['le']]
            brk = [i for i in R[d] if sel(i) and R[d][i] and R[base][i] and not R[d][i]['le'] and R[base][i]['le']]
            fx = [i for i in R[d] if sel(i) and R[d][i] and R[base][i] and R[d][i]['ex'] and not R[base][i]['ex']]
            bx = [i for i in R[d] if sel(i) and R[d][i] and R[base][i] and not R[d][i]['ex'] and R[base][i]['ex']]
            print(f'{os.path.basename(d)[-28:]:28s} {name:4s} {n:4d} {ex:5d} {le:4d} {fa:4d} {tg:4d} {o2:4d} {o3:4d}  +{len(fix)}/-{len(brk)}  +{len(fx)}/-{len(bx)}')
    if os.environ.get('EV_DIFF'):
        for d in dumps[1:]:
            print(f'\n## {os.path.basename(d)} vs {os.path.basename(base)}（top-1 が変わった文）')
            for i in R[d]:
                a, b = R[base].get(i), R[d][i]
                if a and b and a['s'] != b['s']:
                    tag = ('直' if b['le'] and not a['le'] else '壊' if a['le'] and not b['le'] else
                           'ex+' if b['ex'] and not a['ex'] else 'ex-' if a['ex'] and not b['ex'] else '・')
                    print(f'{tag}\t{i}\t{a["s"]}\t→\t{b["s"]}\t(正解 {b["gold"]})')
    if os.environ.get('EV_LIST'):
        d = dumps[int(os.environ['EV_LIST'])]
        print(f'\n## 誤り一覧 {os.path.basename(d)}')
        for i, r in R[d].items():
            if r and not r['le']:
                print(f'{i}\t{"致命" if r["fatal"] else "同音"}\t{"継接" if r["tg"] else ""}\t{r["s"]}\t(正解 {r["gold"]})')

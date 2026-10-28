#!/usr/bin/env python3
"""hard_eval.py OUT1 IN1 [OUT2 IN2 ...] [--write hard.tsv] : 「切る」評価用 hard セットの選定と手数比較（EXP_OVERNIGHT.md 実験 1）。

OUTk = ChipUiEvalTest の NK_CHIP_OUT（その下の hard/ に chip_eval.tsv・cands.tsv・cands.tsv.pool）、INk = 入力 tsv。
hard = 候補のうち 1 位が lenient 不一致の文すべて（内訳: ev3 の「区切り誤り」「ツギハギ」＝長い誤塊・区切り違い、「同音」＝同じ読み範囲の表記違い）。
--write で選ばれた文を tsv（in.tsv 形式）に書く。
"""
import sys, os, collections
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from chip_eval import read_set, MODES


def ops(plan):
    c = collections.Counter()
    for step in plan.split(' > '):
        step = step.strip()
        if not step: continue
        for k in ['予測行', 'タップ', '候補', '切る', 'くっつける', '左フリック', '右フリック']:
            if step.startswith(k): c[k] += 1
    return c


def main():
    args = sys.argv[1:]
    w = None
    if '--write' in args:
        i = args.index('--write'); w = args[i + 1]; del args[i:i + 2]
    rows = []
    for out, inp in zip(args[::2], args[1::2]):
        rows += read_set(out, 'hard', inp, {})
    errs = [r for r in rows if r['cls'] != '正解']
    hard = errs
    for r in rows:
        for k in ['new_cut0', 'new_nofull_cut0', 'new_cut1', 'new_nofull_cut1']:
            if k in r: r[k] = 0 if r['cls'] == '正解' else int(r[k])
    print(f'候補 {len(rows)} 文、hard（lenient 誤り）{len(hard)}（区切り誤り {sum(r["cls"]=="区切り誤り" for r in hard)}・ツギハギ {sum(r["cls"]=="ツギハギ" for r in hard)}・同音 {sum(r["cls"]=="同音" for r in hard)}）')

    def stat(rs, a, b):
        ra = [r[a] for r in rs if r[a] >= 0]; rb = [r[b] for r in rs if r[b] >= 0]
        both = [r for r in rs if r[a] >= 0 and r[b] >= 0]
        win = sum(1 for r in rs if r[a] >= 0 and (r[b] < 0 or r[a] < r[b]))
        lose = sum(1 for r in rs if r[b] >= 0 and (r[a] < 0 or r[b] < r[a]))
        pa = sum(r[a] for r in both) / max(1, len(both)); pb = sum(r[b] for r in both) / max(1, len(both))
        return f'{len(ra)}/{len(rs)} vs {len(rb)}/{len(rs)} | {pa:.2f} vs {pb:.2f} ({len(both)}) | {win} / {lose}'

    print('\n| 集合 | n | モード | 到達(6手内) 新 vs 現行 | 平均手数(両方到達) 新 vs 現行 | 新が少ない / 現行が少ない |')
    print('|---|---|---|---|---|---|')
    for name, rs in [('hard 全体', hard), ('区切り誤り＋ツギハギ', [r for r in hard if r['cls'] != '同音']), ('同音', [r for r in hard if r['cls'] == '同音']),
                     ('短文', [r for r in hard if r['id'][0] in 'xzubfd']), ('2文連結', [r for r in hard if r['id'][0] in 'yw'])]:
        for mname, a, b in MODES:
            print(f'| {name} | {len(rs)} | {mname} | {stat(rs, a, b)} |')
    if 'new_cut0' in hard[0]:
        print('\n切るの手数を変えた新 UI（hard 全体、到達 / 両方到達した文の平均）:')
        for mname, full in [('全体候補あり', ''), ('全体候補なし', '_nofull')]:
            cols = [('切る 2 手（既定）', 'new' + full), ('切るなし', 'new' + full + '_cut0'), ('切る 1 手', 'new' + full + '_cut1')]
            both = [r for r in hard if all(r[c] >= 0 for _, c in cols)]
            print(f'- {mname}: ' + '、'.join(f'{n} {sum(r[c] >= 0 for r in hard)}/{len(hard)}・{sum(r[c] for r in both) / max(1, len(both)):.2f}' for n, c in cols) + f'（平均は {len(both)} 文）')
            for n, c in cols[1:]:
                d = [r['id'] + f"({r['new' + full]}→{r[c]})" for r in hard if r[c] != r['new' + full]]
                print(f'  - {n}で手数が変わる文: {" ".join(d) if d else "なし"}')
    for mname, a, b in MODES:
        tot = collections.Counter(); cut_rows = []
        for r in hard:
            c = ops(r['new_nofull_plan'] if a == 'new_nofull' else r['new_plan'])
            tot += c
            if c['切る']: cut_rows.append(r['id'])
        print(f'\n{mname}: 新 UI 最短手順の操作 {dict(tot)}、切るを使った文 {len(cut_rows)}: {" ".join(cut_rows)}')
    print('\n| id | 分類 | 1位 | 正解 | 初期チップ | 新 | 現行 | 新(全体なし) | 現行(全体なし) | 新UI手順(全体なし) | 現行UI手順(全体なし) |')
    print('|---|---|---|---|---|---|---|---|---|---|---|')
    for r in hard:
        print(f"| {r['id']} | {r['cls']} | {r['top1']} | {r['gold']} | {r['chips'].replace('|', '／')} | {r['new']} | {r['cur']} | {r['new_nofull']} | {r['cur_nofull']} | {r['new_nofull_plan']} | {r['cur_nofull_plan']} |")
    if w:
        src = {}
        for inp in args[1::2]:
            for l in open(inp, encoding='utf-8'):
                c = l.rstrip('\n').split('\t'); src[c[0]] = l.rstrip('\n')
        with open(w, 'w', encoding='utf-8') as f:
            for r in hard: f.write(src[r['id']] + '\n')


if __name__ == '__main__':
    main()

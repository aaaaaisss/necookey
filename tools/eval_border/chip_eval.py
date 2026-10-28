#!/usr/bin/env python3
"""chip_eval.py OUTDIR NAME=IN.TSV [NAME=IN.TSV...] [--classified classified.tsv] [--list]
ChipUiEvalTest の OUTDIR/NAME/chip_eval.tsv を分類別に集計（Markdown を標準出力へ）。

分類は ev3.py の judge（OUTDIR/NAME/cands.tsv と .pool）:
  正解 = 1 位が lenient 一致（手数 0）、ツギハギ = tg、区切り誤り = 致命で tg でない、同音 = 致命でない誤り。
手数 -1 = 6 手以内に届かない。平均は届いた文だけ。「同じ文で」は両方届いた文どうしの平均。
モード: 全体候補あり = 読み全体の N-best（2〜8 位）を 1 手で選べる、なし = チップ／文節操作だけ。
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ev2 import load_meta, load
from ev3 import judge

MODES = [('全体候補あり', 'new', 'cur'), ('全体候補なし', 'new_nofull', 'cur_nofull')]


def read_set(out, name, inp, cls_old):
    d = os.path.join(out, name)
    meta = load_meta(inp)
    res = judge(meta, load(os.path.join(d, 'cands.tsv')), load(os.path.join(d, 'cands.tsv.pool')))
    rows = []
    lines = open(os.path.join(d, 'chip_eval.tsv'), encoding='utf-8').read().splitlines()
    head = lines[0].split('\t')
    for l in lines[1:]:
        f = dict(zip(head, l.split('\t')))
        r = res.get(f['id'])
        if r is None: continue
        cls = '正解' if r['le'] else 'ツギハギ' if r['tg'] else '区切り誤り' if r['fatal'] else '同音'
        row = dict(f, cls=cls, set=name, old=cls_old.get(f['id'], ''))
        for k in ['new', 'new_adv', 'cur', 'new_nofull', 'cur_nofull']:
            row[k] = 0 if r['le'] else int(f[k])
        rows.append(row)
    return rows


def cell(rs, a, b):
    n = len(rs)
    if n == 0: return '-'
    ra = [r[a] for r in rs if r[a] >= 0]; rb = [r[b] for r in rs if r[b] >= 0]
    both = [r for r in rs if r[a] >= 0 and r[b] >= 0]
    ma = sum(ra) / len(ra) if ra else float('nan'); mb = sum(rb) / len(rb) if rb else float('nan')
    pa = sum(r[a] for r in both) / len(both) if both else float('nan')
    pb = sum(r[b] for r in both) / len(both) if both else float('nan')
    win = sum(1 for r in rs if r[a] >= 0 and (r[b] < 0 or r[a] < r[b]))
    lose = sum(1 for r in rs if r[b] >= 0 and (r[a] < 0 or r[b] < r[a]))
    return (f'{len(ra)}/{n} vs {len(rb)}/{n} | {ma:.2f} vs {mb:.2f} | {pa:.2f} vs {pb:.2f} ({len(both)}) | {win} / {lose}')


def table(title, groups):
    print(f'\n### {title}\n')
    print('| 分類 | n | モード | 到達 新 vs 現行 | 平均手数 新 vs 現行 | 同じ文で 新 vs 現行 (文数) | 新が少ない / 現行が少ない |')
    print('|---|---|---|---|---|---|---|')
    for name, rs in groups:
        for mname, a, b in MODES:
            print(f'| {name} | {len(rs)} | {mname} | {cell(rs, a, b)} |')


def main():
    args = sys.argv[1:]
    out = args.pop(0)
    cls_old = {}
    show_list = '--list' in args
    if show_list: args.remove('--list')
    if '--classified' in args:
        i = args.index('--classified'); fn = args[i + 1]; del args[i:i + 2]
        for l in open(fn, encoding='utf-8'):
            f = l.rstrip('\n').split('\t')
            if f[0] != 'id' and len(f) > 2: cls_old[f[0]] = f[2]
    allrows = []
    for a in args:
        name, inp = a.split('=', 1)
        allrows += read_set(out, name, inp, cls_old)
    sets = list(dict.fromkeys(r['set'] for r in allrows))
    errs = [r for r in allrows if r['cls'] != '正解']
    groups = [(c, [r for r in errs if r['cls'] == c]) for c in ['区切り誤り', 'ツギハギ', '同音']]
    table('誤り文・全セット合計（分類別）', groups + [('誤り計', errs)])
    table('誤り文・セット別', [(s, [r for r in errs if r['set'] == s]) for s in sets])
    adv = [r for r in errs]
    print('\n自動送り（候補選択後に次のチップを選択）: 全体候補あり 到達 %d/%d、平均 %.2f（既定 %.2f）' % (
        sum(r['new_adv'] >= 0 for r in adv), len(adv),
        sum(r['new_adv'] for r in adv if r['new_adv'] >= 0) / max(1, sum(r['new_adv'] >= 0 for r in adv)),
        sum(r['new'] for r in adv if r['new'] >= 0) / max(1, sum(r['new'] >= 0 for r in adv))))
    if cls_old:
        olds = sorted({r['old'] for r in allrows if r['old']})
        table('classified.tsv の分類別（179 文、2026-10-26 時点の誤り。今の 1 位が正解なら 0 手）',
              [(c, [r for r in allrows if r['old'] == c]) for c in olds])
    if show_list:
        print('\n### 誤り文ごと（-1 = 6 手で届かない）\n')
        print('| id | 分類 | 1位 | 正解 | 初期チップ | 新 | 現行 | 新(全体なし) | 現行(全体なし) | 新UI手順(全体なし) | 現行UI手順(全体なし) |')
        print('|---|---|---|---|---|---|---|---|---|---|---|')
        for r in errs:
            r = {k: (v.replace('|', '／') if isinstance(v, str) else v) for k, v in r.items()}
            print(f"| {r['id']} | {r['cls']} | {r['top1']} | {r['gold']} | {r['chips']} | {r['new']} | {r['cur']} | "
                  f"{r['new_nofull']} | {r['cur_nofull']} | {r['new_nofull_plan']} | {r['cur_nofull_plan']} |")


if __name__ == '__main__':
    main()

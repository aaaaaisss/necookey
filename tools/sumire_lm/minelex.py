#!/usr/bin/env python3
"""minelex.py OUT.pkl [MB=150] : cc_all.txt（評価セット cc_more.txt とは別）の先頭から MB を fugashi で分かち書きし、
かなだけの語（ひらがな/カタカナ・ー・小書き含む）について (表記, pos1, pos2, cForm, lemma) の頻度と、前後の文字種を数える。
評価セット（colloq.tsv / daily.tsv）の節を含む行はスキップ（汚染防止）。"""
import sys, re, pickle, collections, unicodedata
import fugashi
OUT = sys.argv[1]; MB = int(sys.argv[2]) if len(sys.argv) > 2 else 150
TG = fugashi.Tagger()
KANA = re.compile(r'^[ぁ-ゖァ-ヺー]+$')
ev = set()
for fn in ('/workspace/work/nk-bd/colloq.tsv', '/workspace/work/sumire_lm/daily.tsv'):
    for l in open(fn, encoding='utf-8'):
        f = l.rstrip('\n').split('\t')
        if len(f) >= 5 and len(f[4]) >= 8: ev.add(unicodedata.normalize('NFKC', f[4])[:8])
cnt = collections.Counter(); nlines = 0; nskip = 0; ntok = 0
with open('/workspace/work/sumire_lm/corpus/cc_all.txt', 'rb') as f:
    data = f.read(MB * 1_000_000).decode('utf-8', 'ignore')
for line in data.split('\n')[:-1]:
    line = unicodedata.normalize('NFKC', line).replace('ｰ', 'ー')
    if not line: continue
    nlines += 1
    if any(line[i:i + 8] in ev for i in range(len(line) - 7)):
        nskip += 1; continue
    for w in TG(line):
        ntok += 1
        s = w.surface
        if len(s) >= 2 and KANA.match(s):
            ft = w.feature
            cnt[(s, ft.pos1, ft.pos2, getattr(ft, 'cForm', '') or '', ft.lemma or '')] += 1
pickle.dump(dict(cnt=cnt, nlines=nlines, nskip=nskip, ntok=ntok, mb=MB), open(OUT, 'wb'))
print('lines', nlines, 'skip', nskip, 'tokens', ntok, 'types', len(cnt))

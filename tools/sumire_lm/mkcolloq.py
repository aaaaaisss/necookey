#!/usr/bin/env python3
"""mkcolloq.py OUT.tsv [N=3000] : 口語っぽい節の自動ラベル評価セット（CC-100 の cc_more.txt から、語彙採掘に使う cc_all.txt とは別の部分）。
行 = id \\t split(ctune/cheld 交互) \\t marker \\t reading \\t gold。読みは fugashi(unidic-lite): かなだけの語は表記そのもの（ー・っを残す）、
それ以外は unidic の kana をひらがなに。ノイズ: unidic の読み誤り・表記ゆれ（正解は 1 通りだけ）→ top1 は表記ゆれで過小評価、境界判定の方が信頼できる。"""
import sys, re, random, unicodedata
import fugashi
OUT = sys.argv[1]; N = int(sys.argv[2]) if len(sys.argv) > 2 else 3000
SRC = '/workspace/work/sumire_lm/corpus/cc_more.txt'; OFF = 5_000_000_000; NBYTES = 400_000_000
TG = fugashi.Tagger()
K2H = lambda s: ''.join(chr(ord(c) - 0x60) if 'ァ' <= c <= 'ヶ' else c for c in s)
OKCH = re.compile(r'^[ぁ-ゖァ-ヺー一-龥々、]+$')
KANA = re.compile(r'^[ぁ-ゖァ-ヺー]+$')
MARK = [('long', re.compile(r'[ぁ-ゖ]ー')), ('tsu_end', re.compile(r'っ(、|$)')),
        ('slang', re.compile(r'っしょ|じゃん|じゃね|やばい|やば[っくかみ]|ヤバ|まじ[でかっ]|マジ[でかっ]|すげ|スゲ|わろた|ワロタ|ウケる|うける|めっちゃ|っぽい|^てか|^ってか|ガチで|がちで|ぶっちゃけ|うざ|(?<!ラ)ウザ|キモい|きもい|しんど|えぐい|ねー|ねぇ|よぉ|なぁ|わぁ|ちゃう|ちゃった|っす$|ッス$|んだよ|かよ$|っけ$|とけ$|ってば|んない|らんない|っつ|みたいな$|じゃねえ|ねえよ|すぎ$|わかんね'))]


def reading(s):
    rd = []
    for w in TG(s):
        if KANA.match(w.surface): rd.append(K2H(w.surface))
        else:
            k = w.feature.kana if getattr(w.feature, 'kana', None) and w.feature.kana != '*' else None
            if w.surface == '、': rd.append('、'); continue
            if not k: return None
            rd.append(K2H(k))
    r = ''.join(rd)
    return r if re.fullmatch(r'[ぁ-ゖー、]+', r) else None


seen = set(); pool = []
with open(SRC, 'rb') as f:
    f.seek(OFF); data = f.read(NBYTES).decode('utf-8', 'ignore')
for line in data.split('\n')[1:-1]:
    line = unicodedata.normalize('NFKC', line).replace('､', '、').replace('ｰ', 'ー')
    for cl in re.split(r'[。！？!?…♪☆★\s「」（）()『』]+', line):
        cl = cl.strip('、')
        if not (6 <= len(cl) <= 40) or not OKCH.match(cl) or cl in seen: continue
        mk = [n for n, r in MARK if r.search(cl)]
        if not mk: continue
        seen.add(cl); pool.append((cl, mk[0]))
random.seed(7); random.shuffle(pool)
out = []
for cl, mk in pool:
    rd = reading(cl)
    if not rd: continue
    out.append((cl, mk, rd))
    if len(out) >= N: break
with open(OUT, 'w', encoding='utf-8') as fo:
    for i, (cl, mk, rd) in enumerate(out):
        fo.write(f'c{i}\t{"ctune" if i % 2 == 0 else "cheld"}\t{mk}\t{rd}\t{cl}\n')
print(len(pool), 'candidates;', len(out), 'written')

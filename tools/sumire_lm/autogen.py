#!/usr/bin/env python3
"""Auto-generated training clauses from CC-100 (held-out slice, not used for LM counts).
usage: autogen.py CORPUS_TXT SKIP_BYTES N OUT.tsv [JWTD_TSV AJIMEE_TSV]
- reads CORPUS_TXT from byte SKIP_BYTES (LM counts use the first 4.5e9 bytes -> SKIP=4500000000)
- drops lines containing any JWTD/AJIMEE target clause (same rule as decon.py)
- per line at most one clause (span between 、。 etc.) of 4..24 kana/kanji/ー chars with >=1 kanji,
  context = up to 20 chars before it; reading = fugashi unidic-lite kana (skip if any token lacks one)
- every 7th line considered (spreads over the slice). Out layout = jwtd_kc.tsv (split=auto).
"""
import sys, re, random, unicodedata, fugashi, ahocorasick
src, skip, N, outp = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
tg = fugashi.Tagger()
OK = re.compile(r'^[ぁ-ゖァ-ヺー一-龠々〆ヵヶ]+$'); KAN = re.compile(r'[一-龠々]')
SEP = re.compile(r'[、。，．！？!?「」『』（）()\s・：:；;]')
A = ahocorasick.Automaton(); pats = set()
if len(sys.argv) > 6:
    for l in open(sys.argv[5]):
        f = l.rstrip('\n').split('\t'); pats.update(f[4].split('|')); pats.update(f[5].split('|'))
    for l in open(sys.argv[6]):
        for x in l.rstrip('\n').split('\t'):
            for p in x.split('|'):
                if len(p) >= 6 and KAN.search(p): pats.add(p)
for p in pats:
    if len(p) >= 6: A.add_word(p, 1)
A.make_automaton()
def reading(s):
    out = []
    for w in tg(s):
        k = w.feature.kana
        if not k or k == '*':
            if re.fullmatch(r'[ぁ-ゖァ-ヺー]+', w.surface): k = w.surface
            else: return None
        out.append(k)
    return ''.join(chr(ord(c) - 0x60) if 'ァ' <= c <= 'ヶ' else c for c in ''.join(out))
random.seed(7)
f = open(src, 'rb'); f.seek(skip); f.readline()
o = open(outp, 'w'); n = li = 0; seen = set()
for raw in f:
    li += 1
    if li % 7: continue
    t = unicodedata.normalize('NFKC', raw.decode('utf-8', 'ignore').strip())
    t = t.replace('ｰ', 'ー')
    if len(t) < 8 or next(A.iter(t), None) is not None: continue
    # NFKC turns full-width punctuation to ASCII; SEP covers both
    spans = []; s = 0
    for i, ch in enumerate(t + '。'):
        if SEP.match(ch) or ch in ',.':
            if 4 <= i - s <= 24: spans.append((s, i))
            s = i + 1
    spans = [(a, b) for a, b in spans if OK.match(t[a:b]) and KAN.search(t[a:b]) and t[a:b] not in seen]
    if not spans: continue
    a, b = random.choice(spans); cl = t[a:b]
    r = reading(cl)
    if not r or not re.fullmatch(r'[ぁ-ゖー]+', r): continue
    seen.add(cl)
    ctx = t[max(0, a - 20):a]
    o.write(f"g{n}\tauto\t{ctx}\t{r}\t{cl}\t\t\t\n"); n += 1
    if n >= N: break
print(n, 'auto rows from', li, 'lines')

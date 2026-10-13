#!/usr/bin/env python3
"""Build JWTD kanji-conversion eval set (outside repo; CC BY-SA 3.0 data, never bundle).
Out: jwtd_kc.tsv  cols: id  split(tune|held)  context  reading(hiragana)  expected  wrong  target(correct diff str)  target_wrong
- source: jwtd_v2.0/train.jsonl kanji-conversion diffs (AJIMEE is built from test.jsonl; we also drop any
  sentence whose post_text appears in AJIMEE original_text) -> no AJIMEE leakage.
- clause = span between 、。 etc. containing the (single) diff; context = up to 20 chars before clause.
- reading via fugashi+unidic-lite 'kana' field; kept only if wrong clause reading == correct clause reading
  (true homophone error, reading consistent), clause 4..24 chars of kana/kanji/ー only.
"""
import json, random, re, sys, fugashi
N = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
random.seed(1234)
tg = fugashi.Tagger()
aj = {d['original_text'] for d in json.load(open('ajimee/JWTD_v2/v1/evaluation_items.json'))}
OK = re.compile(r'^[ぁ-ゖァ-ヺー一-龠々〆ヵヶ]+$')
SEP = re.compile(r'[、。，．！？!?「」『』（）()\s・：:；;]')

def reading(s):
    out = []
    for w in tg(s):
        k = w.feature.kana
        if not k or k == '*':
            if re.fullmatch(r'[ぁ-ゖァ-ヺー]+', w.surface): k = w.surface
            else: return None
        out.append(k)
    r = ''.join(out)
    return ''.join(chr(ord(c) - 0x60) if 'ァ' <= c <= 'ヶ' else c for c in r)

def clause_bounds(t, a, b):
    s = a
    while s > 0 and not SEP.match(t[s - 1]): s -= 1
    e = b
    while e < len(t) and not SEP.match(t[e]): e += 1
    return s, e

rows = []
with open('jwtd_v2.0/train.jsonl') as f:
    lines = [l for l in f if 'kanji-conversion' in l]
random.shuffle(lines)
seen = set()
for l in lines:
    if len(rows) >= N: break
    d = json.loads(l)
    if len(d['diffs']) != 1 or not d['diffs'][0]['category'].startswith('kanji-conversion'): continue
    pre, post = d['pre_text'], d['post_text']
    if post in aj or post in seen: continue
    # locate diff by common prefix/suffix
    p = 0
    while p < min(len(pre), len(post)) and pre[p] == post[p]: p += 1
    q = 0
    while q < min(len(pre), len(post)) - p and pre[-1 - q] == post[-1 - q]: q += 1
    s, e = clause_bounds(post, p, len(post) - q)
    clause = post[s:e]
    wclause = pre[s:len(pre) - (len(post) - e)]
    if not (4 <= len(clause) <= 24) or not OK.match(clause) or not OK.match(wclause): continue
    r = reading(clause)
    if not r or r != reading(wclause): continue
    ctx = post[max(0, s - 20):s]
    seen.add(post)
    rows.append((ctx, r, clause, wclause, post[p:len(post) - q], pre[p:len(pre) - q]))
with open('jwtd_kc.tsv', 'w') as o:
    for i, (c, r, x, w, tp, tw) in enumerate(rows):
        o.write(f"j{i}\t{'tune' if i % 2 == 0 else 'held'}\t{c}\t{r}\t{x}\t{w}\t{tp}\t{tw}\n")
# AJIMEE eval-only tsv (input katakana -> hiragana; expected joined by |)
with open('ajimee.tsv', 'w') as o:
    for d in json.load(open('ajimee/JWTD_v2/v1/evaluation_items.json')):
        r = ''.join(chr(ord(c) - 0x60) if 'ァ' <= c <= 'ヶ' else c for c in d['input'])
        o.write(f"a{d['index']}\teval\t{d['context_text']}\t{r}\t{'|'.join(d['expected_output'])}\t\n")
print(len(rows), 'jwtd rows')

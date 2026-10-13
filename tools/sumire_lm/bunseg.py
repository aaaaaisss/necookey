#!/usr/bin/env python3
"""bunseg.py [SCORE_TAG=lin_base+lmw+lmc+pmi_tune] : classify top1 errors (adopted reranker and Sumire top1) into
  a  = bunsetsu/word-boundary error, split in reading space differs from gold
       a1 crossing   : a top1 segment partially overlaps a gold segment (true boundary error)
       a2 nested     : one side splits a span the other keeps whole AND the surfaces of that span differ (e.g. は|医者 vs 歯医者)
  b  = same split, orthographic variant (kana/kanji, okurigana, kanji subset like 子ども/子供, width/NFKC)
  c  = same split, different word (homophone: 製作/制作, 一次/一時, 捜/探)
  d  = other (gold reading alignment failed etc.)
Gold split: Sumire's own segments of the gold row when gold is in the top-50, else fugashi(unidic-lite) tokens of the gold
surface with their kana readings (must concatenate to the input reading; else -> d).  Splits are compared as sets of cut
positions in the reading, so a pure granularity difference (男性|青年誌 vs 男性青年誌, same surface) is NOT an error.
Then for boundary errors: best rank of a top-50 candidate whose split has no crossing with gold ("gold split present"),
number of distinct splits in the top-50, gold noun present in this sentence's top-50 / anywhere in the whole dump
(1.36M rows, proxy for 'in Sumire's dictionary'), POS of the gold token at the first crossing, cost gap.
Writes $W/diag/bunseg_<tag>.tsv (one row per error) and prints the tables. Only JWTD tune is used for any judgment;
held / AJIMEE counts are printed for reference."""
import sys, os, glob, re, unicodedata, collections, json, numpy as np
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm')
TAG = sys.argv[1] if len(sys.argv) > 1 else 'lin_base+lmw+lmc+pmi_tune'
z = np.load(f'{W}/ftab.npz', allow_pickle=True); G = z['G']; LAB = z['LAB'].astype(bool); SPL = z['SPL']
S = np.load(f'{W}/scores/{TAG}.npy').astype(float)
meta = {}
for fn in ('jwtd_kc', 'ajimee'):
    for l in open(f'{W}/data/{fn}.tsv'):
        f = l.rstrip('\n').split('\t'); meta[f[0]] = dict(sp=f[1], ctx=f[2], rd=f[3], exp=f[4].split('|'))
# rows of the evaluation sentences + global surface set (dictionary proxy)
rows = collections.defaultdict(list); seen_surf = set(); gi = 0
for fn in sorted(glob.glob(f'{W}/cands/cands.*.tsv')):
    for l in open(fn):
        f = l.rstrip('\n').split('\t')
        segs = [tuple(x.split(':', 1)) for x in f[7].split('\x1f')] if len(f) >= 8 and f[7] else []
        for _, s in segs: seen_surf.add(s)
        if f[0] in meta:
            rows[f[0]].append(dict(g=gi, rank=int(f[1]), s=f[2], cost=int(f[4]) if f[4] else None, segs=segs))
        gi += 1
assert gi == len(G)
seen_pref = set()
for s in seen_surf:
    for k in range(1, len(s) + 1): seen_pref.add(s[:k])
import fugashi
TG = fugashi.Tagger()
h = lambda s: ''.join(chr(ord(c) - 0x60) if 'ァ' <= c <= 'ヶ' else c for c in s)
KANJI = re.compile(r'[一-龥々〆ヵヶ]')
def fug(surface, reading):
    """fugashi tokens [(reading, surface, pos)] aligned to reading, or None"""
    toks = []
    for w in TG(surface):
        k = w.feature.kana if w.feature.kana and w.feature.kana != '*' else w.surface
        toks.append((h(k), w.surface, f'{w.feature.pos1}-{w.feature.pos2}'))
    norm = lambda x: unicodedata.normalize('NFKC', x).replace('ゔ', 'ぶ')
    if norm(''.join(t[0] for t in toks)) != norm(reading): return None
    return toks
def cuts(segs):
    p = 0; out = []
    for r, _ in segs: p += len(r); out.append(p)
    return out  # end positions
def chunks(a, b):
    """a, b = seg lists (reading, surface). Returns list of (span, a_part, b_part) between common cut points"""
    ca, cb = cuts(a), cuts(b); common = sorted(set(ca) & set(cb)); out = []; ia = ib = 0; lo = 0
    for c in common:
        pa = []; pb = []
        while ia < len(a) and ca[ia] <= c: pa.append(a[ia]); ia += 1
        while ib < len(b) and cb[ib] <= c: pb.append(b[ib]); ib += 1
        out.append(((lo, c), pa, pb)); lo = c
    return out
def crossing(pa, pb):
    """within a chunk (no common inner cut): crossing iff both sides have inner cuts"""
    return len(pa) > 1 and len(pb) > 1
KATA2HIRA = lambda s: ''.join(chr(ord(c) - 0x60) if 'ァ' <= c <= 'ヶ' else c for c in s)
def variant(x, y):
    nx, ny = unicodedata.normalize('NFKC', x), unicodedata.normalize('NFKC', y)
    if nx == ny or KATA2HIRA(nx) == KATA2HIRA(ny): return 'b:width/kana-type'
    kx, ky = KANJI.findall(nx), KANJI.findall(ny)
    if not kx or not ky: return 'b:kana/kanji'
    if kx == ky: return 'b:okurigana'
    sh, lg = (kx, ky) if len(kx) < len(ky) else (ky, kx)
    it = iter(lg)
    if len(sh) < len(lg) and all(c in it for c in sh): return 'b:mixed(子ども/子供)'
    return 'c'
def classify(top, gold):
    """top, gold: seg lists. returns (cls, detail, first differing chunk)"""
    worst = None
    for span, pa, pb in chunks(top, gold):
        sa = ''.join(s for _, s in pa); sb = ''.join(s for _, s in pb)
        if crossing(pa, pb): return 'a1', 'crossing', (span, pa, pb)
        if sa != sb:
            if len(pa) != len(pb):
                fine, coarse = (pa, pb) if len(pa) > len(pb) else (pb, pa)
                hira = lambda x: sum('ぁ' <= ch <= 'ゖ' for ch in x)
                func = any(re.fullmatch('[ぁ-ゖ]+', s_) for _, s_ in fine) and hira(sa) != hira(sb)
                worst = worst or ('a2', 'nested-func' if func else 'nested-compound', (span, pa, pb))
            else:
                v = variant(sa, sb); cand = ('c', 'homophone', (span, pa, pb)) if v == 'c' else ('b', v, (span, pa, pb))
                if worst is None or (worst[0] == 'b' and cand[0] == 'c'): worst = cand
    return worst or ('d', 'same-surface?', None)
def nocross(cand, gold):
    return all(not crossing(pa, pb) for _, pa, pb in chunks(cand, gold))

stats = collections.Counter(); out = []
for sid, rs in rows.items():
    m = meta[sid]; rs.sort(key=lambda r: r['rank'])
    gidx = np.array([r['g'] for r in rs]); lab = LAB[gidx]
    rd = m['rd']
    golds = [i for i in range(len(rs)) if lab[i]]
    for sysname, ti in (('rr', int(np.argmax(S[gidx]))), ('sumire', 0)):
        stats[(m['sp'], sysname, 'n')] += 1
        if lab[ti]: continue
        top = rs[ti]
        in50 = bool(golds)
        if in50: gsegs = rs[golds[0]]['segs']; gsrc = 'sumire'
        else:
            gt = None
            for e in m['exp']:
                gt = fug(e, rd)
                if gt: break
            gsegs = [(r, s) for r, s, _ in gt] if gt else None; gsrc = 'fugashi'
        if not gsegs or ''.join(r for r, _ in top['segs']) != ''.join(r for r, _ in gsegs):
            cls, det, ch = 'd', 'align-fail', None
        else:
            cls, det, ch = classify(top['segs'], gsegs)
            if cls == 'a2':  # granularity may differ (fugashi gold vs Sumire top): re-check both at fugashi granularity
                ft, fg = fug(top['s'], rd), fug(m['exp'][0], rd)
                if ft and fg:
                    c2 = classify([(r, s) for r, s, _ in ft], [(r, s) for r, s, _ in fg])
                    if c2[0] != 'd': cls, det = c2[0], c2[1] + ('' if c2[0] == 'a2' else '(refined)')
        key = (m['sp'], sysname, 'in50' if in50 else 'out50')
        stats[key + (cls,)] += 1; stats[key + (det,)] += 1
        rec = dict(sid=sid, sp=m['sp'], sys=sysname, cls=cls, det=det, in50=in50, gsrc=gsrc, rd=rd, ctx=m['ctx'][-15:],
                   gold=m['exp'][0], top=top['s'], top_rank=top['rank'])
        if cls in ('a1', 'a2') and gsegs:
            # where is a candidate with the gold split?
            def splitok(c):
                k = classify(c, gsegs); return not (k[0] == 'a1' or k[1] == 'nested-func')
            ok = [r['rank'] for r in rs if r['segs'] and ''.join(x for x, _ in r['segs']) == rd and splitok(r['segs'])]
            rec['goldsplit_rank'] = ok[0] if ok else -1
            rec['nsplits'] = len({tuple(cuts(r['segs'])) for r in rs if r['segs']})
            (lo, hi), pa, pb = ch
            rec['chunk_rd'] = rd[lo:hi]; rec['chunk_top'] = '|'.join(s for _, s in pa); rec['chunk_gold'] = '|'.join(s for _, s in pb)
            # fugashi view of the gold chunk for POS / dictionary proxy
            gt = fug(m['exp'][0], rd) if gsrc == 'sumire' else fug(m['exp'][0], rd)
            pos = []; unseen = []; notin50 = []; p = 0
            alls = ''.join(r['s'] for r in rs)
            if gt:
                for r_, s_, ps in gt:
                    a, b = p, p + len(r_); p = b
                    if b <= lo or a >= hi: continue
                    pos.append(ps)
                    if ps.startswith('名詞') and KANJI.search(s_ + 'x') and (KANJI.search(s_) or re.search('[ァ-ヺ]', s_)):
                        if s_ not in alls: notin50.append(s_)
                        if s_ not in seen_pref: unseen.append(s_)
            rec['chunk_pos'] = ' '.join(pos); rec['unseen'] = ' '.join(unseen); rec['notin50'] = ' '.join(notin50)
            # cut position view: top cut inside a gold token (over-split) vs gold cut inside a top segment (under-split)
            ct, cg = set(cuts(pa)[:-1]), set(cuts(pb)[:-1])
            rec['over'] = len(ct - cg); rec['under'] = len(cg - ct)
            PART = {'が','の','を','に','へ','と','で','や','は','も','か','ね','よ','な','た','だ','て','し','ば'}
            frag = any((re.fullmatch('[ぁ-ゖ]{1,2}', s_) and s_ not in PART and len(pa) > len(pb)) or re.search('[ァ-ヺ][ぁ-ゖ]|[ぁ-ゖ]ー|^[ぁぃぅぇぉっゃゅょー]', s_) for _, s_ in pa)
            if unseen or notin50: pat = 'P1 rare/unregistered word (gold noun absent from top-50)'
            elif frag: pat = 'P2 kana fragments (unknown kana run cut into pieces)'
            elif re.search('助詞|助動詞|連体詞', rec['chunk_pos']): pat = 'P3 particle/auxiliary absorbed or split off'
            elif re.search('動詞|形容詞', rec['chunk_pos']): pat = 'P4 inflection boundary'
            else: pat = 'P5 compound noun / prefix-suffix boundary'
            rec['pat'] = pat
            if in50:
                rec['cost_gap'] = (rs[golds[0]]['cost'] or 0) - (rs[0]['cost'] or 0)
        out.append(rec)
os.makedirs(f'{W}/diag', exist_ok=True)
with open(f'{W}/diag/bunseg_{TAG}.jsonl', 'w') as fo:
    for r in out: fo.write(json.dumps(r, ensure_ascii=False) + '\n')
json.dump({'|'.join(k): v for k, v in stats.items()}, open(f'{W}/diag/bunseg_{TAG}_stats.json', 'w'), ensure_ascii=False, indent=0)
for sp in ('tune', 'held', 'eval'):
    for sysname in ('rr', 'sumire'):
        n = stats[(sp, sysname, 'n')]; line = [f'{sp:5s} {sysname:6s} n={n}']
        for cls in ('a1', 'a2', 'b', 'c', 'd'):
            i, o = stats[(sp, sysname, 'in50', cls)], stats[(sp, sysname, 'out50', cls)]
            line.append(f'{cls}={i + o}({i}/{o})')
        print(' '.join(line))
print('wrote', f'{W}/diag/bunseg_{TAG}.jsonl', len(out))

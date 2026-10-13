#!/usr/bin/env python3
"""bdiag.py CANDS META [CANDS2] : Sumire top1 の文節境界ミスを型別に数える（daily / 口語セット用）。
CANDS = SumireNbestDumpTest の cands.*.tsv（id rank surface type cost .. .. segs(reading:surface を \\x1f 区切り)）
META  = id \\t split \\t ctx \\t reading \\t gold(|別解) の TSV。CANDS2 を渡すと 2 条件の比較（改善/悪化）も出す。
境界ミス = bunseg.py と同じ定義（a1 交差 + a2 nested-func）。正解の区切りは正解行が top-50 にあれば Sumire の区切り、無ければ fugashi。
型（最初の境界ミス chunk の正解側表記で判定、上から優先）:
  K1 かな口語語の漢字分割: 正解 chunk が全部かな（ー・っ含む）で top1 chunk に漢字（すげー→酢|毛|ー 型）
  K2 長音ー/小さいっ・ぁぃぅぇぉ 末尾: 正解 chunk に ー・っ・小書き（K1 以外）
  K3 カタカナ未知語: 正解 chunk にカタカナ（K1/K2 以外）
  K4 助詞・助動詞の吸収/切り出し: 正解 chunk の fugashi 品詞に 助詞/助動詞
  K5 その他（複合語・活用など）"""
import sys, os, re, glob, collections, unicodedata, json
import fugashi
TG = fugashi.Tagger()
K2H = lambda s: ''.join(chr(ord(c) - 0x60) if 'ァ' <= c <= 'ヶ' else c for c in s)
norm = lambda x: unicodedata.normalize('NFKC', x).replace('ゔ', 'ぶ')
KANJI = re.compile(r'[一-龥々〆ヵヶ]')
KANA = re.compile(r'^[ぁ-ゖァ-ヺー・]+$')


def load_meta(fn):
    m = {}
    for l in open(fn, encoding='utf-8'):
        f = l.rstrip('\n').split('\t')
        if len(f) >= 5 and f[3]: m[f[0]] = dict(sp=('d' if f[0][0] == 'd' and f[1] in ('tune', 'held') else '') + f[1], rd=f[3], exp=f[4].split('|'))
    return m


def load_cands(pat, meta):
    rows = collections.defaultdict(list)
    for fn in sorted(glob.glob(pat)):
        for l in open(fn, encoding='utf-8'):
            f = l.rstrip('\n').split('\t')
            if f[0] not in meta or len(f) < 8 or not f[4]: continue
            segs = [tuple(x.split(':', 1)) for x in f[7].split('\x1f')] if f[7] else []
            rows[f[0]].append(dict(rank=int(f[1]), s=f[2], segs=segs))
    for v in rows.values(): v.sort(key=lambda r: r['rank'])
    return rows


def fug(surface, reading):
    toks = []
    for w in TG(surface):
        k = w.feature.kana if getattr(w.feature, 'kana', None) and w.feature.kana != '*' else w.surface
        toks.append((K2H(k), w.surface, f'{w.feature.pos1}-{w.feature.pos2}'))
    if norm(''.join(t[0] for t in toks)) != norm(reading): return None
    return toks


def cuts(segs):
    p = 0; out = []
    for r, _ in segs: p += len(r); out.append(p)
    return out


def chunks(a, b):
    ca, cb = cuts(a), cuts(b); common = sorted(set(ca) & set(cb)); out = []; ia = ib = 0; lo = 0
    for c in common:
        pa = []; pb = []
        while ia < len(a) and ca[ia] <= c: pa.append(a[ia]); ia += 1
        while ib < len(b) and cb[ib] <= c: pb.append(b[ib]); ib += 1
        out.append(((lo, c), pa, pb)); lo = c
    return out


def boundary_chunk(top, gold):
    """first chunk that is a boundary error (a1 crossing or a2 nested-func), else None"""
    for span, pa, pb in chunks(top, gold):
        sa = ''.join(s for _, s in pa); sb = ''.join(s for _, s in pb)
        if len(pa) > 1 and len(pb) > 1 and sa != sb: return span, pa, pb
        if sa != sb and len(pa) != len(pb):
            fine = pa if len(pa) > len(pb) else pb
            hira = lambda x: sum('ぁ' <= ch <= 'ゖ' for ch in x)
            if any(re.fullmatch('[ぁ-ゖ]+', s_) for _, s_ in fine) and hira(sa) != hira(sb): return span, pa, pb
    return None


def ktype(span, pa, pb, gfug, rd):
    sb = ''.join(s for _, s in pb); sa = ''.join(s for _, s in pa)
    if KANA.match(sb) and KANJI.search(sa): return 'K1'
    if re.search('[ーっッぁぃぅぇぉゃゅょァィゥェォャュョ]', sb): return 'K2'
    if re.search('[ァ-ヺ]', sb): return 'K3'
    lo, hi = span; pos = []; p = 0
    for r_, s_, ps in (gfug or []):
        a, b = p, p + len(r_); p = b
        if b > lo and a < hi: pos.append(ps)
    if any(re.match('助詞|助動詞', x) for x in pos): return 'K4'
    return 'K5'


def analyse(meta, rows):
    res = {}
    for sid, rs in rows.items():
        m = meta[sid]; rd = m['rd']; top = rs[0]
        ok = norm(top['s']) in {norm(e) for e in m['exp']}
        g = [r for r in rs if norm(r['s']) in {norm(e) for e in m['exp']}]
        gf = None
        for e in m['exp']:
            gf = fug(e, rd)
            if gf: break
        gsegs = g[0]['segs'] if g else ([(r, s) for r, s, _ in gf] if gf else None)
        rec = dict(sp=m['sp'], ok=ok, in50=bool(g), top=top['s'], gold=m['exp'][0], rd=rd, bnd=False)
        if not ok and gsegs and ''.join(r for r, _ in top['segs']) == ''.join(r for r, _ in gsegs):
            ch = boundary_chunk(top['segs'], gsegs)
            if ch:  # granularity may differ (Sumire vs fugashi): re-check both at fugashi granularity
                ft = fug(top['s'], rd)
                if ft and gf:
                    ch2 = boundary_chunk([(r, s) for r, s, _ in ft], [(r, s) for r, s, _ in gf])
                    ch = ch2
            if ch:
                span, pa, pb = ch
                rec.update(bnd=True, typ=ktype(span, pa, pb, gf, rd), ctop='|'.join(s for _, s in pa),
                           cgold='|'.join(s for _, s in pb))
        elif not ok: rec['align_fail'] = True
        res[sid] = rec
    return res


def summary(res, title):
    print(f'== {title}')
    sps = sorted({r['sp'] for r in res.values()})
    for sp in sps:
        rr = [r for r in res.values() if r['sp'] == sp]; n = len(rr)
        acc = sum(r['ok'] for r in rr); b = [r for r in rr if r['bnd']]
        tc = collections.Counter(r['typ'] for r in b)
        print(f'  {sp:6s} n={n} top1={acc / n:.3f} ({acc}) bnd={len(b)} ' + ' '.join(f'{k}={tc[k]}' for k in ('K1', 'K2', 'K3', 'K4', 'K5'))
              + f'  align_fail={sum(1 for r in rr if r.get("align_fail"))}')


if __name__ == '__main__':
    meta = load_meta(sys.argv[2])
    A = analyse(meta, load_cands(sys.argv[1], meta)); summary(A, sys.argv[1])
    if len(sys.argv) > 3:
        B = analyse(meta, load_cands(sys.argv[3], meta)); summary(B, sys.argv[3])
        for sp in sorted({r['sp'] for r in B.values()}):
            ids = [i for i in A if i in B and A[i]['sp'] == sp]
            imp = [i for i in ids if B[i]['ok'] and not A[i]['ok']]; wor = [i for i in ids if A[i]['ok'] and not B[i]['ok']]
            bi = [i for i in ids if A[i]['bnd'] and not B[i]['bnd']]; bw = [i for i in ids if B[i]['bnd'] and not A[i]['bnd']]
            print(f'  {sp}: top1 +{len(imp)} -{len(wor)}  bnd fixed {len(bi)} new {len(bw)}')
            for i in wor[:8]: print(f'     - {i} {A[i]["top"][:30]} -> {B[i]["top"][:30]} (gold {A[i]["gold"][:30]})')
            for i in imp[:8]: print(f'     + {i} {A[i]["top"][:30]} -> {B[i]["top"][:30]}')
    if os.environ.get('BD_EX'):
        k = int(os.environ['BD_EX'])
        for t in ('K1', 'K2', 'K3', 'K4', 'K5'):
            ex = [(i, r) for i, r in A.items() if r['bnd'] and r['typ'] == t]
            print(f'-- {t} examples ({len(ex)})')
            for i, r in ex[:k]: print(f'   {i} {r["sp"]} [{r["ctop"]}] vs [{r["cgold"]}]  top={r["top"][:40]}  gold={r["gold"][:40]}')
    if os.environ.get('BD_OUT'):
        json.dump(A, open(os.environ['BD_OUT'], 'w'), ensure_ascii=False)

"""Shared helpers for the SUMIRE-LM scripts."""
ROWK = ('rank', 's', 'type', 'cost', 'lid', 'rid', 'nseg', 'wt', 'pos', 'nc', 'bos')
def rowdict(t):
    r = dict(zip(ROWK, t)); r['wt'] = r['wt'].split(' ') if r['wt'] else []; r['pos'] = r['pos'].split(' ') if r['pos'] else []
    return r
CONTENT = ('名詞', '動詞', '形容詞', '副詞', '形状詞')
def content(r, lo, hi):
    """content words of row dict r between token lo and hi (numerals excluded)"""
    return {w for w, p in zip(r['wt'][lo:hi], r['pos'][lo:hi]) if p.split('-')[0] in CONTENT and p != '名詞-数詞'}

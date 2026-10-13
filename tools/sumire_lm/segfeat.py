#!/usr/bin/env python3
"""segfeat.py feats|train : split (bunsetsu-boundary) features for the linear reranker, from the Sumire segments in the dump.
  feats -> $W/segfeat.npz (rows aligned with ftab.npz / cands order):
    sg_frag   segments that are 1-2 char hiragana and not a common particle/auxiliary (kana fragments)
    sg_mix    segments mixing katakana and hiragana, or starting with a small kana / long-vowel mark (ヴォ|っ|ファ, が|ー)
    sg_one    segments whose reading is a single char
    sg_share  log share of top-50 rows with exactly the same cut set (split 'posterior' by count)
    sg_wshare log of softmax(-cost/500) mass of rows with the same cut set
    sg_top    same cut set as Sumire rank 0
    sg_dseg   nseg - min nseg in the list
  train -> lin base+lmw+lmc+pmi (+ seg) on JWTD tune (l2 by inner 2-fold CV on tune, as rr.py), prints tune(CV)/held/eval
           and boundary-error counts of the new top1 (bunseg-style crossing test against the gold row, gold in top-50 only)."""
import sys, os, glob, re, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
W = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm')
PART = {'が', 'の', 'を', 'に', 'へ', 'と', 'で', 'や', 'は', 'も', 'か', 'ね', 'よ', 'な', 'た', 'だ', 'て', 'し', 'ば', 'から', 'まで',
        'より', 'など', 'こと', 'もの', 'ため', 'です', 'ます', 'ない', 'れる', 'られ', 'せる', 'いる', 'ある', 'する', 'して', 'した', 'では', 'には', 'とは'}
NAMES = ['sg_frag', 'sg_mix', 'sg_one', 'sg_share', 'sg_wshare', 'sg_top', 'sg_dseg']
def feats():
    out = []; grp = []
    def flush():
        if not grp: return
        cs = [r[0] for r in grp]; costs = np.array([r[1] for r in grp], float)
        w = np.exp(-(costs - costs.min()) / 500.0); from collections import Counter, defaultdict
        cnt = Counter(cs); mass = defaultdict(float)
        for c, x in zip(cs, w): mass[c] += x
        ns = np.array([len(c) for c in cs]); mn = ns.min()
        for (c, cost, fr, mx, one), n in zip(grp, ns):
            out.append((fr, mx, one, np.log(cnt[c] / len(grp)), np.log(mass[c] / w.sum()), float(c == cs[0]), n - mn))
        grp.clear()
    last = None
    for fn in sorted(glob.glob(f'{W}/cands/cands.*.tsv')):
        for l in open(fn):
            f = l.rstrip('\n').split('\t')
            if f[0] != last: flush(); last = f[0]
            segs = [tuple(x.split(':', 1)) for x in f[7].split('\x1f')] if len(f) >= 8 and f[7] else []
            p = 0; cut = []
            for r, _ in segs: p += len(r); cut.append(p)
            fr = sum(bool(re.fullmatch('[ぁ-ゖ]{1,2}', s)) and s not in PART for _, s in segs)
            mx = sum(bool(re.search('[ァ-ヺ][ぁ-ゖ]|[ぁ-ゖ][ァ-ヺ]|^[ぁぃぅぇぉっゃゅょゎァィゥェォッャュョーゝゞ]', s)) for _, s in segs)
            one = sum(len(r) == 1 for r, _ in segs)
            grp.append((tuple(cut), int(f[4]) if f[4] else 0, fr, mx, one))
    flush()
    np.savez(f'{W}/segfeat.npz', X=np.array(out, np.float32), names=np.array(NAMES)); print('rows', len(out))
def train():
    import rr
    T = rr.load(); z = np.load(f'{W}/segfeat.npz'); assert len(z['X']) == len(T['G'])
    T['X'] = np.concatenate([T['X'], z['X'].astype(np.float64)], 1); T['names'] = T['names'] + list(z['names'])
    base = rr.dense_names(T, ['base', 'lmw', 'lmc', 'pmi'])
    # boundary-error proxy on groups with gold in top-50: chosen row's cut set has a crossing with the gold row's cut set
    cuts = []
    for fn in sorted(glob.glob(f'{W}/cands/cands.*.tsv')):
        for l in open(fn):
            f = l.rstrip('\n').split('\t'); p = 0; c = []
            for x in (f[7].split('\x1f') if len(f) >= 8 and f[7] else []):
                p += len(x.split(':', 1)[0]); c.append(p)
            cuts.append(frozenset(c))
    G = T['G']
    def cross(a, b):  # some segment of a partially overlaps some segment of b
        A = sorted(a); B = sorted(b); sa = [0] + A; sb = [0] + B
        segA = list(zip(sa[:-1], A)); segB = list(zip(sb[:-1], B))
        return any(x0 < y0 < x1 < y1 or y0 < x0 < y1 < x1 for x0, x1 in segA for y0, y1 in segB)
    gold_row = np.full(T['ng'], -1); idx = np.nonzero(T['LAB'])[0]; gold_row[G[idx][::-1]] = idx[::-1]
    def report(name, s):
        a = rr.acc_by_split(s, T); o = np.lexsort((-s, G)); f = o[rr.group_first(G[o])]
        be = {}
        for sp in ('tune', 'held', 'eval'):
            m = np.nonzero((T['gsp'] == sp) & (gold_row >= 0))[0]
            be[sp] = sum(cross(cuts[f[g]], cuts[gold_row[g]]) for g in m if not T['LAB'][f[g]])
        print(f"{name:40s} tune {a['tune']:.3f} held {a['held']:.3f} eval {a['eval']:.3f} | crossing errors (gold in top-50) tune {be['tune']} held {be['held']} eval {be['eval']}", flush=True)
    report('Sumire top1', -rr.col(T, 'rank'))
    for nm, names in (('lin base+lmw+lmc+pmi', base), ('+ seg features', base + NAMES)):
        s, w, l2 = rr.linear(T, names, ['tune'])
        report(f'{nm} (l2={l2})', s)
        if nm.startswith('+'): print('  seg weights', ' '.join(f'{n}:{v:+.3f}' for n, v in zip(names, w) if n.startswith('sg_')))
        # inner 2-fold CV accuracy on tune (the number used for the decision)
        X = np.stack([rr.col(T, n) for n in names], 1); tr = rr.train_rows(T, ['tune']); mu = X[tr].mean(0); sd = X[tr].std(0) + 1e-9
        Xs = (X - mu) / sd; acc = []
        for k in range(2):
            ww = rr.softmax_train(Xs, G, T['LAB'], rr.train_rows(T, ['tune'], (k, 2)), l2)
            vr = rr.train_rows(T, ['tune'], (1 - k, 2)); acc.append(rr.acc_by_split_rows(Xs @ ww, T, vr))
        print(f'  tune 2-fold CV acc {np.mean(acc):.4f} (over tune groups with gold in top-50)', flush=True)
if __name__ == '__main__':
    {'feats': feats, 'train': train}[sys.argv[1]]()

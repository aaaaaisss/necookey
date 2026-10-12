#!/usr/bin/env python3
"""lcost.py : 案2「Sumire のラティスのコストを識別学習する」(local lattice cost corrections).
Corrections are local to the lattice: per word (reading:surface) and per adjacent word pair (incl. BOS/EOS), added to the
Sumire path cost, so the on-device search cost stays the same (one table lookup per node / edge).
Learned by an averaged (margin) structured perceptron as a re-ranking of the existing top-50 dumps.
Targets: the adopted reranker's top1 (teacher = lin base+lmw+lmc+pmi, trained on JWTD tune) on the CC-100 auto clauses
(distillation; no gold of auto is used unless tgt=autogold), optionally + JWTD tune gold. Hyper-parameters chosen on JWTD tune.
JWTD held / AJIMEE (split eval) are reported only.

lcost.py prep                      -> $WORK/lcost/prep.npz, keys.pkl
lcost.py train k=v ...             -> prints tune/held/eval top1; params: tgt=teacher|autogold|teacher+tune|tune
                                      eta, margin, ep, minc (min #groups a feature occurs in, on the training groups), big=0/1,
                                      uni=0/1, save=NAME (-> $WORK/lcost/NAME.npz: keys + corrections)
lcost.py export NAME OUT.tsv [q]   -> text table for the Kotlin loader (see LatticeCostCorrection.kt), q = cost quantum
lcost.py student NAME DIR          -> DIR/cands.*.tsv-like eval of a new dump not needed; see HANDOFF."""
import sys, os, glob, pickle, numpy as np
WORK = os.environ.get('SLM_WORK', '/workspace/work/sumire_lm'); O = f'{WORK}/lcost'
os.makedirs(O, exist_ok=True)
mode = sys.argv[1]
BOS, EOS = 0, 1

def load_segs(pattern):
    segs = {}
    for fn in sorted(glob.glob(pattern)):
        for l in open(fn, encoding='utf-8'):
            f = l.rstrip('\n').split('\t')
            if len(f) >= 8 and f[4] != '' and int(f[1]) < 50: segs[(f[0], int(f[1]))] = (int(f[4]), f[7])
    return segs

if mode == 'prep':
    z = np.load(f'{WORK}/ftab.npz', allow_pickle=True)
    teach = np.load(f'{WORK}/scores/lin_base+lmw+lmc+pmi_tune.npy')
    items = pickle.load(open(f'{WORK}/items.pkl', 'rb'))
    segs = load_segs(f'{WORK}/cands/cands.*.tsv')
    wid = {'<s>': BOS, '</s>': EOS}; bid = {}
    cost = []; lab = z['LAB']; G = z['G']; spl = []; gid = []
    wf_ptr = [0]; wf = []; bf_ptr = [0]; bf = []; nmiss = 0
    row = 0
    for g, (i, sp, exp, ctx, rows) in enumerate(items):
        spl.append(sp); gid.append(i)
        for t in rows:
            rank = t[0]; c, sg = segs.get((i, rank), (int(t[3]), ''))
            cost.append(c)
            ws = [wid.setdefault(x, len(wid)) for x in sg.split('\x1f')] if sg else []
            if not ws: nmiss += 1
            seq = [BOS] + ws + [EOS] if ws else []
            wf.extend(ws); wf_ptr.append(len(wf))
            for a, b in zip(seq[:-1], seq[1:]): bf.append(bid.setdefault((a, b), len(bid)))
            bf_ptr.append(len(bf)); row += 1
        if g % 5000 == 0: print(g, len(wid), len(bid), flush=True)
    assert row == len(G)
    gp = np.r_[0, np.cumsum(np.bincount(G))]
    np.savez(f'{O}/prep.npz', cost=np.array(cost, np.int64), lab=lab, G=G, gp=gp, spl=np.array(spl), gid=np.array(gid),
             wf=np.array(wf, np.int32), wf_ptr=np.array(wf_ptr, np.int64), bf=np.array(bf, np.int32), bf_ptr=np.array(bf_ptr, np.int64),
             teach=teach)
    pickle.dump((wid, bid), open(f'{O}/keys.pkl', 'wb'))
    print('rows', row, 'words', len(wid), 'bigrams', len(bid), 'rows without segs', nmiss)

elif mode in ('train', 'eval0'):
    P = dict(a.split('=', 1) for a in sys.argv[2:])
    tgt = P.get('tgt', 'teacher'); eta = float(P.get('eta', 200)); margin = float(P.get('margin', 0)); ep = int(P.get('ep', 5))
    minc = int(P.get('minc', 2)); use_b = int(P.get('big', 1)); use_u = int(P.get('uni', 1)); seed = int(P.get('seed', 0))
    d = np.load(f'{O}/prep.npz')
    cost = d['cost'].astype(np.float64); lab = d['lab']; gp = d['gp']; spl = d['spl']; teach = d['teach']
    wf, wp, bf, bp = d['wf'], d['wf_ptr'], d['bf'], d['bf_ptr']
    nW = int(wf.max()) + 2; nB = int(bf.max()) + 1
    ng = len(gp) - 1
    hasseg = (wp[1:] - wp[:-1]) > 0
    # per-group target index (within group) for training
    def target(g, kind):
        a, b = gp[g], gp[g + 1]
        if kind == 'teacher':
            ok = hasseg[a:b]
            if not ok.any(): return -1
            s = np.where(ok, teach[a:b], -np.inf); return int(np.argmax(s))
        L = lab[a:b] & hasseg[a:b]
        return int(np.argmax(L)) if L.any() else -1
    groups = []
    for g in range(ng):
        s = spl[g]
        if s == 'auto' and tgt in ('teacher', 'teacher+tune'): groups.append((g, target(g, 'teacher')))
        elif s == 'auto' and tgt == 'autogold': groups.append((g, target(g, 'gold')))
        elif s == 'tune' and tgt in ('teacher+tune', 'tune'): groups.append((g, target(g, 'gold')))
    groups = [(g, t) for g, t in groups if t >= 0]
    # feature ids: words -> [0, nW), bigrams -> nW + [0, nB)
    def feats(g):
        a, b = gp[g], gp[g + 1]
        ids = []; cand = []
        if use_u:
            ids.append(wf[wp[a]:wp[b]]); cand.append(np.repeat(np.arange(b - a), wp[a + 1:b + 1] - wp[a:b]))
        if use_b:
            ids.append(nW + bf[bp[a]:bp[b]]); cand.append(np.repeat(np.arange(b - a), bp[a + 1:b + 1] - bp[a:b]))
        return (np.concatenate(ids) if ids else np.zeros(0, np.int64)), (np.concatenate(cand) if cand else np.zeros(0, np.int64))
    nF = nW + nB
    # feature frequency over training groups (distinct groups)
    cnt = np.zeros(nF, np.int32)
    for g, t in groups:
        f, _ = feats(g); cnt[np.unique(f)] += 1
    active = cnt >= minc; active[BOS] = active[EOS] = False
    w = np.zeros(nF); u = np.zeros(nF); step = 1
    rng = np.random.default_rng(seed)
    if mode == 'train':
        for e in range(ep):
            order = rng.permutation(len(groups)); nerr = 0
            for k in order:
                g, t = groups[k]; a, b = gp[g], gp[g + 1]
                f, c = feats(g); m = active[f]; f, c = f[m], c[m]
                s = cost[a:b] + np.bincount(c, weights=w[f], minlength=b - a)
                s = np.where(hasseg[a:b], s, np.inf)
                s2 = s.copy(); s2[t] = np.inf; p = int(np.argmin(s2))
                if s[t] + margin > s[p]:
                    nerr += 1
                    ft = f[c == t]; fp = f[c == p]
                    np.subtract.at(w, ft, eta); np.add.at(w, fp, eta)
                    np.subtract.at(u, ft, eta * step); np.add.at(u, fp, eta * step)
                step += 1
            print(f'epoch {e} updates {nerr}/{len(groups)}', flush=True)
        wa = w - u / step
    else:
        wa = w
    res = {}
    for split in ('tune', 'held', 'eval', 'auto'):
        gs = [g for g in range(ng) if spl[g] == split]
        if split == 'auto': gs = gs[:3000]
        ok = ok0 = okt = 0
        for g in gs:
            a, b = gp[g], gp[g + 1]
            f, c = feats(g); m = active[f]; f, c = f[m], c[m]
            s = cost[a:b] + np.bincount(c, weights=wa[f], minlength=b - a)
            ok += bool(lab[a + int(np.argmin(s))]); ok0 += bool(lab[a])
            okt += int(np.argmin(s)) == int(np.argmax(teach[a:b]))
        res[split] = (ok / len(gs), ok0 / len(gs), okt / len(gs))
    nz = int((wa != 0).sum()); nzu = int((wa[:nW] != 0).sum())
    print('PARAMS', P, 'nonzero', nz, 'uni', nzu, 'bi', nz - nzu)
    for k, (a, b, c) in res.items(): print(f'  {k}: student top1 {a:.3f} (sumire rank0 {b:.3f}) agree-teacher {c:.3f}')
    if 'save' in P:
        np.savez(f'{O}/{P["save"]}.npz', w=wa.astype(np.float32), nW=nW, active=active)
        print('saved', P['save'])

elif mode == 'export':
    name, out = sys.argv[2], sys.argv[3]; q = float(sys.argv[4]) if len(sys.argv) > 4 else 1.0
    s = np.load(f'{O}/{name}.npz'); w = s['w']; nW = int(s['nW'])
    wid, bid = pickle.load(open(f'{O}/keys.pkl', 'rb'))
    inv = {v: k for k, v in wid.items()}
    nu = nb = 0
    with open(out, 'w', encoding='utf-8') as f:
        for k, i in wid.items():
            if i < len(w) and i >= 2 and round(w[i] / q) != 0:
                r, s_ = k.split(':', 1); f.write(f'U\t{r}\t{s_}\t{int(round(w[i] / q) * q)}\n'); nu += 1
        for (a, b), j in bid.items():
            v = w[nW + j]
            if round(v / q) == 0: continue
            ka = inv[a]; kb = inv[b]
            ra, sa = ka.split(':', 1) if a >= 2 else ('', ka); rb, sb = kb.split(':', 1) if b >= 2 else ('', kb)
            f.write(f'B\t{ra}\t{sa}\t{rb}\t{sb}\t{int(round(v / q) * q)}\n'); nb += 1
    print('unigram', nu, 'bigram', nb, 'bytes', os.path.getsize(out))

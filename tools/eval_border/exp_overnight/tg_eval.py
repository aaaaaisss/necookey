import sys,os
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from ev2 import load_meta, load
from ev3 import judge
meta=load_meta(os.environ.get('ALL_IN','all_in.tsv'))
def setof(i):
    c=i[0]
    if c in 'ubfhc': return '179'
    if c=='r': return 'reg'
    if c=='d': return 'dia'
    return 'hard'
cfgs=sys.argv[1:]
res={c:judge(meta,load(f'tg/cands.tsv.{c}'),load(f'tg/cands.tsv.{c}.pool')) for c in cfgs}
base=res['base']
print('| 構成 | 179 exact/lenient/致命/ツギハギ | reg lenient | dia lenient/ツギハギ | hard(新規 206) lenient/致命/ツギハギ | 壊れ(179/reg/dia) | 壊れ(hard) | 直り(全) |')
print('|---|---|---|---|---|---|---|---|')
for c in cfgs:
    r=res[c]; out=[]
    def agg(s):
        rr=[v for i,v in r.items() if v and setof(i)==s]
        return len(rr),sum(v['ex'] for v in rr),sum(v['le'] for v in rr),sum(v['fatal'] for v in rr),sum(v['tg'] for v in rr)
    n,ex,le,fa,tg=agg('179'); _,_,rle,_,_=agg('reg'); _,_,dle,_,dtg=agg('dia'); hn,_,hle,hfa,htg=agg('hard')
    br=[i for i in r if r[i] and base.get(i) and base[i]['le'] and not r[i]['le']]
    fx=[i for i in r if r[i] and base.get(i) and not base[i]['le'] and r[i]['le']]
    brm=[i for i in br if setof(i)!='hard']; brh=[i for i in br if setof(i)=='hard']
    print(f'| {c} | {ex}/{le}/{fa}/{tg} | {rle} | {dle}/{dtg} | {hle}/{hfa}/{htg} | {len(brm)} {" ".join(brm)} | {len(brh)} {" ".join(brh)} | {len(fx)} {" ".join(fx)} |')

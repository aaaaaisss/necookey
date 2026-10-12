import numpy as np, pickle, unicodedata, sys
K=7; P=np.uint64(1000003)
data=pickle.load(open("need.pkl","rb"))
ctx={}
for fn in ["../data/jwtd_kc.tsv","../data/ajimee.tsv"]:
    for l in open(fn):
        f=l.rstrip("\n").split("\t"); ctx[f[0]]=f[2]
def keys(s):
    a=np.frombuffer(("\n"+s).encode("utf-32-le"),dtype=np.uint32).astype(np.uint64)
    out=[]; h=np.zeros(len(a),np.uint64)
    for k in range(1,K+1):
        h=h[:len(a)-k+1]*P+a[k-1:] if k>1 else a.copy()
        out.append(h)
    return out
need=[set() for _ in range(K)]
for i,sp,exp,rows in data:
    for r,s,c,z,t in rows:
        for k,h in enumerate(keys(unicodedata.normalize("NFKC",ctx[i]+s))): need[k].update(h.tolist())
need=[np.array(sorted(x),np.uint64) for x in need]
cnt=[np.zeros(len(x),np.int64) for x in need]
f=open(sys.argv[1],encoding="utf-8",errors="ignore"); tot=0
while True:
    txt=f.read(50_000_000)
    if not txt: break
    txt=unicodedata.normalize("NFKC",txt)
    for k,h in enumerate(keys(txt)):
        m=np.isin(h,need[k]); u,cc=np.unique(h[m],return_counts=True); cnt[k][np.searchsorted(need[k],u)]+=cc
    tot+=len(txt); print(tot,flush=True)
pickle.dump(dict(tot=tot,c=[dict(zip(n.tolist(),c.tolist())) for n,c in zip(need,cnt)]),open("charcounts.pkl","wb"))

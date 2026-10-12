import numpy as np, pickle, sys
B=21; M=(1<<B)-1
voc=pickle.load(open("voc.pkl","rb")); data=pickle.load(open("need.pkl","rb"))
ids=np.load("ids.npy",mmap_mode="r")
V=len(voc); print("V",V)
assert V<(1<<B)
def tid(w): return voc.get(w,-1)
k1=set();k2=set();k3=set()
for i,sp,exp,rows in data:
    for r,s,c,z,toks in rows:
        a=[0]+[tid(w) for w in toks]
        for j in range(len(a)):
            if a[j]<0: continue
            k1.add(a[j])
            if j>=1 and a[j-1]>=0:
                k2.add((a[j-1]<<B)|a[j])
                if j>=2 and a[j-2]>=0: k3.add((a[j-2]<<2*B)|(a[j-1]<<B)|a[j])
n1=np.array(sorted(k1),np.uint64); n2=np.array(sorted(k2),np.uint64); n3=np.array(sorted(k3),np.uint64)
p2=np.unique(n3>>np.uint64(B))  # needed bigram prefixes (for N1+(ab.))
c1=np.zeros(len(n1),np.int64);c2=np.zeros(len(n2),np.int64);c3=np.zeros(len(n3),np.int64)
fol1=[];fol2=[]  # unique continuations
N=len(ids); CH=40_000_000
for st in range(0,N-2,CH):
    x=np.asarray(ids[st:min(N,st+CH+2)]).astype(np.uint64)
    u=x[:-2]
    bi=(x[:-1]<<np.uint64(B))|x[1:]
    tri=(bi[:-1]<<np.uint64(B))|x[2:]
    for keys,need,cnt in ((u,n1,c1),(bi[:-1],n2,c2),(tri,n3,c3)):
        m=np.isin(keys,need); k,cc=np.unique(keys[m],return_counts=True)
        cnt[np.searchsorted(need,k)]+=cc
    m=np.isin(x[:-2],n1); fol1.append(np.unique(bi[:-1][m]))
    m=np.isin(bi[:-1],p2); fol2.append(np.unique(tri[m]))
    print(st,flush=True)
f1=np.unique(np.concatenate(fol1)); f2=np.unique(np.concatenate(fol2))
k,cc=np.unique(f1>>np.uint64(B),return_counts=True); nf1=dict(zip(k.tolist(),cc.tolist()))
k,cc=np.unique(f2>>np.uint64(B),return_counts=True); nf2=dict(zip(k.tolist(),cc.tolist()))
pickle.dump(dict(N=N,V=V,c1=dict(zip(n1.tolist(),c1.tolist())),c2=dict(zip(n2.tolist(),c2.tolist())),c3=dict(zip(n3.tolist(),c3.tolist())),nf1=nf1,nf2=nf2),open("counts.pkl","wb"))
print("done")

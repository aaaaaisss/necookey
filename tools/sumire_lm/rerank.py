import pickle, math, numpy as np, itertools
B=21
C=pickle.load(open("counts.pkl","rb")); voc=pickle.load(open("voc.pkl","rb")); data=pickle.load(open("need.pkl","rb"))
N,V=C["N"],C["V"]; c1,c2,c3,nf1,nf2=C["c1"],C["c2"],C["c3"],C["nf1"],C["nf2"]
D=0.75
def p1(w): return (c1.get(w,0)+0.5)/(N+0.5*V) if w>=0 else 1e-9
def p2(a,w):
    pb=p1(w)
    if a<0 or w<0: return pb
    ca=c1.get(a,0)
    if ca==0: return pb
    c=c2.get((a<<B)|w,0)
    return max(c-D,0)/ca + D*nf1.get(a,0)/ca*pb
def p3(a,b,w):
    pb=p2(b,w)
    if a<0 or b<0 or w<0: return pb
    cab=c2.get((a<<B)|b,0)
    if cab==0: return pb
    c=c3.get((a<<2*B)|(b<<B)|w,0)
    return max(c-D,0)/cab + D*nf2.get((a<<B)|b,0)/cab*pb
def lp(toks,order=3):
    a=[0]+[voc.get(w,-1) for w in toks]; s=0
    for j in range(1,len(a)):
        p=p3(a[j-2],a[j-1],a[j]) if (order==3 and j>=2) else p2(a[j-1],a[j])
        s+=math.log(p)
    return s
feats=[]
for i,sp,exp,rows in data:
    feats.append((i,sp,set(exp),[(s,c,z,lp(t),lp(t,2),len(t)) for r,s,c,z,t in rows]))
pickle.dump(feats,open("feats.pkl","wb"))
def acc(split,f):
    n=k=0
    for i,sp,exp,rows in feats:
        if (sp!=split): continue
        n+=1; best=max(rows,key=f); k+=best[0] in exp
    return k/n
for name,f in [("sumire",lambda r:-r[1]),("zenzLL",lambda r:r[2]),("tri",lambda r:r[3]),("bi",lambda r:r[4])]:
    print(name,[round(acc(s,f),3) for s in ("tune","held","eval")])
best=None
for lam in [0,0.1,0.2,0.3,0.5,0.7,1,1.5,2,3]:
  for pen in [0,0.5,1,2]:
    f=lambda r:r[3]-lam*r[1]/1000-pen*r[5]
    a=acc("tune",f)
    if best is None or a>best[0]: best=(a,lam,pen)
a,lam,pen=best; f=lambda r:r[3]-lam*r[1]/1000-pen*r[5]
print("tri+sumire lam",lam,"pen",pen,[round(acc(s,f),3) for s in ("tune","held","eval")])

import fugashi, unicodedata, numpy as np, sys, pickle
t=fugashi.Tagger('-Owakati')
voc={"<s>":0}; ids=[]; out=[]
buf=np.empty(0,np.uint32); chunks=[]
for i,l in enumerate(open(sys.argv[1])):
    l=unicodedata.normalize("NFKC",l.strip())
    if not l: continue
    a=[0]
    for w in t.parse(l).split():
        j=voc.get(w)
        if j is None: j=voc[w]=len(voc)
        a.append(j)
    ids.extend(a)
    if len(ids)>5_000_000: chunks.append(np.array(ids,np.uint32)); ids=[]
chunks.append(np.array(ids+[0],np.uint32))
np.save("/workspace/work/ime/lm/ids.npy",np.concatenate(chunks))
pickle.dump(voc,open("/workspace/work/ime/lm/voc.pkl","wb"))
print("tokens",sum(len(c) for c in chunks),"vocab",len(voc))

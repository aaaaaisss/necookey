import fugashi, unicodedata, pickle
t=fugashi.Tagger('-Owakati')
ctx={}
for fn in ["../data/jwtd_kc.tsv","../data/ajimee.tsv"]:
    for l in open(fn):
        f=l.rstrip("\n").split("\t"); ctx[f[0]]=(f[1],f[2],f[4].split("|"))
items={}
for fn in ["../zg/jw.z.cands","../zg/aj.z.cands"]:
    for l in open(fn):
        f=l.rstrip("\n").split("\t")
        if f[4]=="": continue
        items.setdefault(f[0],[]).append((int(f[1]),f[2],int(f[4]),float(f[5])))
data=[]
for i,c in items.items():
    sp,cx,exp=ctx[i]
    rows=[(r,s,cost,z,t.parse(unicodedata.normalize("NFKC",cx+s)).split()) for r,s,cost,z in c]
    data.append((i,sp,exp,rows))
pickle.dump(data,open("need.pkl","wb"))
print(len(data))

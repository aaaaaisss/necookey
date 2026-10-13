# drop corpus lines containing any eval target clause (JWTD expected/wrong, AJIMEE expected)
import ahocorasick, sys, unicodedata
A=ahocorasick.Automaton()
pats=set()
for fn,cols in [("../data/jwtd_kc.tsv",(4,5)),("../data/ajimee.tsv",None)]:
    for l in open(fn):
        f=l.rstrip("\n").split("\t")
        if cols:
            for c in cols:
                for p in f[c].split("|"): pats.add(p)
        else:
            for x in f:
                for p in x.split("|"):
                    if len(p)>=6 and any('\u4e00'<=ch<='\u9fff' for ch in p): pats.add(p)
pats={p for p in pats if len(p)>=6}
for p in pats: A.add_word(p,1)
A.make_automaton()
n=d=0
out=open(sys.argv[2],"w")
for l in open(sys.argv[1],errors="ignore"):
    n+=1
    l=l.strip()
    if not l: continue
    if next(A.iter(l),None) is not None: d+=1; continue
    out.write(l+"\n")
print("pats",len(pats),"lines",n,"dropped",d)

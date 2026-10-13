import lzma, urllib.request
SKIP=1_503_819_331; LIM=3_000_000_000
r=urllib.request.urlopen("https://data.statmt.org/cc-100/ja.txt.xz")
d=lzma.LZMADecompressor(); n=0; w=0
with open("cc2.txt","wb") as f:
    while w<LIM:
        b=r.read(1<<20)
        if not b: break
        o=d.decompress(b)
        if n+len(o)>SKIP:
            k=max(0,SKIP-n); f.write(o[k:]); w+=len(o)-k
        n+=len(o)
print(n,w)

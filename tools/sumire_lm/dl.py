import lzma, urllib.request, sys
LIM=1_500_000_000
r=urllib.request.urlopen("https://data.statmt.org/cc-100/ja.txt.xz")
d=lzma.LZMADecompressor(); n=0
with open("/tmp/cc100.txt","wb") as f:
    while n<LIM:
        b=r.read(1<<20)
        if not b: break
        o=d.decompress(b); f.write(o); n+=len(o)
open("dl.done","w").write(str(n))

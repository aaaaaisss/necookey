import sys
inp={l.split('\t')[0]:l.rstrip('\n').split('\t') for l in open(sys.argv[1])}
a={l.split('\t')[0]:l.rstrip('\n').split('\t') for l in open(sys.argv[2])}
b={l.split('\t')[0]:l.rstrip('\n').split('\t') for l in open(sys.argv[3])}
sp=sys.argv[4] if len(sys.argv)>4 else 'tune'
for k in a:
    if inp[k][1]!=sp or a[k][3]==b[k][3]: continue
    print(('+' if b[k][2]=='1' else '-' if a[k][2]=='1' else '~'), inp[k][4], '|', a[k][3], '->', b[k][3])

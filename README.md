SUMIRE-LM binary tables for CI only (statistics, no corpus text). Never merge into main / sumire-lm.
- lc_np1m.slc: lattice edge cost table (SLC2, tools/sumire_lm/lcsim.py np=1000000 cu=20 qs=7 d=0.75), sha256 1accd8c96f3df50761879e36e476f38911e9a8e29df5caabfd2dcf4a7c8a00b5
- sumire_lm_h56.zip: SUMIRE-LM reranker table h56 (typed1 v3 p25 tables + feats.tsv `sub dir=v2 pre=v2.` + v2/ = packed1 p35; 100 runtime files, 56.17 MB unpacked; extract into assets sumire_lm/), sha256 5b040b8f9401adcfefe19daf67e4717468b308fea0b9bc5e8003db68e57e8196

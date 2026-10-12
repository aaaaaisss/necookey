# SUMIRE-LM handoff (2026-10-12)

Goal (user): raise Sumire alone above zenz-v3.2-xsmall, using classical methods (n-gram, context, discriminative rerank). Datasets and corpora are NOT in the repo (CC BY-SA / Common Crawl); only scripts.

## Data you must rebuild
1. JWTD v2 (Kyoto Univ, CC BY-SA 3.0): https://nlp.ist.i.kyoto-u.ac.jp/nl-resource/JWTD/jwtd_v2.0.tar.gz
   AJIMEE-Bench: `git clone https://github.com/azooKey/AJIMEE-Bench`
   `build_jwtd.py 3000` -> `jwtd_kc.tsv` (cols: id, split(tune/held alternating), context, reading(hiragana), expected(| alternatives), wrong, target, target_wrong) and `ajimee.tsv` (same layout, split=eval). Paths inside scripts assume `/workspace/work/ime/data`; edit as needed. Needs fugashi + unidic-lite.
   AJIMEE is evaluation only (never tune on it).
2. Sumire N-best: `ZenzBench.kt` (driver, compiled with kotlinc against the app test classpath; see `fast.sh` / `printcp.gradle` for how the classpath is made) writes `jw.z.cands` / `aj.z.cands`: per sentence up to 50 rows `id, rank, surface, type, sumireCost, zenzLL, ntok` (rows with empty cost = gold/extra rows, skip). If you don't have llama.cpp, drop the zenz parts and just dump Sumire `getCandidatesWithBunsetsuSeparation` top-50 (no learn/user/UT dicts) with path cost.
   Harness alternative: app test `converter/quality/ConversionBenchHarnessTest.kt` (env NK_BENCH_IN / NK_BENCH_OUT) gives top1/top5/score.
3. Corpus: CC-100 Japanese (not Wikipedia: JWTD/AJIMEE come from Wikipedia). `dl.py` streams the first 1.5 GB of https://data.statmt.org/cc-100/ja.txt.xz (python lzma; no xz binary needed), `dl2.py` the next 3 GB. `decon.py IN OUT` drops lines containing any JWTD/AJIMEE target clause (>=6 chars; 14k of 8.4M lines dropped).

## Pipeline
- `tok.py cc.clean` -> `ids.npy` (uint32 token ids, 0 = sentence start) + `voc.pkl`. fugashi unidic-lite wakati, NFKC. 1.5 GB -> 322M tokens, V=444k, ~7 min. The 4.5 GB run was killed by a 1200 s job limit: run it in chunks with a shared vocab.
- `need.py` -> `need.pkl`: tokenizes context+candidate for every N-best row.
- `count.py` -> `counts.pkl`: counts only the 1-3grams the candidates need, plus continuation counts N1+(a·), N1+(ab·) (numpy, 40M-token chunks, 21-bit ids packed in uint64; asserts V < 2^21).
- `rerank.py`: interpolated absolute discounting (D=0.75), LM log-prob of context+candidate; fits lambda (Sumire cost weight) and length penalty on tune.
- `charcount.py`: char 1-7gram counts for candidates (tokenizer-free option for on-device). Not finished.

## Results so far (exact-match top1)
| method | JWTD tune | JWTD held | AJIMEE |
|---|---|---|---|
| Sumire top1 | .309 | .319 | .525 |
| word 2-gram rerank top-50 | .361 | .371 | .560 |
| word 3-gram rerank top-50 | .419 | .421 | .635 |
| 3-gram + 0.2*cost/1000 | .429 | .431 | .655 |
| zenz xsmall app loop (target) | .467 | .457 | .670 |
| zenz xsmall LL rerank top-50 | .491 | .494 | .735 |
| oracle recall@50 | .841 | .849 | .905 |

## Next steps
1. More data (4.5 GB, chunked), 4-gram (hash keys), Kneser-Ney, tune D.
2. Char n-gram LM rerank (finish charcount.py) and compare; easiest on device (no tokenizer).
3. Discriminative reranker trained on JWTD tune (logistic / structured perceptron): features word 3/4-gram LP, char n-gram LP, Sumire cost, context-word PMI (co-occurrence within line), POS-pair patterns, segment count, kana/kanji ratio, numeral/katakana flags.
4. Topic/cache: PMI between candidate content words and left-context words; on-device cache LM from the user's own history.
5. Lattice integration (bigram bonus on FindPath edges) to lift recall beyond top-50 (segmentation errors are mostly outside top-50).
6. On-device: pruned + quantized LM, target a few MB to ~20 MB; rerank Sumire N-best (n=50) after typing; measure latency. License: ship statistics only.

Repo notes: never delete untracked gojuon_keyboard/, qwerty_keyboard/, tenkey/; no force-push. After pushing to main, report commit hashes right away (user checks CI).

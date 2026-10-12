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

## 2026-10-12 update (Hark/necookey project)
Work data lived in /workspace/work/sumire_lm (data, cands, lm, logs, scores, models, results_v1.txt; `. env.sh` loads pylib). N-best now comes from the branch-only dump CI (f4534e5, 1118a88; artifacts kept 3 days).

| method (exact top1) | JWTD tune | JWTD held | AJIMEE |
|---|---|---|---|
| Sumire top1 | .309 | .319 | .525 |
| 3-gram 1.5GB + cost (repro) | .436 | .438 | .685 |
| 3-gram 4.5GB + cost | .471 | .467 | .720 |
| 4-gram 4.5GB (abs. discount D=.9, KN unigram) + cost | .473 | .471 | .680 |
| char 7-gram (stupid backoff) + cost | .441 | .457 | .690 |
| linear listwise: structure + word LM | .469 | .478 | .725 |
| + char LM | .486 | .499 | .735 |
| **+ PMI (adopted, non-lexical: lin_base+lmw+lmc+pmi_tune)** | .513 | **.511** | **.760** |
| LightGBM lambdarank (tune) | .529 | .487 | .715 |
| linear trained on auto / tune+auto | .49 | .489-.490 | .755 |
| linear + sparse POS | .516 | .515 | .745 |
| zenz app loop (target) | .467 | .457 | .670 |
| zenz LL rerank | .491 | .494 | .735 |
| oracle@50 | .841 | .849 | .905 |

- AJIMEE never used for training/tuning (weights on JWTD tune, l2 by inner 2-fold CV, lambda/length on tune). Lexical features (held up to .545) rejected by the leader: overfit risk.
- Errors (adopted model): held 226/734 (31%) have gold outside top-50 (AJIMEE 19/48); most of the rest are orthographic variants (事/こと, 出来ない/できない, 製作/制作).
- Size estimate (extrapolated): word 4-gram pruned ~4.6M entries ~25MB, char 7-gram ~6M ~30MB, PMI pairs>=500 1.7M ~9MB. Needs stronger pruning for a few MB-20MB.

### Still to do (leader's on-device checks)
1. Finish Sumire-segmentation version (no morphological analyzer): `SLM_PAIR=_seg cooc.py count 7`, `count 8`, `merge 0-8`; then `SLM_WKIND=ws SLM_PAIR=_seg SLM_ITEMS=_seg rr.py feats seg`; `SLM_FTAB=seg rr.py lin base+lmw+lmc+pmi tune`.
2. Pruning sweep: `SLM_PRUNE=... rr.py feats`, retrain on tune, accuracy vs size, for both fugashi and Sumire-seg versions.
3. Latency: `export_bench.py` with the chosen pruning, then `java -jar bench/bench.jar` (median/p95 ms after typing stops; sandbox CPU is faster than a phone); check agreement with Python picks.
4. Next ideas: bigram bonus on FindPath edges to recover gold outside top-50; more corpus (cc_more.txt 6.6GB prepared).

## 2026-10-12 on-device evaluation (size / accuracy / Sumire-seg / latency)
**AJIMEE was never used for training or for any choice** (weights, l2, quantization bits, fingerprint width, cutoffs, budget split,
PMI K, char order): everything was selected on JWTD tune (l2 by inner 2-fold CV on tune); JWTD held and AJIMEE are reported only.

### Representation simulated (odsim.py)
- ARPA backoff tables built from the same interpolated models as before (word 4-gram abs. discount D=.75 + KN unigram; char n-gram
  Dirichlet d=2): p(w|h) = P[hw] if stored else BOW[h]*p(w|h'). Feature set reduced to what one table gives
  (w4/w3/w2 by order truncation, wsb, woov, whit1-4, c5/c3, csb, chit1-5, PMI x4, base x12). Unpruned float reference reproduces the
  adopted model: fugashi tune .513 / held .508 / AJIMEE .760 (orig .513/.511/.760); Sumire-seg .501 / .496 / .750.
- Pruning = **count cutoffs per order** (not entropy/Stolcke: exact entropy pruning needs the full 4.5GB n-gram tables, too expensive
  here), with **exact backoff renormalization** from follower statistics (follow.py: S_t(h), N_t(h) over the full corpus).
- Keys: bucketed hashing, load 8/bucket, fingerprint b bits + ~2 bits/entry directory (int32 per 256 buckets + uint16 per bucket).
  Values: 1-D k-means codebook per order and kind (logprob, backoff, word-unigram log-count, pair log1p-count).
  Word keys are string hashes on device -> no vocabulary strings needed (the bench still uses a HashMap for ids).
- Sizes = full-corpus entry counts (lm/ngsize_*.json power-law extrapolation; PMI from piece-0 pair counts scaled x9 -> od/pmi_size_*.json).
- PMI pruning: count floor + keep a pair only if in the top-K partners (by PMI) of either word (pmitopk.py).

### Single-axis results (fugashi, unpruned tables)
| setting | tune | held | AJIMEE |
|---|---|---|---|
| float, exact keys | .513 | .508 | .760 |
| q8 / q5 / q4 / q3 values | .510/.506/.511/.501 | .505/.506/.503/.507 | .740/.760/.750/.765 |
| q4 + fp16 / 14 / 12 / 10 / 8 | .509/.509/.509/.499/.487 | .501/.499/.502/.493/.483 | .750/.750/.755/.745/.725 |
-> q=4 and fp=12 chosen on tune. False-hit rate of absent keys (empirical, real table in export_od.py; = analytic 8/2^b):
fp12 0.19-0.20 %, fp14 0.045-0.05 %, fp16 0.012 %.
- Char 7 vs 5: unpruned float c7 .513 vs c5 .509 (tune); along the greedy pruning paths (single-LM tune proxy) c5 is equal or better at
  every size (c7 85MB .429 vs c5 89MB .435). **5-gram is enough**; c7 dropped.
- Count-cutoff pruning hurts much more than expected: the discriminative evidence is in rare n-grams (bigram cutoff 50 alone:
  w4 proxy .473 -> .373). Greedy per-order paths (greedy.py, tune proxy = single LM + cost): od/greedy_{w4,c5,c7}_{fug,seg}.jsonl.

### Budgets (q4, fp12; split word/char/PMI chosen on tune among 3 candidates each)
| budget | config (cutoffs per order) | variant | tune | held | AJIMEE | total MB | keys | values | PMI |
|---|---|---|---|---|---|---|---|---|---|
| 20MB | w 5,5,200,200; c5 1,20,200,4000,4000; PMI floor50 top64 | fugashi | .424 | .410 | .645 | 20.2 | 11.8 | 6.7 | 1.8 |
| | | Sumire-seg | .407 | .400 | .610 | 20.2 | 11.8 | 6.7 | 1.8 |
| 10MB | w 5,100,500,1000; c5 1,20,100,4000,4000; PMI floor50 top16 | fugashi | .391 | .387 | .595 | 9.8 | 5.7 | 3.3 | 0.8 |
| | | Sumire-seg | .383 | .385 | .595 | 9.8 | 5.7 | 3.3 | 0.8 |
| 5MB | w 5,500,2000,4000; c5 1,20,500,4000,4000; PMI floor50 top8 | fugashi | .362 | .359 | .535 | 4.9 | 2.8 | 1.7 | 0.5 |
| | | Sumire-seg | .356 | .356 | .550 | 4.9 | 2.8 | 1.7 | 0.5 |
Same cutoffs with 8/5-bit values (fugashi; size grows, cutoffs not re-fitted):
20MB-config q8 .420/.419/.645 27.3MB (vals 13.3), q5 .416/.419/.650 22.0MB; 10MB-config q8 .385/.381/.610 13.3MB, q5 .381/.384/.600 10.7MB;
5MB-config q8 .360/.363/.550 6.7MB, q5 .357/.362/.540 5.4MB. -> 4 bits loses nothing measurable.
Reference: Sumire top1 held .319 / AJIMEE .525; unpruned adopted model .511/.760. Even 20MB keeps only about half of the gain.
Seg mismatch: candidate words use Sumire's split, corpus counts are fugashi tokens; costs ~1 pt (unpruned .496 vs .508 held).

### Latency (bench/OdBench.kt on export_od.py tables of the real size incl. filler, Sumire-seg, top-50, single thread, JDK17, 6 rounds)
| budget | median ms | p95 ms | in-memory tables | held / AJIMEE (Kotlin) | agreement with Python picks |
|---|---|---|---|---|---|
| 20MB | 0.73 | 1.06 | 31 MB (16-bit fp + 8-bit code containers, unpacked) | .395 / .625 | 93.9 % |
| 10MB | 0.67 | 1.01 | 15 MB | .391 / .595 | 93.1 % |
| 5MB | 0.69 | 1.03 | 7.6 MB | .359 / .550 | 95.0 % |
Disagreement comes from the false hits (Python simulates them pseudo-randomly, Kotlin hits real filler keys): with fp16 tables the
20MB agreement is 98.0 %. Load time 0.06-3.5 s here is network-FS dominated (JuiceFS); on device mmap the files.
Phone estimate (rough, NOT measured): Neoverse V2 -> Cortex-A76/A78 class ~2.5-3x slower single-thread, ART vs HotSpot and smaller
caches ~1.5x -> x3-5: median ~2-4 ms, p95 ~3-5 ms. To measure on a phone: `d8 odbench.jar`, push classes.dex + bench/od20 to
/data/local/tmp, `dalvikvm -cp classes.dex OdBenchKt ./od20 5` (see header of OdBench.kt).

### Reproduce
```
. env.sh   # WORK=/workspace/work/sumire_lm
python3 $T/odprep.py fug ; python3 $T/odprep.py seg                 # od/prep_*.npz (~15 s each)
SLM_PAIR=_seg python3 $T/cooc.py merge 0-8                          # (seg pair counts were already counted)
python3 $T/pmitopk.py count ; python3 $T/pmitopk.py flags            # PMI top-K flags + sizes
$T/folrun.sh w 4 ; $T/folrun.sh ws 4 ; $T/folrun.sh c 5 2 ; $T/folrun.sh c 7 6   # follower stats (resumable, ~15-30 min each)
python3 $T/odsim.py fug q=4 fp=12 w=5,5,200,200 c=1,20,200,4000,4000 pf=50 pk=64 tag=f20C   # one setting (~40 s)
$T/odqueue.sh QUEUE.txt                                               # list of settings, resumable
python3 $T/greedy.py w 4 fug                                          # greedy cutoff path (tune proxy)
python3 $T/export_od.py bench/od20 seg q=4 fp=12 <cutoffs> ; kotlinc bench/OdBench.kt -include-runtime -d odbench.jar
java -XX:ActiveProcessorCount=1 -jar odbench.jar bench/od20 6
```
Results: od/results.jsonl (every run), od/sweep.log. Shell jobs in this sandbox: 120 s limit per command; long jobs run with nohup
and are resumable (background jobs get killed occasionally).

### Skipped / simplified (rate limit, leader-approved)
- No full grid: one axis at a time; budget split chosen among 3 hand-picked candidates per budget from the greedy paths.
- Sumire-seg uses the fugashi-selected cutoffs (its own greedy word path od/greedy_w4_seg.jsonl was computed but not used for re-selection);
  seg not run at 8/5 bits; 8/5-bit rows keep the 4-bit cutoffs (over budget) instead of re-fitting.
- fp width chosen on unpruned tables only (not re-checked at each budget); entropy (Stolcke) pruning not done; c7 path stopped early.
- Backoff renormalization is exact; the n-gram and PMI table sizes are extrapolations (power law / scaled sample).

### Recommendation
If the LM must fit <=20MB, use the 20MB config (q4, fp12, char 5-gram, PMI floor50 top64): held .41/.40 (fugashi/seg) vs Sumire .319,
latency ~1 ms here (~3-5 ms phone est.). Accuracy drops steeply with count cutoffs; spending ~40-60MB, or a smarter pruning, is the way
to keep most of the .51 gain.

### Next steps
1. **Next priority (user): reduce bunsetsu-segmentation errors** (hardest for users to fix): classify errors into bunsetsu-boundary errors /
   orthographic variants / other, break down the gold-outside-top-50 cases, extend the segmentation N-best (more boundary variants into the list).
2. Task-aware pruning: keep the n-grams that actually appear in Sumire N-best lists for corpus readings (auto clauses) or that change the
   rerank decision, instead of count cutoffs; entropy pruning as a baseline.
3. Bloom-filter/compressed-trie style keys (~10 bits/n-gram) to fit more rare n-grams; re-select fp per budget.
4. Real-device latency with the bench (adb) and mmap loading.

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

## 2026-10-12 bunsetsu-boundary error diagnosis (bunseg.py / bunsum.py / recomb.py / segfeat.py / splitguard.py)
All judgments on JWTD tune only; held / AJIMEE(eval) are counts for reference. No new dumps: uses cands/*.tsv (Sumire top-50 with
segments `reading:surface`), ftab.npz, scores/lin_base+lmw+lmc+pmi_tune.npy. Outputs in $W/diag/ (not committed).

### Method
- Gold split: Sumire's own segments of the gold row if gold is in the top-50, else fugashi(unidic-lite) tokens of the gold with their
  kana (must concatenate to the input reading). Splits compared as cut sets in reading space, chunked at common cuts, so pure
  granularity differences with the same surface are not errors.
- A boundary = a1 crossing (a top1 segment partially overlaps a gold segment) + a2 nested-func (one side absorbs / splits off a
  particle or okurigana: 毛|が vs 怪我). a2 nested-compound (大治|郎 vs 大二郎, same span, different compound granularity) counted as C.
  B variant = same split, kana/kanji, okurigana, 子ども/子供, width. C homophone = same split, different kanji (製作/制作, 一次/一時).
  Most severe chunk decides (A > C > B). Heuristic; a few A are really homophones with a different split (与し/組みし).

### Error classes (errors / 1500, in top-50 / out)
| | tune rr (adopted) | tune Sumire top1 | held rr | held Sumire | AJIMEE rr | AJIMEE Sumire |
|---|---|---|---|---|---|---|
| errors | 731 | 1037 | 734 | 1022 | 48/200 | 95/200 |
| A boundary | 45 (6.2%, 3.0% of sents) 10/35 | 89 (8.6%, 5.9%) 43/46 | 68 | 101 | 2 | 8 |
| B variant | 144 (19.7%) 114/30 | 124 108/16 | 133 | 123 | 6 | 13 |
| C homophone | 506 (69.2%) 366/140 | 794 640/154 | 497 | 759 | 30 | 62 |
| C compound split | 36 3/33 | 30 8/22 | 36 | 39 | 1 | 3 |
| D other | 0 | 0 | 0 | 0 | 9 (gold alignment failed) | 9 |
JWTD is a Wikipedia mis-conversion corpus, so it is homophone-heavy; boundary errors are 3% of sentences after the reranker (5.9% for
Sumire). The reranker already fixes 55 of Sumire's 89 tune boundary errors and introduces 11 (held: fixes 56, introduces 23).

### Boundary errors, tune (rr 45 / Sumire 89)
- A candidate with the gold split exists in the top-50 for 35/45 rr errors (Sumire rank 0: 12, 1-4: 12, 5-19: 7, 20-49: 4; absent 10);
  for Sumire 79/89 (mostly rank 1-4). Distinct splits in the top-50: median 4 (FindPath MAX_BUNSETSU_SPLIT_PATTERNS = 4).
- rr out-of-top-50 boundary errors 35: gold split in top-50 for 25, of which 22 have every gold noun output somewhere in the dump
  (proxy for 'in Sumire's dictionary'); 7 contain a noun Sumire never outputs (held: 48 / 41 / 32 / 11).
- Direction (rr): crossing 25, top under-splits 17, over-splits 3.
- Patterns (rr / Sumire): P1 rare or unregistered word, gold noun absent from top-50 18/21 (劉|光世 <- 流行|性, 竜|貴妃 <- 隆起|費,
  藩|主家 <- 藩主|化); P2 kana fragments of unknown katakana words 1/12 (ヴォ|っ|ファ, が|ー, は|なし for 話); P3 particle/auxiliary absorbed or
  split off 14/24 (毛|が <- 怪我, は|行っ <- 入っ, の|度 <- 喉, で|小 <- でしょう, と|称さ <- 渡渉|さ); P4 inflection 0/1;
  P5 compound / prefix-suffix 12/31 (代替|駅 <- 大|体液, 長身|痩躯 <- 超|真相|区, 電信|機 <- 電|新規).
- Sumire cost gap gold - top1 for rr in-top-50 boundary errors: 0..3493 (median ~700).

### Cheap offline checks (all on existing data)
| check | tune | held | AJIMEE | decision |
|---|---|---|---|---|
| adopted lin (repro) | .513 (CV .5955) | .511 | .760 | |
| + 7 split features (kana fragments, mixed-script segs, 1-char segs, split share / cost-weighted share in N-best, same split as rank 0, nseg-min) | .509 (CV .5987) | .513 | .750 | not adopted (noise; in-top-50 crossing errors 2->3 tune, 8->8 held) |
| split guard (keep a split compatible with Sumire rank 0 unless margin > theta) | best theta=0 (any theta>0 lowers tune) | | | not adopted |
| N-best lattice recombination oracle (re-combine segments of the top-K by reading span) | top-50: 73/238 gold-outside sentences spellable (A 6/35, B 16/30, C 51/173); top-10: 8/238 | 66/226 (A 5/48) | 4/19 | worth building for B/C recall; little for A |
-> Reranker-side features cannot fix boundary errors: the in-top-50 ones are already mostly fixed; the rest need candidate generation.

### Fix proposals (estimated boundary errors fixable, tune rr baseline 45; upper bounds)
1. **Split-constrained re-conversion** (biggest lever): for each of the top-K distinct splits in the N-best (raise
   MAX_BUNSETSU_SPLIT_PATTERNS 4 -> ~8-10), take per-segment top-k words from the dictionary for that exact reading span and build the
   best combinations (Viterbi per split with the LM), append to the N-best, rerank. Upper bound 22 tune / 32 held (gold split present,
   gold nouns known), realistic ~half; also helps the 140 C + 30 B out-of-top-50 errors. Where: FindPath.backwardAStarWithBunsetsu
   (splitPatterns collection ~L1776-1860, collectBunsetsuSplitPatterns, enqueueBunsetsuSplitExpansion) and
   KanaKanjiEngine.getCandidatesWithBunsetsuSeparation (merge per-split candidates into BunsetsuCandidateResult; splitPatterns /
   splitPatternByCandidateString already flow out). This is also what the UI does when the user stretches a bunsetsu, so it can share code.
2. **Dictionary coverage**: the dump ran without UT dicts (wiki/neologd/person/places). 7 tune / 11 held boundary errors contain a noun
   Sumire never outputs (P1 total 18/20). Re-dump with UT on to measure (one CI run); proper nouns dominate.
3. **Katakana run node for unknown kana spans**: GraphBuilder step 9 adds only a 1-char node (cost 10000) when no dictionary entry ->
   unknown katakana words fragment (ヴォ|っ|ファ). Add a maximal katakana-transliteration node over spans not covered by dictionary words
   (like Mozc AddCharacterTypeBasedNodes). Sumire top1: up to 12 tune; after rr only 1 (low priority for the adopted pipeline).
4. **N-best lattice recombination** in the reranker (no dictionary calls, top-50 segments only): oracle +73 tune / +66 held gold into the
   list (A only 6/5). Needs LM Viterbi over the recombined lattice (median 576 paths at top-50).
5. Not recommended: connection-cost tweaks for P3 (particle absorption is context dependent; LM with left context already fixes most
   when in the list), split features / split guard (tested above, no gain).

### Remaining work
- Implement 1 in a branch-only experiment: dump per-split re-conversion candidates with the CI (ZenzBench-like driver), re-score with
  rr.py, measure tune; then held/AJIMEE for reporting only.
- Re-dump with UT dictionaries on (2) and re-run bunseg.py/bunsum.py.
- JWTD under-represents everyday input; a daily-use test set (chat-style clauses) would weight boundary errors more realistically.
Reproduce: `. env.sh; python3 $T/bunseg.py; python3 $T/bunsum.py; python3 $T/recomb.py; python3 $T/segfeat.py feats; python3 $T/segfeat.py train; python3 $T/splitguard.py`
(each < 2 min, 2 CPU).

## 2026-10-12 修正案1「区切りを固定した再変換」の実装と評価（結論: 効果なし、既定無効のまま）
判断は JWTD tune のみ（K・m・F の選択、重み、l2 は tune / tune 内 2-fold CV）。held / AJIMEE は報告のみ、AJIMEE は一切の選択に未使用。

### 実装（既定動作・表示順は不変）
- `path_algorithm/SplitReconversion.kt`: `SplitReconversionConfig(maxPatterns=K, wordsPerSpan=m, perPattern=N, perPatternForced=F, popBudget)`,
  `SplitReconversionCandidate`（候補・読み区間・区切り順位・語順位の最大・N-best 由来か・forced か）, `SplitReconversionResult`（受け皿 + elapsedNs）。
- `FindPath.splitConstrainedReconversion(graph(枝刈り前), length, cm, nBestPatterns, exclude, config, out)`:
  1. 区切り = N-best の出現順の区切り + 文節区切りだけを状態に持つ A*（(node, 区切り suffix) で重複除去、正確な前向き Viterbi をヒューリスティックに）で上位 K 種類。
     `MAX_BUNSETSU_SPLIT_PATTERNS`(=4, UI の区切り候補) は変えていない（UI の挙動を変えないため別経路で K を取る）。
  2. 区切りごとに「区切りをまたぐ語なし・区切り位置では自立語で始まり・それ以外の位置で自立語が始まらない」ノードだけ、各スパンは辞書上位 m 語
     （adjustedScore 順、同表記同順位）に制限して前向き Viterbi → 後ろ向き A*（連接 + 語コスト + NgramRuleScorer + Mozc 境界チェック）で N-best 外の上位 N 文字列（A）。
  3. （任意 F>0）区切り固定の前向き/後ろ向き Viterbi で「各ノードを必ず通る最良経路」をコスト順に F 本（1 語入れ替え候補、F）。
- `KanaKanjiEngine.getCandidatesWithBunsetsuSeparation(..., splitReconversionConfig, splitReconversionCollector)`: 両方渡したときだけ forwardDp 前に
  ノード一覧を複製（ノード共有）し、N-best 後に計算して collector に入れる。戻り値の候補は変えない（`SplitReconversionTest` で確認）。
- 文節伸縮の再変換との共通化（設計メモ）: UI で文節を伸縮した後の再変換は「区切りを 1 つ固定してスパンごとに語を選び直す」ので、
  `splitConstrainedReconversion(nBestPatterns = listOf(固定区切り), maxPatterns = 1)` の手順 2 がそのまま使える。現状の UI 経路は別実装のまま（未統合）。
- ダンプ CI: `ci/split_reconv.txt` = "K,m,N[,F]"（空 = 無効）を `NK_SPLIT_RECONV` に渡し、追加候補を rank 50.. で出力（列 9-13 = X, 区切り順位, 語順位最大,
  N-best 由来, A/F/AF）、`.time` に通常経路 / 追加処理の ms。`unit` ジョブで変換器の単体テスト（`converter.*`）を実行（両 run とも成功）。
- 評価: `srx.py`（prep / delta / combine / xflags / eval / view）, `srx_run.sh`（旧 need に無い n-gram・PMI ペアだけコーパスで数え直して合成、~15 分）。
  作業ディレクトリ $WORK/srx（v1/ に 1 回目の結果）。

### CI run（N_AUTO=0: JWTD 3000 + AJIMEE 200 のみ、8 シャード、各 ~4 分）
- 38249847524（488b190）: K=10, m=8, N=8（F なし）。
- 38252246071（556fbf9）: K=10, m=8, N=8, F=30。K'<=10, m'<=8 は区切り順位・語順位の列で絞って模擬（m は近似: 小さい m で探索し直したときに出る別候補は含まない）。

### 結果（採用リランカー = 語彙なし線形 base+lmw+lmc+pmi を各候補プールで JWTD tune に再学習。tune / held / AJIMEE）
| pool | 候補数/文 | Sumire top1 | rr | oracle@pool | l2(CV) |
|---|---|---|---|---|---|
| top-50（再計算） | 50 | .309/.319/.525 | .509/.507/.750 | .841/.849/.905 | 0.3 |
| top-50, l2=0.03 固定 | 50 | | **.513/.511/.760** | | |
| + A K=4 m=3 (v1) | 52 | 同上 | .508/.508/.750 | .841/.851/.905 | 0.3 |
| + A K=8 m=3 | 58 | 同上 | .512/.512/.760 | .841/.851/.905 | 0.03 |
| + A K=10 m=8 | 77 | 同上 | .509/.505/.750 | .845/.851/.905 | 0.3 |
| + F K=4 m=3 | 83 | 同上 | .512/.513/.760 | .842/.854/.905 | 0.03 |
| + F K=10 m=3 | 128 | 同上 | .512/.511/.760 | .842/.856/.910 | 0.03 |
| + F K=10 m=8 | 343 | 同上 | .506/.507/.755 | .851/.865/.910 | 0.3 |
| + A+F K=10 m=8 | 347 | 同上 | .505/.507/.755 | .851/.866/.910 | 0.3 |
- tune 最良は .512（A K=8 m=3 ほか同率）だが、top-50 だけでも l2=0.03 なら .513/.511/.760（既存採用モデルと同じ）。差は l2 の選び方によるもので、**追加候補による改善はない**。
  oracle の伸びも最大 +0.010 tune（+15 文 / 1500）、held +0.017、AJIMEE +0.005（+1 文）で、増えた正解もリランカーが選べていない。
- 文節境界ミス（bunsum の A、rr / Sumire、in-pool/out）: top-50 tune 45 (11/34), held 64, AJIMEE 2 → A K=8 m=3: tune 47 (11/36), held 68, AJIMEE 2 →
  A+F K=10 m=8: tune 47 (18/29), held 59, AJIMEE 2。正解区切りの候補がプールに入る文は増える（tune in-pool 11→18）が、選ばれない。Sumire top1 の A は不変（89 tune）。
- 速度（CI ランナー、JVM、1 文あたり、通常経路 = graph 構築 + N-best 50）: 通常 4.5-4.9 ms に対し追加処理 A(K10 m8 N8) 平均 99 ms（中央 93 / p90 178 / p99 249 / 最大 1.2 s）、
  A+F30 平均 116 ms（中央 112 / p90 197 / p99 283）。追加候補数 A: 平均 26/文、A+F30: 平均 298/文。現状の実装（IdentityHashMap・区切りごとの Viterbi×2・区切り探索）は端末では遅すぎる。

### なぜ効かないか（tune、正解が top-50 外の 238 文）
- 238 文中 180 文は「top-50 のどれか 1 つの候補と 1 箇所だけ違う」が、その候補は区切りごとの最良経路から 2-3 箇所違う（例 j464: 正解「後に製作が中京テレビへ移行」、
  top-50 に「あとに製作が中京テレビへ移行」(rank 42) はあるが、区切り最良経路は「あとに制作が中京テレビへ行こう」）。A（コスト順の組み合わせ）も F（最良経路から 1 語）も
  そこまで届かない。正解の語が辞書に無い例（梨紅, 征男, 関城, 加賀見, 廊坊, 太田胃散）も多い。
- 区切りの多様化（K を 4→10）では、JWTD の誤りの大半（同音異義 C）は救えず、境界誤り A は正解区切りが既に top-50 にあるものが多い（前回の診断どおり）。

### 残作業 / 次の候補
1. N-best 近傍の生成: 区切り最良経路ではなく top-k（例 10）の各候補を基準に、各文節を辞書上位語で 1 箇所だけ入れ替える（候補 × 文節 × m）。上の 180 文の多くはこれで届く見込み（未検証）。
   リランカー側で採点するので Kotlin では生成だけ（LM 不要）。
2. それでも選べない問題: プールを増やすと l2 が強くなり精度が落ちる（K10 m8 で -.003〜-.007）。追加候補を区別する特徴（由来・区切り順位・コスト差）や、プール別の学習が要る。
3. UT 辞書あり（修正案2）での再ダンプ: 正解語が辞書に無い例が多いので、再変換より先に効く可能性が高い。
4. 速度: 採用するなら区切り探索を既存 `collectBunsetsuSplitPatterns` の scratch（容量 K）に寄せ、Viterbi を配列化。
- ダンプ設定は既定（N_AUTO=24000、split_reconv 空 = 無効）に戻した（trigger は変更せず、CI は走らせていない）。
Reproduce: `$WORK/dl_art.sh RUN_ID`（$WORK/srx で実行）で artifact を $WORK/srx/cands に → `setsid nohup bash $T/srx_run.sh &` → `SRX_GRID=8:3:A,10:8:AF python3 $T/srx.py eval`
→ `python3 $T/srx.py view 8 3 A; cd $WORK/srx/view_K8_m3A && SLM_WORK=$PWD python3 $T/bunseg.py && SLM_WORK=$PWD python3 $T/bunsum.py`。

## 案2 ラティスコストの識別学習（2026-10-12、途中）
- A: 語・語対補正の構造化パーセプトロン（lcost.py）→ tune .300/held .314、不採用。
- B: 辺コストに単語bigram LM補正 λ·(−log p(w|v))+μ+ω·[OOV]（lcbig.py集計、lcfeat.py/lcsim.py評価）。top-50内並び替え見積もり、int8:
  語対80万 4.8MB tune .351/held .347/AJIMEE .545、語対100万 5.8MB .357/.351/.550（現状 .309/.319/.525）。辺ローカル上限 .407/.399/.620。
- 作業データ /workspace/work/sumire_lm/lcost/（sim3.logにRESULT）。
- 残り: 構成決定(tune)→lcsim.py export→Kotlin LatticeLmCost.kt＋FindPath.getEdgeCost（既定無効）→CIで実探索ダンプ（表の受け渡し方法は要決定）。→ 下の節で実施済み。

## 2026-10-13 案2 の実装と実探索での計測（既定無効のまま）
選択はすべて JWTD tune（構成は sim3.log の tune 結果）。held / AJIMEE は報告のみ。

### 構成と表
- 語対 1,000,000・語の min-count 20・int8 値（qs=7）・D=0.75。tune で再フィット: LAM=272.9 MU=760.5 OMEGA=-684.1（l2=1.0, CV .3581、sim3 と同じ）。
- `lcsim.py run np=1000000 cu=20 qs=7 d=0.75 export=$WORK/lcost/lc_np1m.slc` → 5,798,500 バイト（語 133,166・対 999,895、指紋衝突で 105 対を落とす）。
  sha256 `1accd8c96f3df50761879e36e476f38911e9a8e29df5caabfd2dcf4a7c8a00b5`。
- **語キーを Java String.hashCode から FNV-1a(UTF-16)+fmix32 に変更**: String.hashCode は 2 文字の CJK で 31*c1+c2 が衝突し、初回の書き出しでは
  語彙外の 10k 語が語彙内と誤認された（行の 13% でコストがずれ、held .349/AJIMEE .545）。変更後は語の衝突 0。
- 往復検証 `lcverify.py TABLE`: Kotlin と同じ式で表から全 top-50 行（1.36M 行）を採点 → シミュレーションと 99.95% の行で同一コスト、
  top1 は tune .357 / held .351 / AJIMEE .550 で一致。`lcverify.py TABLE pairs FILE` で任意の (前語, 語) の補正値を出せる。
- 表の受け渡し: 孤立ブランチ `lm-data`（コミット 63732f821b7100dc6e3b9a47ea7725818151ea84、表と README だけ。main / sumire-lm には絶対に
  マージしない）。ダンプ CI はこの固定コミットの raw URL から取得し、ワークフローに直書きした sha256 と照合、不一致なら失敗。
  （シークレット gist は GitHub コネクタに gist 権限がなく 403 だったため、ユーザー指示で孤立ブランチに変更。）

### 実装
- `path_algorithm/LatticeLmCost.kt`: `@Volatile table: Table?`（既定 null = 無効、補正 0）。`load(InputStream)` で SLC2 を読み、語と対をそれぞれ
  開番地ハッシュ（容量 = 2 倍以上の 2 冪、語 2^18・対 2^21 → int 配列 約 9MB + byte 約 2MB をヒープに持つ）に詰める。
  `correction(prev, node)`: node が EOS なら 0、prev が BOS なら p1 のみ、それ以外は対 → なければ alpha(v)·p1(w)。`rint(LAM·(−lp/QS) + MU + OMEGA·oov)`。
- `FindPath.getEdgeCost` に `+ LatticeLmCost.correction(prev, node)` を足しただけ（前向き DP・後ろ向き A*・文節・区切り固定の再変換が同じ値を使う）。
  ConstrainedPathSearch と PosPairPenalty は変更なし（Zenzai の制約付き探索には補正が入らない点に注意）。
- `LatticeLmCostTest`（5 件）: 表なし → 0 かつエンジンの候補・スコアが表の設定/解除の前後で同一、SLC2 読み込み、式（期待値は lcverify.py と同じ計算）、
  BOS/EOS、ハッシュが Python 書き出し側と一致。ローカルと CI unit ジョブで成功。
- ダンプ: `NK_LATTICE_COST=PATH` で各文を表なし / 表ありで 1 回ずつ変換（順序は文ごとに交互）、表ありを `cands.*.tsv`、表なしを `cands.*.tsv.base`、
  `.time` に id・読み長・表なし ms・表あり ms。`ci/lattice_cost.txt` = 1 で有効（既定 0）。`ci/probe_hairu.tsv` = はいる/いる確認用の手書き 30 文（split=probe）。
- 評価 `lcx.py DIR`: Sumire top1・oracle@50（split 別）、はいる→入る誤り、文節境界ミス（bunseg/bunsum を Sumire 順のスコアで実行、A = a1 + nested-func）、速度。

### 結果（CI run 38366880715 = da02112、N_AUTO=0、8 シャード。サンドボックスでのローカル実行も同じ精度）
| | Sumire top1 tune / held / AJIMEE | oracle@50 tune / held / AJIMEE | 文節境界ミス(A) tune / held / AJIMEE |
|---|---|---|---|
| 表なし | .309 / .319 / .525 | .841 / .849 / .905 | 89 / 101 / 8 |
| 表あり（5.8MB） | **.362 / .361 / .560** | .858 / .862 / .930 | **80 / 92 / 6** |
| （参考: top-50 並べ替えシミュレーション） | .357 / .351 / .550 | | |
- 実探索ではシミュレーション（top-50 内の並べ替え）より良い: top-50 外の経路も補正で上がる（oracle@50 も +.013〜+.025）。
- 速度（CI ランナー JVM、1 文あたり、graph 構築 + N-best 50 の全体）: 表なし 平均 4.64 / 中央 3.01 / p90 7.31 / p99 25.8 ms、
  表あり 平均 6.40 / 中央 4.61 / p90 10.65 / p99 34.0 ms（+38%、平均 +1.8 ms）。ローカル: 3.51 → 5.06 ms。実機は未測定。
  遅さは辺ごとの語ハッシュ計算（同じノードを何度も引く）。Node ごとに語番号をキャッシュすれば大半は消せる見込み（未実施）。
- リランカー再適用後の精度は未測定（srx_run.sh 相当の再集計が要る、~15 分。今回はスコープ外）。

### はいる→入る（「わかってはいるけど」「そうはいっても」型）
daily.tsv はこのサンドボックスにもリポジトリ（homophone-ui 含む）にも無かったため、手書きプローブ 30 文と JWTD/AJIMEE で数えた
（定義: 読みに「はい」、top1 に「入」、正解のどれにも「入」が無い）。
| | probe 30 | tune | held | AJIMEE |
|---|---|---|---|---|
| 表なし → 表あり | 2 → 2 | 3 → 3 | 1 → 1 | 0 → 0 |
- 例（両方とも不変）: わかってはいるけど → わかって入るけど、そうはいっても → そう入っても、ご趣旨はわかったと入ったが（正: とは言ったが）、
  採用して入るが（正: してはいるが）、運行自体入っている（正: 自体は行っている）。わかっている・しっている等の「ている」は表なしでも正しい。
- 理由: 補正は語ごとに MU=760 を足す（語数ペナルティ）ので、2 語「は|いる」は 1 語「入る」より不利。h01 の補正和は 入る 6277 vs は+いる 6882
  （逆方向 +605、元の差 159）。h08 は は+言っ が 175 有利になるが元の差 2459 に届かない。→ この型を直すには助詞「は」の後の補助動詞用の
  語対特徴か MU を品詞依存にする必要がある（未実施）。
- 悪化例: ともだちとはなしている → 友達と話ている（表なしは正しい）。

### 次
1. Node 単位の語番号キャッシュで速度差を縮める。2. リランカー再適用（srx_run.sh）で最終精度。3. daily.tsv（homophone-ui 担当側にある）で再計測。
4. MU の品詞依存化 / 「は+いる」型の対特徴（はいる→入る対策）。5. ConstrainedPathSearch にも補正を入れるかの判断。
Reproduce: `python3 $T/lcsim.py run np=1000000 cu=20 qs=7 d=0.75 export=OUT.slc; python3 $T/lcverify.py OUT.slc`;
CI: `ci/lattice_cost.txt`=1・`ci/n_auto.txt`=0 で trigger → `$WORK/dl_art.sh RUN`（$WORK/lcx/ci で）→ `python3 $T/lcx.py $WORK/lcx/ci/cands`。
ダンプ設定は既定（N_AUTO=24000、lattice_cost=0）に戻した（trigger は変更していないので CI は走らない）。

## 2026-10-13 ラティスコストを既定オン ＋ はいる→入る 対策（PosPairPenalty.hairuCost）
- ユーザー判断で案2（LatticeLmCost）を既定で有効に: ダンプ CI は `ci/lattice_cost.txt` = 1（表は lm-data 63732f8 から sha256 照合で取得）。
  アプリ（sumire-lm-app）はビルド時に同じ固定コミットから取得・sha256 照合（不一致でビルド失敗）してアセット `lattice_lm/lc_np1m.slc` に置き、
  IME 起動時に IO スレッドで読み込む（設定「単語のつながりで変換を補正」で無効化可、表が無ければ従来どおり）。実機で遅ければ戻す。
- はいる→入る: `PosPairPenalty.hairuCost`（`hairuPenalty`、既定 `HAIRU_PENALTY` = 8000、0 で無効。前向き DP・A*・Zenzai 制約付き探索で共通）。
  読み「はい…」の「入…」に: 接続助詞「て」(id 348)/「で」(349) の直後の「入る」、「と」「そう」「こう」「ああ」「どう」の直後の「入っ」に追加コスト。
  「入って」（熱くて入って・決して入ってみ）、格助詞「で」（車で入る）、副詞「ぐっと」「ちょっと」の後は対象外。ダンプは `NK_HAIRU=P` で上書き。
- ローカル評価（サンドボックス gradle、JWTD 3000 + AJIMEE 200 + probe 30 + daily.tsv 3000、各条件 1 回、`hcmp.py OFF_DIR ON_DIR`）。P は tune で 3000 / 8000 を比較し 8000
  （tune の はいる 誤りが 1 件多く減り、top1 は同じ）。Sumire top1:

| | JWTD tune / held | AJIMEE | daily tune / held | probe |
|---|---|---|---|---|
| 表なし・対策なし | .309 / .319 | .525 | .535 / .555 | .867 |
| 表あり・対策なし | .362 / .361 | .560 | .557 / .582 | .900 |
| 表なし・対策 8000 | .309 / .319 | .525 | .535 / .555 | .933 |
| 表あり・対策 8000（新既定） | .362 / .361 | .560 | .557 / .583 | .933 |

  はいる→入る 誤り（tune / held / AJIMEE / daily tune / daily held / probe）: 表あり 3/1/0/2/5/2 → 表あり＋対策 2/0/0/1/3/0（表なし 3/1/0/5/5/2 → 2/0/0/4/3/0）。
  対策で top1 が変わった文は全部で 7 文、**悪化 0**: 心がけて入るんですよ→心がけてはいる（+）、と入っても→とは言っても（+）、わかって入るけど→わかってはいる（+）、
  わかって入るんですよ→わかってはいる（正解は「分かって」なので =）、採用して入るが→採用してはいるが（=、別の語の誤り）、ご趣旨はわかったと入ったが→…とはいったが（=）、そう入っても→そうはいっても（表あり、=）。
  残る型: 名詞＋は＋いっ/いる（そんなこと入っても、頭皮ケア入っています、若い女性入っちゃ、ここ入って）。名詞の後は「ここ入って」が正しいこともあり今回は対象外。
- 次: 実機で速度・メモリ（表 約 11MB ヒープ、+1.8 ms/文 CI）を確認、遅ければ Node 単位の語番号キャッシュか既定オフに戻す。名詞＋は＋いっ 型。リランカー再適用後の精度。
Reproduce: `in_daily.tsv` = ajimee + jwtd_kc + probe + daily を NK_DUMP_IN に、NK_LATTICE_COST=lc_np1m.slc、NK_HAIRU=0 / 8000 で
`./gradlew :app:testLiteStandardDebugUnitTest --rerun --tests '*SumireNbestDumpTest*'`（テストファイルを quality/ にコピー、各 ~2 分）→ `python3 $T/hcmp.py OFF ON`。

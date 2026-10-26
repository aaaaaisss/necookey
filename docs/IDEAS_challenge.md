# necookey challenge-track ideas (2026-10-14, all unmeasured)

## Leader's adopted plan (overrides mine)
0. N-best oracle rate (top 5/20/50 contain gold) on held. High → ranking; low → generation.
1. Small auto-labeled data (analyzer readings: train only, never held; LLM-synth dialect partial only, eval on real corpus).
2. Parallel: lattice discriminative retrain (word+connection costs, structured perceptron; top pick, same speed/size, can go to stable); tiny Laya-style scorer (commit + uncertain bunsetsu only; calibrated probs = confidence for A); Mozc comparison (cheap; measures search/correction-layer diff since Sumire is Mozc-derived).
3. Dict-free transducer (CTC/RNN-T style, keyboard input not voice) only if oracle low, time-boxed with pass criteria; weak on homophones; use as extra lattice candidates or char LM; reading-consistency check required.
- Engineering → may go to stable: incremental-edit conversion, optional bunsetsu gesture.
- Dropped for now: diffusion (LLaDA), on-device fine-tune, tap-coordinate converter.

## Leader's "break it" ideas
1. ∞-gram: suffix array over corpus by reading, copy longest real match with its surface; long match = confidence. First: index existing corpus, replace long-match spans, measure held. Caveat: analyzer reading errors, size.
2. Typing rhythm as boundary prior (pauses at word boundaries). Log a few hundred sentences first. Caveat: flick pauses also from finger travel/thinking.
3. Screen as dictionary: back-read visible text to readings → temporary dict. IME reads only its own input field (InputConnection); other apps need AccessibilityService (privacy/Play policy).
4. MBR: pick candidate minimizing expected fix taps (formalizes A; needs calibrated probs).
5. Read-back check: reject outputs whose re-reading mismatches input (safety for zenz/JINEN/dict-free).
6. Retroactive fix of earlier bunsetsu only on big confidence jump.
7. Night tutor: while charging, zenz scores flagged sentences → personal table.
8. Per-app kanji density (user skeptical, low priority).
Order: oracle → ∞-gram → screen dict (input field) → start rhythm logging.

## My earlier ideas
- Stable-track: A low-confidence→kana + kanji chip (leader wants it); F incremental-conversion instability as confidence; G rare 1-char kanji path close to kana path → kana; B boundary tagger; C left context into LM/reranker; D typo-aware lattice; E learn from re-segment picks.
- From-scratch design: auto-labeled data → corpus-generated dict → fully discriminative costs → N-best → small contextual neural reranker (LLM as teacher) → calibrated confidence → personal cache LM.

## User decision 2026-10-14
- Screen-as-dictionary via AccessibilityService: OK if light. Plan: optional separate module, read screen text only when keyboard opens; user must enable it (Android 13+ sideload needs restricted-settings unlock; Play review strict). Measure effect with input-field text first.

## Last ideas (2026-10-14)
- Error-mining data engine (my pick): back-read large kanji corpus, reconvert, cluster misses by type, rank by frequency → automatic fix priority list.
- Committee disagreement (Sumire / ∞-gram / JINEN) as uncertainty signal.
- Kana-first typing, one-tap whole-message kanji conversion at the end.

## Sumire-free engine (user idea 2026-10-14, challenge-track goal)
- Design: ∞-gram copies real text spans (corpus = dictionary) → tiny char model fills gaps → Laya-style scorer → low confidence → kana.
- Sumire stays in stable track. Switch only if it beats Sumire+lattice on held lenient (.880) AND absurd-conversion rate. Fix pass criteria before building.

## 2026-10-23
- User finding: Sumire is confidently wrong; Sumire cost-gap must not be the gate (use reranker margin only).
- Proposed for Nov: rebuild 20MB practical table as budget-constrained discriminative hashed weights (store weights, not probs/backoff); fallback Stolcke entropy pruning. Awaiting go.

## 2026-10-23 USER DIRECTION CHANGE (overrides zenz/JINEN plans)
- zenz also errs a lot → goal: best everyday performance WITHOUT AI (no zenz/JINEN in the conversion path).
- Priorities: (1) unknown-word robustness, (2) minimize boundary/homophone errors by mechanism, (3) rebuild easy-fix UI.
- Open to a Sumire-like engine built from scratch, or an entirely new converter design.
- My proposal: statistical n-gram/∞-gram/discriminative weights count as non-AI (asked user to confirm). Unknown words: ∞-gram corpus span copy + auto vocab extraction + katakana/unknown nodes + low-confidence→kana with kanji chips. Nov first: N-best oracle rate + ∞-gram prototype.
- 2026-10-23 user: statistical models OK. Unknown word → hiragana is acceptable; forced kanji is the real problem. Worried ∞-gram won't catch unknown words.
- My answer: no need to break the whole engine for this. Use corpus index as a CHECKER, not generator: if a converted span made of short kanji words never appears contiguously in the corpus (surface+reading), output kana instead (kanji in chips). Doesn't need to know the unknown word, only that the patchwork isn't real. Measure forced-kanji rate + regressions (買って/県庁/困難/広島県/三年) on daily first; rebuild engine only if this falls short.

## 2026-10-23 brainstorm round (no-AI, unknown words first) — user: "this month, brainstorm a lot"
Forced kanji / unknown words:
1. Corpus existence check for short-kanji patchwork (above).
2. Rare-reading guard: per-kanji reading frequency (on/kun/nanori); paths using rare readings penalized.
3. Name dictionary gated by context (さん/くん/様, place words); otherwise name entries cost more.
4. Kana-likelihood: from corpus, how often a reading appears written in kana (ちゃんと, めっちゃ) → kana path cost.
5. Loanword guess: foreign phonotactics (ー, ふぁ/てぃ/ゔ, っ+ー patterns) and no dict hit → katakana candidate.
6. Personal kana memory: when user reverts to kana / picks kana, stick per reading.
Boundaries:
7. Particle anchors: high-confidence particle/auxiliary positions from corpus kana-bigram stats as boundary prior.
8. Incremental instability (F) as boundary-confidence signal → only those spans flagged.
Homophones:
9. Session/field cache LM: words already in the text field and recent commits get a bonus.
UI:
10. Underline only uncertain spans; tap cycles kanji↔kana, swipe moves boundary, backspace right after commit restores reading of last bunsetsu.

## 2026-10-23 user: first build a set of "barely meaningful" sentences, log what errors happen, then pick ideas. Context without zenz:
- Homophone detector: span where top candidates share reading+POS, differ only in surface (same-reading set in dict) → flag. Cheap, exact.
- Homophone-only disambiguation tables (word-sense style): per confusable set, top discriminative context words (naive Bayes / log-odds) from corpus; store only for flagged sets → small. (Old homophone-ctx 1M/3MB general table gave +1pt over reranker; homophone-only is different target.)
- Case frames: noun+particle+verb compatibility counts (病院に行く, 機会を逃す) incl. left context from getTextBeforeCursor.
- Field/session cache + per-pair personal history.
- Still unsure → kana/chip, or zenz only on the flagged span (fallback).
- 2026-10-23 screen text via AccessibilityService: possible but Play review (disclosure/consent, non-accessibility use hard to pass), privacy (passwords/banking), battery. Rec: first exploit input field text + package name; accessibility as opt-in separate module/sideload only if test set shows context helps.
- 2026-10-23 user: personal use, GitHub only, no network permission → accessibility screen text is GO if useful. Design: on-screen words → local session dictionary; known words get readings from dict; unknown kanji words matched by composing per-kanji readings to typed reading (e.g. screen 鞆の浦 + typed ともの うら). Skip password/sensitive nodes, keep in memory only, short TTL. Feeds homophone tables/cache LM and unknown-word candidates.
- 2026-10-23 user: target = kill boundary errors + absurd conversions; homophones user can fix. Ideas: (a) earlier diag: boundary errors mostly from missing words → vocab/unknown work fixes boundaries too; (b) bunsetsu well-formedness rules (content word + legal function-word chain; reject odd particle/aux sequences, e.g. とかし→溶かし); (c) cross-boundary surface pair must exist in corpus; (d) define 'absurd' metric = span with no corpus support + rare reading/patchwork → kana; measure it on the borderline set.
- 2026-10-23 size/memory: personal use → storage size cap can be relaxed (HANDOFF: 40-60MB keeps most of reranker gain vs 20MB ~half). Worth doing: mmap tables (off-heap), lazy load, keystroke latency budget, real-device measure. Not worth: squeezing to 5-20MB.

## 2026-10-24 borderline set result (border/RESULTS.md)
179 sents, lenient .860, oracle@5 .983, @50 1.0 -> fix ranking. 8 fatal absurd/boundary (dialect negatives ん/もうた/んさい, は-merges), 15 homophones mostly case-frame decidable (帰る/変える, 県庁/堅調, 昨日/機能). Next: case-frame/collocation scorer on N-best + dialect verb-ending nodes.

## 2026-10-24 learning-method ideas (user wants better training)
- Offline: cost-sensitive reranker loss (fatal boundary/absurd >> homophone >> notation=0), hard negatives from lattice-on N-best.
- Personal: all signals as reranker features with time decay, not dict cost overrides. Signal value: correction events (re-segment, kana revert, chip pick) > committed-text n-gram cache > screen text (unknown proper nouns). 後続語学習 = prediction bar mostly.
- Screen text also as topic-cache context for homophones (e.g. partner wrote 地震 -> じしん=地震); weight by proximity to input, drop per-app repeated UI labels.
- (10/24) [u015 可部線 was a test reading typo かべやせん; ignore it] "Not in candidates" check: none truly missing from N-best50, but 3 deep: 可部線 rank21, 待ちんさい rank11, 昨日履いた rank7 (+5 at rank3-4). Fixes: local place/line dict (Hiroshima), dialect verb-ending nodes, N-best diversity (collapse 取る/撮る-type variants so other segmentations surface), safe kana-ish candidate always in top few, on-device log of user re-segment/kana-back = real absent cases.
- (10/24) User: drop regional place dict (not a root fix, IME is nationwide). Priority = correction -> correct user-dict entry -> ranks top next time. Eval idea: replay correction on each border error (可部線, 待ちんさい...), re-input same + other sentences containing the word, measure top1 and collateral damage.

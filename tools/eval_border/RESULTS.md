# borderline set (2026-10-24), Sumire+lattice (nk-dialect, lc_np1m), 179 sents, lenient
cat n exact lenient oracle@5 oracle@50
u(unknown) 40 23 36 39 40 | b(boundary) 40 26 30 39 40 | f(forced-kanji/dialect) 40 19 38 39 40 | h(homophone) 39 31 31 39 39 | c(regress) 20 13 19 20 20
Total lenient 154/179 (.860). oracle@5 176, oracle@50 179 -> ranking problem, not generation (caveat: sentences I wrote are biased to in-dict words).
Errors (errs.txt), 25:
- Absurd/boundary (8, fatal): 壁や線(可部線), 値落ちしても歌, 胃炎買った(言えんかった), 機能は痛く注がない, 駅前運んでる, 移管？(行かん), 地位トマチン祭(待ちんさい), ここでは着物. Pattern: dialect/colloq negative ん・もうた・んさい split into nouns; は/は+い merges.
- Homophone (15): 機能(昨日)x2-ish, 廃車, 行った/言った, 協会, 橋の橋, 変える(帰る)x2, 機会(機械), 意思(石), 自身(地震/自信)x2, 早い, 公園(講演), 図る, 堅調(県庁, regression case!), 賞味(正味). Many decidable by case frame / local collocation (に帰る, を投げた, が揺れた, が速い, を聞きに).
- Forced kanji from unknown: almost none; unknown words mostly ok lenient.
APK: main already does not bundle jinen gguf by default; remaining AI weight = zenz module native llama.cpp libs (check size in Nov).
CORRECTION (10/24): u015 reading was a typo (かべやせん, should be かべせん). Fixed in in.tsv; 壁や線 error was test bug, not Sumire. Real Sumire gives 可部線 per user. Expect lenient 155/179 (.866), absurd 7; rerun in Nov to confirm.

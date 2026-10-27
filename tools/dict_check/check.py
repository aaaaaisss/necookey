#!/usr/bin/env python3
"""システム辞書の読みズレ・基本語欠落の簡易チェック（疑わしい候補を TSV に出すだけ。誤検出あり）。

入力: SystemDictionaryDumpTest の出力（読み\t表記\t左ID\t右ID\tコスト）。
使い方: python3 tools/dict_check/check.py /tmp/dc/dict.tsv [出力dir]
出力:
  reading_suspects.tsv  漢字1字+送り仮名の語で、その漢字の読み（語幹）が辞書全体でほとんど使われないもの
  basic_missing.tsv     basic_words.tsv の基本語（基本形・連用タ接続など）が辞書に無い／コストが極端なもの
  godan_gap.tsv         五段動詞で基本形はあるのに連用タ接続（〜っ/〜い/〜ん）が無い語
"""
import collections
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
KANJI = re.compile(r"[\u4e00-\u9fff\u3005]")
KANA = re.compile(r"^[\u3041-\u3096ー]+$")


def load_pos():
    pos = {}
    with open(os.path.join(ROOT, "app/src/main/assets/id.def"), encoding="utf-8") as f:
        for line in f:
            i, p = line.rstrip("\n").split(" ", 1)
            pos[int(i)] = p
    return pos


def main():
    dump = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.dirname(os.path.abspath(dump))
    pos = load_pos()
    rows = []
    with open(dump, encoding="utf-8") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) == 5:
                rows.append((p[0], p[1], int(p[2]), int(p[3]), int(p[4])))
    print("entries", len(rows))

    # --- 1. 読みズレ: 「漢字1字+かな」で送り仮名が読み末尾と一致する語から（漢字, 語幹読み）を集める
    #     漢字1字の語（単漢字表記）と熟語先頭/末尾の読みも「その漢字の読み」の証拠として数える
    pair_words = collections.defaultdict(set)   # (k, r) -> {(yomi, tango)}
    evidence = collections.Counter()            # (k, r) -> 証拠数（他の語での使用）
    kanji_total = collections.Counter()
    for yomi, tango, l, r, c in rows:
        if not KANA.match(yomi):
            continue
        if len(tango) == 1 and KANJI.match(tango):
            if pos.get(l, "").startswith("名詞"):  # 動詞語幹（体言接続特殊２ 等）は証拠にしない
                evidence[(tango, yomi)] += 3
            continue
        m = re.match(r"^([\u4e00-\u9fff])([\u3041-\u3096]+)$", tango)
        if m:
            k, oku = m.group(1), m.group(2)
            if yomi.endswith(oku) and len(yomi) > len(oku):
                stem = yomi[: -len(oku)]
                pair_words[(k, stem)].add((yomi, tango, c, pos.get(l, "")))
                kanji_total[k] += 1
            continue
    # 語幹読みの多様性: 同じ漢字で語の数が多い読みが主読み。語幹読み r の「他の語での使われ方」は
    # 熟語の読みに r が先頭/末尾として現れるかで近似する（音読みは熟語で頻出、訓読みは送り仮名語で頻出）
    lemma_count = collections.Counter()
    for (k, s), ws in pair_words.items():
        lemma_count[(k, s)] = len({t for _, t, _, _ in ws})
    compound_hits = collections.Counter()
    heads = collections.defaultdict(set)
    for yomi, tango, l, r, c in rows:
        if len(tango) >= 2 and KANA.match(yomi) and KANJI.match(tango[0]):
            heads[tango[0]].add(yomi)
    for (k, s) in pair_words:
        compound_hits[(k, s)] = sum(1 for y in heads.get(k, ()) if y.startswith(s))
    suspects = []
    for (k, s), ws in pair_words.items():
        n_forms = len(ws)
        hits = compound_hits[(k, s)]  # 先頭が k の語で読みが s で始まるもの（自分の活用形も含む）
        others = hits - n_forms       # 自分の活用形以外での使用
        n_readings = sum(1 for (kk, _) in pair_words if kk == k)
        if others <= 0 and evidence[(k, s)] == 0 and n_readings >= 2:
            # 同じ読み+送り仮名で別の漢字の語が辞書にある → 取り違えの可能性が高い
            alt = set()
            for yomi, tango, c, p in ws:
                oku = tango[1:]
                for (k2, s2), ws2 in pair_words.items():
                    if s2 == s and k2 != k and compound_hits[(k2, s2)] - len(ws2) > 0:
                        alt.add(k2 + oku)
            ex = sorted(ws, key=lambda w: w[2])[0]
            # 同じ漢字の別の語幹読みが、この読みより安い（=主読み）なら +1
            main = min((min(w[2] for w in ws2), s2) for (k2, s2), ws2 in pair_words.items() if k2 == k)
            score = (2 if alt else 0) + (1 if main[1] != s else 0)
            suspects.append((score, k, s, n_forms, ex[0], ex[1], ex[2], ex[3], main[1], ",".join(sorted(alt))[:60]))
    # 疑わしさ（score）→ 実害の大きさ（例コストが低い＝上位に出やすい）の順
    suspects.sort(key=lambda x: (-x[0], x[6]))
    with open(os.path.join(out, "reading_suspects.tsv"), "w", encoding="utf-8") as w:
        w.write("score\t漢字\t語幹読み\t活用形数\t例読み\t例表記\t例コスト\t品詞\tこの漢字の主読み\t同読みの別漢字語\n")
        for s in suspects:
            w.write("\t".join(map(str, s)) + "\n")
    print("reading_suspects", len(suspects), "score>=2:", sum(1 for s in suspects if s[0] >= 2))

    # --- 2. 基本語の欠落・コスト異常
    have = collections.defaultdict(list)
    for yomi, tango, l, r, c in rows:
        have[(yomi, tango)].append((c, pos.get(l, "")))
    miss = []
    with open(os.path.join(HERE, "basic_words.tsv"), encoding="utf-8") as f:
        for line in f:
            if not line.strip() or line.startswith("#"):
                continue
            yomi, tango, *note = line.rstrip("\n").split("\t")
            e = have.get((yomi, tango))
            if not e:
                miss.append((yomi, tango, "MISSING", "", note[0] if note else ""))
            elif min(c for c, _ in e) > 9000:
                miss.append((yomi, tango, "HIGH_COST", min(c for c, _ in e), note[0] if note else ""))
    with open(os.path.join(out, "basic_missing.tsv"), "w", encoding="utf-8") as w:
        w.write("読み\t表記\t状態\t最小コスト\tメモ\n")
        for m in miss:
            w.write("\t".join(map(str, m)) + "\n")
    print("basic_missing", len(miss))

    # --- 3. 五段動詞: 基本形があって連用タ接続が無い
    base, ta = {}, set()
    for yomi, tango, l, r, c in rows:
        p = pos.get(l, "")
        if p.startswith("動詞,自立") and "五段" in p:
            if ",基本形" in p:
                base[(yomi, tango)] = (c, p)
            elif ",連用タ接続" in p:
                ta.add((yomi[:-1], tango[:-1]))
    gap = []
    for (yomi, tango), (c, p) in base.items():
        if not KANJI.search(tango) or yomi.endswith("す") or "ユク" in p:  # サ行は連用タ接続を持たない。〜ゆく（ゆっ）は別扱い
            continue
        if (yomi[:-1], tango[:-1]) not in ta:
            gap.append((c, yomi, tango, p))
    gap.sort()
    with open(os.path.join(out, "godan_gap.tsv"), "w", encoding="utf-8") as w:
        w.write("基本形コスト\t読み\t表記\t品詞\n")
        for g in gap:
            w.write("\t".join(map(str, g)) + "\n")
    print("godan_gap", len(gap), "of", len(base))


if __name__ == "__main__":
    main()

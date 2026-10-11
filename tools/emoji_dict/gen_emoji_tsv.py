#!/usr/bin/env python3
"""Generate the emoji reading dictionary TSV (reading, emoji, posIndex, cost) for LoudsTriple build.

Sources (download into WORK dir first, see README.md):
  ja.xml      Unicode CLDR common/annotations/ja.xml          (Unicode License v3)
  jaD.xml     Unicode CLDR common/annotationsDerived/ja.xml   (Unicode License v3)
  emoji-test.txt  Unicode emoji-test.txt (fully-qualified forms)
  data.json   sen-ltd/emoji-search-jp data.json (MIT, colloquial tags like ぴえん)
  old.tsv     dump of the previous emoji dictionary (optional; homograph disambiguation only)
  sys.tsv     dump of the bundled system dictionary (LoudsTriple dump), used only to give
              kanji keywords a reading (surface -> lowest-cost reading, greedy segmentation).
Keywords that cannot be read fully as kana are skipped.
"""
import json, re, sys, html
from pathlib import Path

W = Path(sys.argv[1])
OUT = Path(sys.argv[2])
POS = 11625      # same POS index as the previous Sumire emoji dictionary
COST_SEN = 5900  # colloquial tags first
COST_CLDR = 6000
MAX_READING = 16
MAX_EMOJI_VERSION = 15.1  # newer emoji render as tofu on most devices (previous dictionary: <= 15.1)
SKIN = set(range(0x1F3FB, 0x1F400))

# fully-qualified emoji, keyed by form without FE0F
fq = {}
order = {}
for line in (W / "emoji-test.txt").read_text(encoding="utf-8").splitlines():
    if "; fully-qualified" not in line or line.startswith("#"):
        continue
    ver = re.search(r"# \S+ E(\d+\.\d+)", line)
    if ver and float(ver.group(1)) > MAX_EMOJI_VERSION:
        continue
    cps = [int(c, 16) for c in line.split(";")[0].split()]
    if any(c in SKIN for c in cps):
        continue
    s = "".join(map(chr, cps))
    key = s.replace("\ufe0f", "")
    fq.setdefault(key, s)
    order.setdefault(s, len(order))

def kata2hira(s):
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)

KANA = re.compile(r"^[ぁ-ゖー]+$")

# surface -> (cost, reading) for short surfaces containing kanji
rev_all = {}
for line in (W / "sys.tsv").open(encoding="utf-8"):
    f = line.rstrip("\n").split("\t")
    if len(f) != 4:
        continue
    r, s, _p, c = f
    if len(s) > 8 or not KANA.match(r) or not re.search(r"[\u3400-\u9fff々]", s):
        continue
    c = int(c)
    m = rev_all.setdefault(s, {})
    if r not in m or c < m[r]:
        m[r] = c

# Readings of the previous (Sumire) emoji dictionary, used only to pick among homographs
# (e.g. 旗 -> はた not き, 犬 -> いぬ not けん).
old_by_emoji, old_readings = {}, set()
if (W / "old.tsv").exists():
    for line in (W / "old.tsv").open(encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        old_by_emoji.setdefault(f[1].replace("\ufe0f", ""), set()).add(f[0])
        old_readings.add(f[0])

def pick(surface, emoji):
    m = rev_all[surface]
    mine = old_by_emoji.get(emoji.replace("\ufe0f", ""), set())
    for pool in ([r for r in m if r in mine], [r for r in m if r in old_readings], list(m)):
        if pool:
            r = min(pool, key=lambda x: m[x])
            return m[r], r

def reading(word, emoji):
    w = html.unescape(word).strip()
    w = re.sub(r"[\s・･\-‐]", "", w)
    w = kata2hira(w.replace("ヴ", "ゔ"))
    if not w:
        return None
    if KANA.match(w):
        return w if len(w) <= MAX_READING else None
    # greedy DP: fewest pieces; pieces are kana runs or dictionary surfaces
    n = len(w)
    best = [None] * (n + 1)
    best[0] = (0, 0, "")
    for i in range(n):
        if best[i] is None:
            continue
        pieces, cost, acc = best[i]
        for j in range(n, i, -1):
            seg = w[i:j]
            if KANA.match(seg):
                cand = (pieces + 1, cost, acc + seg)
            elif seg in rev_all:
                c, r = pick(seg, emoji)
                cand = (pieces + 1, cost + c, acc + r)
            else:
                continue
            if best[j] is None or cand[:2] < best[j][:2]:
                best[j] = cand
    if best[n] is None:
        return None
    r = best[n][2]
    return r if KANA.match(r) and len(r) <= MAX_READING else None

entries = {}  # (reading, emoji) -> cost
def add(word, emoji, cost):
    r = reading(word, emoji)
    if r:
        k = (r, emoji)
        if k not in entries or cost < entries[k]:
            entries[k] = cost

ann = re.compile(r'<annotation cp="([^"]+)"( type="tts")?>([^<]*)</annotation>')
for f in ("ja.xml", "jaD.xml"):
    for cp, tts, text in ann.findall((W / f).read_text(encoding="utf-8")):
        cp = html.unescape(cp)
        emoji = fq.get(cp.replace("\ufe0f", ""))
        if emoji is None:
            continue
        if tts:
            parts = [p.strip() for p in text.split(":")]
            words = [parts[-1]] if parts[0] == "旗" and len(parts) > 1 else ["".join(parts)]
        else:
            words = text.split("|")
        for w in words:
            add(w, emoji, COST_CLDR)

for e in json.loads((W / "data.json").read_text(encoding="utf-8"))["emojis"]:
    emoji = fq.get(e["char"].replace("\ufe0f", ""))
    if emoji is None:
        continue
    for w in e.get("tags", []) + [e.get("name_ja", "")]:
        add(w, emoji, COST_SEN)

rows = sorted(entries.items(), key=lambda kv: (kv[0][0], kv[1], order.get(kv[0][1], 1 << 20)))
with OUT.open("w", encoding="utf-8") as o:
    for (r, emoji), cost in rows:
        o.write(f"{r}\t{emoji}\t{POS}\t{cost}\n")
print(f"entries={len(rows)} readings={len({r for (r, _), _ in rows})} emoji={len({e for (_, e), _ in rows})}", file=sys.stderr)

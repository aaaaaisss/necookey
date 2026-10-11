# Emoji reading dictionary

`app/src/main/assets/emoji/{tango,yomi,token}_emoji.dat` (build inputs of `system.compact.kdict`, triple id 2)
are generated from:

- Unicode CLDR `common/annotations/ja.xml` + `common/annotationsDerived/ja.xml` (keywords + tts names),
  Unicode License v3 — https://github.com/unicode-org/cldr
- Unicode `emoji-test.txt` 17.0 (fully-qualified forms up to Emoji 15.1; skin-tone variants skipped)
- sen-ltd/emoji-search-jp `data.json` (colloquial tags such as ぴえん, cost 5900 so they rank first),
  MIT License, Copyright (c) 2026 SEN LLC — see `LICENSE-emoji-search-jp`

Kana keywords are used directly (katakana -> hiragana). Kanji keywords get a reading from the bundled
system dictionary (surface -> reading, fewest-pieces segmentation); homographs prefer readings that the
previous Sumire emoji dictionary used (`old.tsv`, dump of these assets at fadbd27). Unreadable keywords are skipped.

Regenerate (WORK = scratch dir with ja.xml, jaD.xml, emoji-test.txt, data.json):

    kotlinc LoudsTriple.kt -include-runtime -d lt.jar
    java -Xmx6g -jar lt.jar dump app/dictionary-src/system/{tango,yomi,token}.dat.zip zip > WORK/sys.tsv
    git show fadbd27:app/src/main/assets/emoji/tango_emoji.dat > /tmp/t; (same for yomi/token)
    java -jar lt.jar dump /tmp/t /tmp/y /tmp/k > WORK/old.tsv
    python3 gen_emoji_tsv.py WORK WORK/emoji.tsv
    java -jar lt.jar build WORK/emoji.tsv app/src/main/assets/emoji/{tango,yomi,token}_emoji.dat

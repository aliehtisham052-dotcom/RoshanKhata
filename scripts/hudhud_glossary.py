#!/usr/bin/env python3
"""Hudhud's glossary: every concept word of the engine, by concept and language, for a native reader to review
(plan v1, Marhala 11 item 5; 26 Nov 2026).

HudhudMind.kt keeps one table, LEXICON: concept -> the words that mean it, in the nine languages and the scripts a
recogniser writes them in. This script reads that table from the Kotlin source (it never imports or runs Kotlin) and writes
docs/hudhud-glossary.tsv, one row per concept and language:

    concept   language   words (separated by " | ")   reviewed_by

The table is NOT labelled by language in the code, so the language of a word is read from its letters only where the letters
decide it, and is never guessed:

    hi                 Devanagari
    bn                 Bengali script
    sd                 Arabic script with a letter only Sindhi writes among the nine (ڪ ڳ ٺ ڄ ٻ ڌ ڏ ڍ ڙ ۽ ڻ ڱ ڃ ڇ ٿ ڦ ڊ ٽ ڀ ڳ)
    ur                 Arabic script with a letter only Urdu writes among the nine (ٹ ڈ ڑ ں ے ۓ ہ ۂ)
    ar                 Arabic script with ة, ى or the Arabic kaf ك (Urdu, Farsi and Sindhi write ک/ڪ)
    ur|sd|fa|ar        any other Arabic-script word: the letters do not say which of the four (most Farsi words land here)
    en|ur-Latn|in      Latin letters: English, Roman Urdu and Indonesian share the script

A word with letters of two of these at once is written in the first that matches (sd before ur before ar).

reviewed_by is empty when the words are AI-written and no person has read them (all of them on 26 Nov 2026: the repository
records no native read). A reviewer writes their role ("native") in that cell by hand; the script keeps it while that row's words
stay the same, and empties it (saying so) when a word of the row changes, because the review no longer covers the new words.

    python3 scripts/hudhud_glossary.py           # write docs/hudhud-glossary.tsv
    python3 scripts/hudhud_glossary.py --check   # fail when the glossary is stale against HudhudMind.kt (Strings CI)
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIND = ROOT / "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data/HudhudMind.kt"
GLOSSARY = ROOT / "docs/hudhud-glossary.tsv"
COLUMNS = ["concept", "language", "words", "reviewed_by"]
SEP = " | "

SINDHI_ONLY = set("ڪڳٺڄٻڌڏڍڙ۽ڻڱڃڇٿڦڊٽڀ")
URDU_ONLY = set("ٹڈڑںےۓہۂ")
ARABIC_ONLY = set("ةىك")
LANGUAGES = ["en|ur-Latn|in", "ur", "sd", "ar", "ur|sd|fa|ar", "hi", "bn"]


class ParseError(Exception):
    pass


def tokens(src: str):
    """Kotlin tokens enough for the table: string literals (with escapes), identifiers with dots, and ( ) , ;
    comments are skipped. Raw strings (\"\"\") are refused: the table has none, and a guess would be wrong."""
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j + 1
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            if j < 0:
                raise ParseError("an unclosed /* comment")
            i = j + 2
        elif src.startswith('"""', i):
            yield ("raw", i)
            j = src.find('"""', i + 3)
            if j < 0:
                raise ParseError('an unclosed """ string')
            i = j + 3
        elif c == '"':
            j, out = i + 1, []
            while True:
                if j >= n or src[j] == "\n":
                    raise ParseError(f"an unclosed string at offset {i}")
                if src[j] == "\\":
                    esc = src[j + 1]
                    if esc == "u":
                        out.append(chr(int(src[j + 2:j + 6], 16)))
                        j += 6
                        continue
                    out.append({"n": "\n", "t": "\t", '"': '"', "\\": "\\", "$": "$", "'": "'"}.get(esc, "\\" + esc))
                    j += 2
                    continue
                if src[j] == "$":
                    raise ParseError(f"a string template in the table at offset {j}: a word must be a plain literal")
                if src[j] == '"':
                    break
                out.append(src[j])
                j += 1
            yield ("str", "".join(out))
            i = j + 1
        elif c.isalpha() or c == "_":
            j = i
            while j < n and (src[j].isalnum() or src[j] in "_."):
                j += 1
            yield ("id", src[i:j])
            i = j
        elif c in "(),<>:=":
            yield ("p", c)
            i += 1
        else:
            i += 1


def enum_concepts(src: str):
    start = src.find("enum class C {")
    if start < 0:
        raise ParseError("enum class C { was not found")
    end = src.find("}", start)
    body = src[start + len("enum class C {"):end]
    names = []
    for kind, val in tokens(body):
        if kind == "id":
            names.append(val)
    return names


def lexicon(src: str):
    """[(concept, [words in source order])] from `LEXICON ... = mapOf( C.X to listOf("…", …), … )`."""
    anchor = src.find("val LEXICON")
    if anchor < 0:
        raise ParseError("val LEXICON was not found")
    open_at = src.find("mapOf(", anchor)
    if open_at < 0:
        raise ParseError("LEXICON's mapOf( was not found")
    toks = list(tokens(src[open_at + len("mapOf"):]))
    if not toks or toks[0] != ("p", "("):
        raise ParseError("LEXICON's mapOf is not followed by (")
    depth, i, out = 0, 0, []
    current, words, list_depth = None, None, None
    while i < len(toks):
        kind, val = toks[i]
        if kind == "raw":
            raise ParseError("a raw string in LEXICON")
        if (kind, val) == ("p", "("):
            depth += 1
        elif (kind, val) == ("p", ")"):
            if words is not None and depth == list_depth:
                out.append((current, words))
                current, words, list_depth = None, None, None
            depth -= 1
            if depth == 0:
                break
        elif kind == "id" and val.startswith("C.") and depth == 1:
            if words is not None:
                raise ParseError(f"{val} begins before {current}'s list ended")
            if not (i + 2 < len(toks) and toks[i + 1] == ("id", "to") and toks[i + 2] == ("id", "listOf")):
                raise ParseError(f"{val} is not followed by `to listOf(`")
            current, words = val[2:], []
            list_depth = depth + 1
            i += 2
        elif kind == "str":
            if words is None or depth != list_depth:
                raise ParseError(f"a word {val!r} outside a concept's list")
            words.append(val)
        elif kind == "id" and depth >= 2 and val not in ("listOf",):
            raise ParseError(f"{val} inside a word list: a word must be a plain literal")
        i += 1
    if depth != 0:
        raise ParseError("LEXICON's mapOf( was not closed")
    return out


def language_of(word: str) -> str:
    letters = set(word)
    if any("ऀ" <= ch <= "ॿ" for ch in word):
        return "hi"
    if any("ঀ" <= ch <= "৿" for ch in word):
        return "bn"
    if any("؀" <= ch <= "ۿ" for ch in word):
        if letters & SINDHI_ONLY:
            return "sd"
        if letters & URDU_ONLY:
            return "ur"
        if letters & ARABIC_ONLY:
            return "ar"
        return "ur|sd|fa|ar"
    if any(ch.isalpha() and ord(ch) < 0x0250 for ch in word):
        return "en|ur-Latn|in"
    raise ParseError(f"the word {word!r} is in no script the glossary knows")


def build(src: str):
    concepts = enum_concepts(src)
    table = lexicon(src)
    problems = []
    seen = [c for c, _ in table]
    for c in sorted({c for c in seen if seen.count(c) > 1}):
        problems.append(f"LEXICON lists {c} twice")
    for c in seen:
        if c not in concepts:
            problems.append(f"LEXICON names {c}, which is not in enum class C")
    order = {c: k for k, c in enumerate(concepts)}
    rows = []
    for concept, words in sorted(table, key=lambda t: order.get(t[0], len(order))):
        by_lang = {}
        for w in words:
            if not w.strip() or "\t" in w or SEP.strip() in w:
                problems.append(f"{concept}: the word {w!r} is empty or holds a tab or '|'")
                continue
            lang = language_of(w)
            bucket = by_lang.setdefault(lang, [])
            if w not in bucket:
                bucket.append(w)
        for lang in LANGUAGES:
            if lang in by_lang:
                rows.append({"concept": concept, "language": lang, "words": SEP.join(by_lang[lang]), "reviewed_by": ""})
    missing = [c for c in concepts if c not in seen]
    return rows, problems, missing


HEADER = [
    "# Hudhud's glossary (plan v1, Marhala 11 item 5): the engine's concept words, by concept and language, for a native read.",
    "# Written by scripts/hudhud_glossary.py from HudhudMind.kt's LEXICON; --check (Strings CI) fails when it is stale. Do not edit",
    "# the words here: change HudhudMind.kt and run the script. language comes from the letters only (see the script's notes):",
    "# hi, bn, sd, ur, ar are decided by script or by a letter only that language writes; 'ur|sd|fa|ar' and 'en|ur-Latn|in' are",
    "# words the letters cannot place. reviewed_by: empty = AI-written, read by no person (all rows on 26 Nov 2026: the",
    "# repository records no native read); a reviewer writes 'native' by hand and it stays while the row's words are unchanged.",
]


def read_existing():
    if not GLOSSARY.exists():
        return {}, None
    reviews = {}
    lines = [l for l in GLOSSARY.read_text(encoding="utf-8").split("\n") if l and not l.startswith("#")]
    if not lines or lines[0].split("\t") != COLUMNS:
        return {}, f"{GLOSSARY.relative_to(ROOT)}: its header row is not {COLUMNS}"
    for line in lines[1:]:
        cells = line.split("\t")
        if len(cells) != len(COLUMNS):
            return {}, f"{GLOSSARY.relative_to(ROOT)}: a row has {len(cells)} cells, not {len(COLUMNS)}: {line[:60]!r}"
        row = dict(zip(COLUMNS, cells))
        if row["reviewed_by"]:
            reviews[(row["concept"], row["language"])] = (row["words"], row["reviewed_by"])
    return reviews, None


def render(rows):
    out = list(HEADER) + ["\t".join(COLUMNS)]
    out += ["\t".join(r[c] for c in COLUMNS) for r in rows]
    return "\n".join(out) + "\n"


def main(argv):
    try:
        rows, problems, missing = build(MIND.read_text(encoding="utf-8"))
    except ParseError as e:
        print(f"hudhud glossary: FAIL\n  HudhudMind.kt could not be read: {e}")
        return 1
    reviews, bad = read_existing()
    if bad:
        problems.append(bad)
    dropped = []
    for r in rows:
        kept = reviews.get((r["concept"], r["language"]))
        if kept:
            if kept[0] == r["words"]:
                r["reviewed_by"] = kept[1]
            else:
                dropped.append(f"{r['concept']} / {r['language']} ({kept[1]})")
    if problems:
        print("hudhud glossary: FAIL\n  " + "\n  ".join(problems))
        return 1
    text = render(rows)
    words = sum(len(r["words"].split(SEP)) for r in rows)
    reviewed = sum(1 for r in rows if r["reviewed_by"])
    summary = (f"{len(rows)} rows, {len({r['concept'] for r in rows})} concepts, {words} words; "
               f"{reviewed} row(s) reviewed by a person")
    if "--check" in argv:
        current = GLOSSARY.read_text(encoding="utf-8") if GLOSSARY.exists() else ""
        if current != text:
            print("hudhud glossary: FAIL\n  docs/hudhud-glossary.tsv is stale against HudhudMind.kt's LEXICON "
                  "(a word added, removed or moved, or the file edited by hand).")
            if dropped:
                print("  These reviews no longer cover their row's words: " + ", ".join(dropped))
            print("  Regenerate with python3 scripts/hudhud_glossary.py and commit the file with the change.")
            return 1
        print(f"hudhud glossary OK: {summary}")
        if missing:
            print(f"  concepts with no words of their own in LEXICON (read by other rules): {', '.join(missing)}")
        return 0
    GLOSSARY.write_text(text, encoding="utf-8")
    if dropped:
        print("hudhud glossary: these reviews were emptied, their row's words changed: " + ", ".join(dropped))
    print(f"hudhud glossary: wrote {GLOSSARY.relative_to(ROOT)}: {summary}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

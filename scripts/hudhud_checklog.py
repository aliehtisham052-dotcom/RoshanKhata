#!/usr/bin/env python3
"""The owner's Hudhud check log, read by class and never copied (Marhala 2 of docs/hudhud-master-plan-v1.md; lesson 120).

What it reads: the list the app shares from More -> Hudhud's voice -> Testing tools -> Share the list
(`HudhudCheck.share`, `CheckLog` in app/src/main/java/com/innovation313/roshankhata/ui/HudhudCheck.kt), exactly as it
comes off the phone: UTF-8 with a byte-order mark, tab-separated, a header and one row per answer —

    no  time  language  question  taken_as  answer  mark  business

`language` is the APP's display language (a locale tag such as "ur-PK"), not the language of the question; `question`
is what was asked (for a spoken question, what the recogniser heard); `taken_as` is `HudhudCheck.describe` of the
reading ("person: BALANCE [12]", "guessed > help: faq_backup", "error"); `answer` is the card's heading (its label —
a customer's NAME when the answer is about a customer — its figure and first line); `mark` is "right", "wrong" or
empty. A row of seven fields (before 16 Nov 2026) has no business. The log has no "what was meant" column: when the
owner adds one in a spreadsheet (a column headed expected / meant / should_be / sahi, or a ninth column with no
heading), it is read as the expected meaning. A file Excel saved again as "Unicode Text" (UTF-16) is read too.

What it writes, into a folder OUTSIDE the repository (refused inside it, as hudhud_sets.py --sealed is; the input
file is refused inside it too — a real log must never sit in a git working tree):

    <log name>.rows.tsv     one row per marked answer (wrong only, unless --all-marked): its row number, mark, the app's
                            language, the script and language GUESSED from the question, what Hudhud read (ids taken
                            out), the expected meaning when the owner gave one, the question and the card's heading
                            with names, phone numbers and long numbers replaced by placeholders, and why a row needs a
                            human look;
    <log name>.classes.tsv  the rows grouped by (expected meaning, what Hudhud read): count, right/wrong, row numbers,
                            languages, one masked example — so a class of mistakes is visible at a glance.

The placeholders: {NAME1}.. / {ITEM1}.. a word of the customer's / product's name on the card (Hudhud found it);
{PHONE} a phone number; {NUM} a number of five digits or more (amounts of four digits or fewer stay: a reading of
"500" is a class); {SHOP} the owner's shop or village (the fingerprints check_repo_guards.py keeps, never the
words); {X1}.. any other word that is not in Hudhud's own vocabulary (the bank and held-out sentences of the
repository, less the invented test names) or a capitalised word in the middle of a Latin sentence — a name, a
product, a typo or a recogniser's garble.
A row with an {X} is flagged "look": Claude opens that row number in the owner's own file, decides what the word was,
and writes the class with INVENTED names (docs/hudhud-annotation-guide.md). The masking is drift protection, not
proof: a real name that is also an ordinary word Hudhud knows ("Noor", "Barkat", "Qeemat") is not masked unless the
card named it (a row whose card names somebody the question does not is flagged for a look). Nothing written here ever goes into the repository; only classes with invented names do.

What it prints: counts only (rows read, right, wrong, unmarked, malformed lines, rows that need a look, classes, rows
per guessed language and per reading). Never a sentence, never a name, never the expected text.

    python3 scripts/hudhud_checklog.py LOG.tsv --out /private/folder            # wrong answers only
    python3 scripts/hudhud_checklog.py LOG.tsv --out /private/folder --all-marked
    python3 scripts/hudhud_checklog.py LOG.tsv --out /private/folder --with-original   # adds the real question (private)

Tests: scripts/test_hudhud_checklog.py (invented data only).
"""
import csv
import hashlib
import importlib.util
import io
import re
import sys
import unicodedata
from collections import Counter, OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BANK_DIRS = [ROOT / "shared/hudhud", ROOT / "shared/hudhud/more"]
HELDOUT = ROOT / "shared/src/jvmSharedTest/resources/hudhud/heldout.tsv"

# CheckLog's own words (HudhudCheck.kt): the header's first field, the two marks, the field count.
HEADER_START = "no\t"
RIGHT, WRONG = "right", "wrong"
FIELDS = ["no", "time", "language", "question", "taken_as", "answer", "mark", "business"]
EXPECTED_HEADS = {"expected", "meant", "should_be", "shouldbe", "sahi", "correct", "expected_meaning"}

# Words of the bank that are the app's own, kept in the vocabulary although the bank writes them capitalised.
APP_WORDS = {"hudhud", "khata"}
# The invented names of the set tests, if hudhud_sets.py cannot be read (it keeps the list the near-duplicate scan uses).
FALLBACK_NAME_WORDS = ["Ali", "Raza", "Bashir", "Ahmad", "Tariq", "Butt", "Naeem", "Khan", "Saleem", "Kamran",
                       "علی", "رضا", "علي", "अली", "रज़ा", "আলী", "রাজা"]

# Readings whose card label is a customer's (NAME) or a product's (ITEM) name, in HudhudCheck.describe's words.
NAMED_READS = {"person:": "NAME", "balance:": "NAME", "goods:": "ITEM"}

ARAB_SD = set("ڄڃڇڊڌڍڏڙڦڪڱڻ۾ٻڀٺٽٿڳ")
ARAB_UR = set("ٹڈڑںےۂۓہھ")
ARAB_AR = set("ةىيك")
ARAB_FA = set("پچژگ")
SCRIPT_LANGS = {"Arab": ["ur", "sd", "fa", "ar"], "Latn": ["en", "ur-Latn", "id"], "Deva": ["hi"], "Beng": ["bn"]}

# A Pakistani mobile number, as check_invented_numbers.py reads one (digits already 0-9).
PK_MOBILE = re.compile(r"(?<![0-9])(?:\+92|0092|92|0)[ \-]?3\d\d[ \-]?\d{7}(?![0-9])")
# Any other long run of digits: international numbers, CNICs, account numbers, big amounts.
DIGIT_RUN = re.compile(r"\+?[0-9](?:[0-9]|[ \-,.٫٬](?=[0-9]))*")
TASHKEEL = re.compile("[ً-ٰٟ‌‍‎‏]")


# ---------------------------------------------------------------------------------------------------------------
# Reading the file.

def decode(raw):
    """The file's text and whether it is a spreadsheet's re-save (UTF-16, quoted fields) rather than the app's own."""
    if raw.startswith(b"\xff\xfe") or raw.startswith(b"\xfe\xff"):
        return raw.decode("utf-16"), True
    if raw.startswith(b"\xef\xbb\xbf"):
        raw = raw[3:]
    try:
        return raw.decode("utf-8"), False
    except UnicodeDecodeError:
        raise SystemExit("hudhud_checklog: the file is neither UTF-8 nor UTF-16; share it again from the app")


def parse(text, quoted=False):
    """Rows as dicts (the eight fields, plus `expected`), and the count of lines that are not a row."""
    reader = csv.reader(io.StringIO(text), delimiter="\t",
                        quoting=csv.QUOTE_MINIMAL if quoted else csv.QUOTE_NONE)
    expected_at = None
    rows, malformed = [], 0
    for fields in reader:
        if not fields or all(not f.strip() for f in fields):
            continue
        fields = [f.lstrip("﻿") for f in fields]
        if (fields[0].strip() == "no") and len(fields) > 1:
            heads = [h.strip().lower() for h in fields]
            expected_at = next((i for i, h in enumerate(heads) if h in EXPECTED_HEADS), None)
            if expected_at is None and len(heads) > len(FIELDS) and heads[len(FIELDS)] == "":
                expected_at = len(FIELDS)
            continue
        if len(fields) < 7 or not fields[0].strip().isdigit():
            malformed += 1
            continue
        row = {name: (fields[i] if i < len(fields) else "") for i, name in enumerate(FIELDS)}
        at = expected_at if expected_at is not None else (len(FIELDS) if len(fields) > len(FIELDS) else None)
        row["expected"] = fields[at].strip() if at is not None and at < len(fields) else ""
        row["mark"] = row["mark"].strip().lower()
        rows.append(row)
    return rows, malformed


# ---------------------------------------------------------------------------------------------------------------
# Words.

def ascii_digits(text):
    """Every script's figures as 0-9, one character for one (positions are kept)."""
    return "".join(str(unicodedata.digit(c)) if unicodedata.category(c) == "Nd" else c for c in text)


def _wordish(c):
    cat = unicodedata.category(c)
    return cat[0] in "LM" or c in "‌‍'’"


def tokens(text):
    """(start, end) of every word: letters with their marks (Devanagari and Bengali vowel signs stay inside the word)."""
    out, i, n = [], 0, len(text)
    while i < n:
        if _wordish(text[i]) and text[i] not in "'’":
            j = i + 1
            while j < n and _wordish(text[j]):
                j += 1
            while j > i + 1 and text[j - 1] in "'’":
                j -= 1
            out.append((i, j))
            i = j
        else:
            i += 1
    return out


def fold(word):
    """A word as the vocabulary keeps it: NFC, case folded, Arabic short-vowel marks and joiners taken off."""
    w = unicodedata.normalize("NFC", word).casefold()
    return TASHKEEL.sub("", w).replace("'", "").replace("’", "")


def script_of_char(c):
    o = ord(c)
    if 0x0600 <= o <= 0x06FF or 0x0750 <= o <= 0x077F or 0xFB50 <= o <= 0xFDFF or 0xFE70 <= o <= 0xFEFF:
        return "Arab"
    if 0x0900 <= o <= 0x097F:
        return "Deva"
    if 0x0980 <= o <= 0x09FF:
        return "Beng"
    if c.isalpha() and o < 0x0250:
        return "Latn"
    return None


def scripts_in(text):
    return Counter(s for s in map(script_of_char, text) if s)


# ---------------------------------------------------------------------------------------------------------------
# What Hudhud knows: the repository's own sentences.

def _load_script(name):
    path = ROOT / "scripts" / f"{name}.py"
    if not path.exists():
        return None
    try:
        spec = importlib.util.spec_from_file_location(f"_checklog_{name}", path)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        return mod
    except Exception:  # noqa: BLE001 - a broken neighbour must not stop the triage; the fallbacks below are used
        return None


def invented_name_words():
    """The set tests' invented names (hudhud_sets.py's word list, and the same names in the other scripts): never
    vocabulary. Not every word of its phrases: "Bhai" and "Traders" are words a question uses."""
    sets = _load_script("hudhud_sets")
    words = list(getattr(sets, "_NAME_WORDS", []) or []) + FALLBACK_NAME_WORDS
    return {fold(w) for w in words}


def own_phrases():
    """The owner's shop and village as check_repo_guards.py keeps them: fingerprints of two lower-case words."""
    guards = _load_script("check_repo_guards")
    return set(getattr(guards, "OWN_PHRASES", set()) or set())


def _bank_sentences():
    """(language, sentence) of the training bank and the held-out set (labels and placeholders are not words)."""
    for d in BANK_DIRS:
        for path in sorted(d.glob("*.tsv")):
            lang = path.stem
            for line in path.read_text(encoding="utf-8").splitlines():
                if not line.strip() or line.startswith("#"):
                    continue
                parts = line.split("\t")
                yield lang, parts[-1]
    if HELDOUT.exists():
        for line in HELDOUT.read_text(encoding="utf-8").splitlines():
            parts = line.split("\t")
            if len(parts) >= 3:
                yield parts[0], parts[2]


def vocabulary(sentences=None, names=None):
    """Per language, the words Hudhud's sentences use; and their union. A word the bank writes capitalised in the
    middle of a Latin sentence (three letters or more) is a name to the bank, and so is never vocabulary."""
    sentences = list(_bank_sentences()) if sentences is None else sentences
    names = invented_name_words() if names is None else names
    per, capitalised = {}, set()
    for lang, sentence in sentences:
        text = sentence.replace("{NAME}", " ").replace("{ITEM}", " ")
        spans = tokens(text)
        for k, (a, b) in enumerate(spans):
            w = text[a:b]
            if k > 0 and w[:1].isupper() and len(w) >= 3 and script_of_char(w[0]) == "Latn":
                capitalised.add(fold(w))
            per.setdefault(lang, set()).add(fold(w))
    drop = (capitalised - APP_WORDS) | names
    per = {lang: words - drop for lang, words in per.items()}
    union = set().union(*per.values()) if per else set()
    return per, union


# ---------------------------------------------------------------------------------------------------------------
# The guesses and the mask.

def guess_language(question, per_lang):
    """The question's script and language, guessed from its letters and from which language's sentences use its words."""
    counts = scripts_in(question)
    if not counts:
        return "none", "unknown"
    ordered = counts.most_common()
    script = ordered[0][0]
    if len(ordered) > 1 and ordered[1][1] >= 2:
        script_label = "mixed:" + "+".join(s for s, _ in ordered)
    else:
        script_label = script
    langs = SCRIPT_LANGS[script]
    if len(langs) == 1:
        return script_label, langs[0]
    words = [fold(question[a:b]) for a, b in tokens(question) if script_of_char(question[a]) == script]
    score = {lang: sum(1 for w in words if w in per_lang.get(lang, ())) for lang in langs}
    if script == "Arab":
        letters = set(question)
        score["sd"] += 3 * bool(letters & ARAB_SD)
        score["ur"] += 3 * bool(letters & ARAB_UR)
        score["ar"] += 2 * bool(letters & ARAB_AR and not letters & (ARAB_UR | ARAB_FA))
        score["fa"] += 1 * bool(letters & ARAB_FA and not letters & ARAB_UR)
    best = max(score.values())
    if best == 0:
        return script_label, "unknown"
    top = [lang for lang in langs if score[lang] == best]
    return script_label, top[0] if len(top) == 1 else "/".join(top)


def read_class(taken_as):
    """What Hudhud read, without the book's ids ("person: BALANCE [12, 40]" -> "person: BALANCE")."""
    t = re.sub(r"\s*\[[0-9,\s]*\]", "", taken_as or "")
    t = re.sub(r"[0-9]{5,}", "#", t)
    return re.sub(r"\s+", " ", t).strip() or "(empty)"


def card_name(taken_as, answer):
    """(the name the card was headed with, NAME or ITEM) when the reading was about a customer or a product; else None."""
    t = (taken_as or "").strip()
    while True:
        m = re.match(r"^(guessed|now) > ", t)
        if not m:
            break
        t = t[m.end():]
    kind = next((k for start, k in NAMED_READS.items() if t.startswith(start)), None)
    label = (answer or "").split(" · ")[0].strip()
    return (label, kind) if kind and label else None


def _card_in(card_text, question):
    """Whether any word of the card's name is a word of the question."""
    words = {fold(question[a:b]) for a, b in tokens(question)}
    return any(fold(card_text[a:b]) in words for a, b in tokens(card_text))


def _shop_hash(a, b):
    return hashlib.sha256((a + " " + b).encode()).hexdigest()[:16]


def mask(text, vocab, card=None, shop_marks=frozenset(), mask_all_numbers=False):
    """[text] with names, numbers and unknown words replaced; the reasons it needs a human look; how many were masked."""
    if not text:
        return "", set(), 0
    digits = ascii_digits(text)
    cuts = []  # (start, end, kind)
    for m in PK_MOBILE.finditer(digits):
        cuts.append((m.start(), m.end(), "PHONE"))
    taken = [False] * len(text)
    for a, b, _ in cuts:
        for i in range(a, b):
            taken[i] = True
    for m in DIGIT_RUN.finditer(digits):
        a, b = m.start(), m.end()
        if any(taken[a:b]):
            continue
        n = sum(c.isdigit() for c in digits[a:b])
        if mask_all_numbers or n >= 5:
            cuts.append((a, b, "NUM"))
            for i in range(a, b):
                taken[i] = True
    spans = [(a, b) for a, b in tokens(text) if not any(taken[a:b])]
    words = [fold(text[a:b]) for a, b in spans]
    card_text, card_kind = card if card else ("", "NAME")
    card_set = {fold(card_text[a:b]) for a, b in tokens(card_text)}
    look = set()
    labels = {}
    counters = Counter()

    def label_for(kind, key):
        if (kind, key) not in labels:
            counters[kind] += 1
            labels[(kind, key)] = "{%s%d}" % (kind, counters[kind])
        return labels[(kind, key)]

    skip = set()
    for k in range(len(spans) - 1):
        if shop_marks and _shop_hash(words[k], words[k + 1]) in shop_marks:
            cuts.append((spans[k][0], spans[k + 1][1], "SHOP"))
            skip.update((k, k + 1))
    for k, (a, b) in enumerate(spans):
        if k in skip:
            continue
        w, raw = words[k], text[a:b]
        if card_set and w in card_set:
            cuts.append((a, b, label_for(card_kind, w)))
        elif k > 0 and raw[:1].isupper() and len(raw) >= 3 and script_of_char(raw[0]) == "Latn" \
                and w not in APP_WORDS and raw != raw.upper():
            cuts.append((a, b, label_for("X", w)))
            look.add("capitalised word")
        elif w not in vocab:
            cuts.append((a, b, label_for("X", w)))
            look.add("word not in Hudhud's vocabulary")
    cuts.sort()
    out, at, masked = [], 0, 0
    for a, b, kind in cuts:
        if a < at:
            continue
        out.append(text[at:a])
        out.append(kind if kind.startswith("{") else "{%s}" % kind)
        at = b
        masked += 1
    out.append(text[at:])
    return "".join(out), look, masked


# ---------------------------------------------------------------------------------------------------------------
# The triage.

def triage(rows, per_lang, vocab, shop_marks=frozenset(), all_marked=False, with_original=False):
    """The rows to write and the classes, from parsed log rows."""
    keep = {WRONG} | ({RIGHT} if all_marked else set())
    out = []
    for r in rows:
        if r["mark"] not in keep:
            continue
        card = card_name(r["taken_as"], r["answer"])
        question, look, _ = mask(r["question"], vocab, card, shop_marks)
        answer, _, _ = mask(r["answer"], vocab, card, shop_marks, mask_all_numbers=True)
        expected, look_e, _ = mask(r["expected"], vocab, card, shop_marks)
        script, lang = guess_language(r["question"], per_lang)
        if card and not _card_in(card[0], r["question"]):
            # The card is about somebody the question may not name: a wrong person, or a name that is also a word.
            look.add("the card's name is not in the question")
        if script.startswith("mixed"):
            look.add("mixed script")
        if r["taken_as"].strip() == "error":
            look.add("Hudhud failed (error)")
        if not r["question"].strip():
            look.add("empty question")
        if look_e:
            look.add("expected text masked")
        row = OrderedDict([
            ("no", r["no"].strip()), ("mark", r["mark"]), ("app_language", r["language"].strip()),
            ("script", script), ("language_guess", lang), ("read_as", read_class(r["taken_as"])),
            ("expected", expected), ("question_masked", question), ("answer_masked", answer),
            ("needs_look", "; ".join(sorted(look))),
        ])
        if with_original:
            row["question_PRIVATE"] = r["question"]
        out.append(row)
    classes = OrderedDict()
    for row in out:
        k = (row["expected"] or "-", row["read_as"])
        c = classes.setdefault(k, {"count": 0, RIGHT: 0, WRONG: 0, "rows": [], "langs": Counter(), "look": 0,
                                   "example": row["question_masked"]})
        c["count"] += 1
        c[row["mark"]] += 1
        c["rows"].append(row["no"])
        c["langs"][row["language_guess"]] += 1
        c["look"] += bool(row["needs_look"])
    ordered = sorted(classes.items(), key=lambda kv: (-kv[1]["count"], kv[0]))
    return out, OrderedDict(ordered)


def _clean(field):
    return re.sub(r"[\t\r\n]+", " ", str(field))


def write(out_dir, stem, rows, classes):
    out_dir.mkdir(parents=True, exist_ok=True)
    rows_path = out_dir / f"{stem}.rows.tsv"
    heads = list(rows[0].keys()) if rows else ["no", "mark", "app_language", "script", "language_guess", "read_as",
                                                "expected", "question_masked", "answer_masked", "needs_look"]
    with rows_path.open("w", encoding="utf-8", newline="") as f:
        f.write("\t".join(heads) + "\n")
        for r in rows:
            f.write("\t".join(_clean(r[h]) for h in heads) + "\n")
    classes_path = out_dir / f"{stem}.classes.tsv"
    with classes_path.open("w", encoding="utf-8", newline="") as f:
        f.write("expected\tread_as\tcount\twrong\tright\tneeds_look\tlanguages\trows\texample_masked\n")
        for (expected, read), c in classes.items():
            langs = ",".join(f"{lang}:{n}" for lang, n in c["langs"].most_common())
            f.write("\t".join(_clean(x) for x in (expected, read, c["count"], c[WRONG], c[RIGHT], c["look"], langs,
                                                  ",".join(c["rows"]), c["example"])) + "\n")
    return rows_path, classes_path


def summary(rows, malformed, written, classes):
    """Counts only: never a sentence, a name or the owner's own words."""
    marks = Counter(r["mark"] for r in rows)
    lines = [
        f"hudhud_checklog: {len(rows)} answers read, {marks[RIGHT]} right, {marks[WRONG]} wrong, "
        f"{len(rows) - marks[RIGHT] - marks[WRONG]} not marked, {malformed} lines that are not a row",
        f"  written: {len(written)} rows, {len(classes)} classes, "
        f"{sum(1 for r in written if r['needs_look'])} rows need a look in the owner's own file",
    ]
    langs = Counter(r["language_guess"] for r in written)
    if langs:
        lines.append("  by language guessed: " + ", ".join(f"{k} {v}" for k, v in langs.most_common()))
    kinds = Counter(r["read_as"].split(":")[0].split(" >")[0] for r in written)
    if kinds:
        lines.append("  by kind of reading: " + ", ".join(f"{k} {v}" for k, v in kinds.most_common()))
    return "\n".join(lines)


def _inside_repo(path):
    p = path.resolve()
    return p == ROOT or ROOT in p.parents


def main(argv):
    args = [a for a in argv if not a.startswith("--")]
    if "--out" in argv:
        i = argv.index("--out")
        if i + 1 >= len(argv):
            raise SystemExit("hudhud_checklog: --out needs a folder")
        out = Path(argv[i + 1])
        args = [a for a in args if a != argv[i + 1]]
    else:
        raise SystemExit("hudhud_checklog: --out FOLDER is required (a private folder outside the repository)")
    if len(args) != 1:
        raise SystemExit(__doc__.split("\n\n")[0] + "\n\nusage: hudhud_checklog.py LOG.tsv --out FOLDER "
                         "[--all-marked] [--with-original]")
    log = Path(args[0])
    if _inside_repo(out):
        raise SystemExit("hudhud_checklog: --out must be a folder OUTSIDE the repository (lesson 120: the log is "
                         "read by class, never copied)")
    if _inside_repo(log):
        raise SystemExit("hudhud_checklog: the owner's log must not be inside the repository; move it to a private "
                         "folder first (lessons 80, 120)")
    if not log.is_file():
        raise SystemExit("hudhud_checklog: no such file")
    text, quoted = decode(log.read_bytes())
    rows, malformed = parse(text, quoted)
    per_lang, vocab = vocabulary()
    written, classes = triage(rows, per_lang, vocab, own_phrases(), all_marked="--all-marked" in argv,
                              with_original="--with-original" in argv)
    rows_path, classes_path = write(out, log.stem, written, classes)
    print(summary(rows, malformed, written, classes))
    print(f"  files: {rows_path.name}, {classes_path.name} in the folder given")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

#!/usr/bin/env python3
"""Hudhud's labelled sentence sets: one JSONL view of them all, and a quality gate (task T27, 16 Nov 2026).

The sets, as they are kept today:

  train     shared/hudhud/<lang>.tsv and shared/hudhud/more/<lang>.tsv (`meaning<TAB>sentence`), the bank the sense
            learns from; gen_hudhud_bank.py keeps two of every ten first-round sentences of a meaning back as
  heldout   the held-out set (shared/src/jvmSharedTest/resources/hudhud/heldout.tsv), the only accuracy figure;
  owner     owner_set.tsv and owner_1000.tsv (the owner's own kind of question; acceptable readings, `|` between);
  review    review_d.tsv and review_o.tsv (a reviewer's sentences, the reading they have now, OK or not yet).

    python3 scripts/hudhud_sets.py --jsonl OUT.jsonl   # every row of every set, one JSON object a line
    python3 scripts/hudhud_sets.py --check             # the quality gate (the strings job runs this)
    python3 scripts/hudhud_sets.py --report            # the gate, plus every overlap and duplicate it knows of
    python3 scripts/hudhud_sets.py --write-overlap     # rewrite the overlap list (it only ever shrinks)
    python3 scripts/hudhud_sets.py --check --near-list 40          # the gate, plus the first 40 near-duplicate pairs
    python3 scripts/hudhud_sets.py --check --sealed /owner/folder  # plus the sealed set v2, from OUTSIDE the repository
    python3 scripts/hudhud_sets.py --check --near-strict           # fail on any near-duplicate of a trained sentence

A JSONL record: id, text, language, split (train / heldout / owner / review), source (file:line), and what the set
says of the sentence — `meaning` (the bank's label) with `ask` (the reading that meaning is answered as, from
Hudhud.kt's own table), or `accepted` (owner sets), or `ask` + `status` (review sets); `context` is the sentence
before a follow-up; `entities` only where the text itself says it (the bank's placeholders {NAME} and {ITEM});
`tags` (follow_up, mixed_script, not_yet, placeholder, language_inferred). Nothing is invented: a field nobody wrote
is absent or null.

The gate fails on
  1. leakage — a train sentence that is also a held-out, owner or review sentence (compared as gen_hudhud_bank.py
     compares: no case, no marks or punctuation, single spaces). The overlap that was already there on 16 Nov 2026 is
     listed in scripts/hudhud_sets_overlap.tsv, sentence by sentence: fixing it means changing the bank, which means
     learning the sense again (lessons 106, 111), so it waits for the next retrain. A new overlap fails, and so does
     a listed one that has gone (take it off the list, so the list stays true);
  2. a phone-number-shaped run of digits that is not the invented family (check_invented_numbers.py's FAMILY);
  3. a label that is not a reading Hudhud can make — a bank meaning not in Hudhud.kt's meaning table, a reading or
     an accepted prefix whose kind is not an Ask, whose value is not in its enum, or a help page that does not exist;
  4. an empty or whitespace sentence, a language code not among the nine, columns out of place (count, ids, status,
     meaning numbers, a sentence column holding a label or a language);
  5. one sentence with two meanings in train + held-out (a label conflict).
Drift protection, not proof (lesson 125): it cannot say that a label is the RIGHT one; docs/hudhud-annotation-guide.md
says how to choose it.

NEAR-DUPLICATES (plan Marhala 3, 25 Nov 2026; REPORT ONLY unless --near-strict). Every --check also compares the sets'
sentences after a stronger fold (`near_key`: the leakage key, then {NAME}/{ITEM} and the test books' invented customer and
product names in their scripts masked as one name and one product word, every run of digits one number, spaces folded) and
by character 3-grams (Jaccard of the folded sentence's 3-grams, padded with a space, >= NEAR_JACCARD = 0.80). It counts the
pairs between the families train, heldout, owner, review and (with --sealed) sealed — never two sentences of one family — and
prints one line per pair of families: "same after masking" (Jaccard 1) and "near" (>= 0.80). The exit code is unchanged by
them unless --near-strict, which fails on any pair with a TRAIN sentence (the known overlap list keeps exact leakage apart).
`--sealed DIR` reads every *.tsv and *.txt in DIR, a folder of the owner's outside the repository (D12, D18): one sentence a
line, or the column with the most letters; `#` lines are skipped. For the sealed set only counts and the sealed file's own
line numbers are printed, never a sentence and never the repository sentence it meets (plan "Never" 9: nothing is fixed by
looking at the sealed set). Cross-script twins (Roman Urdu and Urdu script) are not compared here: their letters differ; the
calibration's folds (scripts/hudhud_calib_folds.py) keep those together by place instead.
"""
import importlib.util
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "shared/src/jvmSharedTest/resources/hudhud"
ENGINE = ROOT / "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data"
OVERLAP = ROOT / "scripts/hudhud_sets_overlap.tsv"
APP_HUDHUD = ROOT / "app/src/main/java/com/innovation313/roshankhata"

# The nine languages. Indonesian is "id" in the bank's file names and "in" (Android's folder) in the review sets.
LANGUAGES = {"en", "ur", "ur-Latn", "hi", "bn", "ar", "fa", "sd", "id"}
ALIASES = {"in": "id"}
SCRIPT_OF = {"en": "Latn", "ur-Latn": "Latn", "id": "Latn", "ur": "Arab", "sd": "Arab", "ar": "Arab", "fa": "Arab",
             "hi": "Deva", "bn": "Beng"}
STATUSES = {"OK", "MISS", "GUESS", "NOW"}
PLACEHOLDERS = {"{NAME}": "person", "{ITEM}": "product"}
PLACEHOLDER = re.compile(r"\{(?:NAME|ITEM)\}")


def _load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


BANK = _load("gen_hudhud_bank")
NUMBERS = _load("check_invented_numbers")
# Compared as the shipped recipe compares (recipe 2 keeps Devanagari and Bengali marks: "कि" is not "के").
key = BANK.key if BANK.DEFAULT_RECIPE == 1 else BANK.key_indic


# ---------------------------------------------------------------------------------------------------------------
# What Hudhud can read, from his own code (so the gate follows the engine, never a copy of it).

def _enum(text, name):
    m = re.search(r"enum class " + re.escape(name) + r"\s*\{(.*?)(?:;|\n\s*\})", text, re.S)
    if not m:
        raise SystemExit(f"hudhud_sets: enum {name} not found")
    body = re.sub(r"/\*.*?\*/", "", m.group(1), flags=re.S)
    body = re.sub(r"//[^\n]*", "", body)
    return {w for w in re.findall(r"\b[A-Z][A-Z0-9_]*\b", body)}


def _block(text, start):
    """The text of the brace block that opens on the line holding [start]."""
    i = text.index(start)
    j = text.index("{", i)
    depth = 0
    for k in range(j, len(text)):
        depth += {"{": 1, "}": -1}.get(text[k], 0)
        if depth == 0:
            return text[j + 1:k]
    raise SystemExit(f"hudhud_sets: unclosed block after {start!r}")


class Engine:
    def __init__(self):
        hud = (ENGINE / "Hudhud.kt").read_text(encoding="utf-8")
        vq = (ENGINE / "VoiceQuestion.kt").read_text(encoding="utf-8")
        ask = _block(hud, "sealed class Ask")
        self.kinds = set(re.findall(r"^\s{8}(?:data\s+)?(?:class|object)\s+(\w+)", ask, re.M))
        # The meaning table: `"label" -> Ask...` inside `return when (label) {`.
        table = _block(hud, "return when (label)")
        self.meanings = {}
        for labels, rhs in re.findall(r'^\s*("[a-z_]+"(?:\s*,\s*"[a-z_]+")*)\s*->\s*(.+)$', table, re.M):
            for label in re.findall(r'"([a-z_]+)"', labels):
                self.meanings[label] = self._shown(rhs)
        # A meaning whose branch is a block (`"entry_gave", "entry_got" -> {`) is read by the Ask it builds.
        for label, shown in list(self.meanings.items()):
            if shown is None and label.startswith("entry_"):
                self.meanings[label] = "Entry"
        if not self.kinds or len(self.meanings) < 40:
            raise SystemExit("hudhud_sets: could not read Hudhud.kt's Ask kinds or meaning table")
        kind = _enum(vq, "Kind")
        window = _enum(vq, "Window")
        facet = _enum(hud, "Facet")
        # The second part of a reading, as HudhudShown spells it (tests' HudhudShown.of).
        self.values = {
            "Figure": [kind], "Count": [_enum(hud, "Thing"), window | {"null"}], "Status": [_enum(hud, "StatusOf")],
            "Person": [facet], "Goods": [_enum(hud, "GoodsFacet")], "Shop": [_enum(hud, "ShopFacet")],
            "About": [_enum(hud, "AboutTopic")], "Chat": [_enum(hud, "Talk")], "NeedName": [facet | {"null"}],
            "Period": [_enum(hud, "Measure")], "Help": [self._pages()],
        }
        # Tags the entity metrics read from an accepted list besides readings ("PaidOut": money went out), named in
        # HudhudEntities.sideOfShown: they are no reading, but they say the side a row expects.
        entities = (ROOT / "shared/src/jvmSharedTest/kotlin/com/innovation313/roshankhata/data/HudhudEntities.kt")
        self.side_tags = set(re.findall(r'shown == "([A-Za-z]+)"', entities.read_text(encoding="utf-8"))) - self.kinds

    @staticmethod
    def _shown(rhs):
        """`Ask.Figure(VoiceQuestion.Kind.TO_GET)` -> "Figure:TO_GET"; `figureOver(VoiceQuestion.Kind.X, …)` -> "Figure:X"."""
        m = re.match(r"figureOver\((?:\w+\.)*(\w+)", rhs)
        if m:
            return "Figure:" + m.group(1)
        m = re.match(r"Ask\.(\w+)(?:\((?:\w+\.)*([A-Z][A-Z0-9_]*)\b)?", rhs)
        if not m:
            return None
        return m.group(1) + (":" + m.group(2) if m.group(2) else "")

    @staticmethod
    def _pages():
        pages = {l.split("\t")[0] for l in (RES / "pages.tsv").read_text(encoding="utf-8").splitlines()
                 if l.strip() and not l.startswith("#")}
        for path in list(ENGINE.glob("Hudhud*.kt")) + list(APP_HUDHUD.rglob("Hudhud*.kt")):
            pages |= set(re.findall(r'"((?:screen|intro|faq)_[a-z0-9_]+)"', path.read_text(encoding="utf-8")))
        return pages

    def reading_problem(self, reading, prefix):
        """Why [reading] is no reading Hudhud can make (None when it is one). A [prefix] (an owner set's accepted
        answer) may stop part way through its last part: "Figure:SALES" accepts "Figure:SALES_TODAY"."""
        parts = reading.split(":")
        head, rest = parts[0], parts[1:]
        if head not in self.kinds:
            return f"'{head}' is not a kind of Hudhud.Ask"
        allowed = self.values.get(head, [])
        if len(rest) > len(allowed):
            return f"'{reading}' has more parts than a {head} reading has"
        for i, part in enumerate(rest):
            last = i == len(rest) - 1
            ok = part in allowed[i] or (prefix and last and any(v.startswith(part) for v in allowed[i]))
            if not ok:
                return f"'{part}' is not a {head} value" + (" (no such help page)" if head == "Help" else "")
        return None


# ---------------------------------------------------------------------------------------------------------------
# Reading the sets.

def _rows(path):
    for n, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if raw.strip() and not raw.startswith("#"):
            yield n, raw.split("\t")


def scripts_of(text):
    found = set()
    for c in text:
        if not c.isalpha():
            continue
        name = unicodedata.name(c, "")
        for word, script in (("ARABIC", "Arab"), ("DEVANAGARI", "Deva"), ("BENGALI", "Beng"), ("LATIN", "Latn")):
            if word in name:
                found.add(script)
                break
        else:
            found.add(name.split(" ")[0] if name else "?")
    return found


def _tags(text, language, context=None):
    tags = []
    if context:
        tags.append("follow_up")
    letters = scripts_of(PLACEHOLDER.sub("", text))
    if len(letters) > 1:
        tags.append("mixed_script")
    if any(p in text for p in PLACEHOLDERS):
        tags.append("placeholder")
    return tags


def _entities(text):
    found = {role: p for p, role in PLACEHOLDERS.items() if p in text}
    return found or None


def records(engine, problems):
    """Every row of every set as a record; a row that cannot be read is added to [problems] and left out."""
    out = []

    def bad(where, why):
        problems.append(f"{where}: {why}")

    # train and held-out, by the bank's own reader (gen_hudhud_bank.read and numbered_lines_of), each row traced back to
    # its file and line; no second parser of the bank's files.
    first, more = {}, {}
    for lang in BANK.LANGS:
        for folder, into in ((BANK.SRC, first), (BANK.MORE, more)):
            path = folder / f"{lang}.tsv"
            if path.exists():
                for n, label, text in BANK.numbered_lines_of(path):
                    into.setdefault((lang, label, text), f"{path.relative_to(ROOT)}:{n}")
    bank, held = BANK.read()
    for split, rows in (("train", bank), ("heldout", held)):
        for i, (lang, label, text) in enumerate(rows):
            src = first.get((lang, label, text)) or more.get((lang, label, text)) or "?"
            out.append({"id": f"{split}-{lang}-{i:05d}", "text": text, "language": lang, "split": split,
                        "source": src, "meaning": label, "ask": engine.meanings.get(label),
                        "entities": _entities(text), "tags": _tags(text, lang)})

    # The owner's sets: Roman Urdu as he wrote them (said by the script, never by guess: a row in Arabic script would
    # be "ur"); the language is marked inferred.
    for name, meaning_col in (("owner_set.tsv", None), ("owner_1000.tsv", 2)):
        path = RES / name
        want = 3 if meaning_col is None else 4
        for n, cols in _rows(path):
            where = f"{name}:{n}"
            if len(cols) != want:
                bad(where, f"{len(cols)} columns, expected {want}")
                continue
            rid, text, accepted = cols[0], cols[1], cols[-1]
            if not re.fullmatch(r"[A-Z]\d{3,4}", rid):
                bad(where, f"id '{rid}' is not a letter and three or four digits")
            if meaning_col is not None and not re.fullmatch(r"[1-9]\d?", cols[meaning_col]):
                bad(where, f"meaning '{cols[meaning_col]}' is not a number 1-99")
            lang = "ur" if "Arab" in scripts_of(text) else "ur-Latn"
            rec = {"id": f"owner-{rid}", "text": text, "language": lang, "split": "owner", "source": where,
                   "accepted": accepted.split("|"), "entities": None,
                   "tags": _tags(text, lang) + ["language_inferred"]}
            if meaning_col is not None:
                rec["meaning"] = cols[meaning_col]
            out.append(rec)

    # The review sets: language, status, sentence, the sentence before it, the reading now.
    for name in ("review_d.tsv", "review_o.tsv"):
        tag = name[len("review_"):-len(".tsv")]
        for n, cols in _rows(RES / name):
            where = f"{name}:{n}"
            if len(cols) != 5:
                bad(where, f"{len(cols)} columns, expected 5 (language, status, sentence, before, reading)")
                continue
            lang_raw, status, text, before, reading = cols
            lang = ALIASES.get(lang_raw, lang_raw)
            if status not in STATUSES:
                bad(where, f"status '{status}' is not one of {sorted(STATUSES)}")
            tags = _tags(text, lang, before) + ([] if status == "OK" else ["not_yet"])
            out.append({"id": f"review-{tag}-{n:04d}", "text": text, "language": lang, "split": "review",
                        "source": where, "ask": reading, "status": status, "context": before or None,
                        "entities": None, "tags": tags})
    return out


# ---------------------------------------------------------------------------------------------------------------
# The gate.

LABEL_LIKE = re.compile(r"^[A-Z][A-Za-z]+(?::[A-Za-z0-9_]+)*$")
DIGITS = re.compile(r"(?<![\w])(\+|00)?\d(?:[ \-]?\d){6,}(?![\w])")


def phone_problem(text):
    """A run of digits shaped like a phone number that is not the invented family; amounts (seven or more digits
    written plainly, as "1000000") are not phone-shaped."""
    plain = NUMBERS.ascii_digits(text)
    for op, sub, written in NUMBERS.numbers_in(plain):
        if (op, sub) not in NUMBERS.FAMILY:
            return f"'{written.strip()}' looks like a phone number (only the invented 0300 1234567 family may be used)"
    for m in DIGITS.finditer(plain):
        digits = re.sub(r"\D", "", m.group(0))
        international = bool(m.group(1)) and len(digits) >= 9
        trunk = digits.startswith("0") and 10 <= len(digits) <= 12
        if international or trunk or len(digits) >= 10:
            if not any((op, sub) in NUMBERS.FAMILY for op, sub, _ in NUMBERS.numbers_in(m.group(0))):
                return f"'{m.group(0)}' looks like a phone number (only the invented 0300 1234567 family may be used)"
    return None


def overlaps(recs):
    """(set name, folded key) for every train sentence that is also a sentence of another set."""
    train = {key(r["text"]) for r in recs if r["split"] == "train"}
    found = set()
    for r in recs:
        if r["split"] == "train":
            continue
        k = key(r["text"])
        if k in train:
            found.add(("heldout.tsv" if r["split"] == "heldout" else r["source"].split(":")[0], k))
    return found


def known_overlaps():
    if not OVERLAP.exists():
        return set()
    return {tuple(l.split("\t", 1)) for l in OVERLAP.read_text(encoding="utf-8").splitlines()
            if l.strip() and not l.startswith("#")}


def check(engine, recs, problems):
    for r in recs:
        where, text = r["source"], r["text"]
        if not text.strip():
            problems.append(f"{where}: empty sentence")
            continue
        if r["language"] not in LANGUAGES:
            problems.append(f"{where}: language '{r['language']}' is not one of the nine")
        elif r["split"] != "owner":
            own = SCRIPT_OF[r["language"]]
            if own not in scripts_of(text) and scripts_of(PLACEHOLDER.sub("", text)):
                problems.append(f"{where}: a {r['language']} sentence with no {own} letter (columns or language mixed up?)")
        if LABEL_LIKE.match(text) and engine.reading_problem(text, prefix=False) is None:
            problems.append(f"{where}: the sentence column holds a reading ('{text}'): columns mixed up?")
        if text.strip() in {"ur-Latn"} | STATUSES:
            problems.append(f"{where}: the sentence column holds '{text}': columns mixed up?")
        for field in ("text", "context"):
            if r.get(field):
                why = phone_problem(r[field])
                if why:
                    problems.append(f"{where}: {field} {why}")
        if "meaning" in r and r["split"] in ("train", "heldout"):
            if r["meaning"] not in engine.meanings:
                problems.append(f"{where}: meaning '{r['meaning']}' is not in Hudhud.kt's meaning table")
        if r["split"] == "owner":
            for a in r["accepted"]:
                if a in engine.side_tags:
                    continue
                why = engine.reading_problem(a, prefix=True) if a else "an empty accepted reading"
                if why:
                    problems.append(f"{where}: accepted {why}")
        if r["split"] == "review":
            why = engine.reading_problem(r["ask"], prefix=False) if r["ask"] else "no reading"
            if why:
                problems.append(f"{where}: reading {why}")

    # One sentence, one meaning (train and held-out).
    meanings = defaultdict(set)
    for r in recs:
        if r["split"] in ("train", "heldout"):
            meanings[key(r["text"])].add(r["meaning"])
    for k, labels in sorted(meanings.items()):
        if len(labels) > 1:
            problems.append(f"label conflict: '{k}' is {sorted(labels)}")

    # Leakage, against the list of what was already there.
    now, known = overlaps(recs), known_overlaps()
    for name, k in sorted(now - known):
        problems.append(f"leakage: '{k}' is a train sentence and a {name} sentence (a set measured must not be learnt)")
    for name, k in sorted(known - now):
        problems.append(f"{OVERLAP.name}: '{k}' ({name}) no longer overlaps: take it off the list")
    return now


def duplicates_within(recs):
    out = []
    for split in ("owner", "review"):
        seen = defaultdict(list)
        for r in recs:
            if r["split"] == split:
                seen[(r["source"].split(":")[0], key(r["text"]), r.get("context") or "")].append(r["source"])
        out += [(k, v) for k, v in seen.items() if len(v) > 1]
    return out


# ---------------------------------------------------------------------------------------------------------------
# Near-duplicates (report only by default).

NEAR_JACCARD = 0.80
# The invented books of the set tests (HudhudOwnerSetTest, HudhudSenseTest and their copies in the shared harness): two-word
# names first, so "Saleem Bhai" is one name and "bhai" alone stays a word.
_NAME_PHRASES = ["Ali Raza", "Bashir Ahmad", "Tariq Butt", "Naeem Khan", "Saleem Bhai", "Kamran Traders",
                 "علی رضا", "علي رضا", "अली रज़ा", "আলী রাজা"]
_NAME_WORDS = ["Ali", "Raza", "Bashir", "Ahmad", "Tariq", "Butt", "Naeem", "Khan", "Saleem", "Kamran"]
_GOODS_WORDS = ["Urea", "DAP", "Sugar", "Paracetamol", "Atta", "Ghee", "یوریا", "يوريا", "यूरिया", "ইউরিয়া"]
_NAME_TOKEN, _ITEM_TOKEN, _NUM_TOKEN = "qqname", "qqitem", "qqnum"
_PHRASES = None


def near_key(text):
    """The sentence folded for the near-duplicate scan: the leakage key, names, products and numbers masked."""
    global _PHRASES
    if _PHRASES is None:
        _PHRASES = ([(key(p), _NAME_TOKEN) for p in _NAME_PHRASES],
                    {key(w): _NAME_TOKEN for w in _NAME_WORDS} | {key(w): _ITEM_TOKEN for w in _GOODS_WORDS})
    phrases, words = _PHRASES
    t = text.replace("{NAME}", f" {_NAME_TOKEN} ").replace("{ITEM}", f" {_ITEM_TOKEN} ")
    t = "".join("0" if unicodedata.category(c) == "Nd" else c for c in t)
    t = " " + key(t) + " "
    for p, tok in phrases:
        t = t.replace(f" {p} ", f" {tok} ")
    out = []
    for w in t.split():
        w = words.get(w, w)
        w = _NUM_TOKEN if re.fullmatch(r"0+(?:[.,]0+)*", w) else w
        if not (w == _NUM_TOKEN and out and out[-1] == _NUM_TOKEN):
            out.append(w)
    return " ".join(out)


def _grams(k):
    b = f" {k} "
    return {b[i:i + 3] for i in range(len(b) - 2)} if len(b) >= 3 else {b}


def _family(r):
    return {"train": "train", "heldout": "heldout", "owner": "owner", "review": "review"}.get(r["split"], r["split"])


def sealed_records(folder):
    """The sealed set v2 from the owner's folder (never the repository): one sentence a line, else the column with most letters."""
    root = Path(folder).expanduser()
    if not root.is_dir():
        raise SystemExit(f"hudhud_sets: --sealed {folder}: not a folder")
    if ROOT in root.resolve().parents or root.resolve() == ROOT:
        raise SystemExit("hudhud_sets: --sealed must be a folder OUTSIDE the repository (D12, D18)")
    out = []
    for path in sorted(list(root.glob("*.tsv")) + list(root.glob("*.txt"))):
        for n, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if not raw.strip() or raw.startswith("#"):
                continue
            text = max(raw.split("\t"), key=lambda c: sum(ch.isalpha() for ch in c))
            out.append({"id": f"sealed-{path.name}-{n}", "text": text, "split": "sealed", "source": f"{path.name}:{n}"})
    return out


def near_duplicates(recs, threshold=NEAR_JACCARD):
    """Pairs (a, b, jaccard) of sentences of two different families with Jaccard >= threshold of their folded 3-grams.
    Prefix filtering (the rarest grams first) keeps it to seconds: a pair at or above the threshold shares one of the first
    |A| - ceil(t|A|) + 1 grams of A."""
    import math
    docs = []
    for r in recs:
        k = near_key(r["text"])
        if k:
            docs.append((r, _family(r), k, _grams(k)))
    freq = Counter(g for _, _, _, gs in docs for g in gs)
    postings = defaultdict(list)
    for i, (_, _, _, gs) in enumerate(docs):
        for g in gs:
            postings[g].append(i)
    found = {}
    for i, (r, fam, k, gs) in enumerate(docs):
        if fam == "train":
            continue
        order = sorted(gs, key=lambda g: (freq[g], g))
        prefix = order[:len(order) - math.ceil(threshold * len(order)) + 1]
        cands = set()
        for g in prefix:
            cands.update(postings[g])
        for j in cands:
            if j == i:
                continue
            r2, fam2, k2, gs2 = docs[j]
            if fam2 == fam:
                continue
            if not (threshold * len(gs) <= len(gs2) <= len(gs) / threshold):
                continue
            inter = len(gs & gs2)
            jac = inter / (len(gs) + len(gs2) - inter)
            if jac >= threshold:
                a, b = (j, i) if j < i else (i, j)
                found[(a, b)] = (docs[a][0], docs[b][0], 1.0 if k == k2 else jac)
    return list(found.values())


def near_report(pairs, listed=0):
    """One line per pair of families; with [listed], that many example pairs (never a sealed sentence)."""
    fams = ["train", "heldout", "owner", "review", "sealed"]
    by = defaultdict(lambda: [0, 0, 0])
    for a, b, jac in pairs:
        fa, fb = sorted((_family(a), _family(b)), key=fams.index)
        by[(fa, fb)][0 if key(a["text"]) == key(b["text"]) else 1 if jac >= 1.0 else 2] += 1
    lines = [f"near-duplicates (pairs of sentences; Jaccard of 3-grams >= {NEAR_JACCARD}, names/products/numbers masked; report only):"]
    for (fa, fb), (exact, same, near) in sorted(by.items(), key=lambda kv: (fams.index(kv[0][0]), fams.index(kv[0][1]))):
        lines.append(f"  {fa} x {fb}: {exact} exact (the leakage key), {same} the same once masked, {near} near")
    if not by:
        lines.append("  none")
    if listed:
        shown = 0
        for a, b, jac in sorted(pairs, key=lambda p: (-p[2], p[0]["source"], p[1]["source"])):
            if shown >= listed:
                break
            if "sealed" in (_family(a), _family(b)):
                s = a if _family(a) == "sealed" else b
                o = b if s is a else a
                lines.append(f"  {jac:.2f} sealed {s['source']} ~ a {_family(o)} sentence (not shown: plan Never 9)")
            else:
                lines.append(f"  {jac:.2f} {a['source']} '{a['text']}' ~ {b['source']} '{b['text']}'")
            shown += 1
    return lines


def _arg(argv, name):
    return argv[argv.index(name) + 1] if name in argv and argv.index(name) + 1 < len(argv) else None


def main(argv):
    engine, problems = Engine(), []
    recs = records(engine, problems)
    if "--jsonl" in argv:
        out = Path(argv[argv.index("--jsonl") + 1])
        with out.open("w", encoding="utf-8") as f:
            for r in recs:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")
        print(f"{len(recs)} records written to {out}: " +
              ", ".join(f"{s} {n}" for s, n in sorted(Counter(r['split'] for r in recs).items())))
        if "--check" not in argv:
            return 0
    if "--write-overlap" in argv:
        # The list may only shrink: once it exists, a new overlap is never written into it by this flag.
        now = overlaps(recs)
        if OVERLAP.exists():
            now &= known_overlaps()
        lines = ["# Train sentences that were also held-out, owner or review sentences on 16 Nov 2026 (hudhud_sets.py).",
                 "# Each goes when the bank is next changed and the sense learnt again; nothing may be added by hand.",
                 "# set<TAB>sentence as compared (no case, marks or punctuation)"]
        lines += [f"{name}\t{k}" for name, k in sorted(now)]
        OVERLAP.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"{len(now)} overlaps written to {OVERLAP.relative_to(ROOT)}")
        return 0
    now = check(engine, recs, problems)
    near_lines = []
    if "--check" in argv or "--report" in argv:
        scanned = recs + (sealed_records(_arg(argv, "--sealed")) if _arg(argv, "--sealed") else [])
        pairs = near_duplicates(scanned)
        listed = int(_arg(argv, "--near-list") or (20 if "--report" in argv else 0))
        near_lines = near_report(pairs, listed)
        if "--near-strict" in argv:
            for a, b, jac in pairs:
                if "train" in (_family(a), _family(b)):
                    o = b if _family(a) == "train" else a
                    where = o["source"] if _family(o) != "sealed" else f"sealed {o['source']}"
                    problems.append(f"near-duplicate ({jac:.2f}) of a train sentence: {where}")
        print("\n".join(near_lines))
    if "--report" in argv:
        for name, n in sorted(Counter(name for name, _ in now).items()):
            print(f"known overlap with train: {name} {n}")
        for (src, k, ctx), where in duplicates_within(recs):
            print(f"duplicate within {src}: '{k}'{' after ' + repr(ctx) if ctx else ''} at {', '.join(where)}")
    if problems:
        print("\n".join(problems))
        print(f"hudhud sets: {len(problems)} problem(s)")
        return 1
    print(f"hudhud sets OK: {len(recs)} sentences ({', '.join(f'{s} {n}' for s, n in sorted(Counter(r['split'] for r in recs).items()))}); "
          f"{len(now)} known train overlaps listed in {OVERLAP.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

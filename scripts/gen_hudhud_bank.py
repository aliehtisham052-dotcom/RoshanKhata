#!/usr/bin/env python3
"""
Writes Hudhud's example bank for the tests (13 Nov 2026): the sentences in shared/hudhud/*.tsv
(`label<TAB>sentence`, one language a file, written by Claude Fable on the owner's yes) go into
shared/src/jvmSharedTest/resources/hudhud/bank.tsv, and every fifth sentence of each meaning is kept back as the
held-out set the tests measure Hudhud with (heldout.tsv beside it) — sentences he never saw, so the accuracy said
is not his memory of the bank.

The app does not carry the bank: HudhudModelTest learns Hudhud's sense from bank.tsv and holds the model the app
ships (app/src/main/assets/hudhud/sense.bin) to what it learnt. After a change to the bank, the model is learnt
again and written with the same code (see HudhudModelTest's own note).

The second round (shared/hudhud/more/*.tsv, the same shape) goes into the bank whole: the held-out set stays the
first round's, so a figure measured before and after can be compared. A sentence of it that is one of the held-out
sentences (or one already in the bank), compared without case, marks and spaces, is left out — else the test would
measure the bank's memory again.

    python3 scripts/gen_hudhud_bank.py          # write both files
    python3 scripts/gen_hudhud_bank.py --check  # fail if either is stale (the strings job runs this)

Two recipes of the bank (25 Nov 2026, plan H1; the owed retrain L017 of 23 Nov):

  recipe 1  what ships today (DEFAULT_RECIPE): the sentence key drops EVERY mark, so two Hindi or Bengali sentences that differ
            only by a vowel sign (a matra) count as one and the second is dropped (43 distinct sentences lost); and a
            first-round sentence that is also a held-out sentence stays in the bank (9 lines, 6 distinct sentences), so the
            held-out figure measures a little of the bank's memory.
  recipe 2  the stable recipe for the next retrain: `key_indic` keeps the marks of U+0900-U+09FF (Devanagari, Bengali) and
            still drops every other mark (Arabic harakat, Latin accents) and punctuation; a first-round sentence whose
            key is a held-out key is left out of the bank. The held-out set itself is the same under both recipes.

Recipe 2 is not shipped by this task (the bank and sense.bin move together, in the one real retrain, plan H4). It is
written only where asked, and --check always proves its coverage next to the shipped files' currency:

    python3 scripts/gen_hudhud_bank.py --recipe 2 --out-dir /tmp/bank2   # bank.tsv and heldout.tsv of recipe 2 there
    python3 scripts/gen_hudhud_bank.py --recipe 2                        # H4: write recipe 2 over the shipped files (flip DEFAULT_RECIPE)

H4 shipped recipe 2 (DEFAULT_RECIPE = 2, 25 Nov 2026): the Indic-safe key and the held-out copies left out are DONE. What
the next retrain still owes (Marhala 8, 26 Nov 2026; scripts/hudhud_sets_overlap.tsv says "each goes when the bank is next
changed and the sense learnt again"):

  recipe 3  recipe 2, and a bank sentence whose key_indic is a sentence of a MEASURED set (owner_set.tsv, owner_1000.tsv,
            review_d.tsv, review_o.tsv: the sentence column, not the context column) is left out of the bank too, first round
            and second round alike, so the owner's floors and the review rows stop measuring the bank's memory. The held-out
            set is the same. Prepared, not applied: only `--recipe 3` / `--next-key` writes it, and scripts/hudhud_cycle.sh
            --next-key --apply is the one road that makes it the default (it flips DEFAULT_RECIPE, rewrites the overlap list,
            which may only shrink, and learns the sense again in the same step).

    python3 scripts/gen_hudhud_bank.py --next-key --out-dir /tmp/bank3   # recipe 3's bank and held-out there (same as --recipe 3)
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "shared" / "hudhud"
MORE = SRC / "more"
OUT = ROOT / "shared/src/jvmSharedTest/resources/hudhud/bank.tsv"
HELD = ROOT / "shared/src/jvmSharedTest/resources/hudhud/heldout.tsv"
LANGS = ["en", "ur", "ur-Latn", "hi", "bn", "ar", "fa", "sd", "id"]
# Of every meaning's ten sentences in a language, these two are held out (positions 0-9 as written).
HELD_OUT = {3, 8}
# What a plain run writes and --check holds the shipped files to. H4 (the one real retrain) flips this to 2 with the new sense.bin.
DEFAULT_RECIPE = 2


def numbered_lines_of(path):
    """(line number, meaning, sentence) for every line of a bank file; scripts/hudhud_sets.py reads the bank through this."""
    out = []
    for n, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not raw.strip():
            continue
        label, _, text = raw.partition("\t")
        label, text = label.strip(), " ".join(text.split())
        if not label or not text or "\t" in text:
            raise SystemExit(f"{path.name}: a line without a meaning and a sentence: {raw!r}")
        out.append((n, label, text))
    return out


def lines_of(path):
    return [(label, text) for _, label, text in numbered_lines_of(path)]


def key(text):
    """A sentence as compared for sameness: no case, no marks or punctuation, single spaces."""
    import unicodedata
    t = unicodedata.normalize("NFD", text.lower())
    t = "".join(c for c in t if unicodedata.category(c)[0] not in "MP")
    return " ".join(t.split())


def key_indic(text):
    """Recipe 2's key: as [key], but the marks of Devanagari and Bengali (U+0900-U+09FF: vowel signs, nukta, virama, candrabindu)
    stay, because they are part of the letters there: "कि" and "के", "বাড়ি" and "বাড়" are different words. Arabic-script marks
    (harakat, hamza signs), Latin accents and punctuation are still dropped."""
    import unicodedata
    t = unicodedata.normalize("NFD", text.lower())
    t = "".join(c for c in t if unicodedata.category(c)[0] not in "MP" or (unicodedata.category(c)[0] == "M" and 0x0900 <= ord(c) <= 0x09FF))
    return " ".join(t.split())


# The sets Hudhud is measured with (recipe 3 leaves their sentences out of the bank): file name -> the sentence column.
MEASURED = {"owner_set.tsv": 1, "owner_1000.tsv": 1, "review_d.tsv": 2, "review_o.tsv": 2}
MEASURED_DIR = OUT.parent  # where they live (a candidate bank written elsewhere is still measured by the repository's sets)


def measured_keys(res=None):
    """key_indic of every sentence of the measured sets (MEASURED), read from [res] (the tests' resources folder by default)."""
    res = MEASURED_DIR if res is None else Path(res)
    keys = set()
    for name, col in MEASURED.items():
        path = res / name
        if not path.exists():
            raise SystemExit(f"gen_hudhud_bank: {path} is missing (recipe 3 needs every measured set)")
        for raw in path.read_text(encoding="utf-8").splitlines():
            if not raw.strip() or raw.startswith("#"):
                continue
            cols = raw.split("\t")
            if len(cols) > col and cols[col].strip():
                keys.add(key_indic(cols[col]))
    keys.discard("")
    return keys


def read(recipe=None):
    """(bank, held) as (language, meaning, sentence) rows; [recipe] is 1 (as recipe 1 wrote it), 2 (shipped since H4) or 3
    (the next retrain's: recipe 2 without the measured sets' sentences; see the header)."""
    recipe = DEFAULT_RECIPE if recipe is None else recipe
    if recipe not in (1, 2, 3):
        raise SystemExit(f"gen_hudhud_bank: no recipe {recipe}")
    same = key if recipe == 1 else key_indic
    bank, held = [], []
    for lang in LANGS:
        path = SRC / f"{lang}.tsv"
        if not path.exists():
            continue
        seen = {}
        for label, text in lines_of(path):
            i = seen.get(label, 0)
            seen[label] = i + 1
            (held if i in HELD_OUT else bank).append((lang, label, text))
    measured = measured_keys() if recipe == 3 else set()
    if recipe >= 2:
        # A first-round sentence that is a held-out sentence is not taught: the held-out figure would measure memory.
        held_keys = {same(t) for _, _, t in held}
        bank = [b for b in bank if same(b[2]) not in held_keys and same(b[2]) not in measured]
    taken = {same(t) for _, _, t in held} | {same(t) for _, _, t in bank} | measured
    for lang in LANGS:
        path = MORE / f"{lang}.tsv"
        if not path.exists():
            continue
        for label, text in lines_of(path):
            k = same(text)
            if k in taken:
                continue
            taken.add(k)
            bank.append((lang, label, text))
    return bank, held


def coverage_problems():
    """Recipe 2's two repairs, proved on the real files and on a synthetic pair each; a list of what is wrong (empty: fine).

    1. Nothing is lost to the marks: every sentence recipe 2 leaves out of the bank has a twin (the same key_indic) in the held-out
       set or earlier in the bank — never a sentence that only differs by a Devanagari or Bengali vowel sign.
    2. No held-out sentence is taught: no bank sentence has a held-out key under key_indic (and none under the old key either,
       except the Arabic-script/Latin ones the old key folded together, which recipe 2 also leaves out).
    """
    problems = []
    # synthetic: a matra is a letter's part, a harakat is not, punctuation never is
    for a, b in [("कि", "के"), ("बाड़ी", "बाडी"), ("বাড়ি", "বাড়"), ("কি", "কে")]:
        if key(a) != key(b):
            problems.append(f"the old key was meant to fold {a!r} and {b!r}: the check's own premise broke")
        if key_indic(a) == key_indic(b):
            problems.append(f"key_indic folds {a!r} and {b!r}: a vowel sign is lost")
    for a, b in [("كَتَبَ", "كتب"), ("Café!", "cafe"), ("what?", "WHAT")]:
        if key_indic(a) != key_indic(b):
            problems.append(f"key_indic keeps a mark it should drop: {a!r} vs {b!r}")
    bank1, held1 = read(1)
    bank2, held2 = read(2)
    if held1 != held2:
        problems.append("the held-out set differs between recipe 1 and recipe 2")
    held_keys = {key_indic(t) for _, _, t in held2}
    for lang, label, text in bank2:
        if key_indic(text) in held_keys:
            problems.append(f"recipe 2 still teaches a held-out sentence: [{lang}] {label}: {text}")
    # Everything of recipe 1 that recipe 2 drops is a held-out copy; everything recipe 2 adds is a sentence the old key folded away.
    in2 = {(l, lb, t) for l, lb, t in bank2}
    for lang, label, text in bank1:
        if (lang, label, text) not in in2 and key_indic(text) not in held_keys:
            problems.append(f"recipe 2 dropped a sentence that is no held-out copy: [{lang}] {label}: {text}")
    in1 = {(l, lb, t) for l, lb, t in bank1}
    seen = set(held_keys)
    for lang, label, text in bank2:
        k = key_indic(text)
        if k in seen and (lang, label, text) not in in1:
            problems.append(f"recipe 2 added a sentence twice: [{lang}] {label}: {text}")
        seen.add(k)
    # The sentences of the source files, first round and second round, are all accounted for: kept, or the twin of one.
    kept = {key_indic(t) for _, _, t in bank2} | held_keys
    for lang in LANGS:
        for folder in (SRC, MORE):
            path = folder / f"{lang}.tsv"
            if path.exists():
                for label, text in lines_of(path):
                    if key_indic(text) not in kept:
                        problems.append(f"a source sentence is neither kept nor the twin of one: [{lang}] {label}: {text}")
    return problems, bank1, bank2, held2


def recipe3_problems(bank2=None, held2=None):
    """Recipe 3 against recipe 2: the same held-out set; the bank only loses rows, each one a measured-set sentence or a later
    copy of one; no bank row is a measured sentence. (problems, rows left out)."""
    if bank2 is None:
        bank2, held2 = read(2)
    bank3, held3 = read(3)
    measured = measured_keys()
    problems = []
    if held3 != held2:
        problems.append("the held-out set differs between recipe 2 and recipe 3")
    for lang, label, text in bank3:
        if key_indic(text) in measured:
            problems.append(f"recipe 3 still teaches a measured sentence: [{lang}] {label}: {text}")
    s3 = {(l, lb, t) for l, lb, t in bank3}
    if not s3 <= {(l, lb, t) for l, lb, t in bank2}:
        problems.append("recipe 3 adds a row recipe 2 does not have")
    gone = [b for b in bank2 if b not in s3]
    for lang, label, text in gone:
        if key_indic(text) not in measured:
            problems.append(f"recipe 3 left out a sentence that no measured set holds: [{lang}] {label}: {text}")
    if [b for b in bank2 if b in s3] != bank3:
        problems.append("recipe 3 changes the order of the rows it keeps")
    return problems, len(gone)


def bank_text(bank):
    return "".join(f"{label}\t{text}\n" for _, label, text in bank)


def held_text(held):
    return "".join(f"{lang}\t{label}\t{text}\n" for lang, label, text in held)


def _arg(name, default=None):
    if name in sys.argv:
        i = sys.argv.index(name)
        if i + 1 < len(sys.argv):
            return sys.argv[i + 1]
    return default


def main():
    recipe = 3 if "--next-key" in sys.argv else int(_arg("--recipe", DEFAULT_RECIPE))
    if recipe not in (1, 2, 3):
        print("--recipe is 1, 2 or 3 (--next-key is 3)")
        return 2
    bank, held = read(recipe)
    k, h = bank_text(bank), held_text(held)
    out_dir = _arg("--out-dir")
    if out_dir:
        d = Path(out_dir)
        d.mkdir(parents=True, exist_ok=True)
        (d / "bank.tsv").write_text(k, encoding="utf-8")
        (d / "heldout.tsv").write_text(h, encoding="utf-8")
        print(f"Wrote recipe {recipe}: {len(bank)} sentences to {d / 'bank.tsv'} and {len(held)} to {d / 'heldout.tsv'} (the shipped files are untouched).")
        return 0
    if "--check" in sys.argv:
        problems, bank1, bank2, held2 = coverage_problems()
        if problems:
            print("Recipe 2 coverage:\n  " + "\n  ".join(problems[:20]) + (f"\n  ... and {len(problems) - 20} more" if len(problems) > 20 else ""))
            return 1
        added = len({(l, lb, t) for l, lb, t in bank2} - {(l, lb, t) for l, lb, t in bank1})
        dropped = len({(l, lb, t) for l, lb, t in bank1} - {(l, lb, t) for l, lb, t in bank2})
        print(f"Recipe 2 coverage holds: it adds {added} sentences the old key folded together and leaves out {dropped} held-out copies "
              f"({len(bank1)} -> {len(bank2)} in the bank; the held-out set is the same {len(held2)}).")
        p3, left3 = recipe3_problems(bank2, held2)
        if p3:
            print("Recipe 3 (the next retrain's):\n  " + "\n  ".join(p3[:20]))
            return 1
        print(f"Recipe 3 (prepared, not shipped) holds: it leaves out {left3} more sentences that are measured-set sentences; nothing else moves.")
        stale = []
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != k:
            stale.append(str(OUT.relative_to(ROOT)))
        if not HELD.exists() or HELD.read_text(encoding="utf-8") != h:
            stale.append(str(HELD.relative_to(ROOT)))
        if stale:
            print("Stale, run scripts/gen_hudhud_bank.py: " + ", ".join(stale))
            return 1
        print(f"Hudhud's bank is current: {len(bank)} sentences, {len(held)} held out.")
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(k, encoding="utf-8")
    HELD.write_text(h, encoding="utf-8")
    print(f"Wrote {len(bank)} sentences to the bank and {len(held)} to the held-out set.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

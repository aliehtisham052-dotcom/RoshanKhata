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
DEFAULT_RECIPE = 1


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


def read(recipe=None):
    """(bank, held) as (language, meaning, sentence) rows; [recipe] is 1 (as shipped) or 2 (the stable one, see the header)."""
    recipe = DEFAULT_RECIPE if recipe is None else recipe
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
    if recipe == 2:
        # A first-round sentence that is a held-out sentence is not taught: the held-out figure would measure memory.
        held_keys = {same(t) for _, _, t in held}
        bank = [b for b in bank if same(b[2]) not in held_keys]
    taken = {same(t) for _, _, t in held} | {same(t) for _, _, t in bank}
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
    recipe = int(_arg("--recipe", DEFAULT_RECIPE))
    if recipe not in (1, 2):
        print("--recipe is 1 or 2")
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

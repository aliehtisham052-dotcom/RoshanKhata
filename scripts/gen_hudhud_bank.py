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


def lines_of(path):
    out = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        if not raw.strip():
            continue
        label, _, text = raw.partition("\t")
        label, text = label.strip(), " ".join(text.split())
        if not label or not text or "\t" in text:
            raise SystemExit(f"{path.name}: a line without a meaning and a sentence: {raw!r}")
        out.append((label, text))
    return out


def key(text):
    """A sentence as compared for sameness: no case, no marks or punctuation, single spaces."""
    import unicodedata
    t = unicodedata.normalize("NFD", text.lower())
    t = "".join(c for c in t if unicodedata.category(c)[0] not in "MP")
    return " ".join(t.split())


def read():
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
    taken = {key(t) for _, _, t in held} | {key(t) for _, _, t in bank}
    for lang in LANGS:
        path = MORE / f"{lang}.tsv"
        if not path.exists():
            continue
        for label, text in lines_of(path):
            k = key(text)
            if k in taken:
                continue
            taken.add(k)
            bank.append((lang, label, text))
    return bank, held


def bank_text(bank):
    return "".join(f"{label}\t{text}\n" for _, label, text in bank)


def held_text(held):
    return "".join(f"{lang}\t{label}\t{text}\n" for lang, label, text in held)


def main():
    bank, held = read()
    k, h = bank_text(bank), held_text(held)
    if "--check" in sys.argv:
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

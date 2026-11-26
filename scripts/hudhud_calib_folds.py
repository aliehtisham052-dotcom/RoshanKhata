#!/usr/bin/env python3
"""Five folds of Hudhud's training bank for the calibration (plan Marhala 3, 25 Nov 2026), split by PARAPHRASE FAMILY.

Every line of bank.tsv is traced back to where gen_hudhud_bank.py took it from: the round (first round shared/hudhud/<lang>.tsv
or second round shared/hudhud/more/<lang>.tsv), its meaning and its place among that meaning's sentences in that file. The
files are written in parallel: sentence 0 of "to_get" in ur-Latn, ur and hi is one sentence in three scripts ("mujhe kul kitne
paise lene hain", "مجھے کل کتنے پیسے لینے ہیں", "मुझे कुल कितने पैसे लेने हैं"), and the sense reads every word also by its sound
in any script (HudhudMind.skeleton), so the three forms share most of their features. gen_hudhud_bank.py holds out positions 3
and 8 in every language for the same reason. A family is therefore joined by
  1. the same (round, meaning, place) in any of the nine languages, and
  2. the same sentence as hudhud_sets.py compares it (gen_hudhud_bank.key_indic: no case, no marks but Indic vowel signs, no
     punctuation) anywhere in the bank,
taken together (union-find): all forms of one sentence fall in one fold. Within each meaning the families are ordered by a
SHA-256 of their id and each is dealt to the fold with the fewest of that meaning's families (then the fewest lines), so every
meaning has its share in every fold, the folds are of one size, and the split is the
same on every computer.

    python3 scripts/hudhud_calib_folds.py --out build/hudhud-calibrate/folds.tsv   # fold<TAB>language<TAB>meaning<TAB>sentence, bank order
    python3 scripts/hudhud_calib_folds.py --check                                  # prove the trace and the split, write nothing
    python3 scripts/hudhud_calib_folds.py --by-text --out FILE                     # the NAIVE split (text key only), to measure
                                                                                   # how much a split that ignores scripts flatters
    python3 scripts/hudhud_calib_folds.py --src DIR --bank FILE --recipe 3 --out F # a CANDIDATE bank (scripts/hudhud_cycle.sh): the
                                                                                   # sources in DIR (DIR/<lang>.tsv, DIR/more/), its bank.tsv

The output holds the bank's own lines in the bank's own order (checked against bank.tsv), so a fold model is learnt from exactly
the lines the shipped sense was learnt from, minus one fold.
"""
import hashlib
import importlib.util
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FOLDS = 5


def _load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


BANK = _load("gen_hudhud_bank")
SAME = BANK.key if BANK.DEFAULT_RECIPE == 1 else BANK.key_indic


def traced():
    """gen_hudhud_bank.read() of the shipped recipe, each bank row with its (round, meaning, place). Asserted equal to read()."""
    recipe = BANK.DEFAULT_RECIPE
    bank, held = [], []
    for lang in BANK.LANGS:
        path = BANK.SRC / f"{lang}.tsv"
        if not path.exists():
            continue
        seen = {}
        for label, text in BANK.lines_of(path):
            i = seen.get(label, 0)
            seen[label] = i + 1
            (held if i in BANK.HELD_OUT else bank).append((lang, label, text, ("first", label, i)))
    measured = BANK.measured_keys() if recipe == 3 else set()
    if recipe >= 2:
        held_keys = {SAME(t) for _, _, t, _ in held}
        bank = [b for b in bank if SAME(b[2]) not in held_keys and SAME(b[2]) not in measured]
    taken = {SAME(t) for _, _, t, _ in held} | {SAME(t) for _, _, t, _ in bank} | measured
    for lang in BANK.LANGS:
        path = BANK.MORE / f"{lang}.tsv"
        if not path.exists():
            continue
        seen = {}
        for label, text in BANK.lines_of(path):
            i = seen.get(label, 0)
            seen[label] = i + 1
            k = SAME(text)
            if k in taken:
                continue
            taken.add(k)
            bank.append((lang, label, text, ("more", label, i)))
    return bank


class _Union:
    def __init__(self):
        self.up = {}

    def find(self, x):
        self.up.setdefault(x, x)
        while self.up[x] != x:
            self.up[x] = self.up[self.up[x]]
            x = self.up[x]
        return x

    def join(self, a, b):
        ra, rb = self.find(a), self.find(b)
        if ra != rb:
            self.up[max(ra, rb)] = min(ra, rb)


def folds(by_text_only=False):
    rows = traced()
    shipped, _ = BANK.read()
    if [(l, lb, t) for l, lb, t, _ in rows] != shipped:
        raise SystemExit("hudhud_calib_folds: the traced bank is not gen_hudhud_bank.read(): the two readers drifted")
    bank_file = [l for l in BANK.OUT.read_text(encoding="utf-8").splitlines() if l.strip()]
    if [f"{lb}\t{t}" for _, lb, t, _ in rows] != bank_file:
        raise SystemExit("hudhud_calib_folds: bank.tsv is not what gen_hudhud_bank.py writes (run its --check)")
    u = _Union()
    for lang, label, text, place in rows:
        # --by-text: the naive split, for comparison only (one sentence's forms in other scripts may land in other folds).
        own = ("P\t" + lang + "\t" + "\t".join(map(str, place))) if by_text_only else ("P\t" + "\t".join(map(str, place)))
        u.join(own, "T\t" + SAME(text))
    family = [u.find(("P\t" + lang + "\t" + "\t".join(map(str, place))) if by_text_only else ("P\t" + "\t".join(map(str, place))))
              for lang, _, _, place in rows]
    # A family's meaning is its first row's (the bank has no sentence with two meanings: hudhud_sets.py --check).
    by_label = defaultdict(list)
    first_label = {}
    for (_, label, _, _), fam in zip(rows, family):
        if fam not in first_label:
            first_label[fam] = label
            by_label[label].append(fam)
    size = Counter(family)
    fold_of, rows_in = {}, [0] * FOLDS
    for label in sorted(by_label):
        per = [0] * FOLDS
        for fam in sorted(by_label[label], key=lambda f: hashlib.sha256(f.encode("utf-8")).hexdigest()):
            # The fold with the fewest of this meaning's families, then the fewest lines overall, then the lowest number.
            k = min(range(FOLDS), key=lambda j: (per[j], rows_in[j], j))
            fold_of[fam] = k
            per[k] += 1
            rows_in[k] += size[fam]
    return rows, family, [fold_of[f] for f in family]


def check(rows, family, fold, by_text_only=False):
    problems = []
    members = defaultdict(set)
    for f, k in zip(family, fold):
        members[f].add(k)
    split = [f for f, ks in members.items() if len(ks) > 1]
    if split:
        problems.append(f"{len(split)} families fall in more than one fold")
    # The same sentence (as compared) never in two folds, and one place's forms in the nine languages never apart.
    by_key, by_place = defaultdict(set), defaultdict(set)
    for (_, _, text, place), k in zip(rows, fold):
        by_key[SAME(text)].add(k)
        by_place[place].add(k)
    problems += [f"a sentence in two folds: {t!r}" for t, ks in by_key.items() if len(ks) > 1][:5]
    if not by_text_only:
        problems += [f"one place's forms in two folds: {p}" for p, ks in by_place.items() if len(ks) > 1][:5]
    sizes = Counter(fold)
    if max(sizes.values()) > 1.15 * min(sizes.values()):
        problems.append(f"folds out of balance: {dict(sorted(sizes.items()))}")
    return problems


def main(argv):
    global SAME
    # A candidate bank (scripts/hudhud_cycle.sh): its sources, its bank file and its recipe instead of the repository's.
    if "--src" in argv:
        src = Path(argv[argv.index("--src") + 1])
        BANK.SRC, BANK.MORE = src, src / "more"
    if "--bank" in argv:
        BANK.OUT = Path(argv[argv.index("--bank") + 1])
    if "--recipe" in argv:
        BANK.DEFAULT_RECIPE = int(argv[argv.index("--recipe") + 1])
        SAME = BANK.key if BANK.DEFAULT_RECIPE == 1 else BANK.key_indic
    naive = "--by-text" in argv
    rows, family, fold = folds(naive)
    problems = check(rows, family, fold, naive)
    sizes = Counter(fold)
    fams = len(set(family))
    summary = (f"{len(rows)} bank lines, {fams} {'sentence (text-only, naive)' if naive else 'paraphrase'} families, folds " +
               " ".join(f"{k}:{sizes[k]}" for k in range(FOLDS)))
    if problems:
        print("\n".join(problems))
        print("hudhud calib folds: " + str(len(problems)) + " problem(s); " + summary)
        return 1
    if "--out" in argv:
        out = Path(argv[argv.index("--out") + 1])
        out.parent.mkdir(parents=True, exist_ok=True)
        with out.open("w", encoding="utf-8") as f:
            for (lang, label, text, _), k in zip(rows, fold):
                f.write(f"{k}\t{lang}\t{label}\t{text}\n")
        print(f"hudhud calib folds written to {out}: {summary}")
        return 0
    print(f"hudhud calib folds OK: {summary}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

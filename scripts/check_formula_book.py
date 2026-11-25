#!/usr/bin/env python3
"""Holds docs/formula-book.tsv to the source: every `path::Symbol` it names must exist.

Drift protection, not proof (lesson 125): it says the named function is still there,
not that the row's words about it are true. A rename or a delete fails here, so the
book is read again.  Run by .github/workflows/strings.yml and scripts/run_release_checks.sh.
Usage: python3 scripts/check_formula_book.py   (exit 1 on any problem)
"""
import csv, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TSV = os.path.join(ROOT, "docs", "formula-book.tsv")
COLS = ["id", "figure", "function", "tables_columns", "window", "left_out",
        "rounding", "gate", "shown_on", "confidence"]


def word_in(word, text):
    return re.search(r"(?<![\w$])" + re.escape(word) + r"(?![\w$])", text) is not None


def main():
    problems, ids, n = [], set(), 0
    with open(TSV, encoding="utf-8", newline="") as f:
        rows = list(csv.reader(f, delimiter="\t"))
    if not rows or rows[0] != COLS:
        problems.append("header must be: " + "\t".join(COLS))
    for i, r in enumerate(rows[1:], start=2):
        if len(r) != len(COLS):
            problems.append(f"line {i}: {len(r)} columns, want {len(COLS)}")
            continue
        n += 1
        rid, fn = r[0], r[2]
        if not re.fullmatch(r"F\d{2,}", rid) or rid in ids:
            problems.append(f"line {i}: id {rid!r} is malformed or repeated")
        ids.add(rid)
        for c, name in enumerate(COLS):
            if not r[c].strip():
                problems.append(f"{rid}: column {name} is empty")
        for part in [p.strip() for p in fn.split(";") if p.strip()]:
            if "::" not in part:
                problems.append(f"{rid}: {part!r} is not path::Symbol")
                continue
            path, sym = part.split("::", 1)
            full = os.path.join(ROOT, path)
            if not os.path.isfile(full):
                problems.append(f"{rid}: file {path} does not exist")
                continue
            text = open(full, encoding="utf-8").read()
            for piece in sym.split("."):
                if not word_in(piece, text):
                    problems.append(f"{rid}: {piece!r} (of {sym}) not found in {path}")
    for p in problems:
        print("FAIL", p)
    print(f"{n} figures checked, {len(problems)} problems")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
